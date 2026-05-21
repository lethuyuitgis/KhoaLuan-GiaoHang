package com.shop.delivery.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Verify Telegram Mini App initData per
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
 *
 *   secret_key  = HMAC_SHA256(key="WebAppData", msg=bot_token)
 *   hash_check  = HMAC_SHA256(key=secret_key, msg=data_check_string)
 *   data_check_string = "key1=value1\nkey2=value2\n..." (alphabetically sorted, hash field excluded)
 */
@Service
public class TelegramInitDataVerifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitDataVerifier.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String botToken;

    public TelegramInitDataVerifier(@Value("${bot.token}") String botToken) {
        this.botToken = botToken;
    }

    public record VerifiedInitData(
        Long userId,
        String firstName,
        String lastName,
        String username,
        String languageCode,
        String rawUserJson
    ) {
    }

    public VerifiedInitData verify(String initData) {
        return tryVerify(initData)
            .orElseThrow(() -> new IllegalArgumentException("Invalid Telegram initData"));
    }

    public Optional<VerifiedInitData> tryVerify(String initData) {
        if (initData == null || initData.isBlank()) return Optional.empty();

        TreeMap<String, String> params = new TreeMap<>();
        String receivedHash = null;
        for (String pair : initData.split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            String key = pair.substring(0, idx);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            if ("hash".equals(key)) {
                receivedHash = value;
            } else {
                params.put(key, value);
            }
        }

        if (receivedHash == null || params.isEmpty()) {
            log.debug("initData missing hash or empty");
            return Optional.empty();
        }

        StringBuilder checkString = new StringBuilder();
        boolean first = true;
        for (var e : params.entrySet()) {
            if (!first) checkString.append('\n');
            checkString.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }

        byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
            botToken.getBytes(StandardCharsets.UTF_8));

        byte[] expectedHashBytes = hmacSha256(secretKey,
            checkString.toString().getBytes(StandardCharsets.UTF_8));
        String expectedHash = toHex(expectedHashBytes);

        if (!constantTimeEquals(receivedHash, expectedHash)) {
            log.debug("initData hash mismatch");
            return Optional.empty();
        }

        String userJson = params.get("user");
        if (userJson == null) {
            log.debug("initData missing user field");
            return Optional.empty();
        }

        try {
            JsonNode userNode = JSON.readTree(userJson);
            long userId = userNode.path("id").asLong(0);
            if (userId == 0) {
                log.debug("initData user.id is zero or missing");
                return Optional.empty();
            }
            return Optional.of(new VerifiedInitData(
                userId,
                textOrNull(userNode, "first_name"),
                textOrNull(userNode, "last_name"),
                textOrNull(userNode, "username"),
                textOrNull(userNode, "language_code"),
                userJson
            ));
        } catch (Exception ex) {
            log.debug("initData user JSON parse failed", ex);
            return Optional.empty();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) return null;
        return child.asText();
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 failure", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }
}
