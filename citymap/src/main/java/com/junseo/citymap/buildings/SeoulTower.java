package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 남산 정상: N서울타워와 둘레.
 * <ul>
 *   <li>타워: 흰 콘크리트 몸통(지름 15), 몸통 위 전망대(T1~T3 전망층, T4 기계실, T5 회전 레스토랑, T6 방송 장비실),
 *       그 위 안테나(위쪽은 빨강·흰 띠). 실제로는 243m 산 위에 236.7m 탑이라, 이 산 높이에 맞춰 같은 비율로 줄였습니다.</li>
 *   <li>몸통 안: 꺾인 비상계단(지하 1층 → 전망대 모든 층, 걸어서 오름)과 전망대 엘리베이터 2대 (지하 1층에서 탐)</li>
 *   <li>N서울타워 플라자: 타워 밑동을 둘러싼 건물. 지하 1층 전망대 입구·매표·기념품, 1층 매표소·카페·기념품점·안내,
 *       2층 식당, 옥상 테라스(사랑의 자물쇠 난간). 계단실 2곳, 엘리베이터, 층마다 화장실.</li>
 *   <li>정상 광장: 거점이 있는 곳. 화강석 포장, 자물쇠 난간, 팔각정, 봉수대(다섯 개), 소나무, 의자, 보행등.
 *       서쪽 계단으로 지하 1층 테라스, 남쪽 계단으로 산길과 이어짐.</li>
 * </ul>
 * 건물 상자: 거점이 (HUB_I, HUB_J) 에 오는 85×91 상자, 정면(남쪽)이 광장. 높이는 실제 산 높이(건물 y)를 그대로 씁니다.
 * 광장 높이 = 거점 자리 산 높이라서 거점은 산 위 실제 땅 높이에서 그대로 열려 있습니다.
 */
final class SeoulTower {
    static final int W = 85, D = 91;
    /** 상자 안 거점 칸 */
    static final int HUB_I = 42, HUB_J = 59;
    /** 타워 축 칸 (거점에서 북쪽 23칸) */
    static final int CI = 42, CJ = 36;
    /** 몸통 반지름 */
    static final int SHAFT_R = 7;
    /** 플라자 건물 바닥판: 상자 칸 BI0.. (41칸), BJ0.. (33칸), 모서리 반지름 6 */
    static final int BI0 = 22, BJ0 = 18, BW = 41, BD = 33, CORNER = 6;
    /** 광장 타원 */
    static final double PCX = 42, PCJ = 54, PRX = 30, PRZ = 24;

    static final Block SHAFT = WHITE_CONCRETE;
    static final Block POD_GLASS = Block.of("gray_stained_glass", 0x4C4C4C);
    static final Block PLAZA_GLASS = Block.of("light_blue_stained_glass", 0x6699D8);
    static final Block FRAME = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block GRANITE = Block.of("polished_andesite", 0x848685);
    static final Block GRANITE_LINE = Block.of("smooth_stone", 0x9E9E9E);
    static final Block WALL_STONE = Block.of("stone_bricks", 0x7A7979);
    static final Block RAIL = IRON_BARS;
    static final Block RED = Block.of("red_concrete", 0x8E2121);
    static final Block DECK = Block.of("spruce_planks", 0x725430);
    static final Block FLOOR = Block.of("smooth_stone", 0x9E9E9E);

    final Voxels v;
    final Ground g;
    final Random r;
    final Earthwork ew;
    /** 광장 서는 높이 (거점 자리 산 높이) */
    final int P;
    final int B1, F1, F2, ROOF;
    final int[] podLevels;
    final int T1, T2, T3, T4, T5, T6, POD_TOP;
    final int top;

    private SeoulTower(int w, int d, Ground g, Random r) {
        this.g = g;
        this.r = r;
        P = g.surfaceY(HUB_I, HUB_J) + 1;
        B1 = P - 5;
        F1 = P;
        F2 = P + 5;
        ROOF = P + 10;
        T1 = B1 + 76;
        T2 = T1 + 4;
        T3 = T2 + 4;
        T4 = T3 + 4;
        T5 = T4 + 4;
        T6 = T5 + 4;
        POD_TOP = T6 + 4;
        podLevels = new int[]{T1, T2, T3, T4, T5, T6};
        top = Math.min(P + 152, 309);
        int low = Integer.MAX_VALUE;
        for (int j = -1; j <= d; j++) {
            for (int i = -1; i <= w; i++) {
                low = Math.min(low, g.surfaceY(i, j));
            }
        }
        v = new Voxels(w, d, low - 1, top + 1);
        ew = new Earthwork(v, g);
    }

    static Voxels build(int w, int d, Ground g, Random r) {
        SeoulTower t = new SeoulTower(w, d, g, r);
        t.grade();
        t.shaft();
        t.pod();
        t.antenna();
        t.plazaBuilding();
        t.ew.rails(RAIL);
        t.plazaFeatures();
        t.v.connect();
        return t.v;
    }

    // ------------------------------------------------------------------ 땅

    boolean inPlaza(int i, int j) {
        double a = (i - PCX) / PRX, b = (j - PCJ) / PRZ;
        return a * a + b * b <= 1 && j >= BJ0 + BD - 1 + (i >= BI0 && i < BI0 + BW ? 1 : 0);
    }

    /** 플라자 건물 바닥판 안인지 (건물 좌표 bi, bj) */
    static boolean inFoot(int bi, int bj) {
        if (bi < 0 || bj < 0 || bi >= BW || bj >= BD) {
            return false;
        }
        int ci = bi < CORNER ? CORNER : bi > BW - 1 - CORNER ? BW - 1 - CORNER : bi;
        int cj = bj < CORNER ? CORNER : bj > BD - 1 - CORNER ? BD - 1 - CORNER : bj;
        double dx = bi - ci, dz = bj - cj;
        return dx * dx + dz * dz <= CORNER * CORNER + 1;
    }

    static boolean footEdge(int bi, int bj) {
        return inFoot(bi, bj) && (!inFoot(bi - 1, bj) || !inFoot(bi + 1, bj) || !inFoot(bi, bj - 1) || !inFoot(bi, bj + 1));
    }

    private void grade() {
        // 정상 광장
        for (int j = 0; j < v.d; j++) {
            for (int i = 0; i < v.w; i++) {
                if (inPlaza(i, j)) {
                    ew.set(i, j, P, (i + j) % 7 == 0 || (i - j) % 7 == 0 ? GRANITE_LINE : GRANITE);
                }
            }
        }
        // 서쪽 지하 1층 테라스와 내려가는 계단
        for (int j = 34; j <= 49; j++) {
            for (int i = 15; i <= 21; i++) {
                ew.set(i, j, j >= 45 ? P : B1, DECK);
            }
        }
        ew.stairs(16, 49, 0, -1, 1, 0, 5, P, B1, "stone_brick", 0x7A7979);
        // 건물 바닥: 지하 1층 바닥 높이로 고르고 기초는 땅까지
        for (int bj = 0; bj < BD; bj++) {
            for (int bi = 0; bi < BW; bi++) {
                if (inFoot(bi, bj)) {
                    ew.set(BI0 + bi, BJ0 + bj, B1, FLOOR);
                    ew.noRail(BI0 + bi, BJ0 + bj);
                }
            }
        }
        // 남쪽 산길로 내려가는 계단
        int j0 = (int) Math.ceil(PCJ + PRZ) - 1;
        int n = 1;
        while (n < 30 && ew.top(HUB_I, j0 + n) < P - n) {
            n++;
        }
        ew.stairs(HUB_I - 2, j0 + 1, 0, 1, 1, 0, 5, P, P - n + 1, "stone_brick", 0x7A7979);
        ew.apply(STONE, WALL_STONE);
    }

    // ------------------------------------------------------------------ 몸통과 코어

    private void shaft() {
        // 몸통: 지하 1층 바닥부터 전망대 밑까지 꽉 찬 원기둥, 안에 계단실과 엘리베이터
        int bottom = ew.top(CI, CJ) + 1;
        bottom = Math.min(bottom, B1 - 1);
        v.cylinder(CI + 0.5, CJ + 0.5, SHAFT_R, bottom, POD_TOP + 1, SHAFT);
        // 기초는 땅까지
        for (int j = CJ - SHAFT_R; j <= CJ + SHAFT_R; j++) {
            for (int i = CI - SHAFT_R; i <= CI + SHAFT_R; i++) {
                if (Math.hypot(i - CI, j - CJ) <= SHAFT_R) {
                    for (int y = ew.top(i, j) + 1; y < B1 - 1; y++) {
                        v.set(i, y, j, STONE);
                    }
                }
            }
        }
        // 비상계단: 지하 1층부터 4칸마다 참, 전망대 T1~T6 에 문
        int n = (T6 - B1) / 4 + 1;
        int[] levels = new int[n];
        for (int k = 0; k < n; k++) {
            levels[k] = B1 + 4 * k;
        }
        Frame sf = new Frame(v, CI - 1, CJ - 5, 0);
        Interior.stairCore(sf, levels, Interior.CORE_WALL, "polished_andesite", 0x848685, 1);
        for (int level : levels) {
            boolean door = level == B1 || level >= T1;
            if (!door) {
                sf.fill(0, level, -1, 0, level + 2, -1, Interior.CORE_WALL); // 몸통 중간 참은 막힘
            } else {
                // 문에서 몸통 밖(북쪽)까지 통로
                v.fill(CI - 1, level, CJ - 8, CI - 1, level + 2, CJ - 6, AIR);
                v.fill(CI - 1, level - 1, CJ - 8, CI - 1, level - 1, CJ - 6, Interior.LANDING);
            }
        }
        // 계단실 위 마감
        sf.fill(-1, T6 + 3, -1, 3, T6 + 3, 5, SHAFT);
        // 엘리베이터 2대: 지하 1층 → T1, T2, T3, T5 (문은 남쪽)
        int[] lift = {B1, T1, T2, T3, T5};
        for (int e = 0; e < 2; e++) {
            int oi = e == 0 ? CI - 2 : CI + 1;
            Frame ef = new Frame(v, oi, CJ + 1, 0);
            ef.fill(0, B1, 0, 1, T5 + 2, 1, AIR);
            Interior.elevator(ef, lift, Interior.CORE_WALL);
        }
        for (int level : lift) {
            // 엘리베이터 문 앞 승강장에서 몸통 밖(남쪽)까지
            v.fill(CI - 2, level, CJ + 4, CI + 2, level + 2, CJ + 7, AIR);
            v.fill(CI - 2, level - 1, CJ + 4, CI + 2, level - 1, CJ + 7, Interior.LANDING);
        }
    }

    // ------------------------------------------------------------------ 전망대

    /** 높이 y 에서 전망대 바깥 반지름 (0 = 없음) */
    double podRadius(int y) {
        if (y >= T1 - 9 && y < T1 - 1) {
            double t = (y - (T1 - 9) + 1) / 8.0;
            return SHAFT_R + (16 - SHAFT_R) * t;
        }
        if (y >= T1 - 1 && y < T4 - 1) {
            return 16;
        }
        if (y >= T4 - 1 && y < T5 - 1) {
            return 15;
        }
        if (y >= T5 - 1 && y < T6 - 1) {
            return 16.5;
        }
        if (y >= T6 - 1 && y < POD_TOP) {
            return 14;
        }
        if (y == POD_TOP) {
            return 12;
        }
        if (y == POD_TOP + 1) {
            return 9;
        }
        return 0;
    }

    boolean inPod(int i, int j, int y) {
        double rr = podRadius(y);
        return rr > 0 && Math.hypot(i - CI, j - CJ) <= rr;
    }

    boolean podEdge(int i, int j, int y) {
        return inPod(i, j, y) && (!inPod(i - 1, j, y) || !inPod(i + 1, j, y) || !inPod(i, j - 1, y) || !inPod(i, j + 1, y));
    }

    private void pod() {
        int R = 17;
        // 아래 치마 (거꾸로 된 원뿔 껍데기)
        for (int y = T1 - 9; y < T1 - 1; y++) {
            for (int j = CJ - R; j <= CJ + R; j++) {
                for (int i = CI - R; i <= CI + R; i++) {
                    if (inPod(i, j, y) && Math.hypot(i - CI, j - CJ) > SHAFT_R
                            && (podEdge(i, j, y) || !inPod(i, j, y - 1) || y == T1 - 9)) {
                        v.set(i, y, j, SHAFT);
                    }
                }
            }
        }
        for (int k = 0; k < podLevels.length; k++) {
            int level = podLevels[k];
            int next = k + 1 < podLevels.length ? podLevels[k + 1] : POD_TOP;
            boolean glazed = k != 3 && k != 5;
            for (int j = CJ - R; j <= CJ + R; j++) {
                for (int i = CI - R; i <= CI + R; i++) {
                    // 바닥판 (아래층이 더 넓으면 그 천장까지 덮음)
                    boolean slab = inPod(i, j, level - 1) || inPod(i, j, level - 2);
                    if (slab && Math.hypot(i - CI, j - CJ) > SHAFT_R) {
                        boolean rim = !inPod(i, j, level) || podEdge(i, j, level - 1) || podEdge(i, j, level - 2);
                        v.set(i, level - 1, j, rim ? SHAFT : k == 4 ? DARK_OAK_PLANKS : FLOOR);
                    }
                    if (!inPod(i, j, level)) {
                        continue;
                    }
                    if (!podEdge(i, j, level)) {
                        continue;
                    }
                    double ang = Math.atan2(j - CJ, i - CI);
                    double arc = (ang + Math.PI) * podRadius(level);
                    boolean mullion = arc % 3 < 1.05;
                    for (int y = level; y < next - 1; y++) {
                        Block b = glazed ? (mullion ? SHAFT : POD_GLASS) : (y == level + 1 && mullion ? FRAME : SHAFT);
                        v.set(i, y, j, b);
                    }
                }
            }
        }
        // 지붕
        for (int y = POD_TOP - 1; y <= POD_TOP + 1; y++) {
            for (int j = CJ - R; j <= CJ + R; j++) {
                for (int i = CI - R; i <= CI + R; i++) {
                    if (inPod(i, j, y)) {
                        v.set(i, y, j, SHAFT);
                    }
                }
            }
        }
        podInterior();
    }

    private void podInterior() {
        Frame f = Frame.of(v);
        for (int k = 0; k < podLevels.length; k++) {
            int level = podLevels[k];
            double rr = podRadius(level);
            // 천장 등: 몸통 둘레 고리
            for (int a = 0; a < 360; a += 30) {
                double rad = Math.toRadians(a + 15);
                int i = CI + (int) Math.round(Math.cos(rad) * (SHAFT_R + 3.5)), j = CJ + (int) Math.round(Math.sin(rad) * (SHAFT_R + 3.5));
                if (!v.solid(i, level + 2, j)) {
                    v.set(i, level + 2, j, Interior.LIGHT);
                }
            }
            switch (k) {
                case 0, 1, 2 -> {
                    // 전망층: 창가 의자와 망원경
                    for (int a = 0; a < 360; a += 20) {
                        double rad = Math.toRadians(a + 10);
                        int i = CI + (int) Math.round(Math.cos(rad) * (rr - 2)), j = CJ + (int) Math.round(Math.sin(rad) * (rr - 2));
                        if (Math.abs(i - CI) <= 2 && j > CJ) {
                            continue; // 엘리베이터 앞
                        }
                        if (Math.abs(i - CI + 1) <= 1 && j < CJ) {
                            continue; // 계단 앞
                        }
                        if (v.solid(i, level, j)) {
                            continue;
                        }
                        if ((a / 20) % 3 == 0) {
                            v.set(i, level, j, Block.of("polished_blackstone_wall", 0x353038));
                            v.set(i, level + 1, j, Block.of("lightning_rod[facing=" + outward(rad) + ",powered=false,waterlogged=false]", 0xC07050));
                        } else {
                            v.set(i, level, j, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
                        }
                    }
                    if (k == 1) {
                        // T2: 화장실 (북쪽과 서쪽)
                        Interior.restroom(new Frame(v, CI - 3, CJ - 14, 0), 0, 0, 6, 4, level, 4, "남자 화장실", 3);
                        Interior.restroom(new Frame(v, CI - 14, CJ + 3, 3), 0, 0, 6, 4, level, 4, "여자 화장실", 3);
                    }
                    if (k == 0 || k == 2) {
                        // 기념품 진열대
                        for (int b = -2; b <= 2; b += 2) {
                            v.fill(CI + 10, level, CJ + b, CI + 11, level, CJ + b, Furniture.COUNTER);
                            v.fill(CI - 11, level, CJ + b - 6, CI - 10, level, CJ + b - 6, Furniture.COUNTER);
                        }
                    }
                }
                case 3, 5 -> {
                    // 기계실·방송 장비실: 장비 줄
                    for (int a = 0; a < 360; a += 24) {
                        double rad = Math.toRadians(a);
                        int i = CI + (int) Math.round(Math.cos(rad) * (rr - 3)), j = CJ + (int) Math.round(Math.sin(rad) * (rr - 3));
                        if ((Math.abs(i - CI + 1) <= 1 && j < CJ) || v.solid(i, level, j)) {
                            continue;
                        }
                        v.fill(i, level, j, i, level + 1, j, IRON_BLOCK);
                    }
                }
                default -> {
                    // T5 회전 레스토랑: 창가 식탁, 몸통 쪽 주방 카운터
                    for (int a = 0; a < 360; a += 15) {
                        double rad = Math.toRadians(a + 7.5);
                        int i = CI + (int) Math.round(Math.cos(rad) * (rr - 3)), j = CJ + (int) Math.round(Math.sin(rad) * (rr - 3));
                        if ((Math.abs(i - CI) <= 3 && j > CJ) || v.solid(i, level, j)) {
                            continue;
                        }
                        v.set(i, level, j, Furniture.DESK_TOP);
                        int ci = CI + (int) Math.round(Math.cos(rad) * (rr - 2)), cj = CJ + (int) Math.round(Math.sin(rad) * (rr - 2));
                        if (!v.solid(ci, level, cj)) {
                            v.set(ci, level, cj, Block.of("dark_oak_stairs[facing=" + outward(rad) + ",half=bottom,shape=straight,waterlogged=false]", 0x432B14));
                        }
                    }
                    for (int a = 180; a < 360; a += 12) {
                        double rad = Math.toRadians(a);
                        int i = CI + (int) Math.round(Math.cos(rad) * (SHAFT_R + 1.5)), j = CJ + (int) Math.round(Math.sin(rad) * (SHAFT_R + 1.5));
                        if (Math.abs(i - CI + 1) <= 1 || v.solid(i, level, j)) {
                            continue;
                        }
                        v.set(i, level, j, Furniture.COUNTER);
                    }
                }
            }
            if (k == 0) {
                f.set(CI + 3, level + 1, CJ + 8, Blocks.wallSign("dark_oak", "south", "white", true, "", "전망대 T1", "N서울타워"));
            }
        }
    }

    /** 각도 rad 쪽(바깥)의 동서남북 이름 */
    static String outward(double rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        if (Math.abs(c) >= Math.abs(s)) {
            return c > 0 ? "east" : "west";
        }
        return s > 0 ? "south" : "north";
    }

    // ------------------------------------------------------------------ 안테나

    private void antenna() {
        int base = POD_TOP + 2;
        // 받침 원통과 둘레 단
        v.cylinder(CI + 0.5, CJ + 0.5, 4.5, base, base + 11, SHAFT);
        v.cylinder(CI + 0.5, CJ + 0.5, 5.5, base + 4, base + 4, FRAME);
        v.cylinder(CI + 0.5, CJ + 0.5, 5.5, base + 9, base + 9, FRAME);
        // 철탑: 3×3 → 십자 → 한 칸, 위쪽은 빨강·흰 띠
        for (int y = base + 12; y <= top; y++) {
            int h = y - (base + 12);
            boolean red = y > base + 30 && ((y - base) / 5) % 2 == 0;
            Block b = red ? RED : WHITE_CONCRETE;
            double rr = y < base + 34 ? 1.5 : y < top - 12 ? 1.0 : 0.5;
            v.cylinder(CI + 0.5, CJ + 0.5, rr, y, y, b);
            if (h > 0 && h % 9 == 0 && y < top - 12) {
                v.cylinder(CI + 0.5, CJ + 0.5, rr + 1.2, y, y, IRON_BARS);
            }
        }
        v.set(CI, top, CJ, Block.of("end_rod[facing=up]", 0xE0D8C8));
    }

    // ------------------------------------------------------------------ N서울타워 플라자

    private void plazaBuilding() {
        int[] levels = {B1, F1, F2, ROOF};
        // 바닥판과 옥상
        for (int bj = 0; bj < BD; bj++) {
            for (int bi = 0; bi < BW; bi++) {
                if (!inFoot(bi, bj)) {
                    continue;
                }
                int i = BI0 + bi, j = BJ0 + bj;
                boolean shaft = Math.hypot(i - CI, j - CJ) <= SHAFT_R;
                for (int k = 1; k < levels.length; k++) {
                    if (!shaft) {
                        v.set(i, levels[k] - 1, j, k == levels.length - 1 ? DECK : FLOOR);
                    }
                }
            }
        }
        // 바깥 벽: 유리 커튼월, 흙에 묻힌 곳은 돌벽, 층마다 흰 띠
        for (int bj = 0; bj < BD; bj++) {
            for (int bi = 0; bi < BW; bi++) {
                if (!footEdge(bi, bj)) {
                    continue;
                }
                int i = BI0 + bi, j = BJ0 + bj;
                int outside = outsideGround(bi, bj);
                for (int k = 0; k < 3; k++) {
                    for (int y = levels[k]; y < levels[k + 1] - 1; y++) {
                        boolean buried = y <= outside;
                        boolean post = (bi + bj) % 4 == 0 || isCorner(bi, bj);
                        v.set(i, y, j, buried ? GRANITE : post ? FRAME : PLAZA_GLASS);
                    }
                    v.set(i, levels[k + 1] - 1, j, WHITE_CONCRETE);
                }
                // 옥상 난간: 흰 턱 + 쇠 난간 (자물쇠)
                v.set(i, ROOF, j, WHITE_CONCRETE);
                v.set(i, ROOF + 1, j, RAIL);
                // 지하층 아래 드러나는 기초는 화강석
                for (int y = ew.top(i, j) + 1; y < B1 - 1; y++) {
                    v.set(i, y, j, WALL_STONE);
                }
            }
        }
        Frame f = Frame.of(v);
        // 계단실 (서·동), 옥상까지
        Frame west = Frame.facing(v, BI0 + 7, BJ0 + 13, "west");
        Interior.stairCore(west, levels, Interior.CORE_WALL, "polished_andesite", 0x848685);
        west.fill(-1, ROOF + 3, -1, 5, ROOF + 3, 6, WHITE_CONCRETE);
        Frame east = Frame.facing(v, BI0 + 33, BJ0 + 17, "east");
        Interior.stairCore(east, levels, Interior.CORE_WALL, "polished_andesite", 0x848685);
        east.fill(-1, ROOF + 3, -1, 5, ROOF + 3, 6, WHITE_CONCRETE);
        // 엘리베이터 (지하 1층 ~ 2층)
        Frame lift = Frame.facing(v, BI0 + 2, BJ0 + 21, "east");
        Interior.elevator(lift, new int[]{B1, F1, F2}, Interior.CORE_WALL);
        // 화장실 (동쪽, 층마다 남·여)
        for (int k = 0; k < 3; k++) {
            Interior.restroom(Frame.facing(v, BI0 + 38, BJ0 + 4, "west"), 0, 0, 6, 4, levels[k], 5, "남자 화장실", 3);
            Interior.restroom(Frame.facing(v, BI0 + 38, BJ0 + 20, "west"), 0, 0, 6, 4, levels[k], 5, "여자 화장실", 3);
        }
        // 출입구: 1층 남쪽 정문 (광장), 지하 1층 서쪽 (테라스)
        int mid = BI0 + BW / 2;
        for (int i = mid - 2; i <= mid + 2; i++) {
            v.fill(i, F1, BJ0 + BD - 1, i, F1 + 2, BJ0 + BD - 1, AIR);
        }
        for (int i = mid - 4; i <= mid + 4; i++) {
            for (int j = BJ0 + BD; j <= BJ0 + BD + 2; j++) {
                v.set(i, F1 + 4, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            }
        }
        v.set(mid, F1 + 3, BJ0 + BD, Blocks.wallSign("dark_oak", "south", "white", true, "", "N서울타워", "플라자"));
        for (int j = BJ0 + 24; j <= BJ0 + 26; j++) {
            v.fill(BI0, B1, j, BI0, B1 + 2, j, AIR);
        }
        v.set(BI0 - 1, B1 + 3, BJ0 + 27, Blocks.wallSign("dark_oak", "west", "white", true, "", "N서울타워", "전망대 입구"));
        interiorB1(f);
        interior1F(f);
        interior2F(f);
        roofTerrace(f);
    }

    private boolean isCorner(int bi, int bj) {
        return (bi <= CORNER || bi >= BW - 1 - CORNER) && (bj <= CORNER || bj >= BD - 1 - CORNER);
    }

    /** 바깥 벽 칸 바로 바깥의 (고른 뒤) 땅 높이 */
    private int outsideGround(int bi, int bj) {
        int best = Integer.MIN_VALUE;
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] n : nb) {
            if (!inFoot(bi + n[0], bj + n[1])) {
                best = Math.max(best, ew.ground(BI0 + bi + n[0], BJ0 + bj + n[1]));
            }
        }
        return best;
    }

    private boolean free(int i, int y, int j) {
        return !v.solid(i, y, j) && inFoot(i - BI0, j - BJ0) && !footEdge(i - BI0, j - BJ0);
    }

    private void interiorB1(Frame f) {
        int L = B1;
        // 전망대 엘리베이터 앞: 개찰구 줄, 안내판
        for (int i = CI - 6; i <= CI + 6; i++) {
            if (Math.abs(i - CI) > 2 && free(i, L, CJ + 10)) {
                v.set(i, L, CJ + 10, Block.of("polished_blackstone_wall", 0x353038));
            }
        }
        for (int j = CJ + 11; j <= CJ + 13; j++) {
            for (int i = CI - 9; i <= CI + 9; i += 3) {
                if (Math.abs(i - CI) > 2 && free(i, L, j)) {
                    v.set(i, L, j, Block.of("iron_chain[axis=y]", 0x4A4A4A));
                }
            }
        }
        f.set(CI, L + 2, CJ + 8, Blocks.wallSign("dark_oak", "south", "white", true, "", "전망대", "엘리베이터"));
        // 매표 창구 (남쪽 벽)
        for (int i = CI - 10; i <= CI + 10; i++) {
            if (free(i, L, BJ0 + BD - 3)) {
                v.set(i, L, BJ0 + BD - 3, Furniture.COUNTER);
            }
        }
        f.set(CI - 6, L + 2, BJ0 + BD - 2, Blocks.wallSign("birch", "north", "black", false, "", "전망대 매표소"));
        // 북쪽: 기념품점
        shopFloor(L, BJ0 + 2, BJ0 + 9);
        Interior.lights(f, BI0 + 1, BJ0 + 1, BI0 + BW - 2, BJ0 + BD - 2, L, 5, 6, Interior.LIGHT);
    }

    private void interior1F(Frame f) {
        int L = F1;
        // 정문 앞 안내 데스크
        for (int i = CI - 3; i <= CI + 3; i++) {
            if (free(i, L, CJ + 10)) {
                v.set(i, L, CJ + 10, Furniture.COUNTER);
            }
        }
        f.set(CI, L + 2, CJ + 8, Blocks.wallSign("dark_oak", "south", "white", true, "", "안내", "Information"));
        // 서쪽: 매표소 (카운터 + 창구 표지판)
        for (int j = BJ0 + 24; j <= BJ0 + 30; j++) {
            if (free(BI0 + 7, L, j)) {
                v.set(BI0 + 7, L, j, Furniture.COUNTER);
                if (j % 2 == 0) {
                    Furniture.chair(f, BI0 + 6, L, j, "west", "dark_oak");
                }
            }
        }
        f.set(BI0 + 8, L + 2, BJ0 + 23, Blocks.wallSign("birch", "south", "black", false, "", "매표소"));
        // 북서: 카페
        int c0 = BI0 + 10, c1 = BI0 + 13;
        for (int j = BJ0 + 2; j <= BJ0 + 8; j++) {
            if (free(c0, L, j)) {
                v.set(c0, L, j, Furniture.COUNTER);
            }
        }
        v.set(c0, L + 1, BJ0 + 3, Block.of("smoker[facing=east,lit=false]", 0x555451));
        f.set(c0 + 1, L + 2, BJ0 + 9, Blocks.wallSign("spruce", "south", "white", false, "", "카페"));
        for (int j = BJ0 + 3; j <= BJ0 + 9; j += 3) {
            for (int i = c1; i <= BI0 + 30; i += 4) {
                if (free(i, L, j) && free(i + 1, L, j) && free(i, L, j + 1)) {
                    v.set(i, L, j, Furniture.DESK_TOP);
                    Furniture.chair(f, i + 1, L, j, "east", "spruce");
                    if (free(i - 1, L, j)) {
                        Furniture.chair(f, i - 1, L, j, "west", "spruce");
                    }
                }
            }
        }
        // 남동: 기념품점
        shopFloor(L, BJ0 + 27, BJ0 + 30);
        Interior.lights(f, BI0 + 1, BJ0 + 1, BI0 + BW - 2, BJ0 + BD - 2, L, 5, 6, Interior.LIGHT);
    }

    private void interior2F(Frame f) {
        int L = F2;
        // 식당: 식탁 줄, 북쪽 주방
        for (int i = BI0 + 10; i <= BI0 + 30; i++) {
            if (free(i, L, BJ0 + 3)) {
                v.set(i, L, BJ0 + 3, Furniture.COUNTER);
                if (i % 3 == 0) {
                    v.set(i, L + 1, BJ0 + 3, Block.of("smoker[facing=south,lit=false]", 0x555451));
                }
            }
        }
        f.set(BI0 + 20, L + 2, BJ0 + 5, Blocks.wallSign("spruce", "south", "white", false, "", "한식당"));
        // 4인용 식탁 (2칸) 줄: 식탁 사이 2칸, 줄 사이 통로 1칸
        for (int j = BJ0 + 7; j <= BJ0 + BD - 4; j += 4) {
            for (int i = BI0 + 10; i <= BI0 + BW - 12; i += 4) {
                boolean ok = true;
                for (int dj = -1; dj <= 1 && ok; dj++) {
                    ok = free(i, L, j + dj) && free(i + 1, L, j + dj);
                }
                if (ok) {
                    Furniture.table(f, i, L, j, 2, 1, "dark_oak");
                }
            }
        }
        Interior.lights(f, BI0 + 1, BJ0 + 1, BI0 + BW - 2, BJ0 + BD - 2, L, 5, 6, Interior.LIGHT);
    }

    private void shopFloor(int L, int j0, int j1) {
        for (int j = j0; j <= j1; j += 2) {
            for (int i = BI0 + 12; i <= BI0 + 29; i++) {
                if (i % 5 == 0) {
                    continue;
                }
                if (free(i, L, j) && free(i, L, j - 1) && free(i, L, j + 1)) {
                    v.set(i, L, j, Furniture.COUNTER);
                }
            }
        }
    }

    private void roofTerrace(Frame f) {
        int L = ROOF;
        // 의자와 화분, 자물쇠 나무
        for (int bi = 4; bi < BW - 4; bi += 6) {
            int i = BI0 + bi, j = BJ0 + BD - 3;
            if (free(i, L, j) && free(i + 1, L, j)) {
                v.set(i, L, j, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
                v.set(i + 1, L, j, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
            }
        }
        // 몸통 둘레 화단 (나무 상자에 관목)
        for (int a = 0; a < 360; a += 45) {
            double rad = Math.toRadians(a + 22.5);
            int i = CI + (int) Math.round(Math.cos(rad) * 11), j = CJ + (int) Math.round(Math.sin(rad) * 11);
            if (free(i, L, j) && free(i + 1, L, j) && free(i, L, j + 1) && free(i + 1, L, j + 1)) {
                v.fill(i, L, j, i + 1, L, j + 1, Block.of("stripped_spruce_wood[axis=y]", 0x6B5434));
                v.fill(i, L + 1, j, i + 1, L + 1, j + 1, AZALEA_LEAVES);
            }
        }
        loveLocks(L);
    }

    /** 옥상 남쪽 난간에 걸린 사랑의 자물쇠: 쇠 난간 위에 알록달록한 자물쇠 뭉치 (작은 양초 묶음으로) */
    private void loveLocks(int L) {
        for (int bi = 0; bi < BW; bi++) {
            for (int bj = BD / 2; bj < BD; bj++) {
                if (footEdge(bi, bj) && r.nextInt(5) > 0) {
                    v.set(BI0 + bi, L + 2, BJ0 + bj, lock());
                }
            }
        }
    }

    private static final String[] LOCK_COLORS = {"red", "pink", "yellow", "light_blue", "white", "orange", "magenta", "red", "pink"};

    /** 자물쇠 뭉치 하나 (색과 개수는 제각각) */
    Block lock() {
        String c = LOCK_COLORS[r.nextInt(LOCK_COLORS.length)];
        return Block.of(c + "_candle[candles=" + (2 + r.nextInt(3)) + ",lit=false,waterlogged=false]", candleRgb(c));
    }

    static int candleRgb(String c) {
        return switch (c) {
            case "red" -> 0xA02722;
            case "pink" -> 0xD4708F;
            case "yellow" -> 0xE0B52C;
            case "light_blue" -> 0x3A9AD0;
            case "white" -> 0xE0E0E0;
            case "orange" -> 0xD06A1A;
            case "magenta" -> 0xA8409E;
            default -> 0x6AAA2A;
        };
    }

    // ------------------------------------------------------------------ 광장 꾸미기

    private void plazaFeatures() {
        pavilion(22, 63);
        beacons(48, 57);
        // 거점 앞 안내판은 두지 않음 (거점 5×5 와 위 3칸은 비움)
        // 소나무와 보행등: 광장 가장자리 둘레
        for (int a = 0; a < 360; a += 12) {
            double rad = Math.toRadians(a);
            int i = (int) Math.round(PCX + Math.cos(rad) * (PRX - 3)), j = (int) Math.round(PCJ + Math.sin(rad) * (PRZ - 3));
            if (!inPlaza(i, j) || nearHub(i, j, 5) || v.solid(i, P, j)) {
                continue;
            }
            if ((a / 12) % 3 == 0) {
                pine(i, P, j);
            } else if ((a / 12) % 3 == 1) {
                v.fill(i, P, j, i, P + 2, j, Block.of("polished_blackstone_wall", 0x353038));
                v.set(i, P + 3, j, LANTERN);
            } else {
                v.set(i, P, j, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
            }
        }
        // 남쪽 난간 자물쇠: 광장 남쪽 가장자리 쇠 난간 위
        for (int j = (int) PCJ; j < v.d; j++) {
            for (int i = 0; i < v.w; i++) {
                if (inPlaza(i, j) && RAIL.id().equals(idAt(i, P, j)) && r.nextInt(6) > 0) {
                    v.set(i, P + 1, j, lock());
                }
            }
        }
    }

    private String idAt(int i, int y, int j) {
        Block b = v.get(i, y, j);
        return b == null ? "" : b.id();
    }

    boolean nearHub(int i, int j, int m) {
        return Math.abs(i - HUB_I) <= 2 + m && Math.abs(j - HUB_J) <= 2 + m;
    }

    /** 소나무: 굽은 줄기, 납작한 잎 덩이 두세 개 */
    private void pine(int i, int y, int j) {
        Block log = Block.of("spruce_log[axis=y]", 0x3B2A1A);
        Block leaves = Block.of("spruce_leaves[distance=1,persistent=true,waterlogged=false]", 0x3E5F3A);
        v.set(i, y - 1, j, Block.of("coarse_dirt", 0x77563B));
        v.fill(i, y, j, i, y + 3, j, log);
        v.set(i + 1, y + 4, j, log);
        v.set(i + 1, y + 5, j, log);
        v.ellipsoid(i + 1.5, y + 6.2, j + 0.5, 2.6, 1.1, 2.4, leaves);
        v.ellipsoid(i - 0.5, y + 4.6, j + 0.5, 1.8, 0.8, 1.6, leaves);
    }

    /** 팔각정: 돌 기단, 붉은 기둥 8개, 단청 띠, 기와 팔각 지붕 (가운데 (ci, cj)) */
    private void pavilion(int ci, int cj) {
        Block base = Block.of("stone_bricks", 0x7A7979);
        Block col = Block.of("stripped_mangrove_log[axis=y]", 0x8A3A33);
        Block green = Block.of("green_terracotta", 0x4C532A);
        Block tile = Block.of("deepslate_tiles", 0x363637);
        Block tileSlab = Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637);
        int R = 5;
        for (int j = cj - R - 1; j <= cj + R + 1; j++) {
            for (int i = ci - R - 1; i <= ci + R + 1; i++) {
                if (octagon(i - ci, j - cj, R)) {
                    v.set(i, P, j, base);
                } else if (octagon(i - ci, j - cj, R + 1) && j == cj + R + 1 && Math.abs(i - ci) <= 1) {
                    v.set(i, P, j, Blocks.stairs("stone_brick", "north", 0x7A7979));
                }
            }
        }
        v.fill(ci - R + 1, P + 1, cj - R + 1, ci + R - 1, P + 1, cj + R - 1, null);
        for (int j = cj - R + 1; j <= cj + R - 1; j++) {
            for (int i = ci - R + 1; i <= ci + R - 1; i++) {
                if (octagon(i - ci, j - cj, R - 1)) {
                    v.set(i, P, j, Block.of("spruce_planks", 0x725430)); // 마루
                }
            }
        }
        int[][] cols = {{-4, -2}, {-4, 2}, {4, -2}, {4, 2}, {-2, -4}, {2, -4}, {-2, 4}, {2, 4}};
        for (int[] c : cols) {
            v.fill(ci + c[0], P + 1, cj + c[1], ci + c[0], P + 4, cj + c[1], col);
        }
        // 단청 띠 (기둥 위 가로)
        for (int j = cj - 4; j <= cj + 4; j++) {
            for (int i = ci - 4; i <= ci + 4; i++) {
                if (octagon(i - ci, j - cj, 4) && !octagon(i - ci, j - cj, 3)) {
                    v.set(i, P + 5, j, green);
                }
            }
        }
        // 지붕: 처마가 넓고 위로 갈수록 좁아짐
        for (int t = 0; t <= 4; t++) {
            int rr = R + 1 - t;
            for (int j = cj - rr; j <= cj + rr; j++) {
                for (int i = ci - rr; i <= ci + rr; i++) {
                    if (octagon(i - ci, j - cj, rr) && (t == 4 || !octagon(i - ci, j - cj, rr - 2) || t == 0)) {
                        v.set(i, P + 6 + t, j, t == 0 && !octagon(i - ci, j - cj, rr - 1) ? tileSlab : tile);
                    }
                }
            }
        }
        v.set(ci, P + 11, cj, Block.of("deepslate_tile_wall", 0x363637));
        v.set(ci, P + 5, cj, LANTERN_HANGING);
    }

    static boolean octagon(int dx, int dz, int r) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        return ax <= r && az <= r && ax + az <= r + r / 2;
    }

    /**
     * 봉수대 (목멱산 봉수대 복원): 낮은 돌 기단 위에 둥근 돌 봉수 다섯 개가 한 줄로.
     * 봉수마다 아래 앞쪽에 불 넣는 아궁이, 위는 연기 구멍.
     */
    private void beacons(int i0, int j0) {
        Block stone = Block.of("stone_bricks", 0x7A7979);
        Block rough = Block.of("cobblestone", 0x7F7F7F);
        Block slab = Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E);
        int len = 5 * 4 + 1;
        for (int j = j0; j <= j0 + 4; j++) {
            for (int i = i0; i < i0 + len; i++) {
                boolean rim = j == j0 || j == j0 + 4 || i == i0 || i == i0 + len - 1;
                v.set(i, P, j, rim ? stone : slab);
            }
        }
        for (int n = 0; n < 5; n++) {
            int ci = i0 + 2 + 4 * n, cj = j0 + 2;
            for (int y = P + 1; y <= P + 3; y++) {
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        boolean corner = Math.abs(di) + Math.abs(dj) == 2;
                        if (corner && y == P + 3) {
                            continue;
                        }
                        v.set(ci + di, y, cj + dj, y == P + 1 ? rough : stone);
                    }
                }
            }
            v.set(ci, P + 3, cj, AIR);                  // 연기 구멍
            v.set(ci, P + 1, cj + 1, AIR);              // 아궁이 (남쪽)
            v.set(ci, P + 2, cj + 1, Blocks.stairs("stone_brick", "south", 0x7A7979).with("facing=south,half=top,shape=straight,waterlogged=false"));
            v.set(ci, P, cj, stone);
        }
    }
}
