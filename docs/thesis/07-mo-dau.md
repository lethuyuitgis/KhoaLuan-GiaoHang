# MỞ ĐẦU

## 1. Lý do chọn đề tài

Trong vòng một thập kỷ trở lại đây, thương mại điện tử và dịch vụ giao đồ ăn trực tuyến tại Việt Nam đã phát triển bùng nổ với quy mô thị trường giao đồ ăn năm 2024 vượt mốc một tỷ đô la Mỹ. Hai nền tảng tổng hợp lớn là GrabFood và ShopeeFood, kết hợp các cổng thanh toán điện tử như VNPay, MoMo, ZaloPay đã trở thành phương thức quen thuộc của người tiêu dùng đô thị.

Tuy nhiên, đối với nhóm doanh nghiệp nhỏ và vừa — các quán ăn gia đình, cửa hàng tạp hoá khu phố, tiệm bánh hộ kinh doanh — bài toán lại hoàn toàn khác. Họ phải đối mặt với ba lựa chọn không hoàn hảo: (i) tham gia nền tảng tổng hợp với mức hoa hồng 20–25% mỗi đơn và mất quyền sở hữu dữ liệu khách hàng; (ii) tự xây dựng ứng dụng riêng với chi phí lên tới hàng trăm triệu đồng vượt khả năng đầu tư; hoặc (iii) vận hành thủ công qua tin nhắn Zalo, Facebook và điện thoại, dễ phát sinh sai sót và không thể mở rộng.

Trong bối cảnh đó, nền tảng nhắn tin Telegram — với khoảng một tỷ người dùng hoạt động hằng tháng trên toàn cầu — đã trở thành một lựa chọn đầy tiềm năng. Telegram cung cấp ba đặc tính kỹ thuật rất phù hợp với mô hình giao hàng quy mô vừa và nhỏ: *Telegram Bot API* cho phép xây dựng tác tử hội thoại miễn phí; *Telegram Mini App* cho phép nhúng ứng dụng web đầy đủ tính năng bên trong cửa sổ chat với xác thực HMAC-SHA256; và *Telegram Live Location* — tính năng nội tại cho phép chia sẻ vị trí GPS liên tục trong 15 phút đến 8 giờ — tiết kiệm khoảng 80% công sức so với việc tự lập trình một mô-đun GPS streaming riêng.

Xuất phát từ thực tiễn này, đề tài lựa chọn nghiên cứu và xây dựng một hệ thống quản lý giao hàng đầu cuối tích hợp Telegram Mini App, Web Admin và VNPay phục vụ phân khúc shop F&B và tạp hoá quy mô 1–10 shipper, vận hành khoảng 50–500 đơn mỗi ngày.

## 2. Mục tiêu và nhiệm vụ nghiên cứu

Đề tài hướng tới các mục tiêu cụ thể như sau:

- Phân tích đặc điểm của bài toán quản lý giao hàng cuối (last-mile delivery) cho mô hình bán lẻ trực tuyến quy mô nhỏ–vừa và đề xuất giải pháp tận dụng nền tảng Telegram làm cổng vào duy nhất.
- Nghiên cứu các nền tảng công nghệ then chốt: Telegram Bot API và Mini App SDK, Spring Boot 3 với Java 17, React 18 với Vite 5, PostgreSQL 16 với Flyway, tích hợp cổng thanh toán VNPay và mẫu kiến trúc Modular Monolith với Domain-Driven Design.
- Thiết kế và hiện thực hệ thống với ba kênh giao diện: Telegram Mini App (cho khách hàng và shipper), Telegram Bot (cho thao tác hội thoại nhanh) và Web Admin (cho chủ shop).
- Đảm bảo bảy mục tiêu kỹ thuật đo lường được: độ trễ tracking GPS dưới 3 giây, FSM bảy trạng thái cho vòng đời đơn, thanh toán VNPay với HMAC-SHA512, thông báo thời gian thực qua WebSocket STOMP, đóng gói Docker Compose chạy bằng ba lệnh, và mức độ kiểm thử trên 250 test backend.
- Đánh giá kết quả đạt được, các hạn chế còn tồn tại và đề xuất các hướng phát triển trong tương lai.

## 3. Đối tượng và phạm vi nghiên cứu

*Đối tượng nghiên cứu* của đề tài là bài toán quản lý chuỗi cung ứng cuối (last-mile delivery) cho mô hình bán lẻ trực tuyến quy mô nhỏ–vừa, đặc trưng bởi: doanh số khoảng 50–500 đơn mỗi ngày, đội ngũ giao hàng nội bộ 1–10 shipper, bán kính giao hàng dưới 10 km và ba bên liên quan (khách hàng, shipper, chủ shop) với thiết bị và ngữ cảnh sử dụng khác nhau.

*Phạm vi nghiên cứu* được khoanh vùng theo phương pháp MoSCoW: tám tính năng *Must have* và ba tính năng *Should have* được hiện thực và kiểm thử đầy đủ; hai tính năng *Could have* (lưu địa chỉ thường dùng, chat trong bot) và hai tính năng *Won't have* (multi-tenant, siêu quản trị viên) thuộc hướng phát triển hoặc ngoài phạm vi. Hệ thống được triển khai trên một shop duy nhất, một cổng thanh toán điện tử (VNPay sandbox), bản đồ OpenStreetMap qua react-leaflet và không tích hợp đối tác giao vận bên ngoài.

## 4. Phương pháp nghiên cứu

Đề tài kết hợp ba phương pháp:

- *Phương pháp nghiên cứu lý thuyết*: tham khảo các tài liệu chính thức của Telegram, Spring Boot, React, PostgreSQL, VNPay; các sách kinh điển về Domain-Driven Design (Eric Evans), Implementing Domain-Driven Design (Vaughn Vernon), Monolith to Microservices (Sam Newman), Test-Driven Development (Kent Beck).
- *Phương pháp phân tích và thiết kế hệ thống*: áp dụng UML (Use Case Diagram, Sequence Diagram, State Diagram), mẫu kiến trúc Modular Monolith, thiết kế cơ sở dữ liệu chuẩn 3NF và thiết kế bảo mật theo mô hình defense-in-depth với mười một lớp đối phó song song.
- *Phương pháp thực nghiệm và phát triển hướng kiểm thử*: hiện thực hệ thống theo phương pháp GSD (Research → Plan → Plan-check → Execute → Code-review → Fix) với 10 pha P0–P9, viết test trước theo chu kỳ Red–Green–Refactor cho mọi logic nghiệp vụ then chốt, sử dụng Testcontainers để kiểm thử tích hợp với PostgreSQL thật trong Docker.

## 5. Đóng góp của đề tài

Đề tài có những đóng góp chính sau: (1) đề xuất mô hình triển khai lai sử dụng đồng thời ba kênh Telegram (Bot, Mini App, Live Location) cho ba vai trò khác nhau; (2) hiện thực tích hợp VNPay với IPN làm nguồn sự thật và audit JSONB cho mọi giao dịch; (3) áp dụng kiến trúc Modular Monolith với 9 bounded context và giao tiếp qua Spring Application Events; (4) thiết kế bảo mật defense-in-depth gồm mười một lớp đối phó song song; (5) đóng gói toàn bộ hệ thống bằng Docker Compose chạy được chỉ với ba lệnh; và (6) minh hoạ một quy trình phát triển phần mềm có kỷ luật theo phương pháp GSD có thể áp dụng cho khoá luận tốt nghiệp.

## 6. Bố cục báo cáo

Báo cáo được chia thành năm chương:

- **Chương 1. Tổng quan đề tài** — trình bày bối cảnh bài toán, khảo sát và so sánh các giải pháp hiện có, phạm vi theo phương pháp MoSCoW và tám đóng góp kỹ thuật của đề tài.
- **Chương 2. Cơ sở lý thuyết** — trình bày các nền tảng công nghệ then chốt được áp dụng.
- **Chương 3. Phân tích và thiết kế hệ thống** — trình bày phân tích yêu cầu, kiến trúc tổng thể, thiết kế cơ sở dữ liệu, API, máy trạng thái và bảo mật.
- **Chương 4. Cài đặt và kiểm thử** — trình bày các quyết định hiện thực, chiến lược kiểm thử và triển khai bằng Docker Compose.
- **Chương 5. Kết luận và hướng phát triển** — tổng kết kết quả, hạn chế và đề xuất hướng phát triển.

Kèm theo báo cáo là chín phụ lục A–I gồm các tài liệu tra cứu chi tiết được tham chiếu từ các chương chính: đặc tả use case đầy đủ, danh sách endpoint REST, mô tả từng bảng dữ liệu, ma trận chuyển trạng thái, danh mục công nghệ, cấu hình triển khai, thống kê kiểm thử, bộ ảnh giao diện và hai pha mở rộng nghiệp vụ thực hiện sau thời điểm bảo vệ.

\newpage
