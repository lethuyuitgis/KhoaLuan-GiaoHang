package com.shop.delivery.notification;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RatingPromptBuilderTest {

    private final RatingPromptBuilder builder = new RatingPromptBuilder();

    @Test
    void build_returnsKeyboardWithFiveStarsAndSkip() {
        UUID orderId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        InlineKeyboardMarkup kb = builder.build(orderId);

        List<List<InlineKeyboardButton>> rows = kb.getKeyboard();
        assertThat(rows).hasSize(2);

        List<InlineKeyboardButton> starRow = rows.get(0);
        assertThat(starRow).hasSize(5);
        for (int i = 0; i < 5; i++) {
            int expectedStars = i + 1;
            assertThat(starRow.get(i).getText()).isEqualTo("⭐".repeat(expectedStars));
            assertThat(starRow.get(i).getCallbackData())
                .isEqualTo("RATE:" + orderId + ":" + expectedStars);
            assertThat(starRow.get(i).getCallbackData().getBytes().length)
                .as("callback_data must be ≤ 64 bytes (Telegram limit)")
                .isLessThanOrEqualTo(64);
        }

        List<InlineKeyboardButton> skipRow = rows.get(1);
        assertThat(skipRow).hasSize(1);
        assertThat(skipRow.get(0).getText()).isEqualTo("Bỏ qua");
        assertThat(skipRow.get(0).getCallbackData()).isEqualTo("RATE_SKIP:" + orderId);
    }
}
