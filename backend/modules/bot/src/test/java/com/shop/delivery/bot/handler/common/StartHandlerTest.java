package com.shop.delivery.bot.handler.common;

import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import com.shop.delivery.bot.config.BotProperties;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.bot.support.UpdateFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartHandlerTest {

    @Mock TelegramUserService userService;
    @Mock TelegramUserRepository userRepo;
    @Mock BotSender sender;
    @Mock BotProperties botProps;

    @InjectMocks StartHandler handler;

    @Test
    void canHandleShouldReturnTrueForSlashStart() {
        Update update = UpdateFixtures.textMessage(1L, 1001L, "alice", "/start");
        assertThat(handler.canHandle(update)).isTrue();
    }

    @Test
    void canHandleShouldReturnFalseForOtherText() {
        Update update = UpdateFixtures.textMessage(1L, 1001L, "alice", "hello");
        assertThat(handler.canHandle(update)).isFalse();
    }

    @Test
    void handle_newUser_sendsRolePickerAndUpserts() {
        when(userRepo.existsById(1001L)).thenReturn(false);
        when(botProps.getMiniappUrl()).thenReturn(null); // no webapp → use callback button

        Update update = UpdateFixtures.textMessage(1L, 1001L, "alice", "/start");

        handler.handle(update);

        // Upsert command captured correctly
        ArgumentCaptor<TelegramUserUpsertCommand> upsertCap = ArgumentCaptor.forClass(TelegramUserUpsertCommand.class);
        verify(userService).registerOrUpdate(upsertCap.capture());
        assertThat(upsertCap.getValue().id()).isEqualTo(1001L);
        assertThat(upsertCap.getValue().username()).isEqualTo("alice");

        // Inline keyboard with role picker
        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(sender).execute(msgCap.capture());
        SendMessage msg = msgCap.getValue();
        assertThat(msg.getChatId()).isEqualTo("1001");
        assertThat(msg.getText()).contains("vai trò");

        assertThat(msg.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
        InlineKeyboardMarkup kb = (InlineKeyboardMarkup) msg.getReplyMarkup();
        List<List<InlineKeyboardButton>> rows = kb.getKeyboard();
        assertThat(rows).hasSize(2);

        // Row 1: customer button (callback, since no miniapp URL)
        InlineKeyboardButton custBtn = rows.get(0).get(0);
        assertThat(custBtn.getText()).contains("Đặt hàng");
        assertThat(custBtn.getCallbackData()).isEqualTo(StartHandler.CALLBACK_ROLE_CUSTOMER);
        assertThat(custBtn.getWebApp()).isNull();

        // Row 2: shipper button (always callback)
        InlineKeyboardButton shipBtn = rows.get(1).get(0);
        assertThat(shipBtn.getText()).contains("shipper");
        assertThat(shipBtn.getCallbackData()).isEqualTo(StartHandler.CALLBACK_ROLE_SHIPPER);

        // No plain-text path
        verify(sender, never()).sendText(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_returningUser_sendsShortGreeting_noKeyboard() {
        when(userRepo.existsById(1001L)).thenReturn(true);

        Update update = UpdateFixtures.textMessage(1L, 1001L, "alice", "/start");

        handler.handle(update);

        verify(userService).registerOrUpdate(org.mockito.ArgumentMatchers.any(TelegramUserUpsertCommand.class));

        ArgumentCaptor<Long> chatCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(chatCap.capture(), textCap.capture());
        assertThat(chatCap.getValue()).isEqualTo(1001L);
        assertThat(textCap.getValue()).contains("trở lại");

        // No inline keyboard for returning user
        verify(sender, never()).execute(org.mockito.ArgumentMatchers.any(SendMessage.class));
    }

    @Test
    void handle_returningUser_withMiniappUrl_attachesWebAppButton() {
        // User cũ không nhận role picker — nếu /start chỉ trả text trơn thì
        // người chưa cấu hình Menu Button trong BotFather không còn đường vào app.
        when(userRepo.existsById(1001L)).thenReturn(true);
        when(botProps.getMiniappUrl()).thenReturn("https://example.com/miniapp");

        handler.handle(UpdateFixtures.textMessage(1L, 1001L, "alice", "/start"));

        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(sender).execute(msgCap.capture());
        assertThat(msgCap.getValue().getText()).contains("trở lại");
        InlineKeyboardMarkup kb = (InlineKeyboardMarkup) msgCap.getValue().getReplyMarkup();
        InlineKeyboardButton btn = kb.getKeyboard().get(0).get(0);
        assertThat(btn.getText()).contains("Đặt hàng");
        assertThat(btn.getWebApp().getUrl()).isEqualTo("https://example.com/miniapp");
        verify(sender, never()).sendText(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_newUser_withMiniappUrl_usesWebAppButton() {
        when(userRepo.existsById(1001L)).thenReturn(false);
        when(botProps.getMiniappUrl()).thenReturn("https://example.com/miniapp");

        Update update = UpdateFixtures.textMessage(1L, 1001L, "alice", "/start");

        handler.handle(update);

        ArgumentCaptor<SendMessage> msgCap = ArgumentCaptor.forClass(SendMessage.class);
        verify(sender).execute(msgCap.capture());
        InlineKeyboardMarkup kb = (InlineKeyboardMarkup) msgCap.getValue().getReplyMarkup();
        InlineKeyboardButton custBtn = kb.getKeyboard().get(0).get(0);
        assertThat(custBtn.getWebApp()).isNotNull();
        assertThat(custBtn.getWebApp().getUrl()).isEqualTo("https://example.com/miniapp");
        assertThat(custBtn.getCallbackData()).isNull();
    }
}
