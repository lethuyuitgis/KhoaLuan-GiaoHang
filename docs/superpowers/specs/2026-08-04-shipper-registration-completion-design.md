# Thiết kế — Hoàn thiện FSM đăng ký shipper (#5)

> Ngày: 2026-08-04 · Pha mở rộng KLTN GiaoHang · Scope 1–2 tuần.

## 1. Bối cảnh & phát hiện

Đăng ký shipper qua Telegram bot **đã gần hoàn chỉnh ở backend**:

- FSM bot đầy đủ: `/start → ROLE:SHIPPER → name → phone (contact) → vehicle → plate`,
  có `/cancel`, chặn đăng ký trùng, validate biển số/tên.
  (`bot/handler/shipper/ShipperRegistration*Handler`, `bot/fsm/ShipperRegistrationStates`)
- `ShipperProfileService.registerPending()` tạo `user_role(SHIPPER, PENDING)` + `shipper_profile`
  (currentState=OFFLINE) + bắn `ShipperRegisteredEvent`.
- `ShipperRegistrationNotifier` báo mọi SHOP_OWNER khi có đăng ký mới.
- `POST /api/admin/shippers/{id}/approve` → `service.approve()` flip role → ACTIVE,
  evict `RoleResolver`, bắn `ShipperApprovedEvent` → notifier DM shipper kèm nút mở Mini App.

**Vòng lặp bị đứt ở Web Admin:** `ShippersPage.tsx` liệt kê tất cả shipper nhưng
không phân biệt PENDING/ACTIVE và **không có nút Duyệt** — shipper đăng ký xong nằm chờ
vô thời hạn. `ShipperResponse` DTO không trả `user_role.status`. Ngoài ra **không có đường
Từ chối** ở bất kỳ đâu (backend chỉ có `approve`).

## 2. Phạm vi (đã chốt với người dùng)

Cả 4: (A) Duyệt trên Web Admin · (B) Từ chối · (C) Lọc riêng danh sách chờ duyệt ·
(D) Rà soát edge-case FSM bot.

**Reject = xóa hẳn, cho đăng ký lại** (đã chốt): xóa `user_role(SHIPPER)` + `shipper_profile`,
bot báo "bị từ chối", user `/start` đăng ký lại từ đầu. Không thêm trạng thái REJECTED/BLOCKED.

## 3. Thiết kế

### A. Backend — lộ trạng thái duyệt
- `ShipperResponse` DTO thêm `approvalStatus` (`PENDING`|`ACTIVE`), nguồn = `user_role.status`.
- `AdminShipperController.toResponse()` inject `UserRoleRepository`, lookup status theo user
  (giữ đúng pattern N+1 hiện có — controller đã lookup `TelegramUser` từng người).

### B. Backend — reject
- `ShipperProfileService.reject(telegramUserId)`:
  - Guard **chỉ PENDING mới xóa được**. Role ACTIVE → `ValidationException("SHIPPER_ACTIVE", …)`
    (bảo vệ shipper đang chạy đơn, tránh dính FK `delivery_assignment`/`shipper_ledger`).
  - Không tìm thấy role → `NotFoundException`.
  - Xóa `shipper_profile` (nếu có) + `user_role(SHIPPER)`, evict `RoleResolver`,
    bắn `ShipperRejectedEvent(telegramUserId)`.
- `POST /api/admin/shippers/{id}/reject` (cạnh `/approve`, cùng `@PreAuthorize SHOP_OWNER`).
- `shared/event/ShipperRejectedEvent(Long telegramUserId)` (mirror `ShipperApprovedEvent`).
- `ShipperRegistrationNotifier.onShipperRejected` (AFTER_COMMIT) → DM:
  "❌ Đơn đăng ký shipper của bạn chưa được duyệt. Bạn có thể gõ /start để đăng ký lại."

### C. Web Admin — vá UI (`ShippersPage.tsx` + `@shop/shared`)
- `shared/types/shipper.ts`: thêm `approvalStatus: 'PENDING' | 'ACTIVE'`.
- `shared/api/shippers.ts`: `approveShipper(client, id)`, `rejectShipper(client, id)`.
- `ShippersPage`:
  - Section **"⏳ Chờ duyệt (n)"** ở đầu, chỉ hiện `PENDING`, mỗi dòng: tên/vehicle/biển số
    + nút **Duyệt** + **Từ chối** (Từ chối có `window.confirm`).
  - Bảng chính thêm cột/badge trạng thái duyệt (PENDING nổi bật vàng, ACTIVE xanh).
  - 2 mutation `invalidateQueries(['admin','shippers'])`; disable nút khi đang gọi.

### D. FSM edge-case
- **Fix bug:** `AWAITING_NAME` — chuỗi bắt đầu bằng `/` (vd `/start`) lọt validate 2–80 →
  bị lưu thành tên. `handleName` từ chối input bắt đầu bằng `/` với nudge.
- **Đã rà, giữ nguyên (ghi rõ — Rule 12):** không TTL cho state bỏ dở *(ngoài scope)*;
  cho phép trùng SĐT *(ngoài scope)*; đăng ký lại sau reject *(đã đúng nhờ delete)*.

## 4. Test (Rule 9 — mã hóa ý định)
- Backend service: `reject` xóa khi PENDING; **ném `SHIPPER_ACTIVE` khi ACTIVE**
  (test này fail nếu ai bỏ guard → bảo vệ shipper đang chạy); `reject` bắn `ShipperRejectedEvent`.
- Backend controller: `POST /reject` PENDING → 200 + role/profile biến mất.
- Bot FSM: `handleName` từ chối `/start`, giữ nguyên state AWAITING_NAME.
- Frontend: `ShippersPage` render section "Chờ duyệt" cho PENDING + nút Duyệt/Từ chối gọi đúng endpoint.

## 5. Tiêu chí hoàn thành
Shipper `/start` → đăng ký qua bot → hiện ở "Chờ duyệt" Web Admin → admin **Duyệt** →
shipper nhận DM + vào Mini App; hoặc **Từ chối** → shipper nhận DM + `/start` đăng ký lại được.
`./mvnw test` backend xanh + `pnpm test` frontend xanh + build FE thành công.

## 6. File đụng tới
Backend (delivery/shared/notification/bot): `ShipperResponse`, `AdminShipperController`,
`ShipperProfileService`, `ShipperRejectedEvent` (new), `ShipperRegistrationNotifier`,
`ShipperRegistrationTextHandler`.
Frontend: `shared/types/shipper.ts`, `shared/api/shippers.ts`, `webadmin/pages/ShippersPage.tsx`.
Tests tương ứng.
