package com.junseo.citymap.buildings;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 공항 여객 터미널 (인천공항 제1터미널 느낌).
 * 육지 쪽이 오목한 활 모양 건물에 물결치는 흰 지붕, 앞뒤 전면 유리벽, 육지 쪽 큰 차양(출국장 앞 도로),
 * 비행기 쪽에는 탑승교와 계류장(유도선)이 있습니다.
 * 안은 실제 터미널처럼 씁니다.
 * <ul>
 *   <li>1층 출국장 (육지 쪽): 안내 데스크, 체크인 카운터 섬, 둥근 기둥, 출발 안내판, 화장실, 편의점·카페·환전소, 화분</li>
 *   <li>보안 검색대를 지나 계단으로 2층 탑승층(서는 높이 5): 탑승구 의자, 탑승구 번호판, 면세점, 화장실, 탑승교와 이어짐</li>
 *   <li>탑승층 아래 1층(비행기 쪽, 층고 5): 입국장 수하물 수취대</li>
 * </ul>
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
    /** 탑승층 바닥 높이 (서는 높이 = 5, 1층 층고 5) */
    private static final int GATE_FLOOR = 4;
    /** 탑승층이 시작하는 깊이 비율 (육지 쪽 0 → 비행기 쪽 1) */
    private static final double GATE_S = 0.72;
    /** 탑승층 계단 자리 (호 길이) */
    private static final double[] STAIRS = {-68, 4, 76};

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
        t.amenities();
        t.v.connect();
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
                gateLevel(i, j, rr, along);
            }
        }
    }

    /** 탑승층: 비행기 쪽 띠에 바닥판, 안쪽 가장자리 유리 난간 (계단 자리만 트임), 탑승구 의자 */
    private void gateLevel(int i, int j, double rr, double along) {
        double rm = rIn + GATE_S * (rOut - rIn);
        if (rr < rm) {
            return;
        }
        v.set(i, GATE_FLOOR, j, SMOOTH_QUARTZ);
        boolean stairGap = false;
        for (double sa : STAIRS) {
            stairGap |= Math.abs(along - sa) < 2.2;
        }
        if (rr < rm + 1 && !stairGap) {
            v.set(i, GATE_FLOOR + 1, j, GLASS_PANE);
        }
        // 탑승구 대기 의자 줄 (창을 보고)
        int alongI = (int) Math.floor(along);
        double fromWall = rOut - rr;
        if ((Math.abs(fromWall - 4) < 0.5 || Math.abs(fromWall - 6.5) < 0.5) && Math.floorMod(alongI, 8) < 6) {
            v.set(i, GATE_FLOOR + 1, j, Blocks.stairs("dark_oak", "south", 0x432B14));
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
        if (s > 0.38 && s < 0.64 && Math.abs(off) < 2.5) {
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
        // 탑승층으로 오르는 계단 (육지 쪽에서 비행기 쪽으로 다섯 단)
        double rm = rIn + GATE_S * (rOut - rIn);
        for (double sa : STAIRS) {
            double off2 = Math.abs(along - sa);
            if (off2 < 2.2 && rr >= rm - 5 && rr < rm) {
                int k = (int) Math.floor(rr - (rm - 5)); // 0..4
                v.set(i, k, j, Blocks.stairs("polished_andesite", "north", 0x848685));
                for (int y = 0; y < k; y++) {
                    v.set(i, y, j, POLISHED_ANDESITE);
                }
                return;
            }
            // 보안 검색대: 계단 앞 양옆에 엑스레이 검색기 (가운데는 통로)
            if (rr >= rm - 9 && rr < rm - 7 && along - sa <= -2.2 && along - sa > -4.4) {
                v.set(i, 0, j, IRON_BLOCK);
                v.set(i, 1, j, Block.of("black_carpet", 0x141519));
                return;
            }
        }
        // 탑승층 아래 입국장: 수하물 수취대 (검은 벨트 고리)
        if (s > GATE_S + 0.03 && s < 0.95) {
            double beltAlong = Math.round(along / 30.0) * 30.0;
            double da = Math.abs(along - beltAlong), mid = 0.5 * (GATE_S + 0.03 + 0.95);
            boolean ring = da < 7 && Math.abs(s - mid) < 0.06 && (da > 5.5 || Math.abs(s - mid) > 0.035);
            if (ring) {
                v.set(i, 0, j, BLACK_CONCRETE);
                return;
            }
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

    // ------------------------------------------------------------------ 편의시설

    /** 호 길이 along, 중심 거리 rr 인 곳의 건물 좌표 {a, b} */
    private int[] at(double along, double rr) {
        double ps = along / rr;
        return new int[]{(int) Math.floor(ca + rr * Math.sin(ps)), (int) Math.floor(cb - rr * Math.cos(ps))};
    }

    /** 1층·탑승층 화장실, 안내 데스크, 편의점·카페·환전소, 면세점, 탑승구 번호판 */
    private void amenities() {
        double depth = rOut - rIn, rm = rIn + GATE_S * depth;
        // 화장실 (남녀 한 쌍, 문은 육지 쪽): 1층 육지 쪽 둘, 탑승층 비행기 쪽 둘
        for (double al : new double[]{-60, 60}) {
            restrooms(at(al, rIn + 0.17 * depth), 0, Floors.GROUND);
        }
        for (double al : new double[]{-42, 42}) {
            restrooms(at(al, rOut - 6.5), GATE_FLOOR + 1, Floors.GROUND);
        }
        // 안내 데스크 (가운데 육지 쪽)
        int[] c = at(0, rIn + 0.12 * depth);
        for (int da = -3; da <= 3; da++) {
            v.set(c[0] + da, 0, c[1], SMOOTH_QUARTZ);
        }
        v.set(c[0] - 3, 0, c[1] - 1, SMOOTH_QUARTZ);
        v.set(c[0] + 3, 0, c[1] - 1, SMOOTH_QUARTZ);
        hang(c, 4, Blocks.hangingSign("dark_oak", 0, "white", true, "", "안내", "Information"));
        // 1층 가게 (육지 쪽 기둥 줄 사이)
        kiosk(at(-30, rIn + 0.2 * depth), 0, "편의점", "convenience");
        kiosk(at(30, rIn + 0.2 * depth), 0, "카페", "cafe");
        kiosk(at(-84, rIn + 0.2 * depth), 0, "환전", "exchange");
        kiosk(at(84, rIn + 0.2 * depth), 0, "식당", "cafe");
        // 탑승층 면세점과 카페
        kiosk(at(-12, rm + 5), GATE_FLOOR + 1, "면세점", "dutyfree");
        kiosk(at(14, rm + 5), GATE_FLOOR + 1, "면세점", "dutyfree");
        kiosk(at(-88, rm + 5), GATE_FLOOR + 1, "카페", "cafe");
        // 탑승구 번호판 (탑승교 입구 위)
        for (int g = 0; g < gates.length; g++) {
            int[] p = at(Math.toRadians(gates[g]) * (rOut - 3), rOut - 3);
            hang(p, GATE_FLOOR + 5, Blocks.hangingSign("dark_oak", 0, "yellow", true, "", "탑승구 " + (g + 1), "Gate " + (g + 1)));
        }
        // 계단 아래 안내판
        for (double sa : STAIRS) {
            int[] p = at(sa, rm - 10);
            hang(p, 4, Blocks.hangingSign("dark_oak", 8, "white", true, "", "출국장", "Departures"));
        }
    }

    /** 지붕에서 사슬로 매단 표지판 */
    private void hang(int[] p, int y, Block sign) {
        v.set(p[0], y, p[1], sign);
        int roofY = (int) Math.floor(roofHeight(rho(p[0] + 0.5, p[1] + 0.5), psi(p[0] + 0.5, p[1] + 0.5)));
        for (int yy = y + 1; yy < roofY - 1 && yy < v.y0 + v.h; yy++) {
            Block b = v.get(p[0], yy, p[1]);
            if (b != null && !b.isAir()) {
                break;
            }
            v.set(p[0], yy, p[1], Block.of("iron_chain[axis=y,waterlogged=false]", 0x8F8F8F));
        }
    }

    /** 남녀 화장실 한 쌍 (가운데 c, 서는 높이 level). 천장을 덮은 상자 */
    private void restrooms(int[] c, int level, int height) {
        Frame f = new Frame(v, c[0] - 7, c[1] - 3, 0);
        f.fill(-1, level, -1, 15, level + height - 1, 6, AIR);
        Interior.restroom(f, 0, 0, 6, 5, level, height, "남자 화장실", 3);
        Interior.restroom(f, 8, 0, 14, 5, level, height, "여자 화장실", 11);
        f.fill(-1, level + height - 1, -1, 15, level + height - 1, 6, WHITE_CONCRETE);
    }

    /** 작은 가게: 앞 계산대, 뒤 진열대·주방, 위에 매단 간판 */
    private void kiosk(int[] c, int level, String name, String kind) {
        Frame f = new Frame(v, c[0] - 4, c[1] - 3, 0);
        f.fill(0, level, 0, 8, level + 3, 6, AIR);
        Block wall = WHITE_CONCRETE;
        f.fill(0, level, 0, 8, level + 2, 0, wall);
        f.fill(0, level, 0, 0, level + 2, 5, wall);
        f.fill(8, level, 0, 8, level + 2, 5, wall);
        f.fill(0, level + 3, 0, 8, level + 3, 5, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
        for (int a = 1; a <= 7; a++) {
            Block back = switch (kind) {
                case "cafe" -> a == 2 ? SMOKER : a == 4 ? CAULDRON : Furniture.COUNTER;
                case "exchange" -> Furniture.COUNTER;
                default -> (a & 1) == 0 ? Furniture.BOOKSHELF : BARREL;
            };
            f.set(a, level, 1, back);
            if (!kind.equals("exchange")) {
                f.set(a, level + 1, 1, kind.equals("cafe") ? AIR : back);
            }
        }
        f.fill(2, level, 4, 6, level, 4, Furniture.COUNTER);
        if (kind.equals("exchange")) {
            f.fill(2, level + 1, 4, 6, level + 1, 4, GLASS_PANE);
        }
        f.set(4, level + 2, 1, Interior.LIGHT);
        f.set(4, level + 2, 5, Blocks.hangingSign("birch", 0, "black", false, "", name));
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
                    if (rr < rOut - 1.6) {
                        continue; // 건물 안 (탑승층 바닥이 이어짐)
                    }
                    v.set(i, GATE_FLOOR, j, rr < rOut ? SMOOTH_QUARTZ : LIGHT_GRAY_CONCRETE);
                    v.set(i, GATE_FLOOR + 4, j, LIGHT_GRAY_CONCRETE);
                    for (int y = GATE_FLOOR + 1; y <= GATE_FLOOR + 3; y++) {
                        v.set(i, y, j, wall && rr >= rOut ? (y == GATE_FLOOR + 2 ? GRAY_GLASS : LIGHT_GRAY_CONCRETE) : AIR);
                    }
                }
                // 받침 기둥 (끝 쪽)
                if (Math.abs(rr - (rOut + 10)) < 0.2) {
                    double a = ca + rr * Math.sin(ang), b = cb - rr * Math.cos(ang);
                    v.fill((int) Math.floor(a), 0, (int) Math.floor(b), (int) Math.floor(a), GATE_FLOOR - 1, (int) Math.floor(b), GRAY_CONCRETE);
                }
            }
        }
    }
}
