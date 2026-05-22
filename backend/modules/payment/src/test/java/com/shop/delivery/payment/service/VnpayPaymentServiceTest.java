package com.shop.delivery.payment.service;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.payment.api.dto.CreatePaymentResponse;
import com.shop.delivery.payment.api.dto.IpnResponse;
import com.shop.delivery.payment.api.dto.ReturnRedirect;
import com.shop.delivery.payment.config.VnpayProperties;
import com.shop.delivery.payment.entity.Payment;
import com.shop.delivery.payment.repository.PaymentRepository;
import com.shop.delivery.shared.event.PaymentFailedEvent;
import com.shop.delivery.shared.event.PaymentSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VnpayPaymentServiceTest {

    private static final String SECRET = "TESTSECRETKEY123";

    @Mock OrderRepository orderRepo;
    @Mock PaymentRepository paymentRepo;
    @Mock PaymentAuditRecorder audit;
    @Mock ApplicationEventPublisher events;

    private VnpayProperties props;
    private VnpaySignatureService sig;
    private Clock fixedClock;
    private VnpayPaymentService svc;

    @BeforeEach
    void setUp() {
        props = new VnpayProperties(
            "TEST01", SECRET,
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "http://localhost:8080/api/payment/vnpay/return",
            "http://localhost:8080/api/payment/vnpay/ipn",
            15
        );
        sig = new VnpaySignatureService(props);
        fixedClock = Clock.fixed(Instant.parse("2026-05-22T10:00:00Z"), ZoneId.of("UTC"));
        svc = new VnpayPaymentService(orderRepo, paymentRepo, sig, props, audit, events, fixedClock);
    }

    // ----- createPayment -----

    @Test
    void create_returnsSignedUrl_andPersistsPendingPaymentRow() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findAllByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING))
            .thenReturn(List.of());

        CreatePaymentResponse resp = svc.createPayment(order.getId(), 42L, "127.0.0.1");

        assertThat(resp.paymentUrl())
            .startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")
            .contains("vnp_TmnCode=TEST01")
            .contains("vnp_Amount=25000000")              // 250000.00 * 100
            .contains("vnp_TxnRef=")
            .contains("vnp_SecureHash=");
        assertThat(resp.txnRef())
            .startsWith(order.getCode() + "-")
            .matches(".+-\\d+");

        ArgumentCaptor<Payment> cap = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo).save(cap.capture());
        Payment saved = cap.getValue();
        assertThat(saved.getOrderId()).isEqualTo(order.getId());
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(saved.getAmount()).isEqualByComparingTo("250000.00");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getVnpTxnRef()).isEqualTo(resp.txnRef());
    }

    @Test
    void create_marksPriorPendingPaymentsAsSUPERSEDED() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        Payment prior = new Payment();
        prior.setId(UUID.randomUUID());
        prior.setOrderId(order.getId());
        prior.setStatus(PaymentStatus.PENDING);
        prior.setAmount(new BigDecimal("250000.00"));
        when(paymentRepo.findAllByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING))
            .thenReturn(List.of(prior));

        svc.createPayment(order.getId(), 42L, "127.0.0.1");

        // Should save TWICE: once for the superseded prior, once for the new pending.
        verify(paymentRepo, times(2)).save(any(Payment.class));
        assertThat(prior.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(prior.getVnpResponseCode()).isEqualTo("SUPERSEDED");
    }

    @Test
    void create_rejectsOrderOwnedBySomeoneElse() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> svc.createPayment(order.getId(), 99L, "127.0.0.1"))
            .hasMessageContaining("không thuộc về bạn");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void create_rejectsCODOrder() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        order.setPaymentMethod(PaymentMethod.COD);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> svc.createPayment(order.getId(), 42L, "127.0.0.1"))
            .hasMessageContaining("không dùng VNPay");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void create_rejectsAlreadyPaidOrder() {
        Order order = vnpayOrder(UUID.randomUUID(), 42L, new BigDecimal("250000.00"));
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> svc.createPayment(order.getId(), 42L, "127.0.0.1"))
            .hasMessageContaining("đã có trạng thái");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void create_rejectsUnknownOrder() {
        UUID id = UUID.randomUUID();
        when(orderRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.createPayment(id, 42L, "127.0.0.1"))
            .hasMessageContaining("không tồn tại");
    }

    // ----- handleIpn -----

    @Test
    void ipn_happyPath_flipsPaymentToSuccessAndPublishesEvent() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");

        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getVnpTransactionNo()).isEqualTo("14123456");
        assertThat(payment.getVnpResponseCode()).isEqualTo("00");
        assertThat(payment.getPaidAt()).isNotNull();

        verify(paymentRepo).save(payment);
        ArgumentCaptor<PaymentSucceededEvent> ev = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().orderId()).isEqualTo(payment.getOrderId());
        assertThat(ev.getValue().paymentId()).isEqualTo(payment.getId());
        assertThat(ev.getValue().amount()).isEqualByComparingTo("250000.00");
    }

    @Test
    void ipn_badSignature_returns97_andNoDbWrite() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");
        params.put("vnp_SecureHash", "f".repeat(128)); // valid hex but wrong hash

        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("97");
        verify(paymentRepo, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void ipn_unknownTxnRef_returns01() {
        when(paymentRepo.findByVnpTxnRef(any())).thenReturn(Optional.empty());

        Payment ghost = pendingPayment(new BigDecimal("250000.00"));
        Map<String, String> params = signedIpnParams(ghost, "00", "00", "14123456");

        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("01");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void ipn_replayWhenAlreadySuccess_returns02_andAuditsButDoesNotMutate() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(Instant.parse("2026-05-22T09:55:00Z"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");
        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("02");
        verify(paymentRepo, never()).save(any());
        verify(events, never()).publishEvent(any());
        // But audit IS recorded.
        verify(audit).record(eq(payment.getId()),
            eq(com.shop.delivery.payment.domain.PaymentEventType.IPN), any());
    }

    @Test
    void ipn_amountMismatch_returns04_paymentStaysPending() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");
        // Tamper amount AFTER signing (the signed amount was 25000000; we change it to 100
        // and re-sign so the test is honest — attacker who has the secret would do this)
        params.put("vnp_Amount", "100");
        params.put("vnp_SecureHash", sig.buildHashAndQuery(stripHash(params)).hash());

        IpnResponse resp = svc.handleIpn(params);

        assertThat(resp.rspCode()).isEqualTo("04");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void ipn_responseCodeFailure_flipsPaymentToFailedAndPublishesFailedEvent() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        // vnp_ResponseCode=07 means "Suspicious transaction" — VNPay marks failure.
        Map<String, String> params = signedIpnParams(payment, "07", "01", "14123456");
        IpnResponse resp = svc.handleIpn(params);

        // IPN is still ack'd 00 — we accept VNPay's notification successfully even
        // though the payment itself failed. The merchant's job is to record the outcome.
        assertThat(resp.rspCode()).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getVnpResponseCode()).isEqualTo("07");

        ArgumentCaptor<PaymentFailedEvent> ev = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().responseCode()).isEqualTo("07");
    }

    // ----- handleReturn -----

    @Test
    void return_validSig_successCodes_redirectsToStaticSuccess() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "00", "00", "14123456");

        ReturnRedirect rr = svc.handleReturn(params);

        assertThat(rr.success()).isTrue();
        assertThat(rr.redirectUrl())
            .startsWith("/payment-success.html?")
            .contains("orderCode=" + payment.getVnpTxnRef()
                .substring(0, payment.getVnpTxnRef().lastIndexOf('-')))
            .contains("amount=250000");
        // Return URL does NOT write DB
        verify(paymentRepo, never()).save(any());
        // But DOES audit (helps debug Return-before-IPN races)
        verify(audit).record(any(),
            eq(com.shop.delivery.payment.domain.PaymentEventType.RETURN), any());
    }

    @Test
    void return_badSig_redirectsToFailWithReason() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "DH-FAKE-1");
        params.put("vnp_Amount", "100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", "0".repeat(128));

        ReturnRedirect rr = svc.handleReturn(params);

        assertThat(rr.success()).isFalse();
        assertThat(rr.redirectUrl())
            .startsWith("/payment-failed.html?")
            .contains("reason=invalid_signature");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void return_validSig_failCode_redirectsToStaticFailed() {
        Payment payment = pendingPayment(new BigDecimal("250000.00"));
        when(paymentRepo.findByVnpTxnRef(payment.getVnpTxnRef())).thenReturn(Optional.of(payment));

        Map<String, String> params = signedIpnParams(payment, "24", "02", "14123456"); // 24 = user cancelled

        ReturnRedirect rr = svc.handleReturn(params);

        assertThat(rr.success()).isFalse();
        assertThat(rr.redirectUrl())
            .startsWith("/payment-failed.html?")
            .contains("code=24");
        verify(paymentRepo, never()).save(any());
    }

    // ----- helpers -----

    private Order vnpayOrder(UUID id, Long customerId, BigDecimal total) {
        Order o = new Order();
        o.setId(id);
        o.setCode("DH20260522-1");
        o.setCustomerId(customerId);
        o.setTotal(total);
        o.setPaymentMethod(PaymentMethod.VNPAY);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setStatus(OrderStatus.PENDING);
        return o;
    }

    private Payment pendingPayment(BigDecimal amount) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderId(UUID.randomUUID());
        p.setMethod(PaymentMethod.VNPAY);
        p.setAmount(amount);
        p.setStatus(PaymentStatus.PENDING);
        p.setVnpTxnRef("DH20260522-1-1716100000000");
        return p;
    }

    /** Builds a fully-signed IPN-style param map for a given payment + outcome. */
    private Map<String, String> signedIpnParams(Payment p, String responseCode, String txStatus, String txNo) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode",          "TEST01");
        params.put("vnp_Amount",           p.getAmount().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_BankCode",         "NCB");
        params.put("vnp_BankTranNo",       "VNP" + txNo);
        params.put("vnp_CardType",         "ATM");
        params.put("vnp_OrderInfo",        "Thanh toan don hang " + p.getVnpTxnRef()
                                              .substring(0, p.getVnpTxnRef().lastIndexOf('-')));
        params.put("vnp_PayDate",          "20260522170000");
        params.put("vnp_ResponseCode",     responseCode);
        params.put("vnp_TransactionNo",    txNo);
        params.put("vnp_TransactionStatus",txStatus);
        params.put("vnp_TxnRef",           p.getVnpTxnRef());
        String hash = sig.buildHashAndQuery(params).hash();
        params.put("vnp_SecureHash", hash);
        return params;
    }

    private Map<String, String> stripHash(Map<String, String> in) {
        Map<String, String> out = new HashMap<>(in);
        out.remove("vnp_SecureHash");
        out.remove("vnp_SecureHashType");
        return out;
    }

    // Mockito argument matcher shortcut
    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
