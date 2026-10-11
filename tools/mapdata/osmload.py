"""OSM 도로·물 데이터 읽기 (서울 PBF + 영종도 API 타일)."""
import glob
import os
import pickle

import numpy as np
import osmium
import shapely
from shapely import affinity, wkb
from shapely.geometry import LineString

import common as C

ROAD_CLASSES = {
    "motorway", "motorway_link", "trunk", "trunk_link",
    "primary", "primary_link", "secondary", "secondary_link", "tertiary",
}


class _Roads(osmium.SimpleHandler):
    def __init__(self, seen):
        super().__init__()
        self.seen = seen
        self.ways = []

    def way(self, w):
        hw = w.tags.get("highway")
        if hw not in ROAD_CLASSES or w.id in self.seen:
            return
        if w.tags.get("area") == "yes":
            return
        try:
            nodes = [(n.ref, n.lon, n.lat) for n in w.nodes]
        except osmium.InvalidLocationError:
            nodes = [(n.ref, n.lon, n.lat) for n in w.nodes if n.location.valid()]
        if len(nodes) < 2:
            return
        self.seen.add(w.id)
        t = w.tags
        self.ways.append({
            "id": w.id,
            "hw": hw,
            "name": t.get("name", ""),
            "ref": t.get("ref", ""),
            "tunnel": t.get("tunnel", "no") not in ("no", ""),
            "bridge": t.get("bridge", "no") not in ("no", ""),
            "layer": int(t.get("layer", "0")) if t.get("layer", "0").lstrip("-").isdigit() else 0,
            "nodes": nodes,
        })


def load_raw_ways():
    cache = os.path.join(C.CACHE, "raw_ways.pkl")
    if os.path.exists(cache):
        return pickle.load(open(cache, "rb"))
    seen = set()
    files = [os.path.join(C.CACHE, "bbbike", "Seoul.osm.pbf")] + sorted(glob.glob(os.path.join(C.CACHE, "osmapi", "*.osm")))
    ways = []
    for f in files:
        h = _Roads(seen)
        h.apply_file(f, locations=True)
        ways += h.ways
    pickle.dump(ways, open(cache, "wb"))
    return ways


def project_ways(ways):
    """경위도 → 스터드. 서울 노드는 그대로, 인천 노드는 옮기고, 그 밖(부천 등)은 버린다.

    결과: 연속된 유효 노드 구간마다 하나씩 {"hw", "name", ..., "xy": (N,2) array, "ids": [...]} 목록.
    """
    R = C.regions()
    seoul, incheon, inch_real = R["seoul"], R["incheon"], R["incheon_real"]
    seoul_buf = seoul.buffer(80)
    inch_buf = incheon.buffer(80)
    shapely.prepare(seoul_buf)
    shapely.prepare(inch_buf)
    shapely.prepare(inch_real)
    dx, dz = C.INCHEON_SHIFT
    out = []
    for w in ways:
        ids = np.array([n[0] for n in w["nodes"]], dtype=np.int64)
        lon = np.array([n[1] for n in w["nodes"]])
        lat = np.array([n[2] for n in w["nodes"]])
        x, z = C.proj(lon, lat)
        in_inch = shapely.contains_xy(inch_real, x, z)
        xs = np.where(in_inch, x + dx, x)
        zs = np.where(in_inch, z + dz, z)
        ok = np.where(in_inch, shapely.contains_xy(inch_buf, xs, zs), shapely.contains_xy(seoul_buf, xs, zs))
        i = 0
        n = len(ids)
        while i < n:
            if not ok[i]:
                i += 1
                continue
            k = i
            while k < n and ok[k] and in_inch[k] == in_inch[i]:
                k += 1
            if k - i >= 2:
                seg = dict(w)
                del seg["nodes"]
                seg["xy"] = np.column_stack([xs[i:k], zs[i:k]])
                seg["ids"] = ids[i:k]
                seg["incheon"] = bool(in_inch[i])
                out.append(seg)
            i = k
    return out


LANDUSE_CLASSES = {
    # 지형 재질 칠하기용 분류
    "urban": {"residential", "commercial", "retail", "industrial", "construction", "railway", "garages", "education", "religious", "military", "brownfield"},
    "farm": {"farmland", "farmyard", "orchard", "allotments", "greenhouse_horticulture", "meadow", "vineyard"},
    "green": {"grass", "village_green", "recreation_ground", "cemetery", "greenfield", "flowerbed", "greenery"},
}


class _Areas(osmium.SimpleHandler):
    def __init__(self, seen):
        super().__init__()
        self.fab = osmium.geom.WKBFactory()
        self.items = []
        self.seen = seen

    def area(self, a):
        t = a.tags
        kind = None
        if t.get("natural") == "water" or t.get("waterway") == "riverbank" or t.get("landuse") in ("reservoir", "basin"):
            kind = "water"
        elif t.get("natural") in ("wood",) or t.get("landuse") == "forest":
            kind = "forest"
        elif t.get("leisure") in ("park", "garden", "golf_course", "pitch", "nature_reserve"):
            kind = "park"
        elif t.get("natural") in ("beach", "sand"):
            kind = "sand"
        elif t.get("aeroway") == "aerodrome":
            kind = "airport"
        else:
            lu = t.get("landuse")
            for k, vals in LANDUSE_CLASSES.items():
                if lu in vals:
                    kind = k
                    break
        if kind is None:
            return
        key = (a.from_way(), a.orig_id())
        if key in self.seen:
            return
        try:
            g = wkb.loads(self.fab.create_multipolygon(a), hex=True)
        except Exception:
            return
        self.seen.add(key)
        self.items.append((kind, g))


def load_areas():
    """물·숲·공원·도시·농지 등 면 데이터 (스터드 좌표, 인천은 옮김, 플레이 구역+여유로 자름)."""
    from shapely.ops import unary_union

    cache = os.path.join(C.CACHE, "areas.pkl")
    if os.path.exists(cache):
        return pickle.load(open(cache, "rb"))
    files = (sorted(glob.glob(os.path.join(C.CACHE, "osmrel", "*.osm")))
             + [os.path.join(C.CACHE, "bbbike", "Seoul.osm.pbf")]
             + sorted(glob.glob(os.path.join(C.CACHE, "osmapi", "*.osm"))))
    raw = []
    seen = set()
    for f in files:
        h = _Areas(seen)
        try:
            h.apply_file(f, locations=True)
        except Exception as e:
            print("면 데이터 읽기 실패", os.path.basename(f), e)
            continue
        raw += h.items
    R = C.regions()
    seoul, incheon_real = R["seoul"], R["incheon_real"]
    dx, dz = C.INCHEON_SHIFT
    clip = R["playable"].buffer(1600)
    by_kind = {}
    for kind, g in raw:
        g = C.geom_to_studs(g).buffer(0)
        if g.is_empty:
            continue
        parts = []
        a = g.intersection(seoul.buffer(50))
        if not a.is_empty:
            parts.append(a)
        b = g.intersection(incheon_real)
        if not b.is_empty:
            parts.append(affinity.translate(b, dx, dz).difference(seoul))
        for p in parts:
            p = p.intersection(clip)
            if not p.is_empty and p.area > 100:
                by_kind.setdefault(kind, []).append(p)
    out = {k: unary_union(v).buffer(0) for k, v in by_kind.items()}
    pickle.dump(out, open(cache, "wb"))
    return out


def water_polygons():
    return load_areas().get("water")
