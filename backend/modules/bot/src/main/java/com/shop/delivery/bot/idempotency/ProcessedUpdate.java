package com.shop.delivery.bot.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Idempotency key for Telegram updates. Always treated as NEW by Spring Data JPA
 * so that save() invokes persist() (not merge), causing DataIntegrityViolationException
 * on duplicate update_id — that's how ProcessedUpdateService detects duplicates.
 */
@Entity
@Table(name = "processed_update")
public class ProcessedUpdate implements Persistable<Long> {

    @Id
    @Column(name = "update_id")
    private Long updateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedUpdate() { /* JPA */ }

    public ProcessedUpdate(Long updateId) {
        this.updateId = updateId;
    }

    @Override
    public Long getId() {
        return updateId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return true;  // force persist() — see class doc
    }

    public Long getUpdateId() { return updateId; }
    public Instant getProcessedAt() { return processedAt; }
}
