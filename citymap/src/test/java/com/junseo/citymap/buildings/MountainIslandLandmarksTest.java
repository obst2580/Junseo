package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 남산타워·북한산 채석장·교도소 섬: 산 위·섬 위 실제 땅 높이에서 거점이 열려 있는지,
 * 거점에서 걸어서(계단으로) 건물의 모든 층에 가는지, 미리보기 그림.
 */
class MountainIslandLandmarksTest {

    static Placement find(String name) {
        for (Placement p : TestCity.buildings().placements()) {
            if (p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 걷기 검사용: 건물 상자에 원래 지형을 채운 복사본 (빈 칸 중 땅속은 돌).
     * WalkCheck 는 상자 안 빈 칸을 공기로 보므로, 산 위 건물은 땅을 채워야 실제처럼 걸어 다닙니다.
     */
    static Voxels withTerrain(Placement p, int x0, int z0) {
        CityTerrain t = TestCity.terrain();
        Voxels v = p.voxels();
        Voxels out = new Voxels(v.w, v.d, v.y0, v.y0 + v.h - 1);
        Block stone = Block.of("stone", 0x7E7E7E);
        for (int j = 0; j < v.d; j++) {
            for (int i = 0; i < v.w; i++) {
                int top = t.column(x0 + i, z0 + j).mountainHeight - 1;
                for (int y = v.y0; y < v.y0 + v.h; y++) {
                    Block b = v.get(i, y, j);
                    if (b == null && y <= top) {
                        b = stone;
                    }
                    if (b != null) {
                        out.set(i, y, j, b);
                    }
                }
            }
        }
        return out;
    }

    /** 걸어서 간 칸 수 (서는 높이 y, 상자 칸 i0..i1 × j0..j1) */
    static int reached(WalkCheck walk, int y, int i0, int j0, int i1, int j1) {
        int n = 0;
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (walk.reached(i, y, j)) {
                    n++;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 남산타워

    @Test
    void namsanHubIsOpenAtSummitHeight() {
        Layout.Hub hub = Plans.hub(TestCity.terrain(), "tower");
        Placement p = find("남산타워");
        assertNotNull(p, "남산타워");
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int stand = TestCity.terrain().column(hx, hz).mountainHeight;
        List<String> bad = new ArrayList<>();
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int x = hx + dx, z = hz + dz;
                int base = TestCity.terrain().groundY() + 1;
                p.column(x, z, base, Integer.MIN_VALUE, (y, b) -> {
                    int vy = y - base;
                    if (!b.isAir() && vy >= stand && vy < stand + 3) {
                        bad.add(x + "," + vy + "," + z + " " + b.id());
                    }
                });
            }
        }
        assertTrue(bad.isEmpty(), "남산 거점 위를 막음: " + bad);
    }

    @Test
    void namsanTowerFloorsReachableOnFoot() {
        Layout.Hub hub = Plans.hub(TestCity.terrain(), "tower");
        Placement p = find("남산타워");
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int x0 = hx - SeoulTower.HUB_I, z0 = hz - SeoulTower.HUB_J;
        Voxels v = withTerrain(p, x0, z0);
        int P = TestCity.terrain().column(hx, hz).mountainHeight;
        // 거점에서 출발 (WalkCheck 좌표는 상자 좌표 + 1)
        WalkCheck walk = new WalkCheck(v).run(SeoulTower.HUB_I + 1, P, SeoulTower.HUB_J + 1);
        int bi0 = SeoulTower.BI0, bj0 = SeoulTower.BJ0, bi1 = bi0 + SeoulTower.BW - 1, bj1 = bj0 + SeoulTower.BD - 1;
        String[] names = {"플라자 지하 1층", "플라자 1층", "플라자 2층", "옥상 테라스"};
        int[] base = {P - 5, P, P + 5, P + 10};
        for (int k = 0; k < base.length; k++) {
            int n = reached(walk, base[k], bi0, bj0, bi1, bj1);
            assertTrue(n > 60, names[k] + " 에 걸어서 못 가요 (" + n + "칸)");
        }
        int t1 = P - 5 + 76;
        for (int k = 0; k < 6; k++) {
            int y = t1 + 4 * k;
            int n = reached(walk, y, SeoulTower.CI - 17, SeoulTower.CJ - 17, SeoulTower.CI + 17, SeoulTower.CJ + 17);
            assertTrue(n > 40, "전망대 T" + (k + 1) + " (y " + y + ") 에 걸어서 못 가요 (" + n + "칸)");
        }
    }

    // ------------------------------------------------------------------ 교도소

    @Test
    void prisonBuildingsHaveStairsToEveryFloor() {
        Random r = new Random(5);
        InteriorTest.assertAllFloorsReachable("수용동", Prison.cellBlock(33, 16, r, "수용동 가동"), new int[]{0, 4, 8, 12});
        InteriorTest.assertAllFloorsReachable("본관", Prison.admin(19, 16, r, "준서교도소"), new int[]{0, 5, 9, 13});
        InteriorTest.assertAllFloorsReachable("의료동", Prison.infirmary(19, 16, r), new int[]{0, 4, 8});
        InteriorTest.assertAllFloorsReachable("노역장", Prison.workshop(28, 17, r), new int[]{0});
        InteriorTest.assertAllFloorsReachable("식당", Prison.messHall(20, 17, r), new int[]{0});
        Voxels tower = Prison.guardTower();
        WalkCheck walk = new WalkCheck(tower).run();
        assertTrue(walk.reachedAt(8) > 20, "망루 감시실에 걸어서 못 가요 (" + walk.reachedAt(8) + "칸)");
    }

    @Test
    void prisonCellsAreReachableFromTheCorridor() {
        Voxels v = Prison.cellBlock(33, 16, new Random(7), "수용동 가동");
        WalkCheck walk = new WalkCheck(v).run();
        int cells = 0;
        for (int L : new int[]{0, 4, 8}) {
            for (int a = 13; a + 2 <= 31; a += 4) {
                if (walk.reached(a + 1, L, 3)) {
                    cells++;
                }
                if (walk.reached(a + 1, L, 11)) {
                    cells++;
                }
            }
        }
        assertTrue(cells == 30, "들어갈 수 있는 거실 " + cells + "/30");
    }

    @Test
    void prisonStaysOnTheIsland() {
        CityTerrain t = TestCity.terrain();
        List<String> bad = new ArrayList<>();
        for (Placement p : TestCity.buildings().placements()) {
            if (!p.kind.equals("prison")) {
                continue;
            }
            double[] b = p.bounds();
            for (int z = (int) Math.floor(b[1]); z < (int) Math.ceil(b[3]); z++) {
                for (int x = (int) Math.floor(b[0]); x < (int) Math.ceil(b[2]); x++) {
                    int base = t.groundY() + 1;
                    List<Integer> ys = new ArrayList<>();
                    p.column(x, z, base, Integer.MIN_VALUE, (y, blk) -> {
                        if (!blk.isAir() && y - base >= 0) {
                            ys.add(y);
                        }
                    });
                    if (!ys.isEmpty() && t.column(x, z).isWater()) {
                        bad.add(p.name + " " + x + "," + z);
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "교도소가 물 위에: " + bad.stream().limit(10).toList());
    }

    // ------------------------------------------------------------------ 채석장

    @Test
    void quarryBuildingsHaveStairsToEveryFloor() {
        InteriorTest.assertAllFloorsReachable("컨테이너 현장사무실", Quarry.containerOffice(new Random(3)), new int[]{0, 4});
        InteriorTest.assertAllFloorsReachable("광물 거래소", Quarry.oreExchange(new Random(4)), new int[]{0, 4, 8});
        WalkCheck walk = new WalkCheck(Quarry.scaleHouse()).run();
        assertTrue(walk.reachedAt(0) > 6, "계근실");
    }

    @Test
    void quarryHubOpenAndRampWalkable() {
        Layout.Hub hub = Plans.hub(TestCity.terrain(), "quarry");
        Placement p = find("북한산 채석장");
        assertNotNull(p, "채석장");
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int stand = TestCity.terrain().column(hx, hz).mountainHeight;
        List<String> bad = new ArrayList<>();
        for (Placement q : TestCity.buildings().placements()) {
            if (CityBuildings.isGround(q)) {
                continue;
            }
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int x = hx + dx, z = hz + dz;
                    int base = TestCity.terrain().groundY() + 1;
                    q.column(x, z, base, Integer.MIN_VALUE, (y, b) -> {
                        int vy = y - base;
                        if (!b.isAir() && vy >= stand && vy < stand + 3) {
                            bad.add(q.name + " " + x + "," + vy + "," + z);
                        }
                    });
                }
            }
        }
        assertTrue(bad.isEmpty(), "채석장 거점 위를 막음: " + bad);
        // 바닥 가운데에서 운반로를 따라 3단(바닥 + 30)까지 걸어 오름
        int x0 = hx - 80, z0 = hz - 92;
        Voxels v = withTerrain(p, x0, z0);
        WalkCheck walk = new WalkCheck(v).run(80 + 1, stand, 92 + 1);
        int top = 0;
        for (int y = stand; y <= stand + 30; y++) {
            if (reached(walk, y, 0, 0, v.w - 1, v.d - 1) > 0) {
                top = y;
            }
        }
        assertTrue(top >= stand + 29, "운반로로 걸어서 오른 높이 " + (top - stand) + " (30 이상이어야)");
    }

    // ------------------------------------------------------------------ 그림

    record View(String name, int x0, int z0, int x1, int z1, int ymin, int ymax, int scale, int cut, boolean top) {
    }

    /** r: 그릴 범위 (눈 둘레 반지름) */
    record Eye(String name, double x, double y, double z, double yaw, double pitch, int ymin, int ymax, int r) {
        Eye(String name, double x, double y, double z, double yaw, double pitch, int ymin, int ymax) {
            this(name, x, y, z, yaw, pitch, ymin, ymax, 170);
        }
    }

    static final View[] VIEWS = {
            new View("mt-namsan-iso", 560, -200, 945, 130, 0, 200, 2, 0, false),
            new View("mt-namsan-top", 560, -200, 945, 130, 0, 200, 2, 0, true),
            new View("mt-bukhansan-iso", -315, -855, 755, -460, 0, 170, 1, 0, false),
            new View("lm-namsan-iso", 720, -105, 815, -5, 30, 240, 3, 0, false),
            new View("lm-namsan-base", 722, -100, 812, -8, 50, 100, 5, 0, false),
            new View("lm-namsan-b1", 740, -90, 795, -45, 50, 100, 8, 0, false),
            new View("lm-namsan-top", 720, -105, 815, -5, 30, 240, 4, 0, true),
            new View("lm-quarry-iso", -150, -805, 35, -480, 20, 200, 3, 0, false),
            new View("lm-quarry-top", -150, -805, 35, -480, 0, 200, 3, 0, true),
            new View("lm-quarry-yard", -110, -750, -25, -665, 25, 80, 7, 0, false),
            new View("lm-quarry-plant", -60, -735, -30, -680, 25, 60, 12, 0, false),
            new View("lm-prison-iso", -700, 855, -568, 990, -12, 30, 5, 0, false),
            new View("lm-prison-top", -700, 855, -568, 990, -12, 30, 5, 0, true),
            new View("lm-prison-1f", -675, 880, -592, 965, -12, 30, 8, 2, false),
            new View("lm-prison-dock", -665, 852, -603, 900, -12, 25, 9, 0, false),
    };

    static final Eye[] EYES = {
            new Eye("eye-namsan-plaza", 767, 0, -24, 180, -10, 0, 260),
            new Eye("eye-namsan-far", 715, 110, 70, 201, -12, 0, 260),
            new Eye("eye-namsan-yongsan", 545, 45, 150, 230, 6, 0, 260, 330),
            new Eye("eye-bukhansan-junggu", 230, 70, -380, 175, 4, 0, 260, 420),
            new Eye("eye-bukhansan-pyeongchang", 300, 0, -520, 185, -8, 0, 260, 300),
            new Eye("eye-quarry-yard", -72, 0, -684, 200, -6, 20, 200),
            new Eye("eye-quarry-face", -90, 0, -718, -110, -12, 20, 200),
            new Eye("eye-quarry-road", -95, 0, -600, 180, 4, 0, 200),
            new Eye("eye-prison-yard", -634, 0, 936, 180, -4, -10, 60),
            new Eye("eye-prison-dock", -640, 0, 868, 180, -8, -10, 60),
            new Eye("eye-prison-sea", -600, 30, 820, 160, 10, -10, 60),
    };

    @Test
    void renderPreviews() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        CityTerrain terrain = TestCity.terrain();
        CityBuildings b = TestCity.buildings();
        File dir = new File("build/preview");
        dir.mkdirs();
        String only = System.getProperty("previewOnly", "");
        int g = terrain.groundY();
        for (View v : VIEWS) {
            if (!only.isEmpty() && !v.name.contains(only)) {
                continue;
            }
            IsoRender r = new IsoRender(terrain, b, v.x0, v.z0, v.x1, v.z1, g + v.ymin, g + v.ymax);
            BufferedImage img = v.top ? r.top(v.scale) : r.iso(v.scale, v.cut == 0 ? Integer.MAX_VALUE : g + v.cut);
            ImageIO.write(img, "png", new File(dir, v.name + ".png"));
        }
        for (Eye e : EYES) {
            if (!only.isEmpty() && !e.name.contains(only)) {
                continue;
            }
            int r = e.r;
            int gy = terrain.standY((int) e.x, (int) e.z);
            IsoRender ir = new IsoRender(terrain, b, (int) e.x - r, (int) e.z - r, (int) e.x + r, (int) e.z + r, g + e.ymin, g + e.ymax);
            // y 가 0 이면 그 자리 땅 위 눈높이, 아니면 월드 높이 그대로
            double ey = e.y == 0 ? gy + 1.6 : e.y;
            BufferedImage img = ir.perspective(e.x, ey, e.z, e.yaw, e.pitch, 80, 1280, 720);
            ImageIO.write(img, "png", new File(dir, e.name + ".png"));
        }
    }

    /** 건물 하나 잘라 보기 (cut 높이 위를 걷어 냄) */
    @Test
    void renderCutaways() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        String only = System.getProperty("previewOnly", "");
        File dir = new File("build/preview");
        dir.mkdirs();
        if (only.isEmpty() || "lm-namsan-cut".contains(only) || only.contains("namsan-cut")) {
            Layout.Hub hub = Plans.hub(TestCity.terrain(), "tower");
            int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
            Placement p = find("남산타워");
            Voxels v = withTerrain(p, hx - SeoulTower.HUB_I, hz - SeoulTower.HUB_J);
            int P = TestCity.terrain().column(hx, hz).mountainHeight;
            int[] cuts = {P - 2, P + 3, P + 8, P + 12, P + 73, P + 77, P + 81, P + 89};
            String[] names = {"b1", "1f", "2f", "roof", "t1", "t2", "t3", "t5"};
            for (int k = 0; k < cuts.length; k++) {
                boolean pod = k >= 4;
                int ci = SeoulTower.CI, cj = SeoulTower.CJ;
                Voxels c = pod ? crop(v, ci - 18, cj - 18, ci + 18, cj + 18, cuts[k] - 5, cuts[k])
                        : crop(v, SeoulTower.BI0 - 8, SeoulTower.BJ0 - 2, SeoulTower.BI0 + SeoulTower.BW + 1, SeoulTower.BJ0 + SeoulTower.BD + 2, cuts[k] - 8, cuts[k]);
                ImageIO.write(new IsoRender(c, 1).iso(pod ? 14 : 10, Integer.MAX_VALUE), "png", new File(dir, "lm-namsan-cut-" + names[k] + ".png"));
            }
        }
    }

    /** 높이 ya..yb 만 남긴 복사본 (y 를 0 부터로 옮김) */
    static Voxels crop(Voxels v, int i0, int j0, int i1, int j1, int ya, int yb) {
        Voxels out = new Voxels(i1 - i0 + 1, j1 - j0 + 1, 0, yb - ya);
        for (int y = ya; y <= yb; y++) {
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    Block b = v.get(i, y, j);
                    if (b != null) {
                        out.set(i - i0, y - ya, j - j0, b);
                    }
                }
            }
        }
        return out;
    }
}
