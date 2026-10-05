package com.junseo.city.vehicle.model;

import com.junseo.city.vehicle.model.ItemModel.Dir;
import com.junseo.city.vehicle.model.ItemModel.Element;
import com.junseo.city.vehicle.model.ItemModel.Face;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 아이템 모델 미리보기용 작은 그리기 도구 (원근 카메라, 깊이 버퍼, 가장 가까운 픽셀 그림, 뒷면 안 그림).
 * 빛은 마인크래프트가 엔티티·아이템에 쓰는 두 방향 빛과 같은 값을 씁니다. 바닥은 1 m 격자 콘크리트.
 */
final class ModelRaster {
    private record Quad(double[][] p, double[][] uv, Tex tex, double light) {
    }

    private final List<Quad> quads = new ArrayList<>();

    /** 모델을 장면에 넣음. toWorld: 모델 단위 점 → 월드(미터) */
    void add(ItemModel m, Tex tex, UnaryOperator<double[]> toWorld) {
        for (Element e : m.elements) {
            double[] f = e.from(), t = e.to();
            for (Map.Entry<Dir, Face> fe : e.faces().entrySet()) {
                double[][] c = corners(fe.getKey(), f, t);
                double[][] w = new double[4][];
                for (int i = 0; i < 4; i++) {
                    double[] p = e.axis() == null ? c[i] : rotate(c[i], e.axis(), e.angle(), e.origin());
                    w[i] = toWorld.apply(p);
                }
                Face face = fe.getValue();
                double[][] uv = {{face.u1(), face.v1()}, {face.u2(), face.v1()}, {face.u2(), face.v2()}, {face.u1(), face.v2()}};
                double[] n = normal(w);
                quads.add(new Quad(w, uv, tex, light(n)));
            }
        }
    }

    /** 면 네 모서리 (밖에서 볼 때 왼쪽 위, 오른쪽 위, 오른쪽 아래, 왼쪽 아래) */
    static double[][] corners(Dir d, double[] f, double[] t) {
        double x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
        return switch (d) {
            case SOUTH -> new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
            case NORTH -> new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            case EAST -> new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case WEST -> new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case UP -> new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            case DOWN -> new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
        };
    }

    static double[] rotate(double[] p, String axis, double deg, double[] o) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        double x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
        double[] r = switch (axis) {
            case "x" -> new double[]{x, y * c - z * s, y * s + z * c};
            case "y" -> new double[]{x * c + z * s, y, -x * s + z * c};
            default -> new double[]{x * c - y * s, x * s + y * c, z};
        };
        return new double[]{r[0] + o[0], r[1] + o[1], r[2] + o[2]};
    }

    private static double[] normal(double[][] w) {
        double[] a = sub(w[1], w[0]), b = sub(w[3], w[0]);
        double[] n = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
        double l = Math.sqrt(dot(n, n));
        return l == 0 ? new double[]{0, 1, 0} : new double[]{-n[0] / l, -n[1] / l, -n[2] / l};
    }

    private static final double[] L0 = norm(new double[]{0.2, 1.0, -0.7}), L1 = norm(new double[]{-0.2, 1.0, 0.7});

    private static double light(double[] n) {
        return Math.min(1, 0.4 + 0.6 * (Math.max(0, dot(n, L0)) + Math.max(0, dot(n, L1))));
    }

    static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** 카메라 eye 에서 target 을 봄, 세로 화각 fov 도 */
    BufferedImage render(double[] eye, double[] target, double fov, int width, int height, double shadowRx, double shadowRz) {
        int ss = 2, W = width * ss, H = height * ss;
        double[] fw = norm(sub(target, eye)), rt = norm(cross(fw, new double[]{0, 1, 0})), up = cross(rt, fw);
        double f = (H / 2.0) / Math.tan(Math.toRadians(fov) / 2);
        int[] rgb = new int[W * H];
        double[] depth = new double[W * H];
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                double sx = (px + 0.5 - W / 2.0) / f, sy = (H / 2.0 - py - 0.5) / f;
                double[] d = norm(new double[]{fw[0] + rt[0] * sx + up[0] * sy, fw[1] + rt[1] * sx + up[1] * sy, fw[2] + rt[2] * sx + up[2] * sy});
                int k = py * W + px;
                depth[k] = Double.MAX_VALUE;
                if (d[1] < -1e-6) {
                    double tt = -eye[1] / d[1];
                    double gx = eye[0] + d[0] * tt, gz = eye[2] + d[2] * tt;
                    double fx = gx - Math.floor(gx), fz = gz - Math.floor(gz);
                    int c = Math.min(fx, Math.min(1 - fx, Math.min(fz, 1 - fz))) < 0.012 ? 0xFF8C8C88 : 0xFFA9A9A3;
                    double sh = Math.hypot(gx / shadowRx, gz / shadowRz);
                    if (sh < 1.15) {
                        c = Tex.shade(c, 0.55 + 0.45 * Tex.smooth(0.75, 1.15, sh));
                    }
                    double fog = Math.min(0.6, tt / 60);
                    rgb[k] = Tex.mix(c, 0xFFC9D8E6, fog);
                    depth[k] = dot(sub(new double[]{gx, 0, gz}, eye), fw);
                } else {
                    rgb[k] = Tex.mix(0xFFDCE7F0, 0xFF8FB3D9, Math.min(1, d[1] * 2.5));
                }
            }
        }
        for (Quad q : quads) {
            double[] c0 = q.p[0];
            double[] n = normal(q.p);
            if (dot(n, sub(eye, c0)) <= 0) {
                continue;
            }
            tri(q, 0, 1, 2, eye, fw, rt, up, f, W, H, rgb, depth);
            tri(q, 0, 2, 3, eye, fw, rt, up, f, W, H, rgb, depth);
        }
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 0, g = 0, b = 0;
                for (int sy = 0; sy < ss; sy++) {
                    for (int sx = 0; sx < ss; sx++) {
                        int c = rgb[(y * ss + sy) * W + x * ss + sx];
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                    }
                }
                int n = ss * ss;
                img.setRGB(x, y, (r / n) << 16 | (g / n) << 8 | (b / n));
            }
        }
        return img;
    }

    private static void tri(Quad q, int a, int b, int c, double[] eye, double[] fw, double[] rt, double[] up, double f,
                            int W, int H, int[] rgb, double[] depth) {
        int[] idx = {a, b, c};
        double[] sx = new double[3], sy = new double[3], iz = new double[3];
        for (int i = 0; i < 3; i++) {
            double[] p = sub(q.p[idx[i]], eye);
            double z = dot(p, fw);
            if (z < 0.05) {
                return;
            }
            sx[i] = W / 2.0 + f * dot(p, rt) / z;
            sy[i] = H / 2.0 - f * dot(p, up) / z;
            iz[i] = 1 / z;
        }
        int x0 = (int) Math.max(0, Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2])))), x1 = (int) Math.min(W - 1, Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int y0 = (int) Math.max(0, Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2])))), y1 = (int) Math.min(H - 1, Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        double area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
        if (Math.abs(area) < 1e-9) {
            return;
        }
        Tex t = q.tex;
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                double cx = px + 0.5, cy = py + 0.5;
                double w0 = ((sx[1] - cx) * (sy[2] - cy) - (sx[2] - cx) * (sy[1] - cy)) / area;
                double w1 = ((sx[2] - cx) * (sy[0] - cy) - (sx[0] - cx) * (sy[2] - cy)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < -1e-9 || w1 < -1e-9 || w2 < -1e-9) {
                    continue;
                }
                double invz = w0 * iz[0] + w1 * iz[1] + w2 * iz[2];
                double z = 1 / invz;
                int k = py * W + px;
                if (z >= depth[k]) {
                    continue;
                }
                double u = (w0 * q.uv[idx[0]][0] * iz[0] + w1 * q.uv[idx[1]][0] * iz[1] + w2 * q.uv[idx[2]][0] * iz[2]) * z;
                double v = (w0 * q.uv[idx[0]][1] * iz[0] + w1 * q.uv[idx[1]][1] * iz[1] + w2 * q.uv[idx[2]][1] * iz[2]) * z;
                int tx = (int) Math.floor(u / 16 * t.w), ty = (int) Math.floor(v / 16 * t.h);
                tx = Math.max(0, Math.min(t.w - 1, tx));
                ty = Math.max(0, Math.min(t.h - 1, ty));
                int col = t.argb[ty * t.w + tx];
                if ((col >>> 24) < 128) {
                    continue;
                }
                depth[k] = z;
                rgb[k] = Tex.shade(col | 0xFF000000, q.light);
            }
        }
    }
}
