package com.shop.delivery.bot.fsm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Per-user conversation state for multi-turn bot flows (FSM).
 *
 * <p>Backed by the {@code conversation_state} table created in V3__bot.sql.
 * Hibernate 6's {@code @JdbcTypeCode(SqlTypes.JSON)} maps {@code Map<String,Object>}
 * to a Postgres {@code jsonb} column natively — no extra dep needed.
 *
 * <p>P8 introduces the first FSM (rating comment capture). Future flows should
 * reuse this entity and the {@link ConversationStateService} API rather than
 * creating their own state tables.
 */
@Entity
@Table(name = "conversation_state")
public class ConversationState {

    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "state", nullable = false, length = 64)
    private String state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long v) { this.telegramUserId = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> v) { this.data = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
