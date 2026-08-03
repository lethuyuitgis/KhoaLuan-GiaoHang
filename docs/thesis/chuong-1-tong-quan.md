# Chương 1. Tổng quan đề tài

## 1.1. Đặt vấn đề

Thương mại điện tử và dịch vụ giao đồ ăn trực tuyến tại Việt Nam phát triển bùng nổ trong một thập kỷ qua. Theo VECOM và Statista, quy mô thị trường giao đồ ăn trực tuyến năm 2024 vượt mốc một tỷ đô la Mỹ với mức tăng trưởng kép hai con số kéo dài liên tục [5]. GrabFood và ShopeeFood chi phối thị phần; MoMo, ZaloPay, VNPay là phương thức thanh toán quen thuộc của người tiêu dùng đô thị.

Sự phát triển này chủ yếu phục vụ tệp khách hàng đại trà của các nền tảng tổng hợp (aggregator). Doanh nghiệp nhỏ và vừa — quán ăn gia đình, tạp hoá khu phố, tiệm bánh hộ kinh doanh — chỉ có ba lựa chọn, đều không hoàn hảo:

1. *Tham gia nền tảng tổng hợp:* tiếp cận nhiều người dùng nhưng chịu hoa hồng 20–25% mỗi đơn, mất quyền sở hữu dữ liệu khách hàng, không kiểm soát trải nghiệm thương hiệu.
2. *Tự xây ứng dụng riêng:* chi phí ứng dụng native (iOS, Android), backend, hạ tầng bản đồ và cổng thanh toán lên tới hàng trăm triệu đồng, vượt khả năng đầu tư của shop nhỏ.
3. *Vận hành thủ công:* nhận đơn qua Messenger, Zalo, điện thoại và điều phối shipper bằng tin nhắn rời rạc; không theo dõi được trạng thái đơn và vị trí giao hàng thời gian thực; dễ thất lạc đơn, không thể mở rộng.

Telegram là lựa chọn tiềm năng nhưng chưa được khai thác đúng mức tại Việt Nam: khoảng một tỷ người dùng hoạt động hằng tháng, thuộc nhóm ứng dụng tăng trưởng nhanh nhất Đông Nam Á [25]. Ba đặc tính kỹ thuật khiến nền tảng này phù hợp với hệ thống giao hàng quy mô vừa và nhỏ:

- *Telegram Bot API:* xây dựng tác tử (bot) hội thoại miễn phí, không giới hạn lưu lượng tin nhắn, có bàn phím nội tuyến (inline keyboard) cho thao tác một chạm.
- *Telegram Mini App* (Web App): nhúng ứng dụng web đầy đủ tính năng vào cửa sổ chat, dùng chữ ký HMAC-SHA256 trên dữ liệu khởi tạo (initData) để xác thực người dùng, không cần cơ chế đăng nhập riêng.
- *Telegram Live Location:* chia sẻ vị trí GPS liên tục từ 15 phút đến 8 giờ, tần suất 5–10 giây, được Telegram ký HMAC trước khi gửi tới webhook của bot; nhờ đó không phải tự phát triển mô-đun theo dõi GPS (xin quyền, foreground service trên Android, tối ưu pin, xử lý mất sóng).

Đề tài chọn Telegram làm cổng vào duy nhất cho cả ba vai trò (khách hàng, shipper, chủ shop) nhằm cung cấp giải pháp giao hàng đầu cuối cho shop F&B và tạp hoá quy mô 1–10 shipper, khoảng 50–500 đơn mỗi ngày. Bảng 1.1 so sánh hệ thống với hai nhóm giải pháp phổ biến tại Việt Nam.

**Bảng 1.1. So sánh hệ thống của đề tài với các giải pháp giao hàng hiện có**

| Tiêu chí | Hệ thống của đề tài | GrabFood / ShopeeFood | Shop tự xây ứng dụng riêng |
|---|---|---|---|
| Khách hàng cần cài ứng dụng mới | Không (chỉ dùng Telegram đã có) | Có (~150 MB) | Có |
| Theo dõi GPS thời gian thực | Có, qua Telegram Live Location | Có | Hiếm khi có; thường chỉ link web |
| Chi phí triển khai hằng tháng | ~10 USD (1 VPS phổ thông) | Không áp dụng (mô hình thương mại) | 50–100 USD (PaaS + dịch vụ bản đồ) |
| Tự quản lý hạ tầng & dữ liệu | Có | Không | Có |
| Tích hợp cổng thanh toán nội địa | VNPay (mở rộng được) | Đầy đủ | Tuỳ doanh nghiệp |
| Web Admin riêng cho chủ shop | Có | Có | Hiếm |
| Kiến trúc dễ mở rộng | Modular Monolith (DDD-lite) | Microservices nội bộ | Thường monolith thuần |
| Phụ thuộc bên thứ ba | Telegram, VNPay | Toàn bộ chuỗi vận hành | Cổng thanh toán, bản đồ, lưu trữ |

![Hình 1.1. Telegram Mini App của khách hàng — màn hình danh mục sản phẩm](screenshots/miniapp-cust-01-catalog.png){width=7cm}

![Hình 1.2. Web Admin của chủ shop — bảng điều khiển tổng quan](screenshots/admin-02-dashboard.png){width=13cm}

Hệ thống lấp khoảng trống định vị giữa hai nhóm: chi phí triển khai thấp tương đương phương án tự xây, trải nghiệm tracking gần với các nền tảng tổng hợp, và chủ shop tự chủ hoàn toàn về dữ liệu khách hàng.

## 1.2. Mục tiêu đề tài

Mục tiêu tổng quát là *xây dựng một hệ thống quản lý giao hàng đầu cuối* cho shop online quy mô nhỏ–vừa, với ba luồng nghiệp vụ cốt lõi trên ba kênh giao diện:

1. *Khách hàng* đặt đơn, theo dõi vị trí shipper và đánh giá dịch vụ qua **Telegram Mini App** kết hợp **Telegram Bot**.
2. *Shipper* nhận đơn, cập nhật trạng thái giao hàng và chia sẻ vị trí thời gian thực qua **Telegram Bot** kết hợp **Telegram Mini App**.
3. *Chủ shop* quản lý đơn, sản phẩm, shipper, xem báo cáo doanh thu và nhận thông báo thời gian thực qua **Web Admin** trên trình duyệt máy tính (kết hợp Telegram Bot cho thông báo đẩy).

Năm mục tiêu kỹ thuật được lượng hoá:

- *Mục tiêu 1.* Đặt đơn trực tuyến với hai phương thức thanh toán: tiền mặt khi nhận hàng (COD) và **VNPay sandbox** dùng HMAC-SHA512 với IPN làm nguồn sự thật.
- *Mục tiêu 2.* Theo dõi vị trí shipper thời gian thực trên bản đồ Leaflet qua Telegram Live Location, độ trễ end-to-end dưới 3 giây từ lúc Telegram phát bản chỉnh sửa tin nhắn vị trí đến lúc khách hàng thấy chấm di chuyển.
- *Mục tiêu 3.* Áp dụng máy trạng thái hữu hạn (Finite State Machine) cho vòng đời đơn hàng với bảy trạng thái và danh sách chuyển trạng thái được phép, ngăn chuyển dịch trái phép tại tầng nghiệp vụ.
- *Mục tiêu 4.* Thông báo thời gian thực bằng Spring WebSocket (STOMP) và Telegram Bot: chủ shop thấy đơn mới trên dashboard, shipper nhận offer trong vòng vài trăm mili-giây.
- *Mục tiêu 5.* Đóng gói toàn hệ thống bằng Docker Compose để triển khai bằng đúng ba lệnh, hỗ trợ môi trường phát triển (`dev`) và sản phẩm (`prod`) với chế độ fail-fast cho biến môi trường nhạy cảm.

Đề tài còn hướng tới mục tiêu phương pháp luận: *minh hoạ một quy trình phát triển phần mềm có kỷ luật* theo phương pháp GSD (Get Shit Done) và giữ build xanh sau mỗi commit nguyên tử (atomic commit).

## 1.3. Phạm vi đề tài

Phạm vi đề tài được phân loại theo phương pháp **MoSCoW** (Must / Should / Could / Won't have), trích từ tài liệu thiết kế hệ thống, nhằm khoanh rõ ranh giới giữa phần bắt buộc và phần dự phòng, tránh phình phạm vi (scope creep).

**Bảng 1.2. Danh sách yêu cầu theo phương pháp MoSCoW (Must / Should / Could / Won't)**

| STT | Tính năng | Ưu tiên | Trạng thái |
|---|---|---|---|
| 1 | Khách đặt đơn qua Telegram Mini App | Must | Đã hoàn thành |
| 2 | Chủ shop xem và gán đơn cho shipper qua Web Admin | Must | Đã hoàn thành |
| 3 | Shipper nhận / từ chối đơn qua Telegram Bot | Must | Đã hoàn thành |
| 4 | Cập nhật trạng thái đơn theo máy trạng thái hữu hạn | Must | Đã hoàn thành |
| 5 | Telegram Live Location khi đang giao | Must | Đã hoàn thành |
| 6 | Thông báo thời gian thực qua Bot và WebSocket | Must | Đã hoàn thành |
| 7 | Lịch sử đơn hàng (khách hàng và chủ shop) | Must | Đã hoàn thành |
| 8 | Thanh toán điện tử qua VNPay sandbox | Must | Đã hoàn thành |
| 9 | Tính cước theo khoảng cách (công thức Haversine) | Should | Đã hoàn thành |
| 10 | Đánh giá shipper 1–5 sao sau khi giao | Should | Đã hoàn thành |
| 11 | Báo cáo và biểu đồ cho chủ shop (Recharts) | Should | Đã hoàn thành |
| 12 | Lưu địa chỉ thường dùng của khách | Could | Chưa triển khai (hướng phát triển) |
| 13 | Chat trong Bot giữa khách và shipper | Could | Chưa triển khai (hướng phát triển) |
| 14 | Đa shop / đa người thuê (multi-tenant) | Won't | Ngoài phạm vi |
| 15 | Ứng dụng quản trị siêu admin cho nhiều shop | Won't | Ngoài phạm vi |

Cả 11 yêu cầu Must và Should (8 Must, 3 Should) đã được hiện thực và kiểm thử đầy đủ; hai yêu cầu Could thuộc hướng phát triển nêu ở Chương 5; hai yêu cầu Won't nằm ngoài phạm vi. Các giới hạn cụ thể khác gồm:

- *Một shop duy nhất:* không hỗ trợ nhiều cửa hàng trên cùng một backend.
- *Ba vai trò:* khách hàng, shipper, chủ shop; không có vai trò siêu quản trị viên hệ thống.
- *Một cổng thanh toán điện tử:* chỉ VNPay sandbox; mở rộng sang MoMo / ZaloPay là hướng phát triển.
- *Không tích hợp đối tác giao vận bên ngoài* (Giao Hàng Nhanh, Giao Hàng Tiết Kiệm, Ahamove...) — shipper là nhân sự nội bộ của shop.
- *Bản đồ và tile:* OpenStreetMap với react-leaflet, không dùng Google Maps để tránh phụ thuộc API key trả phí.

## 1.4. Đóng góp của đề tài

Phần này tổng hợp tám đóng góp kỹ thuật chính của đề tài và các chỉ số định lượng kèm theo.

### 1.4.1. Mô hình triển khai lai trên nền tảng Telegram

Ba kênh của Telegram được dùng đồng thời theo mô hình *lai (hybrid)*, mỗi kênh cho một vai trò: **Telegram Mini App** (React 18, Vite 5) cho khách hàng duyệt sản phẩm, đặt đơn, theo dõi bản đồ; **Telegram Bot** (`telegrambots-spring-boot-starter` 6.9.7.1) cho shipper nhận offer và thao tác một chạm qua inline keyboard, đồng thời cho khách hàng đánh giá sau khi nhận hàng; **Web Admin** (React, Tailwind) cho chủ shop làm việc trên máy tính. Khác với các nền tảng thương mại ép mọi vai trò vào một ứng dụng di động duy nhất, cách phân vai theo thiết bị tối ưu trải nghiệm của từng vai trò.

### 1.4.2. Theo dõi vị trí thời gian thực bằng Telegram Live Location

Theo dõi shipper trên bản đồ — hạng mục tốn kém nhất khi xây ứng dụng giao hàng — được hiện thực **không cần lập trình streaming GPS phía client**. Shipper chia sẻ vị trí trực tiếp từ Telegram (15 phút / 1 giờ / 8 giờ); Telegram gửi `message.location` ban đầu rồi `edited_message.location` mỗi 5–10 giây. Phía backend, `LiveLocationHandler` wire vào webhook bot, lưu `location_ping`, phát `LocationPingReceivedEvent`; `LocationBroadcaster` lắng nghe bằng `@TransactionalEventListener(AFTER_COMMIT)` rồi đẩy qua STOMP tới khách hàng. Cách này tiết kiệm khoảng 80% công sức so với tự lập trình GPS streaming, an toàn hơn vì vị trí đã được Telegram ký HMAC, và tiết kiệm pin cho shipper.

### 1.4.3. Kiến trúc Modular Monolith với 8 ngữ cảnh nghiệp vụ

Đề tài chọn *Modular Monolith* thay vì Microservices cho phù hợp phạm vi và quy mô. Tám ngữ cảnh nghiệp vụ — `shared`, `auth`, `order`, `delivery`, `payment`, `bot`, `notification`, `app` — tổ chức theo đồ thị phụ thuộc một chiều (DAG, không có chu trình), giao tiếp qua *Spring Application Events* với `@TransactionalEventListener(AFTER_COMMIT)`: (i) module phát sự kiện không cần biết internal của module nhận, (ii) sự kiện chỉ fire sau khi transaction commit thành công, (iii) có thể tách thành microservices về sau gần như không sửa code nghiệp vụ. Hệ thống vừa giữ ưu điểm "1 container, 1 lệnh deploy" của monolith, vừa cô lập trách nhiệm theo nguyên tắc DDD.

### 1.4.4. Tích hợp VNPay sandbox với IPN làm nguồn sự thật

Hệ thống áp dụng mẫu *IPN-as-source-of-truth* mà VNPay khuyến nghị, thay vì cách làm sai phổ biến là tin tưởng Return URL: `POST /api/payment/vnpay/create` (xác thực bằng Telegram initData) tạo `Payment(PENDING)` và ký URL HMAC-SHA512; Mini App mở URL bằng `WebApp.openLink` (không dùng `window.location.href` — pitfall hay gặp); `GET /api/payment/vnpay/return` xác minh chữ ký và **chỉ render trang HTML**, không cập nhật cơ sở dữ liệu; `POST /api/payment/vnpay/ipn` (server-to-server) xác minh chữ ký bằng `MessageDigest.isEqual` (constant time, chống timing attack), kiểm tra số tiền, đảm bảo idempotent rồi cập nhật trạng thái. Mọi sự kiện IPN, kể cả invalid signature, được lưu vào `payment_transaction.raw_payload` (JSONB) phục vụ forensics. `PaymentExpiryScheduler` chạy cron 60 giây, đánh dấu thanh toán PENDING quá 15 phút thành FAILED để giải phóng đơn.

### 1.4.5. Máy trạng thái hữu hạn (FSM) cho hội thoại đánh giá

Đánh giá shipper 1–5 sao dùng một máy trạng thái hữu hạn nhỏ cho luồng nhập comment tuỳ chọn: sau khi đơn `DELIVERED`, bot gửi inline keyboard 5 sao; callback `RATE:<orderId>:<stars>` do `RatingService.rate` xử lý, lưu vào bảng `rating` (V10), và `shipper_profile.rating_avg` được tính lại từ aggregate query để tránh trôi số (floating-point drift) trên `NUMERIC(3,2)`. Bot prompt nhập comment, `ConversationStateService` chuyển user sang state `CUSTOMER_RATING_COMMENT`, lưu payload JSONB vào bảng `conversation_state` (V3) qua `@JdbcTypeCode(SqlTypes.JSON)` của Hibernate 6; khi khách gõ text hoặc `/skip`, FSM tự clear. Đây là hạ tầng tái sử dụng được cho các flow FSM khác.

### 1.4.6. Bảo mật defense-in-depth qua 11 lớp song song

Thay vì dựa duy nhất vào một cơ chế, hệ thống bố trí mười một lớp phòng thủ song song: (L1) `TelegramAuthFilter` xác minh HMAC initData chống spoof; (L2) `JwtAuthFilter` với TTL 15 phút chống session hijack; (L3) allowlist filter chain trong `SecurityConfig`; (L4) `@PreAuthorize` mức controller chống privilege escalation; (L5) custom `@CurrentUser` resolver chống forge customerId trong URL path; (L6) WebSocket CONNECT interceptor (dual auth) chống anonymous connect; (L7) WebSocket SUBSCRIBE allowlist chống cross-user data leak; (L8) VNPay HMAC `MessageDigest.isEqual` chống timing attack; (L9) groupBy enum whitelist trước `DATE_TRUNC` chống SQL injection; (L10) `vnp_TxnRef UNIQUE` và bảng audit `payment_transaction` chống replay; (L11) partial unique index `uq_assignment_shipper_started` (V8) chống gán nhầm vị trí cho shipper khác. Mỗi lớp đều có ít nhất một regression test khoá đảm bảo.

### 1.4.7. Quy trình phát triển có kỷ luật theo phương pháp GSD

Toàn bộ 10 pha (P0 đến P9) được phát triển theo **GSD (Get Shit Done)** — Research → Plan → Plan-check → Execute → Code-review → Fix — sinh ra 10 plan file (~33 000 dòng), 8 research doc (~6 500 dòng), 9 code review report và hơn 152 commit nguyên tử. Quy trình giúp phát hiện lỗi sớm: 12 vấn đề chặn ở khâu plan-checker (trước khi viết mã) và 5 bug nghiêm trọng ở khâu code-review (khe hở phân quyền STOMP, gán sai shipper cho Live Location ping, NPE khi `from` null, khe hở audit IPN, lệch enum `ShipperState` giữa Java và CHECK constraint). Đây là đóng góp về mặt *quy trình* bên cạnh đóng góp về *sản phẩm*, cho thấy GSD là một thay thế đủ kỷ luật cho mô hình Waterfall trong khoá luận tốt nghiệp.

### 1.4.8. Sẵn sàng triển khai (deployment-ready) chỉ bằng ba lệnh

Hệ thống được containerized hoàn chỉnh từ pha P9: ba lệnh `git clone`, `cp .env.example .env` (điền 4 secret) và `docker compose up -d` dựng toàn bộ stack trong khoảng 3 phút (lần đầu build) hoặc 30 giây (cache). Năm container gồm `postgres:16-alpine` (data volume, healthcheck), `backend` (Spring Boot multi-stage Maven build, JRE 17 slim, non-root user, healthcheck `/actuator/health`), `miniapp` và `webadmin` (Vite build phục vụ qua nginx alpine, mỗi image ~10 MB), `nginx` reverse proxy. `application-prod.yml` áp dụng *fail-fast* cho secret: thiếu biến môi trường thì backend không khởi động, tránh deploy production với credential test. Demo seed V11 (1 admin, 10 sản phẩm, 6 telegram user, 30 đơn, 18 assignment, 10 rating, 11 payment) giúp dashboard có dữ liệu ngay sau khi up.

### 1.4.9. Các chỉ số định lượng đáng chú ý

Bảng 1.3 tổng hợp các con số then chốt rút ra từ kho mã nguồn và tài liệu thiết kế tại thời điểm bảo vệ (chưa tính hai pha mở rộng ở Phụ lục I).

**Bảng 1.3. Các chỉ số định lượng đáng chú ý của đề tài**

| Chỉ số | Giá trị |
|---|---|
| Tổng số commit nguyên tử | hơn 152 |
| Tổng số dòng mã (Java + TypeScript) | ~21 000 |
| Tổng số dòng plan + research + review | ~42 000 |
| Số test backend (unit + integration) | 253 (231 unit + 22 integration) |
| Tỷ lệ build xanh trên mỗi commit | 100% |
| Số Flyway migration | 15 (V1 đến V15) |
| Số ngữ cảnh nghiệp vụ (bounded context) | 8 |
| Số endpoint REST | 39 (danh sách đầy đủ ở Phụ lục B) |
| Số topic WebSocket | 2 (`/user/queue/order/*/location`, `/topic/admin/orders`) |
| Số sự kiện cross-module | 9 |
| Số trang Mini App | 9 (3 cho khách, 2 cho shipper, 4 chung) |
| Số trang Web Admin | 8 |
| Số Dockerfile | 3 (backend, miniapp, webadmin) |
| Số container trong compose | 5 |

Các con số trên cho thấy *mật độ kiểm thử* (253 test backend cho ~21 000 dòng mã, xấp xỉ 1,2 test / 100 dòng — vượt mức trung bình của các dự án mã nguồn mở tương đương) và *mật độ tài liệu thiết kế* (~42 000 dòng cho ~21 000 dòng mã, tỉ lệ 2:1), phản ánh kỷ luật "thiết kế trước khi viết mã".

## 1.5. Kết luận chương

Chương 1 đã trình bày bối cảnh bài toán giao hàng quy mô nhỏ–vừa, lý do chọn Telegram làm cổng vào duy nhất, mục tiêu, phạm vi theo phương pháp MoSCoW và tám đóng góp kỹ thuật chính. Chương 2 làm rõ các nền tảng công nghệ then chốt mà đề tài dựa vào để hiện thực các đóng góp trên.
