package com.junseo.city.vehicle.model;

import com.junseo.city.vehicle.model.ItemModel.Dir;
import com.junseo.city.vehicle.model.ItemModel.Face;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@link CarDesign} 으로 차체 모델·바퀴 모델과 그림을 만듭니다.
 * <ul>
 *   <li>모델 단위: 1 미터 = {@link CarDesign#unit()} 칸 (아이템 표시 엔티티에서 16/unit 배로 키워 1:1 이 됨). 차 원점(바닥 가운데)이 모델 (8, 0, 8)</li>
 *   <li>차체: 앞뒤로 {@link #DZ} 씩 썬 조각마다 높이별 층(아래 차체, 어깨, 유리 부분 여러 층)을 상자로 쌓고,
 *       같은 모양이 이어지는 조각은 합칩니다. 바퀴 자리는 바깥쪽만 둥글게 파고 안쪽은 바닥 판으로 가립니다</li>
 *   <li>그림 (512×512): 왼쪽·오른쪽 옆(z·y)·앞·뒤(x·y)·위(x·z)에서 본 그림과 단색 칸. 면은 자리에 맞는 그림을 비춰 씀</li>
 *   <li>바퀴: 8 개 판을 22.5° 씩 돌려 16각 타이어를 만들고, 양옆에 휠 그림(투명 바깥)을 붙임. 바퀴 중심이 모델 (8, 8, 8)</li>
 * </ul>
 */
public final class CarModelMaker {
    static final double DZ = 0.05;
    /** 비스듬한 판 두께 (계단 끝이 삐져나오지 않게 덮음) */
    static final double SKIN = 0.024;
    static final int TEX = 512;

    public record Assets(ItemModel body, Tex bodyTex, ItemModel wheel, Tex wheelTex) {
    }

    private final CarDesign d;
    private final double zF, zR, unit, ppm, hMax, xMax;
    /** 그림 자리 (픽셀): 왼쪽 옆, 오른쪽 옆, 앞, 뒤, 위, 단색 칸 시작 */
    private final int sw, sh, fw, rightY, frontX, rearY, topY, tileX0, tileY0;
    /** 한 색 칸: 이름 → {x, y, 색}. 차 색이 바뀌어도 칸 자리는 같게 이름(역할)으로 나눔 */
    private final java.util.LinkedHashMap<String, int[]> tiles = new java.util.LinkedHashMap<>();
    private final java.util.List<int[]> signs = new java.util.ArrayList<>();
    private int tileCursor;

    private CarModelMaker(CarDesign d) {
        this.d = d;
        this.zF = d.length() / 2;
        this.zR = -d.length() / 2;
        this.unit = d.unit();
        this.hMax = d.height() + 0.20;
        this.xMax = d.width() / 2 + 0.03;
        double p = 48;
        while (p > 24 && (2 * Math.ceil(hMax * p) + 4 + Math.ceil(d.length() * p) > TEX
                || Math.ceil(d.length() * p) + 2 + Math.ceil(2 * xMax * p) > TEX)) {
            p -= 4;
        }
        this.ppm = p;
        sw = (int) Math.ceil(d.length() * ppm);
        sh = (int) Math.ceil(hMax * ppm);
        fw = (int) Math.ceil(2 * xMax * ppm);
        rightY = sh + 2;
        frontX = sw + 2;
        rearY = sh + 2;
        topY = 2 * sh + 4;
        tileX0 = fw + 4;
        tileY0 = topY;
        role("dark", d.underbody());
        role("paint", d.paint());
        role("trim", d.trim());
        role("glass", d.glass());
    }

    public static Assets make(CarDesign d) {
        CarModelMaker m = new CarModelMaker(d);
        ItemModel body = m.body();
        return new Assets(body, m.bodyTexture(), m.wheel(), m.wheelTexture());
    }

    // ------------------------------------------------------------------ 그림 좌표 (uv = 픽셀 × 16 / TEX)

    private static double uv(double px) {
        return px * 16 / TEX;
    }

    private double sideLU(double z) {
        return uv((zF - z) * ppm);
    }

    private double sideRU(double z) {
        return uv((z - zR) * ppm);
    }

    private double sideV(double y) {
        return uv((hMax - y) * ppm);
    }

    private double sideRV(double y) {
        return uv(rightY + (hMax - y) * ppm);
    }

    private double frontU(double x) {
        return uv(frontX + (x + xMax) * ppm);
    }

    private double rearU(double x) {
        return uv(frontX + (xMax - x) * ppm);
    }

    private double rearV(double y) {
        return uv(rearY + (hMax - y) * ppm);
    }

    private double topU(double x) {
        return uv((x + xMax) * ppm);
    }

    private double topV(double z) {
        return uv(topY + (z - zR) * ppm);
    }

    /** 한 색 칸 (8×8 픽셀, 가운데만 씀) */
    private Face role(String name, int color) {
        int[] at = tiles.computeIfAbsent(name, k -> {
            int per = (TEX - tileX0) / 10;
            int[] a = {tileX0 + (tileCursor % per) * 10, tileY0 + (tileCursor / per) * 10, color};
            tileCursor++;
            return a;
        });
        return new Face(uv(at[0] + 2), uv(at[1] + 2), uv(at[0] + 6), uv(at[1] + 6), "0");
    }

    /** 덧붙이는 상자 면 그림 자리 (48×16 픽셀) */
    private Face sign(Tex.Shader shader) {
        int k = signs.size();
        int per = 4;
        int x = tileX0 + (k % per) * 50, y = TEX - 18 - (k / per) * 18;
        signs.add(new int[]{x, y});
        signShaders.add(shader);
        return new Face(uv(x), uv(y), uv(x + 48), uv(y + 16), "0");
    }

    private final java.util.List<Tex.Shader> signShaders = new java.util.ArrayList<>();

    /** 상자 면에 비춘 그림 자리 */
    private Face projected(Dir dir, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (dir) {
            case EAST -> new Face(sideLU(z1), sideV(y1), sideLU(z0), sideV(y0), "0");
            case WEST -> new Face(sideRU(z0), sideRV(y1), sideRU(z1), sideRV(y0), "0");
            case SOUTH -> new Face(frontU(x0), sideV(y1), frontU(x1), sideV(y0), "0");
            case NORTH -> new Face(rearU(x1), rearV(y1), rearU(x0), rearV(y0), "0");
            case UP -> new Face(topU(x0), topV(z0), topU(x1), topV(z1), "0");
            case DOWN -> role("dark", d.underbody());
        };
    }

    private double[] mu(double x, double y, double z) {
        return new double[]{8 + x * unit, y * unit, 8 + z * unit};
    }

    // ------------------------------------------------------------------ 차체

    private static double q(double v) {
        return Math.round(v / 0.005) * 0.005;
    }

    /** 조각 z0..z1 의 층들 {y0, y1, hw} (바퀴집을 판 뒤). 양 끝 중 안쪽 값을 써서 조각이 겉면 밖으로 삐져나오지 않게 */
    double[][] slice(double z0, double z1) {
        double arch = 0;
        for (double axle : new double[]{d.frontAxle(), d.rearAxle()}) {
            for (double z : new double[]{z0, (z0 + z1) / 2, z1}) {
                double dz = z - axle;
                if (Math.abs(dz) < d.archRadius()) {
                    arch = Math.max(arch, d.wheelRadius() + Math.sqrt(d.archRadius() * d.archRadius() - dz * dz));
                }
            }
        }
        double[][] a = d.layers(z0), b = d.layers(z1);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < a.length; i++) {
            double[] l = {Math.max(a[i][0], b[i][0]), Math.min(a[i][1], b[i][1]), Math.min(a[i][2], b[i][2])};
            double y0 = q(Math.max(l[0], arch)), y1 = q(l[1]), hw = q(l[2]);
            if (y1 - y0 >= 0.01 && hw > 0.02) {
                out.add(new double[]{y0, y1, hw});
            }
        }
        return out.toArray(new double[0][]);
    }

    private static boolean same(double[][] a, double[][] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!Arrays.equals(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    /** 이웃 조각이 [y0, y1] × 반폭 hw 를 다 덮나 */
    private static boolean covered(double[][] nb, double y0, double y1, double hw) {
        if (nb == null) {
            return false;
        }
        double reach = y0;
        boolean grew = true;
        while (grew && reach < y1 - 1e-6) {
            grew = false;
            for (double[] l : nb) {
                if (l[2] >= hw - 1e-6 && l[0] <= reach + 1e-6 && l[1] > reach + 1e-6) {
                    reach = l[1];
                    grew = true;
                }
            }
        }
        return reach >= y1 - 1e-6;
    }

    ItemModel body() {
        ItemModel m = new ItemModel();
        m.textures.put("0", "junseocity:item/car/" + d.id());
        m.textures.put("particle", "junseocity:item/car/" + d.id());
        // 조각 썰기·합치기
        int n = (int) Math.round((zF - zR) / DZ);
        List<double[]> runs = new ArrayList<>();      // {z0, z1}
        List<double[][]> runLayers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double z0 = zR + i * DZ, z1 = Math.min(zF, z0 + DZ);
            double[][] s = slice(z0, z1);
            if (!runs.isEmpty() && same(runLayers.get(runLayers.size() - 1), s)) {
                runs.get(runs.size() - 1)[1] = z1;
            } else {
                runs.add(new double[]{z0, z1});
                runLayers.add(s);
            }
        }
        for (int r = 0; r < runs.size(); r++) {
            double z0 = runs.get(r)[0], z1 = runs.get(r)[1];
            double[][] ls = runLayers.get(r);
            double[][] back = r > 0 ? runLayers.get(r - 1) : null, ahead = r + 1 < runs.size() ? runLayers.get(r + 1) : null;
            for (int k = 0; k < ls.length; k++) {
                double y0 = ls[k][0], y1 = ls[k][1], hw = ls[k][2];
                Map<Dir, Face> faces = new EnumMap<>(Dir.class);
                for (Dir dir : new Dir[]{Dir.EAST, Dir.WEST, Dir.UP}) {
                    faces.put(dir, projected(dir, -hw, y0, z0, hw, y1, z1));
                }
                if (k == 0) {
                    faces.put(Dir.DOWN, role("dark", d.underbody()));
                }
                if (!covered(ahead, y0, y1, hw)) {
                    faces.put(Dir.SOUTH, projected(Dir.SOUTH, -hw, y0, z0, hw, y1, z1));
                }
                if (!covered(back, y0, y1, hw)) {
                    faces.put(Dir.NORTH, projected(Dir.NORTH, -hw, y0, z0, hw, y1, z1));
                }
                m.add(mu(-hw, y0, z0), mu(hw, y1, z1), faces);
            }
        }
        // 비스듬한 판 (보닛·앞유리·뒷유리): 가운데 선을 따라 두께 SKIN, x 축으로 기울임
        for (double[] sk : d.skins()) {
            double za = sk[0], ya = sk[1], zb = sk[2], yb = sk[3], hw = sk[4];
            double len = Math.hypot(zb - za, yb - ya), zc = (za + zb) / 2, yc = (ya + yb) / 2;
            double angle = Math.toDegrees(Math.atan2(-(yb - ya), zb - za));
            double ylo = Math.min(ya, yb), yhi = Math.max(ya, yb);
            Map<Dir, Face> faces = new EnumMap<>(Dir.class);
            faces.put(Dir.EAST, projected(Dir.EAST, -hw, ylo, za, hw, yhi, zb));
            faces.put(Dir.WEST, projected(Dir.WEST, -hw, ylo, za, hw, yhi, zb));
            if (Math.abs(angle) <= 45) {
                // 눕힌 판: 윗면이 겉
                double z0 = zc - len / 2, z1 = zc + len / 2, y0 = yc - SKIN / 2, y1 = yc + SKIN / 2;
                faces.put(Dir.UP, projected(Dir.UP, -hw, y1, za, hw, y1, zb));
                faces.put(Dir.SOUTH, projected(Dir.SOUTH, -hw, y0, z0, hw, y1, z1));
                faces.put(Dir.NORTH, projected(Dir.NORTH, -hw, y0, z0, hw, y1, z1));
                m.add(mu(-hw, y0, z0), mu(hw, y1, z1), "x", angle, mu(0, yc, zc), faces);
            } else {
                // 세운 판 (45° 넘게 가파른 곳): 앞·뒷면이 겉, 90° 덜 돌림
                double y0 = yc - len / 2, y1 = yc + len / 2, z0 = zc - SKIN / 2, z1 = zc + SKIN / 2;
                faces.put(Dir.UP, projected(Dir.UP, -hw, y1, za, hw, y1, zb));
                faces.put(Dir.SOUTH, projected(Dir.SOUTH, -hw, ylo, z0, hw, yhi, z1));
                faces.put(Dir.NORTH, projected(Dir.NORTH, -hw, ylo, z0, hw, yhi, z1));
                m.add(mu(-hw, y0, z0), mu(hw, y1, z1), "x", angle > 0 ? angle - 90 : angle + 90, mu(0, yc, zc), faces);
            }
        }
        // 둥근 모서리를 덮는 세운 판 (y 축으로 돌림, 오른쪽은 대칭)
        for (double[] c : d.cornerSkins()) {
            for (int side : new int[]{1, -1}) {
                double za = c[0], xa = side * c[1], zb = c[2], xb = side * c[3], y0 = c[4], y1 = c[5];
                double dx = xb - xa, dz = zb - za, len = Math.hypot(dx, dz), xm = (xa + xb) / 2, zm = (za + zb) / 2;
                double psi = Math.toDegrees(Math.atan2(dx, dz));
                if (psi > 90) {
                    psi -= 180;
                } else if (psi < -90) {
                    psi += 180;
                }
                double zlo = Math.min(za, zb), zhi = Math.max(za, zb), xlo = Math.min(xa, xb), xhi = Math.max(xa, xb);
                Map<Dir, Face> faces = new EnumMap<>(Dir.class);
                faces.put(Dir.UP, role("paint", d.paint()));
                faces.put(Dir.DOWN, role("dark", d.underbody()));
                if (Math.abs(psi) <= 45) {
                    Dir out = side > 0 ? Dir.EAST : Dir.WEST;
                    faces.put(out, projected(out, xlo, y0, zlo, xhi, y1, zhi));
                    faces.put(Dir.SOUTH, projected(Dir.SOUTH, xlo, y0, zlo, xhi, y1, zhi));
                    faces.put(Dir.NORTH, projected(Dir.NORTH, xlo, y0, zlo, xhi, y1, zhi));
                    m.add(mu(xm - SKIN / 2, y0, zm - len / 2), mu(xm + SKIN / 2, y1, zm + len / 2), "y", psi, mu(xm, 0, zm), faces);
                } else {
                    Dir out = zm > 0 ? Dir.SOUTH : Dir.NORTH;
                    faces.put(out, projected(out, xlo, y0, zlo, xhi, y1, zhi));
                    Dir sd = side > 0 ? Dir.EAST : Dir.WEST;
                    faces.put(sd, projected(sd, xlo, y0, zlo, xhi, y1, zhi));
                    m.add(mu(xm - len / 2, y0, zm - SKIN / 2), mu(xm + len / 2, y1, zm + SKIN / 2), "y",
                            psi > 0 ? psi - 90 : psi + 90, mu(xm, 0, zm), faces);
                }
            }
        }
        // 바닥 판 (바퀴집 안쪽이 뚫려 보이지 않게)
        double[] f = d.floor();
        m.add(mu(-f[0], f[1], f[3]), mu(f[0], f[2], f[4]), all(role("dark", d.underbody())));
        // 사이드미러 (몸통은 차 색, 뒤쪽은 거울, 팔은 검정)
        double[] mr = d.mirror();
        for (int side : new int[]{1, -1}) {
            double xa = side * mr[0], xb = side * mr[3];
            Map<Dir, Face> faces = all(role("paint", d.paint()));
            faces.put(Dir.NORTH, role("glass", d.glass()));
            faces.put(Dir.DOWN, role("trim", d.trim()));
            m.add(mu(Math.min(xa, xb), mr[1], mr[2]), mu(Math.max(xa, xb), mr[4], mr[5]), faces);
            double arm0 = side * (mr[0] - 0.09), arm1 = side * (mr[0] + 0.01);
            m.add(mu(Math.min(arm0, arm1), mr[1], mr[2] + 0.03), mu(Math.max(arm0, arm1), mr[1] + 0.04, mr[5] - 0.01), all(role("trim", d.trim())));
        }
        // 덧붙이는 상자 (경광등·표시등·가로대 등)
        for (CarDesign.Extra e : d.extras()) {
            Map<Dir, Face> faces = all(role("x" + e.color(), e.color()));
            if (e.sign() != null) {
                faces.put(Dir.SOUTH, sign(e.sign()));
                faces.put(Dir.NORTH, sign(e.sign()));
            }
            m.add(mu(e.x0(), e.y0(), e.z0()), mu(e.x1(), e.y1(), e.z1()), faces);
        }
        return m;
    }

    private Map<Dir, Face> all(Face f) {
        Map<Dir, Face> faces = new EnumMap<>(Dir.class);
        for (Dir dir : Dir.values()) {
            faces.put(dir, f);
        }
        return faces;
    }

    /** body() 를 먼저 불러야 칸·간판 자리가 정해짐 */
    Tex bodyTexture() {
        Tex t = new Tex(TEX, TEX);
        t.paint(0, 0, sw, sh, (px, py) -> d.side(zF - px / ppm, hMax - py / ppm));
        t.paint(0, rightY, sw, sh, (px, py) -> d.sideRight(zR + px / ppm, hMax - py / ppm));
        t.paint(frontX, 0, fw, sh, (px, py) -> d.front(px / ppm - xMax, hMax - py / ppm));
        t.paint(frontX, rearY, fw, sh, (px, py) -> d.rear(xMax - px / ppm, hMax - py / ppm));
        t.paint(0, topY, fw, sw, (px, py) -> d.top(px / ppm - xMax, zR + py / ppm));
        for (int[] at : tiles.values()) {
            t.fill(at[0], at[1], 8, 8, at[2]);
        }
        for (int i = 0; i < signs.size(); i++) {
            int[] at = signs.get(i);
            Tex.Shader sh = signShaders.get(i);
            t.paint(at[0], at[1], 48, 16, (px, py) -> sh.at(px / 48, py / 16));
        }
        return t;
    }

    // ------------------------------------------------------------------ 바퀴

    ItemModel wheel() {
        ItemModel m = new ItemModel();
        String tex = "junseocity:item/car/wheel_" + d.id();
        m.textures.put("0", tex);
        m.textures.put("particle", tex);
        double R = d.wheelRadius(), w = d.tireWidth() / 2;
        double a = R * Math.cos(Math.PI / 16), s = R * Math.sin(Math.PI / 16);
        double[] origin = {8, 8, 8};
        Face tread = new Face(8, 0, 16, 8, "0");
        // 가로 판 (앞뒤 끝이 타이어 바닥): 0°, ±22.5°, ±45°
        for (double ang : new double[]{0, 22.5, -22.5, 45, -45}) {
            Map<Dir, Face> faces = new EnumMap<>(Dir.class);
            faces.put(Dir.NORTH, tread);
            faces.put(Dir.SOUTH, tread);
            m.add(new double[]{8 - w * unit, 8 - s * unit, 8 - a * unit}, new double[]{8 + w * unit, 8 + s * unit, 8 + a * unit},
                    ang == 0 ? null : "x", ang, origin, faces);
        }
        // 세로 판 (위아래 끝이 타이어 바닥): 90°, 90° ± 22.5°
        for (double ang : new double[]{0, 22.5, -22.5}) {
            Map<Dir, Face> faces = new EnumMap<>(Dir.class);
            faces.put(Dir.UP, tread);
            faces.put(Dir.DOWN, tread);
            m.add(new double[]{8 - w * unit, 8 - a * unit, 8 - s * unit}, new double[]{8 + w * unit, 8 + a * unit, 8 + s * unit},
                    ang == 0 ? null : "x", ang, origin, faces);
        }
        // 양옆 휠 (투명 바깥)
        Face disc = new Face(0, 0, 8, 8, "0");
        Map<Dir, Face> east = new EnumMap<>(Dir.class);
        east.put(Dir.EAST, disc);
        m.add(new double[]{8 + (w - 0.01) * unit, 8 - R * unit, 8 - R * unit}, new double[]{8 + (w + 0.003) * unit, 8 + R * unit, 8 + R * unit}, east);
        Map<Dir, Face> west = new EnumMap<>(Dir.class);
        west.put(Dir.WEST, disc);
        m.add(new double[]{8 - (w + 0.003) * unit, 8 - R * unit, 8 - R * unit}, new double[]{8 - (w - 0.01) * unit, 8 + R * unit, 8 + R * unit}, west);
        return m;
    }

    Tex wheelTexture() {
        Tex t = new Tex(128, 128);
        double R = d.wheelRadius();
        // 휠 (0,0)-(64,64) 가 [-R, R] 네모
        t.paint(0, 0, 64, 64, (px, py) -> {
            double x = (px / 64 * 2 - 1) * R, y = (1 - py / 64 * 2) * R;
            return d.wheel(Math.hypot(x, y), Math.atan2(y, x));
        });
        t.paint(64, 0, 64, 64, (px, py) -> d.tread(px / 64, py / 64));
        return t;
    }
}
