# Chương 1. Tổng quan đề tài

## 1.1. Đặt vấn đề

Trong vòng một thập kỷ trở lại đây, thương mại điện tử và dịch vụ giao đồ ăn trực tuyến tại Việt Nam đã phát triển bùng nổ. Theo số liệu công bố của Hiệp hội Thương mại điện tử Việt Nam (VECOM) và Statista, quy mô thị trường giao đồ ăn trực tuyến năm 2024 đã vượt mốc một tỷ đô la Mỹ, với mức tăng trưởng kép hai con số kéo dài liên tục [5]. Hai nền tảng chiếm thị phần chi phối là GrabFood và ShopeeFood; bên cạnh đó là các kênh thanh toán điện tử như MoMo, ZaloPay, VNPay đã trở thành phương thức quen thuộc của người tiêu dùng đô thị.

Tuy nhiên, sự phát triển nói trên chủ yếu phục vụ tệp khách hàng đại trà của các nền tảng tổng hợp (aggregator). Đối với nhóm doanh nghiệp nhỏ và vừa — các quán ăn gia đình, cửa hàng tạp hoá khu phố, tiệm bánh hộ kinh doanh — bài toán lại hoàn toàn khác. Họ phải đối mặt với ba lựa chọn không hoàn hảo:

1. *Tham gia nền tảng tổng hợp:* được tiếp cận lượng người dùng lớn nhưng chịu mức hoa hồng 20–25% mỗi đơn, mất quyền sở hữu dữ liệu khách hàng và không kiểm soát được trải nghiệm thương hiệu.
2. *Tự xây dựng ứng dụng riêng:* chi phí phát triển ứng dụng di động native (iOS và Android) cùng backend, hạ tầng bản đồ và cổng thanh toán có thể lên tới hàng trăm triệu đồng, vượt khả năng đầu tư của shop nhỏ.
3. *Vận hành thủ công:* nhận đơn qua Facebook Messenger, Zalo hay điện thoại; điều phối shipper qua tin nhắn rời rạc; không có hệ thống theo dõi trạng thái đơn hay vị trí giao hàng theo thời gian thực. Phương án này dễ phát sinh nhầm lẫn, thất lạc đơn và không thể mở rộng.

Trong bối cảnh đó, nền tảng nhắn tin Telegram lại nổi lên như một lựa chọn đầy tiềm năng nhưng chưa được khai thác đúng mức tại Việt Nam. Telegram hiện có khoảng một tỷ người dùng hoạt động hằng tháng trên toàn cầu và là một trong các ứng dụng có tốc độ tăng trưởng nhanh nhất tại khu vực Đông Nam Á [25]. Ba đặc tính kỹ thuật của Telegram khiến nền tảng này trở thành môi trường lý tưởng cho hệ thống giao hàng quy mô vừa và nhỏ:

- *Telegram Bot API* cho phép xây dựng tác tử (bot) hội thoại miễn phí, không giới hạn lưu lượng tin nhắn, kèm theo bàn phím nội tuyến (inline keyboard) hỗ trợ thao tác một chạm.
- *Telegram Mini App* (còn gọi là Web App) cho phép nhúng ứng dụng web đầy đủ tính năng bên trong cửa sổ chat Telegram, có cơ chế ký HMAC-SHA256 trên dữ liệu khởi tạo (initData) để xác thực danh tính người dùng mà không cần xây dựng cơ chế đăng nhập riêng.
- *Telegram Live Location* là tính năng nội tại của ứng dụng cho phép người dùng chia sẻ vị trí GPS liên tục trong khoảng từ 15 phút đến 8 giờ, với tần suất cập nhật 5–10 giây. Tính năng này được Telegram ký bằng HMAC trước khi gửi đến webhook của bot, giúp giảm đáng kể chi phí phát triển một mô-đun theo dõi GPS riêng (vốn yêu cầu xin quyền truy cập, foreground service trên Android, tối ưu pin và xử lý mất sóng).

Đề tài này lựa chọn Telegram làm cổng vào duy nhất cho cả ba vai trò (khách hàng, shipper và chủ shop), tận dụng tối đa ba đặc tính trên nhằm cung cấp một giải pháp giao hàng đầu cuối có tính khả thi cao cho phân khúc shop F&B và tạp hoá quy mô 1–10 shipper, vận hành khoảng 50–500 đơn mỗi ngày.

Để định vị rõ phạm vi đóng góp, hệ thống được so sánh với hai nhóm giải pháp phổ biến hiện nay trên thị trường Việt Nam.

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

![Hình 1.1. Telegram Mini App của khách hàng — màn hình danh mục sản phẩm](screenshots/miniapp-cust-01-catalog.png)

![Hình 1.2. Web Admin của chủ shop — bảng điều khiển tổng quan](screenshots/admin-02-dashboard.png)

Phân tích bảng so sánh trên cho thấy hệ thống của đề tài lấp vào khoảng trống định vị giữa hai nhóm: chi phí triển khai thấp tương đương phương án tự xây nhưng đạt được mức độ trải nghiệm tracking gần với các nền tảng tổng hợp, đồng thời cho phép chủ shop tự chủ hoàn toàn về dữ liệu khách hàng — yếu tố mang ý nghĩa chiến lược trong dài hạn.

## 1.2. Mục tiêu đề tài

Mục tiêu tổng quát của đề tài là *xây dựng một hệ thống quản lý giao hàng đầu cuối* phục vụ cho mô hình một shop online quy mô nhỏ–vừa, với ba luồng nghiệp vụ cốt lõi vận hành liền mạch trên ba kênh giao diện khác nhau:

1. *Khách hàng* đặt đơn, theo dõi vị trí shipper và đánh giá dịch vụ thông qua **Telegram Mini App** kết hợp **Telegram Bot**.
2. *Shipper* tiếp nhận đơn, cập nhật trạng thái giao hàng và chia sẻ vị trí thời gian thực qua **Telegram Bot** kết hợp **Telegram Mini App**.
3. *Chủ shop* quản lý đơn, sản phẩm, shipper, theo dõi báo cáo doanh thu và nhận thông báo thời gian thực qua **Web Admin** trên trình duyệt máy tính (kết hợp Telegram Bot cho thông báo đẩy).

Cụ thể, hệ thống hướng tới đạt được năm mục tiêu kỹ thuật được lượng hoá như sau:

- *Mục tiêu 1.* Hỗ trợ đặt đơn trực tuyến với hai phương thức thanh toán: tiền mặt khi nhận hàng (COD) và thanh toán điện tử qua **VNPay sandbox** với cơ chế HMAC-SHA512 và IPN làm nguồn sự thật.
- *Mục tiêu 2.* Theo dõi vị trí shipper thời gian thực trên bản đồ Leaflet, tận dụng Telegram Live Location, với độ trễ end-to-end dưới 3 giây từ lúc Telegram phát phiên bản chỉnh sửa tin nhắn vị trí đến lúc khách hàng thấy chấm di chuyển trên bản đồ.
- *Mục tiêu 3.* Áp dụng máy trạng thái hữu hạn (Finite State Machine) cho vòng đời đơn hàng với bảy trạng thái và danh sách chuyển trạng thái được phép, ngăn ngừa các chuyển dịch trái phép tại tầng nghiệp vụ.
- *Mục tiêu 4.* Triển khai hệ thống thông báo thời gian thực dựa trên Spring WebSocket (STOMP) và Telegram Bot, cho phép chủ shop thấy đơn mới trên dashboard và shipper nhận offer trong vòng vài trăm mili-giây sau khi sự kiện phát sinh.
- *Mục tiêu 5.* Đóng gói toàn bộ hệ thống bằng Docker Compose để có thể triển khai bằng đúng ba lệnh, hỗ trợ cả môi trường phát triển (`dev`) và sản phẩm (`prod`) với chế độ fail-fast cho biến môi trường nhạy cảm.

Ngoài các mục tiêu kỹ thuật trên, đề tài còn hướng đến mục tiêu phương pháp luận: *minh hoạ một quy trình phát triển phần mềm có kỷ luật* — sử dụng phương pháp GSD (Get Shit Done) với các pha nghiên cứu, lập kế hoạch, kiểm tra kế hoạch, thực thi và đánh giá mã — và giữ trạng thái build xanh sau mỗi commit (atomic commit).

## 1.3. Phạm vi đề tài

Phạm vi đề tài được phân loại theo phương pháp **MoSCoW** (Must / Should / Could / Won't have), được trích từ tài liệu thiết kế hệ thống của đề tài. Cách phân loại này giúp định lượng rõ ranh giới giữa phần bắt buộc và phần dự phòng, tránh hiện tượng phình phạm vi (scope creep).

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

Tổng cộng có 11 trên 13 mục tiêu được phân loại Must và Should đã được hiện thực và kiểm thử đầy đủ; hai mục tiêu Could được liệt kê ở chương 5 thuộc hướng phát triển. Hai mục tiêu Won't nằm hoàn toàn ngoài phạm vi và không được khảo sát.

Bên cạnh phân loại MoSCoW, phạm vi của đề tài còn được khoanh vùng theo các giới hạn cụ thể:

- *Một shop duy nhất:* không hỗ trợ nhiều cửa hàng cùng vận hành trên cùng một backend.
- *Ba vai trò:* khách hàng, shipper và chủ shop. Không có vai trò siêu quản trị viên hệ thống.
- *Một cổng thanh toán điện tử:* chỉ VNPay sandbox. Mở rộng sang MoMo / ZaloPay là hướng phát triển tương lai.
- *Không tích hợp đối tác giao vận bên ngoài* (Giao Hàng Nhanh, Giao Hàng Tiết Kiệm, Ahamove...) — toàn bộ shipper là nhân sự nội bộ của shop.
- *Bản đồ và tile:* sử dụng OpenStreetMap với react-leaflet; không sử dụng Google Maps để tránh phụ thuộc API key trả phí.

## 1.4. Đối tượng nghiên cứu

Đối tượng nghiên cứu của đề tài là **bài toán quản lý chuỗi cung ứng cuối (last-mile delivery)** cho mô hình bán lẻ trực tuyến quy mô nhỏ–vừa, đặc trưng bởi:

- *Phân khúc kinh doanh:* các shop F&B (đồ ăn, đồ uống) và tạp hoá có doanh số khoảng 50–500 đơn / ngày, đội ngũ giao hàng nội bộ 1–10 shipper, bán kính giao hàng dưới 10 km tính từ điểm xuất phát.
- *Ba bên liên quan (actor):* khách hàng đầu cuối, shipper và chủ shop. Mỗi bên có thiết bị, ngữ cảnh sử dụng và yêu cầu trải nghiệm khác nhau.

Đặc biệt, đề tài quan tâm đến *bài toán phối hợp đa kênh* — làm thế nào để ba vai trò trên cùng tham gia vào một quy trình nghiệp vụ thống nhất mà không buộc bên nào phải cài đặt thêm phần mềm ngoài những công cụ họ vốn đã sử dụng hằng ngày (Telegram cho khách hàng và shipper, trình duyệt web cho chủ shop). Đây là hướng tiếp cận khác biệt so với các nền tảng tổng hợp vốn yêu cầu ba ứng dụng riêng biệt cho ba vai trò.

Về mặt kỹ thuật, đối tượng nghiên cứu bao gồm:

- *Lập trình tích hợp Telegram đa kênh* — kết hợp Bot, Mini App và Live Location trong một hệ thống duy nhất.
- *Tích hợp cổng thanh toán nội địa* tuân thủ chuẩn an toàn của VNPay (HMAC-SHA512, IPN làm nguồn sự thật, idempotency).
- *Kiến trúc Modular Monolith* với 8 ngữ cảnh (bounded context) tách bạch nhưng vẫn triển khai trong một quy trình (process) duy nhất.
- *Cơ chế truyền thông realtime hai chiều* dùng STOMP trên WebSocket cho Web Admin và Mini App.

## 1.5. Phương pháp tiếp cận

Đề tài được phát triển theo các tiền đề và phương pháp luận sau đây.

*Kiến trúc Modular Monolith với DDD-lite.* Hệ thống được tổ chức thành 8 ngữ cảnh nghiệp vụ (bounded context) — `shared`, `auth`, `order`, `delivery`, `payment`, `bot`, `notification`, `app` — với đồ thị phụ thuộc một chiều (DAG, không có chu trình). Mỗi mô-đun là một Maven submodule riêng biệt; mã của một mô-đun không gọi trực tiếp tầng repository của mô-đun khác mà giao tiếp qua sự kiện ứng dụng (Spring Application Events). Tiếp cận này giữ được ưu điểm dễ triển khai của monolith, đồng thời tạo lối thoát chuyển sang microservices trong tương lai mà không phải viết lại nghiệp vụ [11], [18], [28].

*Phát triển hướng kiểm thử (Test-Driven Development).* Toàn bộ logic nghiệp vụ then chốt — ký và xác minh chữ ký VNPay, chuyển trạng thái máy trạng thái đơn hàng, tính phí ship theo công thức Haversine, xử lý IPN idempotent — đều được viết test trước, viết hiện thực sau, theo chu kỳ Red–Green–Refactor của Kent Beck [7]. Hệ thống có khoảng **221 unit test và integration test** chạy qua Maven Surefire và Failsafe cùng với Testcontainers (chạy PostgreSQL 16 thật trong Docker container thay vì mock cơ sở dữ liệu) [27]. Tỷ lệ build xanh là 100% trên mỗi commit.

*Phương pháp GSD (Get Shit Done) cho quy trình phát triển.* Đề tài tuân thủ chu kỳ 10 pha (P0 đến P9), mỗi pha trải qua các bước Research → Plan → Plan-check → Execute → Code-review → Fix:

- Pha nghiên cứu tạo ra tài liệu `<phase>-research.md` (~6 500 dòng tổng cộng).
- Pha lập kế hoạch tạo ra `<phase>-<topic>.md` (~33 000 dòng tổng cộng, trung bình mỗi pha 1 500–4 700 dòng).
- Tác nhân kiểm tra kế hoạch (plan-checker) phát hiện vấn đề trước khi viết mã (đề tài đã ghi nhận 12 vấn đề chặn được phát hiện ở giai đoạn này).
- Tác nhân thực thi sóng (wave executor) chia kế hoạch thành các nhiệm vụ nguyên tử (atomic task), mỗi task = một commit.
- Tác nhân review mã tạo ra `<phase>-REVIEW.md` phân loại CRITICAL / IMPORTANT / MINOR; bốn lỗi nghiêm trọng đã được phát hiện ở khâu này (gồm khe hở audit IPN, rò rỉ qua kênh broadcast STOMP, lệch enum trạng thái shipper và lộ trường nhạy cảm trong DTO).
- Tổng cộng có hơn **152 commit nguyên tử** với thông điệp theo chuẩn Conventional Commits.

*Triển khai liên tục bằng Docker Compose.* Toàn bộ stack được containerized với Dockerfile đa giai đoạn (multi-stage) — pha build dùng Maven và JDK 17, pha runtime dùng JRE slim trên user không phải root. Một lệnh `docker compose up -d` đủ để khởi động đầy đủ năm dịch vụ (PostgreSQL, backend Spring Boot, Mini App SPA, Web Admin SPA, Nginx reverse proxy).

## 1.6. Bố cục báo cáo

Báo cáo được chia thành năm chương như sau:

- **Chương 1. Tổng quan đề tài** (chương hiện tại). Trình bày bối cảnh, mục tiêu, phạm vi, đối tượng nghiên cứu và phương pháp tiếp cận của đề tài.
- **Chương 2. Cơ sở lý thuyết.** Trình bày các nền tảng công nghệ then chốt được áp dụng — Telegram Platform, Spring Boot 3 và Java 17, React 18 và Vite 5, PostgreSQL 16 với Flyway, tích hợp VNPay sandbox, Modular Monolith với DDD-lite, Test-Driven Development, và đóng gói bằng Docker Compose.
- **Chương 3. Phân tích và thiết kế hệ thống.** Trình bày phân tích yêu cầu (chức năng và phi chức năng), kiến trúc tổng thể, thiết kế cơ sở dữ liệu, thiết kế giao diện lập trình ứng dụng (API), thiết kế máy trạng thái cho bốn thực thể chính, luồng nghiệp vụ chi tiết và thiết kế bảo mật theo mô hình defense-in-depth.
- **Chương 4. Cài đặt và kiểm thử.** Trình bày các quyết định hiện thực then chốt theo mô-đun, chiến lược kiểm thử, kết quả thực thi test, và đánh giá hiệu năng. (Ngoài phạm vi tài liệu này.)
- **Chương 5. Kết luận và hướng phát triển.** Tổng kết các kết quả đạt được, các hạn chế đã nhận diện trung thực, và đề xuất các hướng phát triển tiếp theo theo bốn nhóm: hoàn thiện trong phạm vi, mở rộng tính năng, củng cố cho môi trường sản phẩm, và các hướng nghiên cứu nâng cao. (Ngoài phạm vi tài liệu này.)

## 1.7. Đóng góp của đề tài

Phần này tổng hợp chín đóng góp kỹ thuật chính của đề tài, được trình bày dưới dạng chín tiểu mục độc lập. Các đóng góp này vừa thể hiện chiều sâu kỹ thuật vừa minh hoạ tính khả thi sản xuất (production-ready) của hệ thống.

### 1.7.1. Mô hình triển khai lai trên nền tảng Telegram

Đề tài đề xuất một mô hình triển khai *lai (hybrid)* sử dụng đồng thời ba kênh của Telegram nhằm tận dụng thế mạnh của từng kênh cho từng vai trò người dùng. Cụ thể, **Telegram Mini App** (xây bằng React 18 và Vite 5) được dùng cho khách hàng để duyệt sản phẩm, đặt đơn và theo dõi bản đồ; **Telegram Bot** (xây trên `telegrambots-springboot-longpolling-starter` 7.x) được dùng cho shipper để nhận offer, thao tác một chạm qua inline keyboard, và cho khách hàng để đánh giá sau khi nhận hàng; còn **Web Admin** (xây trên React + Tailwind) được dành riêng cho chủ shop làm việc trên máy tính. Cách phân vai theo thiết bị này khác với các nền tảng giao đồ ăn thương mại vốn ép tất cả vai trò vào một ứng dụng di động duy nhất; nhờ đó trải nghiệm của từng vai trò được tối ưu thay vì phải đánh đổi.

### 1.7.2. Theo dõi vị trí thời gian thực bằng Telegram Live Location

Tính năng theo dõi shipper trên bản đồ — vốn là hạng mục tốn kém nhất khi xây ứng dụng giao hàng — được hiện thực **không cần lập trình streaming GPS phía client**. Hệ thống tận dụng tính năng *Live Location* gốc của Telegram: shipper bấm vào biểu tượng đính kèm → "Vị trí" → "Chia sẻ vị trí trực tiếp" và chọn thời lượng (15 phút / 1 giờ / 8 giờ). Telegram tự động gửi `message.location` ban đầu, sau đó `edited_message.location` mỗi 5–10 giây. Phía backend, `LiveLocationHandler` được wire vào webhook bot, lưu `location_ping` và phát `LocationPingReceivedEvent`. Một `LocationBroadcaster` lắng nghe sự kiện với `@TransactionalEventListener(AFTER_COMMIT)` và đẩy qua STOMP đến khách hàng. Lợi ích: tiết kiệm khoảng 80% công sức so với tự lập trình GPS streaming, bảo mật cao vì vị trí đã được Telegram ký HMAC, và tiết kiệm pin cho shipper.

### 1.7.3. Kiến trúc Modular Monolith với 8 ngữ cảnh nghiệp vụ

Hệ thống áp dụng *Modular Monolith* thay vì Microservices — một quyết định có chủ đích phù hợp với phạm vi và quy mô của đề tài. Tám ngữ cảnh nghiệp vụ — `shared`, `auth`, `order`, `delivery`, `payment`, `bot`, `notification`, `app` — được tổ chức theo đồ thị phụ thuộc một chiều (DAG, không có chu trình). Các mô-đun giao tiếp qua *Spring Application Events* với `@TransactionalEventListener(AFTER_COMMIT)`, đảm bảo (i) module phát sự kiện không cần biết về internal của module nhận, (ii) sự kiện chỉ fire sau khi transaction commit thành công và (iii) có thể tách thành microservices trong tương lai mà gần như không sửa code nghiệp vụ. Cách tổ chức này giữ được ưu điểm "1 container, 1 lệnh deploy" của monolith mà vẫn đạt được mức độ cô lập trách nhiệm theo nguyên tắc DDD.

### 1.7.4. Tích hợp VNPay sandbox với IPN làm nguồn sự thật

Hệ thống áp dụng đúng mẫu *IPN-as-source-of-truth* mà VNPay khuyến nghị thay vì cách làm sai phổ biến là tin tưởng Return URL. Cụ thể: `POST /api/payment/vnpay/create` (xác thực bằng Telegram initData) tạo `Payment(PENDING)` và ký URL HMAC-SHA512; Mini App mở URL bằng `WebApp.openLink` (không dùng `window.location.href` — đây là pitfall hay gặp); `GET /api/payment/vnpay/return` xác minh chữ ký và **chỉ render trang HTML**, không cập nhật cơ sở dữ liệu; còn `POST /api/payment/vnpay/ipn` (server-to-server) xác minh chữ ký bằng `MessageDigest.isEqual` (constant time, chống timing attack), kiểm tra số tiền, đảm bảo idempotent và cập nhật trạng thái. Mọi sự kiện IPN — kể cả invalid signature — được lưu vào `payment_transaction.raw_payload` (JSONB) để phục vụ forensics. Một `PaymentExpiryScheduler` chạy cron 60 giây để đánh dấu các thanh toán PENDING quá 15 phút thành FAILED, giải phóng đơn cho khách đặt lại.

### 1.7.5. Máy trạng thái hữu hạn (FSM) cho hội thoại đánh giá

Tính năng đánh giá shipper 1–5 sao được hiện thực bằng máy trạng thái hữu hạn nhỏ cho luồng nhập comment tuỳ chọn: sau khi đơn `DELIVERED`, bot gửi inline keyboard 5 sao; khi khách bấm sao, callback `RATE:<orderId>:<stars>` được xử lý bởi `RatingService.rate`, dữ liệu được lưu vào bảng `rating` (V10) và `shipper_profile.rating_avg` được tính lại từ aggregate query để tránh trôi số (floating-point drift) trên `NUMERIC(3,2)`. Sau đó bot prompt nhập comment và `ConversationStateService` chuyển user sang state `CUSTOMER_RATING_COMMENT`, lưu payload JSONB vào bảng `conversation_state` (V3) qua `@JdbcTypeCode(SqlTypes.JSON)` của Hibernate 6. Khi khách gõ text hoặc `/skip`, FSM tự clear. Đây cũng là cơ sở hạ tầng tái sử dụng được cho các flow FSM khác.

### 1.7.6. Bảo mật defense-in-depth qua 11 lớp song song

Hệ thống áp dụng nhiều lớp phòng thủ song song thay vì dựa duy nhất vào một cơ chế. Mười một lớp bao gồm: (L1) `TelegramAuthFilter` xác minh HMAC initData chống spoof; (L2) `JwtAuthFilter` với TTL 15 phút chống session hijack; (L3) allowlist filter chain trong `SecurityConfig`; (L4) `@PreAuthorize` mức controller chống privilege escalation; (L5) custom `@CurrentUser` resolver chống forge customerId trong URL path; (L6) WebSocket CONNECT interceptor (dual auth) chống anonymous connect; (L7) WebSocket SUBSCRIBE allowlist chống cross-user data leak; (L8) VNPay HMAC `MessageDigest.isEqual` chống timing attack; (L9) groupBy enum whitelist trước `DATE_TRUNC` chống SQL injection; (L10) `vnp_TxnRef UNIQUE` và bảng audit `payment_transaction` chống replay; (L11) partial unique index `uq_assignment_shipper_started` (V8) chống quy gán nhầm vị trí cho shipper khác. Mỗi lớp đều có ít nhất một regression test khoá đảm bảo.

### 1.7.7. Quy trình phát triển có kỷ luật theo phương pháp GSD

Toàn bộ 10 pha (P0 đến P9) được phát triển theo phương pháp **GSD (Get Shit Done)** — Research → Plan → Plan-check → Execute → Code-review → Fix — với artefact đầy đủ ở từng pha. Tổng tài liệu sinh ra gồm: 10 plan file (~33 000 dòng), 8 research doc (~6 500 dòng), 9 code review report, và hơn 152 commit nguyên tử. Phương pháp này giúp phát hiện lỗi sớm: 12 vấn đề chặn được phát hiện ở khâu plan-checker (trước khi viết mã), 4 bug nghiêm trọng được catch ở khâu code-review (khe hở audit IPN, rò rỉ STOMP SUBSCRIBE, lệch enum ShipperState, rò rỉ DTO). Đây là một đóng góp về mặt *quy trình* bên cạnh các đóng góp về *sản phẩm*, cho thấy rằng phương pháp GSD có thể được áp dụng cho khoá luận tốt nghiệp như một thay thế đủ kỷ luật so với mô hình Waterfall truyền thống.

### 1.7.8. Sẵn sàng triển khai (deployment-ready) chỉ bằng ba lệnh

Hệ thống đã được containerized hoàn chỉnh từ pha P9. Ba lệnh `git clone`, `cp .env.example .env` (điền 4 secret) và `docker compose up -d` đủ để dựng toàn bộ stack trong khoảng 3 phút (lần đầu build) hoặc 30 giây (cache). Năm container gồm: `postgres:16-alpine` (data volume + healthcheck), `backend` (Spring Boot multi-stage Maven build, JRE 17 slim, non-root user, healthcheck `/actuator/health`), `miniapp` và `webadmin` (Vite build phục vụ qua nginx alpine, mỗi image ~10 MB), và `nginx` reverse proxy. Cấu hình `application-prod.yml` áp dụng *fail-fast* cho secret — thiếu biến môi trường thì backend không khởi động được, tránh trường hợp deploy production với credential test. Hệ thống còn có demo seed V11 (1 admin, 10 sản phẩm, 6 telegram user, 30 đơn, 18 assignment, 10 rating, 11 payment) để reviewer thấy dashboard có dữ liệu ngay sau khi up.

### 1.7.9. Các chỉ số định lượng đáng chú ý

Để định lượng quy mô của đề tài, bảng dưới đây tổng hợp các con số then chốt rút ra từ kho mã nguồn và tài liệu thiết kế.

**Bảng 1.3. Các chỉ số định lượng đáng chú ý của đề tài**

| Chỉ số | Giá trị |
|---|---|
| Tổng số commit nguyên tử | hơn 152 |
| Tổng số dòng mã (Java + TypeScript) | ~21 000 |
| Tổng số dòng plan + research + review | ~42 000 |
| Số test backend (unit + integration) | 221 |
| Tỷ lệ build xanh trên mỗi commit | 100% |
| Số Flyway migration | 11 (V1 đến V11) |
| Số ngữ cảnh nghiệp vụ (bounded context) | 8 |
| Số endpoint REST | ~35 |
| Số topic WebSocket | 2 (`/user/queue/order/*/location`, `/topic/admin/orders`) |
| Số sự kiện cross-module | 9 |
| Số trang Mini App | 9 (3 cho khách, 2 cho shipper, 4 chung) |
| Số trang Web Admin | 8 |
| Số Dockerfile | 3 (backend, miniapp, webadmin) |
| Số container trong compose | 5 |

![Hình 1.3. Bảng điều khiển Web Admin với đầy đủ dữ liệu demo seed V11](screenshots/admin-04-reports.png)

![Hình 1.4. Mini App của khách hàng với giỏ hàng đã chọn — minh hoạ trải nghiệm đặt đơn liền mạch](screenshots/miniapp-cust-05-cart-filled.png)

Các con số trên không chỉ minh hoạ khối lượng công việc mà còn cho thấy *mật độ kiểm thử* (221 test cho ~21 000 dòng mã, tương đương xấp xỉ 1 test cho mỗi 100 dòng — vượt mức trung bình của các dự án mã nguồn mở tương đương) và *mật độ tài liệu thiết kế* (~42 000 dòng tài liệu thiết kế cho ~21 000 dòng mã, tỉ lệ 2:1 — chứng tỏ kỷ luật "thiết kế trước khi viết mã" được tuân thủ nghiêm túc).

## 1.8. Kết luận chương

Chương 1 đã trình bày bối cảnh của bài toán giao hàng quy mô nhỏ–vừa, lý do lựa chọn Telegram làm cổng vào duy nhất, mục tiêu, phạm vi MoSCoW, đối tượng nghiên cứu và phương pháp tiếp cận của đề tài, đồng thời tổng hợp chín đóng góp kỹ thuật chính. Các đóng góp này tạo nền tảng cho phần thiết kế chi tiết được trình bày ở các chương sau. Chương 2 tiếp theo sẽ làm rõ các nền tảng công nghệ then chốt mà đề tài dựa vào để hiện thực các đóng góp đã liệt kê.
