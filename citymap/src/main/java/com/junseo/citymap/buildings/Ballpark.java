package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 잠실 야구장: 지붕 없는 야외 야구장.
 * <ul>
 *   <li>경기장: 땅보다 7칸 낮게 판 그라운드. 내야 흙(베이스 길, 마운드, 홈 둘레), 줄무늬 잔디, 흰 파울선과 타석,
 *       베이스와 홈 플레이트, 외야 경고 구역 흙, 초록 펜스(노란 윗줄, 거리 표지), 노란 파울 폴, 그물 친 백스톱</li>
 *   <li>관중석: 그라운드 둘레 1층 6줄(맨 윗줄이 땅 높이) 위로 트인 콘코스, 홈 뒤와 내야는 콘코스 위로 2층.
 *       좌석은 계단 블록이고 구역마다 색(블루석·오렌지석·레드석·그린석), 통로 계단, 2층 오르는 계단(보미토리)</li>
 *   <li>더그아웃(1루·3루, 콘코스에서 내려가는 계단 통로), 파울 지역 불펜(마운드와 홈 두 쌍), 외야 뒤 전광판(「잠실」과 점수판),
 *       조명탑, 콘코스 매점과 화장실, 출입구와 매표소, 거점 쪽 정문 광장</li>
 * </ul>
 * 홈 플레이트는 거점 쪽 귀퉁이에, 중견수는 맞은편 대각선에 둡니다. 실제 크기(좌우 100m, 가운데 125m)까지,
 * 땅이 작으면 땅에 맞춰 줄입니다.
 */
final class Ballpark {
    /** 1층 관중석 줄 수 */
    static final int ROWS = 6;
    /** 그라운드 땅 블록 높이 (사람은 FIELD + 1 에 섬) */
    static final int FIELD = -ROWS - 2;
    /** 파울 지역 폭, 백스톱(홈 뒤) 반지름 */
    static final int FOUL = 5, BACKSTOP = 9;
    /** 내야 옆 관중석 깊이, 홈 뒤 관중석 깊이 (파울 지역 끝에서 바깥벽까지) */
    static final int SIDE = 15, HOME = 16;
    /** 바깥벽 밖 보도 폭 */
    static final int RING = 3;
    /** 외야 관중석 뒤 콘코스 폭 */
    static final int OUTFIELD_CONCOURSE = 8;

    private static final Block BLUE_SEAT = Block.of("warped_stairs", 0x2B6963);
    private static final Block ORANGE_SEAT = Block.of("waxed_cut_copper_stairs", 0xBF6B4F);
    private static final Block RED_SEAT = Block.of("red_nether_brick_stairs", 0x450709);
    private static final Block GREEN_SEAT = Block.of("waxed_oxidized_cut_copper_stairs", 0x52A284);
    private static final Block STEP = Block.of("polished_andesite_stairs", 0x848685);
    private static final Block CONCRETE = LIGHT_GRAY_CONCRETE;
    private static final Block DIRT = Block.of("coarse_dirt", 0x77563B);
    private static final Block TURF_A = GRASS;
    private static final Block TURF_B = Block.of("moss_block", 0x596E2D);
    private static final Block LINE = WHITE_CONCRETE;
    private static final Block FENCE = GREEN_CONCRETE;
    private static final Block POLE = YELLOW_CONCRETE;
    private static final Block NET = IRON_BARS;
    private static final Block LAMP = SEA_LANTERN;

    private final Voxels v;
    private final SiteLand land;
    private final Random r;
    private final int hubI, hubJ;
    /** 홈 플레이트 칸 */
    private int hi, hj;
    /** 1루선·3루선 방향 (건물 칸 단위 벡터) */
    private int e1i, e1j, e3i, e3j;
    /** 파울선 길이(좌우), 가운데 거리 */
    private int L, C;
    /** 2층이 있는 범위 (파울선 따라 홈에서) */
    private double upperReach;
    private boolean[][] play, stadium;
    private double[][] t, u;

    private Ballpark(SiteLand land, int hubI, int hubJ, Random r) {
        this.land = land;
        this.r = r;
        this.hubI = hubI;
        this.hubJ = hubJ;
        v = new Voxels(land.w, land.d, FIELD - 1, 48);
    }

    static Voxels build(SiteLand land, int hubI, int hubJ, Random r) {
        Ballpark b = new Ballpark(land, hubI, hubJ, r);
        b.fit();
        if (Boolean.getBoolean("mapPreview")) {
            System.out.println("잠실 야구장 맞춤: 홈 " + b.hi + "," + b.hj + " 좌우 " + b.L + " 가운데 " + b.C + " 땅 " + land.w + "×" + land.d);
        }
        b.regions();
        b.plaza();
        b.bowl();
        b.ground();
        b.dugouts();
        b.bullpens();
        b.vomitories();
        b.concourse();
        b.scoreboard();
        b.lightTowers();
        b.v.connect();
        land.clip(b.v);
        return b.v;
    }

    /** 땅 모양: 경기장 바깥벽 안쪽 칸들의 바깥 상자를 따른 다각형 (미니맵용, 대략) */
    static List<double[]> footprint(SiteLand land, int hubI, int hubJ) {
        Ballpark b = new Ballpark(land, hubI, hubJ, new Random(0));
        b.fit();
        b.regions();
        // 각도별로 경기장 끝까지
        List<double[]> pts = new ArrayList<>();
        double ci = b.hi + 0.5 + (b.e1i + b.e3i) * b.L * 0.35, cj = b.hj + 0.5 + (b.e1j + b.e3j) * b.L * 0.35;
        for (int k = 0; k < 72; k++) {
            double th = 2 * Math.PI * k / 72, dx = Math.cos(th), dz = Math.sin(th);
            double far = 0;
            for (double m = 0; m < 200; m += 0.5) {
                int i = (int) Math.floor(ci + dx * m), j = (int) Math.floor(cj + dz * m);
                if (i < 0 || j < 0 || i >= land.w || j >= land.d) {
                    break;
                }
                if (b.stadium[i][j]) {
                    far = m + 0.5;
                }
            }
            pts.add(new double[]{ci + dx * far, cj + dz * far});
        }
        return pts;
    }

    // ------------------------------------------------------------------ 좌표

    /** 1루선 방향 거리 */
    private double a(double i, double j) {
        return (i - (hi + 0.5)) * e1i + (j - (hj + 0.5)) * e1j;
    }

    /** 3루선 방향 거리 */
    private double b(double i, double j) {
        return (i - (hi + 0.5)) * e3i + (j - (hj + 0.5)) * e3j;
    }

    /** 외야 펜스까지 거리 (각도 phi: 0 = 1루선, π/2 = 3루선) */
    private double fence(double phi) {
        return L + (C - L) * Math.pow(Math.sin(2 * phi), 1.6);
    }

    /** 건물 칸 방향 벡터 → 이름 */
    private static String dirName(int di, int dj) {
        if (di > 0) {
            return "east";
        }
        if (di < 0) {
            return "west";
        }
        return dj > 0 ? "south" : "north";
    }

    // ------------------------------------------------------------------ 크기 맞추기

    private void fit() {
        // 거점이 있는 귀퉁이가 홈 뒤: 1루선은 그 반대 가로, 3루선은 그 반대 세로
        boolean left = hubI < land.w / 2, front = hubJ >= land.d / 2;
        int si = left ? 1 : -1, sj = front ? -1 : 1;
        // 홈에서 바라볼 때 1루가 오른쪽: (si, sj) 사분면에서 오른손 쪽 축
        if (si * sj < 0) {
            e1i = si;
            e1j = 0;
            e3i = 0;
            e3j = sj;
        } else {
            e1i = 0;
            e1j = sj;
            e3i = si;
            e3j = 0;
        }
        int best = -1;
        double bestD = 1e9;
        for (int ci = 0; ci < land.w; ci++) {
            for (int cj = 0; cj < land.d; cj++) {
                double dh = Math.hypot(ci - hubI, cj - hubJ);
                if (dh < BACKSTOP + HOME + 5) {
                    continue;
                }
                hi = ci;
                hj = cj;
                if (!standsFit()) {
                    continue;
                }
                int len = fieldFits();
                if (len > best || (len == best && dh < bestD)) {
                    best = len;
                    bestD = dh;
                    bestI = ci;
                    bestJ = cj;
                }
            }
        }
        hi = bestI;
        hj = bestJ;
        L = Math.max(30, best);
        C = (int) Math.round(L * 1.25);
        upperReach = L * 0.62;
    }

    private int bestI, bestJ;

    private boolean inner(double i, double j) {
        return land.inner((int) Math.floor(i), (int) Math.floor(j), RING + 1);
    }

    /** 홈 뒤·내야 관중석 바깥선이 땅 안에 드는지 */
    private boolean standsFit() {
        double hx = hi + 0.5, hz = hj + 0.5;
        double rOut = BACKSTOP + HOME, side = FOUL + SIDE;
        for (int k = 0; k <= 20; k++) {
            double th = Math.PI / 2 * k / 20;
            double ca = -Math.cos(th) * rOut, cb = -Math.sin(th) * rOut;
            if (!inner(hx + ca * e1i + cb * e3i, hz + ca * e1j + cb * e3j)) {
                return false;
            }
        }
        // 파울선 옆 관중석 바깥선 (홈에서 2층 끝까지)
        for (double s = 0; s <= 40; s += 2) {
            if (!inner(hx + s * e1i - side * e3i, hz + s * e1j - side * e3j)
                    || !inner(hx - side * e1i + s * e3i, hz - side * e1j + s * e3j)) {
                return false;
            }
        }
        return true;
    }

    /** 이 홈 자리에서 펜스 + 뒤 4칸이 땅 안에 드는 가장 긴 파울선 (실제 100m 까지) */
    private int fieldFits() {
        double hx = hi + 0.5, hz = hj + 0.5;
        for (int len = 100; len >= 30; len--) {
            int c = (int) Math.round(len * 1.25);
            boolean ok = true;
            for (int k = 0; k <= 30 && ok; k++) {
                double phi = Math.PI / 2 * k / 30;
                double rr = len + (c - len) * Math.pow(Math.sin(2 * phi), 1.6) + 4;
                double ca = Math.cos(phi) * rr, cb = Math.sin(phi) * rr;
                ok = inner(hx + ca * e1i + cb * e3i, hz + ca * e1j + cb * e3j);
            }
            // 파울선 옆 관중석은 폴까지 이어짐
            for (double s = 0; s <= len && ok; s += 3) {
                ok = inner(hx + s * e1i - (FOUL + 4) * e3i, hz + s * e1j - (FOUL + 4) * e3j)
                        && inner(hx - (FOUL + 4) * e1i + s * e3i, hz - (FOUL + 4) * e1j + s * e3j);
            }
            if (ok) {
                return len;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ 영역과 거리

    private void regions() {
        int w = land.w, d = land.d;
        play = new boolean[w][d];
        stadium = new boolean[w][d];
        double rOut = BACKSTOP + HOME, side = FOUL + SIDE;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                double a = a(i + 0.5, j + 0.5), b = b(i + 0.5, j + 0.5), rr = Math.hypot(a, b);
                boolean fair = a >= 0 && b >= 0;
                boolean in = (fair && rr < fence(Math.atan2(b, a)))
                        || (b >= -FOUL && b < 0 && a >= -FOUL && a <= L)
                        || (a >= -FOUL && a < 0 && b >= -FOUL && b <= L)
                        || rr < BACKSTOP;
                play[i][j] = in;
                boolean out = (a < 0 && b < 0 && rr > rOut) || b < -side || a < -side;
                stadium[i][j] = land.inner(i, j, RING + 1) && !out;
            }
        }
        t = chamfer(play, true);
        // 2층이 없는 곳(외야·폴 너머)은 그라운드에서 일정 거리까지만: 둥근 외야 관중석과 그 뒤 콘코스
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                if (stadium[i][j] && !play[i][j] && !upper(i, j) && t[i][j] > ROWS + OUTFIELD_CONCOURSE + 2) {
                    stadium[i][j] = false;
                }
            }
        }
        boolean[][] outside = new boolean[w][d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                outside[i][j] = !stadium[i][j];
            }
        }
        u = chamfer(outside, false);
    }

    /** 표시된 칸(src)까지 거리 (3-4 챔퍼, 칸 단위). 표시된 칸은 0. edgeFar: 상자 밖은 멀다고 봄 */
    private double[][] chamfer(boolean[][] src, boolean edgeFar) {
        int w = land.w, d = land.d;
        double big = 1e9;
        double[][] dist = new double[w][d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                dist[i][j] = src[i][j] ? 0 : (!edgeFar && (i == 0 || j == 0 || i == w - 1 || j == d - 1)) ? 1 : big;
            }
        }
        double o = 1, g = Math.sqrt(2);
        for (int pass = 0; pass < 2; pass++) {
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    double m = dist[i][j];
                    if (i > 0) {
                        m = Math.min(m, dist[i - 1][j] + o);
                    }
                    if (j > 0) {
                        m = Math.min(m, dist[i][j - 1] + o);
                        if (i > 0) {
                            m = Math.min(m, dist[i - 1][j - 1] + g);
                        }
                        if (i < w - 1) {
                            m = Math.min(m, dist[i + 1][j - 1] + g);
                        }
                    }
                    dist[i][j] = m;
                }
            }
            for (int j = d - 1; j >= 0; j--) {
                for (int i = w - 1; i >= 0; i--) {
                    double m = dist[i][j];
                    if (i < w - 1) {
                        m = Math.min(m, dist[i + 1][j] + o);
                    }
                    if (j < d - 1) {
                        m = Math.min(m, dist[i][j + 1] + o);
                        if (i < w - 1) {
                            m = Math.min(m, dist[i + 1][j + 1] + g);
                        }
                        if (i > 0) {
                            m = Math.min(m, dist[i - 1][j + 1] + g);
                        }
                    }
                    dist[i][j] = m;
                }
            }
        }
        return dist;
    }

    /** 관중석 줄 번호: 그라운드 가장자리 바깥 첫 칸이 0 (앞 벽) */
    private int row(int i, int j) {
        return (int) Math.floor(t[i][j] - 0.5);
    }

    /** 구역: 0 홈 뒤, 1 1루 쪽, 3 3루 쪽, 2 외야 */
    private int zone(int i, int j) {
        double a = a(i + 0.5, j + 0.5), b = b(i + 0.5, j + 0.5);
        if (a >= 0 && b >= 0) {
            return 2;
        }
        if (a < 0 && b < 0) {
            return 0;
        }
        return b < 0 ? 1 : 3;
    }

    /** 2층이 있는 칸인지 (홈 뒤와 내야) */
    private boolean upper(int i, int j) {
        double a = a(i + 0.5, j + 0.5), b = b(i + 0.5, j + 0.5);
        int z = zone(i, j);
        return z == 0 || (z == 1 && a <= upperReach) || (z == 3 && b <= upperReach);
    }

    private static int seatLow(int T) {
        return T - ROWS - 1;
    }

    private static int seatUp(int T) {
        return 5 + (T - ROWS - 2);
    }

    /** 바깥(관중석 위) 방향: 그라운드에서 멀어지는 쪽 */
    private String outward(int i, int j) {
        double best = -1e9;
        String dir = "south";
        int[][] dd = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] q : dd) {
            int ni = i + q[0], nj = j + q[1];
            if (ni < 0 || nj < 0 || ni >= land.w || nj >= land.d) {
                continue;
            }
            double g = t[ni][nj] - t[i][j];
            if (g > best) {
                best = g;
                dir = dirName(q[0], q[1]);
            }
        }
        return dir;
    }

    /** 둘레 위치 (통로 간격용) */
    private double along(int i, int j) {
        double a = a(i + 0.5, j + 0.5), b = b(i + 0.5, j + 0.5);
        return switch (zone(i, j)) {
            case 1 -> a;
            case 3 -> b;
            case 0 -> Math.atan2(-b, -a) * (BACKSTOP + 8);
            default -> Math.atan2(b, a) * C;
        };
    }

    private boolean aisle(int i, int j) {
        return Math.floorMod((int) Math.floor(along(i, j)), 12) < 2;
    }

    private Block seat(int i, int j, int T) {
        int z = zone(i, j);
        if (z == 2) {
            return GREEN_SEAT;
        }
        if (T > ROWS) {
            return RED_SEAT;
        }
        return z == 0 ? BLUE_SEAT : ORANGE_SEAT;
    }

    private static String stairs(String facing) {
        return "facing=" + facing + ",half=bottom,shape=straight,waterlogged=false";
    }

    // ------------------------------------------------------------------ 광장

    private void plaza() {
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (!land.land(i, j)) {
                    continue;
                }
                long h = (Math.floorDiv(i, 3) * 73856093L) ^ (Math.floorDiv(j, 3) * 19349663L);
                v.set(i, -1, j, Math.floorMod(h, 9) == 0 ? SMOOTH_STONE : LIGHT_GRAY_CONCRETE);
            }
        }
        // 정문 광장: 홈 뒤 바깥 귀퉁이 (거점) 에 나무·가로등·매표소
        double ux = hubI - hi, uz = hubJ - hj, ul = Math.hypot(ux, uz);
        ux /= ul;
        uz /= ul;
        double rGate = BACKSTOP + HOME + 1.5;
        for (int side = -1; side <= 1; side += 2) {
            // 정문 양옆 (호를 따라 ±12칸) 에 매표소
            double ang = Math.atan2(uz, ux) + side * 12.0 / rGate;
            int bi = (int) Math.floor(hi + 0.5 + Math.cos(ang) * (rGate + 3));
            int bj = (int) Math.floor(hj + 0.5 + Math.sin(ang) * (rGate + 3));
            if (land.inner(bi - 2, bj - 2, 2) && land.inner(bi + 3, bj + 3, 2) && !nearHub(bi, bj, 6)) {
                String face = Math.abs(ux) > Math.abs(uz) ? (ux > 0 ? "east" : "west") : (uz > 0 ? "south" : "north");
                StadiumParts.ticketBooth(Frame.facing(v, bi, bj, face));
            }
        }
        for (int i = 2; i < land.w - 2; i += 8) {
            for (int j = 2; j < land.d - 2; j += 8) {
                if (stadium[i][j] || !land.inner(i, j, 3) || nearHub(i, j, 5) || u[i][j] > 4 || nearStadium(i, j, 3)) {
                    continue;
                }
                MarketPlan.tree(v, i, j);
            }
        }
    }

    private boolean nearHub(int i, int j, int r) {
        return Math.abs(i - hubI) <= r + 2 && Math.abs(j - hubJ) <= r + 2;
    }

    private boolean nearStadium(int i, int j, int r) {
        for (int di = -r; di <= r; di++) {
            for (int dj = -r; dj <= r; dj++) {
                int ni = i + di, nj = j + dj;
                if (ni >= 0 && nj >= 0 && ni < land.w && nj < land.d && stadium[ni][nj]) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 관중석 그릇

    private void bowl() {
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (!stadium[i][j]) {
                    continue;
                }
                if (play[i][j]) {
                    v.set(i, FIELD, j, TURF_A);
                    v.fill(i, FIELD + 1, j, i, -1, j, AIR);
                    continue;
                }
                int T = row(i, j);
                int z = zone(i, j);
                boolean up = upper(i, j);
                if (u[i][j] < 1.5) {
                    outerWall(i, j, up);
                    continue;
                }
                String out = outward(i, j);
                if (T <= 0) {
                    // 앞 벽: 외야는 초록 펜스(노란 윗줄), 내야는 회색 벽과 유리 난간
                    v.fill(i, FIELD, j, i, FIELD, j, CONCRETE);
                    if (z == 2) {
                        v.fill(i, FIELD + 1, j, i, FIELD + 2, j, FENCE);
                        v.set(i, FIELD + 3, j, YELLOW_CONCRETE);
                    } else {
                        v.fill(i, FIELD + 1, j, i, FIELD + 2, j, POLISHED_ANDESITE);
                        v.set(i, FIELD + 3, j, GLASS_PANE);
                    }
                    v.fill(i, FIELD + 4, j, i, -1, j, AIR);
                    continue;
                }
                if (T <= ROWS) {
                    int y = seatLow(T);
                    v.fill(i, FIELD, j, i, y - 1, j, CONCRETE);
                    Block s = aisle(i, j) ? STEP : seat(i, j, T);
                    v.set(i, y, j, s.with(stairs(out)));
                    if (y + 1 <= -1) {
                        v.fill(i, y + 1, j, i, -1, j, AIR);
                    }
                    continue;
                }
                // 콘코스 (땅 높이)
                v.set(i, -1, j, POLISHED_ANDESITE);
                if (!up) {
                    // 2층 끝 벽 (계단 모양)
                    if (upperNeighbor(i, j)) {
                        int top = seatUp(T) + 1;
                        if (T > ROWS + 1) {
                            v.fill(i, 4, j, i, top, j, CONCRETE);
                        }
                    }
                    continue;
                }
                if (T == ROWS + 1) {
                    v.fill(i, 4, j, i, 5, j, CONCRETE);
                    v.set(i, 6, j, GLASS_PANE);
                    continue;
                }
                int y = seatUp(T);
                v.fill(i, 4, j, i, y - 1, j, CONCRETE);
                Block s = aisle(i, j) ? STEP : seat(i, j, T);
                v.set(i, y, j, s.with(stairs(out)));
            }
        }
    }

    private boolean upperNeighbor(int i, int j) {
        for (int[] q : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int ni = i + q[0], nj = j + q[1];
            if (ni >= 0 && nj >= 0 && ni < land.w && nj < land.d && stadium[ni][nj] && !play[ni][nj] && upper(ni, nj)) {
                return true;
            }
        }
        return false;
    }

    /** 이 칸 근처 2층 맨 윗줄 높이 */
    private int topRowY(int i, int j) {
        int tmax = (int) Math.floor(t[i][j] + u[i][j] - 0.5);
        return seatUp(tmax - 1);
    }

    /** 바깥벽: 땅 높이는 흰 기둥과 유리, 2층 뒤는 흰 기둥과 회색 판, 2층 없는 곳은 낮은 벽 */
    private void outerWall(int i, int j, boolean up) {
        boolean post = Math.floorMod((int) Math.floor(along(i, j)), 5) == 0;
        v.set(i, -1, j, POLISHED_ANDESITE);
        int top = up ? topRowY(i, j) + 2 : 4;
        for (int y = 0; y <= top; y++) {
            Block b;
            if (y <= 3) {
                b = post ? WHITE_CONCRETE : y == 0 ? LIGHT_GRAY_CONCRETE : up ? GLASS : IRON_BARS;
            } else if (y == top) {
                b = WHITE_CONCRETE;
            } else {
                b = post ? WHITE_CONCRETE : y % 4 == 0 ? LIGHT_GRAY_CONCRETE : SMOOTH_QUARTZ;
            }
            v.set(i, y, j, b);
        }
        if (gate(i, j)) {
            v.fill(i, 0, j, i, 3, j, AIR);
        }
    }

    /** 출입구 자리: 거점 쪽 중앙, 1루·3루 (2층 끝 너머), 외야 둘 */
    private boolean gate(int i, int j) {
        double a = a(i + 0.5, j + 0.5), b = b(i + 0.5, j + 0.5);
        int z = zone(i, j);
        if (z == 0) {
            double hubAng = Math.atan2(b(hubI + 0.5, hubJ + 0.5), a(hubI + 0.5, hubJ + 0.5));
            double ang = Math.atan2(b, a);
            return Math.abs(ang - hubAng) * Math.hypot(a, b) < 3.5;
        }
        if (z == 1) {
            return Math.abs(a - (upperReach + 6)) < 3;
        }
        if (z == 3) {
            return Math.abs(b - (upperReach + 6)) < 3;
        }
        double phi = Math.atan2(b, a), rr = Math.hypot(a, b);
        return Math.abs(phi - Math.toRadians(20)) * rr < 3 || Math.abs(phi - Math.toRadians(70)) * rr < 3;
    }

    // ------------------------------------------------------------------ 그라운드

    private void ground() {
        double s = L / 100.0;
        int base = (int) Math.round(Math.max(12, 27.43 * s));
        double moundD = 18.44 * Math.max(0.45, s);
        double s2 = Math.sqrt(0.5);
        double mx = moundD * s2, mz = moundD * s2;
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (!play[i][j] || !stadium[i][j]) {
                    continue;
                }
                double a = a(i + 0.5, j + 0.5), b = b(i + 0.5, j + 0.5), rr = Math.hypot(a, b);
                boolean fair = a >= -0.5 && b >= -0.5;
                double fromMound = Math.hypot(a - mx, b - mz);
                boolean grassSquare = a > 1.6 && b > 1.6 && a < base - 1.6 && b < base - 1.6;
                boolean basePath = (a >= -1 && a <= base + 1 && (Math.abs(b) < 1.3 || Math.abs(b - base) < 1.3))
                        || (b >= -1 && b <= base + 1 && (Math.abs(a) < 1.3 || Math.abs(a - base) < 1.3));
                boolean infieldArc = fair && fromMound < base * 1.05;
                boolean homeCircle = rr < 3.6;
                boolean warning = fair && rr > fence(Math.atan2(Math.max(0, b), Math.max(0, a))) - 3;
                boolean moundCircle = fromMound < 2.6;
                Block top;
                if (homeCircle || basePath || (infieldArc && !grassSquare) || warning || moundCircle) {
                    top = DIRT;
                } else {
                    top = Math.floorMod((int) Math.floor((a + b) / 4), 2) == 0 ? TURF_A : TURF_B;
                }
                // 파울선과 타석
                if ((Math.abs(b) < 0.5 && a > 0.5 && a <= L) || (Math.abs(a) < 0.5 && b > 0.5 && b <= L)) {
                    top = LINE;
                }
                double bx = Math.abs(a - b) * s2, bz = (a + b) * s2;
                if (bz > -1.5 && bz < 1.5 && (Math.abs(bx - 1.5) < 0.35 || Math.abs(bx - 2.6) < 0.35)) {
                    top = LINE;
                }
                v.set(i, FIELD, j, top);
            }
        }
        // 홈 플레이트·베이스·투수판
        setField(0, 0, QUARTZ);
        setField(base, 0, Block.of("white_wool", 0xE9ECEC));
        setField(base, base, Block.of("white_wool", 0xE9ECEC));
        setField(0, base, Block.of("white_wool", 0xE9ECEC));
        int[] m = cell(mx, mz);
        v.set(m[0], FIELD + 1, m[1], Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
        // 파울 폴 (노랑, 펜스에서 위로)
        for (int k = 0; k < 2; k++) {
            int[] p = k == 0 ? cell(L + 0.6, 0.2) : cell(0.2, L + 0.6);
            v.fill(p[0], FIELD + 1, p[1], p[0], FIELD + 24, p[1], POLE);
            int[] wing = k == 0 ? cell(L + 0.6, 1.2) : cell(1.2, L + 0.6);
            v.fill(wing[0], FIELD + 8, wing[1], wing[0], FIELD + 24, wing[1], Block.of("yellow_stained_glass_pane", 0xE5E533));
        }
        // 펜스 거리 표지 (좌우·가운데)
        distanceSign(0, L);
        distanceSign(Math.PI / 4, C);
        distanceSign(Math.PI / 2, L);
        // 백스톱 그물: 홈 뒤 (두 더그아웃 사이) 앞 벽 위로
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (!stadium[i][j] || play[i][j] || row(i, j) != 0 || zone(i, j) == 2) {
                    continue;
                }
                double a = a(i + 0.5, j + 0.5), b = b(i + 0.5, j + 0.5);
                if (a < 3 && b < 3) {
                    v.fill(i, FIELD + 4, j, i, FIELD + 8, j, NET);
                }
            }
        }
    }

    private void distanceSign(double phi, int dist) {
        double rr = fence(phi) + 0.6;
        double a = Math.cos(phi) * (rr - 1.2), b = Math.sin(phi) * (rr - 1.2);
        int[] p = cell(a, b);
        int[] f = cell(Math.cos(phi) * rr, Math.sin(phi) * rr);
        int di = f[0] - p[0], dj = f[1] - p[1];
        if (di != 0 && dj != 0) {
            dj = 0;
        }
        if (di == 0 && dj == 0) {
            return;
        }
        String facing = dirName(-di, -dj);
        v.set(p[0], FIELD + 2, p[1], Blocks.wallSign("spruce", facing, "white", false, "", dist + "M", "", ""));
    }

    private int[] cell(double a, double b) {
        double x = hi + 0.5 + a * e1i + b * e3i, z = hj + 0.5 + a * e1j + b * e3j;
        return new int[]{(int) Math.floor(x), (int) Math.floor(z)};
    }

    private void setField(double a, double b, Block blk) {
        int[] p = cell(a, b);
        v.set(p[0], FIELD, p[1], blk);
    }

    // ------------------------------------------------------------------ 더그아웃·불펜

    /**
     * 더그아웃: 1루·3루 앞 벽 속 (홈에서 4~13칸), 그라운드 높이 방에 긴 의자, 위는 앞 두 줄 대신 지붕.
     * 콘코스에서 파울선 따라 내려가는 계단 통로로 들어갑니다 (선수 통로).
     */
    private void dugouts() {
        for (int side = 0; side < 2; side++) {
            for (int s = 4; s <= 13; s++) {
                for (int T = 0; T <= 2; T++) {
                    double along = s + 0.5, across = -(FOUL + T + 0.5);
                    int[] p = side == 0 ? cell(along, across) : cell(across, along);
                    v.set(p[0], FIELD, p[1], POLISHED_ANDESITE);
                    v.fill(p[0], FIELD + 1, p[1], p[0], FIELD + 3, p[1], AIR);
                    v.set(p[0], FIELD + 4, p[1], SMOOTH_STONE);
                    v.fill(p[0], FIELD + 5, p[1], p[0], -1, p[1], AIR);
                    if (T == 2) {
                        String in = side == 0 ? dirName(e3i, e3j) : dirName(e1i, e1j);
                        v.set(p[0], FIELD + 1, p[1], Block.of("dark_oak_stairs", 0x432B14).with(stairs(opposite(in))));
                        if (s % 4 == 0) {
                            v.set(p[0], FIELD + 3, p[1], Interior.LIGHT);
                        }
                    }
                    if (T == 0 && (s == 4 || s == 13)) {
                        v.fill(p[0], FIELD + 1, p[1], p[0], FIELD + 3, p[1], CONCRETE);
                    }
                }
            }
            // 콘코스 → 더그아웃 계단 통로: 콘코스 바닥 아래로 파울선 따라 내려가 앞으로 꺾임
            int depth = -1 - FIELD; // 내려갈 칸 수
            for (int k = 0; k <= depth + 1; k++) {
                for (int lane = 0; lane < 2; lane++) {
                    double along = 14 + depth - k + 0.5, across = -(FOUL + ROWS + 1 + lane + 0.5);
                    int[] p = side == 0 ? cell(along, across) : cell(across, along);
                    int stand = -k;
                    String down = side == 0 ? dirName(-e1i, -e1j) : dirName(-e3i, -e3j);
                    if (k > 0 && stand > FIELD + 1) {
                        v.fill(p[0], FIELD, p[1], p[0], stand - 2, p[1], CONCRETE);
                        v.set(p[0], stand - 1, p[1], STEP.with(stairs(opposite(down))));
                    } else if (k > 0) {
                        v.fill(p[0], FIELD, p[1], p[0], FIELD, p[1], POLISHED_ANDESITE);
                    }
                    v.fill(p[0], Math.max(FIELD + 1, stand), p[1], p[0], Math.max(stand + 2, 0), p[1], AIR);
                    if (k == 0) {
                        continue;
                    }
                    // 계단 구멍 둘레 난간 (콘코스 바닥 위)
                    int[] outer = side == 0 ? cell(along, -(FOUL + ROWS + 3 + 0.5)) : cell(-(FOUL + ROWS + 3 + 0.5), along);
                    if (lane == 1 && v.get(outer[0], 0, outer[1]) == null) {
                        v.set(outer[0], 0, outer[1], IRON_BARS);
                    }
                }
            }
            // 아래 통로: 계단 끝에서 더그아웃 뒤까지 (관중석 밑)
            for (int k = 3; k <= ROWS + 2; k++) {
                for (double along : new double[]{12.5, 13.5}) {
                    double across = -(FOUL + k + 0.5);
                    int[] p = side == 0 ? cell(along, across) : cell(across, along);
                    v.set(p[0], FIELD, p[1], POLISHED_ANDESITE);
                    v.fill(p[0], FIELD + 1, p[1], p[0], FIELD + 3, p[1], AIR);
                    if (k % 3 == 0) {
                        v.set(p[0], FIELD + 3, p[1], Interior.LIGHT);
                    }
                }
            }
        }
    }

    private static String opposite(String dir) {
        return Furniture.opposite(dir);
    }

    /** 불펜: 파울 지역 폴 쪽에 마운드·홈 두 쌍, 그라운드 쪽 낮은 철망 */
    private void bullpens() {
        int pitch = (int) Math.round(Math.max(9, 18.44 * L / 100.0));
        for (int side = 0; side < 2; side++) {
            for (int lane = 0; lane < 2; lane++) {
                double across = -(1.5 + lane * 2 + 0.5);
                for (int s = L - 3 - pitch; s <= L - 2; s++) {
                    int[] p = side == 0 ? cell(s + 0.5, across) : cell(across, s + 0.5);
                    v.set(p[0], FIELD, p[1], DIRT);
                }
                int[] mound = side == 0 ? cell(L - 2.5, across) : cell(across, L - 2.5);
                int[] plate = side == 0 ? cell(L - 2.5 - pitch, across) : cell(across, L - 2.5 - pitch);
                v.set(mound[0], FIELD + 1, mound[1], Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
                v.set(plate[0], FIELD, plate[1], QUARTZ);
            }
            for (int s = L - 5 - pitch; s <= L - 1; s++) {
                int[] p = side == 0 ? cell(s + 0.5, -0.9) : cell(-0.9, s + 0.5);
                if (s < L - 3) {
                    v.set(p[0], FIELD + 1, p[1], IRON_BARS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 2층 오르는 계단

    /**
     * 1루·3루 쪽 2층으로 오르는 계단: 콘코스에서 파울선과 나란히 (폭 2) 올라가 2층 앞쪽 두 줄 사이로 나옵니다.
     * 2층이 얕아서(6줄 안팎) 그라운드 쪽으로 오르는 보미토리 대신 줄과 나란한 계단 구멍을 냅니다.
     */
    private void vomitories() {
        int rise = seatUp(ROWS + 2) + 1; // 2층 첫 줄 서는 높이
        for (int side = 0; side < 2; side++) {
            String up = side == 0 ? dirName(e1i, e1j) : dirName(e3i, e3j);
            for (double s0 = 8; s0 + rise + 2 <= upperReach; s0 += 13) {
                for (int lane = 0; lane < 2; lane++) {
                    double across = -(FOUL + ROWS + 2 + lane + 0.5);
                    for (int k = 0; k <= rise; k++) {
                        int[] p = side == 0 ? cell(s0 + k + 0.5, across) : cell(across, s0 + k + 0.5);
                        if (k > 0) {
                            if (k >= 2) {
                                v.fill(p[0], 0, p[1], p[0], k - 2, p[1], CONCRETE);
                            }
                            v.set(p[0], k - 1, p[1], STEP.with(stairs(up)));
                        }
                        for (int y = Math.max(0, k); y <= k + 2; y++) {
                            v.set(p[0], y, p[1], AIR);
                        }
                    }
                    // 계단 아래쪽 끝 둘레 2층 바닥 난간 (계단 구멍에 떨어지지 않게)
                    int[] railA = side == 0 ? cell(s0 - 0.5, across) : cell(across, s0 - 0.5);
                    if (v.get(railA[0], 5, railA[1]) != null) {
                        v.set(railA[0], seatUp(ROWS + 2 + lane) + 1, railA[1], IRON_BARS);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 콘코스

    /** 2층 밑 콘코스 천장 조명, 1루·3루 쪽 콘코스 바깥벽 따라 매점(작은 가판)과 남녀 화장실 */
    private void concourse() {
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (stadium[i][j] && !play[i][j] && upper(i, j) && row(i, j) >= ROWS + 3 && u[i][j] >= 2
                        && Math.floorMod(i, 5) == 0 && Math.floorMod(j, 5) == 0) {
                    Block b = v.get(i, 4, j);
                    if (b != null && !b.isAir()) {
                        v.set(i, 4, j, LAMP);
                    }
                }
            }
        }
        amenities();
    }

    /**
     * 콘코스 바깥벽을 따라 남녀 화장실(두 곳)과 가판 매점: 벽 바로 안쪽 칸마다 그라운드 쪽을 보게 놓아 보고,
     * 바닥이 비어 있고 앞에 지나갈 칸이 남는 자리에만 (서로 12칸 넘게 띄움).
     */
    private void amenities() {
        List<int[]> placed = new ArrayList<>();
        int fk = 0, rest = 0;
        for (int j = 0; j < land.d; j++) {
            for (int i = 0; i < land.w; i++) {
                if (!stadium[i][j] || play[i][j] || u[i][j] < 1.5 || u[i][j] >= 2.5 || row(i, j) <= ROWS) {
                    continue;
                }
                boolean far = true;
                for (int[] p : placed) {
                    far &= Math.abs(p[0] - i) + Math.abs(p[1] - j) > 14;
                }
                if (!far) {
                    continue;
                }
                String facing = opposite(outward(i, j));
                Frame f = Frame.facing(v, i, j, facing);
                if (rest < 2 && clearArea(f, -5, 5, 6)) {
                    StadiumParts.restrooms(f);
                    rest++;
                    placed.add(new int[]{i, j});
                } else if (clearArea(f, 0, 7, 4)) {
                    StadiumParts.kiosk(f, StadiumParts.FOOD[fk++ % StadiumParts.FOOD.length]);
                    placed.add(new int[]{i, j});
                }
            }
        }
    }

    /** 틀 f 에서 a0..a1, b 0..deep-1 이 빈 콘코스 바닥이고, b = deep 줄은 지나갈 수 있는지 (뒤 벽에 출입구 없음) */
    private boolean clearArea(Frame f, int a0, int a1, int deep) {
        for (int a = a0; a <= a1; a++) {
            for (int b = -1; b <= deep; b++) {
                int i = f.i(a, b), j = f.j(a, b);
                if (i < 0 || j < 0 || i >= land.w || j >= land.d) {
                    return false;
                }
                if (b == -1) {
                    if (gate(i, j) || !stadium[i][j]) {
                        return false;
                    }
                    continue;
                }
                boolean passage = b == deep;
                if (!stadium[i][j] || play[i][j] || row(i, j) < (passage ? ROWS : ROWS + 1) || u[i][j] < (passage ? 1 : 1.5)) {
                    return false;
                }
                Block floor = v.get(i, -1, j);
                if (floor == null || floor.isAir()) {
                    return false;
                }
                for (int y = 0; y <= (passage ? 1 : 3); y++) {
                    Block bl = v.get(i, y, j);
                    if (bl != null && !bl.isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 전광판·조명탑

    /** 가운데 외야 뒤 전광판: 홈을 보는 판, 위에 「잠실」, 아래 점수판 (LED 점) */
    private void scoreboard() {
        double rb = C + ROWS + 4;
        int half = Math.min(17, (int) (C * 0.28));
        // 판 양 끝과 가운데가 경기장 안(외야 콘코스 위)에 들 때까지 당김
        while (rb > C + 2 && !(insideStadium(rb, 0) && insideStadium(rb, half) && insideStadium(rb, -half))) {
            rb -= 1;
        }
        double k0 = rb * Math.sqrt(2); // a + b
        int base = 4, top = base + 15;
        Block frame = GRAY_CONCRETE, screen = BLACK_CONCRETE;
        List<int[]> front = new ArrayList<>();
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                double a = a(i + 0.5, j + 0.5), b = b(i + 0.5, j + 0.5);
                double n = a + b - k0, tg = (a - b) * Math.sqrt(0.5);
                if (Math.abs(tg) > half) {
                    continue;
                }
                if (n >= -0.01 && n < 2.0) {
                    boolean leg = Math.abs(Math.abs(tg) - half * 0.6) < 1.0;
                    if (leg) {
                        v.fill(i, 0, j, i, base - 1, j, frame);
                    }
                    for (int y = base; y <= top; y++) {
                        boolean edge = y == base || y == top || Math.abs(tg) > half - 1;
                        v.set(i, y, j, edge ? frame : screen);
                    }
                }
            }
        }
        // 글씨는 판 앞면(그라운드 쪽)에: a + b = k0 - 1 줄, 오른쪽으로 (i, j) 가 늘어나는 방향
        int di = e1i - e3i, dj = e1j - e3j; // 홈에서 볼 때 오른쪽
        di = Integer.signum(di);
        dj = Integer.signum(dj);
        int textW = HangulFont.width(12, "잠실");
        int[] c0 = cell((k0 - 1) / 2.0, (k0 - 1) / 2.0);
        // 대각선 한 칸 = (di, dj) 한 번: 가운데에서 왼쪽으로 textW/2
        int si = c0[0] - di * (textW / 2), sj = c0[1] - dj * (textW / 2);
        HangulFont.draw(v, 12, "잠실", si, top - 1, sj, di, dj, Block.of("ochre_froglight[axis=y]", 0xF5E9B6));
        // 점수판: 두 줄 × 아홉 회 + R H E 칸 (흐린 칸, 몇 칸은 켜짐)
        int[] runs = {0, 1, 0, 0, 2, 0, 1, 0, 0, 0, 0, 3, 0, 0, 1, 0, 0, 0};
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 12; col++) {
                int pi = c0[0] + di * (col * 2 - 12) , pj = c0[1] + dj * (col * 2 - 12);
                int y = base + 2 + (1 - row) * 2;
                boolean lit = col >= 9 || (col < 9 && runs[row * 9 + col] > 0);
                v.set(pi, y, pj, lit ? Block.of("ochre_froglight[axis=y]", 0xF5E9B6) : GRAY_CONCRETE);
            }
        }
    }

    /** 가운데 축에서 rb 만큼, 옆으로 tg 만큼 간 곳이 경기장 안인지 */
    private boolean insideStadium(double rb, double tg) {
        double s2 = Math.sqrt(0.5);
        double a = rb * s2 + tg * s2, b = rb * s2 - tg * s2;
        int[] p = cell(a, b);
        return p[0] >= 0 && p[1] >= 0 && p[0] < land.w && p[1] < land.d && stadium[p[0]][p[1]];
    }

    /** 조명탑: 2층 양 끝 뒤와 외야 양쪽, 높은 철 기둥 위 등 판 (그라운드를 봄) */
    private void lightTowers() {
        List<double[]> spots = new ArrayList<>();
        double back = FOUL + SIDE - 2.5;
        spots.add(new double[]{upperReach - 2, -back});
        spots.add(new double[]{-back, upperReach - 2});
        double rr = BACKSTOP + HOME - 2.5;
        spots.add(new double[]{-rr * Math.cos(Math.toRadians(25)), -rr * Math.sin(Math.toRadians(25))});
        spots.add(new double[]{-rr * Math.cos(Math.toRadians(65)), -rr * Math.sin(Math.toRadians(65))});
        for (double deg : new double[]{18, 72}) {
            double ph = Math.toRadians(deg), fr = fence(ph) + ROWS + 3;
            spots.add(new double[]{Math.cos(ph) * fr, Math.sin(ph) * fr});
        }
        double ga = C * 0.35, gb = C * 0.35; // 그라운드 가운데쯤
        int topY = 40;
        for (double[] sp : spots) {
            int[] p = cell(sp[0], sp[1]);
            if (p[0] < 1 || p[1] < 1 || p[0] >= land.w - 1 || p[1] >= land.d - 1 || !land.inner(p[0], p[1], 2)) {
                continue;
            }
            int baseY = -1;
            for (int y = topY; y >= -1; y--) {
                Block b = v.get(p[0], y, p[1]);
                if (b != null && !b.isAir()) {
                    baseY = y;
                    break;
                }
            }
            v.fill(p[0], baseY + 1, p[1], p[0], topY - 1, p[1], Block.of("iron_block", 0xDCDCDC));
            // 등 판: 그라운드 쪽을 보게 (가까운 축 방향)
            double da = ga - sp[0], db = gb - sp[1];
            double dx = da * e1i + db * e3i, dz = da * e1j + db * e3j;
            boolean faceX = Math.abs(dx) > Math.abs(dz);
            int fi = faceX ? (int) Math.signum(dx) : 0, fj = faceX ? 0 : (int) Math.signum(dz);
            for (int k = -3; k <= 3; k++) {
                for (int y = topY; y <= topY + 3; y++) {
                    int ci = p[0] + (faceX ? 0 : k), cj = p[1] + (faceX ? k : 0);
                    boolean edge = Math.abs(k) == 3 || y == topY || y == topY + 3;
                    v.set(ci, y, cj, edge ? GRAY_CONCRETE : LAMP);
                    v.set(ci - fi, y, cj - fj, GRAY_CONCRETE);
                }
            }
        }
    }
}
