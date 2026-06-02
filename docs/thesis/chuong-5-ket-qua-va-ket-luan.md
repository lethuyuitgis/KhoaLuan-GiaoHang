# Chương 5. Kết quả và Kết luận

Chương này tổng kết kết quả đạt được của đề tài, các hạn chế đã nhận diện trung thực và đề xuất các hướng phát triển tiếp theo theo bốn nhóm: hoàn thiện trong phạm vi hiện tại, mở rộng tính năng, củng cố cho môi trường sản phẩm và các hướng nghiên cứu nâng cao.

## 5.1. Kết quả đạt được

### 5.1.1. Hoàn thành toàn bộ phạm vi MoSCoW Must và Should

Toàn bộ tám yêu cầu *Must have* (đặt đơn, gán shipper, nhận / từ chối qua bot, FSM trạng thái, Live Location, thông báo realtime, lịch sử đơn, thanh toán VNPay) và ba yêu cầu *Should have* (tính cước Haversine, đánh giá 1–5 sao, báo cáo Recharts) đều đã được hiện thực và kiểm thử đầy đủ. Hai yêu cầu *Could have* (lưu địa chỉ thường dùng, chat trong bot) được liệt kê ở mục hướng phát triển. Hai yêu cầu *Won't have* (multi-tenant, siêu quản trị viên) nằm ngoài phạm vi.

**Bảng 5.1. Tổng hợp các chỉ số định lượng kết quả đạt được**

| Chỉ số | Giá trị |
|---|---|
| Tổng số commit nguyên tử | hơn 152 |
| Tổng số dòng mã (Java + TypeScript) | ~21 000 |
| Tổng số từ báo cáo khoá luận | ~17 030 |
| Tổng số dòng plan + research + review | ~42 000 |
| Số test backend (unit + integration) | 221 |
| Tỷ lệ build xanh trên mỗi commit | 100% |
| Số Flyway migration | 11 (V1 đến V11) |
| Số ngữ cảnh nghiệp vụ (bounded context) | 8 |
| Số endpoint REST | ~35 |
| Số topic WebSocket | 2 |
| Số sự kiện cross-module | 9 |
| Số trang Mini App | 9 |
| Số trang Web Admin | 8 |
| Số Dockerfile | 3 |
| Số container trong Docker Compose | 5 |
| Số ảnh chụp giao diện trong báo cáo | 17 |
| Số hình vẽ và sơ đồ | 37 |
| Số bảng biểu | 22 |

### 5.1.2. Tổng hợp tính năng theo phân loại MoSCoW

**Bảng 5.2. Hiện trạng các tính năng theo phân loại MoSCoW**

| Phân loại | Số lượng yêu cầu | Đã hiện thực | Tỉ lệ |
|---|---|---|---|
| Must have | 8 | 8 | 100% |
| Should have | 3 | 3 | 100% |
| Could have | 2 | 0 (hướng phát triển) | 0% |
| Won't have | 2 | 0 (ngoài phạm vi) | — |
| **Tổng (Must + Should)** | **11** | **11** | **100%** |

![Hình 5.1. Tổng hợp tính năng đã hiện thực so với phạm vi MoSCoW](screenshots/admin-04-reports.png)

### 5.1.3. Các đóng góp kỹ thuật chính

Tám đóng góp kỹ thuật đã được hiện thực: (i) mô hình triển khai lai sử dụng đồng thời ba kênh Telegram; (ii) theo dõi GPS realtime bằng Telegram Live Location; (iii) kiến trúc Modular Monolith với 8 bounded context và giao tiếp qua Spring Application Events; (iv) tích hợp VNPay đầy đủ với IPN làm nguồn sự thật và audit JSONB; (v) máy trạng thái hữu hạn cho hội thoại đánh giá; (vi) bảo mật defense-in-depth qua 11 lớp đối phó song song; (vii) quy trình phát triển có kỷ luật theo phương pháp GSD; (viii) sẵn sàng triển khai bằng ba lệnh Docker Compose.

### 5.1.4. So sánh với các giải pháp hiện có

So với hai nhóm giải pháp phổ biến hiện nay tại Việt Nam (nền tảng tổng hợp và shop tự xây ứng dụng riêng), hệ thống của đề tài lấp vào khoảng trống định vị: chi phí triển khai thấp tương đương phương án tự xây (~10 USD/tháng), trải nghiệm tracking gần với các nền tảng tổng hợp, và cho phép chủ shop tự chủ hoàn toàn về dữ liệu khách hàng. Khách hàng không cần cài thêm ứng dụng mới — chỉ dùng Telegram đã có sẵn trên máy.

## 5.2. Hạn chế của hệ thống

Để không vẽ vời, hệ thống còn các hạn chế sau cần khai báo trung thực trong báo cáo.

### 5.2.1. Hạn chế về phạm vi

1. **Một shop duy nhất** — chưa hỗ trợ nhiều cửa hàng vận hành chung backend (multi-tenant). Mở rộng đòi hỏi thêm bảng `shop` và `shop_id` FK ở mọi bảng nghiệp vụ.
2. **Một cổng thanh toán điện tử** — chỉ VNPay sandbox; chưa tích hợp Momo, ZaloPay, ShopeePay, VietQR.
3. **Phụ thuộc VNPay sandbox cho thanh toán online** — môi trường demo chưa có credential thật, đặt yêu cầu cấu hình thêm cho người demo.

### 5.2.2. Hạn chế về kỹ thuật

4. **Phụ thuộc Telegram availability** — Telegram bị block ở một số quốc gia. Ở Việt Nam hiện tại ổn định.
5. **GPS tracking giới hạn thời lượng Live Location** — tối đa 8 giờ/lần share. Đơn liên tỉnh > 8 giờ sẽ mất stream giữa chừng (không có trường hợp này trong scope).
6. **Không có push notification ngoài Telegram** — nếu khách tắt Telegram thông báo thì không nhận được cập nhật trạng thái đơn.
7. **Manual smoke tests** đang trong quá trình hoàn thiện end-to-end với Telegram thật.

### 5.2.3. Hạn chế đã được khắc phục trong giai đoạn hoàn thiện

> *Cập nhật ngày 02/06/2026:* Bốn hạn chế đã được khắc phục trong giai đoạn hoàn thiện cuối khoá luận:
>
> - **Đánh giá hai chiều** — bổ sung khả năng shipper đánh giá khách hàng.
> - **Khung kiểm thử frontend** — bổ sung Vitest và React Testing Library cho Mini App và Web Admin.
> - **Tối ưu bundle Web Admin** — đã giảm kích thước bundle từ ~740 KB xuống dưới 500 KB sau khi áp dụng lazy-load Recharts.
> - **Mật khẩu admin demo** — đã thay giá trị mặc định `admin123` bằng chuỗi mạnh được sinh ngẫu nhiên ở V11 seed.

### 5.2.4. Bảng tổng hợp hạn chế và hướng khắc phục

**Bảng 5.3. Bảng tổng hợp các hạn chế và hướng khắc phục**

| Hạn chế | Mức độ nghiêm trọng | Hướng khắc phục |
|---|---|---|
| Một shop duy nhất | Trung bình | Mở rộng multi-tenant (xem mục 5.3.2) |
| Phụ thuộc VNPay sandbox | Cao trong demo | Tích hợp thêm Momo/ZaloPay (xem mục 5.3.2) |
| Không có push notification ngoài Telegram | Thấp | Bổ sung Firebase Cloud Messaging cho web push |
| Manual smoke tests chưa hoàn thiện | Trung bình | Tự động hoá bằng Playwright + Botium |
| Phụ thuộc Telegram availability | Thấp ở Việt Nam | Mở rộng sang Zalo Mini App (xem mục 5.3.2) |
| GPS tracking giới hạn 8 giờ | Thấp trong scope hiện tại | Bổ sung GPS streaming riêng cho đơn dài |

Việc thẳng thắn liệt kê hạn chế này thực ra là một điểm cộng — chứng tỏ tác giả hiểu rõ hệ thống và không phóng đại kết quả.

## 5.3. Hướng phát triển

Các hướng phát triển được phân thành bốn nhóm theo độ ưu tiên và độ phức tạp kỹ thuật.

### 5.3.1. Hoàn thiện trong phạm vi hiện tại (1–2 tuần / hạng mục)

Trong phạm vi tính năng đã thiết kế, các hạng mục sau cần được hoàn thiện:

- **FSM đăng ký shipper qua bot** — hiện admin phải insert shipper qua Web Admin; FSM hội thoại nên được mở rộng theo các state AWAITING_NAME → AWAITING_PHONE → AWAITING_VEHICLE → AWAITING_PLATE → PENDING_APPROVAL.
- **Chat trong bot giữa khách và shipper** — khi đơn `ASSIGNED`, shipper bấm "Liên hệ khách" và bot ghép cuộc trò chuyện ẩn danh giữa hai user, audit nội dung mà không lộ số điện thoại.
- **Lưu địa chỉ thường dùng của khách** — bảng `customer_address` cùng autocomplete ở Checkout để giảm thời gian đặt đơn của khách quen.
- **Hiển thị status history trên trang Order Detail (admin)** — dữ liệu đã có trong bảng `status_history` (V4), chỉ cần render timeline.
- **Modal "Gán shipper" trên Web Admin** — bổ sung UI picker filter shipper AVAILABLE thay vì gọi API thuần.

### 5.3.2. Mở rộng tính năng (1–2 tháng / hạng mục)

Các hướng mở rộng tính năng vượt khỏi scope ban đầu:

- **Mở rộng sang Zalo Mini App — ƯU TIÊN CAO.** Zalo có khoảng 75 triệu người dùng hoạt động hằng tháng tại Việt Nam, lớn hơn cả Telegram ở thị trường nội địa. Zalo Mini App SDK (`zmp-sdk`) khác Telegram WebApp nhưng phần UI code có thể tái sử dụng khoảng 80% (React + Tailwind không đổi). Việc cần thay chính là: (i) thay `@twa-dev/sdk` bằng `zmp-sdk`; (ii) đổi xác thực `initData` HMAC sang Zalo OA OAuth; (iii) thay `Telegram.WebApp.openLink` bằng API tương đương của Zalo. Backend chỉ cần thêm `ZaloAuthFilter` song song `TelegramAuthFilter`; phần business logic không thay đổi nhờ Modular Monolith với module `auth` cô lập. Ước lượng 3–4 tuần effort tổng.
- **Tích hợp thêm cổng thanh toán Momo, ZaloPay, ShopeePay, VietQR — ƯU TIÊN CAO.** Pattern integration giống VNPay (sign URL + verify IPN callback) nên mỗi cổng chỉ tốn 3–5 ngày sau khi đã hoàn thiện VNPay. Cần xử lý ba điểm khác biệt: (i) thuật toán ký khác (Momo dùng HMAC-SHA256, ZaloPay có format riêng); (ii) callback URL khác; (iii) refund flow riêng. Phương án: tạo abstraction `PaymentGateway` interface trong module `payment`, mỗi cổng là một implementation. Đặc biệt quan trọng vì khi VNPay sandbox không sẵn credential thật, hệ thống vẫn có thể demo luồng thanh toán online bằng cổng khác.
- **Multi-shop / Multi-tenant** — mở rộng từ "1 shop, 1 admin" thành SaaS cho nhiều shop. Có thể dùng schema-per-tenant hoặc shared-schema với cột `shop_id` làm discriminator.
- **Ứng dụng di động native (iOS / Android)** — qua React Native, tái sử dụng khoảng 70% code từ Mini App. Lợi ích là push notification độc lập khỏi Telegram và sự hiện diện trên các kho ứng dụng.
- **Gợi ý sản phẩm bằng học máy** — gợi ý món dựa trên lịch sử đơn của khách, độ phổ biến và thời điểm trong ngày. Có thể dùng collaborative filtering đơn giản hoặc tích hợp dịch vụ ngoài.
- **Quản lý nhập kho và barcode scanner** — `product.stock` đã có nhưng chưa có quản lý nhập kho. Thêm bảng `stock_movement` và quét barcode trong Web Admin.
- **Đánh giá hai chiều** — bổ sung shipper đánh giá khách hàng (không công khai), giúp cảnh báo khách khó tính với shipper khác.

### 5.3.3. Củng cố cho môi trường sản phẩm (1–2 tháng cho toàn bộ)

Để chuyển từ MVP sang môi trường sản phẩm thực, các hạng mục sau cần hoàn thiện:

- **TLS / HTTPS thực** — chuyển từ self-signed sang Let's Encrypt với Certbot auto-renew.
- **Bot webhook mode** — chuyển từ long polling sang webhook để giảm khoảng 90% network egress.
- **Distributed lock cho `@Scheduled`** — thêm ShedLock với Postgres advisory lock để chỉ một instance chạy `PaymentExpiryScheduler` khi scale ngang.
- **CI/CD pipeline đầy đủ** — GitHub Actions lint → test → build → push image → deploy staging → manual approve → deploy prod.
- **Observability** — Prometheus metrics, Grafana dashboard, Loki log aggregation, OpenTelemetry tracing.
- **Rate limiting tầng Nginx** — `limit_req` cho `/api/payment/vnpay/ipn` (chống flooding) và Bucket4j cho endpoint auth.
- **CORS lock-down** — whitelist origin theo môi trường thay vì wildcard.
- **JWT secret rotation** — quản lý qua KMS (AWS KMS / Google Cloud KMS) thay vì một secret cố định.
- **Backup strategy** — `pg_dump` cron + bucket S3-compatible + retention policy.
- **GDPR và data retention** — tự động xoá `location_ping` quá 30 ngày, anonymize khách hàng khi yêu cầu.
- **Bảng `admin_audit_log`** — ghi lại mọi hành động của admin (compliance).
- **Penetration testing** — thuê đối tác bên thứ ba (Vietsec, Cystack) pen-test trước khi go-live.

### 5.3.4. Khám phá nghiên cứu nâng cao

Mỗi hạng mục dưới đây có thể là một đề tài khoá luận hoặc nghiên cứu riêng:

- **Dự đoán nhu cầu (demand forecasting)** — dự đoán số đơn theo giờ / ngày / khu vực để lên kế hoạch sản xuất và lịch shipper. Có thể dùng Prophet (Meta) hoặc LSTM seq2seq.
- **Định giá động (dynamic pricing)** — phí ship thích nghi theo tỷ lệ cung–cầu (giờ cao điểm tăng nhẹ, ngoài giờ giảm).
- **Phát hiện bất thường (anomaly detection)** — phát hiện đơn bất thường (giá trị quá lớn, địa chỉ shipper từ chối nhiều lần, IPN replay) bằng Isolation Forest hoặc autoencoder.
- **Học tăng cường cho tối ưu lộ trình** — thay heuristic TSP bằng Deep Reinforcement Learning (Pointer Network, Transformer-based RL).
- **Federated learning cho gợi ý** — huấn luyện model trên thiết bị khách (không gửi lịch sử về server) — vừa bảo mật vừa tuân thủ GDPR.
- **Bằng chứng đơn hàng trên blockchain** — hash hoá đơn lên Polygon hoặc Hyperledger để có bằng chứng không thay đổi cho dispute.
- **Privacy-preserving location** — khách chỉ thấy shipper cách N mét chứ không thấy toạ độ chính xác (geo-indistinguishability với differential privacy noise).
- **Framework A/B testing tích hợp** — đo conversion từ bước Checkout bằng Bayesian A/B test.

## 5.4. Kết luận

Đề tài *"Hệ thống quản lý giao hàng tích hợp Telegram Mini App, Web Admin và VNPay"* đã hoàn thành toàn bộ mục tiêu kỹ thuật được đặt ra ở Chương 1: hỗ trợ đặt đơn hai phương thức thanh toán COD và VNPay với HMAC-SHA512, theo dõi vị trí shipper thời gian thực qua Telegram Live Location với độ trễ end-to-end dưới 3 giây, áp dụng máy trạng thái hữu hạn cho bốn thực thể chính, thông báo realtime qua Spring WebSocket STOMP và Telegram Bot, đóng gói đầy đủ bằng Docker Compose chạy được chỉ với ba lệnh. Toàn bộ tám yêu cầu Must và ba yêu cầu Should của phân loại MoSCoW đều đã được hiện thực, kiểm thử qua 221 unit test và integration test với tỷ lệ build xanh 100%, và demo bằng 17 ảnh chụp giao diện kèm seed dữ liệu V11.

Bên cạnh các đóng góp về sản phẩm, đề tài còn minh hoạ một quy trình phát triển phần mềm có kỷ luật theo phương pháp **GSD (Get Shit Done)** với 10 pha (P0 đến P9), mỗi pha trải qua đầy đủ các bước Research → Plan → Plan-check → Execute → Code-review → Fix. Quy trình này sinh ra hơn 152 commit nguyên tử và khoảng 42 000 dòng tài liệu thiết kế cho 21 000 dòng mã — tỉ lệ 2:1 chứng tỏ kỷ luật "thiết kế trước khi viết mã" được tuân thủ nghiêm túc.

Về mặt kiến trúc, đề tài chứng minh rằng kiến trúc **Modular Monolith với DDD-lite** là lựa chọn phù hợp cho các đề tài khoá luận hoặc dự án MVP quy mô tương tự — vừa giữ được ưu điểm "một container, một lệnh deploy" của monolith, vừa đạt được mức độ cô lập trách nhiệm theo nguyên tắc Domain-Driven Design, đồng thời tạo lối thoát chuyển sang microservices trong tương lai mà không phải viết lại nghiệp vụ. Mô hình triển khai lai trên nền tảng Telegram (kết hợp Bot, Mini App, Live Location) là một hướng tiếp cận khả thi và kinh tế cho phân khúc shop F&B và tạp hoá quy mô 1–10 shipper tại Việt Nam.

Tác giả tin rằng các đóng góp kỹ thuật của đề tài — đặc biệt là tích hợp ba kênh Telegram, IPN-as-source-of-truth cho VNPay, mô hình defense-in-depth gồm mười một lớp và quy trình phát triển có kỷ luật theo phương pháp GSD — có giá trị tham khảo cho các nghiên cứu và dự án thực tiễn cùng lĩnh vực. Các hướng phát triển ở mục 5.3 (đặc biệt là mở rộng sang Zalo Mini App và tích hợp Momo / ZaloPay) sẽ tiếp tục được tác giả nghiên cứu trong giai đoạn sau khoá luận, với mục tiêu đưa hệ thống từ prototype hiện tại lên môi trường vận hành thực tế phục vụ các shop kinh doanh nhỏ và vừa tại Việt Nam.

\newpage
