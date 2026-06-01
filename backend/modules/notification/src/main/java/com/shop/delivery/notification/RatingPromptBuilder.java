package com.shop.delivery.notification;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the inline keyboard sent to the customer after an order is DELIVERED.
 * Layout:
 *   Row 1: [⭐] [⭐⭐] [⭐⭐⭐] [⭐⭐⭐⭐] [⭐⭐⭐⭐⭐]
 *   Row 2: [Bỏ qua]
 *
 * <p>callback_data format:
 *   - {@code RATE:<orderId>:<stars>}  (43 bytes — well under 64-byte Telegram limit)
 *   - {@code RATE_SKIP:<orderId>}     (46 bytes)
 */
@Component
public class RatingPromptBuilder {

    public InlineKeyboardMarkup build(UUID orderId) {
        List<InlineKeyboardButton> starRow = new ArrayList<>(5);
        for (int s = 1; s <= 5; s++) {
            starRow.add(InlineKeyboardButton.builder()
                .text("⭐".repeat(s))
                .callbackData("RATE:" + orderId + ":" + s)
                .build());
        }
        InlineKeyboardButton skip = InlineKeyboardButton.builder()
            .text("Bỏ qua")
            .callbackData("RATE_SKIP:" + orderId)
            .build();

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(List.of(starRow, List.of(skip)));
        return kb;
    }
}
