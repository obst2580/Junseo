package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 63빌딩 (은행 본점·금융 타워). 실제 63빌딩처럼 금빛 유리로 덮은 날씬한 초고층(지상 60층, 약 249m).
 * <ul>
 *   <li>평면: 너비 62 × 깊이 30, 뒤쪽 면이 양 끝에서 비스듬히 들어간 사다리꼴(비스듬한 양 끝면).
 *       위쪽 열 층은 양 끝이 계단처럼 몇 번 들어가서 꼭대기가 좁아집니다.</li>
 *   <li>바깥: 금색 유리 커튼월, 금빛 세로 멀리언, 화강석 1층 띠, 정문 차양.</li>
 *   <li>1층(높이 8): 준서은행 본점 영업부 — 번호가 붙은 창구, 번호표 발행기, 대기 의자, 365 자동화코너(ATM),
 *       안내·보안 데스크, 직원 구역(지점장실, 후선 사무실, 창구 뒤 업무석). 직원 문 안쪽 계단으로 지하 1층.</li>
 *   <li>지하 1층: 금고(두꺼운 벽, 열린 원형 강철 문, 현금 철망 칸과 선반, 금괴), 현금 정리실, 대여금고실, 보안실.
 *       현금 수송 일은 여기서 시작합니다.</li>
 *   <li>2층: 은행 상담실, 3~57층 사무실(중간 기계실 두 층), 58·59층 하늘 식당, 60층 전망대. 가운데 코어의
 *       꺾인 계단으로 옥상까지 걸어서 오르고, 엘리베이터 두 대와 층마다 남녀 화장실.</li>
 * </ul>
 * 건물 기준 정면은 남쪽(j = d-1, 넓은 면). 상자는 지하 1층까지 내려갑니다.
 */
final class SixtyThree {
    static final int W = 62, D = 30, FLOORS = 60;
    /** 뒤쪽 면이 한쪽 끝에서 들어간 칸 수 */
    private static final int SLANT = 6;
    /** 지하 1층 서는 높이 (바닥 블록은 그 아래 칸) */
    static final int B1 = -5;
    private static final int BOTTOM = B1 - 2;

    static final Block GOLD_GLASS = Block.of("yellow_stained_glass", 0xD9B43A);
    static final Block GOLD_MULLION = Block.of("gold_block", 0xE8C04A);
    private static final Block GRANITE = POLISHED_GRANITE;
    private static final Block VAULT_WALL = Block.of("polished_deepslate", 0x484849);
    private static final Block STEEL = IRON_BLOCK;
    private static final Block CARPET = Block.of("gray_carpet", 0x3E4447);

    /** 층별 서는 높이 (0 = 1층, 맨 끝 = 옥상) */
    static int[] levels() {
        return Floors.levels(Floors.HALL, Floors.OFFICE, FLOORS);
    }

    /** 위층에서 양 끝이 계단처럼 들어가는 칸 수 */
    static int setback(int k) {
        if (k >= 59) {
            return 8;
        }
        if (k >= 56) {
            return 6;
        }
        if (k >= 53) {
            return 4;
        }
        return k >= 50 ? 2 : 0;
    }

    static boolean inside(int w, int d, int i, int j, int k) {
        int slant = (int) Math.round(SLANT * (d - 1 - j) / (double) (d - 1));
        int in = slant + setback(k);
        return j >= 0 && j < d && i >= in && i <= w - 1 - in;
    }

    static Voxels build(int w, int d, Random r) {
        Tower.Spec s = new Tower.Spec(w, d, FLOORS);
        s.lobbyH = Floors.HALL;
        s.shape = (i, j, k) -> inside(w, d, i, j, k);
        s.glass = GOLD_GLASS;
        s.mullion = GOLD_MULLION;
        s.spandrel = GOLD_GLASS;
        s.podium = GRANITE;
        s.elevators = 2;
        s.restrooms = 2;
        s.mullionEvery = 3;
        s.name = "63빌딩";
        s.use = k -> switch (k) {
            case 0, 1, 57, 58 -> Tower.Use.CUSTOM;
            case 21, 41 -> Tower.Use.MECH;
            case FLOORS - 1 -> Tower.Use.OBSERVATORY;
            default -> Tower.Use.OFFICE;
        };
        s.custom = (t, k, level, h) -> {
            switch (k) {
                case 0 -> bankHall(t, level, h);
                case 1 -> consult(t, level, h);
                default -> restaurant(t, k, level, h);
            }
        };
        Tower t = Tower.build(s, r);
        // 지하층까지 담는 큰 상자로 옮김
        Voxels v = new Voxels(w, d, BOTTOM, t.v.y0 + t.v.h + 12);
        for (int y = t.v.y0; y < t.v.y0 + t.v.h; y++) {
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    Block b = t.v.get(i, y, j);
                    if (b != null) {
                        v.set(i, y, j, b);
                    }
                }
            }
        }
        frontage(t, v);
        basement(t, v);
        top(t, v);
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 1층 은행 영업부

    /** 1층 칸 좌표 기준값 (코어 둘레) */
    private static void bankHall(Tower t, int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        Random r = new Random(t.r.nextLong());
        int w = t.s.w, d = t.s.d, top = level + h - 2;
        int mid = w / 2;
        int c0 = t.ci0, c1 = t.ci1, cj1 = t.cj1, cor = t.corridorJ;
        int front0 = cor + 2;          // 앞 홀 시작 줄
        // 바닥: 화강석 테두리 + 밝은 대리석
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (t.inside(i, j, 0) && !t.edge(i, j, 0) && (i < c0 || i > c1 || j < t.cj0 || j > cj1)) {
                    v.set(i, level - 1, j, (i + j) % 7 == 0 ? POLISHED_DIORITE : Block.of("smooth_quartz", 0xECE6DF));
                }
            }
        }
        // 정문 안 방풍실 (유리 상자)
        Block pane = Block.of("glass_pane", 0xC8DCE4);
        for (int j = d - 4; j <= d - 2; j++) {
            v.fill(mid - 3, level, j, mid - 3, level + 2, j, pane);
            v.fill(mid + 3, level, j, mid + 3, level + 2, j, pane);
        }
        v.fill(mid - 3, level, d - 4, mid + 3, level + 2, d - 4, pane);
        v.fill(mid - 1, level, d - 4, mid + 1, level + 2, d - 4, AIR);
        v.fill(mid - 3, level + 3, d - 4, mid + 3, level + 3, d - 2, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));

        // ---- 직원 구역 경계
        Block wall = Interior.INNER_WALL;
        int left = c0 - 1;            // 왼쪽 직원 구역 동쪽 경계 (코어 왼쪽 복도 바깥)
        // 왼쪽: 자동화코너와 직원 구역 사이 벽 (front0 - 1 줄)
        for (int i = 0; i <= left; i++) {
            if (interior(t, i, front0 - 1)) {
                v.fill(i, level, front0 - 1, i, top, front0 - 1, wall);
            }
        }
        // 코어 앞 복도 왼쪽 끝: 직원 문
        v.fill(left, level, cor, left, top, cor + 1, wall);
        Interior.door(f, left, level, cor, "dark_oak", "east");
        v.set(left + 1, level + 2, cor + 1, Blocks.wallSign("dark_oak", "east", "white", false, "", "직원 전용"));
        if (v.get(left + 1, level + 2, cor + 1) == null) {
            v.set(left + 1, level + 2, cor + 1, Blocks.wallSign("dark_oak", "east", "white", false, "", "직원 전용"));
        }

        // ---- 오른쪽: 창구 (서쪽을 봄), 창구 뒤 업무석
        int counterI = c1 + 6;                 // 창구 줄
        int cja = t.cj0 + 1, cjb = d - 4;       // 창구가 놓이는 j 범위
        // 손님 통로 북쪽 끝 막기
        for (int i = c1 + 1; i < counterI; i++) {
            v.fill(i, level, cja - 1, i, top, cja - 1, wall);
        }
        int window = 1;
        for (int j = cja; j <= cjb; j++) {
            v.set(counterI, level, j, Furniture.COUNTER);
            boolean edgeOfWindow = (j - cja) % 2 == 0;
            v.set(counterI, level + 1, j, edgeOfWindow ? Block.of("white_stained_glass_pane", 0xF0F0F0) : AIR);
            v.fill(counterI, level + 3, j, counterI, top, j, Block.of("white_concrete", 0xCFD5D6));
            if (!edgeOfWindow) {
                // 창구 번호판 (손님 쪽)
                v.set(counterI - 1, level + 3, j, Blocks.wallSign("birch", "west", "black", true, "", window + "번 창구"));
                Furniture.chair(f, counterI - 1, level, j, "west", "birch");   // 손님 의자
                Furniture.chair(f, counterI + 1, level, j, "east", "dark_oak"); // 직원 의자
                v.set(counterI, level + 1, j, Block.of("iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
                window++;
            }
        }
        v.set(counterI, level + 2, cja, Block.of("white_concrete", 0xCFD5D6));
        // 창구 줄 남쪽 끝 벽 (직원 구역 닫기)
        for (int i = counterI; i < w; i++) {
            if (interior(t, i, cjb + 1)) {
                v.fill(i, level, cjb + 1, i, top, cjb + 1, wall);
            }
        }
        // 창구 뒤 업무석 (모니터 책상 줄)
        for (int j = cja; j <= cjb; j += 3) {
            if (interior(t, counterI + 3, j)) {
                Furniture.desk(f, counterI + 3, level, j, "west");
            }
        }

        // ---- 앞 홀: 대기 의자, 번호표 발행기, 안내·보안 데스크
        for (int j = front0 + 1; j <= d - 3; j += 2) {
            for (int i = mid + 6; i <= counterI - 3; i++) {
                if ((i - mid) % 6 != 0 && interior(t, i, j)) {
                    Furniture.chair(f, i, level, j, "west", "dark_oak");
                }
            }
        }
        v.set(mid + 5, level, d - 3, Block.of("light_gray_concrete", 0x7D7D73));
        v.set(mid + 5, level + 1, d - 3, Block.of("stone_button[face=floor,facing=south,powered=false]", 0x7E7E7E));
        v.set(mid + 5, level + 1, d - 4, Blocks.wallSign("birch", "north", "black", false, "", "번호표", "뽑는 곳"));
        v.set(mid + 5, level + 1, d - 4, null);
        v.set(mid + 5, level, d - 4, Block.of("light_gray_concrete", 0x7D7D73));
        v.set(mid + 5, level + 1, d - 4, Blocks.wallSign("birch", "west", "black", false, "", "번호표"));
        v.set(mid + 4, level + 1, d - 4, Blocks.wallSign("birch", "west", "black", false, "", "번호표"));
        v.set(mid + 5, level + 1, d - 4, null);
        // 대기 안내 화면 (창구 위 벽 가운데)
        // 안내·보안 데스크 (정문 왼쪽)
        for (int i = mid - 9; i <= mid - 5; i++) {
            v.set(i, level, d - 6, Furniture.COUNTER);
        }
        Furniture.chair(f, mid - 7, level, d - 7, "north", "dark_oak");
        Furniture.chair(f, mid - 6, level, d - 7, "north", "dark_oak");
        v.set(mid - 8, level + 1, d - 6, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        v.set(mid - 7, level + 3, d - 6, Blocks.hangingSign("dark_oak", 0, "white", true, "", "안내·보안"));
        v.set(mid - 7, level + 4, d - 6, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
        v.fill(mid - 7, level + 5, d - 6, mid - 7, top, d - 6, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
        // 층별 안내판 (엘리베이터 사이 벽)
        String[] dir = {"63빌딩 안내", "1층 준서은행 본점", "58·59층 식당", "60층 전망대"};
        v.set(c0 + 12, level + 1, cor, Blocks.wallSign("dark_oak", "south", "white", true, dir));

        // ---- 365 자동화코너 (앞 왼쪽): 유리 칸막이, ATM 줄, 바깥 문
        int atmEnd = left;
        for (int i = 0; i <= atmEnd; i++) {
            if (interior(t, i, front0) && i % 2 == 0) {
                atm(v, i, level, front0, "south");
            }
        }
        for (int j = front0; j <= d - 2; j++) {
            if (j < front0 + 1 || j > front0 + 3) {
                v.fill(atmEnd + 1, level, j, atmEnd + 1, level + 2, j, pane);
            }
        }
        v.fill(atmEnd + 1, level + 3, front0, atmEnd + 1, top, d - 2, Block.of("white_concrete", 0xCFD5D6));
        v.set(atmEnd / 2 + 2, level + 2, front0, Blocks.wallSign("birch", "south", "blue", true, "", "365", "자동화코너"));
        // 바깥에서 바로 들어오는 문 (밤에도 씀)
        int atmDoor = atmEnd / 2 + 4;
        v.set(atmDoor, level, d - 1, Blocks.door("iron", "south", false));
        v.set(atmDoor, level + 1, d - 1, Blocks.door("iron", "south", true));
        v.set(atmDoor, level, d - 1, AIR);
        v.set(atmDoor, level + 1, d - 1, AIR);

        // ---- 왼쪽 직원 구역: 지하 금고로 내려가는 계단, 지점장실
        Frame stair = stairFrame(t);
        Interior.stairCore(stair, new int[]{B1, level}, Interior.CORE_WALL, "polished_andesite", 0x848685);
        // 계단 위 표지 (직원 구역 쪽)
        int si = stair.i(0, -1) + 1, sj = stair.j(0, -1) + 1;
        if (v.get(si, level + 2, sj) == null) {
            v.set(si, level + 2, sj, Blocks.wallSign("dark_oak", "south", "white", false, "", "지하 1층", "금고"));
        }
        // 지점장실 (계단 북쪽)
        int sb = stair.j(0, Interior.stairDepth(level - B1)) - 1;   // 계단실 북쪽 벽 바로 위 줄
        int ma0 = 0, ma1 = left - 1;
        while (!interior(t, ma0, sb - 1) && ma0 < ma1) {
            ma0++;
        }
        if (sb - 2 >= 2) {
            for (int i = ma0; i <= ma1; i++) {
                v.fill(i, level, sb, i, top, sb, wall);
            }
            Interior.door(f, ma1 - 1, level, sb, "dark_oak", "south");
            v.set(ma1, level + 1, sb + 1, Blocks.wallSign("birch", "south", "black", false, "", "지점장실"));
            int dj = Math.max(2, sb - 3);
            for (int i = ma0 + 2; i <= ma0 + 4 && i < ma1; i++) {
                v.set(i, level, dj, Furniture.DESK_TOP);
            }
            Furniture.chair(f, ma0 + 3, level, dj - 1, "north", "dark_oak");
            v.set(ma0 + 2, level + 1, dj, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            Furniture.plant(f, r, ma1 - 1, level, 2 + (sb - 2) / 2);
            v.set((ma0 + ma1) / 2, top, (sb + 2) / 2, Interior.LIGHT);
        }

        // ---- 코어 뒤 후선 사무실
        Rooms.office(f, r, c0 - 2, 0, c1 + 2, t.cj0 - 3, level, h, (i, j) -> t.free(i, j, 0) && j < t.cj0 - 2);

        // 큰 홀 등
        for (int j = 2; j < d - 1; j += 5) {
            for (int i = 2; i < w - 1; i += 6) {
                if (t.free(i, j, 0) && v.get(i, top, j) == null) {
                    v.set(i, top, j, Interior.LIGHT);
                }
            }
        }
        // 화분
        Furniture.plant(f, r, mid - 4, level, d - 2);
        Furniture.plant(f, r, mid + 4, level, d - 2);
    }

    /** 1층·지하를 잇는 직원 계단 좌표계 (안쪽 a 0..4, b 0..깊이-1, 출입구가 남쪽) */
    private static Frame stairFrame(Tower t) {
        int oi = t.ci0 - 4, oj = t.corridorJ - 1;
        return Frame.facing(t.v, oi, oj, "north");
    }

    /** ATM 한 대 (몸통, 화면·투입구, 위 덮개) */
    private static void atm(Voxels v, int i, int level, int j, String face) {
        v.set(i, level, j, Block.of("light_gray_concrete", 0x7D7D73));
        v.set(i, level + 1, j, Block.of("dropper[facing=" + face + ",triggered=false]", 0x6E6E6E));
        v.set(i, level + 2, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
    }

    /** 바깥 벽·코어가 아닌 바닥 칸 */
    private static boolean interior(Tower t, int i, int j) {
        return t.inside(i, j, 0) && !t.edge(i, j, 0) && !(i >= t.ci0 && i <= t.ci1 && j >= t.cj0 && j <= t.cj1);
    }

    // ------------------------------------------------------------------ 2층 상담실, 하늘 식당

    private static void consult(Tower t, int level, int h) {
        Frame f = Frame.of(t.v);
        Random r = new Random(t.r.nextLong());
        int d = t.s.d;
        // 앞쪽 줄: 유리 칸막이 상담실, 뒤쪽은 사무실
        int b0 = t.corridorJ + 3, b1 = d - 2;
        int[] bb = t.bounds(1);
        for (int a = bb[0] + 3; a + 6 <= bb[2] - 2; a += 7) {
            if (!t.free(a, b0, 1) || !t.free(a + 6, b1 - 1, 1)) {
                continue;
            }
            Rooms.meeting(f, a, b0, a + 6, b1 - 1, level, h);
        }
        Rooms.office(f, r, 0, 0, t.s.w - 1, t.cj0 - 2, level, h, (i, j) -> t.free(i, j, 1) && j < t.cj0 - 2);
        t.v.set(t.ci0 + 12, level + 1, t.corridorJ, Blocks.wallSign("dark_oak", "south", "white", true, "", "준서은행", "자산관리 상담"));
    }

    private static void restaurant(Tower t, int k, int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        Random r = new Random(t.r.nextLong());
        int[] b = t.bounds(k);
        Interior.floor(f, b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, level, Block.of("dark_oak_planks", 0x432B14));
        // 창가 2인·4인 식탁
        for (int j = b[1] + 2; j <= b[3] - 2; j += 3) {
            for (int i = b[0] + 2; i <= b[2] - 3; i += 4) {
                if (t.free(i, j, k) && t.free(i + 1, j, k) && t.free(i, j - 1, k) && t.free(i, j + 1, k)
                        && v.get(i, level, j) == null && v.get(i + 1, level, j) == null) {
                    v.set(i, level, j, Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
                    v.set(i + 1, level, j, Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
                    Furniture.chair(f, i, level, j - 1, "north", "spruce");
                    Furniture.chair(f, i + 1, level, j + 1, "south", "spruce");
                    v.set(i, level + 1, j, Block.of("potted_red_tulip", 0x9E5A3A));
                }
            }
        }
        // 주방 카운터 (코어 왼쪽 뒤)
        for (int i = t.ci0 - 2; i <= t.ci0 + 6; i++) {
            if (t.inside(i, t.cj0 - 3, k) && !t.edge(i, t.cj0 - 3, k)) {
                v.set(i, level, t.cj0 - 3, i % 3 == 0 ? Block.of("smoker[facing=north,lit=false]", 0x555451) : Furniture.COUNTER);
            }
        }
        Interior.lights(f, b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, level, h, 5, Block.of("lantern[hanging=true,waterlogged=false]", 0x6A5B49));
        if (k == 57) {
            v.set(t.ci0 + 12, level + 1, t.corridorJ, Blocks.wallSign("dark_oak", "south", "yellow", true, "", "구름 위 식당", "58·59층"));
        }
        Furniture.plant(f, r, t.ci0 - 2, level, t.corridorJ + 2);
    }

    // ------------------------------------------------------------------ 정문·1층 마감

    private static void frontage(Tower t, Voxels v) {
        int w = t.s.w, d = t.s.d, mid = w / 2;
        // 1층 화강석 띠 (유리 아래 한 줄)와 정문
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (t.edge(i, j, 0)) {
                    v.set(i, 0, j, GRANITE);
                }
            }
        }
        for (int i = mid - 2; i <= mid + 2; i++) {
            v.fill(i, 0, d - 1, i, 2, d - 1, AIR);
            v.set(i, 3, d - 1, GOLD_MULLION);
        }
        // 정문 안내 글씨 (차양 아래 유리 안쪽)
        v.set(mid, 3, d - 2, Blocks.wallSign("dark_oak", "south", "white", true, "", "63빌딩", "준서은행 본점"));
        // 2층 높이 띠: 화강석
        int y2 = t.levels[1] - 1;
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (t.edge(i, j, 0)) {
                    v.set(i, y2, j, GRANITE);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 지하 1층

    private static void basement(Tower t, Voxels v) {
        Frame f = Frame.of(v);
        Random r = new Random(t.r.nextLong());
        int L = B1, top = -2;
        Frame st = stairFrame(t);
        // 계단실 바깥 상자: a -1..5, b -1..깊이
        int depth = Interior.stairDepth(-B1);
        int sx0 = Math.min(st.i(-1, -1), st.i(5, depth)), sx1 = Math.max(st.i(-1, -1), st.i(5, depth));
        int sz0 = Math.min(st.j(-1, -1), st.j(5, depth)), sz1 = Math.max(st.j(-1, -1), st.j(5, depth));
        // 지하층 상자: 계단실 서쪽 벽부터 동쪽으로, 계단실 북쪽 벽부터 남쪽 정면 4칸 앞까지
        int x0 = sx0, x1 = t.ci1 - 4, z0 = sz0, z1 = t.s.d - 3;
        // 둘레 벽과 바닥·천장
        v.fill(x0, L - 1, z0, x1, L - 1, z1, Block.of("polished_andesite", 0x848685));
        v.fill(x0 + 1, L, z0 + 1, x1 - 1, top, z1 - 1, AIR);
        v.walls(x0, L, z0, x1, top, z1, Block.of("deepslate_tiles", 0x363637));
        v.fill(x0, -1, z0, x1, -1, z1, v.get(x0 + 2, -1, z1 - 1) == null ? GRANITE : v.get(x0 + 2, -1, z1 - 1));
        // 계단 (1층에서 그린 것을 다시: 지하 부분 벽이 지워졌을 수 있음)
        Interior.stairCore(st, new int[]{B1, 0}, Interior.CORE_WALL, "polished_andesite", 0x848685);
        // 복도: 계단 출입구 앞 동서로 2칸
        int cz0 = sz1 + 1, cz1 = sz1 + 2;
        // 남쪽 방들 (금고, 현금 정리실, 보안실): 복도 남쪽 벽 = cz1 + 1
        int wallZ = cz1 + 1;
        Block inner = Block.of("light_gray_concrete", 0x7D7D73);
        // 금고: 서쪽, 두꺼운 벽
        int vx0 = x0 + 1, vx1 = Math.min(x1 - 1, x0 + 16);
        v.fill(vx0, L, wallZ, vx1 + 1, top, wallZ, VAULT_WALL);
        v.fill(vx1 + 1, L, wallZ, vx1 + 1, top, z1 - 1, VAULT_WALL);
        v.fill(vx0, L, z1 - 1, vx1, top, z1 - 1, VAULT_WALL);
        v.fill(vx0, L, wallZ, vx0, top, z1 - 1, VAULT_WALL);
        Interior.floor(f, vx0 + 1, wallZ + 1, vx1, z1 - 2, L, Block.of("polished_deepslate", 0x484849));
        // 금고 문: 원형 강철 문틀, 열린 둥근 문 (문틀 동쪽에 수직으로)
        int dc = (vx0 + vx1) / 2;
        for (int y = L - 1; y <= top + 1; y++) {
            for (int i = dc - 3; i <= dc + 3; i++) {
                double dx = i - dc, dy = y - (L + 1);
                double rr = Math.hypot(dx, dy * 1.0);
                if (rr <= 3.2 && rr > 1.9 && y >= L && y <= top) {
                    v.set(i, y, wallZ, STEEL);
                }
            }
        }
        v.fill(dc - 1, L, wallZ, dc + 1, L + 2, wallZ, AIR);
        // 문짝 (두께 1, 지름 5): 문틀 동쪽 끝에 붙어 복도 쪽으로 열림
        int hinge = dc + 2;
        for (int y = L; y <= top; y++) {
            for (int jj = wallZ - 4; jj <= wallZ - 1; jj++) {
                double dz = jj - (wallZ - 2.5), dy = y - (L + 1.5);
                if (dz * dz + dy * dy <= 2.6 * 2.6) {
                    v.set(hinge, y, jj, STEEL);
                }
            }
        }
        v.set(hinge, L + 1, wallZ - 3, Block.of("iron_bars", 0x888888)); // 손잡이 바퀴
        v.set(dc - 2, L + 2, wallZ - 1, Blocks.wallSign("dark_oak", "north", "white", false, "", "금고", "관계자 외 출입금지"));
        // 금고 안: 현금 철망 칸(선반), 금괴, 현금 수레
        int in0 = vx0 + 1, in1 = vx1, jb0 = wallZ + 1, jb1 = z1 - 2;
        for (int i = in0; i <= in1; i++) {
            if (Math.abs(i - dc) <= 1) {
                continue;
            }
            // 뒷벽 선반: 두 단, 현금 다발 (만원 초록, 오만원 노랑)
            v.set(i, L, jb1, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
            v.set(i, L + 1, jb1, Block.of(i % 2 == 0 ? "lime_carpet" : "yellow_carpet", 0x70B919));
            v.set(i, L + 1, jb1, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
            v.set(i, L + 2, jb1, Block.of(i % 3 == 0 ? "yellow_carpet" : "lime_carpet", 0xF8C527));
            // 철망 칸 앞
            v.set(i, L, jb1 - 1, Block.of("lime_carpet", 0x70B919));
            v.set(i, L + 1, jb1 - 1, IRON_BARS);
            v.set(i, L + 2, jb1 - 1, IRON_BARS);
            v.set(i, L, jb1 - 1, IRON_BARS);
        }
        // 철망 칸 문 (열린 철 다락문)
        v.set(in0 + 1, L, jb1 - 1, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        v.set(in0 + 1, L + 1, jb1 - 1, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        // 금괴 받침
        for (int i = in0; i <= in0 + 2; i++) {
            v.set(i, L, jb0 + 1, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        }
        v.set(in0, L + 1, jb0 + 1, null);
        v.set(in0, L, jb0 + 1, GOLD_BLOCK);
        v.set(in0 + 1, L, jb0 + 1, GOLD_BLOCK);
        // 현금 수레 (수송 가방)
        v.set(in1 - 1, L, jb0 + 1, Block.of("black_wool", 0x141519));
        v.set(in1, L, jb0 + 1, Block.of("black_wool", 0x141519));
        v.set(in1 - 1, L + 1, jb0 + 1, Block.of("iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
        Interior.lights(f, in0, jb0, in1, jb1, L, top - L + 2, 4, Interior.LIGHT);

        // 현금 정리실과 보안실 (금고 동쪽)
        int rx0 = vx1 + 2, mx = (rx0 + x1) / 2;
        v.fill(rx0, L, wallZ, x1 - 1, top, wallZ, inner);
        v.fill(mx, L, wallZ, mx, top, z1 - 1, inner);
        Interior.door(f, rx0 + 2, L, wallZ, "iron", "north");
        f.set(rx0 + 2, L, wallZ, AIR);
        f.set(rx0 + 2, L + 1, wallZ, AIR);
        Interior.door(f, rx0 + 2, L, wallZ, "dark_oak", "north");
        Interior.door(f, mx + 2, L, wallZ, "dark_oak", "north");
        v.set(rx0 + 3, L + 1, wallZ - 1, Blocks.wallSign("birch", "north", "black", false, "", "현금 정리실"));
        v.set(mx + 3, L + 1, wallZ - 1, Blocks.wallSign("birch", "north", "black", false, "", "보안실"));
        // 현금 정리실: 탁자와 계수기
        for (int i = rx0 + 1; i < mx - 1; i += 3) {
            v.set(i, L, wallZ + 3, Furniture.WHITE_TOP);
            v.set(i + 1, L, wallZ + 3, Furniture.WHITE_TOP);
            v.set(i, L + 1, wallZ + 3, Block.of("light_gray_carpet", 0x8E8E86));
            Furniture.chair(f, i, L, wallZ + 2, "north", "dark_oak");
        }
        Interior.lights(f, rx0, wallZ + 1, mx - 1, z1 - 2, L, top - L + 2, 3, Interior.LIGHT);
        // 보안실: 감시 화면 벽과 의자
        for (int i = mx + 1; i < x1; i++) {
            v.set(i, L + 1, z1 - 1, Block.of("black_concrete", 0x080A0F));
            v.set(i, L + 2, z1 - 1, Block.of("black_concrete", 0x080A0F));
            v.set(i, L, z1 - 2, Furniture.DESK_TOP);
        }
        for (int i = mx + 2; i < x1 - 1; i += 2) {
            Furniture.chair(f, i, L, z1 - 3, "north", "dark_oak");
        }
        Interior.lights(f, mx, wallZ + 1, x1 - 1, z1 - 2, L, top - L + 2, 3, Interior.LIGHT);

        // 북쪽: 대여금고실 (계단실 동쪽, 복도 북쪽)
        int nx0 = sx1 + 1, nz1 = cz0 - 1;
        v.fill(nx0, L, nz1, x1 - 1, top, nz1, inner);
        Interior.door(f, nx0 + 3, L, nz1, "dark_oak", "south");
        v.set(nx0 + 4, L + 1, nz1 + 1, Blocks.wallSign("birch", "south", "black", false, "", "대여금고"));
        // 벽마다 작은 금고 문 (철 다락문)
        for (int i = nx0; i < x1; i++) {
            for (int y = L; y <= L + 2; y++) {
                v.set(i, y, z0 + 1, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            }
        }
        for (int i = nx0 + 3; i < x1 - 3; i += 6) {
            v.set(i, L, (z0 + nz1) / 2, Furniture.WHITE_TOP);
            v.set(i + 1, L, (z0 + nz1) / 2, Furniture.WHITE_TOP);
        }
        Interior.lights(f, nx0, z0 + 1, x1 - 1, nz1 - 1, L, top - L + 2, 4, Interior.LIGHT);
        // 복도 등
        for (int i = x0 + 3; i < x1; i += 4) {
            v.set(i, top, cz0, Interior.LIGHT);
        }
        v.set(x1 - 2, L, cz1, Block.of("black_wool", 0x141519)); // 현금 수송 가방
        Furniture.plant(f, r, x1 - 1, L, cz0);
    }

    // ------------------------------------------------------------------ 꼭대기

    private static void top(Tower t, Voxels v) {
        int roof = t.roofLevel;
        int[] b = t.bounds(FLOORS - 1);
        // 들어간 층의 테라스 가장자리 금빛 난간
        for (int k = 1; k < FLOORS; k++) {
            if (setback(k) == setback(k - 1)) {
                continue;
            }
            int y = t.levels[k];
            for (int j = 0; j < t.s.d; j++) {
                for (int i = 0; i < t.s.w; i++) {
                    if (t.edge(i, j, k - 1) && !t.inside(i, j, k)) {
                        v.set(i, y, j, GOLD_MULLION);
                    }
                }
            }
        }
        // 옥상 안테나 기둥과 항공 장애등
        int ci = (t.ci0 + t.ci1) / 2, cj = (t.cj0 + t.cj1) / 2;
        v.fill(ci, roof + 4, cj, ci, roof + 13, cj, Block.of("iron_bars", 0x888888));
        v.set(ci, roof + 14, cj, Block.of("shroomlight", 0xF09246));
        v.set(ci, roof + 15, cj, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
        // 지붕 모서리 장애등
        v.set(b[0] + 1, roof + 2, (b[1] + b[3]) / 2, Block.of("shroomlight", 0xF09246));
        v.set(b[2] - 1, roof + 2, (b[1] + b[3]) / 2, Block.of("shroomlight", 0xF09246));
    }

    private SixtyThree() {
    }
}
