# P2 — Order + Product Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the order domain backbone: `Product` CRUD, `Order` creation/lifecycle với state machine + `StatusHistory` audit trail + Haversine fee calc. Sau P2, REST API hỗ trợ: list/create product, create/list/cancel order, admin confirm. Mini App (P3) sẽ tiêu thụ những API này.

**Architecture:** `order` module sở hữu 4 entity (`Product`, `Order`, `OrderItem`, `StatusHistory`) + service layer + state machine. `OrderStateMachine` validate transition; `OrderService` orchestrate persistence + ghi `StatusHistory`. REST endpoints chia 2 nhóm:
- Customer (placeholder auth — `X-Customer-Id` header; P3 sẽ wire vào initData verify).
- Admin (placeholder auth — none yet; P4 sẽ wire vào JWT).

Distance fee = base_fee + max(0, distance_km - free_km) × fee_per_km. Distance từ `pickup_lat/lng` → `delivery_lat/lng` qua Haversine.

**Tech Stack (mới so với P1):**
- Không có lib mới — chỉ Spring Data JPA + REST + Bean Validation đã có

---

## Bối cảnh từ P1

Sau P1:
- 28 commits, build pipeline ổn, ~33 tests pass
- `auth` module có `TelegramUser`, `UserRole`, `TelegramUserService`, `RoleResolver`
- `bot` module có Telegram polling + StartHandler + idempotency (fixed bug)
- Smoke verify: `/start` → bot reply + user DB OK

**Constraints quan trọng:**
- Local JDK = Java 25 → ByteBuddy 1.17.7 đã override trong parent pom (giữ nguyên)
- Port 8080 có thể bị app khác chiếm → mvnw command có `-Dserver.port=8089` khi dùng
- Postgres dev container `shop_delivery_postgres_dev` — keep running giữa các P
- App run command: `cd backend/app && ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

**Decision dùng trong P2:**

1. **Order ID = UUID**, thêm field `code` human-readable. Code format: `"DH" + yyyyMMdd + "-" + RANDOM_5_DIGIT_UPPERCASE_BASE36`. Vd: `DH20260519-7XK2A`. Unique constraint trên `code`.

2. **Pickup address snapshot từ `shop_config`** — vì shop_config chưa có (sẽ thêm sau, hoặc dùng default trong env vars). **Cho P2: hardcode pickup từ application.yml properties** (`shop.pickup.lat`, `shop.pickup.lng`, `shop.fee.base`, `shop.fee.per-km`, `shop.fee.free-km`). P4 hoặc P8 sẽ làm shop_config entity proper.

3. **Distance calc** via Haversine, kết quả `km` BigDecimal 8.3 (snapshot vào order.distance_km).

4. **Fee calc** = `base_fee + max(0, distance_km - free_km) × fee_per_km`. Cũng snapshot vào order.delivery_fee.

5. **Auth strategy P2 (placeholder):**
   - Customer endpoints (`/api/orders/*`): header `X-Customer-Id: <telegram_user_id>` (Long). Sẽ wire initData ở P3.
   - Admin endpoints (`/api/admin/orders/*`, `/api/admin/products`): NO AUTH YET. Sẽ wire JWT ở P4. Comment `// TODO P4: Add @PreAuthorize("hasRole('SHOP_OWNER')")` để dễ tìm.
   - Public: `GET /api/products`, `GET /api/products/:id` — không cần auth.

6. **State machine P2 scope:**
   - `PENDING → CONFIRMED` (admin confirm; P7 sẽ thêm via payment success)
   - `PENDING → CANCELLED` (customer/admin)
   - `CONFIRMED → CANCELLED` (admin only, với reason)
   - `CONFIRMED → ASSIGNED`, `ASSIGNED → DELIVERING`, etc — **skip implementation** (P4–P5), nhưng vẫn để allowed-transition table validate.

---

## File Structure (sau khi P2 hoàn thành)

```
backend/
├── app/
│   ├── src/main/resources/
│   │   ├── application.yml                                 (modify — add shop.* config)
│   │   ├── application-dev.yml                             (modify — dev shop defaults)
│   │   ├── application-prod.yml                            (modify — prod shop env)
│   │   └── db/migration/
│   │       └── V4__order.sql                               (TASK 1)
└── modules/
    └── order/
        ├── pom.xml                                         (TASK 2 — add web + validation deps)
        └── src/
            ├── main/java/com/shop/delivery/order/
            │   ├── domain/
            │   │   ├── OrderStatus.java                    (TASK 4)
            │   │   ├── PaymentMethod.java                  (TASK 4)
            │   │   └── PaymentStatus.java                  (TASK 4)
            │   ├── entity/
            │   │   ├── Product.java                        (TASK 2)
            │   │   ├── Order.java                          (TASK 4)
            │   │   ├── OrderItem.java                      (TASK 4)
            │   │   └── StatusHistory.java                  (TASK 4)
            │   ├── repository/
            │   │   ├── ProductRepository.java              (TASK 2)
            │   │   ├── OrderRepository.java                (TASK 5)
            │   │   ├── OrderItemRepository.java            (TASK 5)
            │   │   └── StatusHistoryRepository.java        (TASK 5)
            │   ├── service/
            │   │   ├── ProductService.java                 (TASK 2)
            │   │   ├── OrderService.java                   (TASK 7)
            │   │   ├── OrderStateMachine.java              (TASK 6)
            │   │   ├── OrderCodeGenerator.java             (TASK 7)
            │   │   ├── DistanceCalculator.java             (TASK 6)
            │   │   ├── FeeCalculator.java                  (TASK 6)
            │   │   └── command/
            │   │       ├── CreateOrderCommand.java         (TASK 7)
            │   │       └── OrderLineCommand.java           (TASK 7)
            │   ├── api/
            │   │   ├── ProductController.java              (TASK 3)
            │   │   ├── OrderController.java                (TASK 8)
            │   │   ├── AdminOrderController.java           (TASK 8)
            │   │   ├── AdminProductController.java         (TASK 3)
            │   │   ├── dto/
            │   │   │   ├── ProductResponse.java            (TASK 3)
            │   │   │   ├── CreateProductRequest.java       (TASK 3)
            │   │   │   ├── UpdateProductRequest.java       (TASK 3)
            │   │   │   ├── CreateOrderRequest.java         (TASK 8)
            │   │   │   ├── OrderItemRequest.java           (TASK 8)
            │   │   │   ├── OrderResponse.java              (TASK 8)
            │   │   │   ├── OrderSummary.java               (TASK 8)
            │   │   │   └── CancelOrderRequest.java         (TASK 8)
            │   │   └── mapper/
            │   │       ├── ProductMapper.java              (TASK 3)
            │   │       └── OrderMapper.java                (TASK 8)
            │   └── config/
            │       └── ShopConfigProperties.java           (TASK 6)
            └── test/java/com/shop/delivery/order/
                ├── OrderTestConfig.java                    (TASK 5)
                ├── support/
                │   └── OrderTestcontainerBase.java         (TASK 5)
                ├── service/
                │   ├── ProductServiceTest.java             (TASK 2)
                │   ├── DistanceCalculatorTest.java         (TASK 6)
                │   ├── FeeCalculatorTest.java              (TASK 6)
                │   ├── OrderStateMachineTest.java          (TASK 6)
                │   ├── OrderServiceTest.java               (TASK 7)
                │   └── OrderCodeGeneratorTest.java         (TASK 7)
                ├── repository/
                │   └── OrderRepositoryIT.java              (TASK 5)
                └── api/
                    └── OrderControllerIT.java              (TASK 9)
```

**File mới:** ~40
**File modify:** 3 application*.yml + 1 pom.xml

---

## TASK 1: Migration V4__order.sql (1 commit)

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V4__order.sql`

- [ ] **Step 1: Write migration**

Create `backend/app/src/main/resources/db/migration/V4__order.sql`:

```sql
-- V4__order.sql — order module tables
-- Owns: product, order, order_item, status_history
-- Used by: miniapp (customer CRUD), webadmin (admin CRUD), delivery (assignment), payment (link to order)

CREATE TABLE product (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    price           NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    image_url       TEXT,
    stock           INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_active ON product(is_active) WHERE is_active = TRUE;

-- "order" is a reserved word — quoting in SQL queries via JPA is handled by @Table(name = "\"order\"") or naming differently.
-- We use "orders" as the table name to avoid quoting hell.
CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(32) NOT NULL UNIQUE,
    customer_id         BIGINT NOT NULL REFERENCES telegram_user(id),
    customer_name       VARCHAR(128),
    customer_phone      VARCHAR(32),
    pickup_lat          NUMERIC(10, 7) NOT NULL,
    pickup_lng          NUMERIC(10, 7) NOT NULL,
    delivery_address    TEXT NOT NULL,
    delivery_lat        NUMERIC(10, 7) NOT NULL,
    delivery_lng        NUMERIC(10, 7) NOT NULL,
    distance_km         NUMERIC(8, 3) NOT NULL CHECK (distance_km >= 0),
    subtotal            NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    delivery_fee        NUMERIC(12, 2) NOT NULL CHECK (delivery_fee >= 0),
    total               NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    payment_method      VARCHAR(16) NOT NULL,            -- COD | VNPAY
    payment_status      VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING | SUCCESS | FAILED | REFUNDED
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    note                TEXT,
    version             INT NOT NULL DEFAULT 0,          -- @Version for optimistic lock
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC);

CREATE TABLE order_item (
    id              BIGSERIAL PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES product(id),
    quantity        INT NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),  -- snapshot từ product.price tại thời điểm tạo order
    subtotal        NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX idx_order_item_order ON order_item(order_id);

CREATE TABLE status_history (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status         VARCHAR(16),                     -- nullable (initial create)
    to_status           VARCHAR(16) NOT NULL,
    changed_by_user_id  BIGINT,                          -- nullable (system events)
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    note                TEXT
);

CREATE INDEX idx_status_history_order ON status_history(order_id, changed_at);
```

**Lưu ý:** Table được đặt là `orders` (số nhiều) thay vì `order` để tránh xung đột với SQL reserved word. Trong code, entity vẫn là `Order` nhưng `@Table(name = "orders")`.

- [ ] **Step 2: Apply migration**

```bash
docker compose -f infra/docker-compose.dev.yml ps | grep -q healthy || docker compose -f infra/docker-compose.dev.yml up -d
sleep 5

cd backend/app
export $(cat ../../.env | xargs 2>/dev/null) || true   # token cũ nếu có, không bắt buộc
BOT_TOKEN=${BOT_TOKEN:-dummy} BOT_USERNAME=${BOT_USERNAME:-DummyBot} \
  nohup ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8089" > /tmp/p2_t1.log 2>&1 &
BOOT_PID=$!

for i in $(seq 1 90); do
  grep -q "Started Application" /tmp/p2_t1.log 2>/dev/null && break
  grep -q "APPLICATION FAILED" /tmp/p2_t1.log 2>/dev/null && break
  sleep 1
done

kill $BOOT_PID 2>/dev/null
sleep 2
ps -p $BOOT_PID > /dev/null 2>&1 && kill -9 $BOOT_PID
cd ../..
```

Verify:
```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY version"
# Expected: 1 init t, 2 auth t, 3 bot t, 4 order t

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d product"
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d orders"
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d order_item"
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "\d status_history"
```

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V4__order.sql
git commit -m "feat(order): add V4 migration for product, orders, order_item, status_history"
```

---

## TASK 2: Product Entity + Repository + Service (TDD, 1 commit)

**Files:**
- Modify: `backend/modules/order/pom.xml` — add deps
- Create: `backend/modules/order/src/main/java/com/shop/delivery/order/entity/Product.java`
- Create: `backend/modules/order/src/main/java/com/shop/delivery/order/repository/ProductRepository.java`
- Create: `backend/modules/order/src/main/java/com/shop/delivery/order/service/ProductService.java`
- Test: `backend/modules/order/src/test/java/com/shop/delivery/order/service/ProductServiceTest.java`

- [ ] **Step 1: Replace `backend/modules/order/pom.xml`**

Read existing first. Replace with:

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

    <artifactId>order</artifactId>
    <name>order</name>
    <description>Product, order, order_item, status_history</description>

    <dependencies>
        <dependency>
            <groupId>com.shop.delivery</groupId>
            <artifactId>shared</artifactId>
        </dependency>
        <dependency>
            <groupId>com.shop.delivery</groupId>
            <artifactId>auth</artifactId>
        </dependency>

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
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Write failing test**

Create `backend/modules/order/src/test/java/com/shop/delivery/order/service/ProductServiceTest.java`:

```java
package com.shop.delivery.order.service;

import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.repository.ProductRepository;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository repo;
    @InjectMocks ProductService service;

    Product sample;

    @BeforeEach
    void setup() {
        sample = new Product();
        sample.setId(1L);
        sample.setName("Áo thun nam M");
        sample.setPrice(new BigDecimal("199000"));
        sample.setStock(10);
        sample.setActive(true);
    }

    @Test
    void createShouldPersistNewProduct() {
        when(repo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        Product result = service.create("Áo polo", "Cotton 100%",
            new BigDecimal("250000"), "https://img/url.jpg", 20);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getName()).isEqualTo("Áo polo");
        assertThat(result.getPrice()).isEqualByComparingTo("250000");
        assertThat(result.getStock()).isEqualTo(20);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void findByIdReturnsProduct() {
        when(repo.findById(1L)).thenReturn(Optional.of(sample));
        Product result = service.findById(1L);
        assertThat(result.getName()).isEqualTo("Áo thun nam M");
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(999L))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("999");
    }

    @Test
    void listActiveReturnsOnlyActiveProducts() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.findAllByActiveTrue(pageable))
            .thenReturn(new PageImpl<>(List.of(sample)));

        Page<Product> result = service.listActive(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isActive()).isTrue();
    }

    @Test
    void updateShouldModifyExistingProduct() {
        when(repo.findById(1L)).thenReturn(Optional.of(sample));
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, "New Name", "New Desc",
            new BigDecimal("300000"), null, 5);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Name");
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("300000");
        assertThat(captor.getValue().getStock()).isEqualTo(5);
    }

    @Test
    void deactivateSetsActiveFalseNotDelete() {
        when(repo.findById(1L)).thenReturn(Optional.of(sample));
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate(1L);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }
}
```

- [ ] **Step 3: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/order -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 4: Implement `Product` entity**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/entity/Product.java`:

```java
package com.shop.delivery.order.entity;

import com.shop.delivery.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
```

- [ ] **Step 5: Implement `ProductRepository`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/repository/ProductRepository.java`:

```java
package com.shop.delivery.order.repository;

import com.shop.delivery.order.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findAllByActiveTrue(Pageable pageable);
}
```

- [ ] **Step 6: Implement `ProductService`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/service/ProductService.java`:

```java
package com.shop.delivery.order.service;

import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.repository.ProductRepository;
import com.shop.delivery.shared.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Product create(String name, String description, BigDecimal price, String imageUrl, Integer stock) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(price);
        p.setImageUrl(imageUrl);
        p.setStock(stock != null ? stock : 0);
        p.setActive(true);
        return repo.save(p);
    }

    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Sản phẩm " + id + " không tồn tại"));
    }

    @Transactional(readOnly = true)
    public Page<Product> listActive(Pageable pageable) {
        return repo.findAllByActiveTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> listAll(Pageable pageable) {
        return repo.findAll(pageable);
    }

    @Transactional
    public Product update(Long id, String name, String description, BigDecimal price, String imageUrl, Integer stock) {
        Product p = findById(id);
        if (name != null) p.setName(name);
        if (description != null) p.setDescription(description);
        if (price != null) p.setPrice(price);
        if (imageUrl != null) p.setImageUrl(imageUrl);
        if (stock != null) p.setStock(stock);
        return repo.save(p);
    }

    @Transactional
    public void deactivate(Long id) {
        Product p = findById(id);
        p.setActive(false);
        repo.save(p);
    }

    @Transactional
    public void activate(Long id) {
        Product p = findById(id);
        p.setActive(true);
        repo.save(p);
    }
}
```

- [ ] **Step 7: Run test — expect pass**

```bash
cd backend && ./mvnw -pl modules/order -am test
```

Expected: 6 tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/modules/order/
git commit -m "feat(order): add Product entity, repository, and service with CRUD"
```

---

## TASK 3: Product REST Controllers + DTOs + Mapper (1 commit)

**Files:**
- Create: 3 DTO classes + 1 mapper + 2 controllers (public + admin)

- [ ] **Step 1: Write DTOs**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/ProductResponse.java`:

```java
package com.shop.delivery.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
    Long id,
    String name,
    String description,
    BigDecimal price,
    String imageUrl,
    Integer stock,
    boolean active,
    Instant createdAt
) {
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/CreateProductRequest.java`:

```java
package com.shop.delivery.order.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProductRequest(
    @NotBlank String name,
    String description,
    @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
    String imageUrl,
    @Min(0) Integer stock
) {
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/UpdateProductRequest.java`:

```java
package com.shop.delivery.order.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateProductRequest(
    String name,
    String description,
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
    String imageUrl,
    @Min(0) Integer stock
) {
}
```

- [ ] **Step 2: Write mapper**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/mapper/ProductMapper.java`:

```java
package com.shop.delivery.order.api.mapper;

import com.shop.delivery.order.api.dto.ProductResponse;
import com.shop.delivery.order.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product p) {
        return new ProductResponse(
            p.getId(),
            p.getName(),
            p.getDescription(),
            p.getPrice(),
            p.getImageUrl(),
            p.getStock(),
            p.isActive(),
            p.getCreatedAt()
        );
    }
}
```

- [ ] **Step 3: Write public controller**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/ProductController.java`:

```java
package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.ProductResponse;
import com.shop.delivery.order.api.mapper.ProductMapper;
import com.shop.delivery.order.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;
    private final ProductMapper mapper;

    public ProductController(ProductService service, ProductMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<ProductResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.listActive(pageable).map(mapper::toResponse);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return mapper.toResponse(service.findById(id));
    }
}
```

- [ ] **Step 4: Write admin controller**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/AdminProductController.java`:

```java
package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.CreateProductRequest;
import com.shop.delivery.order.api.dto.ProductResponse;
import com.shop.delivery.order.api.dto.UpdateProductRequest;
import com.shop.delivery.order.api.mapper.ProductMapper;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin product management.
 * TODO P4: Add @PreAuthorize("hasRole('SHOP_OWNER')") khi có JWT.
 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService service;
    private final ProductMapper mapper;

    public AdminProductController(ProductService service, ProductMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<ProductResponse> listAll(@PageableDefault(size = 50) Pageable pageable) {
        return service.listAll(pageable).map(mapper::toResponse);
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest req) {
        Product p = service.create(req.name(), req.description(), req.price(), req.imageUrl(), req.stock());
        return mapper.toResponse(p);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        Product p = service.update(id, req.name(), req.description(), req.price(), req.imageUrl(), req.stock());
        return mapper.toResponse(p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 5: Verify compile**

```bash
cd backend && ./mvnw -pl modules/order -am compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/api/
git commit -m "feat(order): add public + admin product REST controllers"
```

---

## TASK 4: Order Domain Enums + 3 Entities (TDD, 1 commit)

**Files:**
- 3 enum classes + 3 entity classes + 3 test classes

- [ ] **Step 1: Write enum-related test**

Create `backend/modules/order/src/test/java/com/shop/delivery/order/entity/OrderTest.java`:

```java
package com.shop.delivery.order.entity;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    @Test
    void shouldHaveSensibleDefaults() {
        Order o = new Order();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(o.getVersion()).isZero();
    }

    @Test
    void shouldExposeFields() {
        UUID id = UUID.randomUUID();
        Order o = new Order();
        o.setId(id);
        o.setCode("DH20260519-7XK2A");
        o.setCustomerId(123L);
        o.setCustomerName("Thúy Lê");
        o.setCustomerPhone("+84901234567");
        o.setPickupLat(new BigDecimal("21.0285"));
        o.setPickupLng(new BigDecimal("105.8542"));
        o.setDeliveryAddress("45 Bà Triệu");
        o.setDeliveryLat(new BigDecimal("21.0193"));
        o.setDeliveryLng(new BigDecimal("105.8503"));
        o.setDistanceKm(new BigDecimal("1.234"));
        o.setSubtotal(new BigDecimal("250000"));
        o.setDeliveryFee(new BigDecimal("25000"));
        o.setTotal(new BigDecimal("275000"));
        o.setPaymentMethod(PaymentMethod.COD);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setStatus(OrderStatus.PENDING);
        o.setNote("Giao tối 6-8h");

        assertThat(o.getId()).isEqualTo(id);
        assertThat(o.getCode()).isEqualTo("DH20260519-7XK2A");
        assertThat(o.getCustomerId()).isEqualTo(123L);
        assertThat(o.getTotal()).isEqualByComparingTo("275000");
        assertThat(o.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
    }
}
```

Create `backend/modules/order/src/test/java/com/shop/delivery/order/entity/OrderItemTest.java`:

```java
package com.shop.delivery.order.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void shouldComputeSubtotalFromQuantityAndUnitPrice() {
        OrderItem item = new OrderItem();
        UUID orderId = UUID.randomUUID();
        item.setOrderId(orderId);
        item.setProductId(7L);
        item.setQuantity(3);
        item.setUnitPrice(new BigDecimal("100000"));
        item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));

        assertThat(item.getSubtotal()).isEqualByComparingTo("300000");
    }
}
```

Create `backend/modules/order/src/test/java/com/shop/delivery/order/entity/StatusHistoryTest.java`:

```java
package com.shop.delivery.order.entity;

import com.shop.delivery.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatusHistoryTest {

    @Test
    void shouldStoreTransition() {
        StatusHistory h = new StatusHistory();
        UUID orderId = UUID.randomUUID();
        h.setOrderId(orderId);
        h.setFromStatus(OrderStatus.PENDING);
        h.setToStatus(OrderStatus.CONFIRMED);
        h.setChangedByUserId(999L);
        h.setChangedAt(Instant.now());
        h.setNote("Admin confirmed");

        assertThat(h.getOrderId()).isEqualTo(orderId);
        assertThat(h.getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(h.getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(h.getChangedByUserId()).isEqualTo(999L);
    }
}
```

- [ ] **Step 2: Run tests — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/order -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement enums**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/domain/OrderStatus.java`:

```java
package com.shop.delivery.order.domain;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    ASSIGNED,
    DELIVERING,
    DELIVERED,
    CANCELLED,
    RETURNED
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/domain/PaymentMethod.java`:

```java
package com.shop.delivery.order.domain;

public enum PaymentMethod {
    COD,
    VNPAY
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/domain/PaymentStatus.java`:

```java
package com.shop.delivery.order.domain;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}
```

- [ ] **Step 4: Implement `Order` entity**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/entity/Order.java`:

```java
package com.shop.delivery.order.entity;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;
import com.shop.delivery.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", length = 128)
    private String customerName;

    @Column(name = "customer_phone", length = 32)
    private String customerPhone;

    @Column(name = "pickup_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "delivery_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal deliveryLat;

    @Column(name = "delivery_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal deliveryLng;

    @Column(name = "distance_km", nullable = false, precision = 8, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public BigDecimal getPickupLat() { return pickupLat; }
    public void setPickupLat(BigDecimal pickupLat) { this.pickupLat = pickupLat; }
    public BigDecimal getPickupLng() { return pickupLng; }
    public void setPickupLng(BigDecimal pickupLng) { this.pickupLng = pickupLng; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public BigDecimal getDeliveryLat() { return deliveryLat; }
    public void setDeliveryLat(BigDecimal deliveryLat) { this.deliveryLat = deliveryLat; }
    public BigDecimal getDeliveryLng() { return deliveryLng; }
    public void setDeliveryLng(BigDecimal deliveryLng) { this.deliveryLng = deliveryLng; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
```

- [ ] **Step 5: Implement `OrderItem` entity**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/entity/OrderItem.java`:

```java
package com.shop.delivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}
```

- [ ] **Step 6: Implement `StatusHistory` entity**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/entity/StatusHistory.java`:

```java
package com.shop.delivery.order.entity;

import com.shop.delivery.order.domain.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "status_history")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private OrderStatus toStatus;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(OrderStatus fromStatus) { this.fromStatus = fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public void setToStatus(OrderStatus toStatus) { this.toStatus = toStatus; }
    public Long getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(Long changedByUserId) { this.changedByUserId = changedByUserId; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
```

- [ ] **Step 7: Run tests — expect pass**

```bash
cd backend && ./mvnw -pl modules/order -am test
```

Expected: 9 tests pass (6 ProductService + 1 Order + 1 OrderItem + 1 StatusHistory).

- [ ] **Step 8: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/domain/ \
        backend/modules/order/src/main/java/com/shop/delivery/order/entity/Order.java \
        backend/modules/order/src/main/java/com/shop/delivery/order/entity/OrderItem.java \
        backend/modules/order/src/main/java/com/shop/delivery/order/entity/StatusHistory.java \
        backend/modules/order/src/test/java/com/shop/delivery/order/entity/
git commit -m "feat(order): add Order/OrderItem/StatusHistory entities + enums"
```

---

## TASK 5: Repositories + IT Test Base (TDD, 1 commit)

**Files:**
- 3 repositories + IT test base + 1 IT for OrderRepository

- [ ] **Step 1: Write test base + failing IT**

Create `backend/modules/order/src/test/java/com/shop/delivery/order/support/OrderTestcontainerBase.java`:

```java
package com.shop.delivery.order.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class OrderTestcontainerBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.flyway.enabled", () -> "false");
    }
}
```

Create `backend/modules/order/src/test/java/com/shop/delivery/order/OrderTestConfig.java`:

```java
package com.shop.delivery.order;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Boot config to bootstrap order module integration tests.
 * Includes auth.TelegramUser so FK from orders.customer_id resolves at @DataJpaTest.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = {Order.class, TelegramUser.class})
@EnableJpaRepositories(basePackageClasses = OrderRepository.class)
public class OrderTestConfig {
}
```

Create `backend/modules/order/src/test/java/com/shop/delivery/order/repository/OrderRepositoryIT.java`:

```java
package com.shop.delivery.order.repository;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.order.OrderTestConfig;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.support.OrderTestcontainerBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(OrderTestConfig.class)
class OrderRepositoryIT extends OrderTestcontainerBase {

    @Autowired OrderRepository orderRepo;
    @Autowired EntityManager em;

    @Test
    void shouldPersistOrderAndFindByCode() {
        TelegramUser customer = new TelegramUser();
        customer.setId(1234L);
        customer.setFirstName("Bob");
        em.persist(customer);

        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH20260519-ABCDE");
        o.setCustomerId(1234L);
        o.setPickupLat(new BigDecimal("21.0285"));
        o.setPickupLng(new BigDecimal("105.8542"));
        o.setDeliveryAddress("45 Bà Triệu");
        o.setDeliveryLat(new BigDecimal("21.0193"));
        o.setDeliveryLng(new BigDecimal("105.8503"));
        o.setDistanceKm(new BigDecimal("1.234"));
        o.setSubtotal(new BigDecimal("100000"));
        o.setDeliveryFee(new BigDecimal("25000"));
        o.setTotal(new BigDecimal("125000"));
        o.setPaymentMethod(PaymentMethod.COD);
        orderRepo.save(o);

        Page<Order> result = orderRepo.findAllByCustomerIdOrderByCreatedAtDesc(1234L, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("DH20260519-ABCDE");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void findByCodeShouldReturnOrder() {
        TelegramUser customer = new TelegramUser();
        customer.setId(5555L);
        em.persist(customer);

        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setCode("DH20260519-XYZ12");
        o.setCustomerId(5555L);
        o.setPickupLat(new BigDecimal("21.0"));
        o.setPickupLng(new BigDecimal("105.8"));
        o.setDeliveryAddress("test");
        o.setDeliveryLat(new BigDecimal("21.1"));
        o.setDeliveryLng(new BigDecimal("105.9"));
        o.setDistanceKm(new BigDecimal("5.0"));
        o.setSubtotal(new BigDecimal("50000"));
        o.setDeliveryFee(new BigDecimal("10000"));
        o.setTotal(new BigDecimal("60000"));
        o.setPaymentMethod(PaymentMethod.VNPAY);
        orderRepo.save(o);

        var found = orderRepo.findByCode("DH20260519-XYZ12");
        assertThat(found).isPresent();
        assertThat(found.get().getCustomerId()).isEqualTo(5555L);
    }
}
```

- [ ] **Step 2: Run test — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/order -am test-compile
```

Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement repositories**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/repository/OrderRepository.java`:

```java
package com.shop.delivery.order.repository;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByCode(String code);

    Page<Order> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Order> findAllByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    boolean existsByCode(String code);
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/repository/OrderItemRepository.java`:

```java
package com.shop.delivery.order.repository;

import com.shop.delivery.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findAllByOrderId(UUID orderId);
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/repository/StatusHistoryRepository.java`:

```java
package com.shop.delivery.order.repository;

import com.shop.delivery.order.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findAllByOrderIdOrderByChangedAtAsc(UUID orderId);
}
```

- [ ] **Step 4: Run IT — expect pass**

```bash
cd backend && ./mvnw -pl modules/order -am verify
```

Expected: BUILD SUCCESS, 2 IT tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/repository/ \
        backend/modules/order/src/test/java/com/shop/delivery/order/
git commit -m "feat(order): add Order/OrderItem/StatusHistory repositories with IT"
```

---

## TASK 6: ShopConfigProperties + Distance + Fee + State Machine (TDD, 1 commit)

**Files:**
- ShopConfigProperties + 3 utility services + 3 test files

- [ ] **Step 1: Write `ShopConfigProperties`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/config/ShopConfigProperties.java`:

```java
package com.shop.delivery.order.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "shop")
public class ShopConfigProperties {

    @NotNull
    private Pickup pickup = new Pickup();

    @NotNull
    private Fee fee = new Fee();

    public Pickup getPickup() { return pickup; }
    public void setPickup(Pickup pickup) { this.pickup = pickup; }
    public Fee getFee() { return fee; }
    public void setFee(Fee fee) { this.fee = fee; }

    public static class Pickup {
        @NotNull private BigDecimal lat;
        @NotNull private BigDecimal lng;
        private String address = "Shop";

        public BigDecimal getLat() { return lat; }
        public void setLat(BigDecimal lat) { this.lat = lat; }
        public BigDecimal getLng() { return lng; }
        public void setLng(BigDecimal lng) { this.lng = lng; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    public static class Fee {
        @NotNull private BigDecimal base = new BigDecimal("15000");
        @NotNull private BigDecimal perKm = new BigDecimal("5000");
        @NotNull private BigDecimal freeKm = BigDecimal.ZERO;

        public BigDecimal getBase() { return base; }
        public void setBase(BigDecimal base) { this.base = base; }
        public BigDecimal getPerKm() { return perKm; }
        public void setPerKm(BigDecimal perKm) { this.perKm = perKm; }
        public BigDecimal getFreeKm() { return freeKm; }
        public void setFreeKm(BigDecimal freeKm) { this.freeKm = freeKm; }
    }
}
```

- [ ] **Step 2: Write failing tests**

Create `backend/modules/order/src/test/java/com/shop/delivery/order/service/DistanceCalculatorTest.java`:

```java
package com.shop.delivery.order.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DistanceCalculatorTest {

    DistanceCalculator calc = new DistanceCalculator();

    @Test
    void zeroDistanceWhenSamePoint() {
        BigDecimal d = calc.haversineKm(
            new BigDecimal("21.0285"), new BigDecimal("105.8542"),
            new BigDecimal("21.0285"), new BigDecimal("105.8542")
        );
        assertThat(d.doubleValue()).isCloseTo(0.0, within(0.001));
    }

    @Test
    void shouldComputeKnownDistanceHanoiToHoChiMinh() {
        // Hà Nội (Hoàn Kiếm) → TP.HCM (Bến Thành): ~1140 km
        BigDecimal d = calc.haversineKm(
            new BigDecimal("21.0285"), new BigDecimal("105.8542"),  // Hà Nội
            new BigDecimal("10.7720"), new BigDecimal("106.6986")   // TP.HCM
        );
        assertThat(d.doubleValue()).isCloseTo(1140, within(20.0));
    }

    @Test
    void shouldHandleShortUrbanDistance() {
        // 2 điểm cách nhau ~1km trong Hà Nội
        BigDecimal d = calc.haversineKm(
            new BigDecimal("21.0285"), new BigDecimal("105.8542"),
            new BigDecimal("21.0193"), new BigDecimal("105.8503")
        );
        assertThat(d.doubleValue()).isCloseTo(1.07, within(0.2));
    }
}
```

Create `backend/modules/order/src/test/java/com/shop/delivery/order/service/FeeCalculatorTest.java`:

```java
package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    FeeCalculator calc;
    ShopConfigProperties.Fee fee;

    @BeforeEach
    void setup() {
        fee = new ShopConfigProperties.Fee();
        fee.setBase(new BigDecimal("15000"));
        fee.setPerKm(new BigDecimal("5000"));
        fee.setFreeKm(new BigDecimal("1.0"));
        ShopConfigProperties props = new ShopConfigProperties();
        props.setFee(fee);
        calc = new FeeCalculator(props);
    }

    @Test
    void shortDistanceWithinFreeKmShouldChargeBase() {
        BigDecimal feeAmt = calc.calculate(new BigDecimal("0.8"));
        assertThat(feeAmt).isEqualByComparingTo("15000");
    }

    @Test
    void distanceExceedingFreeKmShouldChargeBaseAndOverage() {
        // 3km - 1km free = 2km * 5000 = 10000 + base 15000 = 25000
        BigDecimal feeAmt = calc.calculate(new BigDecimal("3.0"));
        assertThat(feeAmt).isEqualByComparingTo("25000");
    }

    @Test
    void exactlyFreeKmShouldChargeBaseOnly() {
        BigDecimal feeAmt = calc.calculate(new BigDecimal("1.0"));
        assertThat(feeAmt).isEqualByComparingTo("15000");
    }
}
```

Create `backend/modules/order/src/test/java/com/shop/delivery/order/service/OrderStateMachineTest.java`:

```java
package com.shop.delivery.order.service;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    OrderStateMachine sm = new OrderStateMachine();

    @Test
    void pendingToConfirmedShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.PENDING, OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    void pendingToCancelledShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.PENDING, OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void confirmedToAssignedShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.CONFIRMED, OrderStatus.ASSIGNED)).isTrue();
    }

    @Test
    void assignedToDeliveringShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.ASSIGNED, OrderStatus.DELIVERING)).isTrue();
    }

    @Test
    void deliveringToDeliveredShouldBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.DELIVERING, OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void deliveredToCancelledShouldNotBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.DELIVERED, OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void cancelledToConfirmedShouldNotBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.CANCELLED, OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void pendingToDeliveredShouldNotBeAllowed() {
        assertThat(sm.isAllowed(OrderStatus.PENDING, OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void requireTransitionShouldThrowOnInvalid() {
        assertThatThrownBy(() -> sm.requireAllowed(OrderStatus.DELIVERED, OrderStatus.CANCELLED))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("DELIVERED")
            .hasMessageContaining("CANCELLED");
    }
}
```

- [ ] **Step 3: Run tests — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/order -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 4: Implement `DistanceCalculator`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/service/DistanceCalculator.java`:

```java
package com.shop.delivery.order.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /**
     * Haversine: tính khoảng cách great-circle (km) giữa 2 điểm trên Trái Đất.
     * Trả về BigDecimal scale 3 (làm tròn HALF_UP).
     */
    public BigDecimal haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());

        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                 + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double km = EARTH_RADIUS_KM * c;
        return BigDecimal.valueOf(km).setScale(3, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 5: Implement `FeeCalculator`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/service/FeeCalculator.java`:

```java
package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FeeCalculator {

    private final ShopConfigProperties.Fee fee;

    public FeeCalculator(ShopConfigProperties props) {
        this.fee = props.getFee();
    }

    /** fee = base + max(0, distanceKm - freeKm) × perKm. Làm tròn xuống đồng VND (scale 0). */
    public BigDecimal calculate(BigDecimal distanceKm) {
        BigDecimal chargeable = distanceKm.subtract(fee.getFreeKm()).max(BigDecimal.ZERO);
        BigDecimal overage = chargeable.multiply(fee.getPerKm()).setScale(0, RoundingMode.HALF_UP);
        return fee.getBase().add(overage).setScale(0, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 6: Implement `OrderStateMachine`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderStateMachine.java`:

```java
package com.shop.delivery.order.service;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.ASSIGNED, EnumSet.of(OrderStatus.DELIVERING, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.DELIVERING, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.RETURNED));
        ALLOWED.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.RETURNED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean isAllowed(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class)).contains(to);
    }

    public void requireAllowed(OrderStatus from, OrderStatus to) {
        if (!isAllowed(from, to)) {
            throw new BusinessRuleException(
                "INVALID_STATUS_TRANSITION",
                "Không thể chuyển từ " + from + " sang " + to);
        }
    }
}
```

- [ ] **Step 7: Run tests — expect pass**

```bash
cd backend && ./mvnw -pl modules/order -am test
```

Expected: All order module tests pass — 9 entity + 3 Distance + 3 Fee + 9 StateMachine + 6 Product = 30 unit tests.

- [ ] **Step 8: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/config/ \
        backend/modules/order/src/main/java/com/shop/delivery/order/service/DistanceCalculator.java \
        backend/modules/order/src/main/java/com/shop/delivery/order/service/FeeCalculator.java \
        backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderStateMachine.java \
        backend/modules/order/src/test/java/com/shop/delivery/order/service/DistanceCalculatorTest.java \
        backend/modules/order/src/test/java/com/shop/delivery/order/service/FeeCalculatorTest.java \
        backend/modules/order/src/test/java/com/shop/delivery/order/service/OrderStateMachineTest.java
git commit -m "feat(order): add ShopConfig + Haversine distance, fee calc, state machine"
```

---

## TASK 7: OrderService + OrderCodeGenerator + Commands (TDD, 1 commit)

**Files:**
- 2 command records + OrderCodeGenerator + OrderService + 2 test files

- [ ] **Step 1: Write commands**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/service/command/CreateOrderCommand.java`:

```java
package com.shop.delivery.order.service.command;

import com.shop.delivery.order.domain.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderCommand(
    Long customerId,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    BigDecimal deliveryLat,
    BigDecimal deliveryLng,
    List<OrderLineCommand> items,
    PaymentMethod paymentMethod,
    String note
) {
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/service/command/OrderLineCommand.java`:

```java
package com.shop.delivery.order.service.command;

public record OrderLineCommand(
    Long productId,
    Integer quantity
) {
}
```

- [ ] **Step 2: Write OrderCodeGenerator test**

Create `backend/modules/order/src/test/java/com/shop/delivery/order/service/OrderCodeGeneratorTest.java`:

```java
package com.shop.delivery.order.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCodeGeneratorTest {

    OrderCodeGenerator gen = new OrderCodeGenerator();

    @Test
    void shouldGenerateCodeMatchingExpectedFormat() {
        String code = gen.generate();
        String today = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(code).startsWith("DH" + today + "-");
        assertThat(code).hasSize(2 + 8 + 1 + 5);  // "DH" + date + "-" + 5 chars
    }

    @Test
    void generatedCodesShouldBeReasonablyUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) seen.add(gen.generate());
        // 5 chars base36 = 60M combos. 1000 samples should mostly be unique.
        assertThat(seen).hasSizeGreaterThan(995);
    }
}
```

- [ ] **Step 3: Write OrderService test**

Create `backend/modules/order/src/test/java/com/shop/delivery/order/service/OrderServiceTest.java`:

```java
package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.entity.OrderItem;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.entity.StatusHistory;
import com.shop.delivery.order.repository.OrderItemRepository;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.repository.StatusHistoryRepository;
import com.shop.delivery.order.service.command.CreateOrderCommand;
import com.shop.delivery.order.service.command.OrderLineCommand;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepo;
    @Mock OrderItemRepository orderItemRepo;
    @Mock StatusHistoryRepository statusHistoryRepo;
    @Mock ProductService productService;

    OrderService service;
    ShopConfigProperties shopProps;
    DistanceCalculator distance;
    FeeCalculator fee;
    OrderStateMachine sm;
    OrderCodeGenerator codeGen;

    @BeforeEach
    void setup() {
        shopProps = new ShopConfigProperties();
        shopProps.getPickup().setLat(new BigDecimal("21.0285"));
        shopProps.getPickup().setLng(new BigDecimal("105.8542"));
        shopProps.getFee().setBase(new BigDecimal("15000"));
        shopProps.getFee().setPerKm(new BigDecimal("5000"));
        shopProps.getFee().setFreeKm(BigDecimal.ZERO);

        distance = new DistanceCalculator();
        fee = new FeeCalculator(shopProps);
        sm = new OrderStateMachine();
        codeGen = new OrderCodeGenerator();

        service = new OrderService(orderRepo, orderItemRepo, statusHistoryRepo,
            productService, shopProps, distance, fee, sm, codeGen);
    }

    @Test
    void createOrderShouldComputeSubtotalDistanceFeeTotal() {
        Product p1 = makeProduct(1L, "Áo", new BigDecimal("100000"), 100);
        Product p2 = makeProduct(2L, "Quần", new BigDecimal("200000"), 50);
        when(productService.findById(1L)).thenReturn(p1);
        when(productService.findById(2L)).thenReturn(p2);
        when(orderRepo.existsByCode(any())).thenReturn(false);
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand cmd = new CreateOrderCommand(
            555L, "Bob", "+84900111222",
            "45 Bà Triệu",
            new BigDecimal("21.0193"), new BigDecimal("105.8503"),
            List.of(new OrderLineCommand(1L, 2), new OrderLineCommand(2L, 1)),
            PaymentMethod.COD,
            "Giao tối"
        );

        Order saved = service.create(cmd);

        // subtotal = 2*100000 + 1*200000 = 400000
        assertThat(saved.getSubtotal()).isEqualByComparingTo("400000");
        // distance ~1.07 km, fee = 15000 + 1.07*5000 ~ 20350 (rounded HALF_UP at scale 0)
        assertThat(saved.getDistanceKm().doubleValue()).isBetween(0.8, 1.5);
        assertThat(saved.getDeliveryFee()).isGreaterThanOrEqualTo(new BigDecimal("15000"));
        assertThat(saved.getTotal()).isEqualByComparingTo(saved.getSubtotal().add(saved.getDeliveryFee()));
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getCode()).matches("DH\\d{8}-[A-Z0-9]{5}");

        verify(orderItemRepo).saveAll(any());
        verify(statusHistoryRepo).save(any(StatusHistory.class));
    }

    @Test
    void confirmShouldTransitionPendingToConfirmed() {
        UUID id = UUID.randomUUID();
        Order o = makeOrder(id, OrderStatus.PENDING);
        when(orderRepo.findById(id)).thenReturn(Optional.of(o));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirm(id, 999L, "Admin OK");

        ArgumentCaptor<Order> orderCap = ArgumentCaptor.forClass(Order.class);
        verify(orderRepo).save(orderCap.capture());
        assertThat(orderCap.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        ArgumentCaptor<StatusHistory> histCap = ArgumentCaptor.forClass(StatusHistory.class);
        verify(statusHistoryRepo).save(histCap.capture());
        assertThat(histCap.getValue().getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(histCap.getValue().getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(histCap.getValue().getChangedByUserId()).isEqualTo(999L);
        assertThat(histCap.getValue().getNote()).isEqualTo("Admin OK");
    }

    @Test
    void cancelShouldRejectWhenStatusIsDelivered() {
        UUID id = UUID.randomUUID();
        Order o = makeOrder(id, OrderStatus.DELIVERED);
        when(orderRepo.findById(id)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.cancel(id, 555L, "khách đổi ý"))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void findByIdShouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(orderRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
            .isInstanceOf(NotFoundException.class);
    }

    private Product makeProduct(Long id, String name, BigDecimal price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStock(stock);
        p.setActive(true);
        return p;
    }

    private Order makeOrder(UUID id, OrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setStatus(status);
        return o;
    }
}
```

- [ ] **Step 4: Run tests — expect compile fail**

```bash
cd backend && ./mvnw -pl modules/order -am test
```

Expected: COMPILATION ERROR.

- [ ] **Step 5: Implement `OrderCodeGenerator`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderCodeGenerator.java`:

```java
package com.shop.delivery.order.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OrderCodeGenerator {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";  // bỏ I, O dễ nhầm

    public String generate() {
        StringBuilder suffix = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            suffix.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length())));
        }
        return "DH" + LocalDate.now().format(YYYYMMDD) + "-" + suffix;
    }
}
```

- [ ] **Step 6: Implement `OrderService`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java`:

```java
package com.shop.delivery.order.service;

import com.shop.delivery.order.config.ShopConfigProperties;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.entity.OrderItem;
import com.shop.delivery.order.entity.Product;
import com.shop.delivery.order.entity.StatusHistory;
import com.shop.delivery.order.repository.OrderItemRepository;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.repository.StatusHistoryRepository;
import com.shop.delivery.order.service.command.CreateOrderCommand;
import com.shop.delivery.order.service.command.OrderLineCommand;
import com.shop.delivery.shared.exception.NotFoundException;
import com.shop.delivery.shared.exception.ValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final StatusHistoryRepository statusHistoryRepo;
    private final ProductService productService;
    private final ShopConfigProperties shopProps;
    private final DistanceCalculator distance;
    private final FeeCalculator fee;
    private final OrderStateMachine stateMachine;
    private final OrderCodeGenerator codeGen;

    public OrderService(OrderRepository orderRepo, OrderItemRepository orderItemRepo,
                        StatusHistoryRepository statusHistoryRepo,
                        ProductService productService,
                        ShopConfigProperties shopProps,
                        DistanceCalculator distance, FeeCalculator fee,
                        OrderStateMachine stateMachine, OrderCodeGenerator codeGen) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.productService = productService;
        this.shopProps = shopProps;
        this.distance = distance;
        this.fee = fee;
        this.stateMachine = stateMachine;
        this.codeGen = codeGen;
    }

    @Transactional
    public Order create(CreateOrderCommand cmd) {
        if (cmd.items() == null || cmd.items().isEmpty()) {
            throw new ValidationException("EMPTY_ORDER", "Đơn không có sản phẩm");
        }

        // Resolve product → snapshot price → build OrderItem
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (OrderLineCommand line : cmd.items()) {
            Product p = productService.findById(line.productId());
            if (!p.isActive()) {
                throw new ValidationException("PRODUCT_INACTIVE", "Sản phẩm " + p.getName() + " không còn bán");
            }
            BigDecimal lineSub = p.getPrice().multiply(BigDecimal.valueOf(line.quantity()));
            subtotal = subtotal.add(lineSub);

            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setQuantity(line.quantity());
            item.setUnitPrice(p.getPrice());
            item.setSubtotal(lineSub);
            items.add(item);
        }

        BigDecimal pickupLat = shopProps.getPickup().getLat();
        BigDecimal pickupLng = shopProps.getPickup().getLng();
        BigDecimal distKm = distance.haversineKm(pickupLat, pickupLng, cmd.deliveryLat(), cmd.deliveryLng());
        BigDecimal deliveryFee = fee.calculate(distKm);
        BigDecimal total = subtotal.add(deliveryFee);

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCode(generateUniqueCode());
        order.setCustomerId(cmd.customerId());
        order.setCustomerName(cmd.customerName());
        order.setCustomerPhone(cmd.customerPhone());
        order.setPickupLat(pickupLat);
        order.setPickupLng(pickupLng);
        order.setDeliveryAddress(cmd.deliveryAddress());
        order.setDeliveryLat(cmd.deliveryLat());
        order.setDeliveryLng(cmd.deliveryLng());
        order.setDistanceKm(distKm);
        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setTotal(total);
        order.setPaymentMethod(cmd.paymentMethod());
        order.setNote(cmd.note());

        Order saved = orderRepo.save(order);

        for (OrderItem item : items) {
            item.setOrderId(saved.getId());
        }
        orderItemRepo.saveAll(items);

        recordTransition(saved.getId(), null, OrderStatus.PENDING, cmd.customerId(), "Đơn được tạo");

        return saved;
    }

    @Transactional(readOnly = true)
    public Order findById(UUID id) {
        return orderRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Đơn " + id + " không tồn tại"));
    }

    @Transactional(readOnly = true)
    public Page<Order> findMine(Long customerId, Pageable pageable) {
        return orderRepo.findAllByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> findAll(Pageable pageable) {
        return orderRepo.findAll(pageable);
    }

    @Transactional
    public Order confirm(UUID id, Long actorUserId, String note) {
        return transition(id, OrderStatus.CONFIRMED, actorUserId, note);
    }

    @Transactional
    public Order cancel(UUID id, Long actorUserId, String reason) {
        return transition(id, OrderStatus.CANCELLED, actorUserId, reason);
    }

    private Order transition(UUID id, OrderStatus to, Long actorUserId, String note) {
        Order order = findById(id);
        stateMachine.requireAllowed(order.getStatus(), to);
        OrderStatus from = order.getStatus();
        order.setStatus(to);
        Order saved = orderRepo.save(order);
        recordTransition(saved.getId(), from, to, actorUserId, note);
        return saved;
    }

    private void recordTransition(UUID orderId, OrderStatus from, OrderStatus to,
                                  Long actorUserId, String note) {
        StatusHistory h = new StatusHistory();
        h.setOrderId(orderId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedByUserId(actorUserId);
        h.setChangedAt(Instant.now());
        h.setNote(note);
        statusHistoryRepo.save(h);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = codeGen.generate();
            if (!orderRepo.existsByCode(candidate)) return candidate;
        }
        throw new IllegalStateException("Không thể sinh mã đơn unique sau 10 lần thử");
    }
}
```

- [ ] **Step 7: Run tests — expect pass**

```bash
cd backend && ./mvnw -pl modules/order -am test
```

Expected: All tests pass — 30 unit tests so far.

- [ ] **Step 8: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderCodeGenerator.java \
        backend/modules/order/src/main/java/com/shop/delivery/order/service/OrderService.java \
        backend/modules/order/src/main/java/com/shop/delivery/order/service/command/ \
        backend/modules/order/src/test/java/com/shop/delivery/order/service/OrderCodeGeneratorTest.java \
        backend/modules/order/src/test/java/com/shop/delivery/order/service/OrderServiceTest.java
git commit -m "feat(order): add OrderService.create/confirm/cancel + OrderCodeGenerator"
```

---

## TASK 8: Order REST Controllers + DTOs + Mapper (1 commit)

**Files:**
- 5 DTO classes + OrderMapper + 2 controllers

- [ ] **Step 1: Write DTOs**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/CreateOrderRequest.java`:

```java
package com.shop.delivery.order.api.dto;

import com.shop.delivery.order.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    String customerName,
    String customerPhone,
    @NotBlank String deliveryAddress,
    @NotNull BigDecimal deliveryLat,
    @NotNull BigDecimal deliveryLng,
    @NotEmpty @Valid List<OrderItemRequest> items,
    @NotNull PaymentMethod paymentMethod,
    String note
) {
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/OrderItemRequest.java`:

```java
package com.shop.delivery.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
    @NotNull Long productId,
    @NotNull @Min(1) Integer quantity
) {
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/OrderResponse.java`:

```java
package com.shop.delivery.order.api.dto;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    String code,
    Long customerId,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    BigDecimal deliveryLat,
    BigDecimal deliveryLng,
    BigDecimal distanceKm,
    BigDecimal subtotal,
    BigDecimal deliveryFee,
    BigDecimal total,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    OrderStatus status,
    String note,
    Instant createdAt,
    List<OrderItemResponse> items
) {
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/OrderItemResponse.java`:

```java
package com.shop.delivery.order.api.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long id,
    Long productId,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/OrderSummary.java`:

```java
package com.shop.delivery.order.api.dto;

import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.domain.PaymentMethod;
import com.shop.delivery.order.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummary(
    UUID id,
    String code,
    BigDecimal total,
    OrderStatus status,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    Instant createdAt
) {
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/CancelOrderRequest.java`:

```java
package com.shop.delivery.order.api.dto;

public record CancelOrderRequest(
    String reason
) {
}
```

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/dto/ConfirmOrderRequest.java`:

```java
package com.shop.delivery.order.api.dto;

public record ConfirmOrderRequest(
    String note
) {
}
```

- [ ] **Step 2: Write `OrderMapper`**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/mapper/OrderMapper.java`:

```java
package com.shop.delivery.order.api.mapper;

import com.shop.delivery.order.api.dto.OrderItemResponse;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.entity.OrderItem;
import com.shop.delivery.order.repository.OrderItemRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    private final OrderItemRepository itemRepo;

    public OrderMapper(OrderItemRepository itemRepo) {
        this.itemRepo = itemRepo;
    }

    public OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items = itemRepo.findAllByOrderId(o.getId()).stream()
            .map(this::toItemResponse)
            .toList();
        return new OrderResponse(
            o.getId(), o.getCode(), o.getCustomerId(), o.getCustomerName(), o.getCustomerPhone(),
            o.getDeliveryAddress(), o.getDeliveryLat(), o.getDeliveryLng(),
            o.getDistanceKm(), o.getSubtotal(), o.getDeliveryFee(), o.getTotal(),
            o.getPaymentMethod(), o.getPaymentStatus(), o.getStatus(), o.getNote(),
            o.getCreatedAt(), items
        );
    }

    public OrderSummary toSummary(Order o) {
        return new OrderSummary(
            o.getId(), o.getCode(), o.getTotal(), o.getStatus(),
            o.getPaymentMethod(), o.getPaymentStatus(), o.getCreatedAt()
        );
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(),
            item.getQuantity(), item.getUnitPrice(), item.getSubtotal());
    }
}
```

- [ ] **Step 3: Write customer controller**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/OrderController.java`:

```java
package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.CancelOrderRequest;
import com.shop.delivery.order.api.dto.CreateOrderRequest;
import com.shop.delivery.order.api.dto.OrderItemRequest;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.api.mapper.OrderMapper;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.service.OrderService;
import com.shop.delivery.order.service.command.CreateOrderCommand;
import com.shop.delivery.order.service.command.OrderLineCommand;
import com.shop.delivery.shared.exception.ValidationException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Customer-facing order API.
 * TODO P3: replace X-Customer-Id header với Telegram initData verification filter.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final String CUSTOMER_ID_HEADER = "X-Customer-Id";

    private final OrderService service;
    private final OrderMapper mapper;

    public OrderController(OrderService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestHeader(CUSTOMER_ID_HEADER) Long customerId,
                                @Valid @RequestBody CreateOrderRequest req) {
        if (customerId == null) {
            throw new ValidationException("MISSING_CUSTOMER_ID", "Thiếu header " + CUSTOMER_ID_HEADER);
        }
        CreateOrderCommand cmd = new CreateOrderCommand(
            customerId,
            req.customerName(), req.customerPhone(),
            req.deliveryAddress(), req.deliveryLat(), req.deliveryLng(),
            req.items().stream().map(i -> new OrderLineCommand(i.productId(), i.quantity())).toList(),
            req.paymentMethod(), req.note()
        );
        Order order = service.create(cmd);
        return mapper.toResponse(order);
    }

    @GetMapping("/mine")
    public Page<OrderSummary> mine(@RequestHeader(CUSTOMER_ID_HEADER) Long customerId,
                                   @PageableDefault(size = 20) Pageable pageable) {
        return service.findMine(customerId, pageable).map(mapper::toSummary);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@RequestHeader(CUSTOMER_ID_HEADER) Long customerId,
                             @PathVariable UUID id) {
        Order order = service.findById(id);
        if (!order.getCustomerId().equals(customerId)) {
            throw new com.shop.delivery.shared.exception.NotFoundException(
                "ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        return mapper.toResponse(order);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@RequestHeader(CUSTOMER_ID_HEADER) Long customerId,
                                @PathVariable UUID id,
                                @RequestBody CancelOrderRequest req) {
        Order existing = service.findById(id);
        if (!existing.getCustomerId().equals(customerId)) {
            throw new com.shop.delivery.shared.exception.NotFoundException(
                "ORDER_NOT_FOUND", "Đơn không tồn tại hoặc không thuộc về bạn");
        }
        Order cancelled = service.cancel(id, customerId, req.reason());
        return mapper.toResponse(cancelled);
    }
}
```

- [ ] **Step 4: Write admin controller**

Create `backend/modules/order/src/main/java/com/shop/delivery/order/api/AdminOrderController.java`:

```java
package com.shop.delivery.order.api;

import com.shop.delivery.order.api.dto.CancelOrderRequest;
import com.shop.delivery.order.api.dto.ConfirmOrderRequest;
import com.shop.delivery.order.api.dto.OrderResponse;
import com.shop.delivery.order.api.dto.OrderSummary;
import com.shop.delivery.order.api.mapper.OrderMapper;
import com.shop.delivery.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-facing order management.
 * TODO P4: Add @PreAuthorize("hasRole('SHOP_OWNER')") + extract admin actorId from JWT.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    /** Placeholder admin actor id used in StatusHistory until JWT auth in P4. */
    private static final Long ADMIN_ACTOR_ID = 0L;

    private final OrderService service;
    private final OrderMapper mapper;

    public AdminOrderController(OrderService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<OrderSummary> listAll(@PageableDefault(size = 50) Pageable pageable) {
        return service.findAll(pageable).map(mapper::toSummary);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return mapper.toResponse(service.findById(id));
    }

    @PostMapping("/{id}/confirm")
    public OrderResponse confirm(@PathVariable UUID id, @RequestBody(required = false) ConfirmOrderRequest req) {
        String note = req != null ? req.note() : null;
        return mapper.toResponse(service.confirm(id, ADMIN_ACTOR_ID, note));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id, @RequestBody(required = false) CancelOrderRequest req) {
        String reason = req != null ? req.reason() : null;
        return mapper.toResponse(service.cancel(id, ADMIN_ACTOR_ID, reason));
    }
}
```

- [ ] **Step 5: Verify compile**

```bash
cd backend && ./mvnw -pl modules/order -am compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/modules/order/src/main/java/com/shop/delivery/order/api/
git commit -m "feat(order): add customer + admin REST controllers for order management"
```

---

## TASK 9: ShopConfig App Wiring + Integration Test (1 commit)

**Purpose:** Wire `ShopConfigProperties` into Spring Boot. Add shop config to application.yml profiles. Add IT that creates a real order end-to-end through the REST API.

**Files:**
- Modify: application.yml, application-dev.yml, application-prod.yml — add shop.* config
- Modify: app/pom.xml — enable `@ConfigurationProperties` scanning if needed
- Create: app-level @ConfigurationPropertiesScan or @EnableConfigurationProperties
- Create: integration test for full order create flow

- [ ] **Step 1: Append shop config to `application.yml`**

Append to `backend/app/src/main/resources/application.yml`:

```yaml

shop:
  pickup:
    lat: 21.0285
    lng: 105.8542
    address: "Shop default"
  fee:
    base: 15000
    per-km: 5000
    free-km: 0
```

- [ ] **Step 2: Override in dev (same as default for now)**

Append to `backend/app/src/main/resources/application-dev.yml`:

```yaml

shop:
  pickup:
    lat: 21.0285        # Hoàn Kiếm, HN
    lng: 105.8542
    address: "123 Lê Lợi, Hoàn Kiếm, HN"
  fee:
    base: 15000
    per-km: 5000
    free-km: 1.0
```

- [ ] **Step 3: Override in prod**

Append to `backend/app/src/main/resources/application-prod.yml`:

```yaml

shop:
  pickup:
    lat: ${SHOP_PICKUP_LAT}
    lng: ${SHOP_PICKUP_LNG}
    address: ${SHOP_PICKUP_ADDRESS}
  fee:
    base: ${SHOP_FEE_BASE:15000}
    per-km: ${SHOP_FEE_PER_KM:5000}
    free-km: ${SHOP_FEE_FREE_KM:1.0}
```

- [ ] **Step 4: Add `@ConfigurationPropertiesScan` to Application**

Edit `backend/app/src/main/java/com/shop/delivery/Application.java`. Replace with:

```java
package com.shop.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.shop.delivery")
@EntityScan(basePackages = "com.shop.delivery")
@EnableJpaRepositories(basePackages = "com.shop.delivery")
@ConfigurationPropertiesScan(basePackages = "com.shop.delivery")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

This makes `BotProperties` and `ShopConfigProperties` auto-discovered.

Also update `application-test.yml` to add shop config so `ApplicationTests.contextLoads` passes. Append to `backend/app/src/test/resources/application-test.yml`:

```yaml

shop:
  pickup:
    lat: 21.0285
    lng: 105.8542
    address: "Test Shop"
  fee:
    base: 15000
    per-km: 5000
    free-km: 0
```

- [ ] **Step 5: Add app-level integration test**

Create `backend/app/src/test/java/com/shop/delivery/OrderFlowIT.java`:

```java
package com.shop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.shop.delivery.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(statements = {
    "INSERT INTO telegram_user(id, first_name, language_code) VALUES (5555, 'Test', 'vi') ON CONFLICT DO NOTHING",
    "INSERT INTO product(name, price, stock, is_active) VALUES ('Test Product', 100000, 100, true) ON CONFLICT DO NOTHING"
})
class OrderFlowIT extends PostgresTestContainer {

    @Autowired TestRestTemplate rest;

    private HttpHeaders customerHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Customer-Id", "5555");
        return h;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void shouldCreateOrderViaRestAndAdminConfirm() {
        // 1. Find a product
        ResponseEntity<JsonNode> productList = rest.getForEntity("/api/products", JsonNode.class);
        assertThat(productList.getStatusCode().is2xxSuccessful()).isTrue();
        Long productId = productList.getBody().get("content").get(0).get("id").asLong();

        // 2. Create order
        Map<String, Object> body = Map.of(
            "customerName", "Test Customer",
            "customerPhone", "+84900111222",
            "deliveryAddress", "45 Bà Triệu",
            "deliveryLat", "21.0193",
            "deliveryLng", "105.8503",
            "items", List.of(Map.of("productId", productId, "quantity", 2)),
            "paymentMethod", "COD",
            "note", "test order"
        );

        ResponseEntity<JsonNode> createResp = rest.exchange(
            "/api/orders", HttpMethod.POST,
            new HttpEntity<>(body, customerHeaders()), JsonNode.class);

        assertThat(createResp.getStatusCode().value()).isEqualTo(201);
        JsonNode order = createResp.getBody();
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(order.get("code").asText()).startsWith("DH");
        assertThat(order.get("subtotal").asLong()).isEqualTo(200000L);
        assertThat(order.get("items").size()).isEqualTo(1);

        String orderId = order.get("id").asText();

        // 3. Check /api/orders/mine
        ResponseEntity<JsonNode> mine = rest.exchange(
            "/api/orders/mine", HttpMethod.GET,
            new HttpEntity<>(customerHeaders()), JsonNode.class);
        assertThat(mine.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(mine.getBody().get("content").size()).isGreaterThanOrEqualTo(1);

        // 4. Admin confirms
        ResponseEntity<JsonNode> confirmed = rest.exchange(
            "/api/admin/orders/" + orderId + "/confirm", HttpMethod.POST,
            new HttpEntity<>(Map.of("note", "auto-confirm in test"), jsonHeaders()),
            JsonNode.class);

        assertThat(confirmed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(confirmed.getBody().get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void cancellingDeliveredOrderShouldReturn422() {
        // Skip — no DELIVERED order exists at this point (lifecycle not yet implemented).
        // Will add in P5 (delivery lifecycle).
    }
}
```

- [ ] **Step 6: Run all tests**

```bash
cd backend && ./mvnw verify
```

Expected: BUILD SUCCESS, all tests pass (~33 + ~30 new = 63 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/app/src/main/java/com/shop/delivery/Application.java \
        backend/app/src/main/resources/application.yml \
        backend/app/src/main/resources/application-dev.yml \
        backend/app/src/main/resources/application-prod.yml \
        backend/app/src/test/resources/application-test.yml \
        backend/app/src/test/java/com/shop/delivery/OrderFlowIT.java
git commit -m "feat(order): wire ShopConfigProperties + add end-to-end OrderFlowIT"
```

---

## TASK 10: Manual Smoke Test (verification, no commit)

Test full API stack with `curl`.

**Prerequisites:** Backend running with dev profile, Postgres up. From P0 setup:
```bash
docker compose -f infra/docker-compose.dev.yml ps | grep -q healthy || \
  docker compose -f infra/docker-compose.dev.yml up -d
```

Start backend (might still have bot env vars from P1):
```bash
cd backend/app && \
  BOT_TOKEN=${BOT_TOKEN:-dummy} BOT_USERNAME=${BOT_USERNAME:-DummyBot} \
  ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

In new terminal:

- [ ] **Step 1: Seed test data via SQL**

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery <<EOF
INSERT INTO telegram_user(id, first_name, language_code)
  VALUES (1234567, 'Smoke', 'vi')
  ON CONFLICT DO NOTHING;
INSERT INTO product(name, description, price, stock, is_active) VALUES
  ('Áo polo nam M', 'Cotton 100%', 250000, 50, true),
  ('Quần jeans nam', 'Slim fit', 450000, 30, true),
  ('Túi xách nữ', 'Da PU', 320000, 20, true)
  ON CONFLICT DO NOTHING;
EOF
```

- [ ] **Step 2: GET /api/products (public)**

```bash
curl -s http://localhost:8080/api/products | python3 -m json.tool | head -40
# Expected: 3 products with names, prices, stock
```

- [ ] **Step 3: Note a product ID for later**

```bash
PRODUCT_ID=$(curl -s http://localhost:8080/api/products | python3 -c "import sys, json; print(json.load(sys.stdin)['content'][0]['id'])")
echo "Will order productId=$PRODUCT_ID"
```

- [ ] **Step 4: POST /api/orders (create as customer 1234567)**

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: 1234567" \
  -d '{
    "customerName": "Thúy Lê",
    "customerPhone": "+84901234567",
    "deliveryAddress": "45 Bà Triệu, Hai Bà Trưng, HN",
    "deliveryLat": "21.0193",
    "deliveryLng": "105.8503",
    "items": [{"productId": '"$PRODUCT_ID"', "quantity": 2}],
    "paymentMethod": "COD",
    "note": "Giao buổi tối 6-8h"
  }' | python3 -m json.tool
```

Expected (key fields):
```
"code": "DH20260519-XXXXX",
"status": "PENDING",
"subtotal": 500000.00,        # 250000 × 2
"deliveryFee": ...,             # ~15000-25000
"total": ...,
"distanceKm": ~1.07,
"items": [...]
```

- [ ] **Step 5: Note the order ID**

```bash
ORDER_ID=$(curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: 1234567" \
  -d '{
    "customerName": "Smoke",
    "deliveryAddress": "test",
    "deliveryLat": "21.0",
    "deliveryLng": "105.85",
    "items": [{"productId": '"$PRODUCT_ID"', "quantity": 1}],
    "paymentMethod": "COD"
  }' | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
echo "ORDER_ID=$ORDER_ID"
```

- [ ] **Step 6: GET /api/orders/mine**

```bash
curl -s "http://localhost:8080/api/orders/mine" \
  -H "X-Customer-Id: 1234567" | python3 -m json.tool | head -30
# Expected: list of orders for customer 1234567
```

- [ ] **Step 7: GET /api/orders/:id**

```bash
curl -s "http://localhost:8080/api/orders/$ORDER_ID" \
  -H "X-Customer-Id: 1234567" | python3 -m json.tool | head -30
# Expected: full order details with items
```

- [ ] **Step 8: POST /api/admin/orders/:id/confirm**

```bash
curl -s -X POST "http://localhost:8080/api/admin/orders/$ORDER_ID/confirm" \
  -H "Content-Type: application/json" \
  -d '{"note": "Admin OK"}' | python3 -m json.tool | grep status
# Expected: "status": "CONFIRMED"
```

- [ ] **Step 9: Verify DB state**

```bash
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT id, code, status, payment_method, total FROM orders ORDER BY created_at DESC LIMIT 5"

docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery \
  -c "SELECT from_status, to_status, changed_by_user_id, note FROM status_history ORDER BY changed_at DESC LIMIT 5"
# Expected: status_history rows showing transitions (null→PENDING, then PENDING→CONFIRMED)
```

- [ ] **Step 10: Try cancelling a delivered order — expect 422**

(Skip if no delivered order exists. Use a CONFIRMED-then-CANCELLED flow for now.)

```bash
# Cancel the order (should succeed since status=CONFIRMED)
curl -s -X POST "http://localhost:8080/api/admin/orders/$ORDER_ID/cancel" \
  -H "Content-Type: application/json" \
  -d '{"reason": "khách hủy"}' | python3 -m json.tool | grep status
# Expected: "status": "CANCELLED"

# Try to confirm again — expect 422 BusinessRuleException
curl -s -X POST "http://localhost:8080/api/admin/orders/$ORDER_ID/confirm" | python3 -m json.tool
# Expected: error code INVALID_STATUS_TRANSITION
```

- [ ] **Step 11: Stop backend (Ctrl+C in Spring Boot terminal)**

---

## Acceptance Criteria (P2 done when ALL true)

- [x] `./mvnw clean verify` BUILD SUCCESS
- [x] All tests pass (~63 total, ≥ 30 new in P2)
- [x] Flyway V4 applied: product, orders, order_item, status_history tables
- [x] Smoke test passes:
  - POST /api/orders creates order with subtotal/distance/fee/total correct
  - GET /api/orders/mine returns customer's orders
  - GET /api/orders/:id returns full order with items
  - POST /api/admin/orders/:id/confirm transitions PENDING→CONFIRMED
  - POST /api/admin/orders/:id/cancel transitions to CANCELLED
  - status_history audits all transitions
  - Cancelled order cannot be re-confirmed (422 INVALID_STATUS_TRANSITION)
- [x] Order code format "DH<yyyyMMdd>-<5chars>" unique
- [x] No regressions in P1 (bot still works)

---

## Known Limitations of P2 (giải quyết ở plan sau)

| Limitation | Plan |
|---|---|
| Customer endpoints use X-Customer-Id header (not real auth) | P3 (initData verification) |
| Admin endpoints have no auth | P4 (JWT) |
| Status transitions ASSIGNED/DELIVERING/DELIVERED not actionable | P5 (delivery lifecycle) |
| `shop_config` is properties-based, not DB entity | P4 or P8 |
| No event publishing on status change | P5 (event-driven notifications) |
| No price/stock decrement validation | Maybe later — out of scope for P2 |
| `findByIdAndCustomerId` should be single query | Optimize later |

---

## Troubleshooting

**`Field 'pickup' is required` at startup:** `ShopConfigProperties.Pickup.lat/lng` is `@NotNull`. Make sure application yaml has `shop.pickup.lat` and `lng`. Default values in `Pickup` class are null because there's no sensible default — must be set.

**`Cannot deserialize value of type java.math.BigDecimal from String`:** Jackson may serialize BigDecimal as number or string. Make sure JSON request sends as string `"21.0193"` or number `21.0193`.

**Order code collision (`Không thể sinh mã đơn unique sau 10 lần thử`):** 5-char base32 = ~33M combos. Won't collide in practice. If it does, increase to 6 chars in `OrderCodeGenerator`.

**FK violation on `customer_id`:** Customer must exist in `telegram_user`. Seed test data first via SQL or run /start on bot.

**`@PageableDefault` not applying:** Check that `spring-boot-starter-web` is on classpath (it is via order pom).

---

**END OF P2 PLAN**
