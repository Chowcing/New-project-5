package com.example.expense.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.common.security.UserPrincipal;
import com.example.expense.common.web.GlobalExceptionHandler;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import com.example.expense.user.service.RecycleBinSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    private static final Long USER_ID = 1001L;

    @Mock
    private UserMapper userMapper;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(
                        userMapper,
                        new AdminProperties(),
                        new RecycleBinSettingsService(userMapper)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setConversionService(new ApplicationConversionService())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        UserPrincipal principal = new UserPrincipal(USER_ID, "demo", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.destroy();
    }

    @Test
    void getRecycleBinSettingsReturnsCurrentUsersRetentionDays() throws Exception {
        ExpenseUser user = new ExpenseUser();
        user.setId(USER_ID);
        user.setTrashRetentionDays(30);
        org.mockito.Mockito.when(userMapper.selectById(USER_ID)).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/me/recycle-bin-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retentionDays").value(30));
    }

    @Test
    void updateRecycleBinSettingsUpdatesOnlyCurrentUsersSetting() throws Exception {
        ExpenseUser user = new ExpenseUser();
        user.setId(USER_ID);
        user.setTrashRetentionDays(30);
        org.mockito.Mockito.when(userMapper.selectById(USER_ID)).thenReturn(user);

        mockMvc.perform(put("/api/v1/users/me/recycle-bin-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":365}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retentionDays").value(365));
    }

    @Test
    void updateRecycleBinSettingsRejectsZeroRetentionDays() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/recycle-bin-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userMapper);
    }

    @Test
    void updateRecycleBinSettingsRejectsFractionalRetentionDays() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/recycle-bin-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":1.5}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userMapper);
    }
}
