package com.shop.delivery.promotion.api;

import com.shop.delivery.promotion.api.dto.*;
import com.shop.delivery.promotion.entity.Voucher;
import com.shop.delivery.promotion.repository.VoucherRedemptionRepository;
import com.shop.delivery.promotion.repository.VoucherRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/vouchers")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminVoucherController {

    private final VoucherRepository voucherRepo;
    private final VoucherRedemptionRepository redemptionRepo;

    public AdminVoucherController(VoucherRepository voucherRepo,
                                  VoucherRedemptionRepository redemptionRepo) {
        this.voucherRepo = voucherRepo;
        this.redemptionRepo = redemptionRepo;
    }

    @GetMapping
    public Page<VoucherSummary> list(Pageable pageable) {
        return voucherRepo.findAll(pageable).map(this::toSummary);
    }

    @GetMapping("/{id}")
    public VoucherDetail get(@PathVariable Long id) {
        Voucher v = voucherRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<VoucherDetail.RedemptionRow> rows = redemptionRepo.findAll().stream()
            .filter(r -> r.getVoucherId().equals(id))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(20)
            .map(r -> new VoucherDetail.RedemptionRow(r.getOrderId(), r.getCustomerId(),
                                                     r.getDiscountApplied(), r.getCreatedAt()))
            .toList();
        return new VoucherDetail(toSummary(v), rows);
    }

    @PostMapping
    public ResponseEntity<VoucherSummary> create(@Valid @RequestBody CreateVoucherRequest req) {
        if (voucherRepo.findByCodeIgnoreCaseAndActiveTrue(req.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CODE_TAKEN");
        }
        Voucher v = new Voucher();
        v.setCode(req.code().toUpperCase());
        v.setName(req.name());
        v.setTarget(req.target());
        v.setDiscountType(req.discountType());
        v.setDiscountValue(req.discountValue());
        v.setMaxDiscount(req.maxDiscount());
        v.setMinOrderAmount(req.minOrderAmount() == null ? BigDecimal.ZERO : req.minOrderAmount());
        v.setValidFrom(req.validFrom());
        v.setValidUntil(req.validUntil());
        v.setMaxUsesTotal(req.maxUsesTotal());
        v.setMaxUsesPerCustomer(req.maxUsesPerCustomer());
        Voucher saved = voucherRepo.save(v);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(saved));
    }

    @PutMapping("/{id}")
    public VoucherSummary update(@PathVariable Long id, @Valid @RequestBody UpdateVoucherRequest req) {
        Voucher v = voucherRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        v.setName(req.name());
        v.setDiscountValue(req.discountValue());
        v.setMaxDiscount(req.maxDiscount());
        if (req.minOrderAmount() != null) v.setMinOrderAmount(req.minOrderAmount());
        v.setValidFrom(req.validFrom());
        v.setValidUntil(req.validUntil());
        v.setMaxUsesTotal(req.maxUsesTotal());
        v.setMaxUsesPerCustomer(req.maxUsesPerCustomer());
        v.setActive(req.active());
        return toSummary(voucherRepo.save(v));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@PathVariable Long id) {
        Voucher v = voucherRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        v.setActive(false);
        voucherRepo.save(v);
    }

    private VoucherSummary toSummary(Voucher v) {
        return new VoucherSummary(v.getId(), v.getCode(), v.getName(),
            v.getTarget(), v.getDiscountType(),
            v.getDiscountValue(), v.getMaxDiscount(),
            v.getMinOrderAmount(),
            v.getUsedCount(), v.getMaxUsesTotal(),
            v.getMaxUsesPerCustomer(),
            v.getValidFrom(), v.getValidUntil(),
            v.isActive());
    }
}
