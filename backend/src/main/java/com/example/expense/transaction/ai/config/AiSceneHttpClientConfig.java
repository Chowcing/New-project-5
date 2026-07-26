package com.example.expense.transaction.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiSceneHttpClientConfig {
    @Bean("deepSeekRestClient")
    public RestClient deepSeekRestClient(AiSceneProperties properties) {
        int timeoutMs = properties.getDeepseek().getTimeoutMs();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.min(timeoutMs, 2000));
        requestFactory.setReadTimeout(timeoutMs);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
