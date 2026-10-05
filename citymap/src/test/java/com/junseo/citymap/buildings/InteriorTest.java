package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 건물 안을 걸어서 다닐 수 있는지: 정문에서 들어가 계단으로 모든 층과 옥상까지 */
class InteriorTest {

    static void assertAllFloorsReachable(String name, Voxels v, int[] levels) {
        WalkCheck walk = new WalkCheck(v).run();
        for (int k = 0; k < levels.length; k++) {
            int n = walk.reachedAt(levels[k]);
            assertTrue(n > 20, name + ": " + k + "층(서는 높이 " + levels[k] + ")에 걸어서 못 가요 (" + n + "칸)");
        }
    }

    static Tower.Spec office() {
        Tower.Spec s = new Tower.Spec(46, 30, 8);
        s.name = "준서타워";
        return s;
    }

    @Test
    void officeTowerHasStairsToEveryFloor() {
        Tower t = Tower.build(office(), new Random(1));
        assertAllFloorsReachable("사무 빌딩", t.v, t.levels);
    }

    @Test
    void hotelAndHomesToo() {
        Tower.Spec h = new Tower.Spec(50, 40, 6);
        h.use = k -> k == 0 ? Tower.Use.LOBBY : Tower.Use.HOTEL;
        h.lobbyH = Floors.HALL;
        Tower t = Tower.build(h, new Random(2));
        assertAllFloorsReachable("호텔", t.v, t.levels);

        Tower.Spec a = new Tower.Spec(52, 40, 5);
        a.use = k -> k == 0 ? Tower.Use.LOBBY : Tower.Use.HOME;
        a.typicalH = Floors.HOME;
        Tower t2 = Tower.build(a, new Random(3));
        assertAllFloorsReachable("주상복합", t2.v, t2.levels);
    }

    @Test
    void villasAndShopsHaveStairsToEveryFloor() {
        for (int seed = 0; seed < 60; seed++) {
            for (ShopHouse.Style style : ShopHouse.Style.values()) {
                int w = 10 + seed % 9, d = 10 + (seed * 7) % 9;
                Voxels v = ShopHouse.build(w, d, new Random(seed), style);
                int floors = 0;
                for (int y = 3; y < v.y0 + v.h; y += 4) {
                    if (v.get(1, y, 1) != null || v.get(w / 2, y, d / 2) != null) {
                        floors++;
                    }
                }
                WalkCheck walk = new WalkCheck(v).run();
                for (int k = 0; k <= 2; k++) {
                    int n = walk.reachedAt(4 * k);
                    assertTrue(n > 6, style + " " + w + "×" + d + " #" + seed + ": " + k + "층에 걸어서 못 가요 (" + n + "칸)");
                }
            }
        }
    }

    /** 공항 터미널: 육지 쪽 출입문으로 들어가 계단으로 탑승층(서는 높이 5)까지 */
    @Test
    void terminalGateLevelIsReachable() {
        Placement terminal = TestCity.buildings().placements().stream().filter(p -> p.kind.equals("terminal")).findFirst().orElseThrow();
        WalkCheck walk = new WalkCheck(terminal.voxels()).run();
        assertTrue(walk.reachedAt(0) > 2000, "터미널 1층 " + walk.reachedAt(0));
        assertTrue(walk.reachedAt(5) > 500, "터미널 탑승층 " + walk.reachedAt(5));
    }

    /** 대형시장: 1층(0) → 2층(5) → 3층(9) 계단으로 */
    @Test
    void marketFloorsAreReachable() {
        Placement market = TestCity.buildings().placements().stream().filter(p -> p.kind.equals("market")).findFirst().orElseThrow();
        WalkCheck walk = new WalkCheck(market.voxels()).run();
        assertTrue(walk.reachedAt(0) > 1000, "시장 1층 " + walk.reachedAt(0));
        assertTrue(walk.reachedAt(5) > 500, "시장 2층 " + walk.reachedAt(5));
        assertTrue(walk.reachedAt(9) > 500, "시장 3층 " + walk.reachedAt(9));
    }

    /** 청라돔: 출입구 → 콘코스 → 보미토리 → 계단 통로로 맨 윗줄(서는 높이 10)까지 */
    @Test
    void domeStandsAreReachable() {
        Placement dome = TestCity.buildings().placements().stream().filter(p -> p.kind.equals("dome")).findFirst().orElseThrow();
        WalkCheck walk = new WalkCheck(dome.voxels()).run();
        assertTrue(walk.reachedAt(0) > 1000, "돔 콘코스 " + walk.reachedAt(0));
        assertTrue(walk.reachedAt(4) > 200, "돔 아래쪽 관중석 " + walk.reachedAt(4));
        assertTrue(walk.reachedAt(10) > 100, "돔 맨 윗줄 " + walk.reachedAt(10));
    }

    /** 도시에 실제로 놓인 사무 빌딩·아파트: 정문에서 계단으로 옥상(아파트는 모든 층)까지 */
    @Test
    void cityTowersAndApartmentsAreClimbable() {
        int offices = 0, apartments = 0;
        for (Placement p : TestCity.buildings().placements()) {
            if (p.kind.equals("office") && offices < 12) {
                offices++;
                Voxels v = p.voxels();
                int roof = v.y0 + v.h - 1 - 10;
                WalkCheck walk = new WalkCheck(v).run();
                assertTrue(walk.reachedAt(roof) > 10, p + " 옥상(" + roof + ")에 걸어서 못 가요");
            } else if (p.kind.equals("apartment") && apartments < 8) {
                // 동마다 층 높이가 달라서(필로티·로비) 1층과 꼭대기 근처(옥상·계단실 지붕)에 닿는지만 봄. 층마다는 ApartmentComplexTest
                apartments++;
                Voxels v = p.voxels();
                int top = v.y0 + v.h - 1;
                WalkCheck walk = new WalkCheck(v).run();
                assertTrue(walk.reachedAt(0) > 20, p + " 1층에 못 들어가요");
                int highest = 0;
                for (int y = top; y > 0 && highest == 0; y--) {
                    if (walk.reachedAt(y) > 10) {
                        highest = y;
                    }
                }
                assertTrue(highest >= top - 18, p + " 꼭대기까지 못 올라가요 (" + highest + " / " + top + ")");
            }
        }
        assertTrue(offices > 0 && apartments > 0);
    }

    @Test
    void renderSamples() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        File dir = new File("build/preview");
        dir.mkdirs();
        Tower t = Tower.build(office(), new Random(1));
        ImageIO.write(new IsoRender(t.v, 4).iso(6, Integer.MAX_VALUE), "png", new File(dir, "int-office.png"));
        ImageIO.write(new IsoRender(t.v, 4).iso(8, t.levels[2] + 2), "png", new File(dir, "int-office-cut.png"));
        Tower.Spec h = new Tower.Spec(50, 40, 6);
        h.use = k -> k == 0 ? Tower.Use.LOBBY : Tower.Use.HOTEL;
        Tower th = Tower.build(h, new Random(2));
        ImageIO.write(new IsoRender(th.v, 4).iso(8, th.levels[2] + 2), "png", new File(dir, "int-hotel-cut.png"));
        Tower.Spec a = new Tower.Spec(52, 40, 5);
        a.use = k -> k == 0 ? Tower.Use.LOBBY : Tower.Use.HOME;
        Tower ta = Tower.build(a, new Random(3));
        ImageIO.write(new IsoRender(ta.v, 4).iso(8, ta.levels[2] + 2), "png", new File(dir, "int-home-cut.png"));
        ImageIO.write(new IsoRender(t.v, 4).iso(8, t.levels[0] + 3), "png", new File(dir, "int-lobby-cut.png"));
        for (ShopHouse.Style style : ShopHouse.Style.values()) {
            for (int seed = 1; seed <= 2; seed++) {
                Voxels sv = ShopHouse.build(14 + seed * 2, 15, new Random(seed * 11L), style);
                String n = style.name().toLowerCase();
                ImageIO.write(new IsoRender(sv, 3).iso(14, Integer.MAX_VALUE), "png", new File(dir, "int-" + n + seed + ".png"));
                ImageIO.write(new IsoRender(sv, 3).iso(14, 6), "png", new File(dir, "int-" + n + seed + "-2f.png"));
                ImageIO.write(new IsoRender(sv, 3).iso(14, 2), "png", new File(dir, "int-" + n + seed + "-1f.png"));
            }
        }
    }
}
