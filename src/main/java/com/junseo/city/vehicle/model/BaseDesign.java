package com.junseo.city.vehicle.model;

import java.util.ArrayList;
import java.util.List;

import static com.junseo.city.vehicle.model.Tex.inPoly;
import static com.junseo.city.vehicle.model.Tex.mix;
import static com.junseo.city.vehicle.model.Tex.rgb;
import static com.junseo.city.vehicle.model.Tex.roundRect;
import static com.junseo.city.vehicle.model.Tex.shade;
import static com.junseo.city.vehicle.model.Tex.smooth;

/**
 * 차 모양·칠의 공통 틀. 차마다 크기·윤곽·창·등 모양 같은 값만 채우면 ({@link Designs}) 몸통 조각, 비스듬한 판,
 * 네 방향 그림을 같은 규칙으로 만듭니다. 도색(경찰·택시 등)은 {@link Livery} 로 마지막에 덧칠합니다.
 * <p>
 * 좌표: x 왼쪽(+), y 위, z 앞(+), 미터. 윤곽 배열은 뒤(ZR) → 앞(ZF) 순서.
 */
class BaseDesign implements CarDesign {
    /** 도색: 기본 그림 색 c 를 받아 덧칠한 색을 돌려줌 */
    interface Livery {
        /** right: 차 오른쪽 면 (글씨 방향이 반대) */
        default int side(BaseDesign d, double z, double y, int c, boolean right) {
            return c;
        }

        default int front(BaseDesign d, double x, double y, int c) {
            return c;
        }

        default int rear(BaseDesign d, double x, double y, int c) {
            return c;
        }

        default int top(BaseDesign d, double x, double z, int c) {
            return c;
        }
    }

    // ---- 이름·크기
    String id;
    double L, W, H, ZF, ZR, HW;
    double frontAxle, rearAxle, R, RIM, tireW, track, archR = 0.345;
    double cornerF = 0.42, cornerR = 0.24;
    // ---- 옆 윤곽
    double[] topZ, topY, botZ, botY;
    /** 앞유리 아래(카울) (wsZ0, wsY0), 위(지붕 앞) (wsZ1, wsY1) */
    double wsZ0, wsY0, wsZ1, wsY1;
    /** 뒷유리 위(지붕 끝) z, 아래 끝 z (해치·트렁크 위). 위에서 본 그림에서 유리 */
    double rgZ0, rgZ1;
    /** 지붕이 끝나는 z (여기서 뒤 끝까지 비스듬한 판으로 덮음) */
    double roofEndZ;
    /** 뒤에서 본 뒷유리 아래·위 높이, 아래 반폭 */
    double rgY0, rgY1, rgHW;
    /** 벨트(창 아래선) 높이: 앞유리 아래에서 beltF, 차 뒤 끝에서 beltR */
    double beltF, beltR;
    double[] taper = {0.905, 0.85, 0.795, 0.74};
    double[] taperY = {0.15, 0.29, 0.42};
    // ---- 옆 창·문
    /** 옆창 가장 뒤 끝 z (그 뒤는 C·D 필러) */
    double windowRearZ;
    /** 검은 기둥 {z0, z1} */
    double[][] pillars = {};
    double[] seams = {}, handles = {};
    double handleY = 0.82, creaseY = 0.70, rockerY = 0.20;
    // ---- 앞 그림
    double[] headX, headY;
    double gY = 0.70, gHW = 0.21, gHH = 0.045, gR = 0.03;
    /** 그릴 무늬: 0 그물, 1 가로줄, 2 세로줄 */
    int gStyle;
    double intakeY = 0.30, intakeHW = 0.56, intakeHH = 0.11;
    double fogX = 0.62, fogY = 0.32, fogRX = 0.11, fogRY = 0.075;
    double hoodY = 0.795, plateFY = 0.33, plateRY = 0.68;
    // ---- 뒤 그림
    double[] tailX, tailY;
    /** 뒷등을 잇는 가로 등 (0 이면 없음): 높이와 두께 */
    double tailBarY, tailBarH;
    double trunkSeamY = 0.57;
    // ---- 위 그림
    /** 선루프 z 범위 (같으면 없음) */
    double sunZ0, sunZ1;
    // ---- 기타
    double[] mirror = {0.70, 0.97, 0.86, 0.88, 1.09, 0.96};
    double[] floor;
    double tireWidth = 0.165;
    int spokes = 5;
    boolean darkRim;
    int paint = rgb(236, 236, 229);
    Livery livery = new Livery() { };
    List<Extra> extras = new ArrayList<>();

    static final int GLASS = rgb(30, 35, 42);
    static final int TRIM = rgb(26, 26, 28);
    static final int DARK = rgb(34, 34, 36);
    static final int CHROME = rgb(205, 208, 212);
    static final int LENS = rgb(214, 222, 230);
    static final int RED = rgb(168, 22, 30);
    static final int AMBER = rgb(232, 146, 40);
    static final int PLATE = rgb(242, 242, 240);

    /** 크기를 정하고 나서 부르면 앞뒤 끝·반폭을 맞춤 */
    void size(double length, double width, double height) {
        L = length;
        W = width;
        H = height;
        ZF = L / 2;
        ZR = -L / 2;
        HW = W / 2;
    }

    void wheels(double frontOverhang, double wheelbase, double radius, double rim, double trackWidth) {
        frontAxle = ZF - frontOverhang;
        rearAxle = frontAxle - wheelbase;
        R = radius;
        RIM = rim;
        track = trackWidth;
        archR = radius + 0.07;
    }

    // ------------------------------------------------------------------ CarDesign

    @Override
    public String id() {
        return id;
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
    public double height() {
        return H;
    }

    @Override
    public double wheelRadius() {
        return R;
    }

    @Override
    public double tireWidth() {
        return tireWidth;
    }

    @Override
    public double track() {
        return track;
    }

    @Override
    public double frontAxle() {
        return frontAxle;
    }

    @Override
    public double rearAxle() {
        return rearAxle;
    }

    @Override
    public double archRadius() {
        return archR;
    }

    @Override
    public double[] floor() {
        return floor != null ? floor : new double[]{HW * 0.69, 0.18, 0.40, ZR + 0.25, ZF - 0.25};
    }

    @Override
    public double[] mirror() {
        return mirror;
    }

    @Override
    public double[] plates() {
        return new double[]{plateFY, ZF, plateRY, ZR + 0.03};
    }

    @Override
    public List<Extra> extras() {
        return extras;
    }

    @Override
    public int paint() {
        return paint;
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

    // ------------------------------------------------------------------ 윤곽

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

    double top(double z) {
        return lerp(topZ, topY, z);
    }

    double bottom(double z) {
        return lerp(botZ, botY, z);
    }

    double halfWidth(double z) {
        double hw = HW;
        double df = z - (ZF - cornerF), dr = (ZR + cornerR) - z;
        if (df > 0) {
            hw -= cornerF - Math.sqrt(Math.max(0, cornerF * cornerF - df * df));
        }
        if (dr > 0) {
            hw -= cornerR - Math.sqrt(Math.max(0, cornerR * cornerR - dr * dr));
        }
        return hw;
    }

    double belt(double z) {
        double t = Math.max(0, Math.min(1, (wsZ0 - z) / (wsZ0 - ZR)));
        return beltF + (beltR - beltF) * t;
    }

    double wsZ(double y) {
        return wsZ0 + (y - wsY0) / (wsY1 - wsY0) * (wsZ1 - wsZ0);
    }

    @Override
    public double[][] layers(double z) {
        double b = bottom(z), t = top(z), hw = halfWidth(z), belt = belt(z);
        double[][] spec = {
                {b, 0.34, hw - 0.03},                 // 문턱 (안으로 조금 들어감)
                {0.34, belt - 0.07, hw},              // 차체
                {belt - 0.07, belt, hw - 0.012},      // 어깨
                {belt, belt + taperY[0], hw * taper[0]},  // 유리 부분 (위로 갈수록 좁아짐)
                {belt + taperY[0], belt + taperY[1], hw * taper[1]},
                {belt + taperY[1], belt + taperY[2], hw * taper[2]},
                {belt + taperY[2], 9, hw * taper[3]},
        };
        double[][] out = new double[spec.length][];
        for (int i = 0; i < spec.length; i++) {
            out[i] = new double[]{Math.max(b, spec[i][0]), Math.min(t, spec[i][1]), spec[i][2]};
        }
        return out;
    }

    /** z 에서 높이 y 가 든 층의 반폭 */
    double hwAt(double z, double y) {
        for (double[] l : layers(z)) {
            if (y >= l[0] - 1e-9 && y <= l[1] + 1e-9 && l[1] > l[0]) {
                return l[2];
            }
        }
        return halfWidth(z);
    }

    /** 판을 덮을 위 윤곽 구간: 앞유리·보닛 (지붕 앞 ~ 앞 끝 바로 뒤), 뒷유리·트렁크 (지붕 끝 ~ 뒤 끝 바로 앞) */
    boolean skinned(double z0, double z1) {
        return (z0 >= wsZ1 - 1e-6 && z1 <= ZF - 0.02) || (z1 <= roofEndZ + 1e-6 && z0 >= ZR + 0.015);
    }

    @Override
    public double[][] skins() {
        List<double[]> out = new ArrayList<>();
        for (int i = 1; i < topZ.length; i++) {
            double z0 = topZ[i - 1], z1 = topZ[i];
            if (!skinned(z0, z1)) {
                continue;
            }
            double hw = 9;
            for (double z = z0; z <= z1 + 1e-9; z += 0.01) {
                hw = Math.min(hw, hwAt(z, top(z) - 0.005));
            }
            out.add(new double[]{z0, topY[i - 1], z1, topY[i], hw});
        }
        return out.toArray(new double[0][]);
    }

    /** 바퀴집 위 끝 높이 (바퀴집 밖이면 0) */
    double arch(double z) {
        double a = 0;
        for (double axle : new double[]{frontAxle, rearAxle}) {
            double dz = z - axle;
            if (Math.abs(dz) < archR) {
                a = Math.max(a, R + Math.sqrt(archR * archR - dz * dz));
            }
        }
        return a;
    }

    @Override
    public double[][] cornerSkins() {
        List<double[]> out = new ArrayList<>();
        double[][] corners = {{ZF - cornerF, HW - cornerF, cornerF, 1}, {ZR + cornerR, HW - cornerR, cornerR, -1}};
        for (double[] c : corners) {
            if (c[2] < 0.05) {
                continue;
            }
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

    // ------------------------------------------------------------------ 칠 도구

    /** 차체 색: 아래로 갈수록 조금 어둡고, 창 밑 어깨는 밝게 */
    int body(double y, double belt) {
        double f = 0.90 + 0.10 * smooth(0.15, 0.8, y);
        if (y > belt - 0.075 && y < belt - 0.02) {
            f += 0.03;
        }
        return shade(paint, f);
    }

    /** 유리: 위가 밝고 비스듬한 반사 띠 */
    static int glass(double a, double b, double h) {
        int c = mix(GLASS, rgb(70, 82, 96), smooth(0.2, 1, h) * 0.6);
        double band = ((a * 0.9 + b * 1.4) % 0.9 + 0.9) % 0.9;
        if (band < 0.07) {
            c = mix(c, rgb(150, 160, 172), 0.35);
        }
        return c;
    }

    /** 다각형 변까지 거리 */
    static double edgeDist(double x, double y, double[] xs, double[] ys) {
        double best = Double.MAX_VALUE;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            double ax = xs[j], ay = ys[j], bx = xs[i], by = ys[i];
            double dx = bx - ax, dy = by - ay, t = ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy);
            t = Math.max(0, Math.min(1, t));
            best = Math.min(best, Math.hypot(x - ax - dx * t, y - ay - dy * t));
        }
        return best;
    }

    static int pattern(int style, double x, double y) {
        return switch (style) {
            case 1 -> ((int) Math.floor(y * 60)) % 2 == 0 ? rgb(22, 22, 24) : rgb(70, 72, 76);
            case 2 -> ((int) Math.floor(x * 45)) % 2 == 0 ? rgb(22, 22, 24) : rgb(150, 152, 156);
            default -> ((int) Math.floor(x * 70) + (int) Math.floor(y * 70)) % 2 == 0 ? rgb(20, 20, 22) : rgb(58, 60, 64);
        };
    }

    // ------------------------------------------------------------------ 옆

    @Override
    public int side(double z, double y) {
        return livery.side(this, z, y, baseSide(z, y), false);
    }

    @Override
    public int sideRight(double z, double y) {
        return livery.side(this, z, y, baseSide(z, y), true);
    }

    /** 옆면 글씨: zRear 부터 앞으로 쓰는 글씨가 (z, y) 에 획이 있나. 어느 쪽에서 봐도 왼쪽→오른쪽으로 읽힘 */
    boolean sideText(String text, double zRear, double y0, double h, double z, double y, boolean right) {
        double w = PixelFont.width(text, h);
        return right ? PixelFont.ink(text, zRear, y0, h, z, y) : PixelFont.ink(text, -(zRear + w), y0, h, -z, y);
    }

    int baseSide(double z, double y) {
        double belt = belt(z), t = top(z);
        for (double axle : new double[]{frontAxle, rearAxle}) {
            double dd = Math.hypot(z - axle, y - R);
            if (dd < archR + 0.018 && y > 0.12) {
                return DARK;
            }
        }
        // 앞뒤 둥근 모서리: 앞·뒤 그림을 그 자리(x = 반폭)에서 가져와 이음매 없이
        if (z > ZF - cornerF && y < belt) {
            return front(hwAt(z, y), y);
        }
        if (z < ZR + cornerR) {
            return rear(hwAt(z, y), y);
        }
        // A 필러
        if (y > belt && z >= wsZ(y) - 0.10 && z < wsZ(y) + 0.05) {
            return TRIM;
        }
        int w = window(z, y, belt, t);
        if (w != 0) {
            return w;
        }
        double seam = 0.012;
        for (double sz : seams) {
            if (Math.abs(z - sz) < seam / 2 && y < belt && y > 0.22 && Math.hypot(z - nearAxle(z), y - R) > archR + 0.04) {
                return shade(paint, 0.55);
            }
        }
        for (double hz : handles) {
            double dd = roundRect(z, y, hz, handleY, 0.065, 0.014, 0.012);
            if (dd < 0) {
                return dd > -0.004 ? shade(paint, 0.62) : shade(paint, 0.86);
            }
        }
        double crease = creaseY + (wsZ0 - z) * 0.02;
        if (y > crease - 0.008 && y < crease) {
            return shade(paint, 0.80);
        }
        if (y >= crease && y < crease + 0.01) {
            return shade(paint, 1.03);
        }
        if (y < rockerY) {
            return mix(DARK, rgb(60, 60, 62), (y - 0.15) * 10);
        }
        return body(y, belt);
    }

    /** 옆면의 이 점이 칠할 수 있는 차체 판인지 (창·기둥·바퀴집·문턱·앞뒤 모서리가 아님) */
    boolean bodyPanel(double z, double y) {
        for (double axle : new double[]{frontAxle, rearAxle}) {
            if (Math.hypot(z - axle, y - R) < archR + 0.03) {
                return false;
            }
        }
        if (y < rockerY || z > ZF - cornerF || z < ZR + cornerR) {
            return false;
        }
        double belt = belt(z);
        return !(y > belt && z >= wsZ(y) - 0.10) && window(z, y, belt, top(z)) == 0;
    }

    double nearAxle(double z) {
        return Math.abs(z - frontAxle) < Math.abs(z - rearAxle) ? frontAxle : rearAxle;
    }

    /** 옆창 (없으면 0) */
    int window(double z, double y, double belt, double t) {
        double glassTop = t - 0.06, glassBot = belt + 0.02;
        if (y > glassBot - 0.015 && y < glassTop + 0.015 && z < wsZ(y) - 0.08 && z > windowRearZ - 0.02) {
            boolean inGlass = y > glassBot && y < glassTop && z < wsZ(y) - 0.10 && z > windowRearZ;
            boolean pillar = false;
            for (double[] p : pillars) {
                pillar |= z > p[0] && z < p[1];
            }
            if (inGlass && !pillar) {
                return glass(z, y, (y - glassBot) / Math.max(0.05, glassTop - glassBot));
            }
            return TRIM;
        }
        return 0;
    }

    // ------------------------------------------------------------------ 앞

    @Override
    public int front(double x, double y) {
        return livery.front(this, x, y, baseFront(x, y));
    }

    int baseFront(double x, double y) {
        double ax = Math.abs(x);
        if (y > wsY0) {
            double zw = wsZ(Math.min(y, wsY1));
            return ax < hwAt(zw, y) - 0.07 ? glass(x, y, (y - wsY0) / (wsY1 - wsY0)) : TRIM;
        }
        if (headX != null) {
            if (inPoly(ax, y, headX, headY)) {
                return headlight(ax, y);
            }
            if (edgeDist(ax, y, headX, headY) < 0.016) {
                return TRIM;
            }
        }
        // 그릴 + 엠블럼
        double gd = roundRect(x, y, 0, gY, gHW, gHH, gR);
        if (gd < 0) {
            if (gd > -0.008) {
                return CHROME;
            }
            double em = Math.hypot(x / 0.055, (y - gY) / 0.028);
            if (em < 1) {
                return em > 0.78 ? rgb(120, 124, 130) : CHROME;
            }
            return pattern(gStyle, x, y);
        }
        double pd = roundRect(x, y, 0, plateFY, 0.26, 0.055, 0.01);
        if (pd < 0) {
            return pd > -0.007 ? rgb(40, 40, 42) : PLATE;
        }
        if (fogRX > 0) {
            double fog = Math.hypot((ax - fogX) / fogRX, (y - fogY) / fogRY);
            if (fog < 1) {
                return Math.hypot((ax - fogX) / (fogRX * 0.6), (y - fogY) / (fogRY * 0.6)) < 1 ? rgb(248, 214, 150) : TRIM;
            }
        }
        double gi = roundRect(x, y, 0, intakeY, intakeHW, intakeHH, 0.05);
        if (gi < 0) {
            return gi > -0.008 ? rgb(48, 48, 50) : pattern(0, x * 0.7, y * 0.7);
        }
        if (y < rockerY) {
            return DARK;
        }
        if (y > hoodY) {
            return shade(paint, 1.0);
        }
        return body(y, 9);
    }

    /** 전조등 안쪽: 프로젝터 고리 둘, 바깥 아래는 호박색 */
    int headlight(double ax, double y) {
        double cx = 0, cy = 0;
        for (int i = 0; i < headX.length; i++) {
            cx += headX[i];
            cy += headY[i];
        }
        cx /= headX.length;
        cy /= headY.length;
        double span = 0;
        for (double hx : headX) {
            span = Math.max(span, Math.abs(hx - cx));
        }
        double r1 = Math.hypot(ax - (cx + span * 0.35), y - cy);
        if (r1 < 0.05) {
            return r1 > 0.038 ? CHROME : r1 > 0.02 ? rgb(70, 76, 84) : rgb(240, 244, 250);
        }
        double r2 = Math.hypot(ax - (cx - span * 0.25), y - cy);
        if (r2 < 0.034) {
            return r2 > 0.026 ? CHROME : rgb(90, 96, 104);
        }
        if (ax > cx + span * 0.6 && y < cy) {
            return AMBER;
        }
        return mix(rgb(150, 160, 172), LENS, (y - cy + 0.08) * 5);
    }

    // ------------------------------------------------------------------ 뒤

    @Override
    public int rear(double x, double y) {
        return livery.rear(this, x, y, baseRear(x, y));
    }

    int baseRear(double x, double y) {
        double ax = Math.abs(x);
        double gw = rgHW - (y - rgY0) * 0.30;
        if (y > rgY0 - 0.02 && y < rgY1 + 0.025 && ax < gw + 0.025) {
            if (y > rgY0 && y < rgY1 && ax < gw) {
                return glass(x, y, (y - rgY0) / (rgY1 - rgY0));
            }
            return TRIM;
        }
        if (ax < 0.12 && y > rgY1 + 0.025 && y < rgY1 + 0.05) {
            return RED;        // 보조 제동등
        }
        if (tailX != null && inPoly(ax, y, tailX, tailY)) {
            double cy = 0;
            for (double ty : tailY) {
                cy += ty;
            }
            cy /= tailY.length;
            if (y < cy - 0.03 && y > cy - 0.08) {
                return rgb(225, 228, 232);   // 후진등
            }
            return y > cy + 0.04 ? shade(RED, 1.2) : RED;
        }
        if (tailBarH > 0 && Math.abs(y - tailBarY) < tailBarH / 2 && ax < HW - 0.1) {
            return shade(RED, 0.9);
        }
        double pd = roundRect(x, y, 0, plateRY, 0.26, 0.055, 0.01);
        if (pd < 0) {
            return pd > -0.007 ? rgb(40, 40, 42) : PLATE;
        }
        if (roundRect(x, y, 0, plateRY, 0.30, 0.08, 0.02) < 0) {
            return shade(paint, 0.86);
        }
        double em = Math.hypot(x / 0.05, (y - (plateRY + 0.17)) / 0.025);
        if (em < 1) {
            return CHROME;
        }
        if (Math.abs(ax - (HW - 0.18)) < 0.06 && Math.abs(y - 0.40) < 0.016) {
            return shade(RED, 0.8);
        }
        if (y < 0.30) {
            return DARK;
        }
        if (Math.abs(y - trunkSeamY) < 0.004) {
            return shade(paint, 0.6);
        }
        return body(y, 9);
    }

    // ------------------------------------------------------------------ 위

    @Override
    public int top(double x, double z) {
        return livery.top(this, x, z, baseTop(x, z));
    }

    int baseTop(double x, double z) {
        double ax = Math.abs(x);
        double[][] ls = layers(z);
        double[] topL = null;
        for (double[] l : ls) {
            if (l[1] - l[0] > 0.005) {
                topL = l;
            }
        }
        if (topL == null) {
            return paint;
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
        if (z > wsZ1 && z < wsZ0) {
            if (ax < hwTop - 0.07) {
                double wz = wsZ0 - 0.055 + (0.55 - Math.abs(ax - 0.30)) * 0.04;
                if (Math.abs(z - wz) < 0.008 && ax > 0.04 && ax < hwTop - 0.12) {
                    return TRIM;    // 와이퍼
                }
                return glass(x, z, (z - wsZ1) / (wsZ0 - wsZ1));
            }
            return TRIM;
        }
        if (z >= wsZ0 && z < wsZ0 + 0.05 && ax < hwTop * 0.9) {
            return DARK;   // 와이퍼 아래 검은 판
        }
        if (z < rgZ0 && z > rgZ1) {
            return ax < hwTop - 0.04 ? glass(x, z, (z - rgZ1) / (rgZ0 - rgZ1)) : TRIM;
        }
        if (sunZ1 > sunZ0 && z > sunZ0 && z < sunZ1 && ax < hwTop - 0.12) {
            return glass(x, z, 0.3);
        }
        if (z > wsZ0 + 0.05 && Math.abs(ax - (hwTop - 0.06)) < 0.005) {
            return shade(paint, 0.7);
        }
        double f = z < wsZ1 && z > rgZ0 ? 1.0 - 0.04 * smooth(0.2, 0.6, ax) : 0.98;
        return shade(paint, f);
    }

    // ------------------------------------------------------------------ 바퀴

    @Override
    public int wheel(double r, double th) {
        if (r > R) {
            return 0;
        }
        if (r > RIM + 0.012) {
            double ring = Math.abs(r - (RIM + (R - RIM) * 0.6)) < 0.004 ? 1.25 : 1.0;
            return shade(rgb(34, 34, 36), (0.85 + 0.25 * (r - RIM) / (R - RIM)) * ring);
        }
        if (r > RIM) {
            return darkRim ? rgb(70, 72, 76) : rgb(196, 198, 202);
        }
        double hub = RIM * 0.28;
        if (r < hub) {
            return r > hub - 0.007 ? rgb(80, 82, 86) : r < 0.012 ? rgb(60, 62, 66) : rgb(206, 208, 212);
        }
        double s = Math.abs(Math.sin(spokes * th));
        double split = Math.abs(Math.sin(spokes * th + Math.PI / 2));
        boolean spoke = s > 0.55 && r > hub - 0.005;
        int metal = darkRim ? rgb(58, 60, 64) : rgb(190, 192, 196);
        if (spoke && split > 0.97 && r > hub + 0.02) {
            return darkRim ? rgb(30, 30, 32) : rgb(60, 62, 66);
        }
        if (spoke) {
            return shade(metal, 0.85 + 0.25 * (1 - r / RIM));
        }
        return r < RIM * 0.78 ? rgb(92, 92, 94) : rgb(30, 30, 33);
    }

    @Override
    public int tread(double u, double v) {
        boolean groove = Math.abs(u - 0.33) < 0.03 || Math.abs(u - 0.67) < 0.03;
        boolean block = ((int) (v * 12)) % 2 == 0 && (u < 0.18 || u > 0.82);
        return groove ? rgb(16, 16, 17) : block ? rgb(26, 26, 28) : rgb(38, 38, 40);
    }
}
