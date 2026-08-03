<!--
  ĐỀ CƯƠNG KHÓA LUẬN TỐT NGHIỆP
  Soạn theo cấu trúc của đề cương mẫu Trường ĐH Mở Hà Nội (7 mục):
    1. Tóm tắt đề cương
    2. Giới thiệu đề tài (lý do chọn, phát biểu bài toán, mục tiêu & phạm vi)
    3. Các đề tài liên quan
    4. Nội dung dự kiến đạt được (nhóm chức năng, công nghệ, khắc phục hạn chế)
    5. Kế hoạch thực hiện
    6. Phân công công việc
    7. Tài liệu tham khảo

  Thông tin GVHD / MSSV / Lớp đã điền đầy đủ (không còn placeholder).
  Có thể biên dịch sang .docx/.pdf bằng Pandoc giống các file khác trong thư mục này.
-->

::: {custom-style="TrangBia"}

**TRƯỜNG ĐẠI HỌC MỞ HÀ NỘI**

**TRUNG TÂM ĐÀO TẠO E-LEARNING**

\

\

**ĐỀ CƯƠNG KHÓA LUẬN TỐT NGHIỆP**

\

# ĐỀ TÀI: XÂY DỰNG HỆ THỐNG QUẢN LÝ GIAO HÀNG TÍCH HỢP TELEGRAM MINI APP, WEB ADMIN VÀ VNPAY

\

\

**Giảng viên hướng dẫn:** ThS. Đinh Tuấn Long

**Sinh viên thực hiện:**

1. Lê Thị Trần Thủy (Nhóm trưởng) — MSV: 23C1001P3734
2. Ngô Phúc Hiếu — MSV: 23C1001P5290
3. Nguyễn Thế Thưởng — MSV: 23C1001P4046

**Lớp:** CHDN420

**Ngành đào tạo:** Công nghệ thông tin

**Chuyên ngành:** Công nghệ phần mềm

**Môn học:** Khóa Luận Tốt Nghiệp

\

\

**Hà Nội, năm 2026**

:::

\newpage

# MỤC LỤC

1. Tóm tắt đề cương
2. Giới thiệu đề tài
   - 2.1. Lý do lựa chọn đề tài
   - 2.2. Phát biểu bài toán
   - 2.3. Mục tiêu và phạm vi hệ thống
3. Các đề tài liên quan
4. Nội dung dự kiến đạt được
   - 4.1. Hệ thống dự kiến bao gồm các nhóm chức năng
   - 4.2. Công nghệ sử dụng
   - 4.3. Khắc phục các vấn đề hạn chế của đề tài liên quan
5. Kế hoạch thực hiện
6. Phân công công việc
7. Tài liệu tham khảo

\newpage

# 1. Tóm tắt đề cương

- **Tên đề tài:** "Xây dựng hệ thống quản lý giao hàng tích hợp Telegram Mini App, Web Admin và VNPay"
- **Ngành:** Công nghệ thông tin
- **Chuyên ngành:** Công nghệ phần mềm
- **Công nghệ chính:** Java 17, Spring Boot 3.4, React 18, PostgreSQL 16, Telegram Bot/Mini App, VNPay sandbox, Docker Compose

Đề tài xây dựng một hệ thống quản lý giao hàng đầu cuối (end-to-end) cho mô hình shop bán lẻ trực tuyến quy mô nhỏ và vừa (50–500 đơn/ngày, 1–10 shipper nội bộ). Hệ thống lấy **Telegram** làm cổng vào duy nhất cho khách hàng và shipper, kết hợp **Web Admin** trên trình duyệt cho chủ shop. Toàn bộ chuỗi nghiệp vụ — khách đặt đơn trên Telegram Mini App → chủ shop gán đơn cho shipper → shipper chia sẻ vị trí trực tiếp (Live Location) → khách theo dõi bản đồ thời gian thực → thanh toán qua VNPay → khách đánh giá shipper — vận hành liền mạch và được triển khai một-lệnh qua Docker Compose.

Đề cương bao gồm các nội dung chính:

- Tóm tắt đề cương
- Giới thiệu đề tài
- Các đề tài liên quan
- Nội dung dự kiến đạt được
- Kế hoạch thực hiện
- Phân công nhiệm vụ
- Tài liệu tham khảo

# 2. Giới thiệu đề tài

## 2.1. Lý do lựa chọn đề tài

- **Tính cấp thiết:**
  + Thị trường giao đồ ăn và bán lẻ trực tuyến tại Việt Nam tăng trưởng mạnh, vượt mốc một tỷ USD năm 2024 [5], kéo theo nhu cầu giao hàng chặng cuối (last-mile) ngày càng lớn.
  + Shop nhỏ và vừa hoặc phải chịu hoa hồng 20–25% trên các nền tảng tổng hợp (GrabFood, ShopeeFood), hoặc vận hành thủ công qua Messenger/Zalo — dễ nhầm lẫn, thất lạc đơn, không có theo dõi trạng thái và vị trí giao hàng.
  + Cần một hệ thống chi phí thấp, tự chủ dữ liệu, tự động hóa quy trình điều phối và giao hàng.
- **Tính thực tiễn:**
  + Ứng dụng trực tiếp cho các shop F&B, tạp hoá có đội shipper nội bộ, bán kính giao hàng dưới 10 km.
  + Khách hàng và shipper không phải cài thêm ứng dụng — chỉ dùng Telegram sẵn có; chủ shop quản lý trên trình duyệt máy tính.
  + Chi phí vận hành thấp (~10 USD/tháng cho một VPS phổ thông) so với 50–100 USD/tháng khi tự xây ứng dụng riêng.
- **Tính khoa học:**
  + Áp dụng kiến trúc **Modular Monolith** theo nguyên tắc Domain-Driven Design (DDD-lite) với 8 ngữ cảnh nghiệp vụ tách bạch [11], [18], [28].
  + Sử dụng cơ sở dữ liệu quan hệ PostgreSQL 16 với quản lý phiên bản schema bằng Flyway [23].
  + Áp dụng **máy trạng thái hữu hạn (FSM)** cho vòng đời đơn hàng và phát triển hướng kiểm thử (TDD) cho logic nghiệp vụ then chốt [7].
- **Tính sáng tạo:**
  + Mô hình triển khai **lai (hybrid)** dùng đồng thời ba kênh Telegram: Mini App cho khách, Bot cho shipper, Web Admin cho chủ shop — tối ưu trải nghiệm theo từng thiết bị.
  + Theo dõi vị trí thời gian thực tận dụng **Telegram Live Location** (đã được Telegram ký HMAC), tiết kiệm khoảng 80% công sức so với tự lập trình GPS streaming.
  + Thanh toán điện tử áp dụng đúng mẫu **IPN làm nguồn sự thật** của VNPay (HMAC-SHA512, chống timing attack, idempotent).

## 2.2. Phát biểu bài toán

- **Bài toán:** Quản lý hiệu quả và an toàn toàn bộ chuỗi giao hàng chặng cuối cho một shop online — từ đặt đơn, điều phối shipper, theo dõi vị trí, thanh toán đến đánh giá dịch vụ — trên ba vai trò người dùng (khách hàng, shipper, chủ shop) mà không buộc các bên cài thêm phần mềm ngoài công cụ họ đã dùng hằng ngày.
- **Yêu cầu cụ thể:**
  + Quản lý sản phẩm (thêm, sửa, xóa, tìm kiếm) và đơn hàng.
  + Khách đặt đơn, chọn phương thức thanh toán (COD hoặc VNPay), theo dõi đơn và đánh giá shipper qua Telegram Mini App + Bot.
  + Chủ shop xem, gán đơn cho shipper, theo dõi trạng thái và nhận thông báo thời gian thực qua Web Admin.
  + Shipper nhận/từ chối đơn, cập nhật trạng thái giao hàng và chia sẻ vị trí trực tiếp qua Telegram Bot + Mini App.
  + Cập nhật trạng thái đơn theo máy trạng thái hữu hạn (7 trạng thái) ngăn chuyển dịch trái phép.
  + Thanh toán điện tử VNPay sandbox với IPN làm nguồn sự thật.
  + Báo cáo và thống kê doanh thu cho chủ shop.

## 2.3. Mục tiêu và phạm vi hệ thống

- **Mục tiêu:**
  + Xây dựng hệ thống quản lý giao hàng đầu cuối hoàn chỉnh, vận hành liền mạch trên ba kênh giao diện.
  + Hỗ trợ đặt đơn trực tuyến với hai phương thức thanh toán (COD và VNPay sandbox).
  + Theo dõi vị trí shipper thời gian thực trên bản đồ với độ trễ end-to-end dưới 3 giây.
  + Thông báo thời gian thực qua Spring WebSocket (STOMP) và Telegram Bot.
  + Đóng gói toàn hệ thống bằng Docker Compose, triển khai bằng đúng ba lệnh.
- **Phạm vi (phân loại theo phương pháp MoSCoW):**
  + **Must have:** đặt đơn qua Mini App; gán đơn qua Web Admin; nhận/từ chối đơn qua Bot; máy trạng thái đơn hàng; Live Location; thông báo realtime; lịch sử đơn; thanh toán VNPay.
  + **Should have:** tính cước theo khoảng cách (công thức Haversine); đánh giá shipper 1–5 sao; báo cáo và biểu đồ.
  + **Won't have (ngoài phạm vi):** đa shop/đa người thuê (multi-tenant); siêu quản trị nhiều shop; tích hợp đối tác giao vận bên ngoài (GHN, GHTK, Ahamove).
  + **Giới hạn khác:** một shop duy nhất; ba vai trò; một cổng thanh toán điện tử (VNPay); bản đồ dùng OpenStreetMap với react-leaflet (không dùng Google Maps trả phí).

# 3. Các đề tài liên quan

- **Tên đề tài tham khảo:** "Phần mềm/ứng dụng quản lý giao hàng cho shop online" và các nền tảng giao đồ ăn tổng hợp (GrabFood, ShopeeFood).
- **Ưu điểm:**
  + Nền tảng tổng hợp có lượng người dùng lớn, theo dõi GPS thời gian thực và cổng thanh toán đầy đủ.
  + Giải pháp shop tự xây cho phép tự chủ dữ liệu và quy trình.
- **Hạn chế:**
  + Nền tảng tổng hợp thu hoa hồng cao (20–25%), shop mất quyền sở hữu dữ liệu khách và không kiểm soát trải nghiệm thương hiệu; khách buộc phải cài ứng dụng riêng (~150 MB).
  + Giải pháp tự xây ứng dụng native (iOS + Android) chi phí phát triển và vận hành cao (50–100 USD/tháng), vượt khả năng đầu tư của shop nhỏ.
  + Vận hành thủ công qua Messenger/Zalo không có theo dõi trạng thái đơn, không có vị trí giao hàng thời gian thực, dễ thất lạc đơn và không mở rộng được.

# 4. Nội dung dự kiến đạt được

## 4.1. Hệ thống dự kiến bao gồm các nhóm chức năng

- **Quản lý người dùng và phân quyền:**
  + Xác thực chủ shop bằng JWT trên Web Admin.
  + Xác thực khách hàng và shipper bằng Telegram initData (HMAC-SHA256).
  + Đăng ký shipper qua Bot và quy trình duyệt shipper của chủ shop.
- **Quản lý sản phẩm và đơn hàng:**
  + CRUD sản phẩm trên Web Admin.
  + Khách duyệt danh mục, thêm giỏ hàng, checkout (COD/VNPay) trên Mini App.
  + Vòng đời đơn theo máy trạng thái hữu hạn 7 trạng thái.
- **Quản lý giao hàng và theo dõi vị trí:**
  + Gán đơn cho shipper; shipper nhận/từ chối qua inline keyboard.
  + Chia sẻ Telegram Live Location khi đang giao.
  + Bản đồ tracking thời gian thực trên Mini App (react-leaflet + OpenStreetMap).
  + Tính cước theo khoảng cách (công thức Haversine).
- **Thanh toán:**
  + Tích hợp VNPay sandbox với IPN làm nguồn sự thật, idempotent, chống replay và timing attack.
  + Hết hạn thanh toán PENDING sau 15 phút (cron scheduler).
- **Thông báo và đánh giá:**
  + Thông báo realtime qua WebSocket (STOMP) tới Web Admin và Mini App, qua Bot tới shipper/khách.
  + Đánh giá shipper 1–5 sao kèm bình luận tùy chọn (FSM hội thoại).
- **Báo cáo thống kê:**
  + Dashboard KPI và biểu đồ doanh thu (Recharts) cho chủ shop.
  + Thống kê số lượng đơn theo trạng thái và theo thời gian.

## 4.2. Công nghệ sử dụng

- **Ngôn ngữ lập trình:** Java 17, TypeScript 5.6.
- **Backend:** Spring Boot 3.4, Spring Security, Spring Data JPA, Spring WebSocket (STOMP/SockJS), Flyway, telegrambots-springboot-longpolling-starter 7.x [29], [30].
- **Frontend:** React 18, Vite 5, TanStack Query 5, Tailwind CSS 3, Recharts 3, Zustand 4, @twa-dev/sdk 7, react-leaflet [20].
- **Cơ sở dữ liệu:** PostgreSQL 16, quản lý migration bằng Flyway [23].
- **Thanh toán:** VNPay sandbox (HMAC-SHA512, IPN server-to-server) [6].
- **Realtime:** STOMP over WebSocket, SockJS fallback.
- **Công cụ phát triển:** Visual Studio Code / IntelliJ IDEA, Maven, pnpm workspace.
- **Kiểm thử:** JUnit 5, Testcontainers (PostgreSQL 16 thật), Mockito, AssertJ [27].
- **Triển khai:** Docker Compose (nginx + 4 dịch vụ), Dockerfile đa giai đoạn (multi-stage) [10].
- **Kiến trúc:** Modular Monolith (DDD-lite) với 8 ngữ cảnh nghiệp vụ, giao tiếp qua Spring Application Events [11], [18], [28].

## 4.3. Khắc phục các vấn đề hạn chế của đề tài liên quan

- **Chi phí thấp, tự chủ dữ liệu:** không chịu hoa hồng nền tảng, chủ shop sở hữu toàn bộ dữ liệu khách; chi phí ~10 USD/tháng.
- **Không yêu cầu cài ứng dụng mới:** khách và shipper dùng Telegram sẵn có; chủ shop dùng trình duyệt.
- **Theo dõi GPS thời gian thực giá rẻ:** tận dụng Telegram Live Location thay vì tự lập trình GPS streaming.
- **Thanh toán điện tử an toàn:** áp dụng đúng mẫu IPN-as-source-of-truth của VNPay, chống các lỗi phổ biến (tin tưởng Return URL, timing attack, replay).
- **Bảo mật nhiều lớp (defense-in-depth):** xác thực HMAC initData, JWT, phân quyền `@PreAuthorize`, allowlist WebSocket SUBSCRIBE, audit giao dịch thanh toán [21], [22].
- **Kiến trúc dễ mở rộng:** Modular Monolith tạo lối thoát chuyển sang microservices mà không phải viết lại nghiệp vụ.

# 5. Kế hoạch thực hiện

| Thời gian | Công việc | Mô tả chi tiết |
|---|---|---|
| Tuần 1–2 | Phân tích yêu cầu | Khảo sát hiện trạng giao hàng shop nhỏ; phân tích yêu cầu 3 vai trò; xác định phạm vi MoSCoW |
| Tuần 3–4 | Thiết kế hệ thống | Thiết kế kiến trúc Modular Monolith 8 ngữ cảnh; thiết kế CSDL PostgreSQL + Flyway; thiết kế API và máy trạng thái đơn hàng; thiết kế giao diện 3 kênh |
| Tuần 5–6 | Nền tảng + xác thực | Thiết lập Maven/pnpm workspace; xác thực JWT (Web Admin) và Telegram initData (Mini App); CRUD sản phẩm và đơn hàng |
| Tuần 7 | Mini App khách + Bot | Mini App duyệt món, giỏ hàng, checkout COD; lệnh Bot `/start`, đăng ký và duyệt shipper |
| Tuần 8–9 | Giao hàng + theo dõi vị trí | Gán đơn, shipper nhận/từ chối; Telegram Live Location; bản đồ tracking realtime qua WebSocket |
| Tuần 10 | Thanh toán + báo cáo | Tích hợp VNPay sandbox (IPN-as-source-of-truth); dashboard KPI và biểu đồ; đánh giá shipper |
| Tuần 11 | Kiểm thử và sửa lỗi | Viết unit/integration test (JUnit 5 + Testcontainers); kiểm thử tích hợp end-to-end; sửa lỗi |
| Tuần 12 | Đóng gói và hoàn thiện | Đóng gói Docker Compose + seed dữ liệu demo; viết tài liệu; chuẩn bị báo cáo và bảo vệ khóa luận |

# 6. Phân công công việc

Đề tài do nhóm 3 sinh viên thực hiện, phân công cân bằng khối lượng. Nhóm trưởng Lê Thị Trần Thủy tập trung toàn bộ vào lập trình phần lõi (kiến trúc, backend và các tích hợp Telegram/VNPay); phần frontend, kiểm thử, triển khai, bảo mật và viết báo cáo được chia đều cho hai thành viên còn lại.

| Thành viên | Công việc chính |
|---|---|
| **Lê Thị Trần Thủy** (Nhóm trưởng) | - Thiết kế kiến trúc Modular Monolith và CSDL PostgreSQL + Flyway<br>- Lập trình Backend & Business Layer (Spring Boot, máy trạng thái đơn hàng)<br>- Tích hợp Telegram (Bot, Mini App, Live Location) và cổng thanh toán VNPay |
| Ngô Phúc Hiếu | - Lập trình Frontend Web Admin (React + Tailwind): quản lý sản phẩm, đơn hàng (CRUD), Dashboard KPI và biểu đồ (Recharts)<br>- Kiểm thử (TDD) và đóng gói Docker Compose<br>- Bảo mật defense-in-depth |
| Nguyễn Thế Thưởng | - Lập trình Frontend Telegram Mini App (khách + shipper): giỏ hàng, checkout, bản đồ tracking (react-leaflet)<br>- Thiết kế giao diện và chuẩn bị presentation<br>- Tổng hợp, viết tài liệu và báo cáo khóa luận |

# 7. Tài liệu tham khảo

[5] Hiệp hội Thương mại điện tử Việt Nam — VECOM (2024), *Báo cáo chỉ số Thương mại điện tử Việt Nam 2024*, Hà Nội.

[6] Công ty Cổ phần Giải pháp Thanh toán Việt Nam — VNPAY (2025), *Tài liệu kỹ thuật tích hợp cổng thanh toán VNPay 2.1.0*, Hà Nội.

[7] Beck K. (2002), *Test-Driven Development: By Example*, Addison-Wesley Professional, Boston, MA.

[10] Docker Inc. (2025), *Docker Documentation — Compose specification*, <https://docs.docker.com/compose/>.

[11] Evans E. (2003), *Domain-Driven Design: Tackling Complexity in the Heart of Software*, Addison-Wesley, Boston, MA.

[18] Newman S. (2019), *Monolith to Microservices*, O'Reilly Media, Sebastopol, CA.

[20] OpenJS Foundation (2024), *React Documentation*, <https://react.dev/>.

[21] OWASP Foundation (2021), *OWASP Top 10: 2021*, <https://owasp.org/Top10/>.

[22] OWASP Foundation (2024), *OWASP Application Security Verification Standard (ASVS) 4.0.3*.

[23] PostgreSQL Global Development Group (2025), *PostgreSQL 16 Documentation*, <https://www.postgresql.org/docs/16/>.

[25] Telegram FZ-LLC (2025), *Telegram Bot API Documentation*, <https://core.telegram.org/bots/api>.

[26] Telegram FZ-LLC (2025), *Telegram Mini Apps — Web App SDK*, <https://core.telegram.org/bots/webapps>.

[27] Testcontainers Authors (2024), *Testcontainers for Java Documentation*, <https://java.testcontainers.org/>.

[28] Vernon V. (2013), *Implementing Domain-Driven Design*, Addison-Wesley, Boston, MA.

[29] VMware Inc. (2025), *Spring Boot Reference Documentation 3.4*, <https://docs.spring.io/spring-boot/>.

[30] VMware Inc. (2025), *Spring Security Reference Documentation 6.x*, <https://docs.spring.io/spring-security/reference/>.
