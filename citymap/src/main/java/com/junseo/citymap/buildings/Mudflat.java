package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 구인천 남쪽 해안 갯벌 체험장 (조개잡이): 항만 매립지 서쪽, 큰길과 바다 사이 해안.
 * <ul>
 *   <li>갯벌: 바다에서 FLAT 칸 안쪽까지 땅을 바닷물 높이로 낮추고 진흙(펄)·회색 점토·모래 섞음, 바다 쪽 웅덩이와 구불구불한
 *       갯골(물길), 체험 구역 경계 대나무 말뚝</li>
 *   <li>호안: 갯벌과 윗땅 사이 낮은 석축과 난간, 체험장 입구·양쪽 끝에 내려가는 계단</li>
 *   <li>윗땅: 길에서 들어오는 입구 아치와 길, 안내소 겸 호미·장화 대여소(카운터·선반), 그늘 쉼터, 발 씻는 곳,
 *       물때·밀물 경고 표지판, 체험객 주차장</li>
 * </ul>
 * 물 칸 위에는 아무것도 놓지 않습니다 (갯벌은 모두 땅 칸을 판 것).
 */
final class Mudflat {
    /** 바다에서 이 거리(칸)까지 갯벌 */
    static final int FLAT = 28;

    static final Block MUD = Block.of("mud", 0x3C3837);
    static final Block CLAY = Block.of("clay", 0xA0A6B3);
    static final Block PACKED = Block.of("packed_mud", 0x8E6B50);
    static final Block SAND = Block.of("sand", 0xDBD3A0);

    static List<Placement> plan(CityTerrain t, PortPlan.Site s, List<Placement> existing, long seed) {
        int mx1 = s.rx0 - 2, mx0 = mx1 - 150;
        int z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (int x = mx0; x <= mx1; x++) {
            int e = PortPlan.Site.roadEdge(t, x, s.qz);
            z0 = Math.min(z0, e);
            int z = e;
            while (z < e + 120 && !t.column(x, z).isWater()) {
                z++;
            }
            z1 = Math.max(z1, z);
        }
        if (z1 - z0 < 30) {
            return List.of();
        }
        Area a = new Area(t, mx0, z0, mx1, z1);
        int bx0 = mx0, bz0 = z0, bz1 = z1;
        // 입구: 땅이 가장 깊은 곳 근처
        int ax = mx0 + 95, ae = PortPlan.Site.roadEdge(t, ax, s.qz);
        int le = Integer.MIN_VALUE;
        for (int x = ax - 37; x <= ax - 16; x++) {
            le = Math.max(le, PortPlan.Site.roadEdge(t, x, s.qz));
        }
        PortPlan.R lot = new PortPlan.R(ax - 37, le, ax - 16, le + 13);
        PortPlan.R house = new PortPlan.R(ax - 14, ae + 2, ax - 4, ae + 8);
        List<Placement> out = new ArrayList<>();
        Placement flat = Placement.rect("구인천 갯벌 체험장", "mudflat", bx0, bz0, mx1, bz1, "south",
                (w, d) -> List.of(new double[]{house.x0() - bx0, house.z0() - bz0}, new double[]{house.x1() + 1 - bx0, house.z0() - bz0},
                        new double[]{house.x1() + 1 - bx0, house.z1() + 1 - bz0}, new double[]{house.x0() - bx0, house.z1() + 1 - bz0}),
                (w, d) -> build(a, ax, ae, lot, house, new Random(seed)));
        if (PortPlan.clear(flat, existing, a::land)) {
            out.add(flat);
            boolean lotOk = true;
            for (int z = lot.z0(); z <= lot.z1() && lotOk; z++) {
                for (int x = lot.x0(); x <= lot.x1() && lotOk; x++) {
                    lotOk = a.land(x, z) && !a.flat(x, z);
                }
            }
            if (lotOk) {
                out.add(Placement.rect("갯벌 체험장 주차장", "parking", lot.x0(), lot.z0(), lot.x1(), lot.z1(), "north",
                        (w, d) -> ParkingLot.build(w, d, null)));
            }
        }
        return out;
    }

    /** 땅 지도: 칸마다 땅인지(길·물·다른 구역이 아님), 바다까지 거리 */
    static final class Area {
        final CityTerrain t;
        final int x0, z0, w, d;
        final boolean[] land;
        final short[] dist;

        Area(CityTerrain t, int x0, int z0, int x1, int z1) {
            this.t = t;
            this.x0 = x0;
            this.z0 = z0;
            this.w = x1 - x0 + 1;
            this.d = z1 - z0 + 1;
            land = new boolean[w * d];
            dist = new short[w * d];
            ArrayDeque<Integer> q = new ArrayDeque<>();
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    int k = j * w + i;
                    Column c = t.column(x0 + i, z0 + j);
                    land[k] = !c.isWater() && !c.isRoad() && !c.deck && c.district == null && c.mountainHeight == 0;
                    dist[k] = c.isWater() ? 0 : Short.MAX_VALUE;
                    if (c.isWater()) {
                        q.add(k);
                    }
                }
            }
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!q.isEmpty()) {
                int k = q.poll();
                int i = k % w, j = k / w;
                if (dist[k] > 60) {
                    continue;
                }
                for (int[] dd : dirs) {
                    int ni = i + dd[0], nj = j + dd[1];
                    if (ni < 0 || nj < 0 || ni >= w || nj >= d) {
                        continue;
                    }
                    int nk = nj * w + ni;
                    if (dist[nk] > dist[k] + 1) {
                        dist[nk] = (short) (dist[k] + 1);
                        q.add(nk);
                    }
                }
            }
        }

        boolean in(int x, int z) {
            return x >= x0 && z >= z0 && x < x0 + w && z < z0 + d;
        }

        boolean land(int x, int z) {
            return in(x, z) && land[(z - z0) * w + (x - x0)];
        }

        int dist(int x, int z) {
            return in(x, z) ? dist[(z - z0) * w + (x - x0)] : Short.MAX_VALUE;
        }

        boolean water(int x, int z) {
            return t.column(x, z).isWater();
        }

        boolean flat(int x, int z) {
            return land(x, z) && dist(x, z) <= FLAT;
        }
    }

    static Voxels build(Area a, int ax, int ae, PortPlan.R lot, PortPlan.R house, Random r) {
        PortPlan.Canvas c = new PortPlan.Canvas(a.x0, a.z0, a.x0 + a.w - 1, a.z0 + a.d - 1, -5, 8);
        // 갯골: 바다에서 안쪽으로 구불구불 (가운데 x, 흔들림)
        int[] channels = {a.x0 + 22, a.x0 + 63, ax + 30};
        for (int z = a.z0; z < a.z0 + a.d; z++) {
            for (int x = a.x0; x < a.x0 + a.w; x++) {
                if (!a.land(x, z)) {
                    continue;
                }
                int dist = a.dist(x, z);
                if (dist > FLAT) {
                    continue;
                }
                c.set(x, -1, z, AIR);
                c.set(x, 0, z, AIR);
                double n = noise(x, z);
                Block b;
                if (dist >= FLAT - 2) {
                    b = SAND;
                } else if (n > 0.72) {
                    b = CLAY;
                } else if (n < 0.12 && dist > 8) {
                    b = PACKED;
                } else {
                    b = MUD;
                }
                // 바다 쪽 웅덩이
                if (dist <= 9 && noise(x * 2 + 5, z * 2 - 3) < 0.22) {
                    c.set(x, -3, z, MUD);
                    b = WATER;
                }
                for (int k = 0; k < channels.length; k++) {
                    double cx = channels[k] + 4 * Math.sin(z * 0.17 + k * 2.1) + 2 * Math.sin(z * 0.41 + k);
                    double half = 1.4 - Math.max(0, dist - 8) * 0.06;
                    if (dist <= FLAT - 4 && Math.abs(x + 0.5 - cx) < half) {
                        c.set(x, -4, z, MUD);
                        c.set(x, -3, z, WATER);
                        b = WATER;
                    }
                }
                c.set(x, -2, z, b);
            }
        }
        // 호안: 갯벌과 닿은 윗땅 칸 (갓돌, 석축 면, 난간)
        int stairX0 = ax - 2, stairX1 = ax + 2;
        int[] sideStairs = {a.x0 + 12, a.x0 + a.w - 14};
        for (int z = a.z0; z < a.z0 + a.d; z++) {
            for (int x = a.x0; x < a.x0 + a.w; x++) {
                if (!a.land(x, z) || a.flat(x, z)) {
                    continue;
                }
                boolean wall = a.flat(x + 1, z) || a.flat(x - 1, z) || a.flat(x, z + 1) || a.flat(x, z - 1);
                if (!wall) {
                    continue;
                }
                c.set(x, -1, z, Block.of("smooth_stone", 0x9E9E9E));
                c.set(x, -2, z, Block.of("stone_bricks", 0x7A7979));
                c.set(x, -3, z, Block.of("stone_bricks", 0x7A7979));
                boolean opening = x >= stairX0 && x <= stairX1;
                for (int sx : sideStairs) {
                    opening |= Math.abs(x - sx) <= 1;
                }
                if (!opening) {
                    c.set(x, 0, z, Block.of("dark_oak_fence", 0x432B14));
                } else {
                    // 갯벌 쪽 칸에 내려가는 계단 (오르는 쪽이 윗땅)
                    int[][] nb = {{0, 1, 0}, {1, 0, 1}, {-1, 0, 3}, {0, -1, 2}};
                    String[] facing = {"north", "west", "east", "south"};
                    for (int k = 0; k < 4; k++) {
                        int fx = x + nb[k][0], fzz = z + nb[k][1];
                        if (a.flat(fx, fzz)) {
                            c.set(fx, -1, fzz, Blocks.stairs("stone_brick", facing[k], 0x7A7979));
                            c.set(fx, -2, fzz, Block.of("stone_bricks", 0x7A7979));
                        }
                    }
                }
            }
        }
        // 윗땅 포장: 입구 길 (길 → 계단), 안내소 앞 마당
        for (int z = a.z0; z < a.z0 + a.d; z++) {
            for (int x = ax - 2; x <= ax + 2; x++) {
                if (a.land(x, z) && !a.flat(x, z) && c.get(x, -1, z) == null) {
                    c.set(x, -1, z, x == ax - 2 || x == ax + 2 ? Block.of("smooth_stone", 0x9E9E9E) : Block.of("polished_andesite", 0x848685));
                }
            }
        }
        for (int z = house.z0() - 1; z <= house.z1() + 2; z++) {
            for (int x = house.x1() + 1; x <= ax + 11; x++) {
                if (a.land(x, z) && !a.flat(x, z) && c.get(x, -1, z) == null) {
                    c.set(x, -1, z, Block.of("light_gray_concrete", 0x7D7D73));
                }
            }
        }
        // 입구 아치 (길 쪽)
        int gz = ae + 1;
        for (int x : new int[]{ax - 3, ax + 3}) {
            c.fill(x, 0, gz, x, 4, gz, Block.of("spruce_log[axis=y]", 0x3A2A1A));
        }
        c.fill(ax - 3, 5, gz, ax + 3, 5, gz, Block.of("spruce_planks", 0x725430));
        c.set(ax - 1, 4, gz - 1, Blocks.wallSign("spruce", "north", "white", false, "", "구인천", "갯벌 체험장"));
        c.set(ax + 1, 4, gz - 1, Blocks.wallSign("spruce", "north", "white", false, "", "조개잡이", "체험"));
        // 안내소·대여소
        rentalHouse(c, house, r);
        // 그늘 쉼터 (정자 지붕, 마주 보는 의자)
        int sx = ax + 4, sz = ae + 3;
        for (int[] p : new int[][]{{0, 0}, {5, 0}, {0, 5}, {5, 5}}) {
            c.fill(sx + p[0], 0, sz + p[1], sx + p[0], 2, sz + p[1], Block.of("spruce_log[axis=y]", 0x3A2A1A));
        }
        c.fill(sx - 1, 3, sz - 1, sx + 6, 3, sz + 6, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        c.fill(sx, 3, sz, sx + 5, 3, sz + 5, Block.of("dark_oak_planks", 0x432B14));
        c.fill(sx + 1, 4, sz + 1, sx + 4, 4, sz + 4, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        for (int x = sx + 1; x <= sx + 4; x++) {
            c.set(x, 0, sz + 1, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430));
            c.set(x, 0, sz + 4, Block.of("spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", 0x725430));
        }
        c.set(sx + 2, 2, sz + 2, LANTERN_HANGING);
        // 발 씻는 곳 (계단 옆): 낮은 벽, 수도꼭지, 물 받는 통
        int wz = seawallZ(a, ax + 6) - 3;
        for (int x = ax + 4; x <= ax + 8; x++) {
            c.set(x, 0, wz - 1, Block.of("smooth_stone", 0x9E9E9E));
            c.set(x, 1, wz - 1, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            if ((x - ax) % 2 == 0) {
                c.set(x, 0, wz, Block.of("water_cauldron[level=3]", 0x3F76E4));
                c.set(x, 1, wz, Block.of("tripwire_hook[attached=false,facing=south,powered=false]", 0x8F8F8F));
            }
        }
        c.set(ax + 9, 1, wz - 1, Blocks.wallSign("birch", "east", "black", false, "", "발 씻는 곳"));
        c.set(ax + 9, 0, wz - 1, Block.of("smooth_stone", 0x9E9E9E));
        // 체험 안내판과 경고 표지판 (계단 위)
        int tz = seawallZ(a, ax) - 2;
        c.fill(ax - 6, 0, tz, ax - 6, 1, tz, Block.of("spruce_fence", 0x725430));
        c.fill(ax - 3, 0, tz, ax - 3, 1, tz, Block.of("spruce_fence", 0x725430));
        c.fill(ax - 6, 2, tz, ax - 3, 3, tz, Block.of("spruce_planks", 0x725430));
        c.set(ax - 5, 3, tz - 1, Blocks.wallSign("spruce", "north", "white", false, "갯벌 체험 안내", "체험 시간은", "썰물 전후", "2시간"));
        c.set(ax - 4, 3, tz - 1, Blocks.wallSign("spruce", "north", "white", false, "물때표 확인", "장화·호미는", "안내소 대여"));
        c.set(ax - 5, 2, tz - 1, Blocks.wallSign("spruce", "north", "yellow", false, "밀물 때", "즉시 나오세요"));
        c.set(ax - 4, 2, tz - 1, Blocks.wallSign("spruce", "north", "yellow", false, "갯골 접근", "금지"));
        for (int sxx : new int[]{ax + 3, a.x0 + 12, a.x0 + a.w - 14}) {
            int z = seawallZ(a, sxx) - 1;
            if (z > a.z0 && a.land(sxx, z) && c.get(sxx, 0, z) == null) {
                c.set(sxx, 0, z, Block.of("oak_sign[rotation=8,waterlogged=false]", 0xA2834F)
                        .withText("red", false, "경고", "밀물 시 고립", "위험", "물때 확인"));
            }
        }
        // 체험 구역 경계: 대나무 말뚝 줄 (갯벌 위, 계단 양쪽 40칸)
        for (int bx : new int[]{ax - 40, ax + 40}) {
            for (int z = a.z0; z < a.z0 + a.d; z += 3) {
                if (a.flat(bx, z) && a.dist(bx, z) >= 2 && !WATER.equals(c.get(bx, -2, z))) {
                    c.fill(bx, -1, z, bx, 1, z, Block.of("bamboo_fence", 0xC2AF52));
                }
            }
        }
        // 보행등 (윗땅 길가)
        for (int z = ae + 6; z < seawallZ(a, ax) - 3; z += 8) {
            c.fill(ax + 3, 0, z, ax + 3, 3, z, StreetPlan.POST);
            c.set(ax + 3, 4, z, LANTERN);
        }
        // 주차장 자리는 비워 둠 (따로 놓음)
        for (int z = lot.z0(); z <= lot.z1(); z++) {
            for (int x = lot.x0(); x <= lot.x1(); x++) {
                for (int y = -1; y <= 4; y++) {
                    c.set(x, y, z, null);
                }
            }
        }
        c.v.connect();
        return c.v;
    }

    /** x 줄에서 갯벌이 시작되기 바로 전 (윗땅 마지막 칸) z */
    static int seawallZ(Area a, int x) {
        for (int z = a.z0; z < a.z0 + a.d; z++) {
            if (a.flat(x, z)) {
                return z - 1;
            }
        }
        return a.z0 + a.d / 2;
    }

    /**
     * 갯벌 체험 안내소 겸 호미·장화 대여소 (조립식 패널 1층): 문은 동쪽(입구 길 쪽), 동쪽 창,
     * 안에 대여 카운터와 계산 자리, 장화(검은·초록 초)와 바구니(퇴비통 모양)를 얹은 선반, 벽에 호미 걸이.
     */
    static void rentalHouse(PortPlan.Canvas c, PortPlan.R h, Random r) {
        Block panel = Block.of("white_concrete", 0xCFD5D6), blue = Block.of("blue_concrete", 0x2C2E8F);
        int x0 = h.x0(), z0 = h.z0(), x1 = h.x1(), z1 = h.z1();
        c.fill(x0, -1, z0, x1, -1, z1, Block.of("polished_andesite", 0x848685));
        c.v.walls(x0 - c.x0, 0, z0 - c.z0, x1 - c.x0, 2, z1 - c.z0, panel);
        c.v.walls(x0 - c.x0, 3, z0 - c.z0, x1 - c.x0, 3, z1 - c.z0, blue);
        c.fill(x0, 3, z0, x1, 3, z1, blue);
        c.fill(x0 + 1, 3, z0 + 1, x1 - 1, 3, z1 - 1, Block.of("light_gray_concrete", 0x7D7D73));
        int mz = (z0 + z1) / 2;
        c.set(x1, 0, mz, Blocks.door("spruce", "east", false));
        c.set(x1, 1, mz, Blocks.door("spruce", "east", true));
        c.fill(x1, 1, z0 + 1, x1, 1, mz - 1, Harbor.GLASS);
        c.fill(x1, 1, mz + 1, x1, 1, z1 - 1, Harbor.GLASS);
        for (int x = x0 + 2; x < x1 - 1; x += 3) {
            c.set(x, 1, z1, Harbor.GLASS);
        }
        // 카운터 (문 안쪽 서쪽, 가운데 틈)
        int cx = x1 - 3;
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            if (z != z0 + 1) {
                c.set(cx, 0, z, Furniture.COUNTER);
            }
        }
        c.set(cx, 1, mz + 1, Block.of("heavy_weighted_pressure_plate[power=0]", 0xDCDCDC));
        c.set(cx - 1, 0, mz, Block.of("dark_oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", 0x432B14));
        // 선반: 서쪽 벽 따라 장화(초), 북쪽 벽 바구니, 벽 호미 걸이
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            c.set(x0 + 1, 0, z, Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
            c.set(x0 + 1, 1, z, Block.of((z % 2 == 0 ? "black" : "green") + "_candle[candles=2,lit=false,waterlogged=false]", z % 2 == 0 ? 0x252525 : 0x4C6B2C));
            c.set(x0 + 1, 2, z, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        }
        for (int x = x0 + 2; x <= cx - 2; x++) {
            c.set(x, 0, z0 + 1, Block.of("composter[level=0]", 0x7A5A33));
            c.set(x, 1, z1 - 1, Block.of("tripwire_hook[attached=false,facing=north,powered=false]", 0x8F8F8F));
        }
        c.set((x0 + cx) / 2, 2, mz, Interior.LIGHT);
        c.set(x1 - 1, 2, mz, Interior.LIGHT);
        c.set(x1 + 1, 2, mz - 1, Blocks.wallSign("birch", "east", "black", false, "", "갯벌 체험", "안내소"));
        c.set(x1 + 1, 2, mz + 1, Blocks.wallSign("birch", "east", "black", false, "", "호미·장화", "대여"));
    }

    /** 0..1 사이 값 (칸 덩어리마다 부드럽게) */
    static double noise(int x, int z) {
        double v = Math.sin(x * 0.21 + Math.sin(z * 0.13) * 2.0) * Math.cos(z * 0.19 - x * 0.07) + Math.sin((x + z) * 0.09) * 0.5;
        return (v + 1.5) / 3.0;
    }

    private Mudflat() {
    }
}
