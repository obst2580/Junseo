package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 한강공원: 한강·샛강 둑과 강변 고속도로(강변북로·올림픽대로) 사이, 어느 구역에도 속하지 않은 잔디 띠.
 * <ul>
 *   <li>둑 바로 옆 산책로 (탄성 포장, 2칸), 그 안쪽 자전거길 (회색 4칸, 가운데 노란 점선)</li>
 *   <li>나머지는 잔디밭과 나무 (길에서 떨어진 곳), 산책로 가 벤치와 보행등</li>
 *   <li>너른 곳에 편의점 (안: 계산대·진열대·냉장고·라면 조리기, 앞 탁자), 공중화장실</li>
 *   <li>도로에 닿는 너른 곳에 공영주차장 (주차 칸마다 차 자리, 정면이 도로로 바로 나감)</li>
 * </ul>
 * 강변 땅은 평지(둑과 같은 높이)라 계단이 필요 없습니다. 긴 띠는 64칸 격자로 잘라 토막마다 Placement 하나 ("park").
 * 길·물·다리 칸과 이미 있는 건물 자리는 건드리지 않습니다.
 */
final class HangangPlan {
    static final int TILE = 64, MAX_DEPTH = 32, X_MIN = -830, X_MAX = 1540;
    static final Block WALK = Block.of("packed_mud", 0x8E6B50);
    static final Block BIKE = Block.of("gray_concrete", 0x36393D);
    static final Block BIKE_LINE = Block.of("yellow_concrete", 0xF0AF15);

    /** 강변 땅 지도: 물(강)에서 잰 거리 dw (0 = 땅 아님) */
    private static final class Map2 {
        final int x0, z0, w, h;
        final short[] dw;
        final boolean[] road;
        final int[] seg;

        Map2(int x0, int z0, int w, int h) {
            this.x0 = x0;
            this.z0 = z0;
            this.w = w;
            this.h = h;
            dw = new short[w * h];
            road = new boolean[w * h];
            seg = new int[w * h];
        }

        int k(int x, int z) {
            int i = x - x0, j = z - z0;
            return i < 0 || j < 0 || i >= w || j >= h ? -1 : j * w + i;
        }

        int dw(int x, int z) {
            int k = k(x, z);
            return k < 0 ? 0 : dw[k];
        }
    }

    static List<Placement> plan(CityTerrain t, List<Placement> existing) {
        HillsidePlan.Blocked blocked = new HillsidePlan.Blocked(t, existing);
        double[] min = t.layout().borderMin(), max = t.layout().borderMax();
        int x0 = Math.max(X_MIN, (int) min[0]), x1 = Math.min(X_MAX, (int) max[0] - 1);
        int z0 = (int) min[1], z1 = (int) max[1] - 1;
        Map2 m = new Map2(x0, z0, x1 - x0 + 1, z1 - z0 + 1);
        // 강 칸에서 땅 쪽으로 거리 (구역 밖 잔디만 지나감)
        ArrayDeque<Integer> q = new ArrayDeque<>();
        boolean[] free = new boolean[m.w * m.h];
        boolean[] river = new boolean[m.w * m.h];
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                Column c = t.column(x, z);
                int k = m.k(x, z);
                river[k] = c.isWater() && c.biome == Column.Biome.RIVER;
                m.road[k] = (c.isRoad() || c.deck || c.tunnel) && !c.isWater();
                free[k] = !c.isWater() && !c.isRoad() && !c.deck && !c.tunnel && c.mountainHeight == 0 && c.district == null
                        && (c.surface == Surface.GRASS || c.surface == Surface.EMBANKMENT) && !blocked.at(x, z);
            }
        }
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                int k = m.k(x, z);
                if (!free[k]) {
                    continue;
                }
                for (int[] n : NB) {
                    int nk = m.k(x + n[0], z + n[1]);
                    if (nk >= 0 && river[nk]) {
                        m.dw[k] = 1;
                        q.add(k);
                        break;
                    }
                }
            }
        }
        while (!q.isEmpty()) {
            int k = q.poll();
            int x = m.x0 + k % m.w, z = m.z0 + k / m.w;
            if (m.dw[k] >= MAX_DEPTH) {
                continue;
            }
            for (int[] n : NB) {
                int nk = m.k(x + n[0], z + n[1]);
                if (nk >= 0 && free[nk] && m.dw[nk] == 0) {
                    m.dw[nk] = (short) (m.dw[k] + 1);
                    q.add(nk);
                }
            }
        }
        // 64칸 격자 토막마다 Placement
        List<Placement> out = new ArrayList<>();
        Random rnd = Plans.random(t, "hangang");
        int n = 0;
        for (int tz = Math.floorDiv(z0, TILE); tz <= Math.floorDiv(z1, TILE); tz++) {
            for (int tx = Math.floorDiv(x0, TILE); tx <= Math.floorDiv(x1, TILE); tx++) {
                int bx0 = Integer.MAX_VALUE, bz0 = Integer.MAX_VALUE, bx1 = Integer.MIN_VALUE, bz1 = Integer.MIN_VALUE, cnt = 0, deep = 0;
                for (int z = tz * TILE; z < (tz + 1) * TILE; z++) {
                    for (int x = tx * TILE; x < (tx + 1) * TILE; x++) {
                        int dwv = m.dw(x, z);
                        if (dwv > 0) {
                            bx0 = Math.min(bx0, x);
                            bz0 = Math.min(bz0, z);
                            bx1 = Math.max(bx1, x);
                            bz1 = Math.max(bz1, z);
                            cnt++;
                            deep = Math.max(deep, dwv);
                        }
                    }
                }
                if (cnt < 200 || deep < 9) {
                    continue;
                }
                Segment s = new Segment(t, m, ++n, tx * TILE, tz * TILE, bx0, bz0, bx1, bz1, new Random(rnd.nextLong()));
                out.addAll(s.placements());
            }
        }
        return out;
    }

    private static final int[][] NB = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** 공원 한 토막 */
    private static final class Segment {
        final CityTerrain t;
        final Map2 m;
        final int no, tx0, tz0, x0, z0, x1, z1, w, d;
        final Random r;
        /** 시설 자리 (월드 직사각형): 바닥은 비움 */
        final List<int[]> taken = new ArrayList<>();
        final List<Placement> extra = new ArrayList<>();

        Segment(CityTerrain t, Map2 m, int no, int tx0, int tz0, int x0, int z0, int x1, int z1, Random r) {
            this.t = t;
            this.m = m;
            this.no = no;
            this.tx0 = tx0;
            this.tz0 = tz0;
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.w = x1 - x0 + 1;
            this.d = z1 - z0 + 1;
            this.r = r;
            facilities();
        }

        /** 이 토막의 칸인지 (격자 안 + 강변 땅) */
        boolean mine(int x, int z) {
            return x >= tx0 && x < tx0 + TILE && z >= tz0 && z < tz0 + TILE && m.dw(x, z) > 0;
        }

        boolean inTaken(int x, int z) {
            for (int[] b : taken) {
                if (x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]) {
                    return true;
                }
            }
            return false;
        }

        /** 직사각형이 모두 이 토막 땅이고 물에서 minDw 이상 떨어져 있는지 (둘레 1칸도 땅) */
        boolean fits(int ax, int az, int bx, int bz, int minDw) {
            for (int z = az - 1; z <= bz + 1; z++) {
                for (int x = ax - 1; x <= bx + 1; x++) {
                    boolean edge = z < az || z > bz || x < ax || x > bx;
                    if (!edge && (!mine(x, z) || m.dw(x, z) < minDw)) {
                        return false;
                    }
                    if (edge && m.dw(x, z) == 0 && !m.road[Math.max(0, m.k(x, z))]) {
                        return false;
                    }
                    if (inTaken(x, z)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** 시설 하나 놓을 자리 (정면은 강 쪽). 못 찾으면 null */
        int[] find(int sw, int sd, int minDw) {
            for (int z = z0; z + sd - 1 <= z1; z += 2) {
                for (int x = x0; x + sw - 1 <= x1; x += 2) {
                    for (int rot = 0; rot < 2; rot++) {
                        int ex = rot == 0 ? sw : sd, ez = rot == 0 ? sd : sw;
                        if (fits(x, z, x + ex - 1, z + ez - 1, minDw)) {
                            return new int[]{x, z, x + ex - 1, z + ez - 1, rot};
                        }
                    }
                }
            }
            return null;
        }

        /** 직사각형 정면: 물에 가장 가까운 변 */
        String riverFront(int[] b) {
            double s = 0, n = 0, e = 0, wv = 0;
            for (int x = b[0]; x <= b[2]; x++) {
                s += m.dw(x, b[3] + 2);
                n += m.dw(x, b[1] - 2);
            }
            for (int z = b[1]; z <= b[3]; z++) {
                e += m.dw(b[2] + 2, z);
                wv += m.dw(b[0] - 2, z);
            }
            int bw = b[2] - b[0] + 1, bd = b[3] - b[1] + 1;
            double[] avg = {s / bw, n / bw, e / bd, wv / bd};
            String[] dir = {"south", "north", "east", "west"};
            int best = 0;
            for (int k = 1; k < 4; k++) {
                if (avg[k] > 0 && (avg[best] == 0 || avg[k] < avg[best])) {
                    best = k;
                }
            }
            return dir[best];
        }

        private void facilities() {
            String name = "한강공원 " + no;
            // 공영주차장: 정면이 도로에 닿는 자리
            int[] p = parkingSpot();
            if (p != null) {
                taken.add(p);
                int pw = p[4] == 0 || p[4] == 2 ? p[2] - p[0] + 1 : p[3] - p[1] + 1;
                int pd = p[4] == 0 || p[4] == 2 ? p[3] - p[1] + 1 : p[2] - p[0] + 1;
                String front = new String[]{"south", "west", "north", "east"}[p[4]];
                extra.add(Placement.rect(name + " 주차장", "garage", p[0], p[1], p[2], p[3], front, (ww, dd) -> parking(ww, dd)));
            }
            // 편의점, 화장실 (자전거길 안쪽)
            int[] k = find(9, 8, 10);
            if (k != null) {
                taken.add(k);
                String front = riverFront(k);
                long seed = r.nextLong();
                extra.add(Placement.rect(name + " 편의점", "park", k[0], k[1], k[2], k[3], front, (ww, dd) -> store(ww, dd, new Random(seed))));
            }
            if (no % 3 == 0 || k == null) {
                int[] c = find(11, 9, 10);
                if (c != null) {
                    taken.add(c);
                    extra.add(Placement.rect(name + " 화장실", "park", c[0], c[1], c[2], c[3], riverFront(c), (ww, dd) -> GrandPark.restroom(ww, dd)));
                }
            }
        }

        /** 주차장 (너비 24 또는 18, 깊이 11: 뒤 한 줄 주차, 앞 통로): 정면이 도로에 닿는 자리. {x0, z0, x1, z1, 정면 번호} */
        private int[] parkingSpot() {
            for (int pw : new int[]{24, 18}) {
                int[] p = parkingSpot(pw, 11);
                if (p != null) {
                    return p;
                }
            }
            return null;
        }

        private int[] parkingSpot(int pw, int pd) {
            for (int z = z0; z <= z1; z += 2) {
                for (int x = x0; x <= x1; x += 2) {
                    for (int q = 0; q < 4; q++) {
                        int ex = q % 2 == 0 ? pw : pd, ez = q % 2 == 0 ? pd : pw;
                        int ax = x, az = z, bx = x + ex - 1, bz = z + ez - 1;
                        boolean ok = true;
                        for (int zz = az; zz <= bz && ok; zz++) {
                            for (int xx = ax; xx <= bx && ok; xx++) {
                                ok = mine(xx, zz) && m.dw(xx, zz) >= 10 && !inTaken(xx, zz);
                            }
                        }
                        if (!ok) {
                            continue;
                        }
                        // 정면(q: 0 남, 1 서, 2 북, 3 동) 바로 바깥: 절반 넘게 도로이고 나머지는 평평한 공원 땅 (차가 바로 나감)
                        int n = q % 2 == 0 ? ex : ez, roads = 0;
                        boolean flat = true;
                        for (int s = 0; s < n && flat; s++) {
                            int rx = switch (q) {
                                case 0, 2 -> ax + s;
                                case 1 -> ax - 1;
                                default -> bx + 1;
                            };
                            int rz = switch (q) {
                                case 0 -> bz + 1;
                                case 2 -> az - 1;
                                default -> az + s;
                            };
                            int rk = m.k(rx, rz);
                            Column c = t.column(rx, rz);
                            boolean isRoad = rk >= 0 && m.road[rk] && !c.deck && !c.tunnel;
                            roads += isRoad ? 1 : 0;
                            flat = isRoad || (mine(rx, rz) && !inTaken(rx, rz));
                        }
                        boolean road = flat && roads * 2 > n;
                        if (road) {
                            return new int[]{ax, az, bx, bz, q};
                        }
                    }
                }
            }
            return null;
        }

        List<Placement> placements() {
            List<Placement> out = new ArrayList<>();
            out.add(Placement.rect("한강공원 " + no, "park", x0, z0, x1, z1, "south", (ww, dd) -> ground()));
            out.addAll(extra);
            return out;
        }

        /** 길·잔디·나무·벤치·보행등 (월드 좌표 그대로, 정면 남쪽) */
        private Voxels ground() {
            Voxels v = new Voxels(w, d, -1, 12);
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (!mine(x, z) || inTaken(x, z)) {
                        continue;
                    }
                    int dwv = m.dw(x, z);
                    int i = x - x0, j = z - z0;
                    if (dwv <= 1) {
                        continue; // 둑 (그대로)
                    }
                    if (dwv <= 3) {
                        v.set(i, -1, j, WALK);
                    } else if (dwv >= 5 && dwv <= 8) {
                        boolean line = dwv == 7 && Math.floorMod(x + z, 6) < 3;
                        v.set(i, -1, j, line ? BIKE_LINE : BIKE);
                    } else if (dwv == 4 || dwv == 9) {
                        v.set(i, -1, j, Block.of("smooth_stone", 0x9E9E9E)); // 연석
                    }
                }
            }
            // 산책로 가: 벤치 (강을 봄)와 보행등, 잔디밭 나무
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (!mine(x, z) || inTaken(x, z)) {
                        continue;
                    }
                    int dwv = m.dw(x, z), i = x - x0, j = z - z0;
                    if (dwv == 4 && Math.floorMod(x * 7 + z * 13, 19) == 0) {
                        String face = towardRiver(x, z);
                        if (face != null) {
                            v.set(i, 0, j, Block.of("spruce_stairs[facing=" + Furniture.opposite(face) + ",half=bottom,shape=straight,waterlogged=false]", 0x725430));
                        }
                    }
                    if (dwv == 4 && Math.floorMod(x * 5 + z * 3, 23) == 0) {
                        Kit.parkLamp(v, i, 0, j);
                    }
                    if (dwv >= 12 && Math.floorMod(x * 31 + z * 17, 53) == 0 && treeRoom(x, z)) {
                        Kit.tree(v, i, j, -1, r);
                    }
                }
            }
            v.connect();
            return v;
        }

        /** 나무 둘레 4칸에 시설·자전거길이 없는지 */
        private boolean treeRoom(int x, int z) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dx = -4; dx <= 4; dx++) {
                    if (inTaken(x + dx, z + dz) || (m.dw(x + dx, z + dz) > 0 && m.dw(x + dx, z + dz) <= 9) || m.road[Math.max(0, m.k(x + dx, z + dz))]) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** 강 쪽 방향 (dw 가 줄어드는 쪽) */
        private String towardRiver(int x, int z) {
            int here = m.dw(x, z);
            String[] dir = {"east", "west", "south", "north"};
            for (int k = 0; k < 4; k++) {
                int nd = m.dw(x + NB[k][0], z + NB[k][1]);
                if (nd > 0 && nd < here) {
                    return dir[k];
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ 시설

    /** 공영주차장: 주차선·차 자리, 정면 쪽 매표 부스와 표지판 */
    static Voxels parking(int w, int d) {
        Voxels v = ParkingLot.build(w, d, null);
        Voxels out = new Voxels(w, d, -1, 4);
        PrisonPlan.stamp(out, v, 0, 0, "south");
        for (double[] c : v.carSpots()) {
            out.carSpot(c[0], (int) c[1], c[2], (int) c[3], (int) c[4]);
        }
        out.fill(w - 3, 0, d - 4, w - 2, 2, d - 3, Block.of("white_concrete", 0xCFD5D6));
        out.set(w - 3, 1, d - 3, GLASS_PANE);
        out.fill(w - 3, 3, d - 4, w - 2, 3, d - 3, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        for (int y = 0; y <= 2; y++) {
            out.set(1, y, d - 2, Block.of("polished_blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x353038));
        }
        out.set(1, 2, d - 1, Blocks.wallSign("birch", "south", "blue", false, "한강공원", "공영주차장", "P"));
        out.connect();
        return out;
    }

    /**
     * 한강 편의점 (너비 9, 깊이 8): 흰 벽과 통유리 앞면, 간판, 안에 계산대·진열대·음료 냉장고·라면 조리기,
     * 앞 데크에 파라솔 탁자 둘.
     */
    static Voxels store(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 5);
        int jf = d - 3;
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("spruce_planks", 0x725430));
        v.fill(0, -1, 0, w - 1, -1, jf, Block.of("white_concrete", 0xCFD5D6));
        v.walls(0, 0, 0, w - 1, 2, jf, Block.of("white_concrete", 0xCFD5D6));
        v.fill(0, 3, 0, w - 1, 3, jf, Block.of("light_gray_concrete", 0x7D7D73));
        // 통유리 앞면, 문
        for (int i = 1; i < w - 1; i++) {
            v.set(i, 1, jf, GLASS_PANE);
            v.set(i, 2, jf, GLASS_PANE);
            v.set(i, 0, jf, Block.of("light_gray_concrete", 0x7D7D73));
        }
        Interior.door(Frame.of(v), w / 2, 0, jf, "birch", "south");
        // 간판 띠 (초록) + 글씨
        v.fill(0, 3, jf, w - 1, 3, jf, Block.of("green_concrete", 0x495B24));
        v.set(w / 2, 3, jf + 1, Blocks.wallSign("birch", "south", "white", true, "", "한강 편의점"));
        // 안: 뒤 벽 음료 냉장고, 가운데 진열대, 계산대, 라면 조리기
        for (int i = 1; i < w - 1; i++) {
            v.set(i, 0, 1, Furniture.FRIDGE);
            v.set(i, 1, 1, Block.of("light_blue_stained_glass", 0x6699D8));
        }
        for (int j = 3; j <= jf - 2; j++) {
            v.set(2, 0, j, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
            v.set(2, 1, j, j % 2 == 0 ? BARREL : Furniture.BOOKSHELF);
        }
        v.set(w - 2, 0, jf - 1, Furniture.COUNTER);
        v.set(w - 2, 0, jf - 2, Furniture.COUNTER);
        v.set(w - 2, 0, 3, SMOKER);
        v.set(w - 2, 0, 4, Furniture.COUNTER);
        v.set(w / 2, 2, 3, Interior.LIGHT);
        // 앞 데크: 탁자 둘과 의자, 파라솔 (흰 양털 지붕)
        for (int k = 0; k < 2; k++) {
            int ti = k == 0 ? 1 : w - 3;
            v.set(ti, 0, d - 1, Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
            v.set(ti + 1, 0, d - 1, Block.of("spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0x725430));
            v.set(ti, 1, d - 1, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
            v.set(ti, 2, d - 1, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
            v.set(ti, 3, d - 1, Block.of("white_wool", 0xE9ECEC));
        }
        v.connect();
        return v;
    }

    private HangangPlan() {
    }
}
