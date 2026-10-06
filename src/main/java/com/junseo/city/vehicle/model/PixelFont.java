package com.junseo.city.vehicle.model;

import java.util.Map;

/** 차 도색 글씨용 5×7 글꼴 (영문 대문자 일부와 숫자). 서버에 한글 글꼴이 없어도 그릴 수 있게 직접 둠 */
final class PixelFont {
    private static final Map<Character, String[]> GLYPHS = Map.ofEntries(
            Map.entry('A', new String[]{".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
            Map.entry('B', new String[]{"####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."}),
            Map.entry('C', new String[]{".####", "#....", "#....", "#....", "#....", "#....", ".####"}),
            Map.entry('E', new String[]{"#####", "#....", "#....", "####.", "#....", "#....", "#####"}),
            Map.entry('H', new String[]{"#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
            Map.entry('I', new String[]{"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"}),
            Map.entry('K', new String[]{"#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"}),
            Map.entry('L', new String[]{"#....", "#....", "#....", "#....", "#....", "#....", "#####"}),
            Map.entry('N', new String[]{"#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"}),
            Map.entry('O', new String[]{".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
            Map.entry('P', new String[]{"####.", "#...#", "#...#", "####.", "#....", "#....", "#...."}),
            Map.entry('R', new String[]{"####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"}),
            Map.entry('S', new String[]{".####", "#....", "#....", ".###.", "....#", "....#", "####."}),
            Map.entry('T', new String[]{"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('U', new String[]{"#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
            Map.entry('X', new String[]{"#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"}),
            Map.entry('Y', new String[]{"#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('0', new String[]{".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."}),
            Map.entry('1', new String[]{"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."}),
            Map.entry('2', new String[]{".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"}),
            Map.entry('9', new String[]{".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."}),
            Map.entry(' ', new String[]{".....", ".....", ".....", ".....", ".....", ".....", "....."}));

    private PixelFont() {
    }

    /**
     * 글씨 text 를 왼쪽 아래 (x0, y0), 글자 높이 h 로 쓸 때 점 (x, y) 가 획 위인지 (y 는 위로 증가).
     * 글자 폭 5칸 + 사이 1칸
     */
    static boolean ink(String text, double x0, double y0, double h, double x, double y) {
        double cell = h / 7;
        int col = (int) Math.floor((x - x0) / cell), row = 6 - (int) Math.floor((y - y0) / cell);
        if (col < 0 || row < 0 || row > 6) {
            return false;
        }
        int ch = col / 6, cx = col % 6;
        if (ch >= text.length() || cx == 5) {
            return false;
        }
        String[] g = GLYPHS.get(text.charAt(ch));
        return g != null && g[row].charAt(cx) == '#';
    }

    /** 글씨 전체 폭 */
    static double width(String text, double h) {
        return (text.length() * 6 - 1) * h / 7;
    }
}
