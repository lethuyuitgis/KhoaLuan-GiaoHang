package com.shop.delivery.delivery.entity;

import com.shop.delivery.delivery.domain.ChatRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One anonymous chat message relayed through the bot between the customer and
 * the shipper of a delivery. {@code senderUserId} is kept for audit only — it
 * is never revealed to the other party (the bot relays plain text prefixed with
 * the sender's role, not a forwarded message).
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "assignment_id", nullable = false, columnDefinition = "uuid")
    private UUID assignmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 16)
    private ChatRole senderRole;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }
    public ChatRole getSenderRole() { return senderRole; }
    public void setSenderRole(ChatRole senderRole) { this.senderRole = senderRole; }
    public Long getSenderUserId() { return senderUserId; }
    public void setSenderUserId(Long senderUserId) { this.senderUserId = senderUserId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
