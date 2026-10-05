package com.junseo.citymap.buildings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 한글 간판 글씨: 미리 뽑아 둔 글자 그림(16칸 = Unifont, 12칸 = 문천이 정흑)을 블록으로 찍습니다.
 * 글자 그림은 tools/gen_hangul_glyphs.py 가 만든 resources/buildings/hangul-glyphs.txt 에 있고,
 * 거기 없는 글자를 쓰면 예외가 납니다 (검사가 잡아 줌).
 */
final class HangulFont {
    private record Glyph(int width, long[] rows) {
    }

    private static volatile Map<String, Glyph> glyphs;

    private static Map<String, Glyph> glyphs() {
        Map<String, Glyph> g = glyphs;
        if (g == null) {
            synchronized (HangulFont.class) {
                g = glyphs;
                if (g == null) {
                    g = load();
                    glyphs = g;
                }
            }
        }
        return g;
    }

    private static Map<String, Glyph> load() {
        Map<String, Glyph> out = new HashMap<>();
        try (InputStream in = HangulFont.class.getResourceAsStream("/buildings/hangul-glyphs.txt")) {
            if (in == null) {
                throw new IllegalStateException("buildings/hangul-glyphs.txt 가 없어요");
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split(" ");
                int size = Integer.parseInt(p[0]);
                long[] rows = new long[p.length - 3];
                for (int k = 0; k < rows.length; k++) {
                    rows[k] = Long.parseLong(p[k + 3], 16);
                }
                out.put(size + p[1], new Glyph(Integer.parseInt(p[2]), rows));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** 이 크기로 이 글자를 쓸 수 있는지 */
    static boolean has(int size, char ch) {
        return ch == ' ' || glyphs().containsKey(size + String.valueOf(ch));
    }

    /** 글씨 폭 (칸). 글자 사이 1칸 */
    static int width(int size, String text) {
        return text.isEmpty() ? 0 : text.length() * (size + 1) - 1;
    }

    /**
     * 세로 면에 글씨를 씁니다. 첫 글자 왼쪽 위가 (i, yTop, j), (di, dj) 방향으로 나아갑니다.
     * 남쪽을 보는 면이면 (1, 0), 북쪽이면 (-1, 0), 동쪽이면 (0, -1), 서쪽이면 (0, 1).
     */
    static void draw(Voxels v, int size, String text, int i, int yTop, int j, int di, int dj, Block fg) {
        int at = 0;
        for (char ch : text.toCharArray()) {
            if (ch != ' ') {
                Glyph g = glyphs().get(size + String.valueOf(ch));
                if (g == null) {
                    throw new IllegalArgumentException("간판 글자 그림이 없어요: '" + ch + "' (" + size
                            + "칸). tools/gen_hangul_glyphs.py 의 WORDS 에 넣고 다시 만드세요");
                }
                for (int row = 0; row < g.rows.length; row++) {
                    for (int col = 0; col < g.width; col++) {
                        if ((g.rows[row] >> (g.width - 1 - col) & 1) != 0) {
                            v.set(i + (at + col) * di, yTop - row, j + (at + col) * dj, fg);
                        }
                    }
                }
            }
            at += size + 1;
        }
    }

    /**
     * 간판 판: 바탕(테두리 1칸 포함)과 글씨. (ci, yTop, cj) 는 판의 가운데 위,
     * 판은 면 위치 + out 방향으로 1칸, 글씨는 2칸 나와 있습니다.
     *
     * @return 판 폭
     */
    static int board(Voxels v, int size, String text, int ci, int yTop, int cj, int di, int dj, int oi, int oj,
                     Block bg, Block border, Block fg) {
        int w = width(size, text) + 4, h = size + 2;
        int start = -w / 2;
        for (int t = start; t < start + w; t++) {
            for (int y = yTop - h + 1; y <= yTop; y++) {
                boolean edge = t == start || t == start + w - 1 || y == yTop || y == yTop - h + 1;
                v.set(ci + t * di + oi, y, cj + t * dj + oj, edge ? border : bg);
            }
        }
        draw(v, size, text, ci + (start + 2) * di + 2 * oi, yTop - 1, cj + (start + 2) * dj + 2 * oj, di, dj, fg);
        return w;
    }

    private HangulFont() {
    }
}
