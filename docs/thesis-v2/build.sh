#!/usr/bin/env bash
# Biên dịch báo cáo bản 4 chương (thesis-v2): render Mermaid -> ghép Markdown
# -> .docx -> cấy trang bìa từ TEMPLATE_BAO_CAO_DE_CUONG.docx -> .pdf + đếm trang.
#
# Dùng:  bash docs/thesis-v2/build.sh [thư-mục-đầu-ra]
#        (mặc định đầu ra: docs/thesis-v2/build/out)
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
THESIS="$SRC/../thesis"
WORK="$SRC/build/work"
CACHE="$SRC/build/diagcache"
REF="$THESIS/reference.docx"
TEMPLATE="$THESIS/TEMPLATE_BAO_CAO_DE_CUONG.docx"
OUT="${1:-$SRC/build/out}"

rm -rf "$WORK"
mkdir -p "$WORK" "$OUT" "$CACHE"
cp "$SRC"/*.md "$WORK"/
cp -r "$SRC"/screenshots "$WORK"/screenshots
cp -r "$SRC"/diagrams "$WORK"/diagrams

# 1. Render các khối ```mermaid``` thành PNG
python3 "$THESIS/render-mermaid.py" "$WORK" "$CACHE"

# 2. Ghép theo đúng flow mẫu: bìa (cấy sau) -> Mục lục -> Lời mở đầu -> CH1-CH4
#    -> Kết luận -> TLTK
FILES=(00-trang-bia.md chuong-1-phat-bieu-bai-toan.md chuong-2-phan-tich-he-thong.md
       chuong-3-thiet-ke-he-thong.md chuong-4-ket-qua-san-pham.md)

cd "$WORK"
pandoc "${FILES[@]}" -o "$OUT/BAO-CAO-KLTN.docx" --reference-doc="$REF" \
       --resource-path="$WORK"

# 2b. Nới giãn cách bảng (padding ô + giãn dòng) — TRƯỚC khi cấy bìa
python3 "$SRC/space-tables.py" "$OUT/BAO-CAO-KLTN.docx"

# 3. Cấy nguyên trang bìa (khung viền + logo + bảng thông tin) từ template
python3 "$SRC/inject-cover.py" "$TEMPLATE" "$OUT/BAO-CAO-KLTN.docx"

# 4. Xuất PDF và đếm số trang
cd "$OUT"
soffice --headless --convert-to pdf BAO-CAO-KLTN.docx --outdir "$OUT" >/dev/null 2>&1
n=$(python3 -c "
import re
data = open('$OUT/BAO-CAO-KLTN.pdf', 'rb').read()
print(len(re.findall(rb'/Type\s*/Page[^s]', data)))
")
echo "=== SỐ TRANG: BAO-CAO-KLTN.pdf = $n ==="
