# Zalo Mini App — Thiết lập & Bàn giao

Tài liệu tổng hợp cho nhánh mở rộng **Zalo Mini App** (`frontend/zaloapp`) — chạy
song song với Telegram Mini App (`frontend/miniapp`), phục vụ ~75 triệu MAU Zalo
tại Việt Nam, tái sử dụng phần lớn UI + toàn bộ backend nghiệp vụ.

## 1. Trạng thái

| Hạng mục | Trạng thái |
|---|---|
| Luồng khách (đặt món → giỏ → checkout → theo dõi → đánh giá) | ✅ Đầy đủ, chạy local |
| Lưu địa chỉ + autocomplete · Voucher · Tracking map · Chat ẩn danh · Rating | ✅ Parity với miniapp |
| Auth thật (zmp-sdk + backend verify) | ✅ Đã wire, **gated** — bật khi có creds |
| Trang shipper trên Zalo | ❌ Cố ý bỏ — shipper vẫn dùng Telegram bot |
| Publish lên Zalo thật | ⏳ Cần Zalo Developer Account + `zmp-cli` build |

## 2. Khác biệt kiến trúc so với Telegram

Zalo Mini App **không có bot DM và WebSocket** như Telegram, nên các tính năng
vốn chạy qua bot/STOMP được làm lại bằng **REST + polling**:

| Tính năng | Telegram | Zalo |
|---|---|---|
| Auth | initData (HMAC-SHA256) | `zmp-sdk getAccessToken` → backend verify `/v2.0/me` |
| Theo dõi shipper | STOMP WebSocket | poll `GET /api/orders/{id}/location` (5s) |
| Chat khách↔shipper | bot DM relay | `OrderChat` REST + poll; khách gửi → relay sang **bot Telegram của shipper** |
| Đánh giá | bot FSM | `RatingForm` → `POST /api/orders/{id}/rating` |

**Backend thêm cho Zalo:** `CustomerChatController` + `CustomerChatNotifier`
(relay chat khách→bot shipper qua `CustomerChatSentEvent`). Các endpoint
voucher/location/rating tái dùng nguyên (đã có sẵn cho miniapp).

## 3. Đăng ký (lấy App ID + App Secret + Mini App ID)

1. **eKYC** tại `developers.zalo.me` (bắt buộc trước khi tạo app).
2. **Tạo Ứng dụng** → `developers.zalo.me` → "Tạo ứng dụng mới" → nhận **App ID** +
   **App Secret** (mục Cài đặt/Bảo mật). App ID hiện tại: `2561890966422977519`.
3. **Đăng ký OA** tại `oa.zalo.me` (loại Doanh nghiệp, cần GPKD) — Mini App phải gắn 1 OA.
4. **Tạo Mini App** tại `mini.zalo.me/developers` → chọn app ở bước 2 → nhận **Mini App ID**.
5. Xác thực Mini App → Zalo duyệt **~3–5 ngày làm việc** (tổng cả OA có thể 1–2 tuần).

## 4. Cấu hình creds

Điền vào file `.env` ở gốc repo (mẫu ở `.env.example`):

```
ZALO_APP_ID=2561890966422977519   # public — đã set mặc định
ZALO_APP_SECRET=<bí mật>          # ▼ điền, KHÔNG commit ▼
ZALO_MINI_APP_ID=<từ mini.zalo.me>
```

- `ZALO_APP_ID` **trống** → `ZaloAuthFilter` tự tắt (bỏ qua header `X-Zalo-Access-Token`).
- Verify token qua `/v2.0/me` chủ yếu cần access token từ frontend; App Secret để
  dành cho các flow khác (ZNS, auth-code exchange).

## 5. Code đã wire (nơi cần biết)

- **Backend** `modules/auth/.../ZaloAuthFilter.java`: gọi `GET graph.zalo.me/v2.0/me`
  (header `access_token`), verify + **upsert** `telegram_user` cho khách Zalo mới.
  Profile `dev` chấp nhận token `dev:<id>` để test wiring không cần Zalo thật.
- **Frontend** `zaloapp/src/lib/zalo.ts`: trong Zalo container (`window.ZaloJavaScriptInterface`)
  dùng `zmp-sdk` thật (import động → lazy chunk); ngoài Zalo → `window.open` +
  dev-bypass `X-Dev-User-Id`.

## 6. Chạy & kiểm thử local (không cần Zalo account)

```bash
pnpm --filter @shop/zaloapp dev      # http://localhost:5182 (dev-bypass, user demo seed)
pnpm --filter @shop/zaloapp test     # 52 test
pnpm --filter @shop/zaloapp build
```

Backend `dev` profile: gửi header `X-Zalo-Access-Token: dev:9000000001` để mô phỏng
một khách Zalo đã xác thực (không cần token thật).

## 7. Deploy lên Zalo thật (zmp CLI) — toolchain đã dựng sẵn

### 7.1. Đã có trong repo (commit sẵn)
- `frontend/zaloapp/app-config.json` — manifest Zalo (title "Shop Giao Hàng", header cam).
- `zmp-cli` 4.0.3 (devDep) + scripts trong `zaloapp/package.json`:
  - `build:zalo` = `vite build --base ./` (base **tương đối** để asset chạy trong khung Zalo)
  - `zalo:login` = `zmp login`
  - `zalo:deploy` = `zmp deploy -t -e -o dist`

### 7.2. Điểm mấu chốt về toolchain (đã kiểm chứng)
- **zmp-cli 4.x dùng webpack**, không nuốt được project Vite này → `zmp build` báo
  *"This is not ZMP project"*. **Cách vòng:** build bằng chính vite của app rồi
  deploy thư mục đã build qua cờ **`-e/--existing`**:
  `zmp deploy -t -e -o dist` (đã verify: qua được check project, chỉ chặn ở login).
- **Backend phải PUBLIC** — app chạy trong Zalo trên điện thoại, không gọi được
  `localhost`. Test nhanh bằng tunnel:
  ```bash
  cloudflared tunnel --url http://localhost:8080   # → https://xxx.trycloudflare.com
  ```
- **Build phải nhúng URL backend public** (không phải `/api` tương đối):
  ```bash
  cd frontend/zaloapp
  VITE_API_BASE_URL=https://xxx.trycloudflare.com pnpm build:zalo
  ```

### 7.3. Login (BẮT BUỘC làm trong Terminal thật — không tự động được)
`zmp login` cần TTY tương tác; shell script/CI không nhập prompt được.
```bash
cd frontend/zaloapp
npx zmp login
#  ? Mini App ID:        → dán MINI APP ID (dạng SỐ, lấy ở mini.zalo.me → app → Thông tin)
#  ? Login Method:       → chọn 1. Login Via QR Code With Zalo App
#  → Terminal hiện QR    → mở app Zalo trên điện thoại QUÉT QR → xác nhận → "Login success"
```
Sau khi "Login success", token lưu vào máy → các lệnh `zmp deploy` sau chạy được
không cần login lại.

**Bẫy thường gặp — `✖ Login failed! Error: Invalid data`:**
- Nhập **nhầm Mini App ID bằng secret/app-key** (vd chuỗi kiểu `jBRYTFKvrapx73W8rXVJ`
  KHÔNG phải Mini App ID — Mini App ID là dãy số). Lấy đúng field "App ID" ở
  `mini.zalo.me`.
- Chọn **method 2 (App Access Token)** rồi dán nhầm Mini App ID vào ô token → dùng
  **method 1 (QR)** cho chắc.
- **App mới tạo chưa được Zalo duyệt** → phải chờ Zalo xác thực Mini App (~3–5 ngày)
  mới login/deploy được. Đây là nguyên nhân hay gặp nhất khi mọi field đều đúng mà
  vẫn "Invalid data".

### 7.4. Deploy → lấy QR bản testing
```bash
cd frontend/zaloapp
npx zmp deploy -t -e -o dist -p -m "mo ta phien ban"
#  -t testing · -e deploy dist có sẵn · -o dist thư mục build · -p passive (không hỏi)
#  → in ra LINK + QR bản testing → mở app Zalo quét QR → app chạy trong Zalo thật
```

### 7.5. Bật auth Zalo thật ở backend (thay dev-bypass)
Điền `.env` gốc (mục 4) `ZALO_APP_SECRET` + `ZALO_MINI_APP_ID` rồi restart backend;
`ZaloAuthFilter` sẽ verify access token thật từ `zmp-sdk` qua `graph.zalo.me/v2.0/me`
(dev-bypass `X-Dev-User-Id` chỉ dùng khi test trên trình duyệt thường).

> Còn để ý: **id-namespacing giữa Zalo id và Telegram id** trong bảng `telegram_user`
> nếu chạy song song hai nền tảng trên cùng DB (khách Zalo được upsert vào cùng bảng).
