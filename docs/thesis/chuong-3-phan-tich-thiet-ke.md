# Chương 3. Phân tích và thiết kế hệ thống

Chương này trình bày yêu cầu, kiến trúc, thiết kế cơ sở dữ liệu, API, máy trạng thái, bốn luồng nghiệp vụ trọng tâm và thiết kế bảo mật defense-in-depth của hệ thống.

## 3.1. Phân tích yêu cầu

### 3.1.1. Yêu cầu chức năng

Hệ thống có 15 yêu cầu chức năng (Functional Requirement — FR) theo ba nhóm vai trò.

**Nhóm khách hàng.** **FR1.** Đăng nhập tự động khi mở Mini App qua xác minh `initData` ký HMAC-SHA256. **FR2.** Duyệt danh mục sản phẩm (ảnh, mô tả, giá, tình trạng còn hàng). **FR3.** Thêm, sửa, xoá sản phẩm trong giỏ, giỏ lưu `localStorage` qua Zustand. **FR4.** Đặt đơn COD (tiền mặt khi nhận) hoặc VNPay. **FR5.** Xem lịch sử và chi tiết đơn. **FR6.** Theo dõi vị trí shipper thời gian thực trên bản đồ Leaflet khi đơn ở trạng thái STARTED. **FR7.** Đánh giá shipper 1–5 sao kèm comment tuỳ chọn sau khi đơn DELIVERED, qua bot Telegram.

**Nhóm shipper.** **FR8.** Nhận hoặc từ chối offer đơn qua Telegram Bot bằng inline keyboard hai nút. **FR9.** Bấm "Bắt đầu giao" và "Đã giao" qua Mini App hoặc Bot. **FR10.** Hệ thống ghi `location_ping` mỗi khi Telegram phát Live Location của shipper trong assignment STARTED.

**Nhóm chủ shop.** **FR11.** Đăng nhập Web Admin bằng email và mật khẩu, nhận access token (JWT 15 phút) và refresh token (DB-backed 7 ngày). **FR12.** Xem dashboard KPI và ba biểu đồ Recharts (doanh thu theo ngày, top shipper, tỉ lệ huỷ). **FR13.** Quản lý đơn: xem, lọc theo trạng thái và khoảng ngày, xác nhận, gán shipper, huỷ. **FR14.** CRUD sản phẩm, quản lý shipper (duyệt, khoá, mở khoá). **FR15.** Xem báo cáo doanh thu, top shipper, tỉ lệ huỷ theo khoảng thời gian chọn trước.

### 3.1.2. Yêu cầu phi chức năng

Hệ thống có bảy yêu cầu phi chức năng (Non-Functional Requirement — NFR).

**Bảng 3.1. Bảng yêu cầu phi chức năng (NFR) kèm tiêu chí đo lường**

| Mã | Loại | Yêu cầu | Tiêu chí đo lường |
|---|---|---|---|
| NFR1 | Hiệu năng | Độ trễ tracking GPS end-to-end | < 3 giây từ Telegram phát `edited_message.location` đến lúc khách hàng thấy chấm di chuyển trên bản đồ |
| NFR2 | Hiệu năng | Thời gian phản hồi REST trung bình | < 200 ms ở trạng thái idle (P50) |
| NFR3 | Khả dụng | Uptime mong muốn | ≥ 99% (single VPS, không HA) |
| NFR4 | Bảo mật | Mọi yêu cầu thay đổi trạng thái phải xác thực | 100% endpoint thay đổi đều có filter xác thực; chỉ ba endpoint không qua filter xác thực người dùng là `/api/payment/vnpay/return` và `/api/payment/vnpay/ipn` (xác thực bằng chữ ký HMAC-SHA512 của VNPay) và `/api/bot/webhook` (xác thực bằng header secret token của Telegram) |
| NFR5 | Khả mở rộng | Số shipper tối đa | 50 (single instance), 500+ (sau khi enable ShedLock và scale ngang) |
| NFR6 | Khả dụng | Số đơn / ngày hệ thống xử lý mượt | 50–500 |
| NFR7 | Trải nghiệm | Mini App khởi động mượt | < 2 giây time-to-interactive trên thiết bị 4G phổ thông |

### 3.1.3. Sơ đồ Use Case

```mermaid
---
config:
  flowchart:
    rankSpacing: 18
    nodeSpacing: 25
    padding: 8
---
flowchart TB
  subgraph AD["Actor: Chủ shop"]
    direction TB
    UC15[UC15. Đăng nhập Web Admin]
    UC16[UC16. Xem dashboard]
    UC17[UC17. Quản lý đơn realtime]
    UC18[UC18. Gán shipper cho đơn]
    UC19[UC19. Xác nhận đơn COD]
    UC20[UC20. Huỷ đơn]
    UC21[UC21. CRUD sản phẩm]
    UC22[UC22. Quản lý và duyệt shipper]
    UC23[UC23. Xem báo cáo doanh thu]
    UC15 ~~~ UC16 ~~~ UC17 ~~~ UC18 ~~~ UC19
    UC19 ~~~ UC20 ~~~ UC21 ~~~ UC22 ~~~ UC23
  end
  subgraph SH["Actor: Shipper"]
    direction TB
    UC10[UC10. Nhận offer qua bot]
    UC11[UC11. Từ chối offer]
    UC12[UC12. Bắt đầu giao]
    UC13[UC13. Đánh dấu đã giao]
    UC14[UC14. Chia sẻ Live Location]
    UC10 ~~~ UC11 ~~~ UC12 ~~~ UC13 ~~~ UC14
  end
  subgraph KH["Actor: Khách hàng"]
    direction TB
    UC01[UC01. Đăng nhập Mini App qua Telegram]
    UC02[UC02. Duyệt danh mục sản phẩm]
    UC03[UC03. Thêm vào giỏ hàng]
    UC04[UC04. Đặt đơn COD]
    UC05[UC05. Đặt đơn VNPay]
    UC06[UC06. Xem lịch sử đơn]
    UC07[UC07. Theo dõi shipper trên bản đồ]
    UC08[UC08. Đánh giá shipper qua bot]
    UC09[UC09. Huỷ đơn]
    UC01 ~~~ UC02 ~~~ UC03 ~~~ UC04 ~~~ UC05
    UC05 ~~~ UC06 ~~~ UC07 ~~~ UC08 ~~~ UC09
  end
```

*Hình 3.1. Sơ đồ Use Case tổng thể của ba actor Khách hàng, Shipper và Chủ shop*

### 3.1.4. Đặc tả use case chi tiết

Năm use case trọng tâm — UC04 (Đặt đơn), UC10 (Nhận offer), UC12–UC14 (Giao đơn, chia sẻ Live Location), UC08 (Đánh giá shipper), UC23 (Báo cáo doanh thu) — được đặc tả theo chuẩn UML: Actor, Precondition, Luồng chính, Luồng thay thế, Postcondition. Chúng phủ cả ba actor, hai phương thức thanh toán và toàn bộ vòng đời đơn hàng. Toàn văn xem **Phụ lục A**.

## 3.2. Kiến trúc tổng thể

### 3.2.1. Sơ đồ kiến trúc tổng thể

```mermaid
graph TD
  TGUser[Khách & Shipper<br/>trên Telegram]
  Browser[Chủ shop<br/>Trình duyệt PC]
  CF[Cloudflare DNS]
  Nginx[Nginx :80/:443<br/>Reverse Proxy<br/>+ WebSocket Upgrade]
  Miniapp[Mini App SPA<br/>React 18 + Vite + Leaflet<br/>nginx alpine]
  Webadmin[Web Admin SPA<br/>React 18 + Tailwind + Recharts<br/>nginx alpine]
  Backend[Backend Spring Boot 3.4<br/>:8080<br/>REST + WS + Bot]
  Postgres[(PostgreSQL 16<br/>:5432<br/>+ pgdata volume)]
  Telegram[Telegram Cloud<br/>Bot API + Live Location]
  VNPay[VNPay Sandbox]

  TGUser --> Telegram
  Browser --> CF
  CF --> Nginx
  Nginx --> Miniapp
  Nginx --> Webadmin
  Nginx -->|/api, /ws| Backend
  Telegram <-->|long-polling getUpdates| Backend
  Backend --> Postgres
  Backend -->|sign URL| VNPay
  VNPay -->|IPN server-to-server| Nginx
  Browser -.->|VNPay Return redirect| Nginx
```

*Hình 3.2. Sơ đồ kiến trúc tổng thể hệ thống*

### 3.2.2. Modular Monolith với 8 bounded context

Hệ thống gồm 8 mô-đun Maven độc lập, phụ thuộc nhau theo đồ thị có hướng phi chu trình (DAG).

```mermaid
graph TD
  shared[shared<br/>—<br/>events, dto, exception, util]
  auth[auth<br/>—<br/>telegram_user, user_role,<br/>admin_user, refresh_token]
  order[order<br/>—<br/>product, orders, order_item,<br/>status_history]
  delivery[delivery<br/>—<br/>shipper_profile, delivery_assignment,<br/>location_ping, rating]
  payment[payment<br/>—<br/>payment, payment_transaction,<br/>VNPay signing]
  bot[bot<br/>—<br/>handlers, FSM,<br/>conversation_state, processed_update]
  notification[notification<br/>—<br/>OrderAssignedNotifier,<br/>OrderLifecycleNotifier]
  app[app<br/>—<br/>executable jar,<br/>SecurityConfig, WebSocketConfig]

  auth --> shared
  order --> shared
  order --> auth
  delivery --> shared
  delivery --> auth
  delivery --> order
  payment --> shared
  payment --> auth
  payment --> order
  bot --> shared
  bot --> auth
  bot --> order
  bot --> delivery
  notification --> shared
  notification --> bot
  app --> auth
  app --> order
  app --> delivery
  app --> payment
  app --> bot
  app --> notification
```

*Hình 3.3. Đồ thị phụ thuộc tám bounded context của Modular Monolith*

**Bảng 3.2. Bảng trách nhiệm 8 mô-đun (bounded context) của hệ thống**

| Module | Trách nhiệm chính |
|---|---|
| `shared` | Các sự kiện ứng dụng cross-module (OrderCreatedEvent, OrderConfirmedEvent...), exception cơ sở (DomainException), DTO chung, utility (Haversine, JWT helper). |
| `auth` | Đăng ký Telegram user qua `/start`, xác minh initData HMAC, quản lý vai trò CUSTOMER / SHIPPER / SHOP_OWNER, admin email + password + JWT (access 15 phút, refresh 7 ngày). |
| `order` | CRUD sản phẩm, vòng đời đơn hàng, ghi `status_history`, tính phí ship Haversine, máy trạng thái OrderStatus. |
| `delivery` | Gán shipper, máy trạng thái DeliveryAssignment, lưu `location_ping` từ Live Location, đánh giá shipper, báo cáo top-shipper. |
| `payment` | Tạo URL VNPay với HMAC-SHA512, xử lý Return URL (không cập nhật DB), xử lý IPN idempotent với audit trail JSONB. |
| `bot` | Webhook bot Telegram (chạy long-polling), UpdateRouter theo vai trò, các handler `common/customer/shipper/shopowner`, FSM `ConversationState`, BotSender bọc Bucket4j rate-limit. |
| `notification` | Lắng nghe các sự kiện cross-module bằng `@TransactionalEventListener(AFTER_COMMIT)`, gửi message qua bot và đẩy WebSocket. |
| `app` | Module thực thi — chứa `SecurityConfig`, `WebSocketConfig`, `main` method và file `application.yml`. |

### 3.2.3. Công nghệ sử dụng

Backend: Java 17 LTS, Spring Boot 3.4 (Web, Security, Data JPA, WebSocket/STOMP), Flyway 10 quản lý phiên bản schema, `telegrambots-springboot-longpolling-starter` 7.x làm client Telegram Bot API, dữ liệu trên PostgreSQL 16. Frontend: TypeScript 5.6, React 18 trên Vite 5, TanStack Query 5 (server state), Zustand 4 (UI state), TailwindCSS 3 (giao diện), Recharts 3 (biểu đồ), react-leaflet (bản đồ theo dõi). Hạ tầng: Docker Compose, Nginx 1.27 làm reverse proxy và điểm kết thúc TLS. Kiểm thử: JUnit 5, Mockito, AssertJ, Testcontainers chạy PostgreSQL 16 thật.

Bảng đối chiếu tầng – công nghệ – phiên bản – vai trò xem **Phụ lục E**.

### 3.2.4. Sơ đồ triển khai Docker Compose

```mermaid
graph LR
  subgraph DockerHost[Docker Host - 1 VPS]
    direction LR
    Nginx[nginx<br/>:80, :443<br/>healthcheck<br/>HEAD /]
    Backend[backend<br/>:8080<br/>healthcheck<br/>GET /actuator/health]
    Miniapp[miniapp<br/>:80 internal<br/>healthcheck<br/>HEAD /]
    Webadmin[webadmin<br/>:80 internal<br/>healthcheck<br/>HEAD /]
    Postgres[(postgres :5432<br/>healthcheck<br/>pg_isready)]
    pgdata[(pgdata volume)]
  end
  Postgres --- pgdata
  Backend -.->|depends_on:<br/>service_healthy| Postgres
  Nginx -.->|depends_on:<br/>service_healthy| Backend
  Nginx -.->|depends_on:<br/>service_healthy| Miniapp
  Nginx -.->|depends_on:<br/>service_healthy| Webadmin
```

*Hình 3.4. Sơ đồ triển khai năm container bằng Docker Compose*

`depends_on: condition: service_healthy` bảo đảm thứ tự khởi động: `postgres` healthy trước, `backend` đợi `/actuator/health` trả UP, rồi đến `miniapp`, `webadmin`, `nginx`.

## 3.3. Thiết kế cơ sở dữ liệu

### 3.3.1. Sơ đồ thực thể – quan hệ (ERD)

```mermaid
erDiagram
  telegram_user ||--o{ user_role : has
  telegram_user ||--o| admin_user : "links to"
  telegram_user ||--o| shipper_profile : "is"
  telegram_user ||--o{ orders : places
  telegram_user ||--o{ rating : "rates as customer"
  telegram_user ||--o{ rating : "is rated as shipper"
  telegram_user ||--o| conversation_state : "in FSM"

  admin_user ||--o{ refresh_token : has

  product ||--o{ order_item : included_in

  orders ||--|{ order_item : contains
  orders ||--o{ status_history : "has history"
  orders ||--o| delivery_assignment : "assigned to"
  orders ||--o{ payment : "paid by"
  orders ||--o| rating : "rated by"

  delivery_assignment ||--o{ location_ping : has

  payment ||--o{ payment_transaction : "audit log"

  shop_config }o--|| orders : "supplies pickup"
```

*Hình 3.5. Sơ đồ thực thể – quan hệ (ERD) của cơ sở dữ liệu*

### 3.3.2. Mô tả các bảng dữ liệu

Cơ sở dữ liệu gồm 15 bảng do 11 file Flyway migration V1–V11 tạo ra; bốn file V12–V15 thuộc phần mở rộng sau bảo vệ (Phụ lục I). Các bảng phân bổ theo năm nhóm bounded context — định danh và phân quyền, đơn hàng, giao vận, thanh toán, hội thoại bot — đúng như phân chia mô-đun ở Hình 3.3 và Bảng 3.2.

Mô tả chi tiết từng bảng (khoá chính, cột nghiệp vụ, ràng buộc CHECK/UNIQUE, index) xem **Phụ lục C**.

### 3.3.3. Quyết định thiết kế không tầm thường

**PK UUID cho `orders`, `payment`, `delivery_assignment` thay vì BIGSERIAL.** UUID v4 không tuần tự nên không thể enumerate `/api/orders/1, /api/orders/2, ...` để dò đơn của khách khác. Các bảng nội bộ `rating`, `status_history`, `location_ping` vẫn dùng BIGSERIAL vì không lộ ra URL và đã bị khoá bởi context bảng cha.

**JSONB cho audit và FSM data.** `payment_transaction.raw_payload` lưu toàn bộ map tham số VNPay, vốn có thể đổi giữa các phiên bản API mà không cần migrate schema; `conversation_state.data` lưu payload tuỳ FSM state (CUSTOMER_RATING_COMMENT lưu `{orderId}`, state đăng ký shipper lưu `{name, phone, vehicle, plate}`). Hibernate 6 hỗ trợ native qua `@JdbcTypeCode(SqlTypes.JSON)`.

**Partial unique index `uq_assignment_shipper_started` (V8).** Quy tắc "một shipper chỉ có một assignment STARTED tại một thời điểm" được khoá tại tầng DB thay vì chỉ tầng ứng dụng, vì `LiveLocationHandler` tra `DeliveryAssignment` STARTED để route ping: hai dòng STARTED do race condition sẽ làm GPS rò qua đơn của khách khác. Index từ chối dòng STARTED thứ hai bằng `DataIntegrityViolationException` để service catch và trả lỗi.

**`@Version` optimistic lock trên `orders`, `payment`, `shipper_profile`.** Ba thực thể chịu nhiều luồng đồng thời (admin xác nhận đơn + customer huỷ; VNPay IPN + cron expiry); optimistic lock đơn giản hơn pessimistic lock, retry tại tầng service nếu cần.

**Status history qua bảng audit thay vì trigger.** Trigger DB khó test, khó debug và lock-in DBMS. Service insert dòng audit cùng transaction với update trạng thái, vừa atomic vừa testable.

## 3.4. Thiết kế API

### 3.4.1. Phân vùng theo actor

**Bảng 3.3. Phân vùng URL và cơ chế xác thực**

| Phân vùng | URL prefix | Cơ chế xác thực |
|---|---|---|
| Customer | `/api/orders/*`, `/api/products/*`, `/api/payment/vnpay/create` | Telegram initData (HMAC-SHA256) |
| Shipper | `/api/shipper/*` | Telegram initData |
| Admin | `/api/admin/*` | JWT (Authorization: Bearer) |
| Public | `/api/payment/vnpay/return`, `/api/payment/vnpay/ipn` | Chữ ký HMAC-SHA512 của VNPay |
| Bot webhook | `/api/bot/webhook` | Header secret token (chỉ dùng khi `BOT_MODE=webhook`; mặc định hệ thống chạy long-polling) |

### 3.4.2. Danh sách endpoint

Nhóm khách hàng: `/api/me`, `/api/products/*` (danh mục, chi tiết sản phẩm), `/api/orders/*` (tạo đơn, lịch sử, chi tiết, huỷ, đánh giá), `/api/payment/vnpay/*` (tạo URL thanh toán, Return URL, IPN). Nhóm shipper: `/api/shipper/*` cho danh sách và chi tiết assignment, nhận, từ chối, bắt đầu, hoàn tất đơn, bật/tắt trạng thái nhận đơn. Nhóm quản trị: `/api/admin/*` cho xác thực, dashboard, quản lý đơn hàng, sản phẩm, shipper, ba báo cáo (doanh thu, top shipper, tỉ lệ huỷ), cấu hình shop.

Danh sách đầy đủ URL, method và mô tả từng endpoint xem **Phụ lục B**.

### 3.4.3. Cơ chế xác thực

**Telegram initData (Mini App).** `TelegramAuthFilter` đọc header `X-Telegram-Init-Data`, kiểm tra `auth_date` còn hạn (cấu hình 24 giờ), tính HMAC-SHA256 với khoá `HMAC("WebAppData", botToken)`, so sánh `hash` bằng `MessageDigest.isEqual` rồi set request attribute `currentUser` cho resolver `@CurrentUser`.

**JWT (Web Admin).** `JwtAuthFilter` đọc header `Authorization: Bearer <token>`; token là JWS HS512 payload `{sub: adminId, role: SHOP_OWNER, exp: ...}`, ký bằng `JWT_SECRET` từ env, hạn 15 phút. Refresh token là UUID v4 lưu ở cột `token_hash` (SHA-256, không cleartext), hạn 7 ngày.

**WebSocket dual auth.** `ChannelInterceptor` trên `inboundChannel` yêu cầu CONNECT frame có `X-Telegram-Init-Data` (Mini App) HOẶC `Authorization: Bearer <jwt>` (Web Admin); thiếu cả hai thì throw `AccessDeniedException` để Spring đóng kết nối. Trên SUBSCRIBE frame, allowlist cho customer chỉ subscribe `/user/{customerId}/queue/order/*` của chính mình, admin chỉ subscribe `/topic/admin/orders`.

**Dev-bypass header `X-Dev-User-Id`.** Chỉ active trong profile `dev`: `TelegramAuthFilter` skip xác minh HMAC và set `currentUser` theo ID truyền vào để test Mini App trên trình duyệt thường; profile `prod` bỏ qua header này.

## 3.5. Thiết kế Máy trạng thái (FSM)

### 3.5.1. OrderStatus FSM

```mermaid
stateDiagram-v2
    [*] --> PENDING : create order
    PENDING --> CONFIRMED : payment success (VNPay)<br/>or admin confirm (COD)
    PENDING --> CANCELLED : customer/admin cancel
    CONFIRMED --> ASSIGNED : shipper accepts assignment
    CONFIRMED --> CANCELLED : admin cancel
    ASSIGNED --> DELIVERING : shipper "Bắt đầu giao"
    ASSIGNED --> CANCELLED : admin cancel (kèm reason)
    DELIVERING --> DELIVERED : shipper "Đã giao"
    DELIVERING --> RETURNED : shipper "Không liên lạc được khách"
    DELIVERED --> [*]
    CANCELLED --> [*]
    RETURNED --> [*]
```

*Hình 3.6. Máy trạng thái OrderStatus — bảy trạng thái với chuyển dịch được whitelist*


### 3.5.2. DeliveryAssignment FSM

```mermaid
stateDiagram-v2
    [*] --> OFFERED : admin gán shipper
    OFFERED --> ACCEPTED : shipper accept
    OFFERED --> REJECTED : shipper reject
    OFFERED --> CANCELLED : admin huỷ trước accept
    ACCEPTED --> STARTED : shipper "Bắt đầu giao"
    ACCEPTED --> CANCELLED : admin huỷ
    STARTED --> COMPLETED : shipper "Đã giao"
    STARTED --> CANCELLED : admin huỷ
    COMPLETED --> [*]
    REJECTED --> [*]
    CANCELLED --> [*]
```

*Hình 3.7. Máy trạng thái DeliveryAssignment của phiếu gán shipper*

### 3.5.3. PaymentStatus FSM

```mermaid
stateDiagram-v2
    [*] --> PENDING : create payment (VNPay) hoặc COD
    PENDING --> SUCCESS : VNPay IPN ResponseCode=00
    PENDING --> FAILED : VNPay IPN response != 00<br/>hoặc PaymentExpiryScheduler (15p timeout)
    SUCCESS --> REFUNDED : admin xử lý hoàn tiền (manual)
    SUCCESS --> [*]
    FAILED --> [*]
    REFUNDED --> [*]
```

*Hình 3.8. Máy trạng thái PaymentStatus của giao dịch thanh toán*

### 3.5.4. ConversationState FSM (Bot)

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> CUSTOMER_RATING_COMMENT : khách bấm sao,<br/>RatingService.rate xong
    CUSTOMER_RATING_COMMENT --> IDLE : khách gõ text (lưu comment)<br/>hoặc /skip
```

*Hình 3.9. Máy trạng thái ConversationState của hội thoại bot*

### 3.5.5. Ma trận chuyển trạng thái

Cả bốn máy trạng thái đều theo nguyên tắc whitelist: mỗi phép chuyển được khai báo tường minh kèm sự kiện kích hoạt và điều kiện tiền đề, phép chuyển ngoài danh sách bị tầng service từ chối thay vì âm thầm ghi vào cơ sở dữ liệu. Các trạng thái kết thúc (`DELIVERED`, `CANCELLED`, `RETURNED`, `COMPLETED`, `REJECTED`) do đó là trạng thái hấp thụ, không thể bị đưa ngược về trạng thái đang xử lý.

Ba ma trận chuyển trạng thái đầy đủ của `OrderStatus`, `DeliveryAssignment` và `PaymentStatus` xem **Phụ lục D**. Riêng `ConversationState` không cần ma trận vì mỗi luồng hội thoại chỉ là một chuỗi tuyến tính hai trạng thái (`IDLE` ⇄ state đang chờ nhập), đã thể hiện trọn vẹn trên Hình 3.9.

## 3.6. Thiết kế luồng nghiệp vụ chính

### 3.6.1. Đặt đơn COD

```mermaid
sequenceDiagram
    autonumber
    actor Khach as Khách (Mini App)
    participant BE as Backend (Spring)
    participant DB as PostgreSQL
    participant WS as STOMP /topic/admin/orders
    participant Bot as Telegram Bot
    participant Admin as Web Admin

    Khach->>BE: POST /api/orders<br/>{items, delivery, COD}<br/>+ X-Telegram-Init-Data
    BE->>BE: TelegramAuthFilter verify HMAC
    BE->>BE: PricingService.calc<br/>(Haversine + base + perKm)
    BE->>DB: BEGIN, INSERT orders, order_item,<br/>status_history(null→PENDING)
    DB-->>BE: OK
    BE->>BE: publish OrderCreatedEvent
    BE->>DB: COMMIT
    Note over BE: TransactionalEventListener<br/>AFTER_COMMIT
    BE->>WS: SimpMessagingTemplate<br/>convertAndSend /topic/admin/orders
    WS-->>Admin: realtime push (đơn mới)
    BE->>Bot: BotSender.send (shop owner)
    Bot-->>Bot: tin nhắn "🔔 Đơn mới DH..."
    BE-->>Khach: 201 + OrderResponse
```

*Hình 3.10. Sequence luồng đặt đơn COD — từ Mini App đến Web Admin thời gian thực*


### 3.6.2. Đặt đơn VNPay

```mermaid
sequenceDiagram
    autonumber
    actor Khach as Khách (Mini App)
    participant BE as Backend
    participant DB as PostgreSQL
    participant VNPay
    participant Browser as Browser/WebView

    Khach->>BE: POST /api/orders {items, VNPAY}
    BE->>DB: INSERT orders(PENDING)
    BE-->>Khach: 201 OrderResponse
    Khach->>BE: POST /api/payment/vnpay/create<br/>{orderId}
    BE->>DB: INSERT payment(PENDING)<br/>vnp_txn_ref UNIQUE
    BE->>BE: VnpaySignatureService<br/>HMAC-SHA512 sign params
    BE-->>Khach: {paymentUrl}
    Khach->>Khach: WebApp.openLink(paymentUrl)
    Khach->>VNPay: chuyển hướng đến VNPay sandbox
    VNPay-->>Khach: form OTP, NCB card
    Khach->>VNPay: nhập OTP
    par Return URL (browser)
        VNPay->>Browser: 302 → /api/payment/vnpay/return<br/>?vnp_SecureHash=...
        Browser->>BE: GET /api/payment/vnpay/return
        BE->>BE: verify HMAC<br/>(MessageDigest.isEqual)
        BE-->>Browser: HTML "Thanh toán thành công"<br/>(KHÔNG cập nhật DB)
    and IPN (server-to-server)
        VNPay->>BE: POST /api/payment/vnpay/ipn
        BE->>BE: verify HMAC
        BE->>DB: SELECT payment WHERE vnp_txn_ref
        DB-->>BE: payment(PENDING)
        BE->>DB: BEGIN, UPDATE payment status=SUCCESS<br/>INSERT payment_transaction(IPN, raw_payload JSONB)
        BE->>BE: publish PaymentSucceededEvent
        BE->>DB: COMMIT
        Note over BE: TransactionalEventListener<br/>REQUIRES_NEW
        BE->>DB: BEGIN (new tx)<br/>UPDATE orders SET status=CONFIRMED<br/>INSERT status_history(PENDING→CONFIRMED)
        BE-->>VNPay: {RspCode: "00", Message: "Confirm Success"}
    end
```

*Hình 3.11. Sequence luồng đặt đơn VNPay theo mẫu IPN-as-source-of-truth*

### 3.6.3. Gán shipper và giao đơn

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant BE as Backend
    participant DB
    participant Bot as Telegram Bot
    actor Shipper
    participant WS as STOMP

    Admin->>BE: POST /api/admin/orders/{id}/assign<br/>{shipperId}
    BE->>DB: INSERT delivery_assignment(OFFERED)
    BE->>BE: publish OrderAssignedEvent
    BE-->>Admin: 200
    Note over BE: AFTER_COMMIT
    BE->>Bot: BotSender.send (shipper)
    Bot-->>Shipper: "📦 Đơn DH..., Phí 25k<br/>[Nhận] [Từ chối]"
    Shipper->>Bot: bấm "Nhận"
    Bot->>BE: Update callback OFFER_ACCEPT:xxx<br/>(long-polling getUpdates, hoặc webhook)
    BE->>DB: SELECT delivery_assignment FOR UPDATE
    BE->>DB: UPDATE assignment status=ACCEPTED<br/>UPDATE orders status=ASSIGNED
    BE->>WS: /topic/admin/orders (status change)
    BE->>Bot: notify khách "Shipper X đã nhận"
    Shipper->>BE: POST /api/shipper/assignments/{id}/start
    BE->>DB: UPDATE assignment STARTED<br/>(partial unique index check)<br/>UPDATE orders DELIVERING
    Shipper->>Bot: share Live Location 1h
    loop mỗi 5-10 giây
        Bot->>BE: Update.edited_message.location
        BE->>BE: LiveLocationHandler<br/>tra assignment STARTED của shipper
        BE->>DB: INSERT location_ping
        BE->>BE: publish LocationPingReceivedEvent
        BE->>WS: /user/{customerId}/queue/order/{id}/location
    end
    Shipper->>BE: POST /api/shipper/assignments/{id}/complete
    BE->>DB: UPDATE assignment COMPLETED<br/>UPDATE orders DELIVERED<br/>shipper_profile.total_deliveries += 1
    BE->>Bot: notify khách + prompt rating
```

*Hình 3.12. Sequence luồng gán shipper và giao đơn đến khi hoàn tất*


### 3.6.4. Đánh giá shipper

```mermaid
sequenceDiagram
    autonumber
    actor Khach
    participant Bot
    participant BE as Backend
    participant DB

    Note over Bot: order DELIVERED
    Bot-->>Khach: "Đơn đã giao. Đánh giá?<br/>[1⭐][2⭐][3⭐][4⭐][5⭐][Bỏ qua]"
    Khach->>Bot: bấm 5⭐
    Bot->>BE: callback RATE:orderId:5
    BE->>DB: INSERT rating(order_id, customer_id, shipper_id, stars=5)<br/>(UNIQUE order_id chống trùng)
    BE->>DB: SELECT AVG(stars), COUNT(*) FROM rating<br/>WHERE shipper_id=?
    BE->>DB: UPDATE shipper_profile<br/>SET rating_avg=?, rating_count=?
    BE->>Bot: editMessageReplyMarkup (remove keyboard)
    Bot-->>Khach: "Cảm ơn bạn đã đánh giá 5 sao!"
    BE->>DB: UPSERT conversation_state<br/>state=CUSTOMER_RATING_COMMENT<br/>data={orderId}
    Bot-->>Khach: "Nhập nhận xét (hoặc /skip):"
    alt khách gõ text
        Khach->>Bot: "Shipper rất nhanh và lịch sự"
        Bot->>BE: text incoming
        BE->>BE: RatingCommentHandler (@Order 0)
        BE->>DB: UPDATE rating SET comment=?<br/>DELETE conversation_state
        Bot-->>Khach: "Đã lưu nhận xét."
    else khách gõ /skip
        Khach->>Bot: /skip
        BE->>DB: DELETE conversation_state
        Bot-->>Khach: "OK, bỏ qua nhận xét."
    end
```

*Hình 3.13. Sequence luồng đánh giá shipper qua bot Telegram*

## 3.7. Thiết kế bảo mật

Hệ thống áp dụng *defense-in-depth*: nhiều lớp phòng thủ song song thay vì một cơ chế duy nhất, mỗi lớp đối phó một loại đe doạ cụ thể và có ít nhất một regression test bảo đảm.

### 3.7.1. Mô hình đe doạ (Threat Model) và 11 lớp đối phó

**Bảng 3.4. Mô hình đe doạ và 11 lớp đối phó (defense-in-depth)**

| Lớp | Đe doạ | Cơ chế đối phó | Vị trí trong code |
|---|---|---|---|
| L1 | Giả mạo Telegram identity | `TelegramAuthFilter` verify HMAC initData | `auth/config/TelegramAuthFilter` |
| L2 | Hijack phiên admin | `JwtAuthFilter` (TTL 15p) + refresh token DB-backed | `auth/config/JwtAuthFilter` |
| L3 | Endpoint chưa được phân quyền | `SecurityConfig` filter chain allowlist | `app/config/SecurityConfig` |
| L4 | Privilege escalation | `@PreAuthorize` mức controller | toàn bộ controller `/admin/*` |
| L5 | Forge `customerId` qua path | Custom `@CurrentUser` resolver thay vì `@PathVariable` | `auth/api/CurrentUser` |
| L6 | Anonymous WebSocket connect | CONNECT frame interceptor dual auth | `app/config/WebSocketAuthInterceptor` |
| L7 | Cross-user data leak qua broadcast | SUBSCRIBE frame allowlist `/user/*`, `/queue/*` | `app/config/WebSocketAuthInterceptor` |
| L8 | Signature spoof + timing attack | VNPay HMAC `MessageDigest.isEqual` constant-time | `payment/service/VnpaySignatureService` |
| L9 | SQL injection ở Reports | `groupBy` enum whitelist trước `DATE_TRUNC` bind | `delivery/service/ReportService` |
| L10 | Payment replay | `vnp_TxnRef UNIQUE` + audit trail `payment_transaction` | `payment/service/VnpayIpnService` |
| L11 | Gán nhầm vị trí cho shipper khác | Partial unique index `uq_assignment_shipper_started` (V8) | `db/migration/V8` |

### 3.7.2. Ma trận đối chiếu (mitigation matrix)

**Bảng 3.5. Ma trận đối chiếu STRIDE — lớp đối phó — test bao phủ** [21], [22]

| Loại đe doạ | Lớp đối phó | Test bao phủ |
|---|---|---|
| Spoofing identity | L1, L2, L5 | `TelegramAuthFilterTest`, `JwtAuthFilterTest` |
| Tampering | L8, L9, L10 | `VnpaySignatureServiceTest`, `ReportServiceSqlInjectionIT` |
| Repudiation | L10 (audit trail) | `VnpayIpnAuditIT` |
| Information disclosure | L4, L5, L7 | `WebSocketAdminAuthIT`, `OrderAccessControlIT` |
| Denial of service | Rate-limit Bucket4j (cơ chế bổ trợ, không thuộc 11 lớp) | `BotRateLimitTest` |
| Elevation of privilege | L3, L4 | `AdminEndpointSecurityIT` |
| Race condition (ngoài sáu loại STRIDE) | L11 | `AssignmentConcurrencyIT` |

### 3.7.3. Audit trail

Hai bảng audit cung cấp khả năng truy vết hậu kỳ. **`payment_transaction`** (V9) ghi mỗi event VNPay (CREATE / IPN / RETURN) kèm `raw_payload JSONB` chứa toàn bộ payload đến; kể cả khi chữ ký invalid payload vẫn được ghi để phục vụ điều tra — phát hiện từ code review pha P7, đã vá hậu kỳ. **`status_history`** (V4) insert dòng `(from, to, changed_by_user_id, changed_at, note)` cùng transaction với mỗi lần chuyển trạng thái, là nguồn render timeline trong Order Detail (hướng phát triển).

## 3.8. Kết luận chương

Chương 3 đã trình bày yêu cầu chức năng và phi chức năng, kiến trúc Modular Monolith 8 mô-đun, cơ sở dữ liệu 15 bảng, API 39 endpoint theo ba phân vùng xác thực, bốn máy trạng thái hữu hạn (`OrderStatus`, `DeliveryAssignment`, `PaymentStatus`, `ConversationState`), bốn luồng nghiệp vụ trọng tâm và thiết kế bảo mật defense-in-depth mười một lớp — cơ sở để Chương 4 trình bày quyết định hiện thực, công cụ phát triển và phương án triển khai bằng Docker Compose.
