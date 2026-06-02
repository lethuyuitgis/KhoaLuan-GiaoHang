# Script video demo — Khoá luận tốt nghiệp 2026

> **Tổng thời lượng:** 4 phút (240 giây).
> **Voiceover:** tiếng Việt formal, tốc độ ~150 từ/phút → tổng khoảng 600 từ.
> **Định dạng quay:** màn hình laptop 1080p 30fps (QuickTime trên macOS hoặc OBS Studio); inset webcam góc dưới phải tuỳ chọn.
> **Ngôn ngữ visual:** mọi popup/modal/inline keyboard phải hiển thị rõ trên màn hình.
> **Thiết bị cần chuẩn bị:**
>   - 1 laptop chạy Docker Compose stack ở `(healthy)`.
>   - 1 điện thoại Phone A (vai khách) đã `/start` bot.
>   - 1 điện thoại Phone B (vai shipper) đã được admin duyệt.
>   - Trình duyệt Chrome đăng nhập sẵn Web Admin ở 1 cửa sổ riêng.

---

## Ghi chú quan trọng trước khi quay

1. **Cần 2 tài khoản Telegram.** Vai khách và vai shipper phải là 2 tài khoản khác nhau. Có thể nhờ bạn bè dùng tài khoản Telegram thứ 2 đóng vai shipper, hoặc dùng Telegram Web trên laptop cho 1 vai.
2. **Có thể chia 2 clip mỗi clip 2 phút thay vì 1 clip 4 phút** — tránh phải canh đồng bộ giữa 2 điện thoại liên tục. Clip 1: 00:00–02:00 (intro → khách đặt đơn → admin gán). Clip 2: 02:00–04:00 (shipper share Live Location → khách theo dõi → đánh giá → outro).
3. **Nếu khó canh đồng bộ giữa 2 điện thoại** — có thể pre-record từng segment riêng (Phone A và Phone B) rồi ghép trong Premiere/Final Cut/iMovie thành split-screen.
4. **Voiceover** có thể thu sau khi quay xong screen — dùng GarageBand hoặc Audacity. Hoặc nói lên trong khi quay, sau đó cleanup tiếng ồn bằng noise reduction filter.

---

## Tổng quan bảng thời gian

| Đoạn | Thời gian | Thời lượng | Nội dung |
|---|---|---|---|
| 1 | 00:00 – 00:20 | 20s | Intro: logo + tên đề tài |
| 2 | 00:20 – 00:40 | 20s | Vấn đề & giải pháp |
| 3 | 00:40 – 01:30 | 50s | Mini App khách: catalog → cart → checkout → đặt thành công |
| 4 | 01:30 – 02:00 | 30s | Web Admin: dashboard → orders → gán shipper |
| 5 | 02:00 – 02:40 | 40s | Bot shipper: nhận offer → bắt đầu giao → share Live Location |
| 6 | 02:40 – 03:20 | 40s | Khách theo dõi: bản đồ realtime |
| 7 | 03:20 – 03:40 | 20s | Đánh giá: khách rate 5 sao + comment |
| 8 | 03:40 – 04:00 | 20s | Outro: số liệu + cảm ơn |
| **Tổng** | | **240s** | |

---

## Scene 1 — Intro (00:00 – 00:20)

**Visual:**
- 00:00–00:05: Logo Shop Giao Hàng (icon xe máy + paper plane Telegram) zoom-in fade-in trên background gradient cam–đỏ.
- 00:05–00:15: Text title appears `Xây dựng hệ thống quản lý giao hàng dựa trên nền tảng Telegram` font lớn, dưới có dòng `Khoá luận tốt nghiệp 2026 — [Họ tên] — MSSV [...]`.
- 00:15–00:20: Cross-fade sang khung hình chia 3 (split-screen): 1/3 trái là Mini App catalog, 1/3 giữa là Web Admin dashboard, 1/3 phải là bot Telegram chat.

**Voiceover (≈ 50 từ, 20s):**
> "Kính chào quý thầy cô và quý vị. Đây là demo hệ thống quản lý giao hàng dựa trên nền tảng Telegram — khoá luận tốt nghiệp năm 2026 của em [Họ tên]. Hệ thống tích hợp ba kênh trên Telegram: Mini App cho khách, Bot cho shipper, và Web Admin cho chủ shop. Mời quý vị theo dõi."

---

## Scene 2 — Vấn đề & giải pháp (00:20 – 00:40)

**Visual:**
- 00:20–00:30: Slide tĩnh "Vấn đề" — 3 icon (GrabFood 20–25% hoa hồng / App native chi phí cao / Vận hành thủ công Messenger) đều có dấu X đỏ.
- 00:30–00:40: Slide "Giải pháp" — logo Telegram lớn + dòng "1 tỷ MAU + Bot API miễn phí + Mini App + Live Location native" + dòng "Khách không cần cài app mới".

**Voiceover (≈ 50 từ, 20s):**
> "Shop nhỏ tại Việt Nam đang đối mặt ba lựa chọn không hoàn hảo: chịu hoa hồng cao với GrabFood, tự build app native tốn kém, hoặc vận hành thủ công dễ thất lạc đơn. Đề tài đề xuất tận dụng Telegram làm cổng vào duy nhất — khách hàng không cần cài thêm bất kỳ ứng dụng nào."

---

## Scene 3 — Mini App khách hàng (00:40 – 01:30)

**Visual:**
- 00:40–00:50: Mirror Phone A lên màn hình (QuickTime → File → New Movie Recording → chọn iPhone). Hiển thị app Telegram đang mở bot `@<BOT_USERNAME>`.
- 00:50–00:55: Tap nút "Đặt hàng" trong inline keyboard → Mini App load trong Telegram WebView, hiện catalog 10 sản phẩm.
- 00:55–01:05: Scroll catalog, tap "Phở bò tái" → modal sản phẩm → tap "Thêm vào giỏ" → badge giỏ hàng tăng từ 0 → 1. Tap thêm "Cà phê đen" → badge thành 2.
- 01:05–01:15: Tap icon giỏ hàng → trang Cart hiện 2 món + tổng tiền + nút "Thanh toán".
- 01:15–01:25: Tap "Thanh toán" → trang Checkout hiện form pre-filled tên + số điện thoại (từ Telegram profile). Cho phép Telegram quyền vị trí → drop pin gần Hoàn Kiếm (~21.028, 105.854). Chọn payment method `COD`.
- 01:25–01:30: Tap "Đặt hàng" → màn hình success xanh + mã đơn `DEMO-2026-XXXX`.

**Voiceover (≈ 125 từ, 50s):**
> "Khách hàng bấm vào nút Đặt hàng trong bot Telegram — Mini App được mở trực tiếp bên trong cửa sổ chat mà không cần cài thêm app. Mini App hiển thị danh mục mười sản phẩm demo. Khách tap chọn món, thêm vào giỏ hàng — giỏ được persist bằng Zustand vào localStorage nên không mất khi reload. Khách mở giỏ, bấm Thanh toán, form được pre-fill tên và số điện thoại lấy từ profile Telegram. Khách cho phép quyền vị trí, ghim toạ độ giao trên bản đồ Leaflet, chọn phương thức tiền mặt khi nhận hàng, và bấm Đặt hàng. Hệ thống trả về mã đơn ngay lập tức."

---

## Scene 4 — Web Admin chủ shop (01:30 – 02:00)

**Visual:**
- 01:30–01:35: Cut sang cửa sổ Chrome đang mở Web Admin. Đã login sẵn (skip login screen để tiết kiệm thời gian).
- 01:35–01:45: Trang Dashboard hiện 4 KPI card + Top 3 shipper. Highlight chỉ số "Đơn hôm nay" vừa tăng +1 (hiệu ứng badge xanh "NEW").
- 01:45–01:55: Click tab "Đơn hàng" → orders list xuất hiện. Đơn mới `DEMO-2026-XXXX` đứng đầu list (highlight bằng row màu vàng nhạt cho biết "vừa mới"). Click vào đơn → trang Order Detail mở, hiện thông tin khách + 2 món + tổng tiền + status timeline.
- 01:55–02:00: Click nút "Phân công shipper" → modal hiện danh sách shipper AVAILABLE. Chọn `Nguyễn Văn B` → click Confirm → status đơn flip từ `CONFIRMED` sang `ASSIGNED`.

**Voiceover (≈ 75 từ, 30s):**
> "Ngay khi khách đặt đơn, Web Admin của chủ shop nhận tín hiệu WebSocket. Đơn mới hiện trên đầu danh sách trong khoảng hai giây mà không cần refresh. Chủ shop mở đơn, thấy đầy đủ thông tin khách hàng, danh sách món, tổng tiền và timeline trạng thái. Chủ shop bấm Phân công shipper, chọn một shipper đang AVAILABLE từ danh sách, và xác nhận. Đơn chuyển sang trạng thái ASSIGNED."

---

## Scene 5 — Bot shipper (02:00 – 02:40)

**Visual:**
- 02:00–02:10: Cut sang mirror Phone B (vai shipper). App Telegram nhận tin nhắn mới từ bot: "🆕 Có đơn mới #DEMO-2026-XXXX. Phở bò tái + Cà phê đen. Tổng 75 000 VNĐ. [Nhận] / [Từ chối]". (Lưu ý: ghi note vào hậu trường rằng đây là tài khoản Telegram thứ 2 — có thể nhờ bạn dùng tài khoản phụ.)
- 02:10–02:15: Shipper tap "Nhận" → bot edit message thành "✅ Đã nhận đơn. Mở Mini App để xem chi tiết." + nút Mini App.
- 02:15–02:25: Tap nút Mini App → trang Assignment Detail hiện địa chỉ pickup + delivery + nút "Bắt đầu giao". Tap "Bắt đầu giao" → status flip sang `STARTED`. Thoát Mini App quay về bot.
- 02:25–02:40: Trong bot chat, shipper tap biểu tượng 📎 (đính kèm) → "Vị trí" → "Chia sẻ vị trí trực tiếp" → chọn thời lượng "15 phút" → tap nút share. Bot phản hồi: "✅ Đã bắt đầu chia sẻ vị trí. Khách hàng có thể theo dõi trên bản đồ."

**Voiceover (≈ 100 từ, 40s):**
> "Shipper nhận thông báo offer ngay lập tức trên bot Telegram qua inline keyboard hai nút Nhận hoặc Từ chối. Shipper bấm Nhận, mở Mini App shipper để xem chi tiết đơn, bấm Bắt đầu giao để chuyển trạng thái sang đang giao. Bước quan trọng nhất: shipper bấm biểu tượng đính kèm trong bot, chọn Vị trí, chọn Chia sẻ vị trí trực tiếp, chọn thời lượng mười lăm phút và xác nhận. Telegram sẽ tự động phát vị trí GPS năm đến mười giây một lần, gửi đến webhook của backend mà không cần lập trình streaming phía client."

---

## Scene 6 — Khách theo dõi realtime (02:40 – 03:20)

**Visual:**
- 02:40–02:50: Cut về Phone A (vai khách). Mở lại Mini App → tab "Đơn của tôi" → tap vào đơn `DEMO-2026-XXXX` đang `STARTED`.
- 02:50–03:10: Trang Order Detail mở. Map Leaflet chiếm 60% màn hình. Hiện 2 marker: marker xanh là pickup point của shop, marker cam là vị trí shipper. Polyline nối các ping trước đó.
- 03:10–03:20: Trong khi quay, Phone B di chuyển 2–3 mét (hoặc dùng tính năng fake location của Telegram nếu test offline). Marker cam trên Phone A cập nhật vị trí theo thời gian thực, polyline được vẽ thêm. Hiển thị ETA "~ 5 phút" ở header. Highlight độ trễ "Cập nhật ~ 5–15s".

**Voiceover (≈ 100 từ, 40s):**
> "Quay lại Mini App của khách. Khách mở đơn đang được giao, trang Order Detail hiển thị bản đồ Leaflet với marker shipper đang di chuyển trong thời gian thực. Backend nhận vị trí từ Telegram qua webhook edited_message, lưu vào bảng location_ping, phát Spring event AFTER_COMMIT để đảm bảo chỉ broadcast khi đã commit thành công, rồi push qua kênh STOMP WebSocket dành riêng cho từng khách. Độ trễ end-to-end khoảng năm đến mười lăm giây — chủ yếu là chu kỳ phát vị trí của Telegram, không phải độ trễ hệ thống."

---

## Scene 7 — Đánh giá (03:20 – 03:40)

**Visual:**
- 03:20–03:25: Cut sang Phone B. Shipper bấm nút "Đã giao xong" trong Mini App shipper → status flip sang `DELIVERED`.
- 03:25–03:30: Cut sang Phone A. Bot tự động gửi tin: "✅ Đơn DEMO-2026-XXXX đã giao xong. Vui lòng đánh giá shipper." + inline keyboard 5 nút "⭐ 1" "⭐ 2" ... "⭐ 5" + nút "Bỏ qua".
- 03:30–03:35: Khách tap "⭐ 5" → bot edit message → ack "Cảm ơn bạn!" + prompt mới "Bạn có thể nhập nhận xét hoặc gõ /skip".
- 03:35–03:40: Khách gõ text "Shipper giao nhanh, thái độ tốt" → bot ack "Cảm ơn nhận xét của bạn!". FSM clear.

**Voiceover (≈ 50 từ, 20s):**
> "Khi shipper bấm Đã giao xong, bot tự động gửi khách hàng inline keyboard năm sao để đánh giá. Khách bấm năm sao, bot prompt nhập nhận xét tuỳ chọn. Khách gõ comment và bot xác nhận đã ghi. Rating được lưu vào bảng rating và shipper profile được recompute từ aggregate query để tránh trôi số."

---

## Scene 8 — Outro (03:40 – 04:00)

**Visual:**
- 03:40–03:50: Slide tĩnh "Số liệu cuối" — bảng 8 hàng:
  - 10 phase phát triển
  - ~160 commit atomic
  - 274 test (253 backend + 21 frontend)
  - 12 Flyway migration
  - 8 bounded context
  - ~35 REST endpoint
  - 11 lớp defense-in-depth
  - 100% build green trên main
- 03:50–04:00: Slide tĩnh "Cảm ơn quý thầy cô đã theo dõi" + URL `github.com/[username]/KhoaLuan-GiaoHang` + QR code. Fade-out.

**Voiceover (≈ 50 từ, 20s):**
> "Hệ thống được phát triển qua mười phase theo phương pháp GSD, hơn một trăm sáu mươi commit atomic, hai trăm bảy mươi tư test, mười hai Flyway migration, tám bounded context, và mười một lớp defense-in-depth. Em xin cảm ơn quý thầy cô đã theo dõi. Mời thầy cô xem chi tiết trong báo cáo khoá luận."

---

## Tổng word count voiceover

| Scene | Số từ |
|---|---|
| 1 | 50 |
| 2 | 50 |
| 3 | 125 |
| 4 | 75 |
| 5 | 100 |
| 6 | 100 |
| 7 | 50 |
| 8 | 50 |
| **Tổng** | **600** |

Tại tốc độ ~150 wpm chuẩn, 600 từ tương đương 240 giây = 4 phút.

---

## Checklist trước khi quay

- [ ] Docker stack `up -d` và 5 container đều `(healthy)`.
- [ ] Phone A đã `/start` bot, profile Telegram có tên + (tuỳ chọn) avatar.
- [ ] Phone B đã đăng ký shipper qua FSM và được admin duyệt (status ACTIVE).
- [ ] Web Admin đã login `shop@example.com` / `Demo@Shop2026!` sẵn trong tab Chrome.
- [ ] Catalog có ít nhất 10 sản phẩm (V11 seed đã chạy).
- [ ] Đã clear orders cũ liên quan đến Phone A để demo nhìn sạch sẽ (hoặc filter theo customer).
- [ ] QuickTime mở sẵn 2 New Movie Recording (cho Phone A và Phone B). Hoặc OBS scene đã setup.
- [ ] Microphone đã test (USB mic Yeti hoặc lavalier mic).
- [ ] Phòng yên tĩnh, đóng cửa sổ, tắt thông báo Telegram/Slack/email trên laptop.
- [ ] Pin laptop > 50% hoặc cắm sạc; 2 điện thoại > 70% pin.
- [ ] Đã test sẵn flow end-to-end 1 lần trước khi bấm record.

## Checklist sau khi quay

- [ ] Cắt bỏ các pause dài > 2 giây.
- [ ] Thêm nhạc nền nhẹ (volume -20dB) nếu có voiceover; bỏ nhạc nếu voiceover đã thu chất lượng.
- [ ] Thêm subtitle tiếng Việt (tự gen qua YouTube Studio hoặc Premiere → AI captions).
- [ ] Export MP4 H.264 1080p 30fps, bitrate ~8 Mbps → size khoảng 240 MB.
- [ ] Upload YouTube unlisted (hoặc Google Drive share link "anyone with link can view").
- [ ] Tạo QR code link demo video bằng `qrcode-monkey.com` hoặc CLI `qrencode`.
- [ ] Backup file MP4 vào USB mang theo ngày bảo vệ.
