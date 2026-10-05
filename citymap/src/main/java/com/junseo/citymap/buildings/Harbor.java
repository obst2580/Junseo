package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 구인천 항만 시설 그리기: 안벽 컨테이너 크레인(STS), 컨테이너와 장치장 블록, 조명탑, 정문(게이트), 하역 사무소,
 * 경비 초소, 등대, 테트라포드. 탈것(트럭·지게차·배) 모형은 두지 않고 자리만 둡니다.
 * 모두 건물 상자 좌표(정면 남쪽 = j 가 큰 쪽)입니다.
 */
final class Harbor {
    // 크레인 색: 흰 구조체, 남색 기계실, 붉은·흰 항공 장애 띠
    static final Block STRUCT = Block.of("white_concrete", 0xCFD5D6);
    static final Block NAVY = Block.of("blue_concrete", 0x2C2E8F);
    static final Block RED = Block.of("red_concrete", 0x8E2121);
    static final Block DARK = Block.of("gray_concrete", 0x36393D);
    static final Block YELLOW = Block.of("yellow_concrete", 0xF0AF15);
    static final Block BLACK = Block.of("black_concrete", 0x080A0F);
    static final Block GRATING = Block.of("polished_andesite", 0x848685);
    static final Block BARS = Block.of("iron_bars", 0x888888);
    static final Block GLASS = Block.of("glass_pane", 0xC8DCE4);
    static final Block BOLLARD = Block.of("polished_blackstone_wall", 0x353038);
    static final Block POLE = Block.of("andesite_wall", 0x888888);
    static final Block APRON = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block ASPHALT = Block.of("gray_concrete", 0x36393D);
    static final Block LINE = Block.of("white_concrete", 0xCFD5D6);
    static final Block RAIL = Block.of("polished_deepslate", 0x484849);
    static final Block OBSTRUCTION = Block.of("shroomlight", 0xF09246);

    // ------------------------------------------------------------------ 안벽 크레인

    /** 크레인 상자 너비 (안벽을 따라) */
    static final int CW = 32;
    /** 거더 밑 높이, 거더는 G..G+2, 거더 위 통로의 서는 높이 G+3 */
    static final int G = 33;
    /** 다리 자리 (i): 왼쪽 7..8, 오른쪽 23..24 */
    static final int LEG_L = 7, LEG_R = 23;
    /** 계단탑: 동쪽 벽 i = 6, 문은 j = STAIR_J */
    static final int STAIR_J = 8;
    /** 운전실 서는 높이 */
    static final int CAB = G - 4;
    /** 트롤리 (거더 사이 수레) 자리 j 범위 시작 */
    static final int TROLLEY_J = 17;

    /**
     * 안벽 컨테이너 크레인 (Ship-to-Shore). 상자 너비 {@link #CW}, 깊이 d (남쪽이 바다).
     * jL = 육지 쪽 레일 줄, jW = 바다 쪽 레일 줄 (상자 좌표). 다리 넷이 두 레일을 걸치고, 위에 거더가 육지 쪽 끝(백리치)부터
     * 바다 위 붐 끝까지 뻗습니다. 붐 위 A 프레임에서 앞·뒤 버팀줄. 백리치 위 기계실, 육지 쪽 레일 위에 세워 둔 트롤리와
     * 그 밑에 매단 운전실. 왼쪽 육지 쪽 다리 옆 철망 계단탑으로 거더 위 통로까지, 트롤리 안 계단으로 운전실까지 걸어갑니다.
     */
    static Voxels crane(int d, int jL, int jW, int no, Random r) {
        Voxels v = new Voxels(CW, d, -1, G + 18);
        int[] legs = {LEG_L, LEG_R};
        int[] rows = {jL, jW};
        // 바퀴 대차 (레일 위): 노란 범퍼, 회색 몸통, 검은 바퀴
        for (int li : legs) {
            for (int rj : rows) {
                v.fill(li - 2, 1, rj - 1, li + 3, 1, rj, DARK);
                for (int i = li - 2; i <= li + 3; i++) {
                    v.set(i, 0, rj, (i - li) % 2 == 0 ? Block.of("polished_blackstone", 0x353038) : DARK);
                    v.set(i, 0, rj - 1, DARK);
                }
                v.fill(li - 2, 0, rj - 1, li - 2, 1, rj, YELLOW);
                v.fill(li + 3, 0, rj - 1, li + 3, 1, rj, YELLOW);
                // 다리
                v.fill(li, 2, rj - 1, li + 1, G - 1, rj, STRUCT);
            }
        }
        // 문형 보 (가로): 아래 (트럭·해치 커버가 지나는 높이 위) 와 다리 꼭대기
        for (int rj : rows) {
            v.fill(LEG_L, 16, rj - 1, LEG_R + 1, 17, rj, STRUCT);
            v.fill(LEG_L, G - 2, rj - 1, LEG_R + 1, G - 1, rj, STRUCT);
        }
        // 옆 보 (육지 쪽 다리 ↔ 바다 쪽 다리) 와 대각 버팀대
        for (int li : legs) {
            v.fill(li, 16, jL + 1, li + 1, 17, jW - 2, STRUCT);
            v.rod(li + 1, 18.5, jL + 0.5, li + 1, G - 2.5, jW - 1.5, 0.7, STRUCT);
        }
        // 거더 (백리치 끝부터 붐 끝까지): 바다 쪽 다리를 지나 20칸까지는 3칸 높이, 그 뒤는 2칸으로 가늘어짐
        int tip = d - 1;
        for (int li : legs) {
            for (int j = 0; j <= tip; j++) {
                int bottom = j <= jW + 20 ? G : G + 1;
                v.fill(li, bottom, j, li + 1, G + 2, j, STRUCT);
            }
        }
        // 거더 사이 가로재 (밑)
        for (int j = 0; j <= tip; j += 6) {
            v.fill(LEG_L + 2, j <= jW + 20 ? G : G + 1, j, LEG_R - 1, j <= jW + 20 ? G : G + 1, j, STRUCT);
        }
        // 붐 끝 머리 (붉은·흰 띠, 장애등)
        v.fill(LEG_L, G + 1, tip - 1, LEG_R + 1, G + 2, tip, STRUCT);
        for (int i = LEG_L; i <= LEG_R + 1; i++) {
            v.set(i, G + 2, tip, (i / 2) % 2 == 0 ? RED : STRUCT);
            v.set(i, G + 1, tip, (i / 2) % 2 == 0 ? STRUCT : RED);
        }
        v.set(LEG_L, G + 3, tip, OBSTRUCTION);
        v.set(LEG_R + 1, G + 3, tip, OBSTRUCTION);
        // A 프레임 (바다 쪽 다리 위)
        int apex = G + 16;
        for (int li : legs) {
            v.fill(li, G + 3, jW - 1, li + 1, apex, jW, STRUCT);
            for (int y = apex - 3; y <= apex; y++) {
                Block band = ((apex - y) / 2) % 2 == 0 ? RED : STRUCT;
                v.fill(li, y, jW - 1, li + 1, y, jW, band);
            }
        }
        v.fill(LEG_L, apex - 1, jW - 1, LEG_R + 1, apex, jW, STRUCT);
        v.fill(LEG_L, G + 9, jW - 1, LEG_R + 1, G + 9, jW, STRUCT);
        v.set(LEG_L, apex + 1, jW, OBSTRUCTION);
        v.set(LEG_R + 1, apex + 1, jW, OBSTRUCTION);
        // 버팀줄: A 프레임 꼭대기 → 붐 가운데와 끝 (앞), → 백리치 끝 (뒤)
        for (int li : legs) {
            double ci = li + 1.0;
            v.rod(ci, apex - 0.5, jW - 0.5, ci, G + 3.0, jW + 20.5, 0.8, STRUCT);
            v.rod(ci, apex - 0.5, jW - 0.5, ci, G + 3.0, tip - 1.5, 0.8, STRUCT);
            v.rod(ci, apex - 0.5, jW - 1.5, ci, G + 9.5, 3.5, 0.8, STRUCT); // 기계실 지붕으로 (통로 위를 지나감)
        }
        // 기계실 (백리치 위): 남색 패널, 흰 띠, 문은 왼쪽 거더 통로 쪽
        int mh0 = G + 3, mh1 = G + 8;
        v.fill(LEG_L - 2, G + 2, 0, LEG_R + 3, G + 2, 6, GRATING);
        v.walls(LEG_L - 2, mh0, 0, LEG_R + 3, mh1, 6, NAVY);
        v.fill(LEG_L - 2, mh1 + 1, 0, LEG_R + 3, mh1 + 1, 6, STRUCT);
        v.fill(LEG_L - 2, mh0 + 3, 0, LEG_R + 3, mh0 + 3, 0, STRUCT);
        v.fill(LEG_L - 2, mh0 + 3, 6, LEG_R + 3, mh0 + 3, 6, STRUCT);
        for (int i = LEG_L + 3; i < LEG_R; i += 4) {
            v.set(i, mh0 + 1, 6, GLASS);
        }
        v.set(LEG_L + 1, mh0, 6, Blocks.door("iron", "south", false));
        v.set(LEG_L + 1, mh0 + 1, 6, Blocks.door("iron", "south", true));
        // 안: 권상 드럼, 제어반, 등
        for (int i = LEG_L + 2; i <= LEG_R - 2; i += 6) {
            v.fill(i, mh0, 2, i + 2, mh0 + 1, 3, Block.of("iron_block", 0xDCDCDC));
        }
        v.fill(LEG_L, mh0, 1, LEG_L, mh0 + 2, 4, Block.of("light_gray_concrete", 0x7D7D73));
        v.set((LEG_L + LEG_R) / 2, mh1 - 1, 3, Interior.LIGHT);
        // 트롤리 (육지 쪽 레일 위에 세워 둠): 거더 사이 바닥, 위에 권상 장치 집
        int t0 = TROLLEY_J, t1 = TROLLEY_J + 7;
        v.fill(LEG_L + 2, G + 2, t0, LEG_R - 1, G + 2, t1, GRATING);
        v.fill(LEG_L + 2, G, t0, LEG_R - 1, G + 1, t0, DARK);
        v.fill(LEG_L + 2, G, t1, LEG_R - 1, G + 1, t1, DARK);
        v.fill(LEG_L + 5, G + 3, t0 + 1, LEG_R - 4, G + 5, t1 - 1, Block.of("light_gray_concrete", 0x7D7D73));
        v.fill(LEG_L + 5, G + 6, t0 + 1, LEG_R - 4, G + 6, t1 - 1, STRUCT);
        // 운전실 (트롤리 밑에 매달림): 유리 벽, 유리 바닥 일부, 의자와 조종대
        int ci0 = LEG_L + 5, ci1 = LEG_L + 10, cj0 = t0 + 2, cj1 = t1 + 1;
        v.fill(ci0, CAB - 1, cj0, ci1, CAB + 3, cj1, STRUCT);
        v.fill(ci0 + 1, CAB, cj0 + 1, ci1 - 1, CAB + 2, cj1 - 1, AIR);
        for (int i = ci0 + 1; i < ci1; i++) {
            v.fill(i, CAB, cj1, i, CAB + 2, cj1, GLASS);
        }
        for (int j = cj0 + 1; j < cj1; j++) {
            v.fill(ci1, CAB, j, ci1, CAB + 2, j, GLASS);
            v.set(ci0, CAB + 1, j, GLASS);
        }
        v.fill(ci0 + 2, CAB - 1, cj1 - 2, ci1 - 2, CAB - 1, cj1 - 1, Block.of("glass", 0xC8DCE4));
        v.fill(ci0, CAB + 4, cj0, ci1, G - 1, cj1, DARK); // 매단 틀
        v.set(ci0 + 3, CAB, cj1 - 2, Block.of("dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x432B14));
        v.fill(ci0 + 2, CAB, cj1 - 1, ci1 - 1, CAB, cj1 - 1, Furniture.WHITE_TOP); // 조종대 (문 앞 한 칸은 비움)
        v.set(ci0 + 3, CAB + 2, cj0 + 1, Interior.LIGHT);
        // 트롤리 바닥에서 운전실로 내려가는 계단 (i = LEG_L + 3 .. +4, 남쪽으로 내려감)
        int si0 = LEG_L + 3, si1 = LEG_L + 4;
        int steps = (G + 3) - CAB;
        for (int k = 0; k <= steps; k++) {
            int j = t0 + k, stand = G + 3 - k;
            v.fill(si0, stand, j, si1, stand + 2, j, AIR);
            if (k > 0) {
                v.fill(si0, stand - 1, j, si1, stand - 1, j, Blocks.stairs("polished_andesite", "north", 0x848685));
            }
        }
        int lastJ = t0 + steps;
        v.fill(si0, CAB - 1, lastJ, ci0 - 1, CAB - 1, lastJ, GRATING);
        v.fill(si0, CAB, lastJ, ci0 - 1, CAB + 1, lastJ, AIR);
        // 운전실 문 (서쪽 벽, 계단 끝)
        v.set(ci0, CAB, lastJ, Blocks.door("waxed_copper", "west", false));
        v.set(ci0, CAB + 1, lastJ, Blocks.door("waxed_copper", "west", true));
        // 거더 위 통로 난간 (왼쪽 거더): 바깥 i = LEG_L - 1, 안쪽 i = LEG_L + 2 (트롤리·기계실 자리 빼고)
        for (int j = 7; j <= jW - 2; j++) {
            if (j < STAIR_J - 1 || j > STAIR_J + 3) {
                v.set(LEG_L - 1, G + 3, j, BARS);
            }
            if (j < t0 || j > t1) {
                v.set(LEG_L + 2, G + 3, j, BARS);
            }
        }
        // 계단탑 (왼쪽 육지 쪽 다리 뒤): 철망 벽, 꺾인 계단 1줄, 층마다 4칸 (꼭대기는 거더 위 통로)
        int[] lv = stairLevels();
        Frame sf = Frame.facing(v, LEG_L - 2, STAIR_J, "west");
        Interior.stairCore(sf, lv, BARS, "polished_andesite", 0x848685, 1);
        // 중간 층 문은 다시 막음 (바깥으로 떨어지지 않게): 맨 아래(땅)와 맨 위(통로)만 열어 둠
        for (int k = 1; k < lv.length - 1; k++) {
            sf.fill(0, lv[k], -1, 0, lv[k] + 2, -1, BARS);
        }
        sf.walls(-1, -1, -1, 3, -1, 5, APRON); // 탑 밑동 둘레는 바닥 그대로
        sf.set(0, lv[lv.length - 1] - 1, -1, GRATING); // 꼭대기 문턱 (철망 벽 대신 발판)
        sf.fill(-1, lv[lv.length - 1] + 3, -1, 3, lv[lv.length - 1] + 3, 5, GRATING);
        // 계단탑 아래 표지판
        v.set(LEG_L - 1, 2, STAIR_J + 4, Blocks.wallSign("birch", "south", "black", false, no + "호 크레인", "관계자 외", "승강 금지"));
        // 다리 밑 경고 띠
        for (int li : legs) {
            for (int rj : rows) {
                v.fill(li, 2, rj - 1, li + 1, 2, rj, YELLOW);
                v.fill(li, 3, rj - 1, li + 1, 3, rj, BLACK);
            }
        }
        v.connect();
        return v;
    }

    /** 크레인 계단탑 층 (서는 높이): 4칸마다, 마지막은 거더 위 통로 */
    static int[] stairLevels() {
        int top = G + 3;
        int n = top / 4 + (top % 4 == 0 ? 1 : 2);
        int[] lv = new int[n];
        for (int k = 0; k < n - 1; k++) {
            lv[k] = 4 * k;
        }
        lv[n - 1] = top;
        return lv;
    }

    // ------------------------------------------------------------------ 컨테이너

    /** 컨테이너 색: {몸통, 골판 줄} (선사·임대 회사 색을 흉내 낸 흔한 색, 너무 튀지 않게) */
    static final Block[][] BOX = {
            {Block.of("red_terracotta", 0x8F3D2E), Block.of("red_concrete", 0x7E2A22)},            // 녹 빨강 (임대)
            {Block.of("red_terracotta", 0x8F3D2E), Block.of("red_concrete", 0x7E2A22)},
            {Block.of("blue_concrete", 0x2C2E8F), Block.of("blue_concrete_powder", 0x464AA6)},      // 남색
            {Block.of("light_blue_concrete", 0x2389C7), Block.of("light_blue_concrete_powder", 0x4AB4D5)}, // 하늘색
            {Block.of("light_gray_concrete", 0x7D7D73), Block.of("light_gray_concrete_powder", 0x9A9A94)}, // 회색
            {Block.of("green_concrete", 0x495B24), Block.of("green_concrete_powder", 0x61772C)},    // 녹색
            {Block.of("brown_concrete", 0x603C20), Block.of("brown_terracotta", 0x4D3323)},         // 밤색
            {Block.of("gray_concrete", 0x36393D), Block.of("gray_concrete_powder", 0x4C5155)},      // 진회색
            {Block.of("orange_terracotta", 0xA15325), Block.of("orange_concrete", 0xC25A0E)},      // 주황
            {Block.of("white_concrete", 0xCFD5D6), Block.of("white_concrete_powder", 0xE2E4E4)},    // 흰색 (냉동)
            {Block.of("magenta_terracotta", 0x95576C), Block.of("pink_terracotta", 0xA14E4E)},     // 자주
    };
    static final int[] BOX_WEIGHT = {16, 10, 14, 10, 12, 9, 9, 7, 6, 4, 3};

    static Block[] boxColor(Random r) {
        int total = 0;
        for (int w : BOX_WEIGHT) {
            total += w;
        }
        int x = r.nextInt(total);
        for (int k = 0; k < BOX.length; k++) {
            x -= BOX_WEIGHT[k];
            if (x < 0) {
                return BOX[k];
            }
        }
        return BOX[0];
    }

    /**
     * 컨테이너 한 개: (i, y, j) 부터 i 쪽으로 len (40피트 12, 20피트 6), j 쪽으로 3, 높이 3.
     * 긴 옆면은 한 칸씩 골판 줄, 문 쪽 끝은 문 두 짝 사이 이음과 잠금대 줄. doorEast 면 i 끝에 문.
     */
    static void container(Voxels v, int i, int y, int j, int len, Block[] c, boolean doorEast) {
        Block body = c[0], rib = c[1];
        for (int a = 0; a < len; a++) {
            Block side = a % 2 == 1 ? rib : body;
            for (int b = 0; b < 3; b++) {
                for (int h = 0; h < 3; h++) {
                    boolean longSide = b == 0 || b == 2;
                    v.set(i + a, y + h, j + b, longSide ? side : body);
                }
            }
        }
        int door = doorEast ? i + len - 1 : i;
        for (int h = 0; h < 3; h++) {
            v.set(door, y + h, j, rib);
            v.set(door, y + h, j + 1, Block.of("iron_block", 0xB8B8B8)); // 잠금대
            v.set(door, y + h, j + 2, rib);
        }
        int far = doorEast ? i : i + len - 1;
        for (int b = 0; b < 3; b++) {
            v.set(far, y + 2, j + b, rib);
        }
    }

    /**
     * 장치장 블록 (w × d, d 는 3의 배수가 좋음): 컨테이너 줄(3칸)마다, 베이(40피트 12 + 틈 1)마다 1~4단.
     * 가운데 줄이 높고 가장자리(트럭이 붙는 쪽)는 낮게. 바닥은 아스팔트와 흰 칸 선.
     * reefer 면 흰 냉동 컨테이너 3단까지와 베이 사이 전원 랙(철골 탑과 위 통로).
     */
    static Voxels containerBlock(int w, int d, Random r, boolean reefer) {
        Voxels v = new Voxels(w, d, -1, 14);
        v.fill(0, -1, 0, w - 1, -1, d - 1, ASPHALT);
        int rows = d / 3;
        // 베이 나누기: 40피트(13칸) 위주, 남으면 20피트(7칸)
        java.util.List<int[]> bays = new java.util.ArrayList<>();
        int i = 0;
        while (i < w) {
            int left = w - i;
            if (left >= 12) {
                bays.add(new int[]{i, 12});
                i += 13;
            } else if (left >= 6) {
                bays.add(new int[]{i, 6});
                i += 7;
            } else {
                break;
            }
        }
        for (int[] bay : bays) {
            // 칸 끝 흰 선
            v.fill(bay[0] - 1, -1, 0, bay[0] - 1, -1, d - 1, LINE);
            v.fill(bay[0] + bay[1], -1, 0, bay[0] + bay[1], -1, d - 1, LINE);
            boolean doorEast = r.nextBoolean();
            for (int row = 0; row < rows; row++) {
                int j = row * 3;
                int edge = Math.min(row, rows - 1 - row);
                int max = reefer ? 3 : edge == 0 ? 2 + r.nextInt(2) : 3 + r.nextInt(2);
                int tiers = Math.max(reefer ? 2 : 1, max - (r.nextInt(5) == 0 ? r.nextInt(3) : 0));
                if (!reefer && r.nextInt(14) == 0) {
                    tiers = 0; // 빈 칸 (방금 실어 간 자리)
                }
                for (int t = 0; t < tiers; t++) {
                    Block[] c = reefer ? BOX[9] : boxColor(r);
                    if (bay[1] == 12 && !reefer && r.nextInt(6) == 0) {
                        // 20피트 두 개
                        container(v, bay[0], 3 * t, j, 6, c, false);
                        container(v, bay[0] + 6, 3 * t, j, 6, boxColor(r), true);
                    } else {
                        container(v, bay[0], 3 * t, j, bay[1], c, doorEast);
                    }
                }
            }
        }
        if (reefer) {
            // 전원 랙: 베이 사이 틈(1칸)에 철골 기둥, 맨 위에 철망 통로, 칸마다 전원 상자
            for (int[] bay : bays) {
                int gi = bay[0] + bay[1];
                if (gi >= w) {
                    continue;
                }
                for (int j = 0; j < d; j++) {
                    v.set(gi, 9, j, GRATING);
                    v.set(gi, 10, j, j % 2 == 0 ? BARS : null);
                    if (j % 3 == 1) {
                        v.fill(gi, 0, j, gi, 8, j, BARS);
                        v.set(gi, 4, j, Block.of("light_gray_concrete", 0x7D7D73)); // 전원 상자
                    }
                }
            }
        }
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 조명탑·볼라드

    /** 조명탑 (높이 h, 꼭대기에 투광등 넷): 콘크리트 받침, 가는 철 기둥, 네모 등 틀 */
    static void lightMast(Voxels v, int i, int j, int h) {
        v.set(i, 0, j, Block.of("polished_andesite", 0x848685));
        v.fill(i, 1, j, i, h, j, POLE);
        v.fill(i - 1, h + 1, j - 1, i + 1, h + 1, j + 1, BARS);
        v.set(i, h + 1, j, Block.of("iron_block", 0xB8B8B8));
        v.set(i - 1, h, j - 1, SEA_LANTERN);
        v.set(i + 1, h, j - 1, SEA_LANTERN);
        v.set(i - 1, h, j + 1, SEA_LANTERN);
        v.set(i + 1, h, j + 1, SEA_LANTERN);
    }

    // ------------------------------------------------------------------ 정문 (게이트)

    /**
     * 터미널 정문 (w × d, 정면 남쪽 = 바깥 길 쪽). 차로 4개 (서쪽 둘은 반입, 동쪽 둘은 반출 — 상자 좌표로는
     * 남쪽에서 들어오는 차가 오른쪽(동쪽 i 가 작은 쪽)... 놓는 방향에 맞춰 PortPlan 이 정함), 차로 사이 섬에 검수 부스,
     * 섬 끝 차단기 기둥(팔은 들어 올린 채), 높이 6 위 지붕(캐노피), 지붕 앞 이름 표지판, 차로마다 매단 표지판.
     * 섬은 너비 2, 차로는 너비 4 (섬 5 + 차로 4 = 26칸).
     */
    static Voxels gate(int w, int d, String[] laneNames, Random r) {
        Voxels v = new Voxels(w, d, -1, 9);
        v.fill(0, -1, 0, w - 1, -1, d - 1, ASPHALT);
        int lanes = laneNames.length;
        int c0 = 3, c1 = d - 4; // 지붕 덮는 j 범위
        for (int k = 0; k <= lanes; k++) {
            int ii = k * 6;
            // 섬: 연석 반 블록, 부스, 기둥
            v.fill(ii, -1, 1, ii + 1, -1, d - 2, Block.of("smooth_stone", 0x9E9E9E));
            v.fill(ii, 0, 1, ii + 1, 0, d - 2, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            // 기둥 (지붕 받침)
            v.fill(ii, 1, c0, ii, 5, c0, Block.of("light_gray_concrete", 0x7D7D73));
            v.fill(ii + 1, 1, c1, ii + 1, 5, c1, Block.of("light_gray_concrete", 0x7D7D73));
            if (k > 0 && k < lanes) {
                // 검수 부스 (섬 가운데, 유리)
                int bj0 = d / 2 - 2, bj1 = d / 2 + 1;
                v.fill(ii, 1, bj0, ii + 1, 3, bj1, WHITE_CONCRETE);
                v.fill(ii, 2, bj0, ii + 1, 2, bj1, Block.of("light_blue_stained_glass", 0x6699D8));
                v.fill(ii, 4, bj0, ii + 1, 4, bj1, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            }
            // 차단기: 섬 끝(안쪽 = j 작은 쪽) 노랑·검정 기둥과 들어 올린 팔
            if (k < lanes) {
                v.set(ii + 1, 1, 2, YELLOW);
                v.set(ii + 1, 2, 2, BLACK);
                v.fill(ii + 1, 3, 2, ii + 1, 5, 2, Block.of("end_rod[facing=up]", 0xE8E2D8));
            }
        }
        // 차로 선 (흰 실선), 정지선
        for (int k = 0; k < lanes; k++) {
            int i0 = k * 6 + 2;
            v.fill(i0, -1, d / 2 - 3, i0 + 3, -1, d / 2 - 3, LINE);
        }
        // 지붕 (캐노피): 아래 6, 흰 패널 테두리
        v.fill(0, 6, c0 - 1, w - 1, 6, c1 + 1, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
        v.fill(0, 7, c0 - 1, w - 1, 7, c0 - 1, WHITE_CONCRETE);
        v.fill(0, 7, c1 + 1, w - 1, 7, c1 + 1, WHITE_CONCRETE);
        v.fill(0, 7, c0 - 1, 0, 7, c1 + 1, WHITE_CONCRETE);
        v.fill(w - 1, 7, c0 - 1, w - 1, 7, c1 + 1, WHITE_CONCRETE);
        v.fill(0, 8, c1 + 1, w - 1, 8, c1 + 1, NAVY);
        // 지붕 밑 등
        for (int k = 0; k < lanes; k++) {
            v.set(k * 6 + 3, 5, d / 2, Interior.LIGHT);
            v.set(k * 6 + 4, 5, d / 2 - 3, Interior.LIGHT);
            // 차로 표지판 (지붕에 매달아 바깥 쪽을 봄)
            v.set(k * 6 + 3, 5, c1, Blocks.hangingSign("birch", 0, "black", false, "", laneNames[k]));
        }
        // 앞 이름판 (지붕 앞 남색 띠)
        int mid = w / 2;
        v.set(mid - 4, 7, c1 + 2, Blocks.wallSign("dark_oak", "south", "white", true, "", "구인천항"));
        v.set(mid, 7, c1 + 2, Blocks.wallSign("dark_oak", "south", "white", true, "", "컨테이너"));
        v.set(mid + 4, 7, c1 + 2, Blocks.wallSign("dark_oak", "south", "white", true, "", "터미널 정문"));
        // 속도 제한 표지판 (들어오는 섬 앞)
        v.set(1, 1, d - 1, Blocks.wallSign("birch", "south", "red", false, "제한속도", "10km", "서행"));
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 하역 사무소

    /**
     * 항만 하역 사무소 (터미널 운영동, 2층). 상자 w × d 중 건물은 j 0..d-2, 맨 앞 줄(j = d-1)은 현관 앞 포장과 이름 표지판.
     * 정면 남쪽 = 바깥 앞마당, 뒤 북쪽(j = 0) = 터미널 안.
     * 1층: 정문(양여닫이) → 대기 홀(의자·TV), 반출입·배차 접수 창구(카운터와 직원 책상), 화장실, 계단실,
     * 뒤 문(직원 출입 → 터미널). 2층: 터미널을 내다보는 관제실(뒤쪽 큰 창의 모니터 책상 줄과 영상 벽), 휴게실·사물함, 회의실.
     * 옥상: 계단탑, 난간, 실외기. 층고 4 (작은 사무 건물이라 1층도 4).
     */
    static Voxels office(int w, int d, Random r) {
        int[] lv = {0, 4, 8};
        int bd = d - 1;
        Voxels v = new Voxels(w, d, -1, 13);
        Block wall = WHITE_CONCRETE, band = Block.of("light_gray_concrete", 0x7D7D73);
        Block glass = Block.of("light_blue_stained_glass_pane", 0x6699D8);
        Frame f = Frame.of(v);
        for (int k = 0; k < 3; k++) {
            v.fill(0, lv[k] - 1, 0, w - 1, lv[k] - 1, bd - 1, k == 0 ? Block.of("polished_andesite", 0x848685) : band);
        }
        v.fill(0, -1, bd, w - 1, -1, bd, SMOOTH_STONE);
        for (int k = 0; k < 2; k++) {
            int L = lv[k];
            v.walls(0, L, 0, w - 1, L + 2, bd - 1, wall);
            // 띠창 (앞·뒤), 옆 창
            for (int i = 1; i < w - 1; i++) {
                if (i % 4 != 0) {
                    v.set(i, L + 1, bd - 1, glass);
                    v.set(i, L + 1, 0, glass);
                    if (k == 1) {
                        v.set(i, L + 2, 0, glass); // 관제실 큰 창
                    }
                }
            }
            for (int j = 2; j < bd - 2; j += 3) {
                v.set(0, L + 1, j, glass);
                v.set(w - 1, L + 1, j, glass);
            }
        }
        // 층 사이 남색 띠, 옥상 난간
        v.walls(0, 3, 0, w - 1, 3, bd - 1, NAVY);
        v.walls(0, 8, 0, w - 1, 8, bd - 1, band);
        v.walls(0, 9, 0, w - 1, 9, bd - 1, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        // 계단실: 북서쪽 (i 0..6, j 1..7), 문은 남쪽(홀 쪽, i 4..5)
        Frame sf = Frame.facing(v, 5, 6, "north");
        Interior.stairCore(sf, lv, Interior.CORE_WALL, "polished_andesite", 0x848685);
        sf.walls(-1, 9, -1, 5, 10, 5, wall);
        sf.fill(0, 9, -1, 1, 10, -1, AIR);
        sf.fill(-1, 11, -1, 5, 11, 5, band);
        int mid = w / 2;
        // ---- 1층
        int L = 0;
        Kit.doubleDoor(f, mid - 1, L, bd - 1, "dark_oak", "south");
        v.fill(mid - 3, L, bd - 1, mid - 2, L + 1, bd - 1, glass);
        v.fill(mid + 1, L, bd - 1, mid + 2, L + 1, bd - 1, glass);
        Interior.door(f, w - 3, L, 0, "pale_oak", "north");
        // 접수 창구: j = 6 줄 카운터 (동쪽 끝은 드나드는 틈), 뒤에 직원 책상
        int cj = 6;
        v.fill(7, L, cj, w - 5, L, cj, Furniture.COUNTER);
        for (int i = 8; i < w - 5; i += 3) {
            v.set(i, L + 1, cj, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            Furniture.chair(f, i, L, cj - 1, "north", "dark_oak");
        }
        for (int i = 8; i < w - 5; i += 4) {
            Furniture.desk(f, i, L, 2, "south");
        }
        v.fill(w - 2, L, 1, w - 2, L + 1, 3, Furniture.BOOKSHELF);
        v.set(w - 4, L + 1, 1, Blocks.wallSign("birch", "south", "black", false, "직원 출입구", "안전모·조끼", "착용"));
        v.set(mid, L + 2, cj + 1, Blocks.hangingSign("birch", 0, "black", false, "", "반출입 접수", "배차 창구"));
        // 대기 홀: 의자 줄, 벽 TV, 정수기, 화분
        Kit.seats(f, 8, 13, L, bd - 4, "north", "spruce");
        Kit.seats(f, w - 9, w - 5, L, bd - 4, "north", "spruce");
        f.set(w - 2, L + 1, bd - 4, Furniture.TV);
        f.set(w - 2, L + 1, bd - 5, Furniture.TV);
        v.set(w - 2, L, bd - 2, Block.of("iron_block", 0xDCDCDC));
        Furniture.plant(f, r, w - 2, L, cj + 1);
        // 화장실 (남서쪽, 문은 동쪽 홀로)
        Interior.restroom(Frame.facing(v, 1, bd - 2, "east"), 0, 0, 3, 2, L, 4, "화장실", 1);
        Interior.lights(f, 6, 1, w - 2, bd - 2, L, 4, 4, Interior.LIGHT);
        // ---- 2층: 관제실 (뒤 j 1..7), 휴게실 (앞 서쪽), 회의실 (앞 동쪽)
        L = lv[1];
        int split = 8;
        v.fill(6, L, split, w - 2, L + 2, split, Interior.INNER_WALL);
        v.fill(12, L, split + 1, 12, L + 2, bd - 2, Interior.INNER_WALL);
        Interior.door(f, 9, L, split, "pale_oak", "north");
        Interior.door(f, 18, L, split, "pale_oak", "north");
        v.set(10, L + 1, split - 1, Blocks.wallSign("birch", "north", "black", false, "", "휴게실"));
        v.set(19, L + 1, split - 1, Blocks.wallSign("birch", "north", "black", false, "", "회의실"));
        v.set(8, L + 1, split + 1, Blocks.wallSign("birch", "south", "black", false, "", "관제실"));
        // 관제실: 창(터미널)을 보는 책상 줄, 영상 벽을 보는 책상 줄
        for (int i = 7; i < w - 2; i++) {
            if (i % 3 != 0) {
                Furniture.desk(f, i, L, 1, "south");
            }
        }
        for (int i = 11; i <= 17; i++) {
            v.set(i, L + 1, split - 1, Furniture.TV);
            v.set(i, L + 2, split - 1, Furniture.TV);
            if (i % 2 == 1) {
                Furniture.desk(f, i, L, 5, "north");
            }
        }
        v.fill(w - 2, L, 2, w - 2, L + 1, 2, Block.of("gray_concrete", 0x36393D)); // 무전기 함
        // 휴게실: 소파, 탁자, 사물함
        Furniture.sofa(f, r, 7, L, bd - 2, 4, "north");
        v.fill(7, L, bd - 4, 8, L, bd - 4, Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
        v.fill(11, L, split + 1, 11, L + 1, bd - 3, Rooms.WARDROBE);
        Furniture.plant(f, r, 1, L, bd - 2);
        // 회의실
        Furniture.table(f, 15, L, (split + bd) / 2, 6, 1, "dark_oak");
        v.set(w - 2, L + 1, (split + bd) / 2, Furniture.TV);
        Interior.lights(f, 6, 1, w - 2, split - 1, L, 4, 4, Interior.LIGHT);
        v.set(5, L + 2, bd - 3, Interior.LIGHT);
        v.set(17, L + 2, bd - 3, Interior.LIGHT);
        // ---- 옥상: 실외기, 안테나
        for (int i = 9; i < w - 3; i += 4) {
            v.set(i, 8, 2, Block.of("light_gray_concrete", 0x7D7D73));
            v.set(i + 1, 8, 2, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        }
        v.fill(w - 3, 8, bd - 3, w - 3, 12, bd - 3, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xB87333));
        // 이름 표지판 (정문 옆 벽)
        v.set(mid - 4, 2, bd, Blocks.wallSign("dark_oak", "south", "white", false, "", "구인천항", "하역 사무소"));
        v.set(mid + 3, 2, bd, Blocks.wallSign("dark_oak", "south", "white", false, "", "컨테이너터미널", "운영동"));
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 작은 건물

    /** 경비 초소 (4×4, 정면 남쪽): 흰 벽, 세 면 유리, 책상, 문은 동쪽 */
    static void booth(Voxels v, int i0, int j0, String label) {
        v.fill(i0, -1, j0, i0 + 3, -1, j0 + 3, SMOOTH_STONE);
        v.walls(i0, 0, j0, i0 + 3, 2, j0 + 3, WHITE_CONCRETE);
        v.fill(i0 + 1, 1, j0 + 3, i0 + 2, 1, j0 + 3, GLASS);
        v.fill(i0 + 1, 1, j0, i0 + 2, 1, j0, GLASS);
        v.set(i0, 1, j0 + 1, GLASS);
        v.set(i0 + 3, 0, j0 + 2, Blocks.door("spruce", "east", false));
        v.set(i0 + 3, 1, j0 + 2, Blocks.door("spruce", "east", true));
        v.fill(i0 - 1, 3, j0 - 1, i0 + 4, 3, j0 + 4, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.set(i0 + 1, 0, j0 + 2, Furniture.DESK_TOP);
        v.set(i0 + 2, 0, j0 + 1, Block.of("dark_oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0x432B14));
        v.set(i0 + 2, 2, j0 + 2, Interior.LIGHT);
        v.set(i0 + 1, 2, j0 + 4, Blocks.wallSign("birch", "south", "black", false, "", label));
    }

    /**
     * 빨간 등대 (방파제 끝): 지름 5 원통 높이 12, 꼭대기 흰 띠와 철 난간 전망대, 붉은 유리 등실, 둥근 지붕.
     * (ci, cj) 가 가운데 칸, 문(철문, 잠김)은 north 쪽.
     */
    static void lighthouse(Voxels v, int ci, int cj, String doorSide) {
        Block red = RED, white = WHITE_CONCRETE;
        double cx = ci + 0.5, cz = cj + 0.5;
        v.cylinder(cx, cz, 2.6, -1, -1, Block.of("smooth_stone", 0x9E9E9E));
        v.cylinder(cx, cz, 2.6, 0, 0, Block.of("light_gray_concrete", 0x7D7D73));
        v.cylinder(cx, cz, 2.3, 1, 10, red);
        v.cylinder(cx, cz, 2.3, 9, 9, white);
        v.cylinder(cx, cz, 3.2, 11, 11, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.cylinder(cx, cz, 2.3, 11, 11, white);
        v.tube(cx, cz, 3.2, 0.9, 12, 12, BARS);
        v.cylinder(cx, cz, 1.6, 12, 14, Block.of("red_stained_glass", 0xA04040));
        v.set(ci, 13, cj, GLOWSTONE);
        v.set(ci, 12, cj, GLOWSTONE);
        v.cylinder(cx, cz, 1.9, 15, 15, red);
        v.set(ci, 16, cj, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xB87333));
        // 문 (철문: 관리인만)
        int[] dd = switch (doorSide) {
            case "north" -> new int[]{0, -2};
            case "south" -> new int[]{0, 2};
            case "east" -> new int[]{2, 0};
            default -> new int[]{-2, 0};
        };
        v.set(ci + dd[0], 1, cj + dd[1], Blocks.door("iron", doorSide, false));
        v.set(ci + dd[0], 2, cj + dd[1], Blocks.door("iron", doorSide, true));
        v.set(ci + dd[0], 0, cj + dd[1], Block.of("light_gray_concrete", 0x7D7D73));
    }

    /**
     * 테트라포드 더미 한 칸 (i, j): 회색 콘크리트 뿔 블록을 얼기설기 쌓음. top 은 가장 높은 y.
     * 물에 잠긴 아래쪽(y ≤ -2)은 이끼 낀 색, 사이 빈칸은 바닷물.
     */
    static void tetrapod(Voxels v, int i, int j, int top, long seed) {
        Random r = new Random(seed);
        for (int y = -4; y <= top; y++) {
            int roll = r.nextInt(10);
            Block b;
            if (y <= -3) {
                b = Block.of("mossy_cobblestone", 0x6E7661);
            } else if (roll < 3 && y >= -2) {
                b = null; // 틈
            } else if (roll < 6) {
                b = Block.of("smooth_stone", 0x9E9E9E);
            } else if (roll < 8) {
                String[] f = {"north", "east", "south", "west"};
                b = Block.of("andesite_stairs[facing=" + f[r.nextInt(4)] + ",half=" + (r.nextBoolean() ? "top" : "bottom")
                        + ",shape=straight,waterlogged=" + (y <= -2) + "]", 0x888888);
            } else {
                b = Block.of("polished_andesite", 0x848685);
            }
            if (b == null) {
                v.set(i, y, j, y <= -2 ? WATER : AIR);
            } else {
                v.set(i, y, j, y == -2 && b.id().contains("smooth_stone") ? Block.of("mossy_stone_bricks", 0x737969) : b);
            }
        }
        for (int y = top + 1; y <= -1; y++) {
            v.set(i, y, j, AIR); // 땅 윗면(모래)을 걷어 냄
        }
    }

    private Harbor() {
    }
}
