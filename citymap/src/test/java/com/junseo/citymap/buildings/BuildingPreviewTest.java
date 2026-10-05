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

    /** 눈높이 그림: 눈 위치, 방향(마인크래프트 yaw: 0 남, 90 서, 180 북, -90 동), 아래로 pitch */
    record Eye(String name, double x, double y, double z, double yaw, double pitch, double fov) {
    }

    static final Eye[] EYES = {
            new Eye("eye-guincheon-street", -548, 2.6, 300, 180, -2, 75),
            new Eye("eye-guincheon-alley", -620, 2.6, 175, -90, 0, 75),
            new Eye("eye-market-front", -238, 2.6, 492, 180, -6, 80),
            new Eye("eye-market-inside", -250, 1.7, 432, -90, -3, 80),
            new Eye("eye-market-street", -150, 2.6, 560, 180, -2, 75),
            new Eye("eye-airport-arrival", -1424, 2.6, 562, 140, -8, 80),
            new Eye("eye-cheongna-dome", -818, 2.6, 410, 0, -12, 80),
            new Eye("eye-fill-gangnam", 845, 2.6, 640, 180, -6, 80),
            new Eye("eye-fill-hongdae", -150, 2.6, -250, 180, -2, 75),
            new Eye("eye-fill-junggu", 380, 2.6, -200, 180, -4, 80),
            new Eye("eye-fill-songpa", 1250, 2.6, 560, 180, -4, 80),
            new Eye("eye-police-gangnam", 716, 2.6, 580, -90, -8, 85),
            new Eye("eye-itaewon", 384, 2.6, -102, 180, 0, 75),
            new Eye("eye-hongdae-club", -85, 2.6, -316, 180, 0, 75),
            new Eye("eye-hongdae-busking", -88, 2.6, -335, -90, 2, 80),
            new Eye("eye-univ-gate", 306, 2.6, 506, 0, -8, 75),
            new Eye("eye-univ-acropolis", 314, 1.7, 572, 20, -6, 80),
            new Eye("eye-univ-hospital", 182, 2.6, 522, -15, -10, 80),
            new Eye("eye-gwangjin-park-gate", 1262, 2.6, -196, -100, -4, 80),
            new Eye("eye-gwangjin-fountain", 1316, 1.7, -196, 160, -4, 80),
            new Eye("eye-gwangjin-casino", 1314, 2.6, -149, 15, -14, 80),
            new Eye("eye-lm-plaza", -237, 1.7, 104, 180, 2, 80),
            new Eye("eye-lm-63", -22, 1.7, 42, 59, -28, 85),
            new Eye("eye-lm-broadcast", 118, 1.7, 112, -110, -8, 85),
            new Eye("eye-lm-cityhall", 553, 1.7, -372, 180, -12, 85),
            new Eye("eye-lm-station", 275, 1.7, -400, -100, -8, 85),
            new Eye("eye-lm-platform", 349, 1.7, -455, 180, -2, 80),
            new Eye("eye-lm-myeongdong", 788, 1.7, -318, 180, -4, 80),
            new Eye("eye-lm-jewelry", 797, 1.7, -325, -90, -6, 85),
            new Eye("eye-worldcup-plaza", -530, 2.6, -285, 20, -8, 80),
            new Eye("eye-worldcup-stands", -549, 15.6, -266, 0, 22, 85),
            new Eye("eye-worldcup-concourse", -560, 1.7, -259, -90, 0, 80),
            new Eye("eye-worldcup-pitch", -549, -5.3, -215, 180, -8, 85),
            new Eye("eye-ballpark-home", 1404, 1.7, 436, -135, 4, 85),
            new Eye("eye-ballpark-field", 1421, -5.3, 419, 45, -8, 85),
            new Eye("eye-ballpark-gate", 1393, 2.6, 452, -140, -10, 80),
            new Eye("eye-tower-mall", 1176, 1.7, 262, -90, 0, 80),
            new Eye("eye-themepark-gate", 1310, 2.6, 258, -20, -4, 80),
            new Eye("eye-tower-office", 1175, 26.7, 276, -45, 10, 85),
    };

    @Test
    void renderEyeLevel() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        CityTerrain terrain = TestCity.terrain();
        CityBuildings b = TestCity.buildings();
        File dir = new File("build/preview");
        dir.mkdirs();
        String only = System.getProperty("previewOnly", "");
        int g = terrain.groundY();
        for (Eye e : EYES) {
            if (!only.isEmpty() && !e.name.contains(only)) {
                continue;
            }
            long s = System.currentTimeMillis();
            int r = 170;
            IsoRender ir = new IsoRender(terrain, b, (int) e.x - r, (int) e.z - r, (int) e.x + r, (int) e.z + r, g - 8, g + 70);
            BufferedImage img = ir.perspective(e.x, g + 1 + e.y, e.z, e.yaw, e.pitch, e.fov, 1280, 720);
            ImageIO.write(img, "png", new File(dir, e.name + ".png"));
            System.out.println(e.name + " " + (System.currentTimeMillis() - s) + "ms");
        }
    }

    static final View[] VIEWS = {
            new View("bld-guincheon-top", -880, -30, -400, 700, 40, 1, true, 0),
            new View("bld-guincheon-iso", -700, 230, -440, 470, 45, 2, false, 0),
            new View("bld-market-top", -400, 340, -40, 640, 40, 2, true, 0),
            new View("bld-market-iso", -280, 360, -140, 510, 40, 4, false, 0),
            new View("bld-market-inside", -280, 360, -140, 510, 40, 4, false, 3),
            new View("bld-market-stalls", -245, 400, -180, 470, 30, 9, false, 3),
            new View("bld-market-hall", -268, 360, -156, 478, 45, 5, false, 18),
            new View("bld-market-2f", -268, 360, -156, 478, 45, 5, false, 7),
            new View("bld-market-3f", -268, 360, -156, 478, 45, 5, false, 11),
            new View("bld-market-1f", -268, 360, -156, 478, 45, 5, false, 3),
            new View("bld-cheongna-iso", -890, 380, -750, 560, 60, 4, false, 0),
            new View("bld-cheongna-inside", -890, 380, -750, 560, 60, 4, false, 13),
            new View("bld-airport-top", -1800, 340, -1100, 720, 80, 1, true, 0),
            new View("bld-airport-iso", -1720, 360, -1260, 640, 80, 2, false, 0),
            new View("bld-airport-terminal", -1560, 430, -1340, 590, 50, 4, false, 0),
            new View("bld-airport-inside", -1560, 430, -1340, 590, 50, 4, false, 6),
            new View("bld-airport-statues", -1320, 360, -1120, 560, 70, 3, false, 0),
            new View("fill-gangnam-top", 400, 260, 1140, 830, 60, 1, true, 0),
            new View("fill-gangnam-iso", 560, 420, 760, 620, 140, 2, false, 0),
            new View("fill-songpa-iso", 1000, 380, 1200, 590, 100, 2, false, 0),
            new View("fill-yeouido-iso", -200, 40, 40, 260, 160, 2, false, 0),
            new View("fill-hongdae-iso", -300, -460, -140, -300, 60, 3, false, 0),
            new View("bld-police-gangnam", 731, 527, 802, 633, 50, 4, false, 0),
            new View("bld-itaewon-iso", 340, -190, 432, -98, 50, 4, false, 0),
            new View("bld-itaewon-2f", 340, -190, 432, -98, 50, 4, false, 6),
            new View("bld-hongdae-club-iso", -116, -404, -54, -306, 40, 4, false, 0),
            new View("bld-hongdae-club-b1", -116, -404, -54, -306, 40, 4, false, -3),
            new View("bld-hongdae-club-1f", -116, -404, -54, -306, 40, 4, false, 2),
            new View("lm-univ-top", -40, 355, 420, 700, 80, 1, true, 0),
            new View("lm-univ-campus-iso", 232, 512, 418, 678, 50, 3, false, 0),
            new View("lm-univ-dorm-iso", 232, 392, 342, 498, 40, 4, false, 0),
            new View("lm-univ-hospital-iso", 104, 508, 222, 616, 90, 3, false, 0),
            new View("lm-gwangjin-top", 1030, -366, 1490, -40, 90, 1, true, 0),
            new View("lm-gwangjin-park-iso", 1262, -268, 1374, -170, 30, 4, false, 0),
            new View("lm-gwangjin-casino-iso", 1262, -150, 1374, -78, 90, 3, false, 0),
            new View("lm-yeouido-top", -380, -60, 250, 310, 60, 2, true, 0),
            new View("lm-yeouido-plaza", -355, -35, -160, 125, 30, 3, false, 0),
            new View("lm-yeouido-63", -140, 35, -40, 130, 260, 2, false, 0),
            new View("lm-yeouido-63base", -140, 35, -40, 130, 20, 5, false, 0),
            new View("lm-yeouido-broadcast", 108, 85, 200, 200, 110, 3, false, 0),
            new View("lm-yeouido-studio", 135, 150, 190, 200, 25, 6, false, 12),
            new View("lm-junggu-top", 170, -580, 1050, -130, 60, 1, true, 0),
            new View("lm-junggu-cityhall", 390, -525, 600, -310, 70, 3, false, 0),
            new View("lm-junggu-station", 262, -515, 372, -310, 50, 3, false, 0),
            new View("lm-junggu-station-plat", 300, -500, 372, -420, 30, 6, false, 0),
            new View("lm-junggu-myeongdong", 755, -400, 862, -310, 50, 4, false, 0),
            new View("bld-songpa-themepark", 1262, 245, 1364, 344, 15, 4, false, 0),
            new View("bld-songpa-tower", 1135, 245, 1241, 344, 320, 2, false, 0),
            new View("bld-songpa-ballpark", 1385, 365, 1490, 456, 45, 4, false, 0),
            new View("bld-songpa-ballpark-inside", 1385, 365, 1490, 456, 45, 4, false, 3),
            new View("bld-mapo-worldcup", -610, -290, -490, -155, 50, 3, false, 0),
            new View("bld-mapo-worldcup-inside", -610, -290, -490, -155, 50, 3, false, 3),
            new View("bld-songpa-top", 950, 85, 1630, 599, 320, 1, true, 0),
    };
}
