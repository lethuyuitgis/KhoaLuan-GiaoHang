# Chương 5. Kết quả và Kết luận

Chương này tổng kết các kết quả mà đề tài đã đạt được sau toàn bộ quá trình phân tích, thiết kế và hiện thực; trung thực liệt kê các hạn chế đã được nhận diện trong quá trình phát triển và đánh giá; đề xuất các hướng phát triển tiếp theo được phân loại theo độ ưu tiên kinh doanh và độ phức tạp kỹ thuật; và đưa ra kết luận tổng thể về đóng góp khoa học, đóng góp thực tiễn cùng các bài học kinh nghiệm thu hoạch được từ đề tài.

## 5.1. Kết quả đạt được

Đề tài đã hoàn thành toàn bộ phạm vi *Must have* và *Should have* trong phân loại MoSCoW đã đề ra ở Chương 1, đồng thời đem lại tám đóng góp kỹ thuật và một quy trình phát triển có kỷ luật theo phương pháp GSD. So với hai nhóm giải pháp phổ biến hiện nay tại Việt Nam — nền tảng tổng hợp (GrabFood, ShopeeFood) và shop tự xây ứng dụng riêng — hệ thống của đề tài lấp vào khoảng trống định vị: chi phí triển khai chỉ khoảng mười đô-la Mỹ mỗi tháng cho một VPS, trải nghiệm tracking gần với các nền tảng tổng hợp nhờ Live Location của Telegram, và cho phép chủ shop tự chủ hoàn toàn về dữ liệu khách hàng mà không phải chia hoa hồng cho aggregator. Khách hàng không phải cài thêm bất kỳ ứng dụng mới nào — chỉ cần Telegram đã có sẵn trên thiết bị di động.

### 5.1.1. Kết quả về chức năng

Toàn bộ tám yêu cầu *Must have* và ba yêu cầu *Should have* trong phân loại MoSCoW đã được hiện thực và kiểm thử đầy đủ. Hai yêu cầu *Could have* (lưu địa chỉ thường dùng và chat trong bot) được liệt kê ở mục hướng phát triển 5.3.1 theo đúng kế hoạch ban đầu; hai yêu cầu *Won't have* (multi-tenant nhiều shop và siêu quản trị viên hệ thống) nằm ngoài phạm vi và chỉ được đề cập làm hướng mở rộng dài hạn ở 5.3.2.

**Bảng 5.1. Hiện trạng các tính năng theo phân loại MoSCoW**

| Phân loại | Số lượng yêu cầu | Đã hiện thực | Tỉ lệ |
|---|---|---|---|
| Must have (M1–M8) | 8 | 8 | 100% |
| Should have (S9–S11) | 3 | 3 | 100% |
| Could have (C12–C13) | 2 | 0 (hướng phát triển) | 0% |
| Won't have (W14–W15) | 2 | 0 (ngoài phạm vi) | — |
| **Tổng (Must + Should)** | **11** | **11** | **100%** |

Cụ thể, hệ thống đem lại hơn ba mươi tính năng nghiệp vụ vận hành đầy đủ: khách hàng có thể đăng nhập tự động qua Telegram, duyệt mười sản phẩm minh hoạ với hình ảnh và mô tả, thêm sản phẩm vào giỏ với state được persist qua Zustand, đặt đơn với hai phương thức thanh toán COD và VNPay, ghim toạ độ giao trên bản đồ Leaflet với phí ship tính bằng công thức Haversine, theo dõi vị trí shipper realtime trên bản đồ, xem lịch sử đơn và đánh giá shipper 1–5 sao kèm comment qua FSM hội thoại trong bot; shipper có thể đăng ký qua bot FSM bốn bước (tên → số điện thoại → loại xe → biển số), nhận và từ chối offer qua inline keyboard, chia sẻ Live Location, đánh dấu đã giao, và đánh giá lại khách hàng (V12); chủ shop có thể đăng nhập Web Admin bằng JWT, xem Dashboard với KPI và ba biểu đồ Recharts (doanh thu, top shipper, tỉ lệ huỷ), quản lý đơn realtime qua WebSocket STOMP, CRUD sản phẩm, duyệt và khoá shipper, xem báo cáo doanh thu theo khoảng ngày tuỳ chọn, và cập nhật cấu hình shop.

Hình 5.1 minh hoạ Dashboard của Web Admin sau khi V11 seed apply — hiển thị ba mươi đơn rải đều ba mươi ngày qua với phân bố trạng thái thực tế (60% DELIVERED, 20% ASSIGNED/STARTED, 10% CANCELLED, 10% PENDING/CONFIRMED).

![Hình 5.1. Dashboard của Web Admin hiển thị KPI và biểu đồ doanh thu sau khi V11 seed apply](screenshots/admin-02-dashboard.png)

Hình 5.2 minh hoạ giao diện danh mục của Mini App khi khách hàng mở từ bot Telegram — đây là điểm tiếp xúc đầu tiên của khách với hệ thống.

![Hình 5.2. Giao diện danh mục Mini App khi khách hàng mở từ bot Telegram](screenshots/miniapp-cust-01-catalog.png)

### 5.1.2. Kết quả về kỹ thuật

Hệ thống đem lại tám đóng góp kỹ thuật chính, tương ứng với chín điểm nổi bật được phân tích chi tiết ở phần A của tài liệu *điểm nổi bật và hướng phát triển*. Thứ nhất, **mô hình triển khai lai trên nền tảng Telegram** sử dụng đồng thời ba kênh có vai trò bổ trợ nhau (Mini App cho khách và shipper, Bot cho thao tác nhanh và FSM hội thoại, Web Admin cho desktop của chủ shop) — phá vỡ ràng buộc "một ứng dụng giải quyết mọi vai trò" của các app giao hàng thương mại, cho phép mỗi vai trò được tối ưu thay vì compromise. Thứ hai, **theo dõi GPS realtime bằng Telegram Live Location native** — tận dụng tính năng có sẵn của Telegram thay vì lập trình streaming GPS phía client (tiết kiệm khoảng 80% công sức so với phương án tự build, đồng thời thừa hưởng tính bảo mật HMAC của Telegram). Thứ ba, **kiến trúc Modular Monolith với tám bounded context** giao tiếp một chiều theo đồ thị DAG qua Spring Application Events `@TransactionalEventListener(AFTER_COMMIT)` — vừa giữ ưu điểm "một container, một lệnh deploy" của monolith, vừa đạt mức độ cô lập trách nhiệm theo DDD, đồng thời tạo lối thoát chuyển sang microservices trong tương lai mà gần như không phải sửa code nghiệp vụ.

Thứ tư, **tích hợp VNPay sandbox với IPN làm nguồn sự thật** — không chỉ "redirect rồi tin tưởng Return URL" mà áp dụng đúng pattern *IPN-as-source-of-truth* mà VNPay khuyến nghị, với các đảm bảo kỹ thuật cụ thể: `MessageDigest.isEqual` so sánh hash constant-time chống timing attack (V6 ASVS L1), idempotent IPN trả `RspCode 02` cho replay, audit trail bắt buộc mọi payload IPN vào cột JSONB `payment_transaction.raw_payload` kể cả khi chữ ký không hợp lệ, `Propagation.REQUIRES_NEW` trên `OrderService.confirmAfterPayment` để tránh phantom commit trong `AFTER_COMMIT` phase, và `PaymentExpiryScheduler` cron 60 giây tự đánh dấu `Payment(PENDING)` quá 15 phút thành `FAILED`. Thứ năm, **bảo mật defense-in-depth qua mười một lớp đối phó song song** — từ `TelegramAuthFilter` HMAC ở rìa, qua `JwtAuthFilter` cho admin, `SecurityConfig` allowlist filter chain, `@PreAuthorize` ở controller, `@CurrentUser` resolver chống forge customer ID, WebSocket CONNECT dual auth, WebSocket SUBSCRIBE allowlist (catch ở code review P6), VNPay HMAC constant-time, enum whitelist trước `DATE_TRUNC` chống SQL injection, `vnp_TxnRef UNIQUE` chống replay, đến partial unique index `uq_assignment_shipper_started` chống wrong-shipper attribution.

Thứ sáu, **bộ test suite 274 case** (253 backend + 21 frontend) bao phủ unit, integration với Testcontainers PostgreSQL thật, slice test cho controller validation, security regression test cho mỗi lớp defense-in-depth — đảm bảo tỉ lệ build green 100% trên mỗi commit. Thứ bảy, **mười hai Flyway migration** quản lý toàn bộ schema từ V1 init đến V12 shipper_rating — không có thay đổi schema thủ công nào trên môi trường production. Thứ tám, **đóng gói Docker Compose năm container** với healthcheck chain (postgres → backend → miniapp + webadmin → nginx) và quy trình deploy chỉ ba lệnh — sẵn sàng triển khai trên mọi VPS có Docker.

### 5.1.3. Kết quả về quy trình phát triển

Bên cạnh sản phẩm, đề tài còn minh hoạ một quy trình phát triển có kỷ luật theo phương pháp **GSD (Get Shit Done)** với mười pha P0 đến P9, mỗi pha trải qua đầy đủ sáu bước: Research → Plan → Plan-check → Execute → Code-review → Fix. Mỗi bước sinh ra một artefact tài liệu được commit cùng mã nguồn để giữ truy vết: tổng cộng mười file plan (~33 000 dòng tài liệu thiết kế), tám file research (~6 500 dòng), chín file code review (mỗi pha một file đánh giá theo ba mức CRITICAL/IMPORTANT/MINOR), và hơn 160 commit nguyên tử với message tuân thủ chuẩn Conventional Commits.

Quy trình này đã chứng minh hiệu quả thực tế qua hai mặt định lượng. Thứ nhất, *catch lỗi sớm ở plan-checker* — mười hai blocker đã được phát hiện trước khi chạm code, tiết kiệm hàng giờ debug về sau (ví dụ: blocker yêu cầu thiết kế lại routing STOMP topic ở P5 trước khi gõ bất kỳ dòng nào). Thứ hai, *catch lỗi nghiêm trọng ở code review* — năm critical bug đã được phát hiện và vá trước khi merge: CR-1 STOMP authorization gap cho phép user A subscribe topic của user B, CR-2 wrong-shipper attribution của Live Location ping, CR-3 NPE trong channel message khi `from` null, CR-4 IPN audit gap (PaymentTransaction chưa persist cho IPN signature invalid), CR-5 ShipperState enum mismatch giữa Java và Postgres CHECK constraint. Nếu không có quy trình review hai vòng (plan-check trước và code-review sau), các lỗi này có thể đã đến tay reviewer hoặc tệ hơn là production.

### 5.1.4. Tài liệu sản phẩm

Đề tài để lại bộ tài liệu hoàn chỉnh phục vụ bảo vệ, đánh giá và tái sử dụng cho các nghiên cứu tiếp theo: báo cáo khoá luận với khoảng 24 000 từ chia thành năm chương; mười bảy ảnh chụp giao diện minh hoạ thực tế trên cả Mini App và Web Admin; một file *design spec* khoảng 4 700 dòng đặc tả toàn bộ thiết kế ban đầu; mười file plan thiết kế phân theo pha; tám file research khảo sát công nghệ; chín file code review; và file `RUNBOOK.md` mô tả tám bước smoke test thủ công cho ngày bảo vệ. Toàn bộ tài liệu được commit vào kho mã nguồn dưới `docs/`, đảm bảo bất kỳ ai clone về cũng có ngay tài liệu thiết kế gốc bên cạnh mã nguồn.

### 5.1.5. Số liệu định lượng tổng hợp

Bảng 5.2 tổng hợp các chỉ số định lượng cuối cùng của đề tài, lấy từ thống kê thực tế trên kho mã nguồn tại thời điểm bảo vệ.

**Bảng 5.2. Tổng hợp các chỉ số định lượng kết quả đạt được**

| Chỉ số | Giá trị |
|---|---|
| Tổng số commit nguyên tử | hơn 160 |
| Tổng số dòng mã (Java + TypeScript) | khoảng 21 000 |
| Tổng số từ báo cáo khoá luận | khoảng 24 000 |
| Tổng số dòng tài liệu plan + research + review | khoảng 42 000 |
| Tổng số test backend (unit + integration) | 253 |
| Tổng số test frontend (Vitest + Testing Library) | 21 |
| Tổng số test toàn dự án | 274 |
| Tỉ lệ build xanh trên mỗi commit ở `main` | 100% |
| Số file Flyway migration | 12 (V1 đến V12) |
| Số bounded context | 8 |
| Số endpoint REST | khoảng 35 |
| Số topic WebSocket | 2 (`/user/queue/order/*/location`, `/topic/admin/orders`) |
| Số sự kiện cross-module | 9 |
| Số trang Mini App | 9 |
| Số trang Web Admin | 8 |
| Số Dockerfile | 3 |
| Số container trong Docker Compose | 5 |
| Số ảnh chụp giao diện trong báo cáo | 17 |
| Số hình vẽ và sơ đồ | 37 |
| Số bảng biểu | 26 |

## 5.2. Hạn chế của hệ thống

Để không phóng đại kết quả, mục này khai báo trung thực mười hạn chế đã nhận diện trong quá trình phát triển và đánh giá. Tin tốt là *bốn trong số mười hạn chế ban đầu đã được khắc phục trong giai đoạn hoàn thiện cuối khoá luận* (giai đoạn từ tháng 05/2026 đến tháng 06/2026); sáu hạn chế còn lại được phân loại theo bản chất để hiểu rõ phương án xử lý nếu hệ thống được mở rộng.

### 5.2.1. Bốn hạn chế đã được khắc phục trong giai đoạn hoàn thiện

Trong giai đoạn hoàn thiện cuối, đề tài đã chủ động khắc phục bốn hạn chế từng được liệt kê trong bản đánh giá nội bộ ban đầu:

- **Hạn chế #6 — Đánh giá một chiều (khách → shipper).** Đã được khắc phục bằng Flyway V12 (`shipper_rating.sql`) bổ sung bảng `shipper_rating` và hai cột `rating_avg`, `rating_count` ở `telegram_user`, cùng với `ShipperRatingService` và endpoint REST. Shipper nay có thể đánh giá khách 1–5 sao kèm comment sau khi đơn `DELIVERED`; rating này không công khai cho khách thấy, chỉ admin truy cập được để cảnh báo khách hàng "khó tính" cho shipper khác.
- **Hạn chế #7 — Không có khung kiểm thử frontend.** Đã được khắc phục bằng việc bổ sung Vitest 1.x và Testing Library cho cả ba workspace pnpm. Tổng cộng 21 test frontend: 9 cho `@shop/shared` (format helpers), 6 cho `@shop/miniapp` (Zustand `useCart` store), 6 cho `@shop/webadmin` (`auth-store` và `OrderStatusBadge`).
- **Hạn chế #9 — Bundle size Web Admin vượt 740 kilobyte.** Đã được khắc phục bằng cấu hình Vite `manualChunks` tách Recharts thành chunk riêng lazy-load chỉ khi Dashboard hoặc Reports render. Bundle initial sau tối ưu chỉ còn khoảng 121 kilobyte gzipped — dưới ngưỡng cảnh báo của Vite.
- **Hạn chế #10 — Mật khẩu admin demo `admin123` không an toàn.** Đã được khắc phục bằng cách thay password trong V11 seed bằng chuỗi mạnh `Demo@Shop2026!` (12 ký tự kết hợp chữ hoa, chữ thường, số, ký tự đặc biệt). Tài khoản demo mới là `shop@example.com` / `Demo@Shop2026!`, khác biệt rõ ràng với chuỗi `admin@shop.local` / `admin123` ở V5.

### 5.2.2. Hạn chế out-of-scope cố ý

Hai hạn chế thuộc nhóm này là *quyết định thiết kế có chủ đích* — được loại khỏi phạm vi từ giai đoạn phân tích yêu cầu để giữ scope khoá luận khả thi, không phải lỗi thiếu sót:

- **Hạn chế #1 — Một shop duy nhất, chưa multi-tenant.** Hệ thống được thiết kế cho mô hình "một shop F&B/tạp hoá quy mô nhỏ–vừa với 1–10 shipper". Mở rộng thành SaaS đa-tenant đòi hỏi thêm bảng `shop`, thêm cột `shop_id` FK ở mọi bảng nghiệp vụ, thêm row-level security trên Postgres, và phải refactor mọi query có WHERE clause — một effort tương đương ba đến bốn tuần làm việc. Phương án xử lý nếu mở rộng được mô tả ở 5.3.2.
- **Hạn chế #3 — Phụ thuộc Telegram availability.** Telegram bị block ở một số quốc gia (Iran, Trung Quốc đại lục, một thời điểm là Nga). Ở Việt Nam hiện tại Telegram hoạt động ổn định nên hạn chế này không ảnh hưởng đến target market chính. Phương án xử lý nếu phục vụ thị trường có rào cản Telegram: mở rộng sang Zalo Mini App (xem 5.3.2) — Zalo có khoảng 75 triệu MAU tại Việt Nam và không bị block.

### 5.2.3. Hạn chế do giới hạn nhà cung cấp

- **Hạn chế #5 — GPS tracking giới hạn thời lượng Live Location của Telegram.** Telegram cho phép share Live Location tối đa 8 giờ mỗi lần — vượt khoảng thời gian này, stream sẽ tự ngắt và shipper phải share lại. Đối với mô hình giao hàng nội thành 1–10 km mỗi đơn (target chính của hệ thống), giới hạn này không thành vấn đề vì mỗi đơn kéo dài tối đa 60–90 phút. Đối với đơn liên tỉnh kéo dài hơn 8 giờ, có thể nâng cấp lên GPS streaming riêng (Geolocation API + background sync) trong tương lai, nhưng nằm ngoài phạm vi.

### 5.2.4. Hạn chế cần credential thật

- **Hạn chế #4 — Phụ thuộc VNPay sandbox cho thanh toán online.** Trong môi trường demo phục vụ bảo vệ, người demo cần đăng ký credential sandbox VNPay tại `sandbox.vnpayment.vn/devreg` — quá trình này tốn vài giờ và yêu cầu thông tin doanh nghiệp. Cặp mock `TEST01` / `TESTSECRETKEY123` ở `.env.example` chỉ phục vụ unit test offline, sẽ bị VNPay sandbox thật từ chối ngay. Phương án giảm phụ thuộc: tích hợp thêm Momo / ZaloPay / VietQR có sandbox dễ tiếp cận hơn (xem 5.3.2).
- **Hạn chế #8 — Manual smoke tests chưa hoàn thiện end-to-end với Telegram thật.** Tám bước smoke test trong `RUNBOOK.md` đã được thiết kế nhưng chưa được tự động hoá — đang trong quá trình thực hiện thủ công mỗi lần phát hành. Tự động hoá đòi hỏi Telegram bot test client (`telethon` hoặc `pyrogram`) cộng Playwright cho Mini App, là một effort khoảng một đến hai tuần. Phương án xử lý ở 5.3.3.

### 5.2.5. Hạn chế trade-off cố ý

- **Hạn chế #2 — Không có push notification ngoài Telegram.** Nếu khách hàng tắt thông báo Telegram, họ không nhận được cập nhật trạng thái đơn. Đây là *trade-off cố ý* của mô hình triển khai lai trên Telegram — đổi lại lợi ích "khách không phải cài app mới" và "không tốn chi phí Firebase Cloud Messaging". Phương án mở rộng nếu cần đa kênh notification: bổ sung FCM cho web push và app push, hoặc tích hợp Zalo OA notification (xem 5.3.2).

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

Việc liệt kê thẳng thắn các hạn chế này thực ra là một điểm cộng khi đánh giá khoá luận — chứng tỏ tác giả hiểu rõ phạm vi và các trade-off của hệ thống, không phóng đại kết quả.

## 5.3. Hướng phát triển

Các hướng phát triển được phân thành bốn nhóm theo độ ưu tiên kinh doanh và độ phức tạp kỹ thuật. Mỗi nhóm có khung thời gian effort khác nhau — từ 1–2 tuần cho các hoàn thiện trong scope hiện tại, đến nhiều tháng cho các hướng nghiên cứu nâng cao có thể trở thành đề tài khoá luận riêng.

### 5.3.1. Hoàn thiện trong scope hiện tại

Trong phạm vi tính năng đã thiết kế ban đầu, các hạng mục sau cần được hoàn thiện thêm để hệ thống đạt mức độ "polish" tốt nhất. Mỗi hạng mục có effort ước lượng khoảng một đến hai tuần.

**Chat trong bot giữa khách và shipper.** Khi đơn ở trạng thái `ASSIGNED` hoặc `STARTED`, shipper có thể bấm nút "Liên hệ khách" — bot sẽ ghép một cuộc trò chuyện ẩn danh giữa hai user qua chính bot làm middleman (relay tin nhắn hai chiều, không lộ số điện thoại). Mọi nội dung được audit vào bảng `bot_chat_log` phục vụ giải quyết tranh chấp. Đây là yêu cầu *Could have #13* trong MoSCoW gốc.

**Lưu địa chỉ thường dùng của khách.** Thêm bảng `customer_address(id, customer_id FK, label, address, lat, lng)` cho phép khách lưu các địa chỉ hay đặt (nhà, công ty, nhà bố mẹ…), kèm autocomplete ở trang Checkout — giảm thời gian đặt đơn cho khách quen từ khoảng ba mươi giây xuống còn năm giây. Đây là yêu cầu *Could have #12*.

**Hiển thị status history trên Order Detail (Web Admin).** Dữ liệu đã có sẵn trong bảng `status_history` (V4 migration) nhưng chưa được render. Chỉ cần thêm một component timeline ở trang `OrderDetail` của Web Admin, JOIN `status_history` rồi sort theo `created_at` ASC. Hữu ích cho việc truy vết vấn đề khi có dispute.

**Modal "Gán shipper" trên Web Admin.** Hiện tại admin gán shipper bằng cách gọi API `PUT /api/admin/orders/{id}/assign` trực tiếp; thiếu một UI picker filter shipper `AVAILABLE` để chọn nhanh. Thêm modal với danh sách shipper available, hiển thị khoảng cách (tính qua Haversine từ pickup), và rating trung bình.

**FSM đăng ký shipper hoàn thiện hơn.** FSM đã có ở module `bot` với bốn state cơ bản; có thể bổ sung state `AWAITING_CV_PHOTO` cho shipper upload ảnh chứng minh thư + bằng lái, sau đó admin duyệt thủ công ở Web Admin trước khi `ShipperApprovedEvent` được fire.

**Bot rating cho khách hàng (đã làm).** Hạng mục này đã được hoàn thành trong giai đoạn hoàn thiện qua V12 — shipper có thể rate khách 1–5 sao kèm comment, rating không công khai.

**Frontend test mở rộng.** Hiện tại có 21 test frontend; có thể mở rộng lên 40+ test với coverage cho các page chính (Catalog, Checkout, OrderDetail, Dashboard) bằng Testing Library + MSW (Mock Service Worker) để mock REST response.

### 5.3.2. Mở rộng tính năng vượt khỏi scope ban đầu

Các hướng mở rộng vượt scope ban đầu, effort khoảng một đến hai tháng mỗi hạng mục. Mục này nhấn mạnh hai ưu tiên cao là Zalo Mini App và tích hợp Momo/ZaloPay vì có giá trị kinh doanh trực tiếp lớn nhất với target market Việt Nam.

#### 5.3.2.1. Mở rộng sang Zalo Mini App (ưu tiên cao)

Zalo là ứng dụng nhắn tin có lượng người dùng lớn nhất Việt Nam với khoảng *75 triệu người dùng hoạt động hằng tháng* (MAU), lớn hơn cả Telegram ở thị trường nội địa. Việc mở rộng hệ thống sang Zalo Mini App có giá trị kinh doanh trực tiếp: tiếp cận lượng khách hàng tiềm năng lớn hơn, đa dạng hoá kênh phân phối, giảm phụ thuộc vào một nền tảng duy nhất. Về mặt kỹ thuật, Zalo Mini App SDK (`zmp-sdk`, đăng ký tại `miniapp.zalo.me`) khác Telegram WebApp về API gốc nhưng kiến trúc tổng thể tương đồng — UI code có thể tái sử dụng khoảng 80% (React + Tailwind + Recharts không thay đổi). Ba thay đổi chính cần thực hiện: (i) thay thư viện `@twa-dev/sdk` bằng `zmp-sdk` ở Mini App; (ii) đổi xác thực `initData` HMAC sang Zalo OA OAuth 2.0; (iii) thay `Telegram.WebApp.openLink` cho VNPay redirect bằng API tương đương của Zalo. Phía backend chỉ cần bổ sung một filter `ZaloAuthFilter` song hành với `TelegramAuthFilter` — phần business logic của các module `order`, `delivery`, `payment` hoàn toàn không thay đổi nhờ kiến trúc Modular Monolith đã cô lập module `auth`. Ước lượng tổng effort: ba đến bốn tuần. Hai nền tảng có thể chạy song song, khách hàng đến từ kênh nào cũng dùng được cùng một backend.

#### 5.3.2.2. Tích hợp thêm cổng thanh toán Momo, ZaloPay, VietQR (ưu tiên cao)

Hiện tại hệ thống chỉ tích hợp VNPay sandbox. Mở rộng sang Momo, ZaloPay, ShopeePay và VietQR có hai lợi ích lớn: (i) đa dạng phương thức thanh toán cho khách hàng cuối, tăng tỉ lệ hoàn tất thanh toán; (ii) giảm phụ thuộc vào credential sandbox VNPay — Momo và ZaloPay có quy trình đăng ký sandbox đơn giản hơn, giúp demo bảo vệ thuận lợi hơn. Pattern integration giống VNPay (sign URL với HMAC + verify IPN callback), nên mỗi cổng chỉ tốn khoảng ba đến năm ngày sau khi đã hoàn thiện VNPay. Cần xử lý ba điểm khác biệt: (i) thuật toán ký khác nhau (Momo dùng HMAC-SHA256, ZaloPay dùng HMAC-SHA256 với format payload riêng); (ii) callback URL khác (mỗi cổng có endpoint IPN riêng); (iii) refund flow riêng. Phương án thiết kế: tạo abstraction `PaymentGateway` interface trong module `payment` với hai method `createPaymentUrl(Order)` và `handleIpn(payload)`, mỗi cổng là một implementation cụ thể (`VnpayGateway`, `MomoGateway`, `ZalopayGateway`). Mã nghiệp vụ orchestration không thay đổi — chỉ cần inject `Map<PaymentMethod, PaymentGateway>` qua Spring.

#### 5.3.2.3. Các hướng mở rộng khác

Các hướng mở rộng khác được liệt kê ngắn gọn dưới đây:

- **Multi-shop / Multi-tenant.** Mở rộng từ "1 shop, 1 admin" thành SaaS phục vụ nhiều shop. Có thể dùng schema-per-tenant hoặc shared-schema với cột `shop_id` làm discriminator + row-level security trên Postgres.
- **Ứng dụng di động native.** Qua React Native, tái sử dụng khoảng 70% code từ Mini App. Lợi ích là push notification độc lập khỏi Telegram và sự hiện diện trên các kho ứng dụng (Apple App Store, Google Play).
- **Gợi ý sản phẩm bằng học máy.** Gợi ý món dựa trên lịch sử đơn của khách, độ phổ biến và thời điểm trong ngày. Có thể dùng collaborative filtering đơn giản (matrix factorization) hoặc tích hợp dịch vụ ngoài.
- **Loyalty program và coupon.** Bảng `coupon`, `customer_loyalty(points, tier)`. Bot có thể gửi mã giảm giá định kỳ theo cohort retention. Tăng retention 15–20% theo benchmark ngành F&B.
- **Voice ordering.** Khách gửi voice note vào bot ("Cho 2 phở bò"), backend dùng Whisper hoặc Google Speech-to-Text parse intent rồi tạo đơn nháp. Hỗ trợ khách lớn tuổi, người đang lái xe hoặc khuyết tật tay.
- **Multi-language.** i18n cho UI (Việt / Anh / Trung / Nga cho khách du lịch). Backend đã sẵn cột `language_code` ở `telegram_user`.
- **Quản lý nhập kho và barcode.** Cột `product.stock` đã có nhưng chưa có quản lý nhập kho. Thêm bảng `stock_movement` và barcode scanner trong Web Admin.
- **Tối ưu lộ trình last-mile.** Khi shipper có nhiều đơn cùng lúc, gom theo cluster địa lý và sắp xếp thứ tự tối ưu theo heuristic TSP. Có thể dùng Google OR-Tools.
- **Computer vision verify.** Shipper chụp ảnh hàng trước khi rời shop — CNN check hàng đúng mô tả. Có thể dùng CLIP zero-shot. Giảm dispute "đơn sai".

### 5.3.3. Củng cố cho môi trường sản phẩm (production hardening)

Để chuyển hệ thống từ MVP (Minimum Viable Product) hiện tại sang môi trường sản phẩm thực tế phục vụ khách hàng cuối, các hạng mục dưới đây cần được hoàn thiện. Tổng thời gian ước lượng cho toàn bộ là khoảng một đến hai tháng.

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

Mỗi hạng mục dưới đây có thể là một đề tài khoá luận hoặc nghiên cứu riêng — vượt qua nội dung của một MVP và bước vào lĩnh vực ứng dụng học máy, học sâu, bảo mật nâng cao, blockchain hoặc thiết kế hệ thống phân tán.

- **Dự đoán nhu cầu (demand forecasting).** Dự đoán số đơn theo giờ, ngày, khu vực để lên kế hoạch sản xuất và lịch shipper. Có thể dùng Facebook Prophet, ARIMA Box–Jenkins, hoặc LSTM seq2seq. Đầu vào là lịch sử đơn của hệ thống cộng dữ liệu thời tiết và lịch nghỉ lễ.
- **Định giá động (dynamic pricing).** Phí ship thích nghi theo tỉ lệ cung–cầu (giờ cao điểm tăng nhẹ, ngoài giờ giảm để khuyến khích). Tham khảo nghiên cứu surge pricing của Uber, Lyft.
- **Phát hiện bất thường (anomaly detection).** Phát hiện đơn giá trị bất thường, địa chỉ shipper từ chối nhiều lần, IPN replay attack. Có thể dùng Isolation Forest, One-Class SVM, hoặc autoencoder.
- **Học tăng cường cho tối ưu lộ trình.** Thay heuristic TSP bằng Deep Reinforcement Learning (Pointer Network của Bello et al. 2017, Transformer-based RL của Kool et al. 2019).
- **Federated learning cho gợi ý.** Huấn luyện model trên thiết bị khách (không gửi lịch sử về server) — vừa bảo mật vừa tuân thủ GDPR. Có thể dùng TensorFlow Federated hoặc PySyft.
- **Bằng chứng đơn hàng trên blockchain.** Hash hoá đơn lên Polygon hoặc Hyperledger Fabric để có bằng chứng không thay đổi cho dispute pháp lý.
- **Privacy-preserving location.** Khách chỉ thấy shipper cách N mét chứ không thấy toạ độ chính xác — geo-indistinguishability với differential privacy noise (Andrés et al.).
- **A/B testing framework tích hợp.** Hệ thống Statsig-like trong backend, đo conversion ở các bước Checkout bằng Bayesian A/B test với pyMC.

## 5.4. Kết luận

Đề tài *"Hệ thống quản lý giao hàng tích hợp Telegram Mini App, Web Admin và VNPay"* đã hoàn thành toàn bộ mục tiêu kỹ thuật đặt ra ở Chương 1: hỗ trợ đặt đơn với hai phương thức thanh toán COD và VNPay sử dụng ký HMAC-SHA512 và mô hình IPN-as-source-of-truth; theo dõi vị trí shipper thời gian thực qua Telegram Live Location với độ trễ end-to-end dưới ba giây; áp dụng máy trạng thái hữu hạn cho bốn thực thể chính (`Order`, `DeliveryAssignment`, `Payment` và FSM hội thoại đăng ký shipper); thông báo realtime qua Spring WebSocket STOMP cho admin và Telegram Bot cho khách hàng và shipper; đóng gói đầy đủ bằng Docker Compose năm container chạy được chỉ với ba lệnh shell. Toàn bộ tám yêu cầu Must và ba yêu cầu Should của phân loại MoSCoW đều đã được hiện thực, kiểm thử qua 274 test (253 backend cộng 21 frontend) với tỉ lệ build green 100% trên mỗi commit, và minh hoạ bằng 17 ảnh chụp giao diện thực tế kèm bộ dữ liệu seed V11 phục vụ demo.

Về mặt khoa học, đề tài đem lại bốn đóng góp kỹ thuật chính: (i) **mô hình triển khai lai trên nền tảng Telegram** sử dụng đồng thời ba kênh (Mini App, Bot, Web Admin) với phân vai theo thiết bị — một hướng tiếp cận khả thi và kinh tế cho phân khúc shop F&B và tạp hoá quy mô nhỏ–vừa tại Việt Nam; (ii) **kiến trúc Modular Monolith với DDD-lite** chứng minh là lựa chọn phù hợp cho đề tài khoá luận hoặc dự án MVP quy mô tương tự — vừa giữ ưu điểm "một container, một lệnh deploy", vừa đạt mức độ cô lập theo Domain-Driven Design, đồng thời tạo lối thoát chuyển sang microservices trong tương lai mà không phải viết lại nghiệp vụ; (iii) **tích hợp VNPay với IPN-as-source-of-truth** kèm các đảm bảo kỹ thuật cụ thể (constant-time hash comparison, idempotent IPN, JSONB audit trail bắt buộc, `Propagation.REQUIRES_NEW` trong AFTER_COMMIT phase); (iv) **mô hình bảo mật defense-in-depth gồm mười một lớp** đối phó song song, với mỗi lớp đều có regression test khoá đảm bảo. Đề tài cũng minh hoạ một quy trình phát triển có kỷ luật theo phương pháp **GSD (Get Shit Done)** với mười pha P0 đến P9, mỗi pha trải qua sáu bước Research → Plan → Plan-check → Execute → Code-review → Fix, sinh ra hơn 160 commit nguyên tử và khoảng 42 000 dòng tài liệu thiết kế cho 21 000 dòng mã — tỉ lệ tài liệu trên mã hai phần một chứng tỏ kỷ luật "thiết kế trước khi viết mã" được tuân thủ nghiêm túc.

Về mặt thực tiễn, hệ thống của đề tài lấp vào khoảng trống định vị giữa các nền tảng tổng hợp (chi phí cao, mất hoa hồng, mất kiểm soát dữ liệu) và shop tự xây ứng dụng (chi phí phát triển lớn, đường cong học hỏi cao). Với chi phí vận hành chỉ khoảng mười đô-la Mỹ mỗi tháng cho một VPS, hệ thống có thể phục vụ ngay các shop kinh doanh nhỏ và vừa muốn tự chủ về dữ liệu và quy trình giao hàng. Bài học kinh nghiệm về phương pháp phát triển mà tác giả thu hoạch được — bao gồm việc tuân thủ TDD (test-driven development) ngay từ unit test đầu tiên, lập kế hoạch chi tiết trước khi gõ một dòng code, và duy trì hai vòng review (plan-check trước và code-review sau) — đã giúp catch năm critical bug trước khi merge và mười hai blocker trước khi chạm code, tiết kiệm hàng giờ debug về sau.

Tác giả xin gửi lời cảm ơn chân thành đến giảng viên hướng dẫn đã tận tình chỉ bảo trong suốt quá trình thực hiện đề tài, đến hội đồng phản biện đã dành thời gian đánh giá báo cáo, và đến các tác giả của các công trình tham khảo đã trích dẫn ở phần Tài liệu tham khảo. Các hướng phát triển ở mục 5.3 — đặc biệt là mở rộng sang Zalo Mini App và tích hợp Momo / ZaloPay — sẽ tiếp tục được tác giả nghiên cứu trong giai đoạn sau khoá luận, với mục tiêu đưa hệ thống từ prototype hiện tại lên môi trường vận hành thực tế phục vụ các shop kinh doanh nhỏ và vừa tại Việt Nam.

\newpage
