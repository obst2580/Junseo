package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 용산·강남 경찰서, 이태원 거리, 홍대 클럽 거리: 걸어서 모든 층에 가는지와 미리보기 그림 */
class PoliceStreetsTest {

    static Voxels yongsan() {
        return PoliceStation.build(89, 60, new Random(7), PoliceStation.Style.CLASSIC, "용산경찰서", 44);
    }

    static Voxels gangnam() {
        return PoliceStation.build(95, 60, new Random(8), PoliceStation.Style.MODERN, "강남경찰서", 35);
    }

    @Test
    void policeStationsHaveStairsToEveryFloor() {
        InteriorTest.assertAllFloorsReachable("용산경찰서", yongsan(), PoliceStation.levels());
        InteriorTest.assertAllFloorsReachable("강남경찰서", gangnam(), PoliceStation.levels());
    }

    @Test
    void renderPreviews() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        String only = System.getProperty("previewOnly", "");
        File dir = new File("build/preview");
        dir.mkdirs();
        if (only.isEmpty() || "police-yongsan".contains(only) || only.contains("police-yongsan")) {
            police("police-yongsan", yongsan(), dir);
        }
        if (only.isEmpty() || "police-gangnam".contains(only) || only.contains("police-gangnam")) {
            police("police-gangnam", gangnam(), dir);
        }
    }

    private static void police(String n, Voxels v, File dir) throws IOException {
        IsoRender r = new IsoRender(v, 3);
        ImageIO.write(r.iso(6, Integer.MAX_VALUE), "png", new File(dir, n + ".png"));
        int[] lv = PoliceStation.levels();
        int half = v.w / 2;
        for (int k = 0; k < lv.length - 1; k++) {
            int cut = lv[k] + lv[k + 1] - lv[k] - 3;
            ImageIO.write(new IsoRender(crop(v, 0, 10, half + 4, v.d - 18), 1).iso(11, cut), "png", new File(dir, n + "-" + (k + 1) + "f-w.png"));
            ImageIO.write(new IsoRender(crop(v, half - 4, 10, v.w - 1, v.d - 18), 1).iso(11, cut), "png", new File(dir, n + "-" + (k + 1) + "f-e.png"));
        }
    }

    /** 상자 일부만 떼어 낸 것 (가까이 보기용) */
    static Voxels crop(Voxels v, int i0, int j0, int i1, int j1) {
        Voxels c = new Voxels(i1 - i0 + 1, j1 - j0 + 1, v.y0, v.y0 + v.h - 1);
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    c.set(i - i0, y, j - j0, v.get(i, y, j));
                }
            }
        }
        return c;
    }
}
