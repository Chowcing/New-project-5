package com.example.expense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.auth.dto.AuthLoginStartResponse;
import com.example.expense.auth.dto.LoginVerifyRequest;
import com.example.expense.auth.dto.LoginRequest;
import com.example.expense.auth.dto.RegisterRequest;
import com.example.expense.auth.dto.TokenResponse;
import com.example.expense.auth.entity.AuthChallenge;
import com.example.expense.auth.entity.RefreshToken;
import com.example.expense.auth.mapper.AuthChallengeMapper;
import com.example.expense.auth.mapper.RefreshTokenMapper;
import com.example.expense.common.security.JwtProperties;
import com.example.expense.common.security.JwtService;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import com.example.expense.user.service.UserBootstrapService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private UserMapper userMapper;
    @Mock
    private RefreshTokenMapper refreshTokenMapper;
    @Mock
    private AuthChallengeMapper authChallengeMapper;
    @Mock
    private EmailCodeService emailCodeService;
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
                authChallengeMapper,
                emailCodeService,
                userBootstrapService,
                passwordEncoder,
                jwtService,
                jwtProperties,
                CLOCK
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

        when(emailCodeService.consumeLatest("REGISTER", "demo@example.com", "123456")).thenReturn(null);

        TokenResponse response = authService.register(new RegisterRequest("demo", "secret123", "演示用户", "demo@example.com", "123456"));

        assertThat(response.accessToken()).isEqualTo("access-token");

        verify(userBootstrapService).bootstrapDefaultData(1001L);
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
        verify(emailCodeService).consumeLatest("REGISTER", "demo@example.com", "123456");
    }

    @Test
    void registerRejectsInvalidEmailCodeBeforeCreatingUser() {
        AuthService authService = service();
        when(userMapper.selectOne(any())).thenReturn(null);
        when(emailCodeService.consumeLatest("REGISTER", "demo@example.com", "000000"))
                .thenThrow(new BadCredentialsException("验证码错误"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("demo", "secret123", "演示用户", "demo@example.com", "000000")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("验证码错误");

        verify(userMapper, never()).insert(any(ExpenseUser.class));
        verify(refreshTokenMapper, never()).insert(any(RefreshToken.class));
    }

    @Test
    void loginWithVerifiedEmailCreatesMfaChallengeWithoutIssuingToken() {
        AuthService authService = service();
        ExpenseUser user = activeUser();
        user.setEmail("demo@example.com");
        user.setEmailVerifiedAt(LocalDateTime.now(CLOCK).minusDays(1));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret123", "encoded-password")).thenReturn(true);
        when(emailCodeService.createAndSend("LOGIN", user.getId(), "demo@example.com")).thenReturn("challenge-1");

        AuthLoginStartResponse response = authService.login(new LoginRequest("demo", "secret123"));

        assertThat(response.status()).isEqualTo("MFA_REQUIRED");
        assertThat(response.challengeId()).isEqualTo("challenge-1");
        verify(refreshTokenMapper, never()).insert(any(RefreshToken.class));
    }

    @Test
    void verifyLoginChallengeIssuesToken() {
        AuthService authService = service();
        ExpenseUser user = activeUser();
        AuthChallenge challenge = new AuthChallenge();
        challenge.setUserId(1001L);
        when(emailCodeService.consume("LOGIN", "challenge-1", "123456")).thenReturn(challenge);
        when(userMapper.selectById(1001L)).thenReturn(user);
        when(jwtService.generateAccessToken(1001L, "demo")).thenReturn("access-token");
        when(jwtService.accessTokenSeconds()).thenReturn(1800L);
        when(jwtProperties.getRefreshTokenDays()).thenReturn(14L);

        TokenResponse response = authService.verifyLogin(new LoginVerifyRequest("challenge-1", "123456"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
    }

    @Test
    void loginForLegacyUserRequiresEmailBindingWithoutIssuingToken() {
        AuthService authService = service();
        ExpenseUser user = activeUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret123", "encoded-password")).thenReturn(true);
        when(emailCodeService.createBindSession(user.getId())).thenReturn("bind-session-1");

        AuthLoginStartResponse response = authService.login(new LoginRequest("demo", "secret123"));

        assertThat(response.status()).isEqualTo("EMAIL_BIND_REQUIRED");
        assertThat(response.challengeId()).isEqualTo("bind-session-1");
        verify(refreshTokenMapper, never()).insert(any(RefreshToken.class));
    }

    @Test
    void loginRejectsDisabledUser() {
        AuthService authService = new AuthService(
                userMapper,
                refreshTokenMapper,
                authChallengeMapper,
                emailCodeService,
                userBootstrapService,
                passwordEncoder,
                jwtService,
                jwtProperties,
                CLOCK
        );
        ExpenseUser user = new ExpenseUser();
        user.setId(1001L);
        user.setUsername("demo");
        user.setPasswordHash("encoded-password");
        user.setStatus("DISABLED");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret123", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("demo", "secret123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("账号已被禁用");
    }

    @Test
    void refreshRevokesStoredTokenWithConditionalUpdateBeforeIssuingNewToken() {
        AuthService authService = service();
        RefreshToken stored = activeRefreshToken();
        ExpenseUser user = activeUser();
        when(refreshTokenMapper.selectOne(any())).thenReturn(stored);
        when(refreshTokenMapper.revokeIfActive(anyLong(), any(LocalDateTime.class))).thenReturn(1);
        when(userMapper.selectById(1001L)).thenReturn(user);
        when(jwtService.generateAccessToken(1001L, "demo")).thenReturn("next-access-token");
        when(jwtService.accessTokenSeconds()).thenReturn(1800L);
        when(jwtProperties.getRefreshTokenDays()).thenReturn(14L);

        TokenResponse response = authService.refresh(new com.example.expense.auth.dto.RefreshTokenRequest("refresh-token"));

        assertThat(response.accessToken()).isEqualTo("next-access-token");
        verify(refreshTokenMapper).revokeIfActive(10L, LocalDateTime.now(CLOCK));
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
    }

    @Test
    void refreshRejectsWhenTokenWasAlreadyRevokedByConcurrentRequest() {
        AuthService authService = service();
        when(refreshTokenMapper.selectOne(any())).thenReturn(activeRefreshToken());
        when(refreshTokenMapper.revokeIfActive(anyLong(), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> authService.refresh(new com.example.expense.auth.dto.RefreshTokenRequest("refresh-token")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("刷新凭证无效或已过期");
    }

    private AuthService service() {
        return new AuthService(
                userMapper,
                refreshTokenMapper,
                authChallengeMapper,
                emailCodeService,
                userBootstrapService,
                passwordEncoder,
                jwtService,
                jwtProperties,
                CLOCK
        );
    }

    private RefreshToken activeRefreshToken() {
        RefreshToken stored = new RefreshToken();
        stored.setId(10L);
        stored.setUserId(1001L);
        stored.setExpiresAt(LocalDateTime.now(CLOCK).plusDays(1));
        return stored;
    }

    private ExpenseUser activeUser() {
        ExpenseUser user = new ExpenseUser();
        user.setId(1001L);
        user.setUsername("demo");
        user.setPasswordHash("encoded-password");
        user.setStatus("ACTIVE");
        return user;
    }
}
