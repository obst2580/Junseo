package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 대학교(서울대학교·대학병원)와 광진(대공원·카지노) 건물: 정문에서 걸어 들어가 계단으로 모든 층까지 */
class LandmarkEastTest {

    static Voxels hospital() {
        return Hospital.build(97, Hospital.depth(), new Random(7));
    }

    @Test
    void hospitalHasStairsToEveryFloor() {
        Voxels v = hospital();
        int[] levels = Floors.levels(Floors.GROUND, Floors.OFFICE, Hospital.FLOORS);
        InteriorTest.assertAllFloorsReachable("대학병원", v, levels);
    }

    /** 캠퍼스 건물 견본: {이름, 쓰임, 바깥, 정면 너비, 층수, 북쪽 줄, 남쪽 줄} */
    static CampusHall.Spec[] campusSpecs() {
        CampusHall.Spec lecture = new CampusHall.Spec(58, 5, CampusHall.Kind.LECTURE, CampusHall.Skin.BRICK);
        lecture.name = "인문관";
        lecture.number = "14동";
        CampusHall.Spec admin = new CampusHall.Spec(42, 5, CampusHall.Kind.ADMIN, CampusHall.Skin.CONCRETE_FINS);
        admin.name = "행정관";
        admin.number = "60동";
        admin.south = 10;
        CampusHall.Spec lib = new CampusHall.Spec(54, 5, CampusHall.Kind.LIBRARY, CampusHall.Skin.GLASS);
        lib.name = "중앙도서관";
        lib.number = "62동";
        lib.north = 16;
        lib.south = 16;
        CampusHall.Spec union = new CampusHall.Spec(48, 4, CampusHall.Kind.UNION, CampusHall.Skin.BRICK);
        union.name = "학생회관";
        union.number = "63동";
        CampusHall.Spec dorm = new CampusHall.Spec(70, 6, CampusHall.Kind.DORM, CampusHall.Skin.BRICK);
        dorm.name = "관악사";
        dorm.number = "900동";
        dorm.north = 6;
        dorm.south = 7;
        dorm.front = 3;
        dorm.groundH = Floors.HOME;
        dorm.typicalH = Floors.HOME;
        CampusHall.Spec lab = new CampusHall.Spec(45, 5, CampusHall.Kind.LAB, CampusHall.Skin.CONCRETE_FINS);
        lab.name = "연구소";
        lab.number = "220동";
        return new CampusHall.Spec[]{lecture, admin, lib, union, dorm, lab};
    }

    /** 도시에 실제로 놓는 크기·층수 그대로 */
    @Test
    void placedCampusBuildingsHaveStairsToEveryFloor() {
        java.util.List<CampusHall.Spec> specs = new java.util.ArrayList<>();
        java.util.List<int[]> sizes = new java.util.ArrayList<>();
        for (UniversityPlan.Hall h : UniversityPlan.halls(0, 0)) {
            specs.add(h.spec());
            sizes.add(new int[]{h.w(), h.d()});
            assertTrue(h.d() == h.spec().depth(), h.spec().name + " 깊이");
        }
        CampusHall.Spec dorm = UniversityPlan.dormSpec("900동");
        specs.add(dorm);
        sizes.add(new int[]{70, dorm.depth()});
        for (int n = 0; n < specs.size(); n++) {
            CampusHall.Spec s = specs.get(n);
            Voxels v = CampusHall.build(s, sizes.get(n)[0], sizes.get(n)[1], new Random(n));
            InteriorTest.assertAllFloorsReachable(s.name, v, Floors.levels(s.groundH, s.typicalH, s.floors));
        }
    }

    @Test
    void campusBuildingsHaveStairsToEveryFloor() {
        for (CampusHall.Spec s : campusSpecs()) {
            Voxels v = CampusHall.build(s, s.w, s.depth(), new Random(3));
            InteriorTest.assertAllFloorsReachable(s.name, v, Floors.levels(s.groundH, s.typicalH, s.floors));
        }
    }

    /** 새 거점 후보 자리 (서울대 정문 광장, 카지노 앞 광장) 의 5×5 가 실제 도시에서 비어 있는지 */
    @Test
    void suggestedHubSpotsStayOpen() {
        CityBuildings b = TestCity.buildings();
        com.junseo.citymap.terrain.CityTerrain t = TestCity.terrain();
        assertTrue(GwangjinPlan.casinoHubX != Integer.MIN_VALUE, "카지노가 지어짐");
        int[][] spots = {{UniversityPlan.GATE_HUB_X, UniversityPlan.GATE_HUB_Z}, {GwangjinPlan.casinoHubX, GwangjinPlan.casinoHubZ}};
        for (int[] s : spots) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int x = s[0] + dx, z = s[1] + dz;
                    java.util.List<Integer> ys = new java.util.ArrayList<>();
                    b.column(x, z, t.column(x, z), (y, blk) -> {
                        if (!blk.isAir()) {
                            ys.add(y - t.groundY() - 1);
                        }
                    });
                    assertTrue(ys.stream().noneMatch(y -> y >= 0 && y < 3), "거점 후보 (" + x + ", " + z + ") 가 막힘: " + ys);
                }
            }
        }
    }

    static Voxels casino() {
        return Casino.build(74, 62, new Random(5));
    }

    @Test
    void casinoHasStairsToEveryFloor() {
        InteriorTest.assertAllFloorsReachable("카지노 호텔", casino(), Floors.levels(Floors.HALL, Floors.OFFICE, Casino.FLOORS));
    }

    @Test
    void parkBuildingsCanBeEntered() {
        InteriorTest.assertAllFloorsReachable("대공원 정문", GrandPark.gate(23, 6), new int[]{0});
        InteriorTest.assertAllFloorsReachable("대공원 화장실", GrandPark.restroom(11, 9), new int[]{0});
        InteriorTest.assertAllFloorsReachable("서울대학교 정문", SnuCampus.gate(), new int[]{0});
        // 화장실 칸 안까지 들어갈 수 있는지
        WalkCheck walk = new WalkCheck(GrandPark.restroom(11, 9)).run();
        assertTrue(walk.reached(2, 0, 5), "남자 화장실 안");
        assertTrue(walk.reached(8, 0, 5), "여자 화장실 안");
        // 매점 안 (옆문)
        WalkCheck k = new WalkCheck(GrandPark.kiosk(7, 6)).run(8, 0, 3);
        assertTrue(k.reached(3, 0, 2), "매점 안");
    }

    /** 건물 일부만 잘라 낸 상자 (y ≤ yTop) */
    static Voxels crop(Voxels v, int i0, int j0, int i1, int j1, int yTop) {
        Voxels c = new Voxels(i1 - i0 + 1, j1 - j0 + 1, v.y0, Math.min(yTop, v.y0 + v.h - 1));
        for (int y = v.y0; y <= Math.min(yTop, v.y0 + v.h - 1); y++) {
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    c.set(i - i0, y, j - j0, v.get(i, y, j));
                }
            }
        }
        return c;
    }

    @Test
    void renderDetails() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        String only = System.getProperty("previewOnly", "");
        if (!only.contains("detail")) {
            return;
        }
        File dir = new File("build/preview");
        dir.mkdirs();
        Voxels h = hospital();
        ImageIO.write(new IsoRender(crop(h, 0, 10, 50, 64, 3), 1).iso(14, Integer.MAX_VALUE), "png", new File(dir, "det-hospital-er-lobby.png"));
        ImageIO.write(new IsoRender(crop(h, 14, 0, 50, 34, 23), 1).iso(14, Integer.MAX_VALUE), "png", new File(dir, "det-hospital-ward.png"));
        ImageIO.write(new IsoRender(crop(h, 30, 20, 70, 45, 11), 1).iso(14, Integer.MAX_VALUE), "png", new File(dir, "det-hospital-or.png"));
        Voxels c = casino();
        ImageIO.write(new IsoRender(crop(c, 0, 20, 45, 61, 5), 1).iso(14, Integer.MAX_VALUE), "png", new File(dir, "det-casino-floor.png"));
        ImageIO.write(new IsoRender(crop(c, 10, 20, 40, 34, 18), 1).iso(16, Integer.MAX_VALUE), "png", new File(dir, "det-casino-rooms.png"));
        for (CampusHall.Spec s : campusSpecs()) {
            Voxels v = CampusHall.build(s, s.w, s.depth(), new Random(3));
            int[] lv = Floors.levels(s.groundH, s.typicalH, s.floors);
            ImageIO.write(new IsoRender(crop(v, 0, 0, Math.min(v.w - 1, 34), v.d - 1, lv[1] + 2), 1).iso(14, Integer.MAX_VALUE), "png",
                    new File(dir, "det-campus-" + s.kind.name().toLowerCase() + ".png"));
        }
    }

    @Test
    void renderSamples() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        File dir = new File("build/preview");
        dir.mkdirs();
        String only = System.getProperty("previewOnly", "");
        if (only.isEmpty() || "lm-hospital".contains(only) || only.contains("hospital")) {
            Voxels v = hospital();
            int[] lv = Floors.levels(Floors.GROUND, Floors.OFFICE, Hospital.FLOORS);
            ImageIO.write(new IsoRender(v, 4).iso(5, Integer.MAX_VALUE), "png", new File(dir, "lm-hospital.png"));
            ImageIO.write(new IsoRender(v, 4).iso(7, lv[0] + 3), "png", new File(dir, "lm-hospital-1f.png"));
            ImageIO.write(new IsoRender(v, 4).iso(7, lv[1] + 2), "png", new File(dir, "lm-hospital-2f.png"));
            ImageIO.write(new IsoRender(v, 4).iso(7, lv[2] + 2), "png", new File(dir, "lm-hospital-3f.png"));
            ImageIO.write(new IsoRender(v, 4).iso(7, lv[3] + 2), "png", new File(dir, "lm-hospital-4f.png"));
            ImageIO.write(new IsoRender(v, 4).iso(7, lv[5] + 2), "png", new File(dir, "lm-hospital-ward.png"));
        }
        if (only.isEmpty() || only.contains("casino")) {
            Voxels v = casino();
            int[] lv = Floors.levels(Floors.HALL, Floors.OFFICE, Casino.FLOORS);
            ImageIO.write(new IsoRender(v, 4).iso(5, Integer.MAX_VALUE), "png", new File(dir, "lm-casino.png"));
            ImageIO.write(new IsoRender(v, 4).iso(8, lv[0] + 4), "png", new File(dir, "lm-casino-1f.png"));
            ImageIO.write(new IsoRender(v, 4).iso(8, lv[1] + 2), "png", new File(dir, "lm-casino-2f.png"));
            ImageIO.write(new IsoRender(v, 4).iso(8, lv[3] + 2), "png", new File(dir, "lm-casino-hotel.png"));
        }
        if (only.isEmpty() || only.contains("parkbld")) {
            ImageIO.write(new IsoRender(GrandPark.gate(23, 6), 3).iso(16, Integer.MAX_VALUE), "png", new File(dir, "lm-parkbld-gate.png"));
            ImageIO.write(new IsoRender(GrandPark.restroom(11, 9), 3).iso(20, 2), "png", new File(dir, "lm-parkbld-restroom.png"));
            ImageIO.write(new IsoRender(GrandPark.kiosk(7, 6), 3).iso(24, Integer.MAX_VALUE), "png", new File(dir, "lm-parkbld-kiosk.png"));
        }
        if (only.isEmpty() || only.contains("gate")) {
            Voxels g = SnuCampus.gate();
            ImageIO.write(new IsoRender(g, 3).iso(14, Integer.MAX_VALUE), "png", new File(dir, "lm-gate.png"));
        }
        if (only.isEmpty() || only.contains("campus")) {
            for (CampusHall.Spec s : campusSpecs()) {
                Voxels v = CampusHall.build(s, s.w, s.depth(), new Random(3));
                int[] lv = Floors.levels(s.groundH, s.typicalH, s.floors);
                String n = "lm-campus-" + s.kind.name().toLowerCase();
                ImageIO.write(new IsoRender(v, 3).iso(8, Integer.MAX_VALUE), "png", new File(dir, n + ".png"));
                ImageIO.write(new IsoRender(v, 3).iso(9, lv[0] + 3), "png", new File(dir, n + "-1f.png"));
                ImageIO.write(new IsoRender(v, 3).iso(9, lv[1] + 2), "png", new File(dir, n + "-2f.png"));
            }
        }
    }
}
