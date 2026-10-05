package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 대형시장 본관: 광장시장처럼 큰 건물 하나 안에 시장이 들어 있습니다.
 * <ul>
 *   <li>바깥: 베이지 타일 3층 건물. 1층 둘레는 길 쪽 가게(유리, 간판 띠와 표지판 글씨), 2·3층은 창문 줄,
 *       큰 문 네 곳에 차양과 남색 간판(「전통시장」「대형시장 ○문」), 모서리 돌출 간판</li>
 *   <li>안: 십자로 난 큰 통로는 3층 높이로 뚫린 홀이고 철골 유리 지붕을 얹었습니다. 긴 통로(동서)는 먹자골목:
 *       가운데에 빈대떡·김밥·육회 좌판이 줄지어 있고 양옆에 긴 나무 의자, 좌판마다 등과 매단 이름 표지판.
 *       통로 양옆은 반찬·떡집·건어물·청과·수산·주방·방앗간 가게(가게마다 간판 띠와 매단 이름 표지판),
 *       남서쪽은 수산시장</li>
 *   <li>2층: 홀을 둘러싼 난간 복도와 원단·한복·이불 가게. 둘레 복도 네 귀퉁이에 계단</li>
 *   <li>3층: 상인회 사무실과 창고 (둘레 복도 북쪽 계단으로 올라감). 층마다 북서쪽에 화장실</li>
 * </ul>
 * 건물 기준 정면은 남쪽(j 큰 쪽). 상자 둘레 2칸은 간판·차양이 튀어나오는 자리입니다.
 */
final class MarketHall {
    private static final int MARGIN = 2;
    /** 바깥 가게 줄 깊이 (벽 포함) */
    private static final int RING = 7;
    /** 둘레 복도 폭 */
    private static final int HALL = 4;
    private static final int INNER = RING + HALL;
    /** 층 바닥 높이 (1층 층고 5, 2·3층 4 — Floors 기준) */
    private static final int F2 = 4, F3 = 8, ROOF = 12;
    /** 홀 벽 꼭대기 */
    private static final int WALL_TOP = 19;
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
        m.floor3();
        m.stairs();
        m.restrooms();
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
        Object[] board = ShopHouse.BOARDS[sr.nextInt(ShopHouse.BOARDS.length)];
        if (slot == 0) {
            v.fill(i, 0, j, i, F2 - 1, j, m == 0 ? LIGHT_GRAY_CONCRETE : TILE); // 가게 사이 벽
            return;
        }
        switch (m) {
            case 0 -> {
                v.set(i, 0, j, goods(kind, sr, 0));
                v.set(i, F2 - 1, j, LIGHT_GRAY_CONCRETE); // 셔터 통
                v.set(i, F2 - 2, j, (Block) board[0]); // 간판 띠
                if (slot == 2) {
                    v.set(i, F2 - 3, j, Blocks.hangingSign((String) board[1], rotation(dir), (String) board[2], true, stallName(kind, sr)));
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

    /** 매다는 표지판이 가게 앞(dir 쪽)을 보게 하는 rotation */
    private static int rotation(int dir) {
        return switch (dir) {
            case 0 -> 0;
            case 1 -> 8;
            case 2 -> 12;
            default -> 4;
        };
    }

    private static final String[] NICK = {"순희네", "영자네", "부산", "전주", "할매", "원조", "충청", "제주", "광장", "대박",
            "엄마손", "이모네", "삼촌네", "옛날", "종로", "서울", "강원", "목포"};

    /** 1층 가게 이름 */
    private static String stallName(int kind, Random r) {
        String nick = NICK[r.nextInt(NICK.length)];
        return nick + " " + switch (kind) {
            case 0 -> "반찬";
            case 1 -> "떡집";
            case 2 -> "건어물";
            case 3 -> "청과";
            case 4 -> "수산";
            case 5 -> "주방";
            case 6 -> "방앗간";
            default -> "분식";
        };
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
            // 좌판 이름 (가로대 가운데에 매단 표지판)
            String title = switch (menu) {
                case 'c' -> "빈대떡";
                case 'k' -> "마약김밥";
                case 'r' -> "육회";
                case 't' -> "떡볶이";
                case 's' -> "순대";
                default -> "칼국수";
            };
            int mid = s + 3;
            v.set(ew ? mid : c, 3, ew ? c : mid, Blocks.hangingSign("spruce", ew ? 4 : 0, "white", true,
                    NICK[sr.nextInt(NICK.length)], title));
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
                if (slot == 2) {
                    String name = NICK[sr.nextInt(NICK.length)] + " " + (kind == 0 ? "원단" : kind == 1 ? "한복" : "이불");
                    v.set(i, F3 - 2, j, Blocks.hangingSign("birch", rotation(dir), "black", false, name));
                    v.set(i, F3 - 1, j, WHITE_CONCRETE);
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

    /** 둘레 복도 남쪽·북쪽에 계단 네 개 (1층 → 2층), 북쪽 서편에 하나 더 (2층 → 3층) */
    private void stairs() {
        for (boolean southSide : new boolean[]{true, false}) {
            for (boolean westEnd : new boolean[]{true, false}) {
                run(southSide, westEnd, 0, F2, westEnd ? a0 + INNER + 1 : a1 - INNER - 1);
            }
        }
        run(false, true, F2 + 1, F3, a0 + INNER + 9);
    }

    /** 곧은 계단 한 줄 (2칸 너비): 서는 높이 from 에서 바닥 top 까지, 위층 바닥은 머리 위만큼 뚫음 */
    private void run(boolean southSide, boolean westEnd, int from, int top, int start) {
        int row = southSide ? b1 - RING : b0 + RING; // 둘레 복도 바깥쪽 줄
        int row2 = southSide ? row - 1 : row + 1;
        int step = westEnd ? 1 : -1;
        String facing = westEnd ? "east" : "west";
        for (int k = 0; k <= top - from; k++) {
            int i = start + k * step, y = from + k;
            for (int j : new int[]{row, row2}) {
                if (y > from) {
                    v.fill(i, from, j, i, y - 1, j, SMOOTH_STONE);
                }
                v.set(i, y, j, Blocks.stairs("oak", facing, 0xA2834F));
                for (int yy = y + 1; yy <= Math.min(top, y + 3); yy++) {
                    v.set(i, yy, j, AIR);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 3층·화장실

    /** 3층: 바깥 둘레 방은 사무실(북쪽)·창고, 안쪽 블록은 창고 선반, 복도 등 */
    private void floor3() {
        int y0 = F3 + 1;
        Random r = new Random(seed ^ 0x3F);
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                int dd = depth(i, j);
                if (dd < 1 || nave(i, j) || naveWall(i, j)) {
                    continue;
                }
                boolean north = j - b0 == dd;
                if (dd < RING) {
                    int along = (dd == j - b0 || dd == b1 - j) ? i : j;
                    int unit = Math.floorMod(along, 7);
                    if (dd == RING - 1) {
                        // 복도 쪽 벽, 방마다 문
                        v.fill(i, y0, j, i, ROOF - 1, j, TILE);
                        if (unit == 3) {
                            v.set(i, y0, j, AIR);
                            v.set(i, y0 + 1, j, AIR);
                        }
                    } else if (unit == 0) {
                        v.fill(i, y0, j, i, ROOF - 1, j, TILE); // 방 칸막이
                    } else if (north && dd == 2 && (unit == 2 || unit == 4)) {
                        Furniture.desk(Frame.of(v), i, y0, j, "south"); // 상인회 사무실
                    } else if (!north && dd == 1) {
                        v.set(i, y0, j, r.nextBoolean() ? BARREL : Furniture.BOOKSHELF);
                        v.set(i, y0 + 1, j, BARREL);
                    }
                    if (dd == 3 && unit == 3) {
                        v.set(i, ROOF - 1, j, Interior.LIGHT);
                    }
                    continue;
                }
                if (ringHall(i, j) || side(i, j)) {
                    if ((i + j) % 6 == 0) {
                        v.set(i, ROOF - 1, j, Interior.LIGHT);
                    }
                    continue;
                }
                // 안쪽 창고: 두 줄 선반, 사이는 통로
                if (Math.floorMod(i, 3) == 0 && Math.floorMod(j, 6) != 0) {
                    v.set(i, y0, j, BARREL);
                    v.set(i, y0 + 1, j, r.nextInt(3) == 0 ? Block.of("hay_block[axis=y]", 0xA68B0C) : BARREL);
                } else if (Math.floorMod(i + 1, 6) == 0 && Math.floorMod(j, 6) == 3) {
                    v.set(i, ROOF - 1, j, Interior.LIGHT);
                }
            }
        }
        v.set(a0 + INNER + 3, y0 + 1, b0 + RING, Blocks.wallSign("birch", "south", "black", false, "", "상인회 사무실"));
    }

    /** 홀 둘레 벽 칸 (3층부터 위) */
    private boolean naveWall(int i, int j) {
        boolean edgeNS = Math.abs(i - ci) == HALF_MAIN + 1 && !mainEW(j) && depth(i, j) >= INNER - 1;
        boolean edgeEW = Math.abs(j - cj) == HALF_MAIN + 1 && !mainNS(i) && depth(i, j) >= INNER - 1;
        boolean end = depth(i, j) == INNER - 1 && (mainNS(i) || mainEW(j));
        return edgeNS || edgeEW || end;
    }

    /** 층마다 북쪽 둘레 방 하나를 공중화장실로 (같은 자리에 쌓음) */
    private void restrooms() {
        int i0 = a0 + INNER + 3, i1 = i0 + 6, j0 = b0 + 1, j1 = b0 + RING - 2;
        int[] levels = {0, F2 + 1, F3 + 1};
        int[] tops = {F2 - 1, F3 - 1, ROOF - 1};
        Block wall = Block.of("white_terracotta", 0xD1B2A1);
        for (int n = 0; n < 3; n++) {
            int y0 = levels[n], top = tops[n];
            v.fill(i0, y0, j0, i1, top, j1, AIR);
            v.fill(i0 - 1, y0, j0, i0 - 1, top, j1 + 1, wall);
            v.fill(i1 + 1, y0, j0, i1 + 1, top, j1 + 1, wall);
            v.fill(i0, y0, j1 + 1, i1, top, j1 + 1, wall);
            v.fill(i0, y0 - 1, j0, i1, y0 - 1, j1, Block.of("light_gray_terracotta", 0x876A61));
            // 문 (복도 쪽) 과 이름표
            v.set(i0 + 3, y0, j1 + 1, Blocks.door("pale_oak", "north", false));
            v.set(i0 + 3, y0 + 1, j1 + 1, Blocks.door("pale_oak", "north", true));
            v.set(i0 + 4, y0 + 1, j1 + 2, Blocks.wallSign("birch", "south", "black", false, "", "화장실"));
            // 변기 칸 둘 (바깥벽 쪽), 세면대
            for (int k = 0; k < 2; k++) {
                int a = i0 + k * 3;
                v.set(a, y0, j0, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
                v.fill(a + 2, y0, j0, a + 2, y0 + 1, j0 + 1, WHITE_CONCRETE);
                v.set(a, y0, j0 + 2, WHITE_CONCRETE);
                v.set(a, y0 + 1, j0 + 2, WHITE_CONCRETE);
                v.set(a + 1, y0, j0 + 2, Blocks.door("birch", "north", false));
                v.set(a + 1, y0 + 1, j0 + 2, Blocks.door("birch", "north", true));
            }
            v.set(i1, y0, j1 - 1, CAULDRON);
            v.set(i1, y0, j1, CAULDRON);
            v.set(i0 + 3, top, j0 + 3, Interior.LIGHT);
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
        // 가운데 교차점: 사슬에 매단 등 네 개
        int top = (int) Math.floor(h[ci][cj]);
        for (int[] o : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            for (int y = top - 1; y >= top - 6; y--) {
                v.set(ci + o[0], y, cj + o[1], CHAIN);
            }
            v.set(ci + o[0], top - 7, cj + o[1], LANTERN_HANGING);
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
        // 1층 둘레 가게 간판: 7칸마다 2줄 간판 띠와 표지판 글씨
        for (int side = 0; side < 4; side++) {
            int len = side < 2 ? a1 - a0 : b1 - b0;
            for (int t = 1; t + 5 <= len; t += 7) {
                boolean blocked = false;
                for (int k = -1; k <= 6; k++) {
                    int[] q = facadePoint(side, t + k);
                    blocked |= side < 2 ? mainNS(q[0]) : mainEW(q[1]);
                }
                if (blocked) {
                    continue;
                }
                Object[] board = ShopHouse.BOARDS[r.nextInt(ShopHouse.BOARDS.length)];
                for (int k = 0; k < 6; k++) {
                    int[] q = facadePoint(side, t + k);
                    v.set(q[0], 3, q[1], (Block) board[0]);
                    v.set(q[0], 4, q[1], (Block) board[0]);
                }
                int[] c = facadePoint(side, t + 3);
                String[] name = outerShop(r);
                v.set(c[0] + c[4], 4, c[1] + c[5], Blocks.wallSign((String) board[1], FACING[side], (String) board[2], true, name[0], name[1]));
            }
        }
        // 큰 문: 차양, 남색 간판 판과 표지판 셋 (전통시장 · 대형시장 · ○문)
        String[] gates = {"남문", "북문", "동문", "서문"};
        for (int side = 0; side < 4; side++) {
            int[] p = side < 2 ? new int[]{ci, side == 0 ? b1 : b0} : new int[]{side == 2 ? a1 : a0, cj};
            int[] fp = facadePoint(side, 0);
            int di = fp[2], dj = fp[3], oi = fp[4], oj = fp[5];
            for (int k = -HALF_MAIN - 2; k <= HALF_MAIN + 2; k++) {
                int i = p[0] + di * k, j = p[1] + dj * k;
                for (int y = F2 + 1; y <= F2 + 3; y++) {
                    v.set(i, y, j, BLUE_CONCRETE);
                }
                for (int o = 1; o <= 2; o++) {
                    v.set(i + oi * o, F2, j + oj * o, TOP_SLAB); // 차양
                }
            }
            v.set(p[0] + oi, F2 + 2, p[1] + oj, Blocks.wallSign("dark_oak", FACING[side], "white", true, "", "대형시장", gates[side]));
            v.set(p[0] - di * 4 + oi, F2 + 2, p[1] - dj * 4 + oj, Blocks.wallSign("dark_oak", FACING[side], "white", true, "", "전통시장"));
            v.set(p[0] + di * 4 + oi, F2 + 2, p[1] + dj * 4 + oj, Blocks.wallSign("dark_oak", FACING[side], "yellow", true, "", "SINCE 1958"));
        }
        // 모서리 돌출 간판 (2층)
        for (int side = 0; side < 4; side++) {
            int len = side < 2 ? a1 - a0 : b1 - b0;
            int[] p = facadePoint(side, 1);
            String facing = side < 2 ? "east" : "south";
            v.set(p[0] + p[4], F2 + 3, p[1] + p[5], Blocks.wallHangingSign("dark_oak", facing, "white", true, "대형시장", gates[side]));
            int[] q = facadePoint(side, len - 1);
            v.set(q[0] + q[4], F2 + 3, q[1] + q[5], Blocks.wallHangingSign("dark_oak", facing, "white", true, "대형시장", gates[side]));
        }
    }

    private static final String[] FACING = {"south", "north", "east", "west"};
    private static final Block TOP_SLAB = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);

    /** 시장 바깥 가게 {이름, 업종} */
    private static String[] outerShop(Random r) {
        String[][] kinds = {{"한복", "맞춤 한복"}, {"이불", "혼수 이불"}, {"주단", "비단·원단"}, {"그릇", "주방용품"},
                {"건어물", "멸치·김"}, {"인삼", "홍삼"}, {"떡집", "떡·한과"}, {"약재", "한약재"}, {"양품", "의류"}, {"수선", "옷 수선"}};
        String[] k = kinds[r.nextInt(kinds.length)];
        return new String[]{NICK[r.nextInt(NICK.length)] + " " + k[0], k[1]};
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
}
