"""도시 설계도(map/layout.json)를 통째로 키우거나 줄입니다.

사용법:
    python3 tools/scale_layout.py map/layout.json 0.7 [--height 0.85] [--note "설명"]

- 좌표(구역, 물길, 섬, 도로, 다리, 거점, 산 위치)와 월드 경계에 배율을 곱합니다.
- 강 폭, 섬 반지름, 산 반지름, 산 가장자리·도로 주변 완만해지는 거리도 같은 배율로 줄입니다.
- 도로 폭은 실제 크기 그대로라서 바뀌지 않습니다 (설계도에 폭이 없고 생성기가 정함).
- 물 깊이, 땅·물 높이, 채석장 높이는 그대로입니다. 산 높이는 --height 배율을 따로 줍니다.
- version 을 1 올립니다. 플러그인은 version 이 올라간 설계도를 보면 서버 폴더의 옛 설계도를 바꿔 씁니다.
"""
import argparse
import json


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("file")
    ap.add_argument("scale", type=float)
    ap.add_argument("--height", type=float, default=1.0, help="산 높이 배율")
    ap.add_argument("--note", default=None)
    args = ap.parse_args()
    k, hk = args.scale, args.height

    with open(args.file, encoding="utf-8") as f:
        L = json.load(f)

    r = lambda v: int(round(v * k))
    pt = lambda p: [r(p[0]), r(p[1])]
    pts = lambda ps: [pt(p) for p in ps]

    def ellipse(e):
        e["center"] = pt(e["center"])
        e["rx"], e["rz"] = r(e["rx"]), r(e["rz"])

    L["world_border"] = {"min": pt(L["world_border"]["min"]), "max": pt(L["world_border"]["max"])}
    for d in L["districts"]:
        if "ellipse" in d:
            ellipse(d["ellipse"])
        else:
            d["polygon"] = pts(d["polygon"])
    for w in L["water"]:
        for key in ("line", "polygon"):
            if key in w:
                w[key] = pts(w[key])
        if "width" in w:
            w["width"] = r(w["width"])
    for isl in L.get("islands", []):
        if "ellipse" in isl:
            ellipse(isl["ellipse"])
        else:
            isl["center"], isl["radius"] = pt(isl["center"]), r(isl["radius"])
    for road in L["roads"] + L["bridges"]:
        road["line"] = pts(road["line"])
    for h in L["hubs"]:
        h["pos"] = pt(h["pos"])
    for m in L.get("mountains", []):
        for p in m["peaks"]:
            p["pos"], p["radius"] = pt(p["pos"]), r(p["radius"])
            p["height"] = int(round(p["height"] * hk))
        for fl in m.get("flats", []):
            fl["pos"], fl["radius"] = pt(fl["pos"]), r(fl["radius"])
    t = L["terrain"]
    t["mountain_edge_fade"] = r(t["mountain_edge_fade"])
    t["road_fade"] = r(t["road_fade"])

    L["version"] = L.get("version", 1) + 1
    if args.note:
        L["note"] = args.note

    with open(args.file, "w", encoding="utf-8") as f:
        json.dump(L, f, ensure_ascii=False, indent=1)
        f.write("\n")
    b = L["world_border"]
    print(f"v{L['version']}: 경계 {b['min']}~{b['max']}")


if __name__ == "__main__":
    main()
