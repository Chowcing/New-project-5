package com.example.expense.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.auth.config.MailCodeProperties;
import com.example.expense.auth.entity.AuthChallenge;
import com.example.expense.auth.mapper.AuthChallengeMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailCodeService {
    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);
    private static final int CODE_TTL_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_SECONDS = 60;

    private final AuthChallengeMapper authChallengeMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MailCodeProperties mailProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailCodeService(
            AuthChallengeMapper authChallengeMapper,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            MailCodeProperties mailProperties,
            Clock clock
    ) {
        this.authChallengeMapper = authChallengeMapper;
        this.mailSenderProvider = mailSenderProvider;
        this.mailProperties = mailProperties;
        this.clock = clock;
    }

    @Transactional
    public String createAndSend(String purpose, Long userId, String email) {
        String normalizedEmail = normalizeEmail(email);
        LocalDateTime now = LocalDateTime.now(clock);
        AuthChallenge latest = authChallengeMapper.selectOne(new LambdaQueryWrapper<AuthChallenge>()
                .eq(AuthChallenge::getPurpose, purpose)
                .eq(AuthChallenge::getEmail, normalizedEmail)
                .isNull(AuthChallenge::getConsumedAt)
                .orderByDesc(AuthChallenge::getSentAt)
                .last("LIMIT 1"));
        if (latest != null && latest.getSentAt() != null && latest.getSentAt().plusSeconds(RESEND_SECONDS).isAfter(now)) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        }

        String code = newCode();
        AuthChallenge challenge = new AuthChallenge();
        challenge.setChallengeId(UUID.randomUUID().toString());
        challenge.setUserId(userId);
        challenge.setEmail(normalizedEmail);
        challenge.setPurpose(purpose);
        challenge.setCodeHash(hashCode(code));
        challenge.setExpiresAt(now.plusMinutes(CODE_TTL_MINUTES));
        challenge.setAttemptCount(0);
        challenge.setSentAt(now);
        authChallengeMapper.insert(challenge);
        sendCode(normalizedEmail, code);
        return challenge.getChallengeId();
    }

    @Transactional
    public String createBindSession(Long userId) {
        AuthChallenge challenge = new AuthChallenge();
        challenge.setChallengeId(UUID.randomUUID().toString());
        challenge.setUserId(userId);
        challenge.setPurpose("BIND_EMAIL_SESSION");
        challenge.setExpiresAt(LocalDateTime.now(clock).plusMinutes(CODE_TTL_MINUTES));
        challenge.setAttemptCount(0);
        authChallengeMapper.insert(challenge);
        return challenge.getChallengeId();
    }

    @Transactional
    public String createBindCode(String sessionChallengeId, String email) {
        AuthChallenge session = consume("BIND_EMAIL_SESSION", sessionChallengeId, null);
        return createAndSend("BIND_EMAIL", session.getUserId(), email);
    }

    @Transactional
    public AuthChallenge consumeLatest(String purpose, String email, String code) {
        AuthChallenge challenge = authChallengeMapper.selectOne(new LambdaQueryWrapper<AuthChallenge>()
                .eq(AuthChallenge::getPurpose, purpose)
                .eq(AuthChallenge::getEmail, normalizeEmail(email))
                .isNull(AuthChallenge::getConsumedAt)
                .orderByDesc(AuthChallenge::getSentAt)
                .last("LIMIT 1"));
        return consumeChallenge(challenge, code);
    }

    @Transactional
    public AuthChallenge consume(String purpose, String challengeId, String code) {
        AuthChallenge challenge = authChallengeMapper.selectOne(new LambdaQueryWrapper<AuthChallenge>()
                .eq(AuthChallenge::getPurpose, purpose)
                .eq(AuthChallenge::getChallengeId, challengeId));
        return code == null ? consumeSession(challenge) : consumeChallenge(challenge, code);
    }

    private AuthChallenge consumeSession(AuthChallenge challenge) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (challenge == null || challenge.getConsumedAt() != null || challenge.getExpiresAt().isBefore(now)) {
            throw new BadCredentialsException("验证流程已失效，请重新登录");
        }
        challenge.setConsumedAt(now);
        authChallengeMapper.updateById(challenge);
        return challenge;
    }

    private AuthChallenge consumeChallenge(AuthChallenge challenge, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (challenge == null || challenge.getConsumedAt() != null || challenge.getExpiresAt().isBefore(now)) {
            throw new BadCredentialsException("验证码无效或已过期");
        }
        if (Optional.ofNullable(challenge.getAttemptCount()).orElse(0) >= MAX_ATTEMPTS) {
            throw new BadCredentialsException("验证码错误次数过多，请重新获取");
        }
        if (!hashCode(code).equals(challenge.getCodeHash())) {
            challenge.setAttemptCount(Optional.ofNullable(challenge.getAttemptCount()).orElse(0) + 1);
            authChallengeMapper.updateById(challenge);
            throw new BadCredentialsException("验证码错误");
        }
        challenge.setConsumedAt(now);
        authChallengeMapper.updateById(challenge);
        return challenge;
    }

    private void sendCode(String email, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender != null) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getFrom());
            message.setTo(email);
            message.setSubject("消费记录系统验证码");
            message.setText("您的验证码是：" + code + "，10 分钟内有效。");
            mailSender.send(message);
            return;
        }
        if (mailProperties.isLocalLogEnabled()) {
            log.info("本地邮箱验证码 email={} code={}", email, code);
        } else {
            log.warn("邮件服务未配置，验证码无法发送 email={}", email);
            throw new IllegalStateException("邮件服务未配置");
        }
    }

    private String newCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        return email.trim().toLowerCase();
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
