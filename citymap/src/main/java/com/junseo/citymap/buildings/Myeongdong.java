package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 명동: 차 없는 보행 거리(명동길, 남북)와 골목(동서·남북)을 따라 늘어선 4~7층 상가 건물.
 * 층마다 간판 띠와 진짜 표지판 글씨, 모서리마다 세로로 쌓인 돌출 간판, 1층은 화장품·옷·신발·환전소·식당,
 * 위층은 식당·카페·피부과·네일·노래방·사무실. 거점(jewelry) 바로 앞 건물 1층이 보석상
 * (유리 진열장 아래 금·다이아몬드·에메랄드, 뒤 사무실, 금고실, 방범 셔터), 골목 모퉁이에 명동 카페.
 * <p>
 * 건물은 모두 정면 남쪽(j = d-1)으로 짓고 놓을 때 돌립니다. 맨 앞 줄(j = d-1)은 간판·돌출 간판 자리,
 * 겉벽은 j = d-2. 뒤 왼쪽 구석 1칸 줄 꺾인 계단으로 옥상까지 오르고, 1층은 가게 옆 계단 통로로 들어갑니다.
 */
final class Myeongdong {
    enum Kind { SHOP, JEWELRY, CAFE }

    /** 1층 가게 {이름, 종류} (%s 는 상호 앞말) */
    private static final String[][] GROUND = {
            {"%s코스메틱", "cosmetics"}, {"뷰티 %s", "cosmetics"}, {"%s화장품", "cosmetics"}, {"스킨%s", "cosmetics"},
            {"%s패션", "fashion"}, {"스타일%s", "fashion"}, {"%s 옷가게", "fashion"}, {"%s슈즈", "shoes"},
            {"명동환전소", "exchange"}, {"%s안경", "optical"}, {"명동칼국수", "food"}, {"%s분식", "food"},
            {"%s기념품", "souvenir"}, {"%s약국", "pharmacy"},
    };
    /** 위층 {이름, 종류} */
    private static final String[][] UPPER = {
            {"%s피부과", "clinic"}, {"%s성형외과", "clinic"}, {"%s치과", "clinic"}, {"네일 %s", "nail"},
            {"%s돈가스", "food"}, {"%s칼국수", "food"}, {"카페 %s", "cafe"}, {"%s노래방", "karaoke"},
            {"%s여행사", "office"}, {"%s어학원", "office"}, {"%s패션", "fashion"}, {"%s코스메틱", "cosmetics"},
    };
    private static final Block[][] SKINS = {
            {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE}, {Block.of("white_terracotta", 0xD1B2A1), SMOOTH_STONE},
            {POLISHED_ANDESITE, SMOOTH_STONE}, {Block.of("light_gray_concrete", 0x7D7D73), WHITE_CONCRETE},
            {POLISHED_GRANITE, SMOOTH_STONE}, {Block.of("gray_concrete", 0x36393D), Block.of("light_gray_concrete", 0x7D7D73)},
            {Block.of("quartz_bricks", 0xEAE4DC), SMOOTH_STONE},
    };
    private static final String[] CANDLES = {"pink", "white", "red", "magenta", "light_blue", "yellow"};
    private static final String[] CLOTHES = {"white", "black", "gray", "light_gray", "blue", "pink", "brown", "red", "light_blue"};

    // ------------------------------------------------------------------ 배치

    static void plan(CityTerrain t, Polygon area, BuildMask m, Layout.Hub hub, List<Placement> out) {
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int[] blk = m.componentNear(hx, hz, 30);
        if (blk == null || blk[2] - blk[0] < 50 || blk[3] - blk[1] < 50) {
            return;
        }
        Random rnd = Plans.random(t, "명동");
        int x0 = blk[0], x1 = blk[2], z0 = blk[1], z1 = blk[3];
        int sw0 = hx - 4, sw1 = hx + 4;                         // 명동길 (남북, 9칸)
        int zc = (z0 + z1) / 2, aw0 = zc - 3, aw1 = zc + 2;     // 동서 골목 (6칸)
        int ex0 = sw1 + 1;
        int bx = ex0 + (x1 - ex0) / 2;                          // 동쪽 남북 골목 (5칸)
        int bw0 = bx - 2, bw1 = bx + 2;
        // 거리 바닥 (블록 전체 + 둘레 여유)
        int px0 = x0 - 3, pz0 = z0 - 3, px1 = x1 + 3, pz1 = z1 + 3;
        List<int[]> streets = List.of(new int[]{sw0 - px0, 0, sw1 - px0, pz1 - pz0}, new int[]{0, aw0 - pz0, px1 - px0, aw1 - pz0},
                new int[]{bw0 - px0, 0, bw1 - px0, pz1 - pz0});
        List<int[]> lots = new ArrayList<>();
        // 보석상: 거점 동쪽, 명동길을 봄
        int jd = 20, jw = 16;
        int[] jewel = {ex0, hz - jw / 2, ex0 + jd - 1, hz - jw / 2 + jw - 1};
        if (jewel[3] > z1) {
            jewel[1] -= jewel[3] - z1;
            jewel[3] = z1;
        }
        if (m.rectFree(jewel[0], jewel[1], jewel[2], jewel[3])) {
            long s = rnd.nextLong();
            out.add(Placement.rect("명동 보석상", "jewelry", jewel[0], jewel[1], jewel[2], jewel[3], "west",
                    (w, d) -> building(w, d, new Random(s), Kind.JEWELRY, 5)));
            lots.add(jewel);
        }
        // 명동 카페: 서쪽 남쪽 칸, 골목 모퉁이
        int cd = Math.min(16, sw0 - x0), cw = 13;
        int[] cafe = {sw0 - cd, aw1 + 1, sw0 - 1, aw1 + cw};
        if (m.rectFree(cafe[0], cafe[1], cafe[2], cafe[3])) {
            long s = rnd.nextLong();
            out.add(Placement.rect("명동 카페", "cafe", cafe[0], cafe[1], cafe[2], cafe[3], "east",
                    (w, d) -> building(w, d, new Random(s), Kind.CAFE, 4)));
            lots.add(cafe);
        }
        // 나머지: 칸마다 길을 보는 건물 줄
        // {x0, z0, x1, z1, 정면}
        int[][] rows = {
                {x0, z0, sw0 - 1, aw0 - 1, 'e'}, {x0, aw1 + 1, sw0 - 1, z1, 'e'},
                {ex0, z0, ex0 + 13, aw0 - 1, 'w'}, {ex0, aw1 + 1, ex0 + 13, z1, 'w'},
                {ex0 + 14, z0, bw0 - 1, aw0 - 1, 'e'}, {ex0 + 14, aw1 + 1, bw0 - 1, z1, 'e'},
                {bw1 + 1, z0, bw1 + 14, aw0 - 1, 'w'}, {bw1 + 1, aw1 + 1, bw1 + 14, z1, 'w'},
                {bw1 + 15, z0, x1, aw0 - 1, 'e'}, {bw1 + 15, aw1 + 1, x1, z1, 'e'},
        };
        for (int[] row : rows) {
            if (row[2] - row[0] < 9) {
                continue;
            }
            String front = row[4] == 'e' ? "east" : "west";
            int z = row[1];
            while (z + 10 <= row[3]) {
                int width = 11 + rnd.nextInt(6);
                int za = z, zb = Math.min(row[3], z + width - 1);
                if (row[3] - zb < 10) {
                    zb = row[3];
                }
                int[] lot = {row[0], za, row[2], zb};
                boolean clash = false;
                for (int[] o : lots) {
                    clash |= lot[0] <= o[2] + 1 && lot[2] >= o[0] - 1 && lot[1] <= o[3] + 1 && lot[3] >= o[1] - 1;
                }
                if (!clash && m.rectFree(lot[0], lot[1], lot[2], lot[3])) {
                    long s = rnd.nextLong();
                    int floors = 4 + rnd.nextInt(4);
                    out.add(Placement.rect("명동 상가 건물", "retail", lot[0], lot[1], lot[2], lot[3], front,
                            (w, d) -> building(w, d, new Random(s), Kind.SHOP, floors)));
                    lots.add(lot);
                    z = zb + 2;
                } else {
                    z += 3;
                }
            }
        }
        // 보행 거리 포장 (건물 자리는 비움)
        List<int[]> keep = new ArrayList<>();
        for (int[] l : lots) {
            keep.add(new int[]{l[0] - px0, l[1] - pz0, l[2] - px0, l[3] - pz0});
        }
        out.add(Placement.rect("명동 거리", "plaza", px0, pz0, px1, pz1, "south", (w, d) -> {
            Site s = new Site(t, area, px0, pz0, px1, pz1);
            return street(s, keep, streets, hx - px0, hz - pz0);
        }));
    }

    /** 보행 거리 바닥: 베이지·회색 보도블록 무늬, 거리 가운데 가로등, 골목 입구 표지판 */
    static Voxels street(Site s, List<int[]> keep, List<int[]> streets, int hi, int hj) {
        Voxels v = new Voxels(s.w, s.d, -1, 6);
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (!s.land(i, j) || in(keep, i, j)) {
                    continue;
                }
                boolean st = in(streets, i, j);
                int h = Math.floorMod(i * 31 + j * 17, 7);
                v.set(i, -1, j, st ? (Math.floorMod(i + j, 4) == 0 ? Block.of("smooth_sandstone", 0xDFD6AA)
                        : h == 0 ? SMOOTH_STONE : Block.of("light_gray_concrete", 0x7D7D73)) : Block.of("light_gray_concrete", 0x7D7D73));
            }
        }
        // 거리 가로등 (가운데 줄, 거점 둘레는 비움)
        for (int[] st : streets) {
            boolean ns = st[3] - st[1] > st[2] - st[0];
            int c = ns ? (st[0] + st[2]) / 2 : (st[1] + st[3]) / 2;
            for (int k = (ns ? st[1] : st[0]) + 6; k <= (ns ? st[3] : st[2]) - 4; k += 14) {
                int i = ns ? c : k, j = ns ? k : c;
                if (s.solid(i, j) && !in(keep, i, j) && Math.hypot(i - hi, j - hj) > 6) {
                    Site.lamp(v, i, j);
                }
            }
        }
        // 명동길 입구 표지 (남쪽 끝, 길 양옆 기둥)
        int[] main = streets.get(0);
        for (int j = s.d - 1; j > s.d - 12; j--) {
            if (s.solid(main[0], j) && s.solid(main[2], j)) {
                v.fill(main[0], 0, j, main[0], 3, j, StreetPlan.POST);
                v.set(main[0], 2, j + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "명동길", "보행자 전용"));
                break;
            }
        }
        v.connect();
        return v;
    }

    private static boolean in(List<int[]> boxes, int i, int j) {
        for (int[] b : boxes) {
            if (i >= b[0] && i <= b[2] && j >= b[1] && j <= b[3]) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 건물

    static int[] levels(int floors) {
        return Floors.levels(Floors.GROUND, Floors.OFFICE, floors);
    }

    /** 상가 건물 (정면 남쪽). kind 에 따라 1층(과 2층)이 보석상·카페 */
    static Voxels building(int w, int d, Random r, Kind kind, int floors) {
        int[] lv = levels(floors);
        int roof = lv[floors];
        int jf = d - 2;                                   // 겉벽 (앞)
        Voxels v = new Voxels(w, d, -1, roof + 6);
        Frame f = Frame.of(v);
        Block[] skin = SKINS[r.nextInt(SKINS.length)];
        Block wall = skin[0], trim = skin[1];
        // 바닥판·겉벽
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("light_gray_concrete", 0x7D7D73));
        for (int k = 0; k <= floors; k++) {
            v.fill(0, lv[k] - 1, 0, w - 1, lv[k] - 1, jf, k == 0 ? POLISHED_ANDESITE : SMOOTH_STONE);
        }
        v.walls(0, 0, 0, w - 1, roof - 1, jf, wall);
        for (int k = 1; k < floors; k++) {
            v.walls(0, lv[k] - 1, 0, w - 1, lv[k] - 1, jf, trim);
        }
        // 계단 (뒤 왼쪽 구석, 1칸 줄, 출입구가 남쪽)
        int sd = Interior.stairDepth(lv);
        Frame st = Frame.facing(v, 3, sd, "north");
        Interior.stairCore(st, lv, Interior.CORE_WALL, "stone_brick", 0x7A7979, 1);
        st.fill(-1, roof + 3, -1, 3, roof + 3, sd, SMOOTH_STONE);
        int sj = sd + 1;                                  // 계단 출입구 줄
        int[] stairBox = {0, 0, 4, sj};
        // 1층: 계단 통로 (i 1..3), 문, 통로와 가게 사이 벽
        v.fill(4, 0, sj, 4, lv[1] - 2, jf, wall);
        Interior.door(f, 2, 0, jf, "spruce", "north");
        v.set(1, 0, jf, Block.of("glass_pane", 0xC8DCE4));
        v.set(3, 0, jf, Block.of("glass_pane", 0xC8DCE4));
        v.set(2, lv[1] - 2, jf - 1, Interior.LIGHT);
        // 위층 창과 간판 띠
        Object[] board0 = ShopHouse.BOARDS[r.nextInt(ShopHouse.BOARDS.length)];
        for (int k = 1; k < floors; k++) {
            int L = lv[k];
            for (int i = 1; i < w - 1; i++) {
                if (i % 4 != 0) {
                    v.fill(i, L + 1, jf, i, L + 2, jf, GLASS_PANE);
                }
            }
            for (int j = 3; j < jf - 1; j += 4) {
                v.fill(w - 1, L + 1, j, w - 1, L + 2, j, GLASS_PANE);
            }
        }
        // 층마다 쓰임과 간판
        String[] ground = pick(r, GROUND);
        if (kind == Kind.JEWELRY) {
            ground = new String[]{"명동보석", "jewelry"};
        } else if (kind == Kind.CAFE) {
            ground = new String[]{"명동 카페", "cafe"};
        }
        groundShop(v, f, r, w, d, jf, lv[1] - lv[0], ground, kind);
        for (int k = 1; k < floors; k++) {
            String[] use = kind == Kind.CAFE && k == 1 ? new String[]{"명동 카페 2층", "cafe"} : pick(r, UPPER);
            int L = lv[k], h = lv[k + 1] - L;
            Object[] board = ShopHouse.BOARDS[r.nextInt(ShopHouse.BOARDS.length)];
            v.fill(1, L, jf, w - 2, L, jf, (Block) board[0]);
            v.set(w / 2, L, jf + 1, Blocks.wallSign((String) board[1], "south", (String) board[2], true, (k + 1) + "층", use[0]));
            upper(v, f, r, w, jf, L, h, use[1], stairBox);
        }
        // 모서리 세로 돌출 간판 (층마다)
        int pi = r.nextBoolean() ? 0 : w - 1;
        for (int k = 1; k < floors; k++) {
            String[] n = pick(r, k == 1 && kind == Kind.JEWELRY ? new String[][]{{"금·은·보석", ""}} : UPPER);
            Object[] board = ShopHouse.BOARDS[r.nextInt(ShopHouse.BOARDS.length)];
            v.set(pi, lv[k] + 1, jf + 1, Blocks.wallHangingSign((String) board[1], pi == 0 ? "east" : "west", (String) board[2], true, "", n[0]));
        }
        // 옥상 난간, 실외기, 물탱크
        v.walls(0, roof, 0, w - 1, roof, jf, wall);
        v.walls(0, roof + 1, 0, w - 1, roof + 1, jf, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.set(w - 3, roof, 3, SMOOTH_STONE);
        v.set(w - 3, roof, 5, SMOOTH_STONE);
        v.connect();
        return v;
    }

    private static String[] pick(Random r, String[][] list) {
        String[] s = list[r.nextInt(list.length)];
        return new String[]{s[0].contains("%s") ? s[0].formatted(KoreanNames.prefix(r)) : s[0], s[1]};
    }

    /** 1층 가게 (i 5..w-2): 유리 앞면과 문, 간판 띠, 종류별 실내 */
    private static void groundShop(Voxels v, Frame f, Random r, int w, int d, int jf, int h, String[] name, Kind kind) {
        int a0 = 5, a1 = w - 2, top = h - 2;
        // 앞면 유리와 문
        for (int i = a0; i <= a1; i++) {
            v.set(i, 0, jf, POLISHED_DEEPSLATE);
            v.fill(i, 1, jf, i, top, jf, GLASS_PANE);
        }
        int door = (a0 + a1) / 2;
        v.fill(door, 0, jf, door + 1, 2, jf, AIR);
        // 간판 띠 (1층 위)
        Object[] board = ShopHouse.BOARDS[r.nextInt(ShopHouse.BOARDS.length)];
        if (kind == Kind.JEWELRY) {
            board = new Object[]{BLACK_CONCRETE, "dark_oak", "yellow"};
        } else if (kind == Kind.CAFE) {
            board = new Object[]{Block.of("brown_concrete", 0x603C20), "spruce", "white"};
        }
        v.fill(a0 - 1, h - 1, jf, w - 1, h - 1, jf, (Block) board[0]);
        String line2 = kind == Kind.JEWELRY ? "금·은·다이아몬드" : kind == Kind.CAFE ? "커피·디저트" : "";
        v.set((a0 + a1) / 2, h - 1, jf + 1, Blocks.wallSign((String) board[1], "south", (String) board[2], true, "", name[0], line2));
        Interior.floor(f, a0, 1, a1, jf - 1, 0, kind == Kind.CAFE ? Block.of("oak_planks", 0xA2834F) : Block.of("smooth_quartz", 0xECE6DF));
        switch (kind) {
            case JEWELRY -> jewelry(v, f, r, a0, a1, jf, h, door);
            case CAFE -> cafe(v, f, r, a0, a1, 1, jf - 1, 0, h);
            default -> goods(v, f, r, a0, a1, 1, jf - 1, 0, h, name[1]);
        }
        Interior.lights(f, a0, 1, a1, jf - 1, 0, h, 3, Interior.LIGHT);
    }

    /**
     * 보석상: 앞면 방범 셔터(말아 올린 통과 레일), 유리 진열장(금·다이아몬드·에메랄드 위 유리), 계산대,
     * 뒤 사무실과 금고실(두꺼운 벽, 철문, 금고), 천장 감시 카메라.
     */
    private static void jewelry(Voxels v, Frame f, Random r, int a0, int a1, int jf, int h, int door) {
        int top = h - 2;
        // 방범 셔터: 앞면 위 셔터 통과 양옆 레일 (바깥 줄)
        v.fill(a0, top, jf + 1, a1, top, jf + 1, Block.of("light_gray_concrete", 0x7D7D73));
        v.fill(a0 - 1, 0, jf + 1, a0 - 1, top - 1, jf + 1, IRON_BARS);
        v.fill(a1 + 1, 0, jf + 1, a1 + 1, top - 1, jf + 1, IRON_BARS);
        // 진열장: 벽을 따라 ㄷ자 + 가운데 섬 (보석 블록 위 유리)
        Block[] jewels = {GOLD_BLOCK, Block.of("diamond_block", 0x62DBD5), Block.of("emerald_block", 0x2ACB57),
                GOLD_BLOCK, Block.of("iron_block", 0xDCDCDC), Block.of("amethyst_block", 0x8562C5)};
        int back = 5;                                  // 뒤 사무실·금고실 앞벽 줄 (그 앞 한 줄은 직원 통로)
        int c0 = back + 3, c1 = jf - 3;
        int k = 0;
        for (int j = c0; j <= c1; j++) {
            for (int i : new int[]{a0, a1}) {
                v.set(i, 0, j, jewels[k++ % jewels.length]);
                v.set(i, 1, j, Block.of("glass", 0xC8DCE4));
            }
        }
        for (int i = a0; i <= a1; i++) {
            v.set(i, 0, c0 - 1, jewels[k++ % jewels.length]);
            v.set(i, 1, c0 - 1, Block.of("glass", 0xC8DCE4));
        }
        int mi = (a0 + a1) / 2;
        for (int j = c0 + 2; j <= c1 - 1; j++) {
            v.set(mi, 0, j, jewels[k++ % jewels.length]);
            v.set(mi, 1, j, Block.of("glass", 0xC8DCE4));
        }
        // 직원 통로 (뒤쪽 진열장 끝)
        v.set(a1, 0, c0 - 1, AIR);
        v.set(a1, 1, c0 - 1, AIR);
        // 손님 의자
        Furniture.chair(f, a0 + 1, 0, c0 + 1, "west", "dark_oak");
        Furniture.chair(f, a1 - 1, 0, c0 + 1, "east", "dark_oak");
        // 뒤: 사무실(왼쪽)과 금고실(오른쪽)
        Block in = Interior.INNER_WALL;
        Block vault = Block.of("polished_deepslate", 0x484849);
        v.fill(a0, 0, back, a1, top, back, in);
        int vm = (a0 + a1) / 2 + 1;
        v.fill(vm, 0, 1, vm, top, back, vault);
        v.fill(vm, 0, back, a1, top, back, vault);
        Interior.door(f, a0 + 1, 0, back, "dark_oak", "north");
        v.set(a0 + 2, 1, back + 1, Blocks.wallSign("birch", "south", "black", false, "", "사무실"));
        v.set(vm + 1, 0, back, Block.of("iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]", 0xC2C1C1));
        v.set(vm + 1, 1, back, Block.of("iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]", 0xC2C1C1));
        v.set(vm + 2, 1, back + 1, Block.of("stone_button[face=wall,facing=south,powered=false]", 0x7E7E7E));
        // 사무실: 책상, 감시 화면
        v.set(a0 + 1, 0, 2, Furniture.DESK_TOP);
        v.set(a0 + 2, 0, 2, Furniture.DESK_TOP);
        Furniture.chair(f, a0 + 1, 0, 3, "south", "dark_oak");
        v.fill(a0, 1, 1, vm - 1, 2, 1, Block.of("black_concrete", 0x080A0F));
        // 금고실: 금고(쇠 덩어리와 손잡이), 금괴 선반
        for (int i = vm + 1; i <= a1; i++) {
            v.set(i, 0, 1, Block.of("iron_block", 0xDCDCDC));
            v.set(i, 1, 1, Block.of("iron_block", 0xDCDCDC));
        }
        v.set(vm + 1, 1, 2, Block.of("stone_button[face=wall,facing=south,powered=false]", 0x7E7E7E));
        v.set(a1, 0, 3, GOLD_BLOCK);
        v.set(a1, 0, 4, GOLD_BLOCK);
        v.set(a1, 1, 3, Block.of("diamond_block", 0x62DBD5));
        // 감시 카메라 (천장 모서리)
        v.set(a0, top, jf - 1, Block.of("observer[facing=south,powered=false]", 0x6E6E6E));
        v.set(a1, top, c0, Block.of("observer[facing=north,powered=false]", 0x6E6E6E));
        v.set(mi, top, back + 1, Block.of("observer[facing=north,powered=false]", 0x6E6E6E));
    }

    /** 카페: 계산대(커피 기계·케이크 진열), 탁자와 의자, 화분 */
    private static void cafe(Voxels v, Frame f, Random r, int a0, int a1, int b0, int b1, int L, int h) {
        for (int i = a0; i <= a1; i++) {
            v.set(i, L, b0 + 1, i == a0 + 1 ? Block.of("smoker[facing=south,lit=false]", 0x555451)
                    : i == a0 + 3 ? Block.of("cake[bites=0]", 0xEFE4D3) : Furniture.COUNTER);
        }
        v.set(a0 + 2, L + 1, b0 + 1, Block.of("brown_carpet", 0x724728));
        v.set(a1, L, b0 + 1, AIR);
        for (int j = b0 + 4; j + 1 <= b1 - 1; j += 3) {
            for (int i = a0 + 1; i + 1 <= a1 - 1; i += 3) {
                Furniture.table(f, i, L, j, 1, 1, "spruce");
            }
        }
        Furniture.plant(f, r, a1, L, b1);
        Furniture.plant(f, r, a0, L, b1);
    }

    /** 일반 가게 실내 (종류별) */
    private static void goods(Voxels v, Frame f, Random r, int a0, int a1, int b0, int b1, int L, int h, String kind) {
        v.set(a1, L, b1 - 1, Furniture.COUNTER);
        v.set(a1, L, b1 - 2, Furniture.COUNTER);
        switch (kind) {
            case "cosmetics" -> {
                for (int i = a0 + 1; i <= a1 - 2; i += 3) {
                    for (int j = b0 + 1; j <= b1 - 3; j++) {
                        v.set(i, L, j, Furniture.WHITE_TOP);
                        v.set(i, L + 1, j, Block.of(CANDLES[Math.floorMod(i + j, CANDLES.length)] + "_candle[candles=4,lit=false,waterlogged=false]", 0xED8DAC));
                    }
                }
            }
            case "fashion", "souvenir" -> {
                for (int i = a0 + 1; i <= a1 - 2; i += 3) {
                    for (int j = b0 + 1; j <= b1 - 3; j++) {
                        v.set(i, L + 2, j, IRON_BARS);
                        v.set(i, L + 1, j, Blocks.wool(CLOTHES[r.nextInt(CLOTHES.length)]));
                    }
                }
            }
            case "shoes", "optical" -> {
                for (int j = b0; j <= b1 - 3; j++) {
                    for (int i : new int[]{a0, a1}) {
                        v.set(i, L, j, Furniture.WHITE_TOP);
                        v.set(i, L + 1, j, Block.of(r.nextBoolean() ? "black_carpet" : "brown_carpet", 0x724728));
                    }
                }
            }
            case "food" -> {
                for (int j = b0 + 3; j + 1 <= b1 - 2; j += 3) {
                    for (int i = a0 + 1; i + 1 <= a1 - 2; i += 3) {
                        Furniture.table(f, i, L, j, 2, 1, "oak");
                    }
                }
                for (int i = a0; i <= a1; i++) {
                    v.set(i, L, b0, i == a0 + 1 ? Block.of("smoker[facing=south,lit=false]", 0x555451) : Furniture.COUNTER);
                }
            }
            case "exchange", "pharmacy" -> {
                for (int i = a0; i <= a1 - 1; i++) {
                    v.set(i, L, b0 + 3, Furniture.COUNTER);
                    v.set(i, L + 1, b0 + 3, Block.of("glass_pane", 0xC8DCE4));
                }
                v.set(a0 + 1, L + 1, b0 + 3, AIR);
                for (int i = a0; i <= a1; i++) {
                    v.set(i, L, b0, Furniture.BOOKSHELF);
                }
            }
            default -> {
            }
        }
    }

    /** 위층 한 층 (계단실 상자는 피함) */
    private static void upper(Voxels v, Frame f, Random r, int w, int jf, int L, int h, String use, int[] stairBox) {
        int a0 = 1, a1 = w - 2, b0 = 1, b1 = jf - 1;
        Site.floor(v, a0, b0, a1, b1, L - 1, use.equals("karaoke") ? Block.of("gray_concrete", 0x36393D) : Block.of("light_gray_concrete", 0x7D7D73), stairBox);
        int sa = stairBox[2] + 1;
        switch (use) {
            case "cafe" -> cafe(v, f, r, sa, a1, b0, b1, L, h);
            case "food" -> goods(v, f, r, sa, a1, b0, b1, L, h, "food");
            case "fashion" -> goods(v, f, r, sa, a1, b0, b1, L, h, "fashion");
            case "cosmetics" -> goods(v, f, r, sa, a1, b0, b1, L, h, "cosmetics");
            case "clinic", "nail" -> {
                for (int i = sa; i <= a1; i++) {
                    v.set(i, L, b1 - 3, i == sa + 2 ? AIR : Furniture.COUNTER);
                }
                for (int i = sa; i <= a1 - 1; i += 2) {
                    Furniture.chair(f, i, L, b1 - 1, "south", "birch");
                }
                for (int i = sa + 1; i + 1 <= a1; i += 4) {
                    v.set(i, L, b0 + 1, Block.of("white_wool", 0xE9ECEC));
                    v.set(i + 1, L, b0 + 1, Block.of("white_wool", 0xE9ECEC));
                }
            }
            case "karaoke" -> {
                for (int i = sa; i + 3 <= a1; i += 4) {
                    v.fill(i + 3, L, b0, i + 3, L + h - 2, b0 + 4, Interior.INNER_WALL);
                    v.fill(i, L, b0 + 4, i + 3, L + h - 2, b0 + 4, Interior.INNER_WALL);
                    Interior.door(f, i + 1, L, b0 + 4, "dark_oak", "south");
                    Furniture.sofa(f.sub(i, b0, "west"), r, 0, L, 0, 3, "north");
                    v.set(i + 3, L + 1, b0 + 2, Furniture.TV);
                    v.set(i + 1, L + h - 2, b0 + 2, Block.of("shroomlight", 0xF09246));
                }
            }
            default -> Rooms.office(f, r, sa - 1, b0 - 1, a1 + 1, b1 + 1, L, h, (i, j) -> i >= sa && i <= a1 && j >= b0 && j <= b1);
        }
        Site.lights(v, a0, b0, a1, b1, L + h - 2, 3, stairBox);
    }
}
