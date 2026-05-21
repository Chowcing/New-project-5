package com.example.expense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.auth.dto.LoginRequest;
import com.example.expense.auth.dto.RegisterRequest;
import com.example.expense.auth.dto.TokenResponse;
import com.example.expense.auth.entity.RefreshToken;
import com.example.expense.auth.mapper.RefreshTokenMapper;
import com.example.expense.common.security.JwtProperties;
import com.example.expense.common.security.JwtService;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import com.example.expense.user.service.UserBootstrapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private RefreshTokenMapper refreshTokenMapper;
    @Mock
    private UserBootstrapService userBootstrapService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private JwtProperties jwtProperties;

    @Test
    void registerCreatesCommonDefaultCategoriesAndPaymentMethods() {
        AuthService authService = new AuthService(
                userMapper,
                refreshTokenMapper,
                userBootstrapService,
                passwordEncoder,
                jwtService,
                jwtProperties
        );
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(jwtService.generateAccessToken(1001L, "demo")).thenReturn("access-token");
        when(jwtService.accessTokenSeconds()).thenReturn(1800L);
        when(jwtProperties.getRefreshTokenDays()).thenReturn(14L);
        doAnswer(invocation -> {
            ExpenseUser user = invocation.getArgument(0);
            user.setId(1001L);
            return 1;
        }).when(userMapper).insert(any(ExpenseUser.class));

        TokenResponse response = authService.register(new RegisterRequest("demo", "secret123", "演示用户"));

        assertThat(response.accessToken()).isEqualTo("access-token");

        verify(userBootstrapService).bootstrapDefaultData(1001L);
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
    }

    @Test
    void loginRejectsDisabledUser() {
        AuthService authService = new AuthService(
                userMapper,
                refreshTokenMapper,
                userBootstrapService,
                passwordEncoder,
                jwtService,
                jwtProperties
        );
        ExpenseUser user = new ExpenseUser();
        user.setId(1001L);
        user.setUsername("demo");
        user.setPasswordHash("encoded-password");
        user.setStatus("DISABLED");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret123", "encoded-password")).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "secret123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("账号已被禁用");
    }
}
