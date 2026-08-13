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

## 7. Publish (khi có account) — CHƯA làm

1. Điền `.env` (mục 4).
2. Cài `zmp-cli`, thêm `app-config.json` (manifest Zalo), build `.zmp`.
3. Upload `.zmp` lên **Zalo Mini App Studio** → gửi duyệt.

> Các bước mục 7 cần Zalo runtime/toolchain thật để kiểm chứng — báo lại khi có
> creds để tinh chỉnh (đặc biệt id-namespacing giữa Zalo id và Telegram id trong
> `telegram_user` nếu chạy song song hai nền tảng trên cùng DB).
