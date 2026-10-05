package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 서울역 (공항철도): 서쪽 역 앞 광장(거점)을 보는 옛 역사(1925년 붉은 벽돌과 초록 구리 돔, 지금은 문화역서울284)와
 * 그 북쪽 새 유리 역사, 뒤(동쪽)로 남북으로 뻗은 승강장과 선로.
 * <ul>
 *   <li>새 역사: 1층 매표소(번호 창구), 승차권 자동발매기, 편의점·카페, 화장실, 개찰구, 승강장으로 나가는 문.
 *       2층(높이 10) 맞이방: 대기 의자, 출발 안내판, 푸드코트, 선로 위 연결 통로로 가는 개찰구.
 *       옥상 위 「서울역」 글자 간판.</li>
 *   <li>옛 역사: 붉은 벽돌과 화강석 띠, 가운데 아치 셋 현관과 시계, 팔각 받침 위 초록 구리 돔과 꼭대기 작은 탑,
 *       앞 모서리 작은 탑. 안: 돔 아래 두 층 높이 중앙홀(돌기둥), 날개 전시실, 2층 옛 그릴(식당), 뒤 2층 회랑.</li>
 *   <li>승강장: 1번(역사 옆 승강장)과 섬식 승강장 둘, 선로 넷(자갈과 레일, 끝 막이), 승강장 안전문, 노란 점자 블록,
 *       기둥과 지붕, 매단 승강장 표지판, 의자. 섬식 승강장은 선로 위 유리 연결 통로와 계단으로 갑니다.
 *       선로는 역 구내 안에서 끝납니다 (철도망 없음).</li>
 * </ul>
 */
final class SeoulStation {
    static final int HALL_W = 54, HALL_D = 32;
    static final int[] HALL_LEVELS = {0, 6, 16};
    static final int OLD_W = 58, OLD_D = 24;
    static final int[] OLD_LEVELS = {0, 6, 11};
    /** 승강장 서는 높이 (승강장 윗면 y = 0) */
    static final int PLATFORM = 1;
    /** 연결 통로 서는 높이 (= 새 역사 2층) */
    static final int BRIDGE = 6;

    private static final Block RAIL = Block.of("rail[shape=north_south,waterlogged=false]", 0x7A6A55);
    private static final Block GRAVEL = Block.of("gravel", 0x837F7E);
    private static final Block PLAT = POLISHED_ANDESITE;
    private static final Block TACTILE = Block.of("yellow_concrete", 0xF0AF15);
    private static final Block COPPER = Block.of("waxed_oxidized_copper", 0x52A284);
    private static final Block COPPER_CUT = Block.of("waxed_oxidized_cut_copper", 0x4F9C80);

    static void plan(CityTerrain t, Polygon area, BuildMask m, Layout.Hub hub, List<Placement> out) {
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int[] blk = m.componentNear(hx, hz, 30);
        if (blk == null || blk[2] - blk[0] < 80 || blk[3] - blk[1] < 150) {
            return;
        }
        Random rnd = Plans.random(t, "서울역");
        int bx0 = hx + 13;                          // 역사 정면 (서쪽 벽)
        // 옛 역사: 거점 정면
        int oz0 = hz - OLD_W / 2, oz1 = oz0 + OLD_W - 1, ox1 = bx0 + OLD_D - 1;
        // 새 역사: 옛 역사 북쪽
        int nz1 = oz0 - 5, nz0 = nz1 - HALL_W + 1, nx1 = bx0 + HALL_D - 1;
        if (!m.rectFree(bx0, oz0, ox1, oz1) || !m.rectFree(bx0, nz0, nx1, nz1)) {
            return;
        }
        long s1 = rnd.nextLong(), s2 = rnd.nextLong();
        int bridgeZ = nz0 + 24;                     // 연결 통로 북쪽 줄 (월드 z)
        int bridgeI = bridgeZ - nz0;                // 새 역사 안에서 통로 문 위치 (i)
        out.add(Placement.rect("서울역 (새 역사)", "station", bx0, nz0, nx1, nz1, "west",
                (w, d) -> hall(w, d, bridgeI, new Random(s1))));
        out.add(Placement.rect("문화역서울284 (옛 서울역사)", "station", bx0, oz0, ox1, oz1, "west",
                (w, d) -> oldStation(w, d, new Random(s2))));
        // 승강장·선로: 역사 뒤 블록 끝까지
        int rx0 = ox1 + 1, rx1 = blk[2] + 2, rz0 = blk[1], rz1 = blk[3];
        int hallX1 = nx1, hallZ0 = nz0, hallZ1 = nz1;
        out.add(Placement.rect("서울역 승강장", "station", rx0, rz0, rx1, rz1, "south", (w, d) -> {
            Site s = new Site(t, area, rx0, rz0, rx1, rz1);
            return platforms(s, hallX1 - rx0, hallZ0 - rz0, hallZ1 - rz0, bridgeZ - rz0, rnd(s1));
        }));
        // 역 앞 광장
        int px0 = blk[0] - 3, px1 = bx0 - 1, pz0 = blk[1] - 3, pz1 = blk[3] + 3;
        out.add(Placement.rect("서울역 광장", "plaza", px0, pz0, px1, pz1, "south", (w, d) -> {
            Site s = new Site(t, area, px0, pz0, px1, pz1);
            return frontPlaza(s, hx - px0, hz - pz0);
        }));
    }

    private static Random rnd(long seed) {
        return new Random(seed ^ 0x5EED);
    }

    // ------------------------------------------------------------------ 새 역사

    /**
     * 새 역사 (정면 j = d-1, 뒤 j = 0 이 승강장 쪽). bridgeI: 2층 연결 통로 문이 시작하는 i (7칸 너비).
     */
    static Voxels hall(int w, int d, int bridgeI, Random r) {
        int[] lv = HALL_LEVELS;
        int roof = lv[2];
        Voxels v = new Voxels(w, d, -1, roof + 16);
        Frame f = Frame.of(v);
        Block glass = Block.of("glass", 0xC8DCE4);
        Block frame = WHITE_CONCRETE;
        // 바닥판
        v.fill(0, -1, 0, w - 1, -1, d - 1, POLISHED_ANDESITE);
        v.fill(0, lv[1] - 1, 0, w - 1, lv[1] - 1, d - 1, SMOOTH_STONE);
        v.fill(0, roof - 1, 0, w - 1, roof - 1, d - 1, SMOOTH_STONE);
        // 겉벽: 정면·옆은 유리 커튼월 (흰 틀), 뒤는 밝은 회색 벽
        for (int y = 0; y < roof - 1; y++) {
            for (int i = 0; i < w; i++) {
                boolean post = i % 3 == 0 || i == w - 1 || y == lv[1] - 1;
                v.set(i, y, d - 1, post ? frame : glass);
                v.set(i, y, 0, (y % 5 == 2 && i % 3 != 0 && y > 0) ? glass : Block.of("light_gray_concrete", 0x7D7D73));
            }
            for (int j = 0; j < d; j++) {
                boolean post = j % 3 == 0 || j == d - 1 || y == lv[1] - 1;
                v.set(0, y, j, post ? frame : glass);
                v.set(w - 1, y, j, post ? frame : glass);
            }
        }
        // 지붕 끝 띠와 난간
        v.walls(0, roof - 1, 0, w - 1, roof - 1, d - 1, frame);
        v.walls(0, roof, 0, w - 1, roof, d - 1, Block.of("light_gray_concrete", 0x7D7D73));
        // 정문 (가운데 6칸) + 차양
        int mid = w / 2;
        v.fill(mid - 3, 0, d - 1, mid + 2, 3, d - 1, AIR);
        // 계단 (북쪽 끝, 출입구가 남쪽(+i)을 봄)
        int sd = Interior.stairDepth(lv);
        Frame st = Frame.facing(v, sd, 10, "west");
        Interior.stairCore(st, lv, Interior.CORE_WALL, "polished_andesite", 0x848685);
        st.fill(-1, roof + 3, -1, 5, roof + 3, sd, SMOOTH_STONE);
        int si = sd + 1;   // 계단 출입구 벽 줄 (i)

        // ---- 1층
        Interior.restroom(f, 1, 1, 4, 6, 0, lv[1], "남자 화장실", 2);
        Interior.restroom(f, 7, 1, 10, 6, 0, lv[1], "여자 화장실", 8);
        // 매표소 (북쪽, 창구는 남쪽을 봄): 직원 구역 i 1..si, j 18..d-2
        int ci = si + 3, j0 = 18;
        Block wall = Interior.INNER_WALL;
        v.fill(1, 0, j0 - 1, ci - 1, lv[1] - 2, j0 - 1, wall);
        int n = 1;
        for (int j = j0; j <= d - 3; j++) {
            v.set(ci, 0, j, Furniture.COUNTER);
            v.fill(ci, 3, j, ci, lv[1] - 2, j, WHITE_CONCRETE);
            if ((j - j0) % 3 == 1) {
                v.set(ci + 1, 3, j, Blocks.wallSign("birch", "east", "black", true, "", "매표 " + n++));
                Furniture.chair(f, ci - 1, 0, j, "west", "dark_oak");
                v.set(ci, 1, j, Block.of("iron_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            } else {
                v.set(ci, 1, j, Block.of("white_stained_glass_pane", 0xF0F0F0));
            }
        }
        v.fill(ci, 0, d - 2, ci, lv[1] - 2, d - 2, wall);
        Interior.door(f, ci - 1, 0, j0 - 1, "dark_oak", "north");
        v.set(ci + 1, 4, (j0 + d - 3) / 2 + 1, Blocks.wallSign("dark_oak", "east", "white", true, "", "매표소", "Tickets"));
        // 승차권 자동발매기 (정면 유리 안쪽)
        for (int i = ci + 3; i <= ci + 8; i += 2) {
            v.set(i, 0, d - 2, Block.of("light_gray_concrete", 0x7D7D73));
            v.set(i, 1, d - 2, Block.of("dropper[facing=north,triggered=false]", 0x6E6E6E));
        }
        // 편의점·카페 (남쪽 끝, 유리 앞면은 북쪽을 봄)
        shop(v, f, r, w - 10, 3, w - 2, 15, "카페 준서", "카페");
        shop(v, f, r, w - 10, 17, w - 2, d - 3, "24시 편의점", "편의점");
        // 개찰구 (승강장 나가는 문 앞)와 뒷문
        int gate0 = mid - 6, gate1 = mid + 6;
        gates(v, gate0, gate1, 4, 0);
        v.fill(mid - 2, 0, 0, mid + 2, 2, 0, AIR);
        v.set(mid, 3, 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "타는 곳 1번", "공항철도"));
        // 대기 의자
        for (int j = 8; j <= 14; j += 3) {
            for (int i = ci + 2; i <= w - 13; i++) {
                if ((i - mid) % 7 != 0) {
                    Furniture.chair(f, i, 0, j, "north", "dark_oak");
                }
            }
        }
        int[] stairBox = {0, 9, si, 15};
        Site.lights(v, 1, 1, w - 2, d - 2, lv[1] - 2, 5, stairBox);
        v.set(si + 1, 2, 14, Blocks.wallSign("dark_oak", "east", "white", false, "", "2층 맞이방"));

        // ---- 2층 맞이방
        int L = lv[1], top = roof - 2;
        Site.floor(v, 1, 1, w - 2, d - 2, L - 1, Block.of("smooth_quartz", 0xECE6DF), stairBox);
        // 출발 안내판 (뒷벽)
        v.fill(mid - 12, L + 3, 1, mid + 12, L + 5, 1, BLACK_CONCRETE);
        String[][] board = {{"출발 안내", "공항철도", "", ""}, {"10:20 직통", "공항", "3번 타는 곳", ""},
                {"10:26 일반", "공항", "1번 타는 곳", ""}, {"10:32 일반", "서울역 도착", "2번 타는 곳", ""}};
        for (int k = 0; k < board.length; k++) {
            v.set(mid - 9 + k * 6, L + 4, 2, Blocks.wallSign("dark_oak", "south", "yellow", true, board[k]));
        }
        // 대기 의자 (안내판을 봄)
        for (int j = 8; j <= 20; j += 3) {
            for (int i = si + 3; i <= w - 14; i++) {
                if ((i - mid) % 8 != 0) {
                    Furniture.chair(f, i, L, j, "south", "dark_oak");
                }
            }
        }
        // 푸드코트 (남쪽)
        for (int j = 4; j <= d - 4; j += 4) {
            v.set(w - 3, L, j, Furniture.COUNTER);
            v.set(w - 3, L, j + 1, Block.of("smoker[facing=west,lit=false]", 0x555451));
            Furniture.table(f, w - 8, L, j, 2, 1, "oak");
        }
        v.set(w - 4, L + 3, d / 2, Blocks.wallSign("dark_oak", "west", "white", true, "", "푸드코트"));
        // 연결 통로 문 (뒷벽) + 개찰구
        v.fill(bridgeI, L, 0, bridgeI + 6, L + 2, 0, AIR);
        gates(v, bridgeI - 2, bridgeI + 8, 4, L);
        v.set(bridgeI + 3, L + 3, 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "타는 곳 2·3·4번", "연결 통로"));
        Site.lights(v, 1, 1, w - 2, d - 2, roof - 2, 5, stairBox);
        // 옥상 글자 간판 「서울역」 (쇠틀 위)
        int gw = HangulFont.width(12, "서울역");
        int gi = mid - gw / 2;
        v.fill(gi - 1, roof + 1, d - 2, gi + gw, roof + 1, d - 2, IRON_BARS);
        for (int i = gi; i < gi + gw; i += 4) {
            v.fill(i, roof + 1, d - 2, i, roof + 12, d - 2, IRON_BARS);
        }
        HangulFont.draw(v, 12, "서울역", gi, roof + 13, d - 1, 1, 0, Block.of("blue_concrete", 0x2C2E8F));
        v.connect();
        return v;
    }

    /** 가게 한 칸 (a0..a1, b0..b1 안쪽): 북쪽(a0 쪽) 유리 앞면과 문, 간판 표지판, 계산대와 진열대 */
    private static void shop(Voxels v, Frame f, Random r, int a0, int b0, int a1, int b1, String name, String kind) {
        v.fill(a0 - 1, 0, b0 - 1, a0 - 1, 4, b1 + 1, WHITE_CONCRETE);
        v.fill(a0 - 1, 0, b0 - 1, a1, 4, b0 - 1, WHITE_CONCRETE);
        v.fill(a0 - 1, 0, b1 + 1, a1, 4, b1 + 1, WHITE_CONCRETE);
        v.fill(a0 - 1, 0, b0, a0 - 1, 2, b1, Block.of("glass_pane", 0xC8DCE4));
        int dj = (b0 + b1) / 2;
        v.set(a0 - 1, 0, dj, AIR);
        v.set(a0 - 1, 1, dj, AIR);
        v.set(a0 - 2, 3, dj, Blocks.wallSign("birch", "west", kind.equals("카페") ? "brown" : "blue", true, "", name));
        for (int j = b0; j <= b1; j++) {
            v.set(a0 + 1, 0, j, j == b0 + 1 ? Block.of("smoker[facing=west,lit=false]", 0x555451) : Furniture.COUNTER);
        }
        v.set(a0 + 1, 0, dj, AIR);
        if (kind.equals("편의점")) {
            for (int i = a0 + 3; i < a1; i += 2) {
                for (int j = b0 + 1; j < b1; j++) {
                    v.set(i, 0, j, Furniture.BOOKSHELF);
                    v.set(i, 1, j, (j & 1) == 0 ? BARREL : Furniture.BOOKSHELF);
                }
            }
        } else {
            for (int i = a0 + 3; i < a1; i += 3) {
                for (int j = b0 + 1; j + 1 < b1; j += 4) {
                    Furniture.table(f, i, 0, j, 1, 1, "oak");
                }
            }
        }
        Interior.lights(f, a0, b0, a1, b1, 0, 6, 3, Interior.LIGHT);
    }

    /** 개찰구 한 줄 (i 범위, j 줄): 낮은 기계 사이 한 칸씩 통로 */
    private static void gates(Voxels v, int i0, int i1, int j, int L) {
        for (int i = i0; i <= i1; i++) {
            if ((i - i0) % 2 == 0) {
                v.set(i, L, j, Block.of("smooth_stone", 0x9E9E9E));
                v.set(i, L + 1, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            }
        }
    }

    // ------------------------------------------------------------------ 옛 역사

    static Voxels oldStation(int w, int d, Random r) {
        int[] lv = OLD_LEVELS;
        int roof = lv[2];
        Voxels v = new Voxels(w, d, -1, roof + 16);
        Frame f = Frame.of(v);
        int mid = w / 2;
        int c0 = mid - 9, c1 = mid + 9;       // 가운데 덩어리
        int wf = d - 2;                       // 날개 앞벽 (가운데는 d-1)
        Block brick = BRICKS, stone = SMOOTH_STONE;
        // 바닥판
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("polished_andesite", 0x848685));
        v.fill(0, lv[1] - 1, 0, w - 1, lv[1] - 1, wf, stone);
        v.fill(c0, lv[1] - 1, 0, c1, lv[1] - 1, d - 1, stone);
        v.fill(0, roof - 1, 0, w - 1, roof - 1, wf, stone);
        v.fill(c0, roof - 1, 0, c1, roof - 1, d - 1, stone);
        // 겉벽: 붉은 벽돌, 화강석 기단·층 띠·처마
        v.walls(0, 0, 0, w - 1, roof - 1, wf, brick);
        v.walls(c0, 0, wf, c1, roof - 1, d - 1, brick);
        v.fill(c0 + 1, 0, wf, c1 - 1, roof - 2, wf, AIR);
        for (int y : new int[]{0, lv[1] - 1, roof - 1}) {
            v.walls(0, y, 0, w - 1, y, wf, stone);
            v.walls(c0, y, wf, c1, y, d - 1, stone);
        }
        v.walls(0, roof, 0, w - 1, roof, wf, stone);
        // 가운데 덩어리와 몸체 사이 (날개 앞벽 줄) 트임: 층 띠를 그린 뒤 다시 뚫음
        v.fill(c0 + 1, 0, wf, c1 - 1, lv[1] - 2, wf, AIR);
        v.fill(c0 + 1, lv[1], wf, c1 - 1, roof - 2, wf, AIR);
        // 모서리 화강석 (번갈아)
        for (int y = 1; y < roof - 1; y++) {
            if (y % 2 == 0) {
                for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, wf}, {w - 1, wf}, {c0, d - 1}, {c1, d - 1}}) {
                    v.set(c[0], y, c[1], stone);
                }
            }
        }
        // 창: 1층 세로 창, 2층 아치 창
        for (int i = 3; i < w - 3; i += 3) {
            boolean centre = i >= c0 && i <= c1;
            int j = centre ? d - 1 : wf;
            if (centre && Math.abs(i - mid) <= 5) {
                continue;
            }
            v.fill(i, 1, j, i, 3, j, GLASS_PANE);
            v.fill(i, lv[1] + 1, j, i, lv[1] + 2, j, GLASS_PANE);
            v.set(i, lv[1] + 3, j, Block.of("stone_brick_stairs[facing=" + "south" + ",half=top,shape=straight,waterlogged=false]", 0x7A7979));
            v.fill(i, 1, 0, i, 3, 0, GLASS_PANE);
            v.fill(i, lv[1] + 1, 0, i, lv[1] + 2, 0, GLASS_PANE);
        }
        for (int j = 3; j < wf - 1; j += 3) {
            for (int i : new int[]{0, w - 1}) {
                v.fill(i, 1, j, i, 3, j, GLASS_PANE);
                v.fill(i, lv[1] + 1, j, i, lv[1] + 2, j, GLASS_PANE);
            }
        }
        // 현관: 아치 셋 (가운데 넓게), 위 시계
        for (int[] a : new int[][]{{mid - 5, mid - 4}, {mid - 1, mid + 1}, {mid + 4, mid + 5}}) {
            v.fill(a[0], 0, d - 1, a[1], 3, d - 1, AIR);
            v.set(a[0], 4, d - 1, Block.of("stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]", 0x7A7979));
            v.set(a[1], 4, d - 1, Block.of("stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]", 0x7A7979));
        }
        for (int y = lv[1] + 1; y <= lv[1] + 3; y++) {
            for (int i = mid - 1; i <= mid + 1; i++) {
                v.set(i, y, d - 1, (i == mid && y == lv[1] + 2) ? BLACK_CONCRETE : WHITE_CONCRETE);
            }
        }
        v.set(mid, lv[1] + 3, d - 1, BLACK_CONCRETE);
        v.fill(mid - 2, lv[1], d - 1, mid + 2, lv[1], d - 1, stone);
        // 날개 지붕: 짙은 기와 낮은 지붕
        for (int i = 0; i < w; i++) {
            for (int j = 0; j <= wf; j++) {
                if (i >= c0 && i <= c1) {
                    continue;
                }
                int edge = Math.min(Math.min(i, w - 1 - i), Math.min(j, wf - j));
                if (edge == 0) {
                    v.set(i, roof + 1, j, Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637));
                } else if (edge <= 3) {
                    v.set(i, roof + 1, j, Block.of("deepslate_tiles", 0x363637));
                    if (edge >= 2) {
                        v.set(i, roof + 2, j, Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637));
                    }
                } else {
                    v.set(i, roof + 2, j, Block.of("deepslate_tiles", 0x363637));
                    v.set(i, roof + 3, j, Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637));
                }
            }
        }
        // 가운데 덩어리 앞 모서리 작은 탑 (구리 뾰족 지붕)
        for (int ti : new int[]{c0 + 1, c1 - 1}) {
            v.fill(ti - 1, roof, d - 4, ti + 1, roof + 3, d - 2, brick);
            v.fill(ti - 1, roof + 4, d - 4, ti + 1, roof + 4, d - 2, COPPER_CUT);
            v.set(ti, roof + 5, d - 3, COPPER);
            v.set(ti, roof + 6, d - 3, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
        }
        // 계단이 올라오는 북쪽 날개 뒤는 평평한 옥상 (기와를 걷음)
        v.fill(1, roof + 1, 1, 14, roof + 3, 9, null);
        dome(v, mid, d / 2, roof);
        oldInside(v, f, r, w, d, mid, c0, c1, wf);
        v.connect();
        return v;
    }

    /** 팔각 받침(창 있는 벽돌) 위 초록 구리 돔, 꼭대기 작은 탑 */
    private static void dome(Voxels v, int ci, int cj, int roof) {
        double R = 7.5;
        int base = roof;
        // 받침 (팔각에 가까운 원통, 3칸)
        for (int y = base; y <= base + 2; y++) {
            for (int j = cj - 9; j <= cj + 9; j++) {
                for (int i = ci - 9; i <= ci + 9; i++) {
                    double dd = Math.max(Math.abs(i + 0.5 - ci - 0.5) + Math.abs(j - cj) * 0.42, Math.max(Math.abs(i - ci), Math.abs(j - cj)));
                    if (dd <= R + 0.5 && dd > R - 0.7) {
                        boolean win = y == base + 1 && ((i + j) % 3 == 0);
                        v.set(i, y, j, win ? GLASS_PANE : (y == base + 2 ? SMOOTH_STONE : BRICKS));
                    } else if (dd <= R - 0.7) {
                        v.set(i, y, j, AIR);
                    }
                }
            }
        }
        // 반구 돔 (갈비 무늬)
        int y0 = base + 3;
        for (int y = y0; y <= y0 + (int) R; y++) {
            double hy = y - y0 + 0.5;
            double rr = Math.sqrt(Math.max(0, R * R - hy * hy));
            for (int j = cj - 9; j <= cj + 9; j++) {
                for (int i = ci - 9; i <= ci + 9; i++) {
                    double dd = Math.hypot(i - ci, j - cj);
                    if (dd <= rr + 0.5 && dd > rr - 1.2) {
                        double ang = Math.toDegrees(Math.atan2(j - cj, i - ci));
                        boolean rib = Math.abs(((ang % 45) + 45) % 45 - 22.5) > 19;
                        v.set(i, y, j, rib ? COPPER_CUT : COPPER);
                    }
                }
            }
        }
        // 꼭대기 작은 탑 (랜턴)
        int top = y0 + (int) R + 1;
        v.fill(ci - 1, top, cj - 1, ci + 1, top + 1, cj + 1, COPPER);
        v.set(ci, top, cj - 1, GLASS_PANE);
        v.set(ci, top, cj + 1, GLASS_PANE);
        v.set(ci - 1, top, cj, GLASS_PANE);
        v.set(ci + 1, top, cj, GLASS_PANE);
        v.set(ci, top + 2, cj, COPPER_CUT);
        v.set(ci, top + 3, cj, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
        // 돔 안 조명
        v.set(ci, y0 + (int) R - 1, cj, Block.of("lantern[hanging=true,waterlogged=false]", 0x6A5B49));
    }

    /** 옛 역사 안: 중앙홀(두 층 높이, 돌기둥), 날개 전시실, 2층 그릴, 뒤 회랑, 계단, 화장실 */
    private static void oldInside(Voxels v, Frame f, Random r, int w, int d, int mid, int c0, int c1, int wf) {
        int[] lv = OLD_LEVELS;
        Block in = Interior.INNER_WALL;
        // 중앙홀: 2층 바닥을 걷어 두 층 높이 (뒤 3줄은 2층 회랑으로 남김), 돔으로 뚫음
        v.fill(c0 + 1, lv[1] - 1, 4, c1 - 1, lv[1] - 1, wf - 1, AIR);
        v.fill(c0 + 1, lv[2] - 1, 4, c1 - 1, lv[2] - 1, d - 3, AIR);
        for (int j = 4; j <= d - 2; j++) {
            for (int i = c0 + 1; i <= c1 - 1; i++) {
                v.set(i, -1, j, ((i + j) & 1) == 0 ? POLISHED_DIORITE : POLISHED_ANDESITE);
            }
        }
        // 회랑 난간
        for (int i = c0 + 1; i <= c1 - 1; i++) {
            v.set(i, lv[1], 4, Block.of("iron_bars", 0x888888));
        }
        // 돌기둥 넷
        for (int[] c : new int[][]{{mid - 5, 7}, {mid + 5, 7}, {mid - 5, d - 6}, {mid + 5, d - 6}}) {
            v.fill(c[0], 0, c[1], c[0], lv[2] - 2, c[1], Block.of("quartz_pillar[axis=y]", 0xEBE6E0));
        }
        // 가운데와 날개 사이 벽 (문)
        for (int side : new int[]{-1, 1}) {
            int wi = side < 0 ? c0 : c1;
            for (int k = 0; k < 2; k++) {
                int L = lv[k], top = lv[k + 1] - 2;
                v.fill(wi, L, 1, wi, top, wf - 1, in);
                Interior.door(f, wi, L, k == 0 ? d / 2 : 2, "dark_oak", side < 0 ? "west" : "east");
            }
        }
        // 안내 데스크
        for (int i = mid - 2; i <= mid + 2; i++) {
            v.set(i, 0, d - 8, Furniture.COUNTER);
        }
        v.set(mid, 3, 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "문화역서울284", "옛 서울역사"));
        // 계단 (북쪽 날개 뒤, 출입구가 남쪽(+i)을 봄)
        int sd = Interior.stairDepth(lv);
        Frame st = Frame.facing(v, sd + 1, 2, "west");
        Interior.stairCore(st, lv, Interior.CORE_WALL, "stone_brick", 0x7A7979);
        st.fill(-1, lv[2] + 3, -1, 5, lv[2] + 3, sd, SMOOTH_STONE);
        int[] stairBox = {0, 1, sd + 2, 7};
        // 날개: 1층 전시실(가벽과 의자), 2층 북쪽 그릴·남쪽 전시실, 남쪽 날개 1층 화장실
        for (int side : new int[]{-1, 1}) {
            int a0 = side < 0 ? 1 : c1 + 1, a1 = side < 0 ? c0 - 1 : w - 2;
            for (int k = 0; k < 2; k++) {
                int L = lv[k], h = lv[k + 1] - lv[k];
                Site.floor(v, a0, 1, a1, wf - 1, L - 1, Block.of(k == 0 ? "polished_andesite" : "dark_oak_planks", 0x848685), stairBox);
                if (k == 1 && side < 0) {
                    // 그릴: 흰 식탁보 탁자
                    for (int i = a0 + 2; i + 1 < a1; i += 4) {
                        for (int j = 8; j + 1 < wf - 1; j += 4) {
                            v.set(i, L, j, Block.of("white_wool", 0xE9ECEC));
                            v.set(i + 1, L, j, Block.of("white_wool", 0xE9ECEC));
                            Furniture.chair(f, i, L, j - 1, "north", "dark_oak");
                            Furniture.chair(f, i + 1, L, j + 1, "south", "dark_oak");
                        }
                    }
                    v.set(c0 - 1, L + 1, 3, Blocks.wallSign("birch", "west", "black", false, "", "그릴"));
                } else if (k == 0 && side > 0) {
                    Interior.restroom(f, a1 - 4, 2, a1 - 1, 7, L, h, "남자 화장실", a1 - 3);
                    Interior.restroom(f, a1 - 10, 2, a1 - 7, 7, L, h, "여자 화장실", a1 - 9);
                    exhibits(v, a0, a1 - 12, L, wf, h);
                } else {
                    exhibits(v, side < 0 ? Math.max(a0, sd + 4) : a0, a1, L, wf, h);
                }
                Site.lights(v, a0, 1, a1, wf - 1, L + h - 2, 4, stairBox);
            }
        }
    }

    /** 전시실: 흰 가벽 줄과 가운데 의자 */
    private static void exhibits(Voxels v, int a0, int a1, int L, int wf, int h) {
        for (int i = a0 + 3; i < a1 - 1; i += 6) {
            v.fill(i, L, 4, i, L + 2, wf - 5, Block.of("white_concrete", 0xCFD5D6));
            v.set(i + 2, L, wf / 2, Site.BENCH);
        }
    }

    // ------------------------------------------------------------------ 승강장

    /**
     * 승강장과 선로 (i = x - x0, j = z - z0). hallI: 새 역사 뒷벽 i (그 동쪽부터 1번 승강장),
     * hallJ0..hallJ1: 새 역사 j 범위, bridgeJ: 연결 통로 북쪽 줄.
     */
    static Voxels platforms(Site s, int hallI, int hallJ0, int hallJ1, int bridgeJ, Random r) {
        Voxels v = new Voxels(s.w, s.d, -1, 14);
        int p1a = hallI + 1, p1b = hallI + 5;                 // 1번 승강장 (역사 옆)
        int[] rails = {p1b + 2, p1b + 15, p1b + 18, p1b + 31};
        int i1a = p1b + 4, i1b = p1b + 13, i2a = p1b + 20, i2b = p1b + 29;
        int fence = p1b + 33;
        int jA = 4, jB = s.d - 5;                            // 선로 끝 (남북)
        // 바닥: 역사 뒤 마당(1번 승강장 높이로 이어짐), 승강장, 선로 자갈
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (!s.land(i, j) || (i <= hallI && j >= hallJ0 && j <= hallJ1)) {
                    continue;
                }
                boolean inYard = j >= jA - 2 && j <= jB + 2;
                Block b;
                if (!inYard || i > fence) {
                    b = Block.of("light_gray_concrete", 0x7D7D73);
                    v.set(i, -1, j, b);
                    continue;
                }
                if (i < p1a || (i >= p1a && i <= p1b) || (i >= i1a && i <= i1b) || (i >= i2a && i <= i2b)) {
                    v.set(i, -1, j, SMOOTH_STONE);
                    boolean edge = i == p1b || i == i1a || i == i1b || i == i2a || i == i2b;
                    boolean strip = i == p1b - 1 || i == i1a + 1 || i == i1b - 1 || i == i2a + 1 || i == i2b - 1;
                    v.set(i, 0, j, edge ? Block.of("light_gray_concrete", 0x7D7D73) : strip ? TACTILE : PLAT);
                } else if (i <= fence - 1) {
                    v.set(i, -1, j, GRAVEL);
                } else {
                    v.set(i, -1, j, GRAVEL);
                }
            }
        }
        // 레일과 끝 막이
        for (int ri : rails) {
            for (int j = jA; j <= jB; j++) {
                v.set(ri, 0, j, RAIL);
            }
            for (int j : new int[]{jA - 1, jB + 1}) {
                v.set(ri - 1, 0, j, Block.of("red_concrete", 0x8E2121));
                v.set(ri, 0, j, Block.of("iron_block", 0xDCDCDC));
                v.set(ri + 1, 0, j, Block.of("red_concrete", 0x8E2121));
                v.set(ri, 1, j, Block.of("red_concrete", 0x8E2121));
            }
        }
        // 역사 뒷문 앞 계단 (역사 바닥 → 승강장 높이)
        for (int j = Math.max(0, hallJ0); j <= Math.min(s.d - 1, hallJ1); j++) {
            if (s.land(p1a, j)) {
                v.set(p1a, 0, j, Blocks.stairs("polished_andesite", "east", 0x848685));
            }
        }
        // 동쪽 울타리
        for (int j = jA - 2; j <= jB + 2; j++) {
            if (s.land(fence, j)) {
                v.set(fence, 0, j, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
                v.fill(fence, 1, j, fence, 2, j, IRON_BARS);
            }
        }
        for (int j : new int[]{jA - 3, jB + 3}) {
            for (int i = p1b + 1; i <= fence; i++) {
                if (s.land(i, j)) {
                    v.set(i, 0, j, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
                    v.fill(i, 1, j, i, 2, j, IRON_BARS);
                }
            }
        }
        // 승강장 안전문 (선로 쪽 가장자리, 유리, 문 자리는 흰 유리)
        int bj0 = bridgeJ, bj1 = bridgeJ + 6;
        int[][] edges = {{p1b, 1}, {i1a, -1}, {i1b, 1}, {i2a, -1}, {i2b, 1}};
        for (int[] e : edges) {
            for (int j = jA + 2; j <= jB - 2; j++) {
                Block g = (j % 8 < 2) ? Block.of("white_stained_glass_pane", 0xF0F0F0) : Block.of("glass_pane", 0xC8DCE4);
                v.fill(e[0], 1, j, e[0], 2, j, g);
            }
        }
        // 지붕과 기둥 (연결 통로 아래는 비움), 매단 표지판, 의자, 등
        int[][] plats = {{p1a, p1b, 1}, {i1a, i1b, 2}, {i2a, i2b, 3}};
        for (int[] p : plats) {
            int a0 = p[0] - (p[2] == 1 ? 0 : 0), a1 = p[1];
            int ci = (a0 + a1) / 2;
            for (int j = jA + 3; j <= jB - 3; j++) {
                if (j >= bj0 - 1 && j <= bj1 + 1) {
                    continue;
                }
                for (int i = a0 - 1; i <= a1 + 1; i++) {
                    v.set(i, 5, j, (i == a0 - 1 || i == a1 + 1) ? WHITE_CONCRETE : Block.of("light_gray_concrete", 0x7D7D73));
                }
                if ((j - jA) % 4 == 0) {
                    v.set(ci, 5, j, Interior.LIGHT);
                }
                if ((j - jA) % 12 == 3) {
                    v.fill(ci, 1, j, ci, 4, j, Block.of("iron_block", 0xDCDCDC));
                }
                if ((j - jA) % 24 == 9) {
                    String no = p[2] == 1 ? "1번 타는 곳" : p[2] == 2 ? "2·3번 타는 곳" : "4·5번 타는 곳";
                    v.set(ci, 4, j, Blocks.hangingSign("dark_oak", 0, "white", true, "공항철도", no, "공항 방면"));
                }
                if ((j - jA) % 12 == 7 && a1 - a0 >= 4) {
                    for (int i = ci - 1; i <= ci + 1; i++) {
                        v.set(i, 1, j, Site.BENCH);
                    }
                }
            }
        }
        // 선로 위 연결 통로 (새 역사 2층 → 섬식 승강장 둘), 유리 벽과 지붕
        int bx0 = hallI + 1, bx1 = i2b;
        for (int j = bj0; j <= bj1; j++) {
            for (int i = bx0; i <= bx1; i++) {
                v.set(i, BRIDGE - 1, j, SMOOTH_STONE);
                v.set(i, BRIDGE + 3, j, Block.of("light_gray_concrete", 0x7D7D73));
                if (j == bj0 || j == bj1) {
                    v.fill(i, BRIDGE, j, i, BRIDGE + 2, j, Block.of("glass_pane", 0xC8DCE4));
                }
            }
        }
        for (int i = bx0; i <= bx1; i += 6) {
            v.set(i, BRIDGE + 3, (bj0 + bj1) / 2, Interior.LIGHT);
        }
        // 다리 기둥 (승강장 위에서만)
        for (int[] p : plats) {
            for (int j : new int[]{bj0, bj1}) {
                v.fill(p[0], 1, j, p[0], BRIDGE - 2, j, Block.of("iron_block", 0xDCDCDC));
            }
        }
        // 섬식 승강장 계단 (통로 북쪽, 출입구가 통로를 봄)
        int[] lv = {PLATFORM, BRIDGE};
        int sd = Interior.stairDepth(lv);
        for (int[] p : new int[][]{{i1a, i1b}, {i2a, i2b}}) {
            int oi = (p[0] + p[1]) / 2 + 1;
            Frame st = Frame.facing(v, oi, bj0 - 1, "north");
            Interior.stairCore(st, lv, Interior.CORE_WALL, "polished_andesite", 0x848685, 1);
            st.fill(-1, BRIDGE + 3, -1, 3, BRIDGE + 3, sd, Block.of("light_gray_concrete", 0x7D7D73));
            // 통로 바닥과 이어지게 계단 위 칸을 비움
            st.fill(0, BRIDGE, -1, 0, BRIDGE + 2, -1, AIR);
        }
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 역 앞 광장

    static Voxels frontPlaza(Site s, int hi, int hj) {
        Voxels v = new Voxels(s.w, s.d, -1, 8);
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (s.land(i, j)) {
                    boolean band = Math.floorMod(j - hj, 9) == 0;
                    v.set(i, -1, j, band ? Block.of("light_gray_concrete", 0x7D7D73) : POLISHED_ANDESITE);
                }
            }
        }
        // 나무 줄과 의자 (남북으로, 거점 앞은 비움)
        for (int j = 3; j < s.d - 3; j += 8) {
            if (Math.abs(j - hj) < 9) {
                continue;
            }
            for (int i : new int[]{5, s.w - 6}) {
                if (s.solid(i, j, 2)) {
                    Site.planter(v, i, j);
                    if (s.solid(i + 2, j + 3)) {
                        Site.bench(v, i - 1, j + 3, 3, true);
                    }
                }
            }
        }
        for (int j = 6; j < s.d - 3; j += 12) {
            if (s.solid(s.w / 2, j) && Math.abs(j - hj) > 5) {
                Site.lamp(v, s.w / 2, j);
            }
        }
        // 표석
        int mi = s.w - 4, mj = hj + 8;
        if (s.solid(mi, mj, 1) && s.solid(mi, mj + 3, 1)) {
            v.fill(mi, 0, mj, mi, 1, mj + 3, POLISHED_GRANITE);
            v.set(mi - 1, 1, mj + 1, Blocks.wallSign("dark_oak", "west", "white", false, "", "서울역", "SEOUL STATION"));
        }
        v.connect();
        return v;
    }
}
