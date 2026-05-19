package com.shop.delivery.bot.router;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.handler.common.UnknownCommandHandler;
import com.shop.delivery.bot.idempotency.ProcessedUpdateService;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.bot.support.UpdateFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests use UpdateHandler interface mocks (not concrete handler classes) to avoid ByteBuddy
 * retransformation issues on newer JDKs. UnknownCommandHandler is a real instance backed by
 * a mock BotSender so we can verify it was invoked without mocking the concrete class.
 */
@ExtendWith(MockitoExtension.class)
class UpdateRouterTest {

    @Mock UpdateHandler startHandler;
    @Mock UpdateHandler helpHandler;
    @Mock BotSender botSender;
    @Mock ProcessedUpdateService processedUpdateService;

    @Test
    void shouldRouteToStartHandlerForSlashStart() {
        when(processedUpdateService.markIfNew(any())).thenReturn(true);
        when(startHandler.canHandle(any(Update.class))).thenReturn(true);

        UnknownCommandHandler fallback = new UnknownCommandHandler(botSender);
        UpdateRouter router = new UpdateRouter(
            List.of(startHandler, helpHandler),
            fallback,
            processedUpdateService
        );

        Update update = UpdateFixtures.textMessage(1L, 1001L, "u", "/start");
        router.route(update);

        verify(startHandler).handle(update);
        verify(helpHandler, never()).handle(any());
        verify(botSender, never()).sendText(any(), any());
    }

    @Test
    void shouldFallbackToUnknownCommandWhenNoHandlerCanHandle() {
        when(processedUpdateService.markIfNew(any())).thenReturn(true);
        when(startHandler.canHandle(any(Update.class))).thenReturn(false);
        when(helpHandler.canHandle(any(Update.class))).thenReturn(false);

        UnknownCommandHandler fallback = new UnknownCommandHandler(botSender);
        UpdateRouter router = new UpdateRouter(
            List.of(startHandler, helpHandler),
            fallback,
            processedUpdateService
        );

        Update update = UpdateFixtures.textMessage(1L, 1001L, "u", "/wat");
        router.route(update);

        // Fallback invoked → BotSender called with chatId and a hint text
        verify(botSender).sendText(any(), any());
        verify(startHandler, never()).handle(any());
        verify(helpHandler, never()).handle(any());
    }

    @Test
    void duplicateUpdateShouldBeSkipped() {
        when(processedUpdateService.markIfNew(any())).thenReturn(false);

        UnknownCommandHandler fallback = new UnknownCommandHandler(botSender);
        UpdateRouter router = new UpdateRouter(
            List.of(startHandler),
            fallback,
            processedUpdateService
        );

        router.route(UpdateFixtures.textMessage(1L, 1001L, "u", "/start"));

        verify(startHandler, never()).canHandle(any());
        verify(startHandler, never()).handle(any());
        verify(botSender, never()).sendText(any(), any());
    }
}
