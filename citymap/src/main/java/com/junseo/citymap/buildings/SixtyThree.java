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

    /**
     * 1층 영업부. 앞 홀·코어 앞 복도·오른쪽 창구 앞 통로가 손님 구역이고,
     * 왼쪽(지하 금고 계단·지점장실), 코어 뒤(후선 사무실), 창구 뒤는 직원 구역입니다.
     */
    private static void bankHall(Tower t, int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        Random r = new Random(t.r.nextLong());
        int w = t.s.w, d = t.s.d, top = level + h - 2;
        int mid = w / 2;
        int c0 = t.ci0, c1 = t.ci1, cor = t.corridorJ;
        int front0 = cor + 2;          // 앞 홀 시작 줄
        int left = c0 - 1;             // 코어 왼쪽 복도 바깥 줄 (직원 문)
        Block wall = Interior.INNER_WALL;
        Block pane = Block.of("glass_pane", 0xC8DCE4);
        Block bulkhead = Block.of("white_concrete", 0xCFD5D6);
        Block monitor = Block.of("iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1);
        // 바닥: 밝은 대리석에 줄눈
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (interior(t, i, j)) {
                    v.set(i, level - 1, j, (i + j) % 7 == 0 ? POLISHED_DIORITE : Block.of("smooth_quartz", 0xECE6DF));
                }
            }
        }
        // 정문 안 방풍실 (유리 상자)
        for (int j = d - 4; j <= d - 2; j++) {
            v.fill(mid - 3, level, j, mid - 3, level + 2, j, pane);
            v.fill(mid + 3, level, j, mid + 3, level + 2, j, pane);
        }
        v.fill(mid - 3, level, d - 4, mid + 3, level + 2, d - 4, pane);
        v.fill(mid - 1, level, d - 4, mid + 1, level + 2, d - 4, AIR);
        v.fill(mid - 3, level + 3, d - 4, mid + 3, level + 3, d - 2, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));

        // ---- 직원 구역 경계: 자동화코너 뒤 벽, 코어 앞 복도 왼쪽 끝 직원 문
        for (int i = 0; i <= left; i++) {
            if (interior(t, i, front0 - 1)) {
                v.fill(i, level, front0 - 1, i, top, front0 - 1, wall);
            }
        }
        v.fill(left, level, cor, left, top, cor, wall);
        Interior.door(f, left, level, cor, "dark_oak", "east");
        v.set(left + 1, level + 2, cor, Blocks.wallSign("dark_oak", "east", "white", false, "", "직원 전용"));

        // ---- 오른쪽 창구: 서쪽(손님 통로)을 보고, 창구마다 번호판·손님 의자·직원 의자·모니터
        int counterI = c1 + 6;
        int cja = t.cj0 + 1, cjb = d - 4;
        for (int i = c1 + 1; i < counterI; i++) {
            v.fill(i, level, cja - 1, i, top, cja - 1, wall);   // 손님 통로 북쪽 끝
        }
        int window = 1;
        for (int j = cja; j <= cjb; j++) {
            v.set(counterI, level, j, Furniture.COUNTER);
            v.fill(counterI, level + 3, j, counterI, top, j, bulkhead);
            if ((j - cja) % 2 == 0) {
                v.set(counterI, level + 1, j, Block.of("white_stained_glass_pane", 0xF0F0F0)); // 창구 사이 칸막이
                v.set(counterI, level + 2, j, Block.of("white_stained_glass_pane", 0xF0F0F0));
            } else {
                v.set(counterI - 1, level + 3, j, Blocks.wallSign("birch", "west", "black", true, "", window + "번 창구"));
                Furniture.chair(f, counterI - 1, level, j, "west", "birch");
                Furniture.chair(f, counterI + 1, level, j, "east", "dark_oak");
                v.set(counterI, level + 1, j, monitor);
                window++;
            }
        }
        for (int i = counterI; i < w; i++) {
            if (interior(t, i, cjb + 1)) {
                v.fill(i, level, cjb + 1, i, top, cjb + 1, wall);   // 창구 뒤 남쪽 끝
            }
        }
        for (int j = cja + 1; j <= cjb; j += 3) {
            if (interior(t, counterI + 3, j) && interior(t, counterI + 4, j)) {
                Furniture.desk(f, counterI + 3, level, j, "west");   // 창구 뒤 업무석
            }
        }
        v.set(counterI - 1, level + 4, (cja + cjb) / 2, Blocks.wallSign("birch", "west", "blue", true, "준서은행", "영업부", "", "번호 순서대로"));

        // ---- 앞 홀: 대기 의자 (창구를 봄), 번호표 발행기, 안내·보안 데스크
        for (int j = front0 + 1; j <= d - 3; j += 2) {
            for (int i = mid + 6; i <= counterI - 3; i++) {
                if ((i - mid) % 6 != 0 && interior(t, i, j)) {
                    Furniture.chair(f, i, level, j, "west", "dark_oak");
                }
            }
        }
        v.fill(mid + 5, level, d - 3, mid + 5, level + 1, d - 3, Block.of("light_gray_concrete", 0x7D7D73));
        v.set(mid + 5, level + 2, d - 3, Block.of("stone_button[face=floor,facing=south,powered=false]", 0x7E7E7E));
        v.set(mid + 4, level + 1, d - 3, Blocks.wallSign("birch", "west", "black", false, "", "번호표"));
        for (int i = mid - 9; i <= mid - 5; i++) {
            v.set(i, level, d - 6, Furniture.COUNTER);
        }
        Furniture.chair(f, mid - 7, level, d - 7, "north", "dark_oak");
        Furniture.chair(f, mid - 6, level, d - 7, "north", "dark_oak");
        v.set(mid - 8, level + 1, d - 6, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        v.set(mid - 7, level + 3, d - 6, Blocks.hangingSign("dark_oak", 0, "white", true, "", "안내·보안"));
        v.fill(mid - 7, level + 4, d - 6, mid - 7, top, d - 6, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
        // 층별 안내판 (코어 앞, 계단 옆 벽)
        v.set(c0 + 6, level + 1, cor, Blocks.wallSign("dark_oak", "south", "white", true,
                "63빌딩 안내", "1층 준서은행 본점", "58·59층 식당", "60층 전망대"));

        // ---- 365 자동화코너 (앞 왼쪽): ATM 줄, 유리 칸막이 (바깥 문은 frontage 에서)
        for (int i = 0; i <= left; i++) {
            if (interior(t, i, front0) && i % 2 == 0) {
                atm(v, i, level, front0, "south");
            }
        }
        for (int j = front0; j <= d - 2; j++) {
            if (j < front0 + 1 || j > front0 + 3) {
                v.fill(left + 1, level, j, left + 1, level + 2, j, pane);
            }
        }
        v.fill(left + 1, level + 3, front0, left + 1, top, d - 2, bulkhead);
        v.set(left / 2 + 2, level + 3, front0, Blocks.wallSign("birch", "south", "blue", true, "", "365", "자동화코너"));

        // ---- 왼쪽 직원 구역: 지하 금고로 내려가는 계단 (코어 왼쪽 복도로 열림), 지점장실
        Frame st = stairFrame(t, v);
        int sd = Interior.stairDepth(level - B1);
        Interior.stairCore(st, new int[]{B1, level}, Interior.CORE_WALL, "polished_andesite", 0x848685);
        st.fill(-1, level + 3, -1, 5, level + 3, sd, Interior.CORE_WALL);
        v.set(left, level + 2, t.cj0 + 4, Blocks.wallSign("dark_oak", "east", "white", false, "지하 1층", "금고", "현금 수송"));
        // 지점장실 (계단실 북쪽): 동쪽 벽에 문
        int mi = left - 1, mj1 = t.cj0;
        for (int j = 0; j <= mj1; j++) {
            if (t.inside(mi, j, 0) && !t.edge(mi, j, 0)) {
                v.fill(mi, level, j, mi, top, j, wall);
            }
        }
        int dj = (mj1 + 1) / 2 + 1;
        Interior.door(f, mi, level, dj, "dark_oak", "east");
        v.set(mi + 1, level + 1, dj + 1, Blocks.wallSign("birch", "east", "black", false, "", "지점장실"));
        for (int j = dj - 2; j <= dj; j++) {
            v.set(mi - 4, level, j, Furniture.DESK_TOP);
        }
        Furniture.chair(f, mi - 5, level, dj - 1, "west", "dark_oak");
        v.set(mi - 4, level + 1, dj - 1, Block.of("iron_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        Furniture.sofa(f, r, mi - 3, level, mj1 - 1, 2, "north");
        Furniture.plant(f, r, mi - 1, level, 2);
        v.set(mi - 3, top, dj, Interior.LIGHT);

        // ---- 코어 뒤 후선 사무실
        Rooms.office(f, r, c0 - 2, 0, c1 + 2, t.cj0 - 3, level, h, (i, j) -> t.free(i, j, 0) && j < t.cj0 - 2);

        // 큰 홀 등과 화분
        for (int j = 2; j < d - 1; j += 5) {
            for (int i = 2; i < w - 1; i += 6) {
                if (t.free(i, j, 0) && v.get(i, top, j) == null) {
                    v.set(i, top, j, Interior.LIGHT);
                }
            }
        }
        Furniture.plant(f, r, mid - 4, level, d - 2);
        Furniture.plant(f, r, mid + 4, level, d - 2);
    }

    /**
     * 1층과 지하 1층을 잇는 직원 계단 좌표계: 코어 왼쪽 복도 바로 서쪽, 출입구(b = -1)가 동쪽(복도)을 봄.
     * 안쪽 a 0..4 (남쪽으로), b 0..깊이-1 (서쪽으로).
     */
    private static Frame stairFrame(Tower t, Voxels v) {
        return Frame.facing(v, t.ci0 - 3, t.cj0 + 2, "west");
    }

    /** ATM 한 대 (몸통, 화면·투입구, 위 덮개) */
    private static void atm(Voxels v, int i, int level, int j, String face) {
        v.set(i, level, j, Block.of("light_gray_concrete", 0x7D7D73));
        v.set(i, level + 1, j, Block.of("dropper[facing=" + face + ",triggered=false]", 0x6E6E6E));
        v.set(i, level + 2, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
    }

    /** 1층에서 바깥 벽·코어가 아닌 바닥 칸 */
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
        t.v.set(t.ci0 + 6, level + 1, t.corridorJ, Blocks.wallSign("dark_oak", "south", "white", true, "", "준서은행", "자산관리 상담"));
    }

    private static void restaurant(Tower t, int k, int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        Random r = new Random(t.r.nextLong());
        int[] b = t.bounds(k);
        for (int j = b[1]; j <= b[3]; j++) {
            for (int i = b[0]; i <= b[2]; i++) {
                if (t.inside(i, j, k) && !t.edge(i, j, k) && !(i >= t.ci0 && i <= t.ci1 && j >= t.cj0 && j <= t.cj1)) {
                    v.set(i, level - 1, j, Block.of("dark_oak_planks", 0x432B14));
                }
            }
        }
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
            v.set(t.ci0 + 6, level + 1, t.corridorJ, Blocks.wallSign("dark_oak", "south", "yellow", true, "", "구름 위 식당", "58·59층"));
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
        // 365 자동화코너 바깥 문 (밤에도 드나듦)
        int atm = (t.ci0 - 1) / 2 + 4;
        v.fill(atm, 0, d - 1, atm, 2, d - 1, AIR);
        // 사다리꼴 평면 밖 상자 귀퉁이는 보도 포장
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (!t.inside(i, j, 0)) {
                    v.set(i, -1, j, Block.of("light_gray_concrete", 0x7D7D73));
                }
            }
        }
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

    /**
     * 지하 1층: 계단에서 남북 복도 → 동서 복도. 남쪽에 금고(두꺼운 벽, 열린 원형 강철 문)와 현금 정리실,
     * 북쪽에 대여금고실과 보안실.
     */
    private static void basement(Tower t, Voxels v) {
        Frame f = Frame.of(v);
        Random r = new Random(t.r.nextLong());
        int L = B1, top = -2, hh = top - L + 2;
        Frame st = stairFrame(t, v);
        int sd = Interior.stairDepth(-B1);
        int sx0 = st.i(0, sd), sx1 = st.i(0, -1), sz1 = st.j(5, 0);
        int x0 = sx0, x1 = t.ci1 - 4, z0 = st.j(-1, 0) - 2, z1 = t.s.d - 3;
        Block shell = Block.of("deepslate_tiles", 0x363637);
        Block inner = Block.of("light_gray_concrete", 0x7D7D73);
        v.fill(x0, L - 1, z0, x1, L - 1, z1, POLISHED_ANDESITE);
        v.walls(x0, L, z0, x1, top, z1, shell);
        v.fill(x0 + 1, L, z0 + 1, x1 - 1, top, z1 - 1, AIR);
        Interior.stairCore(st, new int[]{B1, 0}, Interior.CORE_WALL, "polished_andesite", 0x848685);
        int ns1 = sx1 + 2;                     // 남북 복도 동쪽 줄
        int cz0 = sz1 + 1, cz1 = sz1 + 3;      // 동서 복도
        int wallZ = cz1 + 1;                   // 남쪽 방 북쪽 벽

        // ---- 북쪽: 대여금고실, 보안실
        int nw = ns1 + 1, nm = (nw + 1 + x1) / 2;
        v.fill(nw, L, z0 + 1, nw, top, sz1, inner);
        v.fill(nw, L, sz1, x1 - 1, top, sz1, inner);
        v.fill(nm, L, z0 + 1, nm, top, sz1, inner);
        Interior.door(f, nw + 4, L, sz1, "dark_oak", "south");
        v.set(nw + 5, L + 1, sz1 + 1, Blocks.wallSign("birch", "south", "black", false, "", "대여금고"));
        for (int i = nw + 1; i < nm; i++) {
            for (int y = L; y <= L + 2; y++) {
                v.set(i, y, z0 + 1, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            }
        }
        for (int i = nw + 3; i < nm - 2; i += 4) {
            v.set(i, L, (z0 + sz1) / 2 + 1, Furniture.WHITE_TOP);
            v.set(i + 1, L, (z0 + sz1) / 2 + 1, Furniture.WHITE_TOP);
        }
        Interior.lights(f, nw + 1, z0 + 1, nm - 1, sz1 - 1, L, hh, 4, Interior.LIGHT);
        Interior.door(f, nm + 3, L, sz1, "dark_oak", "south");
        v.set(nm + 4, L + 1, sz1 + 1, Blocks.wallSign("birch", "south", "black", false, "", "보안실"));
        for (int i = nm + 1; i < x1; i++) {
            v.fill(i, L + 1, z0 + 1, i, L + 2, z0 + 1, Block.of("black_concrete", 0x080A0F));   // 감시 화면
            v.set(i, L, z0 + 2, Furniture.DESK_TOP);
        }
        for (int i = nm + 2; i < x1 - 1; i += 2) {
            Furniture.chair(f, i, L, z0 + 3, "south", "dark_oak");
        }
        Interior.lights(f, nm + 1, z0 + 1, x1 - 1, sz1 - 1, L, hh, 4, Interior.LIGHT);

        // ---- 남쪽: 금고
        int vx0 = x0 + 1, vx1 = Math.min(x1 - 12, x0 + 17);
        v.fill(x0, L, wallZ, vx1 + 1, top, wallZ, VAULT_WALL);
        v.fill(vx1 + 1, L, wallZ, vx1 + 1, top, z1, VAULT_WALL);
        Interior.floor(f, vx0, wallZ + 1, vx1, z1 - 1, L, Block.of("polished_deepslate", 0x484849));
        int dc = (vx0 + vx1) / 2;
        // 원형 강철 문틀과 3칸 출입구
        for (int y = L; y <= top; y++) {
            for (int i = dc - 3; i <= dc + 3; i++) {
                double rr = Math.hypot(i - dc, y - (L + 1));
                if (rr <= 3.2 && rr > 1.6) {
                    v.set(i, y, wallZ, STEEL);
                }
            }
        }
        v.fill(dc - 1, L, wallZ, dc + 1, L + 2, wallZ, AIR);
        // 활짝 열려 벽에 붙은 둥근 문짝 (두께 1)
        for (int y = L; y <= top; y++) {
            for (int i = dc + 3; i <= dc + 8; i++) {
                double dx = i - (dc + 5.5), dy = y - (L + 1.5);
                if (dx * dx + dy * dy <= 2.6 * 2.6) {
                    v.set(i, y, wallZ - 1, STEEL);
                }
            }
        }
        v.set(dc - 2, L + 1, wallZ - 1, Blocks.wallSign("dark_oak", "north", "white", false, "", "금고", "관계자 외 출입금지"));
        // 금고 안: 뒷벽 현금 선반 두 단 + 철망 칸, 금괴, 현금 수송 가방
        int jb0 = wallZ + 1, jb1 = z1 - 1;
        Block shelf = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);
        for (int i = vx0; i <= vx1; i++) {
            Block cash1 = Block.of(i % 2 == 0 ? "lime_carpet" : "yellow_carpet", i % 2 == 0 ? 0x70B919 : 0xF8C527);
            Block cash2 = Block.of(i % 3 == 0 ? "yellow_carpet" : "lime_carpet", i % 3 == 0 ? 0xF8C527 : 0x70B919);
            v.set(i, L, jb1, shelf);
            v.set(i, L + 1, jb1, cash1);
            v.set(i, L + 2, jb1, shelf);
            v.set(i, L + 3, jb1, cash2);
            v.fill(i, L, jb1 - 1, i, L + 2, jb1 - 1, IRON_BARS);
        }
        for (int i = vx0 + 2; i <= vx1; i += 6) {
            v.fill(i, L, jb1 - 1, i, L + 1, jb1 - 1, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        }
        v.fill(vx0, L, jb0, vx0 + 1, L, jb0 + 1, GOLD_BLOCK);   // 금괴 더미
        v.set(vx0, L + 1, jb0, GOLD_BLOCK);
        v.fill(vx1 - 1, L, jb0, vx1, L, jb0, Block.of("black_wool", 0x141519));   // 현금 수송 가방
        v.set(vx1, L + 1, jb0, Block.of("black_wool", 0x141519));
        Interior.lights(f, vx0, jb0, vx1, jb1 - 2, L, hh, 4, Interior.LIGHT);

        // ---- 남쪽: 현금 정리실
        int rx0 = vx1 + 2;
        v.fill(rx0, L, wallZ, x1 - 1, top, wallZ, inner);
        Interior.door(f, rx0 + 3, L, wallZ, "dark_oak", "north");
        v.set(rx0 + 4, L + 1, wallZ - 1, Blocks.wallSign("birch", "north", "black", false, "", "현금 정리실"));
        for (int i = rx0 + 1; i + 1 < x1 - 1; i += 4) {
            v.set(i, L, wallZ + 3, Furniture.WHITE_TOP);
            v.set(i + 1, L, wallZ + 3, Furniture.WHITE_TOP);
            v.set(i, L + 1, wallZ + 3, Block.of("light_gray_carpet", 0x8E8E86));   // 지폐 계수기
            Furniture.chair(f, i, L, wallZ + 2, "north", "dark_oak");
            Furniture.chair(f, i + 1, L, wallZ + 2, "north", "dark_oak");
        }
        Interior.lights(f, rx0, wallZ + 1, x1 - 1, z1 - 1, L, hh, 4, Interior.LIGHT);
        // 복도 등
        for (int i = x0 + 3; i < x1; i += 4) {
            v.set(i, top, cz0 + 1, Interior.LIGHT);
        }
        for (int j = z0 + 2; j < cz0; j += 4) {
            v.set(ns1, top, j, Interior.LIGHT);
        }
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
