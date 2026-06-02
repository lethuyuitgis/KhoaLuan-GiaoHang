# Điểm nổi bật của ứng dụng & hướng phát triển

> Tài liệu này tóm tắt các đóng góp kỹ thuật của hệ thống quản lý giao hàng dựa trên Telegram, đồng thời đề xuất các hướng phát triển tiếp theo. Nội dung được trích/cô đọng vào Chương 1 (Tổng quan – mục 1.7 *Đóng góp của đề tài*) và Chương 5 (Kết luận – mục 5.2 *Hướng phát triển*) của báo cáo khoá luận.

---

## Phần A. Điểm nổi bật của ứng dụng

### A.1. Mô hình triển khai lai (hybrid) trên nền tảng Telegram

Đa số các giải pháp giao hàng trên thị trường yêu cầu khách hàng cài đặt một ứng dụng riêng. Hệ thống chọn cách tiếp cận khác: tận dụng **Telegram – nền tảng nhắn tin có gần 1 tỷ người dùng hoạt động hằng tháng (MAU)** – làm cổng vào duy nhất. Khách hàng và shipper không cần tạo tài khoản mới hay cài thêm phần mềm; chỉ cần một ứng dụng đã có sẵn trên thiết bị.

Trong phạm vi Telegram, hệ thống sử dụng đồng thời **ba kênh** với vai trò bổ trợ nhau:

| Kênh | Dùng cho ai | Vì sao chọn |
|---|---|---|
| **Telegram Mini App** (React 18 + Vite) | Khách hàng (đặt đơn, theo dõi bản đồ) và Shipper (chi tiết đơn, nút bắt đầu/đã giao) | UI phong phú, hỗ trợ bản đồ Leaflet, theme tự động theo Telegram (dark/light) |
| **Telegram Bot** (Spring Boot + telegrambots-spring-boot-starter 6.9.7) | Shipper (đăng ký, nhận offer, thao tác nhanh) và Khách (đánh giá sau giao) | Inline keyboard cho thao tác 1-tap; FSM cho luồng phức tạp; notification qua hệ thống thông báo gốc của Telegram |
| **Web Admin** (React 18 + Tailwind) | Chủ shop (PC) | Dashboard nhiều cột, biểu đồ Recharts, bảng dữ liệu lớn — phù hợp môi trường desktop |

Sự **phân vai theo thiết bị** này (mobile-first cho khách + shipper, desktop cho admin) khác với hầu hết các app delivery thương mại vốn ép tất cả vào một ứng dụng di động duy nhất. Kết quả: trải nghiệm mỗi vai trò được tối ưu thay vì compromise.

### A.2. Theo dõi vị trí thời gian thực bằng Telegram Live Location

Tính năng theo dõi shipper trên bản đồ — vốn là một trong những hạng mục tốn nhất khi xây ứng dụng giao hàng — được hiện thực **không cần lập trình streaming GPS phía client**. Hệ thống tận dụng tính năng **Live Location gốc của Telegram**: shipper chỉ cần bấm 📎 → "Vị trí" → "Chia sẻ vị trí trực tiếp" và chọn thời lượng (15 phút / 1 giờ / 8 giờ). Telegram tự động gửi `message.location` ban đầu, sau đó là `edited_message.location` mỗi 5–10 giây.

Phía backend, một `LiveLocationHandler` được wire vào webhook bot để:
1. Parse `lat`/`lng`/`accuracy`/`heading` từ payload.
2. Truy vấn `DeliveryAssignment` đang `STARTED` của shipper (xác thực qua `from.id` mà Telegram đã ký HMAC).
3. Lưu `location_ping` row.
4. Phát `LocationPingReceivedEvent`.

Một `LocationBroadcaster` lắng nghe sự kiện với `@TransactionalEventListener(AFTER_COMMIT)` (không gửi nếu transaction rollback) và broadcast qua STOMP `/user/{customerId}/queue/order/{orderId}/location`. Khách hàng nhận được vị trí real-time trên bản đồ Leaflet ngay trong Mini App.

Lợi ích kỹ thuật:
- **Tiết kiệm khoảng 80% công sức** so với việc phải tự lập trình GPS streaming (xin permission, foreground service, battery optimization, background sync, lost-signal handling…).
- **Bảo mật cao** — vị trí được Telegram ký bằng HMAC trước khi đến backend; không có kênh giả mạo nào có thể chèn vị trí vào hệ thống.
- **Tiết kiệm pin** — Telegram là một app người dùng đã chấp nhận chạy nền; không tăng thêm gánh nặng năng lượng nào.

### A.3. Kiến trúc Modular Monolith với 8 bounded context

Hệ thống áp dụng **Modular Monolith** thay vì Microservices — một quyết định có chủ đích cho phạm vi đề tài:

```
shared (base — không phụ thuộc module nào khác)
  ↑
  ├── auth          (Telegram initData verify + JWT admin)
  ├── order         (Order, Product, OrderItem, StatusHistory + state machine)
  ├── delivery      (ShipperProfile, DeliveryAssignment, LocationPing, Rating + Reports)
  ├── payment       (Payment, PaymentTransaction, VNPay signing/IPN)
  ├── bot           (Telegram Bot handlers + FSM ConversationState)
  ├── notification  (OrderAssignedNotifier, OrderLifecycleNotifier, RatingPromptBuilder)
  └── app           (executable jar — wires everything + WebSocketConfig + SecurityConfig)
```

Các module giao tiếp **một chiều, theo DAG (không có chu trình)**. Liên lạc đa-module diễn ra qua **Spring Application Events** với `@TransactionalEventListener(AFTER_COMMIT)`, đảm bảo:
- Module A publish event → Module B listener xử lý mà không cần biết về internal của A.
- Event chỉ fire **sau khi transaction commit thành công** → tránh trường hợp gửi notification rồi DB rollback.
- Có thể tách 1 module ra thành microservice trong tương lai mà gần như không phải sửa code business — chỉ cần thay event publisher bằng message broker (Kafka, RabbitMQ).

Lợi ích so sánh:

| Tiêu chí | Modular Monolith (đã chọn) | Microservices |
|---|---|---|
| Triển khai | 1 container, 1 lệnh | N container, orchestration K8s |
| Debug | Stacktrace xuyên module trong cùng 1 process | Distributed tracing (Jaeger/Zipkin) |
| Transaction | Local ACID transaction | Saga / eventual consistency |
| Phạm vi đề tài | Đủ | Quá phức tạp |
| Đường tiến hoá | Tách thành microservice khi cần (event-based đã sẵn) | Đã ở đó rồi |

### A.4. Tích hợp VNPay sandbox với IPN làm nguồn sự thật

Việc tích hợp cổng thanh toán Việt Nam đầy đủ — không phải chỉ "redirect tới VNPay rồi tin tưởng Return URL" — là một điểm cộng kỹ thuật. Hệ thống áp dụng đúng pattern **IPN-as-source-of-truth** mà VNPay khuyến nghị:

- `POST /api/payment/vnpay/create` (xác thực Telegram initData) → tạo `Payment(PENDING)` + ký URL HMAC-SHA512 → trả `paymentUrl`.
- Mini App mở URL bằng `WebApp.openLink` (KHÔNG dùng `window.location.href` — đây là pitfall hay gặp khiến Mini App bị thoát).
- `GET /api/payment/vnpay/return` — public — verify chữ ký → render trang HTML đơn giản trong WebView. **Không cập nhật DB** ở đây.
- `POST /api/payment/vnpay/ipn` — public, server-to-server từ VNPay — verify chữ ký, kiểm tra số tiền, idempotent → cập nhật `payment.status` + transition `order.status PENDING→CONFIRMED` qua event.

Các đảm bảo kỹ thuật:
1. **`MessageDigest.isEqual`** so sánh hash constant-time → chống timing attack (V6 ASVS L1).
2. **Idempotent IPN** — replay trả `RspCode 02 (already updated)` không thay đổi DB.
3. **Audit trail bắt buộc** — mọi IPN (kể cả invalid signature) được ghi vào `payment_transaction.raw_payload` (JSONB) để forensics. Đây là phát hiện từ code review P7 và được vá hậu kỳ.
4. **`Propagation.REQUIRES_NEW`** trên `OrderService.confirmAfterPayment` — chi tiết tinh tế: nếu để `REQUIRED` mặc định, listener fire trong AFTER_COMMIT phase của outer transaction sẽ không commit được order update. Phát hiện này được catch ở Wave 1 Task 9 IT.
5. **`PaymentExpiryScheduler`** — cron 60s tự đánh dấu các `Payment(PENDING)` quá 15 phút thành `FAILED` để khách có thể đặt lại.

### A.5. Đánh giá shipper với FSM đầu tiên trong dự án

Tính năng đánh giá 1-5 sao yêu cầu một state machine nhỏ cho luồng comment tuỳ chọn:

```
DELIVERED
  → bot send inline keyboard [⭐×5 + Bỏ qua]
  → callback `RATE:<orderId>:<n>` → INSERT rating + recompute shipper_profile.rating_avg
  → bot edit message (remove keyboard) + prompt "Nhập nhận xét (tuỳ chọn) hoặc /skip"
  → FSM state CUSTOMER_RATING_COMMENT
  → text incoming  → UPDATE rating.comment, clear FSM
  → /skip          → clear FSM
```

`ConversationStateService` lưu trạng thái vào bảng `conversation_state` (đã có từ V3 migration nhưng chưa được dùng) với payload **JSONB** thông qua `@JdbcTypeCode(SqlTypes.JSON)` native Hibernate 6 — không cần thêm dependency `hypersistence-utils`. Đây là cơ sở hạ tầng tái sử dụng được cho các flow FSM khác (đăng ký shipper, đặt đơn qua chat...).

**Đảm bảo correctness:**
- `shipper_profile.rating_avg` được **tính lại từ aggregate query** (`SELECT AVG(stars), COUNT(*) FROM rating WHERE shipper_id = ?`) thay vì incremental math, tránh floating-point drift trên `NUMERIC(3,2)`.
- `UNIQUE(order_id)` trên `rating` table chống đánh giá trùng.
- `@Order(0)` trên `RatingCommentHandler` đảm bảo nó được Spring inject lên đầu `List<UpdateHandler>` → bắt được text trước khi handler khác xử lý.

### A.6. Bảo mật defense-in-depth nhiều lớp

Hệ thống áp dụng nhiều lớp phòng thủ song song, không dựa duy nhất vào một cơ chế:

| Lớp | Cơ chế | Bảo vệ chống |
|---|---|---|
| L1 | `TelegramAuthFilter` verify HMAC initData | Spoof Telegram identity |
| L2 | `JwtAuthFilter` verify JWT (15 phút TTL) | Admin session hijack |
| L3 | `SecurityConfig` filter chain allowlist | Endpoint chưa phân quyền |
| L4 | `@PreAuthorize("hasRole('SHOP_OWNER')")` ở controller | Privilege escalation |
| L5 | `@CurrentUser TelegramUser` resolver | Forge customerId trong path |
| L6 | WebSocket CONNECT interceptor (dual auth) | Anonymous WS connect |
| L7 | WebSocket SUBSCRIBE allowlist (`/user/*`, `/queue/*`) | Cross-user data leak qua broadcast (catch ở P6 review) |
| L8 | VNPay HMAC `MessageDigest.isEqual` constant-time | Signature spoof + timing attack |
| L9 | `groupBy` enum whitelist trước `DATE_TRUNC` bind | SQL injection ở Reports |
| L10 | `vnp_TxnRef UNIQUE` + `payment_transaction` audit | Payment replay |
| L11 | Partial unique index `uq_assignment_shipper_started` (V8) | Wrong-shipper attribution |

Mỗi lớp đều có **regression test** (`WebSocketAdminAuthIT`, `VnpaySignatureServiceTest`, `OrderStateMachineTest`...) để khoá đảm bảo. Tổng cộng **221 unit + integration test** chạy mỗi commit qua Maven Failsafe + Surefire + Testcontainers.

### A.7. Quy trình phát triển có kỷ luật (GSD methodology)

Toàn bộ 10 phase (P0 → P9) được phát triển theo phương pháp **GSD (Get Shit Done) — Research → Plan → Plan-check → Execute → Code-review → Fix**:

```
Phase N
 ├─ /gsd-research-phase   → docs/superpowers/research/<phase>-research.md
 ├─ /gsd-plan-phase       → docs/superpowers/plans/<phase>-<topic>.md   (1500–4700 dòng/phase)
 ├─ Plan-checker          → CHECK.md (verdict + blocker)
 ├─ Wave executor         → 1 commit / task, atomic
 ├─ /gsd-code-review      → docs/superpowers/reviews/<phase>-REVIEW.md
 └─ /gsd-code-review-fix  → patch + regression test
```

Tổng số artefact:
- **10 plan file** (tổng ~33 000 dòng — bằng đề tài thiết kế của một dự án production cỡ vừa).
- **8 research doc** (~6 500 dòng).
- **9 code review report** (mỗi phase 1 file, đánh giá CRITICAL/IMPORTANT/MINOR).
- **152+ commit** — mỗi commit là 1 task atomic, message theo Conventional Commits.

Phương pháp này **catch lỗi sớm**: 12 blocker đã được phát hiện ở plan-checker trước khi chạm code (tiết kiệm hàng giờ debug), 4 critical bug được phát hiện ở code review (1 IPN audit gap, 1 STOMP SUBSCRIBE leak, 1 ShipperState enum mismatch, 1 RateOrderResponse DTO leak).

### A.8. Sẵn sàng triển khai (deployment-ready) trong 3 lệnh

Hệ thống đã được **containerized hoàn chỉnh** từ Phase 9:

```bash
git clone <repo> && cd KhoaLuan-GiaoHang
cp .env.example .env             # điền 4 secret: BOT_TOKEN, JWT_SECRET, VNPAY_TMN_CODE, VNPAY_HASH_SECRET
docker compose -f infra/docker-compose.yml up -d
```

Sau ~3 phút (lần đầu build) hoặc ~30 giây (cache), toàn bộ stack chạy:
- `postgres:16-alpine` (data volume + healthcheck)
- `backend` (Spring Boot — multi-stage Maven build → JRE 17 slim, non-root user, healthcheck `/actuator/health`)
- `miniapp` + `webadmin` (Vite build → nginx alpine, mỗi bên ~10 MB image)
- `nginx` reverse proxy (`/api/*` → backend, `/ws/*` upgrade WebSocket, `/miniapp/*` + `/admin/*` static)

`application-prod.yml` áp dụng **fail-fast** cho secret — thiếu biến môi trường thì backend không khởi động được, tránh case ai đó deploy lên prod với credential test.

Các tính năng demo bổ trợ:
- **V11 demo seed** — 1 admin + 10 sản phẩm + 6 telegram user + 30 đơn rải đều 30 ngày + 18 assignment + 10 rating + 11 payment — reviewer có thể thấy dashboard có dữ liệu thực ngay sau khi `up`.
- **Dev-bypass header** (`X-Dev-User-Id`) — chỉ active trong profile `dev`, silently ignored ở prod, cho phép test Mini App ngoài Telegram trên browser thường.
- **RUNBOOK.md** — checklist 8 bước smoke test (P1 → P8) cho ngày bảo vệ.

### A.9. Một số con số đáng chú ý

| Metric | Giá trị |
|---|---|
| Tổng số commit | 152+ |
| Tổng dòng code (Java + TS) | ~21 000 |
| Tổng dòng plan + research + review | ~42 000 |
| Số backend test | 221 (unit + IT) |
| Tỷ lệ build green | 100% (mỗi commit verify) |
| Số Flyway migration | 11 (V1 → V11) |
| Số bounded context | 8 (shared/auth/order/delivery/payment/bot/notification/app) |
| Số endpoint REST | ~35 |
| Số WebSocket topic | 2 (`/user/queue/order/*/location`, `/topic/admin/orders`) |
| Số sự kiện cross-module | 9 (OrderCreated/Confirmed/Assigned/Delivered/Cancelled/PaymentSucceeded/Failed/LocationPingReceived/OrderConfirmed via Payment) |
| Pages Mini App | 9 (3 dành cho khách, 2 cho shipper, 4 chung) |
| Pages Web Admin | 8 (login + 7 page chính) |
| Số file Dockerfile | 3 (backend + miniapp + webadmin) |
| Container compose | 5 (postgres + backend + miniapp + webadmin + nginx) |

---

## Phần B. Hướng phát triển tiếp theo

Các hướng được phân thành 4 nhóm theo độ ưu tiên kinh doanh và độ phức tạp kỹ thuật.

### B.1. Hoàn thiện trong scope hiện tại (1–2 tuần / hạng mục)

| Hạng mục | Mô tả | Giá trị |
|---|---|---|
| **Bot FSM đăng ký shipper đầy đủ** | Hiện tại admin phải insert shipper qua Web Admin. FSM cần hoàn thiện: AWAITING_NAME → AWAITING_PHONE (request_contact button) → AWAITING_VEHICLE (inline keyboard) → AWAITING_PLATE → PENDING_APPROVAL. Spec §6.5 đã có thiết kế chi tiết. | Self-service shipper onboarding |
| **Chat trong Bot giữa khách & shipper** (MoSCoW Could #13) | Khi đơn `ASSIGNED`, shipper bấm "Liên hệ khách" → bot ghép cuộc trò chuyện ẩn danh giữa 2 user (bot làm middleman, không lộ số điện thoại) | Giảm gọi điện trực tiếp, audit được nội dung |
| **Lưu địa chỉ thường dùng của khách** (MoSCoW Could #12) | Bảng `customer_address(id, customer_id, label, address, lat, lng)` + autocomplete ở checkout | Giảm friction đặt đơn từ ~30s xuống ~5s cho khách quen |
| **Hoàn thiện 5 review backlog items** | P6 MN-5 (bundle marker icon local), P7 IM-05 (CANCELLED-then-paid UX), P8 IM-01 (IT cho cancellation null-note), P9 (lazy-load Recharts vendor chunk) | Code quality + bundle size |
| **Hiển thị status history trên Order Detail (admin)** | Data đã có ở `status_history` (V4), chỉ cần JOIN + render timeline | Truy vết đơn dễ hơn cho support |
| **Modal "Gán shipper" trên Web Admin** | Hiện cần POST API trực tiếp; thiếu UI picker (filter shipper AVAILABLE) | Admin workflow hoàn chỉnh |
| **Frontend test** (Vitest + Testing Library) | Chưa setup; chỉ có manual smoke + Playwright ad-hoc | CI green + regression safety net |
| **Bot rating cho khách** | Hiện rating chỉ 1 chiều (khách → shipper). Bổ sung shipper → khách (đánh giá thái độ, không công khai) | Cảnh báo khách "khó tính" với shipper khác |

### B.2. Mở rộng tính năng (1–2 tháng / hạng mục)

| Hạng mục | Mô tả | Cân nhắc |
|---|---|---|
| **Multi-shop / Multi-tenant** | Mở rộng từ "1 shop, 1 admin" → SaaS cho nhiều shop. Cần `shop` table + `shop_id` FK ở mọi bảng nghiệp vụ + row-level security | Schema migration lớn (10+ bảng). Có thể dùng schema-per-tenant hoặc shared-schema with shop_id discriminator |
| **Native mobile app** (iOS / Android via React Native) | Reuse 70% code từ Mini App. Lợi ích: push notification riêng biệt khỏi Telegram, AppStore presence | Cần đăng ký Apple/Google developer (~$99/năm cho Apple) + build pipeline |
| **AI recommendation engine** | Gợi ý món dựa trên: lịch sử đơn của khách + popularity + thời điểm trong ngày. Có thể dùng collaborative filtering đơn giản (Spark MLlib hoặc SciKit-learn) | Cần ít nhất 1 000+ đơn để có signal |
| **Tích hợp thêm cổng thanh toán** | Momo, ZaloPay, ShopeePay, VietQR. Mỗi cổng tốn ~3-5 ngày integration vì pattern tương tự VNPay | Đa dạng hoá để giảm phụ thuộc 1 vendor |
| **Loyalty program / coupon** | Bảng `coupon`, `customer_loyalty(points, tier)`. Bot có thể gửi mã giảm giá định kỳ (cohort retention) | Tăng retention 15-20% theo benchmark ngành |
| **Voice ordering** | Khách gửi voice note vào bot ("Cho 2 phở bò"), backend dùng Whisper / Google Speech-to-Text → parse intent → tạo đơn nháp | Hỗ trợ khách lớn tuổi / lái xe / khuyết tật tay |
| **Multi-language** | i18n cho UI (vi/en + có thể zh, ru cho khách du lịch). Backend đã sẵn `language_code` ở `telegram_user` | Tăng base khách hàng quốc tế ở khu du lịch |
| **Inventory management** | `product.stock` đã có nhưng chưa có quản lý nhập kho. Thêm `stock_movement` table + barcode scanner trong Web Admin | Tránh oversell |
| **Last-mile route optimization** | Khi shipper có nhiều đơn cùng lúc, gom theo cluster địa lý + thứ tự tối ưu (TSP heuristic). Dùng OR-Tools | Tăng năng suất shipper 20-30% |
| **Computer vision verify** | Shipper chụp ảnh hàng trước khi rời shop → CNN check hàng đúng mô tả. Có thể dùng CLIP zero-shot | Giảm dispute "đơn sai" |

### B.3. Production hardening (1-2 tháng cho toàn bộ)

| Hạng mục | Hiện trạng | Mục tiêu |
|---|---|---|
| **TLS/HTTPS thực** | Nginx self-signed cho dev | Let's Encrypt + Certbot auto-renew |
| **Bot webhook mode** | Polling (HTTP long-poll Telegram mỗi 50s) | Webhook (Telegram push vào backend) — giảm 90% network egress |
| **Distributed lock cho scheduler** | `@Scheduled` chạy trên mọi instance khi scale ngang | **ShedLock** với Postgres advisory lock (đã document ở P7 review IM-02) |
| **CI/CD pipeline** | Manual build + push | GitHub Actions: lint → test → build → push image → deploy staging → manual approve → deploy prod |
| **Observability** | App log → stdout | Prometheus metrics + Grafana dashboard + Loki log aggregation + OpenTelemetry trace |
| **Rate limiting** | Không có | Nginx `limit_req` cho `/api/payment/vnpay/ipn` (chống flooding) + Bucket4j cho auth endpoint |
| **CORS lock-down** | `allowedOriginPatterns("*")` | Whitelist origin theo môi trường |
| **JWT secret rotation** | 1 secret cố định | KMS-backed rotating key (AWS KMS / Google Cloud KMS) |
| **Backup strategy** | Không có | `pg_dump` cron + S3-compatible bucket + retention policy |
| **GDPR / data retention** | Lưu vô hạn | Auto-purge `location_ping` > 30 ngày, `payment_transaction` > 7 năm, anonymize khách hàng yêu cầu |
| **Audit log table** | Một số bảng đã có (status_history, payment_transaction) | Thêm `admin_audit_log` cho mọi hành động của admin (compliance) |
| **Penetration testing checklist** | Defense-in-depth code review | Thuê 3rd party (Vietsec, Cystack) pen-test trước khi go-live |

### B.4. Khám phá nghiên cứu nâng cao (mỗi hạng mục có thể là một đề tài khoá luận / nghiên cứu riêng)

| Đề tài | Hướng nghiên cứu | Tham khảo |
|---|---|---|
| **Demand forecasting** | Dự đoán số đơn theo giờ/ngày/khu vực, lên kế hoạch sản xuất + lịch shipper. Có thể dùng Prophet (Meta) hoặc LSTM seq2seq | Box-Jenkins ARIMA, Facebook Prophet |
| **Dynamic pricing** | Phí ship adaptive theo demand-supply ratio (giờ cao điểm tăng nhẹ, ngoài giờ giảm để khuyến khích) | Surge pricing literature (Uber, Lyft) |
| **Anomaly detection** | Phát hiện đơn bất thường (giá trị quá lớn, địa chỉ shipper từ chối nhiều lần, IPN replay) | Isolation Forest, autoencoder |
| **Reinforcement learning cho route optimization** | Thay TSP heuristic bằng DRL (Pointer Network, Transformer-based RL) | Bello et al. 2017, Kool et al. 2019 |
| **Federated learning cho recommendation** | Train model trên thiết bị khách (không gửi lịch sử về server) — bảo mật + GDPR compliant | TensorFlow Federated, PySyft |
| **Blockchain receipt** | Hash hoá đơn lên 1 chain (Polygon, Hyperledger) — bằng chứng không thay đổi cho dispute | Truffle, Hardhat |
| **Privacy-preserving location** | Khách chỉ thấy shipper cách N mét chứ không phải toạ độ chính xác — differential privacy noise | Andrés et al. (geo-indistinguishability) |
| **A/B testing framework tích hợp** | Statsig-like infra trong backend, đo conversion từ checkout step | Bayesian A/B test với pyMC3 |

---

## Phần C. So sánh với các giải pháp tương đương trên thị trường

| Đặc điểm | Đề tài | GrabFood / ShopeeFood | Local pizza shop tự build app |
|---|---|---|---|
| Khách cần cài app | ❌ (chỉ cần Telegram) | ✅ (~150 MB) | ✅ |
| Native realtime tracking | ✅ qua Telegram | ✅ | ❌ thường chỉ web link |
| Cost-to-deploy | ~10 USD/tháng (1 VPS) | N/A (commercial) | ~50-100 USD/tháng (Heroku + Maps API) |
| Tự host hoàn toàn | ✅ | ❌ | ✅ |
| Mã nguồn mở | (có thể) | ❌ | thường ❌ |
| Tích hợp VNPay sandbox đầy đủ | ✅ | ✅ | tuỳ |
| Web Admin riêng | ✅ | ✅ | thường ❌ |
| Modular, scale dễ | ✅ Modular Monolith | Microservices nội bộ | thường monolith thuần |

Định vị: **shop F&B/tạp hoá quy mô nhỏ–vừa (1–10 shipper, ~50–500 đơn/ngày) muốn tự chủ về dữ liệu, không trả % hoa hồng cao cho aggregator**.

---

## Phần D. Hạn chế hiện tại được nhận diện trung thực

Để không vẽ vời, hệ thống còn các hạn chế sau cần khai báo trong báo cáo:

1. **Một shop duy nhất** — chưa multi-tenant.
2. **Không có push notification ngoài Telegram** — nếu khách tắt Telegram thông báo, sẽ không biết đơn đến đâu.
3. **Phụ thuộc Telegram availability** — Telegram bị block ở một số quốc gia. Ở Việt Nam hiện tại ổn định.
4. **Phụ thuộc VNPay sandbox cho thanh toán online** — chưa tích hợp Momo/ZaloPay.
5. **GPS tracking giới hạn thời lượng của Live Location Telegram** (tối đa 8 giờ/lần share) — đơn liên tỉnh > 8 giờ sẽ mất stream giữa chừng (không có trường hợp này trong scope).
6. **Bot rating chỉ một chiều** — chưa cho shipper đánh giá khách.
7. **Không có nền tảng test tự động cho frontend** — chỉ backend có 221 test.
8. **Manual smoke tests** chưa được chạy end-to-end qua Telegram thật (đang trong quá trình thực hiện).
9. **Bundle size webadmin ~740 KB** — vượt warning của Vite, có thể tối ưu thêm.
10. **Demo seed admin password (`admin123`)** không an toàn cho production — cần thay khi deploy.

Việc thẳng thắn liệt kê hạn chế này thực ra là một điểm cộng khi bảo vệ — chứng tỏ tác giả hiểu rõ hệ thống, không phóng đại.

---

*File này được trích vào báo cáo: phần A → Chương 1 mục 1.7; phần B → Chương 5 mục 5.2; phần C → Chương 1 mục 1.1 (so sánh với giải pháp hiện có); phần D → Chương 5 mục 5.1 (kết luận).*
