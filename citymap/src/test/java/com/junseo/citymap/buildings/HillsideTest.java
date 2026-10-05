package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 비탈 동네 집 (해방촌 다가구·동네 가게, 평창동 단독주택): 걸어서 모든 층, 미리보기 그림 */
class HillsideTest {

    @Test
    void dagaguHasStairsToEveryFloor() {
        for (int seed = 0; seed < 40; seed++) {
            int w = 12 + seed % 6, d = 11 + (seed * 3) % 5, floors = 2 + seed % 2;
            int back = seed % 4 - 1;
            String[] shop = seed % 3 == 0 ? HillHouse.shopName(new Random(seed)) : null;
            Voxels v = HillHouse.build(w, d, floors, back, shop, "신흥로", new Random(seed));
            int[] levels = Floors.levels(4, 4, floors);
            WalkCheck walk = new WalkCheck(v).run();
            for (int k = 0; k < levels.length; k++) {
                int n = walk.reachedAt(levels[k]);
                assertTrue(n > 10, "다가구 " + w + "×" + d + " " + floors + "층 seed " + seed + ": " + k + "층(" + levels[k] + ")에 못 감 (" + n + "칸)");
            }
        }
    }

    @Test
    void gardenHouseHasStairsToEveryFloor() {
        for (int seed = 0; seed < 20; seed++) {
            int w = 22 + seed % 6, d = 23 + seed % 5;
            boolean garage = seed % 2 == 0;
            Voxels v = GardenHouse.build(w, d, garage, new Random(seed));
            WalkCheck walk = new WalkCheck(v).run();
            for (int L : new int[]{0, 4}) {
                int n = walk.reachedAt(L);
                assertTrue(n > 30, "단독주택 " + w + "×" + d + " seed " + seed + ": 서는 높이 " + L + " 에 못 감 (" + n + "칸)");
            }
            if (garage) {
                assertTrue(!v.carSpots().isEmpty(), "차고 자리");
            }
        }
    }

    /** 실거주 크기: 원룸 6×8 이상 (욕실 2×3 따로), 투룸 9×10 이상 (침실 둘 3×4, 거실 4×5), 옥탑방도 사는 집이면 6×8 */
    @Test
    void hillHomesAreLivableSize() {
        int checked = 0;
        for (int seed = 0; seed < 60; seed++) {
            int w = 12 + seed % 7, d = 14, floors = 2 + seed % 2;
            int back = seed % 5 - 1;
            String[] shop = seed % 4 == 0 ? HillHouse.shopName(new Random(seed)) : null;
            HillHouse h = HillHouse.create(w, d, floors, back, shop, "신흥로", new Random(seed));
            String id = "다가구 " + w + "×" + d + " seed " + seed;
            assertTrue(!h.units.isEmpty(), id + ": 세대 없음");
            WalkCheck walk = new WalkCheck(h.v).run();
            for (int[] u : h.units) {
                int a = u[2] - u[0] + 1, b = u[3] - u[1] + 1;
                int lo = Math.min(a, b), hi = Math.max(a, b);
                if (u[5] == 2) {
                    assertTrue(lo >= 9 && hi >= 10, id + ": 투룸 " + a + "×" + b);
                } else {
                    assertTrue(lo >= 6 && hi >= 8, id + ": 원룸 " + a + "×" + b);
                }
                assertTrue(reachedIn(walk, u, u[4]) > lo * hi / 3, id + ": 세대 안을 걸어 다닐 수 없음 " + java.util.Arrays.toString(u));
                checked++;
            }
            for (int[] rm : h.rooms) {
                int a = rm[2] - rm[0] + 1, b = rm[3] - rm[1] + 1;
                int lo = Math.min(a, b), hi = Math.max(a, b);
                switch (rm[5]) {
                    case 4 -> assertTrue(lo >= 2 && hi >= 3, id + ": 욕실 " + a + "×" + b);
                    case 3 -> assertTrue(lo >= 3 && hi >= 4, id + ": 침실 " + a + "×" + b);
                    default -> assertTrue(lo >= 4 && hi >= 5, id + ": 거실 " + a + "×" + b);
                }
                assertTrue(reachedIn(walk, rm, rm[4]) > 0, id + ": 방에 못 들어감 " + java.util.Arrays.toString(rm));
            }
        }
        assertTrue(checked > 100, "검사한 세대 " + checked);
    }

    /** 평창동 단독: 집 바닥 16×14 이상, 2층, 1층 거실 6×6·손님방·욕실, 2층 안방 5×5·침실 둘 이상·욕실 둘 */
    @Test
    void gardenHousesAreLivableSize() {
        for (int seed = 0; seed < 20; seed++) {
            GardenHouse g = GardenHouse.create(22 + seed % 6, 23 + seed % 5, seed % 2 == 0, new Random(seed));
            String id = "단독주택 seed " + seed;
            int fw = g.hx1 - g.hx0 + 1, fd = g.hj1 - g.hj0 + 1;
            assertTrue(Math.min(fw, fd) >= 14 && Math.max(fw, fd) >= 16, id + ": 바닥 " + fw + "×" + fd);
            WalkCheck walk = new WalkCheck(g.v).run();
            int[] count = new int[8];
            for (int[] rm : g.rooms) {
                int a = rm[2] - rm[0] + 1, b = rm[3] - rm[1] + 1;
                int lo = Math.min(a, b), hi = Math.max(a, b);
                switch (rm[5]) {
                    case GardenHouse.LIVING -> assertTrue(lo >= 6, id + ": 거실 " + a + "×" + b);
                    case GardenHouse.MASTER -> assertTrue(lo >= 5, id + ": 안방 " + a + "×" + b);
                    case GardenHouse.BED, GardenHouse.GUEST -> assertTrue(lo >= 3 && hi >= 4, id + ": 침실 " + a + "×" + b);
                    case GardenHouse.BATH -> assertTrue(lo >= 2 && hi >= 3, id + ": 욕실 " + a + "×" + b);
                    default -> {
                    }
                }
                if (rm[4] == 4) {
                    count[rm[5]]++;
                }
                assertTrue(reachedIn(walk, rm, rm[4]) > 0, id + ": 방에 못 들어감 " + java.util.Arrays.toString(rm));
            }
            assertTrue(count[GardenHouse.MASTER] == 1 && count[GardenHouse.BED] >= 2 && count[GardenHouse.BATH] >= 2,
                    id + ": 2층 방 " + java.util.Arrays.toString(count));
            assertTrue(g.rooms.stream().anyMatch(rm -> rm[4] == 0 && rm[5] == GardenHouse.GUEST), id + ": 손님방");
        }
    }

    /** 도시 배치에서 비탈 마을들을 다시 만듦 (마을 앞에 놓인 것들만 피할 곳으로) */
    static List<HillVillage> cityVillages() {
        List<Placement> all = TestCity.buildings().placements();
        int first = 0;
        while (first < all.size() && !all.get(first).name.endsWith(" 골목·계단길")) {
            first++;
        }
        return HillsidePlan.villages(TestCity.terrain(), all.subList(0, first));
    }

    /**
     * 실제 지형 + 도시 전체 블록으로 만든 걷기 지도 (월드 직사각형 x0..x1 × z0..z1).
     * 땅속은 돌, 물은 비움 (빠짐), 건물·마을 블록은 생성기처럼 덮어씀.
     */
    static Voxels worldGrid(int x0, int z0, int x1, int z1) {
        com.junseo.citymap.terrain.CityTerrain t = TestCity.terrain();
        CityBuildings b = TestCity.buildings();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                int mh = t.column(x, z).mountainHeight;
                lo = Math.min(lo, mh - 1);
                hi = Math.max(hi, mh - 1);
            }
        }
        lo -= 4;
        hi += 40;
        Voxels v = new Voxels(x1 - x0 + 1, z1 - z0 + 1, lo, hi);
        int base = t.groundY() + 1;
        Block stone = Block.of("stone", 0x7E7E7E);
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                com.junseo.citymap.terrain.Column c = t.column(x, z);
                int i = x - x0, j = z - z0;
                if (c.isWater() && !c.deck) {
                    v.fill(i, lo, j, i, -1, j, Blocks.AIR);
                    continue;
                }
                int top = c.tunnel ? -1 : c.mountainHeight - 1;
                v.fill(i, lo, j, i, top, j, stone);
                b.column(x, z, c, (y, blk) -> v.set(i, y - base, j, blk));
            }
        }
        return v;
    }

    /** 마을마다: 큰길 인도에서 걸어서 모든 골목 줄, 모든 집의 모든 층, 등산로 끝까지 */
    @Test
    void villagesAreWalkableFromTheRoad() {
        com.junseo.citymap.terrain.CityTerrain t = TestCity.terrain();
        List<HillVillage> vs = cityVillages();
        assertTrue(vs.size() >= 6, "비탈 마을 " + vs.size());
        int houses = 0;
        for (HillVillage vl : vs) {
            HillsidePlan.Site s = vl.site;
            int x0 = s.x0() - 8, z0 = s.z0() - 8, x1 = s.x1() + 8, z1 = s.z1() + 8;
            if (vl.trail != null) {
                int[] wr = vl.worldRect(vl.trail.ia - 3, vl.trail.ja - 3, vl.trail.ib + 3, vl.d - 1);
                x0 = Math.min(x0, wr[0]);
                z0 = Math.min(z0, wr[1]);
                x1 = Math.max(x1, wr[2]);
                z1 = Math.max(z1, wr[3]);
            }
            Voxels grid = worldGrid(x0, z0, x1, z1);
            // 출발: 마을 정면 가운데에서 가장 가까운 인도 칸
            int[] fc = vl.g.world(vl.w / 2, vl.d - 1);
            int sx = 0, sz = 0;
            double best = Double.MAX_VALUE;
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    com.junseo.citymap.terrain.Column c = t.column(x, z);
                    // 인도가 우선, 고속도로 앞 마을은 갓길
                    double dd = Math.hypot(x - fc[0], z - fc[1]) + (c.surface == com.junseo.citymap.terrain.Surface.SIDEWALK ? 0 : 6);
                    if (c.isRoad() && !c.deck && !c.tunnel && dd < best
                            && grid.get(x - x0, 0, z - z0) == null) {
                        best = dd;
                        sx = x;
                        sz = z;
                    }
                }
            }
            assertTrue(best < 30, vl.name + ": 가까운 인도가 없음");
            WalkCheck walk = new WalkCheck(grid).run(sx - x0 + 1, 0, sz - z0 + 1);
            for (int r = 1; r < vl.rows.size(); r++) {
                HillVillage.Row row = vl.rows.get(r);
                int n = 0, all = 0;
                for (int j = row.aa(); j <= row.ab(); j++) {
                    for (int i = 2; i <= vl.w - 3; i++) {
                        int[] wc = vl.g.world(i, j);
                        all++;
                        if (walk.reached(wc[0] - x0, row.level(), wc[1] - z0)) {
                            n++;
                        }
                    }
                }
                assertTrue(n > all / 2, vl.name + " " + r + "번째 골목 (높이 " + row.level() + ") 에 걸어서 못 감: " + n + "/" + all);
            }
            for (HillVillage.Lot lot : vl.lots) {
                if (lot.kind().equals("shop")) {
                    continue; // 사람이 안 들어가는 가게는 속을 비움 (Unfurnish.declutter)
                }
                for (int k = 0; k < lot.floors().length; k++) {
                    int y = lot.level() + lot.floors()[k], n = 0;
                    for (int j = lot.j0(); j <= lot.j1(); j++) {
                        for (int i = lot.i0(); i <= lot.i1(); i++) {
                            int[] wc = vl.g.world(i, j);
                            if (walk.reached(wc[0] - x0, y, wc[1] - z0)) {
                                n++;
                            }
                        }
                    }
                    assertTrue(n > 6, vl.name + " " + lot.name() + " (" + lot.i0() + "," + lot.j0() + ") " + k + "층 (높이 " + y + ") 에 걸어서 못 감");
                }
                houses++;
            }
            if (vl.trail != null) {
                int[] wc = vl.g.world(vl.trail.restI + 2, vl.trail.restJ + 1);
                boolean ok = walk.reached(wc[0] - x0, vl.trail.restStand, wc[1] - z0);
                assertTrue(ok, vl.name + " 등산로 쉼터에 걸어서 못 감");
            }
        }
        for (HillVillage vl : vs) {
            System.out.println("비탈 마을 " + vl.name + " (" + vl.style + ") " + vl.site + " 줄 높이 "
                    + vl.rows.stream().map(r -> r.level()).toList() + ", 필지 " + vl.lots.size());
        }
        System.out.println("비탈 필지 모두 " + houses);
        assertTrue(houses >= 50, "비탈 집 " + houses);
    }

    static int reachedIn(WalkCheck walk, int[] r, int level) {
        int n = 0;
        for (int j = r[1]; j <= r[3]; j++) {
            for (int i = r[0]; i <= r[2]; i++) {
                if (walk.reached(i, level, j)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 한강공원 편의점·화장실 안에 걸어 들어가고, 주차장에 차 자리 */
    @Test
    void hangangFacilitiesAreUsable() {
        Voxels store = HangangPlan.store(9, 8, new Random(1));
        assertTrue(new WalkCheck(store).run().reachedAt(0) > 25, "편의점 안");
        assertTrue(new WalkCheck(GrandPark.restroom(11, 9)).run().reachedAt(0) > 25, "화장실 안");
        assertTrue(HangangPlan.parking(18, 11).carSpots().size() >= 3, "작은 주차장 차 자리");
        assertTrue(HangangPlan.parking(24, 11).carSpots().size() >= 5, "주차장 차 자리");
        long parks = TestCity.buildings().placements().stream().filter(p -> p.name.startsWith("한강공원") && p.kind.equals("garage")).count();
        assertTrue(parks >= 1, "한강공원 주차장");
        for (Placement p : TestCity.buildings().placements()) {
            if (p.name.startsWith("한강공원") && p.kind.equals("garage")) {
                assertTrue(!p.carSpots(TestCity.terrain().groundY() + 1).isEmpty(), p.name);
            }
        }
    }

    record View(String name, int x0, int z0, int x1, int z1, int ymin, int ymax, int scale, int cut) {
    }

    record Eye(String name, double x, double y, double z, double yaw, double pitch, int ymin, int ymax) {
    }

    static final View[] VIEWS = {
            new View("dz-hill-yongsan", 555, -125, 665, 50, -6, 70, 4, 0),
            new View("dz-hill-namsan", 735, 35, 875, 125, -6, 60, 4, 0),
            new View("dz-hill-bukhansan", -275, -565, -150, -460, -6, 70, 4, 0),
            new View("dz-hill-bukhansan2", 250, -610, 540, -515, -6, 70, 3, 0),
            new View("dz-hill-trailhead", 55, -650, 160, -495, -6, 130, 4, 0),
            new View("dz-hangang-jamsil", 1180, 50, 1300, 140, -12, 30, 5, 0),
            new View("dz-hangang-mangwon", -580, -180, -440, -100, -12, 30, 5, 0),
    };

    static final Eye[] EYES = {
            new Eye("dz-eye-haebangchon", 572, 0, -48, -112, -14, -6, 90),
            new Eye("dz-eye-haebangchon-alley", 602, 0, -40, 180, -6, -6, 90),
            new Eye("dz-eye-haebangchon-stairs", 603, 0, -56, -90, -18, -6, 90),
            new Eye("dz-eye-pyeongchang", -210, 0, -472, 180, -12, -6, 90),
            new Eye("dz-eye-trailhead", 105, 0, -505, 180, -10, -6, 130),
            new Eye("dz-eye-hangang", 1336, 0, 76, 80, -6, -12, 40),
    };

    @Test
    void renderVillages() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        com.junseo.citymap.terrain.CityTerrain terrain = TestCity.terrain();
        CityBuildings b = TestCity.buildings();
        File dir = new File("build/preview");
        dir.mkdirs();
        String only = System.getProperty("previewOnly", "");
        int g = terrain.groundY();
        for (View v : VIEWS) {
            if (!only.isEmpty() && !v.name().contains(only)) {
                continue;
            }
            IsoRender r = new IsoRender(terrain, b, v.x0(), v.z0(), v.x1(), v.z1(), g + v.ymin(), g + v.ymax());
            ImageIO.write(r.iso(v.scale(), v.cut() == 0 ? Integer.MAX_VALUE : g + v.cut()), "png", new File(dir, v.name() + ".png"));
        }
        for (Eye e : EYES) {
            if (!only.isEmpty() && !e.name().contains(only)) {
                continue;
            }
            int rr = 150;
            int gy = terrain.standY((int) e.x(), (int) e.z());
            IsoRender ir = new IsoRender(terrain, b, (int) e.x() - rr, (int) e.z() - rr, (int) e.x() + rr, (int) e.z() + rr, g + e.ymin(), g + e.ymax());
            double ey = e.y() == 0 ? gy + 1.6 : e.y();
            ImageIO.write(ir.perspective(e.x(), ey, e.z(), e.yaw(), e.pitch(), 80, 1280, 720), "png", new File(dir, e.name() + ".png"));
        }
    }

    @Test
    void renderHouses() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        File dir = new File("build/preview");
        dir.mkdirs();
        Voxels a = HillHouse.build(16, 14, 3, 2, null, "신흥로", new Random(3));
        ImageIO.write(new IsoRender(a, 3).iso(10, Integer.MAX_VALUE), "png", new File(dir, "dz-hill-house.png"));
        ImageIO.write(new IsoRender(a, 3).iso(10, 2), "png", new File(dir, "dz-hill-house-1f.png"));
        ImageIO.write(new IsoRender(a, 3).iso(10, 10), "png", new File(dir, "dz-hill-house-3f.png"));
        Voxels b = HillHouse.build(13, 14, 2, 1, HillHouse.shopName(new Random(5)), "신흥로", new Random(5));
        ImageIO.write(new IsoRender(b, 3).iso(10, Integer.MAX_VALUE), "png", new File(dir, "dz-hill-shop.png"));
        ImageIO.write(new IsoRender(b, 3).iso(10, 2), "png", new File(dir, "dz-hill-shop-1f.png"));
        for (int k = 0; k < 2; k++) {
            Voxels g = GardenHouse.build(24, 26, k == 0, new Random(10 + k));
            ImageIO.write(new IsoRender(g, 3).iso(7, Integer.MAX_VALUE), "png", new File(dir, "dz-garden-house-" + k + ".png"));
            ImageIO.write(new IsoRender(g, 3).iso(7, 2), "png", new File(dir, "dz-garden-house-" + k + "-1f.png"));
            ImageIO.write(new IsoRender(g, 3).iso(7, 6), "png", new File(dir, "dz-garden-house-" + k + "-2f.png"));
        }
        // 골목 한 줄: 다가구 여섯 채를 붙여서
        Voxels row = new Voxels(96, 17, -1, 24);
        int x = 0;
        for (int k = 0; k < 6; k++) {
            int w = 12 + (k * 3) % 5;
            Voxels h = HillHouse.build(w, 14, 2 + k % 2, -1, k == 2 ? HillHouse.shopName(new Random(k)) : null, "신흥로", new Random(k));
            PrisonPlan.stamp(row, h, x, 0, "south");
            x += w + (k % 2);
        }
        row.fill(0, -1, 14, 95, -1, 16, Block.of("light_gray_concrete", 0x7D7D73));
        ImageIO.write(new IsoRender(row, 2).iso(6, Integer.MAX_VALUE), "png", new File(dir, "dz-hill-row.png"));
    }
}
