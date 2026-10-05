package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 아파트 단지: 타워형·판상형 동을 걸어서 모든 층까지, 단지 배치가 땅 덩어리 안에 겹치지 않게 */
class ApartmentComplexTest {
    static final AptUnit.Skin TEST_SKIN = new AptUnit.Skin(Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE,
            Block.of("gray_concrete", 0x36393D), Blocks.GLASS_PANE, Block.of("white_stained_glass_pane", 0xF0F0F0),
            Blocks.POLISHED_GRANITE, Blocks.LIGHT_GRAY_CONCRETE, Block.of("gray_concrete", 0x36393D), Blocks.SMOOTH_STONE);

    /** 모든 층과 옥상에 걸어서 가는지 (first 는 첫 세대 층, 기록용) */
    static void assertFloors(String name, Voxels v, int[] levels, int first) {
        WalkCheck walk = new WalkCheck(v).run();
        for (int k = 0; k < levels.length; k++) {
            int n = walk.reachedAt(levels[k]);
            assertTrue(n > 20, name + ": " + (k + 1) + "층(서는 높이 " + levels[k] + ")에 걸어서 못 가요 (" + n + "칸)");
        }
    }

    @Test
    void buildTimes() {
        for (int n = 0; n < 3; n++) {
            long t0 = System.nanoTime();
            TowerApartment.build(TowerApartment.BOX_W, TowerApartment.BOX_D, 33, TEST_SKIN, 2, 101, "준서", new Random(n));
            long t1 = System.nanoTime();
            SlabApartment.stair(SlabApartment.stairWidth(3), SlabApartment.stairDepth(true), 3, 30, TEST_SKIN, true, 101, "준서", new Random(n));
            long t2 = System.nanoTime();
            System.out.println("만드는 시간: 타워형 33층 " + (t1 - t0) / 1_000_000 + "ms, 계단식 3라인 30층 " + (t2 - t1) / 1_000_000 + "ms");
        }
    }

    @Test
    void towersHaveStairsToEveryFloor() {
        for (int floors : new int[]{8, 25, 33}) {
            Voxels v = TowerApartment.build(TowerApartment.BOX_W, TowerApartment.BOX_D, floors, TEST_SKIN, 2, 101, "준서한빛아파트", new Random(floors));
            assertFloors("타워형 " + floors + "층", v, Floors.levels(Floors.GROUND, Floors.HOME, floors), 1);
        }
    }

    @Test
    void slabsHaveStairsToEveryFloor() {
        for (int lines = 1; lines <= 3; lines++) {
            for (boolean piloti : new boolean[]{false, true}) {
                int floors = 10 + lines * 5;
                Voxels v = SlabApartment.stair(SlabApartment.stairWidth(lines), SlabApartment.stairDepth(piloti), lines, floors,
                        TEST_SKIN, piloti, 101 + lines, "준서한빛아파트", new Random(lines));
                assertFloors("계단식 " + lines + "라인 " + floors + "층" + (piloti ? " 필로티" : ""), v,
                        Floors.levels(piloti ? Floors.GROUND : Floors.HOME, Floors.HOME, floors), piloti ? 1 : 0);
            }
        }
        for (int units : new int[]{5, 9}) {
            int floors = 12 + units % 2;
            Voxels v = SlabApartment.corridor(SlabApartment.corridorWidth(units), SlabApartment.corridorDepth(), units, floors,
                    TEST_SKIN, true, 7, "시범아파트", new Random(units));
            assertFloors("복도식 " + units + "세대 " + floors + "층", v, Floors.levels(Floors.HOME, Floors.HOME, floors), 0);
        }
    }

    /**
     * 사람이 살 수 있는 크기: 84㎡형(새 타워형·판상형) 세대 안쪽 ≥ 10×12, 거실 ≥ 5×6, 주방·식당 ≥ 3×5, 안방 ≥ 4×4,
     * 침실 둘 ≥ 3×4, 욕실 ≥ 2×3, 현관 ≥ 2×2. 59㎡형(옛 복도식)은 세대 ≥ 9×10, 거실 ≥ 4×5, 주방 ≥ 2×4, 침실 둘 ≥ 3×4.
     */
    static void assertLivable(String name, AptUnit.Plan p, boolean big) {
        String at = name + " " + p.w() + "×" + p.d() + " " + p.rooms();
        assertTrue(big ? Math.min(p.w(), p.d()) >= 10 && Math.max(p.w(), p.d()) >= 12
                : Math.min(p.w(), p.d()) >= 9 && Math.max(p.w(), p.d()) >= 10, "세대가 좁아요: " + at);
        assertTrue(p.named("거실").stream().anyMatch(x -> big ? x.atLeast(5, 6) : x.atLeast(4, 5)), "거실: " + at);
        assertTrue(p.named("주방").stream().anyMatch(x -> big ? x.atLeast(3, 5) : x.atLeast(2, 4)), "주방: " + at);
        assertTrue(p.named("현관").stream().anyMatch(x -> x.atLeast(2, 2)), "현관: " + at);
        assertTrue(p.named("욕실").stream().anyMatch(x -> x.atLeast(2, 3)), "욕실: " + at);
        if (big) {
            assertTrue(p.named("안방").stream().anyMatch(x -> x.atLeast(4, 4)), "안방: " + at);
            assertTrue(p.named("침실").stream().filter(x -> x.atLeast(3, 4)).count() >= 2, "침실 둘: " + at);
        } else {
            long beds = p.named("안방").stream().filter(x -> x.atLeast(3, 4)).count() + p.named("침실").stream().filter(x -> x.atLeast(3, 4)).count();
            assertTrue(beds >= 2, "침실 둘: " + at);
        }
    }

    @Test
    void facilitiesAreWalkable() {
        for (int seed = 0; seed < 4; seed++) {
            int w = 20 + seed * 8;
            Voxels shops = ComplexFacilities.shops(w, 14 + seed, new Random(seed));
            WalkCheck walk = new WalkCheck(shops).run();
            assertTrue(walk.reachedAt(0) > 30 && walk.reachedAt(4) > 30 && walk.reachedAt(8) > 30, "상가 " + w + ": 층마다 못 가요");
            Voxels office = ComplexFacilities.management(18 + seed * 4, 12 + seed, "준서", new Random(seed));
            walk = new WalkCheck(office).run();
            assertTrue(walk.reachedAt(0) > 30 && walk.reachedAt(4) > 30 && walk.reachedAt(8) > 10, "관리동: 층마다 못 가요");
            Voxels com = ComplexFacilities.community(24 + seed * 4, 16 + seed, "준서", Blocks.POLISHED_GRANITE, new Random(seed));
            walk = new WalkCheck(com).run();
            assertTrue(walk.reachedAt(0) > 60 && walk.reachedAt(5) > 60 && walk.reachedAt(10) > 10, "커뮤니티센터: 층마다 못 가요");
            // WalkCheck 는 땅 밑을 못 보므로 8칸 올리고 땅(빈 칸)을 돌로 채워서, 앞마당에서 출발
            Voxels ramp = ComplexFacilities.ramp(18 + seed, 30 + seed, new Random(seed));
            Voxels up = lift(ramp, 8);
            walk = new WalkCheck(up).run(up.w / 2 + 1, 8, up.d);
            assertTrue(walk.reachedAt(3) > 40, "지하주차장에 걸어서 못 가요 " + walk.reachedAt(3));
            assertTrue(ramp.carSpots().size() >= 6, "지하주차장 차 자리 " + ramp.carSpots().size());
        }
    }

    @Test
    void unitsAreLivable() {
        TowerApartment t = TowerApartment.make(TowerApartment.BOX_W, TowerApartment.BOX_D, 8, TEST_SKIN, 0, 101, "준서", new Random(1));
        assertTrue(t.plans.size() == 4 * 7, "세대 " + t.plans.size());
        for (AptUnit.Plan p : t.plans) {
            assertLivable("타워형", p, true);
        }
        for (int lines = 1; lines <= 3; lines++) {
            SlabApartment a = SlabApartment.makeStair(SlabApartment.stairWidth(lines), SlabApartment.stairDepth(true), lines, 6, TEST_SKIN, true, 101, "준서", new Random(lines));
            assertTrue(!a.plans.isEmpty());
            for (AptUnit.Plan p : a.plans) {
                assertLivable("계단식", p, true);
            }
        }
        SlabApartment c = SlabApartment.makeCorridor(SlabApartment.corridorWidth(6), SlabApartment.corridorDepth(), 6, 5, TEST_SKIN, true, 3, "시범", new Random(2));
        for (AptUnit.Plan p : c.plans) {
            assertLivable("복도식", p, false);
        }
    }


    // ------------------------------------------------------------------ 단지 배치

    static Map<String, Integer> kinds(List<Placement> ps) {
        Map<String, Integer> n = new TreeMap<>();
        for (Placement p : ps) {
            n.merge(p.kind, 1, Integer::sum);
        }
        return n;
    }

    /** 땅 덩어리 안에 남은 가장 큰 빈 정사각형 한 변 */
    static int emptySquare(BuildMask m, int[] block) {
        int w = block[2] - block[0] + 1, h = block[3] - block[1] + 1, best = 0;
        int[][] dp = new int[h + 1][w + 1];
        for (int j = 1; j <= h; j++) {
            for (int i = 1; i <= w; i++) {
                if (m.free(block[0] + i - 1, block[1] + j - 1)) {
                    dp[j][i] = 1 + Math.min(dp[j - 1][i - 1], Math.min(dp[j - 1][i], dp[j][i - 1]));
                    best = Math.max(best, dp[j][i]);
                }
            }
        }
        return best;
    }

    static List<Placement> synthetic(String district, int w, int h, boolean notch, long seed, BuildMask[] keep) {
        java.util.function.BiPredicate<Integer, Integer> land = (x, z) -> x >= 0 && z >= 0 && x < w && z < h
                && !(notch && x > w * 2 / 3 && z < h / 3);
        BuildMask m = ComplexTestKit.mask(-6, -6, w + 12, h + 12, 2, land);
        BuildMask before = ComplexTestKit.mask(-6, -6, w + 12, h + 12, 2, land);
        int[] block = m.blocks().stream().max(Comparator.comparingInt(b -> b[4])).orElseThrow();
        List<Placement> ps = ApartmentComplex.plan(m, block, district, "준서한빛", new Random(seed));
        if (keep != null) {
            keep[0] = m;
            keep[1] = before;
        }
        assertTrue(ps.stream().anyMatch(p -> p.kind.equals("apartment") && p.name.endsWith("동")), district + " " + w + "×" + h + ": 동이 없어요 " + kinds(ps));
        int empty = emptySquare(m, block);
        assertTrue(empty < 8, district + " " + w + "×" + h + ": 빈 땅이 남았어요 (" + empty + "칸 정사각형)");
        ComplexTestKit.assertSound(district + " " + w + "×" + h, ps, before, null);
        return ps;
    }

    @Test
    void complexFillsSyntheticBlocks() {
        String[] districts = {"songpa", "gangnam", "mapo", "yeouido", "gwangjin", "namsan"};
        int[][] sizes = {{64, 62}, {96, 92}, {140, 110}, {230, 180}};
        int k = 0;
        for (String d : districts) {
            for (int[] size : sizes) {
                k++;
                if ((k + size[0]) % 2 == 0 && size[0] > 100) {
                    continue; // 시간 절약: 큰 덩어리는 구역마다 하나씩
                }
                List<Placement> ps = synthetic(d, size[0], size[1], k % 3 == 0, k, null);
                System.out.println("단지 " + d + " " + size[0] + "×" + size[1] + ": " + kinds(ps));
            }
        }
    }

    @Test
    void complexOnRealCityBlocks() {
        var t = TestCity.terrain();
        for (String d : new String[]{"songpa", "gangnam", "mapo", "yeouido"}) {
            BuildMask m = ComplexTestKit.districtMask(d);
            BuildMask before = ComplexTestKit.districtMask(d);
            List<int[]> blocks = new ArrayList<>(m.blocks());
            blocks.removeIf(b -> b[4] < 3500);
            blocks.sort(Comparator.comparingInt((int[] b) -> -b[4]));
            assertTrue(!blocks.isEmpty(), d + ": 땅 덩어리가 없어요");
            List<Placement> all = new ArrayList<>();
            for (int n = 0; n < Math.min(2, blocks.size()); n++) {
                List<Placement> ps = ApartmentComplex.plan(m, blocks.get(n), d, d.equals("yeouido") ? "시범" : "준서한빛", new Random(n));
                System.out.println("실제 단지 " + d + " " + (blocks.get(n)[2] - blocks.get(n)[0] + 1) + "×" + (blocks.get(n)[3] - blocks.get(n)[1] + 1)
                        + ": " + kinds(ps));
                assertTrue(ps.stream().filter(p -> p.kind.equals("apartment")).count() >= 2, d + ": 동이 모자라요 " + kinds(ps));
                all.addAll(ps);
            }
            ComplexTestKit.assertSound("실제 " + d, all, before, t);
            // 실제로 놓인 동: 계단으로 옥상까지
            for (Placement p : all.stream().filter(p -> p.name.endsWith("동")).limit(3).toList()) {
                Voxels v = p.voxels();
                WalkCheck walk = new WalkCheck(v).run();
                int top = v.y0 + v.h - 1;
                // 도시에 놓이면 아래 3개 층만 살림집으로 남고 그 위는 비움 (Unfurnish.declutter): 3층까지 오르는지 봄
                boolean third = false;
                for (int y = 8; y <= 11; y++) {
                    third |= walk.reachedAt(y) > 10;
                }
                assertTrue(third, p + ": 3층까지 걸어서 못 가요");
                assertTrue(walk.reachedAt(0) > 20, p + ": 1층에 못 들어가요");
            }
        }
    }

    @Test
    void renderComplexes() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        File dir = new File("build/preview");
        dir.mkdirs();
        for (String d : new String[]{"songpa", "gangnam", "mapo", "yeouido"}) {
            BuildMask m = ComplexTestKit.districtMask(d);
            int[] block = m.blocks().stream().max(Comparator.comparingInt(b -> b[4])).orElseThrow();
            List<Placement> ps = ApartmentComplex.plan(m, block, d, d.equals("yeouido") ? "시범" : "준서한빛", new Random(1));
            int x0 = block[0] - 4, z0 = block[1] - 4, x1 = block[2] + 4, z1 = block[3] + 4;
            int top = ps.stream().mapToInt(p -> p.voxels().y0 + p.voxels().h).max().orElse(10);
            Voxels v = ComplexTestKit.composite(ps, x0, z0, x1, z1, -8, top);
            ImageIO.write(new IsoRender(v, 2).iso(2, Integer.MAX_VALUE), "png", new File(dir, "dz-complex-" + d + ".png"));
            ImageIO.write(new IsoRender(v, 2).top(3), "png", new File(dir, "dz-complex-" + d + "-top.png"));
            ImageIO.write(new IsoRender(slice(v, -8, 6), 2).iso(4, Integer.MAX_VALUE), "png", new File(dir, "dz-complex-" + d + "-ground.png"));
        }
    }

    /** dy 칸 올린 상자: 원래 땅속(y < 0)의 빈 칸은 돌로 채움 (땅 밑 길 검사용) */
    static Voxels lift(Voxels v, int dy) {
        Voxels out = new Voxels(v.w, v.d, v.y0 + dy, v.y0 + v.h - 1 + dy);
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    out.set(i, y + dy, j, b == null ? (y < 0 ? Blocks.STONE : null) : b);
                }
            }
        }
        return out;
    }

    /** 아래쪽 y ≤ yMax 만 떼어 낸 상자 (단면 그림을 작게) */
    static Voxels slice(Voxels v, int yMin, int yMax) {
        Voxels out = new Voxels(v.w, v.d, v.y0, yMax);
        for (int y = Math.max(v.y0, yMin); y <= yMax; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b != null) {
                        out.set(i, y, j, b);
                    }
                }
            }
        }
        return out;
    }

    static void png(Voxels v, int margin, int scale, int cut, String name) throws IOException {
        File dir = new File("build/preview");
        dir.mkdirs();
        Voxels s = cut == Integer.MAX_VALUE ? v : slice(v, v.y0, cut);
        ImageIO.write(new IsoRender(s, margin).iso(scale, Integer.MAX_VALUE), "png", new File(dir, name + ".png"));
    }

    @Test
    void renderBuildings() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        Voxels t = TowerApartment.build(TowerApartment.BOX_W, TowerApartment.BOX_D, 12, TEST_SKIN, 2, 101, "준서한빛아파트", new Random(3));
        png(t, 3, 6, Integer.MAX_VALUE, "dz-tower");
        png(t, 3, 14, 10, "dz-tower-cut3f");
        Voxels sl = SlabApartment.stair(SlabApartment.stairWidth(2), SlabApartment.stairDepth(true), 2, 14, TEST_SKIN, true, 102, "준서한빛아파트", new Random(4));
        png(sl, 3, 5, Integer.MAX_VALUE, "dz-slab");
        Voxels c = SlabApartment.corridor(SlabApartment.corridorWidth(6), SlabApartment.corridorDepth(), 6, 12, TEST_SKIN, true, 7, "시범아파트", new Random(6));
        png(c, 3, 5, Integer.MAX_VALUE, "dz-corridor");
        png(ComplexFacilities.shops(32, 16, new Random(2)), 3, 10, Integer.MAX_VALUE, "dz-fac-shops");
        png(ComplexFacilities.playground(20, 18, new Random(2)), 3, 16, Integer.MAX_VALUE, "dz-fac-playground");
        png(ComplexFacilities.park(30, 24, false, new Random(2)), 3, 10, Integer.MAX_VALUE, "dz-fac-park");
    }
}
