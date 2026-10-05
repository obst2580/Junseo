package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 청라돔: 타원형 돔 야구장.
 * <ul>
 *   <li>바깥: 흰 세로 핀과 유리로 된 벽, 빨간 띠, 흰 돔 지붕(격자 무늬, 가운데 유리 천창, 빛 띠), 네 방향 출입구와 차양</li>
 *   <li>안: 바깥벽 안쪽 둥근 복도(콘코스), 빨간 관중석(가운데 흰 구역), 통로(보미토리), 4칸 낮춘 야구장
 *       (내야 흙, 다이아몬드, 투수 마운드, 파울선, 외야 펜스), 북쪽 전광판, 지붕 조명</li>
 * </ul>
 * 홈 플레이트는 정문(남쪽, 건물 기준) 쪽에 있습니다.
 */
final class Dome {
    private static final int MARGIN = 3;
    private static final int WALL_TOP = 14;
    private static final int APEX = 36;
    /** 경기장 바닥 블록 높이 */
    private static final int FIELD = -5;
    /** 관중석 줄 수 (맨 위 줄 높이 9 → 맨 아래 -1) */
    private static final int ROWS = 11;
    private static final double BOWL_START = 5;
    private static final double FIELD_EDGE = BOWL_START + ROWS + 2;

    private final Voxels v;
    private final double cx, cz, A, B;

    private Dome(int w, int d) {
        v = new Voxels(w, d, FIELD - 1, APEX + 3);
        cx = w / 2.0;
        cz = d / 2.0;
        A = w / 2.0 - MARGIN;
        B = d / 2.0 - MARGIN;
    }

    /** 땅을 차지하는 모양: 바깥벽 타원 (건물 좌표) */
    static List<double[]> footprint(int w, int d) {
        List<double[]> pts = new ArrayList<>();
        double a = w / 2.0 - MARGIN, b = d / 2.0 - MARGIN;
        for (int k = 0; k < 48; k++) {
            double t = 2 * Math.PI * k / 48;
            pts.add(new double[]{w / 2.0 + a * Math.cos(t), d / 2.0 + b * Math.sin(t)});
        }
        return pts;
    }

    static Voxels build(int w, int d, Random r) {
        Dome dome = new Dome(w, d);
        dome.shell();
        dome.field();
        dome.extras();
        dome.v.connect();
        return dome.v;
    }

    /** 바깥벽에서 안쪽으로 잰 거리 (벽 밖이면 음수) */
    private double depth(double i, double j) {
        double dx = (i - cx) / A, dz = (j - cz) / B;
        double e = Math.sqrt(dx * dx + dz * dz);
        double phi = Math.atan2(j - cz, i - cx);
        double rho = 1 / Math.sqrt(Math.cos(phi) * Math.cos(phi) / (A * A) + Math.sin(phi) * Math.sin(phi) / (B * B));
        return (1 - e) * rho;
    }

    private double norm(double i, double j) {
        double dx = (i - cx) / A, dz = (j - cz) / B;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 지붕 높이 */
    private double roof(double i, double j) {
        double e = Math.min(1, norm(i, j));
        return WALL_TOP + (APEX - WALL_TOP) * Math.pow(1 - Math.pow(e, 2.2), 1 / 2.2);
    }

    /** 관중석 줄 높이 (줄 밖이면 Integer.MIN_VALUE) */
    private static int seat(double t) {
        if (t < BOWL_START || t >= BOWL_START + ROWS) {
            return Integer.MIN_VALUE;
        }
        int k = (int) Math.floor(t - BOWL_START);
        return 9 - k;
    }

    private void shell() {
        int w = v.w, d = v.d;
        double[][] rf = new double[w][d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                rf[i][j] = roof(i + 0.5, j + 0.5);
            }
        }
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                double pi = i + 0.5, pj = j + 0.5;
                double t = depth(pi, pj);
                double phi = Math.toDegrees(Math.atan2(pj - cz, pi - cx));
                if (t < -MARGIN) {
                    continue;
                }
                if (t < 0) {
                    // 바깥 광장 포장
                    v.set(i, -1, j, ((i + j) % 4 == 0) ? SMOOTH_STONE : POLISHED_ANDESITE);
                    continue;
                }
                // 지붕 (벽 위부터)
                int top = (int) Math.floor(rf[i][j]);
                int low = top;
                for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ni = i + dd[0], nj = j + dd[1];
                    double nt = ni < 0 || nj < 0 || ni >= w || nj >= d ? -1 : depth(ni + 0.5, nj + 0.5);
                    low = Math.min(low, nt < 0 ? WALL_TOP : (int) Math.floor(rf[ni][nj]));
                }
                low = Math.max(WALL_TOP, Math.min(low + 1, top));
                Block roofBlock = roofPattern(pi, pj);
                for (int y = low; y <= top; y++) {
                    v.set(i, y, j, roofBlock);
                }

                if (t < 1.5) {
                    outerWall(i, j, phi);
                    continue;
                }
                // 콘코스 바닥
                v.set(i, -1, j, POLISHED_ANDESITE);
                int s = seat(t);
                if (s != Integer.MIN_VALUE) {
                    stand(i, j, t, s, phi);
                } else if (t >= BOWL_START + ROWS && t < FIELD_EDGE) {
                    // 맨 앞 통로와 경기장 펜스 (초록 펜스, 노란 윗줄, 유리 난간)
                    boolean fence = t >= FIELD_EDGE - 1;
                    v.fill(i, FIELD + 1, j, i, -3, j, fence ? GREEN_CONCRETE : LIGHT_GRAY_CONCRETE);
                    v.set(i, -2, j, fence ? YELLOW_CONCRETE : SMOOTH_STONE);
                    v.fill(i, -1, j, i, 0, j, AIR);
                    if (fence) {
                        v.set(i, -1, j, GLASS_PANE);
                    }
                } else if (t >= BOWL_START - 1 && t < BOWL_START) {
                    // 맨 윗줄 뒤 등받이 벽 (아래는 복도로 트여 있음)
                    v.fill(i, 8, j, i, 11, j, WHITE_CONCRETE);
                }
            }
        }
    }

    /** 지붕 무늬: 흰 패널에 회색 방사형 살과 동심원 띠, 가운데 유리 천창, 살이 만나는 곳에 조명 */
    private Block roofPattern(double i, double j) {
        double e = norm(i, j);
        double phi = Math.atan2((j - cz) / B, (i - cx) / A);
        double turns = phi / (2 * Math.PI);
        boolean radial = Math.abs(((turns * 24) % 1 + 1) % 1 - 0.5) > 0.465;
        if (e < 0.26) {
            // 천창: 유리, 흰 방사형 살
            return Math.abs(((turns * 16) % 1 + 1) % 1 - 0.5) > 0.44 ? WHITE_CONCRETE : GLASS;
        }
        boolean ring = false;
        for (double r : new double[]{0.4, 0.55, 0.7, 0.85}) {
            ring |= Math.abs(e - r) < 0.011;
        }
        if (radial && ring && (Math.abs(e - 0.55) < 0.011 || Math.abs(e - 0.85) < 0.011)) {
            return SEA_LANTERN; // 안에서 보면 경기장 조명
        }
        if (radial || ring) {
            return LIGHT_GRAY_CONCRETE;
        }
        return WHITE_CONCRETE;
    }

    /** 바깥벽: 아래 짙은 띠, 흰 세로 핀과 유리, 위 빨간 띠, 출입구 */
    private void outerWall(int i, int j, double phi) {
        double along = Math.toRadians(phi) * (A + B) / 2;
        boolean fin = Math.floorMod((int) Math.floor(along), 3) == 0;
        for (int y = 0; y < WALL_TOP; y++) {
            Block b;
            if (y <= 1) {
                b = POLISHED_ANDESITE;
            } else if (y >= WALL_TOP - 3) {
                b = y == WALL_TOP - 3 ? WHITE_CONCRETE : RED_CONCRETE;
            } else {
                b = fin ? WHITE_CONCRETE : (y % 4 == 0 ? WHITE_CONCRETE : GLASS);
            }
            v.set(i, y, j, b);
        }
        v.set(i, -1, j, POLISHED_ANDESITE);
        // 네 방향 출입구 (폭 6, 높이 5)
        for (double gate : new double[]{90, -90, 0, 180}) {
            double diff = Math.abs(((phi - gate) % 360 + 540) % 360 - 180);
            double halfDeg = Math.toDegrees(3.5 / ((A + B) / 2));
            if (diff < halfDeg) {
                v.fill(i, 0, j, i, 4, j, AIR);
                v.set(i, 5, j, RED_CONCRETE);
            }
        }
    }

    /** 관중석 한 칸 */
    private void stand(int i, int j, double t, int s, double phi) {
        // 통로(계단줄): 18도마다
        double aisle = Math.abs(((phi % 18) + 18) % 18 - 9);
        boolean isAisle = aisle > 8.0;
        // 홈 뒤(남쪽) 가운데는 흰 좌석
        boolean premium = phi > 60 && phi < 120;
        Block seatBlock = isAisle ? SMOOTH_STONE : premium ? WHITE_CONCRETE : RED_CONCRETE;
        v.set(i, s, j, seatBlock);
        if (s >= 4) {
            v.set(i, s - 1, j, LIGHT_GRAY_CONCRETE); // 관중석 아래면 (복도 천장)
            // 기둥
            if (t >= BOWL_START + 2 && t < BOWL_START + 3 && Math.floorMod((int) Math.round(phi), 15) == 0) {
                v.fill(i, 0, j, i, s - 2, j, WHITE_CONCRETE);
            }
            if (t >= BOWL_START + 1 && t < BOWL_START + 2 && Math.floorMod((int) Math.round(phi), 20) == 10) {
                v.set(i, s - 2, j, SEA_LANTERN); // 복도 천장 조명
            }
        } else {
            v.fill(i, FIELD + 1, j, i, s - 1, j, LIGHT_GRAY_CONCRETE);
        }
        // 보미토리: 45도마다 복도에서 관중석으로 나가는 통로
        for (int k = 0; k < 8; k++) {
            double gate = k * 45 + 22.5;
            double diff = Math.abs(((phi - gate) % 360 + 540) % 360 - 180);
            double r = (A + B) / 2 - t;
            if (Math.toRadians(diff) * r < 1.6 && s <= 3 && s >= -1) {
                v.fill(i, 0, j, i, 2, j, AIR);
                v.set(i, -1, j, SMOOTH_STONE);
                if (s >= 3) {
                    v.set(i, 3, j, seatBlock);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 경기장

    private void field() {
        // 홈 플레이트: 남쪽 (j 큰 쪽). 중견수 쪽은 -j
        double fieldB = B - FIELD_EDGE;
        double hx = cx, hz = cz + fieldB - 6;
        double base = Math.min(15, (A - FIELD_EDGE) * 0.62);
        double s2 = Math.sqrt(0.5);
        for (int j = 0; j < v.d; j++) {
            for (int i = 0; i < v.w; i++) {
                double pi = i + 0.5, pj = j + 0.5;
                if (depth(pi, pj) < FIELD_EDGE) {
                    continue;
                }
                // 다이아몬드 좌표: a = 1루 쪽, b = 3루 쪽
                double dx = pi - hx, dz = pj - hz;
                double a = dx * s2 - dz * s2, b = -dx * s2 - dz * s2;
                Block top;
                double fromMound = Math.hypot(pi - hx, pj - (hz - base * 0.95));
                boolean fair = a >= -0.5 && b >= -0.5;
                boolean infieldArc = fair && fromMound < base * 1.05;
                boolean grassSquare = a > 1.6 && b > 1.6 && a < base - 1.6 && b < base - 1.6;
                boolean basePath = (a >= -1 && a <= base + 1 && (Math.abs(b) < 1.3 || Math.abs(b - base) < 1.3))
                        || (b >= -1 && b <= base + 1 && (Math.abs(a) < 1.3 || Math.abs(a - base) < 1.3));
                boolean homeCircle = Math.hypot(dx, dz) < 3.2;
                boolean warning = depth(pi, pj) < FIELD_EDGE + 2.5;
                if (homeCircle || basePath || (infieldArc && !grassSquare) || warning) {
                    top = COARSE_DIRT;
                } else {
                    top = (Math.floorDiv(j, 4) % 2 == 0) ? GRASS : MOSS;
                }
                // 파울선
                if ((Math.abs(a) < 0.5 && b > 0) || (Math.abs(b) < 0.5 && a > 0)) {
                    top = WHITE_CONCRETE;
                }
                v.set(i, FIELD, j, top);
                v.fill(i, FIELD + 1, j, i, 0, j, AIR);
            }
        }
        // 베이스·홈·마운드
        setField(hx, hz, QUARTZ);
        setField(hx + base * s2, hz - base * s2, QUARTZ);
        setField(hx, hz - base * Math.sqrt(2), QUARTZ);
        setField(hx - base * s2, hz - base * s2, QUARTZ);
        double mz = hz - base * 0.672 * Math.sqrt(2) * 0.68;
        for (int dj = -2; dj <= 2; dj++) {
            for (int di = -2; di <= 2; di++) {
                if (di * di + dj * dj <= 5) {
                    int i = (int) Math.floor(hx) + di, j = (int) Math.floor(mz) + dj;
                    v.set(i, FIELD + 1, j, di == 0 && dj == 0 ? WHITE_CONCRETE : COARSE_DIRT);
                }
            }
        }
    }

    private void setField(double i, double j, Block b) {
        v.set((int) Math.floor(i), FIELD, (int) Math.floor(j), b);
    }

    // ------------------------------------------------------------------ 전광판·조명·차양

    private void extras() {
        // 북쪽 전광판 (경기장을 바라봄)
        int bj = (int) Math.floor(cz - B + 4);
        int bw = 27, bi0 = (int) Math.floor(cx) - bw / 2;
        for (int i = bi0; i < bi0 + bw; i++) {
            for (int y = 9; y <= 21; y++) {
                boolean border = i == bi0 || i == bi0 + bw - 1 || y == 9 || y == 21;
                v.set(i, y, bj, border ? GRAY_CONCRETE : BLACK_CONCRETE);
            }
            v.set(i, 9, bj - 1, GRAY_CONCRETE);
        }
        HangulFont.draw(v, 12, "청라", bi0 + 1, 20, bj + 1, 1, 0, YELLOW_CONCRETE); // 전광판 글씨 (LED)
        for (int i = bi0 + 2; i < bi0 + bw - 2; i += 2) {
            v.set(i, 11, bj + 1, (i / 2) % 3 == 0 ? RED_CONCRETE : SEA_LANTERN);
        }
        // 관중석 맨 위 조명탑 (30도마다)
        for (int k = 0; k < 12; k++) {
            double ang = Math.toRadians(k * 30 + 15);
            double rr = 1 / Math.sqrt(Math.cos(ang) * Math.cos(ang) / (A * A) + Math.sin(ang) * Math.sin(ang) / (B * B)) - BOWL_START + 0.5;
            int li = (int) Math.floor(cx + rr * Math.cos(ang)), lj = (int) Math.floor(cz + rr * Math.sin(ang));
            v.fill(li - 1, 12, lj, li + 1, 13, lj, SEA_LANTERN);
        }
        // 출입구 차양 (빨간 지붕, 바깥으로 3칸)
        for (int k = 0; k < 4; k++) {
            double ang = Math.toRadians(k * 90);
            for (double rr = 0; rr <= MARGIN; rr += 0.5) {
                double ex = 1 / Math.sqrt(Math.cos(ang) * Math.cos(ang) / (A * A) + Math.sin(ang) * Math.sin(ang) / (B * B)) + rr;
                int ci = (int) Math.floor(cx + ex * Math.cos(ang)), cj = (int) Math.floor(cz + ex * Math.sin(ang));
                for (int s = -4; s <= 4; s++) {
                    int i = ci + (Math.abs(Math.cos(ang)) > 0.5 ? 0 : s), j = cj + (Math.abs(Math.cos(ang)) > 0.5 ? s : 0);
                    v.set(i, 6, j, RED_CONCRETE);
                    if (Math.abs(s) == 4 && rr >= MARGIN - 0.5) {
                        v.fill(i, 0, j, i, 5, j, WHITE_CONCRETE);
                    }
                }
            }
        }
    }
}
