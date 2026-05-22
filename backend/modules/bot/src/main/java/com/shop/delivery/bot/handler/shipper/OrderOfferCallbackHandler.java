package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.DeliveryAssignmentService;
import com.shop.delivery.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.UUID;

@Component
public class OrderOfferCallbackHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderOfferCallbackHandler.class);

    private final DeliveryAssignmentService service;
    private final BotSender sender;

    public OrderOfferCallbackHandler(DeliveryAssignmentService service, BotSender sender) {
        this.service = service;
        this.sender = sender;
    }

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasCallbackQuery()) return false;
        String data = update.getCallbackQuery().getData();
        return data != null && (data.startsWith("ACCEPT_ORDER:") || data.startsWith("REJECT_ORDER:"));
    }

    @Override
    public void handle(Update update) {
        CallbackQuery cb = update.getCallbackQuery();
        String data = cb.getData();
        Long shipperId = cb.getFrom().getId();
        Long chatId = cb.getMessage().getChatId();

        boolean accept = data.startsWith("ACCEPT_ORDER:");
        String idPart = data.substring(data.indexOf(':') + 1);

        try {
            UUID assignmentId = UUID.fromString(idPart);
            if (accept) {
                service.accept(assignmentId, shipperId);
                sender.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(cb.getId())
                    .text("Đã nhận đơn")
                    .showAlert(false)
                    .build());
            } else {
                service.reject(assignmentId, shipperId);
                sender.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(cb.getId())
                    .text("Đã từ chối")
                    .showAlert(false)
                    .build());
            }
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid assignmentId in callback: {}", idPart);
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId())
                .text("Lỗi: ID không hợp lệ")
                .showAlert(true)
                .build());
        } catch (DomainException ex) {
            log.warn("Callback domain error: {}", ex.getMessage());
            sender.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(cb.getId())
                .text(ex.getMessage())
                .showAlert(true)
                .build());
            sender.sendText(chatId, "❌ " + ex.getMessage());
        }
    }
}
