package com.shop.delivery.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_ping")
public class LocationPing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "assignment_id", nullable = false, columnDefinition = "uuid")
    private UUID assignmentId;

    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "accuracy", precision = 8, scale = 2)
    private BigDecimal accuracy;

    @Column(name = "heading", precision = 5, scale = 2)
    private BigDecimal heading;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }
    public BigDecimal getLat() { return lat; }
    public void setLat(BigDecimal lat) { this.lat = lat; }
    public BigDecimal getLng() { return lng; }
    public void setLng(BigDecimal lng) { this.lng = lng; }
    public BigDecimal getAccuracy() { return accuracy; }
    public void setAccuracy(BigDecimal accuracy) { this.accuracy = accuracy; }
    public BigDecimal getHeading() { return heading; }
    public void setHeading(BigDecimal heading) { this.heading = heading; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
