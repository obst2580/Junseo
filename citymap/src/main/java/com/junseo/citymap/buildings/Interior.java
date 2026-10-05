package com.junseo.citymap.buildings;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 건물 안의 뼈대: 계단실, 엘리베이터, 화장실, 문, 천장 등.
 * 모두 {@link Frame} 기준 좌표(a, b)로 그립니다. 높이는 「서는 높이」(level, 바닥 블록 바로 위 칸 y)로 줍니다.
 * <p>
 * 계단은 진짜 꺾인 계단(한 층을 두 번에 나눠 오름)이라, 사다리 없이 걸어서 옥상까지 오릅니다.
 */
final class Interior {
    /** 계단실 안쪽 너비 (a 방향): 오르는 줄 2 + 가운데 벽 1 + 내려오는 줄 2 */
    static final int STAIR_WIDTH = 5;

    static final Block INNER_WALL = SMOOTH_QUARTZ;
    static final Block CORE_WALL = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block LANDING = POLISHED_ANDESITE;
    static final Block TILE = Block.of("white_concrete", 0xCFD5D6);
    static final Block LIGHT = Block.of("pearlescent_froglight[axis=y]", 0xEDDBDC);

    /** 이 층고들을 오르는 계단실의 안쪽 깊이 (b 방향) */
    static int stairDepth(int maxRise) {
        return (maxRise + 1) / 2 + 3;
    }

    static int stairDepth(int[] levels) {
        int max = 0;
        for (int k = 0; k + 1 < levels.length; k++) {
            max = Math.max(max, levels[k + 1] - levels[k]);
        }
        return stairDepth(max);
    }

    /**
     * 꺾인 계단실. 안쪽은 a 0..4, b 0..깊이-1 입니다.
     * <ul>
     *   <li>b = 0 줄: 층마다 계단참 (복도와 이어지는 쪽)</li>
     *   <li>a = 0·1: +b 쪽으로 오르는 줄, a = 3·4: -b 쪽으로 오르는 줄, a = 2: 가운데 벽</li>
     *   <li>끝(b 가 큰 쪽)에 꺾이는 참</li>
     * </ul>
     * 계단실 둘레 벽(a = -1·5, b = -1·깊이)은 {@code wall} 이 null 이 아니면 같이 쌓고,
     * 층마다 b = -1 벽의 a = 0·1 에 출입구(2칸 너비, 3칸 높이)를 냅니다.
     *
     * @param levels 서는 높이들 (오름차순, 맨 끝은 옥상)
     */
    static void stairCore(Frame f, int[] levels, Block wall, String stairMaterial, int stairRgb) {
        stairCore(f, levels, wall, stairMaterial, stairRgb, 2);
    }

    /**
     * 줄 너비 lane 인 꺾인 계단 (좁은 빌라 계단실은 1). 안쪽 너비는 2 × lane + 1:
     * a = 0..lane-1 오르는 줄, a = lane 가운데 벽, 그 뒤가 반대로 오르는 줄.
     */
    static void stairCore(Frame f, int[] levels, Block wall, String stairMaterial, int stairRgb, int lane) {
        int n = levels.length, width = 2 * lane + 1;
        int depth = stairDepth(levels);
        // 꺾이는 참은 모든 층이 같은 자리(b = top+1..top+2)에: 층고가 달라도 위층 참이 아래 계단 머리 위로 내려오지 않게
        int top = depth - 3;
        int yTop = levels[n - 1] + 2;
        if (wall != null) {
            f.walls(-1, levels[0] - 1, -1, width, yTop, depth, wall);
        }
        f.fill(0, levels[0], 0, width - 1, yTop, depth - 1, AIR);
        Block up = Blocks.stairs(stairMaterial, "south", stairRgb);
        Block down = Blocks.stairs(stairMaterial, "north", stairRgb);
        int arrival = 0;
        for (int k = 0; k < n; k++) {
            int level = levels[k];
            f.fill(0, level - 1, 0, width - 1, level - 1, 0, LANDING);
            // 내려오는 줄 끝에서 계단참까지 평평한 길
            f.fill(lane + 1, level - 1, 0, width - 1, level - 1, arrival, LANDING);
            if (wall != null) {
                f.fill(0, level, -1, lane - 1, level + 2, -1, AIR);
            }
            if (k == n - 1) {
                break;
            }
            int rise = levels[k + 1] - level, half = (rise + 1) / 2;
            for (int s = 1; s <= half; s++) {
                f.fill(0, level + s - 1, s, lane - 1, level + s - 1, s, up);
            }
            // 오르는 줄 끝에서 꺾이는 참까지 평평한 길
            if (half + 1 <= top) {
                f.fill(0, level + half - 1, half + 1, lane - 1, level + half - 1, top, LANDING);
            }
            f.fill(0, level + half - 1, top + 1, width - 1, level + half - 1, top + 2, LANDING);
            for (int t = 1; t <= rise - half; t++) {
                int b = top + 1 - t;
                f.fill(lane + 1, level + half + t - 1, b, width - 1, level + half + t - 1, b, down);
            }
            f.fill(lane, level, 1, lane, levels[k + 1] - 1, top, wall != null ? wall : CORE_WALL);
            arrival = top - (rise - half);
        }
        // 계단참 벽등 (층마다 하나)
        if (wall != null) {
            for (int k = 0; k < n; k++) {
                f.set(width, levels[k] + 2, 0, LIGHT);
            }
        }
    }

    /**
     * 엘리베이터 한 대: 승강로 안쪽 a 0..1, b 0..1, 둘레 벽, 층마다 b = 2 쪽에 철문 두 짝과 호출 버튼.
     * 문 앞(b = 3)이 승강장입니다. 승강로 안에는 층마다 바닥이 있어서 떨어지지 않습니다.
     */
    static void elevator(Frame f, int[] levels, Block wall) {
        int n = levels.length;
        f.walls(-1, levels[0] - 1, -1, 2, levels[n - 1] + 3, 2, wall);
        for (int k = 0; k < n; k++) {
            int level = levels[k];
            f.fill(0, level - 1, 0, 1, level - 1, 1, POLISHED_ANDESITE);
            f.fill(0, level, 0, 1, level + 2, 1, AIR);
            f.set(0, level, 2, Block.of("iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]", 0xC2C1C1));
            f.set(0, level + 1, 2, Block.of("iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]", 0xC2C1C1));
            f.set(1, level, 2, Block.of("iron_door[facing=north,half=lower,hinge=right,open=false,powered=false]", 0xC2C1C1));
            f.set(1, level + 1, 2, Block.of("iron_door[facing=north,half=upper,hinge=right,open=false,powered=false]", 0xC2C1C1));
            f.set(2, level + 1, 3, Block.of("polished_blackstone_button[face=wall,facing=south,powered=false]", 0x353038));
            f.set(0, level + 2, 2, wall);
            f.set(1, level + 2, 2, wall);
        }
        f.fill(-1, levels[n - 1] + 3, -1, 2, levels[n - 1] + 3, 2, wall);
    }

    /** 문 (2칸). facing 은 기준 좌표 방향 */
    static void door(Frame f, int a, int level, int b, String wood, String facing) {
        f.set(a, level, b, Blocks.door(wood, facing, false));
        f.set(a, level + 1, b, Blocks.door(wood, facing, true));
    }

    /**
     * 방: 벽(a0..a1, b0..b1 둘레, 바닥 level-1 위로 높이 height 칸), 문 하나, 문 옆 이름표.
     * doorSide 는 문이 나는 벽 ("north" = b0, "south" = b1, "west" = a0, "east" = a1), doorAt 은 그 벽을 따라 문 위치.
     */
    static void room(Frame f, int a0, int b0, int a1, int b1, int level, int height, Block wall,
                     String doorSide, int doorAt, String wood, String label) {
        f.walls(a0, level, b0, a1, level + height - 2, b1, wall);
        int da, db;
        String facing;
        switch (doorSide) {
            case "north" -> {
                da = doorAt;
                db = b0;
                facing = "south";
            }
            case "south" -> {
                da = doorAt;
                db = b1;
                facing = "north";
            }
            case "west" -> {
                da = a0;
                db = doorAt;
                facing = "east";
            }
            default -> {
                da = a1;
                db = doorAt;
                facing = "west";
            }
        }
        if (wood == null) {
            f.set(da, level, db, AIR);
            f.set(da, level + 1, db, AIR);
        } else {
            door(f, da, level, db, wood, facing);
        }
        if (label != null) {
            // 문 옆 벽, 바깥쪽에 이름표
            int la = da, lb = db;
            String out;
            switch (doorSide) {
                case "north" -> {
                    la = da + 1;
                    lb = b0 - 1;
                    out = "north";
                }
                case "south" -> {
                    la = da + 1;
                    lb = b1 + 1;
                    out = "south";
                }
                case "west" -> {
                    la = a0 - 1;
                    lb = db + 1;
                    out = "west";
                }
                default -> {
                    la = a1 + 1;
                    lb = db + 1;
                    out = "east";
                }
            }
            if (f.empty(la, level + 1, lb)) {
                f.set(la, level + 1, lb, Blocks.wallSign("birch", out, "black", false, "", label));
            }
        }
    }

    /**
     * 화장실 한 칸(남·여 하나): 안쪽 a0..a1, b0..b1. 칸막이 변기 칸은 b0 벽을 따라, 세면대는 a1 벽을 따라.
     * 문은 b1 쪽 벽(문은 doorA 에)에, 이름표는 문 옆 바깥에. 벽은 둘레에 같이 쌓습니다.
     */
    static void restroom(Frame f, int a0, int b0, int a1, int b1, int level, int height, String label, int doorA) {
        Block tileWall = Block.of("white_terracotta", 0xD1B2A1);
        room(f, a0 - 1, b0 - 1, a1 + 1, b1 + 1, level, height, tileWall, "south", doorA, "pale_oak", label);
        f.fill(a0, level - 1, b0, a1, level - 1, b1, Block.of("light_gray_terracotta", 0x876A61));
        // 변기 칸: 2칸 너비, 깊이 3, 칸막이는 문 하나와 벽
        int a = a0;
        while (a + 1 <= a1 - 2) {
            f.set(a, level, b0, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
            f.set(a + 1, level, b0, Block.of("tripwire_hook[attached=false,facing=south,powered=false]", 0x8F8F8F));
            if (b0 + 2 <= b1 - 1) {
                f.fill(a + 2, level, b0, a + 2, level + 1, b0 + 1, Block.of("white_concrete", 0xCFD5D6));
                f.set(a, level, b0 + 2, Block.of("white_concrete", 0xCFD5D6));
                f.set(a, level + 1, b0 + 2, Block.of("white_concrete", 0xCFD5D6));
                door(f, a + 1, level, b0 + 2, "birch", "north");
                f.set(a + 2, level, b0 + 2, Block.of("white_concrete", 0xCFD5D6));
                f.set(a + 2, level + 1, b0 + 2, Block.of("white_concrete", 0xCFD5D6));
            }
            a += 3;
        }
        // 세면대와 거울
        for (int b = b0 + 4; b <= b1 - 1; b += 2) {
            f.set(a1, level, b, CAULDRON);
            f.set(a1 + 1, level + 1, b, Block.of("light_blue_stained_glass", 0x6699D8));
            f.set(a1, level + 1, b, Block.of("tripwire_hook[attached=false,facing=west,powered=false]", 0x8F8F8F));
        }
        f.set((a0 + a1) / 2, level + height - 2, (b0 + b1) / 2, LIGHT);
    }

    /** 천장 등: 방 안쪽(a0..a1, b0..b1)에 spacing 간격으로, 천장 바로 아래 칸에 */
    static void lights(Frame f, int a0, int b0, int a1, int b1, int level, int height, int spacing, Block light) {
        int off = Math.max(1, spacing / 2);
        for (int b = b0 + off; b <= b1 - 1; b += spacing) {
            for (int a = a0 + off; a <= a1 - 1; a += spacing) {
                if (f.empty(a, level + height - 2, b)) {
                    f.set(a, level + height - 2, b, light);
                }
            }
        }
    }

    /** 바닥 마감 (바닥 블록을 바꿈) */
    static void floor(Frame f, int a0, int b0, int a1, int b1, int level, Block finish) {
        f.fill(a0, level - 1, b0, a1, level - 1, b1, finish);
    }

    private Interior() {
    }
}
