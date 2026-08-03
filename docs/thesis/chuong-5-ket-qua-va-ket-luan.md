# Chương 5. Kết quả và Kết luận

Chương này tổng kết kết quả đạt được, các hạn chế đã nhận diện, các hướng phát triển theo độ ưu tiên kinh doanh và độ phức tạp kỹ thuật, cùng kết luận về đóng góp khoa học, đóng góp thực tiễn và bài học kinh nghiệm.

## 5.1. Kết quả đạt được

Đề tài đã hoàn thành toàn bộ phạm vi *Must have* và *Should have* của MoSCoW ở Chương 1, đem lại tám đóng góp kỹ thuật (Chương 1, mục 1.4) và một quy trình phát triển có kỷ luật theo phương pháp GSD. So với nền tảng tổng hợp (GrabFood, ShopeeFood) và shop tự xây ứng dụng riêng, hệ thống lấp khoảng trống định vị với chi phí khoảng mười đô-la Mỹ mỗi tháng cho một VPS, trải nghiệm tracking nhờ Live Location, quyền tự chủ dữ liệu khách hàng không chia hoa hồng, và khách chỉ cần Telegram có sẵn.

### 5.1.1. Kết quả về chức năng

Tám yêu cầu *Must have* và ba yêu cầu *Should have* đã hiện thực và kiểm thử đầy đủ. Hai yêu cầu *Could have* (lưu địa chỉ thường dùng, chat trong bot) chuyển sang 5.3.1; hai yêu cầu *Won't have* (multi-tenant, siêu quản trị viên hệ thống) ngoài phạm vi, chỉ nêu làm hướng mở rộng dài hạn ở 5.3.2.

**Bảng 5.1. Hiện trạng các tính năng theo phân loại MoSCoW**

| Phân loại | Số lượng yêu cầu | Đã hiện thực | Tỉ lệ |
|---|---|---|---|
| Must have (M1–M8) | 8 | 8 | 100% |
| Should have (S9–S11) | 3 | 3 | 100% |
| Could have (C12–C13) | 2 | 0 (hướng phát triển) | 0% |
| Won't have (W14–W15) | 2 | 0 (ngoài phạm vi) | — |
| **Tổng (Must + Should)** | **11** | **11** | **100%** |

Hệ thống đem lại hơn ba mươi tính năng nghiệp vụ. Khách hàng: đăng nhập tự động qua Telegram, duyệt mười sản phẩm minh hoạ, thêm giỏ (persist qua Zustand), đặt đơn COD hoặc VNPay, ghim toạ độ giao trên bản đồ Leaflet với phí ship theo công thức Haversine, theo dõi shipper realtime, xem lịch sử đơn, đánh giá shipper 1–5 sao kèm comment qua FSM hội thoại trong bot. Shipper: đăng ký qua bot FSM bốn bước (tên → số điện thoại → loại xe → biển số), nhận/từ chối offer qua inline keyboard, chia sẻ Live Location, đánh dấu đã giao, đánh giá lại khách (V12). Chủ shop: đăng nhập Web Admin bằng JWT, Dashboard KPI với ba biểu đồ Recharts (doanh thu, top shipper, tỉ lệ huỷ), quản lý đơn realtime qua WebSocket STOMP, CRUD sản phẩm, duyệt và khoá shipper, báo cáo doanh thu theo khoảng ngày, cấu hình shop. Đặc tả use case chi tiết xem **Phụ lục A**.

### 5.1.2. Kết quả về kỹ thuật

Hệ thống đem lại tám đóng góp kỹ thuật. (i) **Mô hình triển khai lai trên nền tảng Telegram** dùng ba kênh bổ trợ: Mini App cho khách và shipper, Bot cho thao tác nhanh và FSM hội thoại, Web Admin cho desktop của chủ shop. (ii) **Theo dõi GPS realtime bằng Telegram Live Location native**, tiết kiệm khoảng 80% công sức so với tự lập trình streaming GPS phía client và thừa hưởng bảo mật HMAC của Telegram. (iii) **Kiến trúc Modular Monolith tám bounded context** giao tiếp một chiều theo đồ thị DAG qua Spring Application Events `@TransactionalEventListener(AFTER_COMMIT)`, giữ ưu điểm "một container, một lệnh deploy" nhưng vẫn cô lập trách nhiệm theo DDD và mở lối chuyển sang microservices.

(iv) **Tích hợp VNPay sandbox với IPN làm nguồn sự thật**: `MessageDigest.isEqual` so hash constant-time chống timing attack (V6 ASVS L1), IPN idempotent trả `RspCode 02` cho replay, audit trail bắt buộc mọi payload IPN vào cột JSONB `payment_transaction.raw_payload`, `Propagation.REQUIRES_NEW` trên `OrderService.confirmAfterPayment` tránh phantom commit trong `AFTER_COMMIT` phase, `PaymentExpiryScheduler` cron 60 giây đánh dấu `Payment(PENDING)` quá 15 phút thành `FAILED`. (v) **Bảo mật defense-in-depth mười một lớp** từ `TelegramAuthFilter` HMAC ở rìa, `JwtAuthFilter` và `@PreAuthorize` ở tầng ứng dụng, WebSocket CONNECT/SUBSCRIBE allowlist, tới `vnp_TxnRef UNIQUE` và partial unique index `uq_assignment_shipper_started` ở tầng dữ liệu; mỗi lớp có regression test khoá đảm bảo (danh sách đầy đủ ở Bảng 3.4).

(vi) **Bộ test suite 274 case** (253 backend + 21 frontend) gồm unit test, integration test với Testcontainers PostgreSQL thật, slice test controller và security regression test, build green 100% trên mỗi commit (**Phụ lục G**). (vii) **Mười lăm Flyway migration** quản lý schema từ V1 init đến V15 shipper_ledger. (viii) **Đóng gói Docker Compose năm container** với healthcheck chain (postgres → backend → miniapp + webadmin → nginx), deploy ba lệnh trên mọi VPS có Docker (**Phụ lục F**).

### 5.1.3. Kết quả về quy trình phát triển

Đề tài minh hoạ quy trình phát triển có kỷ luật theo phương pháp **GSD (Get Shit Done)**: mười pha P0 đến P9, mỗi pha sáu bước Research → Plan → Plan-check → Execute → Code-review → Fix, mỗi bước sinh một artefact commit cùng mã nguồn để giữ truy vết — mười file plan (~33 000 dòng thiết kế), tám file research (~6 500 dòng), chín file code review (ba mức CRITICAL/IMPORTANT/MINOR) và hơn 152 commit nguyên tử theo chuẩn Conventional Commits.

Hiệu quả thể hiện ở hai mặt định lượng: *plan-checker* phát hiện mười hai blocker trước khi chạm code (ví dụ yêu cầu thiết kế lại routing STOMP topic ở P5), và *code review* vá năm critical bug trước khi merge — CR-1 STOMP authorization gap, CR-2 wrong-shipper attribution của Live Location ping, CR-3 NPE trong channel message khi `from` null, CR-4 IPN audit gap (`PaymentTransaction` chưa persist cho IPN signature invalid), CR-5 `ShipperState` enum mismatch giữa Java và Postgres CHECK constraint.

### 5.1.4. Tài liệu sản phẩm

Bộ tài liệu để lại gồm: báo cáo khoảng 28 000 từ chia năm chương kèm chín phụ lục A–I; ba mươi ảnh chụp giao diện Mini App và Web Admin (**Phụ lục H**); *design spec* khoảng 4 700 dòng; mười file plan; tám file research; chín file code review; `RUNBOOK.md` mô tả tám bước smoke test thủ công. Toàn bộ commit dưới `docs/` nên người clone kho mã có ngay tài liệu thiết kế gốc bên cạnh mã nguồn.

### 5.1.5. Số liệu định lượng tổng hợp

Bảng 5.2 tổng hợp các chỉ số định lượng của đề tài tại thời điểm bảo vệ.

**Bảng 5.2. Tổng hợp các chỉ số định lượng kết quả đạt được**

| Chỉ số | Giá trị |
|---|---|
| Tổng số commit nguyên tử | hơn 152 |
| Tổng số dòng mã (Java + TypeScript) | khoảng 25 000 |
| Tổng số từ báo cáo khoá luận | khoảng 28 000 |
| Tổng số dòng tài liệu plan + research + review + spec | khoảng 47 000 |
| Tổng số test backend (unit + integration) | 253 (231 unit) |
| Tổng số test frontend (Vitest + Testing Library) | 21 |
| Tổng số test toàn dự án | 274 |
| Tỉ lệ build xanh trên mỗi commit ở `main` | 100% |
| Số file Flyway migration | 15 (V1 đến V15) |
| Số mô-đun (bounded context) | 8 |
| Số endpoint REST | khoảng 50 |
| Số topic WebSocket | 2 (`/user/queue/order/*/location`, `/topic/admin/orders`) |
| Số sự kiện cross-module | 9 |
| Số trang Mini App | 13 |
| Số trang Web Admin | 13 |
| Số Dockerfile | 3 |
| Số container trong Docker Compose | 5 |
| Số ảnh chụp giao diện trong báo cáo | 30 |
| Số hình vẽ và sơ đồ | 42 (16 trong chương, 26 trong phụ lục) |
| Số bảng biểu | 24 (15 trong chương, 9 trong phụ lục) |

## 5.2. Hạn chế của hệ thống

Mục này khai báo trung thực mười hạn chế đã nhận diện: *bốn hạn chế đã khắc phục trong giai đoạn hoàn thiện cuối khoá luận* (05/2026–06/2026), sáu hạn chế còn lại được phân loại theo bản chất kèm phương án xử lý.

### 5.2.1. Bốn hạn chế đã được khắc phục trong giai đoạn hoàn thiện

- **Hạn chế #6 — Đánh giá một chiều (khách → shipper).** Khắc phục bằng Flyway V12 (`shipper_rating.sql`) bổ sung bảng `shipper_rating`, hai cột `rating_avg`, `rating_count` ở `telegram_user`, cùng `ShipperRatingService` và endpoint REST: shipper đánh giá khách 1–5 sao kèm comment sau khi đơn `DELIVERED`, rating không công khai cho khách mà chỉ admin truy cập để cảnh báo khách "khó tính" cho shipper khác.
- **Hạn chế #7 — Không có khung kiểm thử frontend.** Khắc phục bằng Vitest 1.x và Testing Library cho ba workspace pnpm: 21 test gồm 9 cho `@shop/shared` (format helpers), 6 cho `@shop/miniapp` (Zustand `useCart` store), 6 cho `@shop/webadmin` (`auth-store`, `OrderStatusBadge`).
- **Hạn chế #9 — Bundle size Web Admin vượt 740 kilobyte.** Khắc phục bằng Vite `manualChunks` tách Recharts thành chunk lazy-load chỉ khi Dashboard hoặc Reports render; bundle initial còn khoảng 121 kilobyte gzipped, dưới ngưỡng cảnh báo của Vite.
- **Hạn chế #10 — Mật khẩu admin demo `admin123` không an toàn.** Khắc phục bằng chuỗi mạnh `Demo@Shop2026!` (12 ký tự gồm chữ hoa, chữ thường, số, ký tự đặc biệt) trong V11 seed; tài khoản demo mới `shop@example.com` / `Demo@Shop2026!` khác rõ với `admin@shop.local` / `admin123` ở V5.

### 5.2.2. Hạn chế out-of-scope cố ý

Hai hạn chế này là *quyết định thiết kế có chủ đích*, loại khỏi phạm vi từ khi phân tích yêu cầu để giữ scope khả thi:

- **Hạn chế #1 — Một shop duy nhất, chưa multi-tenant.** Hệ thống thiết kế cho "một shop F&B/tạp hoá quy mô nhỏ–vừa với 1–10 shipper"; mở rộng thành SaaS đa-tenant đòi hỏi thêm bảng `shop`, cột `shop_id` FK ở mọi bảng nghiệp vụ, row-level security trên Postgres và refactor mọi query có WHERE clause — effort ba đến bốn tuần (phương án ở 5.3.2).
- **Hạn chế #3 — Phụ thuộc Telegram availability.** Telegram bị block ở một số quốc gia (Iran, Trung Quốc đại lục, một thời điểm là Nga) nhưng ổn định ở Việt Nam nên không ảnh hưởng target market chính; nếu phục vụ thị trường có rào cản Telegram thì mở rộng sang Zalo Mini App (5.3.2) — Zalo có khoảng 75 triệu MAU tại Việt Nam và không bị block.

### 5.2.3. Hạn chế do giới hạn nhà cung cấp

- **Hạn chế #5 — GPS tracking giới hạn thời lượng Live Location của Telegram.** Telegram cho phép share Live Location tối đa 8 giờ mỗi lần, vượt khoảng này stream tự ngắt và shipper phải share lại. Với giao hàng nội thành 1–10 km mỗi đơn (target chính), mỗi đơn kéo dài tối đa 60–90 phút nên giới hạn không thành vấn đề; đơn liên tỉnh hơn 8 giờ cần GPS streaming riêng (Geolocation API + background sync), ngoài phạm vi đề tài.

### 5.2.4. Hạn chế cần credential thật

- **Hạn chế #4 — Phụ thuộc VNPay sandbox cho thanh toán online.** Người demo phải đăng ký credential sandbox tại `sandbox.vnpayment.vn/devreg`, tốn vài giờ và yêu cầu thông tin doanh nghiệp; cặp mock `TEST01` / `TESTSECRETKEY123` ở `.env.example` chỉ phục vụ unit test offline và bị VNPay sandbox thật từ chối. Phương án giảm phụ thuộc: tích hợp thêm Momo / ZaloPay / VietQR có sandbox dễ tiếp cận hơn (5.3.2).
- **Hạn chế #8 — Manual smoke tests chưa hoàn thiện end-to-end với Telegram thật.** Tám bước smoke test trong `RUNBOOK.md` vẫn chạy thủ công mỗi lần phát hành; tự động hoá đòi hỏi Telegram bot test client (`telethon` hoặc `pyrogram`) cộng Playwright cho Mini App, effort một đến hai tuần (phương án ở 5.3.3).

### 5.2.5. Hạn chế trade-off cố ý

- **Hạn chế #2 — Không có push notification ngoài Telegram.** Nếu khách tắt thông báo Telegram, họ không nhận cập nhật trạng thái đơn. Đây là *trade-off cố ý* của mô hình triển khai lai, đổi lại "khách không phải cài app mới" và không tốn chi phí Firebase Cloud Messaging; nếu cần đa kênh có thể bổ sung FCM cho web/app push hoặc Zalo OA notification (5.3.2).

### 5.2.6. Bảng tổng hợp mức độ nghiêm trọng và phương án

**Bảng 5.3. Tổng hợp các hạn chế hiện tại và phương án xử lý**

| Hạn chế | Phân loại | Mức nghiêm trọng | Phương án xử lý |
|---|---|---|---|
| #1 Multi-tenant | Out-of-scope cố ý | Trung bình | Thêm `shop_id` FK + row-level security (5.3.2) |
| #2 Push ngoài Telegram | Trade-off cố ý | Thấp | Bổ sung FCM web push (5.3.3) |
| #3 Telegram availability | Out-of-scope cố ý | Thấp ở VN | Mở rộng sang Zalo Mini App (5.3.2) |
| #4 VNPay sandbox | Cần credential | Cao trong demo | Tích hợp Momo/ZaloPay (5.3.2) |
| #5 Live Location 8h | Vendor limitation | Thấp trong scope | GPS streaming riêng cho đơn dài (5.3.4) |
| #8 Manual smoke chưa tự động | Cần credential | Trung bình | Playwright + Telethon (5.3.3) |

Việc liệt kê thẳng thắn các hạn chế cho thấy tác giả hiểu rõ phạm vi và trade-off của hệ thống.

## 5.3. Hướng phát triển

Các hướng phát triển chia bốn nhóm theo độ ưu tiên kinh doanh và độ phức tạp kỹ thuật, effort từ 1–2 tuần cho hoàn thiện trong scope hiện tại đến nhiều tháng cho hướng nghiên cứu nâng cao. Hai pha mở rộng nghiệp vụ đã thực hiện sau bảo vệ (chuỗi cửa hàng và ví shipper) được trình bày trong **Phụ lục I**.

### 5.3.1. Hoàn thiện trong scope hiện tại

Các hạng mục sau thuộc phạm vi thiết kế ban đầu, effort mỗi hạng mục một đến hai tuần.

**Chat trong bot giữa khách và shipper** (*Could have #13*). Khi đơn ở `ASSIGNED` hoặc `STARTED`, shipper bấm "Liên hệ khách" và bot làm middleman relay hai chiều ẩn danh, không lộ số điện thoại; nội dung audit vào bảng `bot_chat_log` phục vụ giải quyết tranh chấp.

**Lưu địa chỉ thường dùng của khách** (*Could have #12*). Thêm bảng `customer_address(id, customer_id FK, label, address, lat, lng)` kèm autocomplete ở Checkout, giảm thời gian đặt đơn cho khách quen từ khoảng ba mươi giây xuống năm giây.

**Hiển thị status history trên Order Detail (Web Admin).** Dữ liệu đã có trong bảng `status_history` (V4 migration) nhưng chưa render; chỉ cần component timeline JOIN `status_history` sort theo `created_at` ASC, hữu ích khi truy vết dispute.

**Modal "Gán shipper" trên Web Admin.** Hiện admin gọi trực tiếp API `PUT /api/admin/orders/{id}/assign`; cần modal liệt kê shipper `AVAILABLE` kèm khoảng cách (Haversine từ pickup) và rating trung bình.

**FSM đăng ký shipper hoàn thiện hơn.** Bổ sung state `AWAITING_CV_PHOTO` vào bốn state hiện có của FSM module `bot` để shipper upload ảnh chứng minh thư và bằng lái, admin duyệt trước khi `ShipperApprovedEvent` được fire.

**Bot rating cho khách hàng (đã làm).** Hoàn thành qua V12: shipper rate khách 1–5 sao kèm comment, rating không công khai.

**Frontend test mở rộng.** Từ 21 test hiện có, mở rộng lên 40+ test phủ các page chính (Catalog, Checkout, OrderDetail, Dashboard) bằng Testing Library + MSW (Mock Service Worker) để mock REST response.

### 5.3.2. Mở rộng tính năng vượt khỏi scope ban đầu

Mỗi hạng mục nhóm này tốn khoảng một đến hai tháng. Hai ưu tiên cao là Zalo Mini App và Momo/ZaloPay vì có giá trị kinh doanh trực tiếp lớn nhất với target market Việt Nam.

#### 5.3.2.1. Mở rộng sang Zalo Mini App (ưu tiên cao)

Zalo có khoảng *75 triệu người dùng hoạt động hằng tháng* (MAU), lớn hơn Telegram ở thị trường nội địa, nên mở rộng sang Zalo Mini App giúp tiếp cận nhiều khách tiềm năng hơn, đa dạng hoá kênh phân phối và giảm phụ thuộc một nền tảng. Zalo Mini App SDK (`zmp-sdk`, đăng ký tại `miniapp.zalo.me`) khác Telegram WebApp về API gốc nhưng kiến trúc tương đồng, UI code tái sử dụng khoảng 80% (React + Tailwind + Recharts không đổi). Ba thay đổi chính: thay `@twa-dev/sdk` bằng `zmp-sdk`, đổi xác thực `initData` HMAC sang Zalo OA OAuth 2.0, thay `Telegram.WebApp.openLink` cho VNPay redirect bằng API tương đương. Backend chỉ thêm `ZaloAuthFilter` song hành `TelegramAuthFilter`; các module `order`, `delivery`, `payment` không đổi nhờ Modular Monolith đã cô lập module `auth`. Effort ba đến bốn tuần, hai nền tảng chạy song song trên cùng backend.

#### 5.3.2.2. Tích hợp thêm cổng thanh toán Momo, ZaloPay, VietQR (ưu tiên cao)

Mở rộng từ VNPay sang Momo, ZaloPay, ShopeePay và VietQR vừa tăng tỉ lệ hoàn tất thanh toán, vừa giảm phụ thuộc credential sandbox VNPay vì Momo và ZaloPay đăng ký đơn giản hơn. Pattern integration giống VNPay (sign URL bằng HMAC + verify IPN callback) nên mỗi cổng chỉ tốn ba đến năm ngày, cần xử lý ba khác biệt: thuật toán ký (Momo và ZaloPay dùng HMAC-SHA256 với format payload riêng), callback URL riêng, refund flow riêng. Thiết kế đề xuất: abstraction `PaymentGateway` trong module `payment` với `createPaymentUrl(Order)` và `handleIpn(payload)`, mỗi cổng là một implementation (`VnpayGateway`, `MomoGateway`, `ZalopayGateway`); mã orchestration không đổi vì chỉ inject `Map<PaymentMethod, PaymentGateway>` qua Spring.

#### 5.3.2.3. Các hướng mở rộng khác

- **Multi-shop / Multi-tenant.** Từ "1 shop, 1 admin" thành SaaS nhiều shop, dùng schema-per-tenant hoặc shared-schema với `shop_id` làm discriminator kèm row-level security trên Postgres.
- **Ứng dụng di động native.** Qua React Native, tái sử dụng khoảng 70% code từ Mini App, đổi lại push notification độc lập khỏi Telegram và hiện diện trên Apple App Store, Google Play.
- **Gợi ý sản phẩm bằng học máy.** Gợi ý món theo lịch sử đơn, độ phổ biến và thời điểm trong ngày bằng collaborative filtering (matrix factorization) hoặc dịch vụ ngoài.
- **Loyalty program và coupon.** Bảng `coupon`, `customer_loyalty(points, tier)`; bot gửi mã giảm giá theo cohort retention, tăng retention 15–20% theo benchmark ngành F&B.
- **Voice ordering.** Khách gửi voice note vào bot ("Cho 2 phở bò"), backend dùng Whisper hoặc Google Speech-to-Text parse intent rồi tạo đơn nháp, hỗ trợ khách lớn tuổi hoặc đang lái xe.
- **Multi-language.** i18n cho UI (Việt / Anh / Trung / Nga cho khách du lịch); backend đã sẵn cột `language_code` ở `telegram_user`.
- **Quản lý nhập kho và barcode.** Cột `product.stock` đã có nhưng chưa quản lý nhập kho; thêm bảng `stock_movement` và barcode scanner trong Web Admin.
- **Tối ưu lộ trình last-mile.** Khi shipper có nhiều đơn cùng lúc, gom theo cluster địa lý và sắp xếp thứ tự tối ưu theo heuristic TSP, có thể dùng Google OR-Tools.
- **Computer vision verify.** Shipper chụp ảnh hàng trước khi rời shop, CNN (hoặc CLIP zero-shot) kiểm tra hàng đúng mô tả, giảm dispute "đơn sai".

### 5.3.3. Củng cố cho môi trường sản phẩm (production hardening)

Để chuyển từ MVP (Minimum Viable Product) sang môi trường sản phẩm thực tế, cần hoàn thiện các hạng mục ở Bảng 5.4, tổng effort một đến hai tháng.

**Bảng 5.4. Các hạng mục production hardening**

| Hạng mục | Hiện trạng | Mục tiêu |
|---|---|---|
| TLS / HTTPS thực | Nginx HTTP (hoặc self-signed cho dev) | Let's Encrypt + Certbot auto-renew |
| Bot webhook mode | Polling (HTTP long-poll Telegram mỗi 50 giây) | Webhook — giảm khoảng 90% network egress |
| Distributed lock cho `@Scheduled` | Chạy trên mọi instance khi scale ngang | ShedLock với Postgres advisory lock |
| CI/CD pipeline | Manual `mvn verify` + `pnpm build` | GitHub Actions: lint → test → build → push image → deploy staging → manual approve → prod |
| Observability | App log → stdout | Prometheus metrics + Grafana dashboard + Loki log aggregation + OpenTelemetry trace |
| Rate limiting | Không có ở tầng nginx | Nginx `limit_req` cho `/api/payment/vnpay/ipn` + Bucket4j cho auth endpoint |
| CORS lockdown | `allowedOriginPatterns("*")` ở dev | Whitelist origin theo môi trường |
| JWT secret rotation | Một secret cố định | Quản lý qua AWS KMS / Google Cloud KMS với rotation định kỳ |
| Backup strategy | Không có | `pg_dump` cron + bucket S3-compatible + retention policy 30 ngày |
| GDPR và data retention | Lưu vô hạn | Auto-purge `location_ping` > 30 ngày, anonymize khách khi yêu cầu |
| Audit log table cho admin | Một số bảng đã có | Thêm `admin_audit_log` ghi mọi hành động của admin (compliance) |
| Penetration testing | Defense-in-depth qua code review | Thuê bên thứ ba (Vietsec, Cystack) pen-test trước go-live |

### 5.3.4. Khám phá nghiên cứu nâng cao

Mỗi hạng mục dưới đây có thể là một đề tài nghiên cứu riêng, bước vào lĩnh vực học máy, học sâu, bảo mật nâng cao, blockchain hoặc hệ thống phân tán.

- **Dự đoán nhu cầu (demand forecasting).** Dự đoán số đơn theo giờ, ngày, khu vực để lên kế hoạch sản xuất và lịch shipper bằng Facebook Prophet, ARIMA Box–Jenkins hoặc LSTM seq2seq, đầu vào là lịch sử đơn cộng dữ liệu thời tiết và lịch nghỉ lễ.
- **Định giá động (dynamic pricing).** Phí ship thích nghi theo tỉ lệ cung–cầu (giờ cao điểm tăng nhẹ, ngoài giờ giảm), tham khảo surge pricing của Uber, Lyft.
- **Phát hiện bất thường (anomaly detection).** Phát hiện đơn giá trị bất thường, địa chỉ shipper từ chối nhiều lần, IPN replay attack bằng Isolation Forest, One-Class SVM hoặc autoencoder.
- **Học tăng cường cho tối ưu lộ trình.** Thay heuristic TSP bằng Deep Reinforcement Learning (Pointer Network của Bello et al. 2017, Transformer-based RL của Kool et al. 2019).
- **Federated learning cho gợi ý.** Huấn luyện model trên thiết bị khách bằng TensorFlow Federated hoặc PySyft, không gửi lịch sử về server nên bảo mật và tuân thủ GDPR.
- **Bằng chứng đơn hàng trên blockchain.** Hash hoá đơn lên Polygon hoặc Hyperledger Fabric làm bằng chứng không thay đổi cho dispute pháp lý.
- **Privacy-preserving location.** Khách chỉ thấy shipper cách N mét thay vì toạ độ chính xác, dùng geo-indistinguishability với differential privacy noise (Andrés et al.).
- **A/B testing framework tích hợp.** Hệ thống Statsig-like trong backend, đo conversion các bước Checkout bằng Bayesian A/B test với pyMC.

## 5.4. Kết luận

Đề tài *"Hệ thống quản lý giao hàng tích hợp Telegram Mini App, Web Admin và VNPay"* đã hoàn thành toàn bộ mục tiêu kỹ thuật ở Chương 1: đặt đơn COD và VNPay với ký HMAC-SHA512 theo mô hình IPN-as-source-of-truth; theo dõi shipper thời gian thực qua Telegram Live Location với độ trễ end-to-end dưới ba giây; máy trạng thái hữu hạn cho bốn thực thể chính (`Order`, `DeliveryAssignment`, `Payment`, FSM hội thoại đăng ký shipper); thông báo realtime qua Spring WebSocket STOMP và Telegram Bot; đóng gói Docker Compose năm container chạy với ba lệnh shell. Tám yêu cầu Must và ba yêu cầu Should đã hiện thực, kiểm thử qua 274 test (253 backend cộng 21 frontend) với build green 100% trên mỗi commit, minh hoạ bằng 30 ảnh chụp giao diện kèm seed V11.

Về mặt khoa học, đề tài đem lại bốn đóng góp: (i) **mô hình triển khai lai trên nền tảng Telegram** dùng ba kênh Mini App, Bot, Web Admin phân vai theo thiết bị — hướng tiếp cận kinh tế cho shop F&B và tạp hoá nhỏ–vừa tại Việt Nam; (ii) **kiến trúc Modular Monolith với DDD-lite** phù hợp cho khoá luận hoặc dự án MVP tương tự; (iii) **tích hợp VNPay với IPN-as-source-of-truth** kèm constant-time hash comparison, idempotent IPN, JSONB audit trail bắt buộc và `Propagation.REQUIRES_NEW` trong AFTER_COMMIT phase; (iv) **bảo mật defense-in-depth mười một lớp** có regression test khoá đảm bảo. Phương pháp **GSD (Get Shit Done)** với mười pha P0 đến P9 sinh hơn 152 commit nguyên tử và khoảng 47 000 dòng tài liệu thiết kế cho 25 000 dòng mã — tỉ lệ xấp xỉ hai phần một, chứng tỏ kỷ luật "thiết kế trước khi viết mã".

Về mặt thực tiễn, hệ thống lấp khoảng trống giữa nền tảng tổng hợp (chi phí cao, mất hoa hồng, mất kiểm soát dữ liệu) và shop tự xây ứng dụng (chi phí phát triển lớn, đường cong học hỏi cao): với khoảng mười đô-la Mỹ mỗi tháng cho một VPS, hệ thống phục vụ được ngay các shop nhỏ và vừa muốn tự chủ dữ liệu và quy trình giao hàng. Bài học kinh nghiệm — tuân thủ TDD (test-driven development) từ unit test đầu tiên, lập kế hoạch chi tiết trước khi gõ code, duy trì hai vòng review — đã giúp catch năm critical bug trước khi merge và mười hai blocker trước khi chạm code.

Tác giả xin gửi lời cảm ơn chân thành đến giảng viên hướng dẫn, hội đồng phản biện và các tác giả của những công trình tham khảo đã trích dẫn. Các hướng phát triển ở mục 5.3, đặc biệt là Zalo Mini App và Momo / ZaloPay, sẽ tiếp tục được nghiên cứu nhằm đưa hệ thống từ prototype lên môi trường vận hành thực tế; hai pha mở rộng đã hoàn thành theo hướng này được trình bày trong **Phụ lục I**.

\newpage
