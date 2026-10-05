package com.junseo.city.map;

import com.junseo.city.pack.PackBuilder;

import java.util.function.IntPredicate;

/**
 * 테스트용: 마인크래프트 클라이언트가 글자열을 그리는 규칙을 흉내 냅니다.
 * <ul>
 *   <li>글자는 커서 위치(x)에서 시작, 윗변 = 글 줄 y + 7 − ascent</li>
 *   <li>그린 뒤 커서는 글자 폭(advance)만큼 이동 (띄우기 글자는 음수도 됨)</li>
 *   <li>뒤에 그린 글자가 앞 글자를 덮음, 알파가 0.1 미만인 점은 안 그려짐</li>
 * </ul>
 */
final class ClientTextSim {
    private final PackBuilder.FontModel font;
    final int width;
    final int height;
    final int[] pixels;
    /** 이 점을 마지막으로 그린 글자 */
    final int[] owner;
    private final int originX;
    private final int originY;
    int cursor;

    /**
     * @param originX 글자열 시작 커서(0)가 놓일 그림 x
     * @param lineY   글 줄 y 가 놓일 그림 y
     */
    ClientTextSim(PackBuilder.FontModel font, int width, int height, int originX, int lineY) {
        this.font = font;
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        this.owner = new int[width * height];
        this.originX = originX;
        this.originY = lineY;
    }

    /** 글자열을 그립니다. draw 가 false 인 글자는 커서만 옮김 */
    void draw(String text, IntPredicate draw) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            PackBuilder.Glyph g = font.bitmaps().get(cp);
            if (g != null && draw.test(cp)) {
                int left = originX + cursor, top = originY + 7 - g.ascent();
                for (int y = 0; y < g.height(); y++) {
                    for (int x = 0; x < g.width(); x++) {
                        int v = g.argb()[y * g.width() + x];
                        if ((v >>> 24) < 26) {
                            continue; // 거의 투명한 점은 버려짐
                        }
                        int px = left + x, py = top + y;
                        if (px >= 0 && py >= 0 && px < width && py < height) {
                            pixels[py * width + px] = blend(pixels[py * width + px], v);
                            owner[py * width + px] = cp;
                        }
                    }
                }
            }
            cursor += font.advance(cp);
        }
    }

    void draw(String text) {
        draw(text, cp -> true);
    }

    private static int blend(int dst, int src) {
        int a = src >>> 24;
        if (a == 255) {
            return src;
        }
        int r = (((src >> 16) & 255) * a + ((dst >> 16) & 255) * (255 - a)) / 255;
        int g = (((src >> 8) & 255) * a + ((dst >> 8) & 255) * (255 - a)) / 255;
        int b = ((src & 255) * a + (dst & 255) * (255 - a)) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
