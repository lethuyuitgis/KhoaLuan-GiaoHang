package com.shop.delivery.auth.api.admin.dto;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    Long adminUserId,
    String email,
    long expiresInSeconds
) {}
