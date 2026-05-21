package com.shop.delivery.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-must-be-at-least-256-bits-long-padding-padding-padding";

    private JwtService service;

    @BeforeEach
    void setup() {
        service = new JwtService(SECRET, Duration.ofMinutes(15));
    }

    @Test
    void shouldIssueAndParseValidToken() {
        String token = service.issueAccessToken(42L, "admin@shop.local");
        Optional<JwtService.JwtClaims> parsed = service.tryParse(token);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().adminUserId()).isEqualTo(42L);
        assertThat(parsed.get().email()).isEqualTo("admin@shop.local");
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = service.issueAccessToken(42L, "admin@shop.local");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThat(service.tryParse(tampered)).isEmpty();
    }

    @Test
    void shouldRejectTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService(
            "different-secret-also-256-bits-long-padding-padding-padding-padding",
            Duration.ofMinutes(15));
        String token = other.issueAccessToken(42L, "admin@shop.local");
        assertThat(service.tryParse(token)).isEmpty();
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        JwtService shortLived = new JwtService(SECRET, Duration.ofMillis(100));
        String token = shortLived.issueAccessToken(42L, "admin@shop.local");
        Thread.sleep(200);
        assertThat(shortLived.tryParse(token)).isEmpty();
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThat(service.tryParse("not-a-jwt")).isEmpty();
        assertThat(service.tryParse(null)).isEmpty();
        assertThat(service.tryParse("")).isEmpty();
    }
}
