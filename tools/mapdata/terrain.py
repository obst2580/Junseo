"""지형 격자: 높이·물·재질.

- 실제 고도에서 E0(m) 아래는 평지(높이 0)로 두고, 그 위로 솟은 부분만 1.5배 과장해서 산으로 만든다.
  그래서 도시 대부분은 평평하고, 북한산·관악산·남산 같은 산과 동네 언덕만 솟는다.
- 물(한강·호수·인천 앞바다)은 수면을 WATER_LEVEL에 두고, 물가에서 멀수록 깊어진다.
- 결과는 16스터드 간격 격자(꼭짓점 값)로 저장하고, 로블록스에서 지형 생성 스크립트가 읽는다.

좌표는 게임 좌표(플레이 구역 중심이 원점)다.
"""
import base64
import os
import pickle

import numpy as np
import shapely
from PIL import Image, ImageDraw
from scipy import ndimage

import common as C
import osmload
import roads

GRID = 16          # 저장 격자 간격
UNIT = 1.0         # 저장 높이 단위(스터드). 지형 칸이 4스터드라 1스터드면 충분
WORK = 8           # 계산용 격자 간격
MARGIN = 1024      # 플레이 구역 밖 배경 지형 폭
E0 = 22.0          # 이 고도(m) 아래는 평지
H_PER_M = C.MOUNTAIN_EXAGGERATION / C.STUD_M
WATER_LEVEL = -3.0
BANK = 24.0        # 물가 비탈 폭
SEAM_X = -3800.0   # 이보다 서쪽(서울시청 기준)은 인천 쪽 배경으로 본다
BARE_PX = 9        # 건물 걷어내기 폭 (계산 격자 칸, 9칸 = 72스터드 = 실제 약 200m)
FAR_START = 300.0  # 플레이 구역 밖 이 거리부터 배경 지형을 낮춘다
FAR_END = 800.0

MATERIALS = ["Grass", "LeafyGrass", "Rock", "Sand", "Ground", "Pavement", "Concrete", "Mud", "Slate", "Asphalt"]
M = {name: i for i, name in enumerate(MATERIALS)}


class Terrain:
    def __init__(self):
        R = C.regions()
        self.R = R
        ox, oz = R["offset"]
        self.ox, self.oz = ox, oz
        minx, minz, maxx, maxz = R["playable"].bounds
        x0 = np.floor((minx - ox - MARGIN) / GRID) * GRID
        z0 = np.floor((minz - oz - MARGIN) / GRID) * GRID
        x1 = np.ceil((maxx - ox + MARGIN) / GRID) * GRID
        z1 = np.ceil((maxz - oz + MARGIN) / GRID) * GRID
        self.x0, self.z0 = float(x0), float(z0)
        self.nx = int((x1 - x0) / GRID) + 1
        self.nz = int((z1 - z0) / GRID) + 1
        # 계산용 격자 (WORK 간격): 저장 격자 꼭짓점이 계산 격자의 짝수 번째 점과 겹친다
        self.wx = (self.nx - 1) * (GRID // WORK) + 1
        self.wz = (self.nz - 1) * (GRID // WORK) + 1

    # 게임 좌표 → 서울시청 기준
    def to_seoul(self, x, z):
        return x + self.ox, z + self.oz

    def work_coords(self):
        xs = self.x0 + np.arange(self.wx) * WORK
        zs = self.z0 + np.arange(self.wz) * WORK
        return np.meshgrid(xs, zs)

    def rasterize(self, geom):
        """서울시청 기준 좌표의 폴리곤 → 계산 격자 마스크."""
        img = Image.new("L", (self.wx, self.wz), 0)
        if geom is None or geom.is_empty:
            return np.zeros((self.wz, self.wx), bool)
        d = ImageDraw.Draw(img)

        def px(coords):
            a = np.asarray(coords)
            return [((x - self.ox - self.x0) / WORK, (z - self.oz - self.z0) / WORK) for x, z in a[:, :2]]

        for p in getattr(geom, "geoms", [geom]):
            if p.geom_type != "Polygon" or p.is_empty:
                continue
            d.polygon(px(p.exterior.coords), fill=255)
            for h in p.interiors:
                d.polygon(px(h.coords), fill=0)
        return np.asarray(img) > 0

    def build(self):
        R = self.R
        gx, gz = self.work_coords()
        sx, sz = self.to_seoul(gx, gz)
        in_seoul = self.rasterize(R["seoul"])
        in_inch = self.rasterize(R["incheon"])
        in_gap = self.rasterize(R["gap"])
        playable = self.rasterize(R["playable"])
        # 지형 고도를 읽을 실제 위치: 인천 쪽은 옮기기 전 위치
        inch_side = in_inch | (~in_seoul & ~in_gap & (sx < SEAM_X))
        dx, dz = C.INCHEON_SHIFT
        lon, lat = C.unproj(np.where(inch_side, sx - dx, sx), np.where(inch_side, sz - dz, sz))
        e = C.dem_lonlat(lon, lat).astype(np.float32)

        areas = osmload.load_areas()
        forest = self.rasterize(areas.get("forest"))
        park = self.rasterize(areas.get("park")) | self.rasterize(areas.get("green"))
        farm = self.rasterize(areas.get("farm"))
        urban = self.rasterize(areas.get("urban"))
        sand = self.rasterize(areas.get("sand"))
        built = self.road_mask(140.0) & playable
        city = (built | urban) & ~forest

        # 위성 고도 자료에는 고층 건물 높이가 섞여 있어 도심에 가짜 언덕이 생긴다.
        # 시가지에서는 건물 크기보다 넓게 최솟값을 깔아(열림 연산) 건물을 걷어내고 부드럽게 다듬은 맨땅 고도를 쓴다.
        e_bare = ndimage.gaussian_filter(ndimage.grey_opening(e, size=(BARE_PX, BARE_PX)), 3)
        w_city = np.clip(ndimage.gaussian_filter(city.astype(np.float32), 4) * 1.4, 0, 1)
        e = (w_city * np.minimum(e, e_bare) + (1 - w_city) * e).astype(np.float32)
        h_land = np.maximum(0.0, e - E0) * H_PER_M

        # 물
        water_osm = self.rasterize(areas.get("water"))
        sea_poly = self.rasterize(roads.sea_polygon())
        sea_dem = inch_side & (e <= 0.5) & ~playable
        water = water_osm | sea_poly | sea_dem
        # 플레이 구역 안에서는 인천 쪽 바다만 지형 고도로 판정 (서울은 OSM 물만)
        water |= in_inch & (e <= 0.5)
        water = ndimage.binary_opening(water, iterations=1) | water_osm

        d_land = ndimage.distance_transform_edt(water) * WORK
        d_water = ndimage.distance_transform_edt(~water) * WORK

        # 높은 곳의 호수는 수면을 주변 땅 높이에 맞춘다
        level = np.full(water.shape, WATER_LEVEL, np.float32)
        lab, n = ndimage.label(water)
        objs = ndimage.find_objects(lab)
        for k, sl in enumerate(objs, start=1):
            if sl is None:
                continue
            sl2 = tuple(slice(max(0, s.start - 2), s.stop + 2) for s in sl)
            comp = lab[sl2] == k
            ring = ndimage.binary_dilation(comp, iterations=2) & ~comp
            if not ring.any():
                continue
            base = float(np.min(h_land[sl2][ring]))
            if base > 1.5:
                lv = level[sl2]
                lv[comp] = base - 1.5
        # 깊이는 물가에서 멀어질수록 2스터드씩 계단식으로 깊어지다가 20에서 멈춘다.
        # 넓은 바다가 균일해져서 지형을 큰 덩어리로 한 번에 채울 수 있다 (생성 속도·저장 크기)
        depth = 4.0 + 2.0 * np.floor(np.minimum(8.0, d_land / 40.0))
        h = np.where(water, level - depth, h_land)
        bank = (~water) & (d_water < BANK)
        bank_h = (WATER_LEVEL + 0.5) * (1 - d_water / BANK)
        h = np.where(bank & (h_land < 6), np.minimum(h, bank_h), h)
        water_top = np.where(water, level, np.nan)

        # 재질
        mat = np.full(water.shape, M["Grass"], np.uint8)
        gy, gxx = np.gradient(h, WORK)
        slope = np.hypot(gxx, gy)
        # 산은 숲, 시가지 바닥은 높이와 상관없이 회색 콘크리트.
        # 보도블록 느낌의 Pavement는 도로변 인도에만 쓴다 (지형 생성기가 칠함)
        mat[h_land > 8] = M["LeafyGrass"]
        mat[city] = M["Concrete"]
        mat[farm] = M["Ground"]
        mat[park] = M["Grass"]
        mat[forest] = M["LeafyGrass"]
        mat[sand] = M["Sand"]
        mat[bank & inch_side] = M["Sand"]
        mat[bank & ~inch_side] = M["Grass"]
        mat[(slope > 0.9) & ~water] = M["Rock"]
        mat[(slope > 0.6) & (h > 60) & ~water & (slope <= 0.9)] = M["Slate"]
        mat[water] = np.where(d_land[water] < 24, M["Sand"], M["Mud"])

        # 플레이 구역에서 멀리 떨어진 배경은 낮춰서 평지·바다로 만든다 (보이기만 하고 갈 수 없는 곳).
        # 가까운 배경(약 300스터드)까지만 실제 산을 두고, 그 뒤로 800스터드까지 서서히 낮춘다.
        d_play = ndimage.distance_transform_edt(~playable) * WORK
        fade = np.clip((d_play - FAR_START) / (FAR_END - FAR_START), 0, 1)
        fade = fade * fade * (3 - 2 * fade)
        land_far = ~water
        h = np.where(land_far, h * (1 - fade), h)
        far = d_play > FAR_END
        mat[far & ~water] = M["LeafyGrass"]
        mat[far & water] = M["Mud"]
        # 도시 안 재질 얼룩 줄이기 (가장 많은 재질로 정리)
        from skimage.filters.rank import majority
        from skimage.morphology import square

        land_mat = np.where(water, 255, mat).astype(np.uint8)
        smoothed = majority(land_mat, square(5))
        keep = (mat == M["Rock"]) | (mat == M["Slate"]) | water | (smoothed == 255)
        mat = np.where(keep, mat, smoothed).astype(np.uint8)

        self.h_work = h.astype(np.float32)
        self.ground_work = np.where(water, 0.0, np.maximum(h, 0.0)).astype(np.float32)
        self.water_work = water
        self.playable_work = playable
        st = GRID // WORK
        self.height = self.h_work[::st, ::st]
        self.water_top = water_top[::st, ::st]
        self.material = mat[::st, ::st]
        return self

    def road_mask(self, half_width):
        """지상 간선도로 주변(시가지로 보는 범위) 마스크."""
        import json

        net = json.load(open(os.path.join(C.CACHE, "stage", "network2d.json")))
        img = Image.new("L", (self.wx, self.wz), 0)
        d = ImageDraw.Draw(img)
        w = max(1, int(half_width * 2 / WORK))
        for e in net["edges"]:
            if e["tier"] != 0:
                continue
            for pc in e["pieces"]:
                pts = [((x - self.ox - self.x0) / WORK, (z - self.oz - self.z0) / WORK) for x, z in pc["pts"]]
                if len(pts) >= 2:
                    d.line(pts, fill=255, width=w, joint="curve")
        return np.asarray(img) > 0

    # ------------------------------------------------------------------ 도로 높이용
    def ground_at(self, x, z):
        """게임 좌표에서 도로 기준 지면 높이 (물 위는 0, 물가 비탈은 0으로 올림)."""
        fx = (np.asarray(x) - self.x0) / WORK
        fz = (np.asarray(z) - self.z0) / WORK
        fx = np.clip(fx, 0, self.wx - 1.001)
        fz = np.clip(fz, 0, self.wz - 1.001)
        i = np.floor(fx).astype(int)
        j = np.floor(fz).astype(int)
        tx, tz = fx - i, fz - j
        g = self.ground_work
        a = g[j, i] * (1 - tx) + g[j, i + 1] * tx
        b = g[j + 1, i] * (1 - tx) + g[j + 1, i + 1] * tx
        return a * (1 - tz) + b * tz

    def is_water(self, x, z):
        i = np.clip(np.round((np.asarray(x) - self.x0) / WORK).astype(int), 0, self.wx - 1)
        j = np.clip(np.round((np.asarray(z) - self.z0) / WORK).astype(int), 0, self.wz - 1)
        return self.water_work[j, i]

    # ------------------------------------------------------------------ 저장
    def encoded(self):
        hq = np.round(self.height / UNIT).astype(np.int64)
        wt = np.where(np.isnan(self.water_top), -32768, np.round(np.nan_to_num(self.water_top) / UNIT)).astype(np.int64)
        return {
            "grid": GRID,
            "x0": self.x0,
            "z0": self.z0,
            "nx": self.nx,
            "nz": self.nz,
            "unit": UNIT,
            "waterLevel": WATER_LEVEL,
            "materials": MATERIALS,
            "height": encode_grid(hq),
            "waterTop": encode_grid(wt),
            "material": encode_grid(self.material.astype(np.int64)),
        }


def _varint(v, out):
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def encode_grid(a):
    """행 단위 차분 + 반복 압축 + 가변 길이 정수 → base64.

    토큰 T (가변 길이 정수):
      T 짝수: 리터럴. 차분 d = 지그재그 복원(T >> 1), 값 = 이전 값 + d
      T 홀수: 반복. 이전 값을 (T >> 1) + 2번 반복
    각 행은 이전 값 0에서 시작한다.
    """
    out = bytearray()
    for row in a:
        prev = 0
        i = 0
        n = len(row)
        while i < n:
            v = int(row[i])
            if v == prev:
                k = i
                while k < n and int(row[k]) == prev:
                    k += 1
                run = k - i
                if run >= 2:
                    _varint(((run - 2) << 1) | 1, out)
                    i = k
                    continue
            d = v - prev
            z = (d << 1) if d >= 0 else ((-d << 1) - 1)
            _varint(z << 1, out)
            prev = v
            i += 1
    return base64.b64encode(bytes(out)).decode("ascii")


def decode_grid(s, nx, nz):
    """검증용 디코더 (로블록스 쪽 디코더와 같은 규칙)."""
    data = base64.b64decode(s)
    out = np.zeros((nz, nx), np.int64)
    pos = 0

    def varint():
        nonlocal pos
        v = 0
        shift = 0
        while True:
            b = data[pos]
            pos += 1
            v |= (b & 0x7F) << shift
            if b < 0x80:
                return v
            shift += 7

    for j in range(nz):
        prev = 0
        i = 0
        while i < nx:
            t = varint()
            if t & 1:
                run = (t >> 1) + 2
                out[j, i:i + run] = prev
                i += run
            else:
                z = t >> 1
                d = (z >> 1) if (z & 1) == 0 else -((z + 1) >> 1)
                prev += d
                out[j, i] = prev
                i += 1
    assert pos == len(data)
    return out


def load():
    cache = os.path.join(C.CACHE, "terrain.pkl")
    if os.path.exists(cache):
        return pickle.load(open(cache, "rb"))
    t = Terrain().build()
    pickle.dump(t, open(cache, "wb"))
    return t


if __name__ == "__main__":
    import time

    import terrain as _self  # 피클이 __main__ 대신 모듈 경로로 저장되도록

    t0 = time.time()
    t = _self.load()
    print("격자", t.nx, "x", t.nz, f"{time.time() - t0:.0f}s")
    print("높이 범위", float(t.height.min()), float(t.height.max()))
    enc = t.encoded()
    for k in ("height", "waterTop", "material"):
        print(k, f"{len(enc[k]) / 1024:.0f}KB")
    back = decode_grid(enc["height"], t.nx, t.nz)
    assert np.array_equal(back, np.round(t.height / _self.UNIT).astype(np.int64)), "디코딩 불일치"
    print("디코딩 검증 통과")
