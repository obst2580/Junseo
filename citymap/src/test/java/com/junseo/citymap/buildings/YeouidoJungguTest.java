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
    }
}
