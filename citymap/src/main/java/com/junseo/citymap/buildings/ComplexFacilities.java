package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 아파트 단지 안의 시설 (동 건물 말고).
 * <ul>
 *   <li>{@link #gateRoad}: 정문 진입로 — 차로 둘(아스팔트·노란 중앙선), 양쪽 보도와 화단, 과속방지턱, 문주(돌 기둥 둘과
 *       길 위를 건너는 보, 단지 이름 표지판), 경비실, 들어 올린 차단기, 보행등. 옛 단지는 문주 대신 낮은 이름 돌담</li>
 *   <li>{@link #ramp}: 지하주차장 입구 — 지붕 덮인 경사로(반 칸씩 내려감), 높이 제한 봉과 표지판, 지하 1층 주차장
 *       (주차선, 천장 등, 기둥), 사람이 오르내리는 계단 출입구, 잔디 위 환기구. 꼭대기 앞마당과 지하에 차 자리</li>
 *   <li>{@link #shops}: 단지 상가 2층 — 1층 가게(편의점·부동산·세탁소·미용실·치킨집), 2층 학원·태권도·소아과, 간판</li>
 *   <li>{@link #management}: 관리동 — 1층 관리사무소·경로당, 2층 어린이집</li>
 *   <li>{@link #community}: 커뮤니티센터 — 1층 카페·피트니스, 2층 독서실·골프연습장</li>
 *   <li>{@link #playground}: 놀이터 — 탄성 포장, 미끄럼틀과 오르는 망이 달린 놀이대, 그네, 모래밭, 벤치</li>
 *   <li>{@link #park}: 단지 공원 — 산책로, 나무, 잔디, 벤치, 정자, 운동기구, 보행등</li>
 * </ul>
 * 모두 정면은 남쪽(j = d-1). 차 모형은 두지 않고 주차 칸마다 차 꺼내는 자리만 표시합니다.
 */
final class ComplexFacilities {
    static final Block ASPHALT = GRAY_CONCRETE;
    static final Block WALK = LIGHT_GRAY_CONCRETE;
    static final Block CURB = POLISHED_ANDESITE;
    static final Block LINE = WHITE_CONCRETE;
    static final Block RUBBER = Block.of("green_terracotta", 0x4C532A);
    static final Block RUBBER2 = Block.of("red_terracotta", 0x8F3D2E);
    static final Block SAND = Block.of("sand", 0xDBD3A0);
    static final Block CEIL_LIGHT = Interior.LIGHT;

    /** 문주 진입로 최소 크기 (정면 너비 × 깊이) */
    static final int GATE_W = 16, GATE_D = 16;
    /** 지하주차장 입구 최소 크기 */
    static final int RAMP_W = 18, RAMP_D = 30;

    // ================================================================== 정문 진입로

    /**
     * 정문 진입로 (정면 = 바깥 큰길 쪽). 너비 w (16 이상), 깊이 d. 차로는 가운데 6칸.
     *
     * @param grand 문주(새 단지) 인지, 낮은 이름 돌담(옛 단지) 인지
     * @param stone 문주 돌
     */
    static Voxels gateRoad(int w, int d, String complex, boolean grand, Block stone, Block cap, Random r) {
        Voxels v = new Voxels(w, d, -1, 12);
        int c = w / 2, l0 = c - 3, l1 = c + 2;
        int s0 = l0 - 2, s1 = l1 + 2; // 보도 [s0, l0-1], [l1+1, s1]
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                Block b;
                if (i >= l0 && i <= l1) {
                    b = ASPHALT;
                } else if (i == l0 - 1 || i == l1 + 1) {
                    b = CURB;
                } else if (i >= s0 && i <= s1) {
                    b = (i + j) % 5 == 0 ? Block.of("stone_bricks", 0x7A7979) : WALK;
                } else {
                    b = GRASS;
                }
                v.set(i, -1, j, b);
            }
            // 노란 중앙선 (두 줄)
            if (j % 4 < 2) {
                v.set(c - 1, -1, j, YELLOW_CONCRETE);
            }
        }
        // 화단 (차로 밖 잔디): 회양목과 보행등, 작은 나무
        for (int j = 1; j < d - 1; j++) {
            for (int i = 0; i < w; i++) {
                if (v.get(i, -1, j) == GRASS && (i == 0 || i == w - 1 || i == s0 - 1 || i == s1 + 1)) {
                    v.set(i, 0, j, Kit.BOX_HEDGE);
                }
            }
        }
        for (int j = 3; j < d - 3; j += 7) {
            if (s0 >= 1) {
                Kit.parkLamp(v, s0 - 1, 0, j);
            }
            if (s1 + 1 < w) {
                Kit.parkLamp(v, s1 + 1, 0, j);
            }
        }
        // 과속방지턱 (노란·검은 반 블록 줄무늬)
        int bump = d / 2;
        for (int i = l0; i <= l1; i++) {
            v.set(i, 0, bump, (i % 2 == 0) ? Block.of("cut_sandstone_slab[type=bottom,waterlogged=false]", 0xD9CD9F)
                    : Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
        }
        String[] lines = nameLines(complex);
        if (grand) {
            // 문주: 보도 바깥쪽 돌 기둥 둘 (3×4), 길 위를 건너는 보 (y 7..9)
            int j0 = d - 6, j1 = d - 3;
            int pa0 = Math.max(0, s0 - 3), pa1 = s0 - 1, pb0 = s1 + 1, pb1 = Math.min(w - 1, s1 + 3);
            v.fill(pa0, 0, j0, pa1, 10, j1, stone);
            v.fill(pb0, 0, j0, pb1, 10, j1, stone);
            v.fill(pa0, 7, j0 + 1, pb1, 9, j1 - 1, stone);
            v.fill(pa0, 10, j0 + 1, pb1, 10, j1 - 1, cap);
            v.fill(pa0, 11, j0, pa1, 11, j1, cap);
            v.fill(pb0, 11, j0, pb1, 11, j1, cap);
            // 보 아래 등
            for (int i = l0; i <= l1; i += 2) {
                v.set(i, 6, j0 + 2, LANTERN_HANGING);
            }
            // 이름 표지판 (보 앞뒤, 가운데)
            v.set(c, 8, j1, Blocks.wallSign("dark_oak", "south", "white", true, lines));
            v.set(c - 1, 8, j1, Blocks.wallSign("dark_oak", "south", "white", true, lines));
            v.set(c, 8, j0, Blocks.wallSign("dark_oak", "north", "white", true, lines));
            v.set(c - 1, 8, j0, Blocks.wallSign("dark_oak", "north", "white", true, lines));
        } else {
            // 옛 단지: 길가 낮은 이름 돌담
            int i0 = Math.max(0, s0 - 4);
            v.fill(i0, 0, d - 3, s0 - 1, 1, d - 3, stone);
            v.fill(i0, 2, d - 3, s0 - 1, 2, d - 3, cap);
            v.set((i0 + s0 - 1) / 2, 1, d - 2, Blocks.wallSign("spruce", "south", "white", false, lines));
        }
        // 경비실 (왼쪽 화단, 유리 창과 문은 보도 쪽)
        if (s0 >= 3 && d - 12 >= 1) {
            int g0 = s0 - 3, g1 = s0 - 1, gj0 = d - 12, gj1 = d - 9;
            v.fill(g0, -1, gj0, g1, -1, gj1, POLISHED_ANDESITE);
            v.fill(g0, 0, gj0, g1, 2, gj1, AIR);
            v.walls(g0, 0, gj0, g1, 2, gj1, WHITE_CONCRETE);
            v.fill(g1, 1, gj0, g1, 1, gj1, GLASS_PANE);
            v.fill(g0, 3, gj0 - 1, g1, 3, gj1 + 1, Kit.SLAB);
            Interior.door(Frame.of(v), g1, 0, gj1 - 1, "spruce", "east");
            v.set(g0 + 1, 0, gj0 + 1, Furniture.DESK_TOP);
            v.set(g0 + 1, 1, gj0, Block.of("black_concrete", 0x080A0F));
            v.set(g1 + 1, 2, gj0 + 1, Blocks.wallSign("birch", "east", "black", false, "", "경비실"));
            // 차단기 (올려 둔 상태, 연석 위)
            v.set(l0 - 1, 0, gj1 + 2, Block.of("yellow_concrete", 0xF0AF15));
            v.fill(l0 - 1, 1, gj1 + 2, l0 - 1, 4, gj1 + 2, Block.of("end_rod[facing=up]", 0xE8E2D8));
        }
        v.connect();
        return v;
    }

    /** 표지판 줄: {"", 이름, "아파트", ""} (길면 나눔) */
    static String[] nameLines(String complex) {
        String n = complex == null ? "" : complex;
        if (n.endsWith("아파트")) {
            n = n.substring(0, n.length() - 3);
        }
        if (n.length() > 9) {
            return new String[]{n.substring(0, 9), n.substring(9, Math.min(18, n.length())), "아파트", ""};
        }
        return new String[]{"", n, "아파트", ""};
    }

    // ================================================================== 지하주차장 입구

    /**
     * 지하주차장 입구와 지하 1층 주차장 (정면 = 차가 들어오는 쪽). w ≥ 18, d ≥ 30.
     * 경사로는 반 칸씩 10칸을 내려가 지하 바닥(y -6, 서는 높이 -5)에 닿습니다.
     */
    static Voxels ramp(int w, int d, Random r) {
        w = Math.max(w, RAMP_W);
        d = Math.max(d, RAMP_D);
        Voxels v = new Voxels(w, d, -7, 6);
        int c = w / 2, l0 = c - 3, l1 = c + 2; // 경사로 차로 6칸
        int top = d - 7;                     // 경사로 첫 칸 (j = top 에서 반 칸 내려감)
        int hall1 = top - 10;                // 지하 주차장 남쪽 끝 줄 (경사로 바닥과 이어짐)
        Block wall = Block.of("light_gray_concrete", 0x7D7D73);
        Block floor = Block.of("gray_concrete", 0x36393D);
        // 땅 위: 앞마당 아스팔트, 나머지는 잔디
        v.fill(0, -1, 0, w - 1, -1, d - 1, GRASS);
        v.fill(0, -1, top + 1, w - 1, -1, d - 1, ASPHALT);
        // 앞마당 차 자리 (경사로 양옆, 차 앞은 정면 쪽)
        for (int side = 0; side < 2; side++) {
            int a0 = side == 0 ? 1 : w - 4;
            if ((side == 0 && a0 + 2 < l0 - 1) || (side == 1 && a0 > l1 + 1)) {
                v.fill(a0, -1, d - 6, a0, -1, d - 2, LINE);
                v.fill(a0 + 3, -1, d - 6, a0 + 3, -1, d - 2, LINE);
                v.fill(a0, -1, d - 6, a0 + 3, -1, d - 6, LINE);
                v.carSpot(a0 + 2.0, 0, d - 3.5, 0, 1);
            }
        }
        // 지하 주차장 (j 0..hall1): 벽, 바닥, 천장(y -2), 그 위 잔디
        v.fill(0, -7, 0, w - 1, -7, hall1, wall);
        v.walls(0, -6, 0, w - 1, -2, hall1 + 1, wall);
        v.fill(1, -6, 1, w - 2, -6, hall1, floor);
        v.fill(1, -5, 1, w - 2, -3, hall1, AIR);
        v.fill(0, -2, 0, w - 1, -2, hall1 + 1, SMOOTH_STONE);
        // 경사로 (지붕 덮임): 양옆 옹벽, 반 칸씩 내려감
        for (int n = 0; n < 10; n++) {
            int j = top - n;
            boolean half = n % 2 == 0;
            int blockY = half ? -1 - n / 2 : -1 - (n + 1) / 2;
            v.fill(l0, -7, j, l1, blockY - 1, j, wall);
            v.fill(l0, blockY, j, l1, blockY, j, half ? Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E) : floor);
            v.fill(l0, blockY + 1, j, l1, 2, j, AIR);
            v.fill(l0 - 1, -7, j, l0 - 1, 2, j, wall);
            v.fill(l1 + 1, -7, j, l1 + 1, 2, j, wall);
        }
        // 경사로가 지하 주차장으로 들어가는 곳 (hall1+1 줄은 트임)
        v.fill(l0, -5, hall1 + 1, l1, -3, hall1 + 1, AIR);
        v.fill(l0, -6, hall1 + 1, l1, -6, hall1 + 1, floor);
        // 지붕 (경사로 위 y 3), 정면 보, 높이 제한 봉, 표지판
        v.fill(l0 - 2, 3, hall1 + 1, l1 + 2, 3, top + 1, Block.of("light_gray_concrete", 0x7D7D73));
        v.fill(l0 - 2, 4, hall1 + 1, l1 + 2, 4, top + 1, Kit.SLAB);
        v.fill(l0 - 1, 0, top + 1, l0 - 1, 2, top + 1, wall);
        v.fill(l1 + 1, 0, top + 1, l1 + 1, 2, top + 1, wall);
        v.fill(l0 - 2, -1, top + 1, l1 + 2, -1, top + 1, ASPHALT);
        v.fill(l0, 2, top + 2, l1, 2, top + 2, Block.of("iron_bars", 0x888888));
        v.set(l0 - 1, 2, top + 2, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
        v.set(c, 3, top + 2, Blocks.wallSign("birch", "south", "black", false, "", "지하주차장", "높이제한 2.3m", ""));
        v.set(c - 1, 3, top + 2, Blocks.wallSign("birch", "south", "black", false, "", "입구", "", ""));
        for (int j = hall1 + 2; j <= top; j += 3) {
            v.set(c, 2, j, CEIL_LIGHT);
        }
        // 주차 칸: 서쪽 벽을 따라 (차 앞은 동쪽 통로), 동쪽 벽을 따라 (북쪽 끝은 계단 출입구)
        int aisle0 = 6, aisle1 = w - 7;
        for (int j = 1; j + 2 <= hall1; j += 3) {
            v.fill(1, -6, j, 5, -6, j, LINE);
            v.carSpot(3.5, -5, j + 2.0, 1, 0);
            if (j >= 10) {
                v.fill(w - 6, -6, j, w - 2, -6, j, LINE);
                v.carSpot(w - 3.5, -5, j + 2.0, -1, 0);
            }
        }
        // 통로 가운데 노란 화살표 줄
        for (int j = 2; j < hall1; j += 4) {
            v.set((aisle0 + aisle1) / 2, -6, j, YELLOW_CONCRETE);
        }
        // 기둥 (칸 사이, 통로 가장자리)
        for (int j = 7; j < hall1; j += 9) {
            v.fill(aisle0 - 1 + 1, -5, j, aisle0 - 1 + 1, -3, j, wall);
            v.fill(aisle1, -5, j, aisle1, -3, j, wall);
        }
        // 천장 등 (천장에 묻음)
        for (int j = 3; j < hall1; j += 4) {
            for (int i = 3; i < w - 2; i += 5) {
                v.set(i, -2, j, CEIL_LIGHT);
            }
        }
        // 사람 계단 출입구 (북동쪽 구석): 지하 서는 높이 -5 에서 땅 위 0 까지
        int sx = w - 3;
        Interior.stairCore(Frame.facing(v, sx, 7, "north"), new int[]{-5, 0}, wall, "polished_andesite", 0x848685, 1);
        v.fill(sx - 3, 3, 0, sx + 1, 3, 8, Kit.SLAB);
        v.set(sx - 1, 1, 9, Blocks.wallSign("birch", "south", "black", false, "", "지하주차장", "출입구", ""));
        v.set(sx - 4, -4, 3, Blocks.wallSign("birch", "west", "black", false, "", "B1", "", ""));
        // 잔디 위 환기구
        for (int i = 2; i + 1 < w - 6; i += 7) {
            v.fill(i, 0, 3, i + 1, 0, 3, Block.of("light_gray_concrete", 0x7D7D73));
            v.fill(i, 1, 3, i + 1, 1, 3, Block.of("iron_bars", 0x888888));
            v.fill(i, 2, 3, i + 1, 2, 3, Kit.SLAB);
        }
        v.connect();
        return v;
    }

    // ================================================================== 단지 상가

    /** 1층 가게 종류 {이름 형식, 업종} */
    private static final String[][] SHOP = {
            {"%s마트", "편의점"}, {"%s부동산", "부동산"}, {"%s세탁소", "세탁"}, {"헤어%s", "미용실"}, {"%s치킨", "치킨"},
            {"%s약국", "약국"}, {"%s공인중개사", "부동산"}, {"%s반찬", "반찬"}, {"%s베이커리", "빵집"},
    };
    private static final String[][] UPPER = {
            {"%s수학학원", "학원"}, {"%s영어학원", "학원"}, {"%s태권도", "태권도"}, {"%s소아과", "의원"}, {"%s피아노", "학원"},
            {"%s독서실", "독서실"}, {"%s치과", "의원"},
    };

    /**
     * 단지 상가 2층 (정면 = 단지 길 또는 큰길). w 20..48, d 12..18.
     * 1층 가게 칸마다 유리 앞면·문·간판, 2층은 뒤쪽 복도와 앞쪽 학원·의원. 서쪽 끝 계단 입구(정면 문 → 2칸 통로 → 뒤쪽 계단, 옥상까지).
     */
    static Voxels shops(int w, int d, Random r) {
        w = Math.max(20, w);
        d = Math.max(12, d);
        int[] levels = {0, 4, 8};
        Voxels v = new Voxels(w, d, -1, 12);
        Block wall = r.nextBoolean() ? Block.of("white_terracotta", 0xD1B2A1) : Block.of("light_gray_concrete", 0x7D7D73);
        Block base = POLISHED_GRANITE, band = Block.of("smooth_stone", 0x9E9E9E);
        int jf = d - 3, east = w - 1;
        v.fill(0, -1, 0, east, -1, d - 1, WALK);
        for (int k = 0; k <= 2; k++) {
            v.fill(0, levels[k] - 1, 0, east, levels[k] - 1, jf, k == 0 ? POLISHED_ANDESITE : k == 2 ? SMOOTH_STONE : band);
        }
        v.walls(0, 0, 0, east, 7, jf, wall);
        v.walls(0, 0, 0, east, 0, jf, base);
        v.walls(0, 3, 0, east, 3, jf, band);
        // 계단 (서쪽 뒤 구석, 줄 너비 1): 출입구는 북쪽 복도(j 1..2) 쪽
        Interior.stairCore(new Frame(v, 1, 4, 0), levels, wall, "polished_andesite", 0x848685, 1);
        int stairE = 4;
        // 1층 통로 (i 5..6, 정면 문에서 뒤쪽 복도까지), 가게는 i 8 부터
        int p0 = stairE + 1, p1 = stairE + 2, shop0 = p1 + 2;
        v.fill(p1 + 1, 0, 1, p1 + 1, 2, jf - 1, wall);
        v.fill(1, 0, 1, p1, 2, 2, AIR);
        v.fill(p0, 0, 3, p1, 2, jf - 1, AIR);
        v.fill(1, -1, 1, p1, -1, 2, POLISHED_DIORITE);
        v.fill(p0, -1, 3, p1, -1, jf - 1, POLISHED_DIORITE);
        Kit.doubleDoor(Frame.of(v), p0, 0, jf, "birch", "south");
        v.set(p0, 2, jf - 2, CEIL_LIGHT);
        v.set(p1, 2, 1, CEIL_LIGHT);
        v.set(2, 2, jf + 1, Blocks.wallSign("birch", "south", "black", false, "", "2층", "학원·병원", ""));
        // 1층 가게 칸
        int shops = Math.max(1, (east - shop0) / 8);
        int bay = (east - shop0) / shops;
        for (int k = 0; k < shops; k++) {
            int a0 = shop0 + k * bay, a1 = k == shops - 1 ? east - 1 : a0 + bay - 2;
            if (k > 0) {
                v.fill(a0 - 1, 0, 1, a0 - 1, 2, jf - 1, wall);
            }
            String[] s = SHOP[r.nextInt(SHOP.length)];
            String name = s[0].formatted(KoreanNames.prefix(r));
            // 유리 앞면과 문
            v.fill(a0, 0, jf, a1, 2, jf, GLASS_PANE);
            v.set(a0, 0, jf, base);
            int door = a0 + 1;
            v.set(door, 0, jf, Blocks.door("birch", "south", false));
            v.set(door, 1, jf, Blocks.door("birch", "south", true));
            // 간판 (띠 위)
            Object[] board = ShopHouse.BOARDS[r.nextInt(ShopHouse.BOARDS.length)];
            v.fill(a0, 3, jf + 1, a1, 3, jf + 1, (Block) board[0]);
            v.set((a0 + a1) / 2, 3, jf + 2, Blocks.wallSign((String) board[1], "south", (String) board[2], true, "", name, s[1], ""));
            shopInterior(v, r, a0, 1, a1, jf - 1, s[1]);
        }
        // 2층: 뒤쪽 복도 (j 1..2), 앞쪽 방들
        int cj = 3;
        v.fill(p0, 4, cj, east - 1, 6, cj, Interior.INNER_WALL);
        v.fill(1, 4, 1, east - 1, 6, cj - 1, AIR);
        v.fill(1, 3, 1, east - 1, 3, cj - 1, POLISHED_DIORITE);
        for (int i = 3; i < east; i += 6) {
            v.set(i, 6, 2, CEIL_LIGHT);
        }
        int rooms = Math.max(1, (east - p0) / 10);
        int rw = (east - p0) / rooms;
        for (int k = 0; k < rooms; k++) {
            int a0 = p0 + k * rw, a1 = k == rooms - 1 ? east - 1 : a0 + rw - 2;
            if (k > 0) {
                v.fill(a0 - 1, 4, cj, a0 - 1, 6, jf - 1, Interior.INNER_WALL);
            }
            Interior.door(Frame.of(v), a0 + 1, 4, cj, "pale_oak", "north");
            String[] s = UPPER[r.nextInt(UPPER.length)];
            String name = s[0].formatted(KoreanNames.prefix(r));
            // 창 (2층 앞면)과 창 간판
            for (int i = a0; i <= a1; i++) {
                v.set(i, 5, jf, GLASS_PANE);
                v.set(i, 6, jf, i % 3 == 0 ? wall : GLASS_PANE);
            }
            v.set((a0 + a1) / 2, 7, jf + 1, Blocks.wallSign("birch", "south", "blue", false, "", name, "", ""));
            v.set(a0 + 2, 5, cj - 1, Blocks.wallSign("birch", "north", "black", false, "", name));
            upperInterior(v, r, a0, cj + 1, a1, jf - 1, s[1]);
        }
        // 옥상 난간과 실외기
        v.walls(0, 8, 0, east, 8, jf, band);
        for (int i = p1 + 3; i < east - 2; i += 5) {
            v.set(i, 8, 2, Block.of("smooth_quartz", 0xECE6DF));
            v.set(i + 1, 8, 2, Block.of("iron_bars", 0x888888));
        }
        // 앞 보도: 자전거 거치대
        for (int i = shop0 + 1; i < east; i += 9) {
            v.set(i, 0, d - 1, Block.of("iron_bars", 0x888888));
        }
        v.connect();
        return v;
    }

    private static void shopInterior(Voxels v, Random r, int a0, int b0, int a1, int b1, String type) {
        Frame f = Frame.of(v);
        int mid = (a0 + a1) / 2;
        switch (type) {
            case "편의점", "반찬", "빵집" -> {
                for (int b = b0 + 1; b <= b1 - 3; b += 3) {
                    for (int a = a0 + 2; a <= a1 - 1; a++) {
                        v.set(a, 0, b, Furniture.BOOKSHELF);
                        v.set(a, 1, b, Block.of("barrel[facing=up,open=false]", 0x86643B));
                    }
                }
                v.fill(a0, 0, b0, a1, 1, b0, Block.of("white_stained_glass", 0xF0F0F0)); // 냉장 진열대
                v.set(a1, 0, b1 - 1, Furniture.COUNTER);
                v.set(a1, 1, b1 - 1, Block.of("black_concrete", 0x080A0F));
            }
            case "부동산" -> {
                Furniture.desk(f, a0 + 2, 0, b0 + 2, "north");
                Furniture.desk(f, a0 + 4, 0, b0 + 2, "north");
                Furniture.table(f, mid, 0, b1 - 2, 2, 1, "dark_oak");
                v.fill(a0, 1, b0 - 1 + 1, a1, 2, b0 - 1 + 1, Block.of("white_concrete", 0xCFD5D6)); // 매물 지도판
            }
            case "세탁" -> {
                v.fill(a0 + 1, 0, b1 - 1, a1 - 1, 0, b1 - 1, Furniture.COUNTER);
                for (int a = a0; a <= a1; a++) {
                    v.set(a, 2, b0 + 1, Block.of("iron_chain[axis=x,waterlogged=false]", 0x505050));
                    v.set(a, 1, b0 + 1, Blocks.wool(Furniture.SHEETS[r.nextInt(Furniture.SHEETS.length)]));
                }
                v.set(a0, 0, b0, IRON_BLOCK);
            }
            case "미용실" -> {
                for (int a = a0 + 1; a <= a1 - 1; a += 2) {
                    Furniture.chair(f, a, 0, b0 + 1, "north", "dark_oak");
                    v.set(a, 1, b0, Block.of("light_blue_stained_glass", 0x6699D8));
                }
                Kit.seats(f, a0 + 1, a0 + 3, 0, b1 - 1, "east", "birch");
            }
            default -> {
                for (int b = b0 + 1; b <= b1 - 2; b += 3) {
                    for (int a = a0 + 1; a + 1 <= a1 - 1; a += 4) {
                        Furniture.table(f, a, 0, b, 2, 1, "spruce");
                    }
                }
                v.set(a1, 0, b0, Furniture.COUNTER);
            }
        }
        Interior.lights(f, a0, b0, a1, b1, 0, 4, 4, CEIL_LIGHT);
    }

    private static void upperInterior(Voxels v, Random r, int a0, int b0, int a1, int b1, String type) {
        Frame f = Frame.of(v);
        switch (type) {
            case "학원", "독서실" -> {
                v.fill(a0 - 0, 5, b0, a0, 6, b1, Block.of("green_concrete", 0x495B24)); // 칠판 (서쪽 벽 안쪽)
                for (int b = b0 + 1; b <= b1 - 1; b += 2) {
                    for (int a = a0 + 2; a <= a1 - 1; a += 2) {
                        v.set(a, 4, b, Furniture.DESK_TOP);
                        Furniture.chair(f, a + 1, 4, b, "east", "oak");
                    }
                }
            }
            case "태권도" -> {
                v.fill(a0, 3, b0, a1, 3, b1, Block.of("blue_concrete", 0x2C2E8F));
                v.fill(a0 + 1, 3, b0 + 1, a1 - 1, 3, b1 - 1, Block.of("red_concrete", 0x8E2121));
                v.fill(a0, 5, b0, a1, 6, b0, Block.of("light_blue_stained_glass", 0x6699D8)); // 거울
            }
            default -> {
                Kit.seats(f, a0, a1 - 2, 4, b0, "south", "birch");
                v.fill(a1 - 1, 4, b1 - 1, a1, 4, b1 - 1, Furniture.COUNTER);
                Furniture.bed(f, r, a1, 4, b0 + 1, "south");
            }
        }
        Interior.lights(f, a0, b0, a1, b1, 4, 4, 4, CEIL_LIGHT);
    }

    // ================================================================== 관리동

    /** 관리동 2층: 1층 관리사무소·경로당, 2층 어린이집. w 18..30, d 12..16 */
    static Voxels management(int w, int d, String complex, Random r) {
        w = Math.max(18, w);
        d = Math.max(12, d);
        int[] levels = {0, 4, 8};
        Voxels v = new Voxels(w, d, -1, 12);
        Block wall = Block.of("bricks", 0x966153), band = SMOOTH_STONE;
        int jf = d - 3, east = w - 1;
        v.fill(0, -1, 0, east, -1, d - 1, WALK);
        for (int k = 0; k <= 2; k++) {
            v.fill(0, levels[k] - 1, 0, east, levels[k] - 1, jf, k == 0 ? POLISHED_ANDESITE : band);
        }
        v.walls(0, 0, 0, east, 7, jf, wall);
        v.walls(0, 3, 0, east, 3, jf, band);
        // 가운데 홀 (i sc-2..sc+2): 북쪽 끝 계단 (줄 너비 1, 출입구는 홀 쪽), 남쪽 현관
        int sc = w / 2;
        Interior.stairCore(Frame.facing(v, sc + 1, 5, "north"), levels, wall, "polished_andesite", 0x848685, 1);
        for (int k = 0; k < 2; k++) {
            v.fill(sc - 2, levels[k], 7, sc + 2, levels[k] + 2, jf - 1, AIR);
            v.set(sc, levels[k] + 2, (7 + jf) / 2, CEIL_LIGHT);
        }
        v.fill(sc - 2, 0, jf, sc + 2, 2, jf, wall);
        Kit.doubleDoor(Frame.of(v), sc, 0, jf, "spruce", "south");
        v.fill(sc - 2, 3, jf + 1, sc + 3, 3, jf + 2, Kit.SLAB);
        // 1층 서쪽: 관리사무소 (민원 창구, 책상), 동쪽: 경로당 (좌식 방)
        int w0 = sc - 3, e0 = sc + 3;
        v.fill(w0, 0, 1, w0, 2, jf - 1, Interior.INNER_WALL);
        v.fill(e0, 0, 1, e0, 2, jf - 1, Interior.INNER_WALL);
        Interior.door(Frame.of(v), w0, 0, jf - 2, "pale_oak", "east");
        Interior.door(Frame.of(v), e0, 0, jf - 2, "pale_oak", "west");
        Frame f = Frame.of(v);
        for (int a = 2; a < w0 - 1; a += 3) {
            Furniture.desk(f, a, 0, 2, "south");
        }
        v.fill(1, 0, jf - 3, w0 - 2, 0, jf - 3, Furniture.COUNTER);
        v.fill(1, 1, 1, w0 - 1, 2, 1, Block.of("white_concrete", 0xCFD5D6));
        // 경로당: 장판(노란 바닥), 낮은 상, TV
        v.fill(e0 + 1, -1, 1, east - 1, -1, jf - 1, Block.of("yellow_terracotta", 0xBA8523));
        for (int a = e0 + 2; a + 1 < east - 1; a += 4) {
            v.fill(a, 0, jf / 2, a + 1, 0, jf / 2, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        }
        v.set(east - 1, 1, 2, Furniture.TV);
        v.set(east - 2, 1, 2, Furniture.TV);
        Interior.lights(f, 1, 1, east - 1, jf - 1, 0, 4, 4, CEIL_LIGHT);
        // 창
        for (int i = 1; i < east; i++) {
            if (Math.abs(i - sc) > 2 && i != w0 && i != e0 && i % 3 != 0) {
                v.set(i, 1, jf, GLASS_PANE);
                v.set(i, 5, jf, GLASS_PANE);
                v.set(i, 6, jf, GLASS_PANE);
                v.set(i, 1, 0, GLASS_PANE);
                v.set(i, 5, 0, GLASS_PANE);
            }
        }
        // 2층 어린이집: 알록달록 매트, 낮은 탁자와 의자, 장난감 선반
        v.fill(1, 3, 1, sc - 4, 3, jf - 1, Block.of("light_blue_concrete", 0x2389C7));
        v.fill(sc + 4, 3, 1, east - 1, 3, jf - 1, Block.of("light_blue_concrete", 0x2389C7));
        v.fill(sc - 2, 3, 7, sc + 2, 3, jf - 1, POLISHED_DIORITE);
        v.fill(sc - 3, 4, 1, sc - 3, 6, jf - 1, Interior.INNER_WALL);
        v.fill(sc + 3, 4, 1, sc + 3, 6, jf - 1, Interior.INNER_WALL);
        Interior.door(Frame.of(v), sc - 3, 4, jf - 2, "pale_oak", "east");
        Interior.door(Frame.of(v), sc + 3, 4, jf - 2, "pale_oak", "west");
        String[] mats = {"yellow", "lime", "pink", "light_blue", "orange"};
        for (int a = 2; a < east - 1; a += 3) {
            if (Math.abs(a - sc) <= 3) {
                continue;
            }
            v.set(a, 3, jf / 2 + 1, Blocks.wool(mats[r.nextInt(mats.length)]));
            v.set(a, 4, jf / 2, Block.of("birch_slab[type=bottom,waterlogged=false]", 0xC0AF79));
            v.set(a + 1, 4, jf / 2, Block.of("birch_trapdoor[facing=west,half=bottom,open=false,powered=false,waterlogged=false]", 0xC0AF79));
        }
        Interior.lights(f, 1, 1, east - 1, jf - 1, 4, 4, 4, CEIL_LIGHT);
        // 옥상 난간
        v.walls(0, 8, 0, east, 8, jf, band);
        // 간판
        v.set(3, 2, jf + 1, Blocks.wallSign("birch", "south", "black", false, "", "관리사무소"));
        v.set(east - 3, 2, jf + 1, Blocks.wallSign("birch", "south", "black", false, "", "경로당"));
        v.set(sc + 3, 7, jf + 1, Blocks.wallSign("birch", "south", "black", false, "", "어린이집"));
        v.set(sc - 2, 7, jf + 1, Blocks.wallSign("dark_oak", "south", "white", false, nameLines(complex)));
        // 앞 화단
        for (int i = 0; i <= east; i++) {
            if (Math.abs(i - sc) > 2) {
                v.set(i, -1, d - 1, GRASS);
                v.set(i, 0, d - 1, Kit.BOX_HEDGE);
            }
        }
        v.connect();
        return v;
    }

    // ================================================================== 커뮤니티센터

    /** 커뮤니티센터 2층 (유리 앞면): 1층 카페·피트니스, 2층 독서실·골프연습장. w 24..40, d 16..22 */
    static Voxels community(int w, int d, String complex, Block stone, Random r) {
        w = Math.max(24, w);
        d = Math.max(16, d);
        int[] levels = {0, 5, 10};
        Voxels v = new Voxels(w, d, -1, 14);
        Block glass = Block.of("light_blue_stained_glass", 0x6699D8), frame = Block.of("gray_concrete", 0x36393D);
        int jf = d - 3, east = w - 1;
        v.fill(0, -1, 0, east, -1, d - 1, WALK);
        for (int k = 0; k <= 2; k++) {
            v.fill(0, levels[k] - 1, 0, east, levels[k] - 1, jf, k == 0 ? POLISHED_DIORITE : SMOOTH_STONE);
        }
        // 뒤·옆 돌벽, 앞 유리 커튼월 (세로 살)
        v.walls(0, 0, 0, east, 9, jf, stone);
        for (int i = 1; i < east; i++) {
            for (int y = 0; y <= 8; y++) {
                v.set(i, y, jf, i % 4 == 0 || y == 4 ? frame : glass);
            }
        }
        // 옆벽 띠창
        for (int j = 2; j < jf - 1; j++) {
            v.set(0, 2, j, glass);
            v.set(east, 2, j, glass);
            v.set(0, 7, j, glass);
            v.set(east, 7, j, glass);
        }
        // 동쪽 끝 계단 (옥상까지)
        int sx = east - 2;
        Interior.stairCore(Frame.facing(v, sx, jf - 3, "north"), levels, stone, "polished_andesite", 0x848685);
        int stairW = sx - 6;
        // 정문 (가운데 유리문)
        int door = w / 2;
        v.set(door, 0, jf, AIR);
        v.set(door + 1, 0, jf, AIR);
        v.set(door, 1, jf, AIR);
        v.set(door + 1, 1, jf, AIR);
        Kit.doubleDoor(Frame.of(v), door, 0, jf, "birch", "south");
        v.fill(door - 3, 5, jf + 1, door + 4, 5, jf + 2, Kit.SLAB);
        v.set(door - 2, 3, jf + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "커뮤니티센터", complexShort(complex), ""));
        Frame f = Frame.of(v);
        // 1층: 서쪽 카페 (계산대·탁자), 동쪽 피트니스 (러닝머신·운동기구), 가운데 안내 데스크
        int cafe1 = door - 2, gym0 = door + 3;
        v.fill(cafe1, 0, 1, cafe1, 3, jf - 3, Block.of("white_stained_glass_pane", 0xF0F0F0));
        v.fill(gym0, 0, 1, gym0, 3, jf - 3, Block.of("white_stained_glass_pane", 0xF0F0F0));
        v.fill(2, 0, 1, cafe1 - 2, 0, 1, Furniture.COUNTER);
        v.set(3, 1, 1, Block.of("smoker[facing=south,lit=false]", 0x555451)); // 커피 기계
        for (int b = 4; b <= jf - 3; b += 3) {
            for (int a = 2; a + 1 <= cafe1 - 2; a += 4) {
                Furniture.table(f, a, 0, b, 2, 1, "birch");
            }
        }
        for (int a = gym0 + 2; a < stairW; a += 2) {
            v.set(a, 0, 2, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
            v.set(a, 0, 3, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
            v.set(a, 1, 1, Block.of("iron_bars", 0x888888));
        }
        for (int a = gym0 + 2; a < stairW; a += 3) {
            v.set(a, 0, jf - 4, Block.of("anvil[facing=east]", 0x444444));
        }
        v.fill(stairW - 1, 1, 6, stairW - 1, 3, jf - 5, Block.of("light_blue_stained_glass", 0x6699D8)); // 거울
        v.fill(door - 1, 0, jf - 4, door + 2, 0, jf - 4, Furniture.COUNTER);
        Interior.lights(f, 1, 1, east - 1, jf - 1, 0, 5, 5, CEIL_LIGHT);
        // 2층: 계단 앞 줄(정면 쪽)로 골프연습장(초록 매트, 그물)을 지나 서쪽 독서실(칸막이 책상)
        int golf0 = w / 2 + 1;
        v.fill(golf0, 5, 1, golf0, 8, jf - 1, Interior.INNER_WALL);
        Interior.door(Frame.of(v), golf0, 5, jf - 4, "pale_oak", "east");
        for (int b = 2; b <= jf - 2; b += 2) {
            for (int a = 2; a < golf0 - 1; a += 3) {
                v.set(a, 5, b, Furniture.DESK_TOP);
                v.set(a, 6, b, Block.of("white_stained_glass_pane", 0xF0F0F0));
                Furniture.chair(f, a, 5, b + 1, "south", "birch");
            }
        }
        v.fill(golf0 + 1, 4, 1, stairW, 4, jf - 1, Block.of("green_concrete", 0x495B24));
        v.fill(golf0 + 1, 5, 1, stairW, 8, 1, Block.of("iron_bars", 0x888888));
        for (int a = golf0 + 2; a < stairW; a += 3) {
            v.set(a, 4, jf - 4, Block.of("lime_concrete", 0x5EA918));
        }
        Interior.lights(f, 1, 1, east - 1, jf - 1, 5, 5, 5, CEIL_LIGHT);
        // 서쪽 2층 독서실 문: 계단에서 복도로 (골프장 칸을 지나) → 골프장 서쪽 벽 문
        Interior.door(Frame.of(v), golf0, 5, 2, "pale_oak", "west");
        // 옥상: 난간, 옥상 정원 화단
        v.walls(0, 10, 0, east, 10, jf, frame);
        for (int i = 2; i < stairW - 2; i += 6) {
            Kit.flowerBed(v, i, 1, i + 3, 3, 9, r);
        }
        // 앞 데크와 화분
        v.fill(0, -1, jf + 1, east, -1, d - 1, Block.of("spruce_planks", 0x725430));
        for (int i = 1; i < east; i += 5) {
            if (Math.abs(i - door) > 3) {
                v.set(i, 0, d - 1, Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
            }
        }
        v.connect();
        return v;
    }

    static String complexShort(String complex) {
        String n = complex == null ? "" : complex;
        return n.length() > 9 ? n.substring(0, 9) : n;
    }

    // ================================================================== 놀이터

    /** 놀이터 (탄성 포장, 놀이대·미끄럼틀·오르는 망, 그네, 모래밭, 벤치, 둘레 나무). w, d ≥ 12 */
    static Voxels playground(int w, int d, Random r) {
        w = Math.max(12, w);
        d = Math.max(12, d);
        Voxels v = new Voxels(w, d, -1, 9);
        Block post = Block.of("light_blue_terracotta", 0x716C89);
        Block roof = Block.of("red_terracotta", 0x8F3D2E), slide = Block.of("yellow_terracotta", 0xBA8523);
        // 바닥: 둘레 보도, 안쪽 초록 탄성 포장, 놀이대 둘레 붉은 포장
        v.fill(0, -1, 0, w - 1, -1, d - 1, WALK);
        v.fill(1, -1, 1, w - 2, -1, d - 2, RUBBER);
        int cx = w / 2, cz = d / 2 - 1;
        v.fill(cx - 4, -1, cz - 3, cx + 4, -1, cz + 3, RUBBER2);
        // 놀이대: 기둥 넷, 높은 판(y 2), 지붕, 계단, 미끄럼틀, 오르는 망
        int a0 = cx - 2, a1 = cx + 1, b0 = cz - 1, b1 = cz + 1;
        for (int a : new int[]{a0, a1}) {
            for (int b : new int[]{b0, b1}) {
                v.fill(a, 0, b, a, 4, b, post);
            }
        }
        v.fill(a0, 1, b0, a1, 1, b1, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        v.fill(a0, 5, b0, a1, 5, b1, roof);
        v.fill(a0 + 1, 6, b0, a1 - 1, 6, b1, roof);
        for (int a = a0; a <= a1; a++) {
            v.set(a, 2, b0, Block.of("iron_bars", 0x888888));
        }
        // 오르는 계단 (서쪽): 반 칸씩
        v.set(a0 - 1, 0, cz, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        v.set(a0 - 2, 0, cz, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        v.set(a0 - 1, 0, cz, Block.of("spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0x725430));
        // 미끄럼틀 (동쪽으로 내려감, 양옆 노란 턱)
        v.set(a1 + 1, 1, cz, Block.of("cut_sandstone_slab[type=bottom,waterlogged=false]", 0xD9CD9F));
        v.set(a1 + 2, 0, cz, Block.of("cut_sandstone_slab[type=top,waterlogged=false]", 0xD9CD9F));
        v.set(a1 + 3, 0, cz, Block.of("cut_sandstone_slab[type=bottom,waterlogged=false]", 0xD9CD9F));
        for (int t = 1; t <= 3; t++) {
            v.set(a1 + t, t == 1 ? 1 : 0, cz - 1, slide);
            v.set(a1 + t, t == 1 ? 1 : 0, cz + 1, slide);
        }
        // 오르는 망 (남쪽): 쇠사슬
        for (int a = a0; a <= a1; a++) {
            v.set(a, 0, b1 + 1, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
        }
        // 그네 (북서쪽): 틀 + 사슬 + 앉는 판
        if (w >= 14 && d >= 12) {
            int sj = 2, si0 = 2, si1 = Math.min(w - 3, si0 + 5);
            v.fill(si0, 0, sj, si0, 3, sj, post);
            v.fill(si1, 0, sj, si1, 3, sj, post);
            v.fill(si0, 3, sj, si1, 3, sj, Block.of("iron_bars", 0x888888));
            for (int i = si0 + 2; i < si1; i += 2) {
                v.set(i, 2, sj, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
                v.set(i, 1, sj, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
                v.set(i, 0, sj, Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
            }
        }
        // 모래밭 (남동쪽): 나무 테두리
        if (w >= 14 && d >= 14) {
            int s0 = w - 7, s1 = w - 3, t0 = d - 6, t1 = d - 3;
            v.fill(s0, -1, t0, s1, -1, t1, SAND);
            v.walls(s0 - 1, 0, t0 - 1, s1 + 1, 0, t1 + 1, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        }
        // 흔들 놀이기구 (스프링) 둘
        v.set(3, 0, d - 4, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC9714E));
        v.set(3, 1, d - 4, Block.of("lime_terracotta", 0x677534));
        // 벤치 (둘레)
        Kit.bench(v, 1, 0, d / 2, 3, false, "east");
        Kit.bench(v, w - 2, 0, d / 2 - 1, 3, false, "west");
        Kit.parkLamp(v, 1, 0, 1);
        Kit.parkLamp(v, w - 2, 0, d - 2);
        v.connect();
        return v;
    }

    // ================================================================== 단지 공원

    /**
     * 단지 공원 조각 (잔디, 둘레 산책로, 나무, 벤치, 보행등. 넓으면 정자나 운동기구).
     *
     * @param old 옛 단지 (큰 느티나무·플라타너스 위주)
     */
    static Voxels park(int w, int d, boolean old, Random r) {
        Voxels v = new Voxels(w, d, -1, 14);
        v.fill(0, -1, 0, w - 1, -1, d - 1, GRASS);
        if (w < 5 || d < 5) {
            // 좁은 띠: 회양목 생울타리와 키 작은 나무
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    if ((i + j) % 2 == 0 && (i == 0 || j == 0 || i == w - 1 || j == d - 1)) {
                        v.set(i, 0, j, Kit.BOX_HEDGE);
                    }
                }
            }
            return v;
        }
        // 산책로: 가운데 십자 또는 고리
        Block path = old ? Block.of("stone_bricks", 0x7A7979) : Block.of("bricks", 0x966153);
        boolean ring = w >= 14 && d >= 14;
        if (ring) {
            v.walls(2, -1, 2, w - 3, -1, d - 3, path);
            v.walls(3, -1, 3, w - 4, -1, d - 4, path);
            v.fill(w / 2, -1, 0, w / 2 + 1, -1, 2, path);
            v.fill(0, -1, d / 2, 2, -1, d / 2 + 1, path);
        } else if (w >= d) {
            v.fill(0, -1, d / 2, w - 1, -1, d / 2, path);
            if (d >= 7) {
                v.fill(0, -1, d / 2 + 1, w - 1, -1, d / 2 + 1, path);
            }
        } else {
            v.fill(w / 2, -1, 0, w / 2, -1, d - 1, path);
            if (w >= 7) {
                v.fill(w / 2 + 1, -1, 0, w / 2 + 1, -1, d - 1, path);
            }
        }
        // 가운데 쉼터: 정자 또는 운동기구
        if (ring && w >= 18 && d >= 18 && r.nextBoolean()) {
            pavilion(v, w / 2 - 3, d / 2 - 3);
        } else if (ring && w >= 16 && d >= 16) {
            exercise(v, w / 2 - 3, d / 2 - 2, r);
        } else if (ring) {
            Kit.flowerBed(v, w / 2 - 2, d / 2 - 2, w / 2 + 2, d / 2 + 2, -1, r);
        }
        // 나무: 길·쉼터에서 떨어진 잔디에, 간격 7~9
        int step = old ? 9 : 7;
        for (int j = 1 + r.nextInt(2); j < d - 1; j += step) {
            for (int i = 1 + r.nextInt(3); i < w - 1; i += step) {
                if (clearAround(v, i, j, 1)) {
                    if (old) {
                        Kit.zelkova(v, i, j, -1, r);
                    } else {
                        Kit.tree(v, i, j, -1, r);
                    }
                }
            }
        }
        // 벤치와 보행등 (길가)
        int benches = 0;
        for (int j = 1; j < d - 1 && benches < 4; j += 5) {
            for (int i = 1; i < w - 1 && benches < 4; i += 6) {
                if (v.get(i, -1, j) == GRASS && v.get(i, 0, j) == null && isPath(v, i + 1, j)) {
                    Kit.bench(v, i, 0, j, 1, true, "east");
                    benches++;
                }
            }
        }
        for (int j = 2; j < d - 2; j += 9) {
            for (int i = 2; i < w - 2; i += 9) {
                if (v.get(i, -1, j) == GRASS && v.get(i, 0, j) == null && v.get(i, 3, j) == null) {
                    Kit.parkLamp(v, i, 0, j);
                }
            }
        }
        // 화단 가장자리 회양목 몇 줄
        for (int i = 0; i < w; i += 1) {
            if (v.get(i, -1, 0) == GRASS && v.get(i, 0, 0) == null && i % 4 != 0) {
                v.set(i, 0, 0, Kit.BOX_HEDGE);
            }
        }
        v.connect();
        return v;
    }

    private static boolean isPath(Voxels v, int i, int j) {
        Block b = v.get(i, -1, j);
        return b != null && b != GRASS;
    }

    private static boolean clearAround(Voxels v, int i, int j, int r) {
        for (int dj = -r; dj <= r; dj++) {
            for (int di = -r; di <= r; di++) {
                Block g = v.get(i + di, -1, j + dj);
                if (g == null || g != GRASS || v.get(i + di, 0, j + dj) != null) {
                    return false;
                }
            }
        }
        // 정자·운동기구 위로 가지가 덮이지 않게
        for (int dj = -4; dj <= 4; dj++) {
            for (int di = -4; di <= 4; di++) {
                Block b = v.get(i + di, 0, j + dj);
                if (b != null && b != Kit.BOX_HEDGE && !b.id().contains("lantern") && !b.id().contains("_wall")) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 정자 (7×7): 돌 바닥, 나무 기둥 넷, 기와 지붕, 둘레 마루 */
    private static void pavilion(Voxels v, int i0, int j0) {
        Block pillar = Block.of("stripped_spruce_log[axis=y]", 0x6F5532);
        v.fill(i0, -1, j0, i0 + 6, -1, j0 + 6, Block.of("stone_bricks", 0x7A7979));
        v.fill(i0 + 1, 0, j0 + 1, i0 + 5, 0, j0 + 5, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        for (int a : new int[]{i0 + 1, i0 + 5}) {
            for (int b : new int[]{j0 + 1, j0 + 5}) {
                v.fill(a, 0, b, a, 3, b, pillar);
            }
        }
        Block tile = Block.of("deepslate_tiles", 0x363637);
        v.fill(i0, 4, j0, i0 + 6, 4, j0 + 6, Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637));
        v.fill(i0 + 1, 4, j0 + 1, i0 + 5, 4, j0 + 5, tile);
        v.fill(i0 + 2, 5, j0 + 2, i0 + 4, 5, j0 + 4, Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637));
        v.set(i0 + 3, 3, j0 + 3, LANTERN_HANGING);
    }

    /** 야외 운동기구 (철봉, 허리 돌리기, 걷기 기구) */
    private static void exercise(Voxels v, int i0, int j0, Random r) {
        v.fill(i0, -1, j0, i0 + 6, -1, j0 + 4, Block.of("red_terracotta", 0x8F3D2E));
        Block bar = Block.of("iron_bars", 0x888888), postB = Block.of("cyan_terracotta", 0x565B5B);
        v.fill(i0 + 1, 0, j0 + 1, i0 + 1, 2, j0 + 1, postB);
        v.fill(i0 + 3, 0, j0 + 1, i0 + 3, 2, j0 + 1, postB);
        v.set(i0 + 2, 2, j0 + 1, bar);
        v.set(i0 + 5, 0, j0 + 1, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC9714E));
        v.set(i0 + 5, 1, j0 + 1, Block.of("polished_blackstone_pressure_plate[powered=false]", 0x353038));
        v.fill(i0 + 2, 0, j0 + 3, i0 + 2, 1, j0 + 3, postB);
        v.set(i0 + 2, 2, j0 + 3, Block.of("iron_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
        v.set(i0 + 4, 0, j0 + 3, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
    }

    private ComplexFacilities() {
    }
}
