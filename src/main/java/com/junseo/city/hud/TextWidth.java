package com.junseo.city.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 마인크래프트 기본 글꼴로 쓴 글자의 화면 폭(칸) 계산.
 * 액션바는 글자열 전체 폭의 가운데에 맞춰 그려지므로, 미니맵을 제자리에 두려면 함께 보낸 메시지 폭을 알아야 합니다.
 * 표는 클라이언트 글꼴 파일에서 뽑았습니다 (tools/gen_font_widths.py).
 */
public final class TextWidth {
    private static final byte[] WIDTH = new byte[0x10000];
    /** true 면 unifont 글자 (굵게 하면 +0.5), 아니면 +1 */
    private static final boolean[] UNIFONT = new boolean[0x10000];

    static {
        try (InputStream in = TextWidth.class.getResourceAsStream("/hud/default-font-widths.txt")) {
            if (in == null) {
                throw new IllegalStateException("hud/default-font-widths.txt 가 없어요");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            int prev = -1;
            byte width = 6;
            boolean uni = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.trim().split(" ");
                int start = Integer.parseInt(parts[0], 16);
                fill(prev, start, width, uni);
                uni = parts[1].endsWith("u");
                width = Byte.parseByte(uni ? parts[1].substring(0, parts[1].length() - 1) : parts[1]);
                prev = start;
            }
            fill(prev, 0x10000, width, uni);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void fill(int from, int to, byte width, boolean uni) {
        for (int c = Math.max(0, from); c < to; c++) {
            WIDTH[c] = width;
            UNIFONT[c] = uni;
        }
    }

    /** 글자 하나의 폭 (굵게면 bold) */
    public static float of(int codepoint, boolean bold) {
        if (codepoint == '\n') {
            return 0;
        }
        if (codepoint < 0 || codepoint >= WIDTH.length) {
            return bold ? 7 : 6; // 보조 평면 글자는 쓰지 않음: "없는 글자" 네모로 침
        }
        float w = WIDTH[codepoint];
        return bold ? w + (UNIFONT[codepoint] ? 0.5f : 1f) : w;
    }

    /** 글자열 폭 (스타일 하나) */
    public static float of(String text, boolean bold) {
        float sum = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            sum += of(cp, bold);
            i += Character.charCount(cp);
        }
        return sum;
    }

    private TextWidth() {
    }
}
