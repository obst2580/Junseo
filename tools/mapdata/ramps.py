"""고가 고속도로(1층) ↔ 지상 도로(0층) 진출입 램프.

실제 OSM의 연결로(motorway_link, trunk_link)를 따라 램프를 만든다.
1/10로 줄이면 클로버형 나들목의 고리 램프는 너무 작아지므로,
고속도로와 지상 도로를 잇는 연결로 중 쓸 만한 것만 골라 단순화한다.
"""
import math
from collections import defaultdict

import networkx as nx
import numpy as np
from shapely.geometry import LineString, Point
from shapely.ops import nearest_points, unary_union
from shapely.strtree import STRtree

import osmload
import roads

LINK = {"motorway_link", "trunk_link"}
MAIN1 = {"motorway", "trunk"}
MAIN0 = {"primary", "secondary", "tertiary", "primary_link", "secondary_link"}

RAMP_W = 16.0
SNAP_MAX = 80.0
MIN_LEN = 100.0      # 고가 높이 17스터드를 약 17% 경사로 내려가려면 이 정도 길이가 필요
MAX_LEN = 1400.0
MAX_TURN = math.radians(150)
DEDUPE_DIST = 140.0
MAX_PER_JUNCTION = 4


def edge_line(e):
    pts = []
    for pc in e["pieces"]:
        p = pc["pts"]
        pts += p if not pts else p[1:]
    return LineString(pts)


def total_turn(xy):
    if len(xy) < 3:
        return 0.0
    d = np.diff(xy, axis=0)
    ang = np.arctan2(d[:, 1], d[:, 0])
    t = np.diff(ang)
    t = (t + np.pi) % (2 * np.pi) - np.pi
    return float(np.abs(t).sum())


def push_off(path, main, dist):
    """고가 위에서 시작한 램프가 고가 바로 아래로 겹쳐 달리지 않게 옆으로 민다.

    첫 점(고가 중심)은 그대로 두고, 고가 폭 안에 들어온 나머지 점들을 고가 가장자리 밖으로 옮긴다.
    """
    out = [path[0]]
    side = None
    end = path[-1]
    for p in path[1:]:
        q = Point(*p)
        d = main.distance(q)
        if d < dist and np.hypot(*(p - end)) > 40:
            sp = np.array(nearest_points(main, q)[0].coords[0])
            v = p - sp
            n = np.hypot(*v)
            if n < 1e-6:
                # 고가 진행 방향의 수직 방향으로
                t = main.interpolate(main.project(q) + 1).coords[0]
                tv = np.array(t) - sp
                v = np.array([-tv[1], tv[0]])
                n = np.hypot(*v)
            v = v / n
            if side is None:
                side = v
            elif np.dot(side, v) < 0:
                v = side
            p = sp + v * dist
        else:
            if side is None:
                sp = np.array(nearest_points(main, q)[0].coords[0])
                side = (p - sp) / max(np.hypot(*(p - sp)), 1e-6)
        out.append(p)
    return np.array(out)


def build_ramps(net, segs):
    # 일반화된 도로망
    lines1, idx1, lines0, idx0 = [], [], [], []
    for i, e in enumerate(net["edges"]):
        ln = edge_line(e)
        if e["tier"] == 1:
            lines1.append(ln)
            idx1.append(i)
        else:
            lines0.append(ln)
            idx0.append(i)
    tree1, tree0 = STRtree(lines1), STRtree(lines0)
    ground_buf = [ln.buffer(net["edges"][i]["w"] / 2 + 2) for ln, i in zip(lines0, idx0)]
    ground_tree = STRtree(ground_buf)

    # OSM 노드 → 속한 도로 종류
    node_cls = defaultdict(set)
    node_xy = {}
    for s in segs:
        for nid, p in zip(s["ids"], s["xy"]):
            node_cls[int(nid)].add(s["hw"])
            node_xy[int(nid)] = p
    # 연결로 그래프
    G = nx.Graph()
    for s in segs:
        if s["hw"] not in LINK:
            continue
        ids = [int(n) for n in s["ids"]]
        for a, b, pa, pb in zip(ids[:-1], ids[1:], s["xy"][:-1], s["xy"][1:]):
            G.add_edge(a, b, w=float(np.hypot(*(pb - pa))))

    candidates = []
    for comp in nx.connected_components(G):
        a1 = [n for n in comp if node_cls[n] & MAIN1]
        a0 = [n for n in comp if node_cls[n] & MAIN0]
        if not a1 or not a0:
            continue
        sub = G.subgraph(comp)
        for s1 in a1:
            dist, paths = nx.single_source_dijkstra(sub, s1, weight="w")
            for s0 in a0:
                if s0 not in dist:
                    continue
                L = dist[s0]
                if L < MIN_LEN * 0.6 or L > MAX_LEN:
                    continue
                xy = np.array([node_xy[n] for n in paths[s0]])
                if total_turn(xy) > MAX_TURN:
                    continue
                candidates.append((xy, frozenset(comp)))

    def snap(tree, lines, idx, p):
        q = Point(*p)
        j = tree.nearest(q)
        if j is None:
            return None
        ln = lines[j]
        if ln.distance(q) > SNAP_MAX:
            return None
        sp = nearest_points(ln, q)[0]
        return idx[j], np.array(sp.coords[0]), ln.project(sp)

    ramps = []
    per_comp = defaultdict(int)
    # 너무 짧지도 길지도 않은 것부터
    candidates.sort(key=lambda c: abs(LineString(c[0]).length - 350))
    for xy, comp in candidates:
        if per_comp[comp] >= MAX_PER_JUNCTION:
            continue
        s1 = snap(tree1, lines1, idx1, xy[0])
        s0 = snap(tree0, lines0, idx0, xy[-1])
        if s1 is None or s0 is None:
            continue
        e1, p1, d1 = s1
        e0, p0, d0 = s0
        # 램프 경로: 고속도로 위 접점 → 실제 연결로 중간 → 지상 도로 접점
        mid = [p for p in xy[1:-1] if np.hypot(*(p - p1)) > 40 and np.hypot(*(p - p0)) > 40]
        path = np.vstack([p1] + mid + [p0]) if mid else np.vstack([p1, p0])
        path = roads.smooth_line(path)
        path = push_off(path, lines1[idx1.index(e1)], net["edges"][e1]["w"] / 2 + RAMP_W / 2 + 1)
        line = LineString(path)
        if line.length < MIN_LEN or total_turn(path) > MAX_TURN:
            continue
        # 지상 쪽 절반이 다른 지상 도로를 가로지르면 평면 교차가 되므로 버림
        tail = LineString([line.interpolate(f, normalized=True).coords[0] for f in np.linspace(0.4, 0.75, 10)])
        crossing = False
        for j in ground_tree.query(tail):
            if idx0[j] != e0 and ground_buf[j].intersects(tail):
                crossing = True
                break
        if crossing:
            continue
        # 이미 만든 램프와 겹치면 버림
        dup = False
        for r in ramps:
            if (np.hypot(*(np.array(r["t1"]) - p1)) < DEDUPE_DIST and np.hypot(*(np.array(r["t0"]) - p0)) < DEDUPE_DIST) \
                    or LineString(r["pts"]).buffer(RAMP_W).intersection(line).length > line.length * 0.3:
                dup = True
                break
        if dup:
            continue
        per_comp[comp] += 1
        ramps.append({
            "e1": int(e1), "d1": float(d1), "t1": p1.round(2).tolist(),
            "e0": int(e0), "d0": float(d0), "t0": p0.round(2).tolist(),
            "w": RAMP_W,
            "pts": path.round(2).tolist(),
        })
    return ramps


if __name__ == "__main__":
    import json
    import os

    import common as C

    net = json.load(open(os.path.join(C.CACHE, "stage", "network2d.json")))
    segs = osmload.project_ways(osmload.load_raw_ways())
    net["ramps"] = build_ramps(net, segs)
    print("램프", len(net["ramps"]))
    json.dump(net, open(os.path.join(C.CACHE, "stage", "network2d.json"), "w"))
