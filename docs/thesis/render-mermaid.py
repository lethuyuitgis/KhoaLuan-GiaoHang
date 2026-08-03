#!/usr/bin/env python3
"""Render các khối ```mermaid``` trong Markdown thành PNG và thay bằng tham chiếu ảnh Pandoc.

Nếu ngay sau khối fence là một dòng in nghiêng dạng `*Hình x.y. ...*` thì dòng đó được coi là
chú thích hình: nó bị tiêu thụ và trở thành alt text của ảnh, để Pandoc đặt caption dưới hình.

Dùng: render-mermaid.py <thư-mục-work> [thư-mục-cache]
Ảnh đã render được cache theo tên file nên lần build sau không phải gọi lại mmdc.
"""
import re
import shutil
import subprocess
import sys
from pathlib import Path

work = Path(sys.argv[1])
cache = Path(sys.argv[2]) if len(sys.argv) > 2 else work / "diagcache"
cache.mkdir(parents=True, exist_ok=True)
mdir = work / "diagrams"
mdir.mkdir(exist_ok=True)

FENCE = re.compile(r"```mermaid\n(.*?)\n```[ \t]*\n(?:\n\*(Hình [^*\n]+)\*\n)?", re.DOTALL)

TEXT_W_CM = 15.5   # khổ A4 21cm trừ lề trái 3.5cm và lề phải 2cm
MAX_H_CM = 19.0    # chừa chỗ cho dòng chú thích trên trang cao 29.7cm


def fix_semicolons(code: str) -> str:
    """mmdc không parse được ';' trong nhãn message của sequenceDiagram."""
    if "sequenceDiagram" in code:
        code = code.replace(";", ",")
    return code


def size_attr(png: Path) -> str:
    """Co sơ đồ vừa cột chữ; nếu sơ đồ quá cao thì giới hạn theo chiều cao."""
    header = png.read_bytes()[16:24]
    w = int.from_bytes(header[:4], "big")
    h = int.from_bytes(header[4:], "big")
    if h / w * TEXT_W_CM <= MAX_H_CM:
        return f"{{width={TEXT_W_CM}cm}}"
    return f"{{height={MAX_H_CM}cm}}"


for md in sorted(work.glob("*.md")):
    text = md.read_text()
    if "```mermaid" not in text:
        continue
    idx = 0
    parts = []
    pos = 0
    for m in FENCE.finditer(text):
        idx += 1
        name = f"{md.stem}-d{idx}"
        png_cache = cache / f"{name}.png"
        if not png_cache.exists():
            mmd = cache / f"{name}.mmd"
            mmd.write_text(fix_semicolons(m.group(1)) + "\n")
            subprocess.run(
                ["npx", "-y", "@mermaid-js/mermaid-cli", "-i", str(mmd),
                 "-o", str(png_cache), "-b", "white", "-s", "2"],
                check=True, capture_output=True,
            )
        shutil.copy(png_cache, mdir / f"{name}.png")
        caption = m.group(2) or ""
        parts.append(text[pos:m.start()])
        parts.append(f"![{caption}](diagrams/{name}.png){size_attr(png_cache)}\n")
        pos = m.end()
    parts.append(text[pos:])
    md.write_text("".join(parts))
    print(f"{md.name}: đã render {idx} sơ đồ")
