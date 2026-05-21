package com.shop.delivery.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String ROLE_SHOP_OWNER = "SHOP_OWNER";

    private final SecretKey signingKey;
    private final Duration accessTtl;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-ttl:PT15M}") Duration accessTtl) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 256 bits (32 bytes)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = accessTtl;
    }

    public record JwtClaims(Long adminUserId, String email, String role) {}

    public String issueAccessToken(Long adminUserId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(adminUserId))
            .claim(CLAIM_EMAIL, email)
            .claim(CLAIM_ROLE, ROLE_SHOP_OWNER)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtl)))
            .signWith(signingKey)
            .compact();
    }

    public Optional<JwtClaims> tryParse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Optional.of(new JwtClaims(
                Long.parseLong(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_ROLE, String.class)
            ));
        } catch (Exception ex) {
            log.debug("JWT parse failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
