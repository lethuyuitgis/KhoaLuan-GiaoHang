package com.shop.delivery.payment.service;

import com.shop.delivery.payment.config.VnpayProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * HMAC-SHA512 signer + verifier for VNPay v2.1.0.
 *
 * <p><strong>Port of {@code vnpay_jsp.zip → Config.java + ajaxServlet.java}</strong>.
 * The encoding rules below are not negotiable — change a single byte and
 * signature verification fails for every transaction:
 *
 * <ul>
 *   <li>Skip params whose value is {@code null} or empty string.</li>
 *   <li>Sort param names ascending (case-sensitive — VNPay's prefix is always
 *       {@code vnp_*} so case doesn't matter in practice, but {@code Collections.sort}
 *       is deterministic).</li>
 *   <li>URL-encode both keys AND values with {@code US-ASCII} (VNPay's sample;
 *       all {@code vnp_*} keys are pure ASCII, so {@code US-ASCII} ≡ {@code UTF-8} for keys;
 *       values are already diacritic-stripped per spec §10.1 "không dấu").</li>
 *   <li>Join with {@code &}, no trailing separator.</li>
 *   <li>HMAC-SHA512 using the secret as UTF-8 bytes; result is lowercase hex.</li>
 *   <li>Compare with {@link MessageDigest#isEqual(byte[], byte[])} (constant-time).</li>
 * </ul>
 *
 * <p>See {@link #verify} for the matching decode contract on incoming requests.
 */
@Service
public class VnpaySignatureService {

    private final VnpayProperties props;

    public VnpaySignatureService(VnpayProperties props) {
        this.props = props;
    }

    /**
     * Build the data-to-be-hashed AND the URL-safe query suffix in one pass.
     *
     * @return {@link BuildResult} carrying both the lowercase-hex HMAC-SHA512
     *         signature and the query string to append after {@code ?}
     *         (caller must also append {@code &vnp_SecureHash=<hash>}).
     */
    public BuildResult buildHashAndQuery(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            fieldNames.add(e.getKey());
        }
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String name = itr.next();
            String value = params.get(name);
            String encName  = URLEncoder.encode(name,  StandardCharsets.US_ASCII);
            String encValue = URLEncoder.encode(value, StandardCharsets.US_ASCII);
            // hashData uses raw name + encoded value (matches ajaxServlet.java exactly)
            hashData.append(name).append('=').append(encValue);
            // query suffix uses encoded name + encoded value (browser-safe)
            query.append(encName).append('=').append(encValue);
            if (itr.hasNext()) {
                hashData.append('&');
                query.append('&');
            }
        }
        String hash = hmacSHA512(props.hashSecret(), hashData.toString());
        return new BuildResult(hash, query.toString());
    }

    /**
     * Verify a {@code vnp_SecureHash} against the received params. Strips both
     * {@code vnp_SecureHash} and {@code vnp_SecureHashType} before recomputing
     * (matches {@code vnpay_ipn.jsp}'s {@code hashAllFields} behaviour).
     *
     * <p>Returns {@code false} on any of: null/blank received hash, signature
     * mismatch, or any internal encoding error.
     */
    public boolean verify(Map<String, String> receivedParams, String receivedHash) {
        if (receivedHash == null || receivedHash.isBlank()) return false;

        Map<String, String> work = new HashMap<>(receivedParams);
        work.remove("vnp_SecureHash");
        work.remove("vnp_SecureHashType");

        // Re-build hashData with same rules as buildHashAndQuery (encoded value, raw name,
        // sorted, skip empty).
        List<String> fieldNames = new ArrayList<>();
        for (Map.Entry<String, String> e : work.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            fieldNames.add(e.getKey());
        }
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String name = itr.next();
            String encValue = URLEncoder.encode(work.get(name), StandardCharsets.US_ASCII);
            hashData.append(name).append('=').append(encValue);
            if (itr.hasNext()) hashData.append('&');
        }

        String expected = hmacSHA512(props.hashSecret(), hashData.toString());
        // Constant-time compare — V6 ASVS L1 requires this for any crypto MAC check.
        // Locale.ROOT on toLowerCase guards against the Turkish-I locale issue (defensive — hex
        // chars are unaffected but it's a one-line cheap insurance against future input).
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            receivedHash.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII)
        );
    }

    static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (GeneralSecurityException e) {
            // Should never happen — HmacSHA512 is mandatory in every JDK.
            throw new IllegalStateException("HmacSHA512 unavailable", e);
        }
    }

    /**
     * Result of {@link #buildHashAndQuery}.
     *
     * @param hash         lowercase hex HMAC-SHA512 (128 chars)
     * @param queryString  URL-encoded query suffix WITHOUT the leading {@code ?}
     *                     and WITHOUT {@code vnp_SecureHash} appended
     */
    public record BuildResult(String hash, String queryString) {}
}
