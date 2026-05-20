package com.shop.delivery.order.api.mapper;

import com.shop.delivery.order.api.dto.OrderItemResponse;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.entity.OrderItem;
import com.shop.delivery.order.repository.OrderItemRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    private final OrderItemRepository itemRepo;

    public OrderMapper(OrderItemRepository itemRepo) {
        this.itemRepo = itemRepo;
    }

    public OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items = itemRepo.findAllByOrderId(o.getId()).stream()
            .map(this::toItemResponse)
            .toList();
        return new OrderResponse(
            o.getId(), o.getCode(), o.getCustomerId(), o.getCustomerName(), o.getCustomerPhone(),
            o.getDeliveryAddress(), o.getDeliveryLat(), o.getDeliveryLng(),
            o.getDistanceKm(), o.getSubtotal(), o.getDeliveryFee(), o.getTotal(),
            o.getPaymentMethod(), o.getPaymentStatus(), o.getStatus(), o.getNote(),
            o.getCreatedAt(), items
        );
    }

    public OrderSummary toSummary(Order o) {
        return new OrderSummary(
            o.getId(), o.getCode(), o.getTotal(), o.getStatus(),
            o.getPaymentMethod(), o.getPaymentStatus(), o.getCreatedAt()
        );
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(),
            item.getQuantity(), item.getUnitPrice(), item.getSubtotal());
    }
}
