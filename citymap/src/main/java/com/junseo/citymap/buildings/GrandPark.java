package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 대공원 (서울어린이대공원처럼): 정문과 정문 광장, 넓은 포장길, 분수 광장, 연못과 정자, 잔디광장, 꽃밭,
 * 놀이터(미끄럼틀·그네·정글짐·시소, 모래밭), 작은 동물마을(울타리 우리와 쉼터, 물통·건초), 벤치와 보행등,
 * 매점, 화장실 건물. 땅은 {@link SnuCampus#grounds} 로 깔고 시설은 여기서 얹습니다.
 */
final class GrandPark {
    static final String NAME = "대공원";
    private static final Block POST = Block.of("yellow_concrete", 0xF0AF15);
    private static final Block TILE_ROOF = Block.of("deepslate_tiles", 0x363637);

    /** 공원 시설 자리 (땅 상자 기준 i, j) */
    record Layout(int fountainI, int fountainJ, int pondI, int pondJ, int pondRi, int pondRj,
                  int pavilionI, int pavilionJ, int[][] paddocks, int[] playground, int infoI, int infoJ,
                  int[][] flowerBeds) {
    }

    /** 땅과 시설 */
    static Voxels grounds(int w, int d, char[][] map, Ground ground, Random r, Layout lay) {
        Voxels v = SnuCampus.grounds(w, d, map, ground, r);
        fountain(v, lay.fountainI, lay.fountainJ);
        pond(v, map, lay, r);
        pondFountain(v, lay.pondI, lay.pondJ);
        pavilion(v, lay.pavilionI, lay.pavilionJ);
        String[] animals = {"꽃사슴", "조랑말", "양"};
        for (int n = 0; n < lay.paddocks.length; n++) {
            paddock(v, lay.paddocks[n], animals[n % animals.length], r);
        }
        playground(v, lay.playground, r);
        for (int[] bed : lay.flowerBeds) {
            Kit.flowerBed(v, bed[0], bed[1], bed[2], bed[3], -1, r);
        }
        infoBoard(v, lay.infoI, lay.infoJ);
        v.connect();
        return v;
    }

    /** 분수: 둥근 아래 수반(돌 테두리, 물), 가운데 기둥과 위 수반, 물속 조명 */
    private static void fountain(Voxels v, int ci, int cj) {
        for (int j = cj - 6; j <= cj + 6; j++) {
            for (int i = ci - 6; i <= ci + 6; i++) {
                double dd = Math.hypot(i - ci, j - cj);
                if (dd <= 4.5) {
                    v.set(i, -1, j, (i + j) % 5 == 0 ? SEA_LANTERN : POLISHED_ANDESITE);
                    v.set(i, 0, j, Blocks.WATER);
                } else if (dd <= 5.5) {
                    v.set(i, -1, j, STONE_BRICKS);
                    v.set(i, 0, j, Block.of("polished_granite", 0x9A6A59));
                } else if (dd <= 6.5) {
                    v.set(i, -1, j, POLISHED_GRANITE);
                }
            }
        }
        // 가운데 기둥과 위 수반 (3×3 물, 둘레 테)
        v.fill(ci - 1, -1, cj - 1, ci + 1, 1, cj + 1, STONE_BRICKS);
        v.fill(ci - 1, 2, cj - 1, ci + 1, 2, cj + 1, STONE_BRICKS);
        v.fill(ci - 2, 3, cj - 2, ci + 2, 3, cj + 2, Block.of("polished_granite", 0x9A6A59));
        v.fill(ci - 1, 3, cj - 1, ci + 1, 3, cj + 1, Blocks.WATER);
        v.fill(ci, 3, cj, ci, 5, cj, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
        v.set(ci, 6, cj, Block.of("end_rod[facing=up]", 0xE8E2D8));
    }

    /** 연못: 물가 돌 테두리, 연잎, 물가 갈대 */
    private static void pond(Voxels v, char[][] map, Layout lay, Random r) {
        int w = map[0].length, d = map.length;
        for (int j = 1; j < d - 1; j++) {
            for (int i = 1; i < w - 1; i++) {
                if (map[j][i] != SnuCampus.WATER) {
                    continue;
                }
                boolean shore = map[j - 1][i] != SnuCampus.WATER || map[j + 1][i] != SnuCampus.WATER
                        || map[j][i - 1] != SnuCampus.WATER || map[j][i + 1] != SnuCampus.WATER;
                if (shore) {
                    v.set(i, -1, j, Block.of("mossy_cobblestone", 0x6E7661));
                    if (r.nextInt(4) == 0) {
                        v.set(i, 0, j, Block.of("tall_grass[half=lower]", 0x7CBD6B));
                        v.set(i, 1, j, Block.of("tall_grass[half=upper]", 0x7CBD6B));
                    }
                } else if (r.nextInt(9) == 0) {
                    v.set(i, 0, j, Block.of("lily_pad", 0x208030));
                }
            }
        }
    }

    /** 연못 가운데 분수: 돌 기둥 위 물이 담긴 수반 */
    private static void pondFountain(Voxels v, int ci, int cj) {
        v.set(ci, -1, cj, STONE_BRICKS);
        v.fill(ci, 0, cj, ci, 1, cj, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
        v.fill(ci - 1, 2, cj - 1, ci + 1, 2, cj + 1, STONE_BRICKS);
        v.fill(ci - 2, 3, cj - 2, ci + 2, 3, cj + 2, Block.of("polished_granite", 0x9A6A59));
        v.fill(ci - 1, 3, cj - 1, ci + 1, 3, cj + 1, Blocks.WATER);
        v.set(ci, 3, cj, STONE_BRICKS);
        v.set(ci, 4, cj, Block.of("end_rod[facing=up]", 0xE8E2D8));
    }

    /** 정자: 높인 마루, 네 기둥, 기와 지붕 (가운데가 높은 모임지붕), 오르는 돌계단 */
    private static void pavilion(Voxels v, int ci, int cj) {
        Block log = Block.of("stripped_spruce_log[axis=y]", 0x735A3A);
        v.fill(ci - 3, -1, cj - 3, ci + 3, -1, cj + 3, STONE_BRICKS);
        v.fill(ci - 3, 0, cj - 3, ci + 3, 0, cj + 3, Block.of("spruce_planks", 0x725430));
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            v.fill(ci + c[0], 1, cj + c[1], ci + c[0], 3, cj + c[1], log);
        }
        // 난간 (계단 쪽은 틈)
        for (int t = -2; t <= 2; t++) {
            v.set(ci + t, 1, cj - 3, Block.of("spruce_fence", 0x725430));
            v.set(ci - 3, 1, cj + t, Block.of("spruce_fence", 0x725430));
            v.set(ci + 3, 1, cj + t, Block.of("spruce_fence", 0x725430));
            if (Math.abs(t) == 2) {
                v.set(ci + t, 1, cj + 3, Block.of("spruce_fence", 0x725430));
            }
        }
        v.fill(ci - 1, 0, cj + 4, ci + 1, 0, cj + 4, Blocks.stairs("stone_brick", "north", 0x7A7979));
        // 지붕: 처마 단 3, 가운데 용마루
        for (int s = 0; s < 3; s++) {
            int r = 4 - s, y = 4 + s;
            for (int j = cj - r; j <= cj + r; j++) {
                for (int i = ci - r; i <= ci + r; i++) {
                    boolean edge = Math.abs(i - ci) == r || Math.abs(j - cj) == r;
                    if (!edge) {
                        continue;
                    }
                    String facing = j == cj - r ? "south" : j == cj + r ? "north" : i == ci - r ? "east" : "west";
                    v.set(i, y, j, Blocks.stairs("deepslate_tile", facing, 0x363637));
                }
            }
        }
        v.fill(ci - 1, 7, cj - 1, ci + 1, 7, cj + 1, TILE_ROOF);
        v.set(ci, 8, cj, Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637));
        // 처마 밑 단청 띠 (초록)
        for (int t = -3; t <= 3; t++) {
            for (int[] e : new int[][]{{t, -3}, {t, 3}, {-3, t}, {3, t}}) {
                v.set(ci + e[0], 3, cj + e[1], v.get(ci + e[0], 3, cj + e[1]) == null ? Block.of("green_terracotta", 0x4C532A) : log);
            }
        }
        v.set(ci, 3, cj, LANTERN_HANGING);
    }

    /** 동물 우리: 돌 밑단과 쇠창살 울타리, 사육사 문, 쉼터(나무 헛간), 물통·먹이통·건초, 나무 한 그루, 안내 표지 */
    private static void paddock(Voxels v, int[] p, String animal, Random r) {
        int i0 = p[0], j0 = p[1], i1 = p[2], j1 = p[3];
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                boolean edge = i == i0 || i == i1 || j == j0 || j == j1;
                if (edge) {
                    v.set(i, -1, j, STONE_BRICKS);
                    v.set(i, 0, j, STONE_BRICKS);
                    v.set(i, 1, j, IRON_BARS);
                    v.set(i, 2, j, IRON_BARS);
                } else if ((i * 7 + j * 3) % 11 == 0) {
                    v.set(i, -1, j, COARSE_DIRT);
                }
            }
        }
        // 사육사 문 (뒤쪽)
        v.set(i1 - 2, 0, j0, Block.of("spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]", 0x725430));
        v.set(i1 - 2, 1, j0, AIR);
        v.set(i1 - 2, 2, j0, AIR);
        v.set(i1 - 2, -1, j0, COARSE_DIRT);
        // 쉼터: 뒤쪽 구석 나무 헛간 (앞이 트임)
        int si = i0 + 2, sj = j0 + 2;
        Block plank = Block.of("spruce_planks", 0x725430);
        v.fill(si, 0, sj, si + 4, 2, sj, plank);
        v.fill(si, 0, sj, si, 2, sj + 3, plank);
        v.fill(si + 4, 0, sj, si + 4, 2, sj + 3, plank);
        v.fill(si - 1, 3, sj - 1, si + 5, 3, sj + 4, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        v.fill(si + 1, 0, sj + 1, si + 3, 0, sj + 1, Block.of("hay_block[axis=x]", 0xA68B0C));
        // 물통·먹이통
        v.set(i0 + 2, 0, j1 - 2, Block.of("water_cauldron[level=3]", 0x3F76E4));
        v.set(i0 + 3, 0, j1 - 2, Block.of("composter[level=0]", 0x7A5530));
        v.set(i0 + 4, 0, j1 - 2, Block.of("hay_block[axis=y]", 0xA68B0C));
        // 나무와 바위
        if (i1 - i0 >= 10) {
            Kit.zelkova(v, i1 - 3, (j0 + j1) / 2, -1, r);
        }
        v.set(i0 + 6, 0, j1 - 3, Block.of("mossy_cobblestone", 0x6E7661));
        v.set(i0 + 7, 0, j1 - 3, Block.of("cobblestone", 0x7F7F7F));
        // 안내 표지 (앞 울타리 바깥, 길 쪽)
        int mi = (i0 + i1) / 2;
        v.set(mi, 0, j1 + 1, Block.of("stone_bricks", 0x7A7979));
        v.set(mi, 1, j1 + 1, Block.of("stone_brick_slab[type=bottom,waterlogged=false]", 0x7A7979));
        v.set(mi, 0, j1 + 2, Blocks.wallSign("spruce", "south", "black", false, "동물마을", animal + " 우리", "먹이를 주지", "마세요"));
    }

    /** 놀이터: 모래밭 위 미끄럼틀 탑, 그네, 정글짐, 시소, 둘레 벤치 */
    private static void playground(Voxels v, int[] p, Random r) {
        int i0 = p[0], j0 = p[1], i1 = p[2], j1 = p[3];
        // 둘레 낮은 테두리
        for (int i = i0; i <= i1; i++) {
            v.set(i, -1, j0, POLISHED_ANDESITE);
            v.set(i, -1, j1, POLISHED_ANDESITE);
        }
        for (int j = j0; j <= j1; j++) {
            v.set(i0, -1, j, POLISHED_ANDESITE);
            v.set(i1, -1, j, POLISHED_ANDESITE);
        }
        // 미끄럼틀 탑: 노란 기둥, 나무 마루(높이 2), 빨간 지붕, 계단, 미끄럼판
        int ti = i0 + 3, tj = j0 + 3;
        for (int[] c : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
            v.fill(ti + c[0], 0, tj + c[1], ti + c[0], 4, tj + c[1], POST);
        }
        v.fill(ti, 2, tj, ti + 2, 2, tj + 2, Block.of("oak_planks", 0xA2834F));
        v.fill(ti, 5, tj, ti + 2, 5, tj + 2, RED_CONCRETE);
        // 오르는 계단 (남쪽에서 북쪽으로 세 단)
        for (int t = 0; t < 3; t++) {
            int j = tj + 5 - t;
            v.set(ti + 1, t, j, Blocks.stairs("oak", "north", 0xA2834F));
            if (t > 0) {
                v.fill(ti + 1, 0, j, ti + 1, t - 1, j, Block.of("oak_planks", 0xA2834F));
            }
        }
        // 미끄럼판: 마루 동쪽에서 반 칸씩 내려감 (흰 판, 양옆 낮은 난간)
        String[] steps = {"bottom", "top", "bottom", "top", "bottom"};
        int[] ys = {2, 1, 1, 0, 0};
        for (int s = 0; s < steps.length; s++) {
            int i = ti + 3 + s;
            v.set(i, ys[s], tj + 1, Block.of("smooth_quartz_slab[type=" + steps[s] + ",waterlogged=false]", 0xECE6DF));
        }
        // 그네: 양 끝 기둥, 위 가로대, 쇠사슬과 앉는 판 두 개
        int gi = i1 - 9, gj = j0 + 3;
        if (gi > ti + 8) {
            v.fill(gi, 0, gj, gi, 3, gj, POST);
            v.fill(gi + 6, 0, gj, gi + 6, 3, gj, POST);
            v.fill(gi, 4, gj, gi + 6, 4, gj, Block.of("stripped_oak_log[axis=x]", 0xB18F57));
            for (int s : new int[]{gi + 2, gi + 4}) {
                v.fill(s, 2, gj, s, 3, gj, Kit.CHAIN);
                v.set(s, 1, gj, Block.of("oak_slab[type=top,waterlogged=false]", 0xA2834F));
            }
        }
        // 정글짐: 쇠창살 격자 4×4×3
        int ji = i0 + 3, jj = j1 - 6;
        for (int y = 0; y <= 2; y++) {
            for (int a = 0; a <= 3; a++) {
                for (int b = 0; b <= 3; b++) {
                    boolean frame = (a == 0 || a == 3 || b == 0 || b == 3) && (y == 2 || (a % 3 == 0 && b % 3 == 0));
                    if (frame || (y == 2 && (a + b) % 2 == 0)) {
                        v.set(ji + a, y, jj + b, Block.of("iron_bars", 0x888888));
                    }
                }
            }
        }
        // 시소
        int si = i1 - 8, sj = j1 - 4;
        if (si > ji + 6) {
            v.set(si + 2, 0, sj, Block.of("oak_fence", 0xA2834F));
            v.fill(si, 1, sj, si + 4, 1, sj, Block.of("oak_slab[type=bottom,waterlogged=false]", 0xA2834F));
        }
        // 둘레 벤치 (보호자)
        Kit.bench(v, i0 + 2, 0, j1 + 1, 3, true, "north");
        Kit.bench(v, i1 - 4, 0, j1 + 1, 3, true, "north");
    }

    /** 정문 안쪽 공원 안내도 */
    private static void infoBoard(Voxels v, int i, int j) {
        v.fill(i, 0, j - 1, i, 2, j + 1, Block.of("dark_oak_planks", 0x432B14));
        v.fill(i, 3, j - 1, i, 3, j + 1, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        v.set(i - 1, 2, j, Blocks.wallSign("birch", "west", "black", false, "대공원 안내도", "분수광장 · 연못", "동물마을 · 놀이터", "매점 · 화장실"));
        v.set(i - 1, 1, j, Blocks.wallSign("birch", "west", "black", false, "개장 05:00~22:00", "입장료 없음", "반려동물은", "목줄 필수"));
    }

    // ------------------------------------------------------------------ 따로 놓는 건물

    /** 정문 (정면 너비 23, 깊이 6): 양쪽 안내소·관리사무소, 그 사이 큰 차양과 둥근 기둥, 차양 앞 이름 */
    static Voxels gate(int w, int d) {
        Voxels v = new Voxels(w, d, -1, 9);
        v.fill(0, -1, 0, w - 1, -1, d - 1, POLISHED_GRANITE);
        for (int i = 5; i < w - 5; i++) {
            for (int j = 0; j < d; j++) {
                if ((i + j) % 3 == 0) {
                    v.set(i, -1, j, POLISHED_ANDESITE);
                }
            }
        }
        Block wall = Block.of("white_concrete", 0xCFD5D6);
        String[] names = {"안내소", "관리사무소"};
        for (int n = 0; n < 2; n++) {
            int a0 = n == 0 ? 0 : w - 5, a1 = a0 + 4;
            v.fill(a0, 0, 0, a1, 3, d - 2, wall);
            v.fill(a0 + 1, 0, 1, a1 - 1, 3, d - 3, AIR);
            v.fill(a0, 4, 0, a1, 4, d - 2, Block.of("polished_andesite", 0x848685));
            // 창 (앞)
            v.fill(a0 + 1, 1, d - 2, a1 - 1, 2, d - 2, Block.of("glass_pane", 0xC8DCE4));
            // 문 (통로 쪽)
            Frame f = Frame.facing(v, n == 0 ? a1 : a0, 2, n == 0 ? "east" : "west");
            Interior.door(f, 0, 0, 0, "spruce", "south");
            v.set(n == 0 ? a1 + 1 : a0 - 1, 2, 3, Blocks.wallSign("birch", n == 0 ? "east" : "west", "black", false, "", names[n]));
            // 안: 책상과 의자
            Frame in = Frame.of(v);
            Furniture.desk(in, a0 + 2, 0, d - 3, "north");
            v.set(a0 + 2, 2, 2, Interior.LIGHT);
        }
        // 큰 차양 (높이 6) 과 기둥
        v.fill(0, 6, 0, w - 1, 6, d - 2, wall);
        v.fill(0, 7, d - 2, w - 1, 7, d - 2, Block.of("gray_concrete", 0x36393D));
        for (int i : new int[]{8, w - 9}) {
            v.fill(i, 0, 2, i, 5, 2, Block.of("quartz_pillar[axis=y]", 0xEBE6E0));
        }
        for (int i = 3; i < w - 3; i += 4) {
            v.set(i, 6, 2, Interior.LIGHT);
        }
        for (int a : new int[]{0, w - 1}) {
            v.fill(a, 4, 0, a, 5, d - 2, wall);
        }
        // 이름 (차양 앞 띠에 빛나는 글씨)
        int mid = w / 2;
        v.set(mid, 7, d - 1, Blocks.wallSign("dark_oak", "south", "white", true, "", NAME));
        v.set(mid - 2, 7, d - 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "정문"));
        v.set(mid + 2, 7, d - 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "GRAND PARK"));
        v.set(mid, 7, 0, null);
        v.connect();
        return v;
    }

    /** 공원 화장실 건물 (너비 11, 깊이 9): 남녀 칸, 앞 처마, 벽돌 벽 */
    static Voxels restroom(int w, int d) {
        Voxels v = new Voxels(w, d, -1, 5);
        v.fill(0, -1, 0, w - 1, -1, d - 1, POLISHED_ANDESITE);
        Frame f = Frame.of(v);
        int half = w / 2;
        Interior.restroom(f, 1, 1, half - 1, d - 3, 0, 4, "남자 화장실", 2);
        Interior.restroom(f, half + 1, 1, w - 2, d - 3, 0, 4, "여자 화장실", w - 3);
        // 바깥 벽 벽돌 (문과 표지판은 그대로)
        for (int y = 0; y <= 2; y++) {
            for (int i = 0; i < w; i++) {
                for (int j : new int[]{0, d - 2}) {
                    Block b = v.get(i, y, j);
                    if (b != null && b.id().equals("minecraft:white_terracotta")) {
                        v.set(i, y, j, BRICKS);
                    }
                }
            }
            for (int j = 0; j < d - 1; j++) {
                for (int i : new int[]{0, w - 1}) {
                    v.set(i, y, j, BRICKS);
                }
            }
        }
        for (int i = 2; i < w - 2; i += 3) {
            v.set(i, 2, 0, Block.of("glass_pane", 0xC8DCE4));
        }
        // 지붕과 앞 처마
        v.fill(0, 3, 0, w - 1, 3, d - 1, Block.of("polished_andesite", 0x848685));
        v.fill(0, 4, 0, w - 1, 4, 0, Kit.SLAB);
        v.set(half, 2, d - 1, Blocks.wallSign("birch", "south", "black", false, "", "화장실"));
        v.connect();
        return v;
    }

    /** 매점 (너비 7, 깊이 6): 판매 창과 받침대, 처마, 냉장고·진열대, 옆 출입문 */
    static Voxels kiosk(int w, int d) {
        Voxels v = new Voxels(w, d, -1, 5);
        v.fill(0, -1, 0, w - 1, -1, d - 1, POLISHED_ANDESITE);
        int jf = d - 2;
        Block wall = Block.of("white_concrete", 0xCFD5D6);
        v.fill(0, 0, 0, w - 1, 2, jf, wall);
        v.fill(1, 0, 1, w - 2, 2, jf - 1, AIR);
        v.fill(0, 3, 0, w - 1, 3, jf, Block.of("polished_andesite", 0x848685));
        // 판매 창 (앞): 받침대 위 열린 창
        for (int i = 1; i <= w - 3; i++) {
            v.set(i, 0, jf, Furniture.COUNTER);
            v.set(i, 1, jf, AIR);
            v.set(i, 2, jf, AIR);
        }
        // 처마 (초록 줄무늬 천)
        for (int i = 0; i < w; i++) {
            v.set(i, 3, d - 1, i % 2 == 0 ? Block.of("green_wool", 0x546D1B) : Block.of("white_wool", 0xE9ECEC));
        }
        // 안: 냉장고, 진열대, 출입문 (옆)
        v.fill(1, 0, 1, 1, 1, 1, Furniture.FRIDGE);
        v.fill(2, 0, 1, w - 3, 0, 1, Block.of("white_terracotta", 0xD1B2A1));
        v.fill(2, 1, 1, w - 3, 1, 1, Block.of("barrel[facing=up,open=false]", 0x86643B));
        Frame fd = Frame.facing(v, w - 1, 2, "east");
        Interior.door(fd, 0, 0, 0, "spruce", "south");
        v.set(w / 2, 2, 2, Interior.LIGHT);
        v.set(w / 2, 3, d - 1, Blocks.wallSign("birch", "south", "black", false, "", "매점", "음료·간식"));
        v.connect();
        return v;
    }

    private GrandPark() {
    }
}
