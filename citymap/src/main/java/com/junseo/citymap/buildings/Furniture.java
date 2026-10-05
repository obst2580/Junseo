package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 가구와 살림. 블록 엔티티가 있어야 보이는 블록(침대·상자·현수막)은 생성 단계에서 안 보여서 쓰지 않고,
 * 비슷하게 보이는 블록으로 만듭니다 (침대 = 양털 매트리스와 나무 머리판).
 * 모두 {@link Frame} 기준 좌표, 높이는 서는 높이(level).
 */
final class Furniture {
    static final Block DESK_TOP = Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430);
    static final Block WHITE_TOP = Block.of("smooth_quartz_slab[type=top,waterlogged=false]", 0xECE6DF);
    static final Block COUNTER = Block.of("smooth_quartz", 0xECE6DF);
    static final Block FRIDGE = Block.of("iron_block", 0xDCDCDC);
    static final Block BOOKSHELF = Block.of("bookshelf", 0x6B5839);
    static final Block TV = Block.of("black_concrete", 0x080A0F);
    static final String[] SHEETS = {"white", "white", "light_gray", "light_blue", "pink", "gray", "yellow"};
    static final String[] SOFA = {"dark_oak", "spruce", "mangrove", "pale_oak", "polished_andesite"};
    static final Block[] PLANTS = {FLOWER_POT_FERN, FLOWER_POT_TULIP, FLOWER_POT_DANDELION,
            Block.of("potted_bamboo", 0x5D8A2A), Block.of("potted_azalea_bush", 0x63753A)};

    /** 의자 (등받이가 back 쪽). back 은 기준 방향 */
    static void chair(Frame f, int a, int level, int b, String back, String wood) {
        f.set(a, level, b, Block.of(wood + "_stairs[facing=" + back + ",half=bottom,shape=straight,waterlogged=false]", rgb(wood)));
    }

    /** 책상 한 칸 + 모니터(쇠 다락문을 세운 판) + 의자. 사람은 sit 쪽에 앉아 반대쪽을 봄 */
    static void desk(Frame f, int a, int level, int b, String sit) {
        f.set(a, level, b, DESK_TOP);
        f.set(a, level + 1, b, Block.of("iron_trapdoor[facing=" + sit + ",half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        int[] d = step(sit);
        if (f.empty(a + d[0], level, b + d[1])) {
            chair(f, a + d[0], level, b + d[1], sit, "dark_oak");
        }
    }

    /** 침대 (2칸): 머리 (a, b), 발치는 toward 쪽. 양털 매트리스 + 머리판 + 베개 */
    static void bed(Frame f, Random r, int a, int level, int b, String toward) {
        int[] d = step(toward);
        Block sheet = wool(SHEETS[r.nextInt(SHEETS.length)]);
        f.set(a, level, b, Block.of("white_wool", 0xE9ECEC));
        f.set(a + d[0], level, b + d[1], sheet);
        f.set(a, level + 1, b, Block.of("white_carpet", 0xE9ECEC));
        int ha = a - d[0], hb = b - d[1];
        if (f.empty(ha, level, hb) || f.get(ha, level, hb) == null) {
            f.set(ha, level, hb, Block.of("spruce_trapdoor[facing=" + opposite(toward) + ",half=bottom,open=true,powered=false,waterlogged=false]", 0x725430));
        }
    }

    /** 소파 (길이 len, a 방향으로 늘어섬): 앉는 쪽이 face */
    static void sofa(Frame f, Random r, int a, int level, int b, int len, String face) {
        String wood = SOFA[r.nextInt(SOFA.length)];
        for (int k = 0; k < len; k++) {
            f.set(a + k, level, b, Block.of(wood + "_stairs[facing=" + opposite(face) + ",half=bottom,shape=straight,waterlogged=false]", rgb(wood)));
        }
    }

    /** 식탁 (w×d 판) + 둘레 의자 */
    static void table(Frame f, int a0, int level, int b0, int w, int d, String wood) {
        Block top = Block.of(wood + "_slab[type=top,waterlogged=false]", rgb(wood));
        f.fill(a0, level, b0, a0 + w - 1, level, b0 + d - 1, top);
        for (int a = a0; a < a0 + w; a++) {
            if (f.empty(a, level, b0 - 1)) {
                chair(f, a, level, b0 - 1, "north", wood);
            }
            if (f.empty(a, level, b0 + d)) {
                chair(f, a, level, b0 + d, "south", wood);
            }
        }
    }

    /** 부엌: a0..a1 을 따라 b 벽에 붙은 조리대 (개수대·가스레인지·냉장고), 위에 수납장 */
    static void kitchen(Frame f, int a0, int a1, int level, int b, String wallSide) {
        String face = opposite(wallSide);
        for (int a = a0; a <= a1; a++) {
            Block x;
            if (a == a1) {
                x = FRIDGE;
                f.set(a, level + 1, b, FRIDGE);
            } else if (a == a0 + 1) {
                x = CAULDRON;
            } else if (a == a0 + 3) {
                x = Block.of("smoker[facing=" + face + ",lit=false]", 0x555451);
            } else {
                x = COUNTER;
            }
            f.set(a, level, b, x);
            if (a != a1) {
                f.set(a, level + 2, b, COUNTER); // 위 수납장 (천장까지)
            }
        }
    }

    /** 벽걸이 TV (2칸) + 아래 거실장 */
    static void tv(Frame f, int a, int level, int b) {
        f.set(a, level + 1, b, TV);
        f.set(a + 1, level + 1, b, TV);
        f.set(a, level, b, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        f.set(a + 1, level, b, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
    }

    static void plant(Frame f, Random r, int a, int level, int b) {
        if (f.empty(a, level, b)) {
            f.set(a, level, b, PLANTS[r.nextInt(PLANTS.length)]);
        }
    }

    static Block wool(String color) {
        return Blocks.wool(color);
    }

    static String opposite(String dir) {
        return switch (dir) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** 기준 방향으로 한 칸 {da, db} */
    static int[] step(String dir) {
        return switch (dir) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "east" -> new int[]{1, 0};
            default -> new int[]{-1, 0};
        };
    }

    static int rgb(String wood) {
        return switch (wood) {
            case "oak" -> 0xA2834F;
            case "spruce" -> 0x725430;
            case "birch" -> 0xC0AF79;
            case "dark_oak" -> 0x432B14;
            case "mangrove" -> 0x763631;
            case "pale_oak" -> 0xE3D9D4;
            case "cherry" -> 0xE2B3AD;
            case "quartz", "smooth_quartz" -> 0xECE6DF;
            default -> 0x848685;
        };
    }

    private Furniture() {
    }
}
