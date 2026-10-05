package com.junseo.city.vehicle.model;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** 모델 그림 (ARGB). 칸마다 3×3 점을 찍어 평균 내는 방식으로 매끈하게 칠합니다. */
public final class Tex {
    /** 그림 칸 안의 한 점 (x, y 픽셀 좌표, 소수) → ARGB */
    @FunctionalInterface
    public interface Shader {
        int at(double x, double y);
    }

    public final int w, h;
    public final int[] argb;

    public Tex(int w, int h) {
        this.w = w;
        this.h = h;
        this.argb = new int[w * h];
    }

    /** (x0, y0) 부터 rw × rh 칸을 셰이더로 칠함 (3×3 표본 평균) */
    public void paint(int x0, int y0, int rw, int rh, Shader s) {
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                double a = 0, r = 0, g = 0, b = 0;
                for (int sy = 0; sy < 3; sy++) {
                    for (int sx = 0; sx < 3; sx++) {
                        int c = s.at(x + (sx + 0.5) / 3, y + (sy + 0.5) / 3);
                        double ca = (c >>> 24) / 255.0;
                        a += ca;
                        r += ca * ((c >> 16) & 255);
                        g += ca * ((c >> 8) & 255);
                        b += ca * (c & 255);
                    }
                }
                int px = x0 + x, py = y0 + y;
                if (px < 0 || py < 0 || px >= w || py >= h) {
                    continue;
                }
                if (a <= 0) {
                    argb[py * w + px] = 0;
                    continue;
                }
                int ia = (int) Math.round(a / 9 * 255);
                argb[py * w + px] = ia << 24 | clamp(r / a) << 16 | clamp(g / a) << 8 | clamp(b / a);
            }
        }
    }

    /** 한 색으로 칠함 */
    public void fill(int x0, int y0, int rw, int rh, int c) {
        for (int y = y0; y < y0 + rh; y++) {
            for (int x = x0; x < x0 + rw; x++) {
                argb[y * w + x] = c;
            }
        }
    }

    public byte[] png() {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public int get(int x, int y) {
        return argb[Math.floorMod(y, h) * w + Math.floorMod(x, w)];
    }

    // ------------------------------------------------------------------ 색 도구

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    /** 색 c 의 밝기를 f 배 */
    public static int shade(int c, double f) {
        return (c & 0xFF000000) | clamp(((c >> 16) & 255) * f) << 16 | clamp(((c >> 8) & 255) * f) << 8 | clamp((c & 255) * f);
    }

    /** a 와 b 를 t (0..1) 만큼 섞음 */
    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ba = b >>> 24;
        return clamp(aa + (ba - aa) * t) << 24
                | clamp(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t) << 16
                | clamp(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t) << 8
                | clamp((a & 255) + ((b & 255) - (a & 255)) * t);
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /** 0..1 사이 매끈한 계단 */
    public static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** 둥근 직사각형 안쪽 거리 (안이면 음수) */
    public static double roundRect(double x, double y, double cx, double cy, double hw, double hh, double r) {
        double qx = Math.abs(x - cx) - hw + r, qy = Math.abs(y - cy) - hh + r;
        return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
    }

    /** 점이 다각형 안에 있나 (짝홀 규칙) */
    public static boolean inPoly(double x, double y, double[] xs, double[] ys) {
        boolean in = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                in = !in;
            }
        }
        return in;
    }

    /** 작은 결 (0..1), 같은 자리는 늘 같은 값 */
    public static double grain(double x, double y) {
        long h = Double.doubleToLongBits(Math.floor(x * 7.3)) * 31 + Double.doubleToLongBits(Math.floor(y * 7.3));
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h & 0xFFFF) / 65535.0;
    }
}
