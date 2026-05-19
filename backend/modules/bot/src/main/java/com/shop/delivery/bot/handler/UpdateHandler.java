package com.shop.delivery.bot.handler;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface UpdateHandler {

    /** Trả về true nếu handler có thể xử lý update này. UpdateRouter sẽ gọi sequentially. */
    boolean canHandle(Update update);

    /** Xử lý update. Side effects: gọi BotSender, gọi services khác. Không throw — log và quay lại. */
    void handle(Update update);
}
