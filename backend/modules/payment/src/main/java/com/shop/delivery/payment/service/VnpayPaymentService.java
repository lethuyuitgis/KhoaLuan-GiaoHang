package com.shop.delivery.payment.service;

import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.payment.api.dto.CreatePaymentResponse;
import com.shop.delivery.payment.api.dto.IpnResponse;
import com.shop.delivery.payment.api.dto.ReturnRedirect;
import com.shop.delivery.payment.config.VnpayProperties;
import com.shop.delivery.payment.domain.PaymentEventType;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.shared.event.PaymentFailedEvent;
import com.shop.delivery.shared.event.PaymentSucceededEvent;
import com.shop.delivery.shared.exception.NotFoundException;
import com.shop.delivery.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * VNPay business logic. Three public methods:
 *
 * <ul>
 *   <li>{@link #createPayment} — caller-authenticated (controller does ownership
 *       check via initData). Builds + signs URL, persists PENDING Payment row,
 *       supersedes prior PENDING rows for the same order, returns the URL.</li>
 *   <li>{@link #handleIpn} — public unauthenticated. Verifies signature, checks
 *       idempotency, amount, response codes; flips status; emits events.
 *       <strong>Source of truth</strong> for payment outcome.</li>
 *   <li>{@link #handleReturn} — public unauthenticated. Verifies signature,
 *       decides redirect target. <strong>No DB writes</strong> apart from
 *       audit row.</li>
 * </ul>
 *
 * <p>Uses {@link Clock} for testability (fixed clock in unit tests, system
 * clock in production). Uses {@link ApplicationEventPublisher} (NOT direct
 * call to {@code OrderService}) for cross-module signalling — see research
 * §Anti-Patterns.
 */
@Service
public class VnpayPaymentService {

    private static final Logger log = LoggerFactory.getLogger(VnpayPaymentService.class);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final VnpaySignatureService sig;
    private final VnpayProperties props;
    private final PaymentAuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public VnpayPaymentService(OrderRepository orderRepo,
                               PaymentRepository paymentRepo,
                               VnpaySignatureService sig,
                               VnpayProperties props,
                               PaymentAuditRecorder audit,
                               ApplicationEventPublisher events,
                               Clock clock) {
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.sig = sig;
        this.props = props;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // -------- create --------

    @Transactional
    public CreatePaymentResponse createPayment(UUID orderId, Long callerCustomerId, String ipAddr) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Đơn không tồn tại"));
        if (!order.getCustomerId().equals(callerCustomerId)) {
            throw new ValidationException("FORBIDDEN", "Đơn không thuộc về bạn");
        }
        if (order.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new ValidationException("INVALID_METHOD", "Đơn không dùng VNPay");
        }
        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ValidationException("ALREADY_PAID",
                "Đơn đã có trạng thái thanh toán: " + order.getPaymentStatus());
        }

        // Supersede any prior PENDING attempts for this order.
        List<Payment> prior = paymentRepo.findAllByOrderIdAndStatus(orderId, PaymentStatus.PENDING);
        for (Payment p : prior) {
            p.setStatus(PaymentStatus.FAILED);
            p.setVnpResponseCode("SUPERSEDED");
            paymentRepo.save(p);
        }

        long epochMs = clock.instant().toEpochMilli();
        String txnRef = order.getCode() + "-" + epochMs;

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());
        payment.setMethod(PaymentMethod.VNPAY);
        payment.setAmount(order.getTotal());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setVnpTxnRef(txnRef);
        paymentRepo.save(payment);

        // Build VNPay params (insertion order does not matter — signer sorts).
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), VN_ZONE);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version",    "2.1.0");
        params.put("vnp_Command",    "pay");
        params.put("vnp_TmnCode",    props.tmnCode());
        params.put("vnp_Amount",     order.getTotal().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CurrCode",   "VND");
        params.put("vnp_TxnRef",     txnRef);
        params.put("vnp_OrderInfo",  "Thanh toan don hang " + order.getCode());
        params.put("vnp_OrderType",  "other");
        params.put("vnp_Locale",     "vn");
        params.put("vnp_ReturnUrl",  props.returnUrl());
        params.put("vnp_IpAddr",     ipAddr == null ? "127.0.0.1" : ipAddr);
        params.put("vnp_CreateDate", now.format(VNP_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(props.timeoutMinutes()).format(VNP_DATE));

        VnpaySignatureService.BuildResult signed = sig.buildHashAndQuery(params);
        String url = props.payUrl() + "?" + signed.queryString() + "&vnp_SecureHash=" + signed.hash();

        audit.record(payment.getId(), PaymentEventType.CREATE, params);

        log.info("VNPay payment created: orderId={} txnRef={} amount={}",
            order.getId(), txnRef, order.getTotal());

        return new CreatePaymentResponse(url, txnRef);
    }

    // -------- ipn --------

    @Transactional
    public IpnResponse handleIpn(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        Optional<Payment> maybe = (txnRef != null && !txnRef.isBlank())
            ? paymentRepo.findByVnpTxnRef(txnRef)
            : Optional.empty();

        // Audit every IPN attempt where we can match a Payment row — including bad-signature
        // probes — so we have evidence of probing attempts. We can't audit when the txnRef is
        // unknown because `payment_transaction.payment_id` has a NOT NULL FK (V9 schema).
        maybe.ifPresent(p -> audit.record(p.getId(), PaymentEventType.IPN, params));

        String receivedHash = params.get("vnp_SecureHash");
        if (!sig.verify(params, receivedHash)) {
            log.warn("VNPay IPN: invalid checksum (txnRef={})", txnRef);
            return IpnResponse.invalidChecksum();
        }
        if (maybe.isEmpty()) {
            log.warn("VNPay IPN: unknown txnRef={}", txnRef);
            return IpnResponse.orderNotFound();
        }
        Payment payment = maybe.get();

        // Idempotency — design §10.3 step 3
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("VNPay IPN replay: txnRef={} status={}", txnRef, payment.getStatus());
            return IpnResponse.alreadyConfirmed();
        }

        // Amount check — design §10.3 step 4
        long expected;
        try {
            expected = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
        } catch (ArithmeticException ae) {
            // Defensive: NUMERIC(12,2) × 100 should always be integral. If it isn't,
            // something corrupted the row — fail closed rather than throwing 500.
            log.error("Payment {} has non-integer amount in VND-cents: {}",
                payment.getId(), payment.getAmount());
            return IpnResponse.invalidAmount();
        }
        String rawAmount = params.get("vnp_Amount");
        if (rawAmount == null || rawAmount.isBlank()) {
            return IpnResponse.invalidAmount();
        }
        long received;
        try {
            received = Long.parseLong(rawAmount);
        } catch (NumberFormatException nfe) {
            return IpnResponse.invalidAmount();
        }
        if (expected != received) {
            log.warn("VNPay IPN amount mismatch: txnRef={} expected={} received={}",
                txnRef, expected, received);
            return IpnResponse.invalidAmount();
        }

        String responseCode = params.get("vnp_ResponseCode");
        String txStatus     = params.get("vnp_TransactionStatus");
        boolean success = "00".equals(responseCode) && "00".equals(txStatus);

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
            payment.setVnpResponseCode(responseCode);
            payment.setPaidAt(clock.instant());
            paymentRepo.save(payment);
            events.publishEvent(new PaymentSucceededEvent(
                payment.getOrderId(), payment.getId(), payment.getAmount(), payment.getVnpTxnRef()));
            return IpnResponse.ok();
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setVnpResponseCode(responseCode);
        paymentRepo.save(payment);
        events.publishEvent(new PaymentFailedEvent(
            payment.getOrderId(), payment.getId(), payment.getVnpTxnRef(), responseCode));
        return IpnResponse.ok();
    }

    // -------- return --------

    @Transactional
    public ReturnRedirect handleReturn(Map<String, String> params) {
        String hash = params.get("vnp_SecureHash");
        String txnRef = params.get("vnp_TxnRef");
        boolean valid = sig.verify(params, hash);

        // Always try to audit (helps debug Return-before-IPN races); skip if no payment match.
        Optional<Payment> match = (txnRef != null)
            ? paymentRepo.findByVnpTxnRef(txnRef)
            : Optional.empty();
        match.ifPresent(p -> audit.record(p.getId(), PaymentEventType.RETURN, params));

        if (!valid) {
            return new ReturnRedirect("/payment-failed.html?reason=invalid_signature", false);
        }

        String responseCode = params.get("vnp_ResponseCode");
        String txStatus = params.get("vnp_TransactionStatus");
        boolean ok = "00".equals(responseCode) && "00".equals(txStatus);

        String orderCode = (txnRef != null && txnRef.contains("-"))
            ? txnRef.substring(0, txnRef.lastIndexOf('-'))
            : "";
        String amountVnd = "";
        if (match.isPresent()) {
            amountVnd = match.get().getAmount().toBigInteger().toString();
        } else if (params.get("vnp_Amount") != null) {
            try {
                amountVnd = String.valueOf(Long.parseLong(params.get("vnp_Amount")) / 100);
            } catch (NumberFormatException ignored) { /* leave blank */ }
        }

        if (ok) {
            String url = "/payment-success.html?orderCode="
                + URLEncoder.encode(orderCode, StandardCharsets.UTF_8)
                + "&amount=" + URLEncoder.encode(amountVnd, StandardCharsets.UTF_8);
            return new ReturnRedirect(url, true);
        }
        String url = "/payment-failed.html?code="
            + URLEncoder.encode(responseCode == null ? "" : responseCode, StandardCharsets.UTF_8)
            + "&orderCode=" + URLEncoder.encode(orderCode, StandardCharsets.UTF_8);
        return new ReturnRedirect(url, false);
    }
}
