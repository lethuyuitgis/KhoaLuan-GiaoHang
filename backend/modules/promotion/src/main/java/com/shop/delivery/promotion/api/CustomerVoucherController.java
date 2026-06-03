package com.shop.delivery.promotion.api;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.promotion.api.dto.ValidateVoucherRequest;
import com.shop.delivery.promotion.api.dto.ValidateVoucherResponse;
import com.shop.delivery.promotion.domain.VoucherValidationResult;
import com.shop.delivery.promotion.service.VoucherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/vouchers")
public class CustomerVoucherController {

    private final VoucherService service;

    public CustomerVoucherController(VoucherService service) {
        this.service = service;
    }

    @PostMapping("/validate")
    public ResponseEntity<ValidateVoucherResponse> validate(
            @CurrentUser TelegramUser user,
            @Valid @RequestBody ValidateVoucherRequest req) {
        Long customerId = user.getId();
        VoucherValidationResult r = service.validate(
            req.code(), req.target(), req.subtotal(), req.deliveryFee(), customerId);
        if (!r.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "INVALID_VOUCHER_" + r.reason());
        }
        return ResponseEntity.ok(new ValidateVoucherResponse(
            r.voucher().getCode(), r.voucher().getName(), r.discountAmount()));
    }
}
