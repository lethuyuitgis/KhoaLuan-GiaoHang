#!/usr/bin/env python3
"""Cấy nguyên trang bìa từ TEMPLATE_BAO_CAO_DE_CUONG.docx (khung viền + logo
ĐH Mở HN + bảng thông tin + footer ngày tháng) vào đầu file báo cáo đã build
bằng Pandoc.

Dùng:  python3 inject-cover.py <template.docx> <target.docx>

Việc làm:
  1. Cắt fragment bìa trong template (từ đầu <w:body> đến trước đoạn TOC đầu tiên).
  2. Đổi "ĐỀ CƯƠNG: " -> "ĐỀ TÀI: " trên tiêu đề bìa.
  3. Ép giãn đơn cho mọi đoạn bìa thiếu <w:spacing> (file đích mặc định giãn 1.5
     sẽ làm bìa phình sang trang 2 nếu không ép).
  4. Chép 2 ảnh (khung viền, logo) + footer ngày tháng sang file đích, đăng ký
     relationship / content-type, footer chỉ áp cho trang đầu (titlePg).
  5. Gộp khai báo namespace của template vào root file đích.
"""
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

SPACING = '<w:spacing w:before="0" w:after="0" w:line="240" w:lineRule="auto"/>'
# Trang bìa là section RIÊNG với lề đối xứng của template (1440 twips = 2.54cm
# mọi phía) — tách khỏi lề đóng-gáy bất đối xứng của thân bài (trái 3.5cm) để
# khung bìa neo theo cột không bị đẩy lệch sang phải. Footer bìa gắn ngay section
# này (default), không cần titlePg vì bìa chỉ 1 trang.
COVER_SECTPR = (
    '<w:p><w:pPr><w:sectPr>'
    '<w:footerReference w:type="default" r:id="rIdCoverFtr"/>'
    '<w:pgSz w:w="11909" w:h="16834"/>'
    '<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" '
    'w:header="720" w:footer="720" w:gutter="0"/>'
    '</w:sectPr></w:pPr></w:p>'
)
FOOTER_PART = 'word/footerCover.xml'
FOOTER_CT = ('application/vnd.openxmlformats-officedocument'
             '.wordprocessingml.footer+xml')


def extract_cover(template_xml: str) -> str:
    body = template_xml[template_xml.find('<w:body>') + len('<w:body>'):]
    cut = body.find('Tóm tắt đề cương')
    if cut < 0:
        sys.exit('Không tìm thấy ranh giới TOC trong template')
    # TOC của Google Docs nằm trong khối <w:sdt>: nếu đang ở trong sdt thì cắt
    # trước cả khối, không thì cắt ở đầu đoạn chứa TOC
    sdt_open = body.rfind('<w:sdt>', 0, cut)
    sdt_close = body.rfind('</w:sdt>', 0, cut)
    if sdt_open > sdt_close:
        fragment = body[:sdt_open]
    else:
        start = max(body.rfind('<w:p ', 0, cut), body.rfind('<w:p>', 0, cut))
        fragment = body[:start]
    # bỏ các đoạn trống cuối bìa — nếu giữ sẽ tràn thành một trang trắng
    while True:
        fragment = fragment.rstrip()
        i = max(fragment.rfind('<w:p '), fragment.rfind('<w:p>'))
        tail = fragment[i:]
        if (fragment.endswith('</w:p>') and '<w:t' not in tail
                and '<w:drawing' not in tail):
            fragment = fragment[:i]
        else:
            return fragment


def force_single_spacing(fragment: str) -> str:
    """Chèn <w:spacing giãn đơn> vào mọi <w:pPr> chưa có, đúng vị trí schema
    (trước w:ind / w:jc / w:rPr, sau các phần tử đứng trước spacing)."""
    def fix(m):
        ppr = m.group(0)
        if '<w:spacing' in ppr:
            return ppr
        for anchor in ('<w:ind ', '<w:ind/', '<w:jc ', '<w:rPr>', '</w:pPr>'):
            i = ppr.find(anchor)
            if i >= 0:
                return ppr[:i] + SPACING + ppr[i:]
        return ppr
    fragment = re.sub(r'<w:pPr>.*?</w:pPr>', fix, fragment, flags=re.S)
    # đoạn không có pPr nào
    fragment = re.sub(r'<w:p>(?!<w:pPr)', '<w:p><w:pPr>' + SPACING + '</w:pPr>',
                      fragment)
    return fragment


def merge_root_namespaces(target_xml: str, template_xml: str) -> str:
    t_root = re.search(r'<w:document([^>]*)>', template_xml).group(1)
    g_match = re.search(r'<w:document([^>]*)>', target_xml)
    g_root = g_match.group(1)
    add = ''
    for ns in re.finditer(r'(xmlns:[\w-]+)="([^"]*)"', t_root):
        if ns.group(1) + '=' not in g_root:
            add += f' {ns.group(1)}="{ns.group(2)}"'
    if add:
        target_xml = target_xml.replace('<w:document' + g_root + '>',
                                        '<w:document' + g_root + add + '>', 1)
    return target_xml


def main():
    template_path, target_path = sys.argv[1], sys.argv[2]
    zt = zipfile.ZipFile(template_path)
    template_xml = zt.read('word/document.xml').decode('utf-8')

    cover = extract_cover(template_xml)
    cover = cover.replace('ĐỀ CƯƠNG: ', 'ĐỀ TÀI: ')
    cover = force_single_spacing(cover)
    # ánh xạ lại rId ảnh: rId trong template -> rId mới trong file đích
    rels_t = zt.read('word/_rels/document.xml.rels').decode('utf-8')
    rid_media = dict(re.findall(
        r'Id="(rId\d+)"[^>]*Target="media/([^"]+)"', rels_t))
    for old, media in rid_media.items():
        cover = cover.replace(f'"{old}"', f'"rIdCover{media.split(".")[0]}"')

    footer_xml = zt.read('word/footer1.xml').decode('utf-8')
    footer_xml = re.sub(r'Hà Nội, ngày [^<]*', 'Hà Nội, năm 2026', footer_xml)

    tmp = tempfile.mkdtemp()
    out = Path(tmp) / 'out.docx'
    zin = zipfile.ZipFile(target_path)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zo:
        for item in zin.namelist():
            data = zin.read(item)
            if item == 'word/document.xml':
                xml = data.decode('utf-8')
                xml = merge_root_namespaces(xml, template_xml)
                # Bìa + section-break riêng (lề đối xứng) rồi mới đến thân bài;
                # thân bài giữ nguyên sectPr cuối (lề đóng-gáy bất đối xứng).
                xml = xml.replace('<w:body>', '<w:body>' + cover + COVER_SECTPR, 1)
                data = xml.encode('utf-8')
            elif item == '[Content_Types].xml':
                ct = data.decode('utf-8')
                if 'Extension="jpeg"' not in ct:
                    ct = ct.replace('</Types>',
                                    '<Default Extension="jpeg" '
                                    'ContentType="image/jpeg"/></Types>')
                if 'Extension="png"' not in ct:
                    ct = ct.replace('</Types>',
                                    '<Default Extension="png" '
                                    'ContentType="image/png"/></Types>')
                ct = ct.replace('</Types>',
                                f'<Override PartName="/{FOOTER_PART}" '
                                f'ContentType="{FOOTER_CT}"/></Types>')
                data = ct.encode('utf-8')
            elif item == 'word/_rels/document.xml.rels':
                rels = data.decode('utf-8')
                extra = ''
                for media in rid_media.values():
                    kind = ('http://schemas.openxmlformats.org/officeDocument/'
                            '2006/relationships/image')
                    extra += (f'<Relationship Id="rIdCover{media.split(".")[0]}" '
                              f'Type="{kind}" Target="media/cover_{media}"/>')
                kind_f = ('http://schemas.openxmlformats.org/officeDocument/'
                          '2006/relationships/footer')
                extra += (f'<Relationship Id="rIdCoverFtr" Type="{kind_f}" '
                          f'Target="footerCover.xml"/>')
                data = rels.replace('</Relationships>',
                                    extra + '</Relationships>').encode('utf-8')
            zo.writestr(item, data)
        for media in rid_media.values():
            zo.writestr(f'word/media/cover_{media}',
                        zt.read(f'word/media/{media}'))
        zo.writestr(FOOTER_PART, footer_xml.encode('utf-8'))
    zin.close()
    shutil.move(str(out), target_path)
    print(f'Đã cấy bìa template vào {target_path}')


if __name__ == '__main__':
    main()
