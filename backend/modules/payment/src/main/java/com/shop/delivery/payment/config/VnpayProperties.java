package com.shop.delivery.payment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * VNPay sandbox / production configuration.
 *
 * <p>{@code hashSecret} is masked in {@link #toString()} — DO NOT log this record directly,
 * but if you do, the secret will not leak.
 */
@ConfigurationProperties(prefix = "vnpay")
@Validated
public record VnpayProperties(
    @NotBlank String tmnCode,
    @NotBlank String hashSecret,
    @NotBlank String payUrl,
    @NotBlank String returnUrl,
    @NotBlank String ipnUrl,
    @Positive int timeoutMinutes
) {
    @Override
    public String toString() {
        return "VnpayProperties[tmnCode=" + tmnCode
            + ", hashSecret=***"
            + ", payUrl=" + payUrl
            + ", returnUrl=" + returnUrl
            + ", ipnUrl=" + ipnUrl
            + ", timeoutMinutes=" + timeoutMinutes + "]";
    }
}
