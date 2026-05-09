package com.example.expense.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.auth.dto.LoginRequest;
import com.example.expense.auth.dto.RefreshTokenRequest;
import com.example.expense.auth.dto.RegisterRequest;
import com.example.expense.auth.dto.TokenResponse;
import com.example.expense.auth.entity.RefreshToken;
import com.example.expense.auth.mapper.RefreshTokenMapper;
import com.example.expense.category.entity.Category;
import com.example.expense.category.mapper.CategoryMapper;
import com.example.expense.common.security.JwtProperties;
import com.example.expense.common.security.JwtService;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
    private final CategoryMapper categoryMapper;
    private final PaymentMethodService paymentMethodService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            CategoryMapper categoryMapper,
            PaymentMethodService paymentMethodService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.categoryMapper = categoryMapper;
        this.paymentMethodService = paymentMethodService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        ExpenseUser existing = userMapper.selectOne(new LambdaQueryWrapper<ExpenseUser>()
                .eq(ExpenseUser::getUsername, request.username()));
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        ExpenseUser user = new ExpenseUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        userMapper.insert(user);
        createDefaultData(user.getId());
        TokenResponse tokenResponse = issueTokens(user);
        log.info("用户注册成功 userId={}", user.getId());
        return tokenResponse;
    }

    public TokenResponse login(LoginRequest request) {
        ExpenseUser user = userMapper.selectOne(new LambdaQueryWrapper<ExpenseUser>()
                .eq(ExpenseUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("登录失败");
            throw new BadCredentialsException("用户名或密码错误");
        }
        TokenResponse tokenResponse = issueTokens(user);
        log.info("登录成功 userId={}", user.getId());
        return tokenResponse;
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.refreshToken());
        RefreshToken stored = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, tokenHash));
        if (stored == null || stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("刷新 token 失败");
            throw new BadCredentialsException("刷新凭证无效或已过期");
        }

        // Refresh Token 每次使用后立即吊销并签发新值，降低旧 token 泄露后的可用窗口。
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenMapper.updateById(stored);

        ExpenseUser user = userMapper.selectById(stored.getUserId());
        TokenResponse tokenResponse = issueTokens(user);
        log.info("刷新 token 成功 userId={}", user.getId());
        return tokenResponse;
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, hashToken(request.refreshToken())));
        if (stored != null && stored.getRevokedAt() == null) {
            stored.setRevokedAt(LocalDateTime.now());
            refreshTokenMapper.updateById(stored);
            log.info("退出登录 userId={}", stored.getUserId());
        }
    }

    private TokenResponse issueTokens(ExpenseUser user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = newRefreshToken();

        RefreshToken stored = new RefreshToken();
        stored.setUserId(user.getId());
        stored.setTokenHash(hashToken(refreshToken));
        stored.setExpiresAt(LocalDateTime.now().plusDays(jwtProperties.getRefreshTokenDays()));
        refreshTokenMapper.insert(stored);

        return new TokenResponse(accessToken, refreshToken, jwtService.accessTokenSeconds());
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

    private void createDefaultData(Long userId) {
        createCategory(userId, "餐饮", "EXPENSE", "shop-o", "#ee6a5c", 10);
        createCategory(userId, "交通", "EXPENSE", "logistics", "#4d8cff", 20);
        createCategory(userId, "购物", "EXPENSE", "cart-o", "#f0a23a", 30);
        createCategory(userId, "日用", "EXPENSE", "bag-o", "#2f7d68", 40);
        createCategory(userId, "住房", "EXPENSE", "home-o", "#8b5cf6", 50);
        createCategory(userId, "水电燃气", "EXPENSE", "fire-o", "#f59e0b", 60);
        createCategory(userId, "通讯", "EXPENSE", "phone-o", "#3b82f6", 70);
        createCategory(userId, "医疗", "EXPENSE", "shield-o", "#e25555", 80);
        createCategory(userId, "教育", "EXPENSE", "bookmark-o", "#64748b", 90);
        createCategory(userId, "娱乐", "EXPENSE", "music-o", "#d85f8a", 100);
        createCategory(userId, "旅行", "EXPENSE", "hotel-o", "#14b8a6", 110);
        createCategory(userId, "人情礼金", "EXPENSE", "gift-o", "#ec4899", 120);
        createCategory(userId, "其他支出", "EXPENSE", "records-o", "#64748b", 990);

        createCategory(userId, "工资", "INCOME", "paid", "#39a66a", 10);
        createCategory(userId, "奖金", "INCOME", "gold-coin-o", "#2f9b63", 20);
        createCategory(userId, "兼职", "INCOME", "manager-o", "#3b82f6", 30);
        createCategory(userId, "投资理财", "INCOME", "chart-trending-o", "#f59e0b", 40);
        createCategory(userId, "报销", "INCOME", "balance-list-o", "#8b5cf6", 50);
        createCategory(userId, "退款", "INCOME", "refund-o", "#2f7d68", 60);
        createCategory(userId, "其他收入", "INCOME", "cash-back-record", "#64748b", 990);

        paymentMethodService.createDefaults(userId);
    }

    private void createCategory(Long userId, String name, String type, String icon, String color, int sortOrder) {
        Category category = new Category();
        category.setUserId(userId);
        category.setName(name);
        category.setType(type);
        category.setIcon(icon);
        category.setColor(color);
        category.setSortOrder(sortOrder);
        categoryMapper.insert(category);
    }

}
