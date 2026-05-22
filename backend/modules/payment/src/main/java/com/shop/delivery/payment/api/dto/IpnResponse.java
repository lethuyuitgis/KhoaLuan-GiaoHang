package com.shop.delivery.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * VNPay's IPN response contract — field NAMES (RspCode, Message) are part of
 * the protocol, not a stylistic choice. Capitalisation matters.
 *
 * <p>Documented RspCodes (from {@code vnpay_ipn.jsp}):
 * <ul>
 *   <li>{@code 00} — Confirm Success (payment recorded)</li>
 *   <li>{@code 01} — Order not Found (TxnRef has no Payment row)</li>
 *   <li>{@code 02} — Order already confirmed (replay — Payment.status != PENDING)</li>
 *   <li>{@code 04} — Invalid Amount (vnp_Amount != payment.amount * 100)</li>
 *   <li>{@code 97} — Invalid Checksum (HMAC mismatch)</li>
 * </ul>
 */
public record IpnResponse(
    @JsonProperty("RspCode") String rspCode,
    @JsonProperty("Message") String message
) {
    public static IpnResponse ok()                  { return new IpnResponse("00", "Confirm Success"); }
    public static IpnResponse orderNotFound()       { return new IpnResponse("01", "Order not found"); }
    public static IpnResponse alreadyConfirmed()    { return new IpnResponse("02", "Order already confirmed"); }
    public static IpnResponse invalidAmount()       { return new IpnResponse("04", "Invalid Amount"); }
    public static IpnResponse invalidChecksum()     { return new IpnResponse("97", "Invalid Checksum"); }
}
