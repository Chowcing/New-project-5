package com.example.expense.auth.service;

import com.example.expense.auth.config.MailCodeProperties;
import com.example.expense.auth.entity.AuthChallenge;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class EmailCodeService {
    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);
    private static final int CODE_TTL_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_SECONDS = 60;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MailCodeProperties mailProperties;
    private final Clock clock;
    private final RedisTemplate<String, AuthChallenge> challengeRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailCodeService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            MailCodeProperties mailProperties,
            Clock clock,
            RedisTemplate<String, AuthChallenge> challengeRedisTemplate,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailProperties = mailProperties;
        this.clock = clock;
        this.challengeRedisTemplate = challengeRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String createAndSend(String purpose, Long userId, String email) {
        try {
            String normalizedEmail = normalizeEmail(email);
            LocalDateTime now = LocalDateTime.now(clock);
            String latestChallengeId = stringRedisTemplate.opsForValue().get(latestKey(purpose, normalizedEmail));
            AuthChallenge latest = latestChallengeId == null ? null : getChallenge(latestChallengeId);
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
            challengeRedisTemplate.opsForValue().set(challengeKey(challenge.getChallengeId()), challenge, Duration.ofMinutes(CODE_TTL_MINUTES));
            stringRedisTemplate.opsForValue().set(latestKey(purpose, normalizedEmail), challenge.getChallengeId(), Duration.ofMinutes(CODE_TTL_MINUTES));
            try {
                sendCode(normalizedEmail, code);
            } catch (RuntimeException ex) {
                deleteChallenge(challenge);
                throw ex;
            }
            return challenge.getChallengeId();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new AuthTemporaryUnavailableException(ex);
        }
    }

    public String createBindSession(Long userId) {
        try {
            AuthChallenge challenge = new AuthChallenge();
            challenge.setChallengeId(UUID.randomUUID().toString());
            challenge.setUserId(userId);
            challenge.setPurpose("BIND_EMAIL_SESSION");
            challenge.setExpiresAt(LocalDateTime.now(clock).plusMinutes(CODE_TTL_MINUTES));
            challenge.setAttemptCount(0);
            challengeRedisTemplate.opsForValue().set(challengeKey(challenge.getChallengeId()), challenge, Duration.ofMinutes(CODE_TTL_MINUTES));
            return challenge.getChallengeId();
        } catch (DataAccessException ex) {
            throw new AuthTemporaryUnavailableException(ex);
        }
    }

    public String createBindCode(String sessionChallengeId, String email) {
        AuthChallenge session = consume("BIND_EMAIL_SESSION", sessionChallengeId, null);
        return createAndSend("BIND_EMAIL", session.getUserId(), email);
    }

    public AuthChallenge consumeLatest(String purpose, String email, String code) {
        try {
            String normalizedEmail = normalizeEmail(email);
            String challengeId = stringRedisTemplate.opsForValue().get(latestKey(purpose, normalizedEmail));
            return consumeChallenge(challengeId == null ? null : getChallenge(challengeId), code);
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new AuthTemporaryUnavailableException(ex);
        }
    }

    public AuthChallenge consume(String purpose, String challengeId, String code) {
        try {
            AuthChallenge challenge = getChallenge(challengeId);
            if (challenge != null && !purpose.equals(challenge.getPurpose())) {
                challenge = null;
            }
            return code == null ? consumeSession(challenge) : consumeChallenge(challenge, code);
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new AuthTemporaryUnavailableException(ex);
        }
    }

    public AuthChallenge requireActive(String purpose, String challengeId) {
        try {
            AuthChallenge challenge = getChallenge(challengeId);
            if (challenge != null && !purpose.equals(challenge.getPurpose())) {
                challenge = null;
            }
            return requireActiveChallenge(challenge);
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new AuthTemporaryUnavailableException(ex);
        }
    }

    public void retire(String purpose, String challengeId) {
        try {
            AuthChallenge challenge = getChallenge(challengeId);
            if (challenge != null && purpose.equals(challenge.getPurpose())) {
                challengeRedisTemplate.delete(challengeKey(challenge.getChallengeId()));
            }
        } catch (DataAccessException ex) {
            throw new AuthTemporaryUnavailableException(ex);
        }
    }

    private AuthChallenge consumeSession(AuthChallenge challenge) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (challenge == null || challenge.getConsumedAt() != null || challenge.getExpiresAt().isBefore(now)) {
            throw new BadCredentialsException("验证流程已失效，请重新登录");
        }
        challenge.setConsumedAt(now);
        deleteChallenge(challenge);
        return challenge;
    }

    private AuthChallenge requireActiveChallenge(AuthChallenge challenge) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (challenge == null || challenge.getConsumedAt() != null || challenge.getExpiresAt().isBefore(now)) {
            throw new BadCredentialsException("验证码无效或已过期");
        }
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
            Duration ttl = Duration.between(now, challenge.getExpiresAt());
            challengeRedisTemplate.opsForValue().set(challengeKey(challenge.getChallengeId()), challenge,
                    ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(CODE_TTL_MINUTES) : ttl);
            throw new BadCredentialsException("验证码错误");
        }
        challenge.setConsumedAt(now);
        deleteChallenge(challenge);
        return challenge;
    }

    private AuthChallenge getChallenge(String challengeId) {
        return challengeId == null ? null : challengeRedisTemplate.opsForValue().get(challengeKey(challengeId));
    }

    private void deleteChallenge(AuthChallenge challenge) {
        challengeRedisTemplate.delete(challengeKey(challenge.getChallengeId()));
        if (challenge.getEmail() != null) {
            stringRedisTemplate.delete(latestKey(challenge.getPurpose(), challenge.getEmail()));
        }
    }

    private String challengeKey(String challengeId) {
        return "auth:challenge:id:" + challengeId;
    }

    private String latestKey(String purpose, String email) {
        return "auth:challenge:latest:" + purpose + ":" + email;
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
