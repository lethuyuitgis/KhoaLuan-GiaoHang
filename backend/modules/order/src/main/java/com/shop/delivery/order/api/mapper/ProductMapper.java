package com.shop.delivery.order.api.mapper;

import com.shop.delivery.order.api.dto.ProductResponse;
import com.shop.delivery.order.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product p) {
        return new ProductResponse(
            p.getId(),
            p.getName(),
            p.getDescription(),
            p.getPrice(),
            p.getImageUrl(),
            p.getStock(),
            p.isActive(),
            p.getCreatedAt()
        );
    }
}
