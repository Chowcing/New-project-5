package com.example.expense.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final Key signingKey;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(properties.getAccessTokenMinutes() * 60);
        // Access Token 只放最小身份信息，服务端仍用 userId 做数据权限过滤，避免客户端传 userId 越权。
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public UserPrincipal parseAccessToken(String token) {
        // JJWT 会同时校验签名和过期时间；解析失败统一交给过滤器忽略本次认证。
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        return new UserPrincipal(userId, username, false);
    }

    public long accessTokenSeconds() {
        return properties.getAccessTokenMinutes() * 60;
    }
}
