package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 교도소 건물들 (교도소 섬). 모두 정면(입구)이 남쪽(j = d-1)이고, 맨 앞 한 줄은 간판·차양을 다는 앞마당입니다.
 * 바깥은 연한 베이지 칠한 콘크리트, 회색 띠와 굽도리, 창에는 쇠창살.
 * <ul>
 *   <li>수용동: 3층, 가운데 복도 양옆으로 쇠창살 앞면의 거실(감방). 거실마다 매트리스 침대·변기·세면대·작은 상, 높은 창살 창.
 *       복도 끝 계단실로 옥상까지, 층마다 근무실(유리창)과 휴게실, 1층 휴게실 쪽에 출입구</li>
 *   <li>본관: 3층. 1층 민원실과 접견실(유리 칸막이를 사이에 둔 접견 창구), 2층 사무실, 3층 소장실·회의실</li>
 *   <li>의료동: 2층. 1층 대기실·진료실·처치실·약제실, 2층 병실</li>
 *   <li>노역장: 한 층 높은 작업장. 봉제(베틀)·목공 작업대, 자재 더미, 감독석</li>
 *   <li>식당: 긴 식탁과 의자, 배식대, 뒤쪽 주방</li>
 *   <li>망루: 담 모서리의 감시탑. 좁은 계단실 위에 사방 유리창 감시실</li>
 * </ul>
 */
final class Prison {
    static final Block WALL = Block.of("white_terracotta", 0xD1B2A1);
    static final Block TRIM = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block PLINTH = Block.of("stone_bricks", 0x7A7979);
    static final Block SLAB = SMOOTH_STONE;
    static final Block INNER = Block.of("white_concrete", 0xCFD5D6);
    static final Block CELL_FLOOR = Rooms.MARU_LIGHT;
    static final Block CORRIDOR = POLISHED_ANDESITE;
    static final Block PANE = Block.of("glass_pane", 0xC8DCE4);
    static final Block TOILET = Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE);
    static final Block STOOL = Block.of("oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xA2834F);

    // ------------------------------------------------------------------ 공통

    /** 바닥판·바깥 벽·층 띠·옥상 난간. 몸체는 j 0..d-2 (j = d-1 은 앞마당) */
    static void shell(Voxels v, int[] lv, Block floor0) {
        int w = v.w, bd = v.d - 1;
        int n = lv.length - 1;
        for (int k = 0; k <= n; k++) {
            v.fill(0, lv[k] - 1, 0, w - 1, lv[k] - 1, bd - 1, k == 0 ? floor0 : SLAB);
        }
        for (int k = 0; k < n; k++) {
            v.walls(0, lv[k], 0, w - 1, lv[k + 1] - 2, bd - 1, WALL);
            v.walls(0, lv[k + 1] - 1, 0, w - 1, lv[k + 1] - 1, bd - 1, TRIM);
        }
        v.walls(0, 0, 0, w - 1, 0, bd - 1, PLINTH);
        v.walls(0, lv[n], 0, w - 1, lv[n], bd - 1, TRIM);
        // 앞마당 포장
        v.fill(0, -1, bd, w - 1, -1, bd, SMOOTH_STONE);
    }

    /** 창: 바깥 벽 칸 (i, j) 의 층 높이 L, 층고 h 에 창 (barred 면 쇠창살) */
    static void window(Voxels v, int i, int j, int L, int h, boolean barred) {
        for (int y = L + 1; y <= L + h - 2; y++) {
            v.set(i, y, j, barred ? IRON_BARS : PANE);
        }
    }

    /** 바깥 벽 둘레에 일정한 간격으로 창 (모서리와 문, 이미 다른 것이 있는 칸은 건너뜀) */
    static void windows(Voxels v, int[] lv, int every, boolean barredBack, boolean barredAll) {
        int w = v.w, bd = v.d - 1;
        for (int k = 0; k + 1 < lv.length; k++) {
            int L = lv[k], h = lv[k + 1] - lv[k];
            for (int i = 2; i < w - 2; i++) {
                if (i % every == 1) {
                    if (WALL.equals(v.get(i, L + 1, bd - 1))) {
                        window(v, i, bd - 1, L, h, barredAll);
                    }
                    if (WALL.equals(v.get(i, L + 1, 0))) {
                        window(v, i, 0, L, h, barredBack || barredAll);
                    }
                }
            }
            for (int j = 2; j < bd - 2; j++) {
                if (j % every == 1) {
                    if (WALL.equals(v.get(0, L + 1, j))) {
                        window(v, 0, j, L, h, barredAll);
                    }
                    if (WALL.equals(v.get(w - 1, L + 1, j))) {
                        window(v, w - 1, j, L, h, barredAll);
                    }
                }
            }
        }
    }

    /** 앞 출입구 (가운데 mid, 너비 wd, 높이 3) 와 위 차양, 문 위 간판 */
    static void entrance(Voxels v, int mid, int wd, String... sign) {
        int bd = v.d - 1;
        for (int i = mid - wd / 2; i < mid - wd / 2 + wd; i++) {
            v.fill(i, 0, bd - 1, i, 2, bd - 1, AIR);
            v.set(i, 3, bd, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
        }
        v.set(mid - wd / 2 - 1, 3, bd, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
        v.set(mid - wd / 2 + wd, 3, bd, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
        if (sign.length > 0) {
            v.set(mid, 4, bd, Blocks.wallSign("dark_oak", "south", "white", false, sign));
        }
    }

    /** 옥상까지 오르는 계단실 지붕 */
    static void stairRoof(Frame sf, int top, int width, int depth) {
        sf.fill(-1, top + 3, -1, width, top + 3, depth, SLAB);
    }

    // ------------------------------------------------------------------ 수용동

    /** 수용동 (권장 33×16): 3층, 층마다 거실 10개 */
    static Voxels cellBlock(int w, int d, Random r, String name) {
        int[] lv = {0, 4, 8, 12};
        Voxels v = new Voxels(w, d, -1, 16);
        shell(v, lv, CORRIDOR);
        // 계단실: 복도 서쪽 끝 (안쪽 j 6..8 이 복도 너비와 같음), 문은 동쪽(복도)
        Frame sf = Frame.facing(v, 6, 6, "west");
        Interior.stairCore(sf, lv, Interior.CORE_WALL, "polished_andesite", 0x848685, 1);
        stairRoof(sf, lv[3], 3, Interior.stairDepth(lv));
        for (int k = 0; k < 3; k++) {
            cellFloor(v, r, lv[k], k, name);
        }
        // 1층 출입구 (휴게실 앞)
        v.fill(9, 0, d - 2, 10, 2, d - 2, AIR);
        for (int i = 7; i <= 12; i++) {
            v.set(i, 3, d - 1, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
        }
        v.set(11, 2, d - 1, Blocks.wallSign("dark_oak", "south", "white", false, "", name));
        // 옥상: 물탱크
        v.fill(w - 6, 12, 3, w - 3, 13, 5, Block.of("light_blue_terracotta", 0x716C89));
        v.connect();
        return v;
    }

    private static void cellFloor(Voxels v, Random r, int L, int k, String name) {
        int w = v.w, bd = v.d - 1;
        Frame f = Frame.of(v);
        // 복도
        v.fill(8, L - 1, 6, w - 2, L - 1, 8, CORRIDOR);
        for (int i = 10; i < w - 1; i += 4) {
            v.set(i, L + 2, 7, Interior.LIGHT);
        }
        v.fill(w - 1, L + 1, 6, w - 1, L + 1, 8, IRON_BARS); // 복도 끝 창
        // 북쪽: 근무실 (계단실 옆 ~ 첫 칸), 복도 쪽은 유리창과 문
        Interior.floor(f, 1, 1, 11, 4, L, Block.of("gray_concrete_powder", 0x4C5155));
        v.fill(8, L, 5, 8, L + 2, 5, INNER);
        v.fill(9, L, 5, 11, L + 1, 5, PANE);
        v.fill(9, L + 2, 5, 11, L + 2, 5, INNER);
        Interior.door(f, 10, L, 5, "oak", "north");
        Furniture.desk(f, 3, L, 2, "south");
        Furniture.desk(f, 5, L, 2, "south");
        v.fill(2, L + 1, 1, 6, L + 1, 1, Furniture.TV); // CCTV 화면
        f.set(8, L + 1, 6, Blocks.wallSign("birch", "south", "black", false, "", "근무실"));
        v.set(6, L + 2, 3, Interior.LIGHT);
        window(v, 3, 0, L, 4, true);
        window(v, 9, 0, L, 4, true);
        // 남쪽: 휴게실 (1층은 출입 홀)
        v.fill(8, L, 9, 8, L + 2, 9, INNER);
        v.fill(9, L, 9, 11, L + 2, 9, AIR);
        Interior.floor(f, 1, 10, 11, bd - 2, L, k == 0 ? CORRIDOR : CELL_FLOOR);
        if (k == 0) {
            v.fill(2, L, 11, 5, L, 11, Furniture.COUNTER); // 출입 확인 책상
            Furniture.chair(f, 3, L, 10, "north", "dark_oak");
        } else {
            Furniture.table(f, 3, L, 12, 3, 1, "spruce");
            v.set(1, L + 1, 11, Furniture.TV);
            window(v, 4, bd - 1, L, 4, true);
            window(v, 7, bd - 1, L, 4, true);
        }
        v.set(6, L + 2, 11, Interior.LIGHT);
        // 거실 (감방) 칸막이와 거실
        for (int x = 12; x <= w - 5; x += 4) {
            v.fill(x, L, 1, x, L + 2, 4, INNER);
            v.fill(x, L, 10, x, L + 2, bd - 2, INNER);
            v.set(x, L, 5, INNER);
            v.set(x, L + 1, 5, INNER);
            v.set(x, L + 2, 5, INNER);
            v.fill(x, L, 9, x, L + 2, 9, INNER);
        }
        int no = 1;
        for (int a = 13; a + 2 <= w - 2; a += 4) {
            cell(new Frame(v, a, 1, 0), r, L);
            cell(new Frame(v, a + 2, bd - 2, 2), r, L);
            // 거실 번호 (칸막이 벽에)
            f.set(a - 1, L + 2, 6, Blocks.wallSign("birch", "south", "black", false, "", (k + 1) + "0" + no));
            f.set(a - 1, L + 2, 8, Blocks.wallSign("birch", "north", "black", false, "", (k + 1) + "0" + (no + 1)));
            no += 2;
        }
    }

    /**
     * 거실 하나 (안쪽 a 0..2, b 0..3; b = -1 은 바깥 벽, b = 4 는 쇠창살 앞면). 앞면 가운데 한 칸은 드나드는 구멍.
     * 매트리스 침대, 변기, 세면대, 작은 상, 높은 창살 창, 천장 등.
     */
    static void cell(Frame f, Random r, int L) {
        f.fill(0, L - 1, 0, 2, L - 1, 3, CELL_FLOOR);
        f.fill(0, L, 4, 2, L + 2, 4, IRON_BARS);
        f.set(1, L, 4, AIR);
        f.set(1, L + 1, 4, AIR);
        Furniture.bed(f, r, 0, L, 0, "east");
        f.set(2, L, 0, TOILET);
        f.set(2, L, 1, CAULDRON);
        f.set(0, L, 2, Furniture.DESK_TOP);
        f.set(1, L + 1, -1, IRON_BARS);
        f.set(1, L + 2, 1, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 본관

    /** 본관 (권장 19×16): 1층 민원실·접견실, 2층 사무실, 3층 소장실·회의실 */
    static Voxels admin(int w, int d, Random r, String name) {
        int[] lv = {0, 5, 9, 13};
        Voxels v = new Voxels(w, d, -1, 17);
        int bd = d - 1;
        shell(v, lv, POLISHED_ANDESITE);
        windows(v, lv, 3, true, false);
        Frame f = Frame.of(v);
        Frame sf = Frame.facing(v, w - 7, 6, "east");
        Interior.stairCore(sf, lv, Interior.CORE_WALL, "polished_andesite", 0x848685);
        stairRoof(sf, lv[3], 5, Interior.stairDepth(lv));
        for (int k = 0; k < 3; k++) {
            int L = lv[k], h = lv[k + 1] - L;
            Interior.restroom(Frame.facing(v, w - 2, 9, "west"), 0, 0, 3, 5, L, h, "화장실", 2);
            Interior.lights(f, 1, 1, w - 9, bd - 2, L, h, 4, Interior.LIGHT);
        }
        // 1층: 접견실 (안쪽 칸막이 너머는 수용자 쪽, 철문으로 교도소 안과 이어짐)
        int L = 0;
        int x1 = w - 9;
        v.fill(1, L, 6, x1 - 1, L + 3, 6, PANE);
        v.fill(x1, L, 1, x1, L + 3, 5, INNER);
        for (int i = 1; i <= x1 - 1; i++) {
            v.set(i, L, 5, Furniture.DESK_TOP);
            v.set(i, L, 7, Furniture.DESK_TOP);
            if (i % 3 == 0) {
                v.fill(i, L, 5, i, L + 2, 5, INNER);
                v.fill(i, L, 7, i, L + 2, 7, INNER);
            } else {
                f.set(i, L, 4, STOOL);
                f.set(i, L, 8, STOOL.rotate(2));
            }
        }
        v.set(4, 0, 0, Blocks.door("iron", "north", false));
        v.set(4, 1, 0, Blocks.door("iron", "north", true));
        v.set(5, L + 3, 8, Blocks.hangingSign("birch", 0, "black", false, "", "접견실"));
        // 민원실: 창구와 대기 의자
        for (int i = 2; i <= 6; i++) {
            v.set(i, L, 11, Furniture.COUNTER);
        }
        v.set(4, L + 3, 12, Blocks.hangingSign("birch", 0, "black", false, "", "민원실"));
        Furniture.chair(f, 3, L, 10, "north", "dark_oak");
        Furniture.chair(f, 5, L, 10, "north", "dark_oak");
        Furniture.sofa(f, r, 1, L, bd - 2, 3, "north");
        entrance(v, (x1 + 1) / 2 + 2, 3, "", name);
        // 2층: 사무실
        L = lv[1];
        for (int j = 2; j <= bd - 3; j += 3) {
            for (int i = 2; i <= x1 - 2; i += 3) {
                Furniture.desk(f, i, L, j, "south");
            }
        }
        v.set(5, L + 2, 1, Blocks.hangingSign("birch", 0, "black", false, "", "보안과"));
        // 3층: 소장실과 회의실
        L = lv[2];
        // (x1 줄은 계단실 문 앞 복도로 비워 둠)
        Interior.room(f, 0, 0, x1 - 1, 7, L, 4, Interior.INNER_WALL, "south", 5, "dark_oak", "소장실");
        Furniture.desk(f, 4, L, 3, "north");
        Furniture.sofa(f, r, 3, L, 6, 3, "north");
        v.fill(1, L, 1, 1, L + 1, 6, Furniture.BOOKSHELF);
        Block glass = Block.of("white_stained_glass_pane", 0xF0F0F0);
        v.walls(1, L, 9, x1 - 1, L + 2, bd - 2, glass);
        Interior.door(f, 5, L, 9, "pale_oak", "south");
        Furniture.table(f, 3, L, 11, x1 - 5, 1, "dark_oak");
        v.set(5, L + 1, bd - 2, Furniture.TV);
        v.set(5, L + 2, 11, Interior.LIGHT);
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 의료동

    /** 의료동 (권장 19×16): 1층 진료실·처치실·약제실·대기실, 2층 병실 */
    static Voxels infirmary(int w, int d, Random r) {
        int[] lv = {0, 4, 8};
        Voxels v = new Voxels(w, d, -1, 12);
        int bd = d - 1;
        shell(v, lv, POLISHED_ANDESITE);
        windows(v, lv, 3, true, true);
        Frame f = Frame.of(v);
        Frame sf = Frame.facing(v, w - 7, 6, "east");
        Interior.stairCore(sf, lv, Interior.CORE_WALL, "polished_andesite", 0x848685);
        stairRoof(sf, lv[2], 5, Interior.stairDepth(lv));
        Interior.restroom(Frame.facing(v, w - 2, 9, "west"), 0, 0, 3, 5, 0, 4, "화장실", 2);
        int x1 = w - 9;
        Block white = Block.of("white_concrete", 0xCFD5D6);
        // 1층
        int L = 0;
        Interior.floor(f, 1, 1, x1, bd - 2, L, Block.of("light_gray_concrete", 0x7D7D73));
        // (x1 줄은 계단실 문 앞 복도로 비워 둠)
        Interior.room(f, 0, 0, 5, 6, L, 4, white, "south", 3, "pale_oak", "진료실");
        Furniture.desk(f, 2, L, 2, "west");
        v.set(3, L, 5, Block.of("white_wool", 0xE9ECEC));
        v.set(4, L, 5, Block.of("white_wool", 0xE9ECEC));
        Interior.room(f, 5, 0, x1 - 1, 6, L, 4, white, "south", 7, "pale_oak", "처치실");
        v.set(6, L, 2, Block.of("white_wool", 0xE9ECEC));
        v.set(6, L, 3, Block.of("white_wool", 0xE9ECEC));
        v.set(8, L, 1, CAULDRON);
        v.fill(1, L, 9, 4, L, 9, Furniture.COUNTER); // 약제실 창구
        v.set(2, L + 2, 9, Blocks.hangingSign("birch", 0, "black", false, "", "약제실"));
        Furniture.sofa(f, r, 6, L, bd - 3, 3, "north");
        Interior.lights(f, 1, 7, x1, bd - 2, L, 4, 4, Interior.LIGHT);
        v.set(3, L + 2, 3, Interior.LIGHT);
        v.set(8, L + 2, 3, Interior.LIGHT);
        entrance(v, (x1 + 1) / 2 + 2, 2, "", "의료과");
        // 2층: 병실 둘
        L = lv[1];
        Interior.floor(f, 1, 1, x1, bd - 2, L, Block.of("light_gray_concrete", 0x7D7D73));
        v.walls(0, L, 0, x1 - 1, L + 2, 6, white);
        Rooms.ward(new Frame(v, x1 - 2, 5, 2), r, x1 - 2, 5, L, 4); // 문은 남쪽(복도) 벽
        f.set(3, L + 1, 7, Blocks.wallSign("birch", "south", "black", false, "", "병실"));
        Interior.lights(f, 1, 7, x1, bd - 2, L, 4, 4, Interior.LIGHT);
        v.fill(1, L, bd - 3, 4, L, bd - 3, Furniture.COUNTER); // 간호 근무대
        Furniture.chair(f, 2, L, bd - 2, "south", "dark_oak");
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 노역장

    /** 노역장 (권장 28×17): 높은 한 층, 봉제·목공 작업대, 자재, 감독석 */
    static Voxels workshop(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 9);
        int bd = d - 1, H = 7;
        v.fill(0, -1, 0, w - 1, -1, bd - 1, POLISHED_ANDESITE);
        v.fill(0, -1, bd, w - 1, -1, bd, SMOOTH_STONE);
        v.walls(0, 0, 0, w - 1, H - 1, bd - 1, WALL);
        v.walls(0, 0, 0, w - 1, 0, bd - 1, PLINTH);
        v.walls(0, H - 1, 0, w - 1, H - 1, bd - 1, TRIM);
        // 지붕: 낮은 박공 (긴 변을 따라 용마루), 회색 패널
        Block roof = Block.of("light_gray_concrete", 0x7D7D73);
        for (int j = 0; j < bd; j++) {
            int rise = Math.min(j, bd - 1 - j) / 3;
            v.fill(0, H + rise, j, w - 1, H + rise, j, roof);
            if (rise > 0) {
                v.fill(0, H, j, 0, H + rise - 1, j, WALL);
                v.fill(w - 1, H, j, w - 1, H + rise - 1, j, WALL);
            }
        }
        // 높은 창 (창살)
        for (int i = 2; i < w - 2; i += 3) {
            v.fill(i, 3, 0, i, 4, 0, IRON_BARS);
            v.fill(i, 3, bd - 1, i, 4, bd - 1, IRON_BARS);
        }
        // 큰 문 (가운데)과 사람 문
        int mid = w / 2;
        v.fill(mid - 2, 0, bd - 1, mid + 1, 3, bd - 1, AIR);
        v.fill(mid - 3, 4, bd, mid + 2, 4, bd, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
        v.set(mid + 3, 3, bd, Blocks.wallSign("dark_oak", "south", "white", false, "", "노역장", "제1작업장"));
        Frame f = Frame.of(v);
        // 봉제반 (서쪽): 베틀 + 의자 줄
        for (int j = 2; j <= bd - 7; j += 3) {
            for (int i = 2; i <= mid - 4; i += 2) {
                v.set(i, 0, j, Block.of("loom[facing=south]", 0x8E7A5A));
                Furniture.chair(f, i, 0, j + 1, "south", "spruce");
            }
        }
        // 목공반 (동쪽): 작업대 줄과 공구
        for (int j = 2; j <= bd - 7; j += 4) {
            for (int i = mid + 3; i <= w - 4; i++) {
                v.set(i, 0, j, (i - mid) % 4 == 0 ? Block.of("crafting_table", 0x8A6A44) : Furniture.DESK_TOP);
            }
            v.set(w - 3, 0, j + 1, Blocks.STONECUTTER);
            v.set(mid + 3, 0, j + 1, Block.of("grindstone[face=floor,facing=south]", 0x8E8E8E));
        }
        // 자재 더미와 통
        v.fill(w - 3, 0, bd - 4, w - 2, 1, bd - 3, OAK_PLANKS);
        v.fill(w - 6, 0, bd - 3, w - 5, 0, bd - 3, BARREL);
        v.fill(1, 0, 2, 1, 1, 4, Block.of("white_wool", 0xE9ECEC)); // 원단
        // 감독석 (문 옆 유리 칸)
        v.fill(mid - 7, 0, bd - 5, mid - 7, 2, bd - 2, PANE);
        v.fill(1, 0, bd - 5, mid - 7, 2, bd - 5, PANE);
        v.set(mid - 7, 0, bd - 3, AIR);
        v.set(mid - 7, 1, bd - 3, AIR);
        Furniture.desk(f, mid - 9, 0, bd - 3, "north");
        // 등 (지붕 바로 아래)
        for (int j = 3; j < bd - 1; j += 5) {
            int y = H + Math.min(j, bd - 1 - j) / 3 - 1;
            for (int i = 3; i < w - 2; i += 5) {
                if (!v.solid(i, y, j)) {
                    v.set(i, y, j, Interior.LIGHT);
                }
            }
        }
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 식당

    /** 식당 (권장 20×17): 긴 식탁, 배식대, 주방 */
    static Voxels messHall(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 7);
        int bd = d - 1, H = 5;
        v.fill(0, -1, 0, w - 1, -1, bd - 1, Block.of("light_gray_concrete", 0x7D7D73));
        v.fill(0, -1, bd, w - 1, -1, bd, SMOOTH_STONE);
        v.walls(0, 0, 0, w - 1, H - 1, bd - 1, WALL);
        v.walls(0, 0, 0, w - 1, 0, bd - 1, PLINTH);
        v.fill(0, H, 0, w - 1, H, bd - 1, SLAB);
        v.walls(0, H, 0, w - 1, H, bd - 1, TRIM);
        v.walls(0, H + 1, 0, w - 1, H + 1, bd - 1, TRIM);
        windows(v, new int[]{0, H}, 3, true, true);
        Frame f = Frame.of(v);
        // 주방 (뒤쪽) 과 배식대
        v.fill(1, 0, 5, w - 2, 0, 5, Furniture.COUNTER);
        v.fill(1, 1, 5, w - 2, 1, 5, PANE);
        for (int i = 3; i < w - 2; i += 4) {
            v.set(i, 1, 5, AIR); // 배식구
        }
        v.fill(1, 2, 5, w - 2, 3, 5, INNER);
        v.set(w - 2, 0, 5, AIR);
        v.set(w - 2, 1, 5, AIR);
        Interior.door(f, w - 2, 0, 5, "pale_oak", "north");
        for (int i = 1; i < w - 1; i++) {
            Block b = i % 4 == 1 ? Block.of("smoker[facing=south,lit=false]", 0x555451) : i % 4 == 3 ? CAULDRON : Furniture.COUNTER;
            v.set(i, 0, 1, b);
        }
        Interior.lights(f, 1, 1, w - 2, 4, 0, H, 4, Interior.LIGHT);
        f.set(3, 2, 6, Blocks.wallSign("birch", "south", "black", false, "", "배식대"));
        // 긴 식탁 (가운데 통로 남김)
        int mid = w / 2;
        for (int j = 8; j <= bd - 4; j += 4) {
            for (int i = 2; i <= w - 3; i++) {
                if (Math.abs(i - mid) <= 1) {
                    continue;
                }
                v.set(i, 0, j, Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
                Furniture.chair(f, i, 0, j - 1, "north", "oak");
                Furniture.chair(f, i, 0, j + 1, "south", "oak");
            }
        }
        Interior.lights(f, 1, 6, w - 2, bd - 2, 0, H, 4, Interior.LIGHT);
        entrance(v, mid, 2, "", "식당");
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 망루

    /**
     * 망루 (9×11): 좁은 계단실 (5×7) 위에 사방 유리창 감시실 (y 8..10). 입구는 남쪽.
     * 감시실 바닥 y 7, 지붕 y 11, 지붕 위 탐조등.
     */
    static Voxels guardTower() {
        int[] lv = {0, 4, 8};
        Voxels v = new Voxels(9, 11, -1, 13);
        Block body = Block.of("light_gray_concrete", 0x7D7D73);
        Frame sf = new Frame(v, 5, 7, 2);
        Interior.stairCore(sf, lv, body, "polished_andesite", 0x848685, 1);
        v.fill(2, -1, 2, 6, -1, 8, POLISHED_ANDESITE);
        // 감시실
        for (int j = 0; j <= 10; j++) {
            for (int i = 0; i <= 8; i++) {
                v.setIfEmpty(i, 7, j, SLAB); // 계단실 안은 그대로
            }
        }
        v.walls(0, 8, 0, 8, 10, 10, Block.of("glass", 0xC8DCE4));
        v.walls(0, 8, 0, 8, 8, 10, body);
        v.fill(0, 11, 0, 8, 11, 10, SLAB);
        v.walls(0, 11, 0, 8, 11, 10, TRIM);
        for (int[] c : new int[][]{{0, 0}, {8, 0}, {0, 10}, {8, 10}}) {
            v.fill(c[0], 8, c[1], c[0], 10, c[1], body);
        }
        v.set(7, 8, 9, Furniture.DESK_TOP);
        v.set(4, 10, 1, Interior.LIGHT);
        v.set(4, 12, 5, Block.of("sea_lantern", 0xACC7BE)); // 탐조등
        v.set(6, 12, 5, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC07050));
        v.connect();
        return v;
    }

    private Prison() {
    }
}
