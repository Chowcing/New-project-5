package com.example.expense.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.auth.dto.LoginRequest;
import com.example.expense.auth.dto.RefreshTokenRequest;
import com.example.expense.auth.service.AuthService;
import com.example.expense.auth.service.LoginRateLimitException;
import com.example.expense.auth.service.LoginRateLimiter;
import com.example.expense.common.config.SecurityConfig;
import com.example.expense.common.security.JwtAuthenticationFilter;
import com.example.expense.common.security.JwtService;
import com.example.expense.common.web.GlobalExceptionHandler;
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
@Import({AuthController.class, SecurityConfig.class, GlobalExceptionHandler.class, AuthControllerSecurityTest.SecurityBeans.class})
class AuthControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AuthService authService;
    @MockBean
    private UserMapper userMapper;
    @MockBean
    private AdminProperties adminProperties;
    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @Test
    void logoutDoesNotRequireAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk());

        verify(authService).logout(new RefreshTokenRequest("refresh-token"));
    }

    @Test
    void loginReturnsTooManyRequestsWhenRateLimited() throws Exception {
        doThrow(new LoginRateLimitException("登录失败次数过多，请 15 分钟后重试", 900))
                .when(loginRateLimiter).checkAllowed("demo", "127.0.0.1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("登录失败次数过多，请 15 分钟后重试"))
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(900));

        verify(authService, never()).login(any(LoginRequest.class));
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
