"""구역마다 골목 대로(왕복 4차로, 대로와 같은 규격)를 격자로 깔아 도시 도로망을 완성합니다.

사용법:
    python3 tools/design_streets.py map/layout.json

- 손으로 설계한 길(고속도로·대로·터널·활주로)은 그대로 두고, 이 도구가 만든 길(id 가 S- 로 시작)만 새로 만듭니다.
- 고속도로 말고는 모든 길이 같은 규격(왕복 4차로 + 인도)입니다 [확정].
- 구역마다 layout.json 의 "street_plan" (방향·간격·기준점)대로 격자를 깝니다.
- 물(한강·샛강·바다), 산 구역, 활주로, 거점 표시 자리, 나란히 달리는 큰길 옆은 피합니다.
- 길 끝이 다른 길에 닿지 않으면 마지막 교차로까지 잘라 냅니다. 그래서 막다른 길이 없습니다.
- 큰길과 이어지지 않는 길 묶음은 버립니다.
"""
import json
import math
import sys

STREET_KIND = "arterial"  # 고속도로 외에는 모두 같은 규격
STREET_HALF = 12          # 절반 폭 (차도 8 + 인도 4)
HALF = {"highway": 14, "arterial": 12, "street": 7, "tunnel": 6, "runway": 22.5}
INSET = 10               # 구역 경계에서 이만큼 안쪽까지만 (이웃 구역 길과 겹치지 않게)
WATER_GAP = 6            # 물가에서 길 가장자리까지 띄울 거리
PARALLEL_DEG = 30        # 이보다 비슷한 방향으로 가까이 달리는 큰길이 있으면 그 자리는 비움
PARALLEL_GAP = 28        # 나란한 큰길과 이 거리(가장자리끼리)보다 가까우면 비움
EXTEND = 60              # 길 끝에서 이만큼 안에 큰길이 있으면 이어 붙임
MIN_LEN = 30


def ellipse_points(cx, cz, rx, rz, angle, n=64):
    a = math.radians(angle)
    out = []
    for i in range(n):
        t = 2 * math.pi * i / n
        x, z = rx * math.cos(t), rz * math.sin(t)
        out.append((cx + x * math.cos(a) - z * math.sin(a), cz + x * math.sin(a) + z * math.cos(a)))
    return out


def inside(poly, x, z):
    c = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, zi = poly[i]
        xj, zj = poly[j]
        if (zi > z) != (zj > z) and x < (xj - xi) * (z - zi) / (zj - zi) + xi:
            c = not c
        j = i
    return c


def seg_dist(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    l2 = dx * dx + dz * dz
    t = 0 if l2 == 0 else max(0, min(1, ((px - ax) * dx + (pz - az) * dz) / l2))
    return math.hypot(px - ax - t * dx, pz - az - t * dz), t


def line_dist(line, x, z):
    best = (1e18, 0)
    for k in range(len(line) - 1):
        d, _ = seg_dist(x, z, *line[k], *line[k + 1])
        if d < best[0]:
            best = (d, k)
    return best


def edge_dist(poly, x, z):
    return min(seg_dist(x, z, *poly[i], *poly[(i + 1) % len(poly)])[0] for i in range(len(poly)))


def seg_intersect(p, p2, q, q2):
    """선분 p-p2 와 q-q2 의 교차 매개변수 (t, u) 또는 None"""
    rx, rz = p2[0] - p[0], p2[1] - p[1]
    sx, sz = q2[0] - q[0], q2[1] - q[1]
    den = rx * sz - rz * sx
    if abs(den) < 1e-9:
        return None
    t = ((q[0] - p[0]) * sz - (q[1] - p[1]) * sx) / den
    u = ((q[0] - p[0]) * rz - (q[1] - p[1]) * rx) / den
    if -1e-9 <= t <= 1 + 1e-9 and -1e-9 <= u <= 1 + 1e-9:
        return t, u
    return None


class World:
    def __init__(self, L):
        self.L = L
        self.districts = {}
        for d in L["districts"]:
            if "ellipse" in d:
                e = d["ellipse"]
                poly = ellipse_points(e["center"][0], e["center"][1], e["rx"], e["rz"], e.get("angle", 0))
            else:
                poly = [tuple(p) for p in d["polygon"]]
            self.districts[d["id"]] = poly
        self.rivers = [(w["line"], w["width"] / 2) for w in L["water"] if w["kind"] != "sea"]
        self.sea = [tuple(p) for w in L["water"] if w["kind"] == "sea" for p in w["polygon"]]
        self.islands = []
        for isl in L.get("islands", []):
            if "ellipse" in isl:
                e = isl["ellipse"]
                self.islands.append(("poly", ellipse_points(e["center"][0], e["center"][1], e["rx"], e["rz"], e.get("angle", 0))))
            else:
                self.islands.append(("circle", (isl["center"][0], isl["center"][1], isl["radius"])))
        self.mountains = [self.districts[m["district"]] for m in L.get("mountains", [])]
        self.majors = [r for r in L["roads"] if not r["id"].startswith("S-")]
        self.hubs = [tuple(h["pos"]) for h in L["hubs"]]

    def on_island(self, x, z):
        for kind, shape in self.islands:
            if kind == "poly" and inside(shape, x, z):
                return True
            if kind == "circle" and math.hypot(x - shape[0], z - shape[1]) <= shape[2]:
                return True
        return False

    def wet(self, x, z, gap):
        """물이거나 물가에서 gap 안쪽"""
        for line, half in self.rivers:
            if line_dist(line, x, z)[0] <= half + gap:
                return True
        if self.sea and not self.on_island(x, z):
            if inside(self.sea, x, z) or edge_dist(self.sea, x, z) <= gap:
                return True
        return False

    def mountain(self, x, z):
        return any(inside(p, x, z) for p in self.mountains)


def direction(line, k):
    (ax, az), (bx, bz) = line[k], line[k + 1]
    l = math.hypot(bx - ax, bz - az)
    return (bx - ax) / l, (bz - az) / l


def allowed(world, poly, x, z, ux, uz):
    if not inside(poly, x, z) or edge_dist(poly, x, z) < INSET:
        return False
    if world.wet(x, z, STREET_HALF + WATER_GAP) or world.mountain(x, z):
        return False
    for hx, hz in world.hubs:
        if math.hypot(x - hx, z - hz) < STREET_HALF + 6:
            return False
    for r in world.majors:
        half = HALF[r["kind"]]
        d, k = line_dist(r["line"], x, z)
        if r["kind"] == "runway" and d < half + STREET_HALF + 12:
            return False
        if d < half + STREET_HALF + PARALLEL_GAP:
            vx, vz = direction(r["line"], k)
            if abs(vx * ux + vz * uz) > math.cos(math.radians(PARALLEL_DEG)):
                return False
    return True


def grid_segments(world, did, plan):
    poly = world.districts[did]
    ang = math.radians(plan["angle"])
    ox, oz = plan["origin"]
    out = []
    for fam, (ux, uz), spacing in ((0, (math.cos(ang), math.sin(ang)), plan["spacing"][1]),
                                   (1, (-math.sin(ang), math.cos(ang)), plan["spacing"][0])):
        nx, nz = -uz, ux
        # 구역을 덮는 범위
        ps = [((x - ox) * nx + (z - oz) * nz, (x - ox) * ux + (z - oz) * uz) for x, z in poly]
        n0, n1 = min(p[0] for p in ps), max(p[0] for p in ps)
        t0, t1 = min(p[1] for p in ps) - EXTEND, max(p[1] for p in ps) + EXTEND
        k0, k1 = math.ceil(n0 / spacing), math.floor(n1 / spacing)
        for k in range(k0, k1 + 1):
            off = k * spacing
            bx, bz = ox + nx * off, oz + nz * off
            run = None
            t = t0
            while t <= t1 + 1:
                x, z = bx + ux * t, bz + uz * t
                ok = t <= t1 and allowed(world, poly, x, z, ux, uz)
                if ok and run is None:
                    run = t
                elif not ok and run is not None:
                    if t - run >= MIN_LEN:
                        out.append({"fam": fam, "district": did, "a": (bx + ux * run, bz + uz * run),
                                    "b": (bx + ux * (t - 1), bz + uz * (t - 1)), "u": (ux, uz)})
                    run = None
                t += 1
    return out


def extend_to_majors(world, seg):
    """끝에서 EXTEND 안에 큰길(가운데선)이 있으면 거기까지 늘림 (물·산은 건너지 않음)"""
    for end in ("a", "b"):
        px, pz = seg[end]
        ux, uz = seg["u"]
        if end == "a":
            ux, uz = -ux, -uz
        far = (px + ux * EXTEND, pz + uz * EXTEND)
        best = None
        for r in world.majors:
            if r["kind"] in ("runway",):
                continue
            line = r["line"]
            for k in range(len(line) - 1):
                hit = seg_intersect((px, pz), far, tuple(line[k]), tuple(line[k + 1]))
                if hit and (best is None or hit[0] < best):
                    best = hit[0]
        if best is not None:
            tx, tz = px + (far[0] - px) * best, pz + (far[1] - pz) * best
            steps = int(math.hypot(tx - px, tz - pz))
            if all(not world.wet(px + ux * i, pz + uz * i, 2) and not world.mountain(px + ux * i, pz + uz * i)
                   for i in range(steps)):
                seg[end] = (tx, tz)


def trim(segments, majors):
    """끝이 다른 길에 닿지 않는 꼬리를 마지막 교차로까지 잘라 냄 (변화가 없을 때까지)"""
    changed = True
    while changed:
        changed = False
        keep = []
        for s in segments:
            ts = []
            a, b = s["a"], s["b"]
            for o in segments:
                if o is s:
                    continue
                hit = seg_intersect(a, b, o["a"], o["b"])
                if hit:
                    ts.append(hit[0])
            for r in majors:
                line = r["line"]
                for k in range(len(line) - 1):
                    hit = seg_intersect(a, b, tuple(line[k]), tuple(line[k + 1]))
                    if hit:
                        ts.append(hit[0])
            if len(ts) < 2 or max(ts) - min(ts) < 1e-6:
                changed = True
                continue
            lo, hi = min(ts), max(ts)
            if lo > 1e-6 or hi < 1 - 1e-6:
                changed = True
                s["a"] = (a[0] + (b[0] - a[0]) * lo, a[1] + (b[1] - a[1]) * lo)
                s["b"] = (a[0] + (b[0] - a[0]) * hi, a[1] + (b[1] - a[1]) * hi)
            if math.hypot(s["b"][0] - s["a"][0], s["b"][1] - s["a"][1]) >= 20:
                keep.append(s)
            else:
                changed = True
        segments = keep
    return segments


def connected(segments, majors):
    """큰길과 이어진 길만 남김"""
    n = len(segments)
    linked = [False] * n
    for i, s in enumerate(segments):
        for r in majors:
            line = r["line"]
            if any(seg_intersect(s["a"], s["b"], tuple(line[k]), tuple(line[k + 1])) for k in range(len(line) - 1)):
                linked[i] = True
                break
    frontier = [i for i in range(n) if linked[i]]
    while frontier:
        i = frontier.pop()
        for j in range(n):
            if not linked[j] and seg_intersect(segments[i]["a"], segments[i]["b"], segments[j]["a"], segments[j]["b"]):
                linked[j] = True
                frontier.append(j)
    return [s for i, s in enumerate(segments) if linked[i]]


OFF_ROAD_HUBS = {"quarry", "hideout", "tower", "prison"}   # 산속·섬: 길이 안 닿아도 됨
HUB_GAP = 10             # 거점 표시는 길 가장자리에서 이만큼 떨어진 곳


def road_edge(roads, x, z):
    return min(line_dist(r["line"], x, z)[0] - HALF[r["kind"]] for r in roads if r["kind"] != "runway")


def place_hubs(world, L):
    """거점이 길 위에 있거나 길에서 멀면, 가까운 길 옆(가장자리에서 HUB_GAP)으로 옮김"""
    roads = [r for r in L["roads"] if r["kind"] != "runway"]
    moved = []
    for h in L["hubs"]:
        if h["id"] in OFF_ROAD_HUBS:
            continue
        x, z = h["pos"]
        if 5 <= road_edge(roads, x, z) <= 25:
            continue
        best = None
        for r in roads:
            d, k = line_dist(r["line"], x, z)
            if d > 200:
                continue
            (ax, az), (bx, bz) = r["line"][k], r["line"][k + 1]
            _, t = seg_dist(x, z, ax, az, bx, bz)
            fx, fz = ax + (bx - ax) * t, az + (bz - az) * t
            ux, uz = direction(r["line"], k)
            for side in (1, -1):
                for slide in range(0, 60, 4):
                    for sgn in (1, -1):
                        cx = fx + ux * slide * sgn - uz * side * (HALF[r["kind"]] + HUB_GAP)
                        cz = fz + uz * slide * sgn + ux * side * (HALF[r["kind"]] + HUB_GAP)
                        edge = road_edge(roads, cx, cz)
                        if edge < HUB_GAP - 0.5 or edge > HUB_GAP + 3 or world.wet(cx, cz, 4) or world.mountain(cx, cz):
                            continue
                        cost = math.hypot(cx - x, cz - z)
                        if best is None or cost < best[0]:
                            best = (cost, cx, cz)
        if best:
            h["pos"] = [round(best[1]), round(best[2])]
            moved.append(f"{h['id']} {round(best[0])}m")
    return moved


def main():
    path = sys.argv[1]
    with open(path, encoding="utf-8") as f:
        L = json.load(f)
    # 이 도구가 만든 길(id 가 S- 로 시작)만 새로 만듦. 손으로 그린 중로는 큰길처럼 그대로 둠
    L["roads"] = [r for r in L["roads"] if not r["id"].startswith("S-")]
    world = World(L)
    plans = {k: v for k, v in L.get("street_plan", {}).items() if not k.startswith("_")}
    segments = []
    for did, plan in plans.items():
        segs = grid_segments(world, did, plan)
        for s in segs:
            extend_to_majors(world, s)
        segments += segs
    segments = trim(segments, world.majors)
    segments = connected(segments, world.majors)

    names = {d["id"]: d["name"] for d in L["districts"]}
    count = {}
    streets = []
    for s in sorted(segments, key=lambda s: (s["district"], s["fam"], s["a"][1], s["a"][0])):
        n = count.get(s["district"], 0) + 1
        count[s["district"]] = n
        streets.append({"id": f"S-{s['district']}-{n}", "name": f"{names[s['district']]} {n}길", "kind": STREET_KIND,
                        "line": [[round(s["a"][0], 1), round(s["a"][1], 1)], [round(s["b"][0], 1), round(s["b"][1], 1)]]})
    L["roads"] += streets
    moved = place_hubs(world, L)
    if moved:
        print("길 옆으로 옮긴 거점: " + ", ".join(moved))
    with open(path, "w", encoding="utf-8") as f:
        json.dump(L, f, ensure_ascii=False, indent=1)
        f.write("\n")
    total = sum(math.hypot(r["line"][1][0] - r["line"][0][0], r["line"][1][1] - r["line"][0][1]) for r in streets)
    print(f"구역 안 길 {len(streets)}개, 총 {total / 1000:.1f}km  " + ", ".join(f"{names[k]} {v}" for k, v in sorted(count.items())))


if __name__ == "__main__":
    main()
