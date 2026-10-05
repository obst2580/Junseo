"""건물 간판에 쓸 한글 글자 그림(블록 1칸 = 1픽셀)을 만듭니다.

사용법:
    python3 tools/gen_hangul_glyphs.py

- 16칸 글자: GNU Unifont (/usr/share/fonts/opentype/unifont/unifont.otf). 큰 간판(정문, 건물 이름)용.
- 12칸 글자: 문천이 정흑(WenQuanYi Zen Hei, /usr/share/fonts/truetype/wqy/wqy-zenhei.ttc). 가게 간판용.
- 결과: citymap/src/main/resources/buildings/hangul-glyphs.txt
  줄마다 "크기 글자 폭 줄1 줄2 ..." (줄은 왼쪽 픽셀이 높은 비트인 16진수)
- 간판 글씨를 새로 쓰면 WORDS 에 넣고 다시 실행합니다 (없는 글자는 검사가 잡아 줌).
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "citymap/src/main/resources/buildings/hangul-glyphs.txt"

FONTS = {
    16: ("/usr/share/fonts/opentype/unifont/unifont.otf", 0, 128),
    12: ("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", 1, 110),
}

# 간판 글씨 (건물 코드에서 쓰는 것과 같아야 함)
WORDS = [
    # 큰 간판
    "대형시장", "청라돔", "준서국제공항", "출국", "도착",
    # 시장 안 가게·구역 간판
    "빈대떡", "마약김밥", "육회", "떡볶이", "순대", "칼국수", "녹두전", "먹자골목",
    "원단", "한복", "이불", "수산시장", "건어물", "반찬", "과일", "채소", "그릇", "잡화", "참기름", "떡집",
    "동문", "서문", "남문", "북문", "정문", "주차장", "화장실", "할인", "포목",
    # 시장 둘레 상가
    "약국", "식당", "치킨", "노래방", "부동산", "세탁소", "철물점", "미용실", "편의점", "분식", "호프", "전당포",
    "중국집", "슈퍼", "정육점", "카페", "학원", "여관", "다방", "사진관", "안경", "공구", "고물상", "카센터",
]


def glyph(font, ch, size, threshold):
    im = Image.new("L", (size * 2, size * 2), 0)
    ImageDraw.Draw(im).text((0, 0), ch, font=font, fill=255)
    px = im.load()
    rows = []
    for y in range(size * 2):
        bits = [1 if px[x, y] >= threshold else 0 for x in range(size * 2)]
        rows.append(bits)
    # 세로: 모든 글자를 같은 칸(size 줄)에 맞추려고 글꼴의 위아래 기준을 씀
    asc, desc = font.getmetrics()
    top = max(0, (asc + desc - size) // 2)
    rows = rows[top:top + size]
    width = size
    return width, rows


def main():
    chars = sorted({c for w in WORDS for c in w if c.strip()})
    lines = ["# tools/gen_hangul_glyphs.py 로 만든 파일. 크기 글자 폭 줄들(16진수, 왼쪽 픽셀이 높은 비트)"]
    for size, (path, index, threshold) in FONTS.items():
        font = ImageFont.truetype(path, size, index=index)
        for ch in chars:
            width, rows = glyph(font, ch, size, threshold)
            hexrows = []
            for r in rows:
                v = 0
                for x in range(width):
                    v = (v << 1) | r[x]
                hexrows.append(format(v, "x"))
            lines.append(f"{size} {ch} {width} " + " ".join(hexrows))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(chars)}자 × {len(FONTS)}크기 → {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
