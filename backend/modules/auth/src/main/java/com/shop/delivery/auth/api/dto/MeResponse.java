package com.shop.delivery.auth.api.dto;

import com.shop.delivery.auth.domain.Role;

import java.util.Set;

public record MeResponse(
    Long id,
    String username,
    String firstName,
    String lastName,
    String languageCode,
    Set<Role> roles
) {
}
