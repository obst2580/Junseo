"""도로망 2D → 3D: 지형을 따라 높이를 붙이고 게임 좌표로 바꾼다.

- 지상 도로(0층): 지형 높이를 부드럽게 따라가되 경사 10% 이하.
- 고가 고속도로(1층): 지형 + DECK_H 높이, 경사 6% 이하. 지도 경계가 아닌 곳에서 끊기면 땅으로 내려온다.
- 다리·터널 구간: 양 끝 높이를 잇는 직선.
- 램프: 고가 위 접점에서 고가 폭을 벗어날 때까지 같은 높이, 그 뒤 지상 접점까지 부드럽게 내려감.
- 맵 경계 도로: 벽 바깥으로 실제 길을 따라 더 잇고, 벽 안쪽에 차단막 자리를 둔다. 남는 막다른 끝에는 회차로.
"""
import collections
import copy
import math

import numpy as np
import shapely
import shapely.affinity
from shapely.geometry import LineString, Point
from shapely.ops import unary_union
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


BOUNDARY_NEAR = 250.0   # 맵 경계에서 이 거리 안에서 끝나는 도로를 경계 도로로 본다
EXT_REAL = 1600.0       # 벽 바깥으로 실제 길을 따라 잇는 최대 길이 (게임 안 보이는 거리 너머까지)
WALK_LEN = 2 * EXT_REAL + BOUNDARY_NEAR
FAR_OK = 400.0          # EXT_REAL을 넘어도 플레이 구역에서 이만큼 멀어질 때까지는 더 잇는다
REENTRY_FAR = 250.0     # 벽에서 이만큼 멀어졌다가
REENTRY_NEAR = 100.0    # 다시 이만큼 가까워지면 되돌아온 것으로 본다
EXT_STRAIGHT = 1200.0   # 실제 길을 못 찾으면 곧게 잇는 최대 길이
EXT_STEP = 8.0
GRID_INSET = 32.0       # 지형 격자 끝에서 이만큼 안쪽에서 멈춘다
INWARD_MAX = 320.0      # 벽 밖으로 나가기 전에 플레이 구역 안을 이보다 길게 지나야 하면 잇지 않는다
COAST_PROBE = 200.0     # (곧게 이을 때) 끝에서 이 거리 안이 절반 넘게 물이면 바닷가 막다른 길로 보고 잇지 않는다
WATER_SPAN_MAX = 800.0  # (곧게 이을 때) 이보다 긴 물은 건너지 않는다
START_R = 90.0          # 도로 끝에서 이 거리 안의 실제 길에서 출발한다
START_ALIGN = 0.6
WALK_TURN = math.radians(70)
BLEND = 300.0           # 도로 끝과 실제 길 사이 어긋남을 이 길이에 걸쳐 없앤다
PAIR_R = 120.0          # 다시 들어온 실제 길이 다른 경계 끝에 이만큼 가까우면 둘을 잇는다
JOIN_R = 50.0
REJOIN_SEARCH = 600.0   # 다시 들어온 뒤 짝을 찾는 거리
MERGE_R = 14.0
BARRIER_IN = 3.0        # 차단막을 벽보다 이만큼 안쪽에 세운다
WALL_TOL = 24.0         # meta.py가 벽을 만들 때 쓰는 단순화 허용 오차
WALK_CLASSES = {1: {"motorway": 0, "trunk": 0, "primary": 2},
                0: {"primary": 0, "secondary": 1, "tertiary": 2, "unclassified": 3, "residential": 4}}


def osm_walkers():
    """층별 실제 도로 그래프 (실제 좌표, 인천을 옮기기 전)."""
    import networkx as nx
    import osmload

    ways = osmload.load_raw_ways()
    out = {}
    for tier, cls in WALK_CLASSES.items():
        G = nx.Graph()
        coord = {}
        for w in ways:
            r = cls.get(w["hw"])
            if r is None:
                continue
            ids = [n[0] for n in w["nodes"]]
            x, z = C.proj(np.array([n[1] for n in w["nodes"]]), np.array([n[2] for n in w["nodes"]]))
            for k in range(len(ids) - 1):
                if ids[k] != ids[k + 1]:
                    G.add_edge(ids[k], ids[k + 1], r=r)
            for nid, xx, zz in zip(ids, x, z):
                coord[nid] = np.array([xx, zz])
        elist = list(G.edges(data="r"))
        tree = STRtree([LineString([coord[u], coord[v]]) for u, v, _ in elist])
        out[tier] = (G, coord, elist, tree)
    return out


def walk_real(W, p, d, max_len):
    """p에서 d 쪽으로 실제 길을 따라간 선. 꺾임이 가장 작은 길을 고르고, 처음 BLEND 동안 p와의 어긋남을 줄여 간다."""
    G, coord, elist, tree = W
    best = None
    for idx in tree.query(Point(*p).buffer(START_R)):
        u, v, r = elist[idx]
        a, b = coord[u], coord[v]
        seg = b - a
        L = float(np.hypot(*seg))
        if L < 1e-6:
            continue
        t = float(np.clip(np.dot(p - a, seg) / (L * L), 0, 1))
        q = a + t * seg
        dist = float(np.hypot(*(p - q)))
        if dist > START_R:
            continue
        for x, y in ((u, v), (v, u)):
            al = float(np.dot((coord[y] - coord[x]) / L, d))
            if al < START_ALIGN:
                continue
            score = dist + (1 - al) * 120 + r * 20
            if best is None or score < best[0]:
                best = (score, q, x, y, r)
    if best is None:
        return None
    _, q, prev, cur, r = best
    path = [q, coord[cur]]
    total = float(np.hypot(*(coord[cur] - q)))
    seen = {prev, cur}
    while total < max_len:
        din = coord[cur] - coord[prev]
        din = din / max(float(np.hypot(*din)), 1e-9)
        choice = None
        for nb in G.neighbors(cur):
            if nb in seen:
                continue
            dv = coord[nb] - coord[cur]
            l = float(np.hypot(*dv))
            if l < 1e-6:
                continue
            turn = math.acos(float(np.clip(np.dot(din, dv / l), -1, 1)))
            if turn > WALK_TURN:
                continue
            score = turn + 0.35 * abs(G[cur][nb]["r"] - r)
            if choice is None or score < choice[0]:
                choice = (score, nb, l)
        if choice is None:
            break
        _, nb, l = choice
        r = G[cur][nb]["r"]
        prev, cur = cur, nb
        seen.add(cur)
        path.append(coord[cur])
        total += l
    path = np.array(path)
    s = np.concatenate([[0], np.cumsum(np.hypot(*np.diff(path, axis=0).T))])
    path = path + (p - path[0]) * np.clip(1 - s / BLEND, 0, 1)[:, None]
    return path


def extend_boundary_ends(nodes, edges, terr, R):
    """맵 경계에서 끝나는 도로를 벽 바깥으로 잇는다. 벽 너머에서 길이 뚝 끊겨 보이지 않게 한다.

    - 실제 길(OSM)을 따라 EXT_REAL까지 잇는다. 물은 다리로 건넌다.
    - 벽 밖으로 나갔다가 플레이 구역으로 다시 들어오면, 그 자리의 다른 경계 끝이나 도로에 잇는다.
    - 실제 길을 못 찾으면 곧게 잇고, 끝에서 바로 바다인 길(항만·해안 도로)은 잇지 않는다.
    간선과 노드를 그 자리에서 고친다 (둘을 잇는 길은 새 간선). 반환: 경계 끝마다 정보.
    """
    ox, oz = R["offset"]
    playable = R["playable"]
    boundary = playable.boundary
    seoul, incheon = R["seoul"], R["incheon"]
    sx, sz = C.INCHEON_SHIFT
    gx0 = terr.x0 + ox + GRID_INSET
    gz0 = terr.z0 + oz + GRID_INSET
    gx1 = terr.x0 + (terr.nx - 1) * 16 + ox - GRID_INSET
    gz1 = terr.z0 + (terr.nz - 1) * 16 + oz - GRID_INSET
    walkers = osm_walkers()

    inc = {}
    for ei, e in enumerate(edges):
        inc.setdefault(e["a"], []).append(ei)
        inc.setdefault(e["b"], []).append(ei)
    lines = [LineString(np.vstack([np.asarray(pc["pts"]) for pc in e["pieces"]])) for e in edges]
    tree = STRtree(lines)

    # 경계 끝과 바깥 방향
    cand = {}
    for i, n in enumerate(nodes):
        if len(inc.get(i, [])) != 1:
            continue
        p = np.array([n["x"], n["z"]], dtype=np.float64)
        if boundary.distance(Point(*p)) >= BOUNDARY_NEAR:
            continue
        ei = inc[i][0]
        line = lines[ei] if edges[ei]["b"] == i else LineString(np.asarray(lines[ei].coords)[::-1])
        inner = np.asarray(line.interpolate(line.length - min(40.0, line.length * 0.5)).coords[0])
        d = p - inner
        if np.hypot(*d) < 1e-6:
            continue
        cand[i] = (p, d / np.hypot(*d), ei)

    new_lines = {0: [], 1: []}
    resolved = set()
    out = []

    def pieces_of(path):
        """경로를 EXT_STEP 간격 점으로 나누고 물 위는 다리로."""
        line = LineString(path)
        ks = np.arange(0.0, line.length, EXT_STEP)
        ks = np.append(ks, line.length)
        q = shapely.get_coordinates(shapely.line_interpolate_point(line, ks))
        kw = terr.is_water(q[:, 0] - ox, q[:, 1] - oz)
        bounds = [0] + [k for k in range(1, len(ks)) if kw[k] != kw[k - 1]] + [len(ks) - 1]
        res = []
        for a_i, b_i in zip(bounds[:-1], bounds[1:]):
            if b_i > a_i:
                res.append({"kind": "bridge" if kw[a_i] else "road", "pts": q[a_i:b_i + 1].tolist()})
        return res

    for i in sorted(cand):
        if i in resolved:
            continue
        p, d, ei = cand[i]
        e = edges[ei]
        tier = e["tier"]
        resolved.add(i)
        info = {"node": i, "edge": ei, "ext_edge": None, "extended": False, "orig": p.copy(), "reason": "?"}
        out.append(info)

        # 인천 쪽 끝은 실제 좌표로 되돌려 실제 길을 찾고, 찾은 길을 다시 옮긴다
        is_inch = incheon.distance(Point(*p)) < seoul.distance(Point(*p))
        shift = np.array([sx, sz]) if is_inch else np.zeros(2)
        path = walk_real(walkers[tier], p - shift, d, WALK_LEN)
        real = path is not None and len(path) >= 2
        if real:
            path = path + shift
            # 실제 길이 일찍 끝나면 (자료 끝, 작은 길로 바뀜) 마지막 방향으로 곧게 더 잇는다
            L_real = LineString(path).length
            if L_real < WALK_LEN - 50:
                tail_line = LineString(path)
                a = np.asarray(tail_line.interpolate(max(0.0, L_real - 60)).coords[0])
                dd = path[-1] - a
                if np.hypot(*dd) > 1e-6:
                    dd /= np.hypot(*dd)
                    path = np.vstack([path, path[-1] + dd * (WALK_LEN - L_real)])
            straight_from = L_real
        else:
            path = np.array([p, p + d * EXT_STRAIGHT])
            straight_from = 0.0
        line = LineString(path)
        ss = np.arange(0.0, line.length, EXT_STEP)
        q = shapely.get_coordinates(shapely.line_interpolate_point(line, ss))
        water = terr.is_water(q[:, 0] - ox, q[:, 1] - oz)
        if not real and water[(ss > 0) & (ss <= COAST_PROBE)].mean() >= 0.5:
            info["reason"] = "coast"
            continue
        stop, reason = len(ss), ("real" if real else "straight")
        outside = (q[:, 0] < gx0) | (q[:, 0] > gx1) | (q[:, 1] < gz0) | (q[:, 1] > gz1)
        if outside.any():
            stop, reason = int(np.argmax(outside)), "grid"
        dist = shapely.distance(playable, shapely.points(q[:stop]))
        outs = np.flatnonzero(dist > 0)
        if not len(outs) or ss[outs[0]] > INWARD_MAX:
            info["reason"] = "inward"
            continue
        o = int(outs[0])
        # 벽 밖으로 나갔다가 되돌아오는 곳: 플레이 구역에 다시 들어오거나, 멀어졌다가 다시 가까워지는 곳
        runmax = np.maximum.accumulate(dist[o:stop])
        cond = ((dist[o:stop] == 0) & (ss[o:stop] - ss[o] > 40)) | ((runmax > REENTRY_FAR) & (dist[o:stop] < REENTRY_NEAR))
        back = np.flatnonzero(cond)
        k_re = o + int(back[0]) if len(back) else None
        # 길이 상한: 벽 밖으로 EXT_REAL을 넘고 플레이 구역에서 충분히 멀어진 곳
        capk = np.flatnonzero((ss[o:stop] - ss[o] > EXT_REAL) & (dist[o:stop] > FAR_OK))
        cap = o + int(capk[0]) if len(capk) else None
        if cap is not None and (k_re is None or cap < k_re):
            stop, k_re = cap, None
            # 실제 다리 한가운데서 자르지 않는다
            while stop < len(ss) and water[stop - 1] and ss[stop] < straight_from:
                stop += 1
        tail = None  # 마지막에 덧붙일 점 (다른 끝·도로에 이을 때)
        pair = None
        if k_re is not None:
            hi = min(len(ss), k_re + int(REJOIN_SEARCH / EXT_STEP))
            others = [j for j in cand if j not in resolved and edges[cand[j][2]]["tier"] == tier]
            for k in range(k_re, hi):
                for j in others:
                    if np.hypot(*(q[k] - cand[j][0])) < PAIR_R:
                        pair = j
                        break
                if pair is not None:
                    stop, tail, reason = k, cand[pair][0], "pair"
                    break
            if pair is None:
                for k in range(k_re, hi):
                    pt = Point(*q[k])
                    for j in tree.query(pt.buffer(JOIN_R)):
                        if j != ei and edges[j]["tier"] == tier and lines[j].distance(pt) < JOIN_R:
                            sp = lines[j].interpolate(lines[j].project(pt))
                            stop, tail, reason = k, np.array([sp.x, sp.y]), "join"
                            break
                    if tail is not None:
                        break
            if tail is None:
                # 짝을 못 찾으면 벽에서 가장 멀어진 곳에서 멈춘다
                stop, reason = o + int(np.argmax(dist[o:k_re])) + 1, "reentry"
        # 다른 연장선과 겹치면 거기서 합친다
        if new_lines[tier]:
            near = shapely.distance(unary_union(new_lines[tier]), shapely.points(q[:stop]))
            hit = np.flatnonzero((near < MERGE_R) & (ss[:stop] > 30))
            if len(hit) and hit[0] < stop:
                stop, tail, pair, reason = int(hit[0]), None, None, "merge"
        # 같은 층의 다른 도로를 가로지르면 거기서 멈춘다
        if stop >= 2:
            seg = LineString(q[:stop])
            for j in tree.query(seg):
                if j == ei or edges[j]["tier"] != tier:
                    continue
                x = seg.intersection(lines[j])
                for g in getattr(x, "geoms", [x]):
                    if g.is_empty:
                        continue
                    for c in np.asarray(g.coords):
                        sc = seg.project(Point(*c))
                        if sc > 4:
                            k = int(np.searchsorted(ss, sc))
                            if k < stop:
                                stop, tail, pair, reason = k, c, None, "road"
        # 곧게 이을 때: 긴 물은 건너지 않는다. 어느 쪽이든 물 위에서 끝나지 않게 한다
        k = 0
        while k < stop:
            if water[k]:
                j = k
                while j < stop and water[j]:
                    j += 1
                long_span = ss[k] >= straight_from and (j - k) * EXT_STEP >= WATER_SPAN_MAX
                if (j >= stop and tail is None) or long_span:
                    stop, tail, pair, reason = max(0, k - 2), None, None, "water"
                    break
                k = j
            else:
                k += 1
        info["reason"] = reason
        pts = q[:stop]
        if tail is not None:
            pts = np.vstack([pts, tail])
        if len(pts) < 2 or LineString(pts).length < 24.0:
            continue
        pts[0] = p
        pcs = pieces_of(pts)
        if not pcs:
            continue
        if pair is not None:
            # 두 경계 끝을 잇는 새 간선
            edges.append({"a": i, "b": pair, "tier": tier, "cls": e["cls"], "w": e["w"], "name": e["name"], "pieces": pcs})
            info["ext_edge"] = len(edges) - 1
            resolved.add(pair)
            out.append({"node": pair, "edge": cand[pair][2], "ext_edge": len(edges) - 1, "extended": True,
                        "orig": cand[pair][0].copy(), "reason": "pair"})
        elif e["b"] == i:
            last = e["pieces"][-1]
            if last["kind"] == pcs[0]["kind"]:
                last["pts"] = list(last["pts"]) + pcs[0]["pts"][1:]
                pcs = pcs[1:]
            e["pieces"] = list(e["pieces"]) + pcs
        else:
            rev = [{"kind": pc["kind"], "pts": pc["pts"][::-1]} for pc in pcs[::-1]]
            first = e["pieces"][0]
            if first["kind"] == rev[-1]["kind"]:
                first["pts"] = rev[-1]["pts"][:-1] + list(first["pts"])
                rev = rev[:-1]
            e["pieces"] = rev + list(e["pieces"])
        if pair is None:
            n = nodes[i]
            n["x"], n["z"] = float(pts[-1][0]), float(pts[-1][1])
        new_lines[tier].append(LineString(pts))
        info["extended"] = True
    return out


def barriers_for(ends, out_edges, R, world):
    """경계 도로마다 벽 바로 안쪽에 세울 차단막 자리 (게임 좌표).

    도로가 벽을 지나는 곳에 세운다. 벽을 지나지 않고 플레이 구역 안에서 끝나는 길(바닷가 등)은 길 끝에 세우고,
    다른 도로에 이어진 길에는 세우지 않는다.
    """
    ox, oz = R["offset"]
    inner = shapely.affinity.translate(R["playable"].simplify(WALL_TOL), -ox, -oz)
    wall = inner.boundary
    out = []
    for info in ends:
        e = out_edges[info["edge"]]
        pts = np.vstack([np.asarray(pc["pts"]) for pc in e["pieces"]])
        if e["b"] != info["node"]:
            pts = pts[::-1]
        n_orig = len(pts)
        if info["ext_edge"] is not None:
            x = out_edges[info["ext_edge"]]
            ext = np.vstack([np.asarray(pc["pts"]) for pc in x["pieces"]])
            if x["a"] != info["node"]:
                ext = ext[::-1]
            pts = np.vstack([pts, ext[1:]])
        seg_len = np.hypot(*np.diff(pts[:, [0, 2]], axis=0).T)
        keep = np.concatenate([[True], seg_len > 1e-6])
        s_all = np.concatenate([[0], np.cumsum(seg_len)])
        s_orig = float(s_all[n_orig - 1]) if info["ext_edge"] is not None else None
        pts = pts[keep]
        line = LineString(pts[:, [0, 2]])
        s = np.concatenate([[0], np.cumsum(np.hypot(*np.diff(pts[:, [0, 2]], axis=0).T))])
        if s_orig is None:
            s_orig = line.project(Point(*world([info["orig"]])[0]))
        s_bar = None
        cross = line.intersection(wall)
        cands = [g for g in getattr(cross, "geoms", [cross]) if not g.is_empty and g.geom_type == "Point"]
        if cands:
            sc = min((line.project(g) for g in cands), key=lambda v: abs(v - s_orig))
            before_in = inner.contains(line.interpolate(max(0.0, sc - 6)))
            s_bar = sc - BARRIER_IN if before_in else sc + BARRIER_IN
        elif info.get("reason") != "road" and inner.contains(Point(pts[-1][0], pts[-1][2])):
            s_bar = s[-1] - BARRIER_IN
        if s_bar is None:
            continue
        s_bar = float(np.clip(s_bar, 0, s[-1]))
        q = line.interpolate(s_bar)
        y = float(np.interp(s_bar, s, pts[:, 1]))
        a = np.asarray(line.interpolate(max(0.0, s_bar - 10)).coords[0])
        b = np.asarray(line.interpolate(min(s[-1], s_bar + 10)).coords[0])
        dv = b - a
        dv /= max(np.hypot(*dv), 1e-6)
        out.append({"x": round(q.x, 2), "y": round(y, 2), "z": round(q.y, 2), "dx": round(float(dv[0]), 4),
                    "dz": round(float(dv[1]), 4), "tier": e["tier"], "w": e["w"], "kind": _kind_at(e, line, s_bar)})
    return out


def _kind_at(e, line, s_bar):
    """s_bar 위치의 구간 종류 (road / bridge / tunnel)."""
    best, kind = 1e18, "road"
    q = line.interpolate(s_bar)
    for pc in e["pieces"]:
        p = np.asarray(pc["pts"])
        if len(p) < 2:
            continue
        dd = LineString(p[:, [0, 2]]).distance(q)
        if dd < best:
            best, kind = dd, pc["kind"]
    return kind


TOUCH_R = 30.0     # 다른 도로와 이만큼 가깝고 높이가 비슷하면 이어진 것으로 본다
TOUCH_DY = 6.0
VISIBLE_R = 300.0  # 플레이 구역에서 이 거리 안의 막다른 끝만 손본다


def dead_end_turnarounds(out_nodes, out_edges, barriers, R, joined=()):
    """그래도 남는 막다른 끝(바닷가 길, 내려온 고가 끝 등)에 회차로를 둔다. 길이 잘려 보이지 않고 일부러 끝나는 곳처럼 보이게."""
    ox, oz = R["offset"]
    playable = shapely.affinity.translate(R["playable"], -ox, -oz)
    deg = collections.Counter()
    for e in out_edges:
        for k in (e["a"], e["b"]):
            if k >= 0:
                deg[k] += 1
    polys = []
    for ei, e in enumerate(out_edges):
        for pc in e["pieces"]:
            p = np.asarray(pc["pts"])
            if len(p) >= 2:
                polys.append((ei, p))
    tree = STRtree([LineString(p[:, [0, 2]]) for _, p in polys])
    out = []
    for i, n in enumerate(out_nodes):
        if deg[i] != 1 or i in joined:
            continue
        pt = Point(n["x"], n["z"])
        if playable.distance(pt) > VISIBLE_R:
            continue
        if any(np.hypot(b["x"] - n["x"], b["z"] - n["z"]) < 20 for b in barriers):
            continue
        own = next(ei for ei, e in enumerate(out_edges) if e["a"] == i or e["b"] == i)
        touching = False
        for j in tree.query(pt.buffer(TOUCH_R)):
            ei, p = polys[j]
            if ei == own:
                continue
            line = LineString(p[:, [0, 2]])
            if line.distance(pt) >= TOUCH_R:
                continue
            s = np.concatenate([[0], np.cumsum(np.hypot(*np.diff(p[:, [0, 2]], axis=0).T))])
            y = float(np.interp(line.project(pt), s, p[:, 1]))
            if abs(y - n["y"]) < TOUCH_DY:
                touching = True
                break
        if touching:
            continue
        w = out_edges[own]["w"]
        out.append({"x": n["x"], "y": n["y"], "z": n["z"], "d": round(w * 1.8 + 8, 1), "tier": n["tier"]})
    return out


def build(net, terr):
    R = C.regions()
    ox, oz = R["offset"]
    boundary = R["playable"].boundary

    def world(xy):
        xy = np.asarray(xy, dtype=np.float64)
        return np.column_stack([xy[:, 0] - ox, xy[:, 1] - oz])

    nodes = copy.deepcopy(net["nodes"])
    edges = copy.deepcopy(net["edges"])
    deg = np.zeros(len(nodes), int)
    for e in edges:
        deg[e["a"]] += 1
        deg[e["b"]] += 1

    # 맵 경계 도로를 벽 바깥으로 잇는다 (끝 노드가 옮겨지므로 아래 '끊긴 고가' 판정보다 먼저 경계 여부를 기억해 둔다)
    near_boundary = np.array([boundary.distance(Point(n["x"], n["z"])) < BOUNDARY_NEAR for n in nodes])
    bends = extend_boundary_ends(nodes, edges, terr, R)
    n_ext = sum(1 for x in bends if x["extended"])
    deg = np.zeros(len(nodes), int)
    for e in edges:
        deg[e["a"]] += 1
        deg[e["b"]] += 1
    print("경계 도로 처리", dict(collections.Counter(x.get("reason", "?") for x in bends)))

    # 1차: 간선마다 지형을 따라간 기본 높이. 노드 높이는 이어진 간선 끝 높이들의 평균으로 잡는다
    ring = np.array([[0, 0]] + [[24 * math.cos(a), 24 * math.sin(a)] for a in np.linspace(0, 2 * math.pi, 8, endpoint=False)])
    # 끊긴 고가는 땅으로 내려온다. 다른 고가에 이어진 경계 끝(합류·짝)은 그대로 둔다
    joined = {x["node"] for x in bends if x["reason"] in ("merge", "join", "road", "pair")}
    node_dead = np.zeros(len(nodes), bool)
    for i, n in enumerate(nodes):
        if n["tier"] == 1 and deg[i] == 1 and i not in joined:
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
        if j is None or t0_lines[j].distance(q) > 400:
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

    # 맵 경계 도로의 차단막 자리
    barriers = barriers_for(bends, out_edges, R, world)
    turnarounds = dead_end_turnarounds(out_nodes, out_edges, barriers, R, joined)

    print(f"끊긴 고가 연결 {connectors}개, 램프 {len(out_ramps)}개 (경사 때문에 {dropped}개 제외), 경계 도로 {len(bends)}개 (벽 바깥으로 이음 {n_ext}개, 차단막 {len(barriers)}개), 막다른 길 회차로 {len(turnarounds)}개")
    return {"offset": [ox, oz], "deckHeight": DECK_H, "nodes": out_nodes, "edges": out_edges, "ramps": out_ramps,
            "barriers": barriers, "turnarounds": turnarounds}
