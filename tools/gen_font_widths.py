"""마인크래프트 기본 글꼴의 글자 폭 표를 만듭니다 (액션바 HUD 위치 계산용).

사용법:
    python3 tools/gen_font_widths.py <마인크래프트 assets/minecraft 폴더> > src/main/resources/hud/default-font-widths.txt

필요한 파일 (클라이언트 jar 안에 있음):
    font/include/default.json, font/include/unifont.json, font/unifont.zip,
    textures/font/ascii.png, accented.png, nonlatin_european.png

계산 방법은 클라이언트와 같습니다.
    비트맵 글자: (int)(0.5 + 실제폭 * 배율) + 1     (실제폭 = 가장 오른쪽 불투명 열 + 1)
    unifont 글자: (오른쪽 - 왼쪽 + 1) // 2 + 1     (size_overrides 가 있으면 그 값)
결과는 "시작코드 폭" 줄의 목록입니다. 폭이 바뀌는 곳에서만 줄이 생깁니다 (BMP 0000~FFFF).
폭 뒤의 u 는 unifont 글자라는 뜻입니다 (굵게 하면 +0.5, 나머지는 +1).
어느 글꼴에도 없는 글자는 클라이언트의 "없는 글자" 네모(폭 6)로 칩니다.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

from PIL import Image


def load_json(path):
    # 마인크래프트 json 에는 가끔 끝 쉼표가 있어서 지웁니다
    text = re.sub(r",(\s*[\]}])", r"\1", Path(path).read_text(encoding="utf-8"))
    return json.loads(text)


def bitmap_widths(root, provider, out):
    file = provider["file"].split(":", 1)[1]
    img = Image.open(root / "textures" / file).convert("RGBA")
    rows = provider["chars"]
    cols = max(len(r) for r in rows)
    cw, ch = img.width // cols, img.height // len(rows)
    scale = provider.get("height", 8) / ch
    px = img.load()
    for j, row in enumerate(rows):
        for i, c in enumerate(row):
            cp = ord(c)
            if cp == 0 or cp in out:
                continue
            actual = 0
            for x in range(cw - 1, -1, -1):
                if any(px[i * cw + x, j * ch + y][3] != 0 for y in range(ch)):
                    actual = x + 1
                    break
            out[cp] = int(0.5 + actual * scale) + 1


def unifont_widths(root, provider, out):
    overrides = []
    for o in provider.get("size_overrides", []):
        overrides.append((ord(o["from"]), ord(o["to"]), o["left"], o["right"]))
    zpath = root / provider["hex_file"].split(":", 1)[1]
    with zipfile.ZipFile(zpath) as z:
        for name in z.namelist():
            if not name.endswith(".hex"):
                continue
            for line in z.read(name).decode("ascii").splitlines():
                code, data = line.split(":")
                cp = int(code, 16)
                if cp > 0xFFFF or cp in out:
                    continue
                bit_width = len(data) * 4 // 16
                rows = [int(data[k:k + len(data) // 16], 16) for k in range(0, len(data), len(data) // 16)]
                left = right = None
                for lo, hi, l, r in overrides:
                    if lo <= cp <= hi:
                        left, right = l, r
                        break
                if left is None:
                    mask = 0
                    for r in rows:
                        mask |= r << (32 - bit_width)
                    if mask == 0:
                        left, right = 0, bit_width
                    else:
                        left = 32 - mask.bit_length()
                        right = 32 - ((mask & -mask).bit_length() - 1) - 1
                out[cp] = f"{(right - left + 1) // 2 + 1}u"


def main():
    root = Path(sys.argv[1])
    widths = {0x20: 4, 0x200C: 0}  # include/space.json
    for p in load_json(root / "font/include/default.json")["providers"]:
        bitmap_widths(root, p, widths)
    unifont = load_json(root / "font/include/unifont.json")["providers"]
    for p in unifont:
        if p.get("filter", {}).get("jp"):
            continue  # 일본어 글꼴 옵션은 쓰지 않음
        unifont_widths(root, p, widths)
    print("# 마인크래프트 기본 글꼴 글자 폭 (tools/gen_font_widths.py 로 만듦). 줄 = 시작코드(16진수) 폭")
    last = None
    for cp in range(0x10000):
        w = str(widths.get(cp, 6))
        if w != last:
            print(f"{cp:04X} {w}")
            last = w


if __name__ == "__main__":
    main()
