package com.shop.delivery.bot.support;

import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

public final class UpdateFixtures {

    private UpdateFixtures() {}

    public static Update textMessage(long updateId, long userId, String username, String text) {
        Update u = new Update();
        u.setUpdateId((int) updateId);

        User from = new User();
        from.setId(userId);
        from.setIsBot(false);
        from.setFirstName("Test");
        from.setLastName("User");
        from.setUserName(username);
        from.setLanguageCode("vi");

        Chat chat = new Chat();
        chat.setId(userId);
        chat.setType("private");

        Message msg = new Message();
        msg.setMessageId((int) updateId);
        msg.setFrom(from);
        msg.setChat(chat);
        msg.setText(text);
        msg.setDate((int) (System.currentTimeMillis() / 1000));

        u.setMessage(msg);
        return u;
    }
}
