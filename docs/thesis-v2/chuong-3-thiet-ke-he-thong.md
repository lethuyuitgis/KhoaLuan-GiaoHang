# CHƯƠNG 3: THIẾT KẾ HỆ THỐNG

Chương này trình bày thiết kế dữ liệu của hệ thống quản lý giao hàng dựa trên nền tảng Telegram và cổng thanh toán VNPay. Toàn bộ dữ liệu nghiệp vụ được lưu trữ trong một cơ sở dữ liệu quan hệ PostgreSQL 16, gồm khoảng mười lăm bảng chính, được tạo lập và tiến hoá tuần tự bằng công cụ quản lý phiên bản lược đồ Flyway thông qua các tập tin di trú (migration) đánh số từ V1 đến V15. Nội dung chương được tổ chức thành hai phần lớn: phần thứ nhất phân tích và thiết kế dữ liệu ở mức khái niệm và mức logic, bao gồm mô hình thực thể liên kết (ERD) và đặc tả chi tiết từng bảng; phần thứ hai trình bày mô hình quan hệ, gồm mô hình quan hệ ER rút gọn dưới dạng ký hiệu và mô tả tường minh các quan hệ khoá ngoại được hiện thực trong cơ sở dữ liệu.

## 3.1. Phân tích và thiết kế dữ liệu

### 3.1.1. Mô hình thực thể liên kết (ERD)

Mô hình thực thể liên kết (Entity Relationship Diagram — ERD) là công cụ mô hình hoá dữ liệu ở mức khái niệm, biểu diễn các thực thể (entity) trong hệ thống cùng các mối quan hệ (relationship) giữa chúng mà chưa đi sâu vào kiểu dữ liệu hay ràng buộc cụ thể. Sơ đồ dưới đây được tái sử dụng và mở rộng từ thiết kế lược đồ cơ sở dữ liệu của hệ thống, thể hiện đầy đủ mười bảy thực thể chính và các quan hệ giữa chúng.

```mermaid
erDiagram
  telegram_user ||--o{ user_role : has
  telegram_user ||--o| admin_user : links_to
  telegram_user ||--o| shipper_profile : is
  telegram_user ||--o| conversation_state : in_FSM
  telegram_user ||--o{ orders : places
  telegram_user ||--o{ delivery_assignment : delivers
  telegram_user ||--o{ rating : rates_as_customer
  telegram_user ||--o{ rating : rated_as_shipper

  admin_user ||--o{ refresh_token : has

  product ||--o{ order_item : included_in

  orders ||--|{ order_item : contains
  orders ||--o{ status_history : has_history
  orders ||--o| delivery_assignment : assigned_to
  orders ||--o{ payment : paid_by
  orders ||--o| rating : rated_by

  delivery_assignment ||--o{ location_ping : produces

  payment ||--o{ payment_transaction : audit_log

  shop_config ||--o{ orders : supplies_pickup
```

Các thực thể chính trong mô hình được diễn giải như sau.

**telegram_user** là thực thể trung tâm của toàn bộ hệ thống. Mỗi bản ghi tương ứng với một người dùng Telegram duy nhất, được định danh bằng chính mã số người dùng do Telegram cấp. Đây là điểm hội tụ của nhiều quan hệ: một người dùng có thể được gán một hoặc nhiều vai trò (`user_role`), có thể là chủ tài khoản quản trị (`admin_user`), có thể là một shipper với hồ sơ nghề nghiệp riêng (`shipper_profile`), có thể đang trong một trạng thái hội thoại của bot (`conversation_state`), và với tư cách khách hàng có thể tạo nhiều đơn hàng (`orders`) cũng như đưa ra nhiều lượt đánh giá (`rating`).

**user_role** biểu diễn quan hệ nhiều–nhiều đã được phân giải giữa người dùng và vai trò trong hệ thống. Một người dùng có thể đồng thời giữ nhiều vai trò khác nhau (ví dụ vừa là khách hàng vừa là shipper), và mỗi cặp người dùng–vai trò là duy nhất.

**product** là danh mục sản phẩm được chào bán. Mỗi sản phẩm có thể xuất hiện trong nhiều dòng đơn hàng khác nhau thông qua thực thể trung gian `order_item`.

**orders** là đơn hàng — thực thể nghiệp vụ cốt lõi. Một đơn hàng do một khách hàng tạo ra, chứa một hoặc nhiều dòng sản phẩm (`order_item`), sinh ra một chuỗi lịch sử chuyển trạng thái (`status_history`), có thể được gán tối đa một phiên giao hàng (`delivery_assignment`), có thể phát sinh một hoặc nhiều lần thanh toán (`payment`) và có thể nhận tối đa một lượt đánh giá (`rating`).

**delivery_assignment** là phiên gán shipper cho một đơn hàng. Quan hệ giữa đơn hàng và phiên gán là một–một (một đơn chỉ có tối đa một phiên gán còn hiệu lực). Mỗi phiên gán khi shipper chia sẻ vị trí trực tiếp sẽ sinh ra một chuỗi các điểm định vị (`location_ping`).

**payment** và **payment_transaction** hình thành hai lớp của mô hình thanh toán: `payment` ghi trạng thái thanh toán tổng thể của một đơn, còn `payment_transaction` là nhật ký kiểm toán (audit log) lưu vết mọi sự kiện tương tác với VNPay.

**rating** ghi nhận đánh giá của khách hàng đối với shipper sau khi đơn hàng đã giao thành công. Thực thể này tham chiếu đồng thời tới đơn hàng, tới người dùng đóng vai khách hàng và tới người dùng đóng vai shipper.

Các thực thể phụ trợ gồm: **admin_user** (tài khoản quản trị Web Admin) cùng **refresh_token** (token làm mới phiên đăng nhập); **conversation_state** và **processed_update** phục vụ cơ chế máy trạng thái hội thoại và bảo đảm tính bất biến (idempotency) của bot; và **shop_config** là bản ghi cấu hình đơn nhất (singleton) lưu toạ độ điểm lấy hàng và tham số tính phí giao hàng.

### 3.1.2. Mô hình thực thể logic

Mô hình thực thể logic đặc tả chi tiết từng bảng ở mức có thể hiện thực trực tiếp trên hệ quản trị cơ sở dữ liệu, bao gồm tên cột, kiểu dữ liệu và các ràng buộc/khoá đi kèm. Toàn bộ đặc tả dưới đây phản ánh trung thực các tập tin di trú Flyway V1–V15 của hệ thống. Quy ước ký hiệu: **PK** — khoá chính (Primary Key); **FK** — khoá ngoại (Foreign Key); **UQ** — ràng buộc duy nhất (Unique); **NN** — không rỗng (Not Null); **CK** — ràng buộc kiểm tra (Check).

#### a) Bảng `telegram_user` (V2)

Lưu thông tin người dùng Telegram. Khoá chính là mã số người dùng do Telegram cấp (không tự sinh).

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | BIGINT | PK (= Telegram user ID) |
| username | VARCHAR(64) | — |
| first_name | VARCHAR(128) | — |
| last_name | VARCHAR(128) | — |
| phone | VARCHAR(32) | — |
| photo_url | TEXT | — |
| language_code | VARCHAR(8) | — |
| is_blocked | BOOLEAN | NN, mặc định FALSE |
| created_at | TIMESTAMPTZ | NN, mặc định NOW() |
| updated_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục bộ phận: `idx_telegram_user_username` trên `username` với điều kiện `WHERE username IS NOT NULL`.

#### b) Bảng `user_role` (V2)

Gán vai trò cho người dùng. Phân giải quan hệ nhiều–nhiều giữa người dùng và tập vai trò.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | BIGSERIAL | PK |
| telegram_user_id | BIGINT | NN, FK → telegram_user(id) ON DELETE CASCADE |
| role | VARCHAR(16) | NN — CUSTOMER \| SHIPPER \| SHOP_OWNER |
| status | VARCHAR(16) | NN, mặc định 'ACTIVE' — ACTIVE \| PENDING \| BLOCKED |
| assigned_at | TIMESTAMPTZ | NN, mặc định NOW() |

Ràng buộc duy nhất: `uq_user_role` UNIQUE `(telegram_user_id, role)`. Chỉ mục: `idx_user_role_lookup` trên `(telegram_user_id, status)`.

#### c) Bảng `admin_user` (V5)

Tài khoản đăng nhập Web Admin của chủ shop, xác thực bằng email và mật khẩu băm BCrypt.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | BIGSERIAL | PK |
| email | VARCHAR(255) | NN, UQ |
| password_hash | VARCHAR(255) | NN (BCrypt-10) |
| full_name | VARCHAR(128) | — |
| telegram_user_id | BIGINT | FK → telegram_user(id) |
| is_active | BOOLEAN | NN, mặc định TRUE |
| created_at | TIMESTAMPTZ | NN, mặc định NOW() |
| updated_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục bộ phận: `idx_admin_user_active` trên `is_active` với `WHERE is_active = TRUE`.

#### d) Bảng `refresh_token` (V5)

Lưu refresh token của phiên đăng nhập Web Admin. Chỉ lưu giá trị băm (SHA-256), không lưu bản rõ.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | UUID | PK |
| admin_user_id | BIGINT | NN, FK → admin_user(id) ON DELETE CASCADE |
| token_hash | VARCHAR(255) | NN, UQ (SHA-256) |
| expires_at | TIMESTAMPTZ | NN |
| revoked | BOOLEAN | NN, mặc định FALSE |
| created_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục: `idx_refresh_token_user` trên `admin_user_id`; `idx_refresh_token_expiry` trên `expires_at` với `WHERE revoked = FALSE`.

#### e) Bảng `product` (V4)

Danh mục sản phẩm.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | BIGSERIAL | PK |
| name | VARCHAR(255) | NN |
| description | TEXT | — |
| price | NUMERIC(12,2) | NN, CK (price >= 0) |
| image_url | TEXT | — |
| stock | INT | NN, mặc định 0, CK (stock >= 0) |
| is_active | BOOLEAN | NN, mặc định TRUE |
| created_at | TIMESTAMPTZ | NN, mặc định NOW() |
| updated_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục bộ phận: `idx_product_active` trên `is_active` với `WHERE is_active = TRUE`.

#### f) Bảng `orders` (V4)

Đơn hàng — thực thể nghiệp vụ trung tâm. Khoá chính dạng UUID nhằm chống dò tuần tự (enumeration). Nhiều trường được lưu dưới dạng bản chụp (snapshot) tại thời điểm tạo đơn.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | UUID | PK |
| code | VARCHAR(32) | NN, UQ (dạng "DH<yyyyMMdd>-<seq>") |
| customer_id | BIGINT | NN, FK → telegram_user(id) |
| customer_name | VARCHAR(128) | — (snapshot) |
| customer_phone | VARCHAR(32) | — (snapshot) |
| pickup_lat | NUMERIC(10,7) | NN |
| pickup_lng | NUMERIC(10,7) | NN |
| delivery_address | TEXT | NN |
| delivery_lat | NUMERIC(10,7) | NN |
| delivery_lng | NUMERIC(10,7) | NN |
| distance_km | NUMERIC(8,3) | NN, CK (distance_km >= 0) |
| subtotal | NUMERIC(12,2) | NN, CK (subtotal >= 0) |
| delivery_fee | NUMERIC(12,2) | NN, CK (delivery_fee >= 0) |
| total | NUMERIC(12,2) | NN, CK (total >= 0) |
| payment_method | VARCHAR(16) | NN — COD \| VNPAY |
| payment_status | VARCHAR(16) | NN, mặc định 'PENDING' — PENDING \| SUCCESS \| FAILED \| REFUNDED |
| status | VARCHAR(16) | NN, mặc định 'PENDING' |
| note | TEXT | — |
| version | INT | NN, mặc định 0 (khoá lạc quan @Version) |
| created_at | TIMESTAMPTZ | NN, mặc định NOW() |
| updated_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục ghép: `idx_orders_status_created` trên `(status, created_at DESC)`; `idx_orders_customer_created` trên `(customer_id, created_at DESC)`.

#### g) Bảng `order_item` (V4)

Dòng sản phẩm trong đơn. Đơn giá được chụp lại từ `product.price` tại thời điểm tạo đơn để tránh biến động giá về sau ảnh hưởng đơn cũ.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | BIGSERIAL | PK |
| order_id | UUID | NN, FK → orders(id) ON DELETE CASCADE |
| product_id | BIGINT | NN, FK → product(id) |
| quantity | INT | NN, CK (quantity > 0) |
| unit_price | NUMERIC(12,2) | NN, CK (unit_price >= 0) — snapshot |
| subtotal | NUMERIC(12,2) | NN, CK (subtotal >= 0) |

Chỉ mục: `idx_order_item_order` trên `order_id`.

#### h) Bảng `status_history` (V4)

Nhật ký kiểm toán mỗi lần đơn hàng chuyển trạng thái. Bản ghi được chèn tại tầng dịch vụ trong cùng giao dịch với thao tác cập nhật trạng thái, không dùng trigger.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | BIGSERIAL | PK |
| order_id | UUID | NN, FK → orders(id) ON DELETE CASCADE |
| from_status | VARCHAR(16) | cho phép NULL (lần khởi tạo) |
| to_status | VARCHAR(16) | NN |
| changed_by_user_id | BIGINT | cho phép NULL (sự kiện hệ thống) |
| changed_at | TIMESTAMPTZ | NN, mặc định NOW() |
| note | TEXT | — |

Chỉ mục: `idx_status_history_order` trên `(order_id, changed_at)`.

#### i) Bảng `shipper_profile` (V6)

Hồ sơ nghề nghiệp của shipper. Khoá chính đồng thời là khoá ngoại tới `telegram_user` (quan hệ một–một). Các trường `rating_avg` và `rating_count` được tính lại từ truy vấn tổng hợp trên toàn bộ đánh giá của shipper.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| user_id | BIGINT | PK, FK → telegram_user(id) ON DELETE CASCADE |
| vehicle_type | VARCHAR(16) | NN — MOTORBIKE \| CAR \| BICYCLE |
| license_plate | VARCHAR(16) | — |
| current_state | VARCHAR(16) | NN, mặc định 'OFFLINE' — AVAILABLE \| BUSY \| OFFLINE |
| rating_avg | NUMERIC(3,2) | NN, mặc định 0.00 |
| rating_count | INT | NN, mặc định 0 |
| total_deliveries | INT | NN, mặc định 0 |
| updated_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục: `idx_shipper_state` trên `current_state`.

#### j) Bảng `delivery_assignment` (V6, V8)

Phiên gán shipper cho đơn hàng. Khoá chính UUID; cột `order_id` mang ràng buộc UNIQUE nhằm bảo đảm mỗi đơn chỉ có một phiên gán.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | UUID | PK |
| order_id | UUID | NN, UQ, FK → orders(id) ON DELETE CASCADE |
| shipper_id | BIGINT | FK → telegram_user(id) |
| status | VARCHAR(16) | NN — OFFERED \| ACCEPTED \| STARTED \| COMPLETED \| REJECTED \| CANCELLED |
| assigned_at | TIMESTAMPTZ | NN, mặc định NOW() |
| accepted_at | TIMESTAMPTZ | cho phép NULL |
| rejected_at | TIMESTAMPTZ | cho phép NULL |
| started_at | TIMESTAMPTZ | cho phép NULL |
| delivered_at | TIMESTAMPTZ | cho phép NULL |
| cancelled_at | TIMESTAMPTZ | cho phép NULL |

Chỉ mục: `idx_delivery_assignment_shipper` trên `(shipper_id, status)`; `idx_delivery_assignment_order` trên `order_id`. Ràng buộc duy nhất bộ phận `uq_assignment_shipper_started` (V8): UNIQUE trên `(shipper_id)` với điều kiện `WHERE status = 'STARTED'` — bảo đảm mỗi shipper chỉ có tối đa một phiên giao đang chạy tại một thời điểm.

#### k) Bảng `location_ping` (V7)

Điểm định vị GPS thu được từ tính năng Live Location của Telegram trong lúc shipper đang giao hàng.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | BIGSERIAL | PK |
| assignment_id | UUID | NN, FK → delivery_assignment(id) ON DELETE CASCADE |
| lat | NUMERIC(10,7) | NN |
| lng | NUMERIC(10,7) | NN |
| accuracy | NUMERIC(8,2) | cho phép NULL |
| heading | NUMERIC(5,2) | cho phép NULL |
| recorded_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục: `idx_location_ping_assignment_time` trên `(assignment_id, recorded_at DESC)` — hỗ trợ dựng lại đường đi (polyline); `idx_location_ping_recorded_at` trên `recorded_at`.

#### l) Bảng `payment` (V9)

Thanh toán của đơn hàng. Một đơn có thể phát sinh nhiều lần thanh toán (khi khách thử lại sau thất bại). Cột `vnp_txn_ref` là mã tham chiếu giao dịch VNPay mang ràng buộc UNIQUE nhằm chống phát lại (replay).

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | UUID | PK |
| order_id | UUID | NN, FK → orders(id) |
| method | VARCHAR(16) | NN — COD \| VNPAY |
| amount | NUMERIC(12,2) | NN, CK (amount >= 0) |
| status | VARCHAR(16) | NN — PENDING \| SUCCESS \| FAILED \| REFUNDED |
| vnp_txn_ref | VARCHAR(64) | UQ ("<orderCode>-<epochMs>") |
| vnp_transaction_no | VARCHAR(64) | — |
| vnp_response_code | VARCHAR(16) | — ("00", "07", ...) |
| paid_at | TIMESTAMPTZ | cho phép NULL |
| version | INT | NN, mặc định 0 (khoá lạc quan @Version) |
| created_at | TIMESTAMPTZ | NN, mặc định NOW() |
| updated_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục: `idx_payment_order` trên `order_id`; `idx_payment_status_created` trên `(status, created_at)`.

#### m) Bảng `payment_transaction` (V9)

Nhật ký kiểm toán mọi sự kiện VNPay. Trường `raw_payload` lưu toàn bộ payload đến dưới dạng JSONB, kể cả khi chữ ký không hợp lệ, phục vụ điều tra hậu kỳ (forensics).

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | BIGSERIAL | PK |
| payment_id | UUID | NN, FK → payment(id) ON DELETE CASCADE |
| event_type | VARCHAR(16) | NN — CREATE \| IPN \| RETURN |
| raw_payload | JSONB | NN |
| recorded_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục: `idx_payment_tx_payment` trên `(payment_id, recorded_at)`.

#### n) Bảng `rating` (V10)

Đánh giá của khách hàng đối với shipper sau khi đơn đã giao. Cột `order_id` mang ràng buộc UNIQUE để chống đánh giá trùng cho cùng một đơn.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | BIGSERIAL | PK |
| order_id | UUID | NN, UQ, FK → orders(id) ON DELETE CASCADE |
| customer_id | BIGINT | NN, FK → telegram_user(id) |
| shipper_id | BIGINT | NN, FK → telegram_user(id) |
| stars | SMALLINT | NN, CK (stars BETWEEN 1 AND 5) |
| comment | TEXT | — |
| created_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục: `idx_rating_shipper_created` trên `(shipper_id, created_at DESC)`. Ràng buộc UNIQUE trên `order_id` tự sinh chỉ mục đi kèm.

#### o) Bảng `conversation_state` (V3)

Trạng thái máy hội thoại hữu hạn (FSM) của bot cho mỗi người dùng. Khoá chính đồng thời là khoá ngoại tới `telegram_user`. Trường `data` dạng JSONB lưu ngữ cảnh tuỳ theo trạng thái.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| telegram_user_id | BIGINT | PK, FK → telegram_user(id) ON DELETE CASCADE |
| state | VARCHAR(64) | NN |
| data | JSONB | cho phép NULL |
| updated_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục: `idx_conversation_state_updated_at` trên `updated_at` (hỗ trợ cron dọn trạng thái quá hạn, TTL 30 phút).

#### p) Bảng `processed_update` (V3)

Bảo đảm tính bất biến (idempotency) cho bot: mỗi cập nhật (update) từ Telegram chỉ được xử lý một lần, chống trường hợp Telegram gửi lại trùng lặp.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| update_id | BIGINT | PK |
| processed_at | TIMESTAMPTZ | NN, mặc định NOW() |

Chỉ mục: `idx_processed_update_processed_at` trên `processed_at`.

#### q) Bảng `shop_config` (V13)

Bản ghi cấu hình cửa hàng dạng đơn nhất (singleton). Ràng buộc `CHECK (id = 1)` bảo đảm bảng luôn chỉ có đúng một dòng. Lưu thông tin thương hiệu, toạ độ điểm lấy hàng và các tham số tính phí giao hàng.

| Tên cột | Kiểu dữ liệu | Ràng buộc / Khoá |
|---|---|---|
| id | SMALLINT | PK, CK (id = 1) |
| name | VARCHAR(128) | NN, mặc định 'Shop Giao Hàng' |
| tagline | VARCHAR(256) | NN |
| logo_url | TEXT | — |
| brand_primary | VARCHAR(7) | NN, mặc định '#D97706' |
| brand_secondary | VARCHAR(7) | NN, mặc định '#FB923C' |
| contact_phone | VARCHAR(32) | — |
| contact_email | VARCHAR(128) | — |
| opening_hours | VARCHAR(64) | mặc định '08:00 - 22:00 hằng ngày' |
| pickup_lat | NUMERIC(10,7) | NN, mặc định 21.0285 |
| pickup_lng | NUMERIC(10,7) | NN, mặc định 105.8542 |
| pickup_address | VARCHAR(256) | NN, mặc định 'Shop default' |
| fee_base | NUMERIC(12,2) | NN, mặc định 15000 |
| fee_per_km | NUMERIC(12,2) | NN, mặc định 5000 |
| free_km | NUMERIC(8,3) | NN, mặc định 0 |
| updated_at | TIMESTAMPTZ | NN, mặc định NOW() |

## 3.2. Mô hình quan hệ

### 3.2.1. Mô hình quan hệ ER rút gọn

Mô hình quan hệ rút gọn biểu diễn mỗi bảng dưới dạng ký hiệu `TênBảng(danh_sách_thuộc_tính)`, trong đó khoá chính được đánh dấu **PK** (in đậm/gạch chân), khoá ngoại được đánh dấu **FK**, và ràng buộc duy nhất được đánh dấu **UQ**. Cách biểu diễn này lược bỏ kiểu dữ liệu và ràng buộc kiểm tra, tập trung làm rõ cấu trúc khoá và mối liên kết giữa các bảng.

- **telegram_user**(<u>id</u> **PK**, username, first_name, last_name, phone, photo_url, language_code, is_blocked, created_at, updated_at)

- **user_role**(<u>id</u> **PK**, telegram_user_id **FK**→telegram_user, role, status, assigned_at); **UQ**(telegram_user_id, role)

- **admin_user**(<u>id</u> **PK**, email **UQ**, password_hash, full_name, telegram_user_id **FK**→telegram_user, is_active, created_at, updated_at)

- **refresh_token**(<u>id</u> **PK**, admin_user_id **FK**→admin_user, token_hash **UQ**, expires_at, revoked, created_at)

- **product**(<u>id</u> **PK**, name, description, price, image_url, stock, is_active, created_at, updated_at)

- **orders**(<u>id</u> **PK**, code **UQ**, customer_id **FK**→telegram_user, customer_name, customer_phone, pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng, distance_km, subtotal, delivery_fee, total, payment_method, payment_status, status, note, version, created_at, updated_at)

- **order_item**(<u>id</u> **PK**, order_id **FK**→orders, product_id **FK**→product, quantity, unit_price, subtotal)

- **status_history**(<u>id</u> **PK**, order_id **FK**→orders, from_status, to_status, changed_by_user_id, changed_at, note)

- **shipper_profile**(<u>user_id</u> **PK, FK**→telegram_user, vehicle_type, license_plate, current_state, rating_avg, rating_count, total_deliveries, updated_at)

- **delivery_assignment**(<u>id</u> **PK**, order_id **UQ, FK**→orders, shipper_id **FK**→telegram_user, status, assigned_at, accepted_at, rejected_at, started_at, delivered_at, cancelled_at); **UQ** bộ phận(shipper_id) WHERE status='STARTED'

- **location_ping**(<u>id</u> **PK**, assignment_id **FK**→delivery_assignment, lat, lng, accuracy, heading, recorded_at)

- **payment**(<u>id</u> **PK**, order_id **FK**→orders, method, amount, status, vnp_txn_ref **UQ**, vnp_transaction_no, vnp_response_code, paid_at, version, created_at, updated_at)

- **payment_transaction**(<u>id</u> **PK**, payment_id **FK**→payment, event_type, raw_payload, recorded_at)

- **rating**(<u>id</u> **PK**, order_id **UQ, FK**→orders, customer_id **FK**→telegram_user, shipper_id **FK**→telegram_user, stars, comment, created_at)

- **conversation_state**(<u>telegram_user_id</u> **PK, FK**→telegram_user, state, data, updated_at)

- **processed_update**(<u>update_id</u> **PK**, processed_at)

- **shop_config**(<u>id</u> **PK**, name, tagline, logo_url, brand_primary, brand_secondary, contact_phone, contact_email, opening_hours, pickup_lat, pickup_lng, pickup_address, fee_base, fee_per_km, free_km, updated_at)

### 3.2.2. Mô hình quan hệ từ CSDL

Phần này mô tả tường minh các quan hệ khoá ngoại được hiện thực trong cơ sở dữ liệu, kèm ngữ nghĩa nghiệp vụ và hành vi xoá lan truyền (ON DELETE CASCADE) nếu có. Toàn hệ thống có mười sáu quan hệ khoá ngoại chính, được nhóm theo cụm nghiệp vụ như sau.

**Cụm người dùng và vai trò.** Bảng `telegram_user` là bảng cha của nhiều quan hệ. `user_role.telegram_user_id → telegram_user.id` (ON DELETE CASCADE) — khi xoá người dùng thì mọi vai trò của họ bị xoá theo; quan hệ này là một–nhiều (một người dùng nhiều vai trò), kèm ràng buộc UNIQUE `(telegram_user_id, role)` để tránh trùng vai trò. `admin_user.telegram_user_id → telegram_user.id` là quan hệ một–một tuỳ chọn, liên kết tài khoản quản trị với một tài khoản Telegram để bot có thể gửi thông báo cho chủ shop. `shipper_profile.user_id → telegram_user.id` (ON DELETE CASCADE) là quan hệ một–một, trong đó khoá chính của hồ sơ shipper chính là khoá ngoại trỏ về người dùng. `conversation_state.telegram_user_id → telegram_user.id` (ON DELETE CASCADE) là quan hệ một–một, mỗi người dùng có tối đa một trạng thái hội thoại đang hoạt động.

**Cụm quản trị.** `refresh_token.admin_user_id → admin_user.id` (ON DELETE CASCADE) là quan hệ một–nhiều: một tài khoản quản trị có thể có nhiều refresh token (nhiều thiết bị/phiên); khi xoá tài khoản thì mọi token bị thu hồi theo.

**Cụm đơn hàng.** `orders.customer_id → telegram_user.id` liên kết đơn hàng với khách hàng tạo đơn (một–nhiều). `order_item.order_id → orders.id` (ON DELETE CASCADE) — quan hệ một–nhiều thể hiện đơn hàng gồm nhiều dòng sản phẩm; xoá đơn thì xoá toàn bộ dòng. `order_item.product_id → product.id` liên kết mỗi dòng với sản phẩm gốc (không cascade, vì sản phẩm là dữ liệu danh mục dùng chung). `status_history.order_id → orders.id` (ON DELETE CASCADE) — quan hệ một–nhiều ghi lại toàn bộ lịch sử chuyển trạng thái của một đơn.

**Cụm giao hàng.** `delivery_assignment.order_id → orders.id` (ON DELETE CASCADE, UNIQUE) là quan hệ một–một: một đơn có tối đa một phiên gán shipper. `delivery_assignment.shipper_id → telegram_user.id` liên kết phiên gán với shipper (một–nhiều: một shipper nhiều phiên gán qua thời gian). `location_ping.assignment_id → delivery_assignment.id` (ON DELETE CASCADE) là quan hệ một–nhiều: một phiên giao sinh ra chuỗi nhiều điểm định vị; xoá phiên gán thì xoá theo toàn bộ điểm định vị. Đây là chuỗi quan hệ dây chuyền `orders → delivery_assignment → location_ping`.

**Cụm thanh toán.** `payment.order_id → orders.id` là quan hệ một–nhiều: một đơn có thể phát sinh nhiều lần thanh toán (khách thử lại sau khi thất bại). `payment_transaction.payment_id → payment.id` (ON DELETE CASCADE) là quan hệ một–nhiều: một lần thanh toán ghi nhiều sự kiện kiểm toán (CREATE, IPN, RETURN). Chuỗi quan hệ `orders → payment → payment_transaction` cho phép truy vết đầy đủ vòng đời thanh toán của một đơn.

**Cụm đánh giá.** `rating.order_id → orders.id` (ON DELETE CASCADE, UNIQUE) là quan hệ một–một: mỗi đơn nhận tối đa một đánh giá. `rating.customer_id → telegram_user.id` và `rating.shipper_id → telegram_user.id` là hai quan hệ khoá ngoại cùng trỏ về bảng `telegram_user` nhưng mang hai vai trò ngữ nghĩa khác nhau — người đánh giá (khách hàng) và người được đánh giá (shipper). Đây là ví dụ điển hình về hai quan hệ độc lập giữa cùng một cặp bảng.

**Bảng `product` và `shop_config`.** `product` là bảng cha trong quan hệ với `order_item` như đã nêu. `shop_config` không tham gia quan hệ khoá ngoại hình thức, nhưng về mặt logic cung cấp toạ độ điểm lấy hàng (`pickup_lat`, `pickup_lng`) và tham số phí (`fee_base`, `fee_per_km`, `free_km`) được chụp vào bảng `orders` tại thời điểm tạo đơn — quan hệ logic này được biểu diễn bằng liên kết `shop_config → orders` trong ERD ở mục 3.1.1.

Tổng hợp lại, mô hình dữ liệu tuân thủ nguyên tắc chuẩn hoá tới dạng chuẩn thứ ba (3NF) đối với dữ liệu tham chiếu, đồng thời chủ động phi chuẩn hoá (denormalization) có kiểm soát ở một số điểm phục vụ hiệu năng và bất biến nghiệp vụ: đơn giá (`order_item.unit_price`), thông tin khách (`orders.customer_name`, `orders.customer_phone`), toạ độ lấy hàng và trạng thái thanh toán (`orders.payment_status`) được chụp lại tại thời điểm tạo đơn để đơn hàng bất biến trước các thay đổi về sau. Các khoá chính dạng UUID cho `orders`, `payment`, `delivery_assignment`, `refresh_token` chống dò tuần tự, trong khi các bảng nội bộ dùng BIGSERIAL nhẹ và hiệu quả. Hệ thống ràng buộc UNIQUE và các chỉ mục bộ phận (partial index) bảo đảm các bất biến nghiệp vụ quan trọng ngay tại tầng cơ sở dữ liệu, tiêu biểu là quy tắc "một shipper chỉ có một phiên giao đang chạy" và "mỗi đơn chỉ một phiên gán, một đánh giá".
