package com.junseo.city.map;

/**
 * 지도 글자열 만들기. 글자 커서(가로 위치)를 따라가면서 띄우기 글자로 원하는 곳에 글자를 놓습니다.
 * 커서는 시작점(0) 기준의 칸 단위입니다.
 */
public final class GlyphWriter {
    private final StringBuilder out = new StringBuilder();
    private int cursor;
    private int maxCursor;

    public int cursor() {
        return cursor;
    }

    /** 지금까지 커서가 가장 오른쪽으로 간 곳 (대화창 줄바꿈 검사용) */
    public int maxCursor() {
        return maxCursor;
    }

    public void moveTo(int x) {
        move(x - cursor);
    }

    public void move(int d) {
        while (Math.abs(d) > MapGlyphs.SPACE_RANGE) {
            int step = Math.abs(d) >= 1024 ? 1024 : Math.abs(d) >= 512 ? 512 : Math.abs(d) >= 256 ? 256 : MapGlyphs.SPACE_RANGE;
            step = d > 0 ? step : -step;
            out.appendCodePoint(MapGlyphs.space(step));
            cursor += step;
            d -= step;
        }
        if (d != 0) {
            out.appendCodePoint(MapGlyphs.space(d));
            cursor += d;
        }
        maxCursor = Math.max(maxCursor, cursor);
    }

    /** 커서 자리에 글자를 놓고 advance 만큼 커서를 옮깁니다 */
    public void put(int codepoint, int advance) {
        out.appendCodePoint(codepoint);
        cursor += advance;
        maxCursor = Math.max(maxCursor, cursor);
    }

    /** (x, 윗변 topRow) 에 아이콘 */
    public void icon(int x, int topRow, int icon) {
        moveTo(x);
        put(MapGlyphs.icon(topRow, icon), MapIcons.advance(icon));
    }

    /**
     * 한 줄 칠하기. kinds[from..to) 를 같은 색끼리 묶어서 색 막대 글자로 씁니다.
     * originX 는 kinds[0] 이 놓일 커서 위치입니다.
     * 막대가 실제보다 길어도 다음 막대가 덮으므로 대부분 막대 하나 = 글자 하나입니다.
     * 줄 끝(to)을 넘어 그리지는 않습니다.
     *
     * @return 이 줄에 쓴 막대(색이 바뀌는 구간) 수
     */
    public int row(MapGlyphs.RunSet set, int row, byte[] kinds, int from, int to, int originX) {
        int runs = 0;
        int i = from;
        while (i < to) {
            byte kind = kinds[i];
            int j = i + 1;
            while (j < to && kinds[j] == kind) {
                j++;
            }
            int start = i, len = j - i;
            while (len > 0) {
                int li = set.smallestAtLeast(len, to - start);
                boolean covers = li >= 0;
                if (!covers) {
                    li = set.largestAtMost(len);
                }
                int l = set.length(li);
                moveTo(originX + start);
                put(set.codepoint(row, kind, li), MapGlyphs.runAdvance(l));
                if (covers) {
                    break;
                }
                start += l;
                len -= l;
            }
            runs++;
            i = j;
        }
        return runs;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    public int length() {
        return out.length();
    }
}
