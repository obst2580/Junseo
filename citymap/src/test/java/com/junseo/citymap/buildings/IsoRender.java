package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 건물 미리보기 그림: 남동쪽 위에서 비스듬히 내려다본 그림(아이소메트릭)과 위에서 본 그림.
 * 서버 없이 생성 결과를 눈으로 확인하는 용도입니다.
 */
final class IsoRender {
    private final CityTerrain terrain;
    private final CityBuildings buildings;
    final int x0, z0, w, d, ymin, hy;
    /** [y][z][x] 색 (0 = 빈 칸) */
    private final int[] vol;

    IsoRender(CityTerrain terrain, CityBuildings buildings, int x0, int z0, int x1, int z1, int ymin, int ymax) {
        this.terrain = terrain;
        this.buildings = buildings;
        this.x0 = x0;
        this.z0 = z0;
        this.w = x1 - x0 + 1;
        this.d = z1 - z0 + 1;
        this.ymin = ymin;
        this.hy = ymax - ymin + 1;
        this.vol = new int[w * d * hy];
        fill();
    }

    private int idx(int i, int y, int j) {
        return ((y - ymin) * d + j) * w + i;
    }

    private void put(int i, int y, int j, int rgb) {
        if (y >= ymin && y < ymin + hy) {
            vol[idx(i, y, j)] = rgb;
        }
    }

    private int get(int i, int y, int j) {
        if (i < 0 || j < 0 || i >= w || j >= d || y < ymin || y >= ymin + hy) {
            return 0;
        }
        return vol[idx(i, y, j)];
    }

    private void fill() {
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int x = x0 + i, z = z0 + j;
                Column c = terrain.column(x, z);
                int top;
                int color;
                if (c.isWater() && !c.deck) {
                    top = c.waterTop;
                    color = 0x3F76E4;
                } else if (c.deck) {
                    top = terrain.groundY();
                    color = surfaceColor(c);
                } else {
                    top = c.groundY;
                    color = surfaceColor(c);
                }
                for (int y = Math.max(ymin, top - 3); y <= top; y++) {
                    put(i, y, j, y == top ? color : 0x6B5A44);
                }
                if (c.hubPillar > 0) {
                    for (int y = top + 1; y <= top + c.hubPillar; y++) {
                        put(i, y, j, 0x8E2121);
                    }
                }
                final int fi = i, fj = j;
                buildings.column(x, z, c, (y, b) -> put(fi, y, fj, b.isAir() ? 0 : b.rgb()));
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
                    // 투영: (x, y, z) → ((x - z) s, (x + z) s / 2 - y s)
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
                // 서쪽 칸보다 높으면 그림자
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
}
