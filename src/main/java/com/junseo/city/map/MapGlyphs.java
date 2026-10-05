package com.junseo.city.map;

/**
 * 미니맵·큰 지도를 "글자"로 그리기 위한 글자표.
 * 리소스팩을 만드는 쪽(PackBuilder)과 그리는 쪽(RadarHud, BigMap)이 이 표 하나를 같이 씁니다.
 *
 * <p>원리 (마인크래프트 클라이언트 규칙)
 * <ul>
 *   <li>비트맵 글자의 윗변 = 글자 줄 y + 7 − ascent. 그래서 ascent 를 줄마다 다르게 주면 원하는 높이에 그릴 수 있어요.
 *       여기서는 <b>ascent = 1 − row</b> 로 정해서, row 번째 줄이 "기준 위치 + row" 에 그려지게 합니다.
 *       (액션바: 화면 아래에서 66칸 위가 0번 줄, 대화창 본문: 글 줄 윗변 + 6 이 0번 줄)</li>
 *   <li>비트맵 글자의 폭(advance) = 실제 그림 폭 + 1. 그래서 L칸짜리 색 막대 다음에는 −1칸 띄우기 글자가 필요합니다.</li>
 *   <li>띄우기(space) 글자는 폭을 마음대로(음수도) 정할 수 있어서, 글자 커서를 아무 데나 옮길 수 있어요.</li>
 * </ul>
 */
public final class MapGlyphs {
    /** 리소스팩 안의 글꼴 이름 (assets/junseocity/font/map.json) */
    public static final String FONT = "junseocity:map";

    /** 색 막대 종류 수 = 지도 칸 종류 수 */
    public static final int KINDS = RadarRaster.KINDS;

    /**
     * 1칸 높이 색 막대 글자 묶음. (줄 row, 칸 종류 kind, 길이 length) 마다 글자 하나.
     * 막대는 왼쪽부터 그려서 뒤에 그린 막대가 앞 막대를 덮으므로, 길이가 딱 맞지 않아도(더 길어도) 됩니다.
     */
    public static final class RunSet {
        private final int base;
        private final int rows;
        private final int[] lengths;

        RunSet(int base, int rows, int[] lengths) {
            this.base = base;
            this.rows = rows;
            this.lengths = lengths;
        }

        public int rows() {
            return rows;
        }

        public int[] lengths() {
            return lengths.clone();
        }

        public int lengthCount() {
            return lengths.length;
        }

        public int length(int index) {
            return lengths[index];
        }

        public int codepoint(int row, int kind, int lengthIndex) {
            if (row < 0 || row >= rows || kind < 0 || kind >= KINDS || lengthIndex < 0 || lengthIndex >= lengths.length) {
                throw new IllegalArgumentException("row " + row + " kind " + kind + " length " + lengthIndex);
            }
            return base + (row * KINDS + kind) * lengths.length + lengthIndex;
        }

        public int lastCodepoint() {
            return codepoint(rows - 1, KINDS - 1, lengths.length - 1);
        }

        /** len 보다 같거나 긴 것 중 가장 짧은 길이의 번호. 그런 게 없거나 limit 를 넘으면 -1 */
        int smallestAtLeast(int len, int limit) {
            for (int i = 0; i < lengths.length; i++) {
                if (lengths[i] >= len) {
                    return lengths[i] <= limit ? i : -1;
                }
            }
            return -1;
        }

        /** len 보다 같거나 짧은 것 중 가장 긴 길이의 번호 (len ≥ 1 이면 언제나 있음) */
        int largestAtMost(int len) {
            for (int i = lengths.length - 1; i >= 0; i--) {
                if (lengths[i] <= len) {
                    return i;
                }
            }
            throw new IllegalArgumentException("length " + len);
        }
    }

    /** 미니맵(액션바)용: 64줄. 자주 보내므로 3바이트 글자(BMP 사용자 영역)만 씁니다. */
    public static final RunSet HUD = new RunSet(0xE000, 64, new int[]{1, 2, 3, 4, 6, 8, 12, 16, 32, 64});

    /** 큰 지도(대화창)용: 144줄 */
    public static final RunSet BIG = new RunSet(0xF0000, 144, new int[]{1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 128, 256});

    // ------------------------------------------------------------------ 띄우기 글자

    /** −SPACE_RANGE ~ +SPACE_RANGE 칸 띄우기 글자가 하나씩 있습니다 */
    public static final int SPACE_RANGE = 128;
    private static final int SPACE_BASE = 0xF700;
    /** 큰 띄우기: +256, +512, +1024, −256, −512, −1024 */
    private static final int[] BIG_SPACES = {256, 512, 1024, -256, -512, -1024};
    private static final int BIG_SPACE_BASE = 0xF810;

    public static int space(int advance) {
        if (advance >= -SPACE_RANGE && advance <= SPACE_RANGE) {
            return SPACE_BASE + SPACE_RANGE + advance;
        }
        for (int i = 0; i < BIG_SPACES.length; i++) {
            if (BIG_SPACES[i] == advance) {
                return BIG_SPACE_BASE + i;
            }
        }
        throw new IllegalArgumentException("no space glyph for " + advance);
    }

    /** 리소스팩의 space 글꼴에 넣을 (글자, 폭) 목록 */
    public static int[][] spaces() {
        int[][] out = new int[2 * SPACE_RANGE + 1 + BIG_SPACES.length][];
        int n = 0;
        for (int d = -SPACE_RANGE; d <= SPACE_RANGE; d++) {
            out[n++] = new int[]{space(d), d};
        }
        for (int big : BIG_SPACES) {
            out[n++] = new int[]{space(big), big};
        }
        return out;
    }

    // ------------------------------------------------------------------ 그 밖의 글자

    /** 미니맵 테두리 (RadarHud.W+2 × RadarHud.H+2, 0번 줄 한 칸 위·왼쪽에서 시작) */
    public static final int FRAME = 0xF820;

    /** 글 줄 안에 넣는 아이콘 (범례용, 보통 글자 높이) */
    private static final int LEGEND_BASE = 0xF830;

    public static int legend(int icon) {
        return LEGEND_BASE + icon;
    }

    /** 아이콘(7×7)을 놓을 수 있는 줄: 윗변이 ICON_MIN_ROW ~ ICON_MAX_ROW */
    public static final int ICON_MIN_ROW = -6;
    public static final int ICON_MAX_ROW = 143;
    private static final int ICON_BASE = 0x100000;

    public static int icon(int topRow, int icon) {
        if (topRow < ICON_MIN_ROW || topRow > ICON_MAX_ROW || icon < 0 || icon >= MapIcons.COUNT) {
            throw new IllegalArgumentException("icon row " + topRow + " icon " + icon);
        }
        return ICON_BASE + (topRow - ICON_MIN_ROW) * MapIcons.COUNT + icon;
    }

    /** 큰 지도의 보이지 않는 마우스 영역 (HOTSPOT×HOTSPOT). zoneRow 번째 칸 줄 */
    public static final int HOTSPOT = 16;
    private static final int HOT_BASE = 0xFF000;

    public static int hotspot(int zoneRow) {
        return HOT_BASE + zoneRow;
    }

    public static int hotspotRows() {
        return (BIG.rows() + HOTSPOT - 1) / HOTSPOT;
    }

    /** 색 막대 글자의 폭 */
    public static int runAdvance(int length) {
        return length + 1;
    }

    /** 가로 hotspot 글자의 폭 (마지막 열까지 거의 투명한 점이 있어 폭이 정해짐) */
    public static int hotspotAdvance() {
        return HOTSPOT + 1;
    }

    private MapGlyphs() {
    }
}
