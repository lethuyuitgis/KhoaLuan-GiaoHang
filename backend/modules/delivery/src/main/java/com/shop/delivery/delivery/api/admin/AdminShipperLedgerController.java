package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.api.admin.dto.BalanceResponse;
import com.shop.delivery.delivery.api.admin.dto.SettleRequest;
import com.shop.delivery.delivery.api.shipper.dto.LedgerRow;
import com.shop.delivery.delivery.domain.LedgerEntryType;
import com.shop.delivery.delivery.entity.ShipperLedgerEntry;
import com.shop.delivery.delivery.repository.ShipperLedgerRepository;
import com.shop.delivery.delivery.service.ShipperLedgerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/admin/shippers/{id}")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminShipperLedgerController {

    private final ShipperLedgerService service;
    private final ShipperLedgerRepository repo;

    public AdminShipperLedgerController(ShipperLedgerService service, ShipperLedgerRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    @GetMapping("/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        BigDecimal b = service.balance(id);
        var lastSettled = repo.findByShipperIdOrderByCreatedAtDesc(id, PageRequest.of(0, 100))
            .stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.SETTLEMENT_PAYOUT
                       || e.getEntryType() == LedgerEntryType.SETTLEMENT_DEPOSIT)
            .map(ShipperLedgerEntry::getCreatedAt)
            .findFirst().orElse(null);
        return new BalanceResponse(b, lastSettled);
    }

    @GetMapping("/ledger")
    public Page<LedgerRow> ledger(@PathVariable Long id, Pageable pageable) {
        return service.page(id, pageable).map(e ->
            new LedgerRow(e.getId(), e.getEntryType(), e.getAmount(),
                          e.getOrderId(), e.getNote(), e.getCreatedAt()));
    }

    @PostMapping("/settle")
    public LedgerRow settle(@PathVariable Long id,
                            @Valid @RequestBody SettleRequest req,
                            Authentication authn) {
        LedgerEntryType type = switch (req.type()) {
            case "PAYOUT"  -> LedgerEntryType.SETTLEMENT_PAYOUT;
            case "DEPOSIT" -> LedgerEntryType.SETTLEMENT_DEPOSIT;
            default        -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TYPE");
        };
        BigDecimal signed = type == LedgerEntryType.SETTLEMENT_PAYOUT
            ? req.amount().negate()
            : req.amount();
        String adminEmail = authn != null ? authn.getName() : "admin";
        var e = service.append(id, type, signed, null, req.note(), adminEmail);
        return new LedgerRow(e.getId(), e.getEntryType(), e.getAmount(),
                             e.getOrderId(), e.getNote(), e.getCreatedAt());
    }
}
