package com.junseo.city.map;

/**
 * 지도 아이콘 (7×7 픽셀 그림). 리소스팩 그림과 글자 폭 계산에 같이 씁니다.
 * 장소 아이콘은 둥근 배지 위에 흰 기호 하나입니다 (GTA 지도 아이콘 느낌).
 */
public final class MapIcons {
    public static final int SIZE = 7;

    // 고정 아이콘 번호
    public static final int PLAYER_UP = 0;
    public static final int WAYPOINT = 1;
    public static final int NORTH = 2;
    public static final int AIRPORT = 3;
    public static final int STAR = 4;
    public static final int BANK = 5;
    public static final int TV = 6;
    public static final int HOSPITAL = 7;
    public static final int POLICE = 8;
    public static final int CITYHALL = 9;
    public static final int TRAIN = 10;
    public static final int GEM = 11;
    public static final int ANCHOR = 12;
    public static final int BOX = 13;
    public static final int PICK = 14;
    public static final int NOTE = 15;
    public static final int BALL = 16;
    public static final int TOWER = 17;
    public static final int TREE = 18;
    public static final int WHEEL = 19;
    public static final int SKYSCRAPER = 20;
    public static final int BAG = 21;
    public static final int PRISON = 22;
    private static final int FIXED = 23;
    /** 16방향 화살표 (0 = 화면 위, 시계 방향으로 22.5°씩) */
    private static final int ARROW_BASE = FIXED;
    public static final int COUNT = FIXED + 16;

    private static final int OUTLINE = 0xFF141414;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int[][] PIXELS = new int[COUNT][];

    public static int arrow(int direction16) {
        return ARROW_BASE + Math.floorMod(direction16, 16);
    }

    /** 북쪽이 위인 지도에서 마인크래프트 yaw(0 = 남쪽, 90 = 서쪽) 를 바라보는 화살표 */
    public static int arrowForYaw(float yaw) {
        return arrow(Math.round((yaw + 180f) / 22.5f));
    }

    /** 7×7 ARGB (가로줄 순서) */
    public static int[] pixels(int icon) {
        return PIXELS[icon].clone();
    }

    /** 글자 폭 = 가장 오른쪽 불투명 열 + 1, 그리고 +1 */
    public static int advance(int icon) {
        int[] p = PIXELS[icon];
        for (int x = SIZE - 1; x >= 0; x--) {
            for (int y = 0; y < SIZE; y++) {
                if ((p[y * SIZE + x] >>> 24) != 0) {
                    return x + 2;
                }
            }
        }
        return 1;
    }

    /** 거점 id → 아이콘 번호. 지도에 안 보여 주는 곳(암시장·은신처)은 -1 */
    public static int forHub(String id) {
        return switch (id) {
            case "spawn" -> AIRPORT;
            case "plaza" -> STAR;
            case "bank_hq" -> BANK;
            case "broadcast" -> TV;
            case "hospital" -> HOSPITAL;
            case "police_north", "police_south" -> POLICE;
            case "cityhall" -> CITYHALL;
            case "station" -> TRAIN;
            case "jewelry" -> GEM;
            case "port" -> ANCHOR;
            case "depot" -> BOX;
            case "quarry" -> PICK;
            case "club" -> NOTE;
            case "football", "ballpark" -> BALL;
            case "tower" -> TOWER;
            case "park" -> TREE;
            case "themepark" -> WHEEL;
            case "skyscraper" -> SKYSCRAPER;
            case "mall" -> BAG;
            case "prison" -> PRISON;
            default -> -1; // blackmarket, hideout 등 범죄 장소는 지도에 없음
        };
    }

    static {
        PIXELS[PLAYER_UP] = parse(new String[]{
                "...o...",
                "..owo..",
                "..owo..",
                ".owwwo.",
                ".owwwo.",
                "owwowwo",
                "ooo.ooo"}, 0, WHITE);
        PIXELS[WAYPOINT] = parse(new String[]{
                "...o...",
                "..obo..",
                ".obwbo.",
                "obwwwbo",
                ".obwbo.",
                "..obo..",
                "...o..."}, 0xFFB44CFF, WHITE);
        PIXELS[NORTH] = parse(new String[]{
                ".ooooo.",
                "owbbbwo",
                "owwbbwo",
                "owbwbwo",
                "owbbwwo",
                "owbbbwo",
                ".ooooo."}, 0xFF2A2A2A, WHITE);
        badge(AIRPORT, 0xFF3FA9F5, "..w..", ".www.", "wwwww", "..w..", ".www.");
        badge(STAR, 0xFFE6B400, "..w..", ".www.", "wwwww", ".www.", ".w.w.");
        badge(BANK, 0xFF2E9E4F, ".www.", "ww...", ".www.", "...ww", ".www.");
        badge(TV, 0xFF8E44AD, "w...w", ".w.w.", "wwwww", "w...w", "wwwww");
        badge(HOSPITAL, 0xFFD63031, "..w..", "..w..", "wwwww", "..w..", "..w..");
        badge(POLICE, 0xFF2D6CDF, "wwwww", "w.w.w", "wwwww", ".www.", "..w..");
        badge(CITYHALL, 0xFF607D8B, "..w..", ".www.", "wwwww", "w.w.w", "wwwww");
        badge(TRAIN, 0xFF00897B, "wwwww", "w.w.w", "wwwww", "wwwww", ".w.w.");
        badge(GEM, 0xFF00ACC1, ".www.", "wwwww", ".www.", "..w..", ".....");
        badge(ANCHOR, 0xFF34495E, "..w..", ".www.", "..w..", "w.w.w", ".www.");
        badge(BOX, 0xFFE67E22, "wwwww", "w.w.w", "wwwww", "w...w", "wwwww");
        badge(PICK, 0xFF8D6E63, "wwww.", "...w.", "..w.w", ".w...", "w....");
        badge(NOTE, 0xFFE84393, "..ww.", "..w.w", "..w..", "www..", "ww...");
        badge(BALL, 0xFF27AE60, ".www.", "ww.ww", "w.w.w", "ww.ww", ".www.");
        badge(TOWER, 0xFFE74C3C, "..w..", "..w..", ".www.", "..w..", ".w.w.");
        badge(TREE, 0xFF2E7D32, "..w..", ".www.", "wwwww", "..w..", "..w..");
        badge(WHEEL, 0xFFC2185B, ".www.", "w.w.w", "wwwww", "w.w.w", ".www.");
        badge(SKYSCRAPER, 0xFF455A64, "..w..", ".www.", ".w.w.", ".www.", ".www.");
        badge(BAG, 0xFFF39C12, ".www.", ".w.w.", "wwwww", "wwwww", "wwwww");
        badge(PRISON, 0xFF424242, "wwwww", "w.w.w", "w.w.w", "w.w.w", "wwwww");
        for (int d = 0; d < 16; d++) {
            PIXELS[ARROW_BASE + d] = arrowPixels(d * 22.5);
        }
    }

    /** 둥근 배지 + 가운데 5×5 흰 기호 */
    private static void badge(int icon, int color, String... symbol) {
        String[] rows = {
                ".ooooo.",
                "obbbbbo",
                "obbbbbo",
                "obbbbbo",
                "obbbbbo",
                "obbbbbo",
                ".ooooo."};
        char[][] grid = new char[SIZE][];
        for (int y = 0; y < SIZE; y++) {
            grid[y] = rows[y].toCharArray();
        }
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                if (symbol[y].charAt(x) == 'w') {
                    grid[y + 1][x + 1] = 'w';
                }
            }
        }
        String[] merged = new String[SIZE];
        for (int y = 0; y < SIZE; y++) {
            merged[y] = new String(grid[y]);
        }
        PIXELS[icon] = parse(merged, color, WHITE);
    }

    private static int[] parse(String[] rows, int badge, int white) {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                out[y * SIZE + x] = switch (rows[y].charAt(x)) {
                    case 'o' -> OUTLINE;
                    case 'b' -> badge;
                    case 'w' -> white;
                    default -> 0;
                };
            }
        }
        return out;
    }

    /** 위를 향한 화살표를 degrees(시계 방향) 만큼 돌려 7×7 로 찍고 검은 테두리를 두릅니다 */
    private static int[] arrowPixels(double degrees) {
        double[][] shape = {{0, -3.3}, {2.7, 2.9}, {0, 1.2}, {-2.7, 2.9}};
        double a = Math.toRadians(degrees), cos = Math.cos(a), sin = Math.sin(a);
        boolean[] fill = new boolean[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        double px = x + (sx + 0.5) / 4 - SIZE / 2.0, py = y + (sy + 0.5) / 4 - SIZE / 2.0;
                        // 화면 좌표를 화살표 기준으로 되돌림 (화면 y 는 아래가 +)
                        double ux = px * cos + py * sin, uy = -px * sin + py * cos;
                        if (inside(shape, ux, uy)) {
                            hits++;
                        }
                    }
                }
                fill[y * SIZE + x] = hits >= 7;
            }
        }
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (fill[y * SIZE + x]) {
                    out[y * SIZE + x] = WHITE;
                } else if (near(fill, x, y)) {
                    out[y * SIZE + x] = OUTLINE;
                }
            }
        }
        return out;
    }

    private static boolean near(boolean[] fill, int x, int y) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : d) {
            int nx = x + o[0], ny = y + o[1];
            if (nx >= 0 && ny >= 0 && nx < SIZE && ny < SIZE && fill[ny * SIZE + nx]) {
                return true;
            }
        }
        return false;
    }

    private static boolean inside(double[][] poly, double x, double y) {
        boolean in = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            double xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    private MapIcons() {
    }
}
