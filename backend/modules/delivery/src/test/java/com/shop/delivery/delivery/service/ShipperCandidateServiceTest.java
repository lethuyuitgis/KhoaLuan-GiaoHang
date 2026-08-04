package com.shop.delivery.delivery.service;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.delivery.api.admin.dto.ShipperCandidateResponse;
import com.shop.delivery.delivery.domain.VehicleType;
import com.shop.delivery.delivery.entity.LocationPing;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.LocationPingRepository;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.DistanceCalculator;
import com.shop.delivery.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperCandidateServiceTest {

    @Mock OrderService orderService;
    @Mock ShipperProfileService shipperService;
    @Mock LocationPingRepository pingRepo;
    @Mock TelegramUserRepository userRepo;

    private ShipperCandidateService newService() {
        // DistanceCalculator is pure math — use the real one, not a mock.
        return new ShipperCandidateService(orderService, shipperService, pingRepo,
            new DistanceCalculator(), userRepo);
    }

    private final UUID orderId = UUID.randomUUID();

    private Order pickupAt(String lat, String lng) {
        Order o = new Order();
        o.setPickupLat(new BigDecimal(lat));
        o.setPickupLng(new BigDecimal(lng));
        return o;
    }

    private ShipperProfile shipper(long id) {
        ShipperProfile p = new ShipperProfile();
        p.setUserId(id);
        p.setVehicleType(VehicleType.MOTORBIKE);
        p.setRatingAvg(new BigDecimal("4.50"));
        p.setRatingCount(10);
        p.setTotalDeliveries(20);
        return p;
    }

    private LocationPing ping(String lat, String lng, Instant at) {
        LocationPing lp = new LocationPing();
        lp.setLat(new BigDecimal(lat));
        lp.setLng(new BigDecimal(lng));
        lp.setRecordedAt(at);
        return lp;
    }

    @Test
    void computesDistanceFromLatestPingAndResolvesName() {
        when(orderService.findById(orderId)).thenReturn(pickupAt("21.0000", "105.0000"));
        when(shipperService.listAvailable()).thenReturn(List.of(shipper(7L)));
        TelegramUser u = new TelegramUser();
        u.setId(7L);
        u.setFirstName("Nam");
        when(userRepo.findAllById(List.of(7L))).thenReturn(List.of(u));
        Instant at = Instant.parse("2026-08-04T01:00:00Z");
        when(pingRepo.findLatestByShipper(eq(7L), any(Pageable.class)))
            .thenReturn(List.of(ping("21.0100", "105.0000", at)));

        List<ShipperCandidateResponse> result = newService().listCandidates(orderId);

        assertThat(result).hasSize(1);
        ShipperCandidateResponse c = result.get(0);
        assertThat(c.firstName()).isEqualTo("Nam");
        assertThat(c.ratingAvg()).isEqualByComparingTo("4.50");
        assertThat(c.lastLocationAt()).isEqualTo(at);
        // ~1.11 km per 0.01° latitude — sanity bound, not exact.
        assertThat(c.distanceKm()).isBetween(new BigDecimal("1.0"), new BigDecimal("1.3"));
    }

    @Test
    void nullDistanceWhenShipperHasNoPing() {
        when(orderService.findById(orderId)).thenReturn(pickupAt("21.0000", "105.0000"));
        when(shipperService.listAvailable()).thenReturn(List.of(shipper(9L)));
        when(userRepo.findAllById(List.of(9L))).thenReturn(List.of());
        when(pingRepo.findLatestByShipper(eq(9L), any(Pageable.class))).thenReturn(List.of());

        List<ShipperCandidateResponse> result = newService().listCandidates(orderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).distanceKm()).isNull();
        assertThat(result.get(0).lastLocationAt()).isNull();
    }

    @Test
    void sortsNearestFirstWithUnknownDistanceLast() {
        when(orderService.findById(orderId)).thenReturn(pickupAt("21.0000", "105.0000"));
        ShipperProfile far = shipper(1L);      // has a distant ping
        ShipperProfile near = shipper(2L);     // has a close ping
        ShipperProfile unknown = shipper(3L);  // no ping → null distance
        when(shipperService.listAvailable()).thenReturn(List.of(far, unknown, near));
        lenient().when(userRepo.findAllById(any())).thenReturn(List.of());
        Instant at = Instant.parse("2026-08-04T01:00:00Z");
        when(pingRepo.findLatestByShipper(eq(1L), any(Pageable.class)))
            .thenReturn(List.of(ping("21.2000", "105.0000", at)));   // ~22 km
        when(pingRepo.findLatestByShipper(eq(2L), any(Pageable.class)))
            .thenReturn(List.of(ping("21.0100", "105.0000", at)));   // ~1.1 km
        when(pingRepo.findLatestByShipper(eq(3L), any(Pageable.class)))
            .thenReturn(List.of());

        List<ShipperCandidateResponse> result = newService().listCandidates(orderId);

        assertThat(result).extracting(ShipperCandidateResponse::userId)
            .containsExactly(2L, 1L, 3L); // near, far, unknown-last
    }
}
