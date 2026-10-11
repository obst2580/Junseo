"""3D 미리보기 데이터: build/preview/mapdata.js

지형 높이 격자(40스터드 간격)와 생성된 도로 파트(build/preview/parts.bin), 랜드마크·구 이름을 묶는다.
미리보기 페이지(preview/index.html)가 읽는다.
"""
import base64
import json
import os

import numpy as np
from PIL import Image, ImageDraw

import common as C
import terrain as T

STEP = 5  # 계산 격자(8스터드) 5칸 = 40스터드

PALETTE = {
    "Grass": (106, 140, 72),
    "LeafyGrass": (78, 112, 58),
    "Rock": (110, 106, 100),
    "Sand": (196, 180, 140),
    "Ground": (120, 96, 66),
    "Pavement": (172, 158, 142),
    "Concrete": (140, 140, 136),
    "Mud": (84, 74, 62),
    "Slate": (92, 96, 102),
    "Asphalt": (60, 60, 60),
}


def main():
    terr = T.load()
    roads = json.load(open(os.path.join(C.OUT, "roads.json")))
    meta = json.load(open(os.path.join(C.OUT, "meta.json")))
    h = terr.h_work.copy()

    # 지상 도로 아래 지형을 도로 높이로 눌러 둔다 (실제 게임에서는 지형 생성기가 깎고 채움)
    img = Image.new("F", (terr.wx, terr.wz), -9999.0)
    d = ImageDraw.Draw(img)
    for e in roads["edges"]:
        if e["tier"] != 0:
            continue
        walk = 10 if e["cls"] == "primary" else 8
        # 미리보기 격자가 40스터드 간격이라 도로 둘레를 넉넉히 눌러야 꼭짓점이 잡힌다
        width = max(1, int((e["w"] + 2 * walk + 110) / T.WORK))
        for pc in e["pieces"]:
            if pc["kind"] != "road":
                continue
            pts = np.asarray(pc["pts"])
            for a, b in zip(pts[:-1], pts[1:]):
                y = (a[1] + b[1]) / 2 - 0.6
                pa = ((a[0] - terr.x0) / T.WORK, (a[2] - terr.z0) / T.WORK)
                pb = ((b[0] - terr.x0) / T.WORK, (b[2] - terr.z0) / T.WORK)
                d.line([pa, pb], fill=float(y), width=width)
    for n in roads["nodes"]:
        if n["tier"] == 0 and n["deg"] >= 3:
            cx, cz, r = (n["x"] - terr.x0) / T.WORK, (n["z"] - terr.z0) / T.WORK, 90 / T.WORK
            d.ellipse([cx - r, cz - r, cx + r, cz + r], fill=float(n["y"] - 0.6))
    rh = np.asarray(img)
    h = np.where(rh > -9000, rh, h)

    hs = h[::STEP, ::STEP]
    nz, nx = hs.shape
    st16 = T.GRID // T.WORK
    mi = np.clip((np.arange(nz) * STEP) // st16, 0, terr.material.shape[0] - 1)
    mj = np.clip((np.arange(nx) * STEP) // st16, 0, terr.material.shape[1] - 1)
    mat = terr.material[np.ix_(mi, mj)]
    play = terr.playable_work[::STEP, ::STEP]
    mat = np.where(play, mat, mat + 100).astype(np.uint8)  # 100 이상: 플레이 구역 밖(어둡게)

    parts = open(os.path.join(C.ROOT, "build", "preview", "parts.bin"), "rb").read()

    labels = []
    for lm in meta["landmarks"]:
        labels.append({"t": lm["name"], "x": lm["x"], "y": lm["y"], "z": lm["z"], "k": "lm"})
    for dd in meta["districts"]:
        ring = dd["rings"][0] if dd["rings"] else None
        if not ring:
            continue
        a = np.asarray(ring)
        name = dd["name"].split(" ", 1)[1]
        labels.append({"t": name, "x": float(a[:, 0].mean()), "y": 0, "z": float(a[:, 1].mean()), "k": "gu"})

    seg_counts = {0: 0, 1: 0}
    for e in roads["edges"]:
        for pc in e["pieces"]:
            seg_counts[e["tier"]] += len(pc["pts"]) - 1
    data = {
        "version": meta["version"],
        "terrain": {
            "nx": int(nx), "nz": int(nz), "step": T.WORK * STEP, "x0": terr.x0, "z0": terr.z0,
            "h": base64.b64encode(np.round(hs * 2).astype(np.int16).tobytes()).decode(),
            "m": base64.b64encode(mat.tobytes()).decode(),
            "palette": [PALETTE[n] for n in T.MATERIALS],
            "water": T.WATER_LEVEL,
        },
        "parts": base64.b64encode(parts).decode(),
        "partCount": len(parts) // 22,
        "labels": labels,
        "spawn": meta["spawn"],
        "walls": meta["walls"],
        "size": [round(terr.R["playable"].bounds[2] - terr.R["playable"].bounds[0]), round(terr.R["playable"].bounds[3] - terr.R["playable"].bounds[1])],
        "ramps": len(roads["ramps"]),
    }
    out = os.path.join(C.ROOT, "build", "preview", "mapdata.js")
    with open(out, "w") as f:
        f.write("window.MAPDATA=" + json.dumps(data, ensure_ascii=False, separators=(",", ":")) + ";\n")
    print("저장", out, f"{os.path.getsize(out) / 1e6:.1f}MB", "격자", nx, "x", nz)


if __name__ == "__main__":
    main()
