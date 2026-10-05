package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 공항 섬의 남은 땅: 처음 들어온 사람이 차를 빌리는 렌터카 차고지(공영 차고, 차 꺼내는 곳),
 * 공항 호텔, 항공 화물터미널과 화물 도로. 모두 공항 순환도로(공항로)에 붙여 둡니다.
 */
final class AirportExtras {

    static List<Placement> plan(CityTerrain t, List<Placement> existing) {
        Polygon area = Plans.district(t, "airport");
        if (area == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        claimFootprints(m, existing);
        Random rnd = Plans.random(t, "airport-extras");
        List<Placement> out = new ArrayList<>();

        // 화물터미널 (활주로 남쪽 끝) → 공항 순환도로까지 화물 도로. 도로 남쪽에 렌터카 차고지(순환도로 가까이)와 호텔이 길을 봄
        int[] cargo = nearestRect(m, -1600, 610, 56, 36, 70);
        if (cargo == null) {
            return out;
        }
        long cs = rnd.nextLong();
        out.add(Placement.rect("항공 화물터미널", "cargo", cargo[0], cargo[1], cargo[2], cargo[3], "east",
                (w, d) -> Factory.build(w, d, new Random(cs), false)));
        m.claim(cargo[0] - 1, cargo[1] - 1, cargo[2] + 1, cargo[3] + 1);
        int[] road = road(t, m, cargo, out, existing);
        if (road == null) {
            return out;
        }
        int rz0 = road[3] + 2;
        int[] rent = alongRoad(m, road[2] - 3, rz0, 46, 40, road[0]);
        if (rent != null) {
            long seed = rnd.nextLong();
            out.add(Placement.rect("공항 렌터카 차고지", "garage", rent[0], rent[1], rent[2], rent[3], "north",
                    (w, d) -> Garage.rental(w, d, "준서렌터카", new Random(seed))));
            m.claim(rent[0] - 1, rent[1] - 1, rent[2] + 1, rent[3] + 1);
        }
        int[] hotel = alongRoad(m, (rent == null ? road[2] - 3 : rent[0] - 6), rz0, 32, 26, road[0] + 1);
        if (hotel != null) {
            long seed = rnd.nextLong();
            out.add(Placement.rect("준서 에어포트 호텔", "hotel", hotel[0], hotel[1], hotel[2], hotel[3], "north",
                    (w, d) -> hotel(w, d, new Random(seed))));
            m.claim(hotel[0] - 1, hotel[1] - 1, hotel[2] + 1, hotel[3] + 1);
        }
        return out;
    }

    /** 화물터미널 동쪽 가운데에서 동쪽으로 공항로까지 폭 7 도로. 놓은 직사각형 {x0, z0, x1, z1} (못 놓으면 null) */
    private static int[] road(CityTerrain t, BuildMask m, int[] cargo, List<Placement> out, List<Placement> existing) {
        // 터미널(북쪽)을 피해 남쪽 줄부터 막히지 않은 줄을 찾음
        for (int zc = cargo[3] - 4; zc >= cargo[1] + 4; zc--) {
            int[] r = roadAt(t, m, cargo[2] + 1, zc, existing);
            if (r != null) {
                return place(m, r, out);
            }
        }
        return null;
    }

    /** x0 에서 동쪽으로 길까지 폭 7 (가운데 zc) 이 막히지 않으면 {x0, z0, x1, z1} */
    private static int[] roadAt(CityTerrain t, BuildMask m, int x0, int zc, List<Placement> existing) {
        int x1 = x0;
        while (x1 < x0 + 160 && !t.column(x1 + 1, zc).isRoad()) {
            x1++;
        }
        if (x1 >= x0 + 160 || x1 <= x0) {
            return null;
        }
        int z0 = zc - 3, z1 = zc + 3;
        for (int x = x0 + 1; x <= x1; x++) { // x0 은 화물터미널에 붙은 칸 (터미널 둘레로 막아 둔 자리)
            for (int z = z0; z <= z1; z++) {
                if (t.column(x, z).isRoad() || t.column(x, z).isWater()) {
                    return null;
                }
                // 길가 몇 칸은 순환도로 가로수·가로등 자리: 실제로 낮은 블록이 있는지 봄
                if (!m.free(x, z) && (m.roadDistance(x, z) > 6 || solidLow(existing, x, z))) {
                    return null;
                }
            }
        }
        return new int[]{x0, z0, x1, z1};
    }

    /** (x, z) 에 이미 놓인 건물이 땅 위 0~2 칸에 블록을 두는지 */
    private static boolean solidLow(List<Placement> existing, int x, int z) {
        boolean[] hit = {false};
        for (Placement p : existing) {
            if (!CityBuildings.isGround(p) && p.covers(x + 0.5, z + 0.5)) {
                p.column(x, z, 0, Integer.MIN_VALUE, (y, b) -> hit[0] |= y >= 0 && y <= 2 && !b.isAir());
            }
        }
        return hit[0];
    }

    private static int[] place(BuildMask m, int[] r, List<Placement> out) {
        int x0 = r[0], z0 = r[1], x1 = r[2], z1 = r[3];
        int fx1 = x1;
        out.add(Placement.rect("화물 도로", "plaza", x0, z0, fx1, z1, "east", (w, d) -> {
            Voxels v = new Voxels(w, d, -1, 0);
            v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("gray_concrete", 0x36393D));
            v.fill(0, -1, 0, 0, -1, d - 1, SMOOTH_STONE);
            for (int i = 1; i < w; i += 4) {
                v.fill(i, -1, d / 2, Math.min(w - 1, i + 1), -1, d / 2, YELLOW_CONCRETE);
            }
            return v;
        }));
        m.claim(x0, z0 - 1, x1, z1 + 1);
        return new int[]{x0, z0, x1, z1};
    }

    /** 도로 남쪽 줄 z0 에서, 동쪽 끝 xEast 부터 서쪽으로 옮겨 가며 sx × sz 가 들어가는 첫 자리 (도로 서쪽 끝 xWest 까지) */
    private static int[] alongRoad(BuildMask m, int xEast, int z0, int sx, int sz, int xWest) {
        for (int x1 = xEast; x1 - sx + 1 >= xWest; x1--) {
            if (m.rectFree(x1 - sx + 1, z0, x1, z0 + sz - 1)) {
                return new int[]{x1 - sx + 1, z0, x1, z0 + sz - 1};
            }
        }
        return null;
    }

    private static Voxels hotel(int w, int d, Random r) {
        Tower.Spec s = new Tower.Spec(w, d, 12);
        s.shape = (i, j, k) -> i >= 2 && i < w - 2 && j >= 2 && j < d - 4;
        s.glass = Block.of("cyan_stained_glass", 0x4C7F99);
        s.mullion = WHITE_CONCRETE;
        s.spandrel = WHITE_CONCRETE;
        s.lobbyH = Floors.HALL;
        s.typicalH = Floors.HOME;
        s.use = k -> k == 0 ? Tower.Use.LOBBY : Tower.Use.HOTEL;
        s.elevators = 2;
        s.restrooms = 1;
        s.name = "준서 에어포트 호텔";
        Tower tw = Tower.build(s, r);
        tw.v.fill(0, -1, 0, w - 1, -1, d - 1, LIGHT_GRAY_CONCRETE);
        // 정문 차양 (차 대는 곳)
        tw.v.fill(w / 2 - 4, Floors.HALL - 2, d - 4, w / 2 + 4, Floors.HALL - 2, d - 1, SMOOTH_STONE_SLAB);
        tw.v.fill(w / 2 - 4, 0, d - 1, w / 2 - 4, Floors.HALL - 3, d - 1, StreetPlan.POST);
        tw.v.fill(w / 2 + 4, 0, d - 1, w / 2 + 4, Floors.HALL - 3, d - 1, StreetPlan.POST);
        return tw.v;
    }

    /** 공항 건물은 비스듬히 놓여서 바깥 상자 대신 실제 땅 모양(둘레 1칸 포함)만 막음 */
    private static void claimFootprints(BuildMask m, List<Placement> existing) {
        for (Placement p : existing) {
            if (CityBuildings.isGround(p)) {
                continue;
            }
            double[] b = p.boundsRef();
            if (b[2] < m.x0 || b[0] > m.x0 + m.w || b[3] < m.z0 || b[1] > m.z0 + m.h) {
                continue;
            }
            for (int z = (int) Math.floor(b[1]); z <= (int) Math.ceil(b[3]); z++) {
                for (int x = (int) Math.floor(b[0]); x <= (int) Math.ceil(b[2]); x++) {
                    if (p.covers(x + 0.5, z + 0.5)) {
                        m.claim(x - 1, z - 1, x + 1, z + 1);
                    }
                }
            }
        }
    }

    /**
     * (ax, az) 둘레 radius 안에서 sx × sz 직사각형이 통째로 지을 수 있고 한 변이 길에 바로 닿는(도로 거리가 가장 짧은) 곳.
     * 같으면 (ax, az) 에 가까운 곳. {x0, z0, x1, z1}
     */
    static int[] besideRoad(BuildMask m, int ax, int az, int sx, int sz, int radius) {
        int[] best = null;
        long bestScore = Long.MAX_VALUE;
        for (int dz = -radius; dz <= radius; dz += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) {
                int x0 = ax + dx - sx / 2, z0 = az + dz - sz / 2, x1 = x0 + sx - 1, z1 = z0 + sz - 1;
                int edge = Math.min(Math.min(m.roadDistance((x0 + x1) / 2, z0), m.roadDistance((x0 + x1) / 2, z1)),
                        Math.min(m.roadDistance(x0, (z0 + z1) / 2), m.roadDistance(x1, (z0 + z1) / 2)));
                long score = edge * 100000L + (long) dx * dx + (long) dz * dz;
                if (score < bestScore && m.rectFree(x0, z0, x1, z1)) {
                    best = new int[]{x0, z0, x1, z1};
                    bestScore = score;
                }
            }
        }
        return best;
    }

    /** (ax, az) 에서 가까운 차례로 sx × sz 직사각형이 통째로 지을 수 있는 땅인 곳 {x0, z0, x1, z1} */
    static int[] nearestRect(BuildMask m, int ax, int az, int sx, int sz, int radius) {
        for (int rr = 0; rr <= radius; rr++) {
            for (int dz = -rr; dz <= rr; dz++) {
                for (int dx = -rr; dx <= rr; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rr) {
                        continue;
                    }
                    int x0 = ax + dx - sx / 2, z0 = az + dz - sz / 2;
                    if (m.rectFree(x0, z0, x0 + sx - 1, z0 + sz - 1)) {
                        return new int[]{x0, z0, x0 + sx - 1, z0 + sz - 1};
                    }
                }
            }
        }
        return null;
    }

    private AirportExtras() {
    }
}
