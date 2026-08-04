# Thiết kế — Chat ẩn danh khách↔shipper qua Bot (#1)

> Ngày: 2026-08-04 · Pha mở rộng KLTN GiaoHang.

## Quyết định (đã chốt)
- **Chat-mode tường minh**: bấm nút "💬 Chat" (hoặc "💬 Trả lời") → vào FSM `CHAT_ACTIVE`,
  gõ `/thoat` để thoát. Không relay ngầm mọi text.
- **Lưu `chat_message`** (audit/lịch sử).
- Cả hai bên chat **qua bot DM**; relay bằng `sendText`/`SendMessage` (KHÔNG `forwardMessage`)
  → không bên nào thấy Telegram account/SĐT của bên kia. Text-only (ảnh/vị trí để sau).

## Phân tầng theo dependency (bot→delivery→order)
- **delivery (domain, không đụng bot):**
  - `chat_message` (V17) + entity + repo; `ChatRole {CUSTOMER, SHIPPER}`.
  - `ChatService.participants(assignmentId)` → `ChatParticipants{customerId, shipperId, active}`
    (active = assignment ACCEPTED/STARTED; load order để lấy customerId).
  - `record(...)`, `history(...)`, `historyForOrder(orderId)`.
- **bot (transport + FSM):**
  - `ChatStates` (CHAT_ACTIVE, payload assignmentId+role, callback `CHAT_OPEN:`, `/thoat`).
  - `ChatOpenCallbackHandler` (@Order 35): validate user là 1 bên + đơn active → set FSM.
  - `ChatMessageHandler` (@Order 0): text trong CHAT_ACTIVE → re-validate active → `record` +
    relay `BotSender.sendText(peer, "🧑 Khách: …"/"🛵 Shipper: …")`; `/thoat` thoát; lệnh khác
    escape (để /start,/help vẫn chạy); tin rỗng bỏ qua; payload hỏng → clear sạch.
    Tin relay kèm nút "💬 Trả lời" khi người nhận chưa ở chat-mode.
- **notification:** `onOrderAccepted` gắn nút "💬 Chat" (CHAT_OPEN:<assignmentId>) cho cả 2 bên.
- **audit:** `GET /api/admin/orders/{orderId}/chat` (backend; UI Web Admin để sau).

## Vòng đời
Mở từ khi shipper nhận đơn (ACCEPTED) → đóng khi DELIVERED/CANCELLED (text sau đó → clear
state + "trò chuyện đã đóng").

## Test
- delivery `ChatServiceTest`: participants (2 bên + active/inactive/ missing), record, historyForOrder rỗng.
- bot `ChatMessageHandlerTest`: relay có prefix vai trò + nút trả lời, /thoat, đơn đóng, blank,
  canHandle (plain/‧thoat true, /start false, không chat false).
- bot `ChatOpenCallbackHandlerTest`: mở cho đúng bên+active, từ chối người lạ/đơn xong/UUID hỏng.
- notification `OrderAssignedNotifierTest.onOrderAccepted`: cả 2 bên nhận nút CHAT_OPEN.
- V17 + wiring verify qua `ApplicationTests` + `OrderFlowIT` (Flyway + Testcontainers).

## Tiêu chí done
Shipper nhận đơn → cả 2 nhận nút 💬 Chat → bấm vào chat-mode → nhắn nhau ẩn danh qua bot
(prefix vai trò, giấu định danh) → lưu chat_message → /thoat hoặc đơn xong thì đóng. Admin
xem lại hội thoại qua endpoint audit. BE test xanh.
