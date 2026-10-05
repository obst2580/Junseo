package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 북한산 기슭 벌채 구역과 목재 집하장 (나무꾼): 북한산 서쪽 봉우리 서쪽 기슭(산 아래 평지와 낮은 서쪽 비탈),
 * 북부대로(A1) 바로 북쪽. 남쪽 기슭 주택가와는 떨어져 있습니다.
 * <ul>
 *   <li>목재 집하장 (길가): 시멘트 진입로, 계근대와 계근실, 목재 매입 사무소(카운터), 원목 더미(참나무·낙엽송·자작나무),
 *       지붕만 있는 제재 창고(톱·켠 목재), 주차장</li>
 *   <li>벌채 구역: 낮은 나무 울타리와 「벌채 구역」 표지판, 수종별 구획에 줄지어 심은 나무(진짜 원목·잎, 잎은 사라지지 않음),
 *       베어 낸 그루터기와 새로 심은 묘목, 가운데 임도(자갈길)</li>
 * </ul>
 * 거점(lumber)이 집하장 진입로 앞에 있습니다.
 */
final class LumberPlan {

    static List<Placement> plan(CityTerrain t, List<Placement> existing) {
        Layout.Hub hub = Plans.hub(t, "lumber");
        if (hub == null) {
            return List.of();
        }
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        Random rnd = Plans.random(t, "lumber");
        long s0 = rnd.nextLong(), s1 = rnd.nextLong();
        // 집하장: 거점 둘레, 남쪽 끝은 큰길 가장자리
        int yx0 = hx - 32, yx1 = hx + 30, yz0 = hz - 42;
        int yz1 = yz0;
        for (int x = yx0; x <= yx1; x++) {
            yz1 = Math.max(yz1, edge(t, x, hz));
        }
        int fyz1 = yz1;
        // 벌채 구역: 집하장 북쪽
        int px0 = hx - 34, px1 = hx + 94, pz0 = hz - 173, pz1 = hz - 48;
        int top = 0;
        for (int z = pz0; z <= pz1; z++) {
            for (int x = px0; x <= px1; x++) {
                top = Math.max(top, t.column(x, z).mountainHeight);
            }
        }
        int ytop = top + 16;
        List<Placement> out = new ArrayList<>();
        PortPlan.R lot = new PortPlan.R(hx + 10, hz - 12, hx + 29, hz + 3);
        out.add(Placement.rect("목재 집하장", "lumber", yx0, yz0, yx1, fyz1, "south",
                (w, d) -> List.of(new double[]{0, 0}, new double[]{w, 0}, new double[]{w, 30}, new double[]{0, 30}),
                (w, d) -> yard(t, hx, hz, yx0, yz0, yx1, fyz1, lot, new Random(s0))));
        out.add(Placement.rect("목재 집하장 주차장", "parking", lot.x0(), lot.z0(), lot.x1(), lot.z1(), "south",
                (w, d) -> ParkingLot.build(w, d, null)));
        out.add(Placement.rect("북한산 벌채 구역", "lumber", px0, pz0, px1, pz1, "south",
                (w, d) -> plantation(t, hx, px0, pz0, px1, pz1, ytop, new Random(s1))));
        List<Placement> kept = new ArrayList<>();
        for (Placement p : out) {
            if (PortPlan.clear(p, existing, (x, z) -> {
                Column c = t.column(x, z);
                return !c.isRoad() && !c.isWater();
            })) {
                kept.add(p);
            }
        }
        return kept;
    }

    /** x 줄에서 큰길 바로 북쪽 마지막 땅 z (거점 z 부근에서 남쪽으로 찾음) */
    static int edge(CityTerrain t, int x, int hz) {
        int z = hz - 20;
        while (z < hz + 60 && !t.column(x, z + 1).isRoad()) {
            z++;
        }
        return z;
    }

    static boolean land(CityTerrain t, int x, int z) {
        Column c = t.column(x, z);
        return !c.isRoad() && !c.isWater() && !c.deck && !c.tunnel;
    }

    // ------------------------------------------------------------------ 집하장

    static final Block GRAVEL = Block.of("gravel", 0x837F7E);
    static final Block CEMENT = Block.of("light_gray_concrete", 0x7D7D73);

    static Voxels yard(CityTerrain t, int hx, int hz, int x0, int z0, int x1, int z1, PortPlan.R lot, Random r) {
        PortPlan.Canvas c = new PortPlan.Canvas(x0, z0, x1, z1, -2, 12);
        // 바닥: 자갈·흙 마당, 진입로는 시멘트
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                if (!land(t, x, z) || t.column(x, z).mountainHeight > 0 || lot.has(x, z)) {
                    continue;
                }
                boolean road = Math.abs(x - hx) <= 4 || z >= hz - 6 && x >= hx - 16 && x <= hx + 9;
                long h = (Math.floorDiv(x, 2) * 73856093L) ^ (Math.floorDiv(z, 2) * 19349663L);
                c.set(x, -1, z, road ? CEMENT : Math.floorMod(h, 5) == 0 ? COARSE_DIRT : Math.floorMod(h, 7) == 0 ? DIRT : GRAVEL);
            }
        }
        // 계근대 (진입로 위 강판, 노랑·검정 테두리) 와 계근실
        for (int z = hz - 17; z <= hz - 8; z++) {
            for (int x = hx - 2; x <= hx + 2; x++) {
                boolean rim = z == hz - 17 || z == hz - 8 || x == hx - 2 || x == hx + 2;
                c.set(x, -1, z, rim ? ((x + z) % 2 == 0 ? Harbor.YELLOW : Harbor.BLACK) : Block.of("iron_block", 0xB8B8B8));
            }
        }
        Voxels scale = Quarry.scaleHouse();
        PrisonPlan.stamp(c.v, scale, hx + 4 - x0, hz - 16 - z0, "west");
        // 목재 매입 사무소 (서쪽, 문은 동쪽 = 진입로 쪽)
        office(c, hx - 17, hz - 9, hx - 7, hz - 3);
        // 원목 더미: 서쪽 마당 (수종별)
        String[] wood = {"oak", "spruce", "birch", "spruce", "oak", "dark_oak"};
        int k = 0;
        for (int pz = z0 + 3; pz + 5 <= hz - 13; pz += 8) {
            for (int px = x0 + 2; px + 9 <= hx - 6; px += 12) {
                logPile(c, px, pz, 10, 5, wood[k++ % wood.length], r);
            }
        }
        // 제재 창고 (동쪽): 철골 기둥, 지붕, 톱(석재 절단기 모양)과 켠 목재 더미
        shed(c, hx + 8, z0 + 3, hx + 24, z0 + 14, r);
        // 동쪽 마당 원목 더미 하나 더
        logPile(c, hx + 9, z0 + 18, 10, 5, "spruce", r);
        // 안내판 (진입로 입구)
        c.fill(hx - 6, 0, hz + 3, hx - 6, 2, hz + 3, Block.of("spruce_log[axis=y]", 0x3A2A1A));
        c.set(hx - 6, 3, hz + 3, Block.of("spruce_planks", 0x725430));
        c.set(hx - 5, 2, hz + 3, Blocks.wallSign("spruce", "east", "white", false, "북한산", "목재 집하장", "원목 매입"));
        // 진입로 보행등
        for (int z = hz - 20; z > z0 + 2; z -= 10) {
            c.fill(hx - 5, 0, z, hx - 5, 3, z, StreetPlan.POST);
            c.set(hx - 5, 4, z, LANTERN);
        }
        c.v.connect();
        return c.v;
    }

    /** 목재 매입 사무소 (조립식 1층): 문은 동쪽, 매입 카운터와 저울, 직원 책상, 시세 안내 */
    static void office(PortPlan.Canvas c, int x0, int z0, int x1, int z1) {
        Block panel = Block.of("white_concrete", 0xCFD5D6), green = Block.of("green_concrete", 0x495B24);
        c.fill(x0, -1, z0, x1, -1, z1, Block.of("polished_andesite", 0x848685));
        c.v.walls(x0 - c.x0, 0, z0 - c.z0, x1 - c.x0, 2, z1 - c.z0, panel);
        c.fill(x0, 3, z0, x1, 3, z1, green);
        int mz = (z0 + z1) / 2;
        c.set(x1, 0, mz, Blocks.door("spruce", "east", false));
        c.set(x1, 1, mz, Blocks.door("spruce", "east", true));
        c.fill(x1, 1, z0 + 1, x1, 1, mz - 1, Harbor.GLASS);
        for (int x = x0 + 2; x < x1 - 1; x += 3) {
            c.set(x, 1, z0, Harbor.GLASS);
            c.set(x, 1, z1, Harbor.GLASS);
        }
        // 카운터 (문 앞 홀과 직원 자리 사이, 북쪽 끝은 드나드는 틈)
        int cx = x1 - 4;
        c.fill(cx, 0, z0 + 2, cx, 0, z1 - 1, Furniture.COUNTER);
        c.set(cx, 1, mz, Block.of("heavy_weighted_pressure_plate[power=0]", 0xDCDCDC));
        c.set(cx - 1, 0, mz, Block.of("dark_oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", 0x432B14));
        Frame f = Frame.of(c.v);
        Furniture.desk(f, x0 + 2 - c.x0, 0, z1 - 1 - c.z0, "north");
        c.fill(x0 + 1, 0, z0 + 1, x0 + 1, 1, z0 + 2, Furniture.BOOKSHELF);
        c.set(x1 - 1, 1, z1 - 1, Blocks.wallSign("birch", "north", "black", false, "원목 매입가", "참나무", "낙엽송", "자작나무"));
        c.set((x0 + x1) / 2, 2, mz, Interior.LIGHT);
        c.set(x1 + 1, 2, mz - 1, Blocks.wallSign("birch", "east", "black", false, "", "목재 매입", "사무소"));
    }

    /** 원목 더미: 통나무를 x 쪽으로 눕혀 쌓은 무더기 (길이 len, 폭 wid), 아래 받침목 */
    static void logPile(PortPlan.Canvas c, int x0, int z0, int len, int wid, String wood, Random r) {
        Block log = Block.of(wood + "_log[axis=x]", switch (wood) {
            case "birch" -> 0xD8D3C5;
            case "spruce" -> 0x3A2A1A;
            case "dark_oak" -> 0x3C2E1A;
            default -> 0x6D5532;
        });
        for (int x = x0; x < x0 + len; x += 4) {
            c.fill(x, 0, z0 - 1, x, 0, z0 + wid, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        }
        int h = 3 + r.nextInt(2);
        for (int y = 0; y < h; y++) {
            int in = y / 2;
            for (int z = z0 + in; z < z0 + wid - in; z++) {
                c.fill(x0 + r.nextInt(2), y + (y == 0 ? 0 : 0), z, x0 + len - 1 - r.nextInt(2), y, z, log);
            }
        }
    }

    /** 제재 창고: 철골 기둥과 경사 지붕(회색 슬레이트), 가운데 톱대(레일 위 원목, 석재 절단기 톱), 켠 판재 더미 */
    static void shed(PortPlan.Canvas c, int x0, int z0, int x1, int z1, Random r) {
        Block post = Block.of("polished_blackstone_wall", 0x353038);
        for (int x = x0; x <= x1; x += 4) {
            c.fill(x, 0, z0, x, 3, z0, post);
            c.fill(x, 0, z1, x, 3, z1, post);
        }
        c.fill(x0, -1, z0, x1, -1, z1, CEMENT);
        int mid = (z0 + z1) / 2;
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            int y = 4 + Math.min(z - (z0 - 1), z1 + 1 - z) / 2;
            c.fill(x0 - 1, y, z, x1 + 1, y, z, Block.of("light_gray_terracotta", 0x876A61));
        }
        // 톱대: 레일 위 원목, 톱
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            c.set(x, 0, mid, Block.of("rail[shape=east_west,waterlogged=false]", 0x7A6A55));
        }
        c.fill(x0 + 3, 0, mid - 1, x0 + 6, 0, mid - 1, Block.of("oak_log[axis=x]", 0x6D5532));
        c.set((x0 + x1) / 2, 0, mid + 1, Blocks.STONECUTTER);
        c.set((x0 + x1) / 2 + 1, 0, mid + 1, Block.of("smooth_stone", 0x9E9E9E));
        // 켠 판재·각재 더미
        for (int x = x0 + 2; x <= x0 + 6; x++) {
            c.fill(x, 0, z1 - 3, x, 1, z1 - 2, Block.of("stripped_spruce_log[axis=x]", 0x6E5434));
        }
        for (int x = x1 - 6; x <= x1 - 2; x++) {
            c.fill(x, 0, z1 - 3, x, 1 + r.nextInt(2), z1 - 2, Block.of("spruce_planks", 0x725430));
        }
        c.set((x0 + x1) / 2, 4, mid, LANTERN_HANGING);
        c.set(x0 + 1, 3, mid, LANTERN_HANGING);
    }

    // ------------------------------------------------------------------ 벌채 구역

    /**
     * 벌채 구역: 낮은 울타리(임도 입구는 열림)와 표지판, 수종별 구획(서쪽 참나무, 가운데 낙엽송(가문비 모양), 동쪽 비탈 자작나무)에
     * 5칸 간격으로 심은 나무, 군데군데 그루터기와 묘목, 가운데 임도(자갈)와 가로 임도.
     */
    static Voxels plantation(CityTerrain t, int hx, int x0, int z0, int x1, int z1, int ytop, Random r) {
        PortPlan.Canvas c = new PortPlan.Canvas(x0, z0, x1, z1, -2, ytop);
        int crossZ = (z0 + z1) / 2;
        // 임도
        for (int z = z0 + 1; z <= z1; z++) {
            for (int x = hx - 2; x <= hx + 2; x++) {
                if (land(t, x, z) && t.column(x, z).mountainHeight == 0) {
                    c.set(x, -1, z, (x == hx - 2 || x == hx + 2) ? COARSE_DIRT : GRAVEL);
                }
            }
        }
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = crossZ - 1; z <= crossZ + 1; z++) {
                if (land(t, x, z) && t.column(x, z).mountainHeight == 0) {
                    c.set(x, -1, z, z == crossZ ? GRAVEL : COARSE_DIRT);
                }
            }
        }
        // 나무
        for (int z = z0 + 3; z <= z1 - 3; z += 5) {
            for (int x = x0 + 3; x <= x1 - 3; x += 5) {
                int tx = x + ((z / 5) % 2 == 0 ? 0 : 2);
                if (Math.abs(tx - hx) <= 4 || Math.abs(z - crossZ) <= 3 || tx > x1 - 3) {
                    continue;
                }
                Column col = t.column(tx, z);
                if (!land(t, tx, z) || col.mountainHeight > 32) {
                    continue;
                }
                int base = col.mountainHeight - 1; // 땅 윗면
                String wood = tx < hx - 20 ? "oak" : tx < hx + 50 ? "spruce" : "birch";
                int roll = r.nextInt(100);
                if (roll < 8) {
                    stump(c, tx, base, z, wood);
                } else if (roll < 14) {
                    c.set(tx, base, z, GRASS);
                    c.set(tx, base + 1, z, Block.of(wood + "_sapling[stage=0]", 0x5C8A2E));
                } else {
                    tree(c, tx, base, z, wood, r);
                }
            }
        }
        // 울타리 (땅 높이를 따라), 임도 입구 열림, 표지판
        int n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge || !land(t, x, z)) {
                    continue;
                }
                if (z == z1 && Math.abs(x - hx) <= 3) {
                    continue; // 임도 입구
                }
                int y = t.column(x, z).mountainHeight;
                c.set(x, y, z, Block.of("spruce_fence", 0x725430));
                if (n++ % 31 == 15 && (z == z1 || x == x0)) {
                    int sx = x == x0 ? x + 1 : x, sz = z == z1 ? z - 1 : z;
                    int sy = t.column(sx, sz).mountainHeight;
                    if (c.get(sx, sy, sz) == null) {
                        c.set(sx, sy, sz, Block.of("oak_sign[rotation=" + (z == z1 ? 0 : 4) + ",waterlogged=false]", 0xA2834F)
                                .withText("black", false, "벌채 구역", "표시된 나무만", "베어 주세요", "산불 조심"));
                    }
                }
            }
        }
        // 입구 기둥과 표지판
        for (int x : new int[]{hx - 4, hx + 4}) {
            c.fill(x, 0, z1, x, 2, z1, Block.of("spruce_log[axis=y]", 0x3A2A1A));
        }
        c.set(hx + 5, 1, z1, Blocks.wallSign("spruce", "east", "white", false, "북한산", "벌채 구역", "작업자 외", "출입 주의"));
        c.set(hx + 5, 0, z1, Block.of("spruce_log[axis=y]", 0x3A2A1A));
        c.v.connect();
        return c.v;
    }

    /** 나무 한 그루 (밑동은 땅 윗면 base 위): 참나무(둥근 잎), 낙엽송(가문비 원뿔), 자작나무(흰 줄기, 좁은 잎) */
    static void tree(PortPlan.Canvas c, int x, int base, int z, String wood, Random r) {
        c.set(x, base, z, DIRT);
        Block log = Block.of(wood + "_log[axis=y]", wood.equals("birch") ? 0xD8D3C5 : wood.equals("spruce") ? 0x3A2A1A : 0x6D5532);
        double cx = x - c.x0 + 0.5, cz = z - c.z0 + 0.5;
        switch (wood) {
            case "spruce" -> {
                int h = 8 + r.nextInt(3);
                Block leaves = Block.of("spruce_leaves[distance=1,persistent=true,waterlogged=false]", 0x3D5E3A);
                for (int y = 3; y <= h + 1; y++) {
                    double rad = Math.max(0.6, (h + 1 - y) * 0.42);
                    if ((h - y) % 2 == 1) {
                        rad *= 0.7;
                    }
                    for (int dz = -3; dz <= 3; dz++) {
                        for (int dx = -3; dx <= 3; dx++) {
                            if (dx * dx + dz * dz <= rad * rad + 0.3) {
                                c.setIfEmpty(x + dx, base + y, z + dz, leaves);
                            }
                        }
                    }
                }
                c.fill(x, base + 1, z, x, base + h, z, log);
            }
            case "birch" -> {
                int h = 7 + r.nextInt(3);
                Block leaves = Block.of("birch_leaves[distance=1,persistent=true,waterlogged=false]", 0x6B8F3F);
                c.v.ellipsoid(cx, base + h - 1.0, cz, 1.9, 2.6, 1.9, leaves);
                c.fill(x, base + 1, z, x, base + h - 1, z, log);
            }
            default -> {
                int h = 5 + r.nextInt(2);
                Block leaves = Block.of("oak_leaves[distance=1,persistent=true,waterlogged=false]", 0x4F7F2A);
                c.v.ellipsoid(cx, base + h + 1.0, cz, 2.6, 2.1, 2.6, leaves);
                c.fill(x, base + 1, z, x, base + h, z, log);
            }
        }
    }

    /** 그루터기: 밑동 한 칸 */
    static void stump(PortPlan.Canvas c, int x, int base, int z, String wood) {
        c.set(x, base + 1, z, Block.of(wood + "_log[axis=y]", 0x6D5532));
    }

    private LumberPlan() {
    }
}
