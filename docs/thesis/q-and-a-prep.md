# Q&A Prep — Buổi bảo vệ khoá luận

> **Mục đích:** Tổng hợp 27 câu hỏi có khả năng cao hội đồng IT sẽ hỏi, chia thành 6 nhóm. Mỗi câu có *Trả lời gợi ý* súc tích (3–5 câu) + *Tham chiếu* (chương/mục báo cáo / file code / commit / RUNBOOK).
> **Cách dùng:** Sinh viên đọc kỹ rồi tự nói lại bằng giọng của mình. Không học thuộc lòng cứng nhắc — nắm key points và tham chiếu, dùng từ ngữ tự nhiên.
> **Nguyên tắc trả lời:** ngắn gọn (~30–60s mỗi câu); có *trade-off* và *lý do thiết kế* rõ ràng; không nói "em không biết" — thay vào đó "em chưa làm/đo cụ thể, nhưng nếu mở rộng sẽ làm theo cách [...]".
> **Nguồn gốc:** Mở rộng từ phần "Defense Q&A prep" trong `docs/RUNBOOK.md` (7 câu gốc) lên 27 câu.

---

## Nhóm 1 — Kiến trúc & thiết kế (5 câu)

### Q1. Vì sao chọn Modular Monolith mà không Microservices?

**Trả lời gợi ý:**
"Em chọn Modular Monolith có chủ đích, không phải vì không biết microservices. Lý do thứ nhất là *phạm vi khoá luận* — một mình em phát triển và bảo trì, microservices đòi hỏi orchestration K8s, distributed tracing, service mesh, đó là premature complexity cho 1 shop 50–500 đơn/ngày. Thứ hai là *transaction* — local ACID dễ hơn nhiều so với saga / eventual consistency của microservices. Thứ ba là *lối thoát* — em đã thiết kế 8 module giao tiếp qua Spring Events với `AFTER_COMMIT`, nên khi cần tách microservice trong tương lai chỉ cần thay event publisher bằng Kafka mà gần như không phải sửa code nghiệp vụ. Đây là lựa chọn evolution-friendly."

**Tham chiếu:**
- Chương 3 §3.2 — Kiến trúc tổng thể.
- `diem-noi-bat-va-huong-phat-trien.md` mục A.3.
- Thư mục `backend/` — 8 Maven submodule (`shared`, `auth`, `order`, `delivery`, `payment`, `bot`, `notification`, `app`).

---

### Q2. Cách giao tiếp cross-module — vì sao dùng Spring Application Events?

**Trả lời gợi ý:**
"Hai module không gọi trực tiếp service hay repository của nhau — chỉ giao tiếp qua *Spring Application Events*. Lý do: (1) module phát sự kiện không cần biết internal của module nhận, giữ được nguyên tắc *low coupling*; (2) một sự kiện có thể có 0 hoặc nhiều listener — dễ thêm tính năng mới không phải sửa code cũ (open/closed principle); (3) đây là lối thoát microservices — chỉ cần thay event publisher bằng Kafka/RabbitMQ là tách được. Hệ thống hiện có 9 cross-module event: `OrderCreatedEvent`, `OrderConfirmedEvent`, `OrderAssignedEvent`, `OrderDeliveredEvent`, `OrderCancelledEvent`, `PaymentSucceededEvent`, `PaymentFailedEvent`, `LocationPingReceivedEvent`, `ShipperApprovedEvent`."

**Tham chiếu:**
- Chương 3 §3.2.3 — Giao tiếp cross-module.
- Code: `backend/shared/src/main/java/.../events/` — 9 event class.
- Listener ví dụ: `backend/notification/src/main/java/.../OrderLifecycleNotifier.java`.

---

### Q3. AFTER_COMMIT vs @EventListener thuần — sự khác biệt và lý do chọn?

**Trả lời gợi ý:**
"`@EventListener` thuần fire **đồng bộ ngay khi `publishEvent` được gọi**, kể cả khi transaction chưa commit. Vấn đề: nếu listener gửi notification (ví dụ Telegram bot ack 'Đơn của bạn đã xác nhận') nhưng sau đó transaction rollback do exception, khách nhận noti nhưng DB không có đơn — inconsistent state. `@TransactionalEventListener(phase = AFTER_COMMIT)` fire **chỉ khi transaction outer đã commit thành công** — không bao giờ rollback. Đây là pattern bắt buộc cho mọi side-effect ngoài DB (notification, broadcast, external API call). Có một subtlety: nếu listener cần ghi DB, phải `Propagation.REQUIRES_NEW` vì outer transaction đã đóng — em đã catch điều này ở Wave 1 Task 9 IT của P7 (OrderService.confirmAfterPayment)."

**Tham chiếu:**
- Chương 4 §4.7 — Module notification.
- `diem-noi-bat-va-huong-phat-trien.md` mục A.4.4.
- Code: `backend/payment/src/main/java/.../service/OrderService.java` — `@Transactional(propagation = REQUIRES_NEW)`.

---

### Q4. Vì sao cần cả Mini App + Bot? Mini App đủ chưa?

**Trả lời gợi ý:**
"Vì *UX khác nhau cho từng tình huống*. Mini App tốt cho UI phong phú: catalog có ảnh, giỏ hàng, bản đồ Leaflet, biểu đồ — những thứ cần screen real estate. Bot tốt cho *thao tác 1-tap* và *notification push tự nhiên* — shipper nhận offer đang đi xe, không thể mở Mini App rồi click; inline keyboard 'Nhận / Từ chối' là tối ưu. Bot cũng là kênh duy nhất cho push noti — Telegram không push noti cho Mini App nếu user không mở. Cuối cùng, Bot hỗ trợ FSM hội thoại (đăng ký shipper, đánh giá comment) tự nhiên hơn nhiều so với Mini App. Việc dùng cả hai là tận dụng thế mạnh của từng kênh chứ không phải redundant."

**Tham chiếu:**
- Chương 1 §1.7.1 — Mô hình triển khai lai.
- `diem-noi-bat-va-huong-phat-trien.md` mục A.1.
- Screenshots: `screenshots/miniapp-*` (Mini App) + bot mock.

---

### Q5. ERD có gì đáng chú ý? Vì sao `orders` UUID PK còn `rating` BIGSERIAL?

**Trả lời gợi ý:**
"15 bảng quản lý qua 12 Flyway migration. *Quyết định UUID v4 cho `orders`, `payment`, `delivery_assignment`* là chống enumeration attack: kẻ tấn công không thể đoán `/api/orders/1, /api/orders/2, ...` để dò đơn của khách khác. Các bảng nội bộ như `rating`, `status_history`, `location_ping` vẫn dùng BIGSERIAL vì (1) không lộ ra URL ngoài, (2) đã được khoá bởi context của bảng cha (rating join với orders qua `order_id UUID`), (3) BIGSERIAL nhỏ gọn hơn UUID 16 byte, performance index tốt hơn cho bảng append-only như `location_ping`. Còn lại: `orders.code` là user-facing string `DH<yyyyMMdd>-<seq>` UNIQUE để khách dễ đọc; `orders.version` cho optimistic lock; 2 composite index `(status, created_at DESC)` và `(customer_id, created_at DESC)` cho query thường gặp."

**Tham chiếu:**
- Chương 3 §3.3.1 ERD + §3.3.2 mô tả bảng.
- Code: `backend/order/src/main/resources/db/migration/V4__order.sql`.
- `diem-noi-bat-va-huong-phat-trien.md` mục A.9 — Bảng metrics.

---

## Nhóm 2 — Bảo mật (4 câu)

### Q6. Làm sao verify Telegram initData?

**Trả lời gợi ý:**
"Mini App gửi header `X-Telegram-Init-Data` chứa query string Telegram đã sign. Backend trong `TelegramAuthFilter`: (1) parse query string thành map; (2) trích `hash` ra riêng và tính lại; (3) tạo `data_check_string` bằng cách sort các param khác theo key alphabet và join `key=value\n`; (4) tính HMAC-SHA256 với key là `SHA256("WebAppData", BOT_TOKEN)`; (5) so sánh hash tính lại với hash trong initData bằng `MessageDigest.isEqual` (constant-time, chống timing attack); (6) kiểm tra `auth_date` không quá 24 giờ chống replay. Nếu hợp lệ, parse `user` JSON field, nạp `TelegramUser` từ DB và set vào `SecurityContext`."

**Tham chiếu:**
- Chương 3 §3.7.1 L1 — TelegramAuthFilter.
- Code: `backend/auth/src/main/java/.../filter/TelegramAuthFilter.java`.
- Test: `backend/auth/src/test/java/.../TelegramAuthServiceTest.java`.

---

### Q7. JWT access 15 phút vs refresh 7 ngày — cơ sở chọn?

**Trả lời gợi ý:**
"Trade-off security vs UX. Access token 15 phút ngắn để giảm cửa sổ attack — nếu bị leak (ví dụ qua XSS), kẻ tấn công có tối đa 15 phút trước khi token hết hạn. Refresh token 7 ngày để khách không phải đăng nhập lại liên tục. Quan trọng: refresh token là *DB-backed* (lưu ở bảng `refresh_token`) với cột `token_hash` SHA-256 — backend không lưu cleartext, và có thể *revoke* (logout, đổi password) bằng cách xoá row. Access JWT là JWS HS512 stateless không revoke được, nhưng vì TTL ngắn nên acceptable. Pattern này khớp khuyến nghị OAuth 2.0 và NIST SP 800-63B Authenticator Lifecycle."

**Tham chiếu:**
- Chương 3 §3.6.2 — JWT Web Admin.
- Code: `backend/auth/src/main/java/.../service/JwtService.java`.
- Bảng `refresh_token` ở V2 migration.

---

### Q8. VNPay IPN signature verify chi tiết? Tại sao MessageDigest.isEqual?

**Trả lời gợi ý:**
"IPN từ VNPay là POST có query string với các param `vnp_*` và một param `vnp_SecureHash`. Backend `VnpaySignatureService.verify`: (1) loại param `vnp_SecureHash` và `vnp_SecureHashType` ra khỏi map; (2) sort còn lại theo key alphabet; (3) URL-encode value và join `key=value&...`; (4) tính HMAC-SHA512 với key là `VNPAY_HASH_SECRET`; (5) so sánh hex hash tính lại với hash từ VNPay bằng `MessageDigest.isEqual`. Lý do `MessageDigest.isEqual` (constant-time) thay vì `String.equals`: chống *timing attack* — `String.equals` short-circuit khi gặp byte khác, kẻ tấn công có thể đo response time để brute-force từng byte hash. `MessageDigest.isEqual` luôn duyệt hết, không leak thông tin qua thời gian. Đây là requirement V6 ASVS Level 1."

**Tham chiếu:**
- Chương 3 §3.7.1 L8 + Chương 4 §4.5 — Module payment.
- Code: `backend/payment/src/main/java/.../service/VnpaySignatureService.java`.
- Test: `backend/payment/src/test/java/.../VnpaySignatureServiceTest.java`.

---

### Q9. WebSocket SUBSCRIBE allowlist — chống được attack gì?

**Trả lời gợi ý:**
"Ban đầu em chỉ verify ở STOMP CONNECT — user đã auth thì subscribe được mọi topic. Code review P6 catch lỗ hổng: user A (đã auth là khách hàng) có thể subscribe `/user/B/queue/order/{B-orderId}/location` để theo dõi đơn của user B. Đây là *Insecure Direct Object Reference (IDOR)* qua broadcast channel. Em vá bằng `ChannelInterceptor` chặn SUBSCRIBE: (1) parse destination; (2) match regex allowlist `^/user/(\\d+)/queue/order/[a-f0-9-]+/location$`; (3) extract `userId` ở capture group 1; (4) so với principal.id của session; (5) reject nếu không khớp. Admin có topic riêng `/topic/admin/orders` chỉ accept role SHOP_OWNER. Đây là Lớp 7 trong defense-in-depth, có regression test `WebSocketSubscribeAllowlistIT`."

**Tham chiếu:**
- Chương 3 §3.7.1 L7.
- Code: `backend/app/src/main/java/.../websocket/SubscribeAllowlistInterceptor.java`.
- Commit P6 review fix.
- Code review report: `docs/superpowers/reviews/p6-review.md` CR-1.

---

## Nhóm 3 — Database & Migration (3 câu)

### Q10. Tại sao 12 migration thay vì 1 file schema? Khi nào không nên migrate?

**Trả lời gợi ý:**
"12 migration vì mỗi pha phát triển add/modify schema riêng — schema là kết quả của một *quá trình* tiến hoá, không phải tạo một lần xong. Lợi ích: (1) production có data thật không thể `DROP + CREATE` — Flyway migration up-only là cách duy nhất bảo toàn data; (2) review/rollback từng phase dễ hơn (biết V8 đã add cái gì); (3) repeatable trên mọi môi trường — dev/CI/staging/prod cùng chạy V1→V12 từ schema rỗng. *Không nên migrate*: (1) khi cần *destructive change* trên cột có data (rename phải qua add-new + backfill + drop-old, không phải `ALTER COLUMN`); (2) khi production data quá lớn (cần online migration tools như pg_repack / pt-online-schema-change). Trong scope khoá luận, dataset nhỏ nên Flyway đủ."

**Tham chiếu:**
- Chương 3 §3.3 + Chương 4 §4.3.
- Thư mục: `backend/*/src/main/resources/db/migration/V*.sql`.
- Flyway docs.

---

### Q11. Partial unique index `uq_assignment_shipper_started` — ý nghĩa?

**Trả lời gợi ý:**
"Đây là Lớp 11 defense-in-depth, chống *wrong-shipper attribution* của Live Location ping. Bài toán: một shipper có thể có nhiều assignment trong lịch sử (DELIVERED, REJECTED, CANCELLED) nhưng tại bất kỳ thời điểm nào *chỉ được STARTED tối đa 1 đơn*. Nếu có 2 STARTED, khi shipper share Live Location, backend không biết ping thuộc đơn nào → có thể attribute nhầm cho khách khác. Postgres partial index: `CREATE UNIQUE INDEX uq_assignment_shipper_started ON delivery_assignment(shipper_user_id) WHERE status = 'STARTED'`. Index chỉ index khi status STARTED — các status khác không vi phạm uniqueness. Database-level enforcement, không thể bypass dù logic application có bug. Có regression test `AssignmentConcurrencyIT` cố tình race condition để verify."

**Tham chiếu:**
- Chương 3 §3.7.1 L11.
- Migration: `backend/delivery/src/main/resources/db/migration/V8__delivery.sql`.
- `diem-noi-bat-va-huong-phat-trien.md` mục A.6 Lớp 11.

---

### Q12. status_history pattern — vì sao tách bảng riêng thay vì trigger?

**Trả lời gợi ý:**
"`orders.status` là trạng thái hiện tại; `status_history` là *lịch sử mọi transition*. Em chọn pattern *application-level append* thay vì Postgres trigger với 3 lý do: (1) *transactional safety* — INSERT vào status_history nằm trong cùng transaction với UPDATE orders.status, nếu rollback thì cả hai cùng rollback (trigger thì cũng đảm bảo nhưng khó debug hơn); (2) *richer payload* — code application có context (event nào trigger, user nào, lý do huỷ nếu có), trigger chỉ thấy OLD/NEW row; (3) *testable* — service-level test mock được, trigger phải dựng Postgres thật mới test. Bảng `status_history(id BIGSERIAL, order_id UUID, from_status, to_status, reason, changed_by, changed_at)` — dùng cho timeline trên Order Detail và audit khi có dispute."

**Tham chiếu:**
- Chương 3 §3.3.2 bảng `status_history`.
- Migration V4.
- Service: `backend/order/src/main/java/.../service/OrderStateService.java`.

---

## Nhóm 4 — Tích hợp Telegram & VNPay (4 câu)

### Q13. Vì sao chọn Telegram Live Location thay vì tự GPS streaming?

**Trả lời gợi ý:**
"Tự build GPS streaming phía client đòi hỏi rất nhiều thứ: xin permission location, foreground service trên Android (notification thường xuyên), background sync khi Wifi/4G chập chờn, battery optimization, handle lost-signal, retry queue. Ước lượng riêng phần này 2–3 tuần effort. Telegram Live Location đã giải quyết hết — shipper bấm 📎 → Vị trí → Chia sẻ trực tiếp, Telegram tự handle hết, gửi `edited_message.location` đến webhook bot mỗi 5–10s. Em chỉ cần code `LiveLocationHandler` parse `lat/lng/accuracy/heading`, lưu `location_ping`, phát event. Tiết kiệm ~80% effort. Lợi ích phụ: *bảo mật cao* vì Telegram đã sign HMAC, kẻ tấn công không thể giả vị trí; *tiết kiệm pin* vì Telegram đã chạy nền, không có process mới. Trade-off duy nhất: giới hạn 8h/lần share (8h là quá đủ cho giao nội thành)."

**Tham chiếu:**
- Chương 1 §1.7.2 + Chương 4 §4.4 — Module delivery.
- `diem-noi-bat-va-huong-phat-trien.md` mục A.2.
- Code: `backend/bot/src/main/java/.../handler/LiveLocationHandler.java`.

---

### Q14. VNPay sandbox khác production thế nào? Đã test thật chưa?

**Trả lời gợi ý:**
"Sandbox (`sandbox.vnpayment.vn`) và production (`vnpayment.vn`) khác về: (1) URL endpoint create payment và IPN callback; (2) credential — sandbox dùng `vnp_TmnCode` test riêng, production cần đăng ký doanh nghiệp; (3) ngân hàng test — sandbox chỉ chấp nhận card NCB test cố định `9704198526191432198`, production accept card thật. *Code thì identical* — em dùng cùng `VnpaySignatureService.sign/verify`, chỉ thay URL và secret qua env var. Em đã test sandbox với card NCB end-to-end (chi tiết ở RUNBOOK §P7). Production chưa test vì cần credential thật + domain HTTPS để VNPay whitelist IPN URL. Hạn chế #4 trong báo cáo Chương 5. Phương án xử lý: integrate Momo/ZaloPay có sandbox dễ tiếp cận hơn, hoặc đăng ký VNPay business sandbox qua `sandbox.vnpayment.vn/devreg` (vài ngày)."

**Tham chiếu:**
- Chương 4 §4.5 + RUNBOOK §P7.
- `.env.example` — biến `VNPAY_*`.
- Code: `backend/payment/src/main/resources/application.yml`.

---

### Q15. Bot polling vs webhook — vì sao chọn polling cho dev?

**Trả lời gợi ý:**
"Polling: bot tự gọi Telegram API mỗi 50s để fetch update. Webhook: Telegram push update vào HTTPS endpoint của backend. Trade-off: webhook nhanh hơn (~0s vs ~25s avg) và tiết kiệm 90% network egress, nhưng *yêu cầu HTTPS public + domain công khai* — local dev khó. Polling hoạt động sau NAT, sau corporate firewall, không cần config gì. Em chọn polling cho dev vì đơn giản hoá môi trường demo — sinh viên test ở localhost không cần ngrok hay tunnel. *Production thì nên dùng webhook* — config `BOT_MODE=webhook` + Let's Encrypt cert + `setWebhook` Telegram API. Library `telegrambots-springboot-longpolling-starter` 7.x đã hỗ trợ cả 2 mode, switch chỉ qua config."

**Tham chiếu:**
- Chương 4 §4.6 — Module bot.
- Chương 5 §5.3.3 — Production hardening.
- RUNBOOK pre-flight.

---

### Q16. callback_data 64-byte limit — cách design payload?

**Trả lời gợi ý:**
"Telegram giới hạn `callback_data` 1–64 byte cho mỗi inline keyboard button. Em design payload dạng tiền tố ngắn + ID + tham số, phân cách bằng `:`. Ví dụ: `RATE:<orderUuid>:<stars>` (RATE = 4 + UUID 36 + : + 1 = 43 byte → vừa), `ASSIGN:<offerUuid>:ACCEPT` (ASSIGN = 6 + UUID 36 + : + 6 = 50 byte), `VEHICLE:MOTORBIKE` (cho FSM đăng ký shipper). Phía backend, `CallbackHandlerDispatcher` parse prefix bằng `String.startsWith` rồi route đến handler tương ứng. Nếu payload quá dài (ví dụ orderCode `DEMO-2026-XXXX` đi với rating), em dùng UUID (compact hơn) hoặc lưu state vào `conversation_state` rồi callback chỉ chứa session ID. Pattern này tránh chế biến URL encoding/JSON trong callback_data — vừa over limit vừa khó parse."

**Tham chiếu:**
- Chương 4 §4.6.3 — Bot handler routing.
- Code: `backend/bot/src/main/java/.../handler/RatingCallbackHandler.java`.
- Telegram Bot API docs § InlineKeyboardButton.

---

## Nhóm 5 — Testing & Process (4 câu)

### Q17. 274 test — phân bổ unit vs IT? Coverage là bao nhiêu?

**Trả lời gợi ý:**
"253 backend + 21 frontend = 274 tổng. Backend split: khoảng 180 unit (Mockito mock dependencies, JUnit 5 + AssertJ) chạy qua Surefire; ~73 integration test chạy qua Failsafe với Testcontainers (`postgres:16-alpine` thật trong Docker), test full Spring context + DB + Flyway migration. Frontend: 9 test cho `@shop/shared` (format helpers), 6 cho `@shop/miniapp` (Zustand `useCart`), 6 cho `@shop/webadmin` (`auth-store` + `OrderStatusBadge`). *Coverage*: em chưa chạy JaCoCo formally nên không có số chính xác, nhưng các module critical (`payment`, `order`, `auth`) có ≥ 85% line coverage qua estimate. Nếu thầy/cô yêu cầu, em có thể chạy `mvn jacoco:report` ngay sau buổi bảo vệ và gửi báo cáo. Hướng phát triển 5.3.1 có mở rộng frontend test lên 40+ với MSW mock REST."

**Tham chiếu:**
- Chương 4 §4.10 — Chiến lược kiểm thử.
- Chương 5 §5.1.5 — Bảng 5.2.
- `backend/pom.xml` — Failsafe + Surefire config.

---

### Q18. GSD methodology — khác gì Agile/Scrum?

**Trả lời gợi ý:**
"GSD (Get Shit Done) là quy trình *cá nhân* cho dự án solo, không phải framework team như Scrum. Cốt lõi: mỗi *phase* (đại loại tương đương 1 sprint) trải qua 6 bước tuần tự — Research → Plan → Plan-check → Execute → Code-review → Fix. Khác Scrum: (1) không có daily standup, sprint review, retrospective; (2) artefact là *document* (research.md, plan.md, REVIEW.md) chứ không phải user story trong Jira; (3) *plan-check* là một bước riêng — AI/đồng nghiệp review plan *trước khi gõ code* — em catch 12 blocker ở khâu này. Khác Waterfall: (1) chia thành 10 phase nhỏ (P0–P9) thay vì 1 cycle dài; (2) sau mỗi phase có code review + fix, build green; (3) cho phép adjust scope theo phase. Phù hợp solo dev hoặc team nhỏ 1–3 người."

**Tham chiếu:**
- Chương 1 §1.5 — Phương pháp tiếp cận.
- `diem-noi-bat-va-huong-phat-trien.md` mục A.7.
- Thư mục `docs/superpowers/` — plans + research + reviews.

---

### Q19. Plan-checker catch 12 blocker — ví dụ 1 blocker cụ thể?

**Trả lời gợi ý:**
"Ví dụ blocker ở P5 (Live Location). Plan ban đầu của em định broadcast vị trí qua topic STOMP `/topic/orders/{orderId}/location` để mọi subscriber nhận được. Plan-checker phát hiện vấn đề: *topic public — kẻ tấn công biết orderId là UUID có thể subscribe topic của đơn người khác*. Em chuyển sang dùng *user destination* `/user/{customerId}/queue/order/{orderId}/location` — Spring tự route đến đúng principal đã auth, không broadcast cho ai khác. Nếu không catch ở plan-check, em đã gõ code sai, test pass, rồi đến code review mới phát hiện — phải refactor topic + sửa frontend subscribe code + viết lại 5–6 test. Tiết kiệm khoảng 4–6 giờ debug và refactor. Bài học: plan-check rẻ hơn debug hậu kỳ. Một ví dụ khác: P7 plan-check catch việc `Propagation.REQUIRES_NEW` cần thiết ở `OrderService.confirmAfterPayment` để tránh phantom commit."

**Tham chiếu:**
- Plan P5: `docs/superpowers/plans/p5-*.md` → CHECK.md.
- Plan P7: `docs/superpowers/plans/p7-*.md` → CHECK.md.
- Chương 4 §4.4 / §4.5.

---

### Q20. Code review bao nhiêu lần? Catch bug gì nghiêm trọng nhất?

**Trả lời gợi ý:**
"Mỗi phase 1 review formal qua `gsd-code-review` → tổng 9 REVIEW.md ở `docs/superpowers/reviews/`. Mỗi report phân 3 mức: CRITICAL / IMPORTANT / MINOR. *5 critical bug* đã catch trước merge — bug nghiêm trọng nhất là **CR-4 IPN audit gap (P7)**: khi VNPay gửi IPN với chữ ký không hợp lệ (do attacker spoof hoặc bug), code ban đầu return 400 Bad Request và *không persist gì*. Vấn đề: không có audit trail để forensics — không biết IP nào attempt spoof, payload gì, bao nhiêu lần. Fix: mọi IPN (kể cả invalid signature) lưu vào `payment_transaction.raw_payload` JSONB; chỉ những IPN valid mới apply business logic. Regression test `VnpayIpnAuditIT` verify cả case invalid signature vẫn được persist. 4 critical còn lại: CR-1 STOMP authorization gap, CR-2 wrong-shipper attribution, CR-3 NPE channel message null `from`, CR-5 ShipperState enum mismatch Java vs Postgres CHECK."

**Tham chiếu:**
- Chương 5 §5.1.3 — Quy trình phát triển.
- `docs/superpowers/reviews/p7-review.md` CR-4.
- `diem-noi-bat-va-huong-phat-trien.md` mục A.7.

---

## Nhóm 6 — Câu hỏi hard ball (4 câu) + bonus

### Q21. Hệ thống chỉ là demo hay deploy được production? Bao giờ go-live?

**Trả lời gợi ý:**
"Hệ thống *technically deploy-able* — chỉ 3 lệnh `git clone`, `cp .env.example .env` (điền secret), `docker compose up -d`. Đã có application-prod.yml fail-fast cho secret, healthcheck cho mọi container, nginx reverse proxy ready. *Tuy nhiên chưa go-live thật vì missing 4 thứ production-grade*: (1) HTTPS thật (cần Let's Encrypt + domain); (2) VNPay production credential (cần đăng ký doanh nghiệp); (3) backup strategy (pg_dump cron + S3); (4) observability (Prometheus + Grafana + Loki). Em estimate effort để go-live: 1–2 tuần cho 4 hạng mục trên + 1 tuần penetration test bên thứ ba. Sau đó có thể serve traffic thật. Hệ thống không phải toy demo — nó là MVP có khả năng go-live, chỉ cần thêm 2–3 tuần production hardening (chi tiết Chương 5 §5.3.3 Bảng 5.4)."

**Tham chiếu:**
- Chương 4 §4.11 — Docker Compose deployment.
- Chương 5 §5.3.3 Bảng 5.4 — Production hardening.
- `diem-noi-bat-va-huong-phat-trien.md` mục A.8.

---

### Q22. Multi-tenant chưa làm — tại sao quan trọng và bao lâu để thêm?

**Trả lời gợi ý:**
"Multi-tenant tức là chuyển từ '1 shop, 1 backend instance' sang 'N shop trên cùng backend' — SaaS model. Quan trọng vì: (1) một instance phục vụ nhiều shop, chia chi phí VPS 10 USD/tháng cho 10–50 shop → mỗi shop chỉ tốn 1 USD/tháng; (2) chủ shop không cần kiến thức Docker — em host, họ chỉ login Web Admin. Em chưa làm vì đây là *out-of-scope cố ý* — phạm vi khoá luận gốc là '1 shop' (Won't have #14 MoSCoW). Effort thêm: ~3–4 tuần — (1) Flyway migration thêm bảng `shop` + cột `shop_id FK` ở mọi bảng nghiệp vụ (~10 bảng); (2) row-level security trên Postgres với `CURRENT_SETTING('app.current_shop_id')`; (3) sửa mọi query `WHERE shop_id = ?`; (4) auth thêm shop scope vào JWT claim; (5) UI Web Admin thêm shop switcher cho super admin. *Alternative*: schema-per-tenant — mỗi shop 1 schema riêng, isolation cao hơn nhưng migration phức tạp."

**Tham chiếu:**
- Chương 1 §1.3 — Phạm vi MoSCoW W14.
- Chương 5 §5.2.2 hạn chế #1 + §5.3.2.3 mở rộng multi-tenant.
- Postgres RLS docs.

---

### Q23. Hạn chế lớn nhất của đề tài là gì? (Be honest!)

**Trả lời gợi ý:**
"Hạn chế lớn nhất theo em là *manual smoke test chưa tự động hoá end-to-end với Telegram thật* (#8). Mặc dù 274 test unit + IT đảm bảo từng component đúng và Testcontainers test integration với Postgres thật, nhưng *toàn bộ flow Bot → Mini App → Backend → VNPay vẫn cần test thủ công* qua RUNBOOK 8 bước. Hệ luỵ: (1) regression ở Telegram-specific behavior khó catch (ví dụ payload edited_message thay đổi format); (2) demo bảo vệ phải dựa vào người vận hành đúng các bước; (3) chưa thể CI/CD full automation. Phương án: dùng `telethon` (Python Telegram client lib) để giả lập user gửi update; dùng Playwright để drive Mini App headless; chạy mỗi nightly. Effort estimate 1–2 tuần. Đây là first priority nếu em có thêm 1 tháng. Em thừa nhận đây là gap thật chứ không phải excuse — và đã document ở Chương 5 §5.2.4 hạn chế #8."

**Tham chiếu:**
- Chương 5 §5.2.4 hạn chế #8.
- Chương 5 §5.3.3 — Production hardening row "Manual smoke".
- RUNBOOK §P1–P8 (manual).

---

### Q24. Nếu phải làm lại, bạn sẽ làm khác gì?

**Trả lời gợi ý:**
"Ba thứ. *Một*: setup frontend test ngay từ P0 — em đợi đến giai đoạn hoàn thiện mới thêm Vitest, dẫn đến 9 phase đầu code FE không có safety net, refactor mất an toàn. Đáng lẽ Vitest + Testing Library scaffold cho 3 workspace pnpm trong tuần đầu. *Hai*: đo coverage JaCoCo từ đầu thay vì estimate qua cảm tính — biết module nào coverage thấp để target test trước. *Ba*: thiết kế abstraction `PaymentGateway` ngay từ P7 thay vì hard-code VNPay — bây giờ thêm Momo/ZaloPay vẫn cần refactor `PaymentService`. Nếu có abstraction sớm thì mỗi cổng thanh toán mới chỉ 3 ngày implementation. Bài học chung: *invest vào infrastructure (test, abstraction, observability) sớm* — chi phí lớn ban đầu nhưng giảm ma sát của 80% effort còn lại. Đây không phải hối tiếc lớn — em vẫn deliver đúng phạm vi và 100% Must/Should — nhưng nếu làm lần 2 sẽ nhanh hơn 20–30%."

**Tham chiếu:**
- Chương 5 §5.1.3 — Bài học từ quy trình.
- Chương 5 §5.3.2.2 — PaymentGateway abstraction.
- Hạn chế #7 (đã khắc phục V12) — frontend test setup muộn.

---

### Q25 (bonus). Cost-to-deploy ở scale 100 đơn/ngày? 1000 đơn/ngày?

**Trả lời gợi ý:**
"*100 đơn/ngày* (~3 đơn/giờ peak): 1 VPS 2 vCPU + 2 GB RAM (DigitalOcean Basic 12 USD/tháng) đủ chạy 5 container Docker. Postgres data volume ~1 GB/tháng (tăng do location_ping ~20 row/đơn). Bandwidth ~5 GB/tháng (Telegram polling chiếm chính). Tổng *~15 USD/tháng* — phù hợp shop nhỏ. *1000 đơn/ngày* (~30 đơn/giờ peak): cần 2 vCPU + 4 GB RAM (DigitalOcean General Purpose 24 USD/tháng). Bottleneck đầu tiên là *Postgres write IO* cho `location_ping` (~6000 row/giờ peak) — solution: partition table theo created_at hoặc move sang TimescaleDB. Bot polling thay bằng webhook để giảm 90% egress. Có thể cần Redis cache cho `RoleResolver`. Tổng *~50 USD/tháng* — vẫn cạnh tranh hơn nhiều so với 20% hoa hồng GrabFood ($200+/tháng cho cùng volume)."

**Tham chiếu:**
- Chương 4 §4.11 — Docker Compose footprint.
- Chương 5 §5.3.3 — Production hardening.
- `diem-noi-bat-va-huong-phat-trien.md` phần C bảng so sánh.

---

### Q26 (bonus). Đối thủ trực tiếp ở thị trường VN — bạn cạnh tranh thế nào?

**Trả lời gợi ý:**
"*Không cạnh tranh trực tiếp với GrabFood/ShopeeFood* — họ là aggregator B2C platform, target khách hàng cuối với app riêng. Đề tài là *B2B / vertical software* — bán cho shop nhỏ làm tool tự quản lý. Đối thủ thực sự: (1) *Sapo / KiotViet* — phần mềm quản lý bán hàng có module giao hàng cơ bản, nhưng UX kém và không tích hợp Telegram tracking realtime; (2) *Haravan + addon Ahamove* — outsource giao hàng cho 3rd party, phải chia chi phí; (3) *Pancake POS* — focus điểm bán hàng F&B, ít focus giao hàng. Điểm khác biệt em đem lại: (a) realtime tracking *miễn phí* qua Telegram thay vì trả Google Maps API; (b) khách không cần cài app; (c) self-host hoàn toàn → tự chủ dữ liệu; (d) open source (sẽ public sau bảo vệ) — shop có dev nội bộ tự customize được. Target market: shop F&B + tạp hoá 50–500 đơn/ngày tại đô thị VN — segment GrabFood overpriced và Sapo/KiotViet under-featured."

**Tham chiếu:**
- Chương 1 §1.1 — So sánh với giải pháp hiện có.
- `diem-noi-bat-va-huong-phat-trien.md` phần C.
- Định vị target market: shop F&B/tạp hoá 1–10 shipper.

---

### Q27 (bonus). Bài học lớn nhất rút ra từ đề tài?

**Trả lời gợi ý:**
"Bài học lớn nhất: *đầu tư vào quy trình bằng đầu tư vào code*. Lúc bắt đầu em nghĩ 'plan dài 4700 dòng cho 1 phase là quá thừa, gõ code nhanh hơn'. Nhưng sau khi plan-check catch 12 blocker và code review catch 5 critical bug, em nhận ra mỗi giờ bỏ vào plan + review tiết kiệm khoảng 3–5 giờ debug + refactor. Đặc biệt với dev solo, quy trình GSD đóng vai trò 'second pair of eyes' mà em không có team. Bài học thứ hai: *defense-in-depth là multiplier*, không phải single point. 11 lớp bảo mật mỗi lớp đơn giản (HMAC, JWT, `@PreAuthorize`, ...), nhưng cộng lại làm hệ thống cực khó break. Bài học thứ ba: *thừa nhận hạn chế là điểm cộng* — em liệt kê 10 hạn chế trong báo cáo (4 đã khắc phục, 6 còn lại), điều này chứng tỏ em hiểu hệ thống chứ không phóng đại. Trong nghề kỹ sư, không ai tin người nói 'sản phẩm của tôi không có lỗi'."

**Tham chiếu:**
- Chương 1 §1.5 — Phương pháp tiếp cận.
- Chương 5 §5.1.3 — Hiệu quả quy trình GSD.
- Chương 5 §5.2 — Hạn chế honestly liệt kê.

---

## Phụ lục — Câu hỏi không thuộc 6 nhóm nhưng có thể bị hỏi

| Câu | Trả lời ngắn |
|---|---|
| "Vì sao Postgres thay vì MySQL?" | JSONB native cho FSM payload (`conversation_state.data`) và VNPay audit (`payment_transaction.raw_payload`), better full-text search, idiomatic với Hibernate 6 `@JdbcTypeCode`. |
| "Vì sao STOMP thay vì raw WebSocket?" | Pub/sub semantics + SockJS fallback cho corporate proxy + Spring first-class support + per-principal user destinations (`/user/{id}/queue/...`). |
| "Vì sao Zustand thay vì Redux?" | Boilerplate ít hơn (no provider, no action constants), TypeScript inferring tốt, persist middleware built-in, đủ phức tạp cho 1 shop cart store. |
| "Vì sao Tailwind thay vì Material UI?" | Bundle nhỏ hơn (~10KB vs ~200KB), tự control design system, phù hợp brand cam-đỏ custom của shop. |
| "Vì sao Maven multi-module thay vì Gradle?" | Familiarity, Spring Boot starter ecosystem tốt nhất với Maven, multi-module Maven cấu hình XML rõ ràng hơn Gradle Kotlin DSL cho project size này. |
| "Có dùng AI để code không?" | Có dùng Claude Code làm trợ lý — em viết plan, plan-check, code review prompts và AI thực thi theo plan đã được em verify. Em vẫn là người chịu trách nhiệm cuối về design decision và test verification. |
| "Bao nhiêu tiếng làm khoá luận?" | Khoảng 3–4 tháng × 20 giờ/tuần = ~240–320 giờ effective work (chưa kể research và đọc docs). |
| "Có deploy cloud thật chưa?" | Đã test deploy DigitalOcean droplet Basic 12 USD ở pha P9 — stack chạy ~3 phút build, ~200 MB RAM idle. Chưa go-live thật vì missing HTTPS + domain (xem Q21). |

---

## Phụ lục — Anti-pattern cần tránh khi trả lời

1. **Không nói "em không biết"** — thay bằng "em chưa làm/đo cụ thể, nếu mở rộng sẽ làm [X]".
2. **Không bịa số liệu** — nếu hỏi coverage mà chưa chạy JaCoCo, nói thẳng "em chưa run formal, estimate khoảng ≥ 85% cho module critical, có thể chạy ngay sau buổi bảo vệ".
3. **Không phòng thủ trước câu hỏi hard ball (Q23–Q24)** — hội đồng kiểm tra sự honest, không phải perfection. Liệt kê hạn chế là điểm cộng.
4. **Không trả lời lan man > 90 giây** — nếu thấy lan man, dừng lại và hỏi "thầy/cô có muốn em đi sâu phần nào không?".
5. **Không phủ nhận feedback của hội đồng** — nếu thầy/cô góp ý, ghi nhận và trả lời "em sẽ ghi nhận và nâng cấp ở phiên bản tiếp theo", không tranh cãi gay gắt.
6. **Không quên cảm ơn** sau mỗi câu trả lời dài.

---

## Phụ lục — Mapping câu hỏi ↔ chương báo cáo

| Câu | Chương / mục chính |
|---|---|
| Q1, Q2, Q3 | Chương 3 §3.2 (Kiến trúc) |
| Q4 | Chương 1 §1.7.1 (Mô hình lai) |
| Q5, Q10, Q11, Q12 | Chương 3 §3.3 (Database) |
| Q6, Q7, Q8, Q9 | Chương 3 §3.7 (Bảo mật defense-in-depth) |
| Q13 | Chương 1 §1.7.2 + Chương 4 §4.4 (Live Location) |
| Q14 | Chương 4 §4.5 (Module payment) |
| Q15, Q16 | Chương 4 §4.6 (Module bot) |
| Q17 | Chương 4 §4.10 + Chương 5 §5.1.5 |
| Q18, Q19, Q20 | Chương 1 §1.5 + Chương 5 §5.1.3 (Quy trình GSD) |
| Q21, Q22, Q23, Q24 | Chương 5 §5.2 + §5.3 (Hạn chế + hướng phát triển) |
| Q25, Q26, Q27 | `diem-noi-bat-va-huong-phat-trien.md` phần C + Chương 5 §5.3 |
