#!/usr/bin/env bash
# Biên dịch báo cáo KLTN: render sơ đồ Mermaid -> ghép Markdown -> .docx -> .pdf + đếm trang.
#
# Yêu cầu: pandoc >= 3.x, python3, node/npx (cho @mermaid-js/mermaid-cli),
#          libreoffice (lệnh `soffice`) để xuất PDF và đếm số trang.
#
# Dùng:  bash docs/thesis/build.sh [thư-mục-đầu-ra]
#        (mặc định đầu ra: docs/thesis/build/out)
#
# Sản phẩm:
#   main.docx              — phần chính (bìa -> Chương 5 -> TLTK), dùng để đếm trang theo quy định
#   phuluc.docx            — riêng phần Phụ lục
#   BAO-CAO-KLTN.docx      — bản đầy đủ (phần chính + Phụ lục), bản nộp
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$SRC/build/work"
CACHE="$SRC/build/diagcache"
REF="$SRC/reference.docx"
OUT="${1:-$SRC/build/out}"

rm -rf "$WORK"
mkdir -p "$WORK" "$OUT" "$CACHE"
cp "$SRC"/*.md "$WORK"/
cp -r "$SRC"/screenshots "$WORK"/screenshots

# 1. Render các khối ```mermaid``` thành PNG rồi thay bằng tham chiếu ảnh của Pandoc
python3 "$SRC/render-mermaid.py" "$WORK" "$CACHE"

# 2. Thứ tự ghép phần chính (bìa -> Tài liệu tham khảo)
FILES=(00-trang-bia.md 01-trang-bia-phu.md 02-loi-cam-doan.md 03-loi-cam-on.md
       04-danh-muc-viet-tat.md 05-danh-muc-bang.md 06-danh-muc-hinh.md 07-mo-dau.md
       chuong-1-tong-quan.md chuong-2-co-so-ly-thuyet.md chuong-3-phan-tich-thiet-ke.md
       chuong-4-cai-dat.md chuong-5-ket-qua-va-ket-luan.md
       08-tai-lieu-tham-khao.md)

cd "$WORK"
pandoc "${FILES[@]}" -o "$OUT/main.docx" --reference-doc="$REF" --resource-path="$WORK"
pandoc 09-phu-luc.md -o "$OUT/phuluc.docx" --reference-doc="$REF" --resource-path="$WORK"
pandoc "${FILES[@]}" 09-phu-luc.md -o "$OUT/BAO-CAO-KLTN.docx" \
       --reference-doc="$REF" --resource-path="$WORK"

# 3. Xuất PDF và đếm số trang (phần chính phải <= 80 trang theo Phụ lục 16.3)
cd "$OUT"
for d in *.docx; do
  soffice --headless --convert-to pdf "$d" --outdir "$OUT" >/dev/null 2>&1
done
echo "=== SỐ TRANG ==="
for p in "$OUT"/*.pdf; do
  n=$(python3 -c "
import re, sys
data = open(sys.argv[1], 'rb').read()
print(len(re.findall(rb'/Type\s*/Page[^s]', data)))
" "$p")
  echo "$(basename "$p"): $n"
done
