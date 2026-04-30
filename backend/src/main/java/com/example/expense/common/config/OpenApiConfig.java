package com.example.expense.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI expenseOpenApi() {
        // 显式声明 HTTP Bearer/JWT 安全方案，Swagger UI 才会显示 Authorize 按钮并把 token 带到请求头。
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("填写登录接口返回的 accessToken，无需输入 Bearer 前缀。");

        return new OpenAPI()
                .info(new Info()
                        .title("日常生活消费记录 API")
                        .version("v1")
                        .description("移动端 Web 记账项目 RESTful API"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}

