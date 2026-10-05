"""map/layout.json 을 읽어 도시 설계도 SVG 를 그립니다.

사용법:  python3 map/render_layout.py map/layout.json docs/images/map-v1.svg
좌표는 블록 단위입니다 (X = 동쪽, Z = 남쪽, 1블록 = 1m).
"""
import json
import math
import sys
from xml.sax.saxutils import escape

DISTRICT_FILL = "#f4d9a8"
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


def scaled(layout, k):
    """그림 크기를 일정하게 맞추려고 좌표만 k 배 한 사본 (글자·선 굵기는 그대로)"""
    import copy
    L = copy.deepcopy(layout)
    sp = lambda p: [p[0] * k, p[1] * k]
    L["world_border"] = {"min": sp(L["world_border"]["min"]), "max": sp(L["world_border"]["max"])}
    for d in L["districts"]:
        if "ellipse" in d:
            e = d["ellipse"]
            e["center"], e["rx"], e["rz"] = sp(e["center"]), e["rx"] * k, e["rz"] * k
        else:
            d["polygon"] = [sp(p) for p in d["polygon"]]
    for w in L["water"]:
        for key in ("line", "polygon"):
            if key in w:
                w[key] = [sp(p) for p in w[key]]
        if "width" in w:
            w["width"] = w["width"] * k
    for isl in L.get("islands", []):
        if "ellipse" in isl:
            e = isl["ellipse"]
            e["center"], e["rx"], e["rz"] = sp(e["center"]), e["rx"] * k, e["rz"] * k
        else:
            isl["center"], isl["radius"] = sp(isl["center"]), isl["radius"] * k
    for r in L["roads"] + L.get("bridges", []):
        r["line"] = [sp(p) for p in r["line"]]
    for h in L["hubs"]:
        h["pos"] = sp(h["pos"])
    return L


def render(layout):
    # 그림은 언제나 가로 10000 단위로 그립니다 (도시가 작아도 글자 크기가 같게)
    width = layout["world_border"]["max"][0] - layout["world_border"]["min"][0]
    k = 10000 / width
    layout = scaled(layout, k)
    (bx0, bz0), (bx1, bz1) = layout["world_border"]["min"], layout["world_border"]["max"]
    bx0, bz0, bx1, bz1 = round(bx0), round(bz0), round(bx1), round(bz1)
    legend_w = 2300
    vx, vz, vw, vh = bx0 - 150, bz0 - 450, (bx1 - bx0) + 300 + legend_w, (bz1 - bz0) + 600
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vx} {vz} {vw} {vh}" '
         f'width="{vw // 5}" height="{vh // 5}" font-family="{FONT}">',
         f'<rect x="{vx}" y="{vz}" width="{vw}" height="{vh}" fill="#ffffff"/>',
         f'<defs><clipPath id="border"><rect x="{bx0}" y="{bz0}" width="{bx1 - bx0}" height="{bz1 - bz0}"/></clipPath></defs>',
         f'<rect x="{bx0}" y="{bz0}" width="{bx1 - bx0}" height="{bz1 - bz0}" fill="{LAND}"/>',
         '<g clip-path="url(#border)">']

    # 격자 (1km)
    step = round(1000 * k / 2) if k >= 2 else 1000   # 격자 한 칸 = 실제 500m (작은 도시) 또는 1km
    real = lambda v: round(v / k)
    for x in range(bx0 - bx0 % step, bx1 + 1, step):
        o.append(f'<line x1="{x}" y1="{bz0}" x2="{x}" y2="{bz1}" stroke="#c9c2ad" stroke-width="8" stroke-dasharray="40 40"/>')
        o.append(f'<text x="{x}" y="{bz0 - 40}" font-size="90" text-anchor="middle" fill="#777">X {real(x)}</text>')
    for z in range(bz0 - bz0 % step, bz1 + 1, step):
        o.append(f'<line x1="{bx0}" y1="{z}" x2="{bx1}" y2="{z}" stroke="#c9c2ad" stroke-width="8" stroke-dasharray="40 40"/>')
        o.append(f'<text x="{bx0 - 30}" y="{z + 30}" font-size="90" text-anchor="end" fill="#777">Z {real(z)}</text>')

    # 바다 → 섬 (구역보다 먼저: 섬 위의 공항 구역이 보이게)
    for w in layout["water"]:
        if w["kind"] == "sea":
            o.append(f'<polygon points="{pts(w["polygon"])}" fill="{WATER}"/>')
    for isl in layout.get("islands", []):
        if "ellipse" in isl:
            o.append(f'<polygon points="{pts(ellipse_points(isl["ellipse"]))}" fill="{LAND}" stroke="#8a8a8a" stroke-width="8"/>')
        else:
            cx, cz = isl["center"]
            o.append(f'<circle cx="{cx}" cy="{cz}" r="{isl["radius"]}" fill="#d9d2bd" stroke="#4a4a4a" stroke-width="14"/>')

    # 구역
    labels = []
    for d in layout["districts"]:
        p = ellipse_points(d["ellipse"]) if "ellipse" in d else d["polygon"]
        o.append(f'<polygon points="{pts(p)}" fill="{DISTRICT_FILL}" stroke="#4a4a4a" stroke-width="14"/>')
        cx, _ = centroid(p)
        zs = [q[1] for q in p]
        # 구역 이름은 구역 위쪽에 둬서 시설 이름과 겹치지 않게 합니다
        labels.append(((cx, min(zs) + (max(zs) - min(zs)) * 0.3), d["name"]))

    # 한강·샛강 (구역 위에 그려서 강이 구역을 가르게)
    for w in layout["water"]:
        if w["kind"] != "sea":
            o.append(f'<polyline points="{pts(w["line"])}" fill="none" stroke="{WATER}" stroke-width="{w["width"]}" stroke-linejoin="round" stroke-linecap="round"/>')

    # 도로 (물 위에 그림: 물을 건너는 곳이 곧 다리). 중로 → 대로 → 고속도로 순서로 위에 쌓음
    order = {"street": 0, "runway": 1, "arterial": 2, "tunnel": 3, "highway": 4}
    for r in sorted(layout["roads"], key=lambda r: (order.get(r["kind"], 0), not r["id"].startswith("S-"))):
        line = pts(r["line"])
        if r["kind"] == "highway":
            o.append(f'<polyline points="{line}" fill="none" stroke="#d6362f" stroke-width="70" stroke-linejoin="round" stroke-linecap="round"/>')
        elif r["kind"] == "arterial" and r["id"].startswith("S-"):
            # 구역 안 길: 규격은 대로와 같지만 그림에서는 조금 가늘고 옅게 (큰길이 잘 보이게)
            o.append(f'<polyline points="{line}" fill="none" stroke="#f5b06b" stroke-width="28" stroke-linejoin="round" stroke-linecap="round"/>')
        elif r["kind"] == "arterial":
            o.append(f'<polyline points="{line}" fill="none" stroke="#f0892a" stroke-width="45" stroke-linejoin="round" stroke-linecap="round"/>')
        elif r["kind"] == "street":
            o.append(f'<polyline points="{line}" fill="none" stroke="#8a8a8a" stroke-width="22" stroke-linejoin="round" stroke-linecap="round"/>')
        elif r["kind"] == "runway":
            o.append(f'<polyline points="{line}" fill="none" stroke="#5b5b60" stroke-width="45" stroke-linecap="butt"/>')
        elif r["kind"] == "tunnel":
            o.append(f'<polyline points="{line}" fill="none" stroke="#7b4bc4" stroke-width="40" stroke-dasharray="90 60"/>')

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
             f'준서 시티 도시 설계도 v{layout["version"]} — 압축 서울·인천 {real(bx1 - bx0) / 1000:g}×{real(bz1 - bz0) / 1000:g}km 범위 (1블록 = 1m)</text>')
    rows = [("rect", DISTRICT_FILL, "구역"), ("rect", WATER, "한강·바다"),
            ("line", "#d6362f", "고속도로 (왕복 6차로)"), ("line", "#f0892a", "대로 (왕복 4차로)"),
            ("line", "#f5b06b", "구역 안 길 (대로와 같은 규격)"), ("dash", "#7b4bc4", "남산터널"),
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
    bar = round(500 * k) if k >= 2 else 1000
    o.append(f'<line x1="{lx}" y1="{y}" x2="{lx + bar}" y2="{y}" stroke="#111" stroke-width="30"/>')
    o.append(f'<text x="{lx + bar / 2}" y="{y + 130}" font-size="95" text-anchor="middle">{real(bar)}m ({real(bar):,}블록)</text>')
    o.append(f'<text x="{lx}" y="{y + 330}" font-size="85" fill="#555">북쪽이 위 (Z−), 동쪽이 오른쪽 (X+)</text>')
    o.append(f'<text x="{lx}" y="{y + 450}" font-size="85" fill="#555">설계도 범위 {real(bx1 - bx0)}×{real(bz1 - bz0)}</text>')
    o.append('</svg>')
    return "\n".join(o)


if __name__ == "__main__":
    layout = json.load(open(sys.argv[1], encoding="utf-8"))
    open(sys.argv[2], "w", encoding="utf-8").write(render(layout))
    print("wrote", sys.argv[2])
