package com.example.expense.user.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.common.config.SecurityConfig;
import com.example.expense.common.security.JwtAuthenticationFilter;
import com.example.expense.common.security.JwtService;
import com.example.expense.common.web.GlobalExceptionHandler;
import com.example.expense.user.mapper.UserMapper;
import com.example.expense.user.service.RecycleBinSettingsService;
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

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, UserControllerSecurityTest.SecurityBeans.class})
class UserControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private RecycleBinSettingsService recycleBinSettingsService;
    @MockBean
    private UserMapper userMapper;
    @MockBean
    private AdminProperties adminProperties;

    @Test
    void getRecycleBinSettingsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/recycle-bin-settings"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recycleBinSettingsService, userMapper);
    }

    @Test
    void updateRecycleBinSettingsRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/recycle-bin-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":1}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recycleBinSettingsService, userMapper);
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
