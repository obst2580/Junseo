"""도로망 2D → 3D: 지형을 따라 높이를 붙이고 게임 좌표로 바꾼다.

- 지상 도로(0층): 지형 높이를 부드럽게 따라가되 경사 10% 이하.
- 고가 고속도로(1층): 지형 + DECK_H 높이, 경사 6% 이하. 지도 경계가 아닌 곳에서 끊기면 땅으로 내려온다.
- 다리·터널 구간: 양 끝 높이를 잇는 직선.
- 램프: 고가 위 접점에서 고가 폭을 벗어날 때까지 같은 높이, 그 뒤 지상 접점까지 부드럽게 내려감.
"""
import math

import numpy as np
import shapely
from shapely.geometry import LineString, Point
from shapely.strtree import STRtree

import common as C

DECK_H = 17.0
GRADE = {0: 0.10, 1: 0.06}
SMOOTH = {0: 100.0, 1: 260.0}
STEP = 8.0
RAMP_GRADE = 0.25
MAX_SEG = 160.0


def edge_samples(e):
    """간선을 STEP 간격으로 나눈 점들, 각 점의 구간 종류, 조각별 표본 범위(양끝 포함)."""
    pts, kinds, ranges = [], [], []
    count = 0
    for pc in e["pieces"]:
        p = np.asarray(pc["pts"], dtype=np.float64)
        line = LineString(p)
        L = line.length
        n = max(1, int(math.ceil(L / STEP)))
        ds = sorted(set([i * L / n for i in range(n + 1)] + [line.project(Point(q)) for q in p]))
        sp = np.array([line.interpolate(d).coords[0] for d in ds])
        start = count - 1 if pts else 0
        if pts:
            sp = sp[1:]
        pts.append(sp)
        kinds += [pc["kind"]] * len(sp)
        count += len(sp)
        ranges.append((start, count - 1))
    xy = np.vstack(pts)
    s = np.concatenate([[0], np.cumsum(np.hypot(*np.diff(xy, axis=0).T))])
    return xy, s, np.array(kinds), ranges


def moving_average(v, s, window):
    if len(v) < 3:
        return v.copy()
    out = np.empty_like(v)
    half = window / 2
    cs = np.concatenate([[0], np.cumsum(v)])
    lo = np.searchsorted(s, s - half, side="left")
    hi = np.searchsorted(s, s + half, side="right")
    out = (cs[hi] - cs[lo]) / np.maximum(1, hi - lo)
    return out


def interp_gaps(v, mask):
    """mask가 참인 구간(다리·터널)을 양 끝 값의 직선으로 채운다."""
    v = v.copy()
    n = len(v)
    i = 0
    while i < n:
        if mask[i]:
            k = i
            while k < n and mask[k]:
                k += 1
            a = v[i - 1] if i > 0 else (v[k] if k < n else 0.0)
            b = v[k] if k < n else a
            for j in range(i, k):
                t = (j - i + 1) / (k - i + 1)
                v[j] = a * (1 - t) + b * t
            i = k
        else:
            i += 1
    return v


def limit_grade(y, s, g, fixed_ends=True):
    y = y.copy()
    for _ in range(300):
        before = y.copy()
        for i in range(1, len(y)):
            ds = s[i] - s[i - 1]
            y[i] = min(max(y[i], y[i - 1] - g * ds), y[i - 1] + g * ds)
        if fixed_ends:
            y[-1] = before[-1]
        for i in range(len(y) - 2, -1, -1):
            ds = s[i + 1] - s[i]
            y[i] = min(max(y[i], y[i + 1] - g * ds), y[i + 1] + g * ds)
        if fixed_ends:
            y[0] = before[0]
        if np.max(np.abs(y - before)) < 1e-3:
            break
    return y


def simplify3d(pts, tol_h=2.0, tol_v=0.8, max_seg=MAX_SEG):
    """3D 더글라스-포이커. 수평 허용 오차와 높이 허용 오차를 따로 둔다 (폭 28~56스터드 도로에서 수평 2스터드는 티가 안 남)."""
    pts = np.asarray(pts)
    if len(pts) <= 2:
        return pts
    keep = np.zeros(len(pts), bool)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        if b <= a + 1:
            continue
        p, q = pts[a], pts[b]
        seg = pts[a + 1:b]
        dh = np.array([q[0] - p[0], q[2] - p[2]])
        L2 = float(dh @ dh)
        rel = np.column_stack([seg[:, 0] - p[0], seg[:, 2] - p[2]])
        t = np.clip((rel @ dh) / L2, 0, 1) if L2 > 1e-9 else np.zeros(len(seg))
        hx = rel - t[:, None] * dh
        hdist = np.hypot(hx[:, 0], hx[:, 1])
        vdist = np.abs(seg[:, 1] - (p[1] + t * (q[1] - p[1])))
        score = np.maximum(hdist / tol_h, vdist / tol_v)
        k = int(np.argmax(score))
        if score[k] > 1.0 or np.hypot(*dh) > max_seg:
            keep[a + 1 + k] = True
            stack.append((a, a + 1 + k))
            stack.append((a + 1 + k, b))
    return pts[keep]


def build(net, terr):
    R = C.regions()
    ox, oz = R["offset"]
    boundary = R["playable"].boundary

    def world(xy):
        xy = np.asarray(xy, dtype=np.float64)
        return np.column_stack([xy[:, 0] - ox, xy[:, 1] - oz])

    nodes = net["nodes"]
    edges = net["edges"]
    deg = np.zeros(len(nodes), int)
    for e in edges:
        deg[e["a"]] += 1
        deg[e["b"]] += 1

    # 1차: 간선마다 지형을 따라간 기본 높이. 노드 높이는 이어진 간선 끝 높이들의 평균으로 잡는다
    ring = np.array([[0, 0]] + [[24 * math.cos(a), 24 * math.sin(a)] for a in np.linspace(0, 2 * math.pi, 8, endpoint=False)])
    node_dead = np.zeros(len(nodes), bool)
    for i, n in enumerate(nodes):
        if n["tier"] == 1 and deg[i] == 1 and boundary.distance(Point(n["x"], n["z"])) >= 250:
            node_dead[i] = True
    bases = {}
    ends = [[] for _ in nodes]
    for ei, e in enumerate(edges):
        xy_s, s, kinds, ranges = edge_samples(e)
        xy = world(xy_s)
        tier = e["tier"]
        g = terr.ground_at(xy[:, 0], xy[:, 1])
        base = interp_gaps(g, kinds != "road")
        base = moving_average(base, s, SMOOTH[tier])
        if tier == 1:
            base = base + DECK_H
        bases[ei] = (xy, s, kinds, ranges, base)
        ends[e["a"]].append(base[0])
        ends[e["b"]].append(base[-1])
    node_y = np.zeros(len(nodes))
    for i, n in enumerate(nodes):
        if node_dead[i]:
            p = world([[n["x"], n["z"]]])[0]
            node_y[i] = float(np.mean(terr.ground_at(p[0] + ring[:, 0], p[1] + ring[:, 1])))
        elif ends[i]:
            node_y[i] = float(np.mean(ends[i]))
        if n["tier"] == 0:
            node_y[i] = max(node_y[i], 0.0)

    out_edges = []
    profiles = {}
    for ei, e in enumerate(edges):
        xy, s, kinds, ranges, base = bases[ei]
        tier = e["tier"]
        yA, yB = node_y[e["a"]], node_y[e["b"]]
        t = s / max(s[-1], 1e-6)
        y = base + (yA - base[0]) * (1 - t) + (yB - base[-1]) * t
        y = limit_grade(y, s, GRADE[tier])
        if tier == 0:
            # 지상 도로는 평지(0) 아래로, 다리는 수면 가까이로 내려가지 않게
            floor = np.where(kinds == "bridge", 1.0, 0.0)
            y = np.maximum(y, floor)
            y = limit_grade(y, s, GRADE[tier] * 1.2)
        profiles[ei] = (s, y, xy)
        pieces = []
        for pc, (i0, i1) in zip(e["pieces"], ranges):
            seg = np.column_stack([xy[i0:i1 + 1, 0], y[i0:i1 + 1], xy[i0:i1 + 1, 1]])
            if len(seg) >= 2:
                pieces.append({"kind": pc["kind"], "pts": simplify3d(seg).round(2).tolist()})
        out_edges.append({
            "a": e["a"], "b": e["b"], "tier": tier, "cls": e["cls"], "w": e["w"], "name": e["name"],
            "pieces": pieces,
        })

    out_nodes = []
    for i, n in enumerate(nodes):
        p = world([[n["x"], n["z"]]])[0]
        out_nodes.append({"x": round(float(p[0]), 2), "y": round(float(node_y[i]), 2), "z": round(float(p[1]), 2),
                          "tier": n["tier"], "deg": int(deg[i])})

    # 끊긴 고가가 땅으로 내려온 곳은 가장 가까운 지상 도로로 잇는다
    t0_idx = [i for i, e in enumerate(edges) if e["tier"] == 0]
    t0_lines = [LineString(np.vstack([np.asarray(pc["pts"]) for pc in edges[i]["pieces"]])) for i in t0_idx]
    t0_tree = STRtree(t0_lines)
    connectors = 0
    for i, n in enumerate(nodes):
        if not node_dead[i]:
            continue
        q = Point(n["x"], n["z"])
        j = t0_tree.nearest(q)
        if j is None or t0_lines[j].distance(q) > 160:
            continue
        sp = shapely.ops.nearest_points(t0_lines[j], q)[0]
        a = world([[n["x"], n["z"]]])[0]
        b = world([[sp.x, sp.y]])[0]
        ya = node_y[i]
        yb = float(terr.ground_at(b[0], b[1]))
        if np.hypot(*(b - a)) < 4:
            continue
        out_edges.append({
            "a": i, "b": -1, "tier": 0, "cls": "secondary", "w": 28.0, "name": "",
            "pieces": [{"kind": "road", "pts": [[round(a[0], 2), round(ya, 2), round(a[1], 2)], [round(b[0], 2), round(yb, 2), round(b[1], 2)]]}],
        })
        connectors += 1

    # 램프
    out_ramps = []
    dropped = 0
    for r in net.get("ramps", []):
        s1, y1s, _ = profiles[r["e1"]]
        s0, y0s, _ = profiles[r["e0"]]
        y1 = float(np.interp(r["d1"], s1, y1s))
        y0 = float(np.interp(r["d0"], s0, y0s))
        p = np.asarray(r["pts"], dtype=np.float64)
        line = LineString(p)
        L = line.length
        n = max(2, int(math.ceil(L / STEP)) + 1)
        ds = np.linspace(0, L, n)
        sp = np.array([line.interpolate(d).coords[0] for d in ds])
        main = LineString(np.vstack([np.asarray(pc["pts"]) for pc in edges[r["e1"]]["pieces"]]))
        half = edges[r["e1"]]["w"] / 2 + 2
        dist = np.array([main.distance(Point(*q)) for q in sp])
        free = np.argmax(dist >= half) if (dist >= half).any() else len(ds) - 1
        s_free = ds[free]
        y = np.full(n, y1)
        span = max(L - s_free, 1e-6)
        tt = np.clip((ds - s_free) / span, 0, 1)
        smooth = tt * tt * (3 - 2 * tt)
        y = y1 + (y0 - y1) * smooth
        grade = np.max(np.abs(np.diff(y)) / np.maximum(np.diff(ds), 1e-6))
        if grade > RAMP_GRADE:
            dropped += 1
            continue
        w = world(sp)
        pts3 = np.column_stack([w[:, 0], y, w[:, 1]])
        out_ramps.append({"w": r["w"], "e1": r["e1"], "e0": r["e0"], "pts": simplify3d(pts3, 0.6, 0.2, 80).round(2).tolist()})

    print(f"끊긴 고가 연결 {connectors}개, 램프 {len(out_ramps)}개 (경사 때문에 {dropped}개 제외)")
    return {"offset": [ox, oz], "deckHeight": DECK_H, "nodes": out_nodes, "edges": out_edges, "ramps": out_ramps}
