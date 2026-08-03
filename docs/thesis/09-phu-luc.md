<!--
  PHỤ LỤC của báo cáo khoá luận — ghép sau 08-tai-lieu-tham-khao.md.
  Phần này KHÔNG tính vào giới hạn 80 trang theo Phụ lục 16.3 của Trường ĐH Mở Hà Nội.
  Bảng trong phụ lục đánh số Bảng PL.n, hình đánh số Hình PL.n.
-->

# PHỤ LỤC

Phần phụ lục tập hợp các tài liệu tra cứu chi tiết được tham chiếu từ các chương chính:
đặc tả use case đầy đủ, danh sách endpoint, mô tả từng bảng dữ liệu, ma trận chuyển trạng thái,
danh mục công nghệ, cấu hình triển khai, thống kê kiểm thử, bộ ảnh giao diện và hai pha mở rộng
nghiệp vụ thực hiện sau thời điểm bảo vệ.

\newpage

## PHỤ LỤC A. ĐẶC TẢ USE CASE CHI TIẾT

Năm use case quan trọng nhất được đặc tả theo chuẩn UML: Actor, Precondition, Luồng chính, Luồng thay thế, Postcondition.

#### UC04. Đặt đơn (Khách hàng)

- **Actor:** Khách hàng.
- **Precondition:** Khách đã đăng nhập Mini App (initData hợp lệ); giỏ hàng có ≥ 1 sản phẩm; `shop_config` có toạ độ điểm xuất phát.
- **Luồng chính:**
  1. Khách vào trang Checkout từ giỏ hàng.
  2. Nhập địa chỉ giao, ghim toạ độ trên bản đồ Leaflet.
  3. Nhập số điện thoại liên hệ và ghi chú (tuỳ chọn).
  4. Hệ thống tính phí ship theo Haversine: `distance_km = haversine(pickup, delivery)`, `delivery_fee = base_fee + max(0, distance_km - free_km) × fee_per_km`.
  5. Chọn phương thức thanh toán (COD hoặc VNPay).
  6. Bấm "Xác nhận".
  7. Nếu COD: tạo Order PENDING, phát `OrderCreatedEvent`, bot gửi notification đến chủ shop, WebSocket đẩy đơn mới lên Web Admin realtime.
  8. Nếu VNPay: tạo Order PENDING và Payment PENDING, ký URL VNPay, trả `paymentUrl`; Mini App mở URL bằng `WebApp.openLink`.
- **Luồng thay thế:**
  - 4a. Khoảng cách vượt bán kính giao → trả lỗi `OUT_OF_DELIVERY_RANGE` 422.
  - 6a. Thiếu thông tin bắt buộc → frontend hiển thị validation error, không gọi API.
  - 8a. Khách thoát Mini App giữa luồng VNPay → Order vẫn PENDING; sau 15 phút `PaymentExpiryScheduler` đánh dấu Payment FAILED.
- **Postcondition:** Order đã persist; thông báo đã gửi đến chủ shop.

#### UC10. Nhận offer (Shipper)

- **Actor:** Shipper.
- **Precondition:** Shipper đã đăng ký và được duyệt (role SHIPPER, status ACTIVE); chủ shop vừa gán đơn cho shipper.
- **Luồng chính:**
  1. Backend tạo `DeliveryAssignment` status OFFERED.
  2. Bot gửi shipper message kèm inline keyboard `[Nhận đơn]` và `[Từ chối]`.
  3. Shipper bấm "Nhận đơn"; bot gửi callback `OFFER_ACCEPT:<assignmentId>`.
  4. Backend kiểm tra: shipper không có assignment STARTED nào khác; assignment vẫn OFFERED.
  5. Backend chuyển assignment → ACCEPTED, order → ASSIGNED.
  6. Bot báo khách "Shipper [Tên] đã nhận đơn", gửi WebSocket update lên Web Admin.
- **Luồng thay thế:**
  - 4a. Shipper đã có assignment STARTED (partial unique index `uq_assignment_shipper_started` từ chối) → bắt `DataIntegrityViolationException`, trả lỗi "Bạn đang giao một đơn khác".
  - 4b. Assignment đã được shipper khác accept (race condition) → trả lỗi "Đơn đã có shipper khác nhận".
- **Postcondition:** Order ASSIGNED; assignment ACCEPTED; khách nhận thông báo.

#### UC12 + UC13 + UC14. Giao đơn và chia sẻ Live Location (Shipper)

- **Actor:** Shipper.
- **Precondition:** Assignment ở trạng thái ACCEPTED; shipper đến điểm xuất phát.
- **Luồng chính:**
  1. Shipper bấm "Bắt đầu giao" trên Mini App.
  2. Backend chuyển assignment ACCEPTED → STARTED, order ASSIGNED → DELIVERING.
  3. Bot gửi shipper hướng dẫn share Live Location.
  4. Shipper share Live Location qua Telegram (đính kèm → Location → Share Live Location for 1 hour).
  5. Telegram bắn `Update.message.location` đến webhook; `LiveLocationHandler` lưu `location_ping` đầu tiên và phát `LocationPingReceivedEvent`.
  6. Mỗi 5–10 giây, Telegram bắn `Update.edited_message.location`; backend lưu ping mới và phát event.
  7. `LocationBroadcaster` (TransactionalEventListener AFTER_COMMIT) đẩy `LocationDto` qua STOMP đến `/user/{customerId}/queue/order/{orderId}/location`.
  8. Mini App khách nhận message, cập nhật marker shipper trên Leaflet map.
  9. Shipper đến nơi, bấm "Đã giao".
  10. Backend chuyển assignment STARTED → COMPLETED, order DELIVERING → DELIVERED, `shipper_profile.total_deliveries += 1`, `shipper_profile.current_state = AVAILABLE`.
  11. Bot gửi khách inline keyboard 5 sao để đánh giá.
- **Luồng thay thế:**
  - 4a. Shipper không share Live Location → hệ thống vẫn chạy, chỉ thiếu bản đồ realtime; khách vẫn nhận cập nhật trạng thái.
  - 9a. Shipper báo "Không liên lạc được khách" → assignment STARTED → CANCELLED, order DELIVERING → CANCELLED kèm lý do.
- **Postcondition:** Order DELIVERED; shipper trở về AVAILABLE; khách nhận prompt đánh giá.

#### UC08. Đánh giá shipper (Khách hàng)

- **Actor:** Khách hàng.
- **Precondition:** Order ở trạng thái DELIVERED; chưa có rating cho order này (UNIQUE constraint trên `rating.order_id`).
- **Luồng chính:**
  1. Bot gửi inline keyboard 5 sao kèm nút "Bỏ qua".
  2. Khách bấm số sao N (1 ≤ N ≤ 5); phát callback `RATE:<orderId>:<N>`.
  3. `RatingService.rate` insert dòng `rating(order_id, customer_id, shipper_id, stars=N)`.
  4. Service recompute `shipper_profile.rating_avg = AVG(stars)` và `rating_count = COUNT(*)` từ aggregate query trên toàn bộ rating của shipper.
  5. Bot edit message gốc — xoá inline keyboard, hiển thị "Cảm ơn bạn đã đánh giá N sao!".
  6. Bot prompt nhập comment kèm gợi ý gõ `/skip`.
  7. `ConversationStateService` chuyển state khách sang CUSTOMER_RATING_COMMENT, lưu `{orderId: ...}` vào `conversation_state.data` JSONB.
  8. Nếu khách gõ text: `RatingCommentHandler` (Order 0) bắt text trước handler khác, update `rating.comment`, clear FSM.
  9. Nếu khách gõ `/skip`: clear FSM, không update gì.
- **Luồng thay thế:**
  - 3a. Rating đã tồn tại (UNIQUE violation) → bot trả "Bạn đã đánh giá đơn này rồi".
- **Postcondition:** Rating được lưu; `shipper_profile.rating_avg` đã cập nhật; FSM clear.

#### UC23. Xem báo cáo doanh thu (Chủ shop)

- **Actor:** Chủ shop.
- **Precondition:** Chủ shop đã đăng nhập Web Admin (JWT hợp lệ).
- **Luồng chính:**
  1. Chủ shop vào trang Reports, chọn khoảng ngày `from`/`to` và `groupBy` ∈ {day, week, month}.
  2. Web Admin gọi `GET /api/admin/reports/revenue?from=...&to=...&groupBy=day`.
  3. Backend validate `groupBy` ∈ whitelist (chống SQL injection — lớp L9).
  4. Backend chạy query `generate_series + LEFT JOIN orders WHERE status='DELIVERED'` với `DATE_TRUNC(:groupBy, created_at)`, điền bucket trống bằng 0.
  5. Backend trả `[{ bucket: '2026-05-19', revenue: 1250000 }, ...]`.
  6. Web Admin render LineChart Recharts.
- **Luồng thay thế:**
  - 3a. `groupBy` ngoài whitelist → trả 400 `INVALID_GROUP_BY`.
- **Postcondition:** Chủ shop thấy biểu đồ doanh thu.

\newpage

## PHỤ LỤC B. DANH SÁCH ENDPOINT REST

**Bảng PL.1. Danh sách đầy đủ endpoint REST của hệ thống**

| URL | Method | Auth | Mô tả |
|---|---|---|---|
| `/api/me` | GET | initData | Trả thông tin user và vai trò |
| `/api/products` | GET | initData | Danh mục sản phẩm active |
| `/api/products/{id}` | GET | initData | Chi tiết sản phẩm |
| `/api/orders` | POST | initData | Tạo đơn (COD hoặc VNPay) |
| `/api/orders/mine` | GET | initData | Lịch sử đơn của khách (paginated) |
| `/api/orders/{id}` | GET | initData | Chi tiết đơn |
| `/api/orders/{id}/cancel` | POST | initData | Khách huỷ đơn (PENDING/CONFIRMED) |
| `/api/orders/{id}/rating` | POST | initData | Đánh giá shipper |
| `/api/payment/vnpay/create` | POST | initData | Tạo URL VNPay đã ký |
| `/api/payment/vnpay/return` | GET | (chữ ký) | VNPay redirect khách về |
| `/api/payment/vnpay/ipn` | POST | (chữ ký) | VNPay server-to-server notify |
| `/api/shipper/assignments` | GET | initData | Danh sách đơn đang giao |
| `/api/shipper/assignments/{id}` | GET | initData | Chi tiết assignment |
| `/api/shipper/assignments/{id}/accept` | POST | initData | Nhận đơn |
| `/api/shipper/assignments/{id}/reject` | POST | initData | Từ chối đơn |
| `/api/shipper/assignments/{id}/start` | POST | initData | Bắt đầu giao |
| `/api/shipper/assignments/{id}/complete` | POST | initData | Đã giao |
| `/api/shipper/profile/state` | POST | initData | Toggle AVAILABLE / OFFLINE |
| `/api/admin/auth/login` | POST | (public) | Login email + password |
| `/api/admin/auth/refresh` | POST | refresh token | Cấp lại access token |
| `/api/admin/auth/logout` | POST | JWT | Revoke refresh token |
| `/api/admin/dashboard/summary` | GET | JWT | KPI tổng quan |
| `/api/admin/orders` | GET | JWT | Danh sách đơn (filter + paginate) |
| `/api/admin/orders/{id}` | GET | JWT | Chi tiết đơn (admin view) |
| `/api/admin/orders/{id}/confirm` | POST | JWT | Xác nhận đơn COD |
| `/api/admin/orders/{id}/assign` | POST | JWT | Gán shipper |
| `/api/admin/orders/{id}/cancel` | POST | JWT | Huỷ đơn (kèm lý do) |
| `/api/admin/products` | GET, POST | JWT | CRUD sản phẩm |
| `/api/admin/products/{id}` | PUT, DELETE | JWT | Update / xoá sản phẩm |
| `/api/admin/shippers` | GET | JWT | Danh sách shipper |
| `/api/admin/shippers/{id}` | GET | JWT | Chi tiết shipper |
| `/api/admin/shippers/{id}/approve` | POST | JWT | Duyệt shipper PENDING |
| `/api/admin/shippers/{id}/block` | POST | JWT | Khoá shipper |
| `/api/admin/shippers/{id}/unblock` | POST | JWT | Mở khoá shipper |
| `/api/admin/reports/revenue` | GET | JWT | Báo cáo doanh thu |
| `/api/admin/reports/top-shippers` | GET | JWT | Top shipper theo doanh thu |
| `/api/admin/reports/cancellation` | GET | JWT | Tỉ lệ huỷ |
| `/api/admin/settings` | GET, PUT | JWT | shop_config |

\newpage

## PHỤ LỤC C. MÔ TẢ CHI TIẾT CÁC BẢNG DỮ LIỆU

Hệ thống có 15 bảng chính, tạo bởi 11 file Flyway migration. Mô tả ngắn gọn từng bảng:

- **`telegram_user`** (V2). Người dùng Telegram. PK = `id` (Telegram user ID, BIGINT). Cột chính: `username`, `first_name`, `phone` (lấy qua `KeyboardButton.request_contact`), `is_blocked` (true khi Telegram trả 403). Index `idx_telegram_user_username WHERE username IS NOT NULL`.

- **`user_role`** (V2). Liên kết user với vai trò CUSTOMER / SHIPPER / SHOP_OWNER. UNIQUE `(telegram_user_id, role)`. `status` ∈ {ACTIVE, PENDING, BLOCKED} (chủ yếu cho luồng duyệt shipper).

- **`admin_user`** (V5). Tài khoản Web Admin. `email` UNIQUE, `password_hash` BCrypt-10. Có thể link sang `telegram_user_id` để bot gửi notification cho chủ shop.

- **`refresh_token`** (V5). Refresh token JWT 7 ngày, lưu `token_hash` (SHA-256, không cleartext). `revoked` để soft-delete sau logout.

- **`product`** (V4). Sản phẩm. `price NUMERIC(12,2) CHECK (price >= 0)`, `stock INT CHECK (stock >= 0)`. Partial index `idx_product_active WHERE is_active = TRUE`.

- **`orders`** (V4). Đơn hàng. PK = UUID (chống enumeration). `code` UNIQUE dạng `"DH<yyyyMMdd>-<seq>"`. Snapshot khi tạo: `customer_name`, `customer_phone`, `pickup_lat/lng` (từ `shop_config`), `delivery_lat/lng`, `distance_km`, `delivery_fee`, `total`. `payment_status` denormalized từ `payment.status`. `version` optimistic lock. Composite index `(status, created_at DESC)` và `(customer_id, created_at DESC)`.

- **`order_item`** (V4). Dòng sản phẩm trong đơn. `unit_price` snapshot từ `product.price` khi tạo (tránh giá đổi sau ảnh hưởng đơn cũ). `quantity > 0` CHECK.

- **`status_history`** (V4). Audit log mỗi lần đổi trạng thái đơn — `(from_status, to_status, changed_by_user_id, changed_at, note)`. Insert tại tầng service, không dùng trigger DB.

- **`shipper_profile`** (V6). Hồ sơ shipper. PK = `user_id` (FK `telegram_user`). `vehicle_type` ∈ {MOTORBIKE, CAR, BICYCLE}, `current_state` ∈ {AVAILABLE, BUSY, OFFLINE}. `rating_avg NUMERIC(3,2)`, `rating_count INT` recompute từ aggregate query (không incremental math).

- **`delivery_assignment`** (V6). Gán shipper cho đơn. PK = UUID. `order_id` UNIQUE — một đơn một assignment. `status` ∈ {OFFERED, ACCEPTED, STARTED, COMPLETED, REJECTED, CANCELLED}. Cột timestamp: `assigned_at`, `accepted_at`, `started_at`, `delivered_at`, `cancelled_at`, `rejected_at`.

- **`uq_assignment_shipper_started`** (V8). Partial UNIQUE index `ON delivery_assignment(shipper_id) WHERE status = 'STARTED'` — ràng buộc "một shipper tối đa một assignment đang chạy" tại tầng DB.

- **`location_ping`** (V7). Ping GPS từ Telegram Live Location. `lat/lng NUMERIC(10,7)`, `accuracy`, `heading`. Composite index `(assignment_id, recorded_at DESC)` hỗ trợ query "Polyline đường đi".

- **`payment`** (V9). Một order có thể nhiều payment (khách thử lại sau fail). PK UUID. `vnp_txn_ref VARCHAR(64) UNIQUE` — `"<orderCode>-<epochMs>"`, chống replay. `version` optimistic lock.

- **`payment_transaction`** (V9). Audit log mọi sự kiện VNPay (CREATE / IPN / RETURN). `raw_payload JSONB NOT NULL` — toàn bộ payload kể cả invalid signature, phục vụ forensics.

- **`rating`** (V10). Đánh giá shipper. `order_id` UNIQUE — chống trùng. `stars SMALLINT CHECK (stars BETWEEN 1 AND 5)`. Index `(shipper_id, created_at DESC)` cho trang Shipper Detail.

- **`conversation_state`** (V3). FSM hội thoại bot. PK = `telegram_user_id`. `state VARCHAR(64)`, `data JSONB`. TTL 30 phút (cron xoá stale, hướng phát triển).

- **`processed_update`** (V3). Idempotency cho bot — `update_id` PK, chống Telegram retry duplicate.

\newpage

## PHỤ LỤC D. MA TRẬN CHUYỂN TRẠNG THÁI CỦA CÁC MÁY TRẠNG THÁI

**Bảng PL.2. Ma trận chuyển trạng thái `OrderStatus`**

| Từ → Đến | Sự kiện kích hoạt | Điều kiện |
|---|---|---|
| PENDING → CONFIRMED | Payment SUCCESS / Admin confirm | Đơn còn hiệu lực |
| PENDING → CANCELLED | Customer cancel / Admin cancel | — |
| CONFIRMED → ASSIGNED | Shipper accept | Có DeliveryAssignment cho đơn |
| CONFIRMED → CANCELLED | Admin cancel | Kèm reason |
| ASSIGNED → DELIVERING | Shipper "Bắt đầu giao" | Assignment chuyển STARTED đồng thời |
| ASSIGNED → CANCELLED | Admin cancel | Kèm reason |
| DELIVERING → DELIVERED | Shipper "Đã giao" | Assignment chuyển COMPLETED |
| DELIVERING → RETURNED | Shipper báo không liên lạc được | — |

**Bảng PL.3. Ma trận chuyển trạng thái `DeliveryAssignment`**

| Từ → Đến | Sự kiện |
|---|---|
| OFFERED → ACCEPTED | `OFFER_ACCEPT` callback |
| OFFERED → REJECTED | `OFFER_REJECT` callback |
| OFFERED → CANCELLED | Admin cancel đơn |
| ACCEPTED → STARTED | `/api/shipper/assignments/{id}/start` |
| ACCEPTED → CANCELLED | Admin cancel |
| STARTED → COMPLETED | `/api/shipper/assignments/{id}/complete` |
| STARTED → CANCELLED | Admin cancel |

**Bảng PL.4. Ma trận chuyển trạng thái `PaymentStatus`**

| Từ → Đến | Sự kiện |
|---|---|
| PENDING → SUCCESS | IPN ResponseCode=00, amount khớp |
| PENDING → FAILED | IPN ResponseCode!=00 hoặc PaymentExpiryScheduler |
| SUCCESS → REFUNDED | Admin xử lý refund (chưa hiện thực, hướng phát triển) |

\newpage

## PHỤ LỤC E. DANH MỤC CÔNG NGHỆ VÀ PHIÊN BẢN

**Bảng PL.5. Danh mục công nghệ sử dụng và phiên bản tham chiếu**

| Tầng | Công nghệ | Phiên bản |
|---|---|---|
| Ngôn ngữ backend | Java (Microsoft OpenJDK) | 17.0.18 LTS |
| Build tool backend | Maven Wrapper (`mvnw`) | 3.9.x |
| Framework backend | Spring Boot | 3.4.0 |
| Bảo mật | Spring Security | 6.4.x (Spring Boot managed) |
| ORM | Hibernate ORM | 6.6.x (Spring Boot managed) |
| Migration | Flyway | 10.x (Spring Boot managed) |
| Telegram SDK | telegrambots-spring-boot-starter | 6.9.7 |
| WebSocket | Spring WebSocket + STOMP | 6.2.x |
| Test framework | JUnit Jupiter | 5.11.x |
| Test mocking | Mockito Core | 5.x |
| Test container | Testcontainers PostgreSQL | 1.20.4 |
| Cơ sở dữ liệu | PostgreSQL | 16-alpine |
| Ngôn ngữ frontend | TypeScript | 5.6.x |
| Runtime frontend | Node.js | 22 LTS |
| Build tool frontend | Vite | 5.x |
| Trình quản lý gói frontend | pnpm | 10.x |
| Thư viện UI | React | 18.3.x |
| Trạng thái client | Zustand | 4.5.x |
| Truy vấn server-state | TanStack Query | 5.x |
| Biểu đồ | Recharts | 3.x |
| Bản đồ | Leaflet + react-leaflet | 1.9 / 4.x |
| WebSocket client | @stomp/stompjs + sockjs-client | 7.x / 1.6.x |
| Telegram SDK Mini App | @twa-dev/sdk | 7.x |
| Validation | Jakarta Bean Validation | 3.x |
| Routing frontend | React Router | 6.x |
| HTTP client | Axios | 1.x |
| Định dạng ngày | date-fns (locale vi) | 3.x |
| Reverse proxy | nginx | 1.27-alpine |
| Container runtime | Docker Engine | 24+ |
| Container orchestration | Docker Compose plugin | v2 |
| IDE khuyến nghị | IntelliJ IDEA Ultimate / VS Code | latest |
| Quản lý mã nguồn | Git | 2.40+ |

\newpage

## PHỤ LỤC F. CẤU HÌNH TRIỂN KHAI

### F.1. Danh sách file Flyway migration

**Bảng PL.6. Danh sách 15 file Flyway migration (V1 đến V15)**

| Version | Tên file | Mô-đun đóng góp | Tạo bảng / chỉ mục chính |
|---|---|---|---|
| V1 | `init.sql` | shared | baseline encoding + extension |
| V2 | `auth.sql` | auth | `admin_user`, `refresh_token` |
| V3 | `bot.sql` | bot, auth | `telegram_user`, `user_role`, `conversation_state`, `processed_update` |
| V4 | `order.sql` | order | `product`, `orders`, `order_item`, `status_history` |
| V5 | `admin.sql` | auth | Seed một admin đầu tiên `admin@shop.local` |
| V6 | `delivery.sql` | delivery | `shipper_profile`, `delivery_assignment` |
| V7 | `location.sql` | delivery | `location_ping` (GPS từ Live Location) |
| V8 | `assignment_unique_started.sql` | delivery | Partial UNIQUE INDEX `uq_assignment_shipper_started` |
| V9 | `payment.sql` | payment | `payment`, `payment_transaction` (JSONB) |
| V10 | `rating.sql` | delivery | `rating` (UNIQUE order_id, CHECK stars 1..5) |
| V11 | `demo_seed.sql` | (tất cả) | Seed demo cho reviewer |
| V12 | `shipper_rating.sql` | delivery | `shipper_rating` + cột `rating_avg`, `rating_count` ở `telegram_user` |
| V13 | `shop_config.sql` | order | `shop_config` (singleton row, brand + pickup + fee) |
| V14 | `voucher.sql` | promotion | `voucher`, `voucher_redemption` + cột `discount_products`/`discount_shipping`/`delivery_fee_original` ở `orders` |
| V15 | `shipper_ledger.sql` | delivery | `shipper_ledger` + cột `shipper_commission_pct` ở `shop_config` và `shipper_commission` ở `orders` |

### F.2. Mô tả chi tiết từng migration

**V1 — `init.sql`** thiết lập baseline: tạo `app_meta(key, value, updated_at)` lưu metadata phiên bản và bật Flyway version tracking.

**V2 — `auth.sql`** tạo hai bảng xác thực Web Admin `admin_user` (email UNIQUE, password_hash BCrypt) và `refresh_token` (token_hash SHA-256, expires_at, revoked_at). Cột `token_hash` lưu hash thay vì token gốc — nếu DB bị leak, attacker không dùng lại được token.

**V3 — `bot.sql`** tạo bốn bảng luồng bot Telegram: `telegram_user` (telegram_user_id UNIQUE); `user_role` (role, status) cho phép một user nhiều vai trò (khách + shipper); `conversation_state` (current_state, payload JSONB) lưu FSM hội thoại; `processed_update` (update_id PRIMARY KEY) đảm bảo idempotency.

**V4 — `order.sql`** tạo bốn bảng nghiệp vụ đơn hàng. `orders.status` dùng `VARCHAR(20)` với CHECK constraint enum thay vì PostgreSQL ENUM type — dễ thêm trạng thái mới mà không phải `ALTER TYPE`. Index `idx_orders_status_created` cho dashboard truy vấn theo trạng thái và khoảng ngày; `status_history` append-only phục vụ audit trail.

**V5 — `admin.sql`** seed admin đầu tiên (`admin@shop.local`, password BCrypt cost 10) — chỉ cho dev, production cần thay password ngay sau deploy.

**V6 — `delivery.sql`** tạo `shipper_profile` và `delivery_assignment`; bảng sau có `expires_at` cho offer TTL (mặc định 60 giây) và `route_polyline` lưu encoded polyline lộ trình.

**V7 — `location.sql`** tạo `location_ping` (lat, lng, accuracy, heading, ping_at) với index BRIN trên `ping_at` (thay BTREE) — phù hợp timeseries append-only, tiết kiệm 95% disk space.

**V8 — `assignment_unique_started.sql`** tạo partial unique index khoá business rule "mỗi shipper tối đa một assignment STARTED" — defense-in-depth: kể cả khi code Java có race condition, DB vẫn từ chối INSERT.

**V9 — `payment.sql`** tạo `payment` (order_id UNIQUE, vnp_TxnRef UNIQUE, amount, status, gateway, paid_at, expired_at) và `payment_transaction` (event_type, raw_payload JSONB). Cột JSONB lưu nguyên payload IPN VNPay phục vụ forensics — phát hiện từ code review P7.

**V10 — `rating.sql`** tạo `rating` (order_id UNIQUE, shipper_id, customer_id, stars CHECK 1..5, comment). UNIQUE order_id chống đánh giá trùng.

**V11 — `demo_seed.sql`** seed dữ liệu mẫu cho reviewer: một admin (`shop@example.com`, password `Demo@Shop2026!`), mười sản phẩm món ăn, sáu telegram user (ba khách, hai shipper, một shop owner), ba mươi đơn rải đều ba mươi ngày với phân bố trạng thái thực tế (60% DELIVERED, 20% ASSIGNED/STARTED, 10% CANCELLED, 10% PENDING/CONFIRMED), mười tám assignment, mười rating và mười một payment — giúp Dashboard có ngay dữ liệu hiển thị KPI và biểu đồ.

```sql
-- Trích V11: seed sản phẩm
INSERT INTO product (id, code, name, price, description, image_url, available)
VALUES (1, 'PHO-BO', 'Phở bò tái', 55000,
        'Phở bò Hà Nội truyền thống, nước dùng đậm đà, thịt tái mềm.',
        '/images/pho-bo.jpg', true);
```

**V12 — `shipper_rating.sql`** bổ sung khả năng shipper đánh giá khách (chiều ngược của V10): tạo `shipper_rating(id, order_id UNIQUE, shipper_id, customer_id, stars CHECK 1..5, comment, rated_at)` và thêm hai cột `rating_avg NUMERIC(3,2)`, `rating_count INT` vào `telegram_user`. Đánh giá chiều này không công khai — chỉ admin xem được, phục vụ cảnh báo khách khó tính cho shipper khác.

**V13 — `shop_config.sql`** tạo bảng `shop_config` lưu cấu hình "đổi tại runtime" dưới dạng *singleton row* — `id SMALLINT PRIMARY KEY CHECK (id = 1)` ràng buộc luôn chỉ đúng một dòng. Bốn nhóm cột: brand (`name`, `tagline`, `logo_url`, `brand_primary VARCHAR(7)`, `brand_secondary VARCHAR(7)`, contact), pickup (`pickup_lat NUMERIC(10,7)`, `pickup_lng`, `pickup_address` — thay hardcode toạ độ Hoàn Kiếm trong YAML), phí giao (`fee_base NUMERIC(12,2)`, `fee_per_km`, `free_km NUMERIC(8,3)`) và `updated_at`. Migration `INSERT … ON CONFLICT DO NOTHING` cho `id=1` với default trùng giá trị YAML hiện hành, nên hệ thống boot ra hành vi không khác trước đến khi admin chỉnh qua trang Settings (4.4.3) — nền tảng để Mini App "đổi áo" theo từng shop mà không cần redeploy.

### F.3. Cổng và URL exposed

**Bảng PL.7. Cổng và URL exposed trong môi trường production-like**

| Cổng / URL | Hướng truy cập | Dịch vụ phục vụ |
|---|---|---|
| `80:80` (host) | Public | Nginx — entry point duy nhất |
| `backend:8080` | Internal | Spring Boot REST + WebSocket |
| `postgres:5432` | Internal | PostgreSQL 16 |
| `miniapp:80` | Internal | Nginx serving Mini App SPA |
| `webadmin:80` | Internal | Nginx serving Web Admin SPA |
| `http://localhost/miniapp` | Public | Mini App entry (dev / Telegram WebApp URL) |
| `http://localhost/admin` | Public | Web Admin entry |
| `http://localhost/api/*` | Public | REST API (qua nginx) |
| `http://localhost/ws/*` | Public | WebSocket STOMP (qua nginx) |
| `http://localhost/api/payment/vnpay/ipn` | Public (whitelist VNPay IP) | IPN callback VNPay |

### F.4. Cấu trúc file `.env.example` và tài khoản demo

File `.env.example` ở thư mục gốc (Docker Compose nạp qua `../.env`) chia thành sáu nhóm. Bốn biến bắt buộc đánh dấu `▼ FILL IN ▼`: `BOT_TOKEN` (từ BotFather), `BOT_USERNAME` (không kèm `@`), `JWT_SECRET` (sinh bằng `openssl rand -hex 32`, tối thiểu 32 byte) và cặp `VNPAY_TMN_CODE` / `VNPAY_HASH_SECRET` từ portal sandbox VNPay. Các biến còn lại có mặc định khả dụng: `DB_HOST=postgres`, `DB_PORT=5432`, `DB_NAME=shop_delivery`, `DB_USER=app`, `DB_PASSWORD=app_demo_password`, `BOT_MODE=polling`, cùng các tham số shop (`SHOP_PICKUP_LAT=21.0285`, `SHOP_PICKUP_LNG=105.8542`, `SHOP_FEE_BASE=15000`, `SHOP_FEE_PER_KM=5000`, `SHOP_FEE_FREE_KM=1.0`).

Sau khi V11 seed apply, hệ thống có tài khoản admin demo `shop@example.com` với password `Demo@Shop2026!` (12 ký tự, đủ chữ hoa/thường/số/ký tự đặc biệt), cần đổi trước khi giao Mini App cho khách hàng cuối.

### F.5. Profile `dev` và `prod`

Profile Spring Boot kích hoạt qua `SPRING_PROFILES_ACTIVE`. Profile `dev` (mặc định khi chạy local) cho phép header `X-Dev-User-Id` để test Mini App ngoài Telegram trên trình duyệt thường (header này bị silently ignored ở prod), log level DEBUG, CORS mở cho `localhost:5173` và `localhost:5174`. Profile `prod` (trong Docker Compose) bật fail-fast cho secret: thiếu `BOT_TOKEN`, `JWT_SECRET` hay `VNPAY_HASH_SECRET`, Spring Boot throw `BeanCreationException` và exit code 1, tránh deploy với credential test còn sót; log level INFO, CORS whitelist theo domain, bắt buộc HTTPS cho Mini App URL.

### F.6. Biến môi trường VNPay

Bốn biến VNPay nạp riêng để cô lập credential cổng thanh toán: `VNPAY_TMN_CODE` (mã merchant, public), `VNPAY_HASH_SECRET` (khoá ký HMAC-SHA512, không leak), `VNPAY_RETURN_URL` (khách redirect về sau thanh toán, không cập nhật cơ sở dữ liệu), `VNPAY_IPN_URL` (VNPay gọi server-to-server, nguồn sự thật cập nhật `Payment.status`). Dev local không có public IP cần tunnel (ngrok, cloudflared) để nhận IPN: `VNPAY_IPN_URL=https://<tunnel-id>.ngrok.app/api/payment/vnpay/ipn`.

\newpage

## PHỤ LỤC G. THỐNG KÊ KIỂM THỬ

**Bảng PL.8. Phân bổ test tự động theo mô-đun**

| Module | Unit test | Integration test | Tổng |
|---|---|---|---|
| shared | 17 | 0 | 17 |
| auth | 29 | 2 | 31 |
| order | 32 | 2 | 34 |
| delivery | 47 | 6 | 53 |
| payment | 33 | 2 | 35 |
| bot | 61 | 2 | 63 |
| notification | 12 | 0 | 12 |
| app | 0 | 8 | 8 |
| **Tổng backend** | **231** | **22** | **253** |
| @shop/shared | 9 | 0 | 9 |
| @shop/miniapp | 6 | 0 | 6 |
| @shop/webadmin | 6 | 0 | 6 |
| **Tổng frontend** | **21** | **0** | **21** |
| **Tổng dự án** | **252** | **22** | **274** |

\newpage

## PHỤ LỤC H. BỘ ẢNH GIAO DIỆN HỆ THỐNG

Phụ lục này tập hợp ảnh chụp toàn bộ màn hình của ba kênh giao diện — Web Admin dành cho chủ shop,
Telegram Mini App dành cho khách hàng và Telegram Mini App dành cho shipper — theo đúng trạng thái
hệ thống tại thời điểm bảo vệ.

![Hình PL.1. Web Admin — màn hình đăng nhập với JWT (L2 trong defense-in-depth)](screenshots/admin-01-login.png){width=13cm}

![Hình PL.2. Web Admin — danh sách đơn với filter trạng thái và pagination](screenshots/admin-03-orders.png){width=13cm}

![Hình PL.3. Web Admin — báo cáo doanh thu với DATE_TRUNC bucket + Recharts (L9 whitelist groupBy)](screenshots/admin-04-reports.png){width=13cm}

![Hình PL.4. Web Admin — quản lý sản phẩm CRUD](screenshots/admin-05-products.png){width=13cm}

![Hình PL.5. Web Admin — danh sách và duyệt shipper, dữ liệu shipper_profile + user_role](screenshots/admin-06-shippers.png){width=13cm}

![Hình PL.6. Web Admin — chi tiết đơn, hiển thị status_history và assignment](screenshots/admin-07-order-detail.png){width=13cm}

![Hình PL.7. Mini App khách — danh mục sản phẩm (catalog)](screenshots/miniapp-cust-01-catalog.png){width=7cm}

![Hình PL.8. Mini App khách — giỏ hàng trạng thái rỗng](screenshots/miniapp-cust-02-cart-empty.png){width=7cm}

![Hình PL.9. Mini App khách — lịch sử đơn của khách hàng](screenshots/miniapp-cust-03-orders.png){width=7cm}

![Hình PL.10. Mini App khách — danh mục với badge "giỏ hàng có sản phẩm"](screenshots/miniapp-cust-04-catalog-with-cart.png){width=7cm}

![Hình PL.11. Mini App khách — giỏ hàng đã có sản phẩm, đầy đủ giá và phí](screenshots/miniapp-cust-05-cart-filled.png){width=7cm}

![Hình PL.12. Mini App khách — màn hình Checkout với form địa chỉ, bản đồ và chọn phương thức thanh toán](screenshots/miniapp-cust-06-checkout.png){width=7cm}

![Hình PL.13. Mini App khách — chi tiết đơn với bản đồ Leaflet + marker shipper + Polyline](screenshots/miniapp-cust-07-order-detail.png){width=7cm}

![Hình PL.14. Mini App shipper — danh sách assignment đang nhận hoặc đang giao](screenshots/miniapp-ship-01-assignments.png){width=7cm}

![Hình PL.15. Mini App shipper — chi tiết assignment với nút "Bắt đầu giao" và "Đã giao"](screenshots/miniapp-ship-02-assignment-detail.png){width=7cm}

![Hình PL.16. Mini App — môi trường dev qua Cloudflare Tunnel HTTPS để test Telegram thật](screenshots/miniapp-tunnel-via-https.png){width=7cm}

\newpage

## PHỤ LỤC I. HAI PHA MỞ RỘNG NGHIỆP VỤ SAU BẢO VỆ

Hai pha mở rộng dưới đây được thực hiện sau thời điểm bảo vệ, không thuộc phạm vi cam kết ban đầu
của đề tài; nội dung được đưa vào phụ lục để minh chứng kiến trúc Modular Monolith cho phép bổ sung
ngữ cảnh nghiệp vụ mới mà không phải viết lại phần đã có.

Chương này mô tả hai pha P14 và P15 — bổ sung hai nhóm tính năng khép kín vòng đời thương mại của hệ thống: *khuyến mãi (voucher)* để hấp dẫn khách và *hoa hồng + theo dõi thu nhập* để hỗ trợ shipper như một người lao động thực thụ. Khác với các pha P0–P13 trình bày phân tán trong Chương 3, 4, 5 theo lát cắt kiến trúc / cài đặt / kết quả, hai pha cuối được gom vào một chương riêng vì chúng (i) chạm nhiều module cùng lúc, đáng được mô tả như một câu chuyện tích hợp, (ii) đại diện cho hai *tính năng tiêu chuẩn của mọi nền tảng giao hàng thương mại VN*, và (iii) là phần demo được kỳ vọng tạo ấn tượng nhất với hội đồng. Cả hai đều tuân theo quy trình GSD (Brainstorm → Spec → Plan → Execute với subagent-driven review hai vòng) và đã được merge vào nhánh `main`.

### I.1. Bối cảnh và động lực

Trước hai pha này, hệ thống đã đáp ứng đầy đủ tám yêu cầu *Must have* và ba yêu cầu *Should have* trong phân loại MoSCoW gốc (Chương 1, mục 1.3). Tuy nhiên khi review thiết kế với giảng viên hướng dẫn, hai khoảng trống nghiệp vụ được nhận diện: thứ nhất, hệ thống chưa có cơ chế khuyến mãi (voucher) — tính năng tiêu chuẩn của mọi nền tảng giao hàng VN hiện nay; thứ hai, vai trò shipper trong Mini App mới chỉ "nhận và giao đơn" nhưng chưa "theo dõi thu nhập" — chưa thực sự hỗ trợ shipper như một người lao động chứ không chỉ là một role kỹ thuật.

Hai khoảng trống này thuộc nhóm *Could have* trong MoSCoW gốc nhưng được tái phân loại lên *Should have* sau phản hồi rằng thiếu chúng sẽ làm phần demo "kém thuyết phục về tính khả thi thương mại". Hai pha P14 và P15 lần lượt được brainstorm, lập plan và hiện thực trong tuần đầu tháng 6 năm 2026.

### I.2. Phase 14 — Hệ thống voucher (khuyến mãi)

#### I.2.1. Tổng quan tính năng

Phase 14 hiện thực một hệ thống voucher đầy đủ chức năng theo mô hình *code-based redemption* phổ biến tại VN (giống cách Shopee, Tiki, Be áp voucher): chủ shop tạo mã giảm giá qua Web Admin, khách hàng gõ mã trong Mini App ở bước Checkout, hệ thống xác thực + tính giảm + redeem một lượt duy nhất. Bốn quyết định thiết kế cốt lõi đã được chốt qua brainstorming:

- **Hai kiểu giảm**: số tiền cố định (FIXED — vd "giảm 20 000đ") hoặc phần trăm có trần (PERCENT với `max_discount` cap — vd "giảm 20% tối đa 50 000đ"). Cấu trúc này phủ hầu hết mô hình giảm giá thực tế tại VN mà không phải mở rộng schema sau này.
- **Hai đối tượng áp**: voucher tác động lên *phí ship* (target = `SHIPPING`) hoặc *tiền hàng* (target = `PRODUCTS`). Một đơn được phép stack *tối đa một voucher mỗi loại* — luật stacking này khớp với Shopee/Be, vừa hấp dẫn khách vừa giới hạn rủi ro lạm dụng.
- **Constraints đầy đủ**: mỗi voucher có khoảng hiệu lực (`valid_from`/`valid_until`), đơn tối thiểu (`min_order_amount`), tổng số lần dùng (`max_uses_total`) và số lần dùng tối đa mỗi khách (`max_uses_per_customer`). Sáu rule này được kiểm theo thứ tự fail-fast trong `VoucherService.validate`, mỗi rule trả về một mã lý do từ chối riêng (`NOT_FOUND`, `EXPIRED`, `BELOW_MIN_ORDER`, `EXHAUSTED_TOTAL`, `EXHAUSTED_PER_CUSTOMER`, `WRONG_TARGET`) để frontend hiển thị thông báo rõ ràng.
- **Race condition handling**: khi hai khách cùng tiêu lần dùng cuối của một voucher, hệ thống dùng `SELECT … FOR UPDATE` (pessimistic lock) trong transaction redeem, đảm bảo đúng một khách thành công — verify bằng `VoucherRaceConditionIT` chạy hai thread song song trên Testcontainers Postgres.

#### I.2.2. Tổ chức module backend

Một module Maven mới tên `promotion` được thêm vào kiến trúc Modular Monolith ở Chương 3 — đưa tổng số bounded context từ tám lên chín. Module giữ nguyên cấu trúc gói chuẩn (`entity`, `domain`, `repository`, `service`, `api`) và phụ thuộc một chiều vào `shared` + `auth`, không chạm `order` ở compile time. Để tránh chu trình phụ thuộc khi `OrderService.create` cần áp voucher, một SPI (Service Provider Interface) tên `VoucherApplicator` được định nghĩa trong module `order` và được `promotion` cài đặt — Spring tự nối ở runtime. Pattern này giữ nguyên nguyên tắc DAG của Chương 3: chỉ `promotion → order`, không có chiều ngược.

Hai bảng mới được Flyway tạo trong migration V14 (`backend/app/src/main/resources/db/migration/V14__voucher.sql`): bảng `voucher` lưu định nghĩa từng mã, bảng `voucher_redemption` (UNIQUE `voucher_id, order_id`) là log append-only mỗi lần áp. Cùng V14, bảng `orders` được bổ sung ba cột — `discount_products`, `discount_shipping` và `delivery_fee_original`. Cột cuối là *snapshot phí ship gốc trước khi voucher SHIPPING giảm*, được Phase 15 dùng làm cơ sở tính hoa hồng cho shipper (xem 6.3.2).

#### I.2.3. Giao diện admin và customer

Web Admin được bổ sung ba trang mới (`VouchersPage`, `VoucherFormPage`, `VoucherDetailPage`) cùng mục "Khuyến mãi" trong sidebar. Trang quản lý gồm bảng danh sách kèm bộ lọc theo trạng thái (active/expired) và đối tượng (SHIPPING/PRODUCTS); form tạo có 11 trường (mã, tên, đối tượng, kiểu giảm, giá trị, cap, đơn tối thiểu, hai mốc hiệu lực, tổng số lần dùng, số lần dùng mỗi khách) cùng preview tính trực tiếp "đơn 200 000đ → giảm X đ" giúp admin kiểm tra trước khi lưu; trang chi tiết hiển thị các thông số cùng lịch sử redemption gần nhất.

Hình PL.17, 6.2 và 6.3 minh hoạ ba trang quản lý voucher của Web Admin:

![Hình PL.17. Web Admin — danh sách voucher với filter trạng thái và đối tượng](screenshots/admin-09-vouchers-list.png){width=13cm}

![Hình PL.18. Web Admin — form tạo voucher với preview giảm giá theo thời gian thực](screenshots/admin-10-voucher-form.png){width=13cm}

![Hình PL.19. Web Admin — chi tiết voucher với thông số và lịch sử áp dụng](screenshots/admin-11-voucher-detail.png){width=13cm}

Trang Checkout của Mini App được mở rộng với section "Khuyến mãi" đặt giữa thông tin người nhận và phương thức thanh toán. Hai ô nhập tách bạch (PRODUCTS và SHIPPING) giúp khách hiểu rõ mã nào áp vào phần nào — khác với UI của Shopee/Tiki (gộp một ô để khách "thử" lần lượt), thiết kế hai ô minh bạch tránh bối rối khi mã thứ hai bị từ chối do trùng target. Khi áp thành công, một chip xanh `✓ <code> — Giảm <số tiền>` hiển thị kèm nút "Gỡ"; hai dòng giảm trừ xuất hiện trong phần tổng kết đơn ngay phía trên section.

![Hình PL.20. Mini App — Checkout với hai voucher GIAM20K + FREESHIP đã được áp](screenshots/miniapp-cust-08-checkout-voucher.png){width=7cm}

#### I.2.4. Testing và metrics

Phase 14 đóng góp 21 test mới vào bộ test suite tổng:

- **8 unit test** cho `VoucherCalculator` (pure function tính discount) — cover FIXED, PERCENT, cap, rounding HALF_UP, edge cases (đơn = 0, voucher 200% bị cap về tiền hàng).
- **6 integration test** trên Testcontainers Postgres cho `VoucherService` — cover từng rule trong sáu rule validation và happy path redeem.
- **1 race condition test** mô phỏng hai thread cùng redeem voucher có `max_uses_total=1` — verify đúng một thread thành công.
- **2 stacking test** cho `VoucherApplicator` SPI — verify một SHIPPING + một PRODUCTS được phép, hai PRODUCTS thì bị từ chối với mã `WRONG_TARGET`.
- **2 frontend Vitest test** cho component `VoucherInput` (loading state, error display).

Toàn bộ Phase 14 được thực hiện qua **27 commit** atomic trên nhánh `feat/phase-14-voucher` rồi fast-forward merge vào `main`. Quy trình GSD áp dụng đầy đủ: brainstorming (13 quyết định đã chốt qua hỏi đáp tương tác), spec (`docs/superpowers/specs/2026-06-03-voucher-shipper-commission-design.md`), plan chi tiết 28 task (`docs/superpowers/plans/2026-06-03-phase-14-voucher.md`), và execute qua subagent-driven development với hai vòng review (spec compliance + code quality) sau mỗi task.

### I.3. Phase 15 — Hoa hồng và thu nhập shipper

#### I.3.1. Tổng quan tính năng

Phase 15 hiện thực luồng *thanh toán hoa hồng cho shipper* và *báo cáo thu nhập* — tính năng không thể thiếu của bất kỳ nền tảng giao hàng nào có người lao động thực thụ. Khi đơn chuyển sang trạng thái `DELIVERED`, hệ thống tự động:

1. Tính hoa hồng shipper bằng công thức `delivery_fee_original × shipper_commission_pct / 100` (làm tròn `HALF_UP` về VND).
2. Ghi snapshot hoa hồng vào cột mới `orders.shipper_commission`.
3. Append một dòng `COMMISSION` (số dương) vào bảng *ledger* mới của shipper.
4. Nếu đơn là COD, append thêm một dòng `COD_OWED` (số âm bằng `-order.total`) — biểu diễn việc shipper đang giữ tiền của shop. Đơn VNPay thì bỏ qua bước này vì tiền đã vào tài khoản shop từ lúc tạo đơn.

Tỉ lệ hoa hồng mặc định 80% được lưu trong `shop_config.shipper_commission_pct` (mới thêm cùng V15) — admin có thể chỉnh ngay trên trang Settings. Bốn quyết định thiết kế cốt lõi của Phase 15 cũng được chốt qua brainstorming với người hướng dẫn:

- **Cơ sở tính hoa hồng**: dùng `delivery_fee_original` (phí ship trước khi voucher SHIPPING giảm) chứ KHÔNG dùng `delivery_fee` cuối cùng. Lý do: voucher SHIPPING là chi phí khuyến mãi do shop chịu để thu hút khách — không nên "phạt oan" shipper bằng cách trừ vào hoa hồng. Đây cũng là cách Grab và Be vận hành thực tế tại VN, và là điểm kết nối quan trọng giữa Phase 14 và Phase 15.
- **Ledger thay cho balance đơn giản**: thay vì lưu một con số "số dư" trên `shipper_profile`, hệ thống dùng mô hình *append-only ledger* lấy cảm hứng từ kế toán cổ điển — mỗi giao dịch (commission, COD đã thu, payout, deposit) là một dòng riêng, số dư là tổng `SUM(amount)`. Lợi ích: audit trail đầy đủ, đối chiếu được từng đơn, không mất dữ liệu khi rollback transaction, và demo được "shipper xem lịch sử thu nhập từng đơn".
- **Bốn loại entry**: `COMMISSION` (+), `COD_OWED` (−), `SETTLEMENT_PAYOUT` (− khi shop trả lương), `SETTLEMENT_DEPOSIT` (+ khi shipper nộp COD lại cho shop). Quy ước dấu: số dương = shop nợ shipper, số âm = shipper nợ shop. Admin thực hiện hai loại settlement cuối qua một modal trong `ShipperDetailPage` của Web Admin.
- **Idempotency tuyệt đối**: listener kiểm tra `existsByOrderIdAndEntryType(orderId, COMMISSION)` trước khi tạo entry — phòng khi `OrderDeliveredEvent` được phát lại (event replay) hoặc đơn được mark delivered hai lần do race condition. Hành vi này được verify bằng test `duplicate_event_does_not_double_commission` trong `OrderCompletionListenerIT`.

#### I.3.2. Listener pattern và transactional event

Cơ chế kích hoạt hoa hồng được hiện thực bằng `OrderCompletionListener` đặt trong module `delivery`, dùng pattern `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` đã được Chương 4 mô tả ở module `notification`. Listener chạy sau khi transaction mark-delivered commit thành công, trong một transaction mới (`Propagation.REQUIRES_NEW`) — đảm bảo:

- Nếu mark-delivered rollback vì lỗi, listener KHÔNG chạy → không có entry hoa hồng cho đơn không thực sự DELIVERED.
- Nếu listener fail (vd: DB constraint vi phạm), KHÔNG ảnh hưởng đến transaction mark-delivered đã commit → đơn vẫn DELIVERED đúng nghiệp vụ, lỗi listener chỉ log warning để admin can thiệp.

Sơ đồ luồng đơn giản hoá khi đơn DELIVERED:

```
shipper Mini App: "Đã giao" → AssignmentService.markDelivered(orderId)
   ├── Update orders.status = 'DELIVERED', delivered_at = NOW()
   ├── Publish OrderDeliveredEvent(orderId, shipperId, orderCode, ...)
   └── COMMIT transaction
        ↓ @TransactionalEventListener(AFTER_COMMIT)
        ↓ @Transactional(REQUIRES_NEW)
OrderCompletionListener.onDelivered(event)
   ├── (Idempotent) Skip if existsByOrderIdAndEntryType(orderId, COMMISSION)
   ├── Load order → read delivery_fee_original + total + payment_method
   ├── Load shop_config → read shipper_commission_pct
   ├── commission = CommissionCalculator(deliveryFeeOriginal, pct)
   ├── orders.shipper_commission = commission; save
   ├── INSERT shipper_ledger (COMMISSION, +commission, order_id)
   └── if paymentMethod == COD:
          INSERT shipper_ledger (COD_OWED, -order.total, order_id)
```

Năm test integration trong `OrderCompletionListenerIT` (Testcontainers Postgres) kiểm tra: (i) đơn COD tạo đúng 2 entry, (ii) đơn VNPay chỉ tạo 1 entry COMMISSION, (iii) commission tính trên `delivery_fee_original` chứ không phải `delivery_fee` (verify bằng đơn có voucher SHIPPING giảm 20k), (iv) idempotency khi phát event hai lần, (v) snapshot `orders.shipper_commission` được lưu đúng giá trị.

#### I.3.3. Giao diện shipper trong Mini App

Phần shipper của Mini App được tái thiết kế đáng kể để tương xứng với một ứng dụng giao hàng "chuyên nghiệp". Bốn trang mới được tổ chức dưới một `ShipperLayout` có bottom tab nav cố định (Earnings / Đơn / Ví / Profile) — pattern UI phổ biến của các app giao hàng lớn (Grab, Be, GoJek). Hai trang cũ (`ShipperAssignmentsPage`, `ShipperAssignmentDetailPage`) cũng được redesign khớp tinh thần mới: mỗi assignment giờ là một card có status tag màu, mã đơn font monospace, và badge "+X đ dự kiến" tính sẵn 80% phí ship để shipper biết trước hoa hồng kỳ vọng.

**Trang Earnings (Hình PL.21)** là điểm vào mới của shipper sau khi đăng nhập (thay vì `ShipperAssignmentsPage` cũ). Hero card hiển thị thu nhập hôm nay bằng font lớn cùng số đơn đã giao; ba KPI tile bên dưới tóm tắt thu nhập tuần / tháng / số dư ví; một biểu đồ cột Recharts 7 ngày qua cho shipper nhìn trực quan biến động thu nhập — một động lực kiểu *gamification* khuyến khích nhận đơn nhiều hơn.

![Hình PL.21. Mini App shipper — trang Earnings với hero card, 3 KPI và biểu đồ thu nhập 7 ngày](screenshots/miniapp-ship-03-earnings.png){width=7cm}

**Trang Wallet (Hình PL.22)** trình bày số dư hiện tại của shipper kèm danh sách giao dịch ledger có thể lọc theo loại (Tất cả / Hoa hồng / COD đã thu / Shop trả lương / Đã nộp tiền). Bốn loại entry hiển thị với màu khác nhau (dương màu xanh lá, âm màu đỏ) giúp shipper hiểu ngay tình trạng đối soát — *audit trail* mà mô hình ledger mang lại, không thể có với một con số balance đơn lẻ.

![Hình PL.22. Mini App shipper — trang Wallet với balance + lọc loại giao dịch](screenshots/miniapp-ship-04-wallet.png){width=7cm}

**Trang Profile (Hình PL.23)** hiển thị thông tin cá nhân của shipper (avatar gradient theo brand color, tên, số điện thoại) cùng hai chỉ số quan trọng: điểm đánh giá trung bình (từ V12) và tổng số đơn đã giao. Đây là phiên bản đầu tiên — phiên bản tương lai sẽ thêm KYC documents (giấy phép lái xe, biển số) và chức năng đổi mật khẩu.

![Hình PL.23. Mini App shipper — trang Profile với rating và tổng đơn đã giao](screenshots/miniapp-ship-05-profile.png){width=7cm}

#### I.3.4. Giao diện admin trong Web Admin

Phía admin có ba thay đổi: hai trang mới và ba trang được mở rộng.

**Trang `ShipperEarningsPage` (Hình PL.24)** là báo cáo thu nhập shipper theo khoảng ngày tuỳ chọn, có ba KPI tổng (tổng hoa hồng đã chi trả, số đơn DELIVERED, số shipper hoạt động) và hai biểu đồ Recharts: bar chart top 5 shipper kiếm nhiều nhất, line chart hoa hồng theo ngày. Endpoint `/api/admin/reports/shipper-earnings?from=&to=&groupBy=shipper|day` dùng `DATE_TRUNC('day', created_at)` của Postgres + `FILTER (WHERE entry_type='COMMISSION')` để aggregate ở DB layer, tránh tải toàn bộ ledger lên ứng dụng.

![Hình PL.24. Web Admin — báo cáo Thu nhập shipper với 3 KPI và 2 biểu đồ](screenshots/admin-12-shipper-earnings.png){width=13cm}

**Trang `ShipperDetailPage` (Hình PL.25)** đóng vai trò trung tâm đối soát: hiển thị số dư hiện tại của một shipper, hai nút "Đã trả lương" / "Đã nhận tiền nộp" mở modal cho admin nhập số tiền + ghi chú để tạo entry settlement, và bảng lịch sử ledger với mọi giao dịch của shipper đó. Modal chỉ có ba field (loại, số tiền, ghi chú) để đối soát nhanh, nhưng vẫn ghi lại đầy đủ trong ledger cho audit về sau.

![Hình PL.25. Web Admin — chi tiết shipper với balance, settle modal trigger và lịch sử ledger](screenshots/admin-14-shipper-detail.png){width=13cm}

Trang `ShippersPage` cũ được mở rộng với hai cột mới — "Số dư ví" và "Thu nhập 7 ngày" — cho phép admin scan nhanh tình trạng tài chính của toàn bộ shipper mà không phải vào từng trang chi tiết. Trang `OrderDetailPage` được bổ sung section "Hoa hồng & thanh toán" hiển thị khi đơn DELIVERED (Hình PL.26), gồm: phí ship gốc, giảm phí ship (nếu có voucher), phí ship khách trả thực tế, hoa hồng shipper (đậm xanh), hình thức thanh toán, và số tiền shipper đã thu từ khách (nếu là COD) — bản tổng kết một chỗ cho cả nghiệp vụ giao hàng lẫn dòng tiền.

![Hình PL.26. Web Admin — section Hoa hồng & thanh toán trên trang chi tiết đơn DELIVERED](screenshots/admin-15-order-commission.png){width=13cm}

Trang `SettingsPage` được mở rộng với section mới "Hoa hồng shipper" chứa một ô số `shipper_commission_pct` (0–100, step 0.1, validate cả phía client lẫn server) — admin có thể đổi tỉ lệ bất cứ lúc nào. Đáng lưu ý: việc đổi tỉ lệ chỉ áp dụng cho các đơn DELIVERED *sau thời điểm lưu* — các entry COMMISSION đã ghi trong quá khứ giữ nguyên (snapshot trên `orders.shipper_commission`), tránh đổi tỉ lệ làm sai lệch lịch sử thu nhập của shipper.

#### I.3.5. Testing và metrics

Phase 15 đóng góp 10 test mới: 5 unit cho `CommissionCalculator` (cover công thức cơ bản, rounding HALF_UP, edge cases với 0% và 100%) và 5 integration cho `OrderCompletionListener` đã liệt kê ở 6.3.2. Phase 15 cũng đòi hỏi hạ tầng test phức tạp hơn — `DeliveryTestcontainerBase` và `DeliveryTestApplication` phải scan cả ba module `delivery + order + auth` để Spring context tải đủ bean cho listener pattern; đồng thời 15 file migration phải được copy sang `backend/modules/delivery/src/test/resources/db/migration/` để Flyway boot được Testcontainers Postgres.

Phase 15 được thực hiện qua **16 commit** atomic trên nhánh `feat/phase-15-shipper-commission` rồi fast-forward merge vào `main`. Quy trình GSD áp dụng tương tự Phase 14, với plan chi tiết tại `docs/superpowers/plans/2026-06-03-phase-15-shipper-commission.md` (26 task) và spec chung tại `docs/superpowers/specs/2026-06-03-voucher-shipper-commission-design.md` (đánh dấu "Phase 14 + 15 both complete").

### I.4. Tác động tổng hợp của hai pha

Hai pha P14 và P15 đóng góp tập trung vào ba lát cắt — schema dữ liệu, REST API và giao diện người dùng — như Bảng 6.1 tổng kết.

**Bảng PL.9. Tổng hợp đóng góp định lượng của hai pha mở rộng P14 và P15**

| Hạng mục | Đóng góp |
|---|---|
| Migration mới | 2 (V14 `voucher.sql`, V15 `shipper_ledger.sql`) |
| Bảng dữ liệu mới | 3 (`voucher`, `voucher_redemption`, `shipper_ledger`) |
| Cột mới ở bảng hiện hữu | 5 (`orders.discount_products`, `orders.discount_shipping`, `orders.delivery_fee_original`, `orders.shipper_commission`, `shop_config.shipper_commission_pct`) |
| Bounded context mới | 1 (module Maven `promotion`) |
| REST endpoint mới | 15 (6 voucher + 4 shipper-self + 4 admin shipper + 1 admin reports) |
| Trang Web Admin mới / mở rộng | 5 mới (Vouchers, VoucherForm, VoucherDetail, ShipperEarnings, ShipperDetail) + 3 mở rộng (Settings, Shippers, OrderDetail) |
| Trang Mini App mới / mở rộng | 4 mới (Earnings, EarningsDetail, Wallet, ShipperProfile) + 2 redesign (ShipperAssignments, ShipperAssignmentDetail) + 1 mở rộng (Checkout) |
| Test mới | 55 (21 voucher unit + integration + race + 10 commission unit + integration + 24 hạ tầng và điều chỉnh khác) |
| Ảnh chụp giao diện mới | 11 (Hình PL.17 đến 6.11) |
| Commit nguyên tử | 43 (27 cho P14 + 16 cho P15) |

Mặc dù được phát triển trong thời gian rất ngắn (khoảng hai ngày tập trung sau khi spec + plan đã được duyệt), cả hai pha vẫn tuân thủ đầy đủ các nguyên tắc thiết kế của các pha trước — đặc biệt là tính atomicity của commit, separation of concerns giữa các module (`promotion` không phụ thuộc compile-time vào `delivery`; `delivery` chỉ phụ thuộc `order` qua SPI), và defense-in-depth ở chỗ giáp ranh (pessimistic lock cho race condition, idempotency check cho event replay, snapshot cho audit trail).

### I.5. Hạn chế còn lại

Hai pha mở rộng đóng phần lớn các tính năng nghiệp vụ "phải có" của một nền tảng giao hàng thương mại, nhưng vẫn còn ba hạn chế đáng kể được nhận diện trong quá trình phát triển và sẽ là chủ đề của các luận văn / dự án tiếp theo:

**Thứ nhất**, hệ thống voucher chưa hỗ trợ ràng buộc theo sản phẩm hoặc danh mục — một voucher PRODUCTS hiện áp lên toàn bộ subtotal chứ không thể chỉ áp cho "mặt hàng phở" hoặc "danh mục đồ ăn nóng". Để thêm cần một bảng join `voucher_eligible_product` và mở rộng `VoucherCalculator` để duyệt qua line items thay vì toàn bộ subtotal. Quyết định gác lại với lý do "phức tạp không tương xứng với phạm vi luận văn".

**Thứ hai**, cơ chế settlement hoa hồng hiện chỉ là *ghi nhận* — admin nhập số tiền đã trả/nhận thủ công, không tích hợp cổng thanh toán tự động. Trong thực tế thương mại, settlement nên được tự động hoá qua chuyển khoản API (Napas 247, MoMo Disbursement, hoặc giải pháp tương tự). Đây là dự án mở rộng phù hợp cho một luận văn về *automation và payment integration*.

**Thứ ba**, biểu đồ thu nhập của shipper trong Mini App hiện chỉ hiển thị 7 ngày qua bằng bar chart, chưa có drill-down theo tuần / tháng / năm và chưa so sánh với shipper khác (gamification). Phiên bản đầy đủ kiểu Grab/Be cũng có "thử thách hoàn thành X đơn để được thưởng Y đ" — hướng phát triển lý thú nhưng đòi hỏi cả một subsystem riêng cho *incentive program management*.

Mặc dù còn các hạn chế trên, hai pha mở rộng đã đáp ứng mục tiêu ban đầu là chứng minh tính khả thi thương mại của hệ thống và cho phép demo trọn vẹn vòng đời nghiệp vụ — từ khi khách áp voucher ở Checkout đến khi shipper xem được hoa hồng ngay trong Wallet sau khi giao xong đơn. Trải nghiệm end-to-end này cũng là phần được kỳ vọng tạo ấn tượng tốt nhất với hội đồng trong buổi bảo vệ.
