package com.shop.delivery.bot.router;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.handler.common.UnknownCommandHandler;
import com.shop.delivery.bot.idempotency.ProcessedUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
public class UpdateRouter {

    private static final Logger log = LoggerFactory.getLogger(UpdateRouter.class);

    private final List<UpdateHandler> handlers;
    private final UnknownCommandHandler fallback;
    private final ProcessedUpdateService processedUpdateService;

    public UpdateRouter(List<UpdateHandler> handlers,
                        UnknownCommandHandler fallback,
                        ProcessedUpdateService processedUpdateService) {
        // Filter out fallback từ injected list để tránh đệ quy
        this.handlers = handlers.stream()
            .filter(h -> !(h instanceof UnknownCommandHandler))
            .toList();
        this.fallback = fallback;
        this.processedUpdateService = processedUpdateService;
    }

    public void route(Update update) {
        Long updateId = (long) update.getUpdateId();
        if (!processedUpdateService.markIfNew(updateId)) {
            log.debug("Skip duplicate update_id={}", updateId);
            return;
        }

        try {
            for (UpdateHandler h : handlers) {
                if (h.canHandle(update)) {
                    h.handle(update);
                    return;
                }
            }
            fallback.handle(update);
        } catch (Exception e) {
            log.error("Unhandled error processing update_id={}", updateId, e);
            // Không re-throw; webhook reply 200 OK để Telegram không retry vô tận
        }
    }
}
