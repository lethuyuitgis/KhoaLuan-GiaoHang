# Chương 4. Cài đặt và kiểm thử

Chương này trình bày các quyết định hiện thực then chốt của hệ thống quản lý giao hàng dựa trên Telegram. Nội dung bao gồm: môi trường phát triển, cấu trúc dự án, hiện thực từng mô-đun backend, hiện thực phía frontend (Mini App và Web Admin), quản lý cơ sở dữ liệu qua Flyway migration, triển khai bằng Docker Compose, cấu hình môi trường và chiến lược kiểm thử. Tổng thể chương cho thấy hệ thống vừa được hiện thực theo đúng thiết kế đã đặt ra ở Chương 3, vừa đảm bảo tính khả chuyển và sẵn sàng triển khai (deployment-ready).

## 4.1. Môi trường phát triển

### 4.1.1. Danh sách công cụ và phiên bản

Toàn bộ hệ thống được phát triển trên môi trường thống nhất gồm các công cụ sau đây.

**Bảng 4.1. Môi trường phát triển và phiên bản công cụ**

| Công cụ | Phiên bản | Vai trò |
|---|---|---|
| JDK (Eclipse Temurin) | 17 LTS | Biên dịch và chạy backend |
| Maven Wrapper (`mvnw`) | 3.9+ | Build hệ thống multi-module backend |
| Node.js | 22 LTS | Runtime cho Vite và pnpm |
| pnpm | 10.x | Trình quản lý gói cho frontend |
| Docker Engine | 24+ | Container runtime cho local dev và prod |
| Docker Compose plugin | v2 | Điều phối multi-container |
| Git | 2.40+ | Quản lý mã nguồn |
| IntelliJ IDEA / VS Code | latest | IDE phát triển |
| PostgreSQL client (psql) | 16 | Truy vấn DB cho debug |

Việc khoanh vùng phiên bản giúp đảm bảo tính tái lập (reproducibility) — bất kỳ thành viên nào clone kho mã nguồn về cũng dựng được môi trường giống nhau.

### 4.1.2. Quy ước thư mục và mã nguồn

Mã nguồn được tổ chức theo nguyên tắc *tách rõ backend, frontend và infra*: thư mục `backend/` chứa toàn bộ mã Java (đa mô-đun Maven); thư mục `frontend/` chứa hai workspace pnpm cho Mini App và Web Admin; thư mục `infra/` chứa cấu hình Docker Compose, Nginx và file `.env.example`; thư mục `docs/` chứa toàn bộ tài liệu thiết kế và báo cáo khoá luận.

## 4.2. Cấu trúc dự án

### 4.2.1. Cây thư mục tổng thể

Cây thư mục dưới đây trình bày bố cục các thư mục cấp một của dự án.

```text
KhoaLuan-GiaoHang/
├── backend/                  # Spring Boot 3 multi-module Maven
│   ├── pom.xml               # Aggregator pom
│   ├── modules/
│   │   ├── shared/           # Events, DTO, exception, util
│   │   ├── auth/             # Telegram + JWT authentication
│   │   ├── order/            # Product, Order, OrderItem
│   │   ├── delivery/         # ShipperProfile, Assignment, Rating
│   │   ├── payment/          # VNPay signing + IPN
│   │   ├── bot/              # Telegram Bot handlers + FSM
│   │   ├── notification/     # Event listeners → bot + WebSocket
│   │   └── app/              # Executable jar + SecurityConfig
│   └── mvnw, mvnw.cmd        # Maven wrapper
├── frontend/
│   ├── pnpm-workspace.yaml
│   ├── shared/               # TS types & helpers dùng chung
│   ├── miniapp/              # Telegram Mini App (React 18 + Vite 5)
│   └── webadmin/             # Web Admin (React 18 + Tailwind)
├── infra/
│   ├── docker-compose.yml
│   ├── nginx/                # Reverse proxy config
│   └── .env.example          # Canonical manifest cho biến môi trường
├── docs/
│   ├── thesis/               # Báo cáo khoá luận (file này)
│   └── superpowers/          # Plan, research, review của 10 pha GSD
└── README.md
```

![Hình 4.1. Cây thư mục tổng thể của dự án](screenshots/admin-02-dashboard.png)

### 4.2.2. Phân chia trách nhiệm theo thư mục

Mỗi mô-đun backend là một Maven submodule độc lập, có `pom.xml` riêng, package gốc theo dạng `com.shop.delivery.<module>`. Ranh giới module được kiểm tra bằng `pom.xml` ở thư mục cha — chỉ định rõ thứ tự build và quan hệ phụ thuộc, đảm bảo đồ thị DAG được tuân thủ tại thời điểm biên dịch.

## 4.3. Cài đặt module backend

### 4.3.1. Module `shared`

Module `shared` chứa các kiểu dữ liệu dùng chung cho toàn hệ thống: chín event class (`OrderCreatedEvent`, `OrderConfirmedEvent`, `OrderAssignedEvent`, `OrderDeliveredEvent`, `OrderCancelledEvent`, `PaymentSucceededEvent`, `PaymentFailedEvent`, `LocationPingReceivedEvent`, `RatingSubmittedEvent`), lớp ngoại lệ cơ sở `DomainException`, các DTO `Money` và `GeoPoint`, cùng tiện ích `HaversineCalculator` tính khoảng cách giữa hai toạ độ. Đây là mô-đun không phụ thuộc bất kỳ mô-đun nào khác — đứng tại đáy DAG.

### 4.3.2. Module `auth`

Module `auth` hiện thực hai cơ chế xác thực song song: *Telegram initData* (HMAC-SHA256) cho Mini App và *JWT HS512* cho Web Admin. Lớp `TelegramAuthFilter` đọc header `X-Telegram-Init-Data`, parse query string, kiểm tra `auth_date` không quá 24 giờ và so sánh hash bằng `MessageDigest.isEqual`. Lớp `JwtAuthFilter` xử lý header `Authorization: Bearer <token>` với cặp access token 15 phút và refresh token 7 ngày lưu DB-backed (hash SHA-256).

### 4.3.3. Module `order`

Module `order` quản lý vòng đời đơn hàng — từ tạo `Order(PENDING)`, qua các trạng thái `CONFIRMED`, `ASSIGNED`, `DELIVERING`, đến trạng thái cuối `DELIVERED` / `CANCELLED` / `RETURNED`. Lớp `OrderStateMachine` chứa danh sách chuyển trạng thái được phép (whitelist) và từ chối các chuyển dịch trái phép bằng `IllegalStateTransitionException`. Service `PricingService` tính phí ship dựa trên `HaversineCalculator` từ module `shared`.

### 4.3.4. Module `delivery`

Module `delivery` hiện thực phần *giao hàng*: gán shipper cho đơn (`DeliveryAssignment` với FSM năm trạng thái), nhận và lưu `location_ping` từ Live Location, đánh giá shipper 1–5 sao và truy vấn báo cáo top-shipper. Một sáng kiến cụ thể trong module này là *partial unique index* `uq_assignment_shipper_started` ở Flyway V8, khoá business rule "một shipper chỉ có tối đa một assignment STARTED" tại tầng cơ sở dữ liệu.

### 4.3.5. Module `payment`

Module `payment` hiện thực luồng VNPay đầy đủ: tạo URL đã ký HMAC-SHA512, xử lý Return URL (chỉ render HTML, không cập nhật DB), xử lý IPN server-to-server với idempotency check và audit toàn bộ payload vào cột JSONB `payment_transaction.raw_payload`. `PaymentExpiryScheduler` chạy cron `fixedDelay = 60_000` để đánh dấu `Payment(PENDING)` quá 15 phút thành `FAILED`.

### 4.3.6. Module `bot`

Module `bot` đóng vai trò "facade" với Telegram. `UpdateRouter` định tuyến `Update` theo vai trò người dùng (CUSTOMER / SHIPPER / SHOP_OWNER) đến nhóm handler tương ứng. `ConversationStateService` quản lý FSM hội thoại, lưu state vào bảng `conversation_state` với payload JSONB. `BotSender` được bao bọc bởi Bucket4j rate-limit để tuân thủ giới hạn 30 message/giây của Telegram Bot API.

### 4.3.7. Module `notification`

Module `notification` là nơi tập trung mọi listener cross-module: `OrderAssignedNotifier`, `OrderLifecycleNotifier`, `RatingPromptBuilder`, `LocationBroadcaster`. Mọi listener đều dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` để đảm bảo notification chỉ phát sau khi transaction nghiệp vụ commit thành công, tránh thông báo nhầm khi DB rollback.

### 4.3.8. Module `app`

Module `app` là executable jar duy nhất — chứa `SecurityConfig` (định nghĩa filter chain), `WebSocketConfig` (cấu hình STOMP broker và channel interceptor), `application.yml` và file `application-prod.yml` áp dụng fail-fast cho secret. Khi build, `app` đóng gói tất cả jar phụ thuộc thành một fat jar khoảng 70 MB, sẵn sàng chạy bằng `java -jar app.jar`.

## 4.4. Cài đặt module frontend

### 4.4.1. Workspace pnpm và package `shared`

Frontend được tổ chức thành ba workspace pnpm: `frontend/shared` chứa các kiểu TypeScript dùng chung (`OrderDto`, `PaymentDto`, `LocationDto`) và hai helper `createVnpayPayment`, `formatVnd`; `frontend/miniapp` và `frontend/webadmin` import `@shop/shared` qua đường dẫn workspace. Cách bố trí này tránh duplicate type giữa Mini App và Web Admin, đồng thời cho phép thay đổi shape DTO ở một nơi.

### 4.4.2. Mini App (khách hàng và shipper)

Mini App được xây bằng React 18 + Vite 5 + TypeScript 5.6. SDK `@twa-dev/sdk` được dùng cho các API native (`WebApp.ready`, `WebApp.openLink`, `WebApp.themeParams`, `BackButton`). Trang chính bao gồm: Catalog, Cart, Checkout (với bản đồ Leaflet để ghim toạ độ giao), Orders, Order Detail (với marker shipper realtime) cho khách hàng; Assignments và Assignment Detail cho shipper. State giỏ hàng được lưu trữ qua Zustand kết hợp middleware `persist` vào `localStorage`.

### 4.4.3. Web Admin

Web Admin được xây bằng React 18 + Tailwind 3 + Recharts 3. Tám trang chính: Login (JWT), Dashboard (KPI + ba biểu đồ), Orders (filter + pagination), Order Detail, Products (CRUD), Shippers (duyệt + khoá), Reports (LineChart doanh thu), Settings (`shop_config`). State session lưu trong store Zustand riêng. TanStack Query 5 quản lý server state với `queryKey` phân cấp và `invalidateQueries` sau mutation.

### 4.4.4. WebSocket client

Cả Mini App và Web Admin đều dùng `@stomp/stompjs` kết hợp `sockjs-client` để kết nối đến endpoint `/ws` của backend. Header xác thực được gửi trên CONNECT frame: Mini App gửi `X-Telegram-Init-Data`, Web Admin gửi `Authorization: Bearer <jwt>`. Sau khi kết nối, Mini App subscribe `/user/queue/order/<orderId>/location` để nhận GPS ping; Web Admin subscribe `/topic/admin/orders` để nhận đơn mới và status update.

## 4.5. Cơ sở dữ liệu — Flyway migrations

### 4.5.1. Danh sách 11 file migration

Schema được quản lý hoàn toàn qua Flyway 10. Mỗi pha trong quy trình GSD đóng góp một file migration cho mô-đun của pha đó, đảm bảo schema thay đổi rõ ràng theo timeline.

**Bảng 4.2. Danh sách 11 file Flyway migration (V1 đến V11)**

| File | Nội dung chính |
|---|---|
| `V1__init.sql` | Bảng cấu hình `shop_config`, baseline schema |
| `V2__telegram_user.sql` | `telegram_user`, `user_role` |
| `V3__bot_state.sql` | `conversation_state`, `processed_update` |
| `V4__order.sql` | `product`, `orders`, `order_item`, `status_history` |
| `V5__admin.sql` | `admin_user`, `refresh_token` |
| `V6__delivery.sql` | `shipper_profile`, `delivery_assignment` |
| `V7__location_ping.sql` | `location_ping` (GPS từ Live Location) |
| `V8__assignment_uq.sql` | Partial unique index `uq_assignment_shipper_started` |
| `V9__payment.sql` | `payment`, `payment_transaction` (JSONB audit) |
| `V10__rating.sql` | `rating` (UNIQUE order_id, CHECK stars 1..5) |
| `V11__demo_seed.sql` | Demo seed cho reviewer |

### 4.5.2. Chiến lược migration

Mọi thay đổi schema đều bắt buộc thông qua một file `V<n>__<name>.sql` mới — tuyệt đối không sửa file đã apply (để giữ checksum). Trong môi trường dev, lệnh `mvn flyway:info` hiển thị trạng thái apply hiện tại; lệnh `mvn flyway:migrate` apply các migration còn thiếu. Trong môi trường prod, Flyway tự chạy lúc Spring Boot khởi động — backend không up nếu migration fail (fail-fast).

### 4.5.3. Demo seed (V11)

File `V11__demo_seed.sql` seed dữ liệu mẫu phục vụ demo: 1 admin (`admin@example.com`), 10 sản phẩm, 6 telegram user (3 khách, 2 shipper, 1 shop owner), 30 đơn rải đều 30 ngày qua, 18 assignment, 10 rating và 11 payment. Seed này giúp reviewer thấy dashboard có dữ liệu thực ngay sau khi `docker compose up`, không phải tạo từng đơn thủ công.

## 4.6. Triển khai bằng Docker Compose

### 4.6.1. Sơ đồ năm container

Hệ thống được đóng gói thành năm container chạy đồng thời, phối hợp qua `depends_on: condition: service_healthy`.

![Hình 4.2. Sơ đồ triển khai Docker Compose — 5 container](screenshots/admin-07-order-detail.png)

Năm container bao gồm: `postgres:16-alpine` với volume `pgdata` và healthcheck `pg_isready`; `backend` build từ Dockerfile đa giai đoạn (JDK 17 build → JRE alpine runtime), chạy bằng user `app` không phải root, healthcheck `/actuator/health`; `miniapp` và `webadmin` đều là image Vite-built phục vụ qua nginx alpine (~10 MB mỗi image); và `nginx` reverse proxy ở rìa, expose cổng 80/443.

### 4.6.2. Dockerfile đa giai đoạn của backend

Dockerfile của backend áp dụng *multi-stage build*: pha builder dùng image `eclipse-temurin:17-jdk` để chạy `mvn package`, pha runtime dùng `eclipse-temurin:17-jre-alpine` chỉ chứa JAR và JRE. Image cuối có dung lượng khoảng 220 MB (so với ~450 MB nếu dùng JDK đầy đủ). User non-root `app:app` được tạo và chuyển sang trước khi `ENTRYPOINT`.

### 4.6.3. Cấu hình Nginx

Nginx phục vụ ba nhiệm vụ: serve static SPA, proxy `/api/*` đến backend, và proxy `/ws/*` với header `Upgrade: websocket`. Cấu hình `proxy_read_timeout 86400` được đặt cho `/ws` để duy trì kết nối WebSocket dài. CORS được khoá theo origin của domain triển khai (không dùng wildcard `*` trong prod).

### 4.6.4. Healthcheck và thứ tự khởi động

Khi chạy `docker compose up -d`, thứ tự khởi động được điều phối: `postgres` healthy trước (pg_isready trả OK), sau đó `backend` start và đợi đến khi `/actuator/health` trả `UP` (Flyway migrate xong), tiếp theo `miniapp` và `webadmin` (HTTP HEAD `/` trả 200), cuối cùng là `nginx`. Toàn bộ thứ tự này được khai báo bằng `depends_on: condition: service_healthy` trong `docker-compose.yml`.

## 4.7. Cấu hình môi trường

### 4.7.1. File `.env.example`

File `infra/.env.example` đóng vai trò *canonical manifest* — liệt kê toàn bộ biến môi trường mà hệ thống cần. Sinh viên cần `cp .env.example .env` và điền giá trị thực tế.

**Bảng 4.3. Danh sách biến môi trường trong `.env.example`**

| Biến | Mô tả | Ví dụ |
|---|---|---|
| `POSTGRES_USER` | User PostgreSQL | `delivery` |
| `POSTGRES_PASSWORD` | Mật khẩu PostgreSQL | (random mạnh) |
| `POSTGRES_DB` | Tên database | `delivery_db` |
| `BOT_TOKEN` | Token bot Telegram lấy từ @BotFather | `123456:ABC...` |
| `BOT_USERNAME` | Tên bot, không có @ | `mydelivery_bot` |
| `JWT_SECRET` | Secret để ký JWT HS512 (≥ 32 byte) | (random mạnh) |
| `VNPAY_TMN_CODE` | Mã merchant VNPay sandbox | `CGPAY001` |
| `VNPAY_HASH_SECRET` | Secret HMAC-SHA512 VNPay | (từ portal VNPay) |
| `VNPAY_RETURN_URL` | URL Return cho khách | `https://shop.example.com/payment/return` |
| `VNPAY_IPN_URL` | URL IPN server-to-server | `https://shop.example.com/api/payment/vnpay/ipn` |
| `MINIAPP_URL` | URL Mini App | `https://shop.example.com/miniapp` |
| `WEBADMIN_URL` | URL Web Admin | `https://shop.example.com/admin` |
| `SHOP_PICKUP_LAT` | Vĩ độ điểm xuất phát | `10.762622` |
| `SHOP_PICKUP_LNG` | Kinh độ điểm xuất phát | `106.660172` |

### 4.7.2. Profile `dev` và `prod`

Spring Boot profile được kích hoạt qua `SPRING_PROFILES_ACTIVE`. Profile `dev` cho phép header `X-Dev-User-Id` để test Mini App ngoài Telegram, log level DEBUG, CORS cho phép `localhost`. Profile `prod` bật fail-fast cho secret (thiếu biến → backend exit code 1), log level INFO, CORS whitelist domain cụ thể, và ép HTTPS cho Mini App.

### 4.7.3. Bảo mật biến môi trường

File `.env` thật được liệt kê trong `.gitignore` — tuyệt đối không commit secret vào kho mã nguồn. Trong môi trường production, secret được nạp qua Docker secret hoặc một secret manager (HashiCorp Vault, AWS Secrets Manager) thay vì file phẳng. Đây là practice tối thiểu để phòng tránh leak.

## 4.8. Chiến lược kiểm thử

### 4.8.1. Thống kê 221 test backend

Backend có khoảng 150 unit test (Maven Surefire) chạy dưới 10 giây và 70 integration test (Maven Failsafe) sử dụng Testcontainers (PostgreSQL 16 thật) chạy trong khoảng 3 phút. Tỷ lệ build xanh 100% trên mỗi commit được đảm bảo bằng cách chạy `./mvnw verify` trước khi commit.

### 4.8.2. Phân loại test

Các loại test chính bao gồm: (i) *unit test* cho service và util (`HaversineCalculatorTest`, `OrderStateMachineTest`, `VnpaySignatureServiceTest`); (ii) *integration test* cho controller và webhook (`VnpayIpnIT`, `OrderControllerIT`); (iii) *security test* cho các lớp defense-in-depth (`TelegramAuthFilterTest`, `WebSocketAdminAuthIT`, `ReportServiceSqlInjectionIT`); (iv) *concurrency test* cho race condition (`AssignmentConcurrencyIT`).

### 4.8.3. Manual smoke test theo RUNBOOK

Bên cạnh test tự động, hệ thống có file `RUNBOOK.md` mô tả 8 bước smoke test thủ công (P1 → P8) chạy end-to-end qua Telegram thật và VNPay sandbox: đăng nhập admin, tạo sản phẩm, đặt đơn từ Mini App, thanh toán VNPay, gán shipper, shipper accept và share Live Location, đánh dấu đã giao, đánh giá 5 sao. Smoke test này đảm bảo hệ thống vận hành đúng trong môi trường tích hợp đầy đủ.

## 4.9. Kết luận chương

Chương 4 đã trình bày môi trường phát triển, cấu trúc dự án ba lớp (backend, frontend, infra), hiện thực tám mô-đun backend theo đúng đồ thị DAG đã thiết kế, hiện thực Mini App và Web Admin với React 18, quản lý schema qua 11 file Flyway migration, đóng gói triển khai bằng Docker Compose năm container, cấu hình biến môi trường với fail-fast cho production, và chiến lược kiểm thử với 221 test backend cùng manual smoke test theo RUNBOOK. Chương 5 tiếp theo tổng kết kết quả đạt được, các hạn chế nhận diện được và đề xuất các hướng phát triển tiếp theo.
