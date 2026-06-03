package com.shop.delivery.promotion.domain;

/**
 * Voucher discount formula:
 *   FIXED   — discount_value is a flat VND amount, capped at base (the discount
 *             never exceeds the part it reduces).
 *   PERCENT — discount_value is a percentage of base; final discount is capped
 *             at max_discount when set.
 */
public enum DiscountType {
    FIXED,
    PERCENT
}
