package com.example.expense.auth.service;

import com.example.expense.common.security.JwtProperties;
import com.example.expense.common.security.JwtService;
import com.example.expense.common.security.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void generateAndParseAccessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-must-be-at-least-32-bytes");
        properties.setAccessTokenMinutes(30);
        properties.setRefreshTokenDays(14);

        JwtService service = new JwtService(properties, Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneOffset.UTC));
        String token = service.generateAccessToken(1001L, "demo");
        UserPrincipal principal = service.parseAccessToken(token);

        Assertions.assertEquals(1001L, principal.getUserId());
        Assertions.assertEquals("demo", principal.getUsername());
    }
}
