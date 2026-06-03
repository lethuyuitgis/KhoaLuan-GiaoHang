# Chương 4. Cài đặt hệ thống

Chương này trình bày các quyết định hiện thực cụ thể của hệ thống quản lý giao hàng dựa trên nền tảng Telegram, bao gồm môi trường phát triển và công cụ, cấu trúc dự án, hiện thực chi tiết của tám mô-đun backend, hiện thực phía frontend (Mini App và Web Admin), quản lý schema cơ sở dữ liệu qua mười hai file Flyway migration, đóng gói triển khai bằng Docker Compose, cấu hình biến môi trường và chiến lược kiểm thử nhiều tầng. Mục tiêu của chương là chứng minh rằng các quyết định thiết kế đã đề ra ở Chương 3 được hiện thực đầy đủ và nhất quán, đồng thời hệ thống đạt mức độ sẵn sàng triển khai (deployment-ready) — chỉ cần ba lệnh để dựng toàn bộ stack chạy được trên một máy chủ đơn lẻ.

## 4.1. Môi trường phát triển và công cụ

### 4.1.1. Yêu cầu phần cứng

Hệ thống được phát triển và kiểm thử trên các môi trường máy tính cá nhân phổ thông. Yêu cầu tối thiểu là CPU bốn nhân, bộ nhớ RAM tám gigabyte và dung lượng ổ đĩa trống ít nhất hai mươi gigabyte (tính cả image Docker và dữ liệu PostgreSQL). Cấu hình khuyến nghị cho trải nghiệm phát triển mượt là RAM mười sáu gigabyte để có thể chạy đồng thời IDE, PostgreSQL container, backend Spring Boot và hai dev server Vite. Hệ điều hành hỗ trợ gồm macOS 13+, Linux (Ubuntu 22.04+, Fedora 38+) và Windows 11 với WSL2 (Ubuntu 22.04 LTS) — môi trường Windows thuần không được kiểm thử vì Docker Desktop trên WSL2 cho hiệu năng I/O ổn định hơn đáng kể.

### 4.1.2. Phần mềm phát triển và phiên bản

Toàn bộ hệ thống được phát triển trên một bộ công cụ thống nhất nhằm đảm bảo tính tái lập (reproducibility) — bất kỳ thành viên nào clone kho mã nguồn về cũng dựng được môi trường giống nhau. Bảng 4.1 dưới đây liệt kê đầy đủ tech stack với phiên bản tham chiếu từ `pom.xml` của parent project và các file `package.json` của các workspace frontend.

**Bảng 4.1. Tech stack đầy đủ của hệ thống và phiên bản tham chiếu**

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
| Reverse proxy | nginx | 1.27-alpine |
| Container runtime | Docker Engine | 24+ |
| Container orchestration | Docker Compose plugin | v2 |
| IDE khuyến nghị | IntelliJ IDEA Ultimate / VS Code | latest |
| Quản lý mã nguồn | Git | 2.40+ |

### 4.1.3. Tài khoản và dịch vụ ngoài

Hệ thống phụ thuộc vào hai dịch vụ bên thứ ba bắt buộc: nền tảng Telegram và cổng thanh toán VNPay. Đối với Telegram, người triển khai cần tạo một bot mới qua kênh `@BotFather` bằng lệnh `/newbot`, sau đó ghi nhận hai giá trị: token bot (định dạng `<10-digit-id>:<35-char-alnum>`) và username bot (không kèm ký tự `@`). Đối với VNPay, người triển khai cần đăng ký tài khoản tại cổng `sandbox.vnpayment.vn/devreg` để nhận `TmnCode` (mã merchant) và `HashSecret` (khoá ký HMAC-SHA512); cả hai giá trị này được nạp vào hệ thống qua biến môi trường. Trong giai đoạn phát triển khi chưa có credential thật, hệ thống vẫn chạy được vì các test cho `VnpaySignatureService` sử dụng cặp mock `TEST01` / `TESTSECRETKEY123` — tuy nhiên luồng tạo và xác minh giao dịch end-to-end chỉ hoạt động với credential sandbox thật.

### 4.1.4. Biến môi trường

Hệ thống nạp toàn bộ cấu hình runtime qua biến môi trường thay vì hard-code, tuân thủ nguyên tắc Twelve-Factor App. File `.env.example` ở thư mục gốc đóng vai trò *canonical manifest* — liệt kê toàn bộ biến mà hệ thống cần kèm giá trị mặc định hợp lý. Người triển khai chỉ cần `cp .env.example .env` rồi điền bốn biến bắt buộc (đánh dấu `▼ FILL IN ▼`): `BOT_TOKEN`, `BOT_USERNAME`, `JWT_SECRET` (sinh bằng `openssl rand -hex 32`), và cặp `VNPAY_TMN_CODE` / `VNPAY_HASH_SECRET`. Khoảng mười biến tuỳ chọn khác đã có giá trị mặc định khả dụng ngay (`DB_USER=app`, `SHOP_PICKUP_LAT=21.0285`, `SHOP_FEE_BASE=15000`, v.v.). File `.env` thật được đưa vào `.gitignore` — tuyệt đối không commit secret vào kho mã nguồn.

## 4.2. Cấu trúc dự án

### 4.2.1. Cây thư mục tổng thể

Toàn bộ mã nguồn được tổ chức theo nguyên tắc *tách rõ ranh giới giữa backend, frontend, infra và tài liệu*, mỗi nhánh là một thư mục cấp một độc lập có thể build riêng. Cây thư mục dưới đây mô tả bố cục cấp một và cấp hai của dự án.

```text
KhoaLuan-GiaoHang/
├── backend/                              # Spring Boot 3 multi-module Maven
│   ├── pom.xml                           # Parent pom — aggregator + dependencyManagement
│   ├── app/                              # Executable jar — entry point
│   │   ├── pom.xml
│   │   └── src/main/resources/db/migration/   # 12 file Flyway V1 → V12
│   ├── modules/
│   │   ├── shared/                       # BaseEntity, exception, event records
│   │   ├── auth/                         # Telegram initData + JWT admin
│   │   ├── order/                        # Product, Order, OrderItem, FSM
│   │   ├── delivery/                     # ShipperProfile, Assignment, Rating
│   │   ├── payment/                      # VNPay signing + IPN + scheduler
│   │   ├── bot/                          # Telegram Bot handlers + FSM
│   │   ├── notification/                 # 4 cross-module event listeners
│   │   ├── miniapp/                      # Static resource serving stub
│   │   └── webadmin/                     # Static resource serving stub
│   └── mvnw, mvnw.cmd                    # Maven wrapper
├── frontend/                             # pnpm workspace với 3 package
│   ├── pnpm-workspace.yaml
│   ├── package.json                      # Root scripts: build, test, type-check
│   ├── shared/                           # @shop/shared — types + helpers
│   ├── miniapp/                          # @shop/miniapp — Telegram Mini App
│   └── webadmin/                         # @shop/webadmin — Web Admin
├── infra/
│   ├── docker-compose.yml                # Full demo stack (5 service)
│   ├── docker-compose.dev.yml            # Postgres-only cho dev
│   ├── nginx/nginx.conf                  # Reverse proxy config
│   └── postgres/init.sql                 # Encoding + extension
├── docs/
│   ├── thesis/                           # Báo cáo khoá luận (Chương 1–5)
│   └── superpowers/                      # 10 plan + 8 research + 9 review
├── .env.example                          # Manifest biến môi trường
├── README.md                             # Quick start
└── RUNBOOK.md                            # 8 bước manual smoke test
```

### 4.2.2. Backend Maven multi-module

Backend là một project Maven multi-module với mười submodule con (tám bounded context nghiệp vụ cộng hai stub serving static resource và một module `app` đóng gói executable jar). Parent `pom.xml` ở thư mục `backend/` không chứa source code mà chỉ đảm nhận hai nhiệm vụ: (i) khai báo thứ tự build qua thẻ `<modules>` đảm bảo `shared` luôn build trước, sau đó là các module nghiệp vụ song hàng, cuối cùng là `app`; (ii) tập trung quản lý phiên bản dependency qua `<dependencyManagement>` để mọi module con tham chiếu cùng một phiên bản — tránh tình trạng version drift. Mỗi module con có package gốc theo dạng `com.shop.delivery.<module>` (ví dụ `com.shop.delivery.payment`), ranh giới module được Maven enforce tại thời điểm biên dịch: module `bot` muốn dùng class của `order` thì phải khai báo dependency trong `pom.xml`, không thể tự tiện import.

### 4.2.3. Frontend pnpm workspace

Frontend được tổ chức thành ba workspace pnpm được khai báo trong file `frontend/pnpm-workspace.yaml`: `shared`, `miniapp`, `webadmin`. Workspace `shared` (tên npm `@shop/shared`) chứa các kiểu dữ liệu TypeScript dùng chung giữa Mini App và Web Admin (`OrderDto`, `PaymentDto`, `LocationDto`, `ShipperProfileDto`, v.v.) cùng các helper định dạng (`formatVnd`, `formatDateTime`) và API client wrapper bao bọc trên `axios`. Workspace `miniapp` (`@shop/miniapp`) và `webadmin` (`@shop/webadmin`) đều import `@shop/shared` qua đường dẫn workspace (`"@shop/shared": "workspace:*"`), nhờ đó pnpm hardlink thư mục `frontend/shared/dist` vào `node_modules/@shop/shared` của hai workspace tiêu thụ — thay đổi DTO ở một nơi tự động lan toả mà không cần publish gói lên registry.

### 4.2.4. Hạ tầng và tài liệu

Thư mục `infra/` chứa cấu hình hạ tầng triển khai: hai file Docker Compose (một cho production-like full stack, một cho dev chỉ Postgres), file `nginx.conf` cho reverse proxy, và `postgres/init.sql` thiết lập encoding `UTF8` và extension `pg_trgm` lúc Postgres khởi tạo lần đầu. Thư mục `docs/` chứa toàn bộ tài liệu, được chia thành `docs/thesis/` (báo cáo khoá luận và screenshot) và `docs/superpowers/` (artefact của phương pháp phát triển GSD — bao gồm mười file plan, tám file research, chín file code review, đều được commit cùng mã nguồn để giữ truy vết thiết kế).

## 4.3. Cài đặt module backend

Backend hệ thống được tổ chức thành tám bounded context tuân thủ đồ thị phụ thuộc một chiều (DAG, không có chu trình) đã trình bày ở Chương 3 mục 3.2. Các mục dưới đây mô tả chi tiết hiện thực của từng module theo cùng một cấu trúc gồm: trách nhiệm chính, các thành phần (entity / repository / service / controller) cốt lõi, đóng góp vào schema cơ sở dữ liệu (Flyway migration tương ứng) và đặc điểm thiết kế đáng chú ý.

### 4.3.1. Module `shared`

Module `shared` đứng tại đáy của đồ thị phụ thuộc — không phụ thuộc bất kỳ module nội bộ nào khác. Trách nhiệm chính là cung cấp các kiểu dữ liệu và class tiện ích dùng chung cho toàn hệ thống. Module này chứa: (i) class `BaseEntity` (trong gói `shared/domain`) cung cấp các trường `createdAt` và `updatedAt` được Hibernate tự cập nhật qua `@CreationTimestamp` và `@UpdateTimestamp`, kế thừa bởi mọi entity của các module nghiệp vụ; (ii) hệ phân cấp ngoại lệ `DomainException` với năm class con (`ValidationException`, `NotFoundException`, `ConflictException`, `BusinessRuleException`, `AuthenticationException`, `ExternalServiceException`); (iii) lớp `GlobalExceptionHandler` áp dụng `@RestControllerAdvice` ánh xạ ngoại lệ sang RFC 7807 `ApiError` với mã HTTP phù hợp; (iv) chín event record trong gói `shared/event` (`OrderCreatedEvent`, `OrderConfirmedEvent`, `OrderAssignedEvent`, `OrderDeliveredEvent`, `OrderCancelledEvent`, `PaymentSucceededEvent`, `PaymentFailedEvent`, `ShipperRegisteredEvent`, `ShipperApprovedEvent`) đóng vai trò "ngôn ngữ chung" cho giao tiếp cross-module. Vì là Java 17 `record`, các event này bất biến (immutable) — đảm bảo không listener nào vô tình mutate state đã publish.

### 4.3.2. Module `auth`

Module `auth` hiện thực hai cơ chế xác thực song song của hệ thống: *Telegram initData* (HMAC-SHA256) cho Mini App và *JWT HS512* cho Web Admin. Bốn entity chính: `TelegramUser` (lưu mọi user đã tương tác với bot), `UserRole` (vai trò CUSTOMER / SHIPPER / SHOP_OWNER), `AdminUser` (tài khoản đăng nhập Web Admin, password BCrypt), `RefreshToken` (DB-backed, hash SHA-256). Hai filter chính: `TelegramAuthFilter` đọc header `X-Telegram-Init-Data`, parse query string, kiểm tra `auth_date` không quá 24 giờ và so sánh hash bằng `MessageDigest.isEqual` (constant-time, chống timing attack); `JwtAuthFilter` đọc header `Authorization: Bearer <token>` rồi xác thực qua `JwtService` (SignatureAlgorithm HS512, access token 15 phút, refresh token 7 ngày). `CurrentUserArgumentResolver` cho phép controller inject `@CurrentUser TelegramUser` đã xác thực, tránh boilerplate `SecurityContextHolder.getContext()` lặp lại. Module này đóng góp Flyway V2 (`auth.sql` — tạo `admin_user`, `refresh_token`).

### 4.3.3. Module `order`

Module `order` quản lý vòng đời đơn hàng từ khi tạo `Order(PENDING)` đến trạng thái cuối `DELIVERED`, `CANCELLED` hoặc `RETURNED`. Bốn entity nghiệp vụ: `Product`, `Order`, `OrderItem` (quan hệ 1-N qua `@OneToMany(cascade = CascadeType.ALL)`), `StatusHistory` (ghi lại mọi chuyển trạng thái với `previousStatus`, `newStatus`, `actor`, `note`, `timestamp`). Service quan trọng nhất là `OrderStateMachine` — class triển khai mẫu State Machine đầy đủ với một bảng whitelist `Map<OrderStatus, Set<OrderStatus>>` định nghĩa các chuyển dịch hợp lệ; mọi chuyển dịch không nằm trong bảng đều bị `IllegalStateTransitionException` từ chối. `FeeCalculator` tính phí ship theo công thức Haversine với chính sách `base_fee + max(0, distance_km - free_km) × fee_per_km` lấy tham số từ `shop_config`. `OrderCodeGenerator` sinh mã đơn dạng `ORD-yyMMddHHmmss-XX` (XX là hai chữ số random) đảm bảo unique kể cả khi tạo song song. `PaymentEventListener` lắng nghe `PaymentSucceededEvent` (cross-module) để transition `Order(PENDING) → Order(CONFIRMED)`. Module này đóng góp Flyway V4 (`order.sql` — tạo `product`, `orders`, `order_item`, `status_history`).

Ví dụ logic chuyển trạng thái trong `OrderStateMachine`:

```java
public void assertTransitionAllowed(OrderStatus from, OrderStatus to) {
    Set<OrderStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
    if (!allowed.contains(to)) {
        throw new IllegalStateTransitionException(
            "Không thể chuyển từ %s sang %s".formatted(from, to));
    }
}
```

### 4.3.4. Module `delivery`

Module `delivery` hiện thực phần vận hành giao hàng và là module có nhiều entity nhất. Sáu entity nghiệp vụ: `ShipperProfile` (gắn 1-1 với `TelegramUser` qua `telegram_user_id`, lưu tên, số điện thoại, loại phương tiện, biển số, `rating_avg`, `rating_count`, `state`), `DeliveryAssignment` (gán shipper cho đơn, FSM năm trạng thái OFFERED / ACCEPTED / STARTED / COMPLETED / DECLINED, có cột `expires_at` cho TTL của offer), `LocationPing` (mỗi điểm GPS từ Telegram Live Location), `Rating` (khách → shipper, 1-5 sao, UNIQUE order_id), `ShipperRating` (shipper → khách, 1-5 sao, không công khai — bổ sung ở V12), và các view-DTO cho `ReportsQueryService`. Service trọng tâm là `DeliveryAssignmentService` xử lý gán/nhận/từ chối; `LocationPingService` lưu ping và publish `LocationPingReceivedEvent`; `ReportsQueryService` chạy aggregate query (DATE_TRUNC + GROUP BY) phục vụ dashboard. Một sáng kiến thiết kế của module này là *partial unique index* `uq_assignment_shipper_started` (V8) — khoá business rule "một shipper chỉ có tối đa một assignment STARTED" tại tầng cơ sở dữ liệu, chống được cả race condition không bắt được bằng code Java. Module đóng góp các migration V6 (`delivery.sql`), V7 (`location.sql`), V8 (`assignment_unique_started.sql`), V10 (`rating.sql`), V12 (`shipper_rating.sql`).

```sql
-- Trích V8: enforce "1 shipper có tối đa 1 STARTED assignment"
CREATE UNIQUE INDEX IF NOT EXISTS uq_assignment_shipper_started
    ON delivery_assignment(shipper_id)
    WHERE status = 'STARTED';
```

### 4.3.5. Module `payment`

Module `payment` hiện thực luồng VNPay đầy đủ với mô hình *IPN-as-source-of-truth* — Return URL chỉ render trang HTML cho khách thấy kết quả, mọi cập nhật state đều đến từ IPN server-to-server. Hai entity: `Payment` (gắn 1-1 với `Order` qua `order_id`, có `vnp_TxnRef` UNIQUE) và `PaymentTransaction` (ghi mỗi event IPN với payload JSONB nguyên gốc trong cột `raw_payload`, đảm bảo audit trail bắt buộc kể cả khi IPN có chữ ký không hợp lệ). Ba service: `VnpaySignatureService` ký URL HMAC-SHA512 theo đặc tả VNPay v2.1.0 (sort param ASCII, URL-encode US-ASCII, hash hex lowercase, so sánh bằng `MessageDigest.isEqual`); `VnpayPaymentService` orchestrate tạo URL và xử lý IPN (idempotent — replay trả `RspCode 02` không thay đổi DB); `PaymentExpiryScheduler` chạy cron `@Scheduled(fixedDelay = 60_000)` đánh dấu các `Payment(PENDING)` quá 15 phút thành `FAILED` để khách có thể đặt lại. Một chi tiết tinh tế là `@Transactional(propagation = Propagation.REQUIRES_NEW)` trên `OrderService.confirmAfterPayment` — nếu để `REQUIRED` mặc định, listener fire trong `AFTER_COMMIT` phase của outer transaction sẽ không commit được order update. Phát hiện này được catch ở integration test Wave 1 Task 9. Module đóng góp Flyway V9 (`payment.sql`).

Ví dụ đoạn ký HMAC trong `VnpaySignatureService`:

```java
Mac mac = Mac.getInstance("HmacSHA512");
mac.init(new SecretKeySpec(props.getHashSecret().getBytes(StandardCharsets.UTF_8),
                            "HmacSHA512"));
byte[] hashBytes = mac.doFinal(dataToHash.getBytes(StandardCharsets.UTF_8));
String hashHex = HexFormat.of().formatHex(hashBytes);  // lowercase
```

### 4.3.6. Module `bot`

Module `bot` đóng vai trò "facade" với Telegram Bot API thông qua thư viện `telegrambots-spring-boot-starter` 6.9.7. Thành phần lõi là `DeliveryBot extends TelegramLongPollingBot` (chế độ polling mặc định, có thể switch sang webhook qua biến `BOT_MODE=webhook`). `UpdateRouter` định tuyến `Update` đến danh sách `List<UpdateHandler>` đã được Spring inject theo thứ tự `@Order(n)` — `RatingCommentHandler` và `ShipperRegistrationTextHandler` được đặt `@Order(0)` để chạy trước bất kỳ command handler chung nào, tránh việc user gõ `/help` giữa flow FSM rơi xuống `HelpHandler`. `ProcessedUpdateService` đảm bảo idempotency — mỗi `update_id` chỉ xử lý đúng một lần, kể cả khi Telegram retry. `ConversationStateService` quản lý FSM hội thoại với payload JSONB qua `@JdbcTypeCode(SqlTypes.JSON)` native Hibernate 6 — không cần thêm dependency. `BotSender` được bao bọc bởi Bucket4j rate-limit để tuân thủ giới hạn 30 message/giây của Telegram Bot API. Module có mười một handler cụ thể, đáng chú ý là năm handler cho luồng đăng ký shipper qua bot (`ShipperRegistrationCallbackHandler`, `ShipperRegistrationContactHandler`, `ShipperRegistrationVehicleCallbackHandler`, `ShipperRegistrationTextHandler`, cùng với `OrderOfferCallbackHandler` và `LiveLocationHandler`). Module đóng góp Flyway V3 (`bot.sql` — `telegram_user`, `user_role`, `conversation_state`, `processed_update`).

Ví dụ FSM transition trong `ShipperRegistrationTextHandler`:

```java
switch (state.getCurrentState()) {
    case AWAITING_NAME -> {
        state.getPayload().put("fullName", text);
        conversationStateService.transition(
            chatId, ShipperRegistrationStates.AWAITING_PHONE);
        sender.send(buildRequestContactPrompt(chatId));
    }
    case AWAITING_PLATE -> {
        state.getPayload().put("plate", normalisePlate(text));
        completeRegistration(chatId, state);
    }
}
```

### 4.3.7. Module `notification`

Module `notification` là nơi tập trung mọi listener cross-module — không có entity hay repository riêng, chỉ chứa bốn `@Component` listener: `OrderAssignedNotifier` lắng nghe `OrderAssignedEvent` để gửi inline keyboard "Chấp nhận / Từ chối" cho shipper qua bot; `OrderLifecycleNotifier` lắng nghe `OrderConfirmedEvent`, `OrderDeliveredEvent`, `OrderCancelledEvent` để thông báo cho khách và broadcast STOMP `/topic/admin/orders` cho Web Admin; `RatingPromptBuilder` gửi prompt đánh giá 1-5 sao sau khi đơn `DELIVERED`; `ShipperRegistrationNotifier` (mới thêm) lắng nghe `ShipperRegisteredEvent` và `ShipperApprovedEvent` để thông báo cho admin và chính shipper. Tất cả listener đều dùng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` — đảm bảo notification chỉ phát sau khi transaction nghiệp vụ commit thành công, tránh trường hợp gửi tin nhắn rồi DB rollback. Khi listener publish trở lại event hoặc gọi tiếp lớp service, `Propagation.REQUIRES_NEW` được dùng để bắt đầu transaction mới hoàn toàn độc lập.

### 4.3.8. Module `app`

Module `app` là executable jar duy nhất của hệ thống, đóng vai trò wire toàn bộ thành phần lại với nhau. Class `Application` chứa `main()` method với annotation `@SpringBootApplication` (scan các package `com.shop.delivery.*`), `@EnableScheduling` (kích hoạt `PaymentExpiryScheduler`), `@EnableJpaAuditing`. Module chứa: `SecurityConfig` định nghĩa filter chain với hai filter chain song song (Mini App API qua `TelegramAuthFilter`, Web Admin API qua `JwtAuthFilter`); `WebSocketConfig` cấu hình STOMP broker với channel interceptor `WebSocketAuthInterceptor` xác thực CONNECT frame (dual auth: Telegram initData hoặc JWT Bearer); hai file properties `application.yml` (chung) và `application-prod.yml` (fail-fast cho secret); và thư mục `db/migration/` chứa toàn bộ mười hai file Flyway V1 đến V12. Khi build qua `./mvnw package`, plugin `spring-boot-maven-plugin` đóng gói tất cả module phụ thuộc vào một fat jar dung lượng khoảng 70 megabyte, có thể chạy bằng `java -jar app.jar` hoặc đóng vào Docker image.

## 4.4. Cài đặt module frontend

Frontend hệ thống được tổ chức thành ba workspace TypeScript chia sẻ qua pnpm. Cách bố trí này tránh duplicate type giữa Mini App và Web Admin, đồng thời tách bạch hai ứng dụng cuối có vòng đời phát hành và bundle khác nhau.

### 4.4.1. Package `@shop/shared`

Package `@shop/shared` cung cấp ba nhóm tài sản dùng chung: (i) các kiểu TypeScript đại diện cho DTO trao đổi với backend — mỗi interface tương ứng với một response class Java (`OrderResponse`, `PaymentResponse`, `LocationPingDto`, `ShipperProfileDto`, v.v.); (ii) API client wrapper bao bọc trên `axios` với interceptor inject header xác thực (`X-Telegram-Init-Data` cho Mini App, `Authorization: Bearer` cho Web Admin) tự động, kèm `createVnpayPayment` helper gọi `POST /api/payment/vnpay/create`; (iii) các helper định dạng dùng chung — `formatVnd(amount: number): string` định dạng tiền VND với dấu phân cách hàng nghìn, `formatDateTime(iso: string): string` định dạng thời gian theo locale Việt Nam, `haversineKm(p1, p2): number` tính khoảng cách hai toạ độ (tái sử dụng cho client-side preview phí ship). Toàn bộ package được build bằng `tsc` ra `dist/` chứa cả `.js` và `.d.ts`. Vitest cho package này chạy trong môi trường node và đảm nhận chín test cho các format helper.

### 4.4.2. Mini App (`@shop/miniapp`)

Mini App được xây dựng bằng React 18 + Vite 5 + TypeScript 5.6. SDK `@twa-dev/sdk` được dùng cho các API native của Telegram WebApp: `WebApp.ready()`, `WebApp.expand()`, `WebApp.openLink()`, `WebApp.themeParams`, `BackButton`, `MainButton`, `HapticFeedback`. Ứng dụng có chín trang được route qua `react-router-dom` 6: Splash (xử lý loading initData), Catalog (danh mục), Cart (giỏ hàng), Checkout (gồm bản đồ Leaflet để ghim toạ độ giao), Orders (lịch sử đơn của khách), OrderDetail (với marker shipper realtime cập nhật qua STOMP), ShipperAssignments và ShipperAssignmentDetail (cho vai trò shipper), và NotFound. `TelegramProvider` là một React context wrapper khởi tạo `WebApp.ready()` và cung cấp `initData` cho mọi component con. State giỏ hàng được lưu qua Zustand với middleware `persist` vào `localStorage`, tránh mất giỏ khi khách thoát Mini App giữa chừng. Server state (danh mục, đơn) được TanStack Query cache theo `queryKey` phân cấp (`['products']`, `['order', orderId]`), tự động invalidate sau mutation.

Để mỗi shop có thể tự "thay áo" Mini App mà không phải build lại bundle, một `ThemeProvider` được đặt ngay sau `QueryProvider` trong cây React. Provider này gọi hook `useShopConfig` (đọc public endpoint `/api/public/shop-config` với stale-time năm phút) rồi publish bốn CSS variable lên `:root` — `--brand-primary`, `--brand-primary-dark`, `--brand-primary-light` và `--brand-secondary`. Hàm `shade()` tự sinh hai biến `dark`/`light` từ màu chính theo công thức HSL ± 15% để nút bấm có hover state mà admin không phải khai báo riêng. Các trang Catalog, Cart, Checkout opt-in bằng cách dùng class Tailwind dạng `bg-[var(--brand-primary)]`, do đó khi admin đổi màu trong Settings, customer thấy ngay sau khi cache TanStack Query stale.

Hình 4.2 dưới đây minh hoạ giao diện danh mục sản phẩm của Mini App, là điểm vào đầu tiên của khách hàng sau khi mở bot và bấm nút `Mini App`.

![Hình 4.2. Giao diện danh mục sản phẩm trong Mini App](screenshots/miniapp-cust-01-catalog.png)

Ví dụ store Zustand cho giỏ hàng:

```typescript
export const useCart = create<CartState>()(
  persist(
    (set) => ({
      items: [],
      addItem: (product, qty) => set((s) => ({ items: mergeQty(s.items, product, qty) })),
      removeItem: (id) => set((s) => ({ items: s.items.filter(i => i.product.id !== id) })),
      clear: () => set({ items: [] }),
    }),
    { name: 'cart-v1', storage: createJSONStorage(() => localStorage) },
  ),
);
```

### 4.4.3. Web Admin (`@shop/webadmin`)

Web Admin được xây dựng cũng bằng React 18 + Vite 5 + TypeScript 5.6, nhưng có bundle riêng và phục vụ qua subpath `/admin/*` của nginx. Tám trang chính: Login (form email + password, gọi `POST /api/admin/auth/login` → nhận cặp access + refresh token), Dashboard (KPI numbers và ba biểu đồ Recharts — doanh thu theo ngày, top shipper, tỉ lệ huỷ), Orders (bảng filter theo trạng thái và khoảng ngày, pagination, action gán shipper / huỷ), OrderDetail, Products (CRUD), Shippers (duyệt, khoá, mở khoá), Reports (LineChart doanh thu theo ngày tuỳ chọn), Settings (cập nhật `shop_config`). State session JWT lưu trong Zustand store riêng (`auth-store`); TanStack Query 5 quản lý mọi server state với `invalidateQueries` sau mutation. WebSocket client dùng `@stomp/stompjs` cộng `sockjs-client`, subscribe `/topic/admin/orders` ngay sau login để nhận đơn mới realtime. Để giữ kích thước bundle initial dưới 500 kilobyte, Vite được cấu hình `manualChunks` tách Recharts thành chunk riêng lazy-load chỉ khi Dashboard/Reports render — bundle initial sau tối ưu đạt khoảng 121 kilobyte gzipped.

Hình 4.3, 4.4 và 4.5 dưới đây minh hoạ ba giao diện chính của Web Admin: Dashboard, Reports và Settings.

![Hình 4.3. Giao diện Dashboard của Web Admin với KPI và ba biểu đồ](screenshots/admin-02-dashboard.png)

![Hình 4.4. Giao diện Reports với biểu đồ doanh thu theo khoảng ngày](screenshots/admin-04-reports.png)

![Hình 4.5. Trang Settings — chủ shop cập nhật thương hiệu, điểm pickup và phí giao không cần redeploy](screenshots/admin-08-settings.png)

Trang Settings là điểm cuối cho cấu hình `shop_config` (xem V13 ở mục 4.5): admin nhập tên shop, tagline, logo URL, cặp màu thương hiệu (`brandPrimary`/`brandSecondary` với picker hex + preview nút bấm theo thời gian thực), toạ độ pickup (lat/lng được validate theo regex `#RRGGBB` ở phía client và `@Pattern` ở backend) và ba tham số phí giao (`feeBase`, `feePerKm`, `freeKm`). Khi `PUT /api/admin/shop-config` thành công, TanStack Query invalidate cache `['admin', 'shop-config']` và đồng thời customer-facing endpoint `/api/public/shop-config` cũng phản ánh ngay — Mini App sẽ nhận màu mới khi `useShopConfig` revalidate.

Ví dụ STOMP client factory dùng chung:

```typescript
export function createStompClient(url: string, headers: Record<string, string>) {
  return new Client({
    webSocketFactory: () => new SockJS(url),
    connectHeaders: headers,
    reconnectDelay: 5000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
  });
}
```

## 4.5. Cơ sở dữ liệu — Flyway migrations

### 4.5.1. Tổng quan chiến lược migration

Toàn bộ schema cơ sở dữ liệu được quản lý qua Flyway 10 — không có schema thay đổi nào được thực hiện thủ công trên môi trường production. Mỗi pha trong quy trình GSD đóng góp một (hoặc một số) file migration cho mô-đun của pha đó, đảm bảo schema thay đổi rõ ràng theo timeline phát triển. Quy ước đặt tên file là `V<n>__<snake_name>.sql` với `<n>` là số nguyên tăng tuần tự bắt đầu từ 1, không có khoảng trống. Tổng cộng hệ thống có mười ba file migration (V1 đến V13) tính đến thời điểm bảo vệ.

Trong môi trường dev, lệnh `mvn flyway:info` hiển thị trạng thái apply hiện tại của từng migration; lệnh `mvn flyway:migrate` apply các migration còn thiếu. Trong môi trường production, Flyway tự chạy lúc Spring Boot khởi động — nếu một migration fail, backend không up được (`@SpringBootApplication` fail-fast), tránh trường hợp ứng dụng chạy trên schema không đúng phiên bản. Quy tắc bất di bất dịch: mọi thay đổi schema đều phải thông qua một file `V<n>__<name>.sql` *mới* — tuyệt đối không sửa file đã apply (vì Flyway lưu checksum và sẽ phát hiện sửa đổi, dừng ứng dụng).

### 4.5.2. Danh sách mười ba migration

**Bảng 4.2. Danh sách 13 file Flyway migration (V1 đến V13)**

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

### 4.5.3. Chi tiết các migration trọng tâm

**V1 — `init.sql`** thiết lập baseline schema: tạo bảng `app_meta(key, value, updated_at)` lưu metadata phiên bản và mốc khởi tạo, đồng thời bật Flyway version tracking. Đây là migration nhỏ nhất nhưng quan trọng — mọi migration sau đều giả định baseline này đã apply.

**V2 — `auth.sql`** tạo hai bảng cho luồng xác thực Web Admin: `admin_user(id, email UNIQUE, password_hash BCrypt, role, created_at, updated_at)` và `refresh_token(id, admin_user_id FK, token_hash SHA-256, expires_at, revoked_at)`. Cột `token_hash` lưu hash thay vì token gốc — nếu DB bị leak, attacker không thể dùng lại token đã cấp.

**V3 — `bot.sql`** tạo bốn bảng cho luồng bot Telegram: `telegram_user(id, telegram_user_id UNIQUE, username, first_name, last_name, language_code, created_at)` lưu mọi user đã tương tác; `user_role(id, telegram_user_id FK, role, status)` cho phép một user có nhiều vai trò (khách + shipper); `conversation_state(chat_id, current_state, payload JSONB, updated_at)` lưu FSM hội thoại; `processed_update(update_id PRIMARY KEY, processed_at)` đảm bảo idempotency Telegram update.

**V4 — `order.sql`** tạo bốn bảng nghiệp vụ đơn hàng. Đáng chú ý: cột `orders.status` dùng `VARCHAR(20)` với CHECK constraint enum thay vì PostgreSQL ENUM type — để dễ thêm trạng thái mới mà không phải `ALTER TYPE`. Index `idx_orders_status_created` được tạo cho dashboard truy vấn theo trạng thái và khoảng ngày. `status_history` là bảng append-only — không UPDATE, chỉ INSERT — phục vụ audit trail.

**V5 — `admin.sql`** seed một admin user đầu tiên (`admin@shop.local`) với password BCrypt cost 10. Migration này chỉ có ý nghĩa cho môi trường dev — production cần thay password ngay sau khi deploy.

**V6 — `delivery.sql`** tạo hai bảng cốt lõi của giao hàng: `shipper_profile` và `delivery_assignment`. Đáng chú ý: `delivery_assignment` có cột `expires_at` cho offer TTL (mặc định 60 giây) và cột `route_polyline` lưu encoded polyline cho lộ trình lịch sử.

**V7 — `location.sql`** tạo bảng `location_ping(id, assignment_id FK, lat, lng, accuracy, heading, ping_at)` với index BRIN trên `ping_at` (thay vì BTREE) — phù hợp cho timeseries data append-only, tiết kiệm 95% disk space so với BTREE.

**V8 — `assignment_unique_started.sql`** tạo một partial unique index khoá business rule "mỗi shipper chỉ có tối đa một assignment STARTED tại một thời điểm" — tránh ambiguity khi Live Location ping đến mà query trả về nhiều assignment. Đây là defense-in-depth: kể cả khi code Java có race condition thì DB cũng từ chối INSERT.

**V9 — `payment.sql`** tạo `payment(id, order_id UNIQUE FK, amount, status, gateway, vnp_TxnRef UNIQUE, created_at, paid_at, expired_at)` và `payment_transaction(id, payment_id FK, event_type, raw_payload JSONB, created_at)`. Cột JSONB cho phép lưu nguyên payload IPN VNPay phục vụ forensics — đây là phát hiện từ code review P7 và được khắc phục hậu kỳ.

**V10 — `rating.sql`** tạo `rating(id, order_id UNIQUE, shipper_id FK, customer_id FK, stars CHECK 1..5, comment, rated_at)`. UNIQUE order_id chống đánh giá trùng.

**V11 — `demo_seed.sql`** seed dữ liệu mẫu cho reviewer demo: một admin (`shop@example.com` với password mạnh `Demo@Shop2026!`), mười sản phẩm món ăn đa dạng (phở, bún, cơm tấm, trà sữa…), sáu telegram user (ba khách, hai shipper, một shop owner), ba mươi đơn rải đều ba mươi ngày qua với phân bố trạng thái thực tế (60% DELIVERED, 20% ASSIGNED/STARTED, 10% CANCELLED, 10% PENDING/CONFIRMED), mười tám assignment, mười rating và mười một payment. Seed này giúp Dashboard có ngay dữ liệu để hiển thị KPI và biểu đồ.

```sql
-- Trích V11: seed sản phẩm
INSERT INTO product (id, code, name, price, description, image_url, available)
VALUES (1, 'PHO-BO', 'Phở bò tái', 55000,
        'Phở bò Hà Nội truyền thống, nước dùng đậm đà, thịt tái mềm.',
        '/images/pho-bo.jpg', true);
```

**V12 — `shipper_rating.sql`** bổ sung khả năng shipper đánh giá khách hàng (chiều ngược lại của V10): tạo bảng `shipper_rating(id, order_id UNIQUE, shipper_id, customer_id, stars CHECK 1..5, comment, rated_at)` và thêm hai cột `rating_avg NUMERIC(3,2)`, `rating_count INT` vào `telegram_user`. Đánh giá theo chiều này không công khai — chỉ admin xem được — phục vụ cảnh báo khách hàng khó tính cho shipper khác.

**V13 — `shop_config.sql`** tạo bảng `shop_config` lưu cấu hình "có thể đổi tại runtime" của shop dưới dạng *singleton row* — `id SMALLINT PRIMARY KEY CHECK (id = 1)` ràng buộc luôn chỉ có đúng một dòng. Bảng gồm bốn nhóm cột: (i) brand — `name`, `tagline`, `logo_url`, `brand_primary VARCHAR(7)`, `brand_secondary VARCHAR(7)` cùng `contact_phone`, `contact_email`, `opening_hours`; (ii) pickup — `pickup_lat NUMERIC(10,7)`, `pickup_lng`, `pickup_address` (thay cho hardcode toạ độ Hoàn Kiếm trong YAML); (iii) phí giao — `fee_base NUMERIC(12,2)`, `fee_per_km`, `free_km NUMERIC(8,3)`; (iv) `updated_at TIMESTAMPTZ`. Migration đồng thời `INSERT … ON CONFLICT DO NOTHING` cho `id=1` với default trùng giá trị hiện hành trong YAML, nên hệ thống boot ra hành vi không khác trước đến khi admin chỉnh sửa qua trang Settings (4.4.3). Đây là nền tảng để Mini App "đổi áo" theo từng shop mà không cần redeploy.

## 4.6. Triển khai bằng Docker Compose

### 4.6.1. Sơ đồ năm container và quy trình build

Hệ thống được đóng gói thành năm container chạy đồng thời, được điều phối bởi file `infra/docker-compose.yml`. Quy trình build image gồm hai loại Dockerfile: (i) backend Dockerfile multi-stage — pha builder dùng `eclipse-temurin:17-jdk` chạy `./mvnw -B -DskipTests package`, pha runtime dùng `eclipse-temurin:17-jre-alpine` chỉ chứa fat jar và JRE, image cuối khoảng 220 megabyte (so với 450 MB nếu giữ JDK đầy đủ); user `app` non-root được tạo và switch trước `ENTRYPOINT`; (ii) frontend Dockerfile cho mỗi workspace — pha builder dùng `node:22-alpine` chạy `pnpm install --frozen-lockfile && pnpm build`, pha runtime dùng `nginx:1.27-alpine` chỉ chứa thư mục `dist/` và `nginx.conf` con; mỗi image frontend khoảng mười megabyte.

### 4.6.2. Năm service và healthcheck chain

Năm service được khai báo trong `docker-compose.yml`:

- **`postgres`** — image `postgres:16-alpine`, volume `pgdata:/var/lib/postgresql/data`, healthcheck `pg_isready -U app -d shop_delivery` với interval 10 giây. Không expose port ra host — chỉ truy cập được qua network `shopnet`.
- **`backend`** — build từ `backend/Dockerfile`, depends_on `postgres: service_healthy`, môi trường `SPRING_PROFILES_ACTIVE=prod`, healthcheck gọi `wget` đến `/actuator/health` và grep `"status":"UP"`. Expose nội bộ 8080.
- **`miniapp`** — build từ `frontend/miniapp/Dockerfile`, healthcheck `/healthz` trả `ok`.
- **`webadmin`** — build tương tự `miniapp`.
- **`nginx`** — image `nginx:alpine`, depends_on cả ba service trên `service_healthy`, expose port `80:80` ra host, volume mount `nginx.conf:ro`.

Thứ tự khởi động được điều phối bởi `depends_on: condition: service_healthy`: postgres lên healthy trước, sau đó backend start và đợi Flyway migrate xong, tiếp theo miniapp và webadmin (chỉ cần Vite build done), cuối cùng nginx mở port 80 ra host. Tổng thời gian từ `docker compose up -d --build` đến hệ thống ready là khoảng ba phút cho lần build đầu (Maven download dependency, pnpm install) và khoảng ba mươi giây cho các lần build sau (cache hit).

### 4.6.3. Cấu hình Nginx reverse proxy

Nginx ở rìa hệ thống đảm nhận bốn nhiệm vụ: (i) serve static SPA — `/miniapp/*` map sang container `miniapp:80`, `/admin/*` map sang `webadmin:80`; (ii) proxy `/api/*` → `backend:8080` với `proxy_set_header X-Real-IP` và `X-Forwarded-For`; (iii) proxy `/ws/*` với header `Upgrade: $http_upgrade` và `Connection: "upgrade"` để giữ WebSocket connection — `proxy_read_timeout 86400` để duy trì kết nối dài; (iv) phục vụ healthcheck `/healthz`. CORS được khoá theo domain triển khai cụ thể (không dùng wildcard `*`).

### 4.6.4. Cổng và URL exposed

**Bảng 4.3. Cổng và URL exposed trong môi trường production-like**

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

### 4.6.5. Quy trình deploy ba lệnh

Toàn bộ quy trình deploy lên một máy chủ mới chỉ cần ba lệnh shell:

```bash
git clone <repo-url> && cd KhoaLuan-GiaoHang
cp .env.example .env       # → mở .env, điền 4 secret bắt buộc
docker compose -f infra/docker-compose.yml up -d --build
```

Sau khoảng ba phút (lần đầu) hoặc ba mươi giây (cache), toàn bộ stack sẵn sàng phục vụ tại `http://localhost`. Lệnh `docker compose -f infra/docker-compose.yml logs -f backend` cho phép theo dõi log realtime để xác minh Flyway migrate xong và bot đã kết nối Telegram thành công.

## 4.7. Cấu hình môi trường

### 4.7.1. Cấu trúc file `.env.example`

File `.env.example` ở thư mục gốc dự án (Docker Compose tự động nạp qua tham chiếu `../.env`) được chia thành sáu nhóm với chú thích rõ ràng. Bốn biến bắt buộc được đánh dấu `▼ FILL IN ▼`: `BOT_TOKEN` (token từ BotFather), `BOT_USERNAME` (tên bot không kèm `@`), `JWT_SECRET` (sinh bằng `openssl rand -hex 32`, tối thiểu 32 byte), và cặp `VNPAY_TMN_CODE` / `VNPAY_HASH_SECRET` (từ portal sandbox VNPay). Các biến còn lại có giá trị mặc định khả dụng: `DB_HOST=postgres`, `DB_PORT=5432`, `DB_NAME=shop_delivery`, `DB_USER=app`, `DB_PASSWORD=app_demo_password`, `BOT_MODE=polling`, cũng như các tham số shop (`SHOP_PICKUP_LAT=21.0285`, `SHOP_PICKUP_LNG=105.8542`, `SHOP_FEE_BASE=15000`, `SHOP_FEE_PER_KM=5000`, `SHOP_FEE_FREE_KM=1.0`).

### 4.7.2. Tài khoản demo và quy ước

Sau khi V11 seed apply, hệ thống có sẵn một tài khoản admin demo với thông tin: email `shop@example.com`, password `Demo@Shop2026!`. Đây là chuỗi password mạnh (kết hợp chữ hoa, chữ thường, số, ký tự đặc biệt và độ dài 12 ký tự) — khác biệt rõ ràng so với chuỗi `admin123` đã được ghi nhận trong hạn chế ban đầu và được thay thế trong giai đoạn hoàn thiện. Khi deploy lên môi trường thực, người triển khai cần thay ngay password admin (qua trang Settings hoặc query trực tiếp DB) trước khi giao Mini App cho khách hàng cuối cùng.

### 4.7.3. Profile `dev` và `prod`

Spring Boot profile được kích hoạt qua biến `SPRING_PROFILES_ACTIVE`. Profile `dev` (mặc định khi chạy local) cho phép header `X-Dev-User-Id` để test Mini App ngoài Telegram trên trình duyệt thường (header này bị silently ignored ở prod), log level DEBUG, CORS cho phép `localhost:5173` và `localhost:5174` (hai cổng dev server Vite). Profile `prod` (kích hoạt trong Docker Compose) bật fail-fast cho secret: nếu thiếu bất kỳ biến môi trường nào trong nhóm bắt buộc (`BOT_TOKEN`, `JWT_SECRET`, `VNPAY_HASH_SECRET`), Spring Boot sẽ throw `BeanCreationException` và exit code 1 — tránh trường hợp deploy lên prod với credential test còn sót lại. Log level prod là INFO, CORS whitelist theo domain cụ thể, và bắt buộc HTTPS cho Mini App URL.

### 4.7.4. Biến môi trường VNPay

Bốn biến VNPay được nạp riêng để cô lập credential cổng thanh toán: `VNPAY_TMN_CODE` (mã merchant, public), `VNPAY_HASH_SECRET` (khoá ký HMAC-SHA512, không bao giờ leak), `VNPAY_RETURN_URL` (URL khách được redirect về sau khi thanh toán — render trang HTML kết quả, không cập nhật DB), `VNPAY_IPN_URL` (URL VNPay gọi server-to-server với payload kết quả — là nguồn sự thật để cập nhật `Payment.status`). Trong môi trường dev local không có public IP, người dùng cần dùng dịch vụ tunnel (ngrok, cloudflared) để VNPay có thể callback IPN đến máy local — bằng cách đặt `VNPAY_IPN_URL=https://<tunnel-id>.ngrok.app/api/payment/vnpay/ipn`.

## 4.8. Chiến lược kiểm thử

### 4.8.1. Backend testing pyramid

Backend hệ thống áp dụng mô hình *testing pyramid* tiêu chuẩn với ba tầng. Tầng đáy gồm khoảng 190 *unit test* sử dụng Mockito 5 và AssertJ 3, kiểm thử các service, handler và helper ở mức class đơn lẻ — mọi dependency được mock và assertion tập trung vào logic chuyển trạng thái. Tầng giữa gồm khoảng 28 *integration test* (suffix `IT.java`, chạy bởi Maven Failsafe plugin) sử dụng Testcontainers PostgreSQL 16 thật và `@SpringBootTest` — kiểm thử các tương tác đa-component (repository + service + listener + transaction propagation) trên một schema thật, đã apply đầy đủ mười hai Flyway migration. Tầng đỉnh là các *slice test* dùng `@WebMvcTest` để kiểm thử controller validation và JSON shape mà không cần boot toàn bộ context Spring — phương án này nhanh hơn `@SpringBootTest` nhưng vẫn xác minh được binding và serialization.

### 4.8.2. Frontend testing

Frontend áp dụng Vitest 1.x kết hợp Testing Library cho các test JavaScript. Cấu trúc test theo workspace: (i) `@shop/shared` chạy trong môi trường node (vì không dùng DOM), có chín test bao phủ `formatVnd`, `formatDateTime`, `haversineKm` cho các edge case (số âm, NaN, khoảng cách bằng 0); (ii) `@shop/miniapp` chạy trong jsdom, có sáu test cho Zustand store `useCart` (thêm sản phẩm trùng phải gộp số lượng, xoá sản phẩm cuối phải làm trống, persist phải hoạt động); (iii) `@shop/webadmin` chạy trong jsdom, có sáu test cho `auth-store` (login lưu token, logout clear store) và `OrderStatusBadge` (render đúng màu theo trạng thái). Tổng cộng 21 test frontend, chạy hoàn tất trong khoảng năm giây.

### 4.8.3. Code review process theo phương pháp GSD

Mỗi pha trong quy trình GSD (P0 đến P9) đều có hai artefact bắt buộc liên quan kiểm thử: file `PLAN-CHECK.md` (sản phẩm của bước plan-checker — review plan trước khi chạm code) và file `<phase>-REVIEW.md` (sản phẩm của bước code-review — review diff sau khi execute). Tổng cộng có chín file code review, đánh giá theo ba mức độ: CRITICAL (phải vá ngay, block ship), IMPORTANT (vá trong sprint hiện tại) và MINOR (lưu vào backlog). Phương pháp này đã catch năm critical bug trước khi đi tiếp: CR-1 STOMP SUBSCRIBE leak (Mini App có thể subscribe `/user/*` của user khác), CR-2 wrong-shipper attribution (Live Location của shipper A có thể bị gán sang assignment STARTED của shipper B), CR-3 NPE trong channel message khi `from` null, CR-4 IPN audit gap (`PaymentTransaction` chưa được persist cho IPN signature invalid), CR-5 `ShipperState` enum mismatch (Java enum không khớp CHECK constraint của Postgres).

### 4.8.4. CI verify trên mỗi commit

Quy trình verify được chạy thủ công trước mỗi commit (chưa có CI server tự động trong scope khoá luận, nhưng pipeline đã được thiết kế trong tài liệu hướng phát triển): lệnh `./mvnw verify` build và chạy toàn bộ test backend (Surefire + Failsafe + Testcontainers); lệnh `pnpm -r build` build cả ba workspace frontend; lệnh `pnpm -r type-check` chạy `tsc --noEmit` đảm bảo không có lỗi TypeScript. Tỉ lệ build green trên mỗi commit đạt 100% — không có commit nào ở branch `main` mà `mvn verify` fail.

### 4.8.5. Thống kê test theo module

Bảng 4.4 dưới đây tổng hợp số lượng test theo từng module và phân loại unit / integration.

**Bảng 4.4. Phân bổ test theo module (tính đến thời điểm bảo vệ)**

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

Bên cạnh test tự động, hệ thống có file `RUNBOOK.md` ở thư mục gốc mô tả tám bước smoke test thủ công (P1 → P8) chạy end-to-end qua Telegram thật và VNPay sandbox: đăng nhập admin, tạo sản phẩm, đặt đơn từ Mini App, thanh toán VNPay, gán shipper, shipper accept và share Live Location, đánh dấu đã giao, đánh giá năm sao. Smoke test này được chạy cho mỗi phiên bản phát hành để đảm bảo hệ thống vận hành đúng trong môi trường tích hợp đầy đủ với các dịch vụ ngoài.

## 4.9. Kết luận chương

Chương 4 đã trình bày đầy đủ các quyết định hiện thực then chốt của hệ thống: môi trường phát triển thống nhất với JDK 17, Node 22 và Docker; cấu trúc dự án ba lớp tách bạch backend, frontend và infra; hiện thực tám mô-đun backend theo đúng đồ thị DAG đã thiết kế ở Chương 3, với các đặc điểm thiết kế đáng chú ý gồm State Machine cho `Order` và `DeliveryAssignment`, IPN-as-source-of-truth cho VNPay, FSM hội thoại với payload JSONB cho luồng đăng ký shipper, và `@TransactionalEventListener(AFTER_COMMIT)` cho mọi giao tiếp cross-module; hiện thực Mini App và Web Admin với cùng nền tảng React 18 nhưng có bundle và route tách bạch; quản lý schema qua mười hai file Flyway migration với V11 seed dữ liệu demo và V12 bổ sung shipper rating; đóng gói triển khai bằng Docker Compose năm container với healthcheck chain ba lệnh; cấu hình biến môi trường với fail-fast cho production; và chiến lược kiểm thử nhiều tầng với 253 test backend cộng 21 test frontend, tổng cộng 274 test, đảm bảo tỉ lệ build green 100% trên mỗi commit. Chương 5 tiếp theo tổng kết các kết quả đạt được, hạn chế đã nhận diện trung thực và đề xuất các hướng phát triển tiếp theo cho hệ thống.
