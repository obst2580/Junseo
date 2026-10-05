package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 한국 동네의 낮은 건물. 한 층은 4칸(바닥 1 + 빈 칸 3).
 * <ul>
 *   <li>VILLA (다세대·빌라): 1층 필로티 주차장(주차선만, 차 모형 없음), 2~4층 집. 붉은 벽돌·베이지 타일·흰 벽,
 *       알루미늄 창과 창턱, 방범창, 작은 발코니, 계단실 창, 실외기, 노란 가스관, 문패,
 *       옥상 초록 방수 도장·물탱크·계단실 옥탑</li>
 *   <li>MIXED (상가주택): 1층 가게(유리 가게 앞, 간판, 차양), 위층 집</li>
 *   <li>COMMERCIAL (근린상가): 4~6층, 1층 가게, 위층 학원·의원·노래방·PC방… 간판과 그에 맞는 실내, 돌출 간판</li>
 * </ul>
 * 뒤 구석의 계단실(꺾인 계단)로 걸어서 옥상까지 오릅니다. 상가는 1층 가게 옆 계단 입구로 들어갑니다.
 * 간판에는 진짜 표지판 글씨가 붙습니다 (생성기가 청크를 처음 불러올 때 씀).
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
    private static final Block SILL = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);

    private final Voxels v;
    private final Random r;
    private final Style style;
    private final int w, d, floors, top, i0, i1, j0, jf;
    private final Block wall, trim, base;
    private final boolean piloti;
    /** 계단실: 벽 포함 i 범위 cx0..cx1, j 범위 j0..cz1 (뒤 구석). 계단 줄(오르는 쪽)은 stairI */
    private final boolean coreLeft;
    private final int cx0, cx1, cz1, stairI;
    private final int[] levels;
    /** 층마다 위층 쓰임 (상가 간판 종류) */
    private final String[] uses;

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
        coreLeft = r.nextBoolean();
        cx0 = coreLeft ? i0 : i1 - 4;
        cx1 = cx0 + 4;
        levels = Floors.levels(4, 4, floors);
        cz1 = j0 + Interior.stairDepth(levels) + 1;
        stairI = coreLeft ? cx0 + 3 : cx1 - 1;
        uses = new String[floors];
    }

    static Voxels build(int w, int d, Random r, Style style) {
        ShopHouse h = new ShopHouse(w, d, r, style);
        h.shell();
        h.core();
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

    /** 계단실 좌표계: +b 가 북쪽(뒤), b = -1 벽(출입구)이 앞을 봄 */
    private Frame coreFrame() {
        return Frame.facing(v, coreLeft ? cx0 + 3 : cx1 - 1, cz1 - 1, "north");
    }

    /** 계단실(벽 포함) 안인지 */
    private boolean inCore(int i, int j) {
        return i >= cx0 && i <= cx1 && j >= j0 && j <= cz1;
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
    }

    /** 뒤 구석 계단실 (1칸 줄 꺾인 계단, 옥상 옥탑까지), 층마다 계단실 창 */
    private void core() {
        Interior.stairCore(coreFrame(), levels, wall, "stone_brick", 0x7A7979, 1);
        v.fill(cx0, top + 4, j0, cx1, top + 4, cz1, SMOOTH_STONE);
        for (int k = 0; k < floors; k++) {
            // 뒷벽 계단참 높이에 작은 창
            v.set(cx0 + 2, 4 * k + 3, j0, GLASS_PANE);
        }
    }

    /** 1층 필로티 주차장: 기둥만 남기고 비움, 주차선, 공동현관, 문패 */
    private void piloti() {
        v.fill(i0 + 1, 0, jf, i1 - 1, 2, jf, AIR);
        v.fill(i0, 0, cz1 + 1, i0, 2, jf - 1, AIR);
        v.fill(i1, 0, cz1 + 1, i1, 2, jf - 1, AIR);
        if (coreLeft) {
            v.fill(i1, 0, j0 + 1, i1, 2, cz1, AIR);
        } else {
            v.fill(i0, 0, j0 + 1, i0, 2, cz1, AIR);
        }
        for (int i = i0; i <= i1; i += 4) {
            v.fill(i, 0, jf, i, 2, jf, trim);
        }
        v.fill(i1, 0, jf, i1, 2, jf, trim);
        int mid = (j0 + jf) / 2;
        v.fill(i0, 0, mid, i0, 2, mid, trim);
        v.fill(i1, 0, mid, i1, 2, mid, trim);
        // 천장 등
        for (int i = i0 + 2; i < i1; i += 4) {
            if (!inCore(i, mid)) {
                v.set(i, 2, mid, LANTERN_HANGING);
            }
        }
        // 공동현관 문과 문패
        v.set(stairI, 0, cz1, door("pale_oak", "south", false));
        v.set(stairI, 1, cz1, door("pale_oak", "south", true));
        v.set(stairI - 2, 1, cz1 + 1, Blocks.wallSign("dark_oak", "south", "white", false, KoreanNames.villa(r), KoreanNames.address(r)));
        // 주차선 (계단실 앞은 비움)
        for (int i = i0 + 1; i + 2 < i1; i += 3) {
            if (i <= cx1 && i + 2 >= cx0) {
                continue;
            }
            v.fill(i, -1, j0 + 2, i, -1, jf - 1, WHITE_CONCRETE);
        }
    }

    /** 1층 가게: 계단 입구 옆으로 유리 가게 앞(어두운 틀), 문, 간판판 + 표지판 글씨, 차양, 가게 안 진열대·계산대 */
    private void shopFront() {
        // 계단 입구 통로 (계단실 앞에서 길까지)
        int ca = cx0 + 1, cb = cx1 - 1;
        v.fill(coreLeft ? cx1 : cx0, 0, cz1, coreLeft ? cx1 : cx0, 2, jf, wall);
        v.fill(ca, 0, jf, cb, 2, jf, POLISHED_DEEPSLATE);
        v.set(stairI, 0, jf, door("pale_oak", "south", false));
        v.set(stairI, 1, jf, door("pale_oak", "south", true));
        v.set(ca, 2, jf, GLASS_PANE);
        v.set(cb, 2, jf, GLASS_PANE);
        v.set((ca + cb) / 2, 2, jf - 2, LANTERN_HANGING);
        int sa = coreLeft ? cx1 + 1 : i0 + 1, sb = coreLeft ? i1 - 1 : cx0 - 1;
        if (sb - sa < 3) {
            return;
        }
        int units = Math.max(1, (sb - sa) / 7);
        int unitW = (sb - sa + 1) / units;
        for (int u = 0; u < units; u++) {
            int a = sa + u * unitW, b = u == units - 1 ? sb : a + unitW - 2;
            // 기둥과 유리
            for (int i = a; i <= b; i++) {
                for (int y = 0; y <= 2; y++) {
                    v.set(i, y, jf, y == 0 ? POLISHED_DEEPSLATE : GLASS_PANE);
                }
            }
            if (u < units - 1) {
                v.fill(b + 1, 0, j0 + 1, b + 1, 2, jf, base); // 가게 사이 벽
            }
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
                    if (i != doorI) {
                        v.set(i, 2, jf + 1, Block.of(wood + "_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", 0x725430));
                    }
                }
            }
            shopInside(a, b, name[1]);
        }
        v.fill(i1, 0, jf, i1, 2, jf, coreLeft ? POLISHED_DEEPSLATE : wall);
    }

    /** 가게 안 (a..b 폭): 계산대, 업종에 맞는 진열대·식탁 */
    private void shopInside(int a, int b, String kind) {
        Frame f = Frame.of(v);
        int back = j0 + 1, front = jf - 1;
        boolean food = kind.contains("식당") || kind.contains("분식") || kind.contains("치킨") || kind.contains("호프")
                || kind.contains("중국집") || kind.contains("카페") || kind.contains("국밥") || kind.contains("곱창")
                || kind.contains("통닭") || kind.contains("순대");
        // 계산대 (안쪽 옆벽)
        v.set(b, 0, front - 2, Furniture.COUNTER);
        v.set(b, 0, front - 3, Furniture.COUNTER);
        if (food) {
            // 식탁과 의자, 안쪽 주방
            for (int j = front - 2; j >= back + 3; j -= 3) {
                for (int i = a + 1; i + 1 <= b - 2; i += 3) {
                    if (!inCore(i, j) && !inCore(i + 1, j)) {
                        Furniture.table(f, i, 0, j, 2, 1, "oak");
                    }
                }
            }
            for (int i = a; i <= b; i++) {
                if (!inCore(i, back)) {
                    v.set(i, 0, back, i == a + 1 ? SMOKER : Furniture.COUNTER);
                }
            }
        } else {
            // 진열대 줄
            for (int i = a + 1; i <= b - 2; i += 3) {
                for (int j = back + 1; j <= front - 3; j++) {
                    if (!inCore(i, j)) {
                        v.set(i, 0, j, SMOOTH_STONE_SLAB.with("type=top,waterlogged=false"));
                        v.set(i, 1, j, (j & 1) == 0 ? BARREL : Furniture.BOOKSHELF);
                    }
                }
            }
        }
        for (int j = back + 2; j <= front; j += 4) {
            v.set((a + b) / 2, 2, j, Interior.LIGHT);
        }
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
                if (window) {
                    v.set(i, y + 1, jf, GLASS_PANE);
                    v.set(i, y + 2, jf, GLASS_PANE);
                    if (!commercial) {
                        v.set(i, y, jf + 1, SILL); // 창턱
                    }
                }
            }
            // 뒤·옆 창 (계단실 쪽 제외)
            for (int i = i0 + 2; i < i1 - 1; i += 4) {
                if (!inCore(i, j0)) {
                    v.set(i, y + 1, j0, GLASS_PANE);
                    v.set(i, y + 2, j0, GLASS_PANE);
                }
            }
            for (int j = j0 + 3; j < jf - 1; j += 5) {
                if (!inCore(i0, j)) {
                    v.set(i0, y + 1, j, GLASS_PANE);
                    v.set(i0, y + 2, j, GLASS_PANE);
                }
                if (!inCore(i1, j)) {
                    v.set(i1, y + 1, j, GLASS_PANE);
                    v.set(i1, y + 2, j, GLASS_PANE);
                }
            }
            if (commercial) {
                // 층마다 간판: 창 아래 띠 + 표지판, 안은 간판 업종대로
                Object[] board = BOARDS[r.nextInt(BOARDS.length)];
                String[] name = KoreanNames.upper(r);
                uses[k] = name[1];
                v.fill(i0, y, jf, i1, y, jf, (Block) board[0]);
                v.set((i0 + i1) / 2, y, jf + 1, Blocks.wallSign((String) board[1], "south", (String) board[2], true, k + "층 " + name[1], name[0]));
                if (r.nextBoolean()) {
                    for (int i = i0 + 2; i < i1; i += 4) {
                        v.set(i, y + 1, jf - 1, WHITE_WOOL); // 블라인드
                    }
                }
                business(k, name[1]);
            } else {
                home(k);
                // 2층 창은 방범창, 위층 일부는 발코니
                if (k == 1) {
                    for (int i = i0 + 1; i < i1; i++) {
                        int bay = (i - i0 - 1) % 4;
                        if (bay == 1 || bay == 2) {
                            v.set(i, y + 1, jf + 1, IRON_BARS);
                            v.set(i, y + 2, jf + 1, IRON_BARS);
                        }
                    }
                } else if (r.nextInt(10) < 3) {
                    int a = i0 + 1, b = i1 - 1;
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

    /**
     * 위층 집 한 층 (한 세대): 계단실 앞이 현관, 계단실 옆 뒤쪽에 욕실과 작은 방, 앞쪽에 거실과 안방, 현관 옆 부엌.
     */
    private void home(int k) {
        int L = 4 * k, top = L + 2;
        Frame f = Frame.of(v);
        Block in = Interior.INNER_WALL;
        int oa = coreLeft ? cx1 + 1 : i0 + 1, ob = coreLeft ? i1 - 1 : cx0 - 1; // 계단실 옆 칸들
        int ha = cx0 + 1, hb = cx1 - 1; // 계단실 앞 칸들 (현관·부엌)
        finish(L, r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        Interior.floor(f, Math.max(ha, stairI - 1), cz1 + 1, Math.min(hb, stairI + 1), Math.min(jf - 1, cz1 + 2), L, Interior.TILE);
        if (ob - oa < 3 || jf - cz1 < 4) {
            lights(L);
            return;
        }
        // 계단실 옆 뒤쪽: 욕실(바깥쪽 3칸) + 작은 방
        int bathA = coreLeft ? ob - 2 : oa, bathB = bathA + 2;
        int backEnd = cz1; // 계단실 깊이까지
        int bwall = coreLeft ? bathA - 1 : bathB + 1;
        v.fill(bwall, L, j0 + 1, bwall, top, backEnd, in);
        v.fill(bathA, L, backEnd, bathB, top, backEnd, in);
        Interior.door(f, (bathA + bathB) / 2, L, backEnd, "pale_oak", "south");
        Interior.floor(f, bathA, j0 + 1, bathB, backEnd - 1, L, Interior.TILE);
        v.set(coreLeft ? bathB : bathA, L, j0 + 1, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
        v.set(coreLeft ? bathA : bathB, L, j0 + 1, CAULDRON);
        v.set((bathA + bathB) / 2, top, j0 + 2, Interior.LIGHT);
        // 작은 방 (계단실과 욕실 사이)
        int ra = coreLeft ? oa : bathB + 2, rb = coreLeft ? bathA - 2 : ob;
        if (rb - ra >= 2) {
            v.fill(ra, L, backEnd, rb, top, backEnd, in);
            Interior.door(f, (ra + rb) / 2, L, backEnd, "pale_oak", "south");
            Furniture.bed(f, r, coreLeft ? ra : rb, L, j0 + 1, "south");
            v.set((ra + rb) / 2, top, (j0 + backEnd) / 2, Interior.LIGHT);
        }
        // 부엌: 계단실 앞, 현관 옆 벽을 따라
        int kStart = cz1 + 3;
        if (kStart <= jf - 4) {
            int ki = coreLeft ? cx0 + 1 : cx1 - 1;
            for (int j = kStart; j <= Math.min(jf - 3, kStart + 3); j++) {
                v.set(ki, L, j, j == kStart + 1 ? CAULDRON : j == kStart + 2 ? SMOKER : Furniture.COUNTER);
                v.set(ki, L + 2, j, Furniture.COUNTER);
            }
        }
        // 앞쪽: 안방(바깥쪽) + 거실
        int front0 = backEnd + 2;
        if (jf - 1 - front0 >= 3) {
            int ma = coreLeft ? ob - 4 : oa, mb = coreLeft ? ob : oa + 4;
            if (mb - ma >= 3 && Math.abs(ma - (coreLeft ? cx1 : cx0)) > 2) {
                int mwall = coreLeft ? ma - 1 : mb + 1;
                v.fill(mwall, L, front0, mwall, top, jf - 1, in);
                v.fill(ma, L, front0, mb, top, front0, in);
                Interior.door(f, coreLeft ? ma + 1 : mb - 1, L, front0, "pale_oak", "south");
                Furniture.bed(f, r, coreLeft ? mb : ma, L, jf - 2, "north");
                v.set(coreLeft ? ma : mb, L, front0 + 1, Rooms.WARDROBE);
                v.set(coreLeft ? ma : mb, L + 1, front0 + 1, Rooms.WARDROBE);
                v.set((ma + mb) / 2, top, (front0 + jf) / 2, Interior.LIGHT);
            }
            // 거실: 소파와 TV
            int la = coreLeft ? cx0 + 1 : mb + 2, lb = coreLeft ? ma - 2 : cx1 - 1;
            if (lb - la >= 3) {
                int mid = (la + lb) / 2;
                v.set(mid, L, front0, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
                v.set(mid, L + 1, front0, Furniture.TV);
                Furniture.sofa(f, r, mid - 1, L, jf - 2, 3, "north");
                v.set(mid, L, jf - 4, Block.of("gray_carpet", 0x3E4447));
                Furniture.plant(f, r, la, L, jf - 1);
                v.set(mid, top, (front0 + jf) / 2, Interior.LIGHT);
            }
        }
        v.set(stairI, top, cz1 + 2, Interior.LIGHT);
    }

    /** 천장 등 4칸 간격 (계단실 둘레는 비움) */
    private void lights(int level) {
        for (int j = j0 + 2; j <= jf - 1; j += 4) {
            for (int i = i0 + 2; i <= i1 - 1; i += 4) {
                if (!(i >= cx0 - 1 && i <= cx1 + 1 && j <= cz1 + 1) && v.get(i, level + 2, j) == null) {
                    v.set(i, level + 2, j, Interior.LIGHT);
                }
            }
        }
    }

    /** 바닥 마감 (계단실은 건드리지 않음) */
    private void finish(int level, Block b) {
        for (int j = j0 + 1; j <= jf - 1; j++) {
            for (int i = i0 + 1; i <= i1 - 1; i++) {
                if (!inCore(i, j)) {
                    v.set(i, level - 1, j, b);
                }
            }
        }
    }

    /** 상가 위층: 간판 업종에 맞는 실내 */
    private void business(int k, String kind) {
        int L = 4 * k, top = L + 2;
        Frame f = Frame.of(v);
        int a0 = i0 + 1, a1 = i1 - 1, b0 = j0 + 1, b1 = jf - 1;
        java.util.function.BiPredicate<Integer, Integer> free = (i, j) -> i >= a0 && i <= a1 && j >= b0 && j <= b1
                && !(i >= cx0 - 1 && i <= cx1 + 1 && j <= cz1 + 2);
        finish(L, kind.equals("PC방") || kind.equals("노래방") ? Block.of("gray_concrete", 0x36393D)
                : kind.equals("교회") ? Block.of("red_concrete_powder", 0x9C2B27) : Block.of("light_gray_concrete", 0x7D7D73));
        // 입구 안내 데스크 (계단실 앞)
        int deskI = coreLeft ? cx1 + 2 : cx0 - 2;
        if (free.test(deskI, cz1 + 2)) {
            v.set(deskI, L, cz1 + 2, Furniture.COUNTER);
        }
        switch (kind) {
            case "학원", "독서실" -> {
                // 옆벽 칠판을 보는 책상 줄
                int bi = coreLeft ? a1 : a0, dir = coreLeft ? -1 : 1;
                for (int j = cz1 + 3; j <= b1 - 1; j++) {
                    v.set(bi, L + 1, j, Block.of("green_concrete", 0x495B24));
                    v.set(bi, L + 2, j, Block.of("green_concrete", 0x495B24));
                }
                for (int i = bi + 3 * dir; coreLeft ? i >= a0 + 1 : i <= a1 - 1; i += 2 * dir) {
                    for (int j = b0 + 1; j <= b1 - 1; j += 2) {
                        if (free.test(i, j) && free.test(i + dir, j)) {
                            v.set(i, L, j, Furniture.DESK_TOP);
                            Furniture.chair(f, i + dir, L, j, coreLeft ? "west" : "east", "oak");
                        }
                    }
                }
            }
            case "PC방" -> {
                for (int j = b0 + 1; j <= b1 - 1; j += 3) {
                    for (int i = a0 + 1; i <= a1 - 1; i++) {
                        if (free.test(i, j) && free.test(i, j + 1)) {
                            Furniture.desk(f, i, L, j, "south");
                        }
                    }
                }
            }
            case "노래방" -> {
                // 작은 방들 (3칸 너비), 방마다 소파와 화면
                for (int i = a0; i + 3 <= a1; i += 4) {
                    if (!free.test(i, b0) || !free.test(i + 3, b0 + 4)) {
                        continue;
                    }
                    v.fill(i + 3, L, b0, i + 3, top, b0 + 4, Interior.INNER_WALL);
                    v.fill(i, L, b0 + 4, i + 3, top, b0 + 4, Interior.INNER_WALL);
                    Interior.door(f, i + 1, L, b0 + 4, "dark_oak", "south");
                    Furniture.sofa(f.sub(i, b0, "west"), r, 0, L, 0, 3, "north");
                    v.set(i + 3, L + 1, b0 + 2, Furniture.TV);
                    v.set(i + 1, top, b0 + 2, Block.of("shroomlight", 0xF09246));
                }
            }
            case "의원", "치과", "한의원" -> {
                // 대기 의자, 진료실 두 칸 (침대)
                for (int i = a0 + 1; i <= a0 + 4; i++) {
                    if (free.test(i, b1 - 1)) {
                        Furniture.chair(f, i, L, b1 - 1, "south", "birch");
                    }
                }
                int ra = coreLeft ? a1 - 4 : a0, rb = ra + 4;
                for (int n = 0; n < 2; n++) {
                    int jb = b0 + n * 4;
                    if (!free.test(ra, jb) || !free.test(rb, jb + 3)) {
                        continue;
                    }
                    v.fill(coreLeft ? ra - 1 : rb + 1, L, jb, coreLeft ? ra - 1 : rb + 1, top, jb + 3, Interior.INNER_WALL);
                    v.fill(ra, L, jb + 3, rb, top, jb + 3, Interior.INNER_WALL);
                    Interior.door(f, ra + 2, L, jb + 3, "pale_oak", "south");
                    v.set(ra + 1, L, jb + 1, Block.of("white_wool", 0xE9ECEC));
                    v.set(ra + 2, L, jb + 1, Block.of("white_wool", 0xE9ECEC));
                    v.set(rb - 1, L, jb, Furniture.DESK_TOP);
                    v.set(ra + 2, top, jb + 1, Interior.LIGHT);
                }
            }
            case "당구장" -> {
                for (int j = b0 + 1; j + 3 <= b1; j += 5) {
                    for (int i = a0 + 1; i + 1 <= a1 - 1; i += 4) {
                        if (free.test(i, j) && free.test(i + 1, j + 3)) {
                            v.fill(i, L, j, i + 1, L, j + 3, Block.of("green_wool", 0x546D1B));
                        }
                    }
                }
            }
            case "교회" -> {
                // 앞쪽 강대상을 보는 긴 의자
                for (int i = a0 + 2; i <= a1 - 2; i++) {
                    if (free.test(i, b1)) {
                        v.set(i, L, b1, Block.of("dark_oak_planks", 0x432B14));
                    }
                }
                for (int j = b1 - 3; j >= b0 + 1; j -= 2) {
                    for (int i = a0 + 1; i <= a1 - 1; i++) {
                        if (free.test(i, j) && Math.abs(i - (a0 + a1) / 2) > 0) {
                            Furniture.chair(f, i, L, j, "north", "oak");
                        }
                    }
                }
            }
            case "태권도", "운동" -> {
                for (int j = b0; j <= b1; j++) {
                    for (int i = a0; i <= a1; i++) {
                        if (free.test(i, j)) {
                            v.set(i, L, j, Block.of(((i + j) & 1) == 0 ? "blue_carpet" : "red_carpet", 0x35399D));
                        }
                    }
                }
                int mi = coreLeft ? a1 : a0;
                for (int j = cz1 + 3; j <= b1; j++) {
                    v.fill(mi, L, j, mi, L + 2, j, Block.of("light_blue_stained_glass", 0x6699D8)); // 거울 벽
                }
            }
            default -> Rooms.office(f, r, a0 - 1, b0 - 1, a1 + 1, b1 + 1, L, 4, free);
        }
        lights(L);
    }

    // ------------------------------------------------------------------ 옥상

    private void roof() {
        boolean commercial = style == Style.COMMERCIAL;
        for (int j = j0; j <= jf; j++) {
            for (int i = i0; i <= i1; i++) {
                if (!inCore(i, j) || v.get(i, top, j) == null) {
                    v.set(i, top, j, commercial ? SMOOTH_STONE : GREEN_CONCRETE);
                }
            }
        }
        // 난간 + 위 마감
        for (int i = i0; i <= i1; i++) {
            for (int j : new int[]{j0, jf}) {
                if (!inCore(i, j)) {
                    v.set(i, top + 1, j, wall);
                    v.set(i, top + 2, j, SMOOTH_STONE_SLAB);
                }
            }
        }
        for (int j = j0; j <= jf; j++) {
            for (int i : new int[]{i0, i1}) {
                if (!inCore(i, j)) {
                    v.set(i, top + 1, j, wall);
                    v.set(i, top + 2, j, SMOOTH_STONE_SLAB);
                }
            }
        }
        // 물탱크 (스테인리스 또는 파란 탱크)
        if (w >= 9 && d >= 9) {
            double ti = coreLeft ? i1 - 2.5 : i0 + 2.5;
            Block tank = r.nextBoolean() ? IRON_BLOCK : LIGHT_BLUE_CONCRETE;
            v.cylinder(ti, j0 + 2.5, 1.5, top + 1, top + 2, tank);
            // 안테나 (계단실 옥탑 위)
            v.fill(stairI, top + 5, j0 + 1, stairI, top + 6, j0 + 1, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
        }
        // 실외기 몇 대
        for (int n = 0; n < 2 + r.nextInt(3); n++) {
            int ai = i0 + 2 + r.nextInt(Math.max(1, i1 - i0 - 3)), aj = cz1 + 2 + r.nextInt(Math.max(1, jf - cz1 - 3));
            if (v.get(ai, top + 1, aj) == null) {
                v.set(ai, top + 1, aj, SMOOTH_STONE);
            }
        }
    }

    // ------------------------------------------------------------------ 생활 디테일

    private void details() {
        // 노란 가스관 (앞 모서리를 따라 위로)
        int gi = coreLeft ? i1 : i0;
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
            setIfFree(coreLeft ? i1 : i0, 0, jf + 1, r.nextBoolean() ? FLOWER_POT_FERN : BLACK_CONCRETE);
        }
    }

    private void setIfFree(int i, int y, int j, Block b) {
        if (v.inside(i, y, j) && v.get(i, y, j) == null) {
            v.set(i, y, j, b);
        }
    }
}
