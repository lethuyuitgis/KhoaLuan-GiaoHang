# Hướng dẫn biên dịch báo cáo khoá luận

> **Đề tài:** Hệ thống quản lý giao hàng tích hợp Telegram Mini App, Web Admin và VNPay
> **Nhóm sinh viên:** Lê Thị Trần Thủy (23C1001P3734, nhóm trưởng), Ngô Phúc Hiếu (23C1001P5290), Nguyễn Thế Thưởng (23C1001P4046)
> **Lớp:** CHDN420 — **Ngành:** Công nghệ thông tin (7480201)
> **GVHD:** ThS. Đinh Tuấn Long
> **Năm học:** 2025 – 2026 (Đợt I)

Thư mục này chứa bản nguồn Markdown của báo cáo. File `.docx` nộp hội đồng được sinh
tự động từ các file `.md` bằng Pandoc với `reference.docx` đã chỉnh theo Phụ lục 16.3
của Trường Đại học Mở Hà Nội.

## 1. Biên dịch

```bash
bash docs/thesis/build.sh
```

Script thực hiện: render các khối ```` ```mermaid ```` thành PNG → ghép Markdown theo đúng
thứ tự quy định → xuất `.docx` với `reference.docx` → xuất `.pdf` và in số trang.

Sản phẩm trong `docs/thesis/build/out/` (thư mục này được `.gitignore`):

| File | Nội dung | Số trang hiện tại |
|---|---|---|
| `main.docx` | Phần chính (bìa → Chương 5 → Tài liệu tham khảo) | 87 |
| `phuluc.docx` | Riêng phần Phụ lục A–I | 46 |
| `BAO-CAO-KLTN.docx` | Bản đầy đủ để nộp (phần chính + Phụ lục) | 133 |

Đối chiếu với giới hạn 80 trang: phần đánh số Ả-rập (MỞ ĐẦU → Chương 5 → Tài liệu tham
khảo) là **78 trang** — MỞ ĐẦU 3 + Chương 1 (10) + Chương 2 (13) + Chương 3 (20) +
Chương 4 (17) + Chương 5 (12) + Tài liệu tham khảo 3. Chín trang đầu (bìa ngoài, bìa
trong, lời cam đoan, lời cảm ơn, danh mục viết tắt / bảng / hình) là phần dẫn nhập đánh
số La Mã, không tính vào 80 trang; Mục lục chèn tay sau khi build cũng thuộc phần này.

Yêu cầu công cụ: `pandoc` ≥ 3.x, `python3`, `node`/`npx` (cho `@mermaid-js/mermaid-cli`),
`libreoffice` (lệnh `soffice`, dùng để xuất PDF và đếm trang).

Ảnh sơ đồ đã render được cache tại `build/diagcache/` nên các lần build sau chỉ render
những sơ đồ mới. Xoá thư mục này nếu muốn render lại toàn bộ.

## 2. Thứ tự ghép file

```
docs/thesis/
├── 00-trang-bia.md                   ← Trang bìa ngoài
├── 01-trang-bia-phu.md               ← Trang bìa trong (có GVHD)
├── 02-loi-cam-doan.md                ← Lời cam đoan
├── 03-loi-cam-on.md                  ← Lời cảm ơn
├── 04-danh-muc-viet-tat.md           ← Danh mục ký hiệu, chữ viết tắt
├── 05-danh-muc-bang.md               ← Danh mục bảng biểu (Bảng 1.1 – 5.4)
├── 06-danh-muc-hinh.md               ← Danh mục hình vẽ (Hình 1.1 – 4.1)
├── 07-mo-dau.md                      ← Mở đầu
├── chuong-1-tong-quan.md             ← Chương 1 — Tổng quan đề tài
├── chuong-2-co-so-ly-thuyet.md       ← Chương 2 — Cơ sở lý thuyết
├── chuong-3-phan-tich-thiet-ke.md    ← Chương 3 — Phân tích và thiết kế
├── chuong-4-cai-dat.md               ← Chương 4 — Cài đặt và kiểm thử
├── chuong-5-ket-qua-va-ket-luan.md   ← Chương 5 — Kết quả và kết luận
├── 08-tai-lieu-tham-khao.md          ← Tài liệu tham khảo
└── 09-phu-luc.md                     ← Phụ lục A–I (không tính vào giới hạn 80 trang)
```

Các file khác trong thư mục là tài liệu nội bộ, KHÔNG ghép vào báo cáo:
`de-cuong.md` (đề cương), `demo-video-script.md`, `huong-dan-su-dung.md`,
`q-and-a-prep.md`, `slide-outline.md`, `slide-template.md`,
`diem-noi-bat-va-huong-phat-trien.md`.

## 3. Cấu trúc Phụ lục

Nội dung tra cứu chi tiết được chuyển khỏi các chương chính để phần chính giữ dưới
80 trang. Bảng trong phụ lục đánh số `Bảng PL.n`, hình đánh số `Hình PL.n`.

| Phụ lục | Nội dung | Chuyển từ |
|---|---|---|
| A | Đặc tả use case chi tiết | Chương 3 |
| B | Danh sách đầy đủ endpoint REST (Bảng PL.1) | Chương 3 |
| C | Mô tả chi tiết các bảng dữ liệu | Chương 3 |
| D | Ma trận chuyển trạng thái (Bảng PL.2 – PL.4) | Chương 3 |
| E | Danh mục công nghệ và phiên bản (Bảng PL.5) | Chương 3 + 4 (gộp hai bảng trùng) |
| F | Cấu hình triển khai — Flyway migration, cổng/URL (Bảng PL.6, PL.7), `.env.example`, profile `dev`/`prod`, biến VNPay | Chương 4 (§4.5.2, §4.6.4, §4.7 cũ) |
| G | Thống kê kiểm thử (Bảng PL.8) | Chương 4 |
| H | Bộ ảnh giao diện hệ thống (Hình PL.1 – PL.16) | Chương 3 |
| I | Hai pha mở rộng nghiệp vụ sau bảo vệ (Bảng PL.9, Hình PL.17 – PL.26) | Chương 6 cũ |

## 4. Quy định format (Phụ lục 16.3 — ĐH Mở Hà Nội)

| Mục | Giá trị |
|---|---|
| Font chữ chính | Times New Roman 13 (hoặc 14) |
| Khoảng cách dòng | 1.5 lines |
| Khoảng cách đoạn | Trước 3pt, sau 3pt |
| Lề trên / dưới / trái (gáy) / phải | 2 / 2 / 3.5 / 2 cm |
| Đánh số trang | Giữa cuối trang, số tự nhiên |
| Sub-section tối đa | 4 cấp (ví dụ 1.2.3.4) |
| Caption bảng | PHÍA TRÊN bảng, đánh số theo chương |
| Caption hình | PHÍA DƯỚI hình, đánh số theo chương |
| Trích dẫn | `[15]`, `[15, tr.134-136]`, `[15], [21]` |
| Tổng số trang (không kể phụ lục) | Tối đa 80 trang |

`reference.docx` trong thư mục này đã được chỉnh đúng bảng trên (TNR 13pt, giãn dòng 1.5,
lề 2/2/3.5/2 cm). Không cần sinh lại; nếu cần sinh lại từ đầu:

```bash
pandoc --print-default-data-file reference.docx > reference.docx
```

rồi mở bằng Word và sửa các style `Normal`, `Heading 1`–`Heading 4`, `Caption`, page margins,
page number theo bảng quy định.

> Lưu ý: bản `BAO-CAO-KLTN-v1-112tr.docx` là bản cũ, được sinh với font 12pt và giãn dòng
> đơn nên KHÔNG đúng quy định — con số 112 trang trên tên file không phản ánh số trang
> khi trình bày đúng chuẩn.

## 5. Việc cần làm thủ công sau khi biên dịch

1. Điền số nhóm vào `[SỐ NHÓM]` ở `00-trang-bia.md` và `01-trang-bia-phu.md`.
2. Mở `.docx` bằng Word, chèn mục lục và cập nhật (`Update entire table`).
3. Điền số trang vào Danh mục bảng biểu và Danh mục hình vẽ (Pandoc không tự sinh).
4. Ký tên vào trang Lời cam đoan sau khi in.
5. Chèn logo trường lên trang bìa nếu khoa yêu cầu.
6. In đúng quy cách: bìa cứng, lề gáy 3.5 cm.

## 6. Tham chiếu

- Spec gốc: `../superpowers/specs/2026-05-19-he-thong-quan-ly-giao-hang-design.md`
- Plan từng pha: `../superpowers/plans/`
- Code review: `../superpowers/reviews/`
- Runbook smoke test: `../RUNBOOK.md`
