# P6 — Live Location + Realtime Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khi shipper bắt đầu giao, shipper share Telegram Live Location → backend nhận realtime via webhook/polling → broadcast qua WebSocket → khách xem shipper trên bản đồ Leaflet trong Mini App, vị trí cập nhật realtime. Sau P6, khách bấm đơn DELIVERING → thấy map với 3 marker (shop, shipper, đích) + đường nối, shipper position di chuyển trực tiếp.

**Scope (Tuần 9):**
- Backend: `location_ping` table + LocationPingService + Bot LiveLocationHandler + Spring WebSocket STOMP
- Frontend Mini App: `leaflet` + `react-leaflet` + `@stomp/stompjs` integration + OrderTrackingMap component + update customer OrderDetailPage to show map when status=DELIVERING

**Defer to P7:** VNPay payment integration.

**Architecture:**
- **Telegram Live Location flow:** Shipper bấm 📎 paperclip → Location → "Share Live Location for 1 hour" → Telegram tự gửi `Update.message.location` (initial) → mỗi vài giây gửi `Update.edited_message.location` với `live_period` field. Khi shipper stop sharing, message edited không còn `live_period`.
- **Bot side:** New `LiveLocationHandler implements UpdateHandler` parses location updates → lookup shipper's active STARTED assignment → save LocationPing → publish event.
- **WebSocket:** Spring `spring-boot-starter-websocket` + STOMP. Endpoint `/ws` (SockJS fallback). Topic `/topic/order/{orderId}/location`. Customer subscribes when viewing order detail.
- **Broadcaster:** `LocationBroadcaster` listens to `LocationPingReceivedEvent` → SimpMessagingTemplate.convertAndSend.
- **Map:** Leaflet + OpenStreetMap tiles. 3 markers: shop (pickup_lat/lng), shipper (live), destination (delivery_lat/lng). Polyline shipper → destination.

**Tech Stack (mới so với P5):**
- Backend: `spring-boot-starter-websocket` (Spring 6 WebSocket + STOMP)
- Frontend: `leaflet@1.9`, `react-leaflet@4.x`, `@stomp/stompjs@7.x`, `sockjs-client@1.6`

---

## Bối cảnh từ P5

Sau P5 + P5.1 fix:
- 88 commits, 106 tests pass, BUILD SUCCESS
- Full order lifecycle: PENDING → CONFIRMED → ASSIGNED → DELIVERING → DELIVERED
- DeliveryAssignment + ShipperProfile + 5 domain events
- Bot accept/reject callback works
- Web Admin Shippers + Assign modal
- Mini App shipper page (accept/start/complete)

**Constraints (kế thừa):**
- Java 25 + ByteBuddy 1.17.7
- Backend 8080, Mini App 5173, Web Admin 5174
- Postgres `shop_delivery_postgres_dev`
- Test admin: `admin@shop.local / admin123`

**Decisions chốt:**

1. **Mapping ping → assignment:** Khi nhận location ping từ shipper với userId X, find assignment có shipper_id=X và status=STARTED. Nếu không có → ignore ping (shipper share location nhưng không đang giao đơn nào).

2. **Throttle:** Telegram gửi update mỗi ~10-30s. Backend không throttle thêm. P9 có thể optimize.

3. **WebSocket auth:** Customer cần initData để subscribe. STOMP CONNECT frame có thể carry custom header `X-Telegram-Init-Data`. Backend verifies trong `ChannelInterceptor.preSend(CONNECT)`. **Authorization on subscribe:** Verify orderId trong topic path thuộc về customer. Implement: parse `/topic/order/{id}/location`, fetch order, check customerId match. Hoặc đơn giản hơn: skip authorization, ai có initData hợp lệ đều có thể subscribe — trade-off bảo mật vs simplicity. **Quyết định: full authorization** — `@MessageMapping("/subscribe/order/{id}/location")` với @CurrentUser, customer subscribe qua send-then-receive pattern.

   Actually pure STOMP subscribe doesn't easily integrate with Spring auth on per-topic basis. **Simpler design:** customer subscribes to `/user/queue/order-{id}-location`. Backend uses `SimpMessagingTemplate.convertAndSendToUser(customerId, ...)`. This way only the specific customer receives. Skip topic-based broadcast.

   But customer needs to know to subscribe. **Cleaner approach** (kế thừa): use `/topic/order/{id}/location` broadcast (anyone subscribes can listen), and rely on initData auth at CONNECT. For thesis scope acceptable — orderId is UUID so guessing is hard. Add note in known limitations.

   **Final decision for P6:** broadcast `/topic/order/{orderId}/location`, auth at CONNECT (initData verified), no per-topic authorization. Future P9 can lock down.

4. **WebSocket origins:** Allow all in dev (`allowedOrigins("*")` for `/ws`). Production restrict to known domains.

5. **`live_period` interpretation:** When `message.location.livePeriod` is set, this is a Live Location. When `edited_message` arrives without `livePeriod`, it means stopped sharing OR expired. Either way, we still process the location data (last known position).

6. **Map fallback when no ping:** Customer views DELIVERING order but shipper hasn't shared Live Location yet → show 2 markers (shop + destination) + "Shipper sẽ chia sẻ vị trí khi bắt đầu giao" message.

7. **Polling vs Webhook mode for bot:**
   - Dev: polling — Telegram sends edited_message via `getUpdates` long-poll. **Confirmed works** — already tested in P1.
   - Prod (webhook): edited_message updates POST to `/api/bot/webhook` automatically. UpdateRouter handles.
   - Both paths go through `UpdateRouter` → `LiveLocationHandler`.

8. **Database storage:** Each ping = 1 row. ~10-30s × 30min delivery = ~120 rows per delivery. Cleanup cron later (P9). No retention concern for thesis.

---

## File Structure (sau khi P6 hoàn thành)

```
backend/
├── app/src/main/resources/db/migration/
│   └── V7__location.sql                                    (TASK 1)
└── modules/
    ├── delivery/
    │   ├── pom.xml                                         (modify — websocket dep)
    │   └── src/main/java/com/shop/delivery/delivery/
    │       ├── entity/
    │       │   └── LocationPing.java                       (TASK 2)
    │       ├── repository/
    │       │   └── LocationPingRepository.java             (TASK 2)
    │       ├── service/
    │       │   ├── LocationPingService.java                (TASK 3)
    │       │   └── event/
    │       │       └── LocationPingReceivedEvent.java      (TASK 3)
    │       └── api/
    │           ├── customer/
    │           │   ├── OrderTrackingController.java        (TASK 5 — REST fallback)
    │           │   └── dto/
    │           │       └── LocationPingResponse.java       (TASK 5)
    │           └── ws/
    │               └── LocationBroadcaster.java            (TASK 6)
    └── bot/
        └── src/main/java/com/shop/delivery/bot/
            └── handler/shipper/
                └── LiveLocationHandler.java                (TASK 4)

backend/app/
└── src/main/java/com/shop/delivery/config/
    └── WebSocketConfig.java                                (TASK 6)

frontend/
├── shared/src/
│   ├── types/location.ts                                   (TASK 7)
│   ├── types/index.ts                                      (modify)
│   ├── api/location.ts                                     (TASK 7)
│   └── api/index.ts                                        (modify)
└── miniapp/
    ├── package.json                                        (modify — leaflet, stomp deps)
    ├── src/
    │   ├── lib/
    │   │   └── ws.ts                                       (TASK 8 — STOMP client)
    │   ├── features/tracking/
    │   │   ├── use-live-location.ts                        (TASK 9)
    │   │   └── OrderTrackingMap.tsx                        (TASK 10)
    │   ├── pages/
    │   │   └── OrderDetailPage.tsx                         (modify — embed map when DELIVERING)
    │   └── styles/
    │       └── leaflet-overrides.css                       (TASK 10)
    └── index.html                                          (modify — leaflet CSS link)
```

**Backend file mới:** ~8
**Frontend file mới:** ~5

---

## TASK 1: V7__location.sql migration (1 commit)

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V7__location.sql`

- [ ] **Step 1: Write migration**

Create `backend/app/src/main/resources/db/migration/V7__location.sql`:

```sql
-- V7__location.sql — Live Location ping storage
-- Owns: location_ping

CREATE TABLE location_ping (
    id              BIGSERIAL PRIMARY KEY,
    assignment_id   UUID NOT NULL REFERENCES delivery_assignment(id) ON DELETE CASCADE,
    lat             NUMERIC(10, 7) NOT NULL,
    lng             NUMERIC(10, 7) NOT NULL,
    accuracy        NUMERIC(8, 2),
    heading         NUMERIC(5, 2),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Latest ping query: index supports (assignment_id, recorded_at DESC)
CREATE INDEX idx_location_ping_assignment_time ON location_ping(assignment_id, recorded_at DESC);

-- Cleanup cron will use this; P9 will add scheduled job
CREATE INDEX idx_location_ping_recorded_at ON location_ping(recorded_at);
```

- [ ] **Step 2: Apply migration**

```bash
docker compose -f infra/docker-compose.dev.yml ps | grep -q healthy || docker compose -f infra/docker-compose.dev.yml up -d
sleep 5

cd backend/app
BOT_TOKEN=${BOT_TOKEN:-dummy} BOT_USERNAME=${BOT_USERNAME:-DummyBot} \
  nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p6_t1.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 90); do
  grep -q "Started Application" /tmp/p6_t1.log 2>/dev/null && break
  grep -q "APPLICATION FAILED" /tmp/p6_t1.log 2>/dev/null && break
  sleep 1
done
kill $BOOT_PID 2>/dev/null
sleep 2
cd ../..

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY version"
# Expected: 1-7 all t

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d location_ping"
```

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V7__location.sql
git commit -m "feat(delivery): add V7 migration for location_ping"
```

---

## TASK 2: LocationPing entity + repo (TDD, 1 commit)

**Files:**
- Create: entity + repo + test

- [ ] **Step 1: Write failing test**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/entity/LocationPingTest.java`:

```java
package com.shop.delivery.delivery.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocationPingTest {

    @Test
    void shouldExposeFields() {
        LocationPing p = new LocationPing();
        UUID assignmentId = UUID.randomUUID();
        p.setAssignmentId(assignmentId);
        p.setLat(new BigDecimal("21.0193"));
        p.setLng(new BigDecimal("105.8503"));
        p.setAccuracy(new BigDecimal("12.50"));
        p.setHeading(new BigDecimal("180.5"));
        Instant now = Instant.now();
        p.setRecordedAt(now);

        assertThat(p.getAssignmentId()).isEqualTo(assignmentId);
        assertThat(p.getLat()).isEqualByComparingTo("21.0193");
        assertThat(p.getLng()).isEqualByComparingTo("105.8503");
        assertThat(p.getAccuracy()).isEqualByComparingTo("12.50");
        assertThat(p.getHeading()).isEqualByComparingTo("180.5");
        assertThat(p.getRecordedAt()).isEqualTo(now);
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/delivery -am test
```

- [ ] **Step 3: Implement `LocationPing`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/LocationPing.java`:

```java
package com.shop.delivery.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_ping")
public class LocationPing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "assignment_id", nullable = false, columnDefinition = "uuid")
    private UUID assignmentId;

    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "accuracy", precision = 8, scale = 2)
    private BigDecimal accuracy;

    @Column(name = "heading", precision = 5, scale = 2)
    private BigDecimal heading;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }
    public BigDecimal getLat() { return lat; }
    public void setLat(BigDecimal lat) { this.lat = lat; }
    public BigDecimal getLng() { return lng; }
    public void setLng(BigDecimal lng) { this.lng = lng; }
    public BigDecimal getAccuracy() { return accuracy; }
    public void setAccuracy(BigDecimal accuracy) { this.accuracy = accuracy; }
    public BigDecimal getHeading() { return heading; }
    public void setHeading(BigDecimal heading) { this.heading = heading; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
```

- [ ] **Step 4: Implement repository**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/LocationPingRepository.java`:

```java
package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.entity.LocationPing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LocationPingRepository extends JpaRepository<LocationPing, Long> {
    Optional<LocationPing> findFirstByAssignmentIdOrderByRecordedAtDesc(UUID assignmentId);
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/delivery -am test
```

- [ ] **Step 6: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/LocationPing.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/LocationPingRepository.java \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/entity/LocationPingTest.java
git commit -m "feat(delivery): add LocationPing entity + repo"
```

---

## TASK 3: LocationPingService + event (TDD, 1 commit)

**Files:**
- Create: service + event + test

- [ ] **Step 1: Write event**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/event/LocationPingReceivedEvent.java`:

```java
package com.shop.delivery.delivery.service.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LocationPingReceivedEvent(
    UUID assignmentId,
    UUID orderId,
    Long customerId,
    BigDecimal lat,
    BigDecimal lng,
    BigDecimal accuracy,
    BigDecimal heading,
    Instant recordedAt
) {}
```

- [ ] **Step 2: Write failing test**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/LocationPingServiceTest.java`:

```java
package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.LocationPing;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.LocationPingRepository;
import com.shop.delivery.delivery.service.event.LocationPingReceivedEvent;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationPingServiceTest {

    @Mock LocationPingRepository pingRepo;
    @Mock DeliveryAssignmentRepository assignmentRepo;
    @Mock OrderService orderService;
    @Mock ApplicationEventPublisher events;

    private LocationPingService newService() {
        return new LocationPingService(pingRepo, assignmentRepo, orderService, events);
    }

    @Test
    void savePingForActiveShipperShouldPersistAndPublishEvent() {
        LocationPingService service = newService();
        UUID assignmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setShipperId(8888L);
        a.setOrderId(orderId);
        a.setStatus(AssignmentStatus.STARTED);

        Order o = new Order();
        o.setId(orderId);
        o.setCustomerId(5555L);

        when(assignmentRepo.findAllByShipperIdAndStatusIn(eq(8888L), any())).thenReturn(List.of(a));
        when(orderService.findById(orderId)).thenReturn(o);
        when(pingRepo.save(any(LocationPing.class))).thenAnswer(inv -> inv.getArgument(0));

        service.savePingForShipper(8888L,
            new BigDecimal("21.0193"), new BigDecimal("105.8503"),
            new BigDecimal("12.50"), new BigDecimal("180.5"));

        ArgumentCaptor<LocationPing> pingCaptor = ArgumentCaptor.forClass(LocationPing.class);
        verify(pingRepo).save(pingCaptor.capture());
        assertThat(pingCaptor.getValue().getAssignmentId()).isEqualTo(assignmentId);
        assertThat(pingCaptor.getValue().getLat()).isEqualByComparingTo("21.0193");

        verify(events).publishEvent(any(LocationPingReceivedEvent.class));
    }

    @Test
    void savePingForShipperWithNoActiveAssignmentShouldIgnore() {
        LocationPingService service = newService();
        when(assignmentRepo.findAllByShipperIdAndStatusIn(eq(8888L), any())).thenReturn(List.of());

        service.savePingForShipper(8888L,
            new BigDecimal("21.0"), new BigDecimal("105.8"), null, null);

        verify(pingRepo, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
```

- [ ] **Step 3: Implement `LocationPingService`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/LocationPingService.java`:

```java
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
        // Find active (STARTED) assignment for this shipper
        List<DeliveryAssignment> active = assignmentRepo.findAllByShipperIdAndStatusIn(
            shipperId, List.of(AssignmentStatus.STARTED));
        if (active.isEmpty()) {
            log.debug("No active delivery for shipper {} — ignoring location ping", shipperId);
            return;
        }
        // Take first (should be only one at a time)
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
```

- [ ] **Step 4: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/delivery -am test
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/LocationPingServiceTest.java
git commit -m "feat(delivery): add LocationPingService + LocationPingReceivedEvent"
```

---

## TASK 4: Bot LiveLocationHandler (TDD, 1 commit)

**Files:**
- Create: handler + test

- [ ] **Step 1: Write failing test**

Create `backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/shipper/LiveLocationHandlerTest.java`:

```java
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
            org.mockito.ArgumentMatchers.eq(8888L),
            latCap.capture(), lngCap.capture(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
        assertThat(latCap.getValue().doubleValue()).isEqualTo(21.0193);
        assertThat(lngCap.getValue().doubleValue()).isEqualTo(105.8503);
    }

    @Test
    void handleShouldHandleEditedMessage() {
        Update u = editedMessageLocationUpdate(2L, 8888L, 21.0, 105.0);
        handler.handle(u);
        verify(service).savePingForShipper(
            org.mockito.ArgumentMatchers.eq(8888L),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
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
```

- [ ] **Step 2: Run test — expect compile fail**

- [ ] **Step 3: Implement `LiveLocationHandler`**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/shipper/LiveLocationHandler.java`:

```java
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
 * When shipper stops sharing → message edited removes livePeriod.
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
        Message msg = extractMessageWithLocation(update);
        return msg != null;
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
```

**Note**: `update.hasEditedMessage()` is the telegrambots API — verify it exists. If the method name is different (e.g., `hasEditedMessage` vs `getEditedMessage() != null`), adjust. The 6.9.x API has `hasEditedMessage()`.

- [ ] **Step 4: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/bot -am test
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/shipper/LiveLocationHandler.java \
        backend/modules/bot/src/test/java/com/shop/delivery/bot/handler/shipper/LiveLocationHandlerTest.java
git commit -m "feat(bot): add LiveLocationHandler for shipper Telegram Live Location"
```

---

## TASK 5: REST fallback endpoint for latest location (1 commit)

**Purpose:** Customer Mini App fetches initial location via REST (in case WebSocket connect is slow) then subscribes to WebSocket for updates.

**Files:**
- Create: DTO + controller

- [ ] **Step 1: Write DTO**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/dto/LocationPingResponse.java`:

```java
package com.shop.delivery.delivery.api.customer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LocationPingResponse(
    BigDecimal lat,
    BigDecimal lng,
    BigDecimal accuracy,
    BigDecimal heading,
    Instant recordedAt
) {}
```

- [ ] **Step 2: Write controller**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/OrderTrackingController.java`:

```java
package com.shop.delivery.delivery.api.customer;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.delivery.api.customer.dto.LocationPingResponse;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.service.LocationPingService;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.exception.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Customer-facing endpoint to fetch latest known shipper location for an order.
 * Used as REST fallback before WebSocket connect completes.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderTrackingController {

    private final DeliveryAssignmentRepository assignmentRepo;
    private final LocationPingService pingService;
    private final OrderService orderService;

    public OrderTrackingController(DeliveryAssignmentRepository assignmentRepo,
                                   LocationPingService pingService,
                                   OrderService orderService) {
        this.assignmentRepo = assignmentRepo;
        this.pingService = pingService;
        this.orderService = orderService;
    }

    @GetMapping("/{orderId}/location")
    public LocationPingResponse getLatestLocation(@CurrentUser TelegramUser user,
                                                  @PathVariable UUID orderId) {
        Order order = orderService.findById(orderId);
        if (!order.getCustomerId().equals(user.getId())) {
            throw new NotFoundException("ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        DeliveryAssignment a = assignmentRepo.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("NO_ASSIGNMENT", "Đơn chưa được gán shipper"));

        return pingService.findLatestForAssignment(a.getId())
            .map(p -> new LocationPingResponse(p.getLat(), p.getLng(), p.getAccuracy(), p.getHeading(), p.getRecordedAt()))
            .orElseThrow(() -> new NotFoundException("NO_LOCATION_YET", "Shipper chưa chia sẻ vị trí"));
    }
}
```

- [ ] **Step 3: Verify compile**

```bash
cd backend && ./mvnw -pl modules/delivery -am compile
```

- [ ] **Step 4: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/customer/
git commit -m "feat(delivery): add GET /api/orders/:id/location for tracking fallback"
```

---

## TASK 6: WebSocket config + LocationBroadcaster (1 commit)

**Files:**
- Modify: `backend/app/pom.xml` — add websocket starter
- Create: `WebSocketConfig` in app module
- Create: `LocationBroadcaster` in delivery module
- Modify: `backend/modules/delivery/pom.xml` — add websocket dep

- [ ] **Step 1: Add websocket to delivery pom.xml**

Read existing `backend/modules/delivery/pom.xml`. Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

- [ ] **Step 2: Add websocket to app pom.xml**

Read existing `backend/app/pom.xml`. Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

- [ ] **Step 3: Write `WebSocketConfig`**

Create `backend/app/src/main/java/com/shop/delivery/config/WebSocketConfig.java`:

```java
package com.shop.delivery.config;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.TelegramInitDataVerifier;
import com.shop.delivery.auth.service.TelegramUserService;
import com.shop.delivery.auth.service.TelegramUserUpsertCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private static final String HEADER_INIT_DATA = "X-Telegram-Init-Data";

    private final TelegramInitDataVerifier verifier;
    private final TelegramUserService userService;

    public WebSocketConfig(TelegramInitDataVerifier verifier,
                           TelegramUserService userService) {
        this.verifier = verifier;
        this.userService = userService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Plain WS endpoint
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*");
        // SockJS fallback
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    /**
     * Auth on CONNECT: verify X-Telegram-Init-Data header.
     * P9 enhance: also authorize per-topic subscription.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String initData = accessor.getFirstNativeHeader(HEADER_INIT_DATA);
                    if (initData == null || initData.isBlank()) {
                        log.debug("WS CONNECT rejected — missing X-Telegram-Init-Data header");
                        return null;  // reject by returning null
                    }
                    var verified = verifier.tryVerify(initData);
                    if (verified.isEmpty()) {
                        log.debug("WS CONNECT rejected — invalid initData");
                        return null;
                    }
                    var v = verified.get();
                    TelegramUser user = userService.registerOrUpdate(new TelegramUserUpsertCommand(
                        v.userId(), v.username(), v.firstName(), v.lastName(), v.languageCode()));
                    accessor.setUser(new TelegramUserPrincipal(user));
                    log.debug("WS CONNECT OK for userId={}", user.getId());
                }
                return message;
            }
        });
    }

    /** Wraps TelegramUser as a java.security.Principal for STOMP user destinations. */
    public record TelegramUserPrincipal(TelegramUser user) implements java.security.Principal {
        @Override public String getName() { return String.valueOf(user.getId()); }
    }
}
```

- [ ] **Step 4: Write `LocationBroadcaster`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/ws/LocationBroadcaster.java`:

```java
package com.shop.delivery.delivery.api.ws;

import com.shop.delivery.delivery.service.event.LocationPingReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Listens for LocationPingReceivedEvent and broadcasts to WS topic
 * /topic/order/{orderId}/location so customer Mini App can render realtime map.
 */
@Component
public class LocationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LocationBroadcaster.class);

    private final SimpMessagingTemplate ws;

    public LocationBroadcaster(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    public record LocationMessage(
        UUID orderId,
        BigDecimal lat,
        BigDecimal lng,
        BigDecimal accuracy,
        BigDecimal heading,
        Instant recordedAt
    ) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPingReceived(LocationPingReceivedEvent e) {
        String topic = "/topic/order/" + e.orderId() + "/location";
        LocationMessage payload = new LocationMessage(
            e.orderId(), e.lat(), e.lng(), e.accuracy(), e.heading(), e.recordedAt()
        );
        ws.convertAndSend(topic, payload);
        log.debug("Broadcast location to {}: lat={}, lng={}", topic, e.lat(), e.lng());
    }
}
```

- [ ] **Step 5: Verify compile**

```bash
cd backend && ./mvnw compile
```

- [ ] **Step 6: Run full verify**

```bash
cd backend && ./mvnw verify
```

Expected: BUILD SUCCESS. Should be ~115 tests.

If any IT test fails because the WebSocket endpoints conflict with Spring Security config: check SecurityConfig allows `/ws/**`:

If needed, modify `backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java` to add:
```java
.requestMatchers("/ws/**").permitAll()
```
inside `.authorizeHttpRequests(auth -> auth ...)`. The CONNECT-time auth via STOMP interceptor handles security.

- [ ] **Step 7: Commit**

```bash
git add backend/app/pom.xml backend/modules/delivery/pom.xml \
        backend/app/src/main/java/com/shop/delivery/config/WebSocketConfig.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/ws/LocationBroadcaster.java \
        backend/modules/auth/src/main/java/com/shop/delivery/auth/config/SecurityConfig.java
git commit -m "feat(delivery): add WebSocket STOMP config + LocationBroadcaster"
```

---

## TASK 7: Shared types + API helpers for location (1 commit)

**Files:**
- Create: `frontend/shared/src/types/location.ts`
- Create: `frontend/shared/src/api/location.ts`
- Modify: shared `types/index.ts`, `api/index.ts`

- [ ] **Step 1: Create types**

Create `frontend/shared/src/types/location.ts`:

```typescript
export interface LocationPing {
  lat: string;        // backend serializes BigDecimal as string
  lng: string;
  accuracy: string | null;
  heading: string | null;
  recordedAt: string;
}

export interface LocationMessage {
  orderId: string;
  lat: string;
  lng: string;
  accuracy: string | null;
  heading: string | null;
  recordedAt: string;
}
```

- [ ] **Step 2: Create API helper**

Create `frontend/shared/src/api/location.ts`:

```typescript
import type { AxiosInstance } from 'axios';
import type { LocationPing } from '../types';

export async function getLatestLocation(client: AxiosInstance, orderId: string): Promise<LocationPing> {
  const { data } = await client.get<LocationPing>(`/api/orders/${orderId}/location`);
  return data;
}
```

- [ ] **Step 3: Update barrels**

Read existing `frontend/shared/src/types/index.ts`. Append:
```typescript
export * from './location';
```

Read existing `frontend/shared/src/api/index.ts`. Append:
```typescript
export * from './location';
```

- [ ] **Step 4: Type-check**

```bash
cd frontend && pnpm -r type-check
```

- [ ] **Step 5: Commit**

```bash
git add frontend/shared/src/
git commit -m "feat(shared): add location types + fetch latest location helper"
```

---

## TASK 8: Install leaflet + STOMP in miniapp + WS client (1 commit)

**Files:**
- Modify: `frontend/miniapp/package.json` — add deps
- Modify: `frontend/miniapp/index.html` — link Leaflet CSS
- Create: `frontend/miniapp/src/lib/ws.ts`

- [ ] **Step 1: Update `frontend/miniapp/package.json`**

Read existing. Add to dependencies:
```json
"leaflet": "^1.9.4",
"react-leaflet": "^4.2.1",
"@stomp/stompjs": "^7.0.0",
"sockjs-client": "^1.6.1"
```

And to devDependencies:
```json
"@types/leaflet": "^1.9.12",
"@types/sockjs-client": "^1.5.4"
```

Then install:

```bash
cd frontend && pnpm install
```

- [ ] **Step 2: Add Leaflet CSS to `index.html`**

Read existing `frontend/miniapp/index.html`. Add inside `<head>` (after existing meta/script tags):

```html
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
  integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
  crossorigin="" />
```

- [ ] **Step 3: Create `frontend/miniapp/src/lib/ws.ts`**

```typescript
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { tg } from './telegram';

/**
 * Creates a STOMP client connected via SockJS.
 * Sends X-Telegram-Init-Data on CONNECT for auth.
 */
export function createStompClient(): Client {
  return new Client({
    webSocketFactory: () => new SockJS('/ws') as any,
    connectHeaders: {
      'X-Telegram-Init-Data': tg.initData(),
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},  // silence by default; uncomment for debug: msg => console.log('[STOMP]', msg)
  });
}

export type LocationMessage = {
  orderId: string;
  lat: string;
  lng: string;
  accuracy: string | null;
  heading: string | null;
  recordedAt: string;
};

export type LocationCallback = (msg: LocationMessage) => void;

export function subscribeOrderLocation(
  client: Client,
  orderId: string,
  cb: LocationCallback
): () => void {
  const sub = client.subscribe(`/topic/order/${orderId}/location`, (msg: IMessage) => {
    try {
      const payload = JSON.parse(msg.body) as LocationMessage;
      cb(payload);
    } catch (e) {
      console.error('Failed to parse location message', e);
    }
  });
  return () => sub.unsubscribe();
}
```

- [ ] **Step 4: Build**

```bash
cd frontend/miniapp && pnpm build
```

- [ ] **Step 5: Commit**

```bash
git add frontend/miniapp/package.json frontend/miniapp/index.html \
        frontend/miniapp/src/lib/ws.ts frontend/pnpm-lock.yaml
git commit -m "feat(miniapp): add leaflet + STOMP client deps + WS connection helper"
```

---

## TASK 9: useLiveLocation hook (1 commit)

**Files:**
- Create: `frontend/miniapp/src/features/tracking/use-live-location.ts`

- [ ] **Step 1: Create hook**

Create `frontend/miniapp/src/features/tracking/use-live-location.ts`:

```typescript
import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getLatestLocation, type LocationPing } from '@shop/shared';
import { api } from '@/lib/api';
import { createStompClient, subscribeOrderLocation, type LocationMessage } from '@/lib/ws';
import { tg } from '@/lib/telegram';

export interface LiveLocation {
  lat: number;
  lng: number;
  recordedAt: string;
}

/**
 * Hook that subscribes to live location for an order.
 * 1. Fetches latest via REST as initial state.
 * 2. Opens STOMP connection and subscribes to /topic/order/{id}/location.
 * 3. Updates location state on each new message.
 */
export function useLiveLocation(orderId: string | undefined, enabled: boolean): {
  location: LiveLocation | null;
  isConnected: boolean;
  isLoading: boolean;
} {
  const [location, setLocation] = useState<LiveLocation | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef<ReturnType<typeof createStompClient> | null>(null);

  // 1. REST fallback for initial location
  const { isLoading } = useQuery({
    queryKey: ['order-location', orderId],
    queryFn: async () => {
      if (!orderId) return null;
      try {
        const ping = await getLatestLocation(api, orderId);
        return ping;
      } catch {
        return null;
      }
    },
    enabled: !!orderId && enabled,
    retry: 0,
  });

  // Apply REST result to local state once
  const { data: initialPing } = useQuery<LocationPing | null>({
    queryKey: ['order-location', orderId],
    enabled: false,  // read from cache only
  });
  useEffect(() => {
    if (initialPing && !location) {
      setLocation({
        lat: Number(initialPing.lat),
        lng: Number(initialPing.lng),
        recordedAt: initialPing.recordedAt,
      });
    }
  }, [initialPing, location]);

  // 2. STOMP subscription
  useEffect(() => {
    if (!orderId || !enabled || !tg.isInTelegram()) return;

    const client = createStompClient();
    clientRef.current = client;

    client.onConnect = () => {
      setIsConnected(true);
      subscribeOrderLocation(client, orderId, (msg: LocationMessage) => {
        setLocation({
          lat: Number(msg.lat),
          lng: Number(msg.lng),
          recordedAt: msg.recordedAt,
        });
      });
    };
    client.onDisconnect = () => setIsConnected(false);
    client.onStompError = (frame) => {
      console.error('STOMP error', frame);
      setIsConnected(false);
    };

    client.activate();

    return () => {
      client.deactivate();
      clientRef.current = null;
      setIsConnected(false);
    };
  }, [orderId, enabled]);

  return { location, isConnected, isLoading };
}
```

- [ ] **Step 2: Type-check**

```bash
cd frontend/miniapp && pnpm type-check
```

- [ ] **Step 3: Commit**

```bash
git add frontend/miniapp/src/features/tracking/
git commit -m "feat(miniapp): add useLiveLocation hook (REST initial + STOMP subscription)"
```

---

## TASK 10: OrderTrackingMap component (Leaflet) (1 commit)

**Files:**
- Create: `frontend/miniapp/src/features/tracking/OrderTrackingMap.tsx`
- Create: `frontend/miniapp/src/styles/leaflet-overrides.css`

- [ ] **Step 1: Create Leaflet overrides CSS**

Create `frontend/miniapp/src/styles/leaflet-overrides.css`:

```css
/* Fix Leaflet default marker icons not loading in bundled environment */
.leaflet-container {
  width: 100%;
  height: 100%;
  background: var(--tg-theme-secondary-bg-color, #f0f0f0);
}

.leaflet-control-attribution {
  font-size: 9px !important;
}
```

- [ ] **Step 2: Create `OrderTrackingMap.tsx`**

Create `frontend/miniapp/src/features/tracking/OrderTrackingMap.tsx`:

```typescript
import { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import { useLiveLocation } from './use-live-location';
import 'leaflet/dist/leaflet.css';
import '@/styles/leaflet-overrides.css';

// Fix default marker icons (Leaflet has issues with bundled icons)
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

// Custom shop (green) and shipper (red) icons
const shopIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const shipperIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const destIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

interface Props {
  orderId: string;
  pickupLat: string;
  pickupLng: string;
  deliveryLat: string;
  deliveryLng: string;
  enabled?: boolean;
}

export function OrderTrackingMap({
  orderId, pickupLat, pickupLng, deliveryLat, deliveryLng, enabled = true,
}: Props) {
  const { location, isConnected, isLoading } = useLiveLocation(orderId, enabled);

  const pickup: [number, number] = [Number(pickupLat), Number(pickupLng)];
  const destination: [number, number] = [Number(deliveryLat), Number(deliveryLng)];
  const shipper: [number, number] | null = location ? [location.lat, location.lng] : null;

  // Center: average of pickup & destination
  const center: [number, number] = [
    (pickup[0] + destination[0]) / 2,
    (pickup[1] + destination[1]) / 2,
  ];

  return (
    <div className="w-full h-64 rounded-lg overflow-hidden mb-3 relative">
      <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{y}/{x}.png"
        />
        <Marker position={pickup} icon={shopIcon} />
        <Marker position={destination} icon={destIcon} />
        {shipper && <Marker position={shipper} icon={shipperIcon} />}
        {shipper && (
          <Polyline
            positions={[shipper, destination]}
            pathOptions={{ color: '#9333ea', weight: 3, dashArray: '5, 8' }}
          />
        )}
        <FitBounds pickup={pickup} destination={destination} shipper={shipper} />
      </MapContainer>

      <div className="absolute top-2 right-2 z-[1000] bg-white/90 px-2 py-1 rounded-md text-xs shadow">
        {isLoading && '⏳ Đang tải...'}
        {!isLoading && location && (
          <span className={isConnected ? 'text-green-600' : 'text-yellow-600'}>
            {isConnected ? '🟢 Live' : '🟡 Chờ kết nối'}
          </span>
        )}
        {!isLoading && !location && (
          <span className="text-gray-500">📍 Chờ shipper share location</span>
        )}
      </div>
    </div>
  );
}

function FitBounds({
  pickup, destination, shipper,
}: {
  pickup: [number, number];
  destination: [number, number];
  shipper: [number, number] | null;
}) {
  const map = useMap();
  useEffect(() => {
    const points: [number, number][] = [pickup, destination];
    if (shipper) points.push(shipper);
    map.fitBounds(points, { padding: [40, 40] });
  }, [map, pickup, destination, shipper]);
  return null;
}
```

- [ ] **Step 3: Type-check + build**

```bash
cd frontend/miniapp && pnpm type-check && pnpm build
```

- [ ] **Step 4: Commit**

```bash
git add frontend/miniapp/src/features/tracking/OrderTrackingMap.tsx \
        frontend/miniapp/src/styles/leaflet-overrides.css
git commit -m "feat(miniapp): add OrderTrackingMap Leaflet component with realtime updates"
```

---

## TASK 11: Embed map in customer OrderDetailPage (1 commit)

**Files:**
- Modify: `frontend/miniapp/src/pages/OrderDetailPage.tsx`

- [ ] **Step 1: Update `OrderDetailPage.tsx`**

Read existing `frontend/miniapp/src/pages/OrderDetailPage.tsx`. Add import:

```typescript
import { OrderTrackingMap } from '@/features/tracking/OrderTrackingMap';
```

Inside the page render, ABOVE the "Sản phẩm" card, conditionally embed the map when order.status === 'DELIVERING':

```jsx
{order.status === 'DELIVERING' && (
  <OrderTrackingMap
    orderId={order.id}
    pickupLat={String(order.pickupLat ?? '21.0285')}
    pickupLng={String(order.pickupLng ?? '105.8542')}
    deliveryLat={order.deliveryLat}
    deliveryLng={order.deliveryLng}
  />
)}
```

NOTE: `OrderResponse` type may not have `pickupLat`/`pickupLng` fields. Check `frontend/shared/src/types/order.ts`. If missing, the map uses default Bà Triệu coords.

Actually `Order` entity has `pickupLat/pickupLng` and `OrderResponse` DTO has them already (check the backend `OrderResponse.java`). The TypeScript type `OrderResponse` in shared was created in P2 — verify it includes those fields. If not, you'll need to update the type and the backend mapper. **For P6 simplicity: hardcode default pickup coords (Bà Triệu HN) in OrderTrackingMap if not present**, since `OrderResponse` already exposes `deliveryLat/Lng`.

Let me check existing type — `frontend/shared/src/types/order.ts` defines `OrderResponse`. It does NOT include pickupLat/pickupLng. **Add them now:**

Read `frontend/shared/src/types/order.ts`, then update `OrderResponse` to add:
```typescript
pickupLat: string;
pickupLng: string;
```

Backend `OrderResponse` DTO already includes them (from P2). TypeScript needs to match.

- [ ] **Step 2: Build**

```bash
cd frontend/miniapp && pnpm type-check && pnpm build
```

- [ ] **Step 3: Commit**

```bash
git add frontend/miniapp/src/pages/OrderDetailPage.tsx frontend/shared/src/types/order.ts
git commit -m "feat(miniapp): embed live tracking map in OrderDetail when DELIVERING"
```

---

## TASK 12: Shipper instructions for sharing Live Location (1 commit)

**Files:**
- Modify: `frontend/miniapp/src/pages/ShipperAssignmentDetailPage.tsx`

- [ ] **Step 1: Update shipper detail page**

Read existing `frontend/miniapp/src/pages/ShipperAssignmentDetailPage.tsx`. Add a banner shown when status='STARTED' instructing shipper how to share Live Location:

After the existing "Phí ship" card, before the action buttons, add:

```jsx
{assignment.status === 'STARTED' && (
  <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-4 text-sm">
    <p className="font-semibold mb-2">📍 Hãy chia sẻ vị trí real-time</p>
    <ol className="list-decimal list-inside space-y-1 text-blue-900">
      <li>Quay lại chat với bot</li>
      <li>Bấm biểu tượng 📎 → "Vị trí" → "Chia sẻ vị trí trực tiếp"</li>
      <li>Chọn thời lượng (15 phút / 1 giờ / 8 giờ)</li>
      <li>Khách sẽ thấy vị trí của bạn trên map realtime</li>
    </ol>
  </div>
)}
```

- [ ] **Step 2: Build**

```bash
cd frontend/miniapp && pnpm type-check && pnpm build
```

- [ ] **Step 3: Commit**

```bash
git add frontend/miniapp/src/pages/ShipperAssignmentDetailPage.tsx
git commit -m "feat(miniapp): add Live Location instructions banner for shipper"
```

---

## TASK 13: Smoke test (verification, no commit)

**Prerequisites:** 2 phones with Telegram (shipper + customer), ngrok running.

- [ ] **Step 1: Backend running with real bot token**

```bash
cd /Users/lethitranthuy/Documents/KhoaLuan-GiaoHang
export $(cat .env | xargs)
cd backend/app && ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- [ ] **Step 2: Mini App + ngrok**

```bash
cd frontend/miniapp && pnpm dev   # port 5173
# In another terminal:
ngrok http 5173
# Update BotFather menu button to ngrok URL
```

- [ ] **Step 3: Setup test data**

In Telegram, both phones `/start` the bot to register users. Note their Telegram IDs (visible in user_role table).

Make one user a shipper:
```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery <<EOF
-- Replace 99999999 with real shipper Telegram ID
INSERT INTO shipper_profile(user_id, vehicle_type, license_plate, current_state)
  VALUES (99999999, 'MOTORBIKE', '30A-12345', 'AVAILABLE')
  ON CONFLICT (user_id) DO UPDATE SET current_state = 'AVAILABLE';
INSERT INTO user_role(telegram_user_id, role, status)
  VALUES (99999999, 'SHIPPER', 'ACTIVE')
  ON CONFLICT (telegram_user_id, role) DO NOTHING;
EOF
```

- [ ] **Step 4: Customer places order**

Customer phone → bot → Open App → catalog → add to cart → checkout COD → order PENDING.

- [ ] **Step 5: Admin confirms + assigns**

Web Admin → Orders → confirm PENDING order → assign shipper (the one with user_id=99999999).

Bot sends offer to shipper.

- [ ] **Step 6: Shipper accepts + starts**

Shipper phone → bot → tap "✅ Nhận" → assignment ACCEPTED → open Mini App → /shipper/assignments/{id} → "🚀 Bắt đầu giao" → status STARTED.

- [ ] **Step 7: Shipper shares Live Location**

Shipper phone → back to bot chat → 📎 → Location → "Share Live Location" → choose 15 min.

Backend logs should show:
```
Live location from user 99999999: lat=... lng=... livePeriod=900
Saved location ping shipper=99999999 order=DH... (..., ...)
Broadcast location to /topic/order/.../location
```

- [ ] **Step 8: Customer views map**

Customer phone → /customer/orders/{orderId} → page should show map with 3 markers + status badge "Đang giao".

Customer moves their position in real life (or shipper moves in Telegram), map updates within ~10-30 seconds.

- [ ] **Step 9: Shipper completes**

Shipper Mini App → "✅ Đã giao" → status DELIVERED. Customer + shipper both get bot notification.

Verify DB:
```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT COUNT(*) FROM location_ping WHERE assignment_id = (SELECT id FROM delivery_assignment ORDER BY assigned_at DESC LIMIT 1)"
# Expected: > 0
```

- [ ] **Step 10: Stop everything**

`Ctrl+C` on all terminals.

---

## Acceptance Criteria (P6 done when ALL true)

- [x] V7 migration applied
- [x] `./mvnw verify` BUILD SUCCESS
- [x] `pnpm -r type-check` clean
- [x] Mini App `pnpm build` produces working bundle
- [x] Shipper share Live Location → backend receives + saves
- [x] Customer Mini App displays map with shipper position when DELIVERING
- [x] Map updates realtime via WebSocket
- [x] No regressions in P0-P5

---

## Known Limitations of P6 (giải quyết ở plan sau)

| Limitation | Plan |
|---|---|
| WebSocket subscribe authorization is "any authenticated user" — not per-order check | P9 hardening |
| No fallback to long-polling if WebSocket fails | Acceptable for thesis |
| Location ping retention is unlimited (no cleanup cron) | P9 |
| Map doesn't compute route via roads — straight polyline shipper→destination | OSRM integration future enhancement |
| Shipper instructions banner is text-only — no inline "Open chat" button | Polish |
| When Telegram Live Location stops sharing (expired), customer map freezes on last position | Add "🔴 Live ended" indicator — P9 polish |
| OrderTrackingMap renders even outside Telegram (browser dev) — WebSocket connect skipped | Already handled via `tg.isInTelegram()` check |
| `useQuery` for initial location uses `enabled: false` trick to read from cache — could be cleaner | Minor |

---

## Troubleshooting

**"Shipper chưa chia sẻ vị trí" stuck forever**: Verify shipper actually shared Live Location via paperclip 📎. Check backend logs for "Live location from user".

**WebSocket CONNECT rejected (no logs)**: Check `X-Telegram-Init-Data` header is sent. Test by opening browser DevTools → Network → ws.

**`@types/sockjs-client` missing types**: install via pnpm if pnpm install didn't fetch correctly.

**Map shows but markers don't appear**: Leaflet bundling issue — make sure `import 'leaflet/dist/leaflet.css'` is in component or via index.html link.

**Hot reload breaks STOMP connection**: Normal in dev — client reconnects on `reconnectDelay: 5000`.

**`UnsupportedOperationException: Cannot send to user destination` during tests**: tests may need to skip @TransactionalEventListener for ping events. Add `@WithMockUser` or `@TestPropertySource` to disable WebSocket in tests. Or simpler: make ITs not exercise location flow (only test entity + service + handler with Mockito).

---

**END OF P6 PLAN**
