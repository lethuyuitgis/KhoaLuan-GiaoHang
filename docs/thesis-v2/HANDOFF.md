# BÀN GIAO — Trạng thái công việc báo cáo/đề cương KLTN

> Dán câu này vào session Claude Code mới để tiếp tục:
> **"Đọc docs/thesis-v2/HANDOFF.md rồi tiếp tục công việc."**

## Đề tài
"Xây dựng hệ thống quản lý giao hàng tích hợp Telegram Mini App, Web Admin và VNPay".
GVHD: ThS. Đinh Tuấn Long · Lớp CHDN420 · Nhóm 3: Lê Thị Trần Thủy (23C1001P3734, nhóm trưởng),
Ngô Phúc Hiếu (23C1001P5290), Nguyễn Thế Thưởng (23C1001P4046).

## Ba tài liệu và trạng thái

### 1. Đề cương — `docs/thesis/de-cuong.md`  ✅
- Theo mẫu ĐH Mở Hà Nội 7 mục; nội dung khớp file gốc `~/Desktop/Đề cương khóa luận-...Nhóm2.docx`.
- Phân công: **Thủy chỉ code** (kiến trúc + backend + tích hợp Telegram/VNPay);
  Hiếu = Web Admin + kiểm thử/Docker + bảo mật; **Thưởng viết báo cáo** + Mini App + slide.
- Đã điền GVHD/MSSV/Lớp thật (không còn placeholder).

### 2. Báo cáo chính (5 chương + Phụ lục A–I) — `docs/thesis/`  ✅ ĐẠT TRẦN 80 TRANG
Build: `bash docs/thesis/build.sh` → `docs/thesis/build/out/`

| File | Trang |
|---|---|
| `main.docx` (bìa → Ch5 → TLTK) | 87 |
| `phuluc.docx` (Phụ lục A–I) | 46 |
| `BAO-CAO-KLTN.docx` (bản nộp) | 133 |

**Cách đối chiếu trần 80 trang:** phần đánh số Ả-rập (MỞ ĐẦU → Ch5 → TLTK) = **78 trang**
(Mở đầu 3 + Ch1 10 + Ch2 13 + Ch3 20 + Ch4 17 + Ch5 12 + TLTK 3). Chín trang dẫn nhập
(2 bìa, cam đoan, cảm ơn, 3 danh mục) + Mục lục chèn tay → đánh số La Mã, không tính.

Đã làm trong đợt này:
- Tách toàn bộ nội dung tra cứu sang `09-phu-luc.md` (Phụ lục A–I, đánh số `Bảng PL.n` / `Hình PL.n`).
- Ch1: xoá 3 mục trùng hoàn toàn với MỞ ĐẦU (Đối tượng / Phương pháp / Bố cục), renumber 1.7→1.4, 1.8→1.5.
- Ch4: chuyển §4.7 "Cấu hình môi trường" sang Phụ lục F (F.4–F.6), renumber 4.8→4.7, 4.9→4.8.
- Sửa mâu thuẫn số liệu chéo chương (xem mục "Số liệu chuẩn" bên dưới).
- Backup trước condense: `docs/thesis/.backup-precondense/`.
- `chuong-6-mo-rong.md` đã XOÁ (2026-08-03); nội dung sống duy nhất là Phụ lục I trong `09-phu-luc.md`.

### 3. Báo cáo bản 4 chương theo mẫu — `docs/thesis-v2/`  ✅ (`BAO-CAO-KLTN.docx`, 52 trang)
- Khung mẫu `~/Desktop/BaoCao_PhanMemQuanLyKhamChuaBenh.docx` (CH1 Phát biểu bài toán / CH2 Phân tích /
  CH3 Thiết kế / CH4 Kết quả sản phẩm + Kết luận + TLTK). 8 sơ đồ Mermaid, 15 ảnh, 17 bảng thực thể.
- ⚠️ Bản này CHƯA đồng bộ số liệu mới (còn "endpoint ~35", "8 bounded context").

## Số liệu chuẩn (canonical — đã verify với mã nguồn)
| Chỉ số | Giá trị |
|---|---|
| Maven submodule | 11 (8 bounded context lúc bảo vệ + `promotion` pha mở rộng + 2 stub `miniapp`/`webadmin`) |
| Bounded context | 8 tại thời điểm bảo vệ, 9 sau hai pha mở rộng |
| Flyway migration | 15 (V1–V15); V12–V15 thuộc hai pha mở rộng (Phụ lục I) |
| Test backend | 253 = 231 unit + 22 integration |
| Test frontend | 21 · **Tổng 274** |
| Endpoint REST | 39 (danh sách đầy đủ = Phụ lục B) |
| Đóng góp kỹ thuật | **8** (Ch1 §1.4.1–1.4.8; §1.4.9 là chỉ số định lượng, không phải đóng góp) |
| Thư viện Telegram | `telegrambots-spring-boot-starter` **6.9.7.1** (KHÔNG phải 7.x) |
| Yêu cầu MoSCoW | 8 Must + 3 Should (đã xong) + 2 Could + 2 Won't = 15 |

Lệnh verify lại: `find backend -path "*db/migration*" -name "V*.sql"`,
`grep -A20 "<modules>" backend/pom.xml`, `grep -rn telegrambots backend/modules/bot/pom.xml`.

## Việc còn có thể làm tiếp
- [ ] Điền số nhóm vào `[SỐ NHÓM]` ở `00-trang-bia.md` + `01-trang-bia-phu.md` (chờ phòng đào tạo cấp).
- [ ] Chèn Mục lục tự động + đánh số trang (bottom-center) trong Word sau khi build — Pandoc không sinh được.
- [ ] Đồng bộ số liệu chuẩn ở trên vào `docs/thesis-v2/`.
- [x] Xoá `chuong-6-mo-rong.md` — đã xoá, Phụ lục I là nguồn duy nhất.

## Công cụ build (đã kiểm chứng trên máy)
- `bash docs/thesis/build.sh` — làm hết: render Mermaid (cache `build/diagcache/`), ghép md theo
  đúng thứ tự, xuất docx + pdf, in số trang.
- `docs/thesis/render-mermaid.py` — render fence ```` ```mermaid ````; tự cap `{width=15.5cm}` hoặc
  `{height=19cm}`. LƯU Ý: trong `sequenceDiagram` phải đổi `;` → `,` trong nhãn (script tự làm).
- `docs/thesis/reference.docx` — đã patch đúng Phụ lục 16.3. Các style đã thêm/sửa:
  `TrangBia`, `TieuDeBia`, `ChuKy` (bìa — dùng div `::: {custom-style="..."}`, tránh `#` vì
  `Heading1` có `pageBreakBefore`), `SourceCode` (Consolas 10pt giãn đơn), `BodyText` spacing
  9pt→3pt, `Compact` giãn đơn (dùng cho ô bảng + list tight → tiết kiệm 8 trang).
- Đếm trang: `soffice --headless --convert-to pdf x.docx` rồi đếm regex `/Type\s*/Page[^s]`.

## Ràng buộc hình thức (Phụ lục 16.3 ĐH Mở HN)
Không quá 80 trang (KHÔNG kể phụ lục) · TNR 13–14 · giãn 1.5 · lề trên/dưới 2cm, trái 3.5cm, phải 2cm ·
số trang giữa chân trang · caption bảng ở TRÊN, caption hình ở DƯỚI · đánh số hình/bảng theo chương ·
tối đa 4 cấp mục.
