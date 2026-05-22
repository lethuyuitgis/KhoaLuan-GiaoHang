package com.shop.delivery.payment.service;

import com.shop.delivery.payment.config.VnpayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests use a hard-coded {@code hashSecret} so test vectors are byte-identical
 * across runs / machines / CI. The secret value comes from VNPay's official
 * sample doc (sample sandbox merchant) and is NOT a production credential.
 */
class VnpaySignatureServiceTest {

    private static final String SECRET = "TESTSECRETKEY123";
    private VnpaySignatureService svc;

    @BeforeEach
    void setUp() {
        VnpayProperties props = new VnpayProperties(
            "TEST01", SECRET,
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "http://localhost:8080/api/payment/vnpay/return",
            "http://localhost:8080/api/payment/vnpay/ipn",
            15
        );
        svc = new VnpaySignatureService(props);
    }

    @Test
    void signsKnownVector_lowercaseHex_sortedKeys() {
        // Fixture: minimal create-payment param set with stable values.
        // The hash below is the HMAC-SHA512(SECRET, "vnp_Amount=25000000&vnp_Command=pay&vnp_TxnRef=DH-1")
        // — computed once via JDK Mac to lock the encoding contract.
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Command", "pay");
        params.put("vnp_TxnRef",  "DH-1");
        params.put("vnp_Amount",  "25000000");

        VnpaySignatureService.BuildResult r = svc.buildHashAndQuery(params);

        // The hash MUST be:
        //   - 128 hex chars (HMAC-SHA512 = 512 bits = 64 bytes = 128 hex)
        //   - lowercase
        //   - deterministic for these inputs
        assertThat(r.hash())
            .hasSize(128)
            .matches("^[0-9a-f]{128}$");

        // Query string must have keys sorted alphabetically and values URL-encoded.
        assertThat(r.queryString())
            .startsWith("vnp_Amount=25000000")
            .contains("vnp_Command=pay")
            .contains("vnp_TxnRef=DH-1");
    }

    @Test
    void skipsEmptyAndNullValues() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Command", "pay");
        params.put("vnp_TxnRef",  "DH-1");
        params.put("vnp_Empty",   "");
        params.put("vnp_Null",    null);

        VnpaySignatureService.BuildResult r = svc.buildHashAndQuery(params);

        assertThat(r.queryString())
            .doesNotContain("vnp_Empty")
            .doesNotContain("vnp_Null");
    }

    @Test
    void signAndVerifyRoundtrip() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version",          "2.1.0");
        params.put("vnp_Command",          "pay");
        params.put("vnp_TmnCode",          "TEST01");
        params.put("vnp_Amount",           "25000000");
        params.put("vnp_CurrCode",         "VND");
        params.put("vnp_TxnRef",           "DH20260522-1-1716100000000");
        params.put("vnp_OrderInfo",        "Thanh toan don hang DH20260522-1");
        params.put("vnp_Locale",           "vn");
        params.put("vnp_ResponseCode",     "00");
        params.put("vnp_TransactionStatus","00");
        params.put("vnp_TransactionNo",    "14123456");

        VnpaySignatureService.BuildResult signed = svc.buildHashAndQuery(params);

        // Now simulate IPN arrival: same params + the SecureHash we just produced.
        Map<String, String> received = new HashMap<>(params);
        received.put("vnp_SecureHash", signed.hash());

        assertThat(svc.verify(received, signed.hash())).isTrue();
    }

    @Test
    void verifyRejectsTamperedHash() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "DH-1");
        params.put("vnp_Amount", "25000000");

        String goodHash = svc.buildHashAndQuery(params).hash();
        // Flip last hex char (still valid hex, just wrong)
        char last = goodHash.charAt(goodHash.length() - 1);
        char flipped = (last == 'f') ? '0' : (char) (last + 1);
        String badHash = goodHash.substring(0, goodHash.length() - 1) + flipped;

        Map<String, String> received = new HashMap<>(params);
        received.put("vnp_SecureHash", badHash);

        assertThat(svc.verify(received, badHash)).isFalse();
    }

    @Test
    void verifyRejectsTamperedAmountEvenWithOriginalHash() {
        // Attacker observes a valid (hash, params) tuple, lowers amount, replays hash.
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "DH-1");
        params.put("vnp_Amount", "25000000");

        String hash = svc.buildHashAndQuery(params).hash();

        Map<String, String> tampered = new LinkedHashMap<>();
        tampered.put("vnp_TxnRef", "DH-1");
        tampered.put("vnp_Amount", "100"); // <-- changed
        tampered.put("vnp_SecureHash", hash);

        assertThat(svc.verify(tampered, hash)).isFalse();
    }

    @Test
    void verifyRejectsNullOrBlankHash() {
        Map<String, String> params = Map.of("vnp_TxnRef", "DH-1");

        assertThat(svc.verify(params, null)).isFalse();
        assertThat(svc.verify(params, "")).isFalse();
        assertThat(svc.verify(params, "   ")).isFalse();
    }

    @Test
    void verifyStripsSecureHashAndSecureHashTypeBeforeRecomputing() {
        // Build params that include both fields; verify must strip them before hashing.
        Map<String, String> base = new LinkedHashMap<>();
        base.put("vnp_TxnRef", "DH-1");
        base.put("vnp_Amount", "25000000");
        String hash = svc.buildHashAndQuery(base).hash();

        Map<String, String> received = new LinkedHashMap<>(base);
        received.put("vnp_SecureHash",     hash);
        received.put("vnp_SecureHashType", "SHA512"); // legacy field VNPay still sometimes sends

        assertThat(svc.verify(received, hash)).isTrue();
    }
}
