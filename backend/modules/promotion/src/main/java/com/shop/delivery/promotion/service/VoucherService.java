package com.shop.delivery.promotion.service;

import com.shop.delivery.promotion.domain.VoucherInvalidReason;
import com.shop.delivery.promotion.domain.VoucherTarget;
import com.shop.delivery.promotion.domain.VoucherValidationResult;
import com.shop.delivery.promotion.entity.Voucher;
import com.shop.delivery.promotion.repository.VoucherRedemptionRepository;
import com.shop.delivery.promotion.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepo;
    private final VoucherRedemptionRepository redemptionRepo;
    private final VoucherCalculator calculator;

    public VoucherService(VoucherRepository voucherRepo,
                          VoucherRedemptionRepository redemptionRepo,
                          VoucherCalculator calculator) {
        this.voucherRepo = voucherRepo;
        this.redemptionRepo = redemptionRepo;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public VoucherValidationResult validate(String code, VoucherTarget target,
                                            BigDecimal subtotal,
                                            BigDecimal deliveryFee,
                                            Long customerId) {
        Optional<Voucher> opt = voucherRepo.findByCodeIgnoreCaseAndActiveTrue(code);
        if (opt.isEmpty()) return VoucherValidationResult.invalid(VoucherInvalidReason.NOT_FOUND);
        Voucher v = opt.get();

        if (!v.isActive()) return VoucherValidationResult.invalid(VoucherInvalidReason.INACTIVE);
        if (v.getTarget() != target) return VoucherValidationResult.invalid(VoucherInvalidReason.WRONG_TARGET);

        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(v.getValidFrom()))  return VoucherValidationResult.invalid(VoucherInvalidReason.NOT_YET_VALID);
        if (now.isAfter(v.getValidUntil())) return VoucherValidationResult.invalid(VoucherInvalidReason.EXPIRED);

        if (subtotal.compareTo(v.getMinOrderAmount()) < 0)
            return VoucherValidationResult.invalid(VoucherInvalidReason.BELOW_MIN_ORDER);

        if (v.getMaxUsesTotal() != null && v.getUsedCount() >= v.getMaxUsesTotal())
            return VoucherValidationResult.invalid(VoucherInvalidReason.EXHAUSTED_TOTAL);

        int customerUses = redemptionRepo.countByVoucherIdAndCustomerId(v.getId(), customerId);
        if (customerUses >= v.getMaxUsesPerCustomer())
            return VoucherValidationResult.invalid(VoucherInvalidReason.EXHAUSTED_PER_CUSTOMER);

        BigDecimal base = target == VoucherTarget.SHIPPING ? deliveryFee : subtotal;
        return VoucherValidationResult.ok(v, calculator.discountFor(v, base));
    }
}
