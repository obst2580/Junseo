package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 학교: 본관 모든 층을 계단으로, 체육관 안, 학교 터가 땅 안에 겹치지 않게 */
class SchoolTest {

    @Test
    void mainHallHasStairsToEveryFloor() {
        for (int floors : new int[]{4, 5}) {
            for (int len : new int[]{50, 63, 80, 104}) {
                Voxels v = School.mainHall(len, School.HALL_D, floors, "준서초등학교", "초등학교", new Random(len + floors));
                int[] levels = Floors.levels(Floors.OFFICE, Floors.OFFICE, floors);
                WalkCheck walk = new WalkCheck(v).run();
                for (int k = 0; k < levels.length; k++) {
                    int n = walk.reachedAt(levels[k]);
                    assertTrue(n > 60, "본관 " + len + "칸 " + floors + "층: " + (k + 1) + "층에 걸어서 못 가요 (" + n + ")");
                }
            }
        }
    }

    @Test
    void gymIsWalkable() {
        Voxels v = School.gym(School.GYM_W, School.GYM_D, "준서중학교", new Random(1));
        WalkCheck walk = new WalkCheck(v).run();
        assertTrue(walk.reachedAt(0) > 250, "체육관 안 " + walk.reachedAt(0));
    }

    @Test
    void schoolOnSyntheticBlocks() {
        for (int[] size : new int[][]{{100, 84}, {130, 110}, {96, 160}}) {
            java.util.function.BiPredicate<Integer, Integer> land = (x, z) -> x >= 0 && z >= 0 && x < size[0] && z < size[1];
            BuildMask m = ComplexTestKit.mask(-6, -6, size[0] + 12, size[1] + 12, 2, land);
            BuildMask before = ComplexTestKit.mask(-6, -6, size[0] + 12, size[1] + 12, 2, land);
            int[] block = m.blocks().get(0);
            List<Placement> ps = School.plan(m, block, "songpa", "초등학교", "잠실초등학교", new Random(size[0]));
            assertEquals(3, ps.size(), size[0] + "×" + size[1]);
            assertTrue(ps.stream().allMatch(p -> p.kind.equals("school")));
            ComplexTestKit.assertSound("학교 " + size[0] + "×" + size[1], ps, before, null);
            assertTrue(!ps.get(2).voxels().carSpots().isEmpty(), "교직원 주차장");
        }
        // 작은 땅: 빈 목록
        BuildMask small = ComplexTestKit.mask(-6, -6, 92, 72, 2, (x, z) -> x >= 0 && z >= 0 && x < 80 && z < 60);
        assertTrue(School.plan(small, small.blocks().get(0), "mapo", "중학교", "공덕중학교", new Random(1)).isEmpty());
    }

    @Test
    void schoolOnRealCityBlocks() {
        var t = TestCity.terrain();
        int made = 0;
        for (String d : new String[]{"songpa", "mapo", "gangnam", "yeouido"}) {
            BuildMask m = ComplexTestKit.districtMask(d);
            BuildMask before = ComplexTestKit.districtMask(d);
            for (int[] block : m.blocks().stream().filter(b -> b[4] > 6000).sorted(Comparator.comparingInt((int[] b) -> -b[4])).limit(2).toList()) {
                List<Placement> ps = School.plan(m, block, d, "고등학교", "준서고등학교", new Random(block[0]));
                if (!ps.isEmpty()) {
                    made++;
                    ComplexTestKit.assertSound("실제 학교 " + d, ps, before, t);
                }
            }
        }
        assertTrue(made >= 3, "실제 도시에 학교가 들어간 덩어리 " + made);
    }

    @Test
    void renderSchool() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        File dir = new File("build/preview");
        dir.mkdirs();
        BuildMask m = ComplexTestKit.districtMask("songpa");
        int[] block = m.blocks().stream().filter(b -> b[4] > 6000).max(Comparator.comparingInt(b -> b[4])).orElseThrow();
        List<Placement> ps = School.plan(m, block, "songpa", "초등학교", "잠실초등학교", new Random(3));
        assertEquals(3, ps.size());
        double[] b = ps.get(2).bounds();
        Voxels v = ComplexTestKit.composite(ps, (int) b[0] - 3, (int) b[1] - 3, (int) b[2] + 3, (int) b[3] + 3, -3, 30);
        ImageIO.write(new IsoRender(v, 2).iso(4, Integer.MAX_VALUE), "png", new File(dir, "dz-school.png"));
        ImageIO.write(new IsoRender(v, 2).top(4), "png", new File(dir, "dz-school-top.png"));
        Voxels hall = School.mainHall(70, School.HALL_D, 4, "잠실초등학교", "초등학교", new Random(1));
        ApartmentComplexTest.png(hall, 3, 8, Integer.MAX_VALUE, "dz-school-hall");
        ApartmentComplexTest.png(hall, 3, 10, 2, "dz-school-hall-cut1f");
        ApartmentComplexTest.png(hall, 3, 10, 6, "dz-school-hall-cut2f");
        Voxels gym = School.gym(School.GYM_W, School.GYM_D, "잠실초등학교", new Random(1));
        ApartmentComplexTest.png(gym, 3, 12, Integer.MAX_VALUE, "dz-school-gym");
        ApartmentComplexTest.png(gym, 3, 12, 4, "dz-school-gym-cut");
    }
}
