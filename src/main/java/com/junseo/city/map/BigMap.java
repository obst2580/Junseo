package com.junseo.city.map;

import java.util.ArrayList;
import java.util.List;

/**
 * F키로 여는 큰 지도 (GTA 일시정지 지도). 대화창 본문 한 줄에 글자로 그립니다.
 * 북쪽이 위입니다. 지도 위에는 보이지 않는 16×16 칸(구역 칸)과 장소 아이콘이 있어서
 * 마우스를 올리면 이름이 나오고 누르면 길 안내를 겁니다 (이름·동작은 MapService 가 붙임).
 */
public final class BigMap {
    public static final int W = 256;
    public static final int H = 144;
    public static final int ZONE = MapGlyphs.HOTSPOT;
    public static final int ZONE_COLS = W / ZONE;
    public static final int ZONE_ROWS = H / ZONE;
    /** 대화창 글 줄 윗변에서 지도 0번 줄까지 (ascent = 1 − row 규칙) */
    public static final int TOP_OFFSET = 6;

    /** 글자열 조각. zone / place 가 -1 이 아니면 그 칸·장소의 마우스 동작을 붙입니다 */
    public record Segment(String text, int zone, int place) {
    }

    /** 지도에 그릴 장소 */
    public record Place(String id, String name, int icon, double x, double z) {
    }

    /** 다 그린 지도 */
    public record Picture(List<Segment> segments, int maxCursor, int runs) {
    }

    /** 구역 칸 번호 → 화면 칸 가운데 */
    public static double[] zoneCenter(int zone) {
        int col = zone % ZONE_COLS, row = zone / ZONE_COLS;
        return new double[]{col * ZONE + ZONE / 2.0, row * ZONE + ZONE / 2.0};
    }

    /** 이 화면에서 쓸 보기 (북쪽이 위) */
    public static Viewport view(double cx, double cz, double bpp) {
        return new Viewport(cx, cz, bpp, 0, false, W / 2.0, H / 2.0);
    }

    /**
     * @param places   장소 (아이콘 번호가 -1 이면 안 그림)
     * @param waypoint 길 안내 목적지 (없으면 null)
     * @param me       내 위치·방향 {x, z, yaw} (다른 월드면 null)
     */
    public static Picture draw(RadarRaster raster, Viewport view, List<Place> places, double[] waypoint, double[] me) {
        List<Segment> out = new ArrayList<>();
        GlyphWriter w = new GlyphWriter();
        byte[] kinds = new byte[W];
        int runs = 0;
        for (int y = 0; y < H; y++) {
            view.sampleRow(raster, y, 0, W, kinds);
            runs += w.row(MapGlyphs.BIG, y, kinds, 0, W, 0);
        }
        int max = w.maxCursor();
        StringBuilder plain = new StringBuilder(w.toString());
        int cursor = w.cursor();

        // 보이지 않는 구역 칸 (아이콘보다 먼저: 겹치면 아이콘이 이김)
        for (int zone = 0; zone < ZONE_COLS * ZONE_ROWS; zone++) {
            int col = zone % ZONE_COLS, row = zone / ZONE_COLS;
            GlyphWriter m = new GlyphWriter();
            m.move(col * ZONE - cursor);
            plain.append(m);
            out.add(new Segment(plain.toString(), -1, -1));
            plain.setLength(0);
            out.add(new Segment(new String(Character.toChars(MapGlyphs.hotspot(row))), zone, -1));
            cursor = col * ZONE + MapGlyphs.hotspotAdvance();
            max = Math.max(max, cursor);
        }

        // 장소 아이콘 (마우스를 올리면 이름)
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            if (p.icon() < 0) {
                continue;
            }
            double[] s = view.toScreen(p.x(), p.z());
            if (!onMap(s)) {
                continue;
            }
            int left = left(s);
            GlyphWriter m = new GlyphWriter();
            m.move(left - cursor);
            plain.append(m);
            out.add(new Segment(plain.toString(), -1, -1));
            plain.setLength(0);
            out.add(new Segment(new String(Character.toChars(MapGlyphs.icon(top(s), p.icon()))), -1, i));
            cursor = left + MapIcons.advance(p.icon());
            max = Math.max(max, cursor);
        }
        // 길 안내 목적지 (장소 아이콘 위에: 장소의 마우스 동작은 그대로 남음)
        if (waypoint != null) {
            double[] s = view.toScreen(waypoint[0], waypoint[1]);
            if (onMap(s)) {
                cursor = icon(plain, cursor, s, MapIcons.WAYPOINT);
                max = Math.max(max, cursor);
            }
        }
        // 나
        if (me != null) {
            double[] s = view.toScreen(me[0], me[1]);
            s[0] = Math.max(3, Math.min(W - 4, s[0]));
            s[1] = Math.max(3, Math.min(H - 4, s[1]));
            cursor = icon(plain, cursor, s, MapIcons.arrowForYaw((float) me[2]));
            max = Math.max(max, cursor);
        }
        GlyphWriter end = new GlyphWriter();
        end.move(W - cursor);
        plain.append(end);
        out.add(new Segment(plain.toString(), -1, -1));
        return new Picture(out, max, runs);
    }

    private static boolean onMap(double[] s) {
        return s[0] >= 3 && s[0] <= W - 4 && s[1] >= 3 && s[1] <= H - 4;
    }

    private static int left(double[] s) {
        return (int) Math.floor(s[0]) - MapIcons.SIZE / 2;
    }

    private static int top(double[] s) {
        return (int) Math.floor(s[1]) - MapIcons.SIZE / 2;
    }

    /** plain 에 아이콘을 붙이고 새 커서를 돌려줌 */
    private static int icon(StringBuilder plain, int cursor, double[] s, int icon) {
        GlyphWriter m = new GlyphWriter();
        m.move(left(s) - cursor);
        m.put(MapGlyphs.icon(top(s), icon), MapIcons.advance(icon));
        plain.append(m);
        return cursor + m.cursor();
    }

    /** "공항 터미널 (스폰·입국 심사)" → "공항 터미널" */
    public static String shortName(String name) {
        int i = name.indexOf(" (");
        return i > 0 ? name.substring(0, i) : name;
    }

    private BigMap() {
    }
}
