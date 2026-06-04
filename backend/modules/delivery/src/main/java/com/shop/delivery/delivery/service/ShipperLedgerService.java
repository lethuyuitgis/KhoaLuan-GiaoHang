package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.LedgerEntryType;
import com.shop.delivery.delivery.entity.ShipperLedgerEntry;
import com.shop.delivery.delivery.repository.ShipperLedgerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ShipperLedgerService {

    private final ShipperLedgerRepository repo;

    public ShipperLedgerService(ShipperLedgerRepository repo) { this.repo = repo; }

    @Transactional
    public ShipperLedgerEntry append(Long shipperId, LedgerEntryType type,
                                     BigDecimal amount, UUID orderId,
                                     String note, String createdBy) {
        ShipperLedgerEntry e = new ShipperLedgerEntry();
        e.setShipperId(shipperId);
        e.setEntryType(type);
        e.setAmount(amount);
        e.setOrderId(orderId);
        e.setNote(note);
        e.setCreatedBy(createdBy);
        return repo.save(e);
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(Long shipperId) {
        BigDecimal b = repo.sumAmountByShipperId(shipperId);
        return b == null ? BigDecimal.ZERO : b;
    }

    @Transactional(readOnly = true)
    public Page<ShipperLedgerEntry> page(Long shipperId, Pageable pageable) {
        return repo.findByShipperIdOrderByCreatedAtDesc(shipperId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ShipperLedgerEntry> range(Long shipperId, OffsetDateTime from, OffsetDateTime to) {
        return repo.findByShipperIdAndCreatedAtBetweenOrderByCreatedAtDesc(shipperId, from, to);
    }

    @Transactional(readOnly = true)
    public boolean alreadyHasEntry(UUID orderId, LedgerEntryType type) {
        return repo.existsByOrderIdAndEntryType(orderId, type);
    }
}
