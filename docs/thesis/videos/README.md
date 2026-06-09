# Demo videos (Phase 14 + Phase 15)

Bốn video `.webm` ghi lại flow end-to-end của hệ thống — phục vụ buổi bảo vệ và đính kèm báo cáo. Tất cả được tự động hoá qua Playwright, mỗi video là một context browser độc lập với chế độ ghi hình native (`record_video_dir`).

## Thứ tự xem

| # | File | Thời lượng | Vai trò | Nội dung |
|---|---|---|---|---|
| 1 | `01-admin-create-voucher.webm` | ~30s | Admin (1440×900) | Login → sidebar "Khuyến mãi" → tạo voucher `DEMO50K` (giảm 50 000đ, đơn tối thiểu 100 000đ) |
| 2 | `02-customer-checkout-voucher.webm` | ~45s | Khách (390×844 mobile) | Catalog → thêm 5 món vào giỏ → Checkout → áp `GIAM20K` (PRODUCTS) + `FREESHIP` (SHIPPING) → xem breakdown giảm giá |
| 3 | `03-shipper-deliver-and-wallet.webm` | ~40s | Shipper (390×844 mobile) | Earnings → Đơn → chọn assignment ACCEPTED → "🚀 Bắt đầu giao" → "✅ Đã giao xong" → quay lại Earnings (KPI tăng) → Ví (lịch sử ledger có entry mới COMMISSION + COD_OWED) → Profile |
| 4 | `04-admin-verify-and-settle.webm` | ~60s | Admin (1440×900) | Sidebar "Thu nhập" (biểu đồ updated) → "Shipper" (cột "Số dư ví" có giá trị mới) → ShipperDetail → modal "Đã nhận tiền nộp" 50 000đ → "Khuyến mãi" (DEMO50K hiện ra) |

## Tổng thời lượng demo

~3 phút (175 giây) cho 4 video.

## Đặc điểm kỹ thuật

- **Format**: WebM (VP8 codec) — chuẩn Playwright, play được trên Chrome/Firefox/Edge/Safari 14+
- **Frame rate**: ~25 fps (mặc định Playwright)
- **Resolution**: 1440×900 (admin), 390×844 (mobile @1× — Playwright record không lưu device_scale_factor)
- **Pacing**: `slow_mo=500ms` giữa các action — đủ để người xem theo dõi từng click; thêm `wait_for_timeout` ở điểm cần đọc
- **Telegram WebApp shim**: mock `window.Telegram.WebApp.initData=''` + `?devUserId=` URL param → backend `X-Dev-User-Id` bypass (chỉ profile `dev`)

## Cách render lại

Nếu cần re-record sau khi UI thay đổi:

```bash
# Đảm bảo 3 service đang chạy (port 8080, 5180, 5181)
lsof -iTCP:8080,5180,5181 -sTCP:LISTEN -P

# Đảm bảo DB có ACCEPTED assignment cho shipper 9000000101:
docker exec shop_delivery_postgres_dev psql -U app -d shop_delivery -c "
SELECT id, status FROM delivery_assignment WHERE shipper_id=9000000101 AND status='ACCEPTED' LIMIT 1;"
# Nếu không có, complete bất kỳ STARTED assignment trước:
curl -s -X POST 'http://localhost:8080/api/shipper/assignments/<id>/complete' -H 'X-Dev-User-Id: 9000000101'

# Chạy script ghi (lưu output vào /tmp/demo_videos rồi copy)
/Users/lethitranthuy/.pyenv/versions/3.10.6/bin/python3 /tmp/record_demo.py
cp /tmp/demo_videos/0*.webm docs/thesis/videos/
```

## Convert sang MP4 (nếu cần)

Khi cần share qua kênh không hỗ trợ WebM (vd: PowerPoint cũ, Zalo, Whatsapp):

```bash
# Cài ffmpeg nếu chưa có
brew install ffmpeg

# Convert
for f in docs/thesis/videos/*.webm; do
    ffmpeg -i "$f" -c:v libx264 -crf 23 -preset medium -y "${f%.webm}.mp4"
done
```

MP4 sẽ lớn hơn WebM ~1.5-2× (vẫn dưới 10 MB mỗi video).

## Liên kết với báo cáo

- Hướng dẫn chi tiết flow → `docs/thesis/huong-dan-su-dung.md` mục 5 (Sample scenario)
- Tính năng được demo → `docs/thesis/chuong-6-mo-rong.md` (Voucher + Shipper Commission)
- Screenshots tĩnh → `docs/thesis/screenshots/` (Hình 6.1 → 6.11)
