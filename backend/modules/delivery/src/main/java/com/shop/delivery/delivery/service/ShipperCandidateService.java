package com.shop.delivery.delivery.service;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.delivery.api.admin.dto.ShipperCandidateResponse;
import com.shop.delivery.delivery.entity.LocationPing;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.LocationPingRepository;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.DistanceCalculator;
import com.shop.delivery.order.service.OrderService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the ranked candidate list shown in the Web Admin "Gán shipper" modal:
 * every AVAILABLE shipper enriched with their rating and a best-effort distance
 * to the order's pickup point.
 *
 * <p>Distance is derived from the shipper's last-known location ping (location
 * is only recorded during active deliveries, so an idle shipper has no live
 * position). Shippers with no ping history get a null distance and sort last.
 */
@Service
public class ShipperCandidateService {

    private static final PageRequest LATEST_ONE = PageRequest.of(0, 1);

    private final OrderService orderService;
    private final ShipperProfileService shipperService;
    private final LocationPingRepository pingRepo;
    private final DistanceCalculator distance;
    private final TelegramUserRepository userRepo;

    public ShipperCandidateService(OrderService orderService,
                                   ShipperProfileService shipperService,
                                   LocationPingRepository pingRepo,
                                   DistanceCalculator distance,
                                   TelegramUserRepository userRepo) {
        this.orderService = orderService;
        this.shipperService = shipperService;
        this.pingRepo = pingRepo;
        this.distance = distance;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public List<ShipperCandidateResponse> listCandidates(java.util.UUID orderId) {
        Order order = orderService.findById(orderId); // 404 if the order does not exist
        BigDecimal pickupLat = order.getPickupLat();
        BigDecimal pickupLng = order.getPickupLng();

        List<ShipperProfile> available = shipperService.listAvailable();

        // Batch the name lookup to avoid an N+1 across candidates.
        List<Long> ids = available.stream().map(ShipperProfile::getUserId).toList();
        Map<Long, TelegramUser> usersById = userRepo.findAllById(ids).stream()
            .collect(Collectors.toMap(TelegramUser::getId, Function.identity()));

        return available.stream()
            .map(s -> toCandidate(s, usersById.get(s.getUserId()), pickupLat, pickupLng))
            // Nearest first; unknown-distance shippers sort last.
            .sorted(Comparator.comparing(ShipperCandidateResponse::distanceKm,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private ShipperCandidateResponse toCandidate(ShipperProfile s, TelegramUser u,
                                                 BigDecimal pickupLat, BigDecimal pickupLng) {
        BigDecimal distanceKm = null;
        Instant lastLocationAt = null;
        List<LocationPing> latest = pingRepo.findLatestByShipper(s.getUserId(), LATEST_ONE);
        if (!latest.isEmpty()) {
            LocationPing ping = latest.get(0);
            distanceKm = distance.haversineKm(ping.getLat(), ping.getLng(), pickupLat, pickupLng);
            lastLocationAt = ping.getRecordedAt();
        }
        return new ShipperCandidateResponse(
            s.getUserId(),
            u != null ? u.getFirstName() : null,
            u != null ? u.getLastName() : null,
            u != null ? u.getUsername() : null,
            s.getVehicleType(),
            s.getLicensePlate(),
            s.getRatingAvg(),
            s.getRatingCount(),
            s.getTotalDeliveries(),
            distanceKm,
            lastLocationAt
        );
    }
}
