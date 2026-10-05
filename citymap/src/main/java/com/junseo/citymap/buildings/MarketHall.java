package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 대형시장 본관: 광장시장처럼 큰 건물 하나 안에 시장이 들어 있습니다.
 * <ul>
 *   <li>바깥: 베이지 타일 3층 건물. 1층 둘레는 길 쪽 가게(유리 가게 앞, 간판), 2·3층은 창문 줄과 창문 글씨,
 *       모서리 세로 간판, 큰 문 네 곳 위 한글 간판(남문·북문·먹자골목), 옥상에 「대형시장」 대형 간판</li>
 *   <li>안: 십자로 난 큰 통로는 3층 높이로 뚫린 홀이고 철골 유리 지붕을 얹었습니다. 긴 통로(동서)는 먹자골목:
 *       가운데에 빈대떡·김밥·육회 좌판이 줄지어 있고 양옆에 긴 나무 의자, 좌판 위 등과 이름판,
 *       그 위에 한글 간판이 매달려 있습니다. 통로 양옆은 반찬·떡집·건어물·채소·수산·그릇·참기름 가게,
 *       남서쪽은 수산시장</li>
 *   <li>2층: 홀을 둘러싼 난간 복도와 원단·한복·이불 가게. 둘레 복도 네 귀퉁이에 계단</li>
 * </ul>
 * 건물 기준 정면은 남쪽(j 큰 쪽). 상자 둘레 2칸은 간판이 튀어나오는 자리입니다.
 */
final class MarketHall {
    private static final int MARGIN = 2;
    /** 바깥 가게 줄 깊이 (벽 포함) */
    private static final int RING = 7;
    /** 둘레 복도 폭 */
    private static final int HALL = 4;
    private static final int INNER = RING + HALL;
    /** 층 바닥 높이 */
    private static final int F2 = 6, F3 = 12, ROOF = 18;
    /** 홀 벽 꼭대기와 지붕 용마루 */
    private static final int WALL_TOP = 25, RIDGE = 30;
    /** 큰 통로 반폭 (가운데 칸 ± 4 → 9칸) */
    private static final int HALF_MAIN = 4;
    private static final Block TILE = WHITE_TERRACOTTA;
    private static final Block BAND = SMOOTH_STONE;
    private static final Block CHAIN = Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050);
    private static final String[] FABRIC = {"red", "pink", "magenta", "purple", "blue", "light_blue", "cyan", "lime",
            "yellow", "orange", "white", "green"};
    private static final String[] HANBOK = {"pink", "light_blue", "yellow", "white", "lime", "magenta"};

    private final Voxels v;
    private final long seed;
    private final int a0, a1, b0, b1, ci, cj;
    private final boolean[] sideI, sideJ;

    private MarketHall(int w, int d, Random r) {
        v = new Voxels(w, d, -1, ROOF + 22);
        seed = r.nextLong();
        a0 = MARGIN;
        a1 = w - 1 - MARGIN;
        b0 = MARGIN;
        b1 = d - 1 - MARGIN;
        ci = (a0 + a1) / 2;
        cj = (b0 + b1) / 2;
        sideI = new boolean[w];
        sideJ = new boolean[d];
        // 작은 통로(5칸): 안쪽 구역 가장자리와 큰 통로 사이 가운데
        int west = (a0 + INNER + ci - HALF_MAIN - 1) / 2, east = (a1 - INNER + ci + HALF_MAIN + 1) / 2;
        if (ci - HALF_MAIN - (a0 + INNER) >= 20) {
            mark(sideI, west - 2, west + 2);
            mark(sideI, east - 2, east + 2);
        }
        int north = (b0 + INNER + cj - HALF_MAIN - 1) / 2, south = (b1 - INNER + cj + HALF_MAIN + 1) / 2;
        if (cj - HALF_MAIN - (b0 + INNER) >= 20) {
            mark(sideJ, north - 2, north + 2);
            mark(sideJ, south - 2, south + 2);
        }
    }

    private static void mark(boolean[] a, int from, int to) {
        for (int k = Math.max(0, from); k <= Math.min(a.length - 1, to); k++) {
            a[k] = true;
        }
    }

    static Voxels build(int w, int d, Random r) {
        MarketHall m = new MarketHall(w, d, r);
        m.structure();
        m.floor1();
        m.floor2();
        m.stairs();
        m.nave();
        m.facade();
        m.v.connect();
        return m.v;
    }

    // ------------------------------------------------------------------ 칸 구분

    private boolean inBuilding(int i, int j) {
        return i >= a0 && i <= a1 && j >= b0 && j <= b1;
    }

    /** 바깥벽에서 안쪽으로 몇 칸 (0 = 바깥벽) */
    private int depth(int i, int j) {
        return Math.min(Math.min(i - a0, a1 - i), Math.min(j - b0, b1 - j));
    }

    private boolean mainNS(int i) {
        return Math.abs(i - ci) <= HALF_MAIN;
    }

    private boolean mainEW(int j) {
        return Math.abs(j - cj) <= HALF_MAIN;
    }

    /** 3층 높이로 뚫린 큰 통로 (홀) */
    private boolean nave(int i, int j) {
        return inBuilding(i, j) && depth(i, j) >= INNER && (mainNS(i) || mainEW(j));
    }

    /** 바깥에서 홀로 들어오는 1층 입구 통로 */
    private boolean passage(int i, int j) {
        int dd = depth(i, j);
        return inBuilding(i, j) && dd >= 1 && dd < INNER && ((mainNS(i) && (j - b0 < INNER || b1 - j < INNER))
                || (mainEW(j) && (i - a0 < INNER || a1 - i < INNER)));
    }

    /** 둘레 복도 (1·2층) */
    private boolean ringHall(int i, int j) {
        int dd = depth(i, j);
        return inBuilding(i, j) && dd >= RING && dd < INNER;
    }

    /** 작은 통로 (1·2층) */
    private boolean side(int i, int j) {
        return inBuilding(i, j) && depth(i, j) >= INNER && (sideI[i] || sideJ[j]) && !nave(i, j);
    }

    private boolean walk1(int i, int j) {
        return nave(i, j) || passage(i, j) || ringHall(i, j) || side(i, j);
    }

    // ------------------------------------------------------------------ 바닥·천장·벽

    private void structure() {
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                boolean n = nave(i, j);
                boolean centerLine = (mainEW(j) && j == cj) || (mainNS(i) && i == ci);
                v.set(i, -1, j, n && centerLine ? POLISHED_GRANITE : walk1(i, j) ? POLISHED_ANDESITE : SMOOTH_STONE);
                if (!n) {
                    v.set(i, F2, j, SMOOTH_STONE);
                    v.set(i, F3, j, SMOOTH_STONE);
                    v.set(i, ROOF, j, LIGHT_GRAY_CONCRETE);
                }
                // 1층 둘레 가게 뒷벽과 칸막이, 2층 가게 칸막이
                int dd = depth(i, j);
                if (dd >= 1 && dd < RING && !passage(i, j)) {
                    int along = (dd == j - b0 || dd == b1 - j) ? i : j;
                    if (dd == RING - 1) {
                        v.fill(i, 0, j, i, F2 - 1, j, TILE);
                    } else if (Math.floorMod(along, 7) == 0) {
                        v.fill(i, 0, j, i, F2 - 1, j, TILE);
                        v.fill(i, F2 + 1, j, i, F3 - 1, j, TILE);
                    }
                }
                // 복도 조명: 1층은 2층 바닥에, 2층은 3층 바닥에 매달린 등
                if ((ringHall(i, j) && dd == RING + 1 || side(i, j)) && (i + j) % 4 == 0) {
                    v.set(i, F2 - 1, j, LANTERN_HANGING);
                    v.set(i, F3 - 1, j, LANTERN_HANGING);
                }
                if (passage(i, j) && (i + j) % 4 == 0) {
                    v.set(i, F2 - 1, j, LANTERN_HANGING);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 1층

    private static final int[][] DIRS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

    private void floor1() {
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                int dd = depth(i, j);
                if (dd >= 1 && dd < RING - 1 && !passage(i, j)) {
                    ringShop1(i, j, dd);
                    continue;
                }
                if (dd < INNER || walk1(i, j)) {
                    continue;
                }
                // 가장 가까운 통로 쪽을 보는 가게
                int best = Integer.MAX_VALUE, dir = -1;
                for (int k = 0; k < 4; k++) {
                    for (int s = 1; s <= 7; s++) {
                        int ni = i + DIRS[k][0] * s, nj = j + DIRS[k][1] * s;
                        if (!inBuilding(ni, nj)) {
                            break;
                        }
                        if (walk1(ni, nj)) {
                            if (s - 1 < best) {
                                best = s - 1;
                                dir = k;
                            }
                            break;
                        }
                    }
                }
                if (dir >= 0 && best <= 3) {
                    stall1(i, j, best, dir);
                }
            }
        }
        // 큰 통로 가운데 먹자골목 좌판
        islands(true);
        islands(false);
    }

    private long stallSeed(int i, int j, int dir, int level) {
        int along = DIRS[dir][0] == 0 ? i : j;
        return seed ^ (Math.floorDiv(along, 4) * 0x9E3779B97F4A7C15L) ^ ((long) dir << 40) ^ ((long) level << 50)
                ^ ((long) (DIRS[dir][0] == 0 ? j / 9 : i / 9) << 20);
    }

    /** 1층 가게 한 칸. m = 통로에서 몇 칸 안쪽 (0 = 판매대) */
    private void stall1(int i, int j, int m, int dir) {
        int along = DIRS[dir][0] == 0 ? i : j;
        int slot = Math.floorMod(along, 4);
        Random sr = new Random(stallSeed(i, j, dir, 1));
        int kind = sr.nextInt(8);
        if (i < ci && j > cj && sr.nextInt(10) < 8) {
            kind = 4; // 남서쪽 수산시장
        }
        if (slot == 0) {
            v.fill(i, 0, j, i, F2 - 1, j, m == 0 ? LIGHT_GRAY_CONCRETE : TILE); // 가게 사이 벽
            return;
        }
        switch (m) {
            case 0 -> {
                v.set(i, 0, j, goods(kind, sr, 0));
                // 셔터 통 + 간판 (두 줄)
                v.set(i, F2 - 1, j, LIGHT_GRAY_CONCRETE);
                if (slot == 1) {
                    int di = DIRS[dir][0] == 0 ? 1 : 0, dj = 1 - di;
                    SignText.line(v, i, F2 - 2, j, 3, di, dj, SignText.combo(sr), sr);
                }
            }
            case 1 -> {
                if (slot == 2) {
                    v.set(i, F2 - 1, j, LANTERN_HANGING);
                }
            }
            case 2 -> {
                for (int y = 0; y < (kind == 1 || kind == 2 ? 2 : 1); y++) {
                    v.set(i, y, j, goods(kind, sr, y + 1));
                }
            }
            default -> {
                v.fill(i, 0, j, i, F2 - 1, j, TILE);
                v.set(i, 1, j, kind == 2 || kind == 6 ? BARREL : TILE);
            }
        }
    }

    /** 가게 물건. kind: 0 반찬, 1 떡집, 2 건어물, 3 채소·과일, 4 수산, 5 그릇·잡화, 6 참기름·곡물, 7 분식 */
    private static Block goods(int kind, Random r, int row) {
        return switch (kind) {
            case 0 -> new Block[]{RED_TERRACOTTA, LIME_TERRACOTTA, BROWN_TERRACOTTA, YELLOW_TERRACOTTA, IRON_BLOCK}[r.nextInt(5)];
            case 1 -> new Block[]{WHITE_CONCRETE, PINK_TERRACOTTA, LIME_TERRACOTTA, YELLOW_TERRACOTTA, WHITE_CONCRETE}[r.nextInt(5)];
            case 2 -> r.nextBoolean() ? DRIED_KELP : (r.nextBoolean() ? BARREL : BROWN_TERRACOTTA);
            case 3 -> new Block[]{MELON, PUMPKIN, HAY, MOSS, MELON}[r.nextInt(5)];
            case 4 -> row == 0 ? (r.nextBoolean() ? PACKED_ICE : BLUE_ICE) : (r.nextBoolean() ? PRISMARINE : PACKED_ICE);
            case 5 -> r.nextBoolean() ? CAULDRON : (r.nextBoolean() ? IRON_BLOCK : BARREL);
            case 6 -> r.nextBoolean() ? BARREL : HAY;
            default -> row == 0 ? (r.nextInt(3) == 0 ? SMOKER : IRON_BLOCK) : CAULDRON;
        };
    }

    /** 1층 둘레 가게 (길 쪽을 보는 가게): 안쪽 물건과 천장 등 */
    private void ringShop1(int i, int j, int dd) {
        if (dd == 2 && (i + j) % 7 == 3) {
            v.set(i, 0, j, (i + j) % 2 == 0 ? IRON_BLOCK : BARREL);
        }
        if (dd == 3 && (i * 7 + j * 3) % 7 == 0) {
            v.set(i, F2 - 1, j, SEA_LANTERN);
        }
    }

    /** 큰 통로 가운데 먹자골목 좌판: 스테인리스 판매대, 불판·솥, 양옆 긴 의자, 위에 등과 이름판 */
    private void islands(boolean ew) {
        int c = ew ? cj : ci;
        int from = ew ? a0 + INNER + 2 : b0 + INNER + 2, to = ew ? a1 - INNER - 2 : b1 - INNER - 2;
        int cross = ew ? ci : cj;
        String[][] menus = {{"빈대떡", "c"}, {"김밥", "k"}, {"육회", "r"}, {"떡볶이", "t"}, {"순대", "s"}, {"칼국수", "n"}, {"녹두전", "c"}};
        int n = 0;
        for (int s = from; s + 6 <= to; s += 11) {
            if (Math.abs(s - cross) <= HALF_MAIN + 3 || Math.abs(s + 6 - cross) <= HALF_MAIN + 3
                    || (s < cross && s + 6 > cross)) {
                continue;
            }
            Random sr = new Random(seed ^ (s * 31L) ^ (ew ? 7 : 13));
            char menu = menus[(n++ + (ew ? 0 : 3)) % menus.length][1].charAt(0);
            for (int t = s; t <= s + 6; t++) {
                for (int off = -1; off <= 1; off++) {
                    int i = ew ? t : c + off, j = ew ? c + off : t;
                    boolean end = t == s || t == s + 6;
                    if (off == 0 && !end) {
                        continue; // 주인이 서는 자리
                    }
                    v.set(i, 0, j, end ? IRON_BLOCK : islandItem(menu, sr, t - s));
                }
                // 긴 의자
                if (t > s && t < s + 6) {
                    for (int side : new int[]{-2, 2}) {
                        v.set(ew ? t : c + side, 0, ew ? c + side : t, SPRUCE_SLAB);
                    }
                }
            }
            // 등 받침대: 양 끝 기둥, 위 가로대, 매달린 등, 이름판
            for (int t : new int[]{s, s + 6}) {
                v.fill(ew ? t : c, 1, ew ? c : t, ew ? t : c, 3, ew ? c : t, SPRUCE_FENCE);
            }
            for (int t = s; t <= s + 6; t++) {
                int i = ew ? t : c, j = ew ? c : t;
                v.set(i, 4, j, SPRUCE_PLANKS);
                if ((t - s) % 2 == 1) {
                    v.set(i, 3, j, LANTERN_HANGING);
                }
            }
            Block[] combo = sr.nextBoolean() ? new Block[]{YELLOW_CONCRETE, RED_CONCRETE} : new Block[]{RED_CONCRETE, WHITE_CONCRETE};
            for (int side : new int[]{-1, 1}) {
                int i = ew ? s : c + side, j = ew ? c + side : s;
                SignText.line(v, i, 6, j, 7, ew ? 1 : 0, ew ? 0 : 1, combo, sr);
            }
        }
    }

    private static Block islandItem(char menu, Random r, int t) {
        return switch (menu) {
            case 'c' -> t % 3 == 1 ? CAMPFIRE : t % 3 == 2 ? SMOKER : IRON_BLOCK; // 빈대떡·녹두전: 불판
            case 'k' -> t % 2 == 0 ? DRIED_KELP : WHITE_CONCRETE; // 김밥
            case 'r' -> t % 2 == 0 ? RED_TERRACOTTA : IRON_BLOCK; // 육회
            case 't' -> t % 2 == 0 ? CAULDRON : RED_CONCRETE; // 떡볶이
            case 's' -> t % 2 == 0 ? BROWN_TERRACOTTA : CAULDRON; // 순대
            default -> t % 2 == 0 ? CAULDRON : SMOKER; // 칼국수
        };
    }

    // ------------------------------------------------------------------ 2층

    private void floor2() {
        int y0 = F2 + 1;
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                int dd = depth(i, j);
                if (dd >= 1 && dd < RING && !passage(i, j)) {
                    // 둘레 2층 가게: 둘레 복도 쪽을 보고, 바깥벽에는 창문
                    int m = RING - 1 - dd;
                    ringShop2(i, j, m, dd);
                    continue;
                }
                if (passage(i, j) && dd < RING) {
                    // 입구 통로 위 2층: 둘레 가게와 이어진 방
                    continue;
                }
                if (ringHall(i, j) && (nave(i + 1, j) || nave(i - 1, j) || nave(i, j + 1) || nave(i, j - 1))) {
                    v.set(i, y0, j, IRON_BARS); // 홀 끝 난간
                    continue;
                }
                if (dd < INNER || nave(i, j) || side(i, j) || ringHall(i, j)) {
                    continue;
                }
                // 가장 가까운 2층 통로(작은 통로·둘레 복도) 또는 홀
                int best = Integer.MAX_VALUE, dir = -1;
                boolean toNave = false;
                for (int k = 0; k < 4; k++) {
                    for (int s = 1; s <= 9; s++) {
                        int ni = i + DIRS[k][0] * s, nj = j + DIRS[k][1] * s;
                        if (!inBuilding(ni, nj)) {
                            break;
                        }
                        boolean naveHit = nave(ni, nj);
                        if (naveHit || side(ni, nj) || ringHall(ni, nj)) {
                            if (s - 1 < best) {
                                best = s - 1;
                                dir = k;
                                toNave = naveHit;
                            }
                            break;
                        }
                    }
                }
                if (dir < 0) {
                    continue;
                }
                int m = best;
                if (toNave) {
                    if (m == 0) {
                        v.set(i, y0, j, IRON_BARS); // 홀 쪽 난간
                        v.set(i, F2 - 1, j, (i + j) % 3 == 0 ? LANTERN_HANGING : null);
                        continue;
                    }
                    if (m <= 2) {
                        continue; // 난간 복도
                    }
                    m -= 3;
                }
                if (m <= 3) {
                    stall2(i, j, m, dir);
                }
            }
        }
    }

    /** 2층 가게: 원단·한복·이불 (천 가게) */
    private void stall2(int i, int j, int m, int dir) {
        int y0 = F2 + 1;
        int along = DIRS[dir][0] == 0 ? i : j;
        int slot = Math.floorMod(along, 4);
        Random sr = new Random(stallSeed(i, j, dir, 2));
        int kind = sr.nextInt(3); // 0 원단, 1 한복, 2 이불
        if (slot == 0) {
            v.fill(i, y0, j, i, F3 - 1, j, m == 0 ? LIGHT_GRAY_CONCRETE : TILE);
            return;
        }
        switch (m) {
            case 0 -> {
                v.set(i, y0, j, wool(kind == 1 ? HANBOK[sr.nextInt(HANBOK.length)] : FABRIC[sr.nextInt(FABRIC.length)]));
                if (slot == 1) {
                    int di = DIRS[dir][0] == 0 ? 1 : 0, dj = 1 - di;
                    SignText.line(v, i, F3 - 1, j, 3, di, dj, SignText.combo(sr), sr);
                }
            }
            case 1 -> {
                if (slot == 2) {
                    v.set(i, F3 - 1, j, SEA_LANTERN);
                }
            }
            case 2 -> {
                // 원단 두루마리·한복·이불을 쌓아 둠
                int h = kind == 2 ? 2 : 3;
                for (int y = 0; y < h; y++) {
                    String c = kind == 1 ? HANBOK[sr.nextInt(HANBOK.length)] : kind == 2
                            ? new String[]{"white", "pink", "light_blue", "yellow"}[sr.nextInt(4)] : FABRIC[sr.nextInt(FABRIC.length)];
                    v.set(i, y0 + y, j, wool(c));
                }
            }
            default -> {
                for (int y = y0; y < F3; y++) {
                    v.set(i, y, j, wool(FABRIC[sr.nextInt(FABRIC.length)])); // 벽 가득 원단
                }
            }
        }
    }

    /** 2층 둘레 가게 (m = 둘레 복도에서 몇 칸 안쪽) */
    private void ringShop2(int i, int j, int m, int dd) {
        int y0 = F2 + 1;
        Random sr = new Random(seed ^ (i * 92821L) ^ (j * 689287L));
        if (m == 0) {
            // 가게 앞: 유리와 문
            boolean door = Math.floorMod(i + j, 7) == 3;
            v.fill(i, y0, j, i, F3 - 1, j, door ? null : GLASS_PANE);
            if (!door) {
                v.set(i, y0, j, TILE);
            }
        } else if (m == 3 && (i + j) % 5 == 0) {
            v.set(i, y0, j, wool(FABRIC[sr.nextInt(FABRIC.length)]));
            v.set(i, y0 + 1, j, wool(FABRIC[sr.nextInt(FABRIC.length)]));
        } else if (m == 2 && (i * 3 + j) % 7 == 0) {
            v.set(i, F3 - 1, j, SEA_LANTERN);
        }
    }

    // ------------------------------------------------------------------ 계단

    /** 둘레 복도 남쪽·북쪽에 계단 네 개 (1층 → 2층) */
    private void stairs() {
        for (boolean southSide : new boolean[]{true, false}) {
            for (boolean westEnd : new boolean[]{true, false}) {
                int row = southSide ? b1 - RING : b0 + RING; // 둘레 복도 바깥쪽 줄
                int row2 = southSide ? row - 1 : row + 1;
                int start = westEnd ? a0 + INNER + 1 : a1 - INNER - 1;
                int step = westEnd ? 1 : -1;
                String facing = westEnd ? "east" : "west";
                for (int k = 0; k <= F2; k++) {
                    int i = start + k * step;
                    for (int j : new int[]{row, row2}) {
                        v.fill(i, 0, j, i, k - 1, j, SMOOTH_STONE);
                        v.set(i, k, j, Blocks.stairs("oak", facing, 0xA2834F));
                        for (int y = k + 1; y <= Math.min(F2, k + 3); y++) {
                            v.set(i, y, j, AIR);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 홀 (뚫린 큰 통로)

    private void nave() {
        int w = v.w, d = v.d;
        double[][] h = new double[w][d];
        boolean[][] ribAlongI = new boolean[w][d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                if (!inBuilding(i, j) || depth(i, j) < INNER - 1) {
                    continue;
                }
                double ew = Math.abs(j - cj) <= HALF_MAIN + 1 && i - a0 >= INNER - 1 && a1 - i >= INNER - 1
                        ? WALL_TOP + 1 + (HALF_MAIN + 1 - Math.abs(j - cj)) * 0.8 : 0;
                double ns = Math.abs(i - ci) <= HALF_MAIN + 1 && j - b0 >= INNER - 1 && b1 - j >= INNER - 1
                        ? WALL_TOP + 1 + (HALF_MAIN + 1 - Math.abs(i - ci)) * 0.8 : 0;
                h[i][j] = Math.max(ew, ns);
                ribAlongI[i][j] = ew >= ns;
            }
        }
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                if (h[i][j] <= 0) {
                    continue;
                }
                int top = (int) Math.floor(h[i][j]);
                boolean edgeNS = Math.abs(i - ci) == HALF_MAIN + 1 && !mainEW(j);
                boolean edgeEW = Math.abs(j - cj) == HALF_MAIN + 1 && !mainNS(i);
                boolean end = depth(i, j) == INNER - 1;
                // 홀 벽: 3층 바닥부터 벽 꼭대기까지, 지붕 위로 나온 부분은 창
                if ((edgeNS || edgeEW) && !nave(i, j)) {
                    int along = edgeNS ? j : i;
                    for (int y = F3; y <= WALL_TOP; y++) {
                        boolean window = y > ROOF + 1 && y < WALL_TOP && along % 3 != 0;
                        v.set(i, y, j, window ? GLASS : y == WALL_TOP || y == ROOF + 1 ? BAND : TILE);
                    }
                }
                if (end && (mainNS(i) || mainEW(j))) {
                    // 홀 끝벽 (1·2층은 둘레 복도로 열려 있음)
                    for (int y = F3; y <= WALL_TOP; y++) {
                        v.set(i, y, j, y > ROOF + 1 && y < WALL_TOP && (i + j) % 3 != 0 ? GLASS : TILE);
                    }
                }
                // 지붕: 철골 갈빗대와 반투명 유리
                int low = top;
                for (int[] dd : DIRS) {
                    double nh = h[i + dd[0]][j + dd[1]];
                    low = Math.min(low, nh <= 0 ? WALL_TOP + 1 : (int) Math.floor(nh));
                }
                low = Math.max(WALL_TOP + 1, Math.min(low + 1, top));
                if (end && (mainNS(i) || mainEW(j))) {
                    low = WALL_TOP + 1; // 박공 끝 삼각형
                }
                int along = ribAlongI[i][j] ? i : j;
                boolean ridge = ribAlongI[i][j] ? j == cj : i == ci;
                Block b = ridge || along % 4 == 0 || edgeNS || edgeEW ? LIGHT_GRAY_CONCRETE : WHITE_GLASS;
                for (int y = low; y <= top; y++) {
                    v.set(i, y, j, b);
                }
                // 용마루에 매달린 등 (8칸마다)
                if (ridge && along % 8 == 4 && nave(i, j)) {
                    for (int y = top - 1; y >= top - 5; y--) {
                        v.set(i, y, j, CHAIN);
                    }
                    v.set(i, top - 6, j, LANTERN_HANGING);
                }
            }
        }
        // 가운데 교차점: 청사초롱 샹들리에
        int top = (int) Math.floor(h[ci][cj]);
        for (int y = top - 1; y >= top - 8; y--) {
            v.set(ci, y, cj, CHAIN);
        }
        int ring = top - 9;
        for (int k = 0; k < 8; k++) {
            double ang = Math.toRadians(k * 45);
            int li = (int) Math.floor(ci + 0.5 + 2.6 * Math.cos(ang)), lj = (int) Math.floor(cj + 0.5 + 2.6 * Math.sin(ang));
            v.set(li, ring, lj, RED_WOOL);
            v.set(li, ring - 1, lj, SHROOMLIGHT);
            v.set(li, ring - 2, lj, BLUE_WOOL);
        }
        v.set(ci, ring, cj, GOLD_BLOCK);
        v.set(ci, ring - 1, cj, SEA_LANTERN);

        // 먹자골목(동서 홀) 위에 매달린 한글 간판, 남북 홀에는 현수막
        hangingSigns();
        banners();
    }

    /** 동서 홀 가운데 위에 매달린 양면 한글 간판 (12칸 글씨) */
    private void hangingSigns() {
        String[] words = {"육회", "김밥", "순대", "빈대떡"};
        int west = a0 + INNER, east = a1 - INNER;
        int[][] halves = {{west, ci - HALF_MAIN - 2}, {ci + HALF_MAIN + 2, east}};
        int n = 0;
        for (int[] half : halves) {
            int len = half[1] - half[0];
            String word = null;
            for (String wd : words) {
                if (HangulFont.width(12, wd) + 2 <= len + 2 && (word == null || wd.length() > word.length())) {
                    word = wd;
                }
            }
            if (word == null) {
                continue;
            }
            if (word.length() == 2) {
                word = words[n % 3];
            }
            n++;
            int bw = HangulFont.width(12, word) + 2;
            int i0 = (half[0] + half[1]) / 2 - bw / 2;
            int yTop = WALL_TOP - 2, yBot = yTop - 13;
            for (int i = i0; i < i0 + bw; i++) {
                for (int y = yBot; y <= yTop; y++) {
                    boolean edge = i == i0 || i == i0 + bw - 1 || y == yBot || y == yTop;
                    v.set(i, y, cj, edge ? RED_CONCRETE : YELLOW_CONCRETE);
                }
            }
            // 남쪽 면은 서→동, 북쪽 면은 동→서로 읽힘
            HangulFont.draw(v, 12, word, i0 + 1, yTop - 1, cj + 1, 1, 0, RED_CONCRETE);
            HangulFont.draw(v, 12, word, i0 + bw - 2, yTop - 1, cj - 1, -1, 0, RED_CONCRETE);
            for (int i : new int[]{i0 + 2, i0 + bw - 3}) {
                for (int y = yTop + 1; y <= RIDGE; y++) {
                    if (v.get(i, y, cj) == null) {
                        v.set(i, y, cj, CHAIN);
                    }
                }
            }
        }
    }

    /** 남북 홀을 가로지르는 현수막 (흰 바탕 빨간 글씨 무늬) */
    private void banners() {
        Random r = new Random(seed ^ 0xBA22E2L);
        for (int j = b0 + INNER + 3; j < b1 - INNER - 2; j += 10) {
            if (Math.abs(j - cj) <= HALF_MAIN + 2) {
                continue;
            }
            SignText.line(v, ci - HALF_MAIN, 15, j, HALF_MAIN * 2 + 1, 1, 0,
                    r.nextBoolean() ? new Block[]{WHITE_WOOL, RED_WOOL} : new Block[]{YELLOW_WOOL, BLUE_WOOL}, r);
        }
    }

    // ------------------------------------------------------------------ 바깥 모습

    private void facade() {
        Random r = new Random(seed ^ 0xFACADEL);
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                if (depth(i, j) != 0) {
                    continue;
                }
                boolean ns = j == b0 || j == b1;
                int along = ns ? i : j;
                int unit = Math.floorMod(along, 7);
                boolean entrance = (ns && mainNS(i)) || (!ns && mainEW(j));
                // 1층: 유리 가게 앞 (7칸마다 기둥), 문, 간판 띠
                for (int y = 0; y < F2; y++) {
                    Block b;
                    if (entrance) {
                        b = y <= 4 ? AIR : BAND;
                    } else if (unit == 0) {
                        b = LIGHT_GRAY_CONCRETE;
                    } else if (y <= 2) {
                        b = unit == 3 && y <= 1 ? AIR : GLASS;
                    } else {
                        b = y == F2 - 1 ? BAND : TILE;
                    }
                    v.set(i, y, j, b);
                }
                // 2·3층: 띠, 창문 줄
                for (int y = F2; y <= ROOF; y++) {
                    Block b;
                    boolean floorLine = y == F2 || y == F3 || y == ROOF;
                    boolean window = !floorLine && unit != 0 && ((y >= F2 + 2 && y <= F2 + 4) || (y >= F3 + 2 && y <= F3 + 4));
                    b = floorLine ? BAND : window ? GLASS_PANE : TILE;
                    v.set(i, y, j, b);
                }
                v.set(i, ROOF + 1, j, BAND); // 난간
            }
        }
        // 1층 가게 간판 (7칸마다, 바깥으로 1칸)
        for (int side = 0; side < 4; side++) {
            int len = side < 2 ? a1 - a0 : b1 - b0;
            for (int t = 1; t + 6 <= len; t += 7) {
                int[] p = facadePoint(side, t);
                boolean blocked = false;
                for (int k = -1; k <= 6; k++) {
                    int[] q = facadePoint(side, t + k);
                    blocked |= side < 2 ? mainNS(q[0]) : mainEW(q[1]);
                }
                if (blocked) {
                    continue;
                }
                SignText.line(v, p[0] + p[4], 4, p[1] + p[5], 6, p[2], p[3], SignText.combo(r), r);
                // 2층 창문 글씨 (일부)
                if (r.nextInt(3) == 0) {
                    SignText.line(v, p[0], F2 + 4, p[1], 6, p[2], p[3], new Block[]{GLASS, r.nextBoolean() ? RED_CONCRETE : BLUE_CONCRETE}, r);
                }
            }
        }
        // 모서리 세로 간판
        for (int side = 0; side < 4; side++) {
            int len = side < 2 ? a1 - a0 : b1 - b0;
            for (int t : new int[]{3, len - 5}) {
                int[] p = facadePoint(side, t);
                SignText.vertical(v, p[0] + p[4], ROOF - 2, p[1] + p[5], 11, p[2], p[3], SignText.combo(r), r);
            }
        }
        // 큰 문 위 한글 간판: 남문·북문, 동·서는 「먹자골목」 (건물 이름은 옥상 간판)
        Block blue = BLUE_CONCRETE, white = WHITE_CONCRETE;
        HangulFont.board(v, 12, "남문", ci, ROOF, b1, 1, 0, 0, 1, blue, white, white);
        HangulFont.board(v, 12, "북문", ci, ROOF, b0, -1, 0, 0, -1, blue, white, white);
        HangulFont.board(v, 12, "먹자골목", a1, ROOF, cj, 0, -1, 1, 0, RED_CONCRETE, YELLOW_CONCRETE, YELLOW_CONCRETE);
        HangulFont.board(v, 12, "먹자골목", a0, ROOF, cj, 0, 1, -1, 0, RED_CONCRETE, YELLOW_CONCRETE, YELLOW_CONCRETE);
        rooftopSign();
    }

    /**
     * 바깥벽 위의 점. side 0 = 남쪽(정면), 1 = 북쪽, 2 = 동쪽, 3 = 서쪽.
     * @return {i, j, 글씨 방향 di, dj, 바깥쪽 oi, oj}
     */
    private int[] facadePoint(int side, int t) {
        return switch (side) {
            case 0 -> new int[]{a0 + t, b1, 1, 0, 0, 1};
            case 1 -> new int[]{a1 - t, b0, -1, 0, 0, -1};
            case 2 -> new int[]{a1, b1 - t, 0, -1, 1, 0};
            default -> new int[]{a0, b0 + t, 0, 1, -1, 0};
        };
    }

    /** 옥상 정면 쪽에 철골로 세운 큰 간판 「대형시장」 (16칸 글씨, 흰 바탕 빨간 글씨, 아래 조명) */
    private void rooftopSign() {
        String text = "대형시장";
        int bw = HangulFont.width(16, text) + 6, bh = 16 + 4;
        int i0 = ci - bw / 2, j = b1 - 3;
        int y0 = ROOF + 3, y1 = y0 + bh - 1;
        if (i0 < a0 + 2) {
            return;
        }
        for (int i = i0; i < i0 + bw; i++) {
            for (int y = y0; y <= y1; y++) {
                boolean edge = i == i0 || i == i0 + bw - 1 || y == y0 || y == y1;
                v.set(i, y, j, edge ? (y == y0 && i % 3 == 0 ? SEA_LANTERN : RED_CONCRETE) : WHITE_CONCRETE);
                v.set(i, y, j - 1, LIGHT_GRAY_CONCRETE); // 뒷판
            }
            if ((i - i0) % 8 == 0 || i == i0 + bw - 1) {
                v.fill(i, ROOF + 1, j - 1, i, y0 - 1, j - 1, IRON_BARS); // 철골 다리
                v.fill(i, ROOF + 1, j - 3, i, y1 - 4, j - 3, IRON_BARS);
            }
        }
        HangulFont.draw(v, 16, text, i0 + 3, y1 - 2, j + 1, 1, 0, RED_CONCRETE);
    }
}
