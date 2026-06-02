# Defense-day Runbook — manual smoke test

End-to-end demo flow covering P1–P8. Estimated total runtime: **~25 minutes**
(after the stack is up). Two phones recommended for the live-location demo.

> Tip: open this file on a second screen while running the demo on the laptop.
> Each section is independent — you can skip a phase and still demo the rest.

---

## Pre-flight (5 min)

1. Start the full stack:
   ```bash
   cd KhoaLuan-GiaoHang
   cp .env.example .env
   $EDITOR .env  # fill BOT_TOKEN / BOT_USERNAME / JWT_SECRET / VNPAY_TMN_CODE / VNPAY_HASH_SECRET
   docker compose -f infra/docker-compose.yml up -d --build
   ```
2. Wait for all 5 containers to be `(healthy)`:
   ```bash
   docker compose -f infra/docker-compose.yml ps
   # Repeat until every service shows "Up X seconds (healthy)"
   ```
3. Sanity smoke:
   ```bash
   curl -s http://localhost/actuator/health    # {"status":"UP"}
   curl -sI http://localhost/admin/ | head -1  # HTTP/1.1 200 OK
   ```
4. Register 2 Telegram accounts as the demo principals:
   - **Phone A (Khách)** — your main Telegram. Search bot `@<BOT_USERNAME>`. Send `/start`.
   - **Phone B (Shipper)** — second Telegram (a friend's account or a secondary number). Send `/start` → menu → "Đăng ký làm shipper" → fill form.
5. On the Web Admin:
   - Login `shop@example.com` / `Demo@Shop2026!`
   - Tab "Shipper" → approve Phone B's PENDING row.

---

## P1 — Admin login + auth (2 min)

Goal: prove JWT + role-based access works.

1. Open `http://localhost/admin/login`
2. Login `shop@example.com` / `Demo@Shop2026!` → should land on `/` (Dashboard)
3. F12 → Application → Local Storage → confirm `accessToken` + `refreshToken` keys
4. Logout (top-right menu) → redirected to `/login`
5. Try direct nav to `http://localhost/admin/orders` while logged out → bounced to `/login`. ✓ AuthGuard works.

---

## P2 — Sản phẩm + đơn hàng CRUD (3 min)

1. Web Admin → tab "Sản phẩm" → see 10 seeded products
2. Click "+ Thêm sản phẩm" → fill name "Cà phê đen demo" / price 25000 / stock 50 / save
3. New product appears in the list with image placeholder
4. Tab "Đơn hàng" → confirm 30 demo orders visible across statuses
5. Click a PENDING order → detail page shows order items + customer info + status timeline
6. Click "Xác nhận đơn" → status flips to CONFIRMED; you should see the row update in the orders list within 2s (live WS push from P9 broadcaster).

---

## P3 — Mini App customer flow (3 min)

1. **Phone A** → open the bot chat → tap the "Đặt hàng" menu button (or `/start` → button).
2. Mini App opens inside Telegram → product list appears
3. Tap "Phở bò tái" → "Thêm vào giỏ" → cart badge increments
4. Tap cart icon → "Thanh toán" → checkout form pre-filled with name + phone
5. Allow Telegram location permission → drop pin near Hoàn Kiếm (~21.028, 105.854)
6. **Payment method radio: select COD** (we'll demo VNPay in P7)
7. Tap "Đặt hàng" → success screen → order code shown (e.g. `DEMO-2026-0031`)
8. Switch to Web Admin → "Đơn hàng" tab → the new order is at the top within ~2s (no manual refresh — WebSocket pushed it).

---

## P4 — Bot commands + shipper onboarding (2 min)

1. **Phone A**: `/menu` → confirm inline keyboard appears with "Đặt hàng" / "Đơn của tôi" / "Liên hệ".
2. **Phone B**: `/orders` → see "Hiện không có đơn nào chờ shipper" (shipper queue empty before assignment).
3. **Web Admin**: open the order from P3 → "Phân công shipper" → select Phone B's shipper row → confirm.
4. **Phone B** should receive a bot message: "🆕 Có đơn mới #DEMO-2026-XXXX. [Nhận] / [Từ chối]".

---

## P5 — Shipper nhận đơn + Live Location (5 min)

Goal: shipper accepts, starts delivery, and the customer sees real-time location updates.

> Requires 2 phones. If only 1 phone available, use Telegram Web on a laptop for Phone B.

1. **Phone B** taps **[Nhận]** on the assignment message → status flips to ASSIGNED.
2. **Phone B**: open the bot chat → "Bắt đầu giao" button (or `/start_delivery <orderCode>` text command) → status flips to DELIVERING.
3. **Phone B** in the bot chat: attachment menu (📎) → "Live Location" → choose 15-minute duration → start sharing.

   > Note: Telegram Live Location requires the bot to handle `edited_message` updates. P5 wired this. The backend's `LocationIngestHandler` writes each ping to `location_ping` and broadcasts to `/topic/orders/{id}/location`.

4. **Phone A** (customer) → re-open the Mini App → tap "Đơn của tôi" → tap the DELIVERING order → map view opens with shipper's position pin.
5. **Phone B**: walk around (or use Telegram's "fake my location" if testing offline) → **Phone A's map updates in real-time** (~5-15s lag).

**Troubleshooting:**
- If the map doesn't move: open browser dev tools on the Mini App (Telegram Desktop: right-click → Inspect) → confirm WebSocket connection in Network tab.
- If WebSocket fails behind a corporate proxy: tunnel via ngrok (`ngrok http 80`) and update `VNPAY_RETURN_URL` / `VNPAY_IPN_URL` (only affects P7).

---

## P6 — Tracking map UI (1 min)

1. Same as P5 step 4-5. The map is the deliverable.
2. Verify the polyline trails the shipper's movements (last 10 pings drawn).
3. Verify the ETA estimate text updates at the top.

---

## P7 — VNPay sandbox payment (4 min)

Goal: customer pays online, backend auto-confirms via IPN.

> Sandbox tunnel via ngrok is needed if you want IPN to reach the backend
> from the public internet. For LOCAL demo (browser + backend on same host),
> the `localhost` URLs in `.env` work — VNPay sandbox's return URL hits the
> customer's browser, which calls back to `http://localhost/api/payment/vnpay/return`
> and the backend's polling-based confirm path catches up via the Mini App's
> 3-second order detail polling.

1. **Phone A** → bot → Mini App → add a new item (e.g. "Bún bò Huế") → checkout.
2. **Payment method radio: select VNPay** → "Đặt hàng".
3. Telegram opens VNPay overlay → choose **NCB** test bank → enter test card:
   ```
   Card no:      9704198526191432198
   Cardholder:   NGUYEN VAN A
   Issue date:   07/15
   OTP:          123456
   ```
4. Confirm → VNPay redirects to `http://localhost/api/payment/vnpay/return?...&vnp_ResponseCode=00`.
5. Backend renders the success HTML page; meanwhile VNPay's server has called `/api/payment/vnpay/ipn` (or will within seconds for sandbox).
6. **Mini App's order detail page** (poll every 3s for 60s after redirect) flips `payment_status` from PENDING → SUCCESS, and `status` from PENDING → CONFIRMED.
7. **Web Admin** → "Đơn hàng" → the order's payment badge shows green "SUCCESS".

**If the IPN doesn't fire (testing in isolation):**
- Inspect `backend/app/src/main/resources/static/payment-success.html` — the return page renders even without IPN.
- The 15-minute `PaymentExpiryScheduler` will mark the payment FAILED if no IPN arrives. Manual confirm via Web Admin still works.

---

## P8 — Đánh giá shipper + Dashboard + Reports (5 min)

1. Set up a DELIVERED order so the rating prompt fires:
   - Either: complete the P5 flow + on Phone B → "Đã giao xong" → status DELIVERED.
   - Or: in Web Admin, on the DELIVERING order from P5, click "Đánh dấu đã giao".
2. **Phone A** receives a bot message with inline keyboard `⭐ Đánh giá shipper` + buttons `1 ★` … `5 ★` + `Bỏ qua`.
3. Tap `5 ★` → bot replies "Cảm ơn bạn!" + asks "Bạn có muốn nhập nhận xét? Gõ tin nhắn hoặc /skip".
4. Type a comment → bot acks "Cảm ơn nhận xét của bạn!".
5. **Web Admin** Dashboard → KPI cards refresh; "Top 3 shipper" should now include Phone B's shipper with their new rating.
6. Web Admin → `/reports` → choose date range "last 7 days" → 3 charts render:
   - LineChart doanh thu theo ngày
   - BarChart top shipper
   - PieChart lý do huỷ
   - Lazy-loaded (P9 polish IM-03) — confirm Network tab shows `ReportsPage-XXXX.js` chunk loads on first visit.

---

## Defense Q&A prep

Common reviewer questions + suggested talking points:

| Q | A |
|---|---|
| "Why STOMP over plain WebSocket?" | Pub/sub semantics + SockJS fallback for proxy compatibility + Spring's first-class support. |
| "Why Postgres instead of MySQL?" | JSONB for FSM payload (P8 `conversation_state.data`), better full-text search, idiomatic with Hibernate 6. |
| "What if VNPay IPN doesn't arrive?" | `PaymentExpiryScheduler` runs every 60s; marks PENDING payments older than 15 min as FAILED. Manual admin-confirm endpoint also exists. |
| "Why no production HTTPS?" | Out of scope for thesis defense (no domain provisioned). Production would terminate TLS at nginx via Let's Encrypt. |
| "Why monolith instead of microservices?" | Spring Boot multi-module gives module isolation without operational overhead. ~217 tests, single jar deploy. Microservices = premature complexity. |
| "How does the bot handle high load?" | Bot uses long-polling (Telegram's recommended dev mode). For production, set `BOT_MODE=webhook` + ngrok / public domain; backend handles webhook POSTs via the same handler chain. |
| "Authentication for the WebSocket?" | Mini App passes `X-Telegram-Init-Data` header in STOMP CONNECT; backend verifies HMAC against bot token. Admin passes JWT in `Authorization: Bearer` header. Subscribe whitelist enforces per-principal destination access. |

---

## Tear-down

```bash
docker compose -f infra/docker-compose.yml down
# Keep the volume so data survives across `up`/`down` cycles.
# Full reset (drops the seeded data):
docker compose -f infra/docker-compose.yml down -v
```
