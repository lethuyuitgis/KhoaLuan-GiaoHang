package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.LocationPing;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.LocationPingRepository;
import com.shop.delivery.delivery.service.event.LocationPingReceivedEvent;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LocationPingService {

    private static final Logger log = LoggerFactory.getLogger(LocationPingService.class);

    private final LocationPingRepository pingRepo;
    private final DeliveryAssignmentRepository assignmentRepo;
    private final OrderService orderService;
    private final ApplicationEventPublisher events;

    public LocationPingService(LocationPingRepository pingRepo,
                               DeliveryAssignmentRepository assignmentRepo,
                               OrderService orderService,
                               ApplicationEventPublisher events) {
        this.pingRepo = pingRepo;
        this.assignmentRepo = assignmentRepo;
        this.orderService = orderService;
        this.events = events;
    }

    @Transactional
    public void savePingForShipper(Long shipperId,
                                   BigDecimal lat, BigDecimal lng,
                                   BigDecimal accuracy, BigDecimal heading) {
        List<DeliveryAssignment> active = assignmentRepo.findAllByShipperIdAndStatusInOrderByAssignedAtDesc(
            shipperId, List.of(AssignmentStatus.STARTED));
        if (active.isEmpty()) {
            log.debug("No active delivery for shipper {} — ignoring location ping", shipperId);
            return;
        }
        if (active.size() > 1) {
            // Invariant violation: a shipper should have at most one STARTED assignment.
            // Migration V8 enforces this with a partial unique index, but log defensively in case
            // dev data predates the constraint. Most-recent assignment wins.
            log.warn("Shipper {} has {} STARTED assignments; routing ping to most recent",
                shipperId, active.size());
        }
        DeliveryAssignment a = active.get(0);
        Order order = orderService.findById(a.getOrderId());

        LocationPing ping = new LocationPing();
        ping.setAssignmentId(a.getId());
        ping.setLat(lat);
        ping.setLng(lng);
        ping.setAccuracy(accuracy);
        ping.setHeading(heading);
        ping.setRecordedAt(Instant.now());
        pingRepo.save(ping);

        events.publishEvent(new LocationPingReceivedEvent(
            a.getId(), order.getId(), order.getCustomerId(),
            lat, lng, accuracy, heading, ping.getRecordedAt()
        ));

        log.debug("Saved location ping shipper={} order={} ({}, {})",
            shipperId, order.getCode(), lat, lng);
    }

    @Transactional(readOnly = true)
    public Optional<LocationPing> findLatestForAssignment(UUID assignmentId) {
        return pingRepo.findFirstByAssignmentIdOrderByRecordedAtDesc(assignmentId);
    }
}
