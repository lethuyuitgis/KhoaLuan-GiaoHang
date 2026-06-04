package com.shop.delivery.order.entity;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", length = 128)
    private String customerName;

    @Column(name = "customer_phone", length = 32)
    private String customerPhone;

    @Column(name = "pickup_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "delivery_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal deliveryLat;

    @Column(name = "delivery_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal deliveryLng;

    @Column(name = "distance_km", nullable = false, precision = 8, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "discount_products", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountProducts = BigDecimal.ZERO;

    @Column(name = "discount_shipping", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountShipping = BigDecimal.ZERO;

    @Column(name = "delivery_fee_original", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFeeOriginal;

    @Column(name = "shipper_commission", precision = 12, scale = 2)
    private BigDecimal shipperCommission;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public BigDecimal getPickupLat() { return pickupLat; }
    public void setPickupLat(BigDecimal pickupLat) { this.pickupLat = pickupLat; }
    public BigDecimal getPickupLng() { return pickupLng; }
    public void setPickupLng(BigDecimal pickupLng) { this.pickupLng = pickupLng; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public BigDecimal getDeliveryLat() { return deliveryLat; }
    public void setDeliveryLat(BigDecimal deliveryLat) { this.deliveryLat = deliveryLat; }
    public BigDecimal getDeliveryLng() { return deliveryLng; }
    public void setDeliveryLng(BigDecimal deliveryLng) { this.deliveryLng = deliveryLng; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public BigDecimal getDiscountProducts() { return discountProducts; }
    public void setDiscountProducts(BigDecimal discountProducts) { this.discountProducts = discountProducts; }
    public BigDecimal getDiscountShipping() { return discountShipping; }
    public void setDiscountShipping(BigDecimal discountShipping) { this.discountShipping = discountShipping; }
    public BigDecimal getDeliveryFeeOriginal() { return deliveryFeeOriginal; }
    public void setDeliveryFeeOriginal(BigDecimal deliveryFeeOriginal) { this.deliveryFeeOriginal = deliveryFeeOriginal; }
    public BigDecimal getShipperCommission() { return shipperCommission; }
    public void setShipperCommission(BigDecimal v) { this.shipperCommission = v; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
