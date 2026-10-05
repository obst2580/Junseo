"""map/layout.json 을 읽어 도시 설계도 SVG 를 그립니다.

사용법:  python3 map/render_layout.py map/layout.json docs/images/map-v1.svg
좌표는 블록 단위입니다 (X = 동쪽, Z = 남쪽, 1블록 = 1m).
"""
import json
import math
import sys
from xml.sax.saxutils import escape

PHASE_FILL = {1: "#f4c27a", 2: "#a9d4a3", 3: "#b7c9e8"}
WATER = "#86c2e6"
LAND = "#efeadb"
FONT = "'WenQuanYi Zen Hei','Noto Sans KR','Malgun Gothic',sans-serif"


def pts(points):
    return " ".join(f"{x},{z}" for x, z in points)


def ellipse_points(e, n=48):
    cx, cz = e["center"]
    a = math.radians(e.get("angle", 0))
    out = []
    for i in range(n):
        t = 2 * math.pi * i / n
        x, z = e["rx"] * math.cos(t), e["rz"] * math.sin(t)
        out.append((round(cx + x * math.cos(a) - z * math.sin(a)), round(cz + x * math.sin(a) + z * math.cos(a))))
    return out


def centroid(points):
    return sum(p[0] for p in points) / len(points), sum(p[1] for p in points) / len(points)


def render(layout):
    (bx0, bz0), (bx1, bz1) = layout["world_border"]["min"], layout["world_border"]["max"]
    legend_w = 2300
    vx, vz, vw, vh = bx0 - 150, bz0 - 450, (bx1 - bx0) + 300 + legend_w, (bz1 - bz0) + 600
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vx} {vz} {vw} {vh}" '
         f'width="{vw // 5}" height="{vh // 5}" font-family="{FONT}">',
         f'<rect x="{vx}" y="{vz}" width="{vw}" height="{vh}" fill="#ffffff"/>',
         f'<defs><clipPath id="border"><rect x="{bx0}" y="{bz0}" width="{bx1 - bx0}" height="{bz1 - bz0}"/></clipPath></defs>',
         f'<rect x="{bx0}" y="{bz0}" width="{bx1 - bx0}" height="{bz1 - bz0}" fill="{LAND}"/>',
         '<g clip-path="url(#border)">']

    # 격자 (1km)
    for x in range(bx0 - bx0 % 1000, bx1 + 1, 1000):
        o.append(f'<line x1="{x}" y1="{bz0}" x2="{x}" y2="{bz1}" stroke="#c9c2ad" stroke-width="8" stroke-dasharray="40 40"/>')
        o.append(f'<text x="{x}" y="{bz0 - 40}" font-size="90" text-anchor="middle" fill="#777">X {x}</text>')
    for z in range(bz0 - bz0 % 1000, bz1 + 1, 1000):
        o.append(f'<line x1="{bx0}" y1="{z}" x2="{bx1}" y2="{z}" stroke="#c9c2ad" stroke-width="8" stroke-dasharray="40 40"/>')
        o.append(f'<text x="{bx0 - 30}" y="{z + 30}" font-size="90" text-anchor="end" fill="#777">Z {z}</text>')

    # 구역
    labels = []
    for d in layout["districts"]:
        p = ellipse_points(d["ellipse"]) if "ellipse" in d else d["polygon"]
        o.append(f'<polygon points="{pts(p)}" fill="{PHASE_FILL[d["open_phase"]]}" stroke="#4a4a4a" stroke-width="14"/>')
        cx, _ = centroid(p)
        zs = [q[1] for q in p]
        # 구역 이름은 구역 위쪽에 둬서 시설 이름과 겹치지 않게 합니다
        labels.append(((cx, min(zs) + (max(zs) - min(zs)) * 0.3), d["name"]))

    # 도로 (물보다 먼저 그려서, 다리가 아닌 곳은 물에 가려지게)
    for r in layout["roads"]:
        line = pts(r["line"])
        if r["kind"] == "highway":
            o.append(f'<polyline points="{line}" fill="none" stroke="#d6362f" stroke-width="70" stroke-linejoin="round" stroke-linecap="round"/>')
        elif r["kind"] == "arterial":
            o.append(f'<polyline points="{line}" fill="none" stroke="#f0892a" stroke-width="45" stroke-linejoin="round" stroke-linecap="round"/>')
        elif r["kind"] == "runway":
            o.append(f'<polyline points="{line}" fill="none" stroke="#5b5b60" stroke-width="45" stroke-linecap="butt"/>')
        elif r["kind"] == "tunnel":
            o.append(f'<polyline points="{line}" fill="none" stroke="#7b4bc4" stroke-width="40" stroke-dasharray="90 60"/>')

    # 물
    for w in layout["water"]:
        if w["kind"] == "sea":
            o.append(f'<polygon points="{pts(w["polygon"])}" fill="{WATER}"/>')
        else:
            o.append(f'<polyline points="{pts(w["line"])}" fill="none" stroke="{WATER}" stroke-width="{w["width"]}" stroke-linejoin="round" stroke-linecap="round"/>')

    # 섬
    for isl in layout.get("islands", []):
        cx, cz = isl["center"]
        o.append(f'<circle cx="{cx}" cy="{cz}" r="{isl["radius"]}" fill="#d9d2bd" stroke="#4a4a4a" stroke-width="14"/>')

    # 다리 (물 위)
    for br in layout["bridges"]:
        color = "#d6362f" if br["id"].startswith("HB") else "#f2c200"
        o.append(f'<polyline points="{pts(br["line"])}" fill="none" stroke="{color}" stroke-width="55" stroke-linecap="butt"/>')
        o.append(f'<polyline points="{pts(br["line"])}" fill="none" stroke="#333" stroke-width="6" stroke-dasharray="20 20"/>')

    # 구역 이름
    for (cx, cz), name in labels:
        o.append(f'<text x="{cx:.0f}" y="{cz:.0f}" font-size="170" font-weight="bold" text-anchor="middle" '
                 f'fill="#222" stroke="{LAND}" stroke-width="20" paint-order="stroke">{escape(name)}</text>')

    o.append('</g>')
    o.append(f'<rect x="{bx0}" y="{bz0}" width="{bx1 - bx0}" height="{bz1 - bz0}" fill="none" stroke="#555" stroke-width="20"/>')

    # 거점
    for h in layout["hubs"]:
        x, z = h["pos"]
        star = h["id"] in ("spawn", "plaza", "hospital", "police_north", "police_south", "port", "prison")
        o.append(f'<circle cx="{x}" cy="{z}" r="{55 if star else 38}" fill="{"#c0162b" if star else "#1f5fa8"}" stroke="#fff" stroke-width="14"/>')
        o.append(f'<text x="{x + 70}" y="{z + 30}" font-size="85" fill="#111" stroke="#fff" stroke-width="14" '
                 f'paint-order="stroke">{escape(h["name"])}</text>')

    # 제목과 범례
    lx = bx1 + 250
    o.append(f'<text x="{bx0}" y="{bz0 - 230}" font-size="190" font-weight="bold" fill="#111">'
             f'준서 시티 도시 설계도 v{layout["version"]} — 압축 서울·인천 (1블록 = 1m)</text>')
    rows = [("rect", PHASE_FILL[1], "1차 오픈 구역"), ("rect", PHASE_FILL[2], "2차 오픈 구역"),
            ("rect", PHASE_FILL[3], "3차 오픈 구역"), ("rect", WATER, "한강·바다"),
            ("line", "#d6362f", "고속도로 (기존 도로 본뜸)"), ("line", "#f0892a", "대로 (신규)"),
            ("dash", "#7b4bc4", "남산터널"), ("line", "#f2c200", "한강 다리"),
            ("dot", "#c0162b", "주요 거점 (사람이 몰리는 곳)"), ("dot", "#1f5fa8", "그 밖의 시설")]
    y = bz0 + 100
    o.append(f'<text x="{lx}" y="{y}" font-size="130" font-weight="bold">범례</text>')
    for kind, color, label in rows:
        y += 190
        if kind == "rect":
            o.append(f'<rect x="{lx}" y="{y - 90}" width="200" height="110" fill="{color}" stroke="#4a4a4a" stroke-width="10"/>')
        elif kind == "line":
            o.append(f'<line x1="{lx}" y1="{y - 35}" x2="{lx + 200}" y2="{y - 35}" stroke="{color}" stroke-width="55"/>')
        elif kind == "dash":
            o.append(f'<line x1="{lx}" y1="{y - 35}" x2="{lx + 200}" y2="{y - 35}" stroke="{color}" stroke-width="40" stroke-dasharray="60 40"/>')
        else:
            o.append(f'<circle cx="{lx + 100}" cy="{y - 35}" r="50" fill="{color}"/>')
        o.append(f'<text x="{lx + 260}" y="{y}" font-size="95">{escape(label)}</text>')
    y += 300
    o.append(f'<line x1="{lx}" y1="{y}" x2="{lx + 1000}" y2="{y}" stroke="#111" stroke-width="30"/>')
    o.append(f'<text x="{lx + 500}" y="{y + 130}" font-size="95" text-anchor="middle">1km (1,000블록)</text>')
    o.append(f'<text x="{lx}" y="{y + 330}" font-size="85" fill="#555">북쪽이 위 (Z−), 동쪽이 오른쪽 (X+)</text>')
    o.append(f'<text x="{lx}" y="{y + 450}" font-size="85" fill="#555">월드 경계 {bx1 - bx0}×{bz1 - bz0}</text>')
    o.append('</svg>')
    return "\n".join(o)


if __name__ == "__main__":
    layout = json.load(open(sys.argv[1], encoding="utf-8"))
    open(sys.argv[2], "w", encoding="utf-8").write(render(layout))
    print("wrote", sys.argv[2])
