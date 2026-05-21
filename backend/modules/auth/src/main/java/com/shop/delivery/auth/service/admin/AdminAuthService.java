package com.shop.delivery.auth.service.admin;

import com.shop.delivery.auth.entity.AdminUser;
import com.shop.delivery.auth.entity.RefreshToken;
import com.shop.delivery.auth.repository.AdminUserRepository;
import com.shop.delivery.auth.repository.RefreshTokenRepository;
import com.shop.delivery.auth.service.JwtService;
import com.shop.delivery.auth.service.admin.command.LoginCommand;
import com.shop.delivery.shared.exception.AuthenticationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdminAuthService {

    private final AdminUserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final JwtService jwtService;
    private final PasswordEncoder encoder;
    private final Duration refreshTtl;

    public AdminAuthService(AdminUserRepository userRepo,
                            RefreshTokenRepository refreshRepo,
                            JwtService jwtService,
                            PasswordEncoder encoder,
                            @Value("${jwt.refresh-ttl:P7D}") Duration refreshTtl) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.jwtService = jwtService;
        this.encoder = encoder;
        this.refreshTtl = refreshTtl;
    }

    public record TokenPair(String accessToken, String refreshToken, Long adminUserId) {}

    @Transactional
    public TokenPair login(LoginCommand cmd) {
        AdminUser user = userRepo.findByEmailAndActiveTrue(cmd.email())
            .orElseThrow(() -> new AuthenticationException("INVALID_CREDENTIALS", "Email hoặc mật khẩu sai"));

        if (!encoder.matches(cmd.password(), user.getPasswordHash())) {
            throw new AuthenticationException("INVALID_CREDENTIALS", "Email hoặc mật khẩu sai");
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenPair refresh(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) {
            throw new AuthenticationException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ");
        }

        RefreshToken match = refreshRepo.findAll().stream()
            .filter(t -> !t.isRevoked())
            .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
            .filter(t -> encoder.matches(refreshTokenPlain, t.getTokenHash()))
            .findFirst()
            .orElseThrow(() -> new AuthenticationException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ"));

        AdminUser user = userRepo.findById(match.getAdminUserId())
            .filter(AdminUser::isActive)
            .orElseThrow(() -> new AuthenticationException("USER_INACTIVE", "Tài khoản đã bị khóa"));

        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail());
        return new TokenPair(accessToken, refreshTokenPlain, user.getId());
    }

    @Transactional
    public void logout(String refreshTokenPlain) {
        if (refreshTokenPlain == null) return;
        refreshRepo.findAll().stream()
            .filter(t -> !t.isRevoked())
            .filter(t -> encoder.matches(refreshTokenPlain, t.getTokenHash()))
            .findFirst()
            .ifPresent(t -> refreshRepo.revokeById(t.getId()));
    }

    private TokenPair issueTokens(AdminUser user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail());
        String refreshTokenPlain = UUID.randomUUID().toString();
        String refreshTokenHash = encoder.encode(refreshTokenPlain);

        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setAdminUserId(user.getId());
        rt.setTokenHash(refreshTokenHash);
        rt.setExpiresAt(Instant.now().plus(refreshTtl));
        refreshRepo.save(rt);

        return new TokenPair(accessToken, refreshTokenPlain, user.getId());
    }
}
