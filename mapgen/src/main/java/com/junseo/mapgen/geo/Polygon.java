package com.junseo.mapgen.geo;

import java.util.ArrayList;
import java.util.List;

/** 다각형 (구역, 바다). 안에 있는지와 테두리까지의 거리를 구합니다. */
public final class Polygon {
    private final double[] xs;
    private final double[] zs;
    private final double minX;
    private final double minZ;
    private final double maxX;
    private final double maxZ;

    public Polygon(List<double[]> points) {
        if (points.size() < 3) {
            throw new IllegalArgumentException("다각형에는 점이 3개 이상 있어야 해요");
        }
        int n = points.size();
        xs = new double[n];
        zs = new double[n];
        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i)[0];
            zs[i] = points.get(i)[1];
            x0 = Math.min(x0, xs[i]);
            z0 = Math.min(z0, zs[i]);
            x1 = Math.max(x1, xs[i]);
            z1 = Math.max(z1, zs[i]);
        }
        minX = x0;
        minZ = z0;
        maxX = x1;
        maxZ = z1;
    }

    /** 타원을 다각형으로 바꿉니다. angle 은 도 단위, 시계 방향(+Z 쪽)이 양수입니다. */
    public static Polygon ellipse(double cx, double cz, double rx, double rz, double angleDegrees, int steps) {
        double a = Math.toRadians(angleDegrees);
        double cos = Math.cos(a), sin = Math.sin(a);
        List<double[]> pts = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            double t = 2 * Math.PI * i / steps;
            double x = rx * Math.cos(t), z = rz * Math.sin(t);
            pts.add(new double[]{cx + x * cos - z * sin, cz + x * sin + z * cos});
        }
        return new Polygon(pts);
    }

    public double[] bounds() {
        return new double[]{minX, minZ, maxX, maxZ};
    }

    public boolean contains(double x, double z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        boolean inside = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((zs[i] > z) != (zs[j] > z)
                    && x < (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 테두리(변)까지의 가장 짧은 거리 */
    public double edgeDistance(double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            double ax = xs[j], az = zs[j];
            double dx = xs[i] - ax, dz = zs[i] - az;
            double len2 = dx * dx + dz * dz;
            double t = len2 == 0 ? 0 : ((x - ax) * dx + (z - az) * dz) / len2;
            t = Math.max(0, Math.min(1, t));
            best = Math.min(best, Math.hypot(x - (ax + t * dx), z - (az + t * dz)));
        }
        return best;
    }

    /** 안에 있으면 테두리까지 거리, 밖이면 0 */
    public double insideDistance(double x, double z) {
        return contains(x, z) ? edgeDistance(x, z) : 0;
    }
}
