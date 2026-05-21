# P5 — Delivery Lifecycle + Assign Shipper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Đóng vòng đời 1 đơn từ tạo → giao xong. Chủ shop gán shipper cho đơn từ Web Admin → bot push offer cho shipper → shipper Accept qua bot inline button → shipper mở Mini App → "Bắt đầu giao" → khách thấy đơn DELIVERING → shipper "Đã giao" → đơn DELIVERED. Sau P5, full happy path order lifecycle hoạt động end-to-end.

**Scope (Tuần 7-8):**
- Backend: `DeliveryAssignment` + `ShipperProfile` entities + services + admin assign endpoint
- Bot: OrderAssigned listener → push offer; Accept/Reject callback handlers
- Web Admin: Shippers list + Assign Shipper modal trong Order Detail
- Mini App: Shipper "Đơn của tôi" page + Start/Complete actions

**Defer to P6 (Live Location):** `location_ping` table, Telegram Live Location handler, realtime WebSocket map. P5 chuẩn bị `DELIVERING` status nhưng chưa có realtime location.

**Defer to later:** Shipper self-registration FSM via bot. P5 yêu cầu admin tạo shipper qua Web Admin (admin → "Mời shipper" → tạo bằng tay với telegramUserId đã biết, hoặc shipper gõ `/start` thì user vào DB nhưng admin phải gán role SHIPPER tay).

**Architecture:**
- New `delivery` module entities: `ShipperProfile`, `DeliveryAssignment`. State machine cho `assignment_status`: `OFFERED → ACCEPTED → STARTED → COMPLETED` (parallel với `OrderStatus` ASSIGNED → DELIVERING → DELIVERED).
- `DeliveryAssignmentService` orchestrate: create assignment + update OrderStatus + publish events.
- `notification` module nay được kích hoạt thật sự: `OrderAssignedEvent` listener gửi bot message cho shipper với inline keyboard `[✅ Nhận] [❌ Từ chối]`.
- Callback data format: `ACCEPT_ORDER:<assignmentId>` / `REJECT_ORDER:<assignmentId>`.
- Web Admin: Modal chọn shipper từ danh sách AVAILABLE; option "broadcast" gán cho tất cả AVAILABLE.
- Mini App: New `/shipper/assignments` route; current assignment có nút "Bắt đầu giao" / "Đã giao".

**Tech Stack (mới so với P4):** Không có lib mới. Spring `@TransactionalEventListener` đã sẵn sàng từ Spring Boot.

---

## Bối cảnh từ P4

Sau P4 + tất cả fix:
- 73 commits, 98 tests pass, BUILD SUCCESS
- Backend: JWT auth + Spring Security 6, `/api/admin/**` require SHOP_OWNER
- Web Admin: Login + Dashboard + Orders + Products CRUD hoạt động
- Mini App: Customer COD flow end-to-end OK
- Order state machine sẵn sàng cho ASSIGNED/DELIVERING/DELIVERED transitions (transition table có, chỉ thiếu action endpoints)

**Constraints quan trọng:**
- Java 25 local + ByteBuddy 1.17.7
- Backend port 8080, Mini App 5173, Web Admin 5174
- Postgres `shop_delivery_postgres_dev`
- Test admin seed: `admin@shop.local / admin123` (via V5)
- Test customer ID 5555 + flow-admin@shop.local (OrderFlowIT)

**Decisions chốt:**

1. **Assignment model**: 1-to-1 với Order (UNIQUE constraint trên `order_id`). Nếu shipper từ chối, ta UPDATE existing assignment thành REJECTED và admin có thể assign shipper khác → tạo assignment mới (PostgreSQL: DELETE old + INSERT new, hoặc soft-delete). **Decision: hard delete on reject + create new on next assign.** Simple, sạch.

2. **Broadcast assign**: Khi admin chọn "Broadcast" thay vì 1 shipper cụ thể, ta:
   - Tạo `DeliveryAssignment` với `shipperId = NULL` và `status = BROADCAST`
   - Bot push offer cho tất cả shipper có `currentState = AVAILABLE`
   - Shipper đầu tiên accept → assignment được claim (UPDATE shipperId + status = ACCEPTED), các shipper khác nếu accept sau thì failed với error "Đơn đã được nhận"
   - **For P5 simplicity: skip broadcast. Admin must pick 1 specific shipper.** Broadcast là feature future.

3. **Status transitions (combined Order + Assignment)**:
   ```
   Order.PENDING → CONFIRMED → ASSIGNED → DELIVERING → DELIVERED
                                  ↑           ↑           ↑
                          Assignment:    Assignment:  Assignment:
                          OFFERED→ACCEPTED  STARTED   COMPLETED
                          (REJECTED→Order back to CONFIRMED, assignment deleted)
   ```

4. **Shipper auth**: Mini App shipper endpoints (`/api/shipper/*`) require Telegram initData AUTH + check SHIPPER role. Đã có `RoleResolver.hasRole(userId, Role.SHIPPER)`. Add `@CurrentShipper` annotation hoặc check trong controller.

   **Decision:** Add `RequireRole` annotation + handler that checks via RoleResolver. Or simpler: just check inside controller methods. **Pick the simplest:** controllers use `@CurrentUser TelegramUser user` and call `roleResolver.hasRole(user.getId(), Role.SHIPPER)` — throw `AuthorizationException` (new, 403) if not.

5. **Shipper management in Web Admin**:
   - List all users with SHIPPER role
   - Approve PENDING shippers (move user_role.status from PENDING to ACTIVE)
   - Block shippers (status = BLOCKED)
   - Create new shipper: simple form — admin enters Telegram user ID (must already exist in telegram_user table via /start), shop name, vehicle type, license plate
   - **No bot-based self-registration FSM in P5.** Future enhancement.

6. **Bot push notification on OrderAssignedEvent**:
   - Event published from DeliveryAssignmentService.assign()
   - Listener trong notification module gọi BotSender.execute(SendMessage with InlineKeyboard)
   - InlineKeyboard: `[✅ Nhận DH20260521-XXXXX] [❌ Từ chối]` với callback_data `ACCEPT_ORDER:<assignmentId>:<orderCode>` / `REJECT_ORDER:<assignmentId>`

7. **Bot callback handler**: New `OrderOfferCallbackHandler` trong bot module. Routes callback_data prefix `ACCEPT_ORDER` / `REJECT_ORDER` → call DeliveryAssignmentService → send confirmation message back.

8. **Test admin user**: Reuse `admin@shop.local` từ V5 seed. For shipper tests, need to seed a shipper:
   - Insert telegram_user (id=8888, first_name='Shipper Test')
   - Insert user_role(8888, SHIPPER, ACTIVE)
   - Insert shipper_profile(user_id=8888, vehicle_type=MOTORBIKE, license_plate='29A-12345', current_state=AVAILABLE)

---

## File Structure (sau khi P5 hoàn thành)

```
backend/
├── app/src/main/resources/db/migration/
│   └── V6__delivery.sql                                    (TASK 1)
└── modules/
    ├── delivery/
    │   ├── pom.xml                                         (modify — add deps)
    │   └── src/
    │       ├── main/java/com/shop/delivery/delivery/
    │       │   ├── domain/
    │       │   │   ├── AssignmentStatus.java               (TASK 2)
    │       │   │   ├── ShipperState.java                   (TASK 2)
    │       │   │   └── VehicleType.java                    (TASK 2)
    │       │   ├── entity/
    │       │   │   ├── ShipperProfile.java                 (TASK 2)
    │       │   │   └── DeliveryAssignment.java             (TASK 3)
    │       │   ├── repository/
    │       │   │   ├── ShipperProfileRepository.java       (TASK 2)
    │       │   │   └── DeliveryAssignmentRepository.java   (TASK 3)
    │       │   ├── service/
    │       │   │   ├── ShipperProfileService.java          (TASK 4)
    │       │   │   ├── DeliveryAssignmentService.java      (TASK 5)
    │       │   │   ├── event/
    │       │   │   │   ├── OrderAssignedEvent.java         (TASK 5)
    │       │   │   │   ├── OrderAcceptedEvent.java         (TASK 5)
    │       │   │   │   ├── OrderRejectedEvent.java         (TASK 5)
    │       │   │   │   ├── OrderStartedEvent.java          (TASK 5)
    │       │   │   │   └── OrderDeliveredEvent.java        (TASK 5)
    │       │   │   └── command/
    │       │   │       ├── AssignShipperCommand.java       (TASK 5)
    │       │   │       └── CreateShipperCommand.java       (TASK 4)
    │       │   └── api/
    │       │       ├── admin/
    │       │       │   ├── AdminShipperController.java     (TASK 8)
    │       │       │   ├── AdminAssignController.java      (TASK 8)
    │       │       │   └── dto/
    │       │       │       ├── ShipperResponse.java        (TASK 8)
    │       │       │       ├── CreateShipperRequest.java   (TASK 8)
    │       │       │       └── AssignShipperRequest.java   (TASK 8)
    │       │       └── shipper/
    │       │           ├── ShipperAssignmentController.java (TASK 9)
    │       │           └── dto/
    │       │               ├── AssignmentResponse.java     (TASK 9)
    │       │               └── AssignmentSummary.java      (TASK 9)
    │       └── test/java/com/shop/delivery/delivery/
    │           └── service/
    │               ├── ShipperProfileServiceTest.java      (TASK 4)
    │               └── DeliveryAssignmentServiceTest.java  (TASK 5)
    ├── order/
    │   └── src/main/java/com/shop/delivery/order/
    │       └── service/OrderStateMachine.java              (already has transitions — verify)
    ├── auth/
    │   └── src/main/java/com/shop/delivery/auth/
    │       └── service/RoleResolver.java                   (modify — invalidate cache on role change)
    └── notification/
        ├── pom.xml                                         (modify — add bot dep)
        └── src/main/java/com/shop/delivery/notification/
            ├── OrderAssignedNotifier.java                  (TASK 10 — listener)
            └── OrderStatusNotifier.java                    (TASK 10 — for customer notifications)

backend/modules/bot/
└── src/main/java/com/shop/delivery/bot/handler/
    └── shipper/
        ├── OrderOfferCallbackHandler.java                  (TASK 11)
        └── ShipperHelpHandler.java                         (TASK 11)

frontend/
├── shared/src/api/
│   ├── assignments.ts                                      (TASK 12 — new API funcs)
│   └── shippers.ts                                         (TASK 12 — admin API funcs)
├── shared/src/types/
│   ├── shipper.ts                                          (TASK 12)
│   └── assignment.ts                                       (TASK 12)
├── webadmin/src/pages/
│   ├── ShippersPage.tsx                                    (TASK 13)
│   └── OrderDetailPage.tsx                                 (modify — add Assign modal)
├── webadmin/src/components/
│   └── AssignShipperModal.tsx                              (TASK 13)
└── miniapp/src/pages/
    ├── ShipperAssignmentsPage.tsx                          (TASK 14)
    └── ShipperAssignmentDetailPage.tsx                     (TASK 14)
```

**File mới:** ~30
**File modify:** ~8

---

## TASK 1: V6__delivery.sql migration (1 commit)

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V6__delivery.sql`

- [ ] **Step 1: Write migration**

Create `backend/app/src/main/resources/db/migration/V6__delivery.sql`:

```sql
-- V6__delivery.sql — delivery module tables (assignment + shipper profile)
-- NOTE: location_ping table will be added in V7 (P6 Live Location)

CREATE TABLE shipper_profile (
    user_id             BIGINT PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE,
    vehicle_type        VARCHAR(16) NOT NULL,            -- MOTORBIKE | CAR | BICYCLE
    license_plate       VARCHAR(16),
    current_state       VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',  -- AVAILABLE | BUSY | OFFLINE
    rating_avg          NUMERIC(3, 2) NOT NULL DEFAULT 0.00,
    rating_count        INT NOT NULL DEFAULT 0,
    total_deliveries    INT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shipper_state ON shipper_profile(current_state);

CREATE TABLE delivery_assignment (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    shipper_id      BIGINT REFERENCES telegram_user(id),   -- nullable for BROADCAST (P5 doesn't support but column ready)
    status          VARCHAR(16) NOT NULL,                  -- OFFERED | ACCEPTED | REJECTED | STARTED | COMPLETED | CANCELLED
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at     TIMESTAMPTZ,
    rejected_at     TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ
);

CREATE INDEX idx_delivery_assignment_shipper ON delivery_assignment(shipper_id, status);
CREATE INDEX idx_delivery_assignment_order ON delivery_assignment(order_id);
```

- [ ] **Step 2: Apply migration**

```bash
docker compose -f infra/docker-compose.dev.yml ps | grep -q healthy || docker compose -f infra/docker-compose.dev.yml up -d
sleep 5

cd backend/app
BOT_TOKEN=${BOT_TOKEN:-dummy} BOT_USERNAME=${BOT_USERNAME:-DummyBot} \
  nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p5_t1.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 90); do
  grep -q "Started Application" /tmp/p5_t1.log 2>/dev/null && break
  grep -q "APPLICATION FAILED" /tmp/p5_t1.log 2>/dev/null && break
  sleep 1
done
kill $BOOT_PID 2>/dev/null
sleep 2
cd ../..
```

- [ ] **Step 3: Verify**

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY version"
# Expected: 1-6 all t

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d shipper_profile"
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d delivery_assignment"
```

- [ ] **Step 4: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V6__delivery.sql
git commit -m "feat(delivery): add V6 migration for shipper_profile + delivery_assignment"
```

---

## TASK 2: ShipperProfile entity + enums + repo (TDD, 1 commit)

**Files:**
- Modify: `backend/modules/delivery/pom.xml`
- Create: 3 enums + 1 entity + 1 repo + entity test

- [ ] **Step 1: Update `backend/modules/delivery/pom.xml`**

Read existing. Replace with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.shop.delivery</groupId>
        <artifactId>delivery-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>delivery</artifactId>
    <name>delivery</name>
    <description>Delivery assignment, shipper profile (+ location_ping in P6)</description>

    <dependencies>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>shared</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>auth</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>order</artifactId></dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Write enums**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/domain/ShipperState.java`:

```java
package com.shop.delivery.delivery.domain;

public enum ShipperState {
    AVAILABLE,
    BUSY,
    OFFLINE
}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/domain/VehicleType.java`:

```java
package com.shop.delivery.delivery.domain;

public enum VehicleType {
    MOTORBIKE,
    CAR,
    BICYCLE
}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/domain/AssignmentStatus.java`:

```java
package com.shop.delivery.delivery.domain;

public enum AssignmentStatus {
    OFFERED,    // Admin assigned to shipper, awaiting accept
    ACCEPTED,   // Shipper clicked Accept
    REJECTED,   // Shipper clicked Reject (assignment will be deleted, order back to CONFIRMED)
    STARTED,    // Shipper clicked Bắt đầu giao
    COMPLETED,  // Shipper clicked Đã giao
    CANCELLED   // Admin or customer cancelled
}
```

- [ ] **Step 3: Write failing test**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/entity/ShipperProfileTest.java`:

```java
package com.shop.delivery.delivery.entity;

import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ShipperProfileTest {

    @Test
    void shouldExposeFieldsAndDefaults() {
        ShipperProfile p = new ShipperProfile();
        p.setUserId(8888L);
        p.setVehicleType(VehicleType.MOTORBIKE);
        p.setLicensePlate("29A-12345");
        p.setCurrentState(ShipperState.AVAILABLE);

        assertThat(p.getUserId()).isEqualTo(8888L);
        assertThat(p.getVehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(p.getLicensePlate()).isEqualTo("29A-12345");
        assertThat(p.getCurrentState()).isEqualTo(ShipperState.AVAILABLE);
        assertThat(p.getRatingAvg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.getRatingCount()).isZero();
        assertThat(p.getTotalDeliveries()).isZero();
    }

    @Test
    void newInstanceShouldDefaultToOfflineState() {
        ShipperProfile p = new ShipperProfile();
        assertThat(p.getCurrentState()).isEqualTo(ShipperState.OFFLINE);
    }
}
```

- [ ] **Step 4: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/delivery -am test
```

- [ ] **Step 5: Implement `ShipperProfile`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/ShipperProfile.java`:

```java
package com.shop.delivery.delivery.entity;

import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.domain.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shipper_profile")
public class ShipperProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 16)
    private VehicleType vehicleType;

    @Column(name = "license_plate", length = 16)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 16)
    private ShipperState currentState = ShipperState.OFFLINE;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Column(name = "total_deliveries", nullable = false)
    private Integer totalDeliveries = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public ShipperState getCurrentState() { return currentState; }
    public void setCurrentState(ShipperState currentState) { this.currentState = currentState; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public void setRatingAvg(BigDecimal ratingAvg) { this.ratingAvg = ratingAvg; }
    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
    public Integer getTotalDeliveries() { return totalDeliveries; }
    public void setTotalDeliveries(Integer totalDeliveries) { this.totalDeliveries = totalDeliveries; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 6: Implement `ShipperProfileRepository`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/ShipperProfileRepository.java`:

```java
package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.entity.ShipperProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipperProfileRepository extends JpaRepository<ShipperProfile, Long> {
    List<ShipperProfile> findAllByCurrentState(ShipperState state);
}
```

- [ ] **Step 7: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/delivery -am test
```

- [ ] **Step 8: Commit**

```bash
git add backend/modules/delivery/
git commit -m "feat(delivery): add ShipperProfile entity + repo + enums"
```

---

## TASK 3: DeliveryAssignment entity + repo (TDD, 1 commit)

**Files:**
- Create: entity + repo + test

- [ ] **Step 1: Write failing test**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/entity/DeliveryAssignmentTest.java`:

```java
package com.shop.delivery.delivery.entity;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAssignmentTest {

    @Test
    void shouldExposeFieldsAndDefaults() {
        DeliveryAssignment a = new DeliveryAssignment();
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        a.setId(id);
        a.setOrderId(orderId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.OFFERED);
        Instant now = Instant.now();
        a.setAcceptedAt(now);

        assertThat(a.getId()).isEqualTo(id);
        assertThat(a.getOrderId()).isEqualTo(orderId);
        assertThat(a.getShipperId()).isEqualTo(8888L);
        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.OFFERED);
        assertThat(a.getAcceptedAt()).isEqualTo(now);
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

- [ ] **Step 3: Implement `DeliveryAssignment`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/DeliveryAssignment.java`:

```java
package com.shop.delivery.delivery.entity;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_assignment")
public class DeliveryAssignment {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "shipper_id")
    private Long shipperId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AssignmentStatus status;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public Long getShipperId() { return shipperId; }
    public void setShipperId(Long shipperId) { this.shipperId = shipperId; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(Instant rejectedAt) { this.rejectedAt = rejectedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
```

- [ ] **Step 4: Implement `DeliveryAssignmentRepository`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/DeliveryAssignmentRepository.java`:

```java
package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    Optional<DeliveryAssignment> findByOrderId(UUID orderId);

    Page<DeliveryAssignment> findAllByShipperIdAndStatusInOrderByAssignedAtDesc(
        Long shipperId, List<AssignmentStatus> statuses, Pageable pageable);

    List<DeliveryAssignment> findAllByShipperIdAndStatusIn(Long shipperId, List<AssignmentStatus> statuses);
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/delivery -am test
```

- [ ] **Step 6: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/entity/DeliveryAssignment.java \
        backend/modules/delivery/src/main/java/com/shop/delivery/delivery/repository/DeliveryAssignmentRepository.java \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/entity/DeliveryAssignmentTest.java
git commit -m "feat(delivery): add DeliveryAssignment entity + repo"
```

---

## TASK 4: ShipperProfileService + CreateShipperCommand (TDD, 1 commit)

**Files:**
- Create: command + service + test

- [ ] **Step 1: Write `CreateShipperCommand`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/command/CreateShipperCommand.java`:

```java
package com.shop.delivery.delivery.service.command;

import com.shop.delivery.delivery.domain.VehicleType;

public record CreateShipperCommand(
    Long telegramUserId,
    VehicleType vehicleType,
    String licensePlate
) {}
```

- [ ] **Step 2: Write failing test**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/ShipperProfileServiceTest.java`:

```java
package com.shop.delivery.delivery.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.domain.VehicleType;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.delivery.service.command.CreateShipperCommand;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperProfileServiceTest {

    @Mock TelegramUserRepository userRepo;
    @Mock UserRoleRepository roleRepo;
    @Mock ShipperProfileRepository profileRepo;
    @Mock RoleResolver roleResolver;

    @InjectMocks ShipperProfileService service;

    @Test
    void createShipperShouldAssignRoleAndCreateProfile() {
        TelegramUser u = new TelegramUser();
        u.setId(8888L);
        u.setFirstName("Test Shipper");
        when(userRepo.findById(8888L)).thenReturn(Optional.of(u));
        when(roleRepo.existsByTelegramUserIdAndRoleAndStatus(8888L, Role.SHIPPER, UserRoleStatus.ACTIVE)).thenReturn(false);
        when(profileRepo.save(any(ShipperProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ShipperProfile result = service.createShipper(new CreateShipperCommand(
            8888L, VehicleType.MOTORBIKE, "29A-12345"));

        assertThat(result.getUserId()).isEqualTo(8888L);
        assertThat(result.getVehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(result.getLicensePlate()).isEqualTo("29A-12345");
        assertThat(result.getCurrentState()).isEqualTo(ShipperState.OFFLINE);

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(roleRepo).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRole()).isEqualTo(Role.SHIPPER);
        assertThat(roleCaptor.getValue().getStatus()).isEqualTo(UserRoleStatus.ACTIVE);
    }

    @Test
    void createShipperShouldThrowIfTelegramUserNotFound() {
        when(userRepo.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createShipper(
            new CreateShipperCommand(9999L, VehicleType.MOTORBIKE, "X")
        )).isInstanceOf(NotFoundException.class);
    }

    @Test
    void setStateShouldUpdateProfile() {
        ShipperProfile p = new ShipperProfile();
        p.setUserId(8888L);
        p.setCurrentState(ShipperState.OFFLINE);
        when(profileRepo.findById(8888L)).thenReturn(Optional.of(p));
        when(profileRepo.save(any(ShipperProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setState(8888L, ShipperState.AVAILABLE);

        ArgumentCaptor<ShipperProfile> captor = ArgumentCaptor.forClass(ShipperProfile.class);
        verify(profileRepo).save(captor.capture());
        assertThat(captor.getValue().getCurrentState()).isEqualTo(ShipperState.AVAILABLE);
    }

    @Test
    void listAllShouldReturnAllShipperProfiles() {
        ShipperProfile p1 = new ShipperProfile();
        p1.setUserId(1L);
        p1.setCurrentState(ShipperState.AVAILABLE);
        ShipperProfile p2 = new ShipperProfile();
        p2.setUserId(2L);
        p2.setCurrentState(ShipperState.OFFLINE);
        when(profileRepo.findAll()).thenReturn(List.of(p1, p2));

        List<ShipperProfile> result = service.listAll();
        assertThat(result).hasSize(2);
    }
}
```

- [ ] **Step 3: Run test — expect compile fail**

- [ ] **Step 4: Implement `ShipperProfileService`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ShipperProfileService.java`:

```java
package com.shop.delivery.delivery.service;

import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.domain.UserRoleStatus;
import com.shop.delivery.auth.entity.UserRole;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.auth.repository.UserRoleRepository;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.delivery.service.command.CreateShipperCommand;
import com.shop.delivery.shared.exception.NotFoundException;
import com.shop.delivery.shared.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ShipperProfileService {

    private final TelegramUserRepository userRepo;
    private final UserRoleRepository roleRepo;
    private final ShipperProfileRepository profileRepo;
    private final RoleResolver roleResolver;

    public ShipperProfileService(TelegramUserRepository userRepo,
                                 UserRoleRepository roleRepo,
                                 ShipperProfileRepository profileRepo,
                                 RoleResolver roleResolver) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.profileRepo = profileRepo;
        this.roleResolver = roleResolver;
    }

    @Transactional
    public ShipperProfile createShipper(CreateShipperCommand cmd) {
        userRepo.findById(cmd.telegramUserId())
            .orElseThrow(() -> new NotFoundException(
                "USER_NOT_FOUND",
                "Telegram user " + cmd.telegramUserId() + " chưa tồn tại — họ phải /start bot trước"));

        // Assign SHIPPER role if not already
        if (!roleRepo.existsByTelegramUserIdAndRoleAndStatus(cmd.telegramUserId(), Role.SHIPPER, UserRoleStatus.ACTIVE)) {
            UserRole role = new UserRole();
            role.setTelegramUserId(cmd.telegramUserId());
            role.setRole(Role.SHIPPER);
            role.setStatus(UserRoleStatus.ACTIVE);
            role.setAssignedAt(Instant.now());
            roleRepo.save(role);
            roleResolver.evict(cmd.telegramUserId());
        }

        // Create profile (or fail if exists)
        if (profileRepo.findById(cmd.telegramUserId()).isPresent()) {
            throw new ValidationException("SHIPPER_EXISTS", "Shipper đã tồn tại");
        }

        ShipperProfile profile = new ShipperProfile();
        profile.setUserId(cmd.telegramUserId());
        profile.setVehicleType(cmd.vehicleType());
        profile.setLicensePlate(cmd.licensePlate());
        profile.setCurrentState(ShipperState.OFFLINE);
        return profileRepo.save(profile);
    }

    @Transactional(readOnly = true)
    public ShipperProfile findById(Long userId) {
        return profileRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("SHIPPER_NOT_FOUND", "Shipper " + userId + " không tồn tại"));
    }

    @Transactional(readOnly = true)
    public List<ShipperProfile> listAll() {
        return profileRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<ShipperProfile> listAvailable() {
        return profileRepo.findAllByCurrentState(ShipperState.AVAILABLE);
    }

    @Transactional
    public ShipperProfile setState(Long userId, ShipperState state) {
        ShipperProfile p = findById(userId);
        p.setCurrentState(state);
        return profileRepo.save(p);
    }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/delivery -am test
```

- [ ] **Step 6: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/
git commit -m "feat(delivery): add ShipperProfileService (create, list, set state)"
```

---

## TASK 5: DeliveryAssignmentService + Events (TDD, 1 commit)

**Files:**
- 5 event records + 1 command + service + test

This is the largest task. Take care.

- [ ] **Step 1: Write events**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/event/OrderAssignedEvent.java`:

```java
package com.shop.delivery.delivery.service.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderAssignedEvent(
    UUID assignmentId,
    UUID orderId,
    String orderCode,
    Long shipperId,
    String deliveryAddress,
    BigDecimal distanceKm,
    BigDecimal deliveryFee
) {}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/event/OrderAcceptedEvent.java`:

```java
package com.shop.delivery.delivery.service.event;

import java.util.UUID;

public record OrderAcceptedEvent(
    UUID assignmentId,
    UUID orderId,
    String orderCode,
    Long shipperId,
    Long customerId
) {}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/event/OrderRejectedEvent.java`:

```java
package com.shop.delivery.delivery.service.event;

import java.util.UUID;

public record OrderRejectedEvent(
    UUID orderId,
    String orderCode,
    Long shipperId
) {}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/event/OrderStartedEvent.java`:

```java
package com.shop.delivery.delivery.service.event;

import java.util.UUID;

public record OrderStartedEvent(
    UUID assignmentId,
    UUID orderId,
    String orderCode,
    Long shipperId,
    Long customerId
) {}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/event/OrderDeliveredEvent.java`:

```java
package com.shop.delivery.delivery.service.event;

import java.util.UUID;

public record OrderDeliveredEvent(
    UUID assignmentId,
    UUID orderId,
    String orderCode,
    Long shipperId,
    Long customerId
) {}
```

- [ ] **Step 2: Write `AssignShipperCommand`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/command/AssignShipperCommand.java`:

```java
package com.shop.delivery.delivery.service.command;

import java.util.UUID;

public record AssignShipperCommand(
    UUID orderId,
    Long shipperId,
    Long adminUserId
) {}
```

- [ ] **Step 3: Write failing test**

Create `backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/DeliveryAssignmentServiceTest.java`:

```java
package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.delivery.service.command.AssignShipperCommand;
import com.shop.delivery.delivery.service.event.OrderAcceptedEvent;
import com.shop.delivery.delivery.service.event.OrderAssignedEvent;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import com.shop.delivery.delivery.service.event.OrderStartedEvent;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryAssignmentServiceTest {

    @Mock DeliveryAssignmentRepository assignmentRepo;
    @Mock ShipperProfileRepository shipperRepo;
    @Mock OrderRepository orderRepo;
    @Mock OrderService orderService;
    @Mock ApplicationEventPublisher events;

    DeliveryAssignmentService service;

    private DeliveryAssignmentService newService() {
        return new DeliveryAssignmentService(assignmentRepo, shipperRepo, orderRepo, orderService, events);
    }

    @Test
    void assignShouldCreateAssignmentTransitionOrderAndPublishEvent() {
        service = newService();
        UUID orderId = UUID.randomUUID();
        Order o = new Order();
        o.setId(orderId);
        o.setCode("DH20260521-A");
        o.setStatus(OrderStatus.CONFIRMED);
        o.setDeliveryAddress("45 Bà Triệu");
        o.setDistanceKm(new BigDecimal("1.5"));
        o.setDeliveryFee(new BigDecimal("20000"));
        o.setCustomerId(5555L);

        ShipperProfile sp = new ShipperProfile();
        sp.setUserId(8888L);
        sp.setCurrentState(ShipperState.AVAILABLE);

        when(orderService.findById(orderId)).thenReturn(o);
        when(shipperRepo.findById(8888L)).thenReturn(Optional.of(sp));
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(assignmentRepo.save(any(DeliveryAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryAssignment result = service.assign(new AssignShipperCommand(orderId, 8888L, 1L));

        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getShipperId()).isEqualTo(8888L);
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.OFFERED);

        verify(orderService).transitionStatus(orderId, OrderStatus.ASSIGNED, 1L, "Gán shipper " + 8888L);
        verify(events).publishEvent(any(OrderAssignedEvent.class));
    }

    @Test
    void assignShouldThrowIfOrderAlreadyAssigned() {
        service = newService();
        UUID orderId = UUID.randomUUID();
        Order o = new Order();
        o.setId(orderId);
        o.setStatus(OrderStatus.ASSIGNED);

        when(orderService.findById(orderId)).thenReturn(o);

        assertThatThrownBy(() ->
            service.assign(new AssignShipperCommand(orderId, 8888L, 1L))
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("đã được gán");
    }

    @Test
    void assignShouldThrowIfShipperNotAvailable() {
        service = newService();
        UUID orderId = UUID.randomUUID();
        Order o = new Order();
        o.setId(orderId);
        o.setStatus(OrderStatus.CONFIRMED);

        ShipperProfile sp = new ShipperProfile();
        sp.setUserId(8888L);
        sp.setCurrentState(ShipperState.OFFLINE);

        when(orderService.findById(orderId)).thenReturn(o);
        when(shipperRepo.findById(8888L)).thenReturn(Optional.of(sp));

        assertThatThrownBy(() ->
            service.assign(new AssignShipperCommand(orderId, 8888L, 1L))
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("không sẵn sàng");
    }

    @Test
    void acceptShouldSetAssignmentAcceptedAndPublishEvent() {
        service = newService();
        UUID assignmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setOrderId(orderId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.OFFERED);

        Order o = new Order();
        o.setId(orderId);
        o.setCode("DH20260521-A");
        o.setCustomerId(5555L);

        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(a));
        when(orderService.findById(orderId)).thenReturn(o);
        when(assignmentRepo.save(any(DeliveryAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryAssignment result = service.accept(assignmentId, 8888L);

        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.ACCEPTED);
        assertThat(result.getAcceptedAt()).isNotNull();
        verify(events).publishEvent(any(OrderAcceptedEvent.class));
    }

    @Test
    void acceptShouldFailIfShipperIsNotAssignedToOrder() {
        service = newService();
        UUID assignmentId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.OFFERED);

        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(a));

        assertThatThrownBy(() ->
            service.accept(assignmentId, 9999L)  // wrong shipper
        ).isInstanceOf(NotFoundException.class);
    }

    @Test
    void startShouldTransitionAssignmentAndOrderToDelivering() {
        service = newService();
        UUID assignmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setOrderId(orderId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.ACCEPTED);

        Order o = new Order();
        o.setId(orderId);
        o.setCode("DH20260521-A");
        o.setCustomerId(5555L);

        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(a));
        when(orderService.findById(orderId)).thenReturn(o);
        when(assignmentRepo.save(any(DeliveryAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.start(assignmentId, 8888L);

        verify(orderService).transitionStatus(orderId, OrderStatus.DELIVERING, 8888L, "Shipper bắt đầu giao");
        verify(events).publishEvent(any(OrderStartedEvent.class));
    }

    @Test
    void completeShouldTransitionAssignmentAndOrderToDelivered() {
        service = newService();
        UUID assignmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(assignmentId);
        a.setOrderId(orderId);
        a.setShipperId(8888L);
        a.setStatus(AssignmentStatus.STARTED);

        Order o = new Order();
        o.setId(orderId);
        o.setCode("DH20260521-A");
        o.setCustomerId(5555L);

        ShipperProfile sp = new ShipperProfile();
        sp.setUserId(8888L);
        sp.setTotalDeliveries(5);

        when(assignmentRepo.findById(assignmentId)).thenReturn(Optional.of(a));
        when(orderService.findById(orderId)).thenReturn(o);
        when(shipperRepo.findById(8888L)).thenReturn(Optional.of(sp));
        when(assignmentRepo.save(any(DeliveryAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shipperRepo.save(any(ShipperProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.complete(assignmentId, 8888L);

        verify(orderService).transitionStatus(orderId, OrderStatus.DELIVERED, 8888L, "Shipper đã giao");

        ArgumentCaptor<ShipperProfile> spCaptor = ArgumentCaptor.forClass(ShipperProfile.class);
        verify(shipperRepo).save(spCaptor.capture());
        assertThat(spCaptor.getValue().getTotalDeliveries()).isEqualTo(6);

        verify(events).publishEvent(any(OrderDeliveredEvent.class));
    }
}
```

- [ ] **Step 4: Run test — expect compile fail**

Will fail because `OrderService.transitionStatus(...)` doesn't exist yet — service has `confirm()` and `cancel()` but not a generic transition. We'll add that.

- [ ] **Step 5: Extend `OrderService` with `transitionStatus`**

Read existing `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java`. Add a public method:

```java
/**
 * Generic status transition — used by delivery module to transition order to ASSIGNED/DELIVERING/DELIVERED.
 * Validates transition via OrderStateMachine, records to status_history.
 */
@Transactional
public Order transitionStatus(UUID orderId, OrderStatus to, Long actorUserId, String note) {
    return transition(orderId, to, actorUserId, note);
}
```

Note: the existing `transition` method is private. We're exposing it via a public wrapper. Alternatively just make `transition` public — but the wrapper keeps semantic distinction. Let me just make `transition` public:

Actually simpler: change `private Order transition(...)` to `public Order transitionStatus(...)`. Rename to be clear.

In the existing `OrderService.java`, find:
```java
private Order transition(UUID id, OrderStatus to, Long actorUserId, String note) {
```
and rename to:
```java
public Order transitionStatus(UUID id, OrderStatus to, Long actorUserId, String note) {
```

Also update callers:
- `confirm`: change `transition(...)` to `transitionStatus(...)`
- `cancel`: change `transition(...)` to `transitionStatus(...)`

This is a refactor — make sure to update them.

- [ ] **Step 6: Implement `DeliveryAssignmentService`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/DeliveryAssignmentService.java`:

```java
package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.delivery.service.command.AssignShipperCommand;
import com.shop.delivery.delivery.service.event.OrderAcceptedEvent;
import com.shop.delivery.delivery.service.event.OrderAssignedEvent;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import com.shop.delivery.delivery.service.event.OrderRejectedEvent;
import com.shop.delivery.delivery.service.event.OrderStartedEvent;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository assignmentRepo;
    private final ShipperProfileRepository shipperRepo;
    private final OrderRepository orderRepo;
    private final OrderService orderService;
    private final ApplicationEventPublisher events;

    public DeliveryAssignmentService(DeliveryAssignmentRepository assignmentRepo,
                                     ShipperProfileRepository shipperRepo,
                                     OrderRepository orderRepo,
                                     OrderService orderService,
                                     ApplicationEventPublisher events) {
        this.assignmentRepo = assignmentRepo;
        this.shipperRepo = shipperRepo;
        this.orderRepo = orderRepo;
        this.orderService = orderService;
        this.events = events;
    }

    @Transactional
    public DeliveryAssignment assign(AssignShipperCommand cmd) {
        Order order = orderService.findById(cmd.orderId());
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessRuleException(
                "ORDER_NOT_ASSIGNABLE",
                "Đơn ở trạng thái " + order.getStatus() + " — không thể gán shipper (chỉ CONFIRMED đã được gán)");
        }
        if (assignmentRepo.findByOrderId(cmd.orderId()).isPresent()) {
            throw new BusinessRuleException(
                "ORDER_ALREADY_ASSIGNED",
                "Đơn đã được gán shipper rồi");
        }

        ShipperProfile shipper = shipperRepo.findById(cmd.shipperId())
            .orElseThrow(() -> new NotFoundException("SHIPPER_NOT_FOUND", "Shipper " + cmd.shipperId() + " không tồn tại"));
        if (shipper.getCurrentState() != ShipperState.AVAILABLE) {
            throw new BusinessRuleException(
                "SHIPPER_NOT_AVAILABLE",
                "Shipper " + cmd.shipperId() + " không sẵn sàng (trạng thái: " + shipper.getCurrentState() + ")");
        }

        DeliveryAssignment a = new DeliveryAssignment();
        a.setId(UUID.randomUUID());
        a.setOrderId(cmd.orderId());
        a.setShipperId(cmd.shipperId());
        a.setStatus(AssignmentStatus.OFFERED);
        a.setAssignedAt(Instant.now());
        DeliveryAssignment saved = assignmentRepo.save(a);

        // Move order to ASSIGNED
        orderService.transitionStatus(cmd.orderId(), OrderStatus.ASSIGNED, cmd.adminUserId(),
            "Gán shipper " + cmd.shipperId());

        events.publishEvent(new OrderAssignedEvent(
            saved.getId(), order.getId(), order.getCode(), cmd.shipperId(),
            order.getDeliveryAddress(), order.getDistanceKm(), order.getDeliveryFee()
        ));

        return saved;
    }

    @Transactional
    public DeliveryAssignment accept(UUID assignmentId, Long actingShipperId) {
        DeliveryAssignment a = findById(assignmentId);
        if (!a.getShipperId().equals(actingShipperId)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND",
                "Không tìm thấy đơn được gán cho bạn");
        }
        if (a.getStatus() != AssignmentStatus.OFFERED) {
            throw new BusinessRuleException("INVALID_ASSIGNMENT_STATE",
                "Đơn đã ở trạng thái " + a.getStatus() + " — không thể chấp nhận");
        }

        a.setStatus(AssignmentStatus.ACCEPTED);
        a.setAcceptedAt(Instant.now());
        DeliveryAssignment saved = assignmentRepo.save(a);

        // Set shipper BUSY
        shipperRepo.findById(actingShipperId).ifPresent(sp -> {
            sp.setCurrentState(ShipperState.BUSY);
            shipperRepo.save(sp);
        });

        Order order = orderService.findById(a.getOrderId());
        events.publishEvent(new OrderAcceptedEvent(
            saved.getId(), order.getId(), order.getCode(), actingShipperId, order.getCustomerId()
        ));
        return saved;
    }

    @Transactional
    public void reject(UUID assignmentId, Long actingShipperId) {
        DeliveryAssignment a = findById(assignmentId);
        if (!a.getShipperId().equals(actingShipperId)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND",
                "Không tìm thấy đơn được gán cho bạn");
        }
        if (a.getStatus() != AssignmentStatus.OFFERED) {
            throw new BusinessRuleException("INVALID_ASSIGNMENT_STATE",
                "Đơn đã ở trạng thái " + a.getStatus() + " — không thể từ chối");
        }

        Order order = orderService.findById(a.getOrderId());

        // Delete assignment; move order back to CONFIRMED so admin can re-assign
        assignmentRepo.deleteById(assignmentId);
        orderService.transitionStatus(order.getId(), OrderStatus.CONFIRMED, actingShipperId,
            "Shipper từ chối — đơn quay về CONFIRMED");

        events.publishEvent(new OrderRejectedEvent(order.getId(), order.getCode(), actingShipperId));
    }

    @Transactional
    public DeliveryAssignment start(UUID assignmentId, Long actingShipperId) {
        DeliveryAssignment a = findById(assignmentId);
        if (!a.getShipperId().equals(actingShipperId)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND", "Không tìm thấy đơn được gán cho bạn");
        }
        if (a.getStatus() != AssignmentStatus.ACCEPTED) {
            throw new BusinessRuleException("INVALID_ASSIGNMENT_STATE",
                "Phải accept trước khi bắt đầu giao (hiện tại: " + a.getStatus() + ")");
        }

        a.setStatus(AssignmentStatus.STARTED);
        a.setStartedAt(Instant.now());
        DeliveryAssignment saved = assignmentRepo.save(a);

        Order order = orderService.findById(a.getOrderId());
        orderService.transitionStatus(order.getId(), OrderStatus.DELIVERING, actingShipperId,
            "Shipper bắt đầu giao");

        events.publishEvent(new OrderStartedEvent(
            saved.getId(), order.getId(), order.getCode(), actingShipperId, order.getCustomerId()
        ));
        return saved;
    }

    @Transactional
    public DeliveryAssignment complete(UUID assignmentId, Long actingShipperId) {
        DeliveryAssignment a = findById(assignmentId);
        if (!a.getShipperId().equals(actingShipperId)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND", "Không tìm thấy đơn được gán cho bạn");
        }
        if (a.getStatus() != AssignmentStatus.STARTED) {
            throw new BusinessRuleException("INVALID_ASSIGNMENT_STATE",
                "Phải bắt đầu giao trước (hiện tại: " + a.getStatus() + ")");
        }

        a.setStatus(AssignmentStatus.COMPLETED);
        a.setDeliveredAt(Instant.now());
        DeliveryAssignment saved = assignmentRepo.save(a);

        Order order = orderService.findById(a.getOrderId());
        orderService.transitionStatus(order.getId(), OrderStatus.DELIVERED, actingShipperId,
            "Shipper đã giao");

        // Increment shipper total_deliveries + set back to AVAILABLE
        shipperRepo.findById(actingShipperId).ifPresent(sp -> {
            sp.setTotalDeliveries(sp.getTotalDeliveries() + 1);
            sp.setCurrentState(ShipperState.AVAILABLE);
            shipperRepo.save(sp);
        });

        events.publishEvent(new OrderDeliveredEvent(
            saved.getId(), order.getId(), order.getCode(), actingShipperId, order.getCustomerId()
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public DeliveryAssignment findById(UUID id) {
        return assignmentRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND", "Đơn gán " + id + " không tồn tại"));
    }

    @Transactional(readOnly = true)
    public List<DeliveryAssignment> findMine(Long shipperId, List<AssignmentStatus> statuses) {
        return assignmentRepo.findAllByShipperIdAndStatusIn(shipperId, statuses);
    }
}
```

- [ ] **Step 7: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/delivery -am test
```

Expected: 6 tests pass + previous 3 = 9 in delivery module.

- [ ] **Step 8: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/service/ \
        backend/modules/delivery/src/test/java/com/shop/delivery/delivery/service/DeliveryAssignmentServiceTest.java \
        backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java
git commit -m "feat(delivery): add DeliveryAssignmentService + 5 events + expose OrderService.transitionStatus"
```

---

## TASK 6: Admin endpoints — Shippers + Assign (1 commit)

**Files:**
- Create: 3 DTOs + 2 controllers in delivery module

- [ ] **Step 1: Write DTOs**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/ShipperResponse.java`:

```java
package com.shop.delivery.delivery.api.admin.dto;

import com.shop.delivery.delivery.domain.ShipperState;
import com.shop.delivery.delivery.domain.VehicleType;

import java.math.BigDecimal;

public record ShipperResponse(
    Long userId,
    String firstName,
    String lastName,
    String username,
    VehicleType vehicleType,
    String licensePlate,
    ShipperState currentState,
    BigDecimal ratingAvg,
    Integer ratingCount,
    Integer totalDeliveries
) {}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/CreateShipperRequest.java`:

```java
package com.shop.delivery.delivery.api.admin.dto;

import com.shop.delivery.delivery.domain.VehicleType;
import jakarta.validation.constraints.NotNull;

public record CreateShipperRequest(
    @NotNull Long telegramUserId,
    @NotNull VehicleType vehicleType,
    String licensePlate
) {}
```

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/dto/AssignShipperRequest.java`:

```java
package com.shop.delivery.delivery.api.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AssignShipperRequest(
    @NotNull Long shipperId
) {}
```

- [ ] **Step 2: Write `AdminShipperController`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/AdminShipperController.java`:

```java
package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import com.shop.delivery.delivery.api.admin.dto.CreateShipperRequest;
import com.shop.delivery.delivery.api.admin.dto.ShipperResponse;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.service.ShipperProfileService;
import com.shop.delivery.delivery.service.command.CreateShipperCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shippers")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminShipperController {

    private final ShipperProfileService service;
    private final TelegramUserRepository userRepo;

    public AdminShipperController(ShipperProfileService service, TelegramUserRepository userRepo) {
        this.service = service;
        this.userRepo = userRepo;
    }

    @GetMapping
    public List<ShipperResponse> list() {
        return service.listAll().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipperResponse create(@Valid @RequestBody CreateShipperRequest req) {
        ShipperProfile p = service.createShipper(new CreateShipperCommand(
            req.telegramUserId(), req.vehicleType(), req.licensePlate()
        ));
        return toResponse(p);
    }

    private ShipperResponse toResponse(ShipperProfile p) {
        TelegramUser u = userRepo.findById(p.getUserId()).orElse(null);
        return new ShipperResponse(
            p.getUserId(),
            u != null ? u.getFirstName() : null,
            u != null ? u.getLastName() : null,
            u != null ? u.getUsername() : null,
            p.getVehicleType(),
            p.getLicensePlate(),
            p.getCurrentState(),
            p.getRatingAvg(),
            p.getRatingCount(),
            p.getTotalDeliveries()
        );
    }
}
```

- [ ] **Step 3: Write `AdminAssignController`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/admin/AdminAssignController.java`:

```java
package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.auth.api.admin.AdminPrincipal;
import com.shop.delivery.delivery.api.admin.dto.AssignShipperRequest;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.service.DeliveryAssignmentService;
import com.shop.delivery.delivery.service.command.AssignShipperCommand;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin endpoint to assign a shipper to a CONFIRMED order.
 * Wired under /api/admin/orders/{orderId}/assign (not /shippers/...) to be discoverable from order context.
 */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminAssignController {

    private final DeliveryAssignmentService service;

    public AdminAssignController(DeliveryAssignmentService service) {
        this.service = service;
    }

    public record AssignmentResponse(
        java.util.UUID id,
        java.util.UUID orderId,
        Long shipperId,
        String status
    ) {}

    @PostMapping("/{orderId}/assign")
    public AssignmentResponse assign(@AuthenticationPrincipal AdminPrincipal admin,
                                     @PathVariable UUID orderId,
                                     @Valid @RequestBody AssignShipperRequest req) {
        DeliveryAssignment a = service.assign(new AssignShipperCommand(orderId, req.shipperId(), admin.adminUserId()));
        return new AssignmentResponse(a.getId(), a.getOrderId(), a.getShipperId(), a.getStatus().name());
    }
}
```

- [ ] **Step 4: Verify compile**

```bash
cd backend && ./mvnw -pl modules/delivery -am compile
```

- [ ] **Step 5: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/
git commit -m "feat(delivery): add admin shipper CRUD + assign endpoints"
```

---

## TASK 7: Shipper endpoints — Accept/Reject/Start/Complete + List (1 commit)

**Files:**
- 2 DTOs + 1 controller

- [ ] **Step 1: Write DTOs**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/shipper/dto/AssignmentResponse.java`:

```java
package com.shop.delivery.delivery.api.shipper.dto;

import com.shop.delivery.delivery.domain.AssignmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
    UUID id,
    UUID orderId,
    String orderCode,
    Long customerId,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    BigDecimal deliveryLat,
    BigDecimal deliveryLng,
    BigDecimal distanceKm,
    BigDecimal deliveryFee,
    BigDecimal total,
    AssignmentStatus status,
    String orderStatus,
    Instant assignedAt,
    Instant acceptedAt,
    Instant startedAt,
    Instant deliveredAt
) {}
```

- [ ] **Step 2: Write `ShipperAssignmentController`**

Create `backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/shipper/ShipperAssignmentController.java`:

```java
package com.shop.delivery.delivery.api.shipper;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.api.shipper.dto.AssignmentResponse;
import com.shop.delivery.delivery.domain.AssignmentStatus;
import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.service.DeliveryAssignmentService;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.shared.exception.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipper/assignments")
public class ShipperAssignmentController {

    private final DeliveryAssignmentService service;
    private final OrderService orderService;
    private final RoleResolver roleResolver;

    public ShipperAssignmentController(DeliveryAssignmentService service,
                                       OrderService orderService,
                                       RoleResolver roleResolver) {
        this.service = service;
        this.orderService = orderService;
        this.roleResolver = roleResolver;
    }

    private void requireShipper(TelegramUser user) {
        if (!roleResolver.hasRole(user.getId(), Role.SHIPPER)) {
            throw new AuthenticationException("NOT_SHIPPER", "Bạn không phải shipper");
        }
    }

    /** Active assignments (offered, accepted, started) — what shipper sees in Mini App */
    @GetMapping
    public List<AssignmentResponse> listActive(@CurrentUser TelegramUser user) {
        requireShipper(user);
        List<DeliveryAssignment> active = service.findMine(user.getId(),
            List.of(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED, AssignmentStatus.STARTED));
        return active.stream().map(this::toResponse).toList();
    }

    @GetMapping("/history")
    public List<AssignmentResponse> listHistory(@CurrentUser TelegramUser user) {
        requireShipper(user);
        List<DeliveryAssignment> done = service.findMine(user.getId(),
            List.of(AssignmentStatus.COMPLETED, AssignmentStatus.CANCELLED));
        return done.stream().map(this::toResponse).toList();
    }

    @PostMapping("/{id}/accept")
    public AssignmentResponse accept(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        requireShipper(user);
        return toResponse(service.accept(id, user.getId()));
    }

    @PostMapping("/{id}/reject")
    public void reject(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        requireShipper(user);
        service.reject(id, user.getId());
    }

    @PostMapping("/{id}/start")
    public AssignmentResponse start(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        requireShipper(user);
        return toResponse(service.start(id, user.getId()));
    }

    @PostMapping("/{id}/complete")
    public AssignmentResponse complete(@CurrentUser TelegramUser user, @PathVariable UUID id) {
        requireShipper(user);
        return toResponse(service.complete(id, user.getId()));
    }

    private AssignmentResponse toResponse(DeliveryAssignment a) {
        Order o = orderService.findById(a.getOrderId());
        return new AssignmentResponse(
            a.getId(), o.getId(), o.getCode(),
            o.getCustomerId(), o.getCustomerName(), o.getCustomerPhone(),
            o.getDeliveryAddress(), o.getDeliveryLat(), o.getDeliveryLng(),
            o.getDistanceKm(), o.getDeliveryFee(), o.getTotal(),
            a.getStatus(), o.getStatus().name(),
            a.getAssignedAt(), a.getAcceptedAt(), a.getStartedAt(), a.getDeliveredAt()
        );
    }
}
```

- [ ] **Step 3: Verify compile**

```bash
cd backend && ./mvnw -pl modules/delivery -am compile
```

- [ ] **Step 4: Commit**

```bash
git add backend/modules/delivery/src/main/java/com/shop/delivery/delivery/api/shipper/
git commit -m "feat(delivery): add shipper endpoints (list, accept/reject/start/complete)"
```

---

## TASK 8: Bot — OrderAssigned listener + Accept/Reject callbacks (1 commit)

**Files:**
- Modify: `backend/modules/notification/pom.xml` — add bot + delivery deps
- Create: notification listener + bot callback handler

- [ ] **Step 1: Update `backend/modules/notification/pom.xml`**

Read existing. Replace with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.shop.delivery</groupId>
        <artifactId>delivery-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>notification</artifactId>
    <name>notification</name>
    <description>Domain event listener — Telegram + WebSocket</description>

    <dependencies>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>shared</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>auth</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>order</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>delivery</artifactId></dependency>
        <dependency><groupId>com.shop.delivery</groupId><artifactId>bot</artifactId></dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Write `OrderAssignedNotifier`**

Create `backend/modules/notification/src/main/java/com/shop/delivery/notification/OrderAssignedNotifier.java`:

```java
package com.shop.delivery.notification;

import com.shop.delivery.bot.sender.BotSender;
import com.shop.delivery.delivery.service.event.OrderAcceptedEvent;
import com.shop.delivery.delivery.service.event.OrderAssignedEvent;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import com.shop.delivery.delivery.service.event.OrderRejectedEvent;
import com.shop.delivery.delivery.service.event.OrderStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

/**
 * Listener that pushes Telegram notifications when assignments change state.
 * Uses @TransactionalEventListener(AFTER_COMMIT) so we don't notify if the DB transaction rolled back.
 */
@Component
public class OrderAssignedNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderAssignedNotifier.class);

    private final BotSender bot;

    public OrderAssignedNotifier(BotSender bot) {
        this.bot = bot;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAssigned(OrderAssignedEvent e) {
        String text = String.format(
            """
            📦 Đơn mới %s
            📍 Giao: %s
            📏 Khoảng cách: %s km
            💰 Phí ship: %s đ
            """,
            e.orderCode(), e.deliveryAddress(), e.distanceKm(), e.deliveryFee()
        );

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
            .keyboardRow(new InlineKeyboardRow(List.of(
                InlineKeyboardButton.builder()
                    .text("✅ Nhận")
                    .callbackData("ACCEPT_ORDER:" + e.assignmentId())
                    .build(),
                InlineKeyboardButton.builder()
                    .text("❌ Từ chối")
                    .callbackData("REJECT_ORDER:" + e.assignmentId())
                    .build()
            )))
            .build();

        SendMessage msg = SendMessage.builder()
            .chatId(e.shipperId())
            .text(text)
            .replyMarkup(keyboard)
            .build();

        bot.execute(msg);
        log.info("Pushed offer to shipper {} for order {}", e.shipperId(), e.orderCode());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAccepted(OrderAcceptedEvent e) {
        bot.sendText(e.shipperId(), "✅ Bạn đã nhận đơn " + e.orderCode() + ". Mở Mini App để bắt đầu giao.");
        bot.sendText(e.customerId(), "🚴 Shipper đã nhận đơn " + e.orderCode() + ". Đơn sắp được giao.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderRejected(OrderRejectedEvent e) {
        bot.sendText(e.shipperId(), "❌ Bạn đã từ chối đơn " + e.orderCode() + ".");
        // Don't notify customer of internal reassignment
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStarted(OrderStartedEvent e) {
        bot.sendText(e.customerId(), "🚀 Đơn " + e.orderCode() + " đang được giao. Shipper đang trên đường tới bạn.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderDelivered(OrderDeliveredEvent e) {
        bot.sendText(e.customerId(), "✅ Đơn " + e.orderCode() + " đã giao xong. Cảm ơn bạn đã mua hàng!");
        bot.sendText(e.shipperId(), "✅ Hoàn thành đơn " + e.orderCode() + ". Chúc bạn ngày làm việc tốt lành!");
    }
}
```

**Note:** `InlineKeyboardRow` may need import — telegrambots 6.x uses `List<List<InlineKeyboardButton>>` directly. Let me check what's compatible. Actually telegrambots 6.9.x InlineKeyboardMarkup.builder uses `keyboardRow(List<InlineKeyboardButton>)`. The `InlineKeyboardRow` class is from newer versions. Adjust:

```java
import java.util.List;
// ...
InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
    .keyboardRow(List.of(
        InlineKeyboardButton.builder()
            .text("✅ Nhận")
            .callbackData("ACCEPT_ORDER:" + e.assignmentId())
            .build(),
        InlineKeyboardButton.builder()
            .text("❌ Từ chối")
            .callbackData("REJECT_ORDER:" + e.assignmentId())
            .build()
    ))
    .build();
```

Use this form. If 6.9.7.1 still requires `InlineKeyboardRow` wrapper class, switch to:
```java
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
// keyboardRow(new InlineKeyboardRow(List.of(...)))
```

Verify by compiling. If both fail, fall back to building keyboard manually:
```java
List<List<InlineKeyboardButton>> rows = List.of(List.of(button1, button2));
InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
keyboard.setKeyboard(rows);
```

- [ ] **Step 3: Write bot callback handler**

Create `backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/shipper/OrderOfferCallbackHandler.java`:

```java
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

/**
 * Handles ACCEPT_ORDER and REJECT_ORDER callback queries from shipper offer messages.
 */
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
                sender.execute(new AnswerCallbackQuery(cb.getId(), "Đã nhận đơn", false, null, 0));
                // OrderAcceptedNotifier will send the followup text message
            } else {
                service.reject(assignmentId, shipperId);
                sender.execute(new AnswerCallbackQuery(cb.getId(), "Đã từ chối", false, null, 0));
            }
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid assignmentId in callback: {}", idPart);
            sender.execute(new AnswerCallbackQuery(cb.getId(), "Lỗi: ID không hợp lệ", true, null, 0));
        } catch (DomainException ex) {
            log.warn("Callback domain error: {}", ex.getMessage());
            sender.execute(new AnswerCallbackQuery(cb.getId(), ex.getMessage(), true, null, 0));
            sender.sendText(chatId, "❌ " + ex.getMessage());
        }
    }
}
```

**Note about `AnswerCallbackQuery`**: in telegrambots 6.9.x, the constructor signature varies. Use builder if available:

```java
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
// ...
sender.execute(AnswerCallbackQuery.builder()
    .callbackQueryId(cb.getId())
    .text("Đã nhận đơn")
    .showAlert(false)
    .build());
```

Use the builder form. Adjust constructor calls if needed.

- [ ] **Step 4: Update `BotSender` if needed**

`BotSender.execute()` is generic via `<T, M extends BotApiMethod<T>>`. Should accept `AnswerCallbackQuery`. Verify the method returns Boolean (AnswerCallbackQuery extends BotApiMethod<Boolean>).

- [ ] **Step 5: Verify compile**

```bash
cd backend && ./mvnw -pl modules/notification -am compile
cd backend && ./mvnw -pl modules/bot -am compile
```

Both should succeed.

- [ ] **Step 6: Commit**

```bash
git add backend/modules/notification/ \
        backend/modules/bot/src/main/java/com/shop/delivery/bot/handler/shipper/OrderOfferCallbackHandler.java
git commit -m "feat(delivery): wire OrderAssigned notifier + bot accept/reject callbacks"
```

---

## TASK 9: Shared types for shipper + assignment (1 commit)

**Files:**
- Create: `frontend/shared/src/types/shipper.ts`
- Create: `frontend/shared/src/types/assignment.ts`
- Update: `frontend/shared/src/types/index.ts`
- Create: `frontend/shared/src/api/shippers.ts` + `assignments.ts`
- Update: `frontend/shared/src/api/index.ts`

- [ ] **Step 1: Create `frontend/shared/src/types/shipper.ts`**

```typescript
export type ShipperState = 'AVAILABLE' | 'BUSY' | 'OFFLINE';
export type VehicleType = 'MOTORBIKE' | 'CAR' | 'BICYCLE';

export interface ShipperResponse {
  userId: number;
  firstName: string | null;
  lastName: string | null;
  username: string | null;
  vehicleType: VehicleType;
  licensePlate: string | null;
  currentState: ShipperState;
  ratingAvg: number;
  ratingCount: number;
  totalDeliveries: number;
}

export interface CreateShipperRequest {
  telegramUserId: number;
  vehicleType: VehicleType;
  licensePlate?: string;
}
```

- [ ] **Step 2: Create `frontend/shared/src/types/assignment.ts`**

```typescript
export type AssignmentStatus = 'OFFERED' | 'ACCEPTED' | 'REJECTED' | 'STARTED' | 'COMPLETED' | 'CANCELLED';

export interface AssignmentResponse {
  id: string;
  orderId: string;
  orderCode: string;
  customerId: number;
  customerName: string | null;
  customerPhone: string | null;
  deliveryAddress: string;
  deliveryLat: string;
  deliveryLng: string;
  distanceKm: string;
  deliveryFee: number;
  total: number;
  status: AssignmentStatus;
  orderStatus: string;
  assignedAt: string;
  acceptedAt: string | null;
  startedAt: string | null;
  deliveredAt: string | null;
}
```

- [ ] **Step 3: Update `frontend/shared/src/types/index.ts`**

Append:
```typescript
export * from './shipper';
export * from './assignment';
```

- [ ] **Step 4: Create `frontend/shared/src/api/shippers.ts`**

```typescript
import type { AxiosInstance } from 'axios';
import type { ShipperResponse, CreateShipperRequest } from '../types';

export async function listShippers(client: AxiosInstance): Promise<ShipperResponse[]> {
  const { data } = await client.get<ShipperResponse[]>('/api/admin/shippers');
  return data;
}

export async function createShipper(client: AxiosInstance, req: CreateShipperRequest): Promise<ShipperResponse> {
  const { data } = await client.post<ShipperResponse>('/api/admin/shippers', req);
  return data;
}

export async function assignShipper(client: AxiosInstance, orderId: string, shipperId: number): Promise<unknown> {
  const { data } = await client.post(`/api/admin/orders/${orderId}/assign`, { shipperId });
  return data;
}
```

- [ ] **Step 5: Create `frontend/shared/src/api/assignments.ts`**

```typescript
import type { AxiosInstance } from 'axios';
import type { AssignmentResponse } from '../types';

export async function listMyAssignments(client: AxiosInstance): Promise<AssignmentResponse[]> {
  const { data } = await client.get<AssignmentResponse[]>('/api/shipper/assignments');
  return data;
}

export async function acceptAssignment(client: AxiosInstance, id: string): Promise<AssignmentResponse> {
  const { data } = await client.post<AssignmentResponse>(`/api/shipper/assignments/${id}/accept`);
  return data;
}

export async function rejectAssignment(client: AxiosInstance, id: string): Promise<void> {
  await client.post(`/api/shipper/assignments/${id}/reject`);
}

export async function startAssignment(client: AxiosInstance, id: string): Promise<AssignmentResponse> {
  const { data } = await client.post<AssignmentResponse>(`/api/shipper/assignments/${id}/start`);
  return data;
}

export async function completeAssignment(client: AxiosInstance, id: string): Promise<AssignmentResponse> {
  const { data } = await client.post<AssignmentResponse>(`/api/shipper/assignments/${id}/complete`);
  return data;
}
```

- [ ] **Step 6: Update `frontend/shared/src/api/index.ts`**

Append:
```typescript
export * from './shippers';
export * from './assignments';
```

- [ ] **Step 7: Type-check**

```bash
cd frontend && pnpm type-check
```

- [ ] **Step 8: Commit**

```bash
git add frontend/shared/src/
git commit -m "feat(shared): add shipper + assignment types and API helpers"
```

---

## TASK 10: Web Admin — Shippers page + Assign modal (1 commit)

**Files:**
- Create: `frontend/webadmin/src/pages/ShippersPage.tsx`
- Create: `frontend/webadmin/src/components/AssignShipperModal.tsx`
- Modify: `frontend/webadmin/src/pages/OrderDetailPage.tsx` — add assign button + modal
- Modify: `frontend/webadmin/src/App.tsx` — add shippers route
- Modify: `frontend/webadmin/src/components/Sidebar.tsx` — add shippers nav item

- [ ] **Step 1: Create `ShippersPage.tsx`**

Create `frontend/webadmin/src/pages/ShippersPage.tsx`:

```typescript
import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listShippers, createShipper, type CreateShipperRequest, type VehicleType } from '@shop/shared';
import { api } from '@/lib/api';

export function ShippersPage() {
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [telegramUserId, setTelegramUserId] = useState('');
  const [vehicleType, setVehicleType] = useState<VehicleType>('MOTORBIKE');
  const [licensePlate, setLicensePlate] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: shippers, isLoading } = useQuery({
    queryKey: ['admin', 'shippers'],
    queryFn: () => listShippers(api),
  });

  const createMut = useMutation({
    mutationFn: (req: CreateShipperRequest) => createShipper(api, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'shippers'] });
      setShowForm(false);
      setTelegramUserId('');
      setVehicleType('MOTORBIKE');
      setLicensePlate('');
      setError(null);
    },
    onError: (err: any) => {
      setError(err.response?.data?.message ?? 'Tạo shipper thất bại');
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    createMut.mutate({
      telegramUserId: Number(telegramUserId),
      vehicleType,
      licensePlate: licensePlate || undefined,
    });
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Shipper</h1>
        <button
          onClick={() => setShowForm(s => !s)}
          className="px-4 py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700"
        >
          {showForm ? 'Đóng' : '+ Thêm shipper'}
        </button>
      </div>

      {showForm && (
        <form onSubmit={onSubmit} className="bg-white rounded-lg shadow p-4 mb-6 max-w-md">
          <h2 className="font-semibold mb-3">Thêm shipper</h2>
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-md p-3 mb-3">
              {error}
            </div>
          )}
          <label className="block mb-3">
            <span className="text-sm text-gray-700">Telegram User ID *</span>
            <input
              type="number"
              required
              value={telegramUserId}
              onChange={e => setTelegramUserId(e.target.value)}
              placeholder="vd: 1951735745"
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
            />
            <p className="text-xs text-gray-500 mt-1">User phải /start bot trước để có entry trong telegram_user</p>
          </label>
          <label className="block mb-3">
            <span className="text-sm text-gray-700">Loại xe *</span>
            <select
              value={vehicleType}
              onChange={e => setVehicleType(e.target.value as VehicleType)}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
            >
              <option value="MOTORBIKE">Xe máy</option>
              <option value="CAR">Ô tô</option>
              <option value="BICYCLE">Xe đạp</option>
            </select>
          </label>
          <label className="block mb-3">
            <span className="text-sm text-gray-700">Biển số</span>
            <input
              type="text"
              value={licensePlate}
              onChange={e => setLicensePlate(e.target.value)}
              placeholder="29A-12345"
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
            />
          </label>
          <button
            type="submit"
            disabled={createMut.isPending}
            className="w-full py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50"
          >
            {createMut.isPending ? 'Đang tạo...' : 'Tạo'}
          </button>
        </form>
      )}

      {isLoading && <p className="text-gray-600">Đang tải...</p>}

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="px-4 py-2 text-left">Tên</th>
              <th className="px-4 py-2 text-left">Telegram ID</th>
              <th className="px-4 py-2 text-left">Xe</th>
              <th className="px-4 py-2 text-left">Biển số</th>
              <th className="px-4 py-2 text-left">Trạng thái</th>
              <th className="px-4 py-2 text-right">Đơn đã giao</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {shippers?.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">Chưa có shipper</td></tr>
            )}
            {shippers?.map(s => (
              <tr key={s.userId} className="hover:bg-gray-50">
                <td className="px-4 py-3">{[s.firstName, s.lastName].filter(Boolean).join(' ') || '-'}</td>
                <td className="px-4 py-3 text-gray-600">{s.userId}</td>
                <td className="px-4 py-3">{s.vehicleType}</td>
                <td className="px-4 py-3">{s.licensePlate ?? '-'}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded-full text-xs ${
                    s.currentState === 'AVAILABLE' ? 'bg-green-100 text-green-800' :
                    s.currentState === 'BUSY' ? 'bg-yellow-100 text-yellow-800' :
                    'bg-gray-200 text-gray-700'
                  }`}>
                    {s.currentState}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">{s.totalDeliveries}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create `AssignShipperModal.tsx`**

Create `frontend/webadmin/src/components/AssignShipperModal.tsx`:

```typescript
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listShippers, assignShipper } from '@shop/shared';
import { api } from '@/lib/api';

interface Props {
  orderId: string;
  orderCode: string;
  onClose: () => void;
}

export function AssignShipperModal({ orderId, orderCode, onClose }: Props) {
  const qc = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: shippers, isLoading } = useQuery({
    queryKey: ['admin', 'shippers'],
    queryFn: () => listShippers(api),
  });

  const available = shippers?.filter(s => s.currentState === 'AVAILABLE') ?? [];

  const assignMut = useMutation({
    mutationFn: (shipperId: number) => assignShipper(api, orderId, shipperId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'order', orderId] });
      qc.invalidateQueries({ queryKey: ['admin', 'orders', 'list'] });
      qc.invalidateQueries({ queryKey: ['admin', 'shippers'] });
      onClose();
    },
    onError: (err: any) => {
      setError(err.response?.data?.message ?? 'Gán shipper thất bại');
    },
  });

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
        <h2 className="text-xl font-bold mb-1">Gán shipper</h2>
        <p className="text-sm text-gray-600 mb-4">Đơn {orderCode}</p>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-md p-3 mb-4">
            {error}
          </div>
        )}

        {isLoading && <p className="text-gray-600">Đang tải...</p>}

        {!isLoading && available.length === 0 && (
          <div className="bg-yellow-50 border border-yellow-200 text-yellow-800 text-sm rounded-md p-3 mb-4">
            Không có shipper nào đang AVAILABLE. Yêu cầu shipper bật trạng thái sẵn sàng.
          </div>
        )}

        <div className="space-y-2 mb-4 max-h-80 overflow-y-auto">
          {available.map(s => (
            <label
              key={s.userId}
              className={`block p-3 rounded-md border cursor-pointer ${
                selectedId === s.userId
                  ? 'border-brand-600 bg-brand-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <input
                type="radio"
                name="shipper"
                value={s.userId}
                checked={selectedId === s.userId}
                onChange={() => setSelectedId(s.userId)}
                className="hidden"
              />
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium">{[s.firstName, s.lastName].filter(Boolean).join(' ') || `Shipper #${s.userId}`}</p>
                  <p className="text-xs text-gray-500">{s.vehicleType} {s.licensePlate ?? ''}</p>
                </div>
                <div className="text-right text-xs text-gray-500">
                  {s.totalDeliveries} đơn
                </div>
              </div>
            </label>
          ))}
        </div>

        <div className="flex gap-2">
          <button
            onClick={() => selectedId !== null && assignMut.mutate(selectedId)}
            disabled={selectedId === null || assignMut.isPending}
            className="flex-1 py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50"
          >
            {assignMut.isPending ? 'Đang gán...' : 'Gán shipper'}
          </button>
          <button
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
          >
            Hủy
          </button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Update `OrderDetailPage.tsx`** — add Assign button when CONFIRMED

Read existing file. Add the "Gán shipper" button:

In the actions section (where Confirm/Cancel are), add:
- import `useState` from react, `AssignShipperModal` from components
- add `const [showAssign, setShowAssign] = useState(false);` to component
- replace the placeholder "Assign shipper sẽ ở P5" line with:
  ```jsx
  {order.status === 'CONFIRMED' && (
    <button
      onClick={() => setShowAssign(true)}
      className="w-full py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700"
    >
      Gán shipper
    </button>
  )}
  {showAssign && (
    <AssignShipperModal
      orderId={order.id}
      orderCode={order.code}
      onClose={() => setShowAssign(false)}
    />
  )}
  ```

The whole actions section becomes (when canConfirm/canCancel/canAssign):
```jsx
{(canConfirm || canCancel || order.status === 'CONFIRMED') && (
  <div className="bg-white rounded-lg shadow p-4 space-y-2">
    {canConfirm && (
      <button onClick={() => confirmMut.mutate()} disabled={confirmMut.isPending}
        className="w-full py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50">
        {confirmMut.isPending ? 'Đang xác nhận...' : 'Xác nhận đơn'}
      </button>
    )}
    {order.status === 'CONFIRMED' && (
      <button onClick={() => setShowAssign(true)}
        className="w-full py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700">
        Gán shipper
      </button>
    )}
    {canCancel && (
      <button onClick={() => cancelMut.mutate()} disabled={cancelMut.isPending}
        className="w-full py-2 border border-red-500 text-red-500 rounded-md hover:bg-red-50 disabled:opacity-50">
        {cancelMut.isPending ? 'Đang hủy...' : 'Hủy đơn'}
      </button>
    )}
  </div>
)}
{showAssign && (
  <AssignShipperModal orderId={order.id} orderCode={order.code} onClose={() => setShowAssign(false)} />
)}
```

- [ ] **Step 4: Update `Sidebar.tsx`** — add Shippers nav item

Read existing. Add to ITEMS array:
```typescript
{ to: '/shippers', label: 'Shipper', icon: '🚴' },
```

- [ ] **Step 5: Update `App.tsx`** — add `/shippers` route

Read existing. Add import + route:
```typescript
import { ShippersPage } from './pages/ShippersPage';
// ...
<Route path="shippers" element={<ShippersPage />} />
```

- [ ] **Step 6: Build**

```bash
cd frontend/webadmin && pnpm build
```

- [ ] **Step 7: Commit**

```bash
git add frontend/webadmin/src/
git commit -m "feat(webadmin): add shippers page + assign shipper modal in order detail"
```

---

## TASK 11: Mini App — Shipper assignments page (1 commit)

**Files:**
- Create: `frontend/miniapp/src/pages/ShipperAssignmentsPage.tsx`
- Create: `frontend/miniapp/src/pages/ShipperAssignmentDetailPage.tsx`
- Modify: `frontend/miniapp/src/App.tsx` — add shipper routes
- Modify: `frontend/miniapp/src/pages/SplashPage.tsx` — verify shipper role redirect

- [ ] **Step 1: Create `ShipperAssignmentsPage.tsx`**

Create `frontend/miniapp/src/pages/ShipperAssignmentsPage.tsx`:

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  listMyAssignments, acceptAssignment, rejectAssignment,
  formatVnd, formatRelative, type AssignmentResponse
} from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';

export function ShipperAssignmentsPage() {
  const qc = useQueryClient();

  const { data: assignments, isLoading } = useQuery({
    queryKey: ['shipper', 'assignments'],
    queryFn: () => listMyAssignments(api),
  });

  const acceptMut = useMutation({
    mutationFn: (id: string) => acceptAssignment(api, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shipper'] }),
    onError: showError,
  });

  const rejectMut = useMutation({
    mutationFn: (id: string) => rejectAssignment(api, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shipper'] }),
    onError: showError,
  });

  async function showError(err: any) {
    const msg = err.response?.data?.message ?? 'Có lỗi xảy ra';
    if (tg.isInTelegram()) await tg.showAlert(msg);
    else alert(msg);
  }

  async function confirmAndReject(id: string) {
    const ok = tg.isInTelegram()
      ? await tg.showConfirm('Bạn có chắc muốn từ chối đơn này?')
      : confirm('Từ chối đơn này?');
    if (ok) rejectMut.mutate(id);
  }

  if (isLoading) return <p className="text-tg-hint">Đang tải...</p>;

  const active = assignments ?? [];

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Đơn của tôi</h1>

      {active.length === 0 && (
        <div className="text-center py-12">
          <p className="text-tg-hint mb-2">Chưa có đơn nào</p>
          <p className="text-xs text-tg-hint">Bật trạng thái AVAILABLE để nhận đơn từ shop</p>
        </div>
      )}

      <div className="space-y-3">
        {active.map(a => (
          <AssignmentCard
            key={a.id}
            a={a}
            onAccept={() => acceptMut.mutate(a.id)}
            onReject={() => confirmAndReject(a.id)}
          />
        ))}
      </div>
    </div>
  );
}

function AssignmentCard({ a, onAccept, onReject }: {
  a: AssignmentResponse;
  onAccept: () => void;
  onReject: () => void;
}) {
  return (
    <Link to={`/shipper/assignments/${a.id}`} className="block bg-tg-secondaryBg rounded-lg p-3">
      <div className="flex items-start justify-between mb-2">
        <div>
          <p className="font-bold">{a.orderCode}</p>
          <p className="text-xs text-tg-hint">{formatRelative(a.assignedAt)}</p>
        </div>
        <StatusBadge status={a.status} />
      </div>

      <p className="text-sm mb-1">📍 {a.deliveryAddress}</p>
      <p className="text-xs text-tg-hint">
        {a.distanceKm}km — phí ship: {formatVnd(a.deliveryFee)}
      </p>

      {a.status === 'OFFERED' && (
        <div className="flex gap-2 mt-3" onClick={e => e.preventDefault()}>
          <button
            onClick={onAccept}
            className="flex-1 py-2 bg-green-600 text-white rounded-md text-sm font-medium"
          >
            ✅ Nhận
          </button>
          <button
            onClick={onReject}
            className="flex-1 py-2 border border-red-500 text-red-500 rounded-md text-sm font-medium"
          >
            ❌ Từ chối
          </button>
        </div>
      )}
    </Link>
  );
}

function StatusBadge({ status }: { status: string }) {
  const labels: Record<string, { label: string; cls: string }> = {
    OFFERED:    { label: 'Mới',         cls: 'bg-yellow-100 text-yellow-800' },
    ACCEPTED:   { label: 'Đã nhận',     cls: 'bg-blue-100 text-blue-800' },
    STARTED:    { label: 'Đang giao',   cls: 'bg-purple-100 text-purple-800' },
    COMPLETED:  { label: 'Đã giao',     cls: 'bg-green-100 text-green-800' },
    REJECTED:   { label: 'Đã từ chối',  cls: 'bg-gray-100 text-gray-800' },
    CANCELLED:  { label: 'Hủy',         cls: 'bg-red-100 text-red-800' },
  };
  const info = labels[status] ?? { label: status, cls: 'bg-gray-100 text-gray-800' };
  return <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${info.cls}`}>{info.label}</span>;
}
```

- [ ] **Step 2: Create `ShipperAssignmentDetailPage.tsx`**

Create `frontend/miniapp/src/pages/ShipperAssignmentDetailPage.tsx`:

```typescript
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listMyAssignments, startAssignment, completeAssignment,
  formatVnd, formatDateTime, type AssignmentResponse
} from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';

export function ShipperAssignmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();

  // Reuse listing query; pick from cache
  const { data: list } = useQuery({
    queryKey: ['shipper', 'assignments'],
    queryFn: () => listMyAssignments(api),
  });

  const assignment: AssignmentResponse | undefined = list?.find(a => a.id === id);

  const startMut = useMutation({
    mutationFn: () => startAssignment(api, id!),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shipper'] }),
    onError: showError,
  });

  const completeMut = useMutation({
    mutationFn: () => completeAssignment(api, id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['shipper'] });
      navigate('/shipper/assignments', { replace: true });
    },
    onError: showError,
  });

  async function showError(err: any) {
    const msg = err.response?.data?.message ?? 'Có lỗi xảy ra';
    if (tg.isInTelegram()) await tg.showAlert(msg);
    else alert(msg);
  }

  if (!assignment) {
    return (
      <div>
        <Link to="/shipper/assignments" className="text-tg-link">← Về danh sách</Link>
        <p className="mt-4 text-tg-hint">Không tìm thấy đơn.</p>
      </div>
    );
  }

  return (
    <div>
      <button onClick={() => navigate(-1)} className="mb-4 text-tg-link">← Quay lại</button>

      <h1 className="text-2xl font-bold mb-1">{assignment.orderCode}</h1>
      <p className="text-xs text-tg-hint mb-4">{formatDateTime(assignment.assignedAt)}</p>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4">
        <h2 className="font-semibold mb-2">Khách hàng</h2>
        <p className="text-sm">{assignment.customerName ?? '(chưa có tên)'}</p>
        {assignment.customerPhone && (
          <a href={`tel:${assignment.customerPhone}`} className="text-sm text-tg-link block mt-1">
            📞 {assignment.customerPhone}
          </a>
        )}
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4">
        <h2 className="font-semibold mb-2">Địa chỉ giao</h2>
        <p className="text-sm">{assignment.deliveryAddress}</p>
        <p className="text-xs text-tg-hint mt-2">
          Khoảng cách: {assignment.distanceKm}km
        </p>
        <p className="text-xs text-tg-hint">
          📍 {assignment.deliveryLat}, {assignment.deliveryLng} (P6 sẽ có map)
        </p>
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4 text-sm">
        <div className="flex justify-between">
          <span className="text-tg-hint">Phí ship</span>
          <span className="font-bold">{formatVnd(assignment.deliveryFee)}</span>
        </div>
        <div className="flex justify-between mt-1">
          <span className="text-tg-hint">Tổng đơn</span>
          <span>{formatVnd(assignment.total)}</span>
        </div>
      </div>

      {assignment.status === 'ACCEPTED' && (
        <button
          onClick={() => startMut.mutate()}
          disabled={startMut.isPending}
          className="w-full py-3 bg-tg-button text-tg-buttonText rounded-lg font-medium disabled:opacity-50"
        >
          {startMut.isPending ? 'Đang xử lý...' : '🚀 Bắt đầu giao'}
        </button>
      )}

      {assignment.status === 'STARTED' && (
        <button
          onClick={() => completeMut.mutate()}
          disabled={completeMut.isPending}
          className="w-full py-3 bg-green-600 text-white rounded-lg font-medium disabled:opacity-50"
        >
          {completeMut.isPending ? 'Đang xử lý...' : '✅ Đã giao'}
        </button>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Update Mini App `App.tsx`** — add shipper routes

Read existing. Add imports + routes inside the Layout-wrapped section:

```typescript
import { ShipperAssignmentsPage } from './pages/ShipperAssignmentsPage';
import { ShipperAssignmentDetailPage } from './pages/ShipperAssignmentDetailPage';
// ...
<Route path="shipper/assignments" element={<ShipperAssignmentsPage />} />
<Route path="shipper/assignments/:id" element={<ShipperAssignmentDetailPage />} />
```

- [ ] **Step 4: Verify `SplashPage.tsx` redirects shipper correctly**

Read existing. It already has:
```typescript
if (data.roles.includes('SHIPPER')) {
  navigate('/shipper/assignments', { replace: true });
}
```
This should work as-is.

- [ ] **Step 5: Build**

```bash
cd frontend/miniapp && pnpm build
```

- [ ] **Step 6: Commit**

```bash
git add frontend/miniapp/src/
git commit -m "feat(miniapp): add shipper assignments list + detail with accept/start/complete"
```

---

## TASK 12: Verify full backend + frontend (1 commit, may be empty)

This task ensures everything still works together. No new code — just verify.

- [ ] **Step 1: Run full backend verify**

```bash
cd backend && ./mvnw verify
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Run full frontend type-check**

```bash
cd frontend && pnpm -r type-check
```

- [ ] **Step 3: Build all frontend**

```bash
cd frontend && pnpm -r build  # or pnpm miniapp:build && pnpm webadmin:build
```

- [ ] **Step 4: Optional smoke test (manual)**

Setup:
1. Backend running (port 8080)
2. Mini App dev server (port 5173)
3. Web Admin dev server (port 5174)
4. ngrok for Mini App (HTTPS to interact via Telegram)

Test flow:
1. **Setup admin** (one-time, already from V5 seed): admin@shop.local / admin123
2. **Customer (Mini App)**: Login → Catalog → Cart → Checkout COD → Order created (PENDING)
3. **Admin (Web Admin)**: Login → Orders → click PENDING order → Confirm → status CONFIRMED
4. **Admin**: Click "Gán shipper" — modal opens. **Need shipper first!**
5. **Create shipper**:
   - Need a Telegram user with /start done. Use a 2nd Telegram account or seed manually:
     ```bash
     docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c \
       "INSERT INTO telegram_user(id, first_name, language_code) VALUES (8888, 'Test Shipper', 'vi') ON CONFLICT DO NOTHING;"
     ```
   - Admin → "Shipper" tab → "+ Thêm shipper" → Telegram User ID: 8888, MOTORBIKE → Tạo
   - Then SQL to make shipper AVAILABLE (no UI to toggle in P5):
     ```bash
     docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c \
       "UPDATE shipper_profile SET current_state = 'AVAILABLE' WHERE user_id = 8888;"
     ```
6. **Admin**: Order detail → "Gán shipper" → select Test Shipper → assign
   - Backend: order moves to ASSIGNED, assignment OFFERED
   - Bot tries to push offer to shipper (will fail because user 8888 isn't real Telegram — but in real setup with 2 phones, this would work)
7. **Shipper (Mini App, real)**: Login → routes to /shipper/assignments → sees offered assignment → "✅ Nhận" → status ACCEPTED
8. **Shipper detail**: → "🚀 Bắt đầu giao" → status STARTED, order DELIVERING
9. **Shipper detail**: → "✅ Đã giao" → status COMPLETED, order DELIVERED
10. **Customer**: Reload orders list → see status DELIVERED

- [ ] **Step 5: No commit needed (just verification)**

This task documents verification but creates no files. If any fix is needed, commit it; otherwise skip.

## Acceptance Criteria (P5 done when ALL true)

- [x] V6 migration applied (shipper_profile, delivery_assignment)
- [x] `./mvnw verify` BUILD SUCCESS (~110+ tests)
- [x] `pnpm -r type-check` clean across shared, miniapp, webadmin
- [x] Admin can create shipper via Web Admin
- [x] Admin can assign shipper to CONFIRMED order
- [x] Order transitions: PENDING → CONFIRMED → ASSIGNED → DELIVERING → DELIVERED
- [x] Assignment transitions: OFFERED → ACCEPTED → STARTED → COMPLETED
- [x] OrderRejected resets order back to CONFIRMED, deletes assignment
- [x] status_history records all transitions with correct actor user ID
- [x] Bot push offer message with [✅ Nhận] [❌ Từ chối] inline buttons
- [x] Bot callback handlers process Accept/Reject correctly
- [x] No regressions: P0-P4 tests still pass
- [x] Smoke test passes manually with real Telegram (if 2 accounts available)

---

## Known Limitations of P5 (giải quyết ở plan sau)

| Limitation | Plan |
|---|---|
| No shipper self-registration FSM via bot | Optional later — admin creates manually |
| No "Broadcast" assign (send to all AVAILABLE) | Optional later — UI prep done, backend skips |
| No Live Location during DELIVERING | P6 |
| No realtime updates (must refresh) | P6 (WebSocket) |
| Shipper state toggle (AVAILABLE/OFFLINE) only via SQL | Add Mini App toggle button later |
| Order detail in Mini App shows `SP #productId` (not name) | P9 polish |
| Notification doesn't handle bot 403 (user blocked bot) | Logged but no retry — P9 |
| Admin shippers page doesn't show block/unblock | Optional later |
| Refresh token still O(n) scan | P9 optimization |

---

## Troubleshooting

**`SHIPPER_NOT_AVAILABLE` when assigning:** Shipper.currentState must be AVAILABLE. After creation it's OFFLINE. Use SQL: `UPDATE shipper_profile SET current_state = 'AVAILABLE' WHERE user_id = ?`. Future P-task could add toggle UI.

**`ORDER_NOT_ASSIGNABLE` (status PENDING/etc):** Only CONFIRMED orders can be assigned. Confirm first.

**Bot doesn't push offer to shipper:** Check:
- Notifier listener is being invoked (logs)
- `BotSender.execute` doesn't throw (user must have started bot and not blocked it)
- shipper_id == telegram_user.id (valid Telegram user)

**Bot callback `ACCEPT_ORDER:...` returns "Đã có lỗi":** Check assignment exists, status is OFFERED, shipper_id matches the user clicking. Logs should show specific DomainException.

**Mini App shipper page shows "Không phải shipper":** Check user_role table has (telegramUserId, SHIPPER, ACTIVE). Admin's "Thêm shipper" creates it automatically. Caffeine cache may need eviction — `RoleResolver.evict(userId)` is called by ShipperProfileService.createShipper, should be fresh.

**Order goes to ASSIGNED but bot doesn't send offer:** Check that `notification` module is on classpath (added bot dep in P5.TASK 8). Check that `@TransactionalEventListener` runs (event publish happens AFTER_COMMIT, so DB write must finish first).

---

**END OF P5 PLAN**
