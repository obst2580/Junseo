"""실제 도로 → 게임용 도로망.

1/10로 줄이면 왕복 분리 차로·가까운 평행 도로가 몇 스터드 간격으로 겹친다.
그래서 도로를 게임 폭으로 굵게 래스터화한 뒤 뼈대(skeleton)를 뽑아 중심선 그래프로 바꾼다.

- 1층(tier 1): 고속도로·도시고속도로(motorway, trunk). 고가로 띄움.
- 0층(tier 0): 간선도로(primary, secondary). 지면.
- 산 터널은 유지, 평지 지하차도·지하도로는 버린다(지상 도로와 겹치므로).
- 물 위를 지나는 구간은 다리로 표시.
"""
import math
from collections import Counter, defaultdict

import networkx as nx
import numpy as np
import shapely
from PIL import Image, ImageDraw
from shapely.geometry import LineString, Point
from shapely.ops import nearest_points, unary_union
from shapely.strtree import STRtree
from skimage.measure import label
from skimage.morphology import skeletonize

import common as C
import osmload

RES = 4.0  # 래스터 해상도 (스터드/픽셀)

TIER1 = {"motorway", "trunk"}
TIER0 = {"primary": 2, "secondary": 1}
WIDTH = {"expressway": 56.0, "primary": 48.0, "secondary": 28.0}
RASTER_W = {"expressway": 14, "primary": 12, "secondary": 7}  # 픽셀

LONG_UNDERGROUND_M = 1500  # 이보다 긴 평지 지하도로만 뺀다
SPUR_PX = 25        # 이보다 짧은 막다른 가지는 잘라냄
CONTRACT_PX = 10    # 교차로 사이 이보다 짧은 연결은 한 점으로 합침
MIN_COMPONENT_PX = 60


# ------------------------------------------------------------------ 래스터
class Raster:
    def __init__(self, bounds, res=RES):
        minx, minz, maxx, maxz = bounds
        self.x0 = math.floor(minx / 16) * 16 - 64
        self.z0 = math.floor(minz / 16) * 16 - 64
        self.res = res
        self.w = int(math.ceil((maxx + 64 - self.x0) / res))
        self.h = int(math.ceil((maxz + 64 - self.z0) / res))

    def to_px(self, xy):
        xy = np.asarray(xy)
        return np.column_stack([(xy[:, 0] - self.x0) / self.res, (xy[:, 1] - self.z0) / self.res])

    def to_xy(self, rc):
        rc = np.asarray(rc, dtype=np.float64)
        return np.column_stack([self.x0 + (rc[:, 1] + 0.5) * self.res, self.z0 + (rc[:, 0] + 0.5) * self.res])

    def new(self, mode="L"):
        return Image.new(mode, (self.w, self.h), 0)


def draw_lines(img, raster, lines, width, value=255):
    d = ImageDraw.Draw(img)
    for xy in lines:
        pts = [tuple(p) for p in raster.to_px(xy)]
        if len(pts) >= 2:
            d.line(pts, fill=value, width=width, joint="curve")
            r = width / 2
            for p in (pts[0], pts[-1]):
                d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=value)


# ------------------------------------------------------------------ 뼈대 → 그래프
OFFS = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]


def skeleton_graph(mask):
    sk = skeletonize(mask)
    H, W = sk.shape
    pad = np.pad(sk, 1)
    deg = np.zeros_like(sk, dtype=np.int8)
    for dr, dc in OFFS:
        deg += pad[1 + dr:1 + dr + H, 1 + dc:1 + dc + W]
    deg = np.where(sk, deg, 0)
    junction = sk & (deg >= 3)
    lab = label(junction, connectivity=2)
    # 노드: 교차 덩어리 + 끝점
    node_of = {}
    nodes = []
    rr, cc = np.nonzero(junction)
    groups = defaultdict(list)
    for r, c in zip(rr, cc):
        groups[lab[r, c]].append((r, c))
    for gid, pix in groups.items():
        nid = len(nodes)
        a = np.array(pix, dtype=np.float64)
        nodes.append(a.mean(axis=0))
        for p in pix:
            node_of[p] = nid
    er, ec = np.nonzero(sk & (deg == 1))
    for r, c in zip(er, ec):
        node_of[(r, c)] = len(nodes)
        nodes.append(np.array([r, c], dtype=np.float64))

    def neighbors(p):
        r, c = p
        out = []
        for dr, dc in OFFS:
            q = (r + dr, c + dc)
            if 0 <= q[0] < H and 0 <= q[1] < W and sk[q]:
                out.append(q)
        return out

    visited = set()
    edges = []
    for p, nid in list(node_of.items()):
        for q in neighbors(p):
            if q in node_of:
                if node_of[q] != nid and nid < node_of[q] and (p, q) not in visited:
                    visited.add((p, q))
                    edges.append((nid, node_of[q], [p, q]))
                continue
            if q in visited:
                continue
            path = [p, q]
            prev, cur = p, q
            visited.add(cur)
            end = None
            while True:
                nxt = None
                for n in neighbors(cur):
                    if n == prev or (n in visited and n not in node_of):
                        continue
                    if n in node_of and node_of[n] == nid and len(path) < 3:
                        continue
                    nxt = n
                    if n in node_of:
                        break
                if nxt is None:
                    break
                path.append(nxt)
                if nxt in node_of:
                    end = node_of[nxt]
                    break
                visited.add(nxt)
                prev, cur = cur, nxt
            if end is None:
                # 막다른 곳: 새 끝점 노드
                end = len(nodes)
                nodes.append(np.array(path[-1], dtype=np.float64))
                node_of[path[-1]] = end
            edges.append((nid, end, path))
    G = nx.MultiGraph()
    for i, p in enumerate(nodes):
        G.add_node(i, rc=p)
    for a, b, path in edges:
        if a == b and len(path) < 6:
            continue
        G.add_edge(a, b, path=np.array(path, dtype=np.float64))
    return G


def path_len(path):
    return float(np.hypot(*np.diff(path, axis=0).T).sum()) if len(path) > 1 else 0.0


def oriented(path, G, a, b):
    """경로 방향을 a→b로 맞추고 양 끝을 노드 위치로 고정."""
    pa = G.nodes[a]["rc"]
    if np.hypot(*(path[0] - pa)) > np.hypot(*(path[-1] - pa)):
        path = path[::-1]
    path = path.copy()
    path[0] = G.nodes[a]["rc"]
    path[-1] = G.nodes[b]["rc"]
    return path


def clean_graph(G):
    changed = True
    while changed:
        changed = False
        # 짧은 막다른 가지 제거
        for a, b, k, d in list(G.edges(keys=True, data=True)):
            if not G.has_edge(a, b, k):
                continue
            L = path_len(d["path"])
            da, db = G.degree(a), G.degree(b)
            if L < SPUR_PX and ((da == 1 and db >= 3) or (db == 1 and da >= 3)):
                G.remove_edge(a, b, k)
                for n in (a, b):
                    if G.degree(n) == 0:
                        G.remove_node(n)
                changed = True
        # 짧은 연결 합치기
        for a, b, k, d in list(G.edges(keys=True, data=True)):
            if a == b or not G.has_edge(a, b, k):
                continue
            if path_len(d["path"]) < CONTRACT_PX and G.degree(a) >= 3 and G.degree(b) >= 3:
                pa, pb = G.nodes[a]["rc"], G.nodes[b]["rc"]
                G.remove_edge(a, b, k)
                newp = (pa + pb) / 2
                for _, nb, kk, dd in list(G.edges(b, keys=True, data=True)):
                    path = dd["path"]
                    G.remove_edge(b, nb, kk)
                    G.add_edge(a, nb if nb != b else a, path=path)
                G.remove_node(b)
                G.nodes[a]["rc"] = newp
                changed = True
                break
        # 2차 노드 이어 붙이기
        for n in list(G.nodes):
            if n not in G or G.degree(n) != 2:
                continue
            es = list(G.edges(n, keys=True, data=True))
            if len(es) != 2:
                continue
            (_, a, ka, da_), (_, b, kb, db_) = es
            if a == n or b == n:
                continue
            p1 = oriented(da_["path"], G, a, n)
            p2 = oriented(db_["path"], G, n, b)
            G.remove_edge(n, a, ka)
            G.remove_edge(n, b, kb)
            G.remove_node(n)
            G.add_edge(a, b, path=np.vstack([p1, p2[1:]]))
            changed = True
    # 작은 고리(뼈대 찌꺼기) 제거
    for a, b, k, d in list(G.edges(keys=True, data=True)):
        if a == b and path_len(d["path"]) < 150:
            G.remove_edge(a, b, k)
    # 작은 조각 제거
    for comp in list(nx.connected_components(G)):
        L = sum(path_len(d["path"]) for _, _, d in G.subgraph(comp).edges(data=True))
        if L < MIN_COMPONENT_PX:
            G.remove_nodes_from(comp)
    return G


def chaikin(pts, iters=2):
    for _ in range(iters):
        if len(pts) < 3:
            return pts
        q = pts[:-1] * 0.75 + pts[1:] * 0.25
        r = pts[:-1] * 0.25 + pts[1:] * 0.75
        mid = np.empty((2 * len(q), 2))
        mid[0::2] = q
        mid[1::2] = r
        pts = np.vstack([pts[:1], mid[1:-1], pts[-1:]])
    return pts


def smooth_line(xy):
    if len(xy) < 3:
        return xy
    ls = LineString(xy).simplify(2.5, preserve_topology=False)
    pts = np.asarray(ls.coords)
    pts = chaikin(pts, 3)
    ls = LineString(pts).simplify(0.6, preserve_topology=False)
    return np.asarray(ls.coords)


# ------------------------------------------------------------------ 구간(다리/터널) 표시
def intervals_on(line, geom, merge_gap=20.0, pad=8.0, min_len=10.0):
    if geom is None or geom.is_empty or not line.intersects(geom):
        return []
    inter = line.intersection(geom)
    parts = []
    for g in getattr(inter, "geoms", [inter]):
        if g.geom_type in ("LineString", "LinearRing") and g.length > 0:
            c = list(g.coords)
            d0, d1 = line.project(Point(c[0])), line.project(Point(c[-1]))
            parts.append((min(d0, d1), max(d0, d1)))
    parts.sort()
    merged = []
    for a, b in parts:
        if merged and a - merged[-1][1] < merge_gap:
            merged[-1][1] = max(merged[-1][1], b)
        else:
            merged.append([a, b])
    out = []
    for a, b in merged:
        if b - a < min_len:
            continue
        out.append((max(0.0, a - pad), min(line.length, b + pad)))
    return out


def split_pieces(xy, bridges, tunnels):
    """선을 다리·터널 경계에서 잘라 [(종류, 점들)] 목록으로."""
    line = LineString(xy)
    marks = []
    for a, b in bridges:
        marks.append((a, b, "bridge"))
    for a, b in tunnels:
        marks.append((a, b, "tunnel"))
    marks.sort()
    cuts = {0.0, line.length}
    for a, b, _ in marks:
        cuts.add(a)
        cuts.add(b)
    # 원래 꼭짓점 + 경계점
    dists = [line.project(Point(p)) for p in xy]
    allpts = sorted(set([round(d, 3) for d in dists] + [round(c, 3) for c in cuts]))
    pts = [(d, np.asarray(line.interpolate(d).coords[0])) for d in allpts]
    kinds = []
    for i in range(len(pts) - 1):
        m = (pts[i][0] + pts[i + 1][0]) / 2
        k = "road"
        for a, b, kk in marks:
            if a <= m <= b:
                k = kk
        kinds.append(k)
    pieces = []
    cur_kind, cur = None, []
    for i in range(len(pts) - 1):
        if kinds[i] != cur_kind:
            if cur:
                pieces.append((cur_kind, np.array(cur)))
            cur_kind = kinds[i]
            cur = [pts[i][1]]
        cur.append(pts[i + 1][1])
    if cur:
        pieces.append((cur_kind, np.array(cur)))
    # 너무 짧은 조각은 앞 조각에 붙임
    out = []
    for kind, p in pieces:
        if len(p) >= 2 and np.hypot(*(p[-1] - p[0])) < 1.0 and out:
            continue
        out.append((kind, p))
    return out


# ------------------------------------------------------------------ 터널 분류
def is_mountain_tunnel(seg):
    """지붕 위로 실제 산이 있는 터널인지. 지하차도·지하도로는 아님."""
    xy = seg["xy"]
    x = xy[:, 0] - (C.INCHEON_SHIFT[0] if seg["incheon"] else 0)
    z = xy[:, 1] - (C.INCHEON_SHIFT[1] if seg["incheon"] else 0)
    # 선을 따라 촘촘히
    ls = LineString(np.column_stack([x, z]))
    if ls.length * C.STUD_M > 4000:  # 4km 넘는 터널은 도심 지하도로로 본다
        return False
    n = max(3, int(ls.length / 10))
    pts = np.array([ls.interpolate(i / (n - 1), normalized=True).coords[0] for i in range(n)])
    e = C.dem_lonlat(*C.unproj(pts[:, 0], pts[:, 1]))
    base = np.linspace(e[0], e[-1], n)
    excess = e - base
    return float(excess.max()) > 20.0 and float((excess > 10.0).mean()) >= 0.5


def build_network():
    R = C.regions()
    segs = osmload.project_ways(osmload.load_raw_ways())
    water = osmload.water_polygons()
    sea = sea_polygon()
    water_all = unary_union([g for g in (water, sea) if g is not None]).buffer(0)
    # 물가를 따라 달리는 도로가 다리로 잡히지 않도록 물 영역을 살짝 줄여서 판정
    water_detect = water_all.buffer(-12)

    raster = Raster(R["playable"].bounds)
    tier1_lines, tier0_lines = [], {1: [], 2: []}
    tunnel_lines = []
    named = []
    for s in segs:
        hw = s["hw"]
        if hw not in TIER1 and hw not in TIER0:
            continue
        if s["tunnel"]:
            if is_mountain_tunnel(s):
                tunnel_lines.append(s["xy"])
            elif LineString(s["xy"]).length * C.STUD_M > LONG_UNDERGROUND_M:
                continue  # 긴 도심 지하도로는 지상 도로와 겹치므로 뺀다
            # 짧은 지하차도는 지상 도로로 둔다 (빼면 그 자리에서 길이 끊긴다)
        if hw in TIER1:
            tier1_lines.append(s["xy"])
            if s["name"]:
                named.append((1, s["name"], LineString(s["xy"])))
        else:
            tier0_lines[TIER0[hw]].append(s["xy"])
            if s["name"]:
                named.append((0, s["name"], LineString(s["xy"])))

    img1 = raster.new()
    draw_lines(img1, raster, tier1_lines, RASTER_W["expressway"])
    img0 = raster.new()
    cls0 = raster.new()
    draw_lines(img0, raster, tier0_lines[1], RASTER_W["secondary"])
    draw_lines(cls0, raster, tier0_lines[1], RASTER_W["secondary"], value=1)
    draw_lines(img0, raster, tier0_lines[2], RASTER_W["primary"])
    draw_lines(cls0, raster, tier0_lines[2], RASTER_W["primary"], value=2)
    cls0 = np.asarray(cls0)

    tunnel_geom = unary_union([LineString(t).buffer(40) for t in tunnel_lines]) if tunnel_lines else None
    trees = {}
    for tier in (0, 1):
        items = [(n, g) for t, n, g in named if t == tier]
        trees[tier] = (STRtree([g for _, g in items]), [n for n, _ in items])

    out_nodes, out_edges = [], []
    for tier, img in ((1, img1), (0, img0)):
        G = clean_graph(skeleton_graph(np.asarray(img) > 0))
        idmap = {}
        for n, d in G.nodes(data=True):
            idmap[n] = len(out_nodes)
            xy = raster.to_xy(d["rc"][None, :])[0]
            out_nodes.append({"x": float(xy[0]), "z": float(xy[1]), "tier": tier, "deg": G.degree(n)})
        for a, b, d in G.edges(data=True):
            path = oriented(d["path"], G, a, b)
            if tier == 0:
                rr = np.clip(np.round(path[:, 0]).astype(int), 0, cls0.shape[0] - 1)
                cc = np.clip(np.round(path[:, 1]).astype(int), 0, cls0.shape[1] - 1)
                vals = cls0[rr, cc]
                vals = vals[vals > 0]
                cls = "primary" if (len(vals) == 0 or (vals == 2).mean() >= 0.5) else "secondary"
            else:
                cls = "expressway"
            xy = raster.to_xy(path)
            xy[0] = [out_nodes[idmap[a]]["x"], out_nodes[idmap[a]]["z"]]
            xy[-1] = [out_nodes[idmap[b]]["x"], out_nodes[idmap[b]]["z"]]
            xy = smooth_line(xy)
            line = LineString(xy)
            if line.length < 1:
                continue
            # 이름
            tree, names = trees[tier]
            votes = Counter()
            for f in (0.25, 0.5, 0.75):
                p = line.interpolate(f, normalized=True)
                idx = tree.nearest(p)
                if idx is not None and tree.geometries[idx].distance(p) < 40:
                    votes[names[idx]] += 1
            name = votes.most_common(1)[0][0] if votes else ""
            bridges = intervals_on(line, water_detect)
            tunnels = intervals_on(line, tunnel_geom, merge_gap=40, pad=0, min_len=30) if tunnel_geom is not None else []
            # 터널이 있는 곳은 다리로 보지 않음
            bridges = [iv for iv in bridges if not any(a < iv[1] and iv[0] < b for a, b in tunnels)]
            pieces = split_pieces(xy, bridges, tunnels)
            out_edges.append({
                "a": idmap[a],
                "b": idmap[b],
                "tier": tier,
                "cls": cls,
                "w": WIDTH[cls],
                "name": name,
                "pieces": [{"kind": k, "pts": p.round(2).tolist()} for k, p in pieces],
            })
    heal_gaps(out_nodes, out_edges, segs, water_detect, tunnel_geom, R["playable"].boundary)
    merge_deg2(out_nodes, out_edges)
    return {"nodes": out_nodes, "edges": out_edges}


# ------------------------------------------------------------------ 끊긴 끝 잇기
HEAL_R = {0: 260.0, 1: 450.0}
SEAM_R = 900.0
SEAM_X = -3800.0
HEAL_CONE = math.radians(40)
EVIDENCE = {0: {"primary", "secondary", "primary_link", "secondary_link"},
            1: {"motorway", "trunk", "motorway_link", "trunk_link"}}


def edge_points(e):
    pts = []
    for pc in e["pieces"]:
        p = [tuple(q) for q in pc["pts"]]
        pts += p if not pts else p[1:]
    return np.array(pts, dtype=np.float64)


def make_pieces(xy, water_detect, tunnel_geom):
    line = LineString(xy)
    bridges = intervals_on(line, water_detect)
    tunnels = intervals_on(line, tunnel_geom, merge_gap=40, pad=0, min_len=30) if tunnel_geom is not None else []
    bridges = [iv for iv in bridges if not any(a < iv[1] and iv[0] < b for a, b in tunnels)]
    return [{"kind": k, "pts": p.round(2).tolist()} for k, p in split_pieces(xy, bridges, tunnels)]


def split_edge(nodes, edges, ei, d):
    """간선 ei를 시작점에서 d 거리 지점에서 둘로 나누고 새 노드 번호를 돌려준다."""
    e = edges[ei]
    first, second = [], []
    acc = 0.0
    done = False
    q = None
    for pc in e["pieces"]:
        p = np.asarray(pc["pts"], dtype=np.float64)
        line = LineString(p)
        L = line.length
        if done:
            second.append(pc)
            continue
        if acc + L < d - 1e-6:
            first.append(pc)
            acc += L
            continue
        t = d - acc
        q = np.asarray(line.interpolate(t).coords[0])
        dists = np.array([line.project(Point(*x)) for x in p])
        a_pts = [x.tolist() for x, dd in zip(p, dists) if dd < t - 1e-6] + [q.tolist()]
        b_pts = [q.tolist()] + [x.tolist() for x, dd in zip(p, dists) if dd > t + 1e-6]
        if len(a_pts) >= 2:
            first.append({"kind": pc["kind"], "pts": a_pts})
        if len(b_pts) >= 2:
            second.append({"kind": pc["kind"], "pts": b_pts})
        done = True
    w = len(nodes)
    nodes.append({"x": float(q[0]), "z": float(q[1]), "tier": e["tier"], "deg": 3})
    edges[ei] = dict(e, b=w, pieces=first)
    edges.append(dict(e, a=w, pieces=second))
    return w


def heal_gaps(nodes, edges, segs, water_detect, tunnel_geom, boundary):
    """단순화 과정에서 끊긴 도로 끝을 잇는다.

    원본 OSM에서 두 곳이 실제로 (거의 곧게) 이어져 있을 때만 잇는다. 서울-인천 이음매에서는
    같은 이름의 도로끼리 잇는다. 원래 막다른 길(공항·공원 진입로 등)은 그대로 둔다.
    """
    from scipy.spatial import cKDTree

    osm = {0: nx.Graph(), 1: nx.Graph()}
    pts = {0: [], 1: []}
    ids = {0: [], 1: []}
    for sg in segs:
        for t in (0, 1):
            if sg["hw"] in EVIDENCE[t]:
                for a, b, pa, pb in zip(sg["ids"][:-1], sg["ids"][1:], sg["xy"][:-1], sg["xy"][1:]):
                    osm[t].add_edge(int(a), int(b), w=float(np.hypot(*(pb - pa))))
                for nid, p in zip(sg["ids"], sg["xy"]):
                    pts[t].append(p)
                    ids[t].append(int(nid))
    kd = {t: cKDTree(np.array(pts[t])) for t in (0, 1)}

    def evidence(t, p, q):
        dist = float(np.hypot(*(q - p)))
        src = [ids[t][i] for i in kd[t].query_ball_point(p, 70)]
        dst = set(ids[t][i] for i in kd[t].query_ball_point(q, 70))
        if not src or not dst:
            return False
        cutoff = 1.8 * dist + 120
        for sn in src[:4]:
            if sn not in osm[t]:
                continue
            lengths = nx.single_source_dijkstra_path_length(osm[t], sn, cutoff=cutoff, weight="w")
            if dst & lengths.keys():
                return True
        return False

    deg = Counter()
    for e in edges:
        deg[e["a"]] += 1
        deg[e["b"]] += 1
    healed = set()
    added = 0
    for u in range(len(nodes)):
        if deg[u] != 1 or u in healed:
            continue
        n = nodes[u]
        t = n["tier"]
        p = np.array([n["x"], n["z"]])
        if boundary.distance(Point(*p)) < 150:
            continue
        ei = next(i for i, e in enumerate(edges) if e["a"] == u or e["b"] == u)
        e = edges[ei]
        xy = edge_points(e)
        if e["b"] == u:
            xy = xy[::-1]
        line = LineString(xy)
        inner = np.asarray(line.interpolate(min(40.0, line.length * 0.5)).coords[0])
        out = p - inner
        if np.hypot(*out) < 1e-6:
            continue
        out = out / np.hypot(*out)
        seam = abs(n["x"] - SEAM_X) < 1500 and -2500 < n["z"] < 6500
        R = SEAM_R if seam else HEAL_R[t]
        cands = []
        for v, m in enumerate(nodes):
            if v == u or m["tier"] != t:
                continue
            q = np.array([m["x"], m["z"]])
            dvec = q - p
            dist = float(np.hypot(*dvec))
            if dist < 1 or dist > R:
                continue
            if math.acos(np.clip(np.dot(dvec / dist, out), -1, 1)) > HEAL_CONE:
                continue
            cands.append((dist, "node", v, q))
        for j, f in enumerate(edges):
            if j == ei or f["tier"] != t:
                continue
            fl = LineString(edge_points(f))
            pt = Point(*p)
            dist = fl.distance(pt)
            if dist < 1 or dist > R:
                continue
            dd = fl.project(pt)
            q = np.asarray(fl.interpolate(dd).coords[0])
            dvec = q - p
            if math.acos(np.clip(np.dot(dvec / np.hypot(*dvec), out), -1, 1)) > HEAL_CONE:
                continue
            cands.append((dist, "edge", (j, dd, fl.length), q))
        cands.sort(key=lambda c: c[0])
        for dist, kind, ref, q in cands[:6]:
            ok = evidence(t, p, q)
            if not ok and seam:
                other = nodes[ref]["tier"] if kind == "node" else None
                names = set()
                if kind == "node":
                    names = {f["name"] for f in edges if (f["a"] == ref or f["b"] == ref)}
                else:
                    names = {edges[ref[0]]["name"]}
                ok = bool(e["name"]) and e["name"] in names
            if not ok:
                continue
            if kind == "node":
                v = ref
            else:
                j, dd, L = ref
                if dd < 20:
                    v = edges[j]["a"]
                elif dd > L - 20:
                    v = edges[j]["b"]
                else:
                    v = split_edge(nodes, edges, j, dd)
                    deg[v] = 2
            q = np.array([nodes[v]["x"], nodes[v]["z"]])
            new_xy = np.vstack([p, q])
            edges.append({"a": u, "b": v, "tier": t, "cls": e["cls"], "w": e["w"], "name": e["name"],
                          "pieces": make_pieces(new_xy, water_detect, tunnel_geom)})
            deg[u] += 1
            deg[v] += 1
            healed.add(u)
            healed.add(v)
            added += 1
            break
    print(f"끊긴 도로 {added}곳 연결")
    resolve_dead_ends(nodes, edges, segs, water_detect, tunnel_geom, boundary)


STREET_CLASSES = {"primary", "secondary", "tertiary", "unclassified", "residential", "living_street",
                  "primary_link", "secondary_link", "tertiary_link"}
PATH_MAX = 1500.0     # 실제 길을 따라 잇는 최대 길이
STRAIGHT_MAX = 450.0  # 곧게 잇는 최대 거리
STUB_MAX = 500.0      # 이보다 짧은 막다른 꼬투리는 지운다
SEAM_PAIR_MAX = 1300.0


def _incident(edges):
    inc = {}
    for i, e in enumerate(edges):
        inc.setdefault(e["a"], []).append(i)
        inc.setdefault(e["b"], []).append(i)
    return inc


def _outward(nodes, edges, u, ei):
    e = edges[ei]
    xy = edge_points(e)
    if e["b"] == u:
        xy = xy[::-1]
    line = LineString(xy)
    p = np.array([nodes[u]["x"], nodes[u]["z"]])
    inner = np.asarray(line.interpolate(min(40.0, line.length * 0.5)).coords[0])
    out = p - inner
    n = np.hypot(*out)
    return p, (out / n if n > 1e-6 else None), line


def _attach(nodes, edges, j, q):
    """간선 j 위 q에 붙일 노드 (끝 근처면 끝 노드, 아니면 간선을 나눠 새 노드)."""
    line = LineString(edge_points(edges[j]))
    dd = line.project(Point(*q))
    if dd < 20:
        return edges[j]["a"]
    if dd > line.length - 20:
        return edges[j]["b"]
    return split_edge(nodes, edges, j, dd)


def resolve_dead_ends(nodes, edges, segs, water_detect, tunnel_geom, boundary):
    """맵 경계가 아닌 곳의 막다른 끝을 없앤다.

    1) 서울-인천 이음매: 방향이 맞는 끝끼리 곧게 잇는다 (원래 부천을 지나 이어지던 길)
    2) 실제 길(소로·동네 길 포함)을 따라 가장 가까운 다른 도로까지 잇는다
    3) 가까운 도로로 곧게 잇는다
    4) 그래도 안 되면 짧은 꼬투리는 지운다
    """
    from scipy.spatial import cKDTree

    graphs = {0: nx.Graph(), 1: nx.Graph()}
    coord = {}
    cls_set = {0: STREET_CLASSES, 1: EVIDENCE[1]}
    for sg in segs:
        for t in (0, 1):
            if sg["hw"] in cls_set[t]:
                for a, b, pa, pb in zip(sg["ids"][:-1], sg["ids"][1:], sg["xy"][:-1], sg["xy"][1:]):
                    graphs[t].add_edge(int(a), int(b), w=float(np.hypot(*(pb - pa))))
                for nid, q in zip(sg["ids"], sg["xy"]):
                    coord[int(nid)] = q
    kd = {}
    for t in (0, 1):
        order = [n for n in graphs[t].nodes]
        kd[t] = (cKDTree(np.array([coord[n] for n in order])), order)

    def is_dead(u, inc):
        return len(inc.get(u, [])) == 1 and boundary.distance(Point(nodes[u]["x"], nodes[u]["z"])) >= 150

    def in_seam(u):
        n = nodes[u]
        return abs(n["x"] - SEAM_X) < 1600 and -2600 < n["z"] < 6600

    def add_edge(u, v, xy, src_edge, cls=None, w=None):
        e = edges[src_edge]
        edges.append({"a": u, "b": v, "tier": e["tier"], "cls": cls or e["cls"], "w": w or e["w"], "name": e["name"],
                      "pieces": make_pieces(np.asarray(xy, dtype=np.float64), water_detect, tunnel_geom)})

    stats = Counter()
    # 1) 이음매 짝짓기
    inc = _incident(edges)
    seam_dead = [u for u in range(len(nodes)) if is_dead(u, inc) and in_seam(u)]
    used = set()
    pairs = []
    for i, u in enumerate(seam_dead):
        pu, ou, _ = _outward(nodes, edges, u, inc[u][0])
        if ou is None:
            continue
        for v in seam_dead[i + 1:]:
            if nodes[v]["tier"] != nodes[u]["tier"]:
                continue
            pv, ov, _ = _outward(nodes, edges, v, inc[v][0])
            if ov is None:
                continue
            d = pv - pu
            dist = float(np.hypot(*d))
            if dist < 1 or dist > SEAM_PAIR_MAX:
                continue
            dirv = d / dist
            if np.dot(ou, dirv) > 0.5 and np.dot(ov, -dirv) > 0.5:
                pairs.append((dist, u, v, pu, pv))
    for dist, u, v, pu, pv in sorted(pairs):
        if u in used or v in used:
            continue
        add_edge(u, v, [pu, pv], inc[u][0])
        used |= {u, v}
        stats["이음매"] += 1

    # 2)~4) 반복
    for _ in range(4):
        inc = _incident(edges)
        dead = [u for u in range(len(nodes)) if is_dead(u, inc)]
        if not dead:
            break
        changed = False
        removed = set()
        for u in dead:
            inc = _incident(edges)
            if not is_dead(u, inc):
                continue
            ei = inc[u][0]
            t = nodes[u]["tier"]
            p, out, own_line = _outward(nodes, edges, u, ei)
            if out is None:
                continue
            own = own_line.buffer(30)
            others = [(j, LineString(edge_points(f))) for j, f in enumerate(edges) if j != ei and f["tier"] == t and j not in removed]
            if not others:
                continue
            tree = STRtree([ln for _, ln in others])
            # 2) 실제 길 따라
            tk, order = kd[t]
            best = None
            for idx in tk.query_ball_point(p, 80)[:6]:
                sn = order[idx]
                dist, paths = nx.single_source_dijkstra(graphs[t], sn, cutoff=PATH_MAX, weight="w")
                for nid, d in sorted(dist.items(), key=lambda kv: kv[1]):
                    if d < 50:
                        continue
                    q = coord[nid]
                    pt = Point(*q)
                    if own.contains(pt):
                        continue
                    hit = None
                    for k in tree.query(pt.buffer(25)):
                        if others[k][1].distance(pt) < 25:
                            hit = others[k][0]
                            break
                    if hit is None:
                        continue
                    path = np.array([coord[x] for x in paths[nid]])
                    pl = LineString(np.vstack([p, path]))
                    if pl.length > 1 and pl.intersection(own).length <= 0.35 * pl.length:
                        if best is None or d < best[0]:
                            best = (d, path, hit, q)
                    break
            if best is not None:
                d, path, j, q = best
                v = _attach(nodes, edges, j, q)
                end = np.array([nodes[v]["x"], nodes[v]["z"]])
                xy = np.vstack([p, path[1:-1], end]) if len(path) > 2 else np.vstack([p, end])
                xy = smooth_line(xy) if len(xy) > 2 else xy
                add_edge(u, v, xy, ei, cls=("secondary" if t == 0 else None), w=(WIDTH["secondary"] if t == 0 else None))
                stats["실제 길 따라"] += 1
                changed = True
                continue
            # 3) 곧게
            cand = None
            pt = Point(*p)
            for j, ln in others:
                dist = ln.distance(pt)
                if dist < 1 or dist > STRAIGHT_MAX:
                    continue
                q = np.asarray(nearest_points(ln, pt)[0].coords[0])
                dv = (q - p) / max(np.hypot(*(q - p)), 1e-6)
                if np.dot(dv, out) < math.cos(math.radians(80)):
                    continue
                if cand is None or dist < cand[0]:
                    cand = (dist, j, q)
            if cand is not None:
                dist, j, q = cand
                v = _attach(nodes, edges, j, q)
                add_edge(u, v, [p, [nodes[v]["x"], nodes[v]["z"]]], ei)
                stats["곧게"] += 1
                changed = True
                continue
            # 4) 짧은 꼬투리 지우기
            if own_line.length < STUB_MAX:
                removed.add(ei)
                stats["꼬투리 삭제"] += 1
                changed = True
        if removed:
            for i in sorted(removed, reverse=True):
                edges.pop(i)
        if not changed:
            break
    inc = _incident(edges)
    left = Counter(nodes[u]["tier"] for u in range(len(nodes)) if is_dead(u, inc))
    print("막다른 끝 처리", dict(stats), "남음", dict(left))


def merge_deg2(nodes, edges):
    """이어 붙인 뒤 생긴 2갈래 노드는 두 간선을 하나로 합친다 (교차로 원판이 필요 없는 곳)."""
    while True:
        inc = {}
        for i, e in enumerate(edges):
            for n in (e["a"], e["b"]):
                inc.setdefault(n, []).append(i)
        target = None
        for n, lst in inc.items():
            if len(lst) == 2 and lst[0] != lst[1]:
                e1, e2 = edges[lst[0]], edges[lst[1]]
                if e1["tier"] == e2["tier"] and e1["cls"] == e2["cls"]:
                    target = (n, lst[0], lst[1])
                    break
        if target is None:
            return
        n, i1, i2 = target
        e1, e2 = edges[i1], edges[i2]

        def oriented_pieces(e, start):
            if e["a"] == start:
                return e["pieces"], e["b"]
            rev = [{"kind": pc["kind"], "pts": list(reversed(pc["pts"]))} for pc in reversed(e["pieces"])]
            return rev, e["a"]

        # e1을 n에서 끝나게, e2를 n에서 시작하게
        p1, a = oriented_pieces(e1, e1["b"] if e1["a"] == n else e1["a"])
        p2, b = oriented_pieces(e2, n)
        start = e1["a"] if e1["b"] == n else e1["b"]
        pieces = list(p1)
        for pc in p2:
            if pieces and pieces[-1]["kind"] == pc["kind"]:
                pieces[-1] = {"kind": pc["kind"], "pts": pieces[-1]["pts"] + pc["pts"][1:]}
            else:
                pieces.append(pc)
        merged = dict(e1, a=start, b=b, pieces=pieces, name=e1["name"] or e2["name"])
        for i in sorted((i1, i2), reverse=True):
            edges.pop(i)
        edges.append(merged)


def sea_polygon():
    """인천 앞바다: 플레이 구역 안에서 지형 고도가 0 이하인 곳."""
    import os
    import pickle

    cache = os.path.join(C.CACHE, "sea.pkl")
    if os.path.exists(cache):
        return pickle.load(open(cache, "rb"))
    R = C.regions()
    incheon = R["incheon"]
    dx, dz = C.INCHEON_SHIFT
    cell = 40.0
    x0, z0, x1, z1 = incheon.bounds
    xs = np.arange(x0, x1, cell)
    zs = np.arange(z0, z1, cell)
    gx, gz = np.meshgrid(xs + cell / 2, zs + cell / 2)
    e = C.dem_lonlat(*C.unproj(gx - dx, gz - dz))
    sea = e <= 0.5
    from shapely.geometry import box
    cells = []
    for j in range(sea.shape[0]):
        row = sea[j]
        i = 0
        while i < len(row):
            if row[i]:
                k = i
                while k < len(row) and row[k]:
                    k += 1
                cells.append(box(xs[i], zs[j], xs[i] + (k - i) * cell, zs[j] + cell))
                i = k
            else:
                i += 1
    g = unary_union(cells).intersection(incheon.buffer(400)).simplify(cell / 2).buffer(0)
    # 고도 자료는 매립 전 바다를 기준으로 한 곳이 있다(송도·인천신항 등). 지도에 시가지·도로가 있으면 땅으로 본다
    g = g.difference(land_evidence()).buffer(0)
    pickle.dump(g, open(cache, "wb"))
    return g


def land_evidence():
    """원본 지도로 볼 때 땅인 곳: 토지이용 면 + 다리가 아닌 도로 둘레."""
    import os
    import pickle

    cache = os.path.join(C.CACHE, "land_evidence.pkl")
    if os.path.exists(cache):
        return pickle.load(open(cache, "rb"))
    areas = osmload.load_areas()
    parts = [areas[k] for k in ("urban", "farm", "park", "green", "forest", "airport", "sand") if areas.get(k) is not None]
    R = C.regions()
    inch = R["incheon"].buffer(400)
    lines = []
    for sg in osmload.project_ways(osmload.load_raw_ways()):
        if sg["bridge"] or sg["tunnel"] or not sg["incheon"]:
            continue
        if sg["hw"] in ("motorway", "trunk", "primary", "secondary", "tertiary", "residential", "unclassified"):
            lines.append(LineString(sg["xy"]).buffer(40))
    g = unary_union(parts + lines).intersection(inch).buffer(0)
    pickle.dump(g, open(cache, "wb"))
    return g


if __name__ == "__main__":
    import json
    import os
    import time

    t = time.time()
    net = build_network()
    print("노드", len(net["nodes"]), "간선", len(net["edges"]), f"{time.time() - t:.0f}s")
    lens = Counter()
    kinds = Counter()
    for e in net["edges"]:
        for p in e["pieces"]:
            pts = np.array(p["pts"])
            L = float(np.hypot(*np.diff(pts, axis=0).T).sum())
            lens[e["cls"]] += L
            kinds[(e["tier"], p["kind"])] += L
    print({k: round(v) for k, v in lens.items()})
    print({str(k): round(v) for k, v in kinds.items()})
    os.makedirs(os.path.join(C.CACHE, "stage"), exist_ok=True)
    json.dump(net, open(os.path.join(C.CACHE, "stage", "network2d.json"), "w"))
