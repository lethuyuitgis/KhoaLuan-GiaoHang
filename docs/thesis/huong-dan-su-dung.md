# Hướng dẫn sử dụng hệ thống

Tài liệu này hướng dẫn ba vai trò chính (chủ shop, khách hàng, shipper) cách dùng hệ thống quản lý giao hàng. Đây là tài liệu vận hành cho hội đồng / reviewer / người dùng cuối — không phải tài liệu dev. Nếu cần *build* hoặc *triển khai*, xem `docs/RUNBOOK.md`.

---

## 1. Tổng quan vai trò và URL

Hệ thống có ba điểm vào, mỗi điểm phục vụ một vai trò khác nhau:

| Vai trò | URL truy cập | Cách xác thực |
|---|---|---|
| **Chủ shop** (admin) | `http://localhost:5181/` (dev) hoặc `https://<domain>/admin/` (prod) | Email + password (JWT) |
| **Khách hàng** (customer) | Mini App qua bot Telegram `@DevBot` → nút "Mở Mini App" | Tự động qua Telegram WebApp initData |
| **Shipper** (giao hàng) | Cùng bot Telegram, tài khoản được cấp quyền SHIPPER | Tự động qua Telegram WebApp initData |

Trong môi trường dev không có Telegram thật, có thể dùng URL trực tiếp `http://localhost:5180/?devUserId=<id>` để giả lập (sandbox xác thực — chỉ hoạt động khi backend chạy với profile `dev`).

**Tài khoản mẫu** sẵn có sau khi seed V11:
- **Admin**: `shop@example.com` / `Demo@Shop2026!` (vai trò SHOP_OWNER)
- **Khách hàng**: `?devUserId=9000000001` (Nguyễn Văn An)
- **Shipper**: `?devUserId=9000000101` (Dũng Phạm — đã được duyệt)

---

## 2. Hướng dẫn cho Chủ shop (Web Admin)

Web Admin là trung tâm điều hành — tất cả nghiệp vụ quản lý đều thực hiện ở đây.

### 2.1. Đăng nhập

Mở `http://localhost:5181/login`, nhập email + mật khẩu (xem mục 1). Hệ thống trả về một cặp `accessToken` + `refreshToken` lưu trong `localStorage`; refresh tự động khi access token hết hạn.

### 2.2. Trang Tổng quan (`/`)

Trang chủ admin (Hình 4.3 trong báo cáo). Hiển thị bốn nhóm chỉ số chính:

- **6 thẻ KPI hôm nay**: Đơn hôm nay, Chờ xác nhận, Đang giao, Đã giao, Đã huỷ, Doanh thu hôm nay.
- **Biểu đồ "Doanh thu 7 ngày qua"** (LineChart Recharts): doanh thu theo từng ngày.
- **Top 3 shipper** (theo số lượng đơn hoàn tất trong 7 ngày).
- Thông tin shop và tên người dùng đang đăng nhập ở sidebar.

Số liệu cập nhật realtime qua WebSocket STOMP — không cần F5.

### 2.3. Quản lý đơn (`/orders`)

Danh sách tất cả đơn hàng với filter:
- Theo trạng thái: PENDING / CONFIRMED / ASSIGNED / DELIVERING / DELIVERED / CANCELLED
- Theo khoảng ngày (date picker)
- Pagination 20 đơn/trang

**Click vào một đơn** → mở `/orders/<id>` xem chi tiết:
- Danh sách sản phẩm + giá
- Thông tin khách (tên, số điện thoại, ghi chú)
- Địa chỉ giao + link Google Maps
- Lịch sử trạng thái (`status_history`) — ai chuyển trạng thái, lúc nào
- Phần "Hoa hồng & thanh toán" (chỉ hiển thị khi DELIVERED): phí ship gốc, voucher giảm, hoa hồng shipper, hình thức thanh toán, số tiền thu COD (Hình 6.10).

**Action gán shipper** trên đơn `CONFIRMED`: chọn shipper từ dropdown rồi bấm "Gán". Hệ thống phát `OrderAssignedEvent` → bot Telegram gửi inline keyboard "Chấp nhận / Từ chối" cho shipper.

### 2.4. Báo cáo (`/reports`)

Báo cáo doanh thu theo khoảng ngày tuỳ chọn:
- Chọn "Từ ngày" + "Đến ngày" + "Nhóm theo" (ngày / tuần)
- LineChart "Doanh thu theo ngày"
- BarChart top shipper (theo doanh thu đơn hoàn tất)

### 2.5. Sản phẩm (`/products`)

CRUD sản phẩm:
- Bảng danh sách: ảnh, tên, giá, trạng thái (còn hàng / hết hàng)
- Nút "Thêm sản phẩm" → form `/products/new` (tên, giá, mô tả, ảnh URL, available)
- Click row → form sửa `/products/:id`
- Nút "Xoá" trong form sửa: soft delete (set `available=false`)

### 2.6. Shipper (`/shippers`)

Danh sách shipper với hai cột mới Phase 15:
- "Số dư ví" — số tiền shop nợ shipper (dương) hoặc shipper nợ shop (âm)
- "Thu nhập 7 ngày" — tổng hoa hồng shipper kiếm trong tuần qua

**Click row** → `/shippers/<id>` xem chi tiết shipper:
- Thông tin cá nhân + đánh giá trung bình
- Card số dư ví hiện tại + nút "Đã trả lương" / "Đã nhận tiền nộp"
- Bảng lịch sử ledger đầy đủ (mọi giao dịch hoa hồng + COD + đối soát)

**Đối soát thủ công**:
1. Click nút "Đã trả lương" (số dư dương) hoặc "Đã nhận tiền nộp" (số dư âm)
2. Modal hiện ra: nhập số tiền + ghi chú (vd: "CK ngày 2026-06-08")
3. Bấm "Lưu" → hệ thống tạo entry SETTLEMENT_PAYOUT (−) hoặc SETTLEMENT_DEPOSIT (+) đưa số dư về 0

### 2.7. Thu nhập shipper (`/shipper-earnings`)

Báo cáo tổng hợp thu nhập shipper (Hình 6.8):
- 3 KPI: Tổng hoa hồng đã chi trả, Số đơn DELIVERED, Số shipper hoạt động
- BarChart "Top shipper" theo hoa hồng kiếm được
- LineChart "Hoa hồng theo ngày"
- Hai input date "Từ ngày" / "Đến ngày" để chọn khoảng

Số liệu chỉ tính các đơn ở trạng thái DELIVERED (đã giao xong) và type entry là COMMISSION.

### 2.8. Khuyến mãi (`/vouchers`)

Quản lý voucher (Hình 6.1):
- Bảng danh sách kèm filter chip theo trạng thái (active/expired/all) và đối tượng (SHIPPING/PRODUCTS/all)
- Mỗi row: mã, tên, đối tượng, mức giảm, đã dùng / max, hết hạn, trạng thái

**Tạo voucher mới** — bấm "+ Tạo voucher" → form `/vouchers/new` (Hình 6.2):

1. **Thông tin chung**:
   - Mã voucher (UPPERCASE A-Z 0-9 _ -, max 32 ký tự)
   - Tên hiển thị (max 128 ký tự)
   - Đối tượng: PRODUCTS (giảm tiền hàng) hoặc SHIPPING (giảm phí ship)
   - Loại giảm: FIXED (số tiền cố định) hoặc PERCENT (phần trăm)

2. **Mức giảm**:
   - Giá trị (đ với FIXED hoặc % với PERCENT, > 0)
   - Cap (chỉ với PERCENT — giảm tối đa, để trống = không giới hạn)
   - Preview: "Đơn 200 000đ → giảm X đ" tính realtime

3. **Điều kiện áp dụng**:
   - Đơn tối thiểu (vd: 100 000đ)
   - Khoảng hiệu lực: từ ngày — đến ngày
   - Tổng số lần dùng (max_uses_total — bỏ trống = không giới hạn)
   - Số lần dùng mỗi khách (max_uses_per_customer — vd: 1)

Sau khi lưu, voucher xuất hiện trong danh sách + có thể được khách nhập ở Checkout.

**Chỉnh sửa voucher**: click row → `/vouchers/:id` xem chi tiết (Hình 6.3) → bấm "Sửa". Lưu ý mã + đối tượng + loại giảm là **immutable** — không sửa được sau khi tạo (tránh xác minh tính nhất quán phức tạp với redemption đã có).

**Vô hiệu hoá**: bấm "Xoá" — soft delete (`is_active=false`), voucher biến mất khỏi danh sách active nhưng lịch sử redemption vẫn nguyên.

### 2.9. Cài đặt (`/settings`)

Trang cấu hình shop (Hình 6.11) — đổi được những thứ thường xuyên cần điều chỉnh mà không phải redeploy:

1. **Thông tin shop**: tên, tagline, logo URL, số điện thoại/email liên hệ, giờ mở cửa
2. **Thương hiệu**: màu chính + phụ (hex `#RRGGBB`) — Mini App áp ngay sau khi lưu nhờ cơ chế dynamic theming
3. **Điểm lấy hàng (pickup)**: địa chỉ + lat/lng — dùng để tính khoảng cách giao
4. **Phí giao hàng**: feeBase + feePerKm + freeKm (công thức `base + max(0, km − freeKm) × perKm`)
5. **Hoa hồng shipper** (Phase 15 mới): tỉ lệ % phí ship gốc mà shipper được hưởng (mặc định 80%, validate 0–100, step 0.1)

Mỗi field có preview hoặc validation thông minh — vd: thay đổi `feePerKm` thấy ngay "Đơn 3km → X đ"; thay đổi tỉ lệ hoa hồng thấy "Đơn 30k phí ship × Y% = Z đ".

> ⚠️ Lưu ý quan trọng về hoa hồng: tỉ lệ vừa thay đổi chỉ áp dụng cho các đơn DELIVERED *sau* thời điểm lưu. Các entry COMMISSION đã ghi trong quá khứ KHÔNG bị recalculate — đã snapshot vào `orders.shipper_commission`.

---

## 3. Hướng dẫn cho Khách hàng (Mini App)

Khách hàng tương tác với hệ thống qua Telegram Mini App. Không cần đăng nhập riêng — Telegram tự xác thực qua `initData`.

### 3.1. Cách mở Mini App

**Cách 1 (production)**: vào Telegram, tìm bot `@DevBot` → nhấn `/start` → chạm nút "Mở Mini App" trong inline keyboard.

**Cách 2 (dev/test)**: mở browser bất kỳ → `http://localhost:5180/?devUserId=9000000001` (hoặc bất kỳ `telegram_user.id` nào có vai trò CUSTOMER).

### 3.2. Splash + chuyển hướng

Trang đầu hiển thị logo shop + tagline + nút "Bắt đầu" → tự chuyển sang `/customer/shop` (catalog).

### 3.3. Danh mục (`/customer/shop`)

Catalog sản phẩm (Hình 4.2). Khách:
- Xem ảnh + tên + giá từng món
- Bấm `+` để thêm 1 món vào giỏ, bấm `−` để giảm
- Bottom tab: Trang chủ / Giỏ hàng / Đơn của tôi — badge "X món" trên Giỏ hàng nếu có món trong cart

Giỏ hàng được persist vào `localStorage` (Zustand `persist`) — không mất nếu khách thoát Mini App giữa chừng.

### 3.4. Giỏ hàng (`/customer/cart`)

Danh sách món đã chọn + tổng tiền. Mỗi món có thể tăng/giảm số lượng hoặc xoá. Bấm "Đặt hàng" để sang Checkout.

### 3.5. Checkout (`/customer/checkout`)

Trang xác nhận đơn (Hình 6.4):

1. **Sản phẩm** — danh sách món đã chọn + tạm tính
2. **Thông tin người nhận** — tên + số điện thoại (mặc định dùng tên Telegram nếu trống)
3. **Địa chỉ giao** — bắt buộc chọn trên bản đồ Leaflet:
   - Bấm vào bản đồ để pin toạ độ
   - Hoặc dùng ô tìm kiếm (Nominatim geocoding) — gõ "123 Lê Lợi, Hoàn Kiếm" → bấm gợi ý
   - Khi pin xong, phí giao tự tính qua công thức Haversine + shop_config
4. **Ghi chú** (tuỳ chọn) — "Giao tối 6-8h, gọi trước khi đến"
5. **Khuyến mãi** (mới Phase 14):
   - Ô "Mã giảm tiền hàng": gõ mã PRODUCTS (vd `GIAM20K`) + bấm "Áp"
   - Ô "Mã giảm phí ship": gõ mã SHIPPING (vd `FREESHIP`) + bấm "Áp"
   - Sau khi áp thành công, chip xanh `✓ <mã> — Giảm X đ` hiện ra cùng nút "× Gỡ"
   - Nếu mã không hợp lệ (hết hạn, không đủ điều kiện, đã dùng), thông báo đỏ "Mã không hợp lệ hoặc không áp dụng được cho đơn này"
6. **Phương thức thanh toán**:
   - **COD**: trả tiền mặt khi shipper giao (mặc định)
   - **VNPay (sandbox)**: chuyển sang trang VNPay → quét QR / nhập thẻ test → IPN tự confirm đơn

Bấm "Đặt hàng" → tạo đơn → chuyển sang trang chi tiết đơn `/customer/orders/<id>`.

### 3.6. Đơn của tôi (`/customer/orders`)

Lịch sử đơn của khách — mỗi đơn hiển thị mã, trạng thái, tổng tiền, thời gian.

**Click một đơn** → `/customer/orders/<id>` xem chi tiết:
- Danh sách món + tạm tính + giảm giá + phí ship + tổng
- Bản đồ Leaflet với marker shipper realtime (nếu trạng thái DELIVERING) — vị trí cập nhật tự động qua STOMP `/user/queue/order/<id>/location`
- Lịch sử trạng thái + thời gian
- Nếu đơn DELIVERED + chưa rate: nút "Đánh giá shipper" → mở rating modal (1-5 sao + comment)

---

## 4. Hướng dẫn cho Shipper (Mini App)

Shipper là vai trò đặc biệt — phải được admin duyệt mới hoạt động được. Sau khi được duyệt, mở Mini App từ cùng bot Telegram → hệ thống tự nhận diện vai trò → chuyển hướng sang `/shipper/earnings`.

### 4.1. Đăng ký làm shipper

Trong bot Telegram, gõ `/become_shipper` → bot khởi động FSM hội thoại 4 bước:
1. Tên đầy đủ
2. Số điện thoại
3. Loại xe (BIKE / CAR)
4. Biển số

Sau khi hoàn thành, đơn đăng ký được gửi tới admin (trang `/shippers`). Admin duyệt → bot thông báo cho shipper.

### 4.2. Bottom tab navigation (mới Phase 15)

Sau khi vào Mini App với vai trò SHIPPER, ở mọi trang đều có thanh tab cố định ở đáy:

```
[💰 Thu nhập] [📋 Đơn] [👛 Ví] [👤 Profile]
```

Bốn tab này là bốn trang chính của shipper. Tab đang active được tô màu thương hiệu.

### 4.3. Trang Thu nhập (`/shipper/earnings`) — trang chủ

Hình 6.5 — landing page mới của shipper sau khi đăng nhập. Gồm:

- **Hero card** lớn: "Hôm nay bạn kiếm được X đ" + số đơn hôm nay (gradient brand color)
- **3 KPI tile**: Tuần này / Tháng này / Số dư ví
- **Biểu đồ cột 7 ngày qua**: thu nhập từng ngày — giúp shipper thấy xu hướng
- Link "Xem chi tiết theo ngày →" sang `/shipper/earnings/history`

### 4.4. Trang Đơn (`/shipper/assignments`) — danh sách đơn

Mỗi đơn được render dưới dạng **card** (mới Phase 15):

```
┌────────────────────────────────────────┐
│ [STATUS]              +X đ dự kiến    │
│ DEMO-2026-0045                         │
│ Nguyễn Văn A · 3.2 km                  │
└────────────────────────────────────────┘
```

- Tag trạng thái màu (xanh dương = ACCEPTED, tím = STARTED, xanh lá = COMPLETED, đỏ = CANCELLED)
- Mã đơn font monospace
- Badge "+X đ dự kiến" — ước lượng hoa hồng = `phí_ship × 80%` (chính xác hơn khi đơn DELIVERED)
- Khoảng cách giao + tên khách

**Click vào một đơn** → `/shipper/assignments/<id>` chi tiết:
- Bản đồ Leaflet hiện điểm pickup + điểm giao
- Thông tin khách + ghi chú
- Nút action theo trạng thái:
  - `OFFERED` (mới được gán): "Chấp nhận" / "Từ chối"
  - `ACCEPTED`: "Bắt đầu giao" → bot Telegram nhắc shipper share Live Location
  - `STARTED`: "Đã giao" → mark DELIVERED
  - `COMPLETED`: hiển thị "Đã ghi nhận vào ví" (hoa hồng đã được tự động cộng)

### 4.5. Trang Ví (`/shipper/wallet`)

Hình 6.6 — quản lý số dư + lịch sử giao dịch:

- **Số dư hiện tại**: số dương = shop nợ shipper (chờ payout), số âm = shipper nợ shop (chờ deposit). Mô tả ngắn bên dưới giải thích.
- **Filter chip**: Tất cả / Hoa hồng / COD đã thu / Shop trả lương / Đã nộp tiền
- **Danh sách entries** theo thứ tự thời gian:
  - `COMMISSION` (xanh) — hoa hồng từng đơn DELIVERED
  - `COD_OWED` (đỏ) — tiền mặt shipper đã thu của khách (sẽ phải nộp lại)
  - `SETTLEMENT_PAYOUT` (đỏ) — shop đã trả lương cho shipper (đối soát)
  - `SETTLEMENT_DEPOSIT` (xanh) — shipper đã nộp tiền cho shop (đối soát)

Mỗi entry có ghi chú + dấu thời gian + số tiền màu xanh/đỏ.

### 4.6. Trang Profile (`/shipper/profile`)

Hình 6.7 — thông tin cá nhân:
- Avatar gradient theme color
- Tên + số điện thoại
- Đánh giá trung bình (★ X.XX) — tính từ rating của khách (V10/V12)
- Tổng đơn đã giao
- Ngày tham gia

---

## 5. Sample scenario — End-to-end demo

Đây là kịch bản demo gợi ý cho buổi bảo vệ — show được toàn bộ flow trong 5-7 phút.

### Setup (1 phút)

1. Mở 3 tab/window song song:
   - Tab 1: Web Admin `http://localhost:5181/orders` (đã login)
   - Tab 2: Mini App khách `http://localhost:5180/?devUserId=9000000001` (mock Telegram)
   - Tab 3: Mini App shipper `http://localhost:5180/shipper/earnings?devUserId=9000000101`

### Scene 1 — Admin tạo voucher (1 phút)

1. Trên tab admin → sidebar "🏷️ Khuyến mãi" → `/vouchers`
2. Bấm "+ Tạo voucher"
3. Điền form:
   - Mã: `DEMO50K`
   - Tên: "Giảm 50 000đ cho demo"
   - Đối tượng: PRODUCTS / Loại: FIXED / Giá trị: 50000
   - Đơn tối thiểu: 100 000đ
   - Hiệu lực: hôm nay → 30 ngày sau
   - Max tổng: 100 / Mỗi khách: 1
4. Bấm "Tạo voucher" → trang redirect về `/vouchers/<id>` xác nhận

### Scene 2 — Khách đặt đơn với voucher (2 phút)

1. Tab khách → catalog → bấm `+` 3-4 món để đẩy subtotal > 100k
2. Bấm tab "Giỏ hàng" → "Đặt hàng"
3. Trên trang Checkout:
   - Tên: "Khách demo"
   - SĐT: "0901234567"
   - Click vào bản đồ để pin địa chỉ giao (vd: gần Hồ Hoàn Kiếm)
   - Section Khuyến mãi: gõ `DEMO50K` vào ô "Mã giảm tiền hàng" → "Áp" → thấy chip xanh "Giảm 50 000đ"
   - Section Phương thức thanh toán: chọn "COD"
4. Bấm "Đặt hàng" → redirect sang trang chi tiết đơn

### Scene 3 — Admin gán shipper + shipper nhận đơn (1 phút)

1. Quay lại tab admin → `/orders` → đơn mới xuất hiện realtime ở đầu danh sách
2. Click đơn → trang chi tiết hiện ra
3. Bấm "Gán shipper" → chọn "Dũng Phạm" (id 9000000101) → "Gán"
4. (Trong production sẽ có bot Telegram bắn inline keyboard, nhưng demo nội bộ thì admin click "Đánh dấu đang giao" để bypass)

### Scene 4 — Shipper hoàn tất đơn (1 phút)

1. Quay lại tab shipper — refresh `/shipper/assignments` → đơn mới xuất hiện trong danh sách
2. Click vào đơn → bấm "Bắt đầu giao" → trạng thái chuyển STARTED
3. Bấm "Đã giao" → trạng thái DELIVERED
4. Listener `OrderCompletionListener` tự động tạo 2 entry ledger:
   - COMMISSION +hoa hồng
   - COD_OWED −tổng đơn (vì là COD)

### Scene 5 — Verify thu nhập hai phía (1 phút)

1. **Tab shipper**:
   - Trang Earnings: hero card cập nhật "Hôm nay X đ" + biểu đồ có cột mới hôm nay
   - Trang Wallet: thấy 2 entry mới (COMMISSION + COD_OWED) → số dư âm (shipper đang giữ COD chưa nộp)

2. **Tab admin**:
   - Sidebar "💰 Thu nhập" → `/shipper-earnings` → KPI "Tổng hoa hồng" tăng
   - Sidebar "Shipper" → `/shippers` → cột "Số dư ví" của Dũng Phạm âm
   - Click row → chi tiết shipper → bấm "Đã nhận tiền nộp" → modal → nhập số tiền = tổng đơn → "Lưu"
   - Ledger thêm entry SETTLEMENT_DEPOSIT → số dư về 0 (hoặc còn lại đúng phần hoa hồng)

3. Quay lại admin → click vào đơn vừa xong → cuộn xuống section "Hoa hồng & thanh toán" → thấy đầy đủ breakdown.

### Scene 6 — Verify voucher đã được redeem (30 giây)

1. Trang admin `/vouchers/<id>` (voucher DEMO50K) → thấy "Đã dùng: 1/100" và một dòng redemption mới trong lịch sử.

---

## 6. Khắc phục sự cố thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Mini App load trắng + lỗi "useState null" | Duplicate React copy (xảy ra khi cài deps không sạch) | Xoá `node_modules/.pnpm` của miniapp, chạy `pnpm install` từ root frontend |
| Settings page báo "Không tải được cấu hình shop" | Backend chưa apply V13 migration hoặc backend đang restart | Kiểm tra `flyway_schema_history`; restart backend qua `mvnw -pl app -am package -DskipTests` |
| Voucher báo INVALID dù thấy còn hạn | Khách đã dùng (max_uses_per_customer = 1) — check `voucher_redemption` | Tăng `max_uses_per_customer` hoặc dùng voucher khác |
| Shipper Earnings hiện 0đ dù đơn DELIVERED | Đơn đó pre-V15 (chưa có `shipper_commission`); hoặc commit không xảy ra; hoặc listener phát chậm | Restart backend; verify `orders.shipper_commission` qua SQL |
| Live Location không cập nhật | Bot Telegram chưa được set webhook hoặc shipper chưa share live location | Check `/api/admin/test/telegram` — gửi tin test; bảo shipper bật "Share live location" trong chat |
| VNPay return 99 SignatureFailed | `vnp_HashSecret` trong `.env` không khớp sandbox | Đối chiếu Merchant Portal VNPay sandbox |

---

## 7. Tham khảo thêm

- **Tài liệu kỹ thuật**: Chương 3 (kiến trúc) + Chương 4 (cài đặt) + Phụ lục I (Phase 14/15)
- **RUNBOOK**: `docs/RUNBOOK.md` — bộ smoke test thủ công 8 bước cho ngày bảo vệ
- **Demo video script**: `docs/thesis/demo-video-script.md` — kịch bản chi tiết hơn nếu cần quay video
- **API spec**: chạy backend → `http://localhost:8080/swagger-ui.html` (nếu có cấu hình)
- **DB schema**: `backend/app/src/main/resources/db/migration/V*.sql` — 15 file migration tuần tự
