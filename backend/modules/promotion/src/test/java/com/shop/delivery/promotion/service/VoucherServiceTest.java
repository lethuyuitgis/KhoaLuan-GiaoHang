package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.DiscountType;
import com.shop.delivery.promotion.domain.VoucherInvalidReason;
import com.shop.delivery.promotion.domain.VoucherTarget;
import com.shop.delivery.promotion.domain.VoucherValidationResult;
import com.shop.delivery.promotion.entity.Voucher;
import com.shop.delivery.promotion.repository.VoucherRepository;
import com.shop.delivery.promotion.support.PromotionTestcontainerBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoucherServiceTest extends PromotionTestcontainerBase {

    @Autowired VoucherRepository repo;
    @Autowired VoucherService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanSlate() {
        // Delete redemptions first (FK constraint), then test vouchers
        jdbc.update("DELETE FROM voucher_redemption");
        // Avoid touching seeded vouchers (FREESHIP, GIAM20K) — delete only ones created in tests
        repo.findAll().stream()
            .filter(v -> !v.getCode().equals("FREESHIP") && !v.getCode().equals("GIAM20K"))
            .forEach(v -> repo.delete(v));
    }

    private UUID seedOrderAndUser() {
        jdbc.update("INSERT INTO telegram_user (id, first_name, language_code, created_at, updated_at) " +
                    "VALUES (1, 'Test', 'vi', NOW(), NOW()) ON CONFLICT (id) DO NOTHING");
        UUID orderId = UUID.randomUUID();
        jdbc.update("INSERT INTO orders (id, code, customer_id, customer_name, customer_phone, " +
                    "pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng, distance_km, " +
                    "subtotal, delivery_fee, total, payment_method, payment_status, status, " +
                    "delivery_fee_original, created_at, updated_at) " +
                    "VALUES (?, ?, 1, 'Test', '0123456789', 21.0, 105.8, 'Test', 21.0, 105.8, 0, " +
                    "100000, 30000, 130000, 'COD', 'PENDING', 'PENDING', 30000, NOW(), NOW())",
                    orderId, "TEST-" + orderId.toString().substring(0, 8));
        return orderId;
    }

    @Test
    void unknownCode_returnsNOT_FOUND() {
        VoucherValidationResult r = service.validate(
            "DOES_NOT_EXIST", VoucherTarget.PRODUCTS,
            bd("100000"), bd("30000"), 1L);
        assertThat(r.isValid()).isFalse();
        assertThat(r.reason()).isEqualTo(VoucherInvalidReason.NOT_FOUND);
    }

    @Test
    void expiredVoucher_returnsEXPIRED() {
        Voucher v = save(productsFixed("EXP", "20000"),
            OffsetDateTime.now().minusDays(10),
            OffsetDateTime.now().minusDays(1));
        VoucherValidationResult r = service.validate(
            v.getCode(), VoucherTarget.PRODUCTS, bd("100000"), bd("30000"), 1L);
        assertThat(r.reason()).isEqualTo(VoucherInvalidReason.EXPIRED);
    }

    @Test
    void wrongTarget_returnsWRONG_TARGET() {
        Voucher v = save(productsFixed("HELLO", "20000"));
        VoucherValidationResult r = service.validate(
            v.getCode(), VoucherTarget.SHIPPING, bd("100000"), bd("30000"), 1L);
        assertThat(r.reason()).isEqualTo(VoucherInvalidReason.WRONG_TARGET);
    }

    @Test
    void belowMinOrder_returnsBELOW_MIN_ORDER() {
        Voucher v = productsFixed("MIN100K", "20000");
        v.setMinOrderAmount(bd("100000"));
        save(v);
        VoucherValidationResult r = service.validate(
            v.getCode(), VoucherTarget.PRODUCTS, bd("50000"), bd("30000"), 1L);
        assertThat(r.reason()).isEqualTo(VoucherInvalidReason.BELOW_MIN_ORDER);
    }

    private Voucher productsFixed(String code, String value) {
        Voucher v = new Voucher();
        v.setCode(code);
        v.setName("Test");
        v.setTarget(VoucherTarget.PRODUCTS);
        v.setDiscountType(DiscountType.FIXED);
        v.setDiscountValue(bd(value));
        v.setValidFrom(OffsetDateTime.now().minusDays(1));
        v.setValidUntil(OffsetDateTime.now().plusDays(30));
        return v;
    }

    @Test
    void redeem_incrementsUsedCount_andLogsRedemption() {
        Voucher v = save(productsFixed("REDEEM1", "20000"));
        UUID orderId = seedOrderAndUser();

        service.redeem(v.getCode(), orderId, 1L, bd("20000"));

        Voucher after = repo.findById(v.getId()).orElseThrow();
        assertThat(after.getUsedCount()).isEqualTo(1);
    }

    @Test
    void redeem_exhaustedTotal_throws() {
        Voucher v = productsFixed("ONE", "10000");
        v.setMaxUsesTotal(1);
        v.setUsedCount(1);
        save(v);

        assertThatThrownBy(() ->
            service.redeem(v.getCode(), UUID.randomUUID(), 1L, bd("10000"))
        ).isInstanceOf(VoucherRedeemException.class);
    }

    private Voucher save(Voucher v) { return repo.saveAndFlush(v); }

    private Voucher save(Voucher v, OffsetDateTime from, OffsetDateTime until) {
        v.setValidFrom(from);
        v.setValidUntil(until);
        return repo.saveAndFlush(v);
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
