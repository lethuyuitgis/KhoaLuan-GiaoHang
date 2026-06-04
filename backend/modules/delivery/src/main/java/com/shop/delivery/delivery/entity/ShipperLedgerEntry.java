package com.shop.delivery.delivery.entity;

import com.shop.delivery.delivery.domain.LedgerEntryType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipper_ledger")
public class ShipperLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipper_id", nullable = false)
    private Long shipperId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private LedgerEntryType entryType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "order_id", columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public Long getShipperId() { return shipperId; }
    public void setShipperId(Long v) { this.shipperId = v; }
    public LedgerEntryType getEntryType() { return entryType; }
    public void setEntryType(LedgerEntryType v) { this.entryType = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID v) { this.orderId = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
