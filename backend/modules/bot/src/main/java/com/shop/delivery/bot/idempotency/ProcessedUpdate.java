package com.shop.delivery.bot.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_update")
public class ProcessedUpdate {

    @Id
    @Column(name = "update_id")
    private Long updateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedUpdate() { /* JPA */ }

    public ProcessedUpdate(Long updateId) {
        this.updateId = updateId;
    }

    public Long getUpdateId() { return updateId; }
    public Instant getProcessedAt() { return processedAt; }
}
