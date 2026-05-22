package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.bot.handler.UpdateHandler;
import com.shop.delivery.delivery.service.LocationPingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Handles location updates from shipper Live Location sharing.
 * Telegram sends:
 * - Update.message.location (initial share, includes livePeriod)
 * - Update.edited_message.location (subsequent updates, same messageId)
 */
@Component
public class LiveLocationHandler implements UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveLocationHandler.class);

    private final LocationPingService service;

    public LiveLocationHandler(LocationPingService service) {
        this.service = service;
    }

    @Override
    public boolean canHandle(Update update) {
        return extractMessageWithLocation(update) != null;
    }

    @Override
    public void handle(Update update) {
        Message msg = extractMessageWithLocation(update);
        if (msg == null) return;

        Long userId = msg.getFrom().getId();
        Location loc = msg.getLocation();
        BigDecimal lat = BigDecimal.valueOf(loc.getLatitude()).setScale(7, RoundingMode.HALF_UP);
        BigDecimal lng = BigDecimal.valueOf(loc.getLongitude()).setScale(7, RoundingMode.HALF_UP);
        BigDecimal accuracy = loc.getHorizontalAccuracy() != null
            ? BigDecimal.valueOf(loc.getHorizontalAccuracy()).setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal heading = loc.getHeading() != null
            ? BigDecimal.valueOf(loc.getHeading()).setScale(2, RoundingMode.HALF_UP) : null;

        log.debug("Live location from user {}: lat={} lng={} livePeriod={}",
            userId, lat, lng, loc.getLivePeriod());

        service.savePingForShipper(userId, lat, lng, accuracy, heading);
    }

    private Message extractMessageWithLocation(Update update) {
        if (update.hasMessage() && update.getMessage().getLocation() != null) {
            return update.getMessage();
        }
        if (update.hasEditedMessage() && update.getEditedMessage().getLocation() != null) {
            return update.getEditedMessage();
        }
        return null;
    }
}
