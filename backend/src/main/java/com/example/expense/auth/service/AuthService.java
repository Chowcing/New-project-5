package com.example.expense.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.expense.auth.dto.AuthLoginStartResponse;
import com.example.expense.auth.dto.BindEmailCodeRequest;
import com.example.expense.auth.dto.BindEmailVerifyRequest;
import com.example.expense.auth.dto.EmailCodeRequest;
import com.example.expense.auth.dto.LoginCodeRequest;
import com.example.expense.auth.dto.LoginRequest;
import com.example.expense.auth.dto.LoginVerifyRequest;
import com.example.expense.auth.dto.RefreshTokenRequest;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    @SuppressWarnings("unused")
    private final AuthChallengeMapper authChallengeMapper;
    private final EmailCodeService emailCodeService;
    private final UserBootstrapService userBootstrapService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            AuthChallengeMapper authChallengeMapper,
            EmailCodeService emailCodeService,
            UserBootstrapService userBootstrapService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.authChallengeMapper = authChallengeMapper;
        this.emailCodeService = emailCodeService;
        this.userBootstrapService = userBootstrapService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public String sendRegisterEmailCode(EmailCodeRequest request) {
        if (emailExists(request.email())) {
            throw new IllegalArgumentException("邮箱已被绑定");
        }
        return emailCodeService.createAndSend("REGISTER", null, request.email());
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        ExpenseUser existing = userMapper.selectOne(new LambdaQueryWrapper<ExpenseUser>()
                .eq(ExpenseUser::getUsername, request.username()));
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (emailExists(request.email())) {
            throw new IllegalArgumentException("邮箱已被绑定");
        }
        emailCodeService.consumeLatest("REGISTER", request.email(), request.emailCode());

        ExpenseUser user = new ExpenseUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setEmail(normalizeEmail(request.email()));
        user.setEmailVerifiedAt(LocalDateTime.now(clock));
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        userBootstrapService.bootstrapDefaultData(user.getId());
        TokenResponse tokenResponse = issueTokens(user);
        log.info("用户注册成功 userId={}", user.getId());
        return tokenResponse;
    }

    public AuthLoginStartResponse login(LoginRequest request) {
        ExpenseUser user = userMapper.selectOne(new LambdaQueryWrapper<ExpenseUser>()
                .eq(ExpenseUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("登录失败");
            throw new BadCredentialsException("用户名或密码错误");
        }
        if ("DISABLED".equals(user.getStatus())) {
            log.warn("禁用用户登录失败 userId={}", user.getId());
            throw new BadCredentialsException("账号已被禁用");
        }
        if (user.getEmail() == null || user.getEmailVerifiedAt() == null) {
            String challengeId = emailCodeService.createBindSession(user.getId());
            log.info("登录需要绑定邮箱 userId={}", user.getId());
            return new AuthLoginStartResponse("EMAIL_BIND_REQUIRED", challengeId, null);
        }
        String challengeId = emailCodeService.createAndSend("LOGIN", user.getId(), user.getEmail());
        log.info("登录需要邮箱验证码 userId={}", user.getId());
        return new AuthLoginStartResponse("MFA_REQUIRED", challengeId, maskEmail(user.getEmail()));
    }

    @Transactional
    public TokenResponse verifyLogin(LoginVerifyRequest request) {
        AuthChallenge challenge = emailCodeService.consume("LOGIN", request.challengeId(), request.code());
        ExpenseUser user = activeUser(challenge.getUserId());
        TokenResponse tokenResponse = issueTokens(user);
        log.info("邮箱验证码登录成功 userId={}", user.getId());
        return tokenResponse;
    }

    public String resendLoginCode(LoginCodeRequest request) {
        AuthChallenge challenge = emailCodeService.requireActive("LOGIN", request.challengeId());
        ExpenseUser user = activeUser(challenge.getUserId());
        String challengeId = emailCodeService.createAndSend("LOGIN", user.getId(), challenge.getEmail());
        emailCodeService.retire("LOGIN", request.challengeId());
        log.info("重新发送登录邮箱验证码 userId={}", user.getId());
        return challengeId;
    }

    @Transactional
    public String sendBindEmailCode(BindEmailCodeRequest request) {
        if (emailExists(request.email())) {
            throw new IllegalArgumentException("邮箱已被绑定");
        }
        return emailCodeService.createBindCode(request.challengeId(), request.email());
    }

    @Transactional
    public TokenResponse verifyBindEmail(BindEmailVerifyRequest request) {
        AuthChallenge challenge = emailCodeService.consume("BIND_EMAIL", request.challengeId(), request.code());
        ExpenseUser user = activeUser(challenge.getUserId());
        user.setEmail(normalizeEmail(challenge.getEmail()));
        user.setEmailVerifiedAt(LocalDateTime.now(clock));
        userMapper.updateById(user);
        TokenResponse tokenResponse = issueTokens(user);
        log.info("用户绑定邮箱成功 userId={}", user.getId());
        return tokenResponse;
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.refreshToken());
        RefreshToken stored = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, tokenHash));
        LocalDateTime now = LocalDateTime.now(clock);
        if (stored == null || stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(now)) {
            log.warn("刷新 token 失败");
            throw new BadCredentialsException("刷新凭证无效或已过期");
        }
        int revoked = refreshTokenMapper.revokeIfActive(stored.getId(), now);
        if (revoked != 1) {
            log.warn("刷新 token 竞争失败 userId={}", stored.getUserId());
            throw new BadCredentialsException("刷新凭证无效或已过期");
        }

        ExpenseUser user = activeUser(stored.getUserId());
        TokenResponse tokenResponse = issueTokens(user);
        log.info("刷新 token 成功 userId={}", user.getId());
        return tokenResponse;
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, hashToken(request.refreshToken())));
        if (stored != null && stored.getRevokedAt() == null) {
            refreshTokenMapper.revokeIfActive(stored.getId(), LocalDateTime.now(clock));
            log.info("退出登录 userId={}", stored.getUserId());
        }
    }

    public void revokeTokens(Long userId) {
        refreshTokenMapper.update(null, new LambdaUpdateWrapper<RefreshToken>()
                .eq(RefreshToken::getUserId, userId)
                .isNull(RefreshToken::getRevokedAt)
                .set(RefreshToken::getRevokedAt, LocalDateTime.now(clock)));
    }

    private TokenResponse issueTokens(ExpenseUser user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = newRefreshToken();

        RefreshToken stored = new RefreshToken();
        stored.setUserId(user.getId());
        stored.setTokenHash(hashToken(refreshToken));
        stored.setExpiresAt(LocalDateTime.now(clock).plusDays(jwtProperties.getRefreshTokenDays()));
        refreshTokenMapper.insert(stored);

        return new TokenResponse(accessToken, refreshToken, jwtService.accessTokenSeconds());
    }

    private ExpenseUser activeUser(Long userId) {
        ExpenseUser user = userMapper.selectById(userId);
        if (user == null || "DISABLED".equals(user.getStatus())) {
            throw new BadCredentialsException("账号已被禁用");
        }
        return user;
    }

    private boolean emailExists(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<ExpenseUser>()
                .eq(ExpenseUser::getEmail, normalizeEmail(email))) != null;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
