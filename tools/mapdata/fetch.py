"""맵 원본 데이터 내려받기.

받는 것:
- OpenStreetMap 서울 추출본 (BBBike): PBF + 셰이프파일
- 영종도 일대 OSM 데이터 (BBBike 범위 밖이라 OSM API로 타일 단위)
- 행정구역 경계 (Nominatim): 서울시, 서울 25개 구, 인천 9개 구
- 지형 고도 타일 (AWS Terrain Tiles, Terrarium 형식)

모두 tools/mapdata/.cache/ 아래에 저장하며, 이미 받은 파일은 다시 받지 않는다.
"""
import json
import math
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor

from common import CACHE, DEM_BOUNDS, DEM_ZOOM, INCHEON_GU, SEOUL_GU

UA = {"User-Agent": "seoul-rp-roblox-map/0.1 (github.com/obst2580/Junseo)", "Accept-Language": "ko"}


def download(url, path, delay=0.0):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    time.sleep(delay)
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=300) as r, open(path + ".part", "wb") as f:
        while chunk := r.read(1 << 20):
            f.write(chunk)
    os.replace(path + ".part", path)
    print("받음", os.path.relpath(path, CACHE))
    return path


def fetch_bbbike():
    base = "https://download.bbbike.org/osm/bbbike/Seoul/"
    download(base + "Seoul.osm.pbf", os.path.join(CACHE, "bbbike", "Seoul.osm.pbf"))
    shp_zip = download(base + "Seoul.osm.shp.zip", os.path.join(CACHE, "bbbike", "Seoul.osm.shp.zip"))
    if not os.path.exists(os.path.join(CACHE, "bbbike", "Seoul-shp")):
        zipfile.ZipFile(shp_zip).extractall(os.path.join(CACHE, "bbbike"))


# BBBike 서울 추출본은 경도 126.58 동쪽만 포함한다. 영종도·인천대교 쪽은 OSM API로 받는다.
YEONGJONG_BBOX = (126.34, 37.38, 126.62, 37.57)
OSM_TILE = (0.06, 0.04)


def fetch_yeongjong():
    x0, y0, x1, y1 = YEONGJONG_BBOX
    tiles = []
    lon = x0
    while lon < x1 - 1e-9:
        lat = y0
        while lat < y1 - 1e-9:
            tiles.append((round(lon, 4), round(lat, 4), round(min(lon + OSM_TILE[0], x1), 4), round(min(lat + OSM_TILE[1], y1), 4)))
            lat += OSM_TILE[1]
        lon += OSM_TILE[0]
    for t in tiles:
        fetch_osm_tile(t)


def fetch_osm_tile(t, depth=0):
    """노드가 너무 많으면(API가 400을 돌려줌) 4등분해서 다시 받는다."""
    name = "_".join(f"{v:.3f}" for v in t)
    url = "https://api.openstreetmap.org/api/0.6/map?bbox=" + ",".join(f"{v:.4f}" for v in t)
    try:
        download(url, os.path.join(CACHE, "osmapi", f"{name}.osm"), delay=1.0)
    except urllib.error.HTTPError as e:
        if e.code != 400 or depth >= 3:
            raise
        x0, y0, x1, y1 = t
        mx, my = (x0 + x1) / 2, (y0 + y1) / 2
        for sub in ((x0, y0, mx, my), (mx, y0, x1, my), (x0, my, mx, y1), (mx, my, x1, y1)):
            fetch_osm_tile(sub, depth + 1)


NOMINATIM = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&polygon_geojson=1&polygon_threshold=0.0002&q="


def fetch_boundaries():
    jobs = {"seoul": "https://nominatim.openstreetmap.org/lookup?format=jsonv2&polygon_geojson=1&polygon_threshold=0.0002&osm_ids=R2297418"}
    for gu in SEOUL_GU:
        jobs["seoul_" + gu] = NOMINATIM + urllib.parse.quote(f"서울특별시 {gu}")
    for gu in INCHEON_GU:
        jobs["incheon_" + gu] = NOMINATIM + urllib.parse.quote(f"인천광역시 {gu}")
    for key, url in jobs.items():
        download(url, os.path.join(CACHE, "boundaries", key + ".json"), delay=1.2)
    # 검사: 각 결과에 경계 폴리곤이 있어야 한다
    for key in jobs:
        d = json.load(open(os.path.join(CACHE, "boundaries", key + ".json")))
        items = d if isinstance(d, list) else [d]
        ok = any(i.get("category") == "boundary" and i.get("geojson", {}).get("type") in ("Polygon", "MultiPolygon") for i in items)
        if not ok:
            raise SystemExit(f"경계 폴리곤 없음: {key}")


def dem_tile(lon, lat, z=DEM_ZOOM):
    n = 2 ** z
    x = (lon + 180) / 360 * n
    y = (1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n
    return int(x), int(y)


def fetch_dem():
    lon0, lat0, lon1, lat1 = DEM_BOUNDS
    x0, y0 = dem_tile(lon0, lat1)
    x1, y1 = dem_tile(lon1, lat0)
    jobs = [(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)]

    def one(xy):
        x, y = xy
        url = f"https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{DEM_ZOOM}/{x}/{y}.png"
        return download(url, os.path.join(CACHE, "dem", f"{DEM_ZOOM}_{x}_{y}.png"))

    with ThreadPoolExecutor(8) as ex:
        list(ex.map(one, jobs))


# 서울 추출본에서 빠지거나 잘린 큰 물 관계 (한강 서쪽 구간 152336, 동쪽 구간 3769500)
EXTRA_RELATIONS = [152336, 3769500]


def fetch_relations():
    for rid in EXTRA_RELATIONS:
        url = f"https://api.openstreetmap.org/api/0.6/relation/{rid}/full"
        download(url, os.path.join(CACHE, "osmrel", f"{rid}.osm"), delay=1.0)


if __name__ == "__main__":
    fetch_bbbike()
    fetch_relations()
    fetch_dem()
    fetch_boundaries()
    fetch_yeongjong()
    print("완료")
