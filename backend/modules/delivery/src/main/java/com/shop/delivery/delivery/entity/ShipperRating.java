package com.shop.delivery.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.UUID;

/**
 * Shipper's rating of a customer after order DELIVERED. Counterpart to
 * {@link Rating} (customer→shipper). One row per order (UNIQUE order_id).
 * Append-only — comment may be edited later by the shipper.
 *
 * <p>The {@code telegram_user.customer_rating_avg / customer_rating_count}
 * columns are derived from this table and recomputed by
 * {@code ShipperRatingService.rateCustomer(...)}. Do not update them elsewhere.
 */
@Entity
@Table(name = "shipper_rating")
@Check(constraints = "stars BETWEEN 1 AND 5")
public class ShipperRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "shipper_id", nullable = false)
    private Long shipperId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "stars", nullable = false)
    private Short stars;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public Long getShipperId() { return shipperId; }
    public void setShipperId(Long shipperId) { this.shipperId = shipperId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Short getStars() { return stars; }
    public void setStars(Short stars) { this.stars = stars; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
