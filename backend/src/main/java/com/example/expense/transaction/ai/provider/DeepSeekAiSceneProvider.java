package com.example.expense.transaction.ai.provider;

import com.example.expense.transaction.ai.config.AiSceneProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DeepSeekAiSceneProvider implements AiSceneProvider {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiSceneProvider.class);
    private static final String SAFE_FAILURE_MESSAGE = "AI 分类服务暂时不可用";
    private static final int MAX_USER_MESSAGE_UTF8_BYTES = 32_768;
    private static final String OUTPUT_EXAMPLE =
            "{\"categoryToken\":\"category_1\",\"channel\":\"OFFLINE\","
                    + "\"onlinePlatformToken\":null,\"confidence\":0.91,\"reason\":\"简短理由\"}";
    private static final String SYSTEM_PROMPT = """
            你是消费场景分类助手。仅从用户提供的候选 token 中选择分类和线上平台。
            channel 只能是 ONLINE 或 OFFLINE；线下场景的 onlinePlatformToken 必须为 null。
            仅输出 JSON 对象，不要输出 Markdown 或额外说明。输出示例：
            %s
            """.formatted(OUTPUT_EXAMPLE);

    private final AiSceneProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DeepSeekAiSceneProvider(
            AiSceneProperties properties,
            @Qualifier("deepSeekRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "deepseek";
    }

    @Override
    public AiSceneProviderResult recommend(AiSceneProviderRequest request) {
        long startedAt = System.nanoTime();
        try {
            JsonNode response = restClient.post()
                    .uri(completionsUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getDeepseek().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(request))
                    .retrieve()
                    .body(JsonNode.class);
            AiSceneProviderResult result = parseResult(response);
            log.info("aiSceneRequest provider={} model={} status=success durationMs={}",
                    providerName(), model(), elapsedMs(startedAt));
            return result;
        } catch (Exception exception) {
            log.warn("aiSceneRequest provider={} model={} status=failure durationMs={}",
                    providerName(), model(), elapsedMs(startedAt));
            throw new AiSceneProviderException(SAFE_FAILURE_MESSAGE);
        }
    }

    private Map<String, Object> requestBody(AiSceneProviderRequest request) throws Exception {
        String userMessage = objectMapper.writeValueAsString(Map.of(
                "itemName", request.itemName(),
                "type", request.type(),
                "categories", request.categories(),
                "onlinePlatforms", request.onlinePlatforms()));
        if (userMessage.getBytes(StandardCharsets.UTF_8).length > MAX_USER_MESSAGE_UTF8_BYTES) {
            throw new AiSceneProviderException(SAFE_FAILURE_MESSAGE);
        }
        return Map.of(
                "model", model(),
                "stream", false,
                "thinking", Map.of("type", "disabled"),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.1,
                "max_tokens", 180,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)));
    }

    private AiSceneProviderResult parseResult(JsonNode response) throws Exception {
        JsonNode choices = response == null ? null : response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new AiSceneProviderException(SAFE_FAILURE_MESSAGE);
        }
        JsonNode content = choices.get(0).path("message").get("content");
        if (content == null || !content.isTextual() || content.asText().isBlank()) {
            throw new AiSceneProviderException(SAFE_FAILURE_MESSAGE);
        }
        JsonNode structured = objectMapper.readTree(content.asText());
        JsonNode confidence = structured == null ? null : structured.get("confidence");
        if (confidence == null || !confidence.isNumber() || !Double.isFinite(confidence.doubleValue())) {
            throw new AiSceneProviderException(SAFE_FAILURE_MESSAGE);
        }
        return objectMapper.treeToValue(structured, AiSceneProviderResult.class);
    }

    private String completionsUrl() {
        String baseUrl = properties.getDeepseek().getBaseUrl();
        String normalized = baseUrl == null || baseUrl.isBlank()
                ? "https://api.deepseek.com"
                : baseUrl.trim();
        return normalized.replaceAll("/+$", "") + "/chat/completions";
    }

    private String model() {
        return properties.getDeepseek().getModel();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
