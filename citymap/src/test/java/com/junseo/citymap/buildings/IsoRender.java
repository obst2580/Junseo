package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 건물 미리보기 그림: 남동쪽 위에서 비스듬히 내려다본 그림(아이소메트릭), 위에서 본 그림,
 * 사람 눈높이에서 본 원근 그림(블록 모양·유리·그림자까지). 서버 없이 생성 결과를 눈으로 확인하는 용도입니다.
 */
final class IsoRender {
    private final CityTerrain terrain;
    private final CityBuildings buildings;
    final int x0, z0, w, d, ymin, hy;
    /** [y][z][x] 블록 번호 (0 = 빈 칸, n = pal(n-1)) */
    private final short[] vol;
    private final List<Block> pal = new ArrayList<>();
    private final Map<Block, Short> index = new HashMap<>();
    private Shape[] shapes;

    IsoRender(CityTerrain terrain, CityBuildings buildings, int x0, int z0, int x1, int z1, int ymin, int ymax) {
        this.terrain = terrain;
        this.buildings = buildings;
        this.x0 = x0;
        this.z0 = z0;
        this.w = x1 - x0 + 1;
        this.d = z1 - z0 + 1;
        this.ymin = ymin;
        this.hy = ymax - ymin + 1;
        this.vol = new short[w * d * hy];
        fill();
    }

    private int idx(int i, int y, int j) {
        return ((y - ymin) * d + j) * w + i;
    }

    private short code(Block b) {
        Short c = index.get(b);
        if (c == null) {
            pal.add(b);
            c = (short) pal.size();
            index.put(b, c);
        }
        return c;
    }

    private void put(int i, int y, int j, Block b) {
        if (y >= ymin && y < ymin + hy) {
            vol[idx(i, y, j)] = b == null || b.isAir() ? 0 : code(b);
        }
    }

    private int raw(int i, int y, int j) {
        if (i < 0 || j < 0 || i >= w || j >= d || y < ymin || y >= ymin + hy) {
            return 0;
        }
        return vol[idx(i, y, j)];
    }

    private int get(int i, int y, int j) {
        int c = raw(i, y, j);
        return c == 0 ? 0 : pal.get(c - 1).rgb();
    }

    private static final Block SOIL = Block.of("dirt", 0x6B5A44);
    private static final Block WATER_SURFACE = Block.of("water", 0x3F76E4);
    private static final Block HUB = Block.of("red_concrete", 0x8E2121);

    private void fill() {
        Map<Integer, Block> surface = new HashMap<>();
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int x = x0 + i, z = z0 + j;
                Column c = terrain.column(x, z);
                int top;
                Block color;
                if (c.isWater() && !c.deck) {
                    top = c.waterTop;
                    color = WATER_SURFACE;
                } else if (c.deck) {
                    top = terrain.groundY();
                    color = surface.computeIfAbsent(surfaceColor(c), rgb -> Block.of("terrain_" + Integer.toHexString(rgb), rgb));
                } else {
                    top = c.groundY;
                    color = surface.computeIfAbsent(surfaceColor(c), rgb -> Block.of("terrain_" + Integer.toHexString(rgb), rgb));
                }
                for (int y = Math.max(ymin, top - 3); y <= top; y++) {
                    put(i, y, j, y == top ? color : SOIL);
                }
                if (c.hubPillar > 0) {
                    for (int y = top + 1; y <= top + c.hubPillar; y++) {
                        put(i, y, j, HUB);
                    }
                }
                final int fi = i, fj = j;
                buildings.column(x, z, c, (y, b) -> put(fi, y, fj, b));
            }
        }
    }

    private static int surfaceColor(Column c) {
        return switch (c.surface) {
            case GRASS -> c.mountainHeight > 0 ? 0x5F8C46 : 0x7CB25A;
            case ROCK -> 0x8C8C8C;
            case SAND -> 0xE1D296;
            case EMBANKMENT -> 0x6E6E6E;
            case ASPHALT -> 0x3C3F44;
            case LINE_WHITE -> 0xF0F0F0;
            case LINE_YELLOW -> 0xF0C828;
            case SIDEWALK -> 0x9A9A96;
            case BORDER_1 -> 0xE06101;
            case BORDER_2 -> 0x5EA918;
            case BORDER_3 -> 0x2389C7;
            case PAD -> 0xEBE5DE;
            case RIVER_BED, SEA_BED -> 0xC8BE96;
        };
    }

    /** 남동쪽 위에서 본 그림. cutY 보다 높은 블록은 빼고 그림 (속 보기) */
    BufferedImage iso(int s, int cutY) {
        int imgW = (w + d) * s + 2, imgH = (w + d) * s / 2 + hy * s + 2;
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0xBFD9EE));
        g.fillRect(0, 0, imgW, imgH);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int ox = d * s, oy = hy * s;
        int maxSum = w + d + hy;
        int[] xs = new int[4], ys = new int[4];
        for (int sum = 0; sum < maxSum; sum++) {
            for (int yy = 0; yy < hy; yy++) {
                int y = ymin + yy;
                if (y > cutY) {
                    break;
                }
                for (int i = 0; i < w; i++) {
                    int j = sum - yy - i;
                    if (j < 0 || j >= d) {
                        continue;
                    }
                    int c = get(i, y, j);
                    if (c == 0) {
                        continue;
                    }
                    boolean top = y + 1 > cutY || get(i, y + 1, j) == 0;
                    boolean south = j + 1 >= d || get(i, y, j + 1) == 0;
                    boolean east = i + 1 >= w || get(i + 1, y, j) == 0;
                    if (!top && !south && !east) {
                        continue;
                    }
                    int yl = y - ymin;
                    if (top) {
                        proj(xs, ys, 0, i, yl + 1, j, s, ox, oy);
                        proj(xs, ys, 1, i + 1, yl + 1, j, s, ox, oy);
                        proj(xs, ys, 2, i + 1, yl + 1, j + 1, s, ox, oy);
                        proj(xs, ys, 3, i, yl + 1, j + 1, s, ox, oy);
                        g.setColor(new Color(c));
                        g.fillPolygon(xs, ys, 4);
                    }
                    if (south) {
                        proj(xs, ys, 0, i, yl, j + 1, s, ox, oy);
                        proj(xs, ys, 1, i + 1, yl, j + 1, s, ox, oy);
                        proj(xs, ys, 2, i + 1, yl + 1, j + 1, s, ox, oy);
                        proj(xs, ys, 3, i, yl + 1, j + 1, s, ox, oy);
                        g.setColor(new Color(shade(c, 0.82)));
                        g.fillPolygon(xs, ys, 4);
                    }
                    if (east) {
                        proj(xs, ys, 0, i + 1, yl, j, s, ox, oy);
                        proj(xs, ys, 1, i + 1, yl, j + 1, s, ox, oy);
                        proj(xs, ys, 2, i + 1, yl + 1, j + 1, s, ox, oy);
                        proj(xs, ys, 3, i + 1, yl + 1, j, s, ox, oy);
                        g.setColor(new Color(shade(c, 0.64)));
                        g.fillPolygon(xs, ys, 4);
                    }
                }
            }
        }
        g.dispose();
        return img;
    }

    private static void proj(int[] xs, int[] ys, int k, int x, int y, int z, int s, int ox, int oy) {
        xs[k] = ox + (x - z) * s;
        ys[k] = oy + (x + z) * s / 2 - y * s;
    }

    /** 위에서 본 그림: 가장 높은 블록 색, 높을수록 밝게 */
    BufferedImage top(int s) {
        BufferedImage img = new BufferedImage(w * s, d * s, BufferedImage.TYPE_INT_RGB);
        int ground = terrain.groundY();
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int c = 0, h = ymin;
                for (int y = ymin + hy - 1; y >= ymin; y--) {
                    int v = get(i, y, j);
                    if (v != 0) {
                        c = v;
                        h = y;
                        break;
                    }
                }
                double f = Math.max(0.75, Math.min(1.3, 0.92 + (h - ground) * 0.012));
                int west = ymin;
                for (int y = ymin + hy - 1; y >= ymin; y--) {
                    if (get(i - 1, y, j - 1) != 0) {
                        west = y;
                        break;
                    }
                }
                if (west > h + 1) {
                    f *= 0.7;
                }
                int rgb = shade(c, f);
                for (int a = 0; a < s; a++) {
                    for (int b = 0; b < s; b++) {
                        img.setRGB(i * s + a, j * s + b, rgb);
                    }
                }
            }
        }
        return img;
    }

    static int shade(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f),
                b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ 눈높이 원근 그림

    /** 블록 안의 모양: 상자 몇 개 ({x0,y0,z0,x1,y1,z1} 0~1), 투명도 (0 = 불투명) */
    private record Shape(double[][] boxes, double alpha, boolean skip) {
        static final Shape FULL = new Shape(new double[][]{{0, 0, 0, 1, 1, 1}}, 0, false);
    }

    private static final double[] SUN = norm(-0.45, 0.8, 0.4);

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    private void prepareShapes() {
        if (shapes != null && shapes.length == pal.size() + 1) {
            return;
        }
        shapes = new Shape[pal.size() + 1];
        for (int k = 0; k < pal.size(); k++) {
            shapes[k + 1] = shapeOf(pal.get(k));
        }
    }

    private static Shape shapeOf(Block b) {
        String id = b.id().replace("minecraft:", "");
        String type = b.property("type"), half = b.property("half"), facing = b.property("facing");
        double alpha = id.contains("glass") ? (id.contains("pane") ? 0.45 : 0.55) : id.equals("water") ? 0.6 : 0;
        if (id.endsWith("_slab")) {
            if ("double".equals(type)) {
                return Shape.FULL;
            }
            return new Shape(new double[][]{"top".equals(type) ? new double[]{0, 0.5, 0, 1, 1, 1} : new double[]{0, 0, 0, 1, 0.5, 1}}, 0, false);
        }
        if (id.endsWith("_carpet")) {
            return new Shape(new double[][]{{0, 0, 0, 1, 0.07, 1}}, 0, false);
        }
        if (id.endsWith("_trapdoor")) {
            if ("true".equals(b.property("open"))) {
                return new Shape(new double[][]{sidePlate(facing, 0.19, true)}, 0, false);
            }
            return new Shape(new double[][]{"top".equals(half) ? new double[]{0, 0.81, 0, 1, 1, 1} : new double[]{0, 0, 0, 1, 0.19, 1}}, 0, false);
        }
        if (id.endsWith("_pane") || id.equals("iron_bars") || id.endsWith("_fence") || id.endsWith("_wall") || id.endsWith("_bars")) {
            double t = id.endsWith("_wall") ? 0.25 : id.endsWith("_fence") ? 0.375 : 0.4375;
            double y1 = id.endsWith("_wall") ? 0.9 : 1;
            List<double[]> boxes = new ArrayList<>();
            boxes.add(new double[]{t, 0, t, 1 - t, id.endsWith("_wall") ? 1 : y1, 1 - t});
            double a = id.endsWith("_wall") ? 0.31 : t;
            double ay0 = id.endsWith("_fence") ? 0.4 : 0, ay1 = id.endsWith("_fence") ? 0.9 : y1;
            if (on(b, "north")) {
                boxes.add(new double[]{a, ay0, 0, 1 - a, ay1, 0.5});
            }
            if (on(b, "south")) {
                boxes.add(new double[]{a, ay0, 0.5, 1 - a, ay1, 1});
            }
            if (on(b, "west")) {
                boxes.add(new double[]{0, ay0, a, 0.5, ay1, 1 - a});
            }
            if (on(b, "east")) {
                boxes.add(new double[]{0.5, ay0, a, 1, ay1, 1 - a});
            }
            return new Shape(boxes.toArray(new double[0][]), alpha, false);
        }
        if (id.endsWith("chain")) {
            return new Shape(new double[][]{"y".equals(b.property("axis")) || b.property("axis") == null ? new double[]{0.44, 0, 0.44, 0.56, 1, 0.56}
                    : "x".equals(b.property("axis")) ? new double[]{0, 0.44, 0.44, 1, 0.56, 0.56} : new double[]{0.44, 0.44, 0, 0.56, 0.56, 1}}, 0, false);
        }
        if (id.endsWith("lantern") && !id.equals("sea_lantern")) {
            boolean hanging = "true".equals(b.property("hanging"));
            return new Shape(new double[][]{hanging ? new double[]{0.31, 0.06, 0.31, 0.69, 0.62, 0.69} : new double[]{0.31, 0, 0.31, 0.69, 0.56, 0.69}}, 0, false);
        }
        if (id.endsWith("_wall_sign")) {
            return new Shape(new double[][]{sidePlate(opposite(facing), 0.125, false)}, 0, false);
        }
        if (id.endsWith("_wall_hanging_sign")) {
            boolean ns = "north".equals(facing) || "south".equals(facing);
            return new Shape(new double[][]{ns ? new double[]{0, 0, 0.44, 1, 0.62, 0.56} : new double[]{0.44, 0, 0, 0.56, 0.62, 1}}, 0, false);
        }
        if (id.endsWith("_hanging_sign")) {
            int rot = Integer.parseInt(b.property("rotation") == null ? "0" : b.property("rotation"));
            boolean ns = rot % 8 < 2 || rot % 8 > 6;
            return new Shape(new double[][]{ns ? new double[]{0.06, 0, 0.44, 0.94, 0.62, 0.56} : new double[]{0.44, 0, 0.06, 0.56, 0.62, 0.94}}, 0, false);
        }
        if (id.endsWith("_door")) {
            return new Shape(new double[][]{sidePlate(opposite(facing), 0.19, false)}, 0, false);
        }
        if (id.equals("ladder")) {
            return new Shape(new double[][]{sidePlate(opposite(facing), 0.12, false)}, 0, false);
        }
        if (id.startsWith("potted_") || id.equals("flower_pot")) {
            return new Shape(new double[][]{{0.31, 0, 0.31, 0.69, 0.38, 0.69}, {0.4, 0.38, 0.4, 0.6, 0.75, 0.6}}, 0, false);
        }
        if (id.equals("campfire")) {
            return new Shape(new double[][]{{0, 0, 0, 1, 0.44, 1}}, 0, false);
        }
        if (id.equals("end_rod") || id.equals("lightning_rod")) {
            return new Shape(new double[][]{{0.44, 0, 0.44, 0.56, 1, 0.56}}, 0, false);
        }
        if (id.equals("cobweb")) {
            return new Shape(new double[0][], 0, true);
        }
        if (id.endsWith("_stairs")) {
            return Shape.FULL;
        }
        if (alpha > 0) {
            return new Shape(new double[][]{{0, 0, 0, 1, 1, 1}}, alpha, false);
        }
        return Shape.FULL;
    }

    private static boolean on(Block b, String dir) {
        String v = b.property(dir);
        return v != null && !v.equals("false") && !v.equals("none");
    }

    private static String opposite(String f) {
        return f == null ? "south" : switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** dir 쪽 면에 붙은 두께 t 의 판 */
    private static double[] sidePlate(String dir, double t, boolean full) {
        double y1 = full ? 1 : 1;
        return switch (dir == null ? "north" : dir) {
            case "south" -> new double[]{0, 0, 1 - t, 1, y1, 1};
            case "east" -> new double[]{1 - t, 0, 0, 1, y1, 1};
            case "west" -> new double[]{0, 0, 0, t, y1, 1};
            default -> new double[]{0, 0, 0, 1, y1, t};
        };
    }

    /**
     * 눈높이 원근 그림. (cx, cy, cz) 는 눈 위치(월드), yaw 는 마인크래프트 방향(0 = 남쪽, 90 = 서쪽, 180 = 북쪽, -90 = 동쪽),
     * pitch 는 아래로 양수.
     */
    BufferedImage perspective(double cx, double cy, double cz, double yaw, double pitch, double fovDeg, int width, int height) {
        prepareShapes();
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double[] fwd = {-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)};
        double[] right = {-Math.cos(yr), 0, -Math.sin(yr)};
        double[] up = {right[1] * fwd[2] - right[2] * fwd[1], right[2] * fwd[0] - right[0] * fwd[2], right[0] * fwd[1] - right[1] * fwd[0]};
        if (up[1] < 0) {
            up = new double[]{-up[0], -up[1], -up[2]};
        }
        double tan = Math.tan(Math.toRadians(fovDeg) / 2);
        double aspect = (double) width / height;
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double sx = (2 * (px + 0.5) / width - 1) * tan * aspect, sy = (1 - 2 * (py + 0.5) / height) * tan;
                double dx = fwd[0] + right[0] * sx + up[0] * sy, dy = fwd[1] + right[1] * sx + up[1] * sy, dz = fwd[2] + right[2] * sx + up[2] * sy;
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                img.setRGB(px, py, trace(cx - x0, cy, cz - z0, dx / l, dy / l, dz / l));
            }
        }
        return img;
    }

    private static int sky(double dy) {
        double t = Math.max(0, Math.min(1, dy * 2.5));
        int r = (int) (205 - 95 * t), g = (int) (222 - 70 * t), b = (int) (238 - 18 * t);
        return (r << 16) | (g << 8) | b;
    }

    /** 광선 하나: 맞은 블록 색 (유리는 섞고 지나감), 그림자, 안개 */
    private int trace(double ox, double oy, double oz, double dx, double dy, double dz) {
        double[] hit = new double[7];
        double r = 0, g = 0, b = 0, weight = 1;
        double px = ox, py = oy, pz = oz;
        double traveled = 0;
        for (int layer = 0; layer < 4; layer++) {
            int c = march(px, py, pz, dx, dy, dz, 260 - traveled, hit);
            if (c == 0) {
                int s = sky(dy);
                r += weight * ((s >> 16) & 255);
                g += weight * ((s >> 8) & 255);
                b += weight * (s & 255);
                break;
            }
            traveled += hit[0];
            double hx = px + dx * hit[0], hyy = py + dy * hit[0], hz = pz + dz * hit[0];
            double nx = hit[1], ny = hit[2], nz = hit[3];
            Block blk = pal.get(c - 1);
            int base = blk.rgb();
            // 블록마다 조금씩 다른 밝기 (질감)
            int bx = (int) Math.floor(hx - nx * 0.01), by = (int) Math.floor(hyy - ny * 0.01), bz = (int) Math.floor(hz - nz * 0.01);
            long hsh = (bx * 73856093L) ^ (by * 19349663L) ^ (bz * 83492791L);
            double grain = 0.94 + 0.12 * ((hsh & 0xff) / 255.0);
            double diffuse = Math.max(0, nx * SUN[0] + ny * SUN[1] + nz * SUN[2]);
            double ambient = ny > 0.5 ? 0.62 : ny < -0.5 ? 0.42 : (Math.abs(nx) > 0.5 ? 0.52 : 0.56);
            double light = ambient + 0.48 * diffuse;
            if (diffuse > 0) {
                double[] tmp = new double[7];
                if (march(hx + nx * 0.002, hyy + ny * 0.002, hz + nz * 0.002, SUN[0], SUN[1], SUN[2], 90, tmp) != 0) {
                    light = ambient * 0.92; // 그림자
                }
            }
            boolean glowing = blk.id().contains("lantern") || blk.id().contains("glowstone") || blk.id().contains("froglight")
                    || blk.id().contains("shroomlight");
            if (glowing) {
                light = 1.15;
            }
            double fog = Math.min(1, traveled / 230.0);
            fog = fog * fog;
            int s = sky(0.05);
            double cr = ((base >> 16) & 255) * light * grain, cg = ((base >> 8) & 255) * light * grain, cb = (base & 255) * light * grain;
            cr = cr * (1 - fog) + ((s >> 16) & 255) * fog;
            cg = cg * (1 - fog) + ((s >> 8) & 255) * fog;
            cb = cb * (1 - fog) + (s & 255) * fog;
            double alpha = shapes[c].alpha;
            double take = alpha > 0 ? 1 - alpha : 1;
            // 유리는 하늘을 조금 비춤
            if (alpha > 0) {
                cr = cr * 0.7 + 200 * 0.3;
                cg = cg * 0.7 + 220 * 0.3;
                cb = cb * 0.7 + 235 * 0.3;
            }
            r += weight * take * cr;
            g += weight * take * cg;
            b += weight * take * cb;
            weight *= 1 - take;
            if (weight < 0.05) {
                break;
            }
            // 유리 뒤로 계속: 이 칸을 빠져나간 곳부터
            px = hx + dx * 0.02;
            py = hyy + dy * 0.02;
            pz = hz + dz * 0.02;
            int skip = c;
            double[] tmp = new double[7];
            int guard = 0;
            while (guard++ < 3 && cellAt(px, py, pz) == skip) {
                double t = exitDistance(px, py, pz, dx, dy, dz);
                px += dx * (t + 0.01);
                py += dy * (t + 0.01);
                pz += dz * (t + 0.01);
                traveled += t + 0.01;
            }
        }
        int ri = (int) Math.max(0, Math.min(255, r)), gi = (int) Math.max(0, Math.min(255, g)), bi = (int) Math.max(0, Math.min(255, b));
        return (ri << 16) | (gi << 8) | bi;
    }

    private int cellAt(double x, double y, double z) {
        return raw((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static double exitDistance(double x, double y, double z, double dx, double dy, double dz) {
        double tx = dx > 0 ? (Math.floor(x) + 1 - x) / dx : dx < 0 ? (x - Math.floor(x)) / -dx : Double.MAX_VALUE;
        double ty = dy > 0 ? (Math.floor(y) + 1 - y) / dy : dy < 0 ? (y - Math.floor(y)) / -dy : Double.MAX_VALUE;
        double tz = dz > 0 ? (Math.floor(z) + 1 - z) / dz : dz < 0 ? (z - Math.floor(z)) / -dz : Double.MAX_VALUE;
        return Math.min(tx, Math.min(ty, tz));
    }

    /**
     * 칸을 따라 광선을 진행 (DDA). 처음 맞은 블록 번호, out = {거리, 법선 x, y, z}. 못 맞으면 0.
     */
    private int march(double ox, double oy, double oz, double dx, double dy, double dz, double maxDist, double[] out) {
        int cx = (int) Math.floor(ox), cy = (int) Math.floor(oy), cz = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx), tdy = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy),
                tdz = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tmx = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? cx + 1 - ox : ox - cx) * tdx;
        double tmy = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? cy + 1 - oy : oy - cy) * tdy;
        double tmz = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? cz + 1 - oz : oz - cz) * tdz;
        double t = 0;
        for (int steps = 0; steps < 900 && t < maxDist; steps++) {
            if (cy < ymin && dy < 0 || cy >= ymin + hy && dy > 0) {
                return 0;
            }
            int c = raw(cx, cy, cz);
            if (c != 0 && !shapes[c].skip) {
                Shape s = shapes[c];
                if (s == Shape.FULL || (s.boxes.length == 1 && s.boxes[0][0] == 0 && s.boxes[0][4] == 1 && s.boxes[0][3] == 1
                        && s.boxes[0][1] == 0 && s.boxes[0][5] == 1)) {
                    out[0] = t;
                    // 법선: 마지막으로 넘은 면 (처음 칸이면 위)
                    out[1] = steps > 0 && lastAxis == 0 ? -stepX : 0;
                    out[2] = steps == 0 ? 1 : lastAxis == 1 ? -stepY : 0;
                    out[3] = steps > 0 && lastAxis == 2 ? -stepZ : 0;
                    return c;
                }
                // 칸 안의 작은 상자들과 교차
                double best = Double.MAX_VALUE;
                double[] bestN = null;
                for (double[] bx : s.boxes) {
                    double[] hitN = new double[3];
                    double th = boxHit(ox, oy, oz, dx, dy, dz, cx + bx[0], cy + bx[1], cz + bx[2], cx + bx[3], cy + bx[4], cz + bx[5], hitN);
                    if (th >= 0 && th < best) {
                        best = th;
                        bestN = hitN;
                    }
                }
                if (bestN != null) {
                    out[0] = best;
                    out[1] = bestN[0];
                    out[2] = bestN[1];
                    out[3] = bestN[2];
                    return c;
                }
            }
            if (tmx < tmy && tmx < tmz) {
                cx += stepX;
                t = tmx;
                tmx += tdx;
                lastAxis = 0;
            } else if (tmy < tmz) {
                cy += stepY;
                t = tmy;
                tmy += tdy;
                lastAxis = 1;
            } else {
                cz += stepZ;
                t = tmz;
                tmz += tdz;
                lastAxis = 2;
            }
            // 다음 칸이 꽉 찬 블록이면 법선은 넘은 면
            int nc = raw(cx, cy, cz);
            if (nc != 0 && shapes[nc] == Shape.FULL) {
                out[0] = t;
                out[1] = lastAxis == 0 ? -stepX : 0;
                out[2] = lastAxis == 1 ? -stepY : 0;
                out[3] = lastAxis == 2 ? -stepZ : 0;
                return nc;
            }
        }
        return 0;
    }

    private int lastAxis;

    /** 광선과 상자의 교차 거리 (없으면 -1), 법선을 n 에 */
    private static double boxHit(double ox, double oy, double oz, double dx, double dy, double dz,
                                 double x0, double y0, double z0, double x1, double y1, double z1, double[] n) {
        double tmin = -Double.MAX_VALUE, tmax = Double.MAX_VALUE;
        int axis = -1;
        double[] o = {ox, oy, oz}, dd = {dx, dy, dz}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        double sign = 0;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(dd[a]) < 1e-12) {
                if (o[a] < lo[a] || o[a] > hi[a]) {
                    return -1;
                }
                continue;
            }
            double t1 = (lo[a] - o[a]) / dd[a], t2 = (hi[a] - o[a]) / dd[a];
            double s = -1;
            if (t1 > t2) {
                double tt = t1;
                t1 = t2;
                t2 = tt;
                s = 1;
            }
            if (t1 > tmin) {
                tmin = t1;
                axis = a;
                sign = s;
            }
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) {
                return -1;
            }
        }
        if (tmax < 0 || axis < 0) {
            return -1;
        }
        n[0] = n[1] = n[2] = 0;
        n[axis] = sign;
        return Math.max(0, tmin);
    }
}
