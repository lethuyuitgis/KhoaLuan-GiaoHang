package com.shop.delivery.promotion.service;

import com.shop.delivery.order.spi.VoucherApplicator;
import com.shop.delivery.promotion.entity.Voucher;
import com.shop.delivery.promotion.repository.VoucherRepository;
import com.shop.delivery.promotion.support.PromotionTestcontainerBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for voucher stacking rules verified via {@link VoucherApplicatorImpl}.
 *
 * Rule: an order may stack at most one PRODUCTS voucher + one SHIPPING voucher.
 * Using two PRODUCTS vouchers must be rejected.
 *
 * Placed in the promotion module because {@link VoucherApplicatorImpl} — the only
 * concrete implementation of the stacking logic — lives here, and the module already
 * has a full Testcontainers / Flyway test infrastructure.
 */
@DirtiesContext
class VoucherStackingIT extends PromotionTestcontainerBase {

    @Autowired VoucherApplicator applicator;   // VoucherApplicatorImpl
    @Autowired VoucherRepository repo;
    @Autowired JdbcTemplate jdbc;

    private static final Long CUSTOMER_ID = 9001L;

    @BeforeEach
    void setUp() {
        // Seed customer once; ON CONFLICT DO NOTHING makes it idempotent.
        jdbc.update(
            "INSERT INTO telegram_user (id, first_name, language_code, created_at, updated_at) " +
            "VALUES (?, 'StackTest', 'vi', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
            CUSTOMER_ID);

        // Clean up any vouchers left from a previous run for these codes
        for (String code : new String[]{"GIAM10K", "SHIP15K", "GIAM5K"}) {
            jdbc.update(
                "DELETE FROM voucher_redemption WHERE voucher_id IN " +
                "(SELECT id FROM voucher WHERE code = ?)", code);
            jdbc.update("DELETE FROM voucher WHERE code = ?", code);
        }

        seedVoucher("GIAM10K", "PRODUCTS", "FIXED", "10000", null);
        seedVoucher("SHIP15K", "SHIPPING", "FIXED", "15000", null);
        seedVoucher("GIAM5K",  "PRODUCTS", "FIXED",  "5000", null);
    }

    @Test
    void stacking_oneShipping_oneProducts_appliesBoth() {
        BigDecimal subtotal     = new BigDecimal("200000");
        BigDecimal deliveryFee  = new BigDecimal("30000");

        VoucherApplicator.AppliedDiscount applied =
            applicator.validate("GIAM10K", "SHIP15K", subtotal, deliveryFee, CUSTOMER_ID);

        assertThat(applied.products()).isEqualByComparingTo("10000");
        assertThat(applied.shipping()).isEqualByComparingTo("15000");
    }

    @Test
    void stacking_twoProducts_rejectsSecondAsWrongTarget() {
        BigDecimal subtotal    = new BigDecimal("200000");
        BigDecimal deliveryFee = new BigDecimal("30000");

        // "GIAM5K" is PRODUCTS, but the second slot (shippingCode) must be SHIPPING —
        // the applicator validates it against VoucherTarget.SHIPPING and returns WRONG_TARGET.
        assertThatThrownBy(() ->
            applicator.validate("GIAM10K", "GIAM5K", subtotal, deliveryFee, CUSTOMER_ID)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID_VOUCHER_SHIPPING_WRONG_TARGET");
    }

    // ---- helpers --------------------------------------------------------

    private void seedVoucher(String code, String target, String type,
                             String value, String maxDiscount) {
        Voucher v = new Voucher();
        v.setCode(code);
        v.setName(code + " test");

        v.setTarget(com.shop.delivery.promotion.domain.VoucherTarget.valueOf(target));
        v.setDiscountType(com.shop.delivery.promotion.domain.DiscountType.valueOf(type));
        v.setDiscountValue(new BigDecimal(value));

        if (maxDiscount != null) {
            v.setMaxDiscount(new BigDecimal(maxDiscount));
        }

        v.setMinOrderAmount(BigDecimal.ZERO);
        v.setValidFrom(OffsetDateTime.now().minusDays(1));
        v.setValidUntil(OffsetDateTime.now().plusDays(30));
        v.setMaxUsesTotal(100);
        v.setMaxUsesPerCustomer(5);
        repo.saveAndFlush(v);
    }
}
