package com.example.expense.ocr.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ocr")
@Getter
@Setter
public class OcrProperties {
    private boolean enabled = false;
    private String provider = "disabled";
    private Local local = new Local();

    @Getter
    @Setter
    public static class Local {
        private String baseUrl = "http://localhost:9000";
    }
}
