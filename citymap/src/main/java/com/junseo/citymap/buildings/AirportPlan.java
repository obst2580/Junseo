package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 공항 섬. 처음 들어온 사람이 압도되도록 크게 짓습니다.
 * <ul>
 *   <li>활주로와 나란한 활 모양 여객 터미널(스폰 거점 바로 앞), 탑승교와 계류장, 세워 둔 여객기</li>
 *   <li>터미널 서쪽 관제탑</li>
 *   <li>섬으로 들어오는 고속도로 두 개(공항고속도로·올림픽대로) 위에 거대한 홍살문,
 *       길 양쪽에 늘어선 높이 40칸짜리 무인석(돌 장군상)</li>
 *   <li>공항로 고리 안 지구본 조형물 광장과 주차장</li>
 * </ul>
 * 위치는 설계도의 활주로(RW1)·스폰 거점·도로 선에서 계산하므로 설계도를 조금 고쳐도 따라옵니다.
 */
final class AirportPlan {
    /** 터미널 가운데가 스폰 거점보다 활주로 동쪽으로 얼마나 가 있는지 */
    private static final double TERMINAL_SHIFT = 36;
    private static final double HALF_LENGTH = 97;
    private static final double DEPTH = 42;
    private static final double R_IN = 358;
    /** 터미널 육지 쪽 벽이 스폰 거점에서 활주로 쪽으로 몇 칸 */
    private static final double FRONT_GAP = 12;
    private static final double[] GATES = {-10.5, -3.5, 3.5, 10.5};

    /** 활주로 기준 좌표계: u = 활주로 방향(동쪽 끝이 0), v = 활주로에서 터미널 쪽 */
    record Frame(double ox, double oz, double ux, double uz, double vx, double vz) {
        double[] world(double u, double v) {
            return new double[]{ox + u * ux + v * vx, oz + u * uz + v * vz};
        }

        double u(double x, double z) {
            return (x - ox) * ux + (z - oz) * uz;
        }

        double v(double x, double z) {
            return (x - ox) * vx + (z - oz) * vz;
        }

        /** 건물 +a 축의 월드 각도 (도) */
        double angle() {
            return Math.toDegrees(Math.atan2(uz, ux));
        }
    }

    static List<Placement> plan(CityTerrain t) {
        Layout.Road runway = Plans.road(t, "RW1");
        Layout.Hub spawn = Plans.hub(t, "spawn");
        if (runway == null || spawn == null) {
            return List.of();
        }
        Frame f = frame(runway, spawn);
        Random rnd = Plans.random(t, "airport");
        List<Placement> out = new ArrayList<>();

        // ---- 터미널 (활 중심은 육지 쪽 멀리)
        double us = f.u(spawn.x(), spawn.z()), vs = f.v(spawn.x(), spawn.z());
        double uc = us + TERMINAL_SHIFT, vFront = vs - FRONT_GAP;
        double rOut = R_IN + DEPTH;
        double vAir = vFront - DEPTH;
        // 상자: 계류장(활주로 가장자리)부터 차양까지
        double halfRunway = 22.5;
        double b0 = halfRunway - 2;                 // 상자 시작 v
        double a0 = uc - HALF_LENGTH - 12;           // 상자 시작 u
        int w = (int) Math.ceil(2 * HALF_LENGTH + 24), d = (int) Math.ceil(vFront + 32 - b0); // 출국장 앞 보도는 도로까지
        double ca = uc - a0, cb = vFront + R_IN - b0;
        double[] origin = f.world(a0, b0);
        List<double[]> fp = new ArrayList<>();
        double halfAngle = Math.asin(HALF_LENGTH / rOut);
        for (int k = 0; k <= 16; k++) {
            double ps = -halfAngle + 2 * halfAngle * k / 16;
            fp.add(new double[]{ca + rOut * Math.sin(ps), cb - rOut * Math.cos(ps)});
        }
        for (int k = 16; k >= 0; k--) {
            double ps = -halfAngle + 2 * halfAngle * k / 16;
            fp.add(new double[]{ca + R_IN * Math.sin(ps), cb - R_IN * Math.cos(ps)});
        }
        out.add(Placement.rotated("공항 여객 터미널", "terminal", origin[0], origin[1], f.angle(), w, d, 0, 0, fp,
                () -> Terminal.build(w, d, ca, cb, R_IN, rOut, HALF_LENGTH, GATES)));

        // ---- 여객기 (탑승교 끝에 기수를 대고)
        for (int g = 0; g < GATES.length; g++) {
            if (g == 1) {
                continue; // 한 곳은 비워 둠
            }
            double ps = Math.toRadians(GATES[g]);
            double nose = rOut + 15;
            double nu = uc + nose * Math.sin(ps), nv = (vFront + R_IN) - nose * Math.cos(ps);
            // 기수 방향(터미널 쪽) = 활 중심 쪽
            double du = -Math.sin(ps), dv = Math.cos(ps);
            double[] nw = f.world(nu, nv);
            double dx = du * f.ux() + dv * f.vx(), dz = du * f.uz() + dv * f.vz();
            double ang = Math.toDegrees(Math.atan2(-dx, dz));
            out.add(Placement.rotated("여객기", "plane", nw[0], nw[1], ang, Landmarks.PLANE_W, Landmarks.PLANE_D,
                    Landmarks.PLANE_W / 2.0, Landmarks.PLANE_D - 0.5, null, Landmarks::airplane));
        }

        // ---- 관제탑 (터미널 서쪽)
        double[] tw = f.world(uc - HALF_LENGTH - 45, (vAir + vFront) / 2);
        out.add(Placement.rotated("관제탑", "tower", tw[0], tw[1], f.angle(), Landmarks.TOWER, Landmarks.TOWER,
                Landmarks.TOWER / 2.0, Landmarks.TOWER / 2.0, null, Landmarks::controlTower));

        // ---- 진입 고속도로: 홍살문과 무인석
        approach(t, "H1", true, rnd, out);
        approach(t, "H3", false, rnd, out);

        // ---- 공항로 고리 안: 지구본 광장과 주차장
        loop(t, rnd, out);
        return out;
    }

    /**
     * 활주로 한쪽 끝을 원점으로, 스폰 거점 쪽을 +v 로. 건물을 돌려 놓을 때 거울상이 되지 않도록
     * v 는 늘 u 를 시계 방향으로 90도 돌린 쪽이 되게 끝을 고릅니다 (지금 설계도에서는 동쪽 끝이 원점).
     */
    static Frame frame(Layout.Road runway, Layout.Hub spawn) {
        double[] p = runway.line().get(0), q = runway.line().get(runway.line().size() - 1);
        double[] end = p[0] > q[0] ? p : q, start = p[0] > q[0] ? q : p;
        double len = Math.hypot(end[0] - start[0], end[1] - start[1]);
        double ux = (end[0] - start[0]) / len, uz = (end[1] - start[1]) / len;
        if ((spawn.x() - end[0]) * -uz + (spawn.z() - end[1]) * ux < 0) {
            double[] tmp = end;
            end = start;
            start = tmp;
            ux = -ux;
            uz = -uz;
        }
        return new Frame(end[0], end[1], ux, uz, -uz, ux);
    }

    /**
     * 섬으로 들어오는 고속도로에 홍살문 하나와 무인석 세 쌍.
     * fromStart 이면 도로 선의 첫 점(공항로와 만나는 곳)부터, 아니면 끝 점부터 잽니다.
     */
    private static void approach(CityTerrain t, String roadId, boolean fromStart, Random rnd, List<Placement> out) {
        Layout.Road road = Plans.road(t, roadId);
        if (road == null) {
            return;
        }
        List<double[]> line = new ArrayList<>(road.line());
        if (!fromStart) {
            java.util.Collections.reverse(line);
        }
        // 무인석: 공항로 쪽에서 55·95·135칸, 길 가운데에서 양옆 24칸, 길을 바라봄 (다른 길에 걸치면 뺌)
        for (double s : new double[]{55, 95, 135}) {
            double[] pd = pointAt(line, s);
            for (int side : new int[]{-1, 1}) {
                double nx = -pd[3] * side, nz = pd[2] * side; // 길에 수직
                double sx = pd[0] + nx * 24, sz = pd[1] + nz * 24;
                double ang = Math.toDegrees(Math.atan2(nx, -nz)); // 정면이 길 쪽 (-n)
                long seed = rnd.nextLong();
                if (!clearBox(t, sx, sz, ang, Landmarks.STATUE_W / 2.0, Landmarks.STATUE_D / 2.0)) {
                    continue;
                }
                out.add(Placement.rotated("무인석 조형물", "statue", sx, sz, ang, Landmarks.STATUE_W, Landmarks.STATUE_D,
                        Landmarks.STATUE_W / 2.0, Landmarks.STATUE_D / 2.0, null, () -> Landmarks.colossus(seed)));
            }
        }
        // 홍살문: 175칸 지점, 길을 가로질러
        double[] gd = pointAt(line, 175);
        int gw = 52;
        double gateAngle = Math.toDegrees(Math.atan2(-gd[2], gd[3]));
        out.add(Placement.rotated("홍살문", "gate", gd[0], gd[1], gateAngle, gw, Landmarks.GATE_D, gw / 2.0, Landmarks.GATE_D / 2.0,
                null, () -> Landmarks.hongsalGate(gw)));
    }

    /** 돌려 놓은 상자(가운데 cx, cz, 반 크기 ha × hb) 자리에 도로·물이 없는지 */
    static boolean clearBox(CityTerrain t, double cx, double cz, double angleDeg, double ha, double hb) {
        double c = Math.cos(Math.toRadians(angleDeg)), s = Math.sin(Math.toRadians(angleDeg));
        for (double a = -ha; a <= ha; a += 2) {
            for (double b = -hb; b <= hb; b += 2) {
                double x = cx + a * c - b * s, z = cz + a * s + b * c;
                if (!Ground.clearAt(t, (int) Math.floor(x), (int) Math.floor(z), 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 선을 따라 s 만큼 간 점과 방향 {x, z, dx, dz} */
    static double[] pointAt(List<double[]> line, double s) {
        for (int k = 0; k + 1 < line.size(); k++) {
            double[] a = line.get(k), b = line.get(k + 1);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (s <= len || k + 2 == line.size()) {
                double t = Math.min(1, s / len);
                return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, (b[0] - a[0]) / len, (b[1] - a[1]) / len};
            }
            s -= len;
        }
        double[] a = line.get(0);
        return new double[]{a[0], a[1], 1, 0};
    }

    /** 공항로(AP) 고리 안: 가운데 지구본 광장, 나머지는 주차장 */
    private static void loop(CityTerrain t, Random rnd, List<Placement> out) {
        Layout.Road ap = Plans.road(t, "AP");
        if (ap == null) {
            return;
        }
        // 고리 다각형의 무게중심
        double sx = 0, sz = 0, sa = 0;
        List<double[]> pts = ap.line();
        for (int k = 0; k + 1 < pts.size(); k++) {
            double[] a = pts.get(k), b = pts.get(k + 1);
            double cr = a[0] * b[1] - b[0] * a[1];
            sa += cr;
            sx += (a[0] + b[0]) * cr;
            sz += (a[1] + b[1]) * cr;
        }
        double cx = sx / (3 * sa), cz = sz / (3 * sa);
        BuildMask m = BuildMask.of(t, new Polygon(pts.subList(0, pts.size() - 1)), 2);
        int half = Landmarks.GLOBE / 2;
        int gx0 = (int) Math.floor(cx) - half, gz0 = (int) Math.floor(cz) - half;
        out.add(Placement.rect("지구본 조형물", "statue", gx0, gz0, gx0 + Landmarks.GLOBE - 1, gz0 + Landmarks.GLOBE - 1, "south",
                (w, d) -> Landmarks.globe()));
        m.claim(gx0 - 6, gz0 - 6, gx0 + Landmarks.GLOBE + 5, gz0 + Landmarks.GLOBE + 5);
        for (int pass = 0; pass < 2; pass++) {
            for (int[] block : m.blocks()) {
                if (block[4] < 200) {
                    continue;
                }
                for (LotPlanner.Lot lot : new LotPlanner(m, rnd, 12, 30, 4, 6).split(block)) {
                    long seed = rnd.nextLong();
                    out.add(Plans.lot("공항 주차장", "parking", lot, (w, d) -> ParkingLot.build(w, d, new Random(seed))));
                }
                GuincheonPlan.claimAll(m, out);
            }
        }
    }

    private AirportPlan() {
    }
}
