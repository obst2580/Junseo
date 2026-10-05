package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 2~4층짜리 낮은 건물. 한 층은 4칸(바닥 1 + 빈 칸 3).
 * <ul>
 *   <li>CHEAP (구인천): 붉은 벽돌·회색 시멘트 다세대 주택, 1층 상가 또는 필로티 주차장, 초록 옥상과 물탱크</li>
 *   <li>MARKET (대형시장 주변): 알록달록한 상가. 1층은 열린 가게와 줄무늬 차양, 위에 큰 간판</li>
 * </ul>
 * 안에는 사다리로 옥상까지 오를 수 있습니다.
 */
final class ShopHouse {
    enum Style { CHEAP, MARKET }

    private static final Block[] CHEAP_WALLS = {BRICKS, BRICKS, WHITE_TERRACOTTA, LIGHT_GRAY_CONCRETE, SANDSTONE,
            TERRACOTTA, LIGHT_GRAY_TERRACOTTA, MUD_BRICKS, WHITE_CONCRETE};
    private static final Block[] MARKET_WALLS = {WHITE_CONCRETE, YELLOW_TERRACOTTA, ORANGE_TERRACOTTA, WHITE_TERRACOTTA,
            PINK_TERRACOTTA, LIGHT_BLUE_TERRACOTTA, SANDSTONE, BRICKS, LIME_TERRACOTTA};
    private static final String[] SIGN_COLORS = {"red", "yellow", "orange", "blue", "green", "light_blue", "magenta",
            "lime", "white", "cyan", "pink", "purple"};
    private static final String[] AWNING = {"red", "white", "yellow", "green", "blue", "orange", "light_blue"};

    static Voxels build(int w, int d, Random r, Style style) {
        boolean market = style == Style.MARKET;
        int floors = market ? 2 + r.nextInt(2) : (r.nextInt(10) < 3 ? 2 : r.nextInt(10) < 6 ? 3 : 4);
        if (w < 9 || d < 9) {
            floors = Math.min(floors, 2);
        }
        int top = 4 * floors - 1; // 옥상 바닥
        // 정면은 남쪽(j = d-1). 앞쪽 1칸(가게면 차양 자리 3칸)은 비워 둠
        int front = market ? Math.min(3, d - 7) : 1;
        int jf = d - 1 - Math.max(0, front); // 정면 벽
        int i0 = 0, i1 = w - 1, j0 = 0;
        Voxels v = new Voxels(w, d, -1, top + 6);

        Block wall = market ? MARKET_WALLS[r.nextInt(MARKET_WALLS.length)] : CHEAP_WALLS[r.nextInt(CHEAP_WALLS.length)];
        Block trim = wall == WHITE_CONCRETE || wall == SANDSTONE ? LIGHT_GRAY_CONCRETE : r.nextBoolean() ? WHITE_CONCRETE : SMOOTH_STONE;
        Block roof = market ? SMOOTH_STONE : r.nextInt(10) < 5 ? GREEN_CONCRETE : r.nextBoolean() ? SMOOTH_STONE : LIGHT_GRAY_CONCRETE;
        boolean piloti = !market && r.nextInt(10) < 4 && w >= 9 && d >= 10; // 1층 필로티 주차장 빌라
        boolean shop = !piloti; // 1층 가게

        // 앞마당 포장
        v.fill(0, -1, jf + 1, w - 1, -1, d - 1, market ? POLISHED_ANDESITE : SMOOTH_STONE);

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
        Block pane = market ? GLASS_PANE : r.nextInt(3) == 0 ? GRAY_PANE : GLASS_PANE;
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
        } else if (market) {
            // 1층 앞면을 통째로 연 가게: 판매대와 물건
            v.fill(i0 + 1, 0, jf, i1 - 1, 2, jf, AIR);
            goods(v, r, i0 + 1, i1 - 1, jf - 1);
        } else {
            // 1층 유리 가게 앞면과 문
            for (int i = i0 + 1; i < i1; i++) {
                v.fill(i, 0, jf, i, 2, jf, (i - i0) % 6 == 0 ? trim : GLASS);
            }
            v.set(door, 0, jf, door("spruce", "south", false));
            v.set(door, 1, jf, door("spruce", "south", true));
            v.set(door, 2, jf, trim);
        }

        // 간판: 1층 위 정면 (가게면 두 줄 높이, 한 칸 튀어나옴)
        if (!piloti) {
            Block sign = concrete(SIGN_COLORS[r.nextInt(SIGN_COLORS.length)]);
            int rows = market ? 2 : 1;
            v.fill(i0, 3, jf + 1, i1, 2 + rows, jf + 1, sign);
            // 간판 테두리 불빛
            for (int i = i0 + 1; i < i1; i += 4) {
                v.set(i, 3, jf + 1, market ? SEA_LANTERN : sign);
            }
        }
        // 시장 가게는 간판 아래 줄무늬 차양 (2칸 앞으로)
        if (market && front >= 2) {
            Block a = wool(AWNING[r.nextInt(AWNING.length)]), b = WHITE_WOOL;
            for (int i = i0; i <= i1; i++) {
                Block c = (i / 2) % 2 == 0 ? a : b;
                v.set(i, 3, jf + 2, c);
                v.set(i, 2, jf + 3, c);
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
        if (!market && w >= 8 && d >= 8) {
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

    /** 가게 앞 판매대 (시장 물건) */
    static void goods(Voxels v, Random r, int ia, int ib, int j) {
        int kind = r.nextInt(7);
        for (int i = ia; i <= ib; i++) {
            Block b = switch (kind) {
                case 0 -> r.nextBoolean() ? MELON : PUMPKIN; // 과일·채소
                case 1 -> wool(SIGN_COLORS[r.nextInt(SIGN_COLORS.length)]); // 천·한복
                case 2 -> r.nextInt(3) == 0 ? CAULDRON : SMOKER; // 먹거리
                case 3 -> r.nextBoolean() ? BARREL : DRIED_KELP; // 건어물
                case 4 -> r.nextBoolean() ? FLOWER_POT_TULIP : FLOWER_POT_DANDELION; // 꽃
                case 5 -> r.nextBoolean() ? HAY : BARREL; // 곡물
                default -> r.nextBoolean() ? PACKED_ICE : PRISMARINE; // 생선
            };
            v.set(i, 0, j, kind == 4 ? SPRUCE_PLANKS : b);
            if (kind == 4) {
                v.set(i, 1, j, b);
            } else if (kind == 1 && r.nextBoolean()) {
                v.set(i, 1, j, wool(SIGN_COLORS[r.nextInt(SIGN_COLORS.length)]));
            }
        }
    }

    private ShopHouse() {
    }
}
