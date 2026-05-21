package com.shop.delivery.auth.api;

import com.shop.delivery.auth.api.dto.MeResponse;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.RoleResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final RoleResolver roleResolver;

    public MeController(RoleResolver roleResolver) {
        this.roleResolver = roleResolver;
    }

    @GetMapping
    public MeResponse me(@CurrentUser TelegramUser user) {
        return new MeResponse(
            user.getId(),
            user.getUsername(),
            user.getFirstName(),
            user.getLastName(),
            user.getLanguageCode(),
            roleResolver.activeRolesOf(user.getId())
        );
    }
}
