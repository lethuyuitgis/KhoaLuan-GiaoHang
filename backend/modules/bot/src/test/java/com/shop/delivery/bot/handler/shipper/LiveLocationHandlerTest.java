package com.shop.delivery.bot.handler.shipper;

import com.shop.delivery.delivery.service.LocationPingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LiveLocationHandlerTest {

    @Mock LocationPingService service;

    @InjectMocks LiveLocationHandler handler;

    @Test
    void canHandleShouldReturnTrueForMessageWithLocation() {
        Update u = messageLocationUpdate(1L, 8888L, 21.0193, 105.8503);
        assertThat(handler.canHandle(u)).isTrue();
    }

    @Test
    void canHandleShouldReturnTrueForEditedMessageWithLocation() {
        Update u = editedMessageLocationUpdate(1L, 8888L, 21.0193, 105.8503);
        assertThat(handler.canHandle(u)).isTrue();
    }

    @Test
    void canHandleShouldReturnFalseForTextMessage() {
        Update u = new Update();
        Message m = new Message();
        User from = new User();
        from.setId(8888L);
        m.setFrom(from);
        m.setText("hello");
        u.setMessage(m);
        assertThat(handler.canHandle(u)).isFalse();
    }

    @Test
    void handleShouldCallServiceWithLatLng() {
        Update u = messageLocationUpdate(1L, 8888L, 21.0193, 105.8503);

        handler.handle(u);

        ArgumentCaptor<BigDecimal> latCap = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> lngCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(service).savePingForShipper(
            eq(8888L),
            latCap.capture(), lngCap.capture(),
            any(), any()
        );
        assertThat(latCap.getValue().doubleValue()).isEqualTo(21.0193);
        assertThat(lngCap.getValue().doubleValue()).isEqualTo(105.8503);
    }

    @Test
    void handleShouldHandleEditedMessage() {
        Update u = editedMessageLocationUpdate(2L, 8888L, 21.0, 105.0);
        handler.handle(u);
        verify(service).savePingForShipper(eq(8888L), any(), any(), any(), any());
    }

    @Test
    void handleShouldDoNothingIfNoLocation() {
        Update u = new Update();
        Message m = new Message();
        User from = new User();
        from.setId(8888L);
        m.setFrom(from);
        u.setMessage(m);
        handler.handle(u);
        verifyNoInteractions(service);
    }

    private Update messageLocationUpdate(long updateId, long userId, double lat, double lng) {
        Update u = new Update();
        u.setUpdateId((int) updateId);
        Message m = new Message();
        User from = new User();
        from.setId(userId);
        from.setIsBot(false);
        from.setFirstName("Shipper");
        m.setFrom(from);
        Location loc = new Location();
        loc.setLatitude(lat);
        loc.setLongitude(lng);
        m.setLocation(loc);
        u.setMessage(m);
        return u;
    }

    private Update editedMessageLocationUpdate(long updateId, long userId, double lat, double lng) {
        Update u = new Update();
        u.setUpdateId((int) updateId);
        Message m = new Message();
        User from = new User();
        from.setId(userId);
        from.setIsBot(false);
        m.setFrom(from);
        Location loc = new Location();
        loc.setLatitude(lat);
        loc.setLongitude(lng);
        m.setLocation(loc);
        u.setEditedMessage(m);
        return u;
    }
}
