package com.shop.delivery.promotion.api.dto;

import java.math.BigDecimal;

public record ValidateVoucherResponse(
    String code,
    String name,
    BigDecimal discountAmount
) {}
