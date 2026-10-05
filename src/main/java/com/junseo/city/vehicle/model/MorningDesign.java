package com.junseo.city.vehicle.model;

import static com.junseo.city.vehicle.model.Tex.inPoly;
import static com.junseo.city.vehicle.model.Tex.mix;
import static com.junseo.city.vehicle.model.Tex.rgb;
import static com.junseo.city.vehicle.model.Tex.roundRect;
import static com.junseo.city.vehicle.model.Tex.shade;
import static com.junseo.city.vehicle.model.Tex.smooth;

/**
 * 국산 경차 (2011~2015 모닝 같은 5도어 해치백), 흰색. 길이 3.6 m, 폭 1.6 m, 높이 1.49 m, 축간 2.39 m, 14인치 휠.
 * 상표·로고는 넣지 않고 가운데에 이름 없는 은색 타원 엠블럼만 둡니다.
 */
public final class MorningDesign implements CarDesign {
    static final double L = 3.595, W = 1.595, ZF = L / 2, ZR = -L / 2, HW = W / 2;
    static final double FRONT_AXLE = ZF - 0.62, REAR_AXLE = FRONT_AXLE - 2.385;
    static final double R = 0.277, RIM = 0.1778;

    static final int PAINT = rgb(236, 236, 229);
    static final int GLASS = rgb(30, 35, 42);
    static final int TRIM = rgb(26, 26, 28);
    static final int DARK = rgb(34, 34, 36);
    static final int CHROME = rgb(205, 208, 212);
    static final int LENS = rgb(214, 222, 230);
    static final int RED = rgb(168, 22, 30);
    static final int AMBER = rgb(232, 146, 40);
    static final int PLATE = rgb(242, 242, 240);

    // 옆에서 본 윤곽 (z, y): 뒤 → 앞
    static final double[] TOP_Z = {ZR, -1.78, -1.74, -1.68, -1.58, -1.45, -1.30, -0.60, -0.05, 0.12, 0.25, 0.45, 0.65, 0.85, 1.00, 1.08, 1.30, 1.50, 1.64, 1.73, 1.775, ZF};
    static final double[] TOP_Y = {0.86, 0.98, 1.12, 1.25, 1.36, 1.43, 1.465, 1.485, 1.475, 1.455, 1.40, 1.29, 1.175, 1.06, 0.975, 0.94, 0.895, 0.85, 0.80, 0.74, 0.67, 0.58};
    static final double[] BOT_Z = {ZR, -1.78, -1.74, -1.62, -1.45, 1.45, 1.64, 1.74, 1.785, ZF};
    static final double[] BOT_Y = {0.40, 0.30, 0.22, 0.17, 0.155, 0.155, 0.18, 0.24, 0.31, 0.40};
    /** 앞유리 아래·위 끝 (z, y) */
    static final double WS_Z0 = 1.05, WS_Y0 = 0.94, WS_Z1 = 0.12, WS_Y1 = 1.455;

    @Override
    public String id() {
        return "morning";
    }

    @Override
    public double length() {
        return L;
    }

    @Override
    public double width() {
        return W;
    }

    @Override
    public double wheelRadius() {
        return R;
    }

    @Override
    public double tireWidth() {
        return 0.165;
    }

    @Override
    public double track() {
        return 1.42;
    }

    @Override
    public double frontAxle() {
        return FRONT_AXLE;
    }

    @Override
    public double rearAxle() {
        return REAR_AXLE;
    }

    @Override
    public double archRadius() {
        return 0.345;
    }

    static double lerp(double[] xs, double[] ys, double x) {
        if (x <= xs[0]) {
            return ys[0];
        }
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) {
                double t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return ys[i - 1] + (ys[i] - ys[i - 1]) * t;
            }
        }
        return ys[ys.length - 1];
    }

    @Override
    public double top(double z) {
        return lerp(TOP_Z, TOP_Y, z);
    }

    @Override
    public double bottom(double z) {
        return lerp(BOT_Z, BOT_Y, z);
    }

    @Override
    public double halfWidth(double z) {
        double rf = 0.42, rr = 0.24, hw = HW;
        double df = z - (ZF - rf), dr = (ZR + rr) - z;
        if (df > 0) {
            hw -= rf - Math.sqrt(Math.max(0, rf * rf - df * df));
        }
        if (dr > 0) {
            hw -= rr - Math.sqrt(Math.max(0, rr * rr - dr * dr));
        }
        return hw;
    }

    @Override
    public double belt(double z) {
        return 0.88 + Math.max(0, Math.min(2.45, 1.0 - z)) * 0.045;
    }

    @Override
    public double[][] layers(double z) {
        double b = bottom(z), t = top(z), hw = halfWidth(z), belt = belt(z);
        double[][] spec = {
                {b, 0.34, hw - 0.03},                 // 문턱 (안으로 조금 들어감)
                {0.34, belt - 0.07, hw},              // 차체
                {belt - 0.07, belt, hw - 0.012},      // 어깨
                {belt, belt + 0.15, hw * 0.905},      // 유리 부분 (위로 갈수록 좁아짐)
                {belt + 0.15, belt + 0.29, hw * 0.85},
                {belt + 0.29, belt + 0.42, hw * 0.795},
                {belt + 0.42, 9, hw * 0.74},
        };
        double[][] out = new double[spec.length][];
        for (int i = 0; i < spec.length; i++) {
            out[i] = new double[]{Math.max(b, spec[i][0]), Math.min(t, spec[i][1]), spec[i][2]};
        }
        return out;
    }

    /** 위 윤곽의 마디마다 판 하나: 앞유리 (지붕 앞 ~ 와이퍼), 보닛 (와이퍼 ~ 앞 끝 바로 뒤), 뒷유리 */
    @Override
    public double[][] skins() {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int i = 1; i < TOP_Z.length; i++) {
            double z0 = TOP_Z[i - 1], z1 = TOP_Z[i];
            boolean hatch = z1 <= -1.30 && z0 >= -1.78;
            boolean front = z0 >= 0.12 && z1 <= 1.775;
            if (!hatch && !front) {
                continue;
            }
            double hw = 9;
            for (double z = z0; z <= z1 + 1e-9; z += 0.01) {
                hw = Math.min(hw, hwAt(z, top(z) - 0.005));
            }
            out.add(new double[]{z0, TOP_Y[i - 1], z1, TOP_Y[i], hw});
        }
        return out.toArray(new double[0][]);
    }

    /** 바퀴집 위 끝 높이 (바퀴집 밖이면 0) */
    double arch(double z) {
        double a = 0;
        for (double axle : new double[]{FRONT_AXLE, REAR_AXLE}) {
            double dz = z - axle;
            if (Math.abs(dz) < archRadius()) {
                a = Math.max(a, R + Math.sqrt(archRadius() * archRadius() - dz * dz));
            }
        }
        return a;
    }

    /** 앞 모서리 (반지름 0.42), 뒤 모서리 (0.24) 를 15° 씩 */
    @Override
    public double[][] cornerSkins() {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        double[][] corners = {{ZF - 0.42, HW - 0.42, 0.42, 1}, {ZR + 0.24, HW - 0.24, 0.24, -1}};
        for (double[] c : corners) {
            for (int k = 0; k < 6; k++) {
                double a0 = Math.toRadians(k * 15), a1 = Math.toRadians((k + 1) * 15);
                double za = c[0] + c[3] * c[2] * Math.sin(a0), xa = c[1] + c[2] * Math.cos(a0);
                double zb = c[0] + c[3] * c[2] * Math.sin(a1), xb = c[1] + c[2] * Math.cos(a1);
                double lo = 0, hi = 9;
                for (double t = 0; t <= 1.0001; t += 0.1) {
                    double z = za + (zb - za) * t;
                    lo = Math.max(lo, Math.max(bottom(z), arch(z)));
                    hi = Math.min(hi, Math.min(top(z), belt(z)));
                }
                if (hi - lo > 0.03) {
                    out.add(new double[]{za, xa, zb, xb, lo, hi});
                }
            }
        }
        return out.toArray(new double[0][]);
    }

    @Override
    public double[] floor() {
        return new double[]{0.55, 0.17, 0.40, -1.55, 1.55};
    }

    @Override
    public double[] mirror() {
        return new double[]{0.70, 0.97, 0.86, 0.88, 1.09, 0.96};
    }

    @Override
    public double[] plates() {
        return new double[]{0.33, ZF, 0.68, ZR + 0.03};
    }

    // ------------------------------------------------------------------ 칠

    /** 흰 차체: 아래로 갈수록 조금 어둡고, 창 밑 어깨는 밝게 */
    private int body(double y, double belt) {
        double f = 0.90 + 0.10 * smooth(0.15, 0.8, y);
        if (y > belt - 0.075 && y < belt - 0.02) {
            f += 0.03;
        }
        return shade(PAINT, f);
    }

    /** 유리: 위가 밝고 비스듬한 반사 띠 */
    private static int glass(double a, double b, double h) {
        int c = mix(GLASS, rgb(70, 82, 96), smooth(0.2, 1, h) * 0.6);
        double band = ((a * 0.9 + b * 1.4) % 0.9 + 0.9) % 0.9;
        if (band < 0.07) {
            c = mix(c, rgb(150, 160, 172), 0.35);
        }
        return c;
    }

    private static double wsZ(double y) {
        return WS_Z0 + (y - WS_Y0) / (WS_Y1 - WS_Y0) * (WS_Z1 - WS_Z0);
    }

    /** z 에서 높이 y 가 든 층의 반폭 */
    double hwAt(double z, double y) {
        for (double[] l : layers(z)) {
            if (y >= l[0] - 1e-9 && y <= l[1] + 1e-9) {
                return l[2];
            }
        }
        return halfWidth(z);
    }

    @Override
    public int side(double z, double y) {
        double belt = belt(z), t = top(z);
        // 바퀴집 테두리 그림자
        for (double axle : new double[]{FRONT_AXLE, REAR_AXLE}) {
            double dd = Math.hypot(z - axle, y - R);
            if (dd < archRadius() + 0.018 && y > 0.12) {
                return DARK;
            }
        }
        // 앞뒤 둥근 모서리: 앞·뒤 그림을 그 자리(x = 반폭)에서 가져와 이음매 없이
        if (z > ZF - 0.42 && y < belt) {
            return front(hwAt(z, y), y);
        }
        if (z < ZR + 0.24) {
            return rear(hwAt(z, y), y);
        }
        // A 필러 (검정)
        if (y > belt && z >= wsZ(y) - 0.10 && z < wsZ(y) + 0.05) {
            return TRIM;
        }
        // 창: 앞문 · B 필러 · 뒷문 · 뒤쪽 작은 창
        double glassTop = t - 0.06, glassBot = belt + 0.02;
        if (y > glassBot - 0.015 && y < glassTop + 0.015 && z < wsZ(y) - 0.08 && z > -1.38) {
            boolean inGlass = y > glassBot && y < glassTop && z < wsZ(y) - 0.10;
            boolean pillar = (z > -0.37 && z < -0.29) || (z > -1.08 && z < -1.02);
            if (inGlass && !pillar && z > -1.36) {
                return glass(z, y, (y - glassBot) / Math.max(0.05, glassTop - glassBot));
            }
            return TRIM;
        }
        // 문틈·손잡이
        double seam = 0.012;
        boolean seamLine = (Math.abs(z - 0.80) < seam / 2 && y < belt && y > 0.25)
                || (Math.abs(z + 0.33) < seam / 2 && y < belt && y > 0.22)
                || (Math.abs(z + 1.05) < seam / 2 && y < belt && Math.hypot(z - REAR_AXLE, y - R) > archRadius() + 0.04);
        if (seamLine) {
            return shade(PAINT, 0.55);
        }
        for (double hz : new double[]{-0.14, -0.92}) {
            double dd = roundRect(z, y, hz, 0.815, 0.065, 0.014, 0.012);
            if (dd < 0) {
                return dd > -0.004 ? shade(PAINT, 0.62) : shade(PAINT, 0.86);
            }
        }
        // 옆 주름선 (뒤로 갈수록 조금 올라감)
        double crease = 0.70 + (1.0 - z) * 0.03;
        if (y > crease - 0.008 && y < crease) {
            return shade(PAINT, 0.80);
        }
        if (y >= crease && y < crease + 0.01) {
            return shade(PAINT, 1.03);
        }
        // 아래 문턱
        if (y < 0.20) {
            return mix(DARK, rgb(60, 60, 62), (y - 0.15) * 10);
        }
        return body(y, belt);
    }

    @Override
    public int front(double x, double y) {
        double ax = Math.abs(x);
        // 앞유리 계단의 세운 면: 가운데 유리, 가장자리 A 필러
        if (y > WS_Y0) {
            double zw = wsZ(Math.min(y, WS_Y1));
            return ax < hwAt(zw, y) - 0.07 ? glass(x, y, (y - WS_Y0) / (WS_Y1 - WS_Y0)) : TRIM;
        }
        // 전조등
        double[] hx = {0.29, 0.62, 0.76, 0.795, 0.74, 0.55, 0.36, 0.28}, hy = {0.79, 0.80, 0.76, 0.68, 0.62, 0.635, 0.70, 0.765};
        if (inPoly(ax, y, hx, hy)) {
            double ring = Math.hypot(ax - 0.555, y - 0.715);
            if (ring < 0.052) {
                return ring > 0.040 ? CHROME : ring > 0.022 ? rgb(70, 76, 84) : rgb(240, 244, 250);
            }
            double ring2 = Math.hypot(ax - 0.405, y - 0.735);
            if (ring2 < 0.034) {
                return ring2 > 0.026 ? CHROME : rgb(90, 96, 104);
            }
            if (ax > 0.66 && y < 0.70) {
                return AMBER;
            }
            return mix(rgb(150, 160, 172), LENS, (y - 0.62) * 5);
        }
        if (inPoly(ax, y, new double[]{0.27, 0.63, 0.78, 0.81, 0.75, 0.54, 0.35, 0.26}, new double[]{0.80, 0.815, 0.77, 0.68, 0.605, 0.62, 0.69, 0.76})) {
            return TRIM;    // 전조등 테두리
        }
        // 위 그릴 (호랑이 코) + 엠블럼
        double gTop = 0.745 - 0.018 * (1 - ax / 0.21);
        if (ax < 0.215 && y > 0.66 && y < gTop + 0.008) {
            if (ax > 0.205 || y < 0.668 || y > gTop) {
                return CHROME;
            }
            double em = Math.hypot(x / 0.055, (y - 0.705) / 0.028);
            if (em < 1) {
                return em > 0.78 ? rgb(120, 124, 130) : CHROME;
            }
            boolean cell = ((int) Math.floor(x * 70) + (int) Math.floor(y * 70)) % 2 == 0;
            return cell ? rgb(20, 20, 22) : rgb(58, 60, 64);
        }
        // 번호판
        double pd = roundRect(x, y, 0, 0.33, 0.26, 0.055, 0.01);
        if (pd < 0) {
            return pd > -0.007 ? rgb(40, 40, 42) : PLATE;
        }
        // 안개등 자리
        double fog = Math.hypot((ax - 0.62) / 0.12, (y - 0.32) / 0.075);
        if (fog < 1) {
            return Math.hypot((ax - 0.62) / 0.07, (y - 0.32) / 0.044) < 1 ? rgb(248, 214, 150) : TRIM;
        }
        // 아래 공기 흡입구 (검은 그물)
        double gi = roundRect(x, y, 0, 0.30, 0.56, 0.11, 0.05);
        if (gi < 0) {
            boolean cell = ((int) Math.floor(x * 50) + (int) Math.floor(y * 50)) % 2 == 0;
            return gi > -0.008 ? rgb(48, 48, 50) : cell ? rgb(22, 22, 24) : rgb(44, 45, 48);
        }
        if (y < 0.20) {
            return DARK;
        }
        if (y > 0.795) {
            return shade(PAINT, 1.0);
        }
        if (Math.abs(y - 0.62) < 0.004 && ax < 0.7) {
            return shade(PAINT, 0.82);
        }
        return body(y, 0.9);
    }

    @Override
    public int rear(double x, double y) {
        double ax = Math.abs(x);
        // 뒷유리
        double gw = 0.57 - (y - 1.02) * 0.30;
        if (y > 1.00 && y < 1.40 && ax < gw + 0.025) {
            if (y > 1.02 && y < 1.375 && ax < gw) {
                return glass(x, y, (y - 1.02) / 0.36);
            }
            return TRIM;
        }
        // 보조 제동등
        if (ax < 0.12 && y > 1.40 && y < 1.425) {
            return RED;
        }
        // 뒷등
        double[] tx = {0.52, 0.72, 0.79, 0.80, 0.70, 0.58, 0.51}, ty = {1.10, 1.12, 1.04, 0.82, 0.80, 0.86, 0.98};
        if (inPoly(ax, y, tx, ty)) {
            if (ax > 0.61 && ax < 0.74 && y > 0.86 && y < 0.92) {
                return rgb(225, 228, 232);
            }
            double ring = Math.hypot(ax - 0.67, y - 1.00);
            return ring < 0.05 && ring > 0.035 ? shade(RED, 1.35) : y > 1.05 ? shade(RED, 1.2) : RED;
        }
        // 번호판 자리
        double pd = roundRect(x, y, 0, 0.68, 0.26, 0.055, 0.01);
        if (pd < 0) {
            return pd > -0.007 ? rgb(40, 40, 42) : PLATE;
        }
        if (roundRect(x, y, 0, 0.68, 0.30, 0.08, 0.02) < 0) {
            return shade(PAINT, 0.86);
        }
        double em = Math.hypot(x / 0.05, (y - 0.85) / 0.025);
        if (em < 1) {
            return CHROME;
        }
        // 반사판, 아래 검은 부분
        if (Math.abs(ax - 0.62) < 0.06 && Math.abs(y - 0.40) < 0.016) {
            return shade(RED, 0.8);
        }
        if (y < 0.30) {
            return DARK;
        }
        if (Math.abs(y - 0.57) < 0.004) {
            return shade(PAINT, 0.6);
        }
        return body(y, 0.95);
    }

    @Override
    public int top(double x, double z) {
        double ax = Math.abs(x);
        double[][] ls = layers(z);
        double[] topL = null;
        for (double[] l : ls) {
            if (l[1] - l[0] > 0.005) {
                topL = l;
            }
        }
        if (topL == null) {
            return PAINT;
        }
        if (ax > topL[2] + 1e-6) {
            // 아래층이 드러난 계단 (어깨·유리 부분 옆): 그 층 옆면 그림을 이어 씀
            for (double[] l : ls) {
                if (ax <= l[2] + 1e-6 && l[1] - l[0] > 0.005 && l[1] > belt(z) + 0.01) {
                    return side(z, l[1] - 0.012);
                }
            }
            return body(0.8, 0.9);
        }
        double hwTop = topL[2];
        // 앞유리
        if (z > WS_Z1 && z < WS_Z0) {
            if (ax < hwTop - 0.07) {
                double wz = 0.995 + (0.55 - Math.abs(ax - 0.30)) * 0.04;
                if (Math.abs(z - wz) < 0.008 && ax > 0.04 && ax < 0.62) {
                    return TRIM;    // 와이퍼
                }
                return glass(x, z, (z - WS_Z1) / (WS_Z0 - WS_Z1));
            }
            return TRIM;
        }
        if (z >= WS_Z0 && z < WS_Z0 + 0.05 && ax < hwTop * 0.9) {
            return DARK;   // 와이퍼 아래 검은 판
        }
        // 뒷유리 (지붕 끝 스포일러 아래)
        if (z < -1.38) {
            return ax < hwTop - 0.04 ? glass(x, z, (z + 1.80) / 0.42) : TRIM;
        }
        // 보닛 틈선
        if (z > WS_Z0 + 0.05 && Math.abs(ax - (hwTop - 0.06)) < 0.005) {
            return shade(PAINT, 0.7);
        }
        double f = z < WS_Z1 && z > -1.45 ? 1.0 - 0.04 * smooth(0.2, 0.6, ax) : 0.98;
        return shade(PAINT, f);
    }

    @Override
    public int wheel(double r, double th) {
        if (r > R) {
            return 0;
        }
        if (r > RIM + 0.012) {
            double ring = Math.abs(r - 0.245) < 0.004 ? 1.25 : 1.0;
            return shade(rgb(34, 34, 36), (0.85 + 0.25 * (r - RIM) / (R - RIM)) * ring);
        }
        if (r > RIM) {
            return rgb(196, 198, 202);
        }
        if (r < 0.05) {
            return r > 0.043 ? rgb(80, 82, 86) : r < 0.012 ? rgb(60, 62, 66) : rgb(206, 208, 212);
        }
        // 다섯 갈래 두 줄 스포크 (은색), 사이로 브레이크 디스크
        double s = Math.abs(Math.sin(5 * th));
        double split = Math.abs(Math.sin(5 * th + Math.PI / 2));
        boolean spoke = s > 0.55 && r > 0.045;
        if (spoke && split > 0.97 && r > 0.07) {
            return rgb(60, 62, 66);
        }
        if (spoke) {
            return shade(rgb(190, 192, 196), 0.85 + 0.25 * (1 - r / RIM));
        }
        return r < 0.14 ? rgb(92, 92, 94) : rgb(30, 30, 33);
    }

    @Override
    public int tread(double u, double v) {
        boolean groove = Math.abs(u - 0.33) < 0.03 || Math.abs(u - 0.67) < 0.03;
        boolean block = ((int) (v * 12)) % 2 == 0 && (u < 0.18 || u > 0.82);
        return groove ? rgb(16, 16, 17) : block ? rgb(26, 26, 28) : rgb(38, 38, 40);
    }

    @Override
    public int paint() {
        return PAINT;
    }

    @Override
    public int glass() {
        return GLASS;
    }

    @Override
    public int trim() {
        return TRIM;
    }

    @Override
    public int underbody() {
        return DARK;
    }
}
