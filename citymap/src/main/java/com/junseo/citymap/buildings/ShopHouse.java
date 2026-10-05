package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 2~4층짜리 낮은 건물. 한 층은 4칸(바닥 1 + 빈 칸 3).
 * <ul>
 *   <li>CHEAP (구인천): 붉은 벽돌·회색 시멘트 다세대 주택, 1층 상가 또는 필로티 주차장, 초록 옥상과 물탱크</li>
 *   <li>COMMERCIAL (대형시장 둘레): 타일·시멘트 상가 건물. 1층은 유리 가게, 층마다 간판, 모서리 세로 간판</li>
 * </ul>
 * 안에는 사다리로 옥상까지 오를 수 있습니다.
 */
final class ShopHouse {
    enum Style { CHEAP, COMMERCIAL }

    private static final Block[] CHEAP_WALLS = {BRICKS, BRICKS, WHITE_TERRACOTTA, LIGHT_GRAY_CONCRETE, SANDSTONE,
            TERRACOTTA, LIGHT_GRAY_TERRACOTTA, MUD_BRICKS, WHITE_CONCRETE};
    private static final Block[] COMMERCIAL_WALLS = {WHITE_TERRACOTTA, WHITE_TERRACOTTA, LIGHT_GRAY_CONCRETE, SANDSTONE,
            BRICKS, WHITE_CONCRETE, LIGHT_GRAY_TERRACOTTA, TERRACOTTA};

    static Voxels build(int w, int d, Random r, Style style) {
        boolean commercial = style == Style.COMMERCIAL;
        int floors = commercial ? 3 + r.nextInt(2) : (r.nextInt(10) < 3 ? 2 : r.nextInt(10) < 6 ? 3 : 4);
        if (w < 9 || d < 9) {
            floors = Math.min(floors, 2);
        }
        int top = 4 * floors - 1; // 옥상 바닥
        // 정면은 남쪽(j = d-1). 앞쪽 1칸(가게면 차양 자리 3칸)은 비워 둠
        int front = 1;
        int jf = d - 1 - Math.max(0, front); // 정면 벽
        int i0 = 0, i1 = w - 1, j0 = 0;
        Voxels v = new Voxels(w, d, -1, top + 6);

        Block wall = commercial ? COMMERCIAL_WALLS[r.nextInt(COMMERCIAL_WALLS.length)] : CHEAP_WALLS[r.nextInt(CHEAP_WALLS.length)];
        Block trim = wall == WHITE_CONCRETE || wall == SANDSTONE ? LIGHT_GRAY_CONCRETE : r.nextBoolean() ? WHITE_CONCRETE : SMOOTH_STONE;
        Block roof = r.nextInt(10) < 5 ? GREEN_CONCRETE : r.nextBoolean() ? SMOOTH_STONE : LIGHT_GRAY_CONCRETE;
        boolean piloti = !commercial && r.nextInt(10) < 4 && w >= 9 && d >= 10; // 1층 필로티 주차장 빌라
        boolean shop = !piloti; // 1층 가게

        // 앞마당 포장
        v.fill(0, -1, jf + 1, w - 1, -1, d - 1, commercial ? POLISHED_ANDESITE : SMOOTH_STONE);

        // 층 바닥과 바깥 벽
        for (int k = 0; k <= floors; k++) {
            int y = 4 * k - 1;
            v.fill(i0, y, j0, i1, y, jf, k == 0 ? (piloti ? GRAY_CONCRETE : POLISHED_ANDESITE) : k == floors ? roof : SMOOTH_STONE);
        }
        v.walls(i0, 0, j0, i1, top - 1, jf, wall);
        // 층마다 띠 (바닥 높이)
        for (int k = 1; k < floors; k++) {
            int y = 4 * k - 1;
            v.walls(i0, y, j0, i1, y, jf, trim);
        }
        // 모서리 기둥
        for (int[] c : new int[][]{{i0, j0}, {i1, j0}, {i0, jf}, {i1, jf}}) {
            v.fill(c[0], 0, c[1], c[0], top - 1, c[1], trim);
        }
        // 창문: 정면·뒷면은 2칸 창, 옆면은 듬성듬성
        Block pane = r.nextInt(3) == 0 ? GRAY_PANE : GLASS_PANE;
        for (int k = piloti || shop ? 1 : 0; k < floors; k++) {
            int y = 4 * k + 1;
            for (int i = i0 + 1; i < i1; i++) {
                if ((i - i0 - 1) % 4 < 2) {
                    v.fill(i, y, jf, i, y + 1, jf, pane);
                    v.fill(i, y, j0, i, y + 1, j0, pane);
                }
            }
            for (int j = j0 + 2; j < jf - 1; j++) {
                if ((j - j0 - 2) % 6 < 2) {
                    v.fill(i0, y, j, i0, y + 1, j, pane);
                    v.fill(i1, y, j, i1, y + 1, j, pane);
                }
            }
        }

        int door = (i0 + i1) / 2;
        if (piloti) {
            // 1층은 기둥만 두고 비움 (주차장). 뒤 구석에 계단실
            v.fill(i0 + 1, 0, jf, i1 - 1, 2, jf, AIR);
            v.fill(i0, 0, j0 + 1, i0, 2, jf - 1, AIR);
            v.fill(i1, 0, j0 + 1, i1, 2, jf - 1, AIR);
            for (int i = i0; i <= i1; i += 5) {
                v.fill(i, 0, jf, i, 2, jf, trim);
                v.fill(i, 0, (j0 + jf) / 2, i, 2, (j0 + jf) / 2, trim);
            }
            v.fill(i0, 0, (j0 + jf) / 2, i0, 2, (j0 + jf) / 2, trim);
            v.fill(i1, 0, (j0 + jf) / 2, i1, 2, (j0 + jf) / 2, trim);
            v.walls(i0, 0, j0, i0 + 3, 2, j0 + 3, wall);
            v.set(i0 + 2, 0, j0 + 3, door("oak", "south", false));
            v.set(i0 + 2, 1, j0 + 3, door("oak", "south", true));
            // 주차선
            for (int i = i0 + 5; i < i1; i += 3) {
                v.fill(i, -1, j0 + 5, i, -1, jf - 1, WHITE_CONCRETE);
            }
        } else {
            // 1층 유리 가게 앞면과 문
            for (int i = i0 + 1; i < i1; i++) {
                v.fill(i, 0, jf, i, 2, jf, (i - i0) % 6 == 0 ? trim : GLASS);
            }
            v.set(door, 0, jf, door("spruce", "south", false));
            v.set(door, 1, jf, door("spruce", "south", true));
            v.set(door, 2, jf, trim);
        }

        // 간판: 1층 위 정면 (한 칸 튀어나옴, 두 줄 글씨 무늬). 상가 건물은 2층 이상에도 층마다 간판
        if (!piloti) {
            SignText.line(v, i0, 4, jf + 1, w, 1, 0, SignText.combo(r), r);
        }
        if (commercial) {
            for (int k = 1; k < floors; k++) {
                if (r.nextInt(10) < 6) {
                    SignText.line(v, i0 + 1, 4 * k + 3, jf + 1, w - 2, 1, 0, SignText.combo(r), r);
                } else if (r.nextBoolean()) {
                    // 창문에 붙인 글씨
                    SignText.line(v, i0 + 1, 4 * k + 2, jf, w - 2, 1, 0, new Block[]{GLASS, r.nextBoolean() ? RED_CONCRETE : BLUE_CONCRETE}, r);
                }
            }
            // 모서리 세로 간판
            if (floors >= 3) {
                int len = 4 * floors - 6;
                SignText.vertical(v, r.nextBoolean() ? i0 : i1, 4 * floors - 2, jf + 1, len, 0, 1, SignText.combo(r), r);
            }
        }

        // 지붕 난간
        v.walls(i0, top + 1, j0, i1, top + 1, jf, wall);

        // 사다리: 뒤쪽 벽 안쪽, 옥상까지
        int li = piloti ? i0 + 1 : i1 - 1;
        int lj = j0 + 1;
        for (int y = 0; y <= top; y++) {
            v.set(li, y, lj, LADDER);
        }
        // 층 조명 (천장)
        for (int k = 0; k < floors; k++) {
            int y = 4 * k + 3;
            v.set((i0 + i1) / 2, y, (j0 + jf) / 2, SEA_LANTERN);
            if (w > 14) {
                v.set(i0 + 3, y, (j0 + jf) / 2, SEA_LANTERN);
                v.set(i1 - 3, y, (j0 + jf) / 2, SEA_LANTERN);
            }
        }

        // 옥상: 물탱크, 옥탑방, 실외기
        if (w >= 8 && d >= 8) {
            Block tank = r.nextBoolean() ? LIGHT_BLUE_CONCRETE : WHITE_CONCRETE;
            v.cylinder(i0 + 3, j0 + 3, 1.6, top + 1, top + 3, tank);
            if (r.nextInt(10) < 4 && w >= 10) {
                int bi = i1 - 5;
                v.walls(bi, top + 1, j0 + 1, i1 - 1, top + 3, j0 + 4, wall);
                v.fill(bi, top + 4, j0 + 1, i1 - 1, top + 4, j0 + 4, roof);
                v.set(bi + 2, top + 1, j0 + 4, door("oak", "south", false));
                v.set(bi + 2, top + 2, j0 + 4, door("oak", "south", true));
                v.set(i1 - 1, top + 2, j0 + 2, GLASS_PANE);
            }
            for (int n = 0; n < 1 + r.nextInt(3); n++) {
                int ai = i0 + 2 + r.nextInt(Math.max(1, w - 4)), aj = j0 + 6 + r.nextInt(Math.max(1, jf - 7));
                if (v.get(ai, top + 1, aj) == null) {
                    v.set(ai, top + 1, aj, SMOOTH_STONE_SLAB);
                }
            }
        }
        // 사다리 위 옥상 출입구는 비워 둠
        v.set(li, top + 1, lj, null);
        v.connect();
        return v;
    }

    private ShopHouse() {
    }
}
