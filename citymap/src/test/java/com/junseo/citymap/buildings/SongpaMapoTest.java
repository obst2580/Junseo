package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 송파(테마파크 부지·초고층 타워·잠실 야구장)와 마포(서울월드컵경기장): 지어졌는지, 모든 층·관중석에 걸어서 가는지 */
class SongpaMapoTest {

    static Placement find(String kind) {
        for (Placement p : TestCity.buildings().placements()) {
            if (p.kind.equals(kind)) {
                return p;
            }
        }
        return null;
    }

    @Test
    void themeParkSiteOfficeIsWalkable() {
        Placement p = find("site");
        assertNotNull(p, "테마파크 부지");
        Voxels v = p.voxels();
        WalkCheck walk = new WalkCheck(v).run();
        // 현장 사무실 1층(서는 높이 0)과 2층(4)
        assertTrue(walk.reachedAt(0) > 2000, "부지 안 땅");
        assertTrue(walk.reachedAt(4) > 20, "현장 사무실 2층: " + walk.reachedAt(4));
    }

    @Test
    void renderPreviews() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        // -DpreviewOnly=lm-site:3,7 → 테마파크 부지만, 높이 3·7 에서 자른 그림도
        String spec = System.getProperty("previewOnly", "");
        String[] parts = spec.split(":", -1);
        String only = parts[0];
        String cuts = parts.length > 1 ? parts[1] : "";
        // 세 번째 칸: 부분만 크게 (i0,j0,i1,j1,배율[,y0,y1])
        int[] crop = parts.length > 2 ? java.util.Arrays.stream(parts[2].split(",")).mapToInt(Integer::parseInt).toArray() : null;
        File dir = new File("build/preview");
        dir.mkdirs();
        for (String kind : new String[]{"site", "skyscraper", "ballpark", "stadium"}) {
            if (!only.isEmpty() && !("lm-" + kind).contains(only)) {
                continue;
            }
            Placement p = find(kind);
            if (p == null) {
                continue;
            }
            Voxels v = p.voxels();
            int scale = v.h > 100 ? 2 : 4;
            if (crop != null) {
                int ya = crop.length > 5 ? crop[5] : v.y0, yb = crop.length > 6 ? crop[6] : v.y0 + v.h - 1;
                Voxels c = new Voxels(crop[2] - crop[0] + 1, crop[3] - crop[1] + 1, ya, yb);
                for (int y = ya; y <= yb; y++) {
                    for (int j = crop[1]; j <= crop[3]; j++) {
                        for (int i = crop[0]; i <= crop[2]; i++) {
                            c.set(i - crop[0], y, j - crop[1], v.get(i, y, j));
                        }
                    }
                }
                v = c;
                scale = crop[4];
            }
            ImageIO.write(new IsoRender(v, 2).iso(scale, Integer.MAX_VALUE), "png", new File(dir, "lm-" + kind + ".png"));
            for (String cut : cuts.split(",")) {
                if (!cut.isBlank()) {
                    int y = Integer.parseInt(cut.trim());
                    ImageIO.write(new IsoRender(v, 2).iso(crop != null ? scale : Math.max(scale, 4), y), "png", new File(dir, "lm-" + kind + "-cut" + y + ".png"));
                }
            }
        }
    }
}
