package com.shop.delivery.bot.handler.common;

import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.bot.support.UpdateFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StartHandlerTest {

    @Mock TelegramUserService userService;
    @Mock BotSender sender;

    @InjectMocks StartHandler handler;

    @Test
    void handleShouldRegisterUserAndSendWelcomeText() {
        Update update = UpdateFixtures.textMessage(1L, 1001L, "alice", "/start");

        handler.handle(update);

        ArgumentCaptor<TelegramUserUpsertCommand> userCaptor = ArgumentCaptor.forClass(TelegramUserUpsertCommand.class);
        verify(userService).registerOrUpdate(userCaptor.capture());
        assertThat(userCaptor.getValue().id()).isEqualTo(1001L);
        assertThat(userCaptor.getValue().username()).isEqualTo("alice");
        assertThat(userCaptor.getValue().firstName()).isEqualTo("Test");
        assertThat(userCaptor.getValue().languageCode()).isEqualTo("vi");

        ArgumentCaptor<Long> chatIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(chatIdCaptor.capture(), textCaptor.capture());
        assertThat(chatIdCaptor.getValue()).isEqualTo(1001L);
        assertThat(textCaptor.getValue()).contains("Chào");
    }

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
}
