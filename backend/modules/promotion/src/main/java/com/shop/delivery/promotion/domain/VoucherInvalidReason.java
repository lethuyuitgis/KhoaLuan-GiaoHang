package com.shop.delivery.promotion.domain;

/** Stable reason codes returned to the frontend so it can show localized error messages. */
public enum VoucherInvalidReason {
    NOT_FOUND,
    INACTIVE,
    NOT_YET_VALID,
    EXPIRED,
    BELOW_MIN_ORDER,
    EXHAUSTED_TOTAL,
    EXHAUSTED_PER_CUSTOMER,
    WRONG_TARGET
}
