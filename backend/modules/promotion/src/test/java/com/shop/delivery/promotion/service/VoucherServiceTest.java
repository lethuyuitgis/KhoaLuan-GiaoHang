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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherServiceTest extends PromotionTestcontainerBase {

    @Autowired VoucherRepository repo;
    @Autowired VoucherService service;

    @BeforeEach
    void cleanSlate() {
        // Avoid touching seeded vouchers (FREESHIP, GIAM20K) — delete only ones created in tests
        repo.findAll().stream()
            .filter(v -> !v.getCode().equals("FREESHIP") && !v.getCode().equals("GIAM20K"))
            .forEach(v -> repo.delete(v));
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

    private Voucher save(Voucher v) { return repo.saveAndFlush(v); }

    private Voucher save(Voucher v, OffsetDateTime from, OffsetDateTime until) {
        v.setValidFrom(from);
        v.setValidUntil(until);
        return repo.saveAndFlush(v);
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
