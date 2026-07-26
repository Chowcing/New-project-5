package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.ai.config.AiSceneProperties;
import com.example.expense.transaction.ai.provider.AiSceneProvider;
import com.example.expense.transaction.ai.provider.AiSceneProviderException;
import com.example.expense.transaction.ai.provider.AiSceneProviderRequest;
import com.example.expense.transaction.ai.provider.AiSceneProviderResult;
import com.example.expense.transaction.ai.service.AiSceneRateLimiter;
import com.example.expense.transaction.ai.service.AiSceneUnavailableException;
import com.example.expense.transaction.dto.AiSceneRecommendationRequest;
import com.example.expense.transaction.dto.AiSceneRecommendationResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionAiRecommendationServiceTest {
    private static final Long USER_ID = 1001L;

    @Mock
    private CategoryService categoryService;
    @Mock
    private OnlinePlatformService onlinePlatformService;
    @Mock
    private AiSceneRateLimiter rateLimiter;
    @Mock
    private AiSceneProvider provider;

    private AiSceneProperties properties;
    private TransactionAiRecommendationService service;

    @BeforeEach
    void setUp() {
        properties = new AiSceneProperties();
        properties.setEnabled(true);
        properties.setProvider("test");
        properties.setConfidenceThreshold(0.75);
        service = new TransactionAiRecommendationService(
                categoryService,
                onlinePlatformService,
                rateLimiter,
                properties,
                List.of(provider));
    }

    @Test
    void returnsOwnedHighConfidenceCategoryAndOfflineChannel() {
        Category category = category(12L, USER_ID, "娱乐", "EXPENSE");
        when(categoryService.list(USER_ID, "EXPENSE")).thenReturn(List.of(category));
        when(onlinePlatformService.list(USER_ID)).thenReturn(List.of());
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(any())).thenReturn(
                new AiSceneProviderResult("category_1", "OFFLINE", null, 0.91,
                        "乐园通常属于线下娱乐消费"));

        AiSceneRecommendationResponse response = service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE"));

        assertThat(response.status()).isEqualTo("SUGGESTED");
        assertThat(response.categoryId()).isEqualTo(12L);
        assertThat(response.categoryName()).isEqualTo("娱乐");
        assertThat(response.channel()).isEqualTo("OFFLINE");
        assertThat(response.onlinePlatformId()).isNull();
        assertThat(response.onlinePlatformName()).isNull();
        assertThat(response.confidence()).isEqualTo(0.91);
        assertThat(response.reason()).isEqualTo("乐园通常属于线下娱乐消费");
        verify(rateLimiter).checkAllowed(USER_ID);
    }

    @Test
    void confidenceBelowThresholdReturnsUncertainWithoutOwnedIds() {
        stubCandidates();
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(any())).thenReturn(
                new AiSceneProviderResult("category_1", "ONLINE", "platform_1", 0.749, "不够确定"));

        AiSceneRecommendationResponse response = service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE"));

        assertThat(response.status()).isEqualTo("UNCERTAIN");
        assertThat(response.categoryId()).isNull();
        assertThat(response.categoryName()).isNull();
        assertThat(response.channel()).isNull();
        assertThat(response.onlinePlatformId()).isNull();
        assertThat(response.onlinePlatformName()).isNull();
        assertThat(response.confidence()).isZero();
    }

    @Test
    void confidenceAtThresholdReturnsSuggested() {
        stubCandidates();
        when(provider.providerName()).thenReturn("TEST");
        when(provider.recommend(any())).thenReturn(
                new AiSceneProviderResult("category_1", "ONLINE", "platform_1", 0.75, "刚好达到阈值"));

        AiSceneRecommendationResponse response = service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE"));

        assertThat(response.status()).isEqualTo("SUGGESTED");
        assertThat(response.categoryId()).isEqualTo(12L);
        assertThat(response.channel()).isEqualTo("ONLINE");
        assertThat(response.onlinePlatformId()).isEqualTo(22L);
    }

    @Test
    void unknownCategoryTokenFailsWithoutLookingUpModelSuppliedIds() {
        stubCandidates();
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(any())).thenReturn(
                new AiSceneProviderResult("category_999", "OFFLINE", null, 0.91, "未知 token"));

        assertThatThrownBy(() -> service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE")))
                .isInstanceOf(AiSceneUnavailableException.class)
                .hasMessage("AI 分类服务暂时不可用");

        verify(categoryService, never()).requireOwned(any(), any());
        verify(onlinePlatformService, never()).requireOwned(any(), any());
    }

    @Test
    void unknownOnlinePlatformTokenFailsSafely() {
        stubCandidates();
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(any())).thenReturn(
                new AiSceneProviderResult("category_1", "ONLINE", "platform_999", 0.91, "未知 token"));

        assertThatThrownBy(() -> service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE")))
                .isInstanceOf(AiSceneUnavailableException.class)
                .hasMessage("AI 分类服务暂时不可用");
    }

    @Test
    void offlineChannelClearsReturnedPlatformToken() {
        stubCandidates();
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(any())).thenReturn(
                new AiSceneProviderResult("category_1", "OFFLINE", "platform_999", 0.91, "线下场景"));

        AiSceneRecommendationResponse response = service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE"));

        assertThat(response.status()).isEqualTo("SUGGESTED");
        assertThat(response.channel()).isEqualTo("OFFLINE");
        assertThat(response.onlinePlatformId()).isNull();
        assertThat(response.onlinePlatformName()).isNull();
    }

    @Test
    void onlineChannelMayOmitPlatform() {
        stubCandidates();
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(any())).thenReturn(
                new AiSceneProviderResult("category_1", "ONLINE", null, 0.91, "无法确定平台"));

        AiSceneRecommendationResponse response = service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE"));

        assertThat(response.status()).isEqualTo("SUGGESTED");
        assertThat(response.channel()).isEqualTo("ONLINE");
        assertThat(response.onlinePlatformId()).isNull();
        assertThat(response.onlinePlatformName()).isNull();
    }

    @Test
    void providerReceivesOnlyNormalizedOwnedCandidatesAndNoUserId() {
        Category owned = category(12L, USER_ID, "娱乐", "EXPENSE");
        Category foreign = category(13L, 2002L, "他人分类", "EXPENSE");
        OnlinePlatform ownedPlatform = platform(22L, USER_ID, "美团");
        OnlinePlatform foreignPlatform = platform(23L, 2002L, "他人平台");
        when(categoryService.list(USER_ID, "EXPENSE")).thenReturn(List.of(owned, foreign));
        when(onlinePlatformService.list(USER_ID)).thenReturn(List.of(ownedPlatform, foreignPlatform));
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(any())).thenReturn(
                new AiSceneProviderResult("category_1", "ONLINE", "platform_1", 0.91, "匹配"));

        service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("  乐园  ", "expense"));

        ArgumentCaptor<AiSceneProviderRequest> captor = ArgumentCaptor.forClass(AiSceneProviderRequest.class);
        verify(provider).recommend(captor.capture());
        AiSceneProviderRequest providerRequest = captor.getValue();
        assertThat(providerRequest.itemName()).isEqualTo("乐园");
        assertThat(providerRequest.type()).isEqualTo("EXPENSE");
        assertThat(providerRequest.categories())
                .extracting("token", "name")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("category_1", "娱乐"));
        assertThat(providerRequest.onlinePlatforms())
                .extracting("token", "name")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("platform_1", "美团"));
        assertThat(Arrays.stream(AiSceneProviderRequest.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("userId");
        verify(categoryService).list(USER_ID, "EXPENSE");
        verify(onlinePlatformService).list(USER_ID);
    }

    @Test
    void disabledFeatureNeverExecutesProviderRateLimitOrCandidateQueries() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE")))
                .isInstanceOf(AiSceneUnavailableException.class)
                .hasMessage("AI 分类服务暂时不可用");

        verifyNoInteractions(provider, rateLimiter, categoryService, onlinePlatformService);
    }

    @Test
    void emptyOrDisabledProviderNeverExecutesDependencies() {
        for (String providerName : List.of("", "  ", "disabled", " DISABLED ")) {
            properties.setProvider(providerName);

            assertThatThrownBy(() -> service.recommend(
                    USER_ID,
                    new AiSceneRecommendationRequest("乐园", "EXPENSE")))
                    .isInstanceOf(AiSceneUnavailableException.class)
                    .hasMessage("AI 分类服务暂时不可用");
        }

        verifyNoInteractions(provider, rateLimiter, categoryService, onlinePlatformService);
    }

    @Test
    void sanitizesControlCharactersAndLimitsReasonToEightyCharacters() {
        stubCandidates();
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(any())).thenReturn(
                new AiSceneProviderResult(
                        "category_1",
                        "OFFLINE",
                        null,
                        0.91,
                        "\n\u0000" + "理".repeat(90) + "\r"));

        AiSceneRecommendationResponse response = service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE"));

        assertThat(response.reason()).isEqualTo("理".repeat(80));
        assertThat(response.reason()).hasSize(80);
    }

    @Test
    void providerFailureIsMappedToSafeUnavailableMessage() {
        stubCandidates();
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(any())).thenThrow(new AiSceneProviderException("api-key=secret timeout"));

        assertThatThrownBy(() -> service.recommend(
                USER_ID,
                new AiSceneRecommendationRequest("乐园", "EXPENSE")))
                .isInstanceOf(AiSceneUnavailableException.class)
                .hasMessage("AI 分类服务暂时不可用")
                .hasCauseInstanceOf(AiSceneProviderException.class)
                .message()
                .doesNotContain("secret");
    }

    @Test
    void statusUsesConfigurationOnlyAndNeverTouchesProviderOrRequestDependencies() {
        properties.setEnabled(true);
        properties.setProvider("deepseek");

        assertThat(service.availability().enabled()).isTrue();

        properties.setProvider("disabled");
        assertThat(service.availability().enabled()).isFalse();

        verifyNoInteractions(provider, rateLimiter, categoryService, onlinePlatformService);
    }

    private void stubCandidates() {
        when(categoryService.list(USER_ID, "EXPENSE"))
                .thenReturn(List.of(category(12L, USER_ID, "娱乐", "EXPENSE")));
        when(onlinePlatformService.list(USER_ID))
                .thenReturn(List.of(platform(22L, USER_ID, "美团")));
    }

    private Category category(Long id, Long userId, String name, String type) {
        Category category = new Category();
        category.setId(id);
        category.setUserId(userId);
        category.setName(name);
        category.setType(type);
        return category;
    }

    private OnlinePlatform platform(Long id, Long userId, String name) {
        OnlinePlatform platform = new OnlinePlatform();
        platform.setId(id);
        platform.setUserId(userId);
        platform.setName(name);
        return platform;
    }
}
