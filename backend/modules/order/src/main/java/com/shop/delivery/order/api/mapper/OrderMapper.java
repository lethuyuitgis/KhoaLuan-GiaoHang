package com.shop.delivery.order.api.mapper;

import com.shop.delivery.order.api.dto.OrderItemResponse;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.entity.OrderItem;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.repository.OrderItemRepository;
import com.shop.delivery.order.repository.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

    private final OrderItemRepository itemRepo;
    private final ProductRepository productRepo;

    public OrderMapper(OrderItemRepository itemRepo, ProductRepository productRepo) {
        this.itemRepo = itemRepo;
        this.productRepo = productRepo;
    }

    public OrderResponse toResponse(Order o) {
        List<OrderItem> rawItems = itemRepo.findAllByOrderId(o.getId());
        List<Long> productIds = rawItems.stream().map(OrderItem::getProductId).distinct().toList();
        // Single-query preload — N+1 avoidance for OrderDetailPage and Mini App order detail.
        Map<Long, Product> productsById = productRepo.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderItemResponse> items = rawItems.stream()
            .map(item -> toItemResponse(item, productsById.get(item.getProductId())))
            .toList();

        return new OrderResponse(
            o.getId(), o.getCode(), o.getCustomerId(), o.getCustomerName(), o.getCustomerPhone(),
            o.getPickupLat(), o.getPickupLng(),
            o.getDeliveryAddress(), o.getDeliveryLat(), o.getDeliveryLng(),
            o.getDistanceKm(), o.getSubtotal(),
            o.getDiscountProducts(), o.getDiscountShipping(), o.getDeliveryFeeOriginal(),
            o.getDeliveryFee(), o.getTotal(),
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

    public OrderItemResponse toItemResponse(OrderItem item, Product product) {
        String name = product != null ? product.getName() : "SP #" + item.getProductId();
        String image = product != null ? product.getImageUrl() : null;
        return new OrderItemResponse(item.getId(), item.getProductId(), name, image,
            item.getQuantity(), item.getUnitPrice(), item.getSubtotal());
    }
}
