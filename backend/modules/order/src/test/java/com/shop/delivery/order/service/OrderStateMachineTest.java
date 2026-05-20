package com.shop.delivery.order.service;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    OrderStateMachine sm = new OrderStateMachine();

    @Test
    void pendingToConfirmedShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.PENDING, OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    void pendingToCancelledShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.PENDING, OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void confirmedToAssignedShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.CONFIRMED, OrderStatus.ASSIGNED)).isTrue();
    }

    @Test
    void assignedToDeliveringShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.ASSIGNED, OrderStatus.DELIVERING)).isTrue();
    }

    @Test
    void deliveringToDeliveredShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.DELIVERING, OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void deliveredToCancelledShouldNotBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.DELIVERED, OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void cancelledToConfirmedShouldNotBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.CANCELLED, OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void pendingToDeliveredShouldNotBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.PENDING, OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void requireTransitionShouldThrowOnInvalid() {
        assertThatThrownBy(() -> sm.requireAllowed(OrderStatus.DELIVERED, OrderStatus.CANCELLED))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("DELIVERED")
            .hasMessageContaining("CANCELLED");
    }
}
