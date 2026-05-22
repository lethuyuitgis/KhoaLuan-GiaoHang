package com.shop.delivery.payment.domain;

/**
 * Type of event recorded in {@code payment_transaction}.
 *
 * <ul>
 *   <li>{@code CREATE} — emitted when a {@code Payment} row is first inserted via
 *       {@code POST /api/payment/vnpay/create}. Raw payload = the sorted params sent to VNPay.</li>
 *   <li>{@code IPN} — emitted on every call to {@code /api/payment/vnpay/ipn},
 *       including duplicates / invalid-signature / amount-mismatch (audit trail).</li>
 *   <li>{@code RETURN} — emitted on every call to {@code /api/payment/vnpay/return}.</li>
 * </ul>
 */
public enum PaymentEventType {
    CREATE,
    IPN,
    RETURN
}
