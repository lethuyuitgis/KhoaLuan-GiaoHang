package com.shop.delivery.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramInitDataVerifierTest {

    private static final String TEST_BOT_TOKEN = "1111:TEST_TOKEN_FOR_HMAC_TEST_ONLY";

    private TelegramInitDataVerifier verifier;

    @BeforeEach
    void setup() {
        verifier = new TelegramInitDataVerifier(TEST_BOT_TOKEN);
    }

    @Test
    void shouldAcceptValidInitData() {
        Map<String, String> data = new TreeMap<>();
        data.put("auth_date", "1700000000");
        data.put("query_id", "AAH123456");
        data.put("user", "{\"id\":1234567,\"first_name\":\"Test\",\"last_name\":\"User\",\"username\":\"testuser\",\"language_code\":\"vi\"}");

        String initData = buildSignedInitData(data, TEST_BOT_TOKEN);

        TelegramInitDataVerifier.VerifiedInitData result = verifier.verify(initData);

        assertThat(result.userId()).isEqualTo(1234567L);
        assertThat(result.firstName()).isEqualTo("Test");
        assertThat(result.username()).isEqualTo("testuser");
        assertThat(result.languageCode()).isEqualTo("vi");
    }

    @Test
    void shouldRejectTamperedInitData() {
        Map<String, String> data = new TreeMap<>();
        data.put("auth_date", "1700000000");
        data.put("user", "{\"id\":1234567,\"first_name\":\"Test\"}");
        String validInitData = buildSignedInitData(data, TEST_BOT_TOKEN);

        // Tamper: change user ID
        String tampered = validInitData.replace("1234567", "9999999");

        assertThat(verifier.tryVerify(tampered)).isEmpty();
    }

    @Test
    void shouldRejectMissingHashField() {
        String initData = "auth_date=1700000000&user=%7B%22id%22%3A1%7D";
        assertThat(verifier.tryVerify(initData)).isEmpty();
    }

    @Test
    void shouldRejectMalformedUserJson() {
        Map<String, String> data = new TreeMap<>();
        data.put("auth_date", "1700000000");
        data.put("user", "not-json");
        String initData = buildSignedInitData(data, TEST_BOT_TOKEN);

        assertThat(verifier.tryVerify(initData)).isEmpty();
    }

    @Test
    void shouldRejectWhenSignedWithDifferentToken() {
        Map<String, String> data = new TreeMap<>();
        data.put("auth_date", "1700000000");
        data.put("user", "{\"id\":1234567,\"first_name\":\"Test\"}");
        String initData = buildSignedInitData(data, "DIFFERENT_TOKEN_NOT_TEST");

        assertThat(verifier.tryVerify(initData)).isEmpty();
    }

    /**
     * Helper that builds a signed initData string using INDEPENDENT
     * HMAC implementation (not the verifier under test).
     */
    static String buildSignedInitData(Map<String, String> sortedData, String botToken) {
        StringBuilder checkString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : sortedData.entrySet()) {
            if (!first) checkString.append('\n');
            checkString.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
            botToken.getBytes(StandardCharsets.UTF_8));
        byte[] hashBytes = hmacSha256(secretKey, checkString.toString().getBytes(StandardCharsets.UTF_8));
        String hash = toHex(hashBytes);

        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> e : sortedData.entrySet()) {
            if (qs.length() > 0) qs.append('&');
            qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        qs.append("&hash=").append(hash);
        return qs.toString();
    }

    static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
