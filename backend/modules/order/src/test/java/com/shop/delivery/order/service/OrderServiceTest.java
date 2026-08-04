package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.entity.OrderItem;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.entity.StatusHistory;
import com.shop.delivery.order.repository.OrderItemRepository;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.repository.StatusHistoryRepository;
import com.shop.delivery.order.service.command.CreateOrderCommand;
import com.shop.delivery.order.service.command.OrderLineCommand;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepo;
    @Mock OrderItemRepository orderItemRepo;
    @Mock StatusHistoryRepository statusHistoryRepo;
    @Mock ProductService productService;
    @Mock ApplicationEventPublisher events;

    OrderService service;
    ShopConfigProperties shopProps;
    DistanceCalculator distance;
    FeeCalculator fee;
    OrderStateMachine sm;
    OrderCodeGenerator codeGen;

    @BeforeEach
    void setup() {
        shopProps = new ShopConfigProperties();
        shopProps.getPickup().setLat(new BigDecimal("21.0285"));
        shopProps.getPickup().setLng(new BigDecimal("105.8542"));
        shopProps.getFee().setBase(new BigDecimal("15000"));
        shopProps.getFee().setPerKm(new BigDecimal("5000"));
        shopProps.getFee().setFreeKm(BigDecimal.ZERO);

        distance = new DistanceCalculator();
        fee = new FeeCalculator(shopProps);
        sm = new OrderStateMachine();
        codeGen = new OrderCodeGenerator();

        service = new OrderService(orderRepo, orderItemRepo, statusHistoryRepo,
            productService, shopProps, distance, fee, sm, codeGen, events, null);
    }

    @Test
    void createOrderShouldComputeSubtotalDistanceFeeTotal() {
        Product p1 = makeProduct(1L, "Áo", new BigDecimal("100000"), 100);
        Product p2 = makeProduct(2L, "Quần", new BigDecimal("200000"), 50);
        when(productService.findById(1L)).thenReturn(p1);
        when(productService.findById(2L)).thenReturn(p2);
        when(orderRepo.existsByCode(any())).thenReturn(false);
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand cmd = new CreateOrderCommand(
            555L, "Bob", "+84900111222",
            "45 Bà Triệu",
            new BigDecimal("21.0193"), new BigDecimal("105.8503"),
            List.of(new OrderLineCommand(1L, 2), new OrderLineCommand(2L, 1)),
            PaymentMethod.COD,
            "Giao tối",
            null, null
        );

        Order saved = service.create(cmd);

        // subtotal = 2*100000 + 1*200000 = 400000
        assertThat(saved.getSubtotal()).isEqualByComparingTo("400000");
        // distance ~1.07 km
        assertThat(saved.getDistanceKm().doubleValue()).isBetween(0.8, 1.5);
        assertThat(saved.getDeliveryFee()).isGreaterThanOrEqualTo(new BigDecimal("15000"));
        assertThat(saved.getTotal()).isEqualByComparingTo(saved.getSubtotal().add(saved.getDeliveryFee()));
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getCode()).matches("DH\\d{8}-[A-Z0-9]{5}");

        verify(orderItemRepo).saveAll(any());
        verify(statusHistoryRepo).save(any(StatusHistory.class));
    }

    @Test
    void confirmShouldTransitionPendingToConfirmed() {
        UUID id = UUID.randomUUID();
        Order o = makeOrder(id, OrderStatus.PENDING);
        when(orderRepo.findById(id)).thenReturn(Optional.of(o));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirm(id, 999L, "Admin OK");

        ArgumentCaptor<Order> orderCap = ArgumentCaptor.forClass(Order.class);
        verify(orderRepo).save(orderCap.capture());
        assertThat(orderCap.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        ArgumentCaptor<StatusHistory> histCap = ArgumentCaptor.forClass(StatusHistory.class);
        verify(statusHistoryRepo).save(histCap.capture());
        assertThat(histCap.getValue().getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(histCap.getValue().getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(histCap.getValue().getChangedByUserId()).isEqualTo(999L);
        assertThat(histCap.getValue().getNote()).isEqualTo("Admin OK");
    }

    @Test
    void cancelShouldRejectWhenStatusIsDelivered() {
        UUID id = UUID.randomUUID();
        Order o = makeOrder(id, OrderStatus.DELIVERED);
        when(orderRepo.findById(id)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.cancel(id, 555L, "khách đổi ý"))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void findByIdShouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(orderRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listHistoryShouldReturnEntriesOrderedForExistingOrder() {
        UUID id = UUID.randomUUID();
        when(orderRepo.findById(id)).thenReturn(Optional.of(makeOrder(id, OrderStatus.CONFIRMED)));
        StatusHistory h1 = historyEntry(id, null, OrderStatus.PENDING);
        StatusHistory h2 = historyEntry(id, OrderStatus.PENDING, OrderStatus.CONFIRMED);
        when(statusHistoryRepo.findAllByOrderIdOrderByChangedAtAsc(id)).thenReturn(List.of(h1, h2));

        List<StatusHistory> result = service.listHistory(id);

        assertThat(result).containsExactly(h1, h2);
    }

    @Test
    void listHistoryShouldThrowWhenOrderMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listHistory(id))
            .isInstanceOf(NotFoundException.class);
    }

    private StatusHistory historyEntry(UUID orderId, OrderStatus from, OrderStatus to) {
        StatusHistory h = new StatusHistory();
        h.setOrderId(orderId);
        h.setFromStatus(from);
        h.setToStatus(to);
        return h;
    }

    private Product makeProduct(Long id, String name, BigDecimal price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStock(stock);
        p.setActive(true);
        return p;
    }

    private Order makeOrder(UUID id, OrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setStatus(status);
        return o;
    }
}
