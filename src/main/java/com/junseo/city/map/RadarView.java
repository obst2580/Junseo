package com.junseo.city.map;

/**
 * 지도 화면 128×128 칸과 월드 좌표 사이의 변환.
 * rotate 면 GTA 레이더처럼 플레이어가 보는 방향이 위, 아니면 북쪽이 위입니다.
 */
public final class RadarView {
    public static final int SIZE = 128;

    private final double cx;
    private final double cz;
    private final double blocksPerPixel;
    private final boolean rotate;
    // 앞쪽(f)과 오른쪽(r) 방향 (월드 x, z)
    private final double fx, fz, rx, rz;

    /**
     * @param yawDegrees 마인크래프트 yaw (0 = 남쪽, 90 = 서쪽)
     */
    public RadarView(double cx, double cz, double blocksPerPixel, float yawDegrees, boolean rotate) {
        this.cx = cx;
        this.cz = cz;
        this.blocksPerPixel = blocksPerPixel;
        this.rotate = rotate;
        double yaw = Math.toRadians(yawDegrees);
        fx = -Math.sin(yaw);
        fz = Math.cos(yaw);
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
    }

    /** 화면 칸(가운데 기준) → 월드 좌표 */
    public double[] toWorld(double px, double py) {
        double dx = (px - SIZE / 2.0) * blocksPerPixel, dy = (py - SIZE / 2.0) * blocksPerPixel;
        if (!rotate) {
            return new double[]{cx + dx, cz + dy};
        }
        return new double[]{cx + rx * dx - fx * dy, cz + rz * dx - fz * dy};
    }

    /** 월드 좌표 → 화면 칸 (화면 밖이면 0~128 을 벗어남) */
    public double[] toScreen(double wx, double wz) {
        double ox = (wx - cx) / blocksPerPixel, oz = (wz - cz) / blocksPerPixel;
        if (!rotate) {
            return new double[]{SIZE / 2.0 + ox, SIZE / 2.0 + oz};
        }
        double dx = ox * rx + oz * rz;
        double dy = -(ox * fx + oz * fz);
        return new double[]{SIZE / 2.0 + dx, SIZE / 2.0 + dy};
    }

    /** 화면 전체를 칸 종류로 채웁니다 (out 길이 128×128) */
    public void draw(RadarRaster raster, byte[] out) {
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                double dx = (px + 0.5 - SIZE / 2.0) * blocksPerPixel, dy = (py + 0.5 - SIZE / 2.0) * blocksPerPixel;
                double wx, wz;
                if (rotate) {
                    wx = cx + rx * dx - fx * dy;
                    wz = cz + rz * dx - fz * dy;
                } else {
                    wx = cx + dx;
                    wz = cz + dy;
                }
                out[py * SIZE + px] = raster.at(wx, wz);
            }
        }
    }

    /** 화면 칸 → 지도 아이콘 좌표 (-128 ~ 127) */
    public static byte cursor(double screen) {
        return (byte) Math.max(-128, Math.min(127, Math.round(screen * 2 - SIZE)));
    }

    /** 화면 밖이면 가장자리로 끌어옵니다. 결과 [x, y, 화면밖이면 1] */
    public static double[] clampToEdge(double[] screen, double margin) {
        double c = SIZE / 2.0, dx = screen[0] - c, dy = screen[1] - c;
        double limit = c - margin;
        double m = Math.max(Math.abs(dx), Math.abs(dy));
        if (m <= limit) {
            return new double[]{screen[0], screen[1], 0};
        }
        double k = limit / m;
        return new double[]{c + dx * k, c + dy * k, 1};
    }

    /** 마인크래프트 지도 아이콘 방향 (0~15). 바닐라와 같은 계산 */
    public static byte direction(float yawDegrees) {
        return (byte) Math.floorMod(Math.round(yawDegrees / 22.5f), 16);
    }

    /** rotate 화면에서 내 화살표는 언제나 위를 향함 */
    public static final byte UP = 8;
}
