package com.example.expense.auth.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.auth.dto.RefreshTokenRequest;
import com.example.expense.auth.service.AuthService;
import com.example.expense.common.config.SecurityConfig;
import com.example.expense.common.security.JwtAuthenticationFilter;
import com.example.expense.common.security.JwtService;
import com.example.expense.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({AuthController.class, SecurityConfig.class, AuthControllerSecurityTest.SecurityBeans.class})
class AuthControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AuthService authService;
    @MockBean
    private UserMapper userMapper;
    @MockBean
    private AdminProperties adminProperties;

    @Test
    void logoutDoesNotRequireAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk());

        verify(authService).logout(new RefreshTokenRequest("refresh-token"));
    }

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(UserMapper userMapper, AdminProperties adminProperties) {
            return new JwtAuthenticationFilter(mock(JwtService.class), userMapper, adminProperties);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
