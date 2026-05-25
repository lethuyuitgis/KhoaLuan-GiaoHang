package com.shop.delivery.order.service;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderItemRepository;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.repository.StatusHistoryRepository;
import com.shop.delivery.order.config.ShopConfigProperties;
import com.shop.delivery.shared.event.OrderConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceConfirmAfterPaymentTest {

    @Mock OrderRepository orderRepo;
    @Mock OrderItemRepository orderItemRepo;
    @Mock StatusHistoryRepository historyRepo;
    @Mock ProductService productSvc;
    @Mock ShopConfigProperties shopProps;
    @Mock DistanceCalculator distance;
    @Mock FeeCalculator fee;
    @Mock OrderCodeGenerator codeGen;
    @Mock ApplicationEventPublisher events;

    private OrderStateMachine stateMachine;
    private OrderService svc;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine(); // real instance — exercises whitelist
        svc = new OrderService(orderRepo, orderItemRepo, historyRepo, productSvc, shopProps,
            distance, fee, stateMachine, codeGen, events);
    }

    @Test
    void confirmAfterPayment_pendingOrder_transitionsToConfirmedAndPublishesEvent() {
        Order o = vnpayPendingOrder();
        when(orderRepo.findById(o.getId())).thenReturn(Optional.of(o));
        when(orderRepo.save(o)).thenReturn(o);

        svc.confirmAfterPayment(o.getId());

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        ArgumentCaptor<OrderConfirmedEvent> ev = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().orderId()).isEqualTo(o.getId());
        assertThat(ev.getValue().orderCode()).isEqualTo(o.getCode());
        assertThat(ev.getValue().paymentMethod()).isEqualTo("VNPAY");
    }

    @Test
    void confirmAfterPayment_alreadyConfirmedOrder_setsPaymentSuccessButNoTransitionNoEvent() {
        // Idempotency: IPN arrived twice (rare but possible) — should not re-emit.
        Order o = vnpayPendingOrder();
        o.setStatus(OrderStatus.CONFIRMED);
        when(orderRepo.findById(o.getId())).thenReturn(Optional.of(o));
        when(orderRepo.save(o)).thenReturn(o);

        svc.confirmAfterPayment(o.getId());

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(events, never()).publishEvent(any(OrderConfirmedEvent.class));
    }

    @Test
    void confirmAfterPayment_cancelledOrder_doesNothing() {
        // Edge: customer cancelled AFTER paying. Don't resurrect.
        Order o = vnpayPendingOrder();
        o.setStatus(OrderStatus.CANCELLED);
        when(orderRepo.findById(o.getId())).thenReturn(Optional.of(o));

        svc.confirmAfterPayment(o.getId());

        // payment_status still updated (audit trail) but order stays cancelled.
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events, never()).publishEvent(any());
    }

    private Order vnpayPendingOrder() {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH20260522-9");
        o.setCustomerId(123L);
        o.setTotal(new BigDecimal("250000.00"));
        o.setPaymentMethod(PaymentMethod.VNPAY);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setStatus(OrderStatus.PENDING);
        return o;
    }

    private static <T> T any(Class<T> c) { return org.mockito.ArgumentMatchers.any(c); }
    private static Object any() { return org.mockito.ArgumentMatchers.any(); }
}
