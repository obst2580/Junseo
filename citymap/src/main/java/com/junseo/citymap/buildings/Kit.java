package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 대학교·병원·대공원·카지노가 같이 쓰는 도구: 칸막이 벽, 양여닫이 문, 복도 옆 방 줄, 대기 의자,
 * 조경(느티나무·은행나무·소나무·화단), 벤치, 보행등. 차 모형은 두지 않습니다 (진짜 탈것은 따로 만듦).
 * 모두 {@link Frame} 기준 좌표 또는 건물 상자 좌표이고, 높이는 서는 높이(level)입니다.
 */
final class Kit {
    static final Block WHITE_PANE = Block.of("white_stained_glass_pane", 0xF0F0F0);
    static final Block CURTAIN = Block.of("light_blue_stained_glass_pane", 0x6699D8);
    static final Block TOP_SLAB = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);
    static final Block SLAB = Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E);
    static final Block QUARTZ_TOP = Block.of("smooth_quartz_slab[type=top,waterlogged=false]", 0xECE6DF);
    static final Block CHAIN = Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050);
    static final Block MONITOR = Block.of("black_concrete", 0x080A0F);
    static final Block GRASS_TUFT = Block.of("short_grass", 0x7CBD6B);
    static final Block BOX_HEDGE = Block.of("oak_leaves[persistent=true]", 0x4F7F2A);

    /** 칸막이 벽: (i0..i1, j0..j1) 을 바닥 level 부터 천장 아래(level + h - 2)까지 */
    static void wall(Voxels v, int i0, int j0, int i1, int j1, int level, int h, Block b) {
        v.fill(i0, level, j0, i1, level + h - 2, j1, b);
    }

    /** 문 한 짝 (경첩 방향 지정) */
    static Block door(String wood, String facing, boolean upper, boolean right) {
        return Block.of(wood + "_door[facing=" + facing + ",half=" + (upper ? "upper" : "lower") + ",hinge=" + (right ? "right" : "left")
                + ",open=false,powered=false]", wood.equals("iron") ? 0xC2C1C1 : Furniture.rgb(wood));
    }

    /** 양여닫이 문 (a, a+1 두 짝). facing 은 기준 좌표 방향 */
    static void doubleDoor(Frame f, int a, int level, int b, String wood, String facing) {
        f.set(a, level, b, door(wood, facing, false, false));
        f.set(a, level + 1, b, door(wood, facing, true, false));
        f.set(a + 1, level, b, door(wood, facing, false, true));
        f.set(a + 1, level + 1, b, door(wood, facing, true, true));
    }

    /** 방 하나를 꾸미는 쪽: 안쪽 a 0..w-1, b 0..d-1, 복도 쪽 벽 b = -1 (문은 여기서 냄), 바깥 쪽 b = d */
    interface Room {
        void build(Frame f, int w, int d, int index);
    }

    /**
     * 복도 한쪽 방 줄. f 는 +b 가 방 안쪽을 보는 좌표계, 복도 쪽 벽은 b = -1.
     * a0..a1 을 안쪽 너비 roomW 방(사이 벽 1칸)으로 나누고, 마지막 방은 남는 칸을 가집니다.
     * backWall 이면 b = depth 에 뒷벽도 쌓습니다 (바깥 벽이면 건물이 쌓음).
     */
    static void strip(Frame f, int a0, int a1, int depth, int level, int h, int roomW, Block wall, boolean backWall, Room room) {
        int top = level + h - 2;
        f.fill(a0 - 1, level, -1, a1 + 1, top, -1, wall);
        if (backWall) {
            f.fill(a0 - 1, level, depth, a1 + 1, top, depth, wall);
        }
        f.fill(a0 - 1, level, 0, a0 - 1, top, depth - 1, wall);
        f.fill(a1 + 1, level, 0, a1 + 1, top, depth - 1, wall);
        int index = 0;
        for (int a = a0; a <= a1; ) {
            int w = roomW;
            if (a1 - (a + w) < roomW) {
                w = a1 - a + 1; // 마지막 방
            }
            if (w < 2) {
                break;
            }
            if (a + w <= a1) {
                f.fill(a + w, level, 0, a + w, top, depth - 1, wall);
            }
            room.build(f.sub(a, 0, "south"), w, depth, index++);
            a += w + 1;
        }
    }

    /** 복도 벽(b = -1)에 문 하나와 문 옆 이름표 */
    static void roomDoor(Frame f, int a, int level, String wood, String label) {
        Interior.door(f, a, level, -1, wood, "south");
        if (label != null && f.empty(a + 1, level + 1, -2)) {
            f.set(a + 1, level + 1, -2, Blocks.wallSign("birch", "north", "black", false, "", label));
        }
    }

    /** 대기 의자 줄 (a0..a1, 앉는 사람이 face 쪽을 봄) */
    static void seats(Frame f, int a0, int a1, int level, int b, String face, String wood) {
        for (int a = a0; a <= a1; a++) {
            if (f.empty(a, level, b)) {
                Furniture.chair(f, a, level, b, Furniture.opposite(face), wood);
            }
        }
    }

    /** 바닥 마감 (a0..a1, b0..b1 의 level - 1) */
    static void finish(Frame f, int a0, int b0, int a1, int b1, int level, Block b) {
        f.fill(a0, level - 1, b0, a1, level - 1, b1, b);
    }

    // ------------------------------------------------------------------ 조경

    /** 느티나무: 굵은 줄기, 넓게 퍼진 둥근 잎 (지름 약 9). y0 는 땅 윗면 블록 높이 */
    static void zelkova(Voxels v, int i, int j, int y0, Random r) {
        int h = 4 + r.nextInt(2);
        v.set(i, y0, j, COARSE_DIRT);
        v.fill(i, y0 + 1, j, i, y0 + h, j, OAK_LOG);
        v.ellipsoid(i + 0.5, y0 + h + 2.3, j + 0.5, 4.2, 2.6, 4.2, OAK_LEAVES);
        v.fill(i, y0 + h + 1, j, i, y0 + h + 2, j, OAK_LOG);
    }

    /** 은행나무: 곧은 줄기, 위로 긴 잎 (가을이면 노랗지만 지금은 초록) */
    static void ginkgo(Voxels v, int i, int j, int y0, Random r) {
        int h = 3 + r.nextInt(2);
        Block leaves = Block.of("birch_leaves[persistent=true]", 0x6B8F3F);
        v.set(i, y0, j, COARSE_DIRT);
        v.fill(i, y0 + 1, j, i, y0 + h + 5, j, Block.of("birch_log[axis=y]", 0xD8D3C5));
        v.ellipsoid(i + 0.5, y0 + h + 4.5, j + 0.5, 2.4, 4.2, 2.4, leaves);
        v.fill(i, y0 + h + 1, j, i, y0 + h + 6, j, Block.of("birch_log[axis=y]", 0xD8D3C5));
    }

    /** 소나무: 붉은 줄기 위쪽에만 납작한 잎 덩어리 */
    static void pine(Voxels v, int i, int j, int y0, Random r) {
        int h = 5 + r.nextInt(3);
        Block log = Block.of("spruce_log[axis=y]", 0x3A2A1A);
        Block leaves = Block.of("spruce_leaves[persistent=true]", 0x3D5E3A);
        v.set(i, y0, j, COARSE_DIRT);
        v.fill(i, y0 + 1, j, i, y0 + h, j, log);
        v.ellipsoid(i + 0.5, y0 + h + 1.0, j + 0.5, 3.0, 1.4, 3.0, leaves);
        v.ellipsoid(i + 1.5, y0 + h - 1.5, j + 0.5, 2.0, 1.0, 2.0, leaves);
        v.fill(i, y0 + h - 2, j, i, y0 + h + 1, j, log);
    }

    /** 나무 한 그루 (종류는 무작위) */
    static void tree(Voxels v, int i, int j, int y0, Random r) {
        switch (r.nextInt(5)) {
            case 0, 1 -> zelkova(v, i, j, y0, r);
            case 2, 3 -> ginkgo(v, i, j, y0, r);
            default -> pine(v, i, j, y0, r);
        }
    }

    /** 공원 벤치 (나무 계단 블록 의자, 길이 len, a 방향). 앉으면 face 쪽을 봄 */
    static void bench(Voxels v, int i, int y, int j, int len, boolean alongI, String face) {
        String back = Furniture.opposite(face);
        for (int k = 0; k < len; k++) {
            int ii = alongI ? i + k : i, jj = alongI ? j : j + k;
            v.set(ii, y, jj, Block.of("spruce_stairs[facing=" + back + ",half=bottom,shape=straight,waterlogged=false]", 0x725430));
        }
    }

    /** 공원 보행등: 검은 기둥 위 등 */
    static void parkLamp(Voxels v, int i, int y, int j) {
        v.fill(i, y, j, i, y + 2, j, Block.of("polished_blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x353038));
        v.set(i, y + 3, j, LANTERN);
    }

    /** 화단: 테두리 돌, 안은 흙과 꽃 */
    static void flowerBed(Voxels v, int i0, int j0, int i1, int j1, int y0, Random r) {
        Block[] flowers = {Block.of("red_tulip", 0x9E2A1E), Block.of("pink_tulip", 0xE09CB5), Block.of("orange_tulip", 0xD5701A),
                Block.of("white_tulip", 0xE6E6E6), Block.of("poppy", 0xB22A1E), Block.of("dandelion", 0xF0D020),
                Block.of("cornflower", 0x4466DD), Block.of("oxeye_daisy", 0xEDEDE0), Block.of("allium", 0xA86ACB)};
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                boolean rim = i == i0 || i == i1 || j == j0 || j == j1;
                if (rim) {
                    v.set(i, y0 + 1, j, Block.of("stone_brick_slab[type=bottom,waterlogged=false]", 0x7A7979));
                    v.set(i, y0, j, STONE_BRICKS);
                } else {
                    v.set(i, y0, j, GRASS);
                    v.set(i, y0 + 1, j, flowers[Math.floorMod((i / 2) * 7 + (j / 2) * 3 + r.nextInt(2), flowers.length)]);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 주차장

    /** 노상 주차장 (주차선만, 차는 두지 않음): 아스팔트, 흰 선, 둘레 연석, 통로 화살표 대신 가운데 점선 */
    static Voxels parking(int w, int d) {
        Voxels v = new Voxels(w, d, -1, 1);
        v.fill(0, -1, 0, w - 1, -1, d - 1, GRAY_CONCRETE);
        v.walls(0, -1, 0, w - 1, -1, d - 1, SMOOTH_STONE);
        // 주차 칸 (깊이 5, 너비 3) 두 줄 사이 통로 6: i 방향으로 16칸마다 되풀이
        for (int i = 1; i + 4 < w - 1; i += 16) {
            for (int j = 1; j < d - 1; j += 3) {
                v.fill(i, -1, j, Math.min(i + 4, w - 2), -1, j, WHITE_CONCRETE);
                if (i + 15 < w - 1) {
                    v.fill(i + 11, -1, j, i + 15, -1, j, WHITE_CONCRETE);
                }
            }
            if (i + 10 < w - 1) {
                for (int j = 2; j < d - 2; j += 4) {
                    v.fill(i + 8, -1, j, i + 8, -1, j + 1, YELLOW_CONCRETE);
                }
            }
        }
        return v;
    }

    private Kit() {
    }
}
