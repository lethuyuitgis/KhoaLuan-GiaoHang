package com.shop.delivery.auth.api.admin;

import com.shop.delivery.auth.api.admin.dto.LoginRequest;
import com.shop.delivery.auth.api.admin.dto.RefreshRequest;
import com.shop.delivery.auth.api.admin.dto.TokenResponse;
import com.shop.delivery.auth.repository.AdminUserRepository;
import com.shop.delivery.auth.service.admin.AdminAuthService;
import com.shop.delivery.auth.service.admin.command.LoginCommand;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService authService;
    private final AdminUserRepository userRepo;
    private final long accessTtlSeconds;

    public AdminAuthController(AdminAuthService authService,
                               AdminUserRepository userRepo,
                               @Value("${jwt.access-ttl:PT15M}") Duration accessTtl) {
        this.authService = authService;
        this.userRepo = userRepo;
        this.accessTtlSeconds = accessTtl.toSeconds();
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        AdminAuthService.TokenPair tokens = authService.login(new LoginCommand(req.email(), req.password()));
        return new TokenResponse(
            tokens.accessToken(), tokens.refreshToken(),
            tokens.adminUserId(), req.email(), accessTtlSeconds
        );
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        AdminAuthService.TokenPair tokens = authService.refresh(req.refreshToken());
        String email = userRepo.findById(tokens.adminUserId())
            .map(u -> u.getEmail())
            .orElse("");
        return new TokenResponse(
            tokens.accessToken(), tokens.refreshToken(),
            tokens.adminUserId(), email, accessTtlSeconds
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest req) {
        if (req != null && req.refreshToken() != null) {
            authService.logout(req.refreshToken());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
