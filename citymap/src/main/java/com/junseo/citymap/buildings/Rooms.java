package com.junseo.citymap.buildings;

import java.util.Random;
import java.util.function.BiPredicate;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 실제로 쓰는 방 배치: 집 한 채, 사무실, 호텔 객실, 병실, 교실.
 * 모두 {@link Frame} 기준 좌표. 안쪽 a 0..w-1, b 0..d-1 이고 b = -1 쪽이 복도(현관), b = d 쪽이 창(바깥)입니다.
 * 바깥 벽과 창은 건물이 그리고, 여기서는 안쪽 벽·문·가구·등만 놓습니다.
 */
final class Rooms {
    static final Block MARU = Block.of("stripped_oak_wood[axis=x]", 0xB18F57);
    static final Block MARU_LIGHT = Block.of("birch_planks", 0xC0AF79);
    static final Block BATH_TILE = Block.of("light_gray_terracotta", 0x876A61);
    static final Block CARPET_TILE = Block.of("gray_concrete_powder", 0x4C5155);
    static final Block WARDROBE = Block.of("stripped_birch_wood[axis=y]", 0xC4B07B);

    /**
     * 집 한 채 (원룸·투룸·아파트 한 세대). 크기에 따라 방 수가 달라집니다.
     * 현관 문은 b = -1 벽의 a = entryA 에 냅니다 (그 벽은 건물이 쌓아 둔 것).
     */
    static void home(Frame f, Random r, int w, int d, int level, int height, int entryA) {
        Interior.floor(f, 0, 0, w - 1, d - 1, level, r.nextBoolean() ? MARU : MARU_LIGHT);
        Interior.door(f, entryA, level, -1, "pale_oak", "south");
        Block wall = Interior.INNER_WALL;
        int top = level + height - 2;
        if (w < 7 || d < 6) {
            // 원룸: 한쪽에 부엌, 창가에 침대
            Furniture.kitchen(f, Math.max(0, w - 5), w - 1, level, 0, "north");
            Furniture.bed(f, r, 0, level, d - 2, "east");
            Furniture.desk(f, w - 1, level, d - 1, "west");
            Interior.lights(f, 0, 0, w - 1, d - 1, level, height, 4, Interior.LIGHT);
            return;
        }
        int bk = Math.max(3, d * 2 / 5);
        int bathW = 3;
        // 욕실 (뒤 구석)
        f.fill(bathW, level, 0, bathW, top, bk - 1, wall);
        f.fill(0, level, bk, bathW, top, bk, wall);
        Interior.door(f, bathW, level, bk - 1, "pale_oak", "east");
        Interior.floor(f, 0, 0, bathW - 1, bk - 1, level, Interior.TILE);
        f.set(0, level, 0, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
        f.set(bathW - 1, level, 0, CAULDRON);
        f.set(bathW - 1, level + 1, -1, Block.of("light_blue_stained_glass", 0x6699D8));
        if (bk >= 3) {
            f.fill(0, level, bk - 1, 1, level, bk - 1, Block.of("white_terracotta", 0xD1B2A1)); // 욕조
        }
        f.set(1, top, 1, Interior.LIGHT);
        // 현관 바닥
        f.fill(Math.max(bathW + 1, entryA - 1), level - 1, 0, Math.min(w - 1, entryA + 1), level - 1, 1, Interior.TILE);
        // 부엌 (뒤쪽 나머지 벽을 따라) + 식탁
        int ka = Math.max(bathW + 3, w - 6);
        if (w - ka >= 4) {
            Furniture.kitchen(f, ka, w - 1, level, 0, "north");
            if (bk >= 4 && w - ka >= 5) {
                Furniture.table(f, ka + 1, level, 2, 2, 1, "oak");
            }
        }
        // 앞쪽: 침실 (왼쪽) + 거실 (오른쪽)
        int bw = Math.max(3, Math.min(w / 2 - 1, 5));
        f.fill(bw, level, bk + 1, bw, top, d - 1, wall);
        f.fill(0, level, bk, bw, top, bk, wall);
        Interior.door(f, bw - 1, level, bk, "pale_oak", "south");
        Furniture.bed(f, r, 0, level, d - 1 - Math.min(2, d - bk - 3), "east");
        if (d - bk >= 4) {
            f.set(0, level, bk + 1, WARDROBE);
            f.set(0, level + 1, bk + 1, WARDROBE);
        }
        f.set(bw / 2, top, (bk + d) / 2, Interior.LIGHT);
        // 거실: TV 는 침실 벽에, 소파는 맞은편
        int la0 = bw + 1, la1 = w - 1, lb0 = bk + 1, lb1 = d - 1;
        if (la1 - la0 >= 3 && lb1 - lb0 >= 2) {
            int mid = (lb0 + lb1) / 2;
            f.set(bw, level + 1, mid, Furniture.TV); // 벽걸이 TV (침실 벽)
            f.set(la0, level, mid, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
            Furniture.sofa(f.sub(la1, mid - 1, "west"), r, 0, level, 0, Math.min(3, lb1 - mid + 2), "south");
            f.set(Math.min(la1 - 1, la0 + 2), level, mid, Block.of("gray_carpet", 0x3E4447));
            Furniture.plant(f, r, la1, level, lb1);
            f.set((la0 + la1) / 2, top, mid, Interior.LIGHT);
        }
        f.set((bathW + w) / 2, top, Math.max(1, bk / 2), Interior.LIGHT);
    }

    /**
     * 사무실 바닥: free(a, b) 인 곳에 마주 보는 책상 줄(한 묶음 = 의자·책상·책상·의자), 줄 끝 화분, 천장 등.
     * 창가(가장자리)는 2칸 통로로 비웁니다.
     */
    static void office(Frame f, Random r, int a0, int b0, int a1, int b1, int level, int height, BiPredicate<Integer, Integer> free) {
        for (int b = b0 + 3; b + 3 <= b1 - 2; b += 6) {
            int run = 0;
            for (int a = a0 + 2; a <= a1 - 2; a++) {
                boolean ok = true;
                for (int db = 0; db <= 3 && ok; db++) {
                    ok = free.test(a, b + db) && f.empty(a, level, b + db);
                }
                if (!ok || run == 6) {
                    if (run > 0 && free.test(a, b + 1) && f.empty(a, level, b + 1)) {
                        Furniture.plant(f, r, a, level, b + 1);
                    }
                    run = run == 6 ? -1 : 0;
                    continue;
                }
                if (run < 0) {
                    run = 0;
                    continue;
                }
                Furniture.desk(f, a, level, b + 1, "north");
                Furniture.desk(f, a, level, b + 2, "south");
                run++;
            }
        }
        for (int b = b0 + 2; b <= b1 - 1; b += 5) {
            for (int a = a0 + 2; a <= a1 - 1; a += 5) {
                if (free.test(a, b) && f.empty(a, level + height - 2, b)) {
                    f.set(a, level + height - 2, b, Interior.LIGHT);
                }
            }
        }
    }

    /** 회의실: 유리 칸막이 방, 가운데 긴 탁자와 의자, 벽 화면 */
    static void meeting(Frame f, int a0, int b0, int a1, int b1, int level, int height) {
        Block glass = Block.of("white_stained_glass_pane", 0xF0F0F0);
        f.walls(a0, level, b0, a1, level + height - 2, b1, glass);
        Interior.door(f, (a0 + a1) / 2, level, b1, "pale_oak", "north");
        if (a1 - a0 >= 4 && b1 - b0 >= 4) {
            Furniture.table(f, a0 + 2, level, (b0 + b1) / 2, a1 - a0 - 3, 1, "dark_oak");
            f.set((a0 + a1) / 2, level + 1, b0 + 1, Furniture.TV);
        }
        f.set((a0 + a1) / 2, level + height - 2, (b0 + b1) / 2, Interior.LIGHT);
    }

    /** 호텔 객실 (안쪽 w×d, 문은 b = -1 의 a = 1): 문 옆 욕실, 창 쪽 침대, 책상·TV, 창가 의자 */
    static void hotel(Frame f, Random r, int w, int d, int level, int height) {
        Interior.floor(f, 0, 0, w - 1, d - 1, level, Block.of("brown_concrete_powder", 0x7D5435));
        Interior.door(f, 1, level, -1, "dark_oak", "south");
        int top = level + height - 2;
        if (w >= 5 && d >= 6) {
            f.fill(2, level, 0, 2, top, 2, Interior.INNER_WALL);
            f.fill(0, level, 3, 2, top, 3, Interior.INNER_WALL);
            Interior.door(f, 2, level, 1, "pale_oak", "east");
            f.set(0, level, 0, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
            f.set(0, level, 2, CAULDRON);
            Interior.floor(f, 0, 0, 1, 2, level, Interior.TILE);
        }
        Furniture.bed(f, r, w - 1, level, d - 3, "west");
        if (w >= 4) {
            Furniture.bed(f, r, w - 1, level, d - 2, "west");
        }
        f.set(0, level, d - 2, Furniture.DESK_TOP);
        Furniture.chair(f, 1, level, d - 2, "east", "dark_oak");
        f.set(0, level + 1, d - 4, Furniture.TV);
        Furniture.plant(f, r, 0, level, d - 1);
        f.set(w / 2, top, d / 2, Interior.LIGHT);
    }

    /** 병실 (안쪽 w×d, 문은 b = -1 의 가운데): 침대 줄, 침대 사이 커튼, 세면대 */
    static void ward(Frame f, Random r, int w, int d, int level, int height) {
        Interior.floor(f, 0, 0, w - 1, d - 1, level, Block.of("light_gray_concrete", 0x7D7D73));
        Interior.door(f, w / 2, level, -1, "pale_oak", "south");
        Block curtain = Block.of("light_blue_stained_glass_pane", 0x6699D8);
        for (int a = 0; a + 1 < w; a += 3) {
            for (int side = 0; side < 2; side++) {
                int b = side == 0 ? 1 : d - 2;
                if (b < 1 || (side == 1 && d < 6)) {
                    continue;
                }
                Block sheet = Block.of("white_wool", 0xE9ECEC);
                f.set(a, level, b, sheet);
                f.set(a + 1, level, b, sheet);
                f.set(a, level + 1, b, Block.of("white_carpet", 0xE9ECEC));
                f.set(a, level, b + (side == 0 ? -1 : 1), Block.of("iron_bars", 0x888888));
                if (a + 2 < w) {
                    f.set(a + 2, level, b, curtain);
                    f.set(a + 2, level + 1, b, curtain);
                }
            }
        }
        f.set(w - 1, level, d / 2, CAULDRON);
        Interior.lights(f, 0, 0, w - 1, d - 1, level, height, 4, Interior.LIGHT);
    }

    /** 교실 (안쪽 w×d, 앞 칠판은 a = 0 벽, 문은 b = -1 의 뒤쪽): 책상 줄, 교탁, 칠판 */
    static void classroom(Frame f, int w, int d, int level, int height) {
        Interior.door(f, w - 2, level, -1, "oak", "south");
        f.fill(-1, level + 1, 1, -1, level + 2, d - 2, Block.of("green_concrete", 0x495B24));
        f.set(1, level, d / 2, Furniture.DESK_TOP);
        for (int a = 3; a < w - 1; a += 2) {
            for (int b = 1; b < d - 1; b += 2) {
                f.set(a, level, b, Furniture.DESK_TOP);
                Furniture.chair(f, a + 1, level, b, "east", "oak");
            }
        }
        Interior.lights(f, 0, 0, w - 1, d - 1, level, height, 4, Interior.LIGHT);
    }

    private Rooms() {
    }
}
