package com.shop.delivery.order.service;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.ASSIGNED, EnumSet.of(OrderStatus.DELIVERING, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.DELIVERING, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.RETURNED));
        ALLOWED.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.RETURNED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean isAllowed(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class)).contains(to);
    }

    public void requireAllowed(OrderStatus from, OrderStatus to) {
        if (!isAllowed(from, to)) {
            throw new BusinessRuleException(
                "INVALID_STATUS_TRANSITION",
                "Không thể chuyển từ " + from + " sang " + to);
        }
    }
}
