package com.shop.delivery.payment.api;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.payment.api.dto.CreatePaymentRequest;
import com.shop.delivery.payment.api.dto.CreatePaymentResponse;
import com.shop.delivery.payment.api.dto.IpnResponse;
import com.shop.delivery.payment.api.dto.ReturnRedirect;
import com.shop.delivery.payment.service.VnpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * VNPay HTTP endpoints. URL paths are fixed by the design spec — do not change.
 *
 * <ul>
 *   <li>{@code POST /api/payment/vnpay/create} — Telegram-authenticated, returns JSON.</li>
 *   <li>{@code GET  /api/payment/vnpay/return} — public, returns 302 redirect.</li>
 *   <li>{@code POST /api/payment/vnpay/ipn}    — public, returns JSON with VNPay's
 *       required {@code RspCode}/{@code Message} shape. Always 200 OK (never 4xx/5xx)
 *       — VNPay treats non-200 as failed delivery and may retry.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/payment/vnpay")
public class VnpayController {

    private final VnpayPaymentService svc;

    public VnpayController(VnpayPaymentService svc) {
        this.svc = svc;
    }

    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CreatePaymentResponse create(@Valid @RequestBody CreatePaymentRequest req,
                                        @CurrentUser TelegramUser user,
                                        HttpServletRequest http) {
        String ip = resolveClientIp(http);
        return svc.createPayment(req.orderId(), user.getId(), ip);
    }

    @GetMapping("/return")
    public ResponseEntity<Void> returnFromVnpay(@RequestParam Map<String, String> rawParams,
                                                HttpServletRequest http) {
        // @RequestParam Map decoded values for us; collect query params from the
        // request as-is (in case duplicates appear, last-wins is fine for VNPay).
        Map<String, String> params = collectQueryParams(http);
        ReturnRedirect r = svc.handleReturn(params);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(r.redirectUrl()))
            .build();
    }

    @PostMapping(value = "/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public IpnResponse ipn(HttpServletRequest http) {
        Map<String, String> params = collectQueryParams(http);
        return svc.handleIpn(params);
    }

    /**
     * Pull every query param into a Map. VNPay sends IPN as form-encoded GET-style
     * params in either query string or POST body — Spring exposes both via
     * {@code getParameterNames}.
     */
    private static Map<String, String> collectQueryParams(HttpServletRequest http) {
        Map<String, String> out = new HashMap<>();
        Enumeration<String> names = http.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = http.getParameter(name);
            if (value != null) out.put(name, value);
        }
        return out;
    }

    private static String resolveClientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // First entry is the original client
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String remote = http.getRemoteAddr();
        return remote == null || remote.isBlank() ? "127.0.0.1" : remote;
    }
}
