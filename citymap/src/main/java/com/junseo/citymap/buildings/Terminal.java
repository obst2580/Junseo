package com.junseo.citymap.buildings;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 공항 여객 터미널 (인천공항 제1터미널 느낌).
 * 육지 쪽이 오목한 활 모양 건물에 물결치는 흰 지붕, 앞뒤 전면 유리벽, 육지 쪽 큰 차양(출국장 앞 도로),
 * 비행기 쪽에는 탑승교와 계류장(유도선)이 있습니다.
 * 안은 한 층짜리 큰 홀: 체크인 카운터 섬, 둥근 기둥, 출발 안내판, 의자, 화분, 천장 조명.
 * <p>
 * 건물 좌표: a = 활주로 방향, b = 활주로에서 육지 쪽으로. 활 중심 (ca, cb) 은 육지 쪽 멀리 있고,
 * 중심에서 rIn ~ rOut 사이가 건물입니다.
 */
final class Terminal {
    final double ca, cb, rIn, rOut, halfAngle;
    /** 탑승교 각도 (도) */
    final double[] gates;
    private final Voxels v;

    private static final int BASE_H = 13;

    Terminal(int w, int d, double ca, double cb, double rIn, double rOut, double halfLength, double[] gates) {
        this.ca = ca;
        this.cb = cb;
        this.rIn = rIn;
        this.rOut = rOut;
        this.halfAngle = Math.asin(halfLength / rOut);
        this.gates = gates;
        this.v = new Voxels(w, d, -1, 34);
    }

    static Voxels build(int w, int d, double ca, double cb, double rIn, double rOut, double halfLength, double[] gates) {
        Terminal t = new Terminal(w, d, ca, cb, rIn, rOut, halfLength, gates);
        t.apron();
        t.hall();
        t.bridges();
        return t.v;
    }

    double rho(double a, double b) {
        return Math.hypot(a - ca, b - cb);
    }

    /** 활 중심에서 본 각도 (0 = 활주로 쪽 정면, 라디안) */
    double psi(double a, double b) {
        return Math.atan2(a - ca, cb - b);
    }

    /** 지붕 높이: 앞뒤로 둥근 단면 + 가운데가 솟은 물결 */
    double roofHeight(double rr, double ps) {
        double s = Math.max(0, Math.min(1, (rr - rIn) / (rOut - rIn)));
        double arch = 8.5 * Math.pow(Math.sin(Math.PI * (0.15 + 0.7 * s)), 0.8);
        double bulge = 7 * Math.exp(-Math.pow(Math.toDegrees(ps) / 3.2, 2));
        double wave = 1.6 * Math.cos(Math.toDegrees(ps) / 2.2);
        return BASE_H + arch + bulge + wave;
    }

    // ------------------------------------------------------------------ 계류장

    private void apron() {
        for (int j = 0; j < v.d; j++) {
            for (int i = 0; i < v.w; i++) {
                double a = i + 0.5, b = j + 0.5, rr = rho(a, b), ps = psi(a, b);
                if (rr < rOut || Math.abs(a - ca) > rOut * Math.sin(halfAngle) + 10) {
                    continue;
                }
                Block floor = LIGHT_GRAY_CONCRETE;
                // 유도선: 탑승교마다 비행기 쪽으로 곧게, 활주로와 나란한 유도로 중심선
                for (double g : gates) {
                    double off = (ps - Math.toRadians(g)) * rr;
                    if (Math.abs(off) < 0.5) {
                        floor = YELLOW_CONCRETE;
                    }
                }
                if (Math.abs(b - 12) < 0.5) {
                    floor = YELLOW_CONCRETE;
                }
                v.set(i, -1, j, floor);
            }
        }
    }

    // ------------------------------------------------------------------ 본관

    private void hall() {
        double[][] roof = new double[v.w][v.d];
        for (int i = 0; i < v.w; i++) {
            for (int j = 0; j < v.d; j++) {
                roof[i][j] = roofHeight(rho(i + 0.5, j + 0.5), psi(i + 0.5, j + 0.5));
            }
        }
        double endT = 1.3 / rOut; // 끝벽 두께 (라디안)
        for (int j = 0; j < v.d; j++) {
            for (int i = 0; i < v.w; i++) {
                double a = i + 0.5, b = j + 0.5, rr = rho(a, b), ps = psi(a, b);
                double aps = Math.abs(ps);
                boolean inHall = rr >= rIn && rr < rOut && aps <= halfAngle;
                boolean canopy = rr >= rIn - 7 && rr < rIn && aps <= halfAngle - 0.004;
                boolean eave = rr >= rOut && rr < rOut + 3 && aps <= halfAngle;
                if (!inHall && !canopy && !eave) {
                    // 차양 밖 출국장 앞 보도 (도로까지)
                    if (rr >= rIn - 30 && rr < rIn && aps <= halfAngle + 0.02) {
                        v.set(i, -1, j, LIGHT_GRAY_CONCRETE);
                    }
                    continue;
                }
                int top = (int) Math.floor(roof[i][j]);
                if (canopy) {
                    // 출국장 앞 차양: 지붕에서 이어지는 얇은 판, 끝에 조명
                    int y = Math.min(top, BASE_H - 1);
                    v.set(i, y, j, rr < rIn - 6 ? WHITE_CONCRETE : (Math.floorMod((int) (ps * rr), 6) == 0 ? SEA_LANTERN : SMOOTH_QUARTZ));
                    // 차양 기둥
                    if (rr >= rIn - 5 && rr < rIn - 4 && Math.floorMod((int) Math.floor(ps * rr), 16) == 0) {
                        v.fill(i, 0, j, i, y - 1, j, WHITE_CONCRETE);
                    }
                    v.set(i, -1, j, SMOOTH_STONE);
                    continue;
                }
                if (eave) {
                    v.set(i, Math.min(top, BASE_H + 1), j, WHITE_CONCRETE);
                    continue;
                }
                // 바닥
                v.set(i, -1, j, Math.floorMod((int) Math.floor(ps * rr), 8) == 0 ? POLISHED_ANDESITE : SMOOTH_QUARTZ);
                // 지붕 (구멍 없이 이웃 높이까지 채움)
                int low = top;
                for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ni = Math.max(0, Math.min(v.w - 1, i + dd[0])), nj = Math.max(0, Math.min(v.d - 1, j + dd[1]));
                    low = Math.min(low, (int) Math.floor(roof[ni][nj]));
                }
                low = Math.min(top, low + 1);
                double along = ps * rr;
                boolean rib = Math.floorMod((int) Math.floor(along), 7) == 0;
                boolean skylight = Math.abs((rr - rIn) / (rOut - rIn) - 0.5) < 0.06;
                for (int y = low; y <= top; y++) {
                    v.set(i, y, j, skylight ? GLASS : rib ? LIGHT_GRAY_CONCRETE : WHITE_CONCRETE);
                }
                // 천장 조명
                if (Math.floorMod((int) Math.floor(along), 7) == 3 && Math.floorMod((int) Math.floor(rr), 6) == 0) {
                    v.set(i, top, j, SEA_LANTERN);
                }

                boolean landWall = rr < rIn + 1.2;
                boolean airWall = rr >= rOut - 1.2;
                boolean endWall = aps > halfAngle - endT;
                if (landWall || airWall || endWall) {
                    glassWall(i, j, low - 1, along, landWall && !endWall);
                    continue;
                }
                interior(i, j, rr, ps, along, low - 1);
            }
        }
    }

    /** 전면 유리벽: 4칸마다 흰 세로살, 5칸마다 가로살. 육지 쪽은 24칸마다 출입문 */
    private void glassWall(int i, int j, int topY, double along, boolean entrances) {
        boolean mullion = Math.floorMod((int) Math.floor(along), 4) == 0;
        boolean door = entrances && Math.floorMod((int) Math.floor(along) + 2, 24) < 4;
        for (int y = 0; y <= topY; y++) {
            Block b;
            if (door && y <= 3) {
                b = AIR;
            } else if (y == 0 && !door) {
                b = LIGHT_GRAY_CONCRETE;
            } else if (mullion || y % 5 == 4) {
                b = WHITE_CONCRETE;
            } else {
                b = LIGHT_BLUE_GLASS;
            }
            v.set(i, y, j, b);
        }
    }

    /** 홀 안 */
    private void interior(int i, int j, double rr, double ps, double along, int ceiling) {
        double s = (rr - rIn) / (rOut - rIn);
        int alongI = (int) Math.floor(along);
        // 둥근 기둥 두 줄
        for (double rowS : new double[]{0.3, 0.7}) {
            double colR = rIn + rowS * (rOut - rIn);
            double colAlong = Math.round(along / 18.0) * 18.0;
            if (Math.hypot(rr - colR, along - colAlong) < 1.2) {
                v.fill(i, 0, j, i, ceiling, j, QUARTZ_PILLAR);
                return;
            }
        }
        // 체크인 카운터 섬: 기둥 사이마다, 건물 깊이 방향으로 길게
        double islandAlong = Math.round((along - 9) / 18.0) * 18.0 + 9;
        double off = along - islandAlong;
        if (s > 0.38 && s < 0.82 && Math.abs(off) < 2.5) {
            if (Math.abs(off) < 0.6) {
                v.fill(i, 0, j, i, 2, j, WHITE_CONCRETE); // 가운데 벽
                v.set(i, 4, j, BLUE_CONCRETE); // 위 표지판
                v.set(i, 5, j, s > 0.6 && s < 0.64 ? SEA_LANTERN : BLUE_CONCRETE);
            } else if (Math.abs(off) < 1.6) {
                v.set(i, -1, j, BLACK_CONCRETE); // 짐 벨트
            } else {
                v.set(i, 0, j, SMOOTH_QUARTZ); // 카운터
            }
            return;
        }
        // 비행기 쪽 창가 의자
        if (s > 0.86 && s < 0.93 && Math.floorMod(alongI, 6) < 4) {
            v.set(i, 0, j, DARK_OAK_SLAB);
            return;
        }
        // 육지 쪽 화분
        if (s > 0.08 && s < 0.14 && Math.floorMod(alongI, 12) == 6) {
            v.set(i, 0, j, MOSS);
            v.set(i, 1, j, AZALEA_LEAVES);
            v.set(i, 2, j, AZALEA_LEAVES);
            return;
        }
        // 가운데 출발 안내판 (매달림)
        if (Math.abs(Math.toDegrees(ps)) < 1.2 && s > 0.2 && s < 0.26) {
            for (int y = 7; y <= 10; y++) {
                v.set(i, y, j, (y == 8 || y == 9) && Math.floorMod(alongI, 2) == 0 ? YELLOW_CONCRETE : BLACK_CONCRETE);
            }
            v.fill(i, 11, j, i, ceiling, j, IRON_BARS);
        }
    }

    // ------------------------------------------------------------------ 탑승교 (장식: 3층 높이 통로와 받침 기둥)

    private void bridges() {
        for (double g : gates) {
            double ang = Math.toRadians(g);
            // 건물 비행기 쪽 벽에서 활주로 쪽으로 14칸
            for (double rr = rOut - 1; rr <= rOut + 13; rr += 0.4) {
                for (double side = -1.5; side <= 1.5; side += 0.4) {
                    double a = ca + rr * Math.sin(ang) + side * Math.cos(ang);
                    double b = cb - rr * Math.cos(ang) + side * Math.sin(ang);
                    int i = (int) Math.floor(a), j = (int) Math.floor(b);
                    boolean wall = Math.abs(side) > 1.1;
                    v.set(i, 3, j, LIGHT_GRAY_CONCRETE);
                    v.set(i, 7, j, LIGHT_GRAY_CONCRETE);
                    for (int y = 4; y <= 6; y++) {
                        v.set(i, y, j, wall ? (y == 5 ? GRAY_GLASS : LIGHT_GRAY_CONCRETE) : (rr < rOut ? null : AIR));
                    }
                }
                // 받침 기둥 (끝 쪽)
                if (Math.abs(rr - (rOut + 10)) < 0.2) {
                    double a = ca + rr * Math.sin(ang), b = cb - rr * Math.cos(ang);
                    v.fill((int) Math.floor(a), 0, (int) Math.floor(b), (int) Math.floor(a), 2, (int) Math.floor(b), GRAY_CONCRETE);
                }
            }
        }
    }
}
