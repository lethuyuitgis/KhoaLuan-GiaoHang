# Hệ thống Hỗ trợ Quản lý Giao hàng — Design Spec

**Mã đề tài:** Đề tài 3 (Khóa luận tốt nghiệp)
**Ngày tạo:** 2026-05-19
**Trạng thái:** Draft (đang chờ duyệt để chuyển sang implementation plan)
**Tác giả:** Lê Thị Trần Thúy (thuyltt@uitgis.vn)

---

## 1. Tóm tắt (Executive Summary)

Hệ thống hỗ trợ quản lý giao hàng cho **một shop online nhỏ**, vận hành đa nền tảng:

- **Khách hàng** đặt đơn qua **Telegram Mini App**, theo dõi shipper trên bản đồ bằng **Telegram Live Location**, thanh toán **COD hoặc VNPay**.
- **Shipper** nhận thông báo và xử lý đơn qua **Telegram Bot**, share Live Location khi giao.
- **Chủ shop** quản lý đơn, sản phẩm, shipper, báo cáo qua **Web Admin trên PC**, nhận notification qua **Telegram Bot**.

**Stack chính:** Spring Boot 3 (Java 17) + PostgreSQL 16 + React 18 + Vite + TailwindCSS + Leaflet, đóng gói Docker Compose.

**Kiến trúc:** Modular Monolith với 8 bounded contexts (`bot`, `auth`, `miniapp`, `webadmin`, `order`, `delivery`, `payment`, `notification`).

---

## 2. Phạm vi (Scope)

### 2.1. Trong scope

- 1 shop duy nhất (single-tenant)
- 3 vai trò: Khách hàng, Shipper, Chủ shop
- Đặt đơn online + đặt đơn thủ công (chủ shop nhập từ Facebook/Zalo)
- Tracking trạng thái đơn theo state machine
- Telegram Live Location khi đang giao
- Thanh toán COD + VNPay (sandbox)
- Báo cáo cơ bản cho chủ shop (Chart.js)
- Đánh giá shipper 1-5 sao (sau khi giao xong)

### 2.2. Ngoài scope (Won't have)

- Multi-shop / multi-tenant
- App Admin hệ thống (siêu admin quản lý nhiều shop)
- Native mobile app (iOS/Android)
- Tích hợp ngân hàng thật (chỉ dùng VNPay sandbox)
- Tích hợp đối tác giao vận (GHN, J&T, ...)

### 2.3. MoSCoW

| # | Tính năng | Ưu tiên |
|---|---|---|
| 1 | Khách đặt đơn qua Mini App | **Must** |
| 2 | Shop xem & gán đơn cho shipper (Web Admin) | **Must** |
| 3 | Shipper nhận/từ chối đơn qua Bot | **Must** |
| 4 | Cập nhật trạng thái đơn (state machine) | **Must** |
| 5 | Telegram Live Location khi giao | **Must** |
| 6 | Thông báo realtime qua Bot + WebSocket | **Must** |
| 7 | Lịch sử đơn hàng (khách + shop) | **Must** |
| 8 | Thanh toán online VNPay (sandbox) | **Must** |
| 9 | Tính cước theo khoảng cách (Haversine) | **Should** |
| 10 | Đánh giá shipper 1-5 sao | **Should** |
| 11 | Báo cáo/thống kê cho shop (Chart.js) | **Should** |
| 12 | Lưu địa chỉ thường dùng của khách | **Could** |
| 13 | Chat trong Bot giữa khách & shipper | **Could** |
| 14 | Multi-shop / multi-tenant | **Won't** |
| 15 | App Admin hệ thống riêng | **Won't** |

---

## 3. Đối tượng sử dụng (Actors)

| Actor | Thiết bị chính | Giao diện chính | Auth |
|---|---|---|---|
| **Khách hàng** | Mobile (Telegram) | Telegram Mini App | Telegram `initData` HMAC |
| **Shipper** | Mobile (Telegram) | Bot + Mini App (xem danh sách) | Telegram `initData` HMAC |
| **Chủ shop** | PC + Mobile (notification) | Web Admin (chính) + Bot (notification) | Email/password → JWT |

---

## 4. Kiến trúc tổng thể

### 4.1. Sơ đồ context

```
┌─────────────────────────────────────────────────────────────┐
│                     TELEGRAM CLIENT                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                   │
│  │ Khách    │  │ Shipper  │  │ Chủ shop │                   │
│  │ (MiniApp │  │ (Bot +   │  │ (Bot —   │                   │
│  │  + Bot)  │  │  MiniApp)│  │  noti)   │                   │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘                   │
└───────┼─────────────┼─────────────┼─────────────────────────┘
        │ Webhook HTTPS              │           ┌──────────────┐
        ▼                            ▼           │ Chủ shop     │
   ┌──────────────────────────────────┐         │ (Web Admin   │
   │       Spring Boot Monolith        │ ◄──────►│  trên PC)    │
   │  (8 bounded contexts)             │ REST+WS │              │
   └────────┬─────────────────────────┘         └──────────────┘
            │
            ▼
       PostgreSQL 16
```

### 4.2. Bounded contexts (modules)

| Module | Trách nhiệm | Entity owned |
|---|---|---|
| `bot` | Webhook receiver, update router, command/callback handlers, FSM, send Telegram message | `processed_update`, `conversation_state` |
| `auth` | Register Telegram user, verify `initData`, role management, admin email/password + JWT | `telegram_user`, `user_role`, `admin_user`, `refresh_token` |
| `miniapp` | REST API + WebSocket cho Mini App (Khách + Shipper) | (controller layer, no entity) |
| `webadmin` | REST API + WebSocket cho Web Admin (Chủ shop) | (controller layer, no entity) |
| `order` | CRUD sản phẩm, lifecycle đơn, lịch sử | `product`, `order`, `order_item`, `status_history` |
| `delivery` | Gán shipper, transition trạng thái giao hàng, lưu Live Location | `delivery_assignment`, `location_ping`, `shipper_profile` |
| `payment` | Tạo URL VNPay, verify Return/IPN, audit trail | `payment`, `payment_transaction` |
| `notification` | Listen domain event, gửi Telegram message + WebSocket push | `notification_log` (optional) |

### 4.3. Quy tắc giao tiếp giữa modules

- **Internal**: qua interface `*Service`, không gọi repository chéo module
- **Asynchronous**: `Spring ApplicationEvent` với `@TransactionalEventListener(phase = AFTER_COMMIT)`
- **DB**: mỗi module sở hữu một số bảng, không module nào query trực tiếp bảng của module khác

### 4.4. Tech stack chi tiết

| Tầng | Công nghệ | Phiên bản |
|---|---|---|
| Language | Java | 17 LTS |
| Framework | Spring Boot | 3.3.x |
| Build | Maven | 3.9+ |
| Bot lib | `telegrambots-spring-boot-starter` (rubenlagus) | 6.x |
| ORM | Spring Data JPA + Hibernate | 6.x |
| DB | PostgreSQL | 16 |
| Migration | Flyway | 10.x |
| Security | Spring Security | 6.x |
| WebSocket | Spring WebSocket (STOMP over WS) | 6.x |
| Validation | Jakarta Bean Validation | 3.x |
| Mapping | MapStruct | 1.5.x |
| Cache | Caffeine | 3.x |
| Resilience | Resilience4j (optional) | 2.x |
| Logging | Logback + Logstash encoder | latest |
| Monitoring | Spring Boot Actuator + Micrometer | included |
| Frontend FW | React + TypeScript + Vite | 18 + 5 + 5 |
| Styling | TailwindCSS | 3.x |
| Routing | React Router | 6.x |
| Server state | TanStack Query | 5.x |
| UI state | Zustand | 4.x |
| HTTP | Axios | 1.x |
| Map | react-leaflet + Leaflet + OpenStreetMap tiles | 4.x + 1.9 |
| Date | date-fns (locale vi) | 3.x |
| Telegram SDK | `@twa-dev/sdk` (Mini App only) | latest |
| Chart (Admin) | Recharts hoặc chart.js | latest |
| Form (Admin) | react-hook-form + zod | latest |
| Table (Admin) | TanStack Table | 8.x |
| Container | Docker + Docker Compose | latest |
| Reverse proxy | Nginx + Certbot (Let's Encrypt) | 1.27 |

---

## 5. Domain Model & Data Schema

### 5.1. Entities theo module

#### Module `auth`

```
telegram_user
─────────────
id              BIGINT PRIMARY KEY     -- = Telegram user ID
username        VARCHAR(64) NULL
first_name      VARCHAR(128)
last_name       VARCHAR(128) NULL
phone           VARCHAR(32) NULL       -- thu thập qua KeyboardButton.request_contact
photo_url       TEXT NULL
language_code   VARCHAR(8) NULL
is_blocked      BOOLEAN DEFAULT FALSE
created_at      TIMESTAMPTZ
updated_at      TIMESTAMPTZ

user_role
─────────
id              BIGSERIAL PK
telegram_user_id BIGINT FK → telegram_user(id)
role            VARCHAR(16)             -- CUSTOMER | SHIPPER | SHOP_OWNER
status          VARCHAR(16)             -- ACTIVE | PENDING | BLOCKED (mainly for SHIPPER approval)
assigned_at     TIMESTAMPTZ
UNIQUE (telegram_user_id, role)

admin_user                              -- chủ shop login Web Admin
──────────
id              BIGSERIAL PK
email           VARCHAR(255) UNIQUE NOT NULL
password_hash   VARCHAR(255) NOT NULL   -- BCrypt
full_name       VARCHAR(128)
telegram_user_id BIGINT FK NULL         -- link để bot gửi noti
is_active       BOOLEAN DEFAULT TRUE
created_at      TIMESTAMPTZ

refresh_token
─────────────
id              UUID PK
admin_user_id   BIGINT FK
token_hash      VARCHAR(255) UNIQUE
expires_at      TIMESTAMPTZ
revoked         BOOLEAN DEFAULT FALSE
created_at      TIMESTAMPTZ
```

#### Module `order`

```
product
───────
id              BIGSERIAL PK
name            VARCHAR(255) NOT NULL
description     TEXT NULL
price           NUMERIC(12,2) NOT NULL
image_url       TEXT NULL
stock           INT DEFAULT 0
is_active       BOOLEAN DEFAULT TRUE
created_at, updated_at

order
─────
id              UUID PK
code            VARCHAR(32) UNIQUE      -- "DH20260519-1"
customer_id     BIGINT FK → telegram_user
customer_name   VARCHAR(128)            -- snapshot
customer_phone  VARCHAR(32)             -- snapshot
pickup_lat      NUMERIC(10,7)           -- snapshot từ shop_config
pickup_lng      NUMERIC(10,7)
delivery_address TEXT
delivery_lat    NUMERIC(10,7)
delivery_lng    NUMERIC(10,7)
distance_km     NUMERIC(8,3)
subtotal        NUMERIC(12,2)
delivery_fee    NUMERIC(12,2)
total           NUMERIC(12,2)
payment_method  VARCHAR(16)             -- COD | VNPAY
payment_status  VARCHAR(16)             -- PENDING | SUCCESS | FAILED | REFUNDED  (denormalized từ latest payment.status; source of truth vẫn là payment table)
status          VARCHAR(16)             -- PENDING | CONFIRMED | ASSIGNED | DELIVERING | DELIVERED | CANCELLED | RETURNED
note            TEXT NULL
version         INT DEFAULT 0           -- @Version (optimistic lock)
created_at, updated_at
INDEX (status, created_at DESC)
INDEX (customer_id, created_at DESC)

order_item
──────────
id              BIGSERIAL PK
order_id        UUID FK → order
product_id      BIGINT FK → product
quantity        INT NOT NULL
unit_price      NUMERIC(12,2)           -- snapshot tại thời điểm tạo đơn
subtotal        NUMERIC(12,2)

status_history
──────────────
id              BIGSERIAL PK
order_id        UUID FK → order
from_status     VARCHAR(16)
to_status       VARCHAR(16)
changed_by_user_id BIGINT NULL
changed_at      TIMESTAMPTZ
note            TEXT NULL
INDEX (order_id, changed_at)
```

#### Module `delivery`

```
shipper_profile
───────────────
user_id         BIGINT PK FK → telegram_user
vehicle_type    VARCHAR(16)             -- MOTORBIKE | CAR | BICYCLE
license_plate   VARCHAR(16)
current_state   VARCHAR(16)             -- AVAILABLE | BUSY | OFFLINE
rating_avg      NUMERIC(3,2) DEFAULT 0
rating_count    INT DEFAULT 0
total_deliveries INT DEFAULT 0
updated_at

delivery_assignment
───────────────────
id              UUID PK
order_id        UUID FK UNIQUE → order
shipper_id      BIGINT FK → telegram_user
assigned_at     TIMESTAMPTZ
accepted_at     TIMESTAMPTZ NULL
rejected_at     TIMESTAMPTZ NULL
started_at      TIMESTAMPTZ NULL        -- shipper bấm "Bắt đầu giao"
delivered_at    TIMESTAMPTZ NULL
cancelled_at    TIMESTAMPTZ NULL
INDEX (shipper_id, assigned_at DESC)

location_ping
─────────────
id              BIGSERIAL PK
assignment_id   UUID FK → delivery_assignment
lat             NUMERIC(10,7)
lng             NUMERIC(10,7)
accuracy        NUMERIC(8,2) NULL
heading         NUMERIC(5,2) NULL
recorded_at     TIMESTAMPTZ
INDEX (assignment_id, recorded_at DESC)

rating                                  -- Should #10
──────
id              BIGSERIAL PK
order_id        UUID UNIQUE FK → order
customer_id     BIGINT FK
shipper_id      BIGINT FK
stars           SMALLINT CHECK (stars BETWEEN 1 AND 5)
comment         TEXT NULL
created_at      TIMESTAMPTZ
```

#### Module `payment`

```
payment
───────
id              UUID PK
order_id        UUID FK → order
method          VARCHAR(16)             -- COD | VNPAY
amount          NUMERIC(12,2)
status          VARCHAR(16)             -- PENDING | SUCCESS | FAILED | REFUNDED
vnp_txn_ref     VARCHAR(64) UNIQUE NULL -- "DH20260519-1-1716100000000"
vnp_transaction_no VARCHAR(64) NULL
vnp_response_code VARCHAR(8) NULL
paid_at         TIMESTAMPTZ NULL
version         INT DEFAULT 0
created_at, updated_at

payment_transaction
───────────────────
id              BIGSERIAL PK
payment_id      UUID FK → payment
event_type      VARCHAR(16)             -- CREATE | IPN | RETURN
raw_payload     JSONB
recorded_at     TIMESTAMPTZ
```

#### Module `shop_config` (singleton, dùng chung)

```
shop_config
───────────
id              SMALLINT PK             -- = 1 (singleton)
name            VARCHAR(128)
pickup_address  TEXT
pickup_lat      NUMERIC(10,7)
pickup_lng      NUMERIC(10,7)
base_fee        NUMERIC(12,2)           -- phí cố định
fee_per_km      NUMERIC(12,2)           -- phí mỗi km vượt
free_km         NUMERIC(5,2) DEFAULT 0  -- km miễn phí đầu (vd: 1km đầu free)
support_phone   VARCHAR(32)
updated_at
```

#### Misc

```
saved_address                           -- Could #12
─────────────
id              BIGSERIAL PK
customer_id     BIGINT FK → telegram_user
label           VARCHAR(64)
address         TEXT
lat             NUMERIC(10,7)
lng             NUMERIC(10,7)
is_default      BOOLEAN DEFAULT FALSE

processed_update                        -- bot idempotency
────────────────
update_id       BIGINT PK
processed_at    TIMESTAMPTZ DEFAULT NOW()
-- Cron xóa record > 24h

conversation_state                      -- bot FSM
──────────────────
telegram_user_id BIGINT PK FK
state           VARCHAR(64)
data            JSONB
updated_at      TIMESTAMPTZ
-- TTL 30 phút, cron xóa stale

notification_log (optional, audit)
─────────────────
id              BIGSERIAL PK
user_id         BIGINT
channel         VARCHAR(8)              -- BOT | WS
message_type    VARCHAR(32)
payload         JSONB
status          VARCHAR(16)             -- SENT | FAILED | RATE_LIMITED
sent_at         TIMESTAMPTZ
```

### 5.2. Enum chính

| Enum | Values |
|---|---|
| `Role` | `CUSTOMER`, `SHIPPER`, `SHOP_OWNER` |
| `OrderStatus` | `PENDING`, `CONFIRMED`, `ASSIGNED`, `DELIVERING`, `DELIVERED`, `CANCELLED`, `RETURNED` |
| `PaymentMethod` | `COD`, `VNPAY` |
| `PaymentStatus` | `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED` (dùng cho cả `payment.status` và `order.payment_status`; `order.payment_status` là denormalized field — source of truth là `payment` table) |
| `ShipperState` | `AVAILABLE`, `BUSY`, `OFFLINE` |
| `VehicleType` | `MOTORBIKE`, `CAR`, `BICYCLE` |
| `UserRoleStatus` | `ACTIVE`, `PENDING`, `BLOCKED` |

### 5.3. State machine `OrderStatus`

```
   PENDING ──(payment SUCCESS hoặc shop confirm COD)──► CONFIRMED
      │                                                    │
      │                                                    ▼
      │                                       (shop gán shipper, shipper accept)
      │                                                    │
      │                                                    ▼
      │                                                ASSIGNED ──(shipper bắt đầu giao)──► DELIVERING ──(delivered)──► DELIVERED
      │                                                    │                                     │                          │
      └──(khách hủy)──► CANCELLED ◄────────────────────────┴─────────(shop hủy/shipper hoàn)─────┘                          │
                                                                                                                              ▼
                                                                                                                        (khách rate)
```

Cho phép transition cụ thể (whitelist):

| From → To | Điều kiện |
|---|---|
| `PENDING` → `CONFIRMED` | Payment SUCCESS, hoặc Admin confirm COD |
| `PENDING` → `CANCELLED` | Customer/Admin hủy |
| `CONFIRMED` → `ASSIGNED` | Shipper accept (sau khi admin assign hoặc broadcast) |
| `CONFIRMED` → `CANCELLED` | Admin hủy |
| `ASSIGNED` → `DELIVERING` | Shipper bấm "Bắt đầu giao" |
| `ASSIGNED` → `CANCELLED` | Admin hủy (cần lý do) |
| `DELIVERING` → `DELIVERED` | Shipper bấm "Đã giao" |
| `DELIVERING` → `RETURNED` | Shipper báo không liên lạc được |

---

## 6. User Flows

### 6.1. Flow 1: Khách đặt đơn

```
1. Khách: Telegram → @YourShopBot → /start
2. Bot: Welcome message + nút "🛒 Đặt hàng" (KeyboardButton.web_app)
3. Khách bấm "Đặt hàng" → Telegram mở Mini App WebView
4. Backend: nhận initData → verify HMAC → trả /api/me
5. Mini App:
   - GET /api/products → hiện catalog
   - Khách thêm sản phẩm vào cart (Zustand)
   - Vào checkout: nhập địa chỉ giao (autocomplete + ghim Leaflet), SĐT, ghi chú
   - Backend tính phí ship (Haversine) → hiện total
   - Chọn COD hoặc VNPay
6. Khách bấm "Xác nhận":
   - COD: POST /api/orders → order PENDING → bot noti chủ shop
   - VNPAY: POST /api/orders + POST /api/payment/vnpay/create
            → trả URL VNPay → Mini App mở URL (window.location)
            → khách thanh toán → VNPay redirect /api/payment/vnpay/return
            → song song VNPay gọi IPN → order CONFIRMED (IPN là source of truth)
7. Bot gửi khách: "Đơn DH20260519-1 đã tạo, đang chờ shipper nhận"
```

### 6.2. Flow 2: Chủ shop xác nhận & gán shipper

```
1. Chủ shop nhận noti Bot: "🔔 Đơn mới DH20260519-1 (COD/VNPay, 250k)"
2. Chủ shop: browser → admin.yourdomain.com → login email/password → JWT
3. Dashboard: bảng đơn realtime (WebSocket subscribe /topic/admin/orders)
4. Click đơn → modal chi tiết → "Xác nhận" (nếu COD chưa CONFIRMED)
5. Chọn shipper từ dropdown (filter currentState=AVAILABLE):
   - "Gán trực tiếp" → 1 shipper cụ thể
   - "Broadcast" → tất cả AVAILABLE, ai accept trước được
6. Backend tạo DeliveryAssignment, gửi bot offer cho shipper(s)
```

### 6.3. Flow 3: Shipper nhận & giao đơn

```
1. Shipper nhận bot message:
   "📦 Đơn mới DH20260519-1
    Lấy: 123 Lê Lợi (shop)
    Giao: 45 Bà Triệu  (~3.2km)
    Phí ship: 25k     [✅ Nhận] [❌ Từ chối]"
2. Bấm "Nhận":
   - DeliveryAssignment.accepted_at = NOW(), order → ASSIGNED
   - Bot báo khách: "Shipper [Tên] đã nhận đơn"
   - Web Admin WS update
3. Shipper đến shop → bấm "🚀 Bắt đầu giao" (bot hoặc MiniApp)
   - order → DELIVERING
   - Bot reply: "Hãy share Live Location để khách theo dõi:
     📎 → Location → Share Live Location for 1 hour"
4. Shipper share Live Location:
   - Telegram gửi initial Update với message.location
   - Telegram tự edit message (~10-30s/lần) với edited_message.location
   - Backend lưu mỗi ping vào location_ping
   - Backend push WS /topic/order/{id}/location → khách thấy map update
5. Khách: trong MiniApp mở "Theo dõi đơn":
   - Leaflet map + 3 marker (shop, shipper, đích) + Polyline
   - Cập nhật realtime khi nhận WS message
6. Shipper đến nơi → bấm "✅ Đã giao":
   - order → DELIVERED
   - Bot báo khách: "Đơn đã giao. Đánh giá shipper? ⭐⭐⭐⭐⭐"
```

### 6.4. Flow 4: Hủy / Hoàn đơn

```
- Khách hủy (PENDING/CONFIRMED, chưa ASSIGNED):
  MiniApp → "Hủy đơn" → CANCELLED
  Nếu đã thanh toán VNPay → mark REFUND_REQUESTED, chủ shop xử lý thủ công
- Shipper báo "Không liên lạc được khách" (DELIVERING):
  Bot → "Hoàn về shop" → RETURNED
- Admin có thể CANCEL bất kỳ lúc nào trước DELIVERED (kèm lý do)
```

### 6.5. Flow 5: Đăng ký Shipper (FSM)

```
1. Bot /start → "Đăng ký làm shipper" → FSM bắt đầu
   States: AWAITING_NAME → AWAITING_PHONE (request_contact)
         → AWAITING_VEHICLE (inline) → AWAITING_PLATE → DONE
2. user_role(SHIPPER) tạo với status=PENDING
3. Bot noti chủ shop: "Có shipper mới đăng ký"
4. Chủ shop vào Web Admin → /shippers → duyệt
5. status=ACTIVE → bot báo shipper "Đã được duyệt"
```

### 6.6. Flow 6: Đánh giá shipper (Should #10)

```
1. Order DELIVERED → bot gửi khách inline keyboard 5 sao
2. Khách bấm sao → callback "RATE:<orderId>:<stars>"
3. Bot hỏi comment (optional) → state CUSTOMER_RATING_COMMENT
4. Backend lưu rating + update shipper_profile.rating_avg/count
```

---

## 7. Telegram Bot Architecture

### 7.1. Bot mode

| Profile | Mode | Lý do |
|---|---|---|
| `dev` | Long polling (`TelegramLongPollingBot`) | Không cần HTTPS, không cần expose port |
| `prod` | Webhook (`TelegramWebhookBot`) | Hiệu năng cao, ít tài nguyên |

### 7.2. Package structure `module/bot`

```
com.shop.delivery.bot/
├── config/                  ── BotConfig, TelegramBotsConfig
├── DeliveryBot.java         ── Extends TelegramWebhookBot
├── router/                  ── UpdateRouter, RoleResolver (cache 5p)
├── handler/
│   ├── common/              ── StartHandler, HelpHandler, UnknownCommandHandler
│   ├── customer/            ── /myorders, callback ORDER_DETAIL
│   ├── shipper/             ── Registration FSM, OrderOffer, StartDelivery, Complete, LiveLocation
│   └── shopowner/           ── Notification only (no command)
├── fsm/                     ── ConversationState enum, ConversationStore (Postgres), Flow classes
├── keyboard/                ── KeyboardFactory, CallbackData constants
└── sender/                  ── BotSender @Service wrapper (rate-limited)
```

### 7.3. UpdateRouter logic

```
Update đến → UpdateRouter:
  1. Update.hasMessage()?
     - Resolve role(user_id) → [CUSTOMER | SHIPPER | SHOP_OWNER | NEW_USER]
     - Nếu text bắt đầu "/" → CommandHandler theo role
     - Nếu message.contact → ContactHandler (FSM đăng ký shipper)
     - Nếu message.location → LiveLocationHandler (initial)
     - Nếu user đang trong FSM state → tiếp tục FSM
  2. Update.hasEditedMessage() && có location?
     - LiveLocationHandler (subsequent edits)
  3. Update.hasCallbackQuery()?
     - Parse callback_data "ACTION:param1:param2"
     - Route theo prefix action
  4. Khác → log + ignore
```

### 7.4. Commands & UI per role

**Khách:**
```
/start    → Welcome + nút "🛒 Đặt hàng" (web_app)
/myorders → Inline list 10 đơn gần nhất, callback "ORDER_DETAIL:<id>"
/help
```

**Shipper:**
```
/start         → Nếu chưa đăng ký: bắt đầu FSM
                  Nếu PENDING: "Đang chờ duyệt"
                  Nếu ACTIVE: menu chính
/myassignments → Danh sách đơn đang giao
/available     → Toggle AVAILABLE ↔ OFFLINE
/help
```
Push noti khi có đơn (không phải command):
```
📦 Đơn mới DH20260519-1
   Khoảng cách: 3.2km — Phí ship: 25k
   Giao: 45 Bà Triệu, Hai Bà Trưng
   [✅ Nhận]  [❌ Từ chối]
```

**Chủ shop:**
```
/start → "Truy cập admin tại: https://admin.yourdomain.com"
/help
```
Push noti:
```
🔔 Đơn mới DH20260519-1 (250k, COD)
   📍 Quận Hai Bà Trưng
   [Xem ngay]  ← URL button mở Web Admin
```

### 7.5. Telegram Live Location handling

- Telegram gửi `Update.message.location` (lần đầu) + `Update.edited_message.location` (mỗi ~10-30s)
- `LiveLocationHandler`:
  - Lookup `DeliveryAssignment` đang `DELIVERING` của shipper này
  - Lưu `LocationPing`
  - Push WebSocket `/topic/order/{orderId}/location`
- Khi shipper "stop sharing" → message edit thành location bình thường (không có `live_period`) → backend hiểu đã stop
- Nếu shipper không share location → hệ thống vẫn hoạt động, chỉ thiếu map realtime (khách vẫn nhận được status updates)

### 7.6. Idempotency & rate limit

- Bảng `processed_update(update_id)` — Telegram retry khi webhook 5xx
- `Bucket4j` rate limit: 25 msg/s toàn bot, 1 msg/s mỗi chat
- `telegram_user.is_blocked=true` khi nhận 403 từ Telegram (user block bot)

### 7.7. Module dependency

```
bot ──► auth  (resolve role, register user)
bot ──► order (read order info để build offer message)
bot ──► delivery (update assignment trên accept/start/complete)
notification ──► bot (BotSender.sendMessage)
```

---

## 8. Frontend Architecture

### 8.1. Repo & build

```
frontend/
├── pnpm-workspace.yaml
├── shared/      ── @shop/shared (types, api client, formatters)
├── miniapp/     ── Vite app, build → dist/miniapp/
└── webadmin/    ── Vite app, build → dist/webadmin/
```

Nginx serve:
- `https://yourdomain.com/miniapp/` → `dist/miniapp/`
- `https://admin.yourdomain.com/` → `dist/webadmin/`

### 8.2. Mini App routes

```
/                                ── Splash (detect role → redirect)
/customer/shop                   ── Catalog
/customer/product/:id            ── Chi tiết
/customer/cart                   ── Giỏ hàng
/customer/checkout               ── Form đặt + thanh toán
/customer/orders                 ── Lịch sử
/customer/orders/:id             ── Chi tiết + map tracking (nếu DELIVERING)
/customer/payment-success
/customer/payment-failed
/shipper/assignments             ── Đơn đang giao
/shipper/assignments/:id         ── Chi tiết + nút action
/shipper/history                 ── Lịch sử
```

### 8.3. Web Admin routes

```
/login
/dashboard                       ── KPI + biểu đồ + đơn realtime
/orders                          ── Table (filter status/date/search)
/orders/:id                      ── Chi tiết + assign shipper modal
/products                        ── CRUD
/products/new
/products/:id/edit
/shippers                        ── Duyệt, khoá, profile
/shippers/:id
/reports                         ── Doanh thu/ngày, top shipper, tỉ lệ hoàn
/settings                        ── shop_config
```

### 8.4. Mini App auth flow

```
1. App mount → WebApp.ready() + WebApp.expand()
2. Đọc WebApp.initData (raw string đã ký HMAC bởi Telegram)
3. Mọi request gắn header X-Telegram-Init-Data: <initData>
4. Backend verify HMAC bằng bot.token → trust user info
5. GET /api/me → trả {user, roles}
6. Route theo role: nếu CUSTOMER → /customer/shop; SHIPPER → /shipper/assignments
```

### 8.5. Web Admin auth flow

```
1. POST /api/admin/auth/login {email, password}
   → BCrypt verify → { accessToken (JWT, 15p), refreshToken (DB-backed, 7 ngày) }
2. Axios interceptor:
   - Gắn Authorization: Bearer <accessToken> mọi request
   - Nếu 401 → POST /refresh → retry
3. Logout → POST /logout → revoke refresh_token row
```

### 8.6. WebSocket topics

```
/topic/order/{orderId}/status         ── Customer + shipper của đơn
/topic/order/{orderId}/location       ── Customer xem map (DELIVERING)
/topic/admin/orders                   ── Mọi đổi trạng thái đơn (admin only)
/topic/admin/shippers                 ── Shipper online/offline (admin only)
/user/queue/personal                  ── Per-user (nếu cần)
```

WebSocket auth qua `ChannelInterceptor` trên CONNECT frame, đọc header `X-Telegram-Init-Data` hoặc `Authorization: Bearer <jwt>`.

---

## 9. REST API (tóm tắt — chi tiết trong plan)

### 9.1. Public / Mini App (initData)

```
GET    /api/me
GET    /api/products
GET    /api/products/:id
POST   /api/orders                          {items, deliveryAddress, lat, lng, note, paymentMethod}
GET    /api/orders/mine                     ?page&size&status
GET    /api/orders/:id
POST   /api/orders/:id/cancel
POST   /api/orders/:id/rating               {stars, comment}
POST   /api/payment/vnpay/create            {orderId}  → {paymentUrl}
GET    /api/payment/vnpay/return            (VNPay redirect, public no auth, verify signature)
POST   /api/payment/vnpay/ipn               (VNPay server-to-server, no auth, verify signature)
```

### 9.2. Shipper (initData)

```
GET    /api/shipper/assignments
GET    /api/shipper/assignments/:id
POST   /api/shipper/assignments/:id/accept
POST   /api/shipper/assignments/:id/reject
POST   /api/shipper/assignments/:id/start
POST   /api/shipper/assignments/:id/complete
POST   /api/shipper/profile/state           {state: AVAILABLE | OFFLINE}
```

### 9.3. Admin (JWT)

```
POST   /api/admin/auth/login
POST   /api/admin/auth/refresh
POST   /api/admin/auth/logout

GET    /api/admin/dashboard/summary
GET    /api/admin/orders                    ?status&from&to&page&size&q
GET    /api/admin/orders/:id
POST   /api/admin/orders/:id/confirm
POST   /api/admin/orders/:id/assign         {shipperId | broadcast: true}
POST   /api/admin/orders/:id/cancel         {reason}

GET    /api/admin/products
POST   /api/admin/products
PUT    /api/admin/products/:id
DELETE /api/admin/products/:id

GET    /api/admin/shippers
GET    /api/admin/shippers/:id
POST   /api/admin/shippers/:id/approve
POST   /api/admin/shippers/:id/block
POST   /api/admin/shippers/:id/unblock

GET    /api/admin/reports/revenue           ?from&to&groupBy=day|week
GET    /api/admin/reports/top-shippers      ?from&to&limit
GET    /api/admin/reports/cancellation      ?from&to

GET    /api/admin/settings
PUT    /api/admin/settings
```

### 9.4. Bot webhook

```
POST   /api/bot/webhook                     (Telegram → backend, verify secret token header)
```

---

## 10. Thanh toán VNPay

### 10.1. Tham số request

| Param | Giá trị |
|---|---|
| `vnp_Version` | `2.1.0` |
| `vnp_Command` | `pay` |
| `vnp_TmnCode` | env `VNPAY_TMN_CODE` |
| `vnp_Amount` | `order.total * 100` |
| `vnp_CurrCode` | `VND` |
| `vnp_TxnRef` | `"{orderCode}-{epochMs}"` UNIQUE |
| `vnp_OrderInfo` | `"Thanh toan don hang {orderCode}"` (không dấu) |
| `vnp_OrderType` | `other` |
| `vnp_Locale` | `vn` |
| `vnp_ReturnUrl` | `https://yourdomain.com/api/payment/vnpay/return` |
| `vnp_IpAddr` | IP khách (từ `X-Forwarded-For`) |
| `vnp_CreateDate` | `yyyyMMddHHmmss` GMT+7 |
| `vnp_ExpireDate` | `+15p` |
| `vnp_SecureHash` | HMAC-SHA512 trên params đã sort + urlencode |

### 10.2. Return URL handler

- Verify chữ ký
- KHÔNG cập nhật DB (IPN mới là source of truth)
- Redirect browser về Mini App: `/customer/payment-success` hoặc `/customer/payment-failed`

### 10.3. IPN handler (Source of truth)

```
1. Verify chữ ký → fail: trả {RspCode: 97}
2. Tìm payment theo vnp_TxnRef → not found: {RspCode: 01}
3. Check idempotent (payment.status != PENDING) → {RspCode: 02}
4. Verify amount (vnp_Amount === payment.amount * 100) → fail: {RspCode: 04}
5. Nếu vnp_ResponseCode=00 && vnp_TransactionStatus=00:
   - payment.markSuccess(vnp_TransactionNo)
   - orderService.confirmAfterPayment(orderId) → publish OrderConfirmedEvent
6. Else: payment.markFailed(responseCode)
7. Lưu payment_transaction (event_type=IPN, raw_payload=JSONB)
8. Trả {RspCode: 00, Message: Confirm Success}
```

### 10.4. Security checklist

| Lỗ hổng | Mitigation |
|---|---|
| Replay attack | Check `payment.status != PENDING` |
| Amount tampering | So sánh `vnp_Amount` với `payment.amount * 100` |
| Fake IPN | Verify HMAC chữ ký |
| Timing attack | `MessageDigest.isEqual` (constant time) |
| Tin return URL | KHÔNG update DB ở `/return`, chỉ ở `/ipn` |
| Race return/IPN | DB pessimistic/optimistic lock trên Payment row |

### 10.5. Edge cases

- Khách đóng browser trước redirect: IPN vẫn cập nhật DB; khách xem lại order thấy đúng
- IPN đến trước Return: DB đã cập nhật, Return chỉ redirect UI
- VNPay timeout 15p: `vnp_ExpireDate` đã set; cron mark FAILED nếu payment PENDING quá hạn
- Khách thanh toán lại sau fail: tạo Payment mới (txnRef khác) cho cùng order

### 10.6. Config env

```yaml
vnpay:
  tmn-code:    ${VNPAY_TMN_CODE}
  hash-secret: ${VNPAY_HASH_SECRET}
  pay-url:     https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  return-url:  https://yourdomain.com/api/payment/vnpay/return
  ipn-url:     https://yourdomain.com/api/payment/vnpay/ipn
  timeout-minutes: 15
```

---

## 11. Realtime, Notification & Error Handling

### 11.1. Event-driven notification

| Event | Publisher | Listeners |
|---|---|---|
| `OrderCreatedEvent` | order | Bot → shop; WS → admin |
| `OrderConfirmedEvent` | order/payment | Bot → customer; WS → customer + admin |
| `OrderAssignedEvent` | delivery | Bot → shipper (offer); WS → admin |
| `DeliveryStartedEvent` | delivery | Bot → customer; WS → customer + admin |
| `DeliveryCompletedEvent` | delivery | Bot → customer (rating prompt); WS → admin |
| `OrderCancelledEvent` | order | Bot → customer + shipper; WS → admin |
| `LocationPingReceivedEvent` | bot | WS → customer (map) |
| `PaymentSucceededEvent` | payment | trigger OrderConfirmedEvent |
| `ShipperRegisteredEvent` | auth | Bot → shop |
| `ShipperApprovedEvent` | auth | Bot → shipper |

Listener dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` để tránh gửi noti khi transaction rollback.

### 11.2. WebSocket (STOMP)

- Endpoint: `/ws` (SockJS fallback)
- Auth qua `ChannelInterceptor` trên CONNECT frame
- Topic authorization: `@MessageMapping` + `@PreAuthorize` check ownership

### 11.3. Error handling 3 tầng

**Domain exceptions:**
```
DomainException extends RuntimeException
  ├── NotFoundException             ── 404
  ├── ValidationException           ── 400
  ├── BusinessRuleException         ── 422
  ├── AuthenticationException       ── 401
  ├── AuthorizationException        ── 403
  ├── ExternalServiceException      ── 502
  └── ConflictException             ── 409
```

**Global handler** `@RestControllerAdvice` → response body:
```json
{
  "code": "ORDER_NOT_CANCELLABLE",
  "message": "Đơn đã được giao, không thể hủy",
  "traceId": "a3b1c5e8",
  "timestamp": "2026-05-19T10:30:00Z"
}
```

**Frontend** Axios interceptor → `WebApp.showAlert` (Mini App) / toast (Web Admin), auto logout 401.

### 11.4. External services resilience

| Service | Timeout | Retry | Circuit breaker |
|---|---|---|---|
| VNPay | 5s connect / 10s read | Không retry POST tạo URL | Optional |
| Telegram API | 30s | 3 lần exponential backoff cho 5xx/timeout | — |
| Database | HikariCP default | Spring Retry cho `OptimisticLockingFailureException` | — |

Telegram rate limit: Bucket4j 25 msg/s toàn bot, 1 msg/s mỗi chat.

### 11.5. Logging & observability

- Logback JSON format trong prod (logstash-logback-encoder)
- MDC: `traceId` (UUID), `userId`, `chatId`
- Mask sensitive: bot token, hash secret, phone (giữ 4 số cuối)
- Spring Boot Actuator: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`

### 11.6. Idempotency

- Bot webhook: bảng `processed_update(update_id)`
- VNPay IPN: check `payment.status != PENDING`
- Create order: header `X-Idempotency-Key: <UUID>` cache 5 phút (Caffeine)

---

## 12. Testing Strategy

| Tầng | Tool | Phạm vi |
|---|---|---|
| Unit | JUnit 5 + Mockito | Service logic, sign/verify VNPay, FSM transitions, Haversine |
| Integration | `@SpringBootTest` + Testcontainers (Postgres) | Repository, controller, event flow |
| Bot test | Mock TelegramBot + Update JSON fixtures | Handler routing, callback parsing, FSM |
| VNPay security test | WireMock VNPay sandbox | Signature valid/invalid, IPN replay, amount tampering |
| Frontend unit | Vitest + Testing Library | Component, hooks, utils |
| E2E | Playwright | Web Admin happy path (login → tạo product → xem đơn) |
| Manual | 2 điện thoại + tài khoản test | Telegram Bot flow + Live Location |

### Test cases bắt buộc cho VNPay (báo cáo bảo mật)

| Case | Test data | Expected |
|---|---|---|
| Happy path | Card NCB hợp lệ, OTP đúng | Order CONFIRMED, Payment SUCCESS |
| OTP sai | OTP `000000` | Payment FAILED, Order vẫn PENDING |
| Khách hủy thanh toán | Bấm "Hủy" trên VNPay | `vnp_ResponseCode=24`, Payment FAILED |
| Replay IPN | Curl IPN 2 lần | Lần 2 trả `RspCode 02` |
| Fake signature | Sửa `vnp_SecureHash` | Trả `RspCode 97` |
| Amount tampering | Sửa `vnp_Amount` trong query | Trả `RspCode 04` |

---

## 13. Deployment

### 13.1. Docker Compose services

```
postgres   ── PostgreSQL 16 + healthcheck + volume pgdata
backend    ── Spring Boot fat-jar
frontend   ── Multi-stage: build (node 20) → serve (nginx static)
nginx      ── Reverse proxy + Let's Encrypt SSL, ports 80/443
```

### 13.2. Nginx routing

```
https://yourdomain.com
  /api/        → backend:8080
  /ws          → backend:8080 (upgrade)
  /miniapp/    → frontend (static)
  /            → 301 → /miniapp/

https://admin.yourdomain.com
  /api/        → backend:8080
  /ws          → backend:8080
  /            → frontend (webadmin static)
```

### 13.3. Profiles

| | `dev` | `prod` |
|---|---|---|
| Bot mode | Long polling | Webhook |
| DB | Local Postgres | Postgres compose |
| VNPay | Sandbox | Sandbox (khóa luận) |
| Log level | DEBUG | INFO |
| CORS | `localhost:*` | Domain cụ thể |
| HTTPS | Off | Required (Let's Encrypt) |

### 13.4. Env vars chính

```
DB_URL, DB_USER, DB_PASSWORD
BOT_TOKEN, BOT_USERNAME, BOT_WEBHOOK_URL, BOT_WEBHOOK_SECRET
VNPAY_TMN_CODE, VNPAY_HASH_SECRET
JWT_SECRET
SHOP_TELEGRAM_OWNER_ID (chat_id chủ shop để noti)
```

---

## 14. Timeline (16 tuần)

| Tuần | Nội dung | Output |
|---|---|---|
| 1 | Spec + Plan + Project skeleton + Docker Compose dev + Flyway baseline | "Hello World" Spring Boot + Postgres chạy |
| 2 | Module `auth` + `bot` skeleton, `/start`, register user | Bot trả lời `/start` |
| 3 | Module `order` (entity, CRUD product, create order API) | Postman test pass |
| 4 | Mini App skeleton + TWA SDK + initData verify | Mini App mở từ bot, hiện user info |
| 5 | Mini App: catalog + cart + checkout (COD) | Khách đặt được đơn COD |
| 6 | Web Admin skeleton + JWT login + bảng đơn | Chủ shop login + xem đơn |
| 7 | Assign shipper + bot offer/accept flow | Shipper accept đơn qua bot |
| 8 | Delivery flow: start/complete, status history | Vòng đời 1 đơn từ tạo → giao xong |
| 9 | Telegram Live Location handler + WS + Leaflet | Khách thấy shipper trên map |
| 10 | VNPay integration + security tests | Đơn VNPay flow đầy đủ |
| 11 | Dashboard biểu đồ + báo cáo + rating + phí ship theo km | Should-have features |
| 12 | Polish UI, error handling, logging, edge cases | App ổn định |
| 13 | Deploy VPS + Let's Encrypt + test thực tế | URL public, demo được |
| 14 | Báo cáo Ch.1-3 | Bản nháp |
| 15 | Báo cáo Ch.4-5 | Bản nháp |
| 16 | Ch.6-7 + slide + demo video + dry-run | Hoàn chỉnh |

---

## 15. Rủi ro & Mitigation

| Rủi ro | Mức | Mitigation |
|---|---|---|
| Domain HTTPS chậm có | Cao | Đăng ký tuần 1; tạm dùng ngrok/cloudflare tunnel khi dev |
| VNPay sandbox đăng ký lâu | TB | Đăng ký song song coding tuần 1 |
| Telegram Live Location khó test | TB | 2 điện thoại + seed dữ liệu giả để demo |
| Scope creep | Cao | Đã chốt MoSCoW, KHÔNG mở Won't items |
| Lệch deadline tuần 9-10 | Cao | 4 tuần đệm cuối; ưu tiên Must trước Should |
| Hội đồng hỏi sâu bảo mật VNPay | TB | Có security checklist + test cases minh chứng |

---

## 16. Cấu trúc báo cáo khóa luận

```
Ch.1 Mở đầu
Ch.2 Cơ sở lý thuyết & công nghệ
     - Telegram Bot Platform (Bot API, Mini App, Live Location)
     - Spring Boot + DDD-lite (bounded context)
     - VNPay & cổng thanh toán điện tử
     - WebSocket realtime
     - So sánh GHN, Ahamove APIs
Ch.3 Phân tích yêu cầu (Use case, MoSCoW, NFR)
Ch.4 Thiết kế hệ thống (Kiến trúc, ER, Sequence, State machine, UI)
Ch.5 Cài đặt (Stack, từng module, VNPay, Docker)
Ch.6 Kiểm thử & đánh giá (Test plan, kết quả, security, hiệu năng)
Ch.7 Kết luận (Đạt được, hạn chế, hướng phát triển)
```

---

## 17. Glossary

| Thuật ngữ | Ý nghĩa |
|---|---|
| Bot API | API của Telegram cho server giao tiếp với bot |
| Mini App | Webview HTML/JS embed trong Telegram, gọi qua `KeyboardButton.web_app` |
| initData | Chuỗi đã ký HMAC bởi Telegram, chứa user info; backend verify bằng bot.token |
| Live Location | Tính năng Telegram cho user share vị trí cập nhật mỗi vài giây trong 1-8h |
| VNPay TmnCode | Mã website của merchant tại VNPay |
| VNPay TxnRef | Mã giao dịch unique do merchant tự sinh |
| IPN | Instant Payment Notification — VNPay gọi server-to-server xác nhận thanh toán |
| STOMP | Simple Text Oriented Messaging Protocol — protocol pub/sub trên WebSocket |
| FSM | Finite State Machine — máy trạng thái cho hội thoại đăng ký shipper |
| MoSCoW | Must / Should / Could / Won't — phương pháp phân loại ưu tiên |
| DDD-lite | Domain-Driven Design giản lược — chỉ áp dụng bounded context, không full tactical patterns |

---

**End of Spec**
