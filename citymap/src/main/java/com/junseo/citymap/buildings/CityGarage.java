package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 시청 차고지 (환경미화 일의 출발점): 울타리 친 마당에 청소차 차고, 2층 사무동, 적환장.
 * <ul>
 *   <li>차고: 셔터를 말아 올린 차고 칸 여섯(번호판), 칸마다 바닥 선, 안쪽 공구 벽과 기름통, 타이어 선반.
 *       차는 두지 않습니다.</li>
 *   <li>사무동(2층): 1층 배차 사무실(책상, 배차판), 2층 환경미화원 휴게실(사물함, 탁자, 소파)과 샤워실. 계단.</li>
 *   <li>적환장: 기둥과 지붕만 있는 큰 창고 아래 콘크리트 칸막이 칸(쓰레기 봉투 더미), 큰 컨테이너 두 개,
 *       재활용 분리수거함, 음식물 수거통.</li>
 *   <li>마당: 아스팔트, 청소차 주차 칸 선, 세차장 배수구, 울타리와 정문 기둥 이름판.</li>
 * </ul>
 * 건물 기준 정면(정문)은 남쪽 (j = d-1).
 */
final class CityGarage {
    static final int W = 60, D = 48;
    static final int[] OFFICE_LEVELS = {0, 4, 8};

    static Voxels build(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 14);
        Frame f = Frame.of(v);
        Block asphalt = Block.of("gray_concrete", 0x36393D);
        v.fill(0, -1, 0, w - 1, -1, d - 1, asphalt);
        // 울타리: 낮은 돌담 + 철창, 정문 (가운데 12칸)
        int g0 = w / 2 - 6, g1 = w / 2 + 5;
        for (int i = 0; i < w; i++) {
            for (int j : new int[]{0, d - 2}) {
                if (j == d - 2 && i >= g0 && i <= g1) {
                    continue;
                }
                v.set(i, 0, j, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
                v.set(i, 1, j, IRON_BARS);
                v.set(i, 2, j, IRON_BARS);
            }
        }
        for (int j = 0; j < d - 1; j++) {
            for (int i : new int[]{0, w - 1}) {
                v.set(i, 0, j, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
                v.set(i, 1, j, IRON_BARS);
                v.set(i, 2, j, IRON_BARS);
            }
        }
        for (int i : new int[]{g0 - 1, g1 + 1}) {
            v.fill(i, 0, d - 2, i, 3, d - 2, Block.of("stone_bricks", 0x7A7979));
        }
        v.set(g1 + 1, 2, d - 1, Blocks.wallSign("dark_oak", "south", "white", false, "", "시청 차고지"));
        v.set(g0 - 1, 2, d - 1, Blocks.wallSign("dark_oak", "south", "white", false, "", "환경미화", "작업장"));
        // 열어 둔 미닫이 문 (울타리 안쪽에 겹쳐 둠)
        for (int i = g1 + 2; i <= g1 + 8 && i < w - 1; i++) {
            v.fill(i, 0, d - 3, i, 2, d - 3, IRON_BARS);
        }

        garage(v, f, r, 2, 2, 41, 17);
        office(v, f, r, 43, 2, w - 3, 15);
        transfer(v, f, r, 2, 24, 27, d - 6);
        // 마당: 청소차 주차 칸 선 (차는 없음), 세차장 배수구
        for (int i = 31; i + 4 <= w - 3; i += 5) {
            v.fill(i, -1, 26, i, -1, 36, WHITE_CONCRETE);
        }
        v.fill(w - 3, -1, 26, w - 3, -1, 36, WHITE_CONCRETE);
        for (int i = 33; i <= 37; i++) {
            v.set(i, -1, 40, Block.of("iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
        }
        v.fill(31, 0, 41, 31, 1, 41, Block.of("light_gray_concrete", 0x7D7D73));
        v.set(31, 1, 42, Blocks.wallSign("birch", "south", "black", false, "", "세차장"));
        // 마당 등
        for (int i = 6; i < w - 4; i += 12) {
            v.fill(i, 0, 21, i, 5, 21, StreetPlan.POST);
            v.set(i, 6, 21, Block.of("lantern[hanging=false,waterlogged=false]", 0x6A5B49));
        }
        v.connect();
        return v;
    }

    /** 차고 (i0..i1, j0..j1 겉벽 포함), 칸 입구는 남쪽 */
    private static void garage(Voxels v, Frame f, Random r, int i0, int j0, int i1, int j1) {
        int top = 7;
        Block wall = Block.of("light_gray_concrete", 0x7D7D73);
        Block roof = Block.of("blue_concrete", 0x2C2E8F);
        v.fill(i0, -1, j0, i1, -1, j1, Block.of("light_gray_concrete", 0x7D7D73));
        v.walls(i0, 0, j0, i1, top, j1, wall);
        v.fill(i0, top + 1, j0, i1, top + 1, j1, roof);
        int bays = 6, bw = (i1 - i0 - 1) / bays;
        for (int b = 0; b < bays; b++) {
            int a0 = i0 + 1 + b * bw, a1 = a0 + bw - 2;
            v.fill(a0, 0, j1, a1, 5, j1, AIR);                    // 셔터 올린 입구
            v.fill(a0, 6, j1, a1, 6, j1, Block.of("iron_block", 0xDCDCDC));   // 셔터 통
            v.fill(a0 - 1, 0, j1 - 1, a0 - 1, 5, j1 - 1, IRON_BARS);         // 셔터 레일
            v.fill(a1 + 1, 0, j1 - 1, a1 + 1, 5, j1 - 1, IRON_BARS);
            v.set((a0 + a1) / 2, 7, j1 + 1, Blocks.wallSign("birch", "south", "black", true, "", (b + 1) + "번 차고"));
            // 칸 바닥 선과 등
            v.fill(a0, -1, j0 + 1, a0, -1, j1 - 1, Block.of("yellow_concrete", 0xF0AF15));
            v.set((a0 + a1) / 2, top, (j0 + j1) / 2, Interior.LIGHT);
            // 안쪽 벽: 공구판, 기름통, 타이어 선반
            v.set(a0 + 1, 1, j0 + 1, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            v.set(a0 + 2, 0, j0 + 1, BARREL);
            if (b % 2 == 0) {
                v.fill(a1, 0, j0 + 1, a1, 1, j0 + 2, Block.of("black_concrete", 0x080A0F));   // 타이어 더미
            }
        }
        // 칸 사이 기둥 (벽 대신)
        for (int b = 1; b < bays; b++) {
            int a = i0 + b * bw;
            v.fill(a, 0, j0 + 1, a, top, j1 - 1, AIR);
            v.fill(a, 0, j1, a, top, j1, wall);
            v.fill(a, 0, (j0 + j1) / 2, a, top, (j0 + j1) / 2, wall);
        }
        v.fill(i0 + 1, 0, j0 + 2, i0 + 1, 0, j0 + 4, Block.of("smooth_stone", 0x9E9E9E));   // 작업대
    }

    /** 2층 사무동: 1층 배차 사무실, 2층 휴게실·샤워실, 계단 (출입구 남쪽) */
    private static void office(Voxels v, Frame f, Random r, int i0, int j0, int i1, int j1) {
        int[] lv = OFFICE_LEVELS;
        int roof = lv[lv.length - 1];
        Block wall = Block.of("white_concrete", 0xCFD5D6);
        for (int k = 0; k < lv.length; k++) {
            v.fill(i0, lv[k] - 1, j0, i1, lv[k] - 1, j1, k == lv.length - 1 ? GREEN_CONCRETE : SMOOTH_STONE);
        }
        v.walls(i0, 0, j0, i1, roof - 1, j1, wall);
        v.walls(i0, roof, j0, i1, roof, j1, wall);
        v.fill(i0 + 1, 0, j0 + 1, i1 - 1, roof - 2, j1 - 1, AIR);
        for (int k = 1; k < lv.length - 1; k++) {
            v.fill(i0 + 1, lv[k] - 1, j0 + 1, i1 - 1, lv[k] - 1, j1 - 1, SMOOTH_STONE);
        }
        // 창
        for (int k = 0; k < lv.length - 1; k++) {
            for (int i = i0 + 2; i < i1 - 1; i += 3) {
                v.fill(i, lv[k] + 1, j1, i, lv[k] + 2, j1, GLASS_PANE);
            }
            for (int j = j0 + 2; j < j1 - 1; j += 3) {
                v.fill(i1, lv[k] + 1, j, i1, lv[k] + 2, j, GLASS_PANE);
            }
        }
        // 계단 (서쪽 벽 안쪽, 1칸 줄): 출입구가 남쪽 벽 안 복도 줄(j1-1)을 봄
        Frame st = Frame.facing(v, i0 + 3, j1 - 3, "north");
        int sd = Interior.stairDepth(lv);
        Interior.stairCore(st, lv, Interior.CORE_WALL, "stone_brick", 0x7A7979, 1);
        st.fill(-1, roof + 3, -1, 3, roof + 3, sd, SMOOTH_STONE);
        int sn = j1 - 3 - sd;          // 계단실 북쪽 벽 줄
        int[] stairBox = {i0, sn, i0 + 4, j1 - 2};
        // 문 (남쪽)
        Interior.door(f, i0 + 6, 0, j1, "spruce", "north");
        v.set(i0 + 7, 2, j1 + 1, Blocks.wallSign("birch", "south", "black", false, "", "사무동"));
        // 1층 배차 사무실
        int a0 = i0 + 5;
        for (int i = a0 + 1; i < i1 - 1; i += 3) {
            Furniture.desk(f, i, 0, j0 + 4, "south");
        }
        v.fill(i1 - 1, 1, j0 + 2, i1 - 1, 2, j0 + 6, WHITE_CONCRETE);   // 배차판
        v.set(i1 - 2, 2, j0 + 4, Blocks.wallSign("birch", "west", "black", false, "배차표", "1·2호차 시청", "3·4호차 명동", "5·6호차 서울역"));
        Site.lights(v, i0 + 1, j0 + 1, i1 - 1, j1 - 1, lv[1] - 2, 3, stairBox);
        // 2층 휴게실 (사물함, 탁자, 소파)과 샤워실 (계단실 북쪽)
        int L = lv[1];
        for (int j = j0 + 1; j <= j0 + 6; j++) {
            v.fill(i1 - 1, L, j, i1 - 1, L + 1, j, Block.of("light_gray_concrete", 0x7D7D73));
        }
        Furniture.table(f, a0 + 2, L, j0 + 5, 3, 1, "oak");
        Furniture.sofa(f, r, a0 + 1, L, j1 - 3, 3, "north");
        if (sn - 1 >= j0 + 1) {
            v.fill(a0, L, j0 + 1, a0, L + 2, sn, Interior.INNER_WALL);
            Interior.door(f, a0, L, j0 + 2, "pale_oak", "east");
            v.set(a0 + 1, L + 1, j0 + 3, Blocks.wallSign("birch", "east", "black", false, "", "샤워실"));
            Interior.floor(f, i0 + 1, j0 + 1, a0 - 1, sn - 1, L, Interior.TILE);
            v.set(i0 + 1, L, j0 + 1, CAULDRON);
            v.set(i0 + 1, L + 2, j0 + 1, Block.of("tripwire_hook[attached=false,facing=south,powered=false]", 0x8F8F8F));
        }
        Site.lights(v, i0 + 1, j0 + 1, i1 - 1, j1 - 1, lv[2] - 2, 3, stairBox);
        // 옥상 난간
        v.walls(i0, roof + 1, j0, i1, roof + 1, j1, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
    }

    /** 적환장: 기둥·지붕, 칸막이 칸에 쓰레기 봉투 더미, 컨테이너, 분리수거함 */
    private static void transfer(Voxels v, Frame f, Random r, int i0, int j0, int i1, int j1) {
        int top = 7;
        Block roof = Block.of("light_gray_concrete", 0x7D7D73);
        v.fill(i0, -1, j0, i1, -1, j1, Block.of("smooth_stone", 0x9E9E9E));
        for (int i = i0; i <= i1; i += 5) {
            for (int j : new int[]{j0, j1}) {
                v.fill(i, 0, j, i, top - 1, j, Block.of("iron_block", 0xDCDCDC));
            }
        }
        v.fill(i0 - 1, top, j0 - 1, i1 + 1, top, j1 + 1, roof);
        // 콘크리트 칸막이 칸 셋 (북쪽 벽에 붙음)
        Block bunker = Block.of("gray_concrete", 0x36393D);
        int bj = j0 + 7;
        v.fill(i0 + 1, 0, j0 + 1, i1 - 1, 2, j0 + 1, bunker);
        for (int i = i0 + 1; i <= i1 - 1; i += 8) {
            v.fill(i, 0, j0 + 1, i, 2, bj, bunker);
        }
        for (int i = i0 + 2; i <= i1 - 2; i++) {
            if ((i - i0 - 1) % 8 == 0) {
                continue;
            }
            for (int j = j0 + 2; j <= bj - 1; j++) {
                int h = (j < bj - 2 ? 2 : 1) - ((i + j) % 4 == 0 ? 1 : 0);
                for (int y = 0; y < h; y++) {
                    int roll = Math.floorMod(i * 7 + j * 13 + y * 3, 10);
                    v.set(i, y, j, roll < 4 ? Block.of("black_wool", 0x141519) : roll < 7 ? Block.of("gray_wool", 0x3E4447)
                            : roll < 9 ? Block.of("white_wool", 0xE9ECEC) : COARSE_DIRT);
                }
            }
        }
        // 큰 컨테이너 둘 (위가 뚫린 쇠 상자)
        for (int c = 0; c < 2; c++) {
            int a = i0 + 2 + c * 9, b = bj + 3;
            v.fill(a, 0, b, a + 6, 1, b + 2, Block.of("green_concrete", 0x495B24));
            v.fill(a + 1, 1, b + 1, a + 5, 1, b + 1, Block.of("gray_wool", 0x3E4447));
        }
        // 분리수거함 (플라스틱·캔·유리·종이)과 음식물 수거통
        String[] bins = {"blue", "yellow", "green", "white", "light_gray", "light_gray"};
        for (int k = 0; k < bins.length; k++) {
            v.set(i0 + 2 + k * 2, 0, j1 - 2, Blocks.concrete(bins[k]));
        }
        v.fill(i0 + 1, 0, j1 - 2, i0 + 1, 1, j1 - 2, Block.of("light_gray_concrete", 0x7D7D73));
        v.set(i0 + 1, 1, j1 - 1, Blocks.wallSign("birch", "south", "black", false, "", "분리수거"));
        v.set((i0 + i1) / 2, top - 1, (j0 + j1) / 2, Interior.LIGHT);
        v.set(i0 + 4, 2, j0 + 2, Blocks.wallSign("birch", "south", "black", false, "", "적환장"));
        for (int i = i0 + 4; i < i1; i += 8) {
            v.set(i, top - 1, (j0 + j1) / 2 + 3, Interior.LIGHT);
        }
    }
}
