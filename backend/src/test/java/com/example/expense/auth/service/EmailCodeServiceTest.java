package com.example.expense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.example.expense.auth.config.MailCodeProperties;
import com.example.expense.auth.entity.AuthChallenge;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
class EmailCodeServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock
    private RedisTemplate<String, AuthChallenge> challengeRedisTemplate;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, AuthChallenge> challengeOps;
    @Mock
    private ValueOperations<String, String> stringOps;

    @Test
    void emailCodeServiceDoesNotKeepDatabaseChallengeDependencyAfterRedisMigration() {
        assertThat(List.of(EmailCodeService.class.getDeclaredFields()).stream()
                .map(Field::getType)
                .map(Class::getName)
                .toList()).noneMatch(typeName -> typeName.contains(".mapper."));
    }

    @Test
    void createAndSendStoresHashedChallengeInRedisAndLatestPointer() {
        EmailCodeService service = service();
        when(challengeRedisTemplate.opsForValue()).thenReturn(challengeOps);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringOps);
        when(stringOps.get("auth:challenge:latest:REGISTER:demo@example.com")).thenReturn(null);

        String challengeId = service.createAndSend("REGISTER", null, " Demo@Example.com ");

        ArgumentCaptor<AuthChallenge> challengeCaptor = ArgumentCaptor.forClass(AuthChallenge.class);
        verify(challengeOps).set(eq("auth:challenge:id:" + challengeId), challengeCaptor.capture(), eq(Duration.ofMinutes(10)));
        verify(stringOps).set(eq("auth:challenge:latest:REGISTER:demo@example.com"), eq(challengeId), eq(Duration.ofMinutes(10)));
        AuthChallenge stored = challengeCaptor.getValue();
        assertThat(stored.getEmail()).isEqualTo("demo@example.com");
        assertThat(stored.getPurpose()).isEqualTo("REGISTER");
        assertThat(stored.getCodeHash()).hasSize(64);
        verify(challengeOps).set(eq("auth:challenge:id:" + challengeId), any(AuthChallenge.class), eq(Duration.ofMinutes(10)));
    }

    @Test
    void createAndSendDeletesRedisKeysWhenEmailSendingFails() {
        EmailCodeService service = service();
        JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new IllegalStateException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        when(challengeRedisTemplate.opsForValue()).thenReturn(challengeOps);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringOps);
        when(stringOps.get("auth:challenge:latest:LOGIN:demo@example.com")).thenReturn(null);

        assertThatThrownBy(() -> service.createAndSend("LOGIN", 1001L, "demo@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("smtp down");

        ArgumentCaptor<AuthChallenge> challengeCaptor = ArgumentCaptor.forClass(AuthChallenge.class);
        verify(challengeOps).set(org.mockito.ArgumentMatchers.startsWith("auth:challenge:id:"),
                challengeCaptor.capture(), eq(Duration.ofMinutes(10)));
        AuthChallenge challenge = challengeCaptor.getValue();
        verify(challengeRedisTemplate).delete("auth:challenge:id:" + challenge.getChallengeId());
        verify(stringRedisTemplate).delete("auth:challenge:latest:LOGIN:demo@example.com");
    }

    @Test
    void createAndSendRejectsResendWithinSixtySeconds() {
        EmailCodeService service = service();
        AuthChallenge latest = challenge("REGISTER", "demo@example.com", "123456");
        latest.setSentAt(LocalDateTime.now(CLOCK).minusSeconds(30));
        when(challengeRedisTemplate.opsForValue()).thenReturn(challengeOps);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringOps);
        when(stringOps.get("auth:challenge:latest:REGISTER:demo@example.com")).thenReturn("challenge-1");
        when(challengeOps.get("auth:challenge:id:challenge-1")).thenReturn(latest);

        assertThatThrownBy(() -> service.createAndSend("REGISTER", null, "demo@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("验证码发送过于频繁，请稍后再试");
    }

    @Test
    void consumeDeletesChallengeAfterSuccessfulVerification() {
        EmailCodeService service = service();
        when(challengeRedisTemplate.opsForValue()).thenReturn(challengeOps);
        AuthChallenge challenge = challenge("LOGIN", "demo@example.com", "123456");
        when(challengeOps.get("auth:challenge:id:challenge-1")).thenReturn(challenge);

        AuthChallenge consumed = service.consume("LOGIN", "challenge-1", "123456");

        assertThat(consumed.getUserId()).isEqualTo(1001L);
        verify(challengeRedisTemplate).delete("auth:challenge:id:challenge-1");
        verify(stringRedisTemplate).delete("auth:challenge:latest:LOGIN:demo@example.com");
    }

    @Test
    void retireDeletesOnlyChallengeAndPreservesLatestPointer() {
        EmailCodeService service = service();
        when(challengeRedisTemplate.opsForValue()).thenReturn(challengeOps);
        AuthChallenge challenge = challenge("LOGIN", "demo@example.com", "123456");
        when(challengeOps.get("auth:challenge:id:challenge-1")).thenReturn(challenge);

        service.retire("LOGIN", "challenge-1");

        verify(challengeRedisTemplate).delete("auth:challenge:id:challenge-1");
        verify(stringRedisTemplate, never()).delete("auth:challenge:latest:LOGIN:demo@example.com");
    }

    @Test
    void consumeIncrementsAttemptsAndRejectsAfterFiveFailures() {
        EmailCodeService service = service();
        AuthChallenge challenge = challenge("LOGIN", "demo@example.com", "123456");
        challenge.setAttemptCount(4);
        when(challengeRedisTemplate.opsForValue()).thenReturn(challengeOps);
        when(challengeOps.get("auth:challenge:id:challenge-1")).thenReturn(challenge);

        assertThatThrownBy(() -> service.consume("LOGIN", "challenge-1", "000000"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("验证码错误");

        assertThat(challenge.getAttemptCount()).isEqualTo(5);
        verify(challengeOps).set("auth:challenge:id:challenge-1", challenge, Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.consume("LOGIN", "challenge-1", "000000"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("验证码错误次数过多，请重新获取");
    }

    @Test
    void redisFailureMakesAuthenticationTemporarilyUnavailable() {
        EmailCodeService service = service();
        when(stringRedisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> service.createAndSend("REGISTER", null, "demo@example.com"))
                .isInstanceOf(AuthTemporaryUnavailableException.class)
                .hasMessage("认证服务暂时不可用，请稍后再试");
    }

    private EmailCodeService service() {
        MailCodeProperties mailProperties = new MailCodeProperties();
        mailProperties.setFrom("noreply@example.com");
        mailProperties.setLocalLogEnabled(true);
        return new EmailCodeService(
                mailSenderProvider,
                mailProperties,
                CLOCK,
                challengeRedisTemplate,
                stringRedisTemplate
        );
    }

    private AuthChallenge challenge(String purpose, String email, String code) {
        AuthChallenge challenge = new AuthChallenge();
        challenge.setChallengeId("challenge-1");
        challenge.setUserId(1001L);
        challenge.setPurpose(purpose);
        challenge.setEmail(email);
        challenge.setCodeHash(hash(code));
        challenge.setAttemptCount(0);
        challenge.setExpiresAt(LocalDateTime.now(CLOCK).plusMinutes(10));
        challenge.setSentAt(LocalDateTime.now(CLOCK));
        return challenge;
    }

    private String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
