"""맵 데이터 공통 설정과 좌표 변환.

좌표계
- 투영 원점은 서울시청. X = 동쪽, Z = 남쪽 (로블록스와 같은 방향). 단위는 스터드.
- 축척 1/10, 1스터드 = 0.28m 기준 → 실제 2.8m가 1스터드.
- 인천은 동쪽으로 INCHEON_SHIFT만큼 옮겨 서울에 붙인다 (설계서 A안).
- 마지막으로 플레이 구역의 중심이 (0, 0)이 되도록 WORLD_OFFSET을 빼서 게임 좌표로 만든다.
"""
import glob
import json
import math
import os
import pickle

import numpy as np
from PIL import Image
from shapely import affinity
from shapely.geometry import MultiPolygon, Polygon, box, shape
from shapely.ops import transform, unary_union

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CACHE = os.path.join(HERE, ".cache")
OUT = os.path.join(ROOT, "map", "data")

LAT0, LON0 = 37.5663, 126.9779
MX = 111320 * math.cos(math.radians(LAT0))
MY = 110574
STUD_M = 2.8
MOUNTAIN_EXAGGERATION = 1.5
INCHEON_SHIFT = (2000.0, 0.0)
SEA_MARGIN = 1000.0

DEM_ZOOM = 12
DEM_BOUNDS = (126.28, 37.22, 127.24, 37.74)

SEOUL_GU = [
    "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구", "강북구", "도봉구",
    "노원구", "은평구", "서대문구", "마포구", "양천구", "강서구", "구로구", "금천구", "영등포구", "동작구",
    "관악구", "서초구", "강남구", "송파구", "강동구",
]
INCHEON_GU = ["제물포구", "영종구", "서해구", "검단구", "미추홀구", "연수구", "남동구", "부평구", "계양구"]


# ------------------------------------------------------------------ 투영
def proj(lon, lat):
    """경위도 → 서울시청 기준 스터드 (X 동쪽, Z 남쪽)."""
    lon = np.asarray(lon, dtype=np.float64)
    lat = np.asarray(lat, dtype=np.float64)
    return (lon - LON0) * MX / STUD_M, -(lat - LAT0) * MY / STUD_M


def unproj(x, z):
    x = np.asarray(x, dtype=np.float64)
    z = np.asarray(z, dtype=np.float64)
    return LON0 + x * STUD_M / MX, LAT0 - z * STUD_M / MY


def geom_to_studs(g):
    return transform(lambda x, y, z=None: proj(x, y), g)


# ------------------------------------------------------------------ 지형 고도
_DEM = None


def _load_dem():
    global _DEM
    if _DEM is not None:
        return _DEM
    tiles = {}
    for p in glob.glob(os.path.join(CACHE, "dem", f"{DEM_ZOOM}_*.png")):
        _, x, y = os.path.basename(p)[:-4].split("_")
        tiles[(int(x), int(y))] = p
    if not tiles:
        raise SystemExit("지형 고도 타일이 없습니다. fetch.py를 먼저 실행하세요.")
    tx0 = min(k[0] for k in tiles)
    ty0 = min(k[1] for k in tiles)
    tx1 = max(k[0] for k in tiles)
    ty1 = max(k[1] for k in tiles)
    arr = np.zeros(((ty1 - ty0 + 1) * 256, (tx1 - tx0 + 1) * 256), dtype=np.float32)
    for (x, y), p in tiles.items():
        a = np.asarray(Image.open(p).convert("RGB"), dtype=np.float32)
        arr[(y - ty0) * 256:(y - ty0 + 1) * 256, (x - tx0) * 256:(x - tx0 + 1) * 256] = (
            a[..., 0] * 256 + a[..., 1] + a[..., 2] / 256 - 32768
        )
    _DEM = (arr, tx0, ty0)
    return _DEM


def dem_lonlat(lon, lat):
    """실제 고도(m). 이중선형 보간."""
    arr, tx0, ty0 = _load_dem()
    n = 256 * 2 ** DEM_ZOOM
    px = (np.asarray(lon) + 180) / 360 * n - tx0 * 256 - 0.5
    py = (1 - np.arcsinh(np.tan(np.radians(lat))) / np.pi) / 2 * n - ty0 * 256 - 0.5
    px = np.clip(px, 0, arr.shape[1] - 1.001)
    py = np.clip(py, 0, arr.shape[0] - 1.001)
    x0 = np.floor(px).astype(np.int64)
    y0 = np.floor(py).astype(np.int64)
    fx = px - x0
    fy = py - y0
    a = arr[y0, x0] * (1 - fx) + arr[y0, x0 + 1] * fx
    b = arr[y0 + 1, x0] * (1 - fx) + arr[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


# ------------------------------------------------------------------ 경계
def _boundary(key):
    d = json.load(open(os.path.join(CACHE, "boundaries", key + ".json")))
    items = d if isinstance(d, list) else [d]
    for it in items:
        g = it.get("geojson", {})
        if it.get("category") == "boundary" and g.get("type") in ("Polygon", "MultiPolygon"):
            return geom_to_studs(shape(g)).buffer(0)
    raise SystemExit(f"경계 폴리곤 없음: {key}")


def _polys(g):
    if g is None or g.is_empty:
        return []
    return list(g.geoms) if isinstance(g, MultiPolygon) else [g]


def _land_cells(region, cell=60.0, threshold=1.0):
    x0, z0, x1, z1 = region.bounds
    xs = np.arange(x0, x1, cell)
    zs = np.arange(z0, z1, cell)
    gx, gz = np.meshgrid(xs + cell / 2, zs + cell / 2)
    land = dem_lonlat(*unproj(gx, gz)) > threshold
    cells = []
    for j in range(land.shape[0]):
        row = land[j]
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
    return unary_union(cells).simplify(cell / 2)


def _fill_gap(seoul, incheon):
    """서울 서쪽 끝과 인천 동쪽 끝 사이 자투리를 행 단위로 메운다."""
    rows = []
    step = 50
    z = 700
    while z < 3700:
        band = box(-40000, z, 40000, z + step)
        s_ = seoul.intersection(band)
        i_ = incheon.intersection(band)
        if not s_.is_empty and not i_.is_empty:
            a, b = i_.bounds[2], s_.bounds[0]
            if 0 < b - a < 3000:
                rows.append(box(a - 5, z, b + 5, z + step))
        z += step
    if not rows:
        return None
    g = unary_union(rows).buffer(150).buffer(-150).difference(seoul).difference(incheon)
    parts = [p for p in _polys(g) if p.area > 1e5]
    return unary_union(parts) if parts else None


def regions():
    """플레이 구역 계산 (서울시청 기준 좌표). 결과는 캐시에 저장."""
    cache_path = os.path.join(CACHE, "regions.pkl")
    if os.path.exists(cache_path):
        return pickle.load(open(cache_path, "rb"))
    seoul = _boundary("seoul")
    seoul_gu = {gu: _boundary("seoul_" + gu) for gu in SEOUL_GU}
    incheon_gu_real = {gu: _boundary("incheon_" + gu) for gu in INCHEON_GU}
    incheon_admin_real = unary_union(list(incheon_gu_real.values())).buffer(0)
    land = _land_cells(incheon_admin_real).intersection(incheon_admin_real)
    big_land = unary_union([p for p in _polys(land) if p.area > 4e5])
    filled = unary_union([Polygon(p.exterior) for p in _polys(incheon_admin_real.intersection(big_land.buffer(SEA_MARGIN)))])
    incheon_real = filled.intersection(incheon_admin_real).buffer(0)
    # 바다를 건너는 긴 다리(인천대교·영종대교 등)는 바다 여유 범위를 벗어나므로 다리를 따라 구역을 넓힌다
    import osmload
    import shapely as _sh
    from shapely.geometry import LineString

    corridors = []
    for w in osmload.load_raw_ways():
        if w["hw"] not in ("motorway", "trunk") or not w["bridge"]:
            continue
        x, z = proj([n[1] for n in w["nodes"]], [n[2] for n in w["nodes"]])
        if len(x) < 2 or not _sh.contains_xy(incheon_admin_real, x, z).all():
            continue
        line = LineString(np.column_stack([x, z]))
        if line.length > 500:
            corridors.append(line.buffer(350))
    if corridors:
        incheon_real = unary_union([incheon_real] + corridors).intersection(incheon_admin_real).buffer(0)
    dx, dz = INCHEON_SHIFT
    incheon = affinity.translate(incheon_real, dx, dz).difference(seoul).buffer(0)
    incheon_gu = {gu: affinity.translate(g, dx, dz).intersection(incheon).buffer(0) for gu, g in incheon_gu_real.items()}
    gap = _fill_gap(seoul, incheon)
    playable = unary_union([seoul, incheon] + ([gap] if gap is not None else [])).buffer(0)
    # 작은 구멍·섬 정리
    playable = unary_union([Polygon(p.exterior) for p in _polys(playable) if p.area > 1e5])
    minx, minz, maxx, maxz = playable.bounds
    ox = round((minx + maxx) / 2 / 16) * 16
    oz = round((minz + maxz) / 2 / 16) * 16
    r = {
        "seoul": seoul,
        "seoul_gu": seoul_gu,
        "incheon_real": incheon_real,
        "incheon": incheon,
        "incheon_gu": incheon_gu,
        "gap": gap,
        "playable": playable,
        "offset": (float(ox), float(oz)),
    }
    pickle.dump(r, open(cache_path, "wb"))
    return r


def write_json(name, obj):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    print("저장", os.path.relpath(path, ROOT), f"{os.path.getsize(path) / 1024:.0f}KB")
