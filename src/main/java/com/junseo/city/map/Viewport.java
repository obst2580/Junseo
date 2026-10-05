package com.junseo.city.map;

/**
 * 지도 화면 칸과 월드 좌표 사이의 변환.
 * rotate 면 GTA 레이더처럼 플레이어가 보는 방향이 위, 아니면 북쪽이 위입니다.
 * 화면 칸 (sx, sy) 는 왼쪽 위가 (0, 0), 오른쪽·아래로 커집니다. 월드 (cx, cz) 가 화면 (screenX, screenY) 에 옵니다.
 */
public final class Viewport {
    private final double cx;
    private final double cz;
    private final double blocksPerPixel;
    private final boolean rotate;
    private final double screenX;
    private final double screenY;
    // 앞쪽(f)과 오른쪽(r) 방향 (월드 x, z)
    private final double fx, fz, rx, rz;

    /**
     * @param yawDegrees 마인크래프트 yaw (0 = 남쪽, 90 = 서쪽). rotate 가 아니면 쓰지 않음
     */
    public Viewport(double cx, double cz, double blocksPerPixel, float yawDegrees, boolean rotate,
                    double screenX, double screenY) {
        this.cx = cx;
        this.cz = cz;
        this.blocksPerPixel = blocksPerPixel;
        this.rotate = rotate;
        this.screenX = screenX;
        this.screenY = screenY;
        double yaw = Math.toRadians(yawDegrees);
        fx = -Math.sin(yaw);
        fz = Math.cos(yaw);
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
    }

    public double blocksPerPixel() {
        return blocksPerPixel;
    }

    public double centerX() {
        return cx;
    }

    public double centerZ() {
        return cz;
    }

    /** 화면 칸 좌표 → 월드 좌표 */
    public double[] toWorld(double sx, double sy) {
        double dx = (sx - screenX) * blocksPerPixel, dy = (sy - screenY) * blocksPerPixel;
        if (!rotate) {
            return new double[]{cx + dx, cz + dy};
        }
        return new double[]{cx + rx * dx - fx * dy, cz + rz * dx - fz * dy};
    }

    /** 월드 좌표 → 화면 칸 좌표 */
    public double[] toScreen(double wx, double wz) {
        double ox = (wx - cx) / blocksPerPixel, oz = (wz - cz) / blocksPerPixel;
        if (!rotate) {
            return new double[]{screenX + ox, screenY + oz};
        }
        return new double[]{screenX + ox * rx + oz * rz, screenY - (ox * fx + oz * fz)};
    }

    /** 화면 한 줄(sy)의 칸 [from, to) 를 칸 종류로 채웁니다. 칸 가운데 점을 봅니다 */
    public void sampleRow(RadarRaster raster, int sy, int from, int to, byte[] out) {
        double dy = (sy + 0.5 - screenY) * blocksPerPixel;
        for (int sx = from; sx < to; sx++) {
            double dx = (sx + 0.5 - screenX) * blocksPerPixel;
            out[sx] = rotate
                    ? raster.at(cx + rx * dx - fx * dy, cz + rz * dx - fz * dy)
                    : raster.at(cx + dx, cz + dy);
        }
    }
}
