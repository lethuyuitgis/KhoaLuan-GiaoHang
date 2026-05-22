package com.shop.delivery.shared.event;

import java.util.UUID;

/**
 * Published when an IPN reports a non-success status (e.g. user cancelled,
 * insufficient funds, bank refused) or the {@code PaymentExpiryScheduler}
 * marks a payment as expired.
 *
 * <p>Currently has no listeners in P7 — kept for symmetry with
 * {@code PaymentSucceededEvent} and for P8 to wire bot notifications
 * ("đơn của bạn thanh toán thất bại, vui lòng thử lại").
 *
 * @param responseCode the VNPay {@code vnp_ResponseCode} value, or {@code "EXPIRED"}
 *                     when emitted by the scheduler.
 */
public record PaymentFailedEvent(
    UUID orderId,
    UUID paymentId,
    String txnRef,
    String responseCode
) {}
