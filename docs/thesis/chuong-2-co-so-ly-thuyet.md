# Chương 2. Cơ sở lý thuyết

Chương này trình bày các nền tảng công nghệ then chốt của đề tài theo lớp: Telegram (cổng vào), Spring Boot 3 (máy chủ), React 18 (trình duyệt), PostgreSQL, VNPay, Modular Monolith với DDD-lite, TDD và Docker Compose.

## 2.1. Nền tảng Telegram

### 2.1.1. Telegram Bot API

Telegram Bot API là giao diện HTTP giữa server và bot. Bot nhận sự kiện qua *long polling* (`GET /getUpdates` với `offset`, Telegram giữ kết nối tới khi có sự kiện mới hoặc timeout — phù hợp môi trường phát triển vì không cần URL HTTPS public) hoặc *webhook* (`setWebhook`, Telegram chủ động đẩy `Update` — phù hợp production vì giảm tải và phản hồi nhanh hơn).

Đề tài dùng `telegrambots-springboot-longpolling-starter 7.x`, long polling cho cả `dev` và `prod`; webhook là hướng phát triển ở chương 5. Các trường `Update` dùng trực tiếp: `message`, `edited_message` (Live Location), `callback_query` (inline keyboard), `contact` (đăng ký shipper qua `KeyboardButton.request_contact`). Do `callback_data` của `InlineKeyboardButton` giới hạn **64 byte UTF-8**, hệ thống chỉ encode `ACTION:param1:param2` rồi tra DB tại handler thay vì nhét toàn bộ payload [25].

```java
InlineKeyboardButton button = InlineKeyboardButton.builder()
    .text("Nhận đơn")
    .callbackData("OFFER_ACCEPT:" + assignmentId.toString())  // < 64 byte
    .build();
```

### 2.1.2. Telegram Mini App (Web App SDK)

Mini App là WebView nhúng trong cửa sổ chat, truy cập API native qua `window.Telegram.WebApp`. Telegram truyền vào URL tham số `initData` — query string ký HMAC-SHA256 bằng khoá bí mật `bot_token`; backend xác minh `initData` để chắc chắn yêu cầu đến từ phiên Telegram hợp pháp:

```java
String dataCheckString = params.entrySet().stream()
    .filter(e -> !"hash".equals(e.getKey()))
    .sorted(Map.Entry.comparingByKey())
    .map(e -> e.getKey() + "=" + e.getValue())
    .collect(Collectors.joining("\n"));

byte[] secret = hmacSha256("WebAppData".getBytes(), botToken.getBytes());
byte[] computed = hmacSha256(secret, dataCheckString.getBytes());
boolean ok = MessageDigest.isEqual(computed, hexToBytes(receivedHash));
```

SDK còn có `WebApp.ready()`, `WebApp.expand()`, `WebApp.openLink(url)` (dùng redirect sang VNPay thay vì `window.location.href` vốn làm thoát Mini App), `WebApp.themeParams` (theme dark/light) và `BackButton` [26].

### 2.1.3. Telegram Live Location

*Live Location* chia sẻ vị trí GPS liên tục trong 15 phút, 1 giờ hoặc 8 giờ, kèm trường tuỳ chọn `heading` và `horizontal_accuracy`, cập nhật mỗi 5–10 giây. `Update.message.location` phát *một lần* khi shipper bắt đầu share (chứa `live_period`); `Update.edited_message.location` phát *mỗi lần* có vị trí mới. Cả hai được Telegram ký HMAC nên backend chỉ cần xác minh `update.from.id`. Khi shipper "Stop sharing", `edited_message.location` cuối cùng không có `live_period` — dấu hiệu đã ngừng chia sẻ. `LiveLocationHandler` tra `DeliveryAssignment` đang `STARTED` của shipper, lưu `location_ping` (V7) và phát `LocationPingReceivedEvent` cho `LocationBroadcaster` đẩy qua WebSocket.

## 2.2. Spring Boot 3 và Java 17

### 2.2.1. Spring MVC và REST Controller

REST controller khai báo bằng `@RestController` trên nền dispatcher servlet của Spring MVC, với annotation tuyến đường (`@GetMapping`, `@PostMapping`) và tham số (`@PathVariable`, `@RequestBody`, `@RequestParam`); body validate qua Jakarta Bean Validation với `@Valid`.

```java
@PostMapping("/{id}/cancel")
public OrderResponse cancel(
        @PathVariable UUID id,
        @CurrentUser TelegramUser user,
        @Valid @RequestBody CancelOrderRequest req) {
    return orderService.cancel(id, user.id(), req.reason());
}
```

### 2.2.2. Spring Data JPA và Hibernate 6

Spring Data JPA cung cấp tầng repository trên JPA, hiện thực bằng Hibernate 6:

- *`@Transactional`* mặc định `Propagation.REQUIRED` cho hầu hết service.
- *`Propagation.REQUIRES_NEW`* cho `OrderService.confirmAfterPayment`: nếu để mặc định, listener fire ở AFTER_COMMIT của transaction ngoài không commit được order update (lỗi phát hiện trong khâu code review của pha P7 — xem Chương 4 mục 4.7.3).
- *`@Version`* (Optimistic Lock) trên `Order`, `Payment`, `ShipperProfile` — các thực thể nhiều luồng cùng cập nhật — để bắt `OptimisticLockingFailureException` và retry.
- *`@JdbcTypeCode(SqlTypes.JSON)`* — Hibernate 6 map field Java sang cột JSONB không cần `hypersistence-utils`.

```java
@Entity
@Table(name = "payment_transaction")
public class PaymentTransaction {
    @Id @GeneratedValue private Long id;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;
    // ...
}
```

### 2.2.3. Spring Security 6 với JWT và custom filter

Spring Security 6 cấu hình qua bean `SecurityFilterChain` với hai filter custom: `TelegramAuthFilter` đặt trước `UsernamePasswordAuthenticationFilter`, đọc header `X-Telegram-Init-Data`, xác minh HMAC, nạp `TelegramUser` vào `SecurityContext`; `JwtAuthFilter` cho `/api/admin/**`, đọc `Authorization: Bearer <jwt>`, xác minh chữ ký HS512 với `JWT_SECRET`, nạp `AdminUser`. Quyền mức phương thức kiểm tra bằng `@PreAuthorize`:

```java
@PreAuthorize("hasRole('SHOP_OWNER')")
@GetMapping("/admin/orders")
public Page<AdminOrderRow> list(...) { ... }
```

Argument resolver `@CurrentUser` inject thực thể đã xác thực vào handler: tránh đọc `SecurityContextHolder` thủ công và chống giả mạo `customerId` qua path parameter, vì danh tính lấy từ context đã được filter xác minh, không forge được như `@PathVariable Long customerId`.

### 2.2.4. Spring Application Events và `@TransactionalEventListener`

*Application Events* giao tiếp trong tiến trình theo mô hình publish-subscribe; `@TransactionalEventListener` gắn cơ chế này với pha transaction. Mọi listener cross-module dùng `phase = AFTER_COMMIT`:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderCreated(OrderCreatedEvent event) {
    adminBroadcaster.broadcast(event);
    botSender.notifyOwner(event);
}
```

Chọn `AFTER_COMMIT` thay vì `@EventListener` để *tránh gửi notification khi transaction rollback*: nếu business rule fail sau khi insert Order thì đơn không tồn tại nhưng khách đã nhận tin, gây message mâu thuẫn. Pitfall kèm theo: listener `AFTER_COMMIT` muốn update DB phải mở transaction mới bằng `REQUIRES_NEW` — lý do `OrderService.confirmAfterPayment` nêu ở 2.2.2.

### 2.2.5. Spring WebSocket với STOMP

Spring WebSocket hỗ trợ STOMP với fallback SockJS. Hệ thống dùng frame *CONNECT* (mở kết nối, gửi header xác thực) và *SUBSCRIBE* (đăng ký destination `/topic/...` broadcast hoặc `/user/...` per-user); *SEND* không dùng vì luồng chỉ một chiều server→client. `ChannelInterceptor` xác thực header trên CONNECT và áp allowlist cho SUBSCRIBE (chống cross-user data leak — L7 trong defense-in-depth); server đẩy message qua `SimpMessagingTemplate`:

```java
template.convertAndSendToUser(
    customerId.toString(),
    "/queue/order/" + orderId + "/location",
    locationDto);
```

### 2.2.6. Spring Scheduling

`@Scheduled` cùng `@EnableScheduling` định nghĩa tác vụ theo lịch: `PaymentExpiryScheduler` với `fixedDelay = 60_000` (60 giây) quét `Payment(PENDING)` quá 15 phút và đánh dấu FAILED; bot cleanup xoá `processed_update` cũ hơn 24 giờ và `conversation_state` stale. `@Scheduled` chạy trên *mọi* instance khi scale ngang, nên môi trường HA cần *ShedLock* với Postgres advisory lock để chỉ một instance thực thi mỗi tick (hạng mục production hardening — xem Chương 5 mục 5.3.3).

## 2.3. React 18 và hệ sinh thái Vite 5

### 2.3.1. Functional Components và Hooks

React 18 dùng *Functional Component* kết hợp Hooks: `useState`, `useEffect` (side effect như gọi API, subscribe WebSocket, với dependency array), `useMemo` (memoize giá trị derived tốn kém) và `useCallback` (memoize hàm, tránh identity mới mỗi render).

### 2.3.2. React Router v6

Mini App và Web Admin routing client-side bằng `BrowserRouter` của React Router v6, kết hợp `lazy + Suspense` để code-split mỗi page:

```tsx
const ReportsPage = lazy(() => import('./pages/ReportsPage'));
// ...
<Route path="/reports" element={
  <Suspense fallback={<Spinner/>}><ReportsPage/></Suspense>
} />
```

### 2.3.3. TanStack Query

TanStack Query quản lý server state: `queryKey` phân cấp (`['orders', 'list', filter]`) để invalidate selectively; `refetchInterval` poll trạng thái VNPay khi PENDING; `invalidateQueries` gọi sau mutation thành công.

### 2.3.4. Zustand

Zustand quản lý client state với API tối giản, không cần Provider. Đề tài dùng một store giỏ hàng với middleware `persist` lưu `localStorage` và một store session admin (access + refresh token); selector pattern tránh re-render thừa:

```ts
const cartCount = useCartStore(s => s.items.reduce((n, i) => n + i.qty, 0));
```

### 2.3.5. TailwindCSS

Tailwind là framework CSS utility-first với JIT chỉ sinh class thực dùng. Đề tài cấu hình dark mode chiến lược `class` cho Web Admin và `media` cho Mini App (theo theme Telegram); CSS bundle Web Admin sau build < 40 KB gzip nhờ purge utility không dùng.

### 2.3.6. Recharts

Recharts vẽ biểu đồ SVG cho React: `LineChart`, `BarChart`, `PieChart` cùng `Tooltip`, `Legend`, `ResponsiveContainer`. Một lưu ý ghi nhận trong khâu code review: prop `formatter` của `Tooltip` khai báo trả về `string | number | ReactNode` tuỳ phiên bản, dễ lệch type khi trả về tuple `[label, value]`; xử lý bằng explicit cast.

### 2.3.7. Leaflet và react-leaflet

Leaflet là thư viện bản đồ mã nguồn mở, `react-leaflet` là wrapper React. Đề tài dùng tile OpenStreetMap miễn phí với `MapContainer`, `TileLayer`, `Marker` (điểm xuất phát, đích đến, vị trí shipper) và `Polyline` (đường shipper đã qua, vẽ từ chuỗi `location_ping`).

### 2.3.8. STOMP client `@stomp/stompjs` với SockJS

Client kết nối WebSocket qua `@stomp/stompjs` với `sockjs-client`, gửi header xác thực trên CONNECT frame:

```ts
const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  connectHeaders: {
    'X-Telegram-Init-Data': WebApp.initData,
  },
});
client.onConnect = () => {
  client.subscribe(`/user/queue/order/${orderId}/location`, msg => {
    updateMarker(JSON.parse(msg.body));
  });
};
client.activate();
```

## 2.4. PostgreSQL 16 và Flyway

### 2.4.1. Migration versioning với Flyway

Flyway đặt tên migration theo quy ước `V<version>__<name>.sql` và mỗi lần boot so sánh bảng meta `flyway_schema_history` với danh sách file để xác định migration cần apply. Đề tài có 11 migration `V1__init.sql` đến `V11__demo_seed.sql`, mỗi pha một migration. Nếu file đã apply bị sửa, checksum không khớp và Flyway từ chối khởi động; cách đúng là tạo migration mới (`V12`, `V13`...), chỉ dùng `flyway repair` trong trường hợp đặc biệt.

### 2.4.2. Các kiểu dữ liệu nâng cao

Năm kiểu dữ liệu nâng cao của PostgreSQL được dùng: *UUID* làm khoá chính cho `orders`, `payment`, `delivery_assignment`, `refresh_token` (chống enumeration); *JSONB* lưu `payment_transaction.raw_payload` (audit payload IPN VNPay) và `conversation_state.data` (state FSM của bot); *NUMERIC(12,2)* cho giá tiền, *NUMERIC(10,7)* cho lat/lng (đủ chính xác tới mét); *TIMESTAMPTZ* cho mọi timestamp, tránh ambiguity nhiều múi giờ; *SMALLINT CHECK (stars BETWEEN 1 AND 5)* giới hạn `rating.stars` tại tầng DB.

### 2.4.3. Chiến lược index

Bốn loại index được dùng:

- *B-tree* (mặc định) cho hầu hết index.
- *Partial index* — `idx_product_active ON product(is_active) WHERE is_active = TRUE`; tương tự `idx_refresh_token_expiry ... WHERE revoked = FALSE` (V5).
- *Composite index* — `idx_orders_status_created ON orders(status, created_at DESC)` cho query "đơn theo status, mới nhất trước".
- *Partial UNIQUE index* — `uq_assignment_shipper_started ON delivery_assignment(shipper_id) WHERE status = 'STARTED'` (V8) khoá ràng buộc "một shipper tối đa một assignment đang chạy" tại tầng DB, vá lớp L11 của defense-in-depth.

### 2.4.4. Ràng buộc CHECK cho giá trị enum

Vì ENUM của PostgreSQL kém linh hoạt khi cần thêm giá trị, đề tài dùng `VARCHAR(16)` kết hợp ràng buộc tại tầng ứng dụng (Java enum); trường nhạy cảm bổ sung `CHECK` chốt giá trị tại tầng DB, ví dụ `stars BETWEEN 1 AND 5` ở bảng `rating` (V10).

### 2.4.5. Time-series với `DATE_TRUNC` và `generate_series`

Báo cáo doanh thu theo ngày/tuần/tháng cần aggregate theo bucket thời gian và điền bucket trống bằng 0: `DATE_TRUNC(:bucket, ts)` cắt timestamp về đầu khoảng, `generate_series` sinh dãy bucket:

```sql
SELECT bucket, COALESCE(SUM(total), 0) AS revenue
FROM generate_series(:from, :to, INTERVAL '1 day') AS bucket
LEFT JOIN orders ON DATE_TRUNC('day', created_at) = bucket
                 AND status = 'DELIVERED'
GROUP BY bucket
ORDER BY bucket;
```

`:bucket` bind từ Java sau khi *đối chiếu enum whitelist* `{'day', 'week', 'month'}` thay vì nhúng trực tiếp giá trị client gửi (lớp L9 của defense-in-depth — chống SQL injection ở Reports).

### 2.4.6. Mẫu status history qua bảng audit riêng

Thay vì trigger DB, đề tài *audit ở tầng ứng dụng* qua bảng `status_history` (V4): mỗi lần chuyển trạng thái, service insert một dòng `(order_id, from_status, to_status, changed_by_user_id, changed_at, note)`, phục vụ truy vết và render timeline trong Order Detail (hướng phát triển sau khoá luận).

## 2.5. Tích hợp VNPay sandbox

### 2.5.1. Cơ chế ký HMAC-SHA512

VNPay chống giả mạo bằng *HMAC-SHA512*. Thuật toán ký theo đặc tả: lấy mọi tham số trừ `vnp_SecureHash` và `vnp_SecureHashType`; URL-encode *giá trị* (không encode khoá) theo `application/x-www-form-urlencoded` (space → `+`); sort khoá theo alphabet ASCII tăng dần; nối thành `key1=value1&key2=value2&...`; tính HMAC-SHA512 với khoá `vnpay.hash-secret`; so sánh bằng `MessageDigest.isEqual` (constant time, chống timing attack).

```java
byte[] expected = HexFormat.of().parseHex(receivedHash);
byte[] computed = hmacSha512(secret, signData);
if (!MessageDigest.isEqual(expected, computed)) {
    throw new InvalidSignatureException();
}
```

### 2.5.2. Hình dạng tham số request

**Bảng 2.1. Tham số bắt buộc khi tạo URL thanh toán VNPay**

| Tham số | Giá trị |
|---|---|
| `vnp_Version` | `2.1.0` |
| `vnp_Command` | `pay` |
| `vnp_TmnCode` | từ env `VNPAY_TMN_CODE` |
| `vnp_Amount` | `order.total × 100` (đơn vị: VND × 100) |
| `vnp_CurrCode` | `VND` |
| `vnp_TxnRef` | `"<orderCode>-<epochMs>"`, UNIQUE |
| `vnp_OrderInfo` | `"Thanh toan don hang <orderCode>"` (không dấu) |
| `vnp_Locale` | `vn` |
| `vnp_CreateDate` | định dạng `yyyyMMddHHmmss`, múi giờ GMT+7 |
| `vnp_ExpireDate` | `vnp_CreateDate + 15 phút` |
| `vnp_SecureHash` | HMAC-SHA512 trên các tham số đã sort + url-encode |

### 2.5.3. Ba endpoint: create, return và IPN

`POST /api/payment/vnpay/create` auth qua Telegram initData, tạo `Payment(PENDING)` và ký URL trả về cho Mini App. `GET /api/payment/vnpay/return` là public, xác minh chữ ký rồi **chỉ** render trang HTML cho khách mà không cập nhật DB. `POST /api/payment/vnpay/ipn` là public server-to-server, xác minh chữ ký, check số tiền và idempotency, cập nhật `payment.status` rồi publish event để `OrderService.confirmAfterPayment` chạy trong transaction mới.

### 2.5.4. Bảng mã RspCode

**Bảng 2.2. Bảng mã `RspCode` trong giao thức IPN của VNPay**

| RspCode | Ý nghĩa |
|---|---|
| `00` | Confirm Success |
| `01` | Order not found |
| `02` | Order already confirmed (idempotent replay) |
| `04` | Invalid amount |
| `97` | Invalid signature |
| `99` | Unknown error |

### 2.5.5. Yêu cầu idempotency

VNPay được phép retry IPN nếu lần đầu không nhận `RspCode=00`, nên IPN phải idempotent: lần đầu chuyển trạng thái và trả `00`; các lần sau thấy `payment.status != PENDING` thì trả `02` mà không đổi DB. Mọi event IPN — kể cả invalid signature — đều ghi vào `payment_transaction.raw_payload`. Tham khảo tài liệu kỹ thuật VNPay sandbox phiên bản 2.1.0 [6].

## 2.6. Modular Monolith và DDD-lite

### 2.6.1. Khái niệm Bounded Context

*Bounded Context* — khái niệm cốt lõi của *Domain-Driven Design* do Eric Evans giới thiệu (2003) [11] và Vaughn Vernon hệ thống lại (2013) [28] — là vùng nơi một mô hình miền cùng ngôn ngữ chung (ubiquitous language) được áp dụng nhất quán. Hai context có thể dùng cùng từ vựng (ví dụ "Order") nhưng khác nghĩa, và khác biệt đó được khoanh vùng bởi đường biên.

### 2.6.2. Modular Monolith so với Microservices

*Modular Monolith* là kiến trúc trung gian: code chia thành nhiều module độc lập về logic như microservices nhưng triển khai trong cùng một process như monolith. Sam Newman trong "Monolith to Microservices" (2019) gọi đây là *bước đệm bắt buộc* trước khi tách sang microservices, vì monolith ban đầu không modular thì việc tách sẽ phải viết lại nghiệp vụ [18].

**Bảng 2.3. So sánh Modular Monolith với Microservices**

| Tiêu chí | Modular Monolith | Microservices |
|---|---|---|
| Triển khai | 1 container, 1 lệnh | N container, orchestration K8s |
| Debug | Stacktrace xuyên module | Distributed tracing (Jaeger/Zipkin) |
| Transaction | Local ACID | Saga / eventual consistency |
| Độ phức tạp vận hành | Thấp | Cao |
| Phù hợp với phạm vi đề tài | Đủ | Quá phức tạp |

### 2.6.3. Đồ thị phụ thuộc DAG (không có chu trình)

Đề tài tổ chức 9 module thành đồ thị phụ thuộc một chiều: `shared` là cơ sở, không phụ thuộc module nào; các module nghiệp vụ (`auth`, `order`, `delivery`, `payment`, `promotion`) phụ thuộc `shared`; `bot` phụ thuộc `auth`, `order`, `delivery`; `notification` phụ thuộc các module qua *event interface*; `app` là module thực thi. Đồ thị không có chu trình, Maven enforce qua thứ tự khai báo trong `pom.xml`.

### 2.6.4. Giao tiếp cross-module qua domain events

Thay vì gọi trực tiếp service hay repository của module khác (anti-pattern), các module phát *domain event* và lắng nghe qua `@TransactionalEventListener(AFTER_COMMIT)`: giảm coupling vì publisher không cần biết consumer, đảm bảo eventual consistency với guarantee "fire chỉ khi commit thành công", và mở lối chuyển sang messaging thực (Kafka, RabbitMQ) mà gần như không sửa code nghiệp vụ.

### 2.6.5. Anti-pattern cần tránh

Anti-pattern phổ biến là *gọi DAO của module khác*: `bot` gọi thẳng `OrderRepository` của `order` vi phạm encapsulation — `order` đổi schema thì `bot` phải sửa theo. Đề tài quy định *gọi service public của module khác qua interface trong package `*.api`*; mỗi module có thư mục `api/` chỉ chứa interface và DTO.

## 2.7. Phát triển hướng kiểm thử (TDD)

### 2.7.1. Chu kỳ Red-Green-Refactor

Kent Beck (2002) định nghĩa TDD qua chu kỳ ba bước lặp [7]: *Red* — viết test cho hành vi chưa tồn tại, chạy fail; *Green* — viết mã tối thiểu để test pass; *Refactor* — cải thiện cấu trúc mà không đổi hành vi, test vẫn green. Đề tài áp dụng TDD cho các logic nhạy cảm: ký/xác minh chữ ký VNPay (`VnpaySignatureServiceTest`), chuyển trạng thái đơn (`OrderStateMachineTest`), tính phí ship theo Haversine (`PricingServiceTest`), idempotent IPN (`VnpayIpnIT`).

### 2.7.2. Test pyramid

*Test pyramid* (Mike Cohn) chủ trương nhiều test unit (nhanh) > ít test integration > rất ít test E2E. Đề tài bố trí 231 unit test (Maven Surefire, runtime dưới 10 giây), 22 integration test (Maven Failsafe với Testcontainers, khoảng 3 phút gồm startup container PostgreSQL) và E2E là smoke test thủ công theo `RUNBOOK.md`; thống kê chi tiết theo mô-đun xem Chương 4 mục 4.7.5 và Phụ lục G.

### 2.7.3. Testcontainers

Testcontainers spin up Docker container thật trong test (PostgreSQL, RabbitMQ...) thay vì mock [27], nhờ đó test integration chạy đúng database thật, bắt được lỗi SQL dialect, partial index, JSONB serialization.

```java
@SpringBootTest
@Testcontainers
class VnpayIpnIT {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        // ...
    }
}
```

### 2.7.4. Mockito và AssertJ

*Mockito* thay thế phụ thuộc trong test unit, ví dụ mock `BotSender` khi test `RatingService` để tránh gọi Telegram API thật. *AssertJ* cung cấp assertion theo fluent API:

```java
assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
assertThat(payment.getPaidAt()).isCloseTo(Instant.now(), within(5, SECONDS));
```

### 2.7.5. Spring Boot Test slices

Để giảm thời gian khởi động ApplicationContext, Spring Boot cung cấp các *test slice*: `@WebMvcTest(Controller.class)` chỉ load controller, mock service phía dưới; `@DataJpaTest` chỉ load tầng JPA với H2 in-memory (đề tài không dùng vì cần PostgreSQL thật); `@SpringBootTest` load full context, dùng cho IT.

## 2.8. Đóng gói Docker và Docker Compose

### 2.8.1. Multi-stage build

*Multi-stage Dockerfile* tách pha build (cần JDK + Maven) khỏi pha runtime (chỉ cần JRE), giảm kích thước image:

```dockerfile
# build stage
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /src
COPY . .
RUN ./mvnw -pl app -am package -DskipTests

# runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /src/app/target/*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Runtime chạy với user không phải root để giảm bề mặt tấn công nếu container bị compromise.

### 2.8.2. Healthcheck và `depends_on: condition: service_healthy`

`healthcheck` khai báo cho mỗi service cùng `depends_on: condition: service_healthy` bảo đảm dịch vụ phụ thuộc chỉ start sau khi dịch vụ chính healthy: backend start sau khi PostgreSQL healthy, nginx start sau khi backend trả `/actuator/health` UP.

```yaml
postgres:
  image: postgres:16-alpine
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER}"]
    interval: 5s
    timeout: 5s
    retries: 10
backend:
  depends_on:
    postgres: { condition: service_healthy }
```

### 2.8.3. Nginx reverse proxy với WebSocket upgrade

Nginx serve static SPA của Mini App và Web Admin, proxy `/api/*` đến backend và proxy `/ws/*` với header upgrade WebSocket:

```nginx
location /ws {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 86400;
}
```

### 2.8.4. Biến môi trường, `.env` và `.dockerignore`

Docker Compose substitution `${VAR}` đọc từ file `.env` cùng cấp `docker-compose.yml`. `.env.example` liệt kê các biến cần điền (BOT_TOKEN, BOT_USERNAME, JWT_SECRET, VNPAY_TMN_CODE, VNPAY_HASH_SECRET, ...), đóng vai trò *canonical manifest*. `.dockerignore` loại `node_modules`, `target/`, `.git`, `*.log` khỏi build context để giảm thời gian truyền context tới Docker daemon.

### 2.8.5. Volume cho data persistence

Filesystem container là ephemeral nên dữ liệu cần persistence phải mount qua *volume*; PostgreSQL mount `pgdata` để dữ liệu survive khi container restart hoặc rebuild.

```yaml
volumes:
  pgdata:
services:
  postgres:
    volumes:
      - pgdata:/var/lib/postgresql/data
```

Tham khảo tài liệu Docker Compose specification chính thức [10].

## 2.9. Kết luận chương

Chương 2 đã giới thiệu tám nền tảng công nghệ then chốt: Telegram (Bot API, Mini App, Live Location), Spring Boot 3 với Java 17, React 18 và hệ sinh thái Vite 5, PostgreSQL 16 với Flyway, VNPay sandbox, Modular Monolith với DDD-lite, phát triển hướng kiểm thử và đóng gói bằng Docker Compose — cơ sở lý thuyết cho phân tích và thiết kế hệ thống ở Chương 3.
