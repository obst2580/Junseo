package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 자주식 공영 주차빌딩 (kind "garage"): 차를 몰고 경사로로 층층이 올라가는 열린 콘크리트 주차 건물.
 * <ul>
 *   <li>층고 3 (바닥판 1 + 빈 칸 2, 실제 주차장 층고 약 3m). 둘레는 바닥판 띠 + 낮은 난간벽 + 가로로 트인 틈,
 *       6~7칸마다 기둥. 옥상도 주차장(난간, 가로등).</li>
 *   <li>경사로: 동쪽 띠를 따라 층마다 같은 자리에 겹쳐 쌓은 직선 경사로(양방향). 아래 반 블록 반 칸씩만 오르고,
 *       반 칸 오를 때마다 2칸 이상 달림(긴 건물은 더 완만). 차 폭 3칸 이상, 어디서나 머리 위 2칸이 빔.
 *       남쪽 끝에서 올라 북쪽 끝에서 다음 층에 닿고, 그 층의 북쪽 통로·주 통로·남쪽 통로를 돌아 다시 남쪽 끝에서 오름.</li>
 *   <li>주차 칸 (너비 3: 선 사이 2칸, 깊이 5~6): 서쪽 벽, 가운데 섬(경사로 벽에 등을 댐), 넓으면 섬 두 줄과 동쪽 줄, 길면 북쪽 벽.
 *       칸마다 차 꺼내는 자리({@link Voxels#carSpot}, 차 앞은 통로 쪽). 엘리베이터 옆 두 칸은 장애인 칸(파란 선),
 *       1층 몇 칸은 여성 우선(분홍 선).</li>
 *   <li>1층 남쪽(정면) 출입구: 오른쪽 들어가는 길(주차권 기계·차단기), 왼쪽 나가는 길(정산소 부스·차단기),
 *       높이 제한 띠(노랑·검정), 요금표, "P 공영주차장" 간판.</li>
 *   <li>사람: 남서쪽 모서리 유리 계단탑(한 층마다 한 번에 오르는 1칸 줄 계단을 남북으로 번갈아, 옥상까지)과 엘리베이터.
 *       1층은 길에서 계단실로 바로 들어가는 문. 계단실 옆에 초록 보행자 통로.</li>
 * </ul>
 * 정면(남쪽)은 j = d-1 (보도 한 줄), 건물 앞벽은 j = d-2.
 */
final class ParkingBuilding {
    /** 층고 */
    static final int H = 3;
    static final int MIN_W = 24, MAX_W = 45, MIN_D = 30, MAX_D = 60, MIN_LEVELS = 2, MAX_LEVELS = 6;

    private static final Block DECK = LIGHT_GRAY_CONCRETE;
    private static final Block ASPHALT = GRAY_CONCRETE;
    private static final Block LINE = WHITE_CONCRETE;
    private static final Block WALKWAY = GREEN_CONCRETE;
    private static final Block RAMP_LOW = Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E);
    private static final Block RAMP_HIGH = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);
    private static final Block RAMP_LINE_LOW = Block.of("smooth_sandstone_slab[type=bottom,waterlogged=false]", 0xDFD6AA);
    private static final Block RAMP_LINE_HIGH = Block.of("smooth_sandstone_slab[type=top,waterlogged=false]", 0xDFD6AA);
    private static final Block CAP = Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E);
    private static final Block CEILING_LIGHT = SEA_LANTERN;
    private static final Block PILLAR = WHITE_CONCRETE;
    private static final Block PILLAR_BASE = Block.of("yellow_terracotta", 0xBA8523);

    /** 겉모양 {띠(바닥판 끝), 난간벽, 기둥, 틈 채움(null 이면 트임)} */
    private static final Block[][] SKINS = {
            {GRAY_CONCRETE, WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, null},                                  // 흰 난간 (새 건물)
            {SMOOTH_STONE, BRICKS, BRICKS, null},                                                          // 붉은 벽돌 (1990년대)
            {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, WHITE_CONCRETE, IRON_BARS},                              // 금속 루버
            {LIGHT_GRAY_CONCRETE, LIGHT_GRAY_CONCRETE, GRAY_CONCRETE, null},                               // 노출 콘크리트
            {WHITE_CONCRETE, POLISHED_ANDESITE, WHITE_CONCRETE, null},                                     // 회색 돌 패널
    };

    private final Voxels v;
    private final Random r;
    private final int w, d, n, top, front;
    private final String name;
    private final Block band, parapet, column, gap;

    // 가로(i) 배치
    private int walk;
    private int ws0, ws1, a10, a11, is0, is1, i20 = -1, i21 = -1, a20 = -1, a21 = -1, es0 = -1, es1 = -1;
    private int g, rp0, rp1, lanes;
    // 세로(j) 배치
    private boolean north;
    private int na0, na1, row0, row1, sa0, sa1;
    /** 경사로: jr0(북쪽 끝, 높은 쪽) .. jr1(남쪽 끝, 낮은 쪽), 반 칸 오를 때마다 k 칸 */
    private int jr0, jr1, k;
    /** 1층 출입구 (나가는 길 gx0..gx0+3, 섬 gx0+4..gx0+6, 들어가는 길 gx0+7..gx0+10) */
    private int gx0;
    /** 계단실 (벽 포함) i 0..4, j stairN..front / 엘리베이터 i 0..3, j liftS-3..liftS */
    private int stairN, liftS;

    private ParkingBuilding(int w, int d, int levels, String name, Random r) {
        this.w = w;
        this.d = d;
        this.n = Math.max(MIN_LEVELS, Math.min(MAX_LEVELS, levels));
        this.name = name;
        this.r = r;
        this.top = H * (n - 1);
        this.front = d - 2;
        this.v = new Voxels(w, d, -1, top + 5);
        Block[] skin = SKINS[r.nextInt(SKINS.length)];
        band = skin[0];
        parapet = skin[1];
        column = skin[2];
        gap = skin[3];
        layout();
    }

    /**
     * @param w      너비 24..45
     * @param d      깊이 30..60 (정면 = 남쪽)
     * @param levels 주차 층 수 2..6 (1층 + 위층, 맨 위는 옥상 주차장)
     */
    static Voxels build(int w, int d, int levels, String name, Random r) {
        ParkingBuilding p = new ParkingBuilding(w, d, levels, name, r);
        p.ground();
        p.decks();
        p.ramps();
        p.stalls();
        p.core();
        p.facade();
        p.gate();
        p.lights();
        p.signs();
        p.v.connect();
        return p.v;
    }

    /** 층 n 의 서는 높이 */
    static int level(int deck) {
        return H * deck;
    }

    // ------------------------------------------------------------------ 배치

    private void layout() {
        int inner = w - 2;
        if (w >= 37) {
            // 두 줄 통로: 서쪽 칸 | 통로 | 섬 칸 | 섬 칸 | 통로 | 동쪽 칸 | 경사로 벽 | 경사로
            int ws = 5, a1 = 5, s1 = 5, s2 = 5, a2 = 5, es = 5;
            lanes = 4;
            int left = inner - 35;
            if (left > 0) { a1++; left--; }
            if (left > 0) { a2++; left--; }
            if (left >= 3) { lanes = 7; left -= 3; }
            if (left > 0) { a1++; left--; }
            if (left > 0) { a2++; left--; }
            if (left > 0) { ws++; left--; }
            if (left > 0) { es++; left--; }
            walk = left;
            ws0 = 1 + walk;
            ws1 = ws0 + ws - 1;
            a10 = ws1 + 1;
            a11 = a10 + a1 - 1;
            is0 = a11 + 1;
            is1 = is0 + s1 - 1;
            i20 = is1 + 1;
            i21 = i20 + s2 - 1;
            a20 = i21 + 1;
            a21 = a20 + a2 - 1;
            es0 = a21 + 1;
            es1 = es0 + es - 1;
            g = es1 + 1;
        } else {
            // 한 줄 통로: 서쪽 칸 | 통로 | 섬 칸 | 경사로 벽 | 경사로
            int ws = 5, a = 6, s = 5;
            lanes = 4;
            int left = inner - 21;
            if (left >= 3) { lanes = 7; left -= 3; }
            if (left > 0) { a++; left--; }
            if (left > 0) { a++; left--; }
            if (left > 0) { ws++; left--; }
            if (left > 0) { s++; left--; }
            walk = Math.min(left, 2);
            left -= walk;
            lanes += Math.min(left, 2); // 경사로 옆 여유 (넓은 차로)
            left -= Math.min(left, 2);
            a += left;
            ws0 = 1 + walk;
            ws1 = ws0 + ws - 1;
            a10 = ws1 + 1;
            a11 = a10 + a - 1;
            is0 = a11 + 1;
            is1 = is0 + s - 1;
            g = is1 + 1;
        }
        rp0 = g + 1;
        rp1 = w - 2;
        lanes = rp1 - rp0 + 1;
        // 세로: 북쪽 벽 0 | 북쪽 칸 1..5 | 북쪽 통로 6칸 | 칸 줄 | 남쪽 통로 7칸 | 앞벽 front | 보도
        north = true;
        na0 = 6;
        na1 = na0 + 5;
        sa1 = front - 1;
        sa0 = sa1 - 6;
        row0 = na1 + 1;
        row1 = sa0 - 1;
        // 경사로는 남북 통로 줄까지 들어가도 됨: 양 끝에 차가 돌아설 평평한 3줄만 남김
        jr0 = na0 + 3;
        int avail = (sa1 - 3) - jr0 + 1;
        k = Math.max(2, Math.min(5, avail / 5));
        jr1 = jr0 + 5 * k - 1;
        stairN = front - 8;
        liftS = stairN - 1;
        gx0 = Math.max(6, a10);
        if (w >= 37) {
            gx0 = Math.max(6, (a10 + a21) / 2 - 5);
        }
    }

    // ------------------------------------------------------------------ 바닥·층판

    private void ground() {
        // 보도 (앞 한 줄)와 1층 바닥 (아스팔트)
        v.fill(0, -1, d - 1, w - 1, -1, d - 1, Block.of("smooth_stone", 0x9E9E9E));
        v.fill(0, -1, 0, w - 1, -1, front, ASPHALT);
    }

    /** 경사로 구멍 (이 층 바닥판을 뚫는 곳) */
    private boolean hole(int i, int j) {
        return i >= rp0 && i <= rp1 && j >= jr0 && j <= jr1;
    }

    /** 계단실·엘리베이터 자리 (벽 포함) */
    private boolean coreCell(int i, int j) {
        return (i <= 4 && j >= stairN && j <= front) || (i <= 3 && j >= liftS - 3 && j <= liftS);
    }

    private void decks() {
        for (int dk = 1; dk < n; dk++) {
            int y = level(dk) - 1;
            for (int j = 1; j < front; j++) {
                for (int i = 1; i < w - 1; i++) {
                    if (!hole(i, j) && !coreCell(i, j)) {
                        v.set(i, y, j, dk == n - 1 ? ASPHALT : DECK);
                    }
                }
            }
        }
        // 경사로 벽 (섬 칸과 경사로 사이): 땅부터 옥상 난간까지
        for (int j = jr0; j <= jr1; j++) {
            v.fill(g, 0, j, g, top + 1, j, parapet);
        }
        for (int dk = 0; dk < n; dk++) {
            for (int j = jr0; j <= jr1; j++) {
                v.set(g, level(dk) - 1, j, band);
            }
        }
        v.fill(g, top + 2, jr0, g, top + 2, jr1, CAP);
        // 옥상: 경사로 구멍 남쪽 끝 난간 (차가 떨어지지 않게)
        v.fill(rp0, top, jr1 + 1, rp1, top, jr1 + 1, parapet);
        v.fill(rp0, top + 1, jr1 + 1, rp1, top + 1, jr1 + 1, CAP);
    }

    // ------------------------------------------------------------------ 경사로

    private void ramps() {
        for (int dk = 0; dk + 1 < n; dk++) {
            int base = level(dk);
            for (int p = 0; p < 5 * k; p++) {
                int j = jr1 - p;
                int half = 1 + p / k; // 1..5 (반 칸 단위)
                for (int i = rp0; i <= rp1; i++) {
                    boolean mid = lanes >= 7 && i == rp0 + lanes / 2;
                    if (half % 2 == 1) {
                        int y = base + (half - 1) / 2;
                        v.set(i, y, j, mid ? RAMP_LINE_LOW : RAMP_LOW);
                        if (dk == 0 && y > 0) {
                            v.fill(i, 0, j, i, y - 1, j, DECK);
                        }
                    } else {
                        int y = base + half / 2 - 1;
                        if (dk == 0) {
                            v.fill(i, 0, j, i, y, j, DECK);
                            if (mid) {
                                v.set(i, y, j, Block.of("smooth_sandstone", 0xDFD6AA));
                            }
                        } else {
                            v.set(i, y, j, mid ? RAMP_LINE_HIGH : RAMP_HIGH);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 주차 칸

    private void stalls() {
        for (int dk = 0; dk < n; dk++) {
            int L = level(dk);
            // 서쪽 칸 (차 앞 동쪽) — 엘리베이터 북쪽까지
            column(L, ws0, ws1, row0, liftS - 4, 1, true, dk);
            // 섬 (차 앞 서쪽)
            column(L, is0, is1, row0, row1, -1, false, dk);
            if (i20 >= 0) {
                column(L, i20, i21, row0, row1, 1, false, dk);
                column(L, es0, es1, row0, row1, -1, false, dk);
            }
            // 북쪽 벽 칸 (차 앞 남쪽)
            if (north) {
                int x1 = g - 1;
                int idx = 0;
                for (int i = 1 + walk; i + 3 <= x1; i += 3) {
                    v.fill(i, L - 1, 1, i, L - 1, 5, LINE);
                    v.fill(i + 3, L - 1, 1, i + 3, L - 1, 5, LINE);
                    v.carSpot(i + 2.0, L, 3.5, 0, 1);
                    if (dk > 0 && idx++ % 2 == 0) {
                        v.set(i, L - 1, 5, CEILING_LIGHT);
                    }
                }
            }
            // 통로 가운데 노란 점선 (주 통로)
            dashes(L, (a10 + a11) / 2, row0, row1);
            if (a20 >= 0) {
                dashes(L, (a20 + a21) / 2, row0, row1);
            }
        }
    }

    /**
     * 칸 한 줄: i 범위 x0..x1 (깊이), j 범위 j0..j1 에 3칸마다 선. nose 는 차 앞이 보는 i 방향.
     * west 면 엘리베이터에 가까운 남쪽 두 칸을 장애인 칸(파란 선)으로.
     *
     * @return 칸 수
     */
    private int column(int L, int x0, int x1, int j0, int j1, int nose, boolean west, int dk) {
        int count = 0;
        int last = j0;
        for (int j = j0; j + 3 <= j1; j += 3) {
            last = j;
        }
        int edge = nose > 0 ? x1 : x0, back = nose > 0 ? x0 : x1;
        for (int j = j0; j + 3 <= j1; j += 3) {
            boolean blue = west && j >= last - 3;
            boolean pink = !west && dk == 0 && nose < 0 && x0 == is0 && j <= j0 + 3;
            Block line = blue ? BLUE_CONCRETE : pink ? PINK_CONCRETE : LINE;
            v.fill(x0, L - 1, j, x1, L - 1, j, line);
            v.fill(x0, L - 1, j + 3, x1, L - 1, j + 3, line);
            // 칸 끝 선 (통로 쪽)
            v.fill(edge, L - 1, j + 1, edge, L - 1, j + 2, line);
            v.carSpot((x0 + x1 + 1) / 2.0, L, j + 2.0, nose, 0);
            count++;
        }
        for (int j = j0; j <= j1; j += 3) {
            int idx = (j - j0) / 3;
            // 아래층 천장 등: 선 끝 (통로 쪽), 한 칸 걸러
            if (dk > 0 && idx % 2 == 0 && v.get(edge, L - 1, j) != null) {
                v.set(edge, L - 1, j, CEILING_LIGHT);
            }
            // 기둥: 칸 머리(뒤쪽) 선 위, 두 칸마다 — 경사로 벽이나 바깥 벽에 붙은 줄은 벽이 대신함
            if (idx % 2 == 1 && dk < n - 1 && back > 1 && back < w - 2 && v.get(back + Integer.signum(back - edge), L, j) == null) {
                v.fill(back, L, j, back, L + 1, j, PILLAR);
                v.set(back, L, j, PILLAR_BASE);
                String zone = String.valueOf((char) ('A' + Math.min(25, Math.max(0, (x0 - 1) / 6)))) + (idx / 2 + 1);
                v.set(back - Integer.signum(back - edge), L + 1, j, Blocks.wallSign("birch", nose > 0 ? "east" : "west",
                        "black", false, "", (dk + 1) + "층 " + zone));
            }
        }
        return count;
    }

    private void dashes(int L, int i, int j0, int j1) {
        for (int j = j0 + 1; j < j1; j += 4) {
            v.fill(i, L - 1, j, i, L - 1, Math.min(j + 1, j1), YELLOW_CONCRETE);
        }
    }

    // ------------------------------------------------------------------ 계단실·엘리베이터

    /**
     * 계단: 계단실 안 i 1(올라가는 줄 A)·2(가운데 벽)·3(줄 B), j stairN+1..front-1.
     * 짝수 층은 남쪽 참(front-2..front-1), 홀수 층은 북쪽 참(stairN+1..stairN+2).
     * 짝수 층 → 줄 A 를 북쪽으로 3단, 홀수 층 → 줄 B 를 남쪽으로 3단 올라 다음 층 참에 닿습니다.
     */
    private void core() {
        Block wall = Interior.CORE_WALL;
        int s0 = stairN, s1 = front;
        int yTop = top + 3;
        v.walls(0, 0, s0, 4, yTop - 1, s1, wall);
        v.fill(1, 0, s0 + 1, 3, yTop - 1, s1 - 1, AIR);
        v.fill(0, yTop, s0, 4, yTop, s1, SMOOTH_STONE);
        Block upN = Blocks.stairs("stone_brick", "north", 0x7A7979);
        Block upS = Blocks.stairs("stone_brick", "south", 0x7A7979);
        for (int dk = 0; dk < n; dk++) {
            int L = level(dk);
            boolean south = dk % 2 == 0;
            int l0 = south ? s1 - 2 : s0 + 1;
            // 참 (이 층의 바닥)
            v.fill(1, L - 1, l0, 3, L - 1, l0 + 1, Interior.LANDING);
            // 층 문 (동쪽 벽, 통로 쪽) — 옥상도
            int dj = south ? s1 - 2 : s0 + 2;
            v.set(4, L, dj, Blocks.door("pale_oak", "east", false));
            v.set(4, L + 1, dj, Blocks.door("pale_oak", "east", true));
            v.set(2, L + 2, south ? s1 - 1 : s0 + 1, Interior.LIGHT);
            if (dk + 1 < n) {
                for (int s = 0; s < 3; s++) {
                    if (south) {
                        v.set(1, L + s, s1 - 3 - s, upN);
                    } else {
                        v.set(3, L + s, s0 + 3 + s, upS);
                    }
                }
            }
        }
        // 가운데 벽 (계단 줄 사이)
        v.fill(2, 0, s0 + 3, 2, yTop - 1, s1 - 3, wall);
        // 1층: 길에서 바로 들어오는 문
        v.set(1, 0, s1, Blocks.door("pale_oak", "south", false));
        v.set(1, 1, s1, Blocks.door("pale_oak", "south", true));
        // 유리 계단탑: 앞(남)·서쪽 벽은 짙은 틀(모서리·층 띠) 사이 통유리, 밤에 계단참 등이 비침
        Block glass = Block.of("light_blue_stained_glass_pane", 0x6699D8);
        Block mullion = POLISHED_DEEPSLATE;
        for (int y = 0; y <= yTop - 1; y++) {
            boolean bandRow = y % H == 2 || y == yTop - 1;
            for (int i = 0; i <= 4; i++) {
                if (!wall.equals(v.get(i, y, s1))) {
                    continue; // 문
                }
                v.set(i, y, s1, i == 0 || i == 4 || bandRow || y == 0 ? mullion : glass);
            }
            for (int j = s0; j < s1; j++) {
                if (wall.equals(v.get(0, y, j))) {
                    v.set(0, y, j, j == s0 || bandRow || y == 0 ? mullion : glass);
                }
            }
        }
        // 엘리베이터 (문은 동쪽, 앞이 보행자 통로)
        int[] lv = new int[n];
        for (int dk = 0; dk < n; dk++) {
            lv[dk] = level(dk);
        }
        Interior.elevator(Frame.facing(v, 1, liftS - 1, "east"), lv, wall);
        // 보행자 통로 (초록): 엘리베이터 앞 ~ 계단실 문 앞 ~ 남쪽 통로
        for (int dk = 0; dk < n; dk++) {
            int L = level(dk);
            v.fill(4, L - 1, liftS - 3, Math.min(5, a10 - 1), L - 1, liftS, WALKWAY);
            v.fill(5, L - 1, stairN, Math.min(5, a10 - 1), L - 1, front - 1, WALKWAY);
            if (walk > 0) {
                v.fill(1, L - 1, row0, walk, L - 1, liftS - 4, WALKWAY);
            }
        }
    }

    // ------------------------------------------------------------------ 1층 출입구

    private void gate() {
        int x = gx0;
        // 앞벽 트기 (나가는 길 x..x+3, 섬 x+4..x+6, 들어가는 길 x+7..x+10)
        v.fill(x, 0, front, x + 10, 1, front, AIR);
        // 높이 제한 띠 (노랑·검정)
        for (int i = x; i <= x + 10; i++) {
            v.set(i, 2, front, (i & 1) == 0 ? YELLOW_CONCRETE : BLACK_CONCRETE);
        }
        // 섬: 연석, 정산소 부스 (안쪽), 차단기 기둥 둘
        Block curb = Block.of("smooth_stone", 0x9E9E9E);
        v.fill(x + 4, 0, front, x + 6, 0, d - 1, curb);
        int b0 = front - 3;
        v.walls(x + 4, 0, b0, x + 6, 1, front - 1, WHITE_CONCRETE);
        v.set(x + 5, 0, b0 + 1, AIR);
        v.set(x + 5, 1, b0 + 1, AIR);
        v.set(x + 5, -1, b0 + 1, Block.of("spruce_planks", 0x725430));
        v.set(x + 4, 1, b0 + 1, GLASS_PANE);          // 나가는 차를 보는 정산 창
        v.set(x + 6, 1, b0 + 1, GLASS_PANE);
        v.set(x + 5, 1, front - 1, GLASS_PANE);
        v.set(x + 5, 0, b0, Blocks.door("pale_oak", "north", false));
        v.set(x + 5, 1, b0, Blocks.door("pale_oak", "north", true));
        // 차단기: 기둥(흰·파랑) 위로 세운 막대 (내려와 있으면 차가 못 지나감 → 올려 둠)
        for (int i : new int[]{x + 4, x + 6}) {
            v.set(i, 1, front, Block.of("blue_concrete", 0x2C2E8F));
            v.set(i, 2, d - 1, Block.of("end_rod[facing=up]", 0xE6DCD0));
        }
        v.set(x + 4, 1, d - 1, WHITE_CONCRETE);
        v.set(x + 6, 1, d - 1, WHITE_CONCRETE);
        // 주차권 기계 (들어가는 길 왼쪽) — 섬 끝
        v.set(x + 6, 0, d - 1, Block.of("smooth_quartz", 0xECE6DF));
        v.set(x + 5, 1, d - 1, Blocks.wallSign("birch", "south", "black", false, "주차요금", "10분 500원", "1일 1만원"));
        v.set(x + 5, 1, front, WHITE_CONCRETE);
        // 입구·출구 표시 (띠에 붙은 표지판)
        v.set(x + 8, 2, d - 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "입구 IN"));
        v.set(x + 2, 2, d - 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "출구 OUT"));
        v.set(x + 5, 2, d - 1, Blocks.wallSign("birch", "south", "black", false, "높이제한", "2.3m"));
        // 길바닥: 들어가는 길·나가는 길 화살표 대신 흰 정지선
        v.fill(x, -1, d - 1, x + 3, -1, d - 1, LINE);
        v.fill(x + 7, -1, d - 1, x + 10, -1, d - 1, LINE);
        v.fill(x, -1, front, x + 3, -1, front, ASPHALT);
        v.fill(x + 7, -1, front, x + 10, -1, front, ASPHALT);
    }

    // ------------------------------------------------------------------ 겉모양

    private boolean perimeter(int i, int j) {
        return (i == 0 || i == w - 1 || j == 0 || j == front) && j <= front;
    }

    private void facade() {
        for (int j = 0; j <= front; j++) {
            for (int i = 0; i < w; i++) {
                if (!perimeter(i, j) || coreCell(i, j)) {
                    continue;
                }
                boolean gateCell = j == front && i >= gx0 && i <= gx0 + 10;
                for (int dk = 0; dk < n; dk++) {
                    int L = level(dk);
                    if (dk > 0) {
                        v.set(i, L - 1, j, band);
                    }
                    if (dk == 0 && gateCell) {
                        continue;
                    }
                    if (dk == n - 1) {
                        v.set(i, L, j, parapet);
                        v.set(i, L + 1, j, CAP);
                    } else {
                        v.set(i, L, j, parapet);
                        v.set(i, L + 1, j, gap);
                    }
                }
            }
        }
        // 기둥 (둘레 6~7칸마다, 모서리)
        int step = 6 + r.nextInt(2);
        for (int i = 0; i < w; i += step) {
            pillar(i, 0);
            if (!(i >= gx0 && i <= gx0 + 10) && !coreCell(i, front)) {
                pillar(i, front);
            }
        }
        pillar(w - 1, 0);
        pillar(w - 1, front);
        for (int j = 0; j <= front; j += step) {
            if (!coreCell(0, j)) {
                pillar(0, j);
            }
            pillar(w - 1, j);
        }
        if (gx0 - 1 > 4) {
            pillar(gx0 - 1, front);
        }
        pillar(gx0 + 11, front);
    }

    private void pillar(int i, int j) {
        v.fill(i, 0, j, i, top, j, column);
        v.set(i, top + 1, j, CAP);
    }

    // ------------------------------------------------------------------ 조명·간판

    private void lights() {
        // 천장 등은 주차 칸 선 끝(통로 쪽)에 박아 둠 (column() 에서) — 위층 바닥에서는 흰 선처럼 보임.
        // 남쪽 통로는 칸이 없어서 가운데 줄에 (옥상 바닥판은 빼고)
        for (int dk = 1; dk < n - 1; dk++) {
            int y = level(dk) - 1;
            for (int i = 6; i < g; i += 6) {
                Block b = v.get(i, y, (sa0 + sa1) / 2);
                if (DECK.equals(b)) {
                    v.set(i, y, (sa0 + sa1) / 2, CEILING_LIGHT);
                }
            }
        }
        // 경사로 벽등
        for (int dk = 0; dk < n; dk++) {
            for (int j = jr0 + 2; j < jr1; j += 5) {
                v.set(g, level(dk) + 1, j, CEILING_LIGHT);
            }
        }
        // 옥상 가로등 (둘레 난간 위)
        for (int i = 4; i < w - 2; i += 9) {
            lamp(i, 0);
        }
        for (int j = 6; j < front - 2; j += 9) {
            lamp(w - 1, j);
            if (!coreCell(0, j)) {
                lamp(0, j);
            }
        }
    }

    private void lamp(int i, int j) {
        v.fill(i, top + 1, j, i, top + 3, j, StreetPlan.POST);
        v.set(i, top + 4, j, LANTERN);
    }

    private void signs() {
        for (int dk = 0; dk < n; dk++) {
            int L = level(dk);
            boolean south = dk % 2 == 0;
            // 층 표시 (계단 문 옆, 통로 쪽 벽)
            int dj = south ? front - 3 : stairN + 3;
            String label = dk == n - 1 ? "옥상" : (dk + 1) + "층";
            v.set(5, L + 1, dj, Blocks.wallSign("birch", "east", "black", false, "", label, "계단·EV"));
            // 경사로 남쪽 끝(오르는 곳)·북쪽 끝(내려가는 곳) 안내 — 경사로 벽 끝에 붙임
            if (dk + 1 < n) {
                String up = dk + 2 == n ? "옥상" : (dk + 2) + "층";
                v.set(g, L + 1, jr1 + 1, Blocks.wallSign("birch", "south", "black", false, "", up + " 가는 길"));
            }
            if (dk > 0) {
                v.set(g, L + 1, jr0 - 1, Blocks.wallSign("birch", "north", "black", false, "", "내려가는 길", "출구"));
            }
        }
        // 출입구 위 P 간판 (2층 난간 자리에 파란 판)
        int pi = gx0 + 5, y0 = level(1);
        v.fill(pi - 1, y0, front, pi + 1, y0 + 1, front, BLUE_CONCRETE);
        v.set(pi, y0 + 1, d - 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "P"));
        v.set(pi, y0, d - 1, Blocks.wallSign("dark_oak", "south", "white", true, "P 공영주차장", fit(name), "24시간"));
        // 계단탑 꼭대기 P
        int yT = top + 1;
        v.fill(1, yT, front, 3, yT + 1, front, BLUE_CONCRETE);
        v.set(2, yT + 1, d - 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "P"));
        v.set(2, yT, d - 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "공영주차장"));
    }

    /** 표지판 한 줄에 들어가게 자름 (한글 9px, 나머지 6px, 90px) */
    static String fit(String s) {
        int px = 0;
        StringBuilder out = new StringBuilder();
        for (char ch : s.toCharArray()) {
            px += ch >= 0xAC00 && ch <= 0xD7A3 ? 9 : 6;
            if (px > 90) {
                break;
            }
            out.append(ch);
        }
        return out.toString();
    }

}
