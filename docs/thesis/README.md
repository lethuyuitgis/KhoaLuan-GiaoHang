# Hướng dẫn biên dịch báo cáo khoá luận

> **Đề tài:** Hệ thống quản lý giao hàng tích hợp Telegram Mini App, Web Admin và VNPay
> **Sinh viên:** [HỌ TÊN SINH VIÊN]
> **MSSV:** [MSSV]
> **GVHD:** [HỌC HÀM, HỌC VỊ — HỌ TÊN GVHD]
> **Khoa:** Công nghệ thông tin
> **Năm học:** [NĂM]

Thư mục này chứa toàn bộ bản nháp Markdown của báo cáo khoá luận. File `.docx` cuối cùng được biên dịch từ các file `.md` này thông qua Pandoc với reference doc tuỳ chỉnh theo chuẩn của khoa.

## 1. Cấu trúc thư mục theo thứ tự ghép file

```
docs/thesis/
├── 00-trang-bia.md                       ← Trang bìa chính
├── 01-trang-bia-phu.md                   ← Trang bìa phụ
├── 02-loi-cam-doan.md                    ← Lời cam đoan
├── 03-loi-cam-on.md                      ← Lời cảm ơn
├── 04-danh-muc-viet-tat.md               ← Danh mục ký hiệu, chữ viết tắt
├── 05-danh-muc-bang.md                   ← Danh mục bảng biểu
├── 06-danh-muc-hinh.md                   ← Danh mục hình vẽ, đồ thị
├── 07-mo-dau.md                          ← Mở đầu
├── chuong-1-tong-quan.md                 ← Chương 1 — Tổng quan đề tài
├── chuong-2-co-so-ly-thuyet.md           ← Chương 2 — Cơ sở lý thuyết
├── chuong-3-phan-tich-thiet-ke.md        ← Chương 3 — Phân tích & Thiết kế
├── chuong-4-cai-dat.md                   ← Chương 4 — Cài đặt và kiểm thử
├── chuong-5-ket-qua-va-ket-luan.md       ← Chương 5 — Kết quả và Kết luận
├── chuong-6-mo-rong.md                   ← Chương 6 — Mở rộng sau bảo vệ (Phase 14 voucher + Phase 15 commission)
├── 08-tai-lieu-tham-khao.md              ← Tài liệu tham khảo
├── diem-noi-bat-va-huong-phat-trien.md   ← Nguồn tham khảo nội bộ (không ghép vào báo cáo chính)
├── README.md                              ← (file này)
└── screenshots/                           ← 30 ảnh chụp giao diện được tham chiếu trong báo cáo
```

Thứ tự ghép trên tuân theo quy định của khoa: **trang bìa → trang bìa phụ → lời cam đoan → lời cảm ơn → danh mục viết tắt → danh mục bảng → danh mục hình → mở đầu → các chương → tài liệu tham khảo → phụ lục (nếu có)**.

## 2. Quy định format của khoa cần tuân thủ

| Mục | Giá trị |
|---|---|
| Font chữ chính | Times New Roman 13 (hoặc 14) |
| Khoảng cách dòng | 1.5 lines |
| Khoảng cách đoạn | Trước 3pt, sau 3pt |
| Lề trên | 2 cm |
| Lề dưới | 2 cm |
| Lề trái (gáy đóng) | 3.5 cm |
| Lề phải | 2 cm |
| Đánh số trang | Giữa cuối trang, số tự nhiên (1, 2, 3 — KHÔNG dùng La Mã) |
| Sub-section tối đa | 4 cấp (ví dụ 1.2.3.4) |
| Mỗi nhóm tiểu mục | Tối thiểu 2 tiểu mục con |
| Caption bảng | PHÍA TRÊN bảng, đánh số theo chương (Bảng 3.2) |
| Caption hình | PHÍA DƯỚI hình, đánh số theo chương (Hình 3.2) |
| Trích dẫn | `[15]`, `[15, tr.134-136]`, hoặc `[15], [21]` |
| Tổng số trang (không kể phụ lục) | Tối đa 80 trang (mục tiêu 60–80) |

## 3. Cài đặt công cụ biên dịch

### 3.1. Pandoc và LaTeX

```bash
# macOS
brew install pandoc
brew install --cask mactex          # cài LaTeX đầy đủ (~5 GB)
# hoặc gọn hơn: brew install basictex

# Ubuntu / Debian
sudo apt-get install pandoc texlive-xetex texlive-fonts-recommended texlive-lang-other
```

Pandoc phiên bản tối thiểu **3.x**.

### 3.2. Mermaid filter (để render sơ đồ Mermaid)

```bash
npm install -g mermaid-filter
```

Filter này biến block ```` ```mermaid ```` thành ảnh PNG/SVG nhúng vào file `.docx` hoặc `.pdf`.

### 3.3. Font Times New Roman

XeLaTeX cần truy cập được font Times New Roman ở cấp hệ điều hành. Trên macOS font này đã có sẵn. Trên Linux có thể cài qua `ttf-mscorefonts-installer`. Nếu không có Times New Roman, có thể dùng **Latin Modern Roman** (đi kèm LaTeX) hoặc **Liberation Serif** (mã nguồn mở, tương thích metric với Times New Roman).

## 4. Sinh `reference.docx` tuỳ chỉnh theo chuẩn khoa

Bước 1 — sinh reference doc mặc định của Pandoc:

```bash
cd docs/thesis
pandoc --print-default-data-file reference.docx > reference.docx
```

Bước 2 — mở `reference.docx` bằng Microsoft Word (hoặc LibreOffice Writer) và chỉnh các style sau theo bảng quy định ở mục 2:

- **Normal** — Times New Roman 13, line spacing 1.5, paragraph spacing 3pt before / 3pt after, first-line indent 1 cm.
- **Heading 1** (Chương) — Times New Roman 16 bold, căn giữa, page break before.
- **Heading 2** (mục 1.1) — Times New Roman 14 bold, căn trái.
- **Heading 3** (tiểu mục 1.1.1) — Times New Roman 13 bold italic.
- **Heading 4** (1.1.1.1) — Times New Roman 13 bold.
- **Caption** (Hình X.Y / Bảng X.Y) — Times New Roman 12 italic căn giữa.
- **Page Layout → Margins** — Top 2 cm, Bottom 2 cm, Left 3.5 cm, Right 2 cm.
- **Insert → Page Number** — Bottom centre, dạng số tự nhiên (1, 2, 3).

Bước 3 — lưu lại file `reference.docx`, dùng làm template cho mọi lần biên dịch tiếp theo.

## 5. Lệnh biên dịch sang `.docx`

Lệnh dưới đây ghép file theo đúng thứ tự yêu cầu và áp dụng reference doc đã chỉnh ở mục 4.

```bash
cd docs/thesis

pandoc \
    00-trang-bia.md \
    01-trang-bia-phu.md \
    02-loi-cam-doan.md \
    03-loi-cam-on.md \
    04-danh-muc-viet-tat.md \
    05-danh-muc-bang.md \
    06-danh-muc-hinh.md \
    07-mo-dau.md \
    chuong-1-tong-quan.md \
    chuong-2-co-so-ly-thuyet.md \
    chuong-3-phan-tich-thiet-ke.md \
    chuong-4-cai-dat.md \
    chuong-5-ket-qua-va-ket-luan.md \
    chuong-6-mo-rong.md \
    08-tai-lieu-tham-khao.md \
    -o khoa-luan.docx \
    --resource-path=. \
    --filter=mermaid-filter \
    --toc \
    --toc-depth=4 \
    --reference-doc=reference.docx \
    --number-sections=false
```

Sau khi sinh `khoa-luan.docx`, mở bằng Word và:

1. **Cập nhật mục lục** (`Update Table → Update entire table`) để bù số trang cho danh mục bảng, danh mục hình và mục lục chính.
2. **Kiểm tra** caption bảng / hình đã đúng vị trí (bảng phía trên, hình phía dưới).
3. **Điền số trang** vào ba danh mục (mục lục, danh mục bảng, danh mục hình) — Pandoc không tự sinh được số trang.

## 6. Biên dịch sang PDF qua XeLaTeX (hỗ trợ tiếng Việt)

```bash
cd docs/thesis

pandoc \
    00-trang-bia.md \
    01-trang-bia-phu.md \
    02-loi-cam-doan.md \
    03-loi-cam-on.md \
    04-danh-muc-viet-tat.md \
    05-danh-muc-bang.md \
    06-danh-muc-hinh.md \
    07-mo-dau.md \
    chuong-1-tong-quan.md \
    chuong-2-co-so-ly-thuyet.md \
    chuong-3-phan-tich-thiet-ke.md \
    chuong-4-cai-dat.md \
    chuong-5-ket-qua-va-ket-luan.md \
    chuong-6-mo-rong.md \
    08-tai-lieu-tham-khao.md \
    -o khoa-luan.pdf \
    --resource-path=. \
    --filter=mermaid-filter \
    --pdf-engine=xelatex \
    -V mainfont="Times New Roman" \
    -V monofont="Menlo" \
    -V fontsize=13pt \
    -V linestretch=1.5 \
    -V geometry:"top=2cm,bottom=2cm,left=3.5cm,right=2cm" \
    -V lang=vi \
    -V documentclass=report \
    --toc --toc-depth=4
```

Lưu ý: nếu không có Times New Roman, thay bằng `-V mainfont="Latin Modern Roman"` hoặc `-V mainfont="Liberation Serif"`.

## 7. Render Mermaid bằng phương án thay thế (nếu mermaid-filter không khả dụng)

Nếu mermaid-filter không cài được, có thể convert thủ công các block ```` ```mermaid ```` thành ảnh PNG / SVG trước rồi nhúng:

```bash
# Cài Mermaid CLI
npm install -g @mermaid-js/mermaid-cli

# Convert từng diagram
mmdc -i diagram1.mmd -o screenshots/diagram1.png -t default -b transparent
```

Sau đó thay block ```` ```mermaid ```` trong các chương bằng `![Hình X.Y. Caption](screenshots/diagramN.png)`.

## 8. Kiểm tra số trang đầu ra

```bash
# macOS / Linux
pdfinfo khoa-luan.pdf | grep Pages
# hoặc
wc -l khoa-luan.docx       # không chính xác, chỉ ước lượng
```

Mục tiêu: **60–80 trang không kể phụ lục**. Nếu vượt 80 trang, cân nhắc cắt bớt các code block dài, gộp các bảng quá chi tiết, hoặc chuyển một số phần phụ trợ xuống phần Phụ lục.

## 9. Lưu ý sinh viên cần tự bổ sung

Trước khi nộp hội đồng, sinh viên cần thực hiện các bước sau đây để hoàn thiện báo cáo:

1. **Điền thông tin cá nhân** vào các placeholder `[TÊN TRƯỜNG]`, `[KHOA]`, `[HỌ TÊN SINH VIÊN]`, `[MSSV]`, `[LỚP]`, `[KHOÁ]`, `[GVHD]`, `[GVPB]`, `[THÀNH PHỐ]`, `[NĂM]` ở các file `00-trang-bia.md`, `01-trang-bia-phu.md`, `02-loi-cam-doan.md`, `03-loi-cam-on.md`.
2. **Soạn lại lời cảm ơn** theo hoàn cảnh cá nhân trong file `03-loi-cam-on.md` (đã có TODO comment gợi ý nội dung).
3. **Ký tên thật** vào trang Lời cam đoan sau khi in.
4. **Bổ sung ảnh sinh viên** vào trang bìa nếu khoa yêu cầu (chèn trực tiếp vào file `.docx` sau khi convert).
5. **Bổ sung logo trường** lên đầu trang bìa nếu khoa yêu cầu (chèn vào reference doc hoặc trang bìa sau khi convert).
6. **Điền số trang** vào các danh mục (mục lục, bảng biểu, hình vẽ) sau khi convert hoàn chỉnh.
7. **Cập nhật thông tin GVHD và năm học** trong file `README.md` (file này) ở header.
8. **Kiểm tra lại Mermaid render** — một số sơ đồ phức tạp có thể cần điều chỉnh kích thước hoặc orientation.
9. **In đúng quy cách** — bìa cứng, gáy đóng tối thiểu 3.5 cm, đúng format khoa quy định.
10. **Chuẩn bị bản số** — gửi kèm `.pdf` và `.docx` cho hội đồng theo quy định.

## 10. Danh sách screenshot (30 ảnh)

Các ảnh chụp giao diện được tham chiếu trong báo cáo nằm ở thư mục `screenshots/`:

| File | Caption sử dụng |
|---|---|
| `admin-01-login.png` | Web Admin — màn hình đăng nhập với JWT (Hình 3.6) |
| `admin-02-dashboard.png` | Web Admin — bảng điều khiển tổng quan (Hình 1.2, 2.2, 3.1, 3.7) |
| `admin-03-orders.png` | Web Admin — danh sách đơn (Hình 2.4, 3.3, 3.8) |
| `admin-04-reports.png` | Web Admin — báo cáo doanh thu (Hình 1.3, 2.3, 3.9, 5.1) |
| `admin-05-products.png` | Web Admin — quản lý sản phẩm CRUD (Hình 3.10) |
| `admin-06-shippers.png` | Web Admin — quản lý shipper (Hình 2.8, 3.5, 3.11) |
| `admin-07-order-detail.png` | Web Admin — chi tiết đơn (Hình 2.6, 3.2, 3.12, 4.2) |
| `miniapp-cust-01-catalog.png` | Mini App khách — danh mục (Hình 1.1, 2.1, 3.13) |
| `miniapp-cust-02-cart-empty.png` | Mini App khách — giỏ hàng rỗng (Hình 3.14) |
| `miniapp-cust-03-orders.png` | Mini App khách — lịch sử đơn (Hình 3.15) |
| `miniapp-cust-04-catalog-with-cart.png` | Mini App khách — danh mục với badge giỏ (Hình 3.16) |
| `miniapp-cust-05-cart-filled.png` | Mini App khách — giỏ hàng có sản phẩm (Hình 1.4, 3.17) |
| `miniapp-cust-06-checkout.png` | Mini App khách — checkout (Hình 2.5, 3.4, 3.18) |
| `miniapp-cust-07-order-detail.png` | Mini App khách — chi tiết đơn (Hình 3.19) |
| `miniapp-ship-01-assignments.png` | Mini App shipper — danh sách assignment (Hình 3.20) |
| `miniapp-ship-02-assignment-detail.png` | Mini App shipper — chi tiết assignment (Hình 3.21) |
| `miniapp-tunnel-via-https.png` | Mini App — tunnel HTTPS dev environment (Hình 2.7, 3.22) |

## 11. Tham chiếu

- Spec gốc: `../superpowers/specs/2026-05-19-he-thong-quan-ly-giao-hang-design.md`
- Plan từng pha: `../superpowers/plans/`
- Code review report: `../superpowers/reviews/`
- Runbook smoke test: `../RUNBOOK.md`
- Tài liệu phụ trợ: `diem-noi-bat-va-huong-phat-trien.md`
