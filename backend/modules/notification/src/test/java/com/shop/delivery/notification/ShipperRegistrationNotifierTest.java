package com.shop.delivery.notification;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.bot.config.BotProperties;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.shared.event.ShipperApprovedEvent;
import com.shop.delivery.shared.event.ShipperRegisteredEvent;
import com.shop.delivery.shared.event.ShipperRejectedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperRegistrationNotifierTest {

    @Mock BotSender bot;
    @Mock UserRoleRepository roleRepo;
    @Mock BotProperties botProps;

    @InjectMocks ShipperRegistrationNotifier notifier;

    @Test
    void onShipperRegistered_broadcastsToActiveAdmins() {
        UserRole admin1 = active(101L, Role.SHOP_OWNER);
        UserRole admin2 = active(102L, Role.SHOP_OWNER);
        UserRole shipperRole = active(999L, Role.SHIPPER); // should be filtered out
        when(roleRepo.findAll()).thenReturn(List.of(admin1, admin2, shipperRole));

        notifier.onShipperRegistered(new ShipperRegisteredEvent(
            5555L, "Nguyễn Văn A", "MOTORBIKE", "29A-12345"));

        ArgumentCaptor<Long> chatCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(bot, org.mockito.Mockito.times(2)).sendText(chatCap.capture(), textCap.capture());
        assertThat(chatCap.getAllValues()).containsExactlyInAnyOrder(101L, 102L);
        String msg = textCap.getAllValues().get(0);
        assertThat(msg).contains("Nguyễn Văn A").contains("29A-12345").contains("Xe máy");
    }

    @Test
    void onShipperApproved_withMiniappUrl_sendsWebAppButton() {
        when(botProps.getMiniappUrl()).thenReturn("https://app.example.com");

        notifier.onShipperApproved(new ShipperApprovedEvent(5555L));

        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(msgCap.capture());
        SendMessage msg = msgCap.getValue();
        assertThat(msg.getChatId()).isEqualTo("5555");
        assertThat(msg.getText()).contains("được duyệt");
        assertThat(msg.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
        InlineKeyboardMarkup kb = (InlineKeyboardMarkup) msg.getReplyMarkup();
        assertThat(kb.getKeyboard().get(0).get(0).getWebApp().getUrl()).isEqualTo("https://app.example.com");
        verify(bot, never()).sendText(eq(5555L), anyString());
    }

    @Test
    void onShipperApproved_noMiniappUrl_fallsBackToPlainText() {
        when(botProps.getMiniappUrl()).thenReturn(null);

        notifier.onShipperApproved(new ShipperApprovedEvent(5555L));

        verify(bot).sendText(eq(5555L), anyString());
    }

    @Test
    void onShipperRejected_dmsShipperToReRegister() {
        notifier.onShipperRejected(new ShipperRejectedEvent(5555L));

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(bot).sendText(eq(5555L), textCap.capture());
        assertThat(textCap.getValue()).contains("/start");
    }

    private static UserRole active(long telegramId, Role role) {
        UserRole r = new UserRole();
        r.setTelegramUserId(telegramId);
        r.setRole(role);
        r.setStatus(UserRoleStatus.ACTIVE);
        return r;
    }
}
