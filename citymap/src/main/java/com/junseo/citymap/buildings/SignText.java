package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 작은 간판: 블록 두 줄 높이에는 한글을 쓸 수 없어서, 멀리서 글씨처럼 보이는 무늬를 찍습니다.
 * 색 조합은 한국 상가 간판에 흔한 것 (흰 바탕 빨간 글씨, 노란 바탕 검은 글씨, 파란 바탕 흰 글씨 …).
 */
final class SignText {
    /** {바탕, 글씨} */
    static final Block[][] COMBOS = {
            {WHITE_CONCRETE, RED_CONCRETE}, {WHITE_CONCRETE, BLUE_CONCRETE}, {YELLOW_CONCRETE, BLACK_CONCRETE},
            {BLUE_CONCRETE, WHITE_CONCRETE}, {RED_CONCRETE, WHITE_CONCRETE}, {GREEN_CONCRETE, WHITE_CONCRETE},
            {ORANGE_CONCRETE, WHITE_CONCRETE}, {BLACK_CONCRETE, YELLOW_CONCRETE}, {WHITE_CONCRETE, GREEN_CONCRETE},
            {CYAN_CONCRETE, WHITE_CONCRETE},
    };
    private static final String[] PATTERNS = {"11/10", "10/11", "11/11", "01/11", "11/01", "10/10", "11/10"};

    static Block[] combo(Random r) {
        return COMBOS[r.nextInt(COMBOS.length)];
    }

    /**
     * 두 줄짜리 가로 간판. (i, yTop, j) 에서 (di, dj) 방향으로 len 칸, 바탕을 깔고 글씨 무늬를 찍습니다.
     */
    static void line(Voxels v, int i, int yTop, int j, int len, int di, int dj, Block[] combo, Random r) {
        for (int t = 0; t < len; t++) {
            v.set(i + t * di, yTop, j + t * dj, combo[0]);
            v.set(i + t * di, yTop - 1, j + t * dj, combo[0]);
        }
        // 양 끝 1칸은 비우고 글자(2칸 + 띄움 1칸)
        for (int t = 1; t + 1 < len; t += 3) {
            String p = PATTERNS[r.nextInt(PATTERNS.length)];
            for (int c = 0; c < 2 && t + c + 1 < len; c++) {
                if (p.charAt(c) == '1') {
                    v.set(i + (t + c) * di, yTop, j + (t + c) * dj, combo[1]);
                }
                if (p.charAt(3 + c) == '1') {
                    v.set(i + (t + c) * di, yTop - 1, j + (t + c) * dj, combo[1]);
                }
            }
        }
    }

    /** 세로 간판: 폭 2칸, 높이 len, 위에서 아래로 글자(2×2 + 띄움 1) */
    static void vertical(Voxels v, int i, int yTop, int j, int len, int di, int dj, Block[] combo, Random r) {
        for (int y = yTop; y > yTop - len; y--) {
            v.set(i, y, j, combo[0]);
            v.set(i + di, y, j + dj, combo[0]);
        }
        for (int y = yTop - 1; y - 1 > yTop - len; y -= 3) {
            String p = PATTERNS[r.nextInt(PATTERNS.length)];
            for (int c = 0; c < 2; c++) {
                if (p.charAt(c) == '1') {
                    v.set(i + c * di, y, j + c * dj, combo[1]);
                }
                if (p.charAt(3 + c) == '1') {
                    v.set(i + c * di, y - 1, j + c * dj, combo[1]);
                }
            }
        }
    }

    private SignText() {
    }
}
