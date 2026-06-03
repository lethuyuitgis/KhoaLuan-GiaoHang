package com.shop.delivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Singleton shop configuration row. Exactly one row (id = 1) — enforced at
 * the DB level by a CHECK constraint. Holds admin-editable brand and fee
 * fields so the same app can serve any shop without code changes.
 *
 * Note: deliberately does NOT extend BaseEntity. The singleton row is seeded
 * by Flyway (V13) and never has a meaningful "created_at" lifecycle — only
 * updates matter, so we keep just an updated_at column.
 */
@Entity
@Table(name = "shop_config")
public class ShopConfig {

    @Id
    @Column(name = "id")
    private Short id = 1;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "tagline", nullable = false, length = 256)
    private String tagline;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "brand_primary", nullable = false, length = 7)
    private String brandPrimary;

    @Column(name = "brand_secondary", nullable = false, length = 7)
    private String brandSecondary;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "contact_email", length = 128)
    private String contactEmail;

    @Column(name = "opening_hours", length = 64)
    private String openingHours;

    @Column(name = "pickup_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Column(name = "pickup_address", nullable = false, length = 256)
    private String pickupAddress;

    @Column(name = "fee_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeBase;

    @Column(name = "fee_per_km", nullable = false, precision = 12, scale = 2)
    private BigDecimal feePerKm;

    @Column(name = "free_km", nullable = false, precision = 8, scale = 3)
    private BigDecimal freeKm;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getBrandPrimary() { return brandPrimary; }
    public void setBrandPrimary(String brandPrimary) { this.brandPrimary = brandPrimary; }
    public String getBrandSecondary() { return brandSecondary; }
    public void setBrandSecondary(String brandSecondary) { this.brandSecondary = brandSecondary; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }
    public BigDecimal getPickupLat() { return pickupLat; }
    public void setPickupLat(BigDecimal pickupLat) { this.pickupLat = pickupLat; }
    public BigDecimal getPickupLng() { return pickupLng; }
    public void setPickupLng(BigDecimal pickupLng) { this.pickupLng = pickupLng; }
    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }
    public BigDecimal getFeeBase() { return feeBase; }
    public void setFeeBase(BigDecimal feeBase) { this.feeBase = feeBase; }
    public BigDecimal getFeePerKm() { return feePerKm; }
    public void setFeePerKm(BigDecimal feePerKm) { this.feePerKm = feePerKm; }
    public BigDecimal getFreeKm() { return freeKm; }
    public void setFreeKm(BigDecimal freeKm) { this.freeKm = freeKm; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
