package com.junseo.city.map;

import java.util.List;

/**
 * 화면 아래에 늘 떠 있는 GTA 미니맵(레이더)을 액션바 글자열로 만듭니다.
 * 보는 방향이 위이고, 둥근 네모 모양입니다. 글자열은 미니맵 왼쪽 끝에서 시작해서 같은 자리로 돌아옵니다(폭 0).
 */
public final class RadarHud {
    public static final int W = 96;
    public static final int H = 64;
    /** 내 위치 (가운데보다 조금 아래: 앞쪽을 더 넓게) */
    public static final int CENTER_X = W / 2;
    public static final int CENTER_Y = 36;
    static final int CORNER = 10;
    /** 아이콘 가운데가 이만큼 안쪽에 있어야 그립니다 */
    private static final int ICON_INSET = 5;

    private static final int[] FROM = new int[H];
    private static final int[] TO = new int[H];
    private static final int[] FRAME_PIXELS;
    private static final int FRAME_ADVANCE;

    /** 지도에 표시할 곳 */
    public record Marker(double x, double z, int icon) {
    }

    static {
        for (int y = 0; y < H; y++) {
            double d = Math.max(0, CORNER - 0.5 - Math.min(y, H - 1 - y));
            int inset = (int) Math.round(CORNER - Math.sqrt(Math.max(0, CORNER * CORNER - d * d)));
            FROM[y] = inset;
            TO[y] = W - inset;
        }
        int fw = W + 2, fh = H + 2;
        FRAME_PIXELS = new int[fw * fh];
        for (int fy = 0; fy < fh; fy++) {
            for (int fx = 0; fx < fw; fx++) {
                int x = fx - 1, y = fy - 1;
                if (inside(x, y)) {
                    continue;
                }
                boolean edge = false;
                for (int dy = -1; dy <= 1 && !edge; dy++) {
                    for (int dx = -1; dx <= 1 && !edge; dx++) {
                        edge = inside(x + dx, y + dy);
                    }
                }
                if (edge) {
                    FRAME_PIXELS[fy * fw + fx] = 0xE0101010;
                }
            }
        }
        int adv = 1;
        for (int fx = fw - 1; fx >= 0 && adv == 1; fx--) {
            for (int fy = 0; fy < fh; fy++) {
                if (FRAME_PIXELS[fy * fw + fx] != 0) {
                    adv = fx + 2;
                    break;
                }
            }
        }
        FRAME_ADVANCE = adv;
    }

    /** 미니맵 모양 안쪽인지 */
    public static boolean inside(int x, int y) {
        return y >= 0 && y < H && x >= FROM[y] && x < TO[y];
    }

    /** 테두리 그림 ((W+2)×(H+2) ARGB) */
    public static int[] framePixels() {
        return FRAME_PIXELS.clone();
    }

    public static int frameAdvance() {
        return FRAME_ADVANCE;
    }

    /**
     * @param raster   도시 그림
     * @param x        내 위치
     * @param yaw      보는 방향 (마인크래프트 yaw)
     * @param bpp      한 칸이 몇 블록인지 (클수록 넓게 보임)
     * @param places   주요 장소
     * @param waypoint 길 안내 목적지 (없으면 null). 미니맵 밖이면 가장자리에 붙여서 그립니다
     */
    public static String build(RadarRaster raster, double x, double z, float yaw, double bpp,
                               List<Marker> places, Marker waypoint) {
        Viewport view = new Viewport(x, z, bpp, yaw, true, CENTER_X, CENTER_Y);
        GlyphWriter w = new GlyphWriter();
        byte[] kinds = new byte[W];
        for (int y = 0; y < H; y++) {
            view.sampleRow(raster, y, FROM[y], TO[y], kinds);
            w.row(MapGlyphs.HUD, y, kinds, FROM[y], TO[y], 0);
        }
        w.moveTo(-1);
        w.put(MapGlyphs.FRAME, FRAME_ADVANCE);

        for (Marker m : places) {
            double[] s = view.toScreen(m.x(), m.z());
            if (s[0] >= ICON_INSET && s[0] <= W - ICON_INSET && s[1] >= ICON_INSET && s[1] <= H - ICON_INSET) {
                icon(w, s, m.icon());
            }
        }
        if (waypoint != null) {
            icon(w, clamp(view.toScreen(waypoint.x(), waypoint.z())), waypoint.icon());
        }
        // 북쪽 표시: 내 위치에서 북쪽(Z−)으로 쭉 간 곳을 가장자리에
        double[] north = view.toScreen(x, z - 100_000);
        icon(w, clamp(north), MapIcons.NORTH);
        icon(w, new double[]{CENTER_X + 0.5, CENTER_Y + 0.5}, MapIcons.PLAYER_UP);
        w.moveTo(0);
        return w.toString();
    }

    private static void icon(GlyphWriter w, double[] screen, int icon) {
        int left = (int) Math.floor(screen[0]) - MapIcons.SIZE / 2;
        int top = (int) Math.floor(screen[1]) - MapIcons.SIZE / 2;
        w.icon(left, top, icon);
    }

    /** 미니맵 밖이면 가운데에서 그쪽으로 가다가 가장자리(안쪽 여백 ICON_INSET)에서 멈춘 곳 */
    static double[] clamp(double[] s) {
        double minX = ICON_INSET, maxX = W - ICON_INSET, minY = ICON_INSET, maxY = H - ICON_INSET;
        if (s[0] >= minX && s[0] <= maxX && s[1] >= minY && s[1] <= maxY) {
            return s;
        }
        double cx = CENTER_X + 0.5, cy = CENTER_Y + 0.5;
        double dx = s[0] - cx, dy = s[1] - cy;
        double t = 1;
        if (dx > 0) {
            t = Math.min(t, (maxX - cx) / dx);
        } else if (dx < 0) {
            t = Math.min(t, (minX - cx) / dx);
        }
        if (dy > 0) {
            t = Math.min(t, (maxY - cy) / dy);
        } else if (dy < 0) {
            t = Math.min(t, (minY - cy) / dy);
        }
        return new double[]{cx + dx * t, cy + dy * t};
    }

    private RadarHud() {
    }
}
