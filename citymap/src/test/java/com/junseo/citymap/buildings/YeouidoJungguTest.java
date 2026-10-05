package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 여의도·중구 랜드마크: 정문에서 걸어 들어가 계단으로 모든 층(지하 포함)에 가는지, 미리보기 그림 */
class YeouidoJungguTest {

    /** 상자를 위로 dy 만큼 올린 복사본 (지하층을 WalkCheck 로 보려고: 땅 아래는 검사하지 않으므로) */
    static Voxels lifted(Voxels v, int dy) {
        Voxels out = new Voxels(v.w, v.d, v.y0 + dy, v.y0 + v.h - 1 + dy);
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b != null) {
                        out.set(i, y + dy, j, b);
                    }
                }
            }
        }
        return out;
    }

    static void assertLevels(String name, WalkCheck walk, int[] levels, int min) {
        for (int k = 0; k < levels.length; k++) {
            int n = walk.reachedAt(levels[k]);
            assertTrue(n > min, name + ": " + k + "번째 층(서는 높이 " + levels[k] + ")에 걸어서 못 가요 (" + n + "칸)");
        }
    }

    @Test
    void sixtyThreeHasStairsToEveryFloorAndTheVault() {
        Voxels v = SixtyThree.build(SixtyThree.W, SixtyThree.D, new Random(63));
        assertLevels("63빌딩", new WalkCheck(v).run(), SixtyThree.levels(), 20);
        // 지하 금고: 1층 홀에서 출발해 직원 계단으로
        int dy = 10;
        Voxels up = lifted(v, dy);
        WalkCheck walk = new WalkCheck(up).run(SixtyThree.W / 2 + 1, dy, SixtyThree.D - 2);
        int n = walk.reachedAt(SixtyThree.B1 + dy);
        assertTrue(n > 150, "63빌딩 지하 1층(금고)에 걸어서 못 가요 (" + n + "칸)");
    }

    @Test
    void broadcastBuildingsHaveStairsToEveryFloor() {
        Voxels t = Broadcast.tower(Broadcast.TOWER_W, Broadcast.TOWER_D, new Random(7));
        assertLevels("방송국 본관", new WalkCheck(t).run(), Floors.levels(Floors.HALL, Floors.OFFICE, Broadcast.TOWER_FLOORS), 20);
        Voxels s = Broadcast.studio(Broadcast.STUDIO_W, Broadcast.STUDIO_D, new Random(8));
        assertLevels("방송국 스튜디오동", new WalkCheck(s).run(), Broadcast.STUDIO_LEVELS, 20);
    }

    @Test
    void cityHallAndGarageHaveStairsToEveryFloor() {
        Voxels old = CityHall.oldHall(CityHall.OLD_W, CityHall.OLD_D, new Random(1));
        assertLevels("서울도서관 (옛 시청)", new WalkCheck(old).run(), CityHall.OLD_LEVELS, 20);
        Voxels nh = CityHall.newHall(CityHall.NEW_W, CityHall.NEW_D, new Random(2));
        assertLevels("서울시청 새 청사", new WalkCheck(nh).run(), CityHall.newLevels(), 20);
        Voxels g = CityGarage.build(CityGarage.W, CityGarage.D, new Random(3));
        assertLevels("시청 차고지 사무동", new WalkCheck(g).run(), CityGarage.OFFICE_LEVELS, 12);
    }

    @Test
    void seoulStationHasStairsToEveryFloor() {
        Voxels h = SeoulStation.hall(SeoulStation.HALL_W, SeoulStation.HALL_D, 24, new Random(4));
        assertLevels("서울역 새 역사", new WalkCheck(h).run(), SeoulStation.HALL_LEVELS, 20);
        Voxels o = SeoulStation.oldStation(SeoulStation.OLD_W, SeoulStation.OLD_D, new Random(5));
        assertLevels("문화역서울284", new WalkCheck(o).run(), SeoulStation.OLD_LEVELS, 20);
    }

    @Test
    void myeongdongBuildingsHaveStairsToEveryFloor() {
        for (int seed = 0; seed < 24; seed++) {
            int w = 11 + seed % 6, d = 14 + (seed * 5) % 10, floors = 4 + seed % 4;
            Myeongdong.Kind kind = seed % 8 == 0 ? Myeongdong.Kind.JEWELRY : seed % 8 == 1 ? Myeongdong.Kind.CAFE : Myeongdong.Kind.SHOP;
            if (kind == Myeongdong.Kind.JEWELRY) {
                w = 16;
                d = 20;
            }
            Voxels v = Myeongdong.building(w, d, new Random(seed), kind, floors);
            assertLevels("명동 " + kind + " " + w + "×" + d + " #" + seed, new WalkCheck(v).run(), Myeongdong.levels(floors), 6);
        }
    }

    /** 하늘색 여백을 잘라 낸 그림 */
    static java.awt.image.BufferedImage crop(java.awt.image.BufferedImage img) {
        int sky = 0xBFD9EE, x0 = img.getWidth(), y0 = img.getHeight(), x1 = 0, y1 = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) != sky) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 < x0) {
            return img;
        }
        x0 = Math.max(0, x0 - 4);
        y0 = Math.max(0, y0 - 4);
        return img.getSubimage(x0, y0, Math.min(img.getWidth() - x0, x1 - x0 + 8), Math.min(img.getHeight() - y0, y1 - y0 + 8));
    }

    static void save(java.awt.image.BufferedImage img, File dir, String name) throws IOException {
        ImageIO.write(crop(img), "png", new File(dir, name + ".png"));
    }

    @Test
    void renderLandmarks() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        String only = System.getProperty("previewOnly", "");
        File dir = new File("build/preview");
        dir.mkdirs();
        if (only.isEmpty() || "lm-63".contains(only) || only.contains("lm-63")) {
            Voxels v = SixtyThree.build(SixtyThree.W, SixtyThree.D, new Random(63));
            save(new IsoRender(v, 4).iso(3, Integer.MAX_VALUE), dir, "lm-63");
            save(new IsoRender(v, 4).iso(8, 3), dir, "lm-63-bank");
            save(new IsoRender(v, 4).iso(8, -3), dir, "lm-63-vault");
            save(new IsoRender(v, 4).iso(8, 10), dir, "lm-63-2f");
            int[] lv = SixtyThree.levels();
            save(new IsoRender(v, 4).iso(8, lv[57] + 2), dir, "lm-63-58f");
            save(new IsoRender(v, 4).iso(8, lv[59] + 2), dir, "lm-63-60f");
        }
        if (only.isEmpty() || only.contains("lm-bc")) {
            Voxels s = Broadcast.studio(Broadcast.STUDIO_W, Broadcast.STUDIO_D, new Random(8));
            save(new IsoRender(s, 3).iso(8, Integer.MAX_VALUE), dir, "lm-bc-studio");
            save(new IsoRender(s, 3).iso(8, 12), dir, "lm-bc-studio-cut");
            save(new IsoRender(s, 3).iso(8, 8), dir, "lm-bc-studio-2f");
            Voxels t = Broadcast.tower(Broadcast.TOWER_W, Broadcast.TOWER_D, new Random(7));
            save(new IsoRender(t, 3).iso(4, Integer.MAX_VALUE), dir, "lm-bc-tower");
            save(new IsoRender(t, 3).iso(8, 12), dir, "lm-bc-newsroom");
        }
        if (only.isEmpty() || only.contains("lm-ch")) {
            Voxels o = CityHall.oldHall(CityHall.OLD_W, CityHall.OLD_D, new Random(1));
            save(new IsoRender(o, 3).iso(8, Integer.MAX_VALUE), dir, "lm-ch-old");
            save(new IsoRender(o, 3).iso(8, 3), dir, "lm-ch-old-1f");
            save(new IsoRender(o, 3).iso(8, 12), dir, "lm-ch-old-3f");
            Voxels n = CityHall.newHall(CityHall.NEW_W, CityHall.NEW_D, new Random(2));
            save(new IsoRender(n, 3).iso(4, Integer.MAX_VALUE), dir, "lm-ch-new");
            save(new IsoRender(n, 3).iso(6, 5), dir, "lm-ch-new-1f");
            save(new IsoRender(n, 3).iso(6, CityHall.newLevels()[8] + 2), dir, "lm-ch-council");
            Voxels g = CityGarage.build(CityGarage.W, CityGarage.D, new Random(3));
            save(new IsoRender(g, 3).iso(8, Integer.MAX_VALUE), dir, "lm-ch-garage");
            save(new IsoRender(g, 3).iso(8, 3), dir, "lm-ch-garage-cut");
        }
        if (only.isEmpty() || only.contains("lm-st")) {
            Voxels h = SeoulStation.hall(SeoulStation.HALL_W, SeoulStation.HALL_D, 24, new Random(4));
            save(new IsoRender(h, 3).iso(6, Integer.MAX_VALUE), dir, "lm-st-hall");
            save(new IsoRender(h, 3).iso(8, 3), dir, "lm-st-hall-1f");
            save(new IsoRender(h, 3).iso(8, 9), dir, "lm-st-hall-2f");
            Voxels o = SeoulStation.oldStation(SeoulStation.OLD_W, SeoulStation.OLD_D, new Random(5));
            save(new IsoRender(o, 3).iso(8, Integer.MAX_VALUE), dir, "lm-st-old");
            save(new IsoRender(o, 3).iso(8, 3), dir, "lm-st-old-1f");
        }
        if (only.isEmpty() || only.contains("lm-md")) {
            Voxels j = Myeongdong.building(16, 20, new Random(8), Myeongdong.Kind.JEWELRY, 5);
            save(new IsoRender(j, 3).iso(10, Integer.MAX_VALUE), dir, "lm-md-jewelry");
            save(new IsoRender(j, 3).iso(12, 2), dir, "lm-md-jewelry-1f");
            Voxels c = Myeongdong.building(13, 16, new Random(9), Myeongdong.Kind.CAFE, 4);
            save(new IsoRender(c, 3).iso(12, 2), dir, "lm-md-cafe-1f");
            for (int k = 0; k < 3; k++) {
                Voxels b = Myeongdong.building(12 + k * 2, 18, new Random(20 + k), Myeongdong.Kind.SHOP, 5 + k);
                save(new IsoRender(b, 3).iso(10, Integer.MAX_VALUE), dir, "lm-md-shop" + k);
                save(new IsoRender(b, 3).iso(12, 7), dir, "lm-md-shop" + k + "-2f");
            }
        }
    }
}
