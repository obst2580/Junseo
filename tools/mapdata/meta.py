"""맵 부가 정보: 경계 벽, 구(區) 구역, 랜드마크 자리, 특수 구역, 스폰 위치. 모두 게임 좌표."""
import math

import numpy as np
from shapely.geometry import LineString, MultiPolygon, Point, Polygon
from shapely.ops import unary_union

import common as C
import osmload

# (이름, 위도, 경도, 지역, 반경 스터드, 종류)
LANDMARKS = [
    ("상암 월드컵경기장", 37.5683021, 126.8972477, "seoul", 120, "필수"),
    ("잠실야구장", 37.5121831, 127.0726803, "seoul", 90, "필수"),
    ("광화문광장", 37.5727839, 126.9768558, "seoul", 60, "강북 도심"),
    ("경복궁", 37.579754, 126.9766818, "seoul", 140, "강북 도심"),
    ("서울시청", 37.5667893, 126.9784204, "seoul", 60, "강북 도심"),
    ("남산타워", 37.55127, 126.9882805, "seoul", 40, "강북 도심"),
    ("국회의사당", 37.5318, 126.9142, "seoul", 110, "여의도"),
    ("63빌딩", 37.5197, 126.9403, "seoul", 40, "여의도"),
    ("여의도 금융가", 37.5253, 126.9258, "seoul", 120, "여의도"),
    ("여의도 한강공원", 37.5283, 126.9330, "seoul", 120, "여의도"),
    ("강남역 사거리", 37.4979, 127.0276, "seoul", 70, "강남"),
    ("테헤란로 업무지구", 37.5040, 127.0450, "seoul", 160, "강남"),
    ("코엑스", 37.5118733, 127.0591105, "seoul", 90, "강남"),
    ("잠실 초고층 타워", 37.5125537, 127.102679, "seoul", 50, "잠실"),
    ("석촌호수", 37.5100884, 127.1040775, "seoul", 80, "잠실"),
    ("올림픽공원", 37.5208, 127.1215, "seoul", 220, "잠실"),
    ("잠실종합운동장", 37.5150262, 127.0737253, "seoul", 110, "잠실"),
    ("김포공항", 37.5590078, 126.7942403, "seoul", 300, "공항"),
    ("서대문 교도소", 37.5745368, 126.9563173, "seoul", 70, "시설"),
    ("인천국제공항", 37.4634593, 126.4417093, "incheon", 500, "인천"),
    ("인천항·연안부두", 37.4526687, 126.629188, "incheon", 150, "인천"),
    ("월미도", 37.4729697, 126.6028277, "incheon", 100, "인천"),
    ("차이나타운", 37.4772562, 126.6191855, "incheon", 70, "인천"),
    ("관악산 채석장(가상)", 37.4449, 126.9637, "seoul", 120, "가상"),
]
FACILITY_CENTER = "서울시청"
FACILITY_RADIUS = 900


def to_game(lat, lon, region):
    x, z = C.proj(lon, lat)
    if region == "incheon":
        x, z = x + C.INCHEON_SHIFT[0], z + C.INCHEON_SHIFT[1]
    ox, oz = C.regions()["offset"]
    return float(x - ox), float(z - oz)


def ring_list(geom, ox, oz, tol):
    out = []
    polys = list(geom.geoms) if isinstance(geom, MultiPolygon) else [geom]
    for p in polys:
        if p.is_empty:
            continue
        p = p.simplify(tol)
        if p.is_empty or p.geom_type != "Polygon":
            continue
        out.append([[round(x - ox, 1), round(z - oz, 1)] for x, z in p.exterior.coords])
    return out


def build(roads3d, terr):
    R = C.regions()
    ox, oz = R["offset"]
    walls = ring_list(R["playable"], ox, oz, 24.0)

    districts = []
    for gu, g in R["seoul_gu"].items():
        districts.append({"name": "서울특별시 " + gu, "rings": ring_list(g, ox, oz, 40.0)})
    for gu, g in R["incheon_gu"].items():
        if not g.is_empty:
            districts.append({"name": "인천광역시 " + gu, "rings": ring_list(g, ox, oz, 40.0)})

    lms = []
    for name, lat, lon, region, radius, group in LANDMARKS:
        x, z = to_game(lat, lon, region)
        y = float(terr.ground_at(x, z))
        lms.append({"name": name, "group": group, "x": round(x, 1), "y": round(y, 1), "z": round(z, 1), "r": radius})

    # 농장: 플레이 구역 안의 큰 농지 몇 곳
    farms = []
    farm = osmload.load_areas().get("farm")
    if farm is not None:
        parts = [p for p in (farm.geoms if isinstance(farm, MultiPolygon) else [farm]) if p.area > 4e4]
        parts.sort(key=lambda p: -p.area)
        for p in parts[:8]:
            c = p.representative_point()
            farms.append({"x": round(c.x - ox, 1), "z": round(c.y - oz, 1), "area": round(p.area)})

    # 스폰: 서울시청 근처에서 도로와 겹치지 않는 평평한 곳
    road_buf = []
    for e in roads3d["edges"]:
        for pc in e["pieces"]:
            pts = np.asarray(pc["pts"])
            if len(pts) >= 2:
                road_buf.append(LineString(pts[:, [0, 2]]).buffer(e["w"] / 2 + 14))
    road_buf = unary_union(road_buf)
    cx, cz = to_game(37.5667893, 126.9784204, "seoul")
    spawn = None
    for r in range(0, 400, 8):
        for a in np.linspace(0, 2 * math.pi, max(8, r // 4), endpoint=False):
            p = Point(cx + r * math.cos(a), cz + r * math.sin(a))
            if not road_buf.contains(p) and road_buf.distance(p) > 6:
                spawn = p
                break
        if spawn:
            break
    sy = float(terr.ground_at(spawn.x, spawn.y))
    return {
        "offset": [ox, oz],
        "walls": walls,
        "districts": districts,
        "landmarks": lms,
        "facilityZone": {"center": FACILITY_CENTER, "x": round(cx, 1), "z": round(cz, 1), "r": FACILITY_RADIUS},
        "farms": farms,
        "spawn": {"x": round(spawn.x, 1), "y": round(sy, 1), "z": round(spawn.y, 1)},
        "attribution": "© OpenStreetMap contributors (ODbL)",
    }
