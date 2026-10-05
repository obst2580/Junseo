package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 한국 동네의 낮은 건물. 한 층은 4칸(바닥 1 + 빈 칸 3).
 * <ul>
 *   <li>VILLA (다세대·빌라): 1층 필로티 주차장(세워 둔 차), 2~4층 집. 붉은 벽돌·베이지 타일·흰 벽,
 *       알루미늄 창과 창턱, 방범창, 작은 발코니, 계단실 세로 창, 실외기, 노란 가스관, 문패,
 *       옥상 초록 방수 도장·물탱크·계단실 옥탑</li>
 *   <li>MIXED (상가주택): 1층 가게(유리 가게 앞, 간판, 차양), 위층 집</li>
 *   <li>COMMERCIAL (근린상가): 4~6층, 1층 가게, 위층 학원·의원·노래방 간판, 돌출 간판</li>
 * </ul>
 * 간판에는 진짜 표지판 글씨가 붙습니다 (생성기가 청크를 처음 불러올 때 씀).
 * 안에는 사다리로 옥상까지 오를 수 있습니다.
 */
final class ShopHouse {
    enum Style { VILLA, MIXED, COMMERCIAL }

    /** {벽, 띠·창틀, 1층 벽} */
    private static final Block[][] VILLA_SKINS = {
            {BRICKS, SMOOTH_STONE, BRICKS},
            {BRICKS, WHITE_CONCRETE, POLISHED_ANDESITE},
            {WHITE_TERRACOTTA, LIGHT_GRAY_TERRACOTTA, POLISHED_GRANITE},
            {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE},
            {SANDSTONE, SMOOTH_STONE, POLISHED_GRANITE},
            {MUD_BRICKS, SMOOTH_STONE, MUD_BRICKS},
    };
    private static final Block[][] COMMERCIAL_SKINS = {
            {POLISHED_GRANITE, SMOOTH_STONE, POLISHED_GRANITE},
            {WHITE_TERRACOTTA, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE},
            {LIGHT_GRAY_CONCRETE, WHITE_CONCRETE, POLISHED_ANDESITE},
            {POLISHED_ANDESITE, SMOOTH_STONE, POLISHED_DEEPSLATE},
            {BRICKS, SMOOTH_STONE, POLISHED_GRANITE},
            {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_DIORITE},
    };
    /** 간판 {판 블록, 표지판 나무, 글자색} */
    static final Object[][] BOARDS = {
            {WHITE_CONCRETE, "birch", "red"}, {WHITE_CONCRETE, "birch", "blue"}, {WHITE_CONCRETE, "birch", "black"},
            {YELLOW_CONCRETE, "bamboo", "black"}, {RED_CONCRETE, "mangrove", "white"}, {BLUE_CONCRETE, "dark_oak", "white"},
            {GREEN_CONCRETE, "warped", "white"}, {BLACK_CONCRETE, "dark_oak", "yellow"}, {ORANGE_CONCRETE, "acacia", "white"},
            {LIGHT_GRAY_CONCRETE, "oak", "black"}, {WHITE_CONCRETE, "birch", "green"},
    };
    private static final String[] AWNING_WOOD = {"spruce", "dark_oak", "birch", "mangrove", "warped", "acacia"};
    private static final String[] CAR_COLORS = {"white", "white", "white", "black", "black", "gray", "light_gray", "blue", "red"};

    private final Voxels v;
    private final Random r;
    private final Style style;
    private final int w, d, floors, top, i0, i1, j0, jf;
    private final Block wall, trim, base;
    private final boolean piloti, shop;
    /** 계단실 위치 (i), 사다리 칸 */
    private final int coreI, ladderI, ladderJ;

    private ShopHouse(int w, int d, Random r, Style style) {
        this.w = w;
        this.d = d;
        this.r = r;
        this.style = style;
        boolean commercial = style == Style.COMMERCIAL;
        int f = switch (style) {
            case VILLA -> r.nextInt(10) < 3 ? 4 : 5;
            case MIXED -> 3 + r.nextInt(2);
            case COMMERCIAL -> 4 + r.nextInt(3);
        };
        if (w < 10 || d < 10) {
            f = Math.min(f, 3);
        }
        floors = f;
        top = 4 * floors - 1;
        // 앞은 1칸 띄우고(간판·차양 자리), 옆·뒤는 넓으면 1칸 띄움 (실외기·가스관 자리)
        int side = w >= 12 ? 1 : 0;
        i0 = side;
        i1 = w - 1 - side;
        j0 = d >= 12 ? 1 : 0;
        jf = d - 2;
        v = new Voxels(w, d, -1, top + 6);
        Block[] skin = (commercial ? COMMERCIAL_SKINS : VILLA_SKINS)[r.nextInt(commercial ? COMMERCIAL_SKINS.length : VILLA_SKINS.length)];
        wall = skin[0];
        trim = skin[1];
        base = skin[2];
        piloti = style == Style.VILLA && w >= 9 && d >= 10;
        shop = !piloti;
        coreI = r.nextBoolean() ? i0 : i1 - 3;
        ladderI = coreI == i0 ? i0 + 1 : i1 - 1;
        ladderJ = j0 + 1;
    }

    static Voxels build(int w, int d, Random r, Style style) {
        ShopHouse h = new ShopHouse(w, d, r, style);
        h.shell();
        if (h.piloti) {
            h.piloti();
        } else {
            h.shopFront();
        }
        h.upperFloors();
        h.roof();
        h.details();
        h.v.connect();
        return h.v;
    }

    // ------------------------------------------------------------------ 몸체

    private void shell() {
        // 앞·옆 빈자리 포장 (인도와 이어지게)
        v.fill(0, -1, 0, w - 1, -1, d - 1, LIGHT_GRAY_CONCRETE);
        for (int k = 0; k <= floors; k++) {
            int y = 4 * k - 1;
            v.fill(i0, y, j0, i1, y, jf, k == 0 ? (piloti ? GRAY_CONCRETE : POLISHED_ANDESITE) : SMOOTH_STONE);
        }
        v.walls(i0, 0, j0, i1, top, jf, wall);
        // 1층 벽은 돌 마감
        v.walls(i0, 0, j0, i1, 2, jf, base);
        // 층 띠 (바닥 높이) — 벽돌 건물은 흰 띠
        for (int k = 1; k < floors; k++) {
            v.walls(i0, 4 * k - 1, j0, i1, 4 * k - 1, jf, trim);
        }
        // 집 안 조명 (천장)
        for (int k = 0; k < floors; k++) {
            int y = 4 * k + 2;
            for (int i = i0 + 3; i < i1 - 1; i += 6) {
                v.set(i, y + 1, (j0 + jf) / 2, SEA_LANTERN);
            }
        }
        // 사다리 (계단 대신, 옥상까지)
        for (int y = 0; y <= top; y++) {
            v.set(ladderI, y, ladderJ, LADDER);
        }
    }

    /** 1층 필로티 주차장: 기둥만 남기고 비움, 주차선과 차, 계단실 입구, 우편함 */
    private void piloti() {
        v.fill(i0 + 1, 0, jf, i1 - 1, 2, jf, AIR);
        v.fill(i0, 0, j0 + 1, i0, 2, jf - 1, AIR);
        v.fill(i1, 0, j0 + 1, i1, 2, jf - 1, AIR);
        for (int i = i0; i <= i1; i += 4) {
            v.fill(i, 0, jf, i, 2, jf, trim);
        }
        v.fill(i1, 0, jf, i1, 2, jf, trim);
        int mid = (j0 + jf) / 2;
        v.fill(i0, 0, mid, i0, 2, mid, trim);
        v.fill(i1, 0, mid, i1, 2, mid, trim);
        // 천장 등
        for (int i = i0 + 2; i < i1; i += 4) {
            v.set(i, 2, mid, LANTERN_HANGING);
        }
        // 계단실 (뒤 구석)
        int ci = coreI;
        v.walls(ci, 0, j0, ci + 3, 2, j0 + 4, base);
        v.set(ci + 2, 0, j0 + 4, door("iron", "south", false));
        v.set(ci + 2, 1, j0 + 4, door("iron", "south", true));
        v.set(ci + 1, 1, j0 + 5, Blocks.wallSign("dark_oak", "south", "white", false, KoreanNames.villa(r), KoreanNames.address(r)));
        // 주차선과 차
        for (int i = i0 + 1; i + 2 < i1; i += 3) {
            if (i <= ci + 3 && i + 2 >= ci) {
                continue;
            }
            v.fill(i, -1, j0 + 5, i, -1, jf - 1, WHITE_CONCRETE);
            if (r.nextInt(10) < 6 && jf - (j0 + 5) >= 4) {
                car(v, r, i + 1, jf - 4);
            }
        }
    }

    /** 1층 가게: 유리 가게 앞(어두운 틀), 문, 간판판 + 표지판 글씨, 차양 */
    private void shopFront() {
        int units = Math.max(1, (i1 - i0 - 1) / 7);
        int unitW = (i1 - i0 - 1) / units;
        for (int u = 0; u < units; u++) {
            int a = i0 + 1 + u * unitW, b = u == units - 1 ? i1 - 1 : a + unitW - 1;
            // 기둥과 유리
            for (int i = a; i <= b; i++) {
                for (int y = 0; y <= 2; y++) {
                    v.set(i, y, jf, y == 0 ? POLISHED_DEEPSLATE : GLASS_PANE);
                }
            }
            v.fill(a - 1, 0, jf, a - 1, 2, jf, POLISHED_DEEPSLATE);
            int doorI = (a + b) / 2;
            v.set(doorI, 0, jf, AIR);
            v.set(doorI, 1, jf, AIR);
            // 간판판 (2줄)과 표지판
            Object[] board = BOARDS[r.nextInt(BOARDS.length)];
            String[] name = KoreanNames.shop(r);
            v.fill(a - 1, 3, jf, b + 1, 3, jf, (Block) board[0]);
            v.set((a + b) / 2, 3, jf + 1, Blocks.wallSign((String) board[1], "south", (String) board[2], true, "", name[0]));
            // 차양 (반쯤)
            if (r.nextInt(10) < 4) {
                String wood = AWNING_WOOD[r.nextInt(AWNING_WOOD.length)];
                for (int i = a; i <= b; i++) {
                    if (i != (a + b) / 2) {
                        v.set(i, 2, jf + 1, Block.of(wood + "_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", 0x725430));
                    }
                }
            }
            // 가게 안: 진열대와 계산대
            v.fill(a, 0, jf - 3, b, 0, jf - 3, SMOOTH_STONE_SLAB);
            v.set(a, 0, j0 + 2, BARREL);
        }
        v.fill(i1, 0, jf, i1, 2, jf, POLISHED_DEEPSLATE);
    }

    // ------------------------------------------------------------------ 위층

    private void upperFloors() {
        boolean commercial = style == Style.COMMERCIAL;
        for (int k = 1; k < floors; k++) {
            int y = 4 * k;
            // 앞면 창: 4칸 칸살마다 2칸 창 (상가는 3칸 큰 창)
            for (int i = i0 + 1; i < i1; i++) {
                int bay = (i - i0 - 1) % 4;
                boolean window = commercial ? bay != 0 : bay == 1 || bay == 2;
                if (window && !inCore(i)) {
                    v.set(i, y + 1, jf, GLASS_PANE);
                    v.set(i, y + 2, jf, GLASS_PANE);
                    if (!commercial) {
                        v.set(i, y, jf + 1, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E)); // 창턱
                    }
                }
            }
            // 뒤·옆 창
            for (int i = i0 + 2; i < i1 - 1; i += 4) {
                v.set(i, y + 1, j0, GLASS_PANE);
                v.set(i, y + 2, j0, GLASS_PANE);
            }
            for (int j = j0 + 3; j < jf - 1; j += 5) {
                v.set(i0, y + 1, j, GLASS_PANE);
                v.set(i0, y + 2, j, GLASS_PANE);
                v.set(i1, y + 1, j, GLASS_PANE);
                v.set(i1, y + 2, j, GLASS_PANE);
            }
            // 계단실 세로 창 (층 중간 높이)
            v.set(coreI + 1, y + 2, jf, GLASS_PANE);
            v.set(coreI + 1, y + 3, jf, GLASS_PANE);
            if (commercial) {
                // 층마다 간판: 창 아래 띠 + 표지판
                Object[] board = BOARDS[r.nextInt(BOARDS.length)];
                String[] name = KoreanNames.upper(r);
                v.fill(i0, y, jf, i1, y, jf, (Block) board[0]);
                v.set((i0 + i1) / 2, y, jf + 1, Blocks.wallSign((String) board[1], "south", (String) board[2], true, k + "층 " + name[1], name[0]));
                // 창에 붙인 글씨 대신 블라인드 색
                if (r.nextBoolean()) {
                    for (int i = i0 + 2; i < i1; i += 4) {
                        v.set(i, y + 1, jf - 1, WHITE_WOOL);
                    }
                }
            } else {
                // 2층 창은 방범창, 위층 일부는 발코니
                if (k == 1) {
                    for (int i = i0 + 1; i < i1; i++) {
                        int bay = (i - i0 - 1) % 4;
                        if ((bay == 1 || bay == 2) && !inCore(i)) {
                            v.set(i, y + 1, jf + 1, IRON_BARS);
                            v.set(i, y + 2, jf + 1, IRON_BARS);
                        }
                    }
                } else if (r.nextInt(10) < 3) {
                    int a = coreI == i0 ? i0 + 5 : i0 + 1, b = coreI == i0 ? i1 - 1 : i1 - 5;
                    if (b - a >= 3) {
                        v.fill(a, y - 1, jf + 1, b, y - 1, jf + 1, trim);
                        v.fill(a, y, jf + 1, b, y, jf + 1, IRON_BARS);
                    }
                }
            }
        }
        // 상가: 모서리 돌출 간판 (2·3층)
        if (commercial || (style == Style.MIXED && r.nextBoolean())) {
            String[] name = commercial ? KoreanNames.upper(r) : KoreanNames.shop(r);
            Object[] board = BOARDS[r.nextInt(BOARDS.length)];
            int i = r.nextBoolean() ? i0 : i1;
            v.set(i, 6, jf + 1, Blocks.wallHangingSign((String) board[1], "east", (String) board[2], true, "", name[0]));
        }
    }

    private boolean inCore(int i) {
        return i >= coreI && i <= coreI + 3 && i - coreI == 1;
    }

    // ------------------------------------------------------------------ 옥상

    private void roof() {
        boolean commercial = style == Style.COMMERCIAL;
        v.fill(i0, top, j0, i1, top, jf, commercial ? SMOOTH_STONE : GREEN_CONCRETE);
        // 난간 + 위 마감
        v.walls(i0, top + 1, j0, i1, top + 1, jf, wall);
        for (int i = i0; i <= i1; i++) {
            v.set(i, top + 2, j0, SMOOTH_STONE_SLAB);
            v.set(i, top + 2, jf, SMOOTH_STONE_SLAB);
        }
        for (int j = j0; j <= jf; j++) {
            v.set(i0, top + 2, j, SMOOTH_STONE_SLAB);
            v.set(i1, top + 2, j, SMOOTH_STONE_SLAB);
        }
        // 계단실 옥탑 (사다리 위)
        int ci = coreI;
        v.walls(ci, top + 1, j0, ci + 3, top + 3, j0 + 3, wall);
        v.fill(ci, top + 4, j0, ci + 3, top + 4, j0 + 3, SMOOTH_STONE);
        v.fill(ci + 1, top + 1, j0 + 1, ci + 2, top + 3, j0 + 2, AIR);
        v.set(ci + 2, top + 1, j0 + 3, door("iron", "south", false));
        v.set(ci + 2, top + 2, j0 + 3, door("iron", "south", true));
        v.set(ladderI, top + 1, ladderJ, null);
        // 물탱크 (스테인리스 또는 파란 탱크)
        if (w >= 9 && d >= 9) {
            double ti = coreI == i0 ? i1 - 2.5 : i0 + 2.5;
            Block tank = r.nextBoolean() ? IRON_BLOCK : LIGHT_BLUE_CONCRETE;
            v.cylinder(ti, j0 + 2.5, 1.5, top + 1, top + 2, tank);
            // 안테나
            v.fill(coreI + 1, top + 5, j0 + 1, coreI + 1, top + 7, j0 + 1, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
        }
        // 실외기 몇 대
        for (int n = 0; n < 2 + r.nextInt(3); n++) {
            int ai = i0 + 2 + r.nextInt(Math.max(1, i1 - i0 - 3)), aj = j0 + 5 + r.nextInt(Math.max(1, jf - j0 - 6));
            if (v.get(ai, top + 1, aj) == null) {
                v.set(ai, top + 1, aj, SMOOTH_STONE);
            }
        }
    }

    // ------------------------------------------------------------------ 생활 디테일

    private void details() {
        // 노란 가스관 (앞 모서리를 따라 위로) + 층마다 가로로 들어감
        int gi = coreI == i0 ? i1 : i0;
        Block gas = Block.of("yellow_stained_glass_pane", 0xE5E533);
        for (int y = 0; y <= top - 2; y++) {
            setIfFree(gi + (gi == i0 ? -1 : 1), y, jf - 1, gas);
        }
        // 옆벽 실외기 (창 옆)
        for (int k = 1; k < floors; k++) {
            if (r.nextInt(10) < 6) {
                int side = r.nextBoolean() ? i0 - 1 : i1 + 1;
                setIfFree(side, 4 * k + 1, j0 + 2 + r.nextInt(Math.max(1, jf - j0 - 4)), SMOOTH_QUARTZ);
            }
        }
        // 앞 화분·쓰레기 봉투
        if (r.nextBoolean()) {
            setIfFree(i0, 0, jf + 1, r.nextBoolean() ? FLOWER_POT_FERN : BLACK_CONCRETE);
        }
    }

    private void setIfFree(int i, int y, int j, Block b) {
        if (v.inside(i, y, j) && v.get(i, y, j) == null) {
            v.set(i, y, j, b);
        }
    }

    /** 2×4 칸 세워 둔 차 (i, j 가 왼쪽 앞, 앞이 +j) */
    static void car(Voxels v, Random r, int i, int j) {
        Block body = concrete(CAR_COLORS[r.nextInt(CAR_COLORS.length)]);
        v.fill(i, 0, j, i + 1, 0, j + 3, body);
        v.fill(i, 1, j + 1, i + 1, 1, j + 2, Block.of("black_stained_glass", 0x191919));
    }

}
