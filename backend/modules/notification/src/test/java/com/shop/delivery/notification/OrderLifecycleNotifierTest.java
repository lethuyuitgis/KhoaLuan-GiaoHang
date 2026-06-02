package com.shop.delivery.notification;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.shared.event.OrderConfirmedEvent;
import com.shop.delivery.shared.event.OrderCreatedEvent;
import com.shop.delivery.shared.event.PaymentFailedEvent;
import com.shop.delivery.shared.event.PaymentSucceededEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleNotifierTest {

    @Mock BotSender bot;
    @Mock UserRoleRepository roleRepo;
    @Mock OrderRepository orderRepo;

    @InjectMocks OrderLifecycleNotifier notifier;

    private static UserRole adminRole(long telegramId) {
        UserRole r = new UserRole();
        r.setTelegramUserId(telegramId);
        r.setRole(Role.SHOP_OWNER);
        r.setStatus(UserRoleStatus.ACTIVE);
        return r;
    }

    private static UserRole inactive(long telegramId) {
        UserRole r = adminRole(telegramId);
        r.setStatus(UserRoleStatus.BLOCKED);
        return r;
    }

    private static UserRole customerRole(long telegramId) {
        UserRole r = new UserRole();
        r.setTelegramUserId(telegramId);
        r.setRole(Role.CUSTOMER);
        r.setStatus(UserRoleStatus.ACTIVE);
        return r;
    }

    @Test
    void onOrderCreated_broadcastsToActiveAdmins_skipsCustomersAndInactive() {
        when(roleRepo.findAll()).thenReturn(List.of(
            adminRole(7001L),
            adminRole(7002L),
            customerRole(9000L),
            inactive(7003L)
        ));

        notifier.onOrderCreated(new OrderCreatedEvent(
            UUID.randomUUID(), "DH-1", 9000L, "COD"));

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(bot).sendText(eq(7001L), textCap.capture());
        verify(bot).sendText(eq(7002L), textCap.capture());
        verify(bot, never()).sendText(eq(9000L), org.mockito.ArgumentMatchers.anyString());
        verify(bot, never()).sendText(eq(7003L), org.mockito.ArgumentMatchers.anyString());

        assertThat(textCap.getValue()).contains("DH-1").contains("COD");
    }

    @Test
    void onOrderConfirmed_notifiesCustomerLookedUpFromRepo() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCode("DH-2");
        order.setCustomerId(9999L);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        notifier.onOrderConfirmed(new OrderConfirmedEvent(orderId, "DH-2", "COD"));

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(bot).sendText(eq(9999L), textCap.capture());
        assertThat(textCap.getValue()).contains("DH-2").contains("xác nhận");
    }

    @Test
    void onOrderConfirmed_missingOrder_doesNotSend() {
        UUID orderId = UUID.randomUUID();
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        notifier.onOrderConfirmed(new OrderConfirmedEvent(orderId, "DH-MISSING", "COD"));

        verifyNoInteractions(bot);
    }

    @Test
    void onPaymentSucceeded_broadcastsAmountToAdmins() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCode("DH-7");
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(roleRepo.findAll()).thenReturn(List.of(adminRole(7100L)));

        notifier.onPaymentSucceeded(new PaymentSucceededEvent(
            orderId, UUID.randomUUID(), new BigDecimal("175000"), "DH-7-abc"));

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(bot).sendText(eq(7100L), textCap.capture());
        assertThat(textCap.getValue()).contains("DH-7").contains("175000");
    }

    @Test
    void onPaymentFailed_tellsCustomerHumanReason() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCode("DH-9");
        order.setCustomerId(9001L);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        notifier.onPaymentFailed(new PaymentFailedEvent(
            orderId, UUID.randomUUID(), "DH-9-x", "EXPIRED"));

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(bot).sendText(eq(9001L), textCap.capture());
        assertThat(textCap.getValue())
            .contains("DH-9")
            .contains("Hết thời gian thanh toán");
    }

    @Test
    void onPaymentFailed_unknownCode_includesRawCode() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setCode("DH-10");
        order.setCustomerId(9002L);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        notifier.onPaymentFailed(new PaymentFailedEvent(
            orderId, UUID.randomUUID(), "DH-10-x", "99"));

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(bot).sendText(eq(9002L), textCap.capture());
        assertThat(textCap.getValue()).contains("99");
    }
}
