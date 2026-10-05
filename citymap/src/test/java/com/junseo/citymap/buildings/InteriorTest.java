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
