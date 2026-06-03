package com.shop.delivery.promotion.domain;

/**
 * What the voucher reduces:
 *   SHIPPING — reduces delivery_fee (the customer pays less for delivery).
 *   PRODUCTS — reduces subtotal (the customer pays less for goods).
 *
 * Per business rule, an order may stack at most one of each target.
 */
public enum VoucherTarget {
    SHIPPING,
    PRODUCTS
}
