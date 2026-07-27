package com.example.expense.transaction.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.expense.transaction.ai.config.AiSceneProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeepSeekAiSceneProviderTest {
    private static final String SAFE_FAILURE_MESSAGE = "AI 分类服务暂时不可用";
    private static final String EXACT_OUTPUT_EXAMPLE =
            "{\"categoryToken\":\"category_1\",\"channel\":\"OFFLINE\","
                    + "\"onlinePlatformToken\":null,\"confidence\":0.91,\"reason\":\"简短理由\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private DeepSeekAiSceneProvider provider;
    private Logger logger;
    private Level originalLoggerLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AiSceneProperties properties = new AiSceneProperties();
        properties.getDeepseek().setApiKey("test-key");
        provider = new DeepSeekAiSceneProvider(properties, builder.build(), objectMapper);

        logger = (Logger) LoggerFactory.getLogger(DeepSeekAiSceneProvider.class);
        originalLoggerLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        server.verify();
        logger.detachAppender(appender);
        logger.setLevel(originalLoggerLevel);
        appender.stop();
    }

    @Test
    void sendsMinimalNonThinkingJsonRequestWithoutApplicationUserData() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"model\":\"deepseek-v4-flash\"")))
                .andExpect(content().string(containsString("\"stream\":false")))
                .andExpect(content().string(containsString("\"thinking\":{\"type\":\"disabled\"}")))
                .andExpect(content().string(containsString("\"response_format\":{\"type\":\"json_object\"}")))
                .andExpect(content().string(containsString("\"temperature\":0.1")))
                .andExpect(content().string(containsString("\"max_tokens\":180")))
                .andExpect(content().string(containsString("乐园")))
                .andExpect(content().string(not(containsString("\"user_id\""))))
                .andExpect(content().string(not(containsString("amount"))))
                .andExpect(request -> assertSafeMessages(((MockClientHttpRequest) request).getBodyAsBytes()))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        AiSceneProviderResult result = provider.recommend(request());

        assertThat(provider.providerName()).isEqualTo("deepseek");
        assertThat(result.categoryToken()).isEqualTo("category_1");
        assertThat(result.channel()).isEqualTo("OFFLINE");
        assertThat(result.onlinePlatformToken()).isNull();
        assertThat(result.confidence()).isEqualTo(0.91);
        assertThat(result.reason()).isEqualTo("乐园通常属于线下娱乐消费");
    }

    @Test
    void rejectsEmptyChoicesWithSafeFailure() {
        expectSuccessResponse("{\"choices\":[]}");

        assertSafeFailure();
    }

    @Test
    void rejectsNullMessageContentWithSafeFailure() {
        expectSuccessResponse("{\"choices\":[{\"message\":{\"content\":null}}]}");

        assertSafeFailure();
    }

    @Test
    void rejectsMalformedStructuredOutputWithSafeFailure() {
        expectSuccessResponse("""
                {"choices":[{"message":{"content":"not-json"}}]}
                """);

        assertSafeFailure();
    }

    @Test
    void rejectsNonFiniteConfidenceWithSafeFailure() {
        expectSuccessResponse("""
                {
                  "choices": [{
                    "message": {
                      "content": "{\\"categoryToken\\":\\"category_1\\",\\"channel\\":\\"OFFLINE\\",\\"onlinePlatformToken\\":null,\\"confidence\\":\\"NaN\\",\\"reason\\":\\"invalid confidence\\"}"
                    }
                  }]
                }
                """);

        assertSafeFailure();
    }

    @Test
    void mapsHttp429ToSafeFailure() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withTooManyRequests());

        assertSafeFailure();
    }

    @Test
    void mapsHttp5xxToSafeFailure() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(request -> org.springframework.http.client.ClientHttpResponse.class.cast(
                        new org.springframework.mock.http.client.MockClientHttpResponse(
                                "upstream-secret".getBytes(), HttpStatus.SERVICE_UNAVAILABLE)));

        assertSafeFailure();
    }

    @Test
    void rejectsOversizedUtf8UserMessageBeforeSendingHttpRequest() {
        AiSceneProviderRequest oversizedRequest = new AiSceneProviderRequest(
                "乐".repeat(11_000),
                "EXPENSE",
                List.of(new AiSceneCandidate("category_1", "娱乐")),
                List.of());

        assertThatThrownBy(() -> provider.recommend(oversizedRequest))
                .isInstanceOf(AiSceneProviderException.class)
                .hasMessage(SAFE_FAILURE_MESSAGE);
    }

    @Test
    void successLogContainsOnlyOperationalMetadata() {
        expectSuccessResponse(successBody());

        provider.recommend(request());

        assertSafeLogs();
        assertThat(formattedLogs()).contains("provider=deepseek", "model=deepseek-v4-flash", "status=success", "durationMs=");
    }

    @Test
    void failureLogContainsOnlyOperationalMetadata() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(request -> new org.springframework.mock.http.client.MockClientHttpResponse(
                        "乐园 娱乐 美团 test-key system prompt model response reason".getBytes(),
                        HttpStatus.INTERNAL_SERVER_ERROR));

        assertSafeFailure();

        assertSafeLogs();
        assertThat(formattedLogs()).contains("provider=deepseek", "model=deepseek-v4-flash", "status=failure", "durationMs=");
    }

    private void assertSafeMessages(byte[] requestBody) throws IOException {
        JsonNode body = objectMapper.readTree(requestBody);
        JsonNode messages = body.path("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        String systemPrompt = messages.get(0).path("content").asText();
        assertThat(systemPrompt).contains("JSON", EXACT_OUTPUT_EXAMPLE);

        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        JsonNode userMessage = objectMapper.readTree(messages.get(1).path("content").asText());
        assertThat(iterableFieldNames(userMessage))
                .containsExactlyInAnyOrder("itemName", "type", "categories", "onlinePlatforms");
        assertThat(userMessage.path("itemName").asText()).isEqualTo("乐园");
        assertThat(userMessage.path("type").asText()).isEqualTo("EXPENSE");
        assertThat(userMessage.path("categories").get(0).path("token").asText()).isEqualTo("category_1");
        assertThat(userMessage.path("categories").get(0).path("name").asText()).isEqualTo("娱乐");
        assertThat(userMessage.path("onlinePlatforms").get(0).path("token").asText()).isEqualTo("platform_1");
        assertThat(userMessage.path("onlinePlatforms").get(0).path("name").asText()).isEqualTo("美团");
    }

    private Set<String> iterableFieldNames(JsonNode node) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private void expectSuccessResponse(String body) {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertSafeFailure() {
        assertThatThrownBy(() -> provider.recommend(request()))
                .isInstanceOf(AiSceneProviderException.class)
                .hasMessage(SAFE_FAILURE_MESSAGE);
    }

    private void assertSafeLogs() {
        assertThat(formattedLogs())
                .doesNotContain("乐园", "娱乐", "美团", "test-key", "system prompt",
                        "乐园通常属于线下娱乐消费", "model response reason");
    }

    private String formattedLogs() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private AiSceneProviderRequest request() {
        return new AiSceneProviderRequest(
                "乐园",
                "EXPENSE",
                List.of(new AiSceneCandidate("category_1", "娱乐")),
                List.of(new AiSceneCandidate("platform_1", "美团")));
    }

    private String successBody() {
        return """
                {
                  "choices": [{
                    "message": {
                      "content": "{\\"categoryToken\\":\\"category_1\\",\\"channel\\":\\"OFFLINE\\",\\"onlinePlatformToken\\":null,\\"confidence\\":0.91,\\"reason\\":\\"乐园通常属于线下娱乐消费\\"}"
                    }
                  }]
                }
                """;
    }
}
