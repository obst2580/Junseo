package com.junseo.city.vehicle.model;

import static com.junseo.city.vehicle.model.Tex.mix;
import static com.junseo.city.vehicle.model.Tex.rgb;

/**
 * 차종별 크기·윤곽·창·등 모양. 실제 국산차 크기를 따랐고 상표·로고는 넣지 않습니다.
 * 일반 차는 색을 받아 만들고 ({@link CarModels} 가 색마다 그림을 따로 만듦), 직업 차는 정해진 도색입니다.
 */
final class Designs {
    private Designs() {
    }

    /** 옆 윤곽 마디들을 부드러운 곡선(카트멀-롬)으로 이어 촘촘한 점으로: {z[], y[]} */
    static double[][] curve(double[][] keys, int sub) {
        int n = keys.length;
        double[] zs = new double[(n - 1) * sub + 1], ys = new double[zs.length];
        int k = 0;
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = keys[Math.max(0, i - 1)], p1 = keys[i], p2 = keys[i + 1], p3 = keys[Math.min(n - 1, i + 2)];
            for (int s = 0; s < sub; s++) {
                double t = (double) s / sub, t2 = t * t, t3 = t2 * t;
                zs[k] = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                        + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                ys[k] = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                        + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
                k++;
            }
        }
        zs[k] = keys[n - 1][0];
        ys[k] = keys[n - 1][1];
        for (int i = 1; i < zs.length; i++) {
            zs[i] = Math.max(zs[i], zs[i - 1] + 0.005);    // 앞으로만
        }
        return new double[][]{zs, ys};
    }

    private static void profile(BaseDesign d, double[][] top, double[][] bottom) {
        double[][] t = curve(top, 3);
        d.topZ = t[0];
        d.topY = t[1];
        d.botZ = new double[bottom.length];
        d.botY = new double[bottom.length];
        for (int i = 0; i < bottom.length; i++) {
            d.botZ[i] = bottom[i][0];
            d.botY[i] = bottom[i][1];
        }
    }

    // ------------------------------------------------------------------ 경차 (모닝형 5도어 해치백)

    static BaseDesign morning(int paint) {
        BaseDesign d = new BaseDesign();
        d.id = "morning";
        d.paint = paint;
        d.size(3.595, 1.595, 1.50);
        d.wheels(0.62, 2.385, 0.277, 0.1778, 1.42);
        double zf = d.ZF, zr = d.ZR;
        d.topZ = new double[]{zr, -1.78, -1.74, -1.68, -1.58, -1.45, -1.30, -0.60, -0.05, 0.12, 0.25, 0.45, 0.65, 0.85, 1.00, 1.08, 1.30, 1.50, 1.64, 1.73, 1.775, zf};
        d.topY = new double[]{0.86, 0.98, 1.12, 1.25, 1.36, 1.43, 1.465, 1.485, 1.475, 1.455, 1.40, 1.29, 1.175, 1.06, 0.975, 0.94, 0.895, 0.85, 0.80, 0.74, 0.67, 0.58};
        d.botZ = new double[]{zr, -1.78, -1.74, -1.62, -1.45, 1.45, 1.64, 1.74, 1.785, zf};
        d.botY = new double[]{0.40, 0.30, 0.22, 0.17, 0.155, 0.155, 0.18, 0.24, 0.31, 0.40};
        d.wsZ0 = 1.05;
        d.wsY0 = 0.94;
        d.wsZ1 = 0.12;
        d.wsY1 = 1.455;
        d.roofEndZ = -1.30;
        d.rgZ0 = -1.38;
        d.rgZ1 = zr;
        d.rgY0 = 1.02;
        d.rgY1 = 1.375;
        d.rgHW = 0.57;
        d.beltF = 0.88;
        d.beltR = 0.99;
        d.windowRearZ = -1.36;
        d.pillars = new double[][]{{-0.37, -0.29}, {-1.08, -1.02}};
        d.seams = new double[]{0.80, -0.33, -1.05};
        d.handles = new double[]{-0.14, -0.92};
        d.handleY = 0.815;
        d.headX = new double[]{0.29, 0.62, 0.76, 0.795, 0.74, 0.55, 0.36, 0.28};
        d.headY = new double[]{0.79, 0.80, 0.76, 0.68, 0.62, 0.635, 0.70, 0.765};
        d.gY = 0.705;
        d.gHW = 0.21;
        d.gHH = 0.042;
        d.tailX = new double[]{0.52, 0.72, 0.79, 0.80, 0.70, 0.58, 0.51};
        d.tailY = new double[]{1.10, 1.12, 1.04, 0.82, 0.80, 0.86, 0.98};
        d.floor = new double[]{0.55, 0.17, 0.40, -1.55, 1.55};
        return d;
    }

    // ------------------------------------------------------------------ 세단 (중형: 쏘나타형, 대형: 그랜저형)

    /** 세단 공통 윤곽. l 길이, h 높이, hood 보닛 길이(앞 끝→카울), deck 트렁크 길이(뒤 끝→뒷유리 아래) */
    static BaseDesign sedanBody(String id, int paint, double l, double w, double h, double frontOh, double wb, double r, double rim,
                                double hood, double deck) {
        BaseDesign d = new BaseDesign();
        d.id = id;
        d.paint = paint;
        d.size(l, w, h);
        d.wheels(frontOh, wb, r, rim, w - 0.26);
        d.tireWidth = 0.235;
        double zf = d.ZF, zr = d.ZR;
        double cowlZ = zf - hood, cowlY = h * 0.68, deckZ = zr + deck, deckY = h * 0.71;
        double roofF = cowlZ - 0.95, roofR = deckZ + 0.98;
        profile(d, new double[][]{
                {zr, 0.80}, {zr + 0.03, 0.96}, {zr + 0.10, deckY - 0.01}, {deckZ - 0.15, deckY + 0.01}, {deckZ, deckY + 0.02},
                {deckZ + 0.30, h * 0.84}, {roofR, h - 0.02}, {(roofR + roofF) / 2, h}, {roofF, h - 0.012},
                {cowlZ - 0.35, h * 0.85}, {cowlZ, cowlY}, {zf - 0.45, cowlY - 0.06}, {zf - 0.15, cowlY - 0.15},
                {zf - 0.04, 0.70}, {zf, 0.60}}, new double[][]{
                {zr, 0.42}, {zr + 0.05, 0.30}, {zr + 0.15, 0.22}, {zr + 0.40, 0.17}, {zr + 0.65, 0.16},
                {zf - 0.55, 0.16}, {zf - 0.30, 0.18}, {zf - 0.13, 0.24}, {zf - 0.03, 0.32}, {zf, 0.42}});
        d.wsZ0 = cowlZ;
        d.wsY0 = cowlY;
        d.wsZ1 = roofF;
        d.wsY1 = h - 0.012;
        d.roofEndZ = roofR;
        d.rgZ0 = roofR - 0.05;
        d.rgZ1 = deckZ + 0.02;
        d.rgY0 = deckY + 0.04;
        d.rgY1 = h - 0.07;
        d.rgHW = w / 2 - 0.30;
        d.beltF = h * 0.645;
        d.beltR = h * 0.69;
        d.cornerF = 0.45;
        d.cornerR = 0.35;
        double bp = (roofF + roofR) / 2 + 0.05;
        d.windowRearZ = deckZ + 0.12;
        d.pillars = new double[][]{{bp - 0.05, bp + 0.03}, {deckZ + 0.30, deckZ + 0.36}};
        d.seams = new double[]{cowlZ - 0.25, bp - 0.01, deckZ + 0.36};
        d.handles = new double[]{bp - 0.12, deckZ + 0.50};
        d.handleY = d.beltF - 0.07;
        d.creaseY = h * 0.52;
        d.hoodY = cowlY - 0.16;
        double hw = w / 2;
        d.headX = new double[]{hw - 0.55, hw - 0.18, hw - 0.04, hw - 0.01, hw - 0.12, hw - 0.40, hw - 0.56};
        d.headY = new double[]{0.80, 0.815, 0.79, 0.745, 0.715, 0.73, 0.765};
        d.gY = 0.57;
        d.gHW = hw - 0.45;
        d.gHH = 0.12;
        d.gR = 0.08;
        d.intakeY = 0.29;
        d.intakeHW = hw - 0.42;
        d.intakeHH = 0.06;
        d.fogX = hw - 0.20;
        d.fogY = 0.33;
        d.fogRX = 0.08;
        d.fogRY = 0.04;
        d.plateFY = 0.37;
        d.plateRY = 0.62;
        d.tailX = new double[]{hw - 0.48, hw - 0.06, hw - 0.01, hw - 0.04, hw - 0.30, hw - 0.48};
        d.tailY = new double[]{0.975, 0.975, 0.935, 0.86, 0.875, 0.92};
        d.tailBarY = 0.945;
        d.tailBarH = 0.018;
        d.trunkSeamY = 0.76;
        d.mirror = new double[]{hw - 0.10, d.beltF + 0.04, cowlZ - 0.30, hw + 0.10, d.beltF + 0.16, cowlZ - 0.17};
        d.floor = new double[]{hw - 0.33, 0.18, 0.42, zr + 0.55, zf - 0.55};
        return d;
    }

    static BaseDesign sedan(int paint) {
        return sedanBody("sedan", paint, 4.90, 1.86, 1.445, 0.97, 2.84, 0.335, 0.229, 1.05, 0.68);
    }

    static BaseDesign grandeur(int paint) {
        BaseDesign d = sedanBody("grandeur", paint, 5.035, 1.88, 1.47, 0.99, 2.895, 0.345, 0.229, 1.10, 0.74);
        d.gHW = 0.56;          // 큰 그릴
        d.gHH = 0.16;
        d.gY = 0.58;
        d.gStyle = 2;
        d.tailBarH = 0.03;     // 뒤를 가로지르는 등
        d.sunZ0 = d.roofEndZ + 0.25;
        d.sunZ1 = d.wsZ1 - 0.15;
        d.spokes = 10;
        return d;
    }

    // ------------------------------------------------------------------ SUV (쏘렌토형)

    static BaseDesign suv(int paint) {
        BaseDesign d = new BaseDesign();
        d.id = "suv";
        d.paint = paint;
        d.size(4.81, 1.90, 1.72);
        d.wheels(0.94, 2.815, 0.37, 0.229, 1.64);
        d.tireWidth = 0.235;
        d.archR = 0.45;
        double zf = d.ZF, zr = d.ZR, h = 1.72;
        profile(d, new double[][]{
                {zr, 0.95}, {zr + 0.03, 1.12}, {zr + 0.10, 1.36}, {zr + 0.22, 1.56}, {zr + 0.45, h - 0.04},
                {-0.40, h}, {0.50, h - 0.02}, {0.95, 1.45}, {1.35, 1.17}, {1.80, 1.12}, {2.15, 1.06}, {2.33, 0.98}, {zf, 0.80}},
                new double[][]{{zr, 0.50}, {zr + 0.06, 0.34}, {zr + 0.20, 0.25}, {zr + 0.55, 0.21}, {zf - 0.60, 0.21},
                        {zf - 0.25, 0.27}, {zf - 0.05, 0.38}, {zf, 0.48}});
        d.wsZ0 = 1.35;
        d.wsY0 = 1.17;
        d.wsZ1 = 0.50;
        d.wsY1 = h - 0.02;
        d.roofEndZ = zr + 0.45;
        d.rgZ0 = zr + 0.40;
        d.rgZ1 = zr;
        d.rgY0 = 1.20;
        d.rgY1 = 1.58;
        d.rgHW = 0.66;
        d.beltF = 1.08;
        d.beltR = 1.16;
        d.taper = new double[]{0.93, 0.89, 0.85, 0.81};
        d.taperY = new double[]{0.16, 0.30, 0.44};
        d.cornerF = 0.40;
        d.cornerR = 0.22;
        d.windowRearZ = zr + 0.48;
        d.pillars = new double[][]{{-0.20, -0.12}, {-1.10, -1.04}};
        d.seams = new double[]{1.10, -0.16, -1.07};
        d.handles = new double[]{-0.05, -0.95};
        d.handleY = 1.02;
        d.creaseY = 0.85;
        d.rockerY = 0.34;
        d.hoodY = 0.98;
        d.headX = new double[]{0.42, 0.78, 0.92, 0.94, 0.82, 0.55, 0.40};
        d.headY = new double[]{0.97, 0.985, 0.95, 0.89, 0.86, 0.88, 0.93};
        d.gY = 0.80;
        d.gHW = 0.52;
        d.gHH = 0.15;
        d.gR = 0.06;
        d.gStyle = 1;
        d.intakeY = 0.46;
        d.intakeHW = 0.55;
        d.intakeHH = 0.08;
        d.fogX = 0.74;
        d.fogY = 0.50;
        d.plateFY = 0.56;
        d.plateRY = 0.80;
        d.tailX = new double[]{0.58, 0.90, 0.95, 0.94, 0.80, 0.62};
        d.tailY = new double[]{1.28, 1.30, 1.22, 1.00, 1.00, 1.12};
        d.trunkSeamY = 0.68;
        d.mirror = new double[]{0.80, 1.16, 1.00, 1.02, 1.30, 1.12};
        d.floor = new double[]{0.62, 0.24, 0.48, zr + 0.55, zf - 0.55};
        // 지붕 가로대 (검정)
        for (int s : new int[]{1, -1}) {
            double x0 = s * 0.62, x1 = s * 0.68;
            d.extras.add(new CarDesign.Extra(Math.min(x0, x1), h - 0.01, zr + 0.65, Math.max(x0, x1), h + 0.05, 0.35, rgb(30, 30, 32)));
        }
        return d;
    }

    // ------------------------------------------------------------------ 스포츠카 (2도어 쿠페)

    static BaseDesign sports(int paint) {
        BaseDesign d = new BaseDesign();
        d.id = "sports";
        d.paint = paint;
        d.size(4.55, 1.90, 1.27);
        d.wheels(0.95, 2.65, 0.34, 0.2413, 1.62);
        d.tireWidth = 0.255;
        double zf = d.ZF, zr = d.ZR, h = 1.27;
        profile(d, new double[][]{
                {zr, 0.72}, {zr + 0.03, 0.90}, {zr + 0.12, 0.95}, {zr + 0.45, 0.97}, {zr + 0.95, 1.12},
                {-0.45, h - 0.01}, {0.05, h}, {0.40, h - 0.04}, {0.80, 1.05}, {1.20, 0.87}, {1.70, 0.80}, {2.05, 0.72},
                {2.22, 0.62}, {zf, 0.50}},
                new double[][]{{zr, 0.38}, {zr + 0.06, 0.26}, {zr + 0.20, 0.17}, {zr + 0.50, 0.13}, {zf - 0.50, 0.13},
                        {zf - 0.20, 0.15}, {zf - 0.05, 0.22}, {zf, 0.32}});
        d.wsZ0 = 1.20;
        d.wsY0 = 0.87;
        d.wsZ1 = 0.40;
        d.wsY1 = h - 0.04;
        d.roofEndZ = -0.45;
        d.rgZ0 = -0.50;
        d.rgZ1 = zr + 0.45;
        d.rgY0 = 0.99;
        d.rgY1 = 1.20;
        d.rgHW = 0.55;
        d.beltF = 0.84;
        d.beltR = 0.92;
        d.taper = new double[]{0.88, 0.80, 0.74, 0.70};
        d.taperY = new double[]{0.12, 0.22, 0.30};
        d.cornerF = 0.40;
        d.cornerR = 0.32;
        d.windowRearZ = -0.75;
        d.seams = new double[]{0.90, -0.62};
        d.handles = new double[]{-0.50};
        d.handleY = 0.80;
        d.creaseY = 0.62;
        d.hoodY = 0.70;
        d.headX = new double[]{0.45, 0.85, 0.93, 0.94, 0.80, 0.55};
        d.headY = new double[]{0.66, 0.665, 0.63, 0.58, 0.575, 0.62};
        d.gY = 0.42;
        d.gHW = 0.42;
        d.gHH = 0.09;
        d.gR = 0.07;
        d.intakeY = 0.24;
        d.intakeHW = 0.70;
        d.intakeHH = 0.05;
        d.fogRX = 0;
        d.plateFY = 0.30;
        d.plateRY = 0.55;
        d.tailX = new double[]{0.40, 0.90, 0.94, 0.92, 0.55};
        d.tailY = new double[]{0.90, 0.90, 0.86, 0.80, 0.84};
        d.tailBarY = 0.875;
        d.tailBarH = 0.02;
        d.trunkSeamY = 0.68;
        d.darkRim = true;
        d.spokes = 7;
        d.mirror = new double[]{0.78, 0.90, 0.92, 0.99, 1.00, 1.04};
        d.floor = new double[]{0.62, 0.15, 0.36, zr + 0.55, zf - 0.55};
        // 트렁크 끝 작은 날개
        d.extras.add(new CarDesign.Extra(-0.70, 0.96, zr + 0.06, 0.70, 1.00, zr + 0.20, rgb(25, 25, 27)));
        return d;
    }

    // ------------------------------------------------------------------ 경찰차·택시 (세단 + 도색)

    static final int POLICE_BLUE = rgb(24, 52, 120);
    static final int POLICE_YELLOW = rgb(214, 220, 40);
    static final int TAXI_ORANGE = rgb(222, 128, 48);

    static BaseDesign police() {
        BaseDesign d = sedanBody("police", rgb(240, 240, 238), 4.90, 1.86, 1.445, 0.97, 2.84, 0.335, 0.229, 1.05, 0.68);
        double h = 1.445;
        d.livery = new BaseDesign.Livery() {
            @Override
            public int side(BaseDesign c, double z, double y, int col, boolean right) {
                if (y > c.belt(z) - 0.01 || isGlassOrTrim(c, z, y)) {
                    return col;
                }
                // 문 아래쪽 파란 띠, 위에 노란 줄, 앞문에 POLICE, 뒤 펜더에 112
                if (y > 0.25 && y < 0.62) {
                    if (c.sideText("POLICE", -0.22, 0.38, 0.13, z, y, right)
                            || c.sideText("112", c.rearAxle + 0.38, 0.38, 0.13, z, y, right)) {
                        return rgb(245, 245, 245);
                    }
                    return mix(POLICE_BLUE, col, 0.15);
                }
                if (y >= 0.62 && y < 0.65) {
                    return POLICE_YELLOW;
                }
                return col;
            }
        };
        // 지붕 경광등: 가운데 받침 + 빨강·파랑
        double z0 = -0.20, z1 = 0.05, y0 = h - 0.005, y1 = h + 0.10;
        d.extras.add(new CarDesign.Extra(-0.55, y0, z0, 0.55, y0 + 0.03, z1, rgb(40, 40, 44)));
        d.extras.add(new CarDesign.Extra(0.02, y0 + 0.03, z0 + 0.02, 0.55, y1, z1 - 0.02, rgb(225, 30, 40)));
        d.extras.add(new CarDesign.Extra(-0.55, y0 + 0.03, z0 + 0.02, -0.02, y1, z1 - 0.02, rgb(30, 70, 230)));
        return d;
    }

    static BaseDesign taxi() {
        BaseDesign d = sedanBody("taxi", TAXI_ORANGE, 4.90, 1.86, 1.445, 0.97, 2.84, 0.335, 0.229, 1.05, 0.68);
        double h = 1.445;
        // 지붕 택시 표시등: 노란 상자, 앞뒤에 TAXI
        // 간판 그림은 48×16 (3:1) 이라 가로를 3배로 재서 글씨 비율을 맞춤
        Tex.Shader sign = (u, v) -> PixelFont.ink("TAXI", (3 - PixelFont.width("TAXI", 0.62)) / 2, 0.19, 0.62, u * 3, 1 - v)
                ? rgb(30, 30, 30) : rgb(250, 214, 60);
        d.extras.add(new CarDesign.Extra(-0.28, h - 0.005, -0.25, 0.28, h + 0.17, -0.05, rgb(250, 214, 60), sign));
        return d;
    }

    /** 옆 그림의 이 점이 창·기둥·바퀴집이면 도색하지 않음 */
    static boolean isGlassOrTrim(BaseDesign c, double z, double y) {
        return !c.bodyPanel(z, y);
    }

    // ------------------------------------------------------------------ 승합 (스타렉스형): 구급차, 현금수송차

    static BaseDesign van(String id, int paint, double h) {
        BaseDesign d = new BaseDesign();
        d.id = id;
        d.paint = paint;
        d.size(5.15, 1.92, h);
        d.wheels(0.95, 3.20, 0.34, 0.216, 1.66);
        d.tireWidth = 0.215;
        d.archR = 0.42;
        double zf = d.ZF, zr = d.ZR;
        double cowlZ = zf - 0.70, cowlY = 1.12;
        profile(d, new double[][]{
                {zr, 1.10}, {zr + 0.02, h - 0.25}, {zr + 0.06, h - 0.04}, {zr + 0.25, h}, {cowlZ - 0.95, h},
                {cowlZ - 0.45, h - 0.30}, {cowlZ, cowlY}, {zf - 0.30, 1.04}, {zf - 0.08, 0.92}, {zf, 0.70}},
                new double[][]{{zr, 0.52}, {zr + 0.05, 0.35}, {zr + 0.20, 0.25}, {zr + 0.55, 0.21}, {zf - 0.55, 0.21},
                        {zf - 0.20, 0.28}, {zf - 0.03, 0.40}, {zf, 0.50}});
        d.wsZ0 = cowlZ;
        d.wsY0 = cowlY;
        d.wsZ1 = cowlZ - 0.95;
        d.wsY1 = h;
        d.roofEndZ = zr + 0.25;
        d.rgZ0 = zr + 0.05;
        d.rgZ1 = zr;
        d.rgY0 = 1.20;
        d.rgY1 = h - 0.18;
        d.rgHW = 0.70;
        d.beltF = 1.08;
        d.beltR = 1.10;
        d.taper = new double[]{0.975, 0.96, 0.95, 0.94};
        d.taperY = new double[]{0.25, 0.50, 0.75};
        d.cornerF = 0.35;
        d.cornerR = 0.12;
        d.windowRearZ = cowlZ - 1.55;
        d.pillars = new double[][]{{cowlZ - 1.05, cowlZ - 0.98}};
        d.seams = new double[]{cowlZ - 0.15, cowlZ - 1.02, cowlZ - 2.30};
        d.handles = new double[]{cowlZ - 0.85, cowlZ - 1.30};
        d.handleY = 1.00;
        d.creaseY = 0.80;
        d.rockerY = 0.32;
        d.hoodY = 1.02;
        d.headX = new double[]{0.40, 0.82, 0.93, 0.95, 0.85, 0.50};
        d.headY = new double[]{0.98, 1.00, 0.97, 0.88, 0.86, 0.92};
        d.gY = 0.80;
        d.gHW = 0.60;
        d.gHH = 0.10;
        d.gR = 0.05;
        d.gStyle = 1;
        d.intakeY = 0.42;
        d.intakeHW = 0.60;
        d.intakeHH = 0.08;
        d.fogX = 0.78;
        d.fogY = 0.48;
        d.plateFY = 0.58;
        d.plateRY = 0.72;
        d.tailX = new double[]{0.78, 0.94, 0.95, 0.94, 0.78};
        d.tailY = new double[]{1.30, 1.30, 1.20, 0.90, 0.90};
        d.trunkSeamY = 0.58;
        d.mirror = new double[]{0.86, 1.20, cowlZ - 0.20, 1.10, 1.40, cowlZ - 0.05};
        d.floor = new double[]{0.62, 0.24, 0.48, zr + 0.55, zf - 0.55};
        return d;
    }

    static BaseDesign ambulance() {
        BaseDesign d = van("ambulance", rgb(244, 244, 242), 2.30);
        d.windowRearZ = d.wsZ0 - 1.10;    // 뒤 칸은 창 없음
        d.rgY1 = 1.75;
        int red = rgb(214, 36, 40), orange = rgb(240, 120, 30);
        d.livery = new BaseDesign.Livery() {
            @Override
            public int side(BaseDesign c, double z, double y, int col, boolean right) {
                if (isGlassOrTrim(c, z, y)) {
                    return col;
                }
                if (y > 0.86 && y < 0.96) {
                    return red;
                }
                if (y >= 0.96 && y < 1.0) {
                    return orange;
                }
                if (c.sideText("119", c.rearAxle + 0.05, 1.22, 0.42, z, y, right)) {
                    return red;
                }
                return col;
            }

            @Override
            public int rear(BaseDesign c, double x, double y, int col) {
                boolean opening = y > c.rgY0 - 0.03 || y < 0.30 || (c.tailX != null && BaseDesign.edgeDist(Math.abs(x), y, c.tailX, c.tailY) < 0.03)
                        || Tex.inPoly(Math.abs(x), y, c.tailX, c.tailY);
                return !opening && y > 0.86 && y < 0.96 ? red : col;
            }
        };
        double h = 2.30;
        d.extras.add(new CarDesign.Extra(-0.70, h - 0.005, 0.85, 0.70, h + 0.10, 1.10, rgb(225, 30, 40)));
        d.extras.add(new CarDesign.Extra(-0.12, h - 0.005, 0.86, 0.12, h + 0.11, 1.09, rgb(245, 245, 245)));
        return d;
    }

    static BaseDesign cashVan() {
        BaseDesign d = van("cashvan", rgb(52, 62, 78), 2.00);
        d.windowRearZ = d.wsZ0 - 1.10;
        d.rgY0 = 1.30;
        d.rgY1 = 1.55;
        d.rgHW = 0.35;
        d.livery = new BaseDesign.Livery() {
            @Override
            public int side(BaseDesign c, double z, double y, int col, boolean right) {
                if (isGlassOrTrim(c, z, y)) {
                    return col;
                }
                if (c.sideText("SECURITY", c.rearAxle - 0.45, 1.15, 0.20, z, y, right)) {
                    return rgb(230, 190, 60);
                }
                if (y > 0.70 && y < 0.74) {
                    return rgb(230, 190, 60);
                }
                return col;
            }
        };
        return d;
    }
}
