package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 대형시장 본관 (광장시장 느낌). 십자로 난 큰 골목(8칸)과 작은 골목(5칸)에 유리 아치 지붕을 씌우고,
 * 골목 양쪽에 4칸짜리 가게를 빽빽하게 늘어놓습니다. 가운데는 유리 돔 아래 먹자 광장(빈대떡·떡볶이 포장마차).
 * 가게마다 줄무늬 차양과 색 간판, 물건(먹거리·한복 천·채소·건어물·생선·꽃·잡화)이 다르고,
 * 남서쪽 구역은 수산시장입니다. 바깥벽은 알록달록한 패널과 불빛 띠, 정문 위에는 큰 간판.
 * <p>
 * 상자 둘레 2칸은 간판·차양이 튀어나오는 자리입니다.
 */
final class MarketHall {
    /** 가게 지붕 높이 */
    private static final int ROOF = 6;
    private static final int MARGIN = 2;
    private static final String[] BRIGHT = {"red", "yellow", "orange", "lime", "light_blue", "magenta", "pink", "cyan", "blue", "green", "purple"};
    private static final Block[] WALL_PANELS = {YELLOW_TERRACOTTA, WHITE_CONCRETE, ORANGE_TERRACOTTA, LIGHT_BLUE_TERRACOTTA,
            PINK_TERRACOTTA, LIME_TERRACOTTA, WHITE_TERRACOTTA};

    private final Voxels v;
    private final Random r;
    private final long seed;
    /** 홀 안쪽 좌표 범위 (바깥벽 포함) */
    private final int a0, a1, b0, b1;
    private final int ci, cj;
    private final boolean[] alleyI, alleyJ;
    private final double plazaR;

    private MarketHall(int w, int d, Random r) {
        this.v = new Voxels(w, d, -1, 26);
        this.r = r;
        this.seed = r.nextLong();
        a0 = MARGIN;
        a1 = w - 1 - MARGIN;
        b0 = MARGIN;
        b1 = d - 1 - MARGIN;
        ci = (a0 + a1 + 1) / 2;
        cj = (b0 + b1 + 1) / 2;
        alleyI = new boolean[w];
        alleyJ = new boolean[d];
        mark(alleyI, ci - 4, ci + 3);
        mark(alleyJ, cj - 4, cj + 3);
        int qw = (a1 - a0) / 4;
        mark(alleyI, a0 + qw - 2, a0 + qw + 2);
        mark(alleyI, a1 - qw - 2, a1 - qw + 2);
        int qd = (b1 - b0) / 4;
        if (b1 - b0 > 50) {
            mark(alleyJ, b0 + qd - 2, b0 + qd + 2);
            mark(alleyJ, b1 - qd - 2, b1 - qd + 2);
        }
        plazaR = Math.min(9.5, Math.min(a1 - a0, b1 - b0) / 5.0);
    }

    private static void mark(boolean[] a, int from, int to) {
        for (int k = Math.max(0, from); k <= Math.min(a.length - 1, to); k++) {
            a[k] = true;
        }
    }

    static Voxels build(int w, int d, Random r) {
        MarketHall m = new MarketHall(w, d, r);
        m.floorsAndStalls();
        m.roofs();
        m.plaza();
        m.outerWalls();
        m.v.connect();
        return m.v;
    }

    private boolean inside(int i, int j) {
        return i > a0 && i < a1 && j > b0 && j < b1;
    }

    private boolean inPlaza(int i, int j) {
        return Math.hypot(i + 0.5 - ci, j + 0.5 - cj) < plazaR;
    }

    /** 사람이 다니는 칸 (골목·광장) */
    private boolean open(int i, int j) {
        return inside(i, j) && (alleyI[i] || alleyJ[j] || inPlaza(i, j));
    }

    // ------------------------------------------------------------------ 바닥과 가게

    private static final int[][] DIRS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}; // 남, 북, 동, 서

    private void floorsAndStalls() {
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                if (!inside(i, j)) {
                    continue;
                }
                if (open(i, j)) {
                    // 골목 바닥: 가운데 줄무늬
                    boolean center = (alleyI[i] && Math.abs(i + 0.5 - ci) < 1) || (alleyJ[j] && Math.abs(j + 0.5 - cj) < 1);
                    v.set(i, -1, j, center ? POLISHED_GRANITE : SMOOTH_STONE);
                    continue;
                }
                v.set(i, -1, j, POLISHED_ANDESITE);
                v.set(i, ROOF, j, LIGHT_GRAY_CONCRETE);
                // 가장 가까운 골목 쪽 (그쪽을 보는 가게)
                int best = Integer.MAX_VALUE, dir = -1;
                for (int k = 0; k < 4; k++) {
                    for (int s = 1; s <= 6; s++) {
                        int ni = i + DIRS[k][0] * s, nj = j + DIRS[k][1] * s;
                        if (!inside(ni, nj)) {
                            break;
                        }
                        if (open(ni, nj)) {
                            if (s - 1 < best) {
                                best = s - 1;
                                dir = k;
                            }
                            break;
                        }
                    }
                }
                if (dir < 0 || best > 3) {
                    continue; // 가게 뒤 창고 (지붕만)
                }
                stallCell(i, j, best, dir);
            }
        }
    }

    /** 가게 한 칸. m 은 골목에서 몇 칸 안쪽인지(0 = 판매대 줄), dir 은 가게가 보는 쪽 */
    private void stallCell(int i, int j, int m, int dir) {
        int along = DIRS[dir][0] == 0 ? i : j;
        int stall = Math.floorDiv(along, 4);
        int slot = Math.floorMod(along, 4);
        long h = seed ^ (stall * 0x9E3779B97F4A7C15L) ^ ((long) dir << 40) ^ ((long) (DIRS[dir][0] == 0 ? j / 8 : i / 8) << 20);
        Random sr = new Random(h);
        int kind = sr.nextInt(7);
        // 남서쪽은 수산시장
        if (i < ci && j > cj && sr.nextInt(10) < 7) {
            kind = 4;
        }
        String color = switch (kind) {
            case 0 -> new String[]{"red", "orange", "yellow"}[sr.nextInt(3)];
            case 1 -> new String[]{"magenta", "pink", "purple"}[sr.nextInt(3)];
            case 2 -> new String[]{"lime", "green", "yellow"}[sr.nextInt(3)];
            case 4 -> new String[]{"light_blue", "cyan", "blue"}[sr.nextInt(3)];
            default -> BRIGHT[sr.nextInt(BRIGHT.length)];
        };
        Block awningA = wool(color), awningB = sr.nextInt(3) == 0 ? YELLOW_WOOL : WHITE_WOOL;
        Block sign = concrete(BRIGHT[sr.nextInt(BRIGHT.length)]);

        if (slot == 0) {
            // 가게 사이 칸막이와 기둥
            v.fill(i, 0, j, i, m == 0 ? ROOF - 1 : 2, j, m == 0 ? WHITE_CONCRETE : SPRUCE_PLANKS);
            return;
        }
        switch (m) {
            case 0 -> {
                // 판매대
                v.set(i, 0, j, counter(kind, sr));
                if (kind == 1 || kind == 2) {
                    v.set(i, 1, j, kind == 1 ? wool(BRIGHT[sr.nextInt(BRIGHT.length)]) : (sr.nextBoolean() ? MELON : PUMPKIN));
                }
                if (kind == 5) {
                    v.set(i, 0, j, SPRUCE_PLANKS);
                    v.set(i, 1, j, sr.nextBoolean() ? FLOWER_POT_TULIP : FLOWER_POT_DANDELION);
                }
                // 줄무늬 차양 (골목으로 한 칸 튀어나옴)
                Block stripe = (along % 2 == 0) ? awningA : awningB;
                v.set(i, 3, j, stripe);
                v.set(i + DIRS[dir][0], 3, j + DIRS[dir][1], stripe);
                // 간판 두 줄, 가운데에 불빛
                v.set(i, 4, j, sign);
                v.set(i, 5, j, slot == 2 ? SEA_LANTERN : sign);
            }
            case 1 -> {
                if (kind == 0 && slot == 2) {
                    v.set(i, 0, j, CAULDRON);
                }
            }
            case 2 -> {
                Block back = switch (kind) {
                    case 1 -> wool(BRIGHT[sr.nextInt(BRIGHT.length)]);
                    case 2, 3 -> BARREL;
                    case 4 -> BLUE_ICE;
                    case 5 -> AZALEA_LEAVES;
                    default -> null;
                };
                if (back != null) {
                    int hgt = kind == 1 ? 1 + sr.nextInt(3) : 1;
                    for (int y = 0; y < hgt; y++) {
                        v.set(i, y, j, kind == 1 ? wool(BRIGHT[sr.nextInt(BRIGHT.length)]) : back);
                    }
                }
                if (slot == 2) {
                    v.set(i, ROOF - 1, j, LANTERN_HANGING);
                }
            }
            default -> {
                // 뒷벽 (선반)
                v.fill(i, 0, j, i, ROOF - 1, j, kind == 1 ? wool(color) : WHITE_TERRACOTTA);
                v.set(i, 1, j, kind == 3 || kind == 2 ? BARREL : v.get(i, 1, j));
            }
        }
    }

    private static Block counter(int kind, Random r) {
        return switch (kind) {
            case 0 -> r.nextInt(3) == 0 ? CAMPFIRE : SMOKER; // 빈대떡·떡볶이
            case 1 -> wool(BRIGHT[r.nextInt(BRIGHT.length)]); // 한복·원단
            case 2 -> r.nextBoolean() ? MELON : HAY; // 채소·과일·곡물
            case 3 -> r.nextBoolean() ? DRIED_KELP : BARREL; // 건어물
            case 4 -> r.nextBoolean() ? PACKED_ICE : PRISMARINE; // 생선
            case 5 -> SPRUCE_PLANKS; // 꽃
            default -> r.nextBoolean() ? CAULDRON : BARREL; // 그릇·잡화
        };
    }

    // ------------------------------------------------------------------ 지붕

    /** 가운데 광장 유리 돔 높이 (없으면 0) */
    private double dome(double i, double j) {
        double rr = Math.hypot(i - ci, j - cj), domeR = plazaR + 1.5;
        return rr <= domeR ? ROOF + Math.sqrt(domeR * domeR - rr * rr) * 0.95 : 0;
    }

    /**
     * 한 방향 골목들의 아치 높이 (없으면 0). coord 는 골목을 가로지르는 좌표, cell 은 그 칸 번호,
     * other 는 골목을 따라가는 칸 번호. 골목 칸과 그 양옆 1칸(차양 줄)을 덮습니다.
     */
    private double arch(double coord, boolean[] alley, int cell, int other, boolean ns) {
        int n = alley.length;
        if (cell < 0 || cell >= n || (ns ? (other < b0 || other > b1) : (other < a0 || other > a1))) {
            return 0;
        }
        int at = -1;
        for (int k = Math.max(0, cell - 1); k <= Math.min(n - 1, cell + 1); k++) {
            if (alley[k]) {
                at = k;
                break;
            }
        }
        if (at < 0) {
            return 0;
        }
        int lo = at, hi = at;
        while (lo > 0 && alley[lo - 1]) {
            lo--;
        }
        while (hi < n - 1 && alley[hi + 1]) {
            hi++;
        }
        double center = (lo + hi + 1) / 2.0, half = (hi - lo + 1) / 2.0 + 1;
        double u = Math.abs(coord - center);
        if (u > half) {
            return 0;
        }
        return ROOF + Math.sqrt(half * half - u * u) * (half > 4 ? 1.0 : 0.9);
    }

    private double vaultHeight(double i, double j) {
        int ii = (int) Math.floor(i), jj = (int) Math.floor(j);
        return Math.max(dome(i, j), Math.max(arch(i, alleyI, ii, jj, true), arch(j, alleyJ, jj, ii, false)));
    }

    private void roofs() {
        int w = v.w, d = v.d;
        double[][] hgt = new double[w][d];
        boolean[][] nsRib = new boolean[w][d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                double ns = arch(i + 0.5, alleyI, i, j, true), ew = arch(j + 0.5, alleyJ, j, i, false);
                hgt[i][j] = Math.max(dome(i + 0.5, j + 0.5), Math.max(ns, ew));
                nsRib[i][j] = ns >= ew;
            }
        }
        for (int i = a0; i <= a1; i++) {
            for (int j = b0; j <= b1; j++) {
                if (hgt[i][j] <= 0) {
                    continue;
                }
                int top = (int) Math.floor(hgt[i][j]);
                if (i == a0 || i == a1 || j == b0 || j == b1) {
                    // 아치 끝(바깥벽 위) 반달 모양을 유리로 막음
                    if (top > ROOF + 1) {
                        v.fill(i, ROOF + 2, j, i, top, j, WHITE_GLASS);
                    }
                    continue;
                }
                int low = top;
                for (int[] dd : DIRS) {
                    double nh = hgt[i + dd[0]][j + dd[1]];
                    low = Math.min(low, nh <= 0 ? ROOF : (int) Math.floor(nh));
                }
                low = Math.max(ROOF, Math.min(low + 1, top));
                boolean rib = (nsRib[i][j] ? j : i) % 4 == 0;
                boolean domeRib = false;
                double rr = Math.hypot(i + 0.5 - ci, j + 0.5 - cj);
                if (dome(i + 0.5, j + 0.5) >= hgt[i][j] && rr <= plazaR + 1.5) {
                    double ang = Math.toDegrees(Math.atan2(j + 0.5 - cj, i + 0.5 - ci));
                    domeRib = Math.abs(((ang % 45) + 45) % 45 - 22.5) > 19.5 || rr < 1.5;
                    rib = false;
                }
                Block shell = domeRib ? GOLD_BLOCK : rib ? RED_CONCRETE : WHITE_GLASS;
                for (int y = low; y <= top; y++) {
                    v.set(i, y, j, shell);
                }
                // 아치 꼭대기 줄에 갈빗대마다 매달린 등
                boolean ridge = nsRib[i][j] ? alleyI[i] && Math.abs(i + 0.5 - centerOf(alleyI, i)) < 1
                        : alleyJ[j] && Math.abs(j + 0.5 - centerOf(alleyJ, j)) < 1;
                if (rib && ridge && top > ROOF + 2 && open(i, j)) {
                    v.set(i, top - 1, j, LANTERN_HANGING);
                }
            }
        }
    }

    private static double centerOf(boolean[] alley, int k) {
        int lo = k, hi = k;
        while (lo > 0 && alley[lo - 1]) {
            lo--;
        }
        while (hi < alley.length - 1 && alley[hi + 1]) {
            hi++;
        }
        return (lo + hi + 1) / 2.0;
    }

    // ------------------------------------------------------------------ 가운데 먹자 광장

    private void plaza() {
        double rr0 = plazaR;
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                double rr = Math.hypot(i + 0.5 - ci, j + 0.5 - cj);
                if (rr >= rr0) {
                    continue;
                }
                // 바닥 무늬: 동심원
                v.set(i, -1, j, ((int) rr) % 3 == 0 ? POLISHED_GRANITE : (((int) rr) % 3 == 1 ? SMOOTH_STONE : POLISHED_DIORITE));
                double ang = Math.toDegrees(Math.atan2(j + 0.5 - cj, i + 0.5 - ci));
                boolean walkway = Math.abs(i + 0.5 - ci) < 2.5 || Math.abs(j + 0.5 - cj) < 2.5;
                // 둥근 포장마차 판매대
                if (rr >= rr0 * 0.42 && rr < rr0 * 0.42 + 1 && !walkway) {
                    v.set(i, 0, j, ((int) (ang + 360)) % 30 < 8 ? SMOKER : SMOOTH_QUARTZ);
                    v.set(i, 3, j, ((int) (ang + 360) / 15) % 2 == 0 ? ORANGE_WOOL : RED_WOOL);
                }
                // 손님 의자
                if (rr >= rr0 * 0.42 + 2 && rr < rr0 * 0.42 + 3 && !walkway && ((i + j) % 2 == 0)) {
                    v.set(i, 0, j, SPRUCE_SLAB);
                }
            }
        }
        // 가운데: 빈대떡 굽는 큰 솥과 불
        v.set(ci, 0, cj, CAMPFIRE);
        v.set(ci - 1, 0, cj, CAULDRON);
        v.set(ci, 0, cj - 1, CAULDRON);
        // 청사초롱 샹들리에 (돔 꼭대기에 매달림)
        int top = (int) Math.floor(vaultHeight(ci + 0.5, cj + 0.5));
        for (int y = top - 1; y >= top - 3; y--) {
            v.set(ci, y, cj, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
        }
        int ring = top - 4;
        for (int k = 0; k < 8; k++) {
            double ang = Math.toRadians(k * 45);
            int li = (int) Math.floor(ci + 0.5 + 2.6 * Math.cos(ang)), lj = (int) Math.floor(cj + 0.5 + 2.6 * Math.sin(ang));
            v.set(li, ring, lj, RED_WOOL);
            v.set(li, ring - 1, lj, SHROOMLIGHT);
            v.set(li, ring - 2, lj, BLUE_WOOL);
        }
        v.set(ci, ring, cj, GOLD_BLOCK);
        v.set(ci, ring - 1, cj, SEA_LANTERN);
    }

    // ------------------------------------------------------------------ 바깥벽과 문

    private void outerWalls() {
        int top = ROOF + 1;
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                boolean edge = i == a0 || i == a1 || j == b0 || j == b1;
                if (!edge) {
                    continue;
                }
                int along = (i == a0 || i == a1) ? j : i;
                Block panel = WALL_PANELS[Math.floorMod(Math.floorDiv(along, 6) * 7 + (i == a0 ? 3 : 0) + (j == b0 ? 5 : 0), WALL_PANELS.length)];
                v.set(i, -1, j, POLISHED_ANDESITE);
                v.fill(i, 0, j, i, 3, j, along % 6 == 0 ? WHITE_CONCRETE : panel);
                if (along % 6 >= 2 && along % 6 <= 4) {
                    v.set(i, 1, j, GLASS_PANE);
                    v.set(i, 2, j, GLASS_PANE);
                }
                // 간판 띠 (여러 색), 위에 불빛 줄
                Block sign = concrete(BRIGHT[Math.floorMod(Math.floorDiv(along, 6) * 5 + 1, BRIGHT.length)]);
                v.set(i, 4, j, along % 6 == 3 ? SEA_LANTERN : sign);
                v.set(i, 5, j, sign);
                v.set(i, ROOF, j, WHITE_CONCRETE);
                v.set(i, top, j, along % 3 == 0 ? (along % 6 == 0 ? SHROOMLIGHT : SEA_LANTERN) : WHITE_CONCRETE);
            }
        }
        // 문: 골목이 바깥벽과 만나는 곳을 엶
        for (int i = a0; i <= a1; i++) {
            if (alleyI[i]) {
                v.fill(i, 0, b0, i, 4, b0, AIR);
                v.fill(i, 0, b1, i, 4, b1, AIR);
            }
        }
        for (int j = b0; j <= b1; j++) {
            if (alleyJ[j]) {
                v.fill(a0, 0, j, a0, 4, j, AIR);
                v.fill(a1, 0, j, a1, 4, j, AIR);
            }
        }
        // 큰 문 네 곳: 빨간 기둥, 큰 간판
        gate(ci, b1, 1, 0, 1, "JUNSEO MARKET");
        gate(ci, b0, -1, 0, -1, "MARKET");
        gate(a1, cj, 0, -1, 1, "MARKET");
        gate(a0, cj, 0, 1, -1, "MARKET");
    }

    /**
     * 큰 문. (gi, gj) 는 벽 위의 문 가운데, (ti, tj) 는 글씨가 나아가는 방향, out 은 바깥쪽 (+1/-1, 벽이 i 방향이면 j 쪽).
     */
    private void gate(int gi, int gj, int ti, int tj, int out, String text) {
        boolean southNorth = tj == 0;
        int oi = southNorth ? 0 : out, oj = southNorth ? out : 0;
        // 기둥: 문 양쪽 (골목 폭 8 + 1)
        for (int side : new int[]{-1, 1}) {
            int pi = gi + (southNorth ? side * 5 + (side < 0 ? 0 : -1) : 0);
            int pj = gj + (southNorth ? 0 : side * 5 + (side < 0 ? 0 : -1));
            for (int k = 0; k <= 1; k++) {
                v.fill(pi + oi * k, 0, pj + oj * k, pi + oi * k, 13, pj + oj * k, RED_CONCRETE);
            }
        }
        // 간판 판: 문 위 y 8~16, 벽 바로 바깥 면
        int len = PixelFont.width(text) + 4;
        int start = -len / 2;
        int y0 = 8, y1 = 16;
        for (int t = start; t < start + len; t++) {
            int i = gi + ti * t + oi, j = gj + tj * t + oj;
            for (int y = y0; y <= y1; y++) {
                boolean border = t == start || t == start + len - 1 || y == y0 || y == y1;
                v.set(i, y, j, border ? GOLD_BLOCK : RED_CONCRETE);
            }
            // 판 뒤 받침
            v.set(i - oi, y0, j - oj, WHITE_CONCRETE);
        }
        PixelFont.draw(v, text, gi + ti * (start + 2) + oi * 2, y1 - 1, gj + tj * (start + 2) + oj * 2, ti, tj, YELLOW_CONCRETE);
        // 간판 아래 불빛
        for (int t = start + 1; t < start + len - 1; t += 3) {
            v.set(gi + ti * t + oi, y0 - 1, gj + tj * t + oj, LANTERN_HANGING);
        }
    }
}
