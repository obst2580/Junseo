package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 건물 미리보기 그림을 저장합니다.
 * 실행: ./gradlew :citymap:test --tests '*BuildingPreviewTest' -DmapPreview=true  →  citymap/build/preview/
 * 특정 그림만: -DpreviewOnly=market (이름 일부)
 */
class BuildingPreviewTest {

    @Test
    void renderBuildings() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        CityTerrain terrain = TestCity.terrain();
        long t0 = System.currentTimeMillis();
        CityBuildings b = TestCity.buildings();
        System.out.println("건물 " + b.placements().size() + "채 배치 " + (System.currentTimeMillis() - t0) + "ms");
        File dir = new File("build/preview");
        dir.mkdirs();
        int g = terrain.groundY();
        String only = System.getProperty("previewOnly", "");

        for (View v : VIEWS) {
            if (!only.isEmpty() && !v.name.contains(only)) {
                continue;
            }
            long s = System.currentTimeMillis();
            IsoRender r = new IsoRender(terrain, b, v.x0, v.z0, v.x1, v.z1, g - 10, g + v.height);
            BufferedImage img = v.top ? r.top(v.scale) : r.iso(v.scale, v.cut == 0 ? Integer.MAX_VALUE : g + v.cut);
            ImageIO.write(img, "png", new File(dir, v.name + ".png"));
            System.out.println(v.name + " " + (System.currentTimeMillis() - s) + "ms");
        }
    }

    record View(String name, int x0, int z0, int x1, int z1, int height, int scale, boolean top, int cut) {
    }

    static final View[] VIEWS = {
            new View("bld-guincheon-top", -880, -30, -400, 700, 40, 1, true, 0),
            new View("bld-guincheon-iso", -700, 230, -440, 470, 45, 2, false, 0),
            new View("bld-market-top", -400, 340, -40, 640, 40, 2, true, 0),
            new View("bld-market-iso", -280, 360, -140, 510, 40, 4, false, 0),
            new View("bld-market-inside", -280, 360, -140, 510, 40, 4, false, 7),
            new View("bld-market-stalls", -245, 400, -180, 470, 30, 9, false, 5),
            new View("bld-market-hall", -268, 360, -156, 478, 45, 5, false, 24),
            new View("bld-market-2f", -268, 360, -156, 478, 45, 5, false, 11),
            new View("bld-market-1f", -268, 360, -156, 478, 45, 5, false, 5),
            new View("bld-cheongna-iso", -890, 380, -750, 560, 60, 4, false, 0),
            new View("bld-cheongna-inside", -890, 380, -750, 560, 60, 4, false, 13),
            new View("bld-airport-top", -1800, 340, -1100, 720, 80, 1, true, 0),
            new View("bld-airport-iso", -1720, 360, -1260, 640, 80, 2, false, 0),
            new View("bld-airport-terminal", -1560, 430, -1340, 590, 50, 4, false, 0),
            new View("bld-airport-inside", -1560, 430, -1340, 590, 50, 4, false, 6),
            new View("bld-airport-statues", -1320, 360, -1120, 560, 70, 3, false, 0),
    };
}
