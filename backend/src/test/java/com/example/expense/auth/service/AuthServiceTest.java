package com.example.expense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.auth.dto.RegisterRequest;
import com.example.expense.auth.dto.TokenResponse;
import com.example.expense.auth.entity.RefreshToken;
import com.example.expense.auth.mapper.RefreshTokenMapper;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.security.JwtProperties;
import com.example.expense.common.security.JwtService;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private RefreshTokenMapper refreshTokenMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private PaymentMethodService paymentMethodService;
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
                categoryService,
                paymentMethodService,
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

        verify(categoryService).createDefaults(1001L);
        verify(paymentMethodService).createDefaults(1001L);
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
    }
}
