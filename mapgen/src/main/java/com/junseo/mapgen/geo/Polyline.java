package com.junseo.mapgen.geo;

import java.util.List;

/** 꺾은선 (한강, 도로). 점에서 가장 가까운 곳까지의 거리와, 선을 따라 잰 위치를 구합니다. */
public final class Polyline {
    private final double[] xs;
    private final double[] zs;
    /** cum[i] = 0번 꼭짓점부터 i번 꼭짓점까지 선을 따라 잰 길이 */
    private final double[] cum;

    public Polyline(List<double[]> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("꺾은선에는 점이 2개 이상 있어야 해요");
        }
        int n = points.size();
        xs = new double[n];
        zs = new double[n];
        cum = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i)[0];
            zs[i] = points.get(i)[1];
            if (i > 0) {
                cum[i] = cum[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            }
        }
    }

    public int segmentCount() {
        return xs.length - 1;
    }

    public double length() {
        return cum[cum.length - 1];
    }

    /** i번 선분의 경계 상자 [minX, minZ, maxX, maxZ] */
    public double[] segmentBounds(int i) {
        return new double[]{Math.min(xs[i], xs[i + 1]), Math.min(zs[i], zs[i + 1]),
                Math.max(xs[i], xs[i + 1]), Math.max(zs[i], zs[i + 1])};
    }

    /**
     * i번 선분까지의 거리. 결과는 out[0] = 거리, out[1] = 선을 따라 잰 위치.
     */
    public void distanceToSegment(int i, double x, double z, double[] out) {
        double ax = xs[i], az = zs[i];
        double dx = xs[i + 1] - ax, dz = zs[i + 1] - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 == 0 ? 0 : ((x - ax) * dx + (z - az) * dz) / len2;
        t = Math.max(0, Math.min(1, t));
        double px = ax + t * dx, pz = az + t * dz;
        out[0] = Math.hypot(x - px, z - pz);
        out[1] = cum[i] + t * Math.sqrt(len2);
    }

    /** 전체 선분 중 가장 가까운 곳. out[0] = 거리, out[1] = 선을 따라 잰 위치 */
    public void nearest(double x, double z, double[] out) {
        double best = Double.MAX_VALUE, along = 0;
        double[] tmp = new double[2];
        for (int i = 0; i < segmentCount(); i++) {
            distanceToSegment(i, x, z, tmp);
            if (tmp[0] < best) {
                best = tmp[0];
                along = tmp[1];
            }
        }
        out[0] = best;
        out[1] = along;
    }
}
