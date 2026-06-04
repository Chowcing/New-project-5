package com.example.expense.ocr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OcrHttpClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(30000);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
