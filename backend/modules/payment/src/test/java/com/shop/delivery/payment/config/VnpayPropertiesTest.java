package com.shop.delivery.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayPropertiesTest {

    @Test
    void shouldBindAllFields() {
        Map<String, Object> raw = Map.of(
            "vnpay.tmn-code",      "TEST01",
            "vnpay.hash-secret",   "SECRET123",
            "vnpay.pay-url",       "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "vnpay.return-url",    "http://localhost:8080/api/payment/vnpay/return",
            "vnpay.ipn-url",       "http://localhost:8080/api/payment/vnpay/ipn",
            "vnpay.timeout-minutes", 15
        );
        ConfigurationPropertySource src = new MapConfigurationPropertySource(raw);
        VnpayProperties props = new Binder(src).bind("vnpay", VnpayProperties.class).get();

        assertThat(props.tmnCode()).isEqualTo("TEST01");
        assertThat(props.hashSecret()).isEqualTo("SECRET123");
        assertThat(props.payUrl()).isEqualTo("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        assertThat(props.returnUrl()).isEqualTo("http://localhost:8080/api/payment/vnpay/return");
        assertThat(props.ipnUrl()).isEqualTo("http://localhost:8080/api/payment/vnpay/ipn");
        assertThat(props.timeoutMinutes()).isEqualTo(15);
    }

    @Test
    void toStringDoesNotLeakHashSecret() {
        VnpayProperties props = new VnpayProperties(
            "TEST01", "SUPERSECRET", "u1", "u2", "u3", 15
        );
        assertThat(props.toString())
            .doesNotContain("SUPERSECRET")
            .contains("hashSecret=***");
    }
}
