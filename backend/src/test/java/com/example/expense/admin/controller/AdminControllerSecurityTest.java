package com.example.expense.admin.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.admin.service.AdminService;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.config.SecurityConfig;
import com.example.expense.common.security.JwtAuthenticationFilter;
import com.example.expense.common.security.JwtService;
import com.example.expense.common.security.UserPrincipal;
import com.example.expense.common.web.GlobalExceptionHandler;
import com.example.expense.user.entity.ExpenseUser;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import({AdminController.class, SecurityConfig.class, GlobalExceptionHandler.class, AdminControllerSecurityTest.SecurityBeans.class})
class AdminControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private AdminService adminService;
    @MockBean
    private BusinessAuditLogService businessAuditLogService;
    @MockBean
    private UserMapper userMapper;
    @MockBean
    private AdminProperties adminProperties;

    @Test
    void adminEndpointsRejectAuthenticatedNonAdminUser() throws Exception {
        ExpenseUser user = new ExpenseUser();
        user.setId(1001L);
        user.setUsername("demo");
        user.setStatus("ACTIVE");
        when(jwtService.parseAccessToken("user-token")).thenReturn(new UserPrincipal(1001L, "demo", false));
        when(userMapper.selectById(1001L)).thenReturn(user);
        when(adminProperties.isAdmin("demo")).thenReturn(false);

        mockMvc.perform(get("/api/v1/admin/workbench")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserMapper userMapper, AdminProperties adminProperties) {
            return new JwtAuthenticationFilter(jwtService, userMapper, adminProperties);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
