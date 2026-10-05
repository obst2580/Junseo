package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 서울월드컵경기장: 지붕 가운데가 뚫린 축구 전용 경기장.
 * <ul>
 *   <li>경기장(피치): 땅 높이보다 7칸 낮게 판 잔디 구장. 줄무늬 잔디, 흰 선(터치라인·골라인·하프라인, 센터서클,
 *       페널티·골 에어리어, 페널티 아크, 코너 아크), 그물 달린 골대 둘, 정면 쪽 벤치와 선수 출입 통로</li>
 *   <li>관중석: 피치 바로 옆에서 시작하는 가파른 2층. 1층(아래)은 땅 높이까지 6줄, 그 위로 트인 둘레 복도(콘코스),
 *       2층은 복도 위로 올라가며 긴 변이 깊고 짧은 변이 얕음. 좌석은 계단 블록, 통로 계단, 2층으로 오르는 계단(보미토리)</li>
 *   <li>지붕: 관중석만 덮는 반투명 막 지붕(흰 색유리)과 흰 살, 가운데는 하늘로 뚫림. 바깥에 기울어진 흰 돛대 16개와
 *       지붕을 매단 케이블 (방패연·돛배 모양), 지붕 안쪽 끝 조명, 양 끝 전광판</li>
 *   <li>바깥: 흰 기둥과 유리 벽, 출입 게이트, 콘코스의 매점과 화장실, 정면 광장(매표소, 나무, 가로등)</li>
 * </ul>
 * 정면(남쪽, j 큰 쪽)이 광장과 거점 쪽. 땅이 실제(약 170×130m)보다 작으면 피치와 관중석을 땅에 맞춰 줄입니다.
 */
final class FootballStadium {
    /** 피치 땅 블록 높이 (사람은 FIELD + 1 에 섬) */
    static final int FIELD = -8;
    /** 1층 관중석 줄 수 (맨 윗줄이 땅 높이) */
    static final int LOWER = 6;
    /** 2층 앞 난간 거리 (안쪽 선에서) */
    static final int UPPER_FRONT = 7;
    /** 피치 둘레 여유 (사이드라인·골라인 밖 잔디) */
    static final int RUNOFF = 4;
    /** 바깥벽 밖 둘레 보도 폭 */
    static final int RING = 3;

    private static final Block SEAT_LOW = Block.of("red_nether_brick_stairs", 0x450709);
    private static final Block SEAT_UP = Block.of("resin_brick_stairs", 0xB4501E);
    private static final Block STEP = Block.of("polished_andesite_stairs", 0x848685);
    private static final Block CONCRETE = LIGHT_GRAY_CONCRETE;
    private static final Block MEMBRANE = Block.of("white_stained_glass", 0xF0F0F0);
    private static final Block RIB = WHITE_CONCRETE;
    private static final Block MAST = Block.of("quartz_block", 0xEBE5DE);
    private static final Block CABLE = IRON_BARS;
    private static final Block LAMP = SEA_LANTERN;
    private static final Block LINE = Block.of("white_concrete", 0xCFD5D6);
    private static final Block TURF_A = GRASS;
    private static final Block TURF_B = Block.of("moss_block", 0x596E2D);

    private final Voxels v;
    private final SiteLand land;
    private final Random r;
    private final int hubI, hubJ;
    /** 경기장 가운데 (칸 경계 좌표) */
    private double cx, cz;
    /** 안쪽 선(관중석 앞) 반폭·반깊이, 둥근 모서리 반지름 */
    private double ax, az, ar;
    /** 바깥벽 반폭·반깊이, 둥근 모서리 반지름 */
    private double bx, bz, br;
    /** 피치 크기 (길이 i 방향, 너비 j 방향) */
    private int pitchL, pitchW;
    /** 지붕 바깥 끝 높이 */
    private int roofY;

    private FootballStadium(SiteLand land, int hubI, int hubJ, Random r) {
        this.land = land;
        this.r = r;
        this.hubI = hubI;
        this.hubJ = hubJ;
        v = new Voxels(land.w, land.d, FIELD - 1, 64);
    }

    static Voxels build(SiteLand land, int hubI, int hubJ, Random r) {
        FootballStadium s = new FootballStadium(land, hubI, hubJ, r);
        s.fit();
        if (Boolean.getBoolean("mapPreview")) {
            System.out.println("월드컵경기장 맞춤: 가운데 " + s.cx + "," + s.cz + " 바깥 " + s.bx + "×" + s.bz + " 피치 " + s.pitchL + "×" + s.pitchW + " 땅 " + land.w + "×" + land.d + " 거점 " + hubI + "," + hubJ);
        }
        s.plaza();
        s.bowl();
        s.pitch();
        s.tunnelAndBenches();
        s.vomitories();
        s.concourse();
        s.roof();
        s.masts();
        s.screens();
        s.v.connect();
        land.clip(s.v);
        return s.v;
    }

    /** 땅 모양: 바깥벽 둘레 (건물 좌표, 미니맵·검사용). build 와 같은 계산 */
    static List<double[]> footprint(SiteLand land, int hubI, int hubJ) {
        FootballStadium s = new FootballStadium(land, hubI, hubJ, new Random(0));
        s.fit();
        List<double[]> pts = new ArrayList<>();
        for (int k = 0; k < 64; k++) {
            double th = 2 * Math.PI * k / 64;
            double dx = Math.cos(th), dz = Math.sin(th);
            // 바깥 선까지 반지름을 이분법으로
            double lo = 0, hi = Math.max(s.bx, s.bz) * 1.5;
            for (int it = 0; it < 30; it++) {
                double m = (lo + hi) / 2;
                if (sdf(m * dx, m * dz, s.bx, s.bz, s.br) < 0) {
                    lo = m;
                } else {
                    hi = m;
                }
            }
            pts.add(new double[]{s.cx + lo * dx, s.cz + lo * dz});
        }
        return pts;
    }

    // ------------------------------------------------------------------ 크기 맞추기

    /** 둥근 직사각형까지 거리 (밖이 +) */
    static double sdf(double px, double pz, double hx, double hz, double rc) {
        double qx = Math.abs(px) - (hx - rc), qz = Math.abs(pz) - (hz - rc);
        double out = Math.hypot(Math.max(qx, 0), Math.max(qz, 0));
        return out + Math.min(Math.max(qx, qz), 0) - rc;
    }

    private boolean fitsLand(double cxx, double czz, double hx, double hz, double rc) {
        // 바깥 둘레(보도 포함)를 따라 걸으며 안쪽 세 칸이 땅인지 (블록 땅은 볼록이라 둘레만 보면 됨)
        double ex = hx + RING, ez = hz + RING, er = rc + RING;
        double sx = ex - er, sz = ez - er;
        List<double[]> pts = new ArrayList<>();
        for (double a = -sx; a <= sx; a += 0.5) {
            pts.add(new double[]{a, -ez, 0, 1});
            pts.add(new double[]{a, ez, 0, -1});
        }
        for (double b = -sz; b <= sz; b += 0.5) {
            pts.add(new double[]{-ex, b, 1, 0});
            pts.add(new double[]{ex, b, -1, 0});
        }
        for (double th = 0; th < Math.PI / 2; th += 0.5 / Math.max(1, er)) {
            double c = Math.cos(th), s = Math.sin(th);
            for (int[] q : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                pts.add(new double[]{q[0] * (sx + er * c), q[1] * (sz + er * s), -q[0] * c, -q[1] * s});
            }
        }
        for (double[] p : pts) {
            for (double k : new double[]{0.3, 1.2, 2.2}) {
                int i = (int) Math.floor(cxx + p[0] + p[2] * k), j = (int) Math.floor(czz + p[1] + p[3] * k);
                if (!land.inner(i, j, 2)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 이 바깥 크기에 들어가는 피치 길이 (양 끝 관중석 14칸, 긴 변 16칸은 남김) */
    private static int pitchFor(double hx, double hz) {
        int endDepth = 14, sideDepth = 16;
        int len = (int) Math.min(105, Math.floor(2 * (hx - RUNOFF - endDepth)));
        len -= Math.floorMod(len, 2);
        while (len > 30 && hz - Math.round(len * 68.0 / 105) / 2.0 - RUNOFF < sideDepth) {
            len -= 2;
        }
        return len;
    }

    private void fit() {
        // 정면 광장: 거점 앞으로 4칸 띄운 곳까지가 경기장 (보도 포함)
        int jMax = hubJ - 2 - 4 - RING;
        br = 22;
        double bestScore = -1;
        for (int iMin = RING; iMin < RING + 12; iMin++) {
            for (int iMax = land.w - 1 - RING; iMax > land.w - 1 - RING - 12; iMax--) {
                double c = (iMin + iMax + 1) / 2.0, hx = (iMax - iMin + 1) / 2.0;
                // 뒤 끝: 맞는 것 중 가장 깊은 것 (이분법)
                int lo = RING, hi = jMax - 50;
                if (!fitsLand(c, (hi + jMax + 1) / 2.0, hx, (jMax - hi + 1) / 2.0, br)) {
                    continue;
                }
                while (lo < hi) {
                    int m = (lo + hi) / 2;
                    if (fitsLand(c, (m + jMax + 1) / 2.0, hx, (jMax - m + 1) / 2.0, br)) {
                        hi = m;
                    } else {
                        lo = m + 1;
                    }
                }
                double cj = (lo + jMax + 1) / 2.0, hz = (jMax - lo + 1) / 2.0;
                double score = pitchFor(hx, hz) * 100000.0 + hx * hz;
                if (score > bestScore) {
                    bestScore = score;
                    cx = c;
                    bx = hx;
                    cz = cj;
                    bz = hz;
                }
            }
        }
        if (bestScore < 0) {
            cx = land.w / 2.0;
            bx = land.w / 2.0 - 8;
            bz = 30;
            cz = jMax - bz;
        }
        pitchL = pitchFor(bx, bz);
        pitchW = (int) Math.round(pitchL * 68.0 / 105);
        pitchW -= Math.floorMod(pitchW, 2);
        ax = pitchL / 2.0 + RUNOFF;
        az = pitchW / 2.0 + RUNOFF;
        ar = 9;
    }

    /** 안쪽 선 밖으로 거리 (관중석 앞에서 0) */
    private double t(int i, int j) {
        return sdf(i + 0.5 - cx, j + 0.5 - cz, ax, az, ar);
    }

    /** 바깥벽 안쪽으로 거리 (바깥벽 밖이 음수) */
    private double u(int i, int j) {
        return -sdf(i + 0.5 - cx, j + 0.5 - cz, bx, bz, br);
    }

    /** 이 칸에서 관중석 위(바깥) 방향: "north" 등 (건물 기준) */
    private String outward(int i, int j) {
        double px = i + 0.5 - cx, pz = j + 0.5 - cz;
        double qx = Math.abs(px) - (ax - ar), qz = Math.abs(pz) - (az - ar);
        boolean alongX;
        if (qx > 0 && qz > 0) {
            alongX = qx >= qz;
        } else {
            alongX = qx > qz;
        }
        if (alongX) {
            return px > 0 ? "east" : "west";
        }
        return pz > 0 ? "south" : "north";
    }

    private static String opposite(String dir) {
        return Furniture.opposite(dir);
    }

    /** 둘레 위치: 긴 변(동서로 뻗은 변)이면 i, 짧은 변이면 j, 모서리면 각도(도) + 1000 */
    private double along(int i, int j) {
        double px = i + 0.5 - cx, pz = j + 0.5 - cz;
        double qx = Math.abs(px) - (ax - ar), qz = Math.abs(pz) - (az - ar);
        if (qx <= 0) {
            return px;
        }
        if (qz <= 0) {
            return pz;
        }
        return 1000 + Math.toDegrees(Math.atan2(qz, qx));
    }

    /** 통로(계단줄) 칸인지: 긴 변·짧은 변은 11칸마다 2칸, 모서리는 가운데 */
    private boolean aisle(int i, int j) {
        double a = along(i, j);
        if (a >= 1000) {
            return Math.abs(a - 1045) < 4;
        }
        return Math.floorMod((int) Math.floor(a), 11) < 2;
    }

    private int seatY(int T) {
        return T < UPPER_FRONT ? FIELD + 2 + (T - 1) : 5 + (T - UPPER_FRONT - 1);
    }

    // ------------------------------------------------------------------ 광장

    private void plaza() {
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (!land.land(i, j)) {
                    continue;
                }
                long h = (Math.floorDiv(i, 3) * 73856093L) ^ (Math.floorDiv(j, 3) * 19349663L);
                boolean path = Math.abs(i + 0.5 - (hubI + 0.5)) < 5 && j > cz + bz;
                v.set(i, -1, j, path ? POLISHED_ANDESITE : Math.floorMod(h, 9) == 0 ? SMOOTH_STONE : LIGHT_GRAY_CONCRETE);
            }
        }
        // 정면 광장: 나무 줄, 가로등, 매표소
        int jFront = (int) Math.ceil(cz + bz) + 1;
        for (int i = 6; i < land.w - 6; i += 9) {
            if (Math.abs(i - hubI) < 12) {
                continue;
            }
            int j = jFront + 5;
            if (land.inner(i, j, 4) && j < land.d - 3) {
                MarketPlan.tree(v, i, j);
            }
            if (land.inner(i + 4, j, 2) && j < land.d - 3) {
                MarketPlan.lampPost(v, i + 4, j);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            int i0 = hubI + side * 9 - (side < 0 ? 4 : 0);
            int j0 = jFront + 1;
            if (land.inner(i0, j0, 2) && land.inner(i0 + 4, j0 + 2, 2)) {
                StadiumParts.ticketBooth(new Frame(v, i0, j0, 0));
            }
        }
    }

    // ------------------------------------------------------------------ 관중석 그릇

    private void bowl() {
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                double t = t(i, j), u = u(i, j);
                if (u < 0) {
                    continue;
                }
                if (t < 0) {
                    // 경기장 안: 땅을 판다
                    v.set(i, FIELD, j, TURF_A);
                    v.fill(i, FIELD + 1, j, i, -1, j, AIR);
                    continue;
                }
                int T = (int) Math.floor(t);
                String out = outward(i, j);
                boolean aisle = aisle(i, j);
                if (u < 1) {
                    outerWall(i, j);
                    continue;
                }
                if (T == 0) {
                    // 앞 벽: 광고판(검정)과 유리 난간
                    v.set(i, FIELD, j, CONCRETE);
                    v.set(i, FIELD + 1, j, BLACK_CONCRETE);
                    v.set(i, FIELD + 2, j, CONCRETE);
                    v.set(i, FIELD + 3, j, GLASS_PANE);
                    v.fill(i, FIELD + 4, j, i, -1, j, AIR);
                    continue;
                }
                if (T < UPPER_FRONT) {
                    // 1층 줄
                    int y = seatY(T);
                    v.fill(i, FIELD, j, i, y - 1, j, CONCRETE);
                    v.set(i, y, j, (aisle ? STEP : SEAT_LOW).with(stairProps(out)));
                    if (y + 1 <= -1) {
                        v.fill(i, y + 1, j, i, -1, j, AIR);
                    }
                    continue;
                }
                // 콘코스 (땅 높이) 와 위 2층
                v.set(i, -1, j, POLISHED_ANDESITE);
                if (T == UPPER_FRONT) {
                    v.fill(i, 4, j, i, 5, j, CONCRETE);
                    v.set(i, 6, j, GLASS_PANE);
                    continue;
                }
                int y = seatY(T);
                v.fill(i, 4, j, i, y - 1, j, CONCRETE);
                v.set(i, y, j, Block.of((aisle ? STEP : SEAT_UP).id(), aisle ? STEP.rgb() : SEAT_UP.rgb()).with(stairProps(out)));
            }
        }
    }

    private static String stairProps(String facing) {
        return "facing=" + facing + ",half=bottom,shape=straight,waterlogged=false";
    }

    /** 맨 윗줄 높이 (이 칸 근처) */
    private int topRowY(int i, int j) {
        double t = t(i, j), u = u(i, j);
        int tmax = (int) Math.floor(t + u);
        return seatY(tmax - 1);
    }

    /** 바깥벽: 땅 높이는 흰 기둥과 유리, 관중석 뒤는 흰 판, 그 위 지붕까지 기둥 */
    private void outerWall(int i, int j) {
        double a = along(i, j);
        boolean post = a >= 1000 ? Math.floorMod((int) Math.floor((a - 1000) / 7.5), 2) == 0 : Math.floorMod((int) Math.floor(a), 4) == 0;
        int top = topRowY(i, j);
        v.set(i, -1, j, POLISHED_ANDESITE);
        for (int y = 0; y <= roofLevel(); y++) {
            Block b;
            if (y <= 3) {
                b = post ? WHITE_CONCRETE : y == 0 ? LIGHT_GRAY_CONCRETE : GLASS;
            } else if (y <= top + 2) {
                b = y == 4 || y % 5 == 4 ? LIGHT_GRAY_CONCRETE : post ? WHITE_CONCRETE : Block.of("white_stained_glass_pane", 0xF0F0F0);
            } else {
                b = post ? WHITE_CONCRETE : null;
            }
            if (b != null) {
                v.set(i, y, j, b);
            }
        }
        // 출입 게이트: 각 변 가운데와 모서리
        if (gate(i, j)) {
            v.fill(i, 0, j, i, 3, j, AIR);
        }
    }

    /** 게이트 자리 (바깥벽 위, 폭 6) */
    private boolean gate(int i, int j) {
        double a = along(i, j);
        double px = i + 0.5 - cx, pz = j + 0.5 - cz;
        if (a >= 1000) {
            return Math.abs(a - 1045) < 6;
        }
        double qx = Math.abs(px) - (ax - ar);
        if (qx <= 0) {
            // 긴 변: 가운데와 ±(길이의 1/3)
            double third = (ax - ar) * 0.6;
            return Math.abs(px - (hubI + 0.5 - cx) * (pz > 0 ? 1 : 0)) < 3.5 || Math.abs(Math.abs(px) - third) < 3;
        }
        return Math.abs(pz) < 3.5;
    }

    private int roofLevel() {
        return roofY;
    }

    // ------------------------------------------------------------------ 피치

    private void pitch() {
        double hl = pitchL / 2.0, hw = pitchW / 2.0;
        double s = pitchL / 105.0;
        double circle = 9.15 * s, boxD = 16.5 * s, boxW = 40.3 * s / 2, goalD = 5.5 * s, goalW = 18.3 * s / 2, spot = 11 * s;
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (t(i, j) >= 0) {
                    continue;
                }
                double px = i + 0.5 - cx, pz = j + 0.5 - cz;
                double ux = Math.abs(px), uz = Math.abs(pz);
                // 깎은 줄무늬 (5칸마다)
                Block b = Math.floorMod((int) Math.floor(px / 5), 2) == 0 ? TURF_A : TURF_B;
                boolean line = false;
                if (ux <= hl && uz <= hw) {
                    // 바깥 선
                    line |= ux > hl - 1 || uz > hw - 1;
                    // 하프라인, 센터서클과 점
                    line |= Math.abs(px) < 0.5;
                    double rc = Math.hypot(px, pz);
                    line |= Math.abs(rc - circle) < 0.5 || rc < 0.8;
                    // 페널티 에어리어, 골 에어리어, 페널티 점, 아크
                    double fromGoal = hl - ux;
                    line |= (Math.abs(fromGoal - boxD) < 0.5 && uz <= boxW) || (fromGoal <= boxD && Math.abs(uz - boxW) < 0.5);
                    line |= (Math.abs(fromGoal - goalD) < 0.5 && uz <= goalW) || (fromGoal <= goalD && Math.abs(uz - goalW) < 0.5);
                    double rs = Math.hypot(fromGoal - spot, pz);
                    line |= rs < 0.7 || (Math.abs(rs - circle) < 0.5 && fromGoal > boxD);
                    // 코너 아크
                    double rcorner = Math.hypot(hl - ux, hw - uz);
                    line |= Math.abs(rcorner - 1.5) < 0.5;
                }
                v.set(i, FIELD, j, line ? LINE : b);
            }
        }
        // 골대 (실제 크기: 폭 7, 높이 2) 와 그물
        Block post = Block.of("pale_oak_fence", 0xE3D9D4);
        Block net = Block.of("white_stained_glass_pane", 0xF0F0F0);
        for (int side = -1; side <= 1; side += 2) {
            int gi = (int) Math.floor(cx + side * pitchL / 2.0 + (side > 0 ? 0 : -1));
            int gj0 = (int) Math.floor(cz) - 4, gj1 = (int) Math.floor(cz) + 3;
            int back = gi + side * 2;
            for (int j = gj0; j <= gj1; j++) {
                v.set(gi, FIELD + 3, j, post);
                v.set(back, FIELD + 1, j, net);
                v.set(back, FIELD + 2, j, net);
                v.set(gi + side, FIELD + 3, j, net);
                v.set(back, FIELD + 3, j, net);
            }
            for (int y = FIELD + 1; y <= FIELD + 2; y++) {
                v.set(gi, y, gj0, post);
                v.set(gi, y, gj1, post);
                v.set(gi + side, y, gj0, net);
                v.set(gi + side, y, gj1, net);
                v.set(back, y, gj0, net);
                v.set(back, y, gj1, net);
            }
        }
    }

    // ------------------------------------------------------------------ 선수 통로와 벤치

    /** 정면(본부석) 가운데: 콘코스에서 피치로 내려가는 선수 통로 (폭 3, 위에 유리 차양), 양옆 벤치 */
    private void tunnelAndBenches() {
        int ci = (int) Math.floor(cx);
        // 정면 쪽 안쪽 선 위치
        int jEdge = (int) Math.ceil(cz + az);
        for (int di = -1; di <= 1; di++) {
            int i = ci + di;
            for (int k = 0; k <= UPPER_FRONT + 3; k++) {
                int j = jEdge + k;
                // k 가 클수록 높음: 콘코스(서는 높이 0)에서 피치(FIELD+1)까지
                int stand = Math.max(FIELD + 1, Math.min(0, k - UPPER_FRONT - 1 + 0));
                int h = Math.max(FIELD + 1, -(UPPER_FRONT + 1 - k));
                stand = Math.min(0, h);
                v.fill(i, FIELD, j, i, stand - 1, j, CONCRETE);
                v.fill(i, stand, j, i, 3, j, AIR);
                if (stand < 0 && k > 0 && stand > FIELD + 1) {
                    v.set(i, stand - 1, j, STEP.with(stairProps("north")));
                } else if (stand == FIELD + 1) {
                    v.set(i, FIELD, j, POLISHED_ANDESITE);
                }
            }
            // 위로 유리 차양 (땅 높이 위)
            for (int j = jEdge - 2; j <= jEdge + 5; j++) {
                v.set(i, 1, j, null);
            }
        }
        // 통로 양옆 벽과 유리 지붕 (피치 쪽으로 튀어나온 차양)
        for (int k = -3; k <= UPPER_FRONT; k++) {
            int j = jEdge + k;
            for (int side = -1; side <= 1; side += 2) {
                int i = ci + side * 2;
                int stand = Math.min(0, Math.max(FIELD + 1, -(UPPER_FRONT + 1 - Math.max(k, 0))));
                if (k >= 0) {
                    v.fill(i, FIELD, j, i, stand + 2, j, WHITE_CONCRETE);
                }
            }
            if (k < 0) {
                v.fill(ci - 2, FIELD + 4, j, ci + 2, FIELD + 4, j, GLASS);
                v.set(ci - 2, FIELD + 1, j, Block.of("iron_bars", 0x888888));
                v.set(ci + 2, FIELD + 1, j, Block.of("iron_bars", 0x888888));
            }
        }
        // 벤치 (통로 양옆, 피치 옆 잔디 위): 의자 줄과 유리 지붕
        for (int side = -1; side <= 1; side += 2) {
            int i0 = ci + side * 5, i1 = ci + side * 12;
            int jb = jEdge - 1;
            for (int i = Math.min(i0, i1); i <= Math.max(i0, i1); i++) {
                v.set(i, FIELD + 1, jb, STEP.with(stairProps("south")));
                v.set(i, FIELD + 1, jb + 0, Block.of("polished_blackstone_stairs", 0x353038).with(stairProps("south")));
                v.set(i, FIELD + 3, jb, GLASS);
                v.set(i, FIELD + 3, jb - 1, GLASS);
            }
            v.fill(Math.min(i0, i1), FIELD + 1, jb - 1, Math.min(i0, i1), FIELD + 2, jb - 1, GLASS_PANE);
            v.fill(Math.max(i0, i1), FIELD + 1, jb - 1, Math.max(i0, i1), FIELD + 2, jb - 1, GLASS_PANE);
        }
    }

    // ------------------------------------------------------------------ 2층 오르는 계단

    /**
     * 긴 변의 통로 줄마다 하나 걸러: 콘코스 뒤에서 피치 쪽으로 오르는 계단(폭 2)이 2층 관중석 가운데로 나옵니다.
     * 계단 위 관중석은 굴처럼 남기고, 머리 위가 3칸 비게 깎습니다.
     */
    private void vomitories() {
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                double t = t(i, j), u = u(i, j);
                if (t < UPPER_FRONT || u < 1) {
                    continue;
                }
                double a = along(i, j);
                if (a >= 1000) {
                    continue;
                }
                double px = i + 0.5 - cx;
                double qx = Math.abs(px) - (ax - ar);
                if (qx > 0) {
                    continue; // 짧은 변(양 끝)은 얕아서 모서리로 이어서 올라감
                }
                int k = (int) Math.floor(a);
                if (Math.floorMod(k, 22) >= 2 || Math.abs(px) > ax - ar - 6) {
                    continue;
                }
                int T = (int) Math.floor(t);
                int tmax = (int) Math.floor(t + u);
                // 계단 아래 끝 (콘코스 바닥, 서는 높이 0) 과 위 끝 (2층 줄과 같은 높이): start - T = seatY(T) + 1 = T + c
                int c = 5 - UPPER_FRONT;
                int start = tmax - 2;
                start -= Math.floorMod(start - c, 2);
                int landT = (start - c) / 2;
                if (landT <= UPPER_FRONT + 1 || T > start || T < landT) {
                    continue;
                }
                String in = opposite(outward(i, j));
                int stand = start - T;
                if (stand > 0) {
                    if (stand >= 2) {
                        v.fill(i, 0, j, i, stand - 2, j, CONCRETE);
                    }
                    v.set(i, stand - 1, j, STEP.with(stairProps(in)));
                }
                for (int y = Math.max(0, stand); y <= stand + 2; y++) {
                    v.set(i, y, j, AIR);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 콘코스: 매점·화장실·조명

    private void concourse() {
        // 천장 조명
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                double t = t(i, j), u = u(i, j);
                if (t >= UPPER_FRONT + 2 && u >= 2 && Math.floorMod(i, 6) == 0 && Math.floorMod(j, 6) == 0 && v.get(i, 4, j) != null
                        && !v.get(i, 4, j).isAir()) {
                    v.set(i, 4, j, LAMP);
                }
            }
        }
        // 바깥벽 따라 매점과 화장실
        amenities();
    }

    /**
     * 콘코스 바깥벽을 따라 남녀 화장실(네 곳)과 매점: 벽 바로 안쪽 칸마다 피치 쪽을 보게 놓아 보고,
     * 바닥이 비어 있고 앞에 지나갈 칸이 남는 자리에만 (서로 14칸 넘게 띄움).
     */
    private void amenities() {
        List<int[]> placed = new ArrayList<>();
        int fk = 0, rest = 0;
        for (int j = 0; j < land.d; j++) {
            for (int i = 0; i < land.w; i++) {
                double tt = t(i, j), uu = u(i, j);
                if (uu < 1 || uu >= 2 || tt < UPPER_FRONT + 1) {
                    continue;
                }
                boolean far = true;
                for (int[] p : placed) {
                    far &= Math.abs(p[0] - i) + Math.abs(p[1] - j) > 16;
                }
                if (!far) {
                    continue;
                }
                Frame f = Frame.facing(v, i, j, opposite(outward(i, j)));
                if (rest < 4 && clearArea(f, -5, 5, 6)) {
                    StadiumParts.restrooms(f);
                    rest++;
                    placed.add(new int[]{i, j});
                } else if (clearArea(f, -1, 9, 5)) {
                    StadiumParts.foodStand(f, StadiumParts.FOOD[fk++ % StadiumParts.FOOD.length]);
                    placed.add(new int[]{i, j});
                }
            }
        }
    }

    /** 틀 f 에서 a0..a1, b 0..deep-1 이 빈 콘코스 바닥이고 b = deep 줄은 지나갈 수 있는지 (뒤 벽에 게이트 없음) */
    private boolean clearArea(Frame f, int a0, int a1, int deep) {
        for (int a = a0; a <= a1; a++) {
            for (int b = -1; b <= deep; b++) {
                int i = f.i(a, b), j = f.j(a, b);
                if (i < 0 || j < 0 || i >= land.w || j >= land.d) {
                    return false;
                }
                double uu = u(i, j), tt = t(i, j);
                if (b == -1) {
                    if (uu >= 1 || gate(i, j)) {
                        return false;
                    }
                    continue;
                }
                boolean passage = b == deep;
                if (uu < 1 || tt < (passage ? UPPER_FRONT - 1 : UPPER_FRONT + 1)) {
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

    // ------------------------------------------------------------------ 지붕·돛대

    private void roof() {
        // 지붕 높이: 가장 높은 맨 윗줄 위 3칸에서, 안쪽으로 조금 들림
        int maxTop = 0;
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (u(i, j) >= 0 && u(i, j) < 1 && t(i, j) > 0) {
                    maxTop = Math.max(maxTop, topRowY(i, j));
                }
            }
        }
        roofY = maxTop + 4;
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                double t = t(i, j), u = u(i, j);
                if (u < 0 || t < 1.5) {
                    continue;
                }
                double depth = t + u;
                int y = roofY + (int) Math.round(u / depth * 3);
                double a = along(i, j);
                boolean rib = a >= 1000 ? Math.floorMod((int) Math.floor((a - 1000) / 7.5), 2) == 0 && ((a - 1000) % 7.5) < 1.2
                        : Math.floorMod((int) Math.floor(a), 8) == 0;
                boolean edge = t < 2.5;
                v.set(i, y, j, edge ? CONCRETE : rib ? RIB : MEMBRANE);
                if (edge && Math.floorMod(i + j, 5) == 0) {
                    v.set(i, y - 1, j, LAMP); // 지붕 끝 조명 (경기장 조명)
                }
                // 지붕과 바깥벽 사이 (맨 윗줄 위 빈 곳) 은 기둥만
                if (u < 1) {
                    for (int yy = topRowY(i, j) + 3; yy < y; yy++) {
                        if (v.get(i, yy, j) == null && rib) {
                            v.set(i, yy, j, WHITE_CONCRETE);
                        }
                    }
                }
            }
        }
        // 바깥벽을 지붕 높이까지 (기둥만)
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                double u = u(i, j);
                if (u >= 0 && u < 1 && t(i, j) > 0) {
                    outerWall(i, j);
                }
            }
        }
    }

    /** 바깥 돛대 16개: 바깥으로 기운 흰 기둥, 꼭대기에서 지붕 가운데로 케이블 */
    private void masts() {
        List<double[]> fp = new ArrayList<>();
        int n = 16;
        // 바깥 선을 따라 고르게: 각도로 근사
        for (int k = 0; k < n; k++) {
            double th = 2 * Math.PI * (k + 0.5) / n;
            double dx = Math.cos(th), dz = Math.sin(th);
            double lo = 0, hi = Math.max(bx, bz) * 1.5;
            for (int it = 0; it < 30; it++) {
                double m = (lo + hi) / 2;
                if (sdf(m * dx, m * dz, bx, bz, br) < 0) {
                    lo = m;
                } else {
                    hi = m;
                }
            }
            fp.add(new double[]{cx + (lo + 1.5) * dx, cz + (lo + 1.5) * dz, dx, dz, lo});
        }
        int top = roofY + 16;
        for (double[] p : fp) {
            double bi = p[0], bj = p[1];
            double ti = bi + p[2] * 3, tj = bj + p[3] * 3;
            v.rod(bi, -0.5, bj, ti, top, tj, 0.7, MAST);
            v.set((int) Math.floor(ti), top + 1, (int) Math.floor(tj), MAST);
            // 케이블: 꼭대기 → 지붕 가운데쯤 한 가닥
            double mid = p[4] - (p[4] - Math.hypot(ax, az) * 0.75) * 0.55;
            v.rod(ti, top, tj, cx + p[2] * mid, roofY + 2, cz + p[3] * mid, 0.3, CABLE);
        }
    }

    /** 양 끝 2층 위 전광판 (지붕 아래) */
    private void screens() {
        for (int side = -1; side <= 1; side += 2) {
            int i = (int) Math.floor(cx + side * (bx - 2.5));
            int j0 = (int) Math.floor(cz) - 8, j1 = (int) Math.floor(cz) + 8;
            int y0 = roofY - 7, y1 = roofY - 1;
            for (int j = j0; j <= j1; j++) {
                for (int y = y0; y <= y1; y++) {
                    boolean border = j == j0 || j == j1 || y == y0 || y == y1;
                    v.set(i, y, j, border ? GRAY_CONCRETE : BLACK_CONCRETE);
                }
            }
            // 화면에 점수 (LED): 가운데 줄
            for (int j = j0 + 2; j <= j1 - 2; j += 3) {
                v.set(i - side, y0 + 3, j, j == (int) Math.floor(cz) - 1 || j == (int) Math.floor(cz) + 2 ? SEA_LANTERN : Block.of("ochre_froglight[axis=y]", 0xF5E9B6));
            }
        }
    }
}
