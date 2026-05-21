package com.shop.delivery.auth.service.admin;

import com.shop.delivery.auth.entity.AdminUser;
import com.shop.delivery.auth.entity.RefreshToken;
import com.shop.delivery.auth.repository.AdminUserRepository;
import com.shop.delivery.auth.repository.RefreshTokenRepository;
import com.shop.delivery.auth.service.JwtService;
import com.shop.delivery.auth.service.admin.command.LoginCommand;
import com.shop.delivery.shared.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock AdminUserRepository userRepo;
    @Mock RefreshTokenRepository refreshRepo;

    JwtService jwtService;
    PasswordEncoder encoder;
    AdminAuthService service;

    @BeforeEach
    void setup() {
        jwtService = new JwtService(
            "test-secret-must-be-at-least-256-bits-long-padding-padding-padding",
            Duration.ofMinutes(15));
        encoder = new BCryptPasswordEncoder(10);
        service = new AdminAuthService(userRepo, refreshRepo, jwtService, encoder, Duration.ofDays(7));
    }

    @Test
    void loginWithCorrectPasswordShouldReturnTokens() {
        AdminUser u = new AdminUser();
        u.setId(1L);
        u.setEmail("admin@shop.local");
        u.setPasswordHash(encoder.encode("admin123"));
        u.setActive(true);

        when(userRepo.findByEmailAndActiveTrue("admin@shop.local")).thenReturn(Optional.of(u));
        when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminAuthService.TokenPair tokens = service.login(new LoginCommand("admin@shop.local", "admin123"));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.refreshToken()).hasSize(36);
        assertThat(tokens.adminUserId()).isEqualTo(1L);
    }

    @Test
    void loginWithWrongPasswordShouldThrow() {
        AdminUser u = new AdminUser();
        u.setEmail("admin@shop.local");
        u.setPasswordHash(encoder.encode("admin123"));
        u.setActive(true);
        when(userRepo.findByEmailAndActiveTrue("admin@shop.local")).thenReturn(Optional.of(u));

        assertThatThrownBy(() ->
            service.login(new LoginCommand("admin@shop.local", "WRONG"))
        ).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void loginWithUnknownEmailShouldThrow() {
        when(userRepo.findByEmailAndActiveTrue(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.login(new LoginCommand("ghost@shop.local", "admin123"))
        ).isInstanceOf(AuthenticationException.class);
    }
}
