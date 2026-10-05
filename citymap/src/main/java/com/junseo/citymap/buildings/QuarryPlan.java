package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 북한산 채석장 (광부 일터). 산속 평평한 터(채석장 거점) 둘레를 계단식 채굴면(높이 10, 폭 6 단)으로 깎아 낸
 * 노천 채석장과, 남쪽 능선을 깎아 지나 골짜기를 따라 도시까지 내려가는 시멘트 포장 진입로.
 * <ul>
 *   <li>채굴면: 바닥 둘레 반지름 R 부터 단마다 10씩 올라감 (최대 9단). 북·동쪽이 주 채굴면, 남서쪽은 사무동·주차장 터라 넓게 깎음</li>
 *   <li>운반로: 바닥에서 채굴면을 따라 시계 방향으로 감아 오르는 경사로 (경사 1/4, 3단까지)</li>
 *   <li>바닥(야드): 1차 파쇄기와 덤프 투입구·투입 경사로, 컨베이어, 선별탑, 골재·모래 야적 더미, 계근대와 계근실,
 *       컨테이너 현장사무실(2층), 광물 거래소(2층), 주차장. 중장비·차량 모형은 두지 않음 (실제 탈것은 따로)</li>
 * </ul>
 * 바닥 높이는 설계도의 채석장 평지 높이(flats)를 씁니다. 거점은 바닥 한가운데에 열어 둡니다.
 */
final class QuarryPlan {

    static List<Placement> plan(CityTerrain t) {
        Layout.Hub hub = Plans.hub(t, "quarry");
        if (hub == null) {
            return List.of();
        }
        Site s = new Site(t, (int) Math.floor(hub.x()), (int) Math.floor(hub.z()));
        Random rnd = Plans.random(t, "quarry");
        List<Placement> out = new ArrayList<>();
        int x0 = s.fx - 80, z0 = s.fz - 92, x1 = s.fx + 95, z1 = s.fz + 70;
        Ground g = Ground.rect(t, x0, z0, x1, z1, "south");
        long seed = rnd.nextLong();
        out.add(Placement.rect("북한산 채석장", "quarry", x0, z0, x1, z1, "south", (w, d) -> s.yardOutline(x0, z0),
                (w, d) -> Quarry.pit(s, g, x0, z0, w, d, new Random(seed))));
        // 진입로: 구간마다 따로 (상자가 길게 늘어져 동네 땅을 막지 않게)
        Road road = new Road(s);
        int[][] parts = {{0, 3}, {3, road.pts.length - 1}};
        for (int[] part : parts) {
            double[] b = road.bounds(part[0], part[1], Road.MARGIN + 4);
            int rx0 = (int) Math.floor(b[0]), rz0 = (int) Math.floor(b[1]), rx1 = (int) Math.ceil(b[2]), rz1 = (int) Math.ceil(b[3]);
            Ground rg = Ground.rect(t, rx0, rz0, rx1, rz1, "south");
            out.add(Placement.rect("채석장 진입로", "quarry", rx0, rz0, rx1, rz1, "south", (w, d) -> road.footprint(part[0], part[1], rx0, rz0),
                    (w, d) -> road.build(rg, rx0, rz0, w, d, part[0], part[1])));
        }
        return out;
    }

    /** 채석장 자리와 채굴면 모양 (월드 좌표) */
    static final class Site {
        static final int H = 10, P = 6, KMAX = 9;
        final CityTerrain t;
        final int fx, fz;
        /** 바닥 서는 높이 */
        final int floor;
        /** 운반로 점들 {x, z, 서는 높이} */
        final List<double[]> ramp = new ArrayList<>();

        Site(CityTerrain t, int fx, int fz) {
            this.t = t;
            this.fx = fx;
            this.fz = fz;
            int f = t.column(fx, fz).mountainHeight;
            for (Layout.Mountain m : t.layout().mountains()) {
                for (Layout.Flat fl : m.flats()) {
                    if (Math.hypot(fl.x() - fx, fl.z() - fz) < 3) {
                        f = (int) Math.round(fl.height());
                    }
                }
            }
            floor = f;
            // 운반로: 북서쪽(-135°)에서 시계 방향으로, 단의 가운데를 지나며 1/4 경사로 3단까지
            double th = Math.toRadians(-135);
            for (double h = floor; h <= floor + 3 * H + 0.01; h += 0.25) {
                double r = 32 + (h - floor) * P / H - 2.5;
                ramp.add(new double[]{fx + r * Math.cos(th), fz + r * Math.sin(th), h});
                th += 4 * 0.25 / r;
            }
            // 끝: 3단 위로 6칸 평평하게
            double[] last = ramp.get(ramp.size() - 1);
            for (int k = 1; k <= 24; k++) {
                double r = 32 + 3 * P - 2.5;
                th += 0.25 / r;
                ramp.add(new double[]{fx + r * Math.cos(th), fz + r * Math.sin(th), last[2]});
            }
        }

        /** 바닥 둘레 반지름 (방향 θ 도) */
        static double radius(double deg) {
            if (deg >= -140 && deg <= 30) {
                return 32;
            }
            if (deg > 30 && deg < 70) {
                return 32 + (deg - 30) / 40 * 8;
            }
            if (deg > -160 && deg < -140) {
                return 40 - (deg + 160) / 20 * 8;
            }
            return 40;
        }

        /** 계단식 채굴면의 서는 높이 (깎지 않는 곳은 MAX) */
        int benchStand(int x, int z) {
            double dx = x - fx, dz = z - fz;
            double r = Math.hypot(dx, dz), R = radius(Math.toDegrees(Math.atan2(dz, dx)));
            if (r <= R) {
                return floor;
            }
            int k = (int) Math.floor((r - R) / P) + 1;
            return k > KMAX ? Integer.MAX_VALUE : floor + H * k;
        }

        /** 깎은 뒤 채석장 땅 서는 높이 (운반로 제외) */
        int pitStand(int x, int z) {
            int mh = t.column(x, z).mountainHeight;
            return Math.min(mh, benchStand(x, z));
        }

        /** 운반로 위면 서는 높이, 아니면 NaN */
        double rampStand(int x, int z) {
            double px = x + 0.5, pz = z + 0.5;
            double best = 3.3 * 3.3, h = Double.NaN;
            for (double[] p : ramp) {
                double dd = (p[0] + 0.5 - px) * (p[0] + 0.5 - px) + (p[1] + 0.5 - pz) * (p[1] + 0.5 - pz);
                if (dd <= best) {
                    best = dd;
                    h = p[2];
                }
            }
            return h;
        }

        /** 바닥 안인지 */
        boolean inYard(int x, int z) {
            double dx = x - fx, dz = z - fz;
            return Math.hypot(dx, dz) <= radius(Math.toDegrees(Math.atan2(dz, dx)));
        }

        /** 미니맵에 그릴 모양: 바닥 둘레 (건물 좌표) */
        List<double[]> yardOutline(int x0, int z0) {
            List<double[]> p = new ArrayList<>();
            for (int k = 0; k < 48; k++) {
                double a = 2 * Math.PI * k / 48 - Math.PI;
                double r = radius(Math.toDegrees(a));
                p.add(new double[]{fx + 0.5 + r * Math.cos(a) - x0, fz + 0.5 + r * Math.sin(a) - z0});
            }
            return p;
        }
    }

    /**
     * 진입로: 바닥 남쪽에서 남쪽 능선을 깎아 넘고 골짜기를 따라 도시 쪽 큰길(북부대로)까지.
     * 높이는 바닥에서 1/5 로 오르다가 땅을 만나면 땅을 따라 내려가고, 한 칸에 0.45 넘게 바뀌지 않게 다듬습니다
     * (반 블록 계단처럼 걸어서 오르내림). 길 폭 7, 바깥쪽은 깎은 비탈(바위) 또는 쌓은 비탈.
     */
    static final class Road {
        static final double HALF = 3.5;
        static final int MARGIN = 20;
        final Site s;
        final double[][] pts;
        /** 꺾인 점마다 누적 길이 */
        final double[] acc;
        /** 1칸 간격 높이 */
        final double[] prof;

        Road(Site s) {
            this.s = s;
            int fx = s.fx, fz = s.fz;
            // 끝점: 남쪽으로 가다가 큰길(인도) 바로 앞에서 멈춤
            int ex = fx - 35, ez = fz + 200;
            for (int k = 0; k < 80 && !s.t.column(ex, ez + 1).isRoad(); k++) {
                ez++;
            }
            double[][] p = {{fx - 10, fz + 28}, {fx - 17, fz + 46}, {fx - 30, fz + 65}, {fx - 30, fz + 150},
                    {fx - 35, fz + 178}, {ex, ez}};
            pts = p;
            acc = new double[p.length];
            for (int k = 1; k < p.length; k++) {
                acc[k] = acc[k - 1] + Math.hypot(p[k][0] - p[k - 1][0], p[k][1] - p[k - 1][1]);
            }
            int n = (int) Math.ceil(acc[p.length - 1]) + 1;
            double[] terr = new double[n];
            for (int k = 0; k < n; k++) {
                double[] q = at(k);
                terr[k] = s.t.column((int) Math.floor(q[0]), (int) Math.floor(q[1])).mountainHeight;
            }
            double[] h = new double[n];
            for (int k = 0; k < n; k++) {
                double sum = 0;
                int c = 0;
                for (int m = Math.max(0, k - 8); m <= Math.min(n - 1, k + 8); m++) {
                    sum += terr[m];
                    c++;
                }
                h[k] = Math.min(sum / c, s.floor + k / 5.0);
            }
            h[n - 1] = Math.min(h[n - 1], terr[n - 1]);
            for (int k = n - 2; k >= 0; k--) {
                h[k] = Math.min(h[k], h[k + 1] + 0.45);
            }
            for (int k = 1; k < n; k++) {
                h[k] = Math.min(h[k], h[k - 1] + 0.45);
            }
            prof = h;
        }

        /** 길이 s 지점 {x, z} */
        double[] at(double sAlong) {
            for (int k = 1; k < pts.length; k++) {
                if (sAlong <= acc[k] || k == pts.length - 1) {
                    double tt = Math.min(1, (sAlong - acc[k - 1]) / (acc[k] - acc[k - 1]));
                    return new double[]{pts[k - 1][0] + (pts[k][0] - pts[k - 1][0]) * tt, pts[k - 1][1] + (pts[k][1] - pts[k - 1][1]) * tt};
                }
            }
            return pts[pts.length - 1];
        }

        double height(double sAlong) {
            int k = (int) Math.floor(sAlong);
            if (k >= prof.length - 1) {
                return prof[prof.length - 1];
            }
            if (k < 0) {
                return prof[0];
            }
            double f = sAlong - k;
            return prof[k] * (1 - f) + prof[k + 1] * f;
        }

        /** 가장 가까운 길 가운데 점: {거리, 누적 길이, 구간 번호} */
        double[] nearest(double x, double z) {
            double[] best = {Double.MAX_VALUE, 0, 0};
            for (int k = 1; k < pts.length; k++) {
                double ax = pts[k - 1][0], az = pts[k - 1][1], bx = pts[k][0], bz = pts[k][1];
                double dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
                double tt = Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / len2));
                double d = Math.hypot(x - (ax + tt * dx), z - (az + tt * dz));
                if (d < best[0]) {
                    best = new double[]{d, acc[k - 1] + tt * Math.sqrt(len2), k - 1};
                }
            }
            return best;
        }

        double[] bounds(int k0, int k1, int margin) {
            double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int k = k0; k <= k1; k++) {
                b[0] = Math.min(b[0], pts[k][0] - margin);
                b[1] = Math.min(b[1], pts[k][1] - margin);
                b[2] = Math.max(b[2], pts[k][0] + margin);
                b[3] = Math.max(b[3], pts[k][1] + margin);
            }
            return b;
        }

        /** 미니맵용: 길 띠 (구간 k0..k1, 건물 좌표) */
        List<double[]> footprint(int k0, int k1, int x0, int z0) {
            List<double[]> left = new ArrayList<>(), right = new ArrayList<>();
            for (int k = k0; k <= k1; k++) {
                int a = Math.max(k0, k - 1), b = Math.min(k1, k + 1);
                double dx = pts[b][0] - pts[a][0], dz = pts[b][1] - pts[a][1], l = Math.hypot(dx, dz);
                double nx = -dz / l * HALF, nz = dx / l * HALF;
                left.add(new double[]{pts[k][0] + 0.5 + nx - x0, pts[k][1] + 0.5 + nz - z0});
                right.add(0, new double[]{pts[k][0] + 0.5 - nx - x0, pts[k][1] + 0.5 - nz - z0});
            }
            left.addAll(right);
            return left;
        }

        /** 구간 k0..k1 에 가장 가까운 칸만 그림 (이웃 구간과 겹치지 않게) */
        Voxels build(Ground g, int x0, int z0, int w, int d, int k0, int k1) {
            double[] hs = new double[w * d];
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            byte[] kind = new byte[w * d];
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    int x = x0 + i, z = z0 + j;
                    double[] nr = nearest(x + 0.5, z + 0.5);
                    int seg = (int) nr[2];
                    if (seg < k0 || seg >= k1 || nr[0] > HALF + MARGIN || s.t.column(x, z).isRoad()) {
                        continue;
                    }
                    double h = height(nr[1]);
                    int ground = groundStand(x, z);
                    double target;
                    if (nr[0] <= HALF) {
                        target = h;
                        kind[j * w + i] = 1;
                    } else if (ground > h + (nr[0] - HALF) * 1.6) {
                        target = h + (nr[0] - HALF) * 1.6;   // 깎은 바위 비탈
                        kind[j * w + i] = 2;
                    } else if (ground < h - (nr[0] - HALF)) {
                        target = h - (nr[0] - HALF);         // 쌓은 비탈
                        kind[j * w + i] = 3;
                    } else {
                        continue;
                    }
                    hs[j * w + i] = target;
                    lo = Math.min(lo, Math.min(ground, (int) Math.floor(target)) - 2);
                    hi = Math.max(hi, Math.max(s.t.column(x, z).mountainHeight, (int) Math.ceil(target)) + 1);
                }
            }
            if (lo == Integer.MAX_VALUE) {
                return new Voxels(w, d, 0, 0);
            }
            Voxels v = new Voxels(w, d, lo, hi);
            Block road = Block.of("smooth_stone", 0x9E9E9E);
            Block roadSlab = Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E);
            Block bank = Block.of("coarse_dirt", 0x77563B);
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    byte k = kind[j * w + i];
                    if (k == 0) {
                        continue;
                    }
                    int x = x0 + i, z = z0 + j;
                    int ground = groundStand(x, z);
                    int old = t().column(x, z).mountainHeight - 1;
                    double target = hs[j * w + i];
                    int full = (int) Math.floor(target) - 1;
                    boolean slab = target - Math.floor(target) >= 0.5;
                    if (k != 1) {
                        // 비탈은 칸 단위로
                        full = (int) Math.round(target) - 1;
                        slab = false;
                    }
                    for (int y = full + 1; y <= Math.max(old, ground - 1); y++) {
                        v.set(i, y, j, AIR);
                    }
                    for (int y = ground; y <= full; y++) {
                        v.set(i, y, j, k == 2 ? Quarry.rock(x, y, z) : k == 3 ? bank : STONE);
                    }
                    if (k == 1) {
                        v.set(i, full, j, road);
                        if (slab) {
                            v.set(i, full + 1, j, roadSlab);
                        }
                    } else if (k == 2) {
                        v.set(i, full, j, Quarry.rock(x, full, z));
                    } else {
                        v.set(i, full, j, Block.of("grass_block[snowy=false]", 0x7CBD6B));
                    }
                }
            }
            // 깎아서 드러난 옆면은 바위로 (흙이 드러나지 않게)
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    if (kind[j * w + i] == 0 && !touches(kind, w, d, i, j)) {
                        continue;
                    }
                    int x = x0 + i, z = z0 + j;
                    int own = surface(kind, hs, w, i, j, x, z);
                    int low = own;
                    for (int[] o : nb) {
                        int ni = i + o[0], nj = j + o[1];
                        if (ni >= 0 && nj >= 0 && ni < w && nj < d) {
                            low = Math.min(low, surface(kind, hs, w, ni, nj, x0 + ni, z0 + nj));
                        }
                    }
                    for (int y = low + 1; y < own; y++) {
                        Block b = v.get(i, y, j);
                        if (b == null || !b.isAir()) {
                            v.set(i, y, j, kind[j * w + i] == 3 ? Block.of("coarse_dirt", 0x77563B) : Quarry.rock(x, y, z));
                        }
                    }
                }
            }
            return v;
        }

        private static boolean touches(byte[] kind, int w, int d, int i, int j) {
            return (i > 0 && kind[j * w + i - 1] != 0) || (i + 1 < w && kind[j * w + i + 1] != 0)
                    || (j > 0 && kind[(j - 1) * w + i] != 0) || (j + 1 < d && kind[(j + 1) * w + i] != 0);
        }

        /** 칸의 맨 위 단단한 블록 y (이 길이 고친 곳은 고친 높이) */
        private int surface(byte[] kind, double[] hs, int w, int i, int j, int x, int z) {
            int k = j * w + i;
            if (kind[k] == 0) {
                return groundStand(x, z) - 1;
            }
            return kind[k] == 1 ? (int) Math.floor(hs[k]) - 1 : (int) Math.round(hs[k]) - 1;
        }

        /** 이 칸의 (채석장 채굴면까지 깎은 뒤) 땅 서는 높이 */
        int groundStand(int x, int z) {
            double dx = x - s.fx, dz = z - s.fz;
            if (Math.hypot(dx, dz) < 100) {
                return s.pitStand(x, z);
            }
            return t().column(x, z).mountainHeight;
        }

        CityTerrain t() {
            return s.t;
        }
    }

    private QuarryPlan() {
    }
}
