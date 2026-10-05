package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 동네 쌈지공원·어린이 놀이터 (kind "park"). 블록 안 자투리 땅에 둡니다.
 * 낮은 회양목 울타리, 흙길(정면 입구에서 안으로), 나무와 벤치, 넓으면 정자나 놀이터(고무 바닥, 미끄럼틀,
 * 그네, 정글짐)와 운동기구. 정면(j = d-1)이 골목·길을 봅니다.
 */
final class PocketPark {
    private static final Block HEDGE = Block.of("oak_leaves[distance=7,persistent=true,waterlogged=false]", 0x4F7F2A);
    private static final Block PATH = Block.of("dirt_path", 0x94783F);
    private static final Block LOG = Block.of("oak_log[axis=y]", 0x6B5434);
    private static final Block LEAVES = Block.of("oak_leaves[distance=7,persistent=true,waterlogged=false]", 0x4F7F2A);
    private static final Block POST = Block.of("dark_oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]", 0x422B14);
    private static final Block ROOF = Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637);
    private static final Block DECK = Block.of("spruce_planks", 0x725431);
    private static final Block RUBBER = Block.of("green_terracotta", 0x4C532A);
    private static final Block RUBBER2 = Block.of("orange_terracotta", 0xA15325);
    private static final Block STEEL = Block.of("yellow_concrete", 0xF0AF15);
    private static final Block CHAIN = Block.of("iron_chain[axis=y,waterlogged=false]", 0x3B3F49);
    private static final Block SEAT = Block.of("oak_slab[type=bottom,waterlogged=false]", 0xA2834F);

    static Voxels build(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 7);
        // 울타리 (정면 가운데 입구 3칸, 넓으면 뒤에도)
        for (int i = 0; i < w; i++) {
            v.set(i, 0, 0, HEDGE);
            v.set(i, 0, d - 1, HEDGE);
        }
        for (int j = 0; j < d; j++) {
            v.set(0, 0, j, HEDGE);
            v.set(w - 1, 0, j, HEDGE);
        }
        int mid = w / 2;
        for (int i = mid - 1; i <= mid + 1; i++) {
            v.set(i, 0, d - 1, AIR);
            v.set(i, -1, d - 1, PATH);
            if (d >= 16) {
                v.set(i, 0, 0, AIR);
                v.set(i, -1, 0, PATH);
            }
        }
        // 흙길: 입구에서 안으로, 가운데에서 좌우로
        int cj = d / 2;
        v.fill(mid - 1, -1, 1, mid + 1, -1, d - 2, PATH);
        if (w >= 12) {
            v.fill(1, -1, cj - 1, w - 2, -1, cj + 1, PATH);
        }
        boolean big = w >= 14 && d >= 14;
        boolean playground = big ? r.nextBoolean() : w >= 11 && d >= 11 && r.nextInt(3) == 0;
        if (big && !playground) {
            pavilion(v, mid - 2, cj - 2);
        } else if (playground) {
            playground(v, w, d, mid, cj, r);
        }
        // 벤치 (길가, 길을 봄)
        for (int j = 3; j < d - 3; j += 5) {
            bench(v, mid - 2, j, "east");
            bench(v, mid + 2, j, "west");
        }
        // 나무: 네 귀퉁이 근처, 넓으면 둘레를 따라
        int[][] spots = {{2, 2}, {w - 3, 2}, {2, d - 3}, {w - 3, d - 3}};
        for (int[] s : spots) {
            tree(v, s[0], s[1], r);
        }
        for (int i = 6; i < w - 6; i += 6) {
            tree(v, i, 2, r);
        }
        // 운동기구 (철봉·허리 돌리기): 넓을 때 한쪽 귀퉁이
        if (w >= 14 && d >= 12) {
            int gi = w - 5, gj = d - 6;
            v.fill(gi, 0, gj, gi, 2, gj, StreetPlan.POST);
            v.fill(gi + 2, 0, gj, gi + 2, 2, gj, StreetPlan.POST);
            v.set(gi + 1, 2, gj, IRON_BARS);
            v.set(gi + 1, -1, gj + 2, RUBBER);
            v.set(gi + 1, 0, gj + 2, StreetPlan.POST);
        }
        // 가로등
        v.fill(1, 0, d - 2, 1, 3, d - 2, StreetPlan.POST);
        v.set(1, 4, d - 2, LANTERN);
        return v;
    }

    /** 5×5 정자: 나무 마루, 기둥 넷, 기와 지붕 */
    private static void pavilion(Voxels v, int i0, int j0) {
        v.fill(i0, -1, j0, i0 + 4, -1, j0 + 4, DECK);
        v.fill(i0, 0, j0, i0 + 4, 0, j0 + 4, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725431));
        for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
            v.fill(i0 + c[0], 1, j0 + c[1], i0 + c[0], 3, j0 + c[1], LOG);
        }
        v.fill(i0 - 1, 4, j0 - 1, i0 + 5, 4, j0 + 5, ROOF);
        v.fill(i0, 4, j0, i0 + 4, 4, j0 + 4, Block.of("deepslate_tiles", 0x363637));
        v.fill(i0 + 1, 5, j0 + 1, i0 + 3, 5, j0 + 3, ROOF);
        v.set(i0 + 2, 3, j0 + 2, LANTERN_HANGING);
    }

    /** 놀이터: 고무 바닥, 미끄럼틀 달린 놀이대, 그네 */
    private static void playground(Voxels v, int w, int d, int mid, int cj, Random r) {
        int i0 = 2, j0 = 2, i1 = Math.min(w - 3, i0 + 9), j1 = Math.min(d - 3, j0 + 7);
        if (i1 >= mid - 2) {
            i1 = mid - 3;
        }
        if (i1 - i0 < 4) {
            i0 = mid + 3;
            i1 = Math.min(w - 3, i0 + 9);
        }
        if (i1 - i0 < 4 || j1 - j0 < 4) {
            return;
        }
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                v.set(i, -1, j, (i + j) % 7 == 0 ? RUBBER2 : RUBBER);
            }
        }
        // 놀이대: 2×2 기둥 위 판, 계단 오르기, 반대편 미끄럼틀
        int pi = i0 + 1, pj = j0 + 1;
        for (int[] c : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
            v.set(pi + c[0], 0, pj + c[1], STEEL);
        }
        v.fill(pi, 1, pj, pi + 1, 1, pj + 1, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xEBE5DE));
        v.set(pi, 0, pj + 2, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725431));
        v.set(pi + 2, 0, pj, Block.of("smooth_quartz_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
        v.set(pi + 3, -1, pj, RUBBER2);
        // 그네: 기둥 둘, 위 가로대, 사슬, 앉는 판
        int si = pi + 4;
        if (si + 3 <= i1) {
            v.fill(si, 0, j1 - 1, si, 3, j1 - 1, STEEL);
            v.fill(si + 3, 0, j1 - 1, si + 3, 3, j1 - 1, STEEL);
            v.fill(si + 1, 3, j1 - 1, si + 2, 3, j1 - 1, STEEL);
            for (int k = 1; k <= 2; k++) {
                v.set(si + k, 2, j1 - 1, CHAIN);
                v.set(si + k, 1, j1 - 1, CHAIN);
                v.set(si + k, 0, j1 - 1, SEAT);
            }
        }
    }

    private static void bench(Voxels v, int i, int j, String facing) {
        if (v.get(i, 0, j) == null && v.get(i, -1, j) == null) {
            v.set(i, 0, j, Block.of("oak_stairs[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]", 0xA2834F));
        }
    }

    private static void tree(Voxels v, int i, int j, Random r) {
        if (v.get(i, -1, j) != null || v.get(i, 0, j) != null) {
            return;
        }
        int h = 3 + r.nextInt(2);
        v.fill(i, 0, j, i, h - 1, j, LOG);
        for (int y = h - 1; y <= h + 1; y++) {
            int rad = y == h + 1 ? 1 : 2;
            for (int a = -rad; a <= rad; a++) {
                for (int b = -rad; b <= rad; b++) {
                    if (Math.abs(a) + Math.abs(b) <= rad + 1 && !(a == 0 && b == 0 && y < h)) {
                        v.setIfEmpty(i + a, y, j + b, LEAVES);
                    }
                }
            }
        }
    }

    private PocketPark() {
    }
}
