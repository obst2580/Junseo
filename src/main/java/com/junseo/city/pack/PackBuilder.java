package com.junseo.city.pack;

import com.junseo.city.map.MapGlyphs;
import com.junseo.city.map.MapIcons;
import com.junseo.city.map.RadarHud;
import com.junseo.city.map.RadarPalette;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 서버 리소스팩(zip)을 만듭니다. 미니맵·큰 지도를 그리는 글꼴(junseocity:map)과 자동차 모델이 들어 있습니다.
 * 같은 내용이면 언제나 같은 zip(같은 SHA-1)이 나와서, 플레이어는 처음 한 번만 받습니다.
 */
public final class PackBuilder {
    /** 리소스팩 형식: 1.21.9 이후(새 형식)부터 앞으로 나올 버전까지 */
    static final int MIN_FORMAT = 65;
    static final int MAX_FORMAT = 1000;
    private static final LocalDateTime ZIP_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    /** 완성된 리소스팩 */
    public record Pack(byte[] zip, String sha1, FontModel font) {
    }

    /** 글자 하나 그림 (배율 1: 그림 1칸 = 화면 1칸) */
    public record Glyph(int width, int height, int[] argb, int ascent, int advance) {
    }

    /** 글꼴 안의 모든 글자. 테스트에서 클라이언트처럼 그려 볼 때 씁니다 */
    public record FontModel(Map<Integer, Glyph> bitmaps, Map<Integer, Integer> spaces) {
        public int advance(int codepoint) {
            Glyph g = bitmaps.get(codepoint);
            if (g != null) {
                return g.advance();
            }
            Integer s = spaces.get(codepoint);
            if (s == null) {
                throw new IllegalArgumentException("unknown glyph U+" + Integer.toHexString(codepoint));
            }
            return s;
        }
    }

    /** bitmap 글꼴 한 묶음 */
    private record Provider(String file, int ascent, int height, int[][] chars) {
    }

    private record Image(int width, int height, int[] argb) {
    }

    private final Map<String, Image> images = new LinkedHashMap<>();
    private final List<Provider> providers = new ArrayList<>();

    public static Pack build() {
        return new PackBuilder().make();
    }

    private Pack make() {
        // 색 막대: 그림 칸 폭(16/64/256)별로 묶어서 같은 그림을 모든 줄이 같이 씀
        for (MapGlyphs.RunSet set : List.of(MapGlyphs.HUD, MapGlyphs.BIG)) {
            Map<Integer, List<Integer>> groups = new TreeMap<>();
            for (int i = 0; i < set.lengthCount(); i++) {
                groups.computeIfAbsent(cell(set.length(i)), k -> new ArrayList<>()).add(i);
            }
            for (Map.Entry<Integer, List<Integer>> g : groups.entrySet()) {
                int cell = g.getKey();
                List<Integer> idx = g.getValue();
                StringBuilder name = new StringBuilder("runs_" + cell);
                for (int i : idx) {
                    name.append('_').append(set.length(i));
                }
                String file = name + ".png";
                images.computeIfAbsent(file, f -> runImage(set, cell, idx));
                for (int row = 0; row < set.rows(); row++) {
                    int[][] chars = new int[MapGlyphs.KINDS][idx.size()];
                    for (int k = 0; k < MapGlyphs.KINDS; k++) {
                        for (int c = 0; c < idx.size(); c++) {
                            chars[k][c] = set.codepoint(row, k, idx.get(c));
                        }
                    }
                    providers.add(new Provider(file, 1 - row, 1, chars));
                }
            }
        }

        // 아이콘: 줄마다 (윗변 = row)
        int n = MapIcons.COUNT, s = MapIcons.SIZE;
        int[] icons = new int[n * s * s];
        for (int i = 0; i < n; i++) {
            int[] p = MapIcons.pixels(i);
            for (int y = 0; y < s; y++) {
                System.arraycopy(p, y * s, icons, y * n * s + i * s, s);
            }
        }
        images.put("icons.png", new Image(n * s, s, icons));
        for (int row = MapGlyphs.ICON_MIN_ROW; row <= MapGlyphs.ICON_MAX_ROW; row++) {
            int[][] chars = new int[1][n];
            for (int i = 0; i < n; i++) {
                chars[0][i] = MapGlyphs.icon(row, i);
            }
            providers.add(new Provider("icons.png", 1 - row, s, chars));
        }
        // 범례 아이콘: 보통 글자처럼 글 줄 안에
        int[][] legend = new int[1][n];
        for (int i = 0; i < n; i++) {
            legend[0][i] = MapGlyphs.legend(i);
        }
        providers.add(new Provider("icons.png", 7, s, legend));

        // 미니맵 테두리: 0번 줄 한 칸 위에서 시작
        images.put("frame.png", new Image(RadarHud.W + 2, RadarHud.H + 2, RadarHud.framePixels()));
        providers.add(new Provider("frame.png", 2, RadarHud.H + 2, new int[][]{{MapGlyphs.FRAME}}));

        // 큰 지도 마우스 칸: 거의 투명한 점(알파 1)으로 채워서 폭이 생기게 (화면에는 안 보임)
        int hs = MapGlyphs.HOTSPOT;
        int[] hot = new int[hs * hs];
        java.util.Arrays.fill(hot, 0x01000000);
        images.put("hotspot.png", new Image(hs, hs, hot));
        for (int j = 0; j < MapGlyphs.hotspotRows(); j++) {
            providers.add(new Provider("hotspot.png", 1 - j * hs, hs, new int[][]{{MapGlyphs.hotspot(j)}}));
        }

        Map<Integer, Integer> spaces = new TreeMap<>();
        for (int[] sp : MapGlyphs.spaces()) {
            spaces.put(sp[0], sp[1]);
        }

        byte[] zip = zip(fontJson(spaces));
        return new Pack(zip, sha1(zip), model(spaces));
    }

    private static int cell(int length) {
        return length <= 16 ? 16 : length <= 64 ? 64 : 256;
    }

    private static Image runImage(MapGlyphs.RunSet set, int cell, List<Integer> idx) {
        int w = cell * idx.size(), h = MapGlyphs.KINDS;
        int[] px = new int[w * h];
        for (int k = 0; k < h; k++) {
            int color = 0xFF000000 | RadarPalette.color((byte) k).getRGB();
            for (int c = 0; c < idx.size(); c++) {
                int len = set.length(idx.get(c));
                for (int x = 0; x < len; x++) {
                    px[k * w + c * cell + x] = color;
                }
            }
        }
        return new Image(w, h, px);
    }

    // ------------------------------------------------------------------ 파일 쓰기

    private String fontJson(Map<Integer, Integer> spaces) {
        StringBuilder j = new StringBuilder("{\"providers\":[\n");
        j.append("{\"type\":\"space\",\"advances\":{");
        boolean first = true;
        for (Map.Entry<Integer, Integer> e : spaces.entrySet()) {
            if (!first) {
                j.append(',');
            }
            first = false;
            j.append('"').appendCodePoint(e.getKey()).append("\":").append(e.getValue());
        }
        j.append("}}");
        for (Provider p : providers) {
            j.append(",\n{\"type\":\"bitmap\",\"file\":\"junseocity:map/").append(p.file())
                    .append("\",\"ascent\":").append(p.ascent())
                    .append(",\"height\":").append(p.height())
                    .append(",\"chars\":[");
            for (int r = 0; r < p.chars().length; r++) {
                if (r > 0) {
                    j.append(',');
                }
                j.append('"');
                for (int cp : p.chars()[r]) {
                    j.appendCodePoint(cp);
                }
                j.append('"');
            }
            j.append("]}");
        }
        j.append("\n]}\n");
        return j.toString();
    }

    private byte[] zip(String fontJson) {
        Map<String, byte[]> files = new TreeMap<>();
        files.put("pack.mcmeta", ("{\"pack\":{\"description\":\"준서 시티 미니맵·지도·자동차\",\"min_format\":" + MIN_FORMAT
                + ",\"max_format\":" + MAX_FORMAT + "}}\n").getBytes(StandardCharsets.UTF_8));
        files.put("assets/junseocity/font/map.json", fontJson.getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, Image> e : images.entrySet()) {
            files.put("assets/junseocity/textures/map/" + e.getKey(), png(e.getValue()));
        }
        files.putAll(com.junseo.city.vehicle.model.CarModels.packFiles());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTimeLocal(ZIP_TIME);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static byte[] png(Image image) {
        BufferedImage img = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, image.width(), image.height(), image.argb(), 0, image.width());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ 클라이언트 계산 흉내

    /** 클라이언트와 같은 규칙으로 글자마다 그림과 폭을 계산합니다 (폭 = 가장 오른쪽 불투명 열 + 1, 그리고 +1) */
    private FontModel model(Map<Integer, Integer> spaces) {
        Map<Integer, Glyph> glyphs = new HashMap<>();
        for (Provider p : providers) {
            Image img = images.get(p.file());
            int rows = p.chars().length, cols = p.chars()[0].length;
            int cw = img.width() / cols, ch = img.height() / rows;
            if (ch != p.height()) {
                throw new IllegalStateException("배율이 1이 아님: " + p.file());
            }
            if (p.ascent() > p.height()) {
                throw new IllegalStateException("ascent " + p.ascent() + " > height " + p.height() + ": " + p.file());
            }
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int[] cellPx = new int[cw * ch];
                    int actual = 0;
                    for (int y = 0; y < ch; y++) {
                        for (int x = 0; x < cw; x++) {
                            int v = img.argb()[(r * ch + y) * img.width() + c * cw + x];
                            cellPx[y * cw + x] = v;
                            if ((v >>> 24) != 0) {
                                actual = Math.max(actual, x + 1);
                            }
                        }
                    }
                    Glyph prev = glyphs.put(p.chars()[r][c], new Glyph(cw, ch, cellPx, p.ascent(), actual + 1));
                    if (prev != null || spaces.containsKey(p.chars()[r][c])) {
                        throw new IllegalStateException("글자 겹침 U+" + Integer.toHexString(p.chars()[r][c]));
                    }
                }
            }
        }
        return new FontModel(glyphs, spaces);
    }
}
