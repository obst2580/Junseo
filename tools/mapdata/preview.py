"""생성 데이터 미리보기 그림 (검토용)."""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import common as C
import osmload
import roads

FONT = os.path.join(C.CACHE, "NotoSansKR.ttf")
COL = {
    "expressway": (232, 110, 60),
    "primary": (250, 250, 250),
    "secondary": (215, 215, 215),
    "bridge": (60, 60, 70),
    "tunnel": (140, 90, 60),
    "ramp": (240, 170, 40),
}


def render(net, out, view=None, width_px=2600, title=""):
    """view: (minx, minz, maxx, maxz) 서울시청 기준 좌표. 없으면 전체."""
    R = C.regions()
    play = R["playable"]
    if view is None:
        minx, minz, maxx, maxz = play.bounds
        m = 300
        view = (minx - m, minz - m, maxx + m, maxz + m)
    minx, minz, maxx, maxz = view
    s = width_px / (maxx - minx)
    H = int((maxz - minz) * s)
    img = Image.new("RGB", (width_px, H), (60, 64, 70))
    d = ImageDraw.Draw(img, "RGBA")

    def px(xy):
        xy = np.asarray(xy)
        return [((x - minx) * s, (z - minz) * s) for x, z in xy]

    def fill(geom, col):
        for p in getattr(geom, "geoms", [geom]):
            if p.is_empty:
                continue
            d.polygon(px(p.exterior.coords), fill=col)
            for h in p.interiors:
                d.polygon(px(h.coords), fill=(0, 0, 0, 0))

    fill(play, (232, 228, 218))
    areas = osmload.load_areas()
    for kind, col in (("forest", (176, 206, 160)), ("park", (190, 220, 170)), ("farm", (236, 222, 170)), ("urban", (220, 214, 210))):
        g = areas.get(kind)
        if g is not None:
            fill(g.intersection(play), col)
    fill(roads.sea_polygon(), (150, 190, 222))
    w = osmload.water_polygons()
    if w is not None:
        fill(w, (150, 190, 222))

    def stroke(pts, col, wid):
        p = px(pts)
        if len(p) >= 2:
            d.line(p, fill=col, width=max(1, int(round(wid * s))), joint="curve")

    for tier in (0, 1):
        for e in net["edges"]:
            if e["tier"] != tier:
                continue
            for pc in e["pieces"]:
                col = COL[e["cls"]]
                if pc["kind"] == "bridge":
                    stroke(pc["pts"], COL["bridge"], e["w"] + 8)
                if pc["kind"] == "tunnel":
                    col = COL["tunnel"]
                stroke(pc["pts"], col, e["w"])
    for r in net.get("ramps", []):
        stroke(r["pts"], COL["ramp"], r.get("w", 14))
    if s > 0.3:
        for n in net["nodes"]:
            x, z = (n["x"] - minx) * s, (n["z"] - minz) * s
            rr = 3
            d.ellipse([x - rr, z - rr, x + rr, z + rr], outline=(200, 30, 30), width=1)
    if title:
        f = ImageFont.truetype(FONT, 28) if os.path.exists(FONT) else None
        d.rectangle([0, 0, width_px, 44], fill=(20, 22, 28))
        d.text((16, 22), title, font=f, fill=(255, 255, 255), anchor="lm")
    img.save(out, quality=88)
    print("그림", out, img.size)


if __name__ == "__main__":
    net = json.load(open(sys.argv[1]))
    outdir = sys.argv[2]
    render(net, os.path.join(outdir, "net_full.jpg"), title="도로망 전체")
    # 여의도·한강 (서울시청 기준 좌표)
    render(net, os.path.join(outdir, "net_yeouido.jpg"), (-3600, 500, 600, 3200), 2000, "여의도·한강 일대")
    render(net, os.path.join(outdir, "net_gangnam.jpg"), (1500, 1800, 6000, 4800), 2000, "강남·잠실 일대")
    render(net, os.path.join(outdir, "net_downtown.jpg"), (-1800, -1400, 1800, 1200), 2000, "도심(시청·광화문)")
    render(net, os.path.join(outdir, "net_incheon.jpg"), (-17000, -1500, -6000, 6500), 2000, "인천 일대")
