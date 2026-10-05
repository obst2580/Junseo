package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 비탈 동네(해방촌·후암동·보광동)의 다가구 주택과 동네 가게. 남산 비탈 골목에 다닥다닥 붙어 층층이 올라갑니다.
 * <ul>
 *   <li>붉은 벽돌(가끔 흙벽돌·미색 미장·회색 미장) 2~3층, 층마다 한 세대 원룸 (욕실, 부엌, 침대, 책상, 옷장)</li>
 *   <li>뒤 구석의 좁은 꺾인 계단실(1칸 줄)이 1층 대문에서 옥상까지 이어짐. 계단 앞 복도에 세대 문</li>
 *   <li>옥상: 초록 방수 도장, 벽돌 난간, 옥탑방(초록 지붕, 문·창, 매트리스·책상), 파란 물탱크, 안테나, 화분·텃밭 상자</li>
 *   <li>앞(남쪽, 아래 골목 쪽): 대문과 문패, 1층 방범창, 창턱, 노란 가스관, 실외기, 대문 처마. 맨 앞 한 줄은 대문 앞 자리</li>
 *   <li>뒤(북쪽)는 석축에 붙고, backFloor 층에 윗골목으로 나가는 뒷문 (그 층 바닥이 윗골목 높이). 그 위 층만 뒤 창</li>
 *   <li>shop 이면 1층이 동네 가게 (슈퍼·세탁소·분식·미용실·부동산·철물점·방앗간): 유리 가게 앞, 간판판과 글씨, 차양, 실내</li>
 * </ul>
 * 너비 11~14, 깊이 10~13 (앞 한 줄 포함). 한 층 4칸 (빈 칸 3).
 */
final class HillHouse {
    /** {벽, 밑단, 띠} */
    static final Block[][] SKINS = {
            {BRICKS, POLISHED_ANDESITE, SMOOTH_STONE},
            {BRICKS, STONE_BRICKS, WHITE_CONCRETE},
            {BRICKS, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
            {MUD_BRICKS, POLISHED_ANDESITE, SMOOTH_STONE},
            {Block.of("white_terracotta", 0xD1B2A1), STONE_BRICKS, SMOOTH_STONE},
            {Block.of("light_gray_terracotta", 0x876A61), POLISHED_ANDESITE, SMOOTH_STONE},
    };
    static final Block GREEN_ROOF = Block.of("green_concrete", 0x495B24);
    static final Block COPPER_ROOF = Block.of("dark_prismarine_slab[type=bottom,waterlogged=false]", 0x335B4B);
    static final Block TANK = Block.of("light_blue_concrete", 0x2389C7);
    static final Block GAS = Block.of("yellow_stained_glass_pane", 0xE5E533);
    static final Block AC = Block.of("smooth_quartz", 0xECE6DF);
    static final Block SILL = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);
    static final Block CAP = Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E);
    private static final String[] DOORS = {"spruce", "dark_oak", "spruce", "warped", "mangrove"};

    /** 동네 가게 {간판 이름, 종류} */
    private static final String[][] SHOPS = {
            {"%s슈퍼", "슈퍼"}, {"%s세탁소", "세탁"}, {"%s분식", "분식"}, {"%s미용실", "미용실"}, {"%s부동산", "부동산"},
            {"%s철물", "철물"}, {"%s슈퍼", "슈퍼"}, {"%s방앗간", "방앗간"},
    };

    final Voxels v;
    private final Random r;
    private final int w, d, jf, floors, backFloor, cx0, cx1, cz1, lane, ua, ub, uw;
    private final boolean left;
    private final String[] shop;
    private final String street;
    private final Block wall, base, trim;
    final int[] levels;
    /** 세대 안쪽 {i0, j0, i1, j1, 서는 높이, 1 = 원룸·2 = 투룸} (검사용) */
    final java.util.List<int[]> units = new java.util.ArrayList<>();
    /** 방 {i0, j0, i1, j1, 서는 높이, 1 = 거실·3 = 침실·4 = 욕실} (검사용) */
    final java.util.List<int[]> rooms = new java.util.ArrayList<>();

    private HillHouse(int w, int d, int floors, int backFloor, String[] shop, String street, Random r) {
        this.w = Math.max(12, w);
        this.d = Math.max(11, d);
        this.r = r;
        this.floors = Math.max(1, Math.min(4, floors));
        this.backFloor = backFloor <= this.floors ? backFloor : -1;
        this.shop = shop;
        this.street = street;
        jf = this.d - 2;
        left = r.nextBoolean();
        cx0 = left ? 0 : this.w - 5;
        cx1 = cx0 + 4;
        levels = Floors.levels(Floors.HOME, Floors.HOME, this.floors);
        cz1 = Interior.stairDepth(levels) + 1;
        lane = left ? 3 : this.w - 2;
        ua = left ? cx1 + 1 : 1;
        ub = left ? this.w - 2 : cx0 - 1;
        uw = ub - ua + 1;
        Block[] skin = SKINS[r.nextInt(SKINS.length)];
        wall = skin[0];
        base = skin[1];
        trim = skin[2];
        v = new Voxels(this.w, this.d, -1, 4 * this.floors + 5);
    }

    /**
     * 다가구 한 채. floors 층 (1~4), backFloor 는 윗골목으로 나가는 뒷문 층 (0 = 1층, -1 = 없음).
     * shop 이 null 이 아니면 1층이 가게 ({간판 이름, 종류}). street 는 문패 길 이름 (7자 안).
     */
    static Voxels build(int w, int d, int floors, int backFloor, String[] shop, String street, Random r) {
        return create(w, d, floors, backFloor, shop, street, r).v;
    }

    /** {@link #build} 와 같고, 세대 자리(검사용)도 같이 */
    static HillHouse create(int w, int d, int floors, int backFloor, String[] shop, String street, Random r) {
        HillHouse h = new HillHouse(w, d, floors, backFloor, shop, street, r);
        h.shell();
        h.core();
        for (int k = 0; k < h.floors; k++) {
            if (k == 0 && shop != null) {
                h.shopFloor();
            } else {
                h.unit(k);
            }
        }
        h.roof();
        h.front();
        h.v.connect();
        return h;
    }

    /** 동네 가게 이름 하나 {간판 이름, 종류} */
    static String[] shopName(Random r) {
        String[] s = SHOPS[r.nextInt(SHOPS.length)];
        return new String[]{s[0].formatted(KoreanNames.prefix(r)), s[1]};
    }

    /** 세대 칸 좌표: x 는 계단실 쪽에서 0, 바깥 벽 쪽으로 커짐 */
    private int X(int x) {
        return left ? ua + x : ub - x;
    }

    // ------------------------------------------------------------------ 몸체

    private void shell() {
        int top = 4 * floors;
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("light_gray_concrete", 0x7D7D73));
        for (int k = 0; k <= floors; k++) {
            v.fill(0, 4 * k - 1, 0, w - 1, 4 * k - 1, jf, k == 0 ? POLISHED_ANDESITE : SMOOTH_STONE);
        }
        v.walls(0, 0, 0, w - 1, top - 1, jf, wall);
        v.walls(0, 0, 0, w - 1, 0, jf, base);
        // 미장집은 바닥 높이에 얇은 띠 (벽돌집은 띠 없이)
        if (wall != BRICKS && wall != MUD_BRICKS) {
            for (int k = 1; k < floors; k++) {
                v.walls(0, 4 * k - 1, 0, w - 1, 4 * k - 1, jf, trim);
            }
        }
    }

    private boolean inCore(int i, int j) {
        return i >= cx0 && i <= cx1 && j <= cz1;
    }

    private void core() {
        Interior.stairCore(Frame.facing(v, lane, cz1 - 1, "north"), levels, wall, "stone_brick", 0x7A7979, 1);
        int top = 4 * floors;
        v.fill(cx0, top + 3, 0, cx1, top + 3, cz1, SMOOTH_STONE);
        // 계단실 옆벽 작은 창 (꺾이는 참 높이)
        int side = left ? 0 : w - 1;
        for (int k = 0; k < floors; k++) {
            v.set(side, 4 * k + 3, cz1 - 2, GLASS_PANE);
        }
        // 계단실 안쪽 벽은 회색 미장
        int inner = left ? cx1 : cx0;
        for (int y = 0; y < top; y++) {
            for (int j = 1; j < cz1; j++) {
                Block b = v.get(inner, y, j);
                if (b != null && b.equals(wall)) {
                    v.set(inner, y, j, Interior.CORE_WALL);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 집 한 층

    /** 계단 앞 복도(현관)와 세대 문. 복도는 계단실 앞 줄 (j = cz1+1 .. jf-1) */
    private void hall(int L, boolean unitDoor) {
        int top = L + 2;
        int wi = left ? cx1 : cx0;
        v.fill(wi, L, cz1 + 1, wi, top, jf - 1, Interior.INNER_WALL);
        if (unitDoor) {
            Interior.door(Frame.of(v), wi, L, cz1 + 1, "pale_oak", left ? "west" : "east");
        }
        Interior.floor(Frame.of(v), cx0 + 1, cz1 + 1, cx1 - 1, jf - 1, L, Interior.TILE);
        v.set(lane, top, jf - 1, Interior.LIGHT);
        // 복도 앞 창 (계단 앞)
        if (L > 0) {
            v.set(left ? cx0 + 1 : cx1 - 1, L + 1, jf, GLASS_PANE);
            v.set(left ? cx0 + 1 : cx1 - 1, L + 2, jf, GLASS_PANE);
        }
    }

    /** 세대 하나: 넓으면 투룸 (안쪽 9×10 이상), 아니면 원룸 (6×8 이상, 욕실 따로) */
    private void unit(int k) {
        int L = 4 * k;
        hall(L, true);
        Interior.floor(Frame.of(v), ua, 1, ub, jf - 1, L, r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        boolean twoRoom = uw >= 10 && jf - 1 >= 10;
        units.add(new int[]{Math.min(X(0), X(uw - 1)), 1, Math.max(X(0), X(uw - 1)), jf - 1, L, twoRoom ? 2 : 1});
        bath(L, 1);
        if (twoRoom) {
            twoRoom(L, k == backFloor);
        } else {
            oneRoom(L, 1, jf - 1, uw, k == backFloor);
        }
        // 창: 앞 (거실·방), 바깥 옆벽, 뒤 (윗골목보다 높은 층만)
        window(Math.min(X(1), X(3)), L, jf, 3);
        int side = left ? w - 1 : 0;
        v.set(side, L + 1, jf - 4, GLASS_PANE);
        v.set(side, L + 2, jf - 4, GLASS_PANE);
        v.set(side, L + 1, 2, GLASS_PANE);
        v.set(side, L + 2, 2, GLASS_PANE);
        if (backFloor >= 0 && k > backFloor) {
            for (int x = 3; x < uw - 1; x += 3) {
                v.set(X(x), L + 1, 0, GLASS_PANE);
                v.set(X(x), L + 2, 0, GLASS_PANE);
            }
        }
    }

    /** 욕실: 계단실 쪽 뒤 구석 안쪽 2×3 (변기·세면대·샤워), 문은 앞쪽. 기준 j0 은 뒤쪽 첫 줄 */
    private void bath(int L, int j0) {
        int top = L + 2;
        Frame f = Frame.of(v);
        v.fill(X(2), L, j0, X(2), top, j0 + 3, Interior.INNER_WALL);
        v.fill(Math.min(X(0), X(2)), L, j0 + 3, Math.max(X(0), X(2)), top, j0 + 3, Interior.INNER_WALL);
        Interior.door(f, X(0), L, j0 + 3, "pale_oak", "south");
        Interior.floor(f, Math.min(X(0), X(1)), j0, Math.max(X(0), X(1)), j0 + 2, L, Interior.TILE);
        v.set(X(0), L, j0, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
        v.set(X(1), L, j0 + 2, CAULDRON); // 세면대 (문 옆), 안쪽 X(1) 은 샤워 칸
        v.set(X(1), L + 1, j0 + 2, Block.of("tripwire_hook[attached=false,facing=" + (left ? "west" : "east") + ",powered=false]", 0x8F8F8F));
        v.set(X(1), top, j0, Block.of("lightning_rod[facing=down,powered=false,waterlogged=false]", 0xC57A55)); // 샤워기
        v.set(X(1), L - 1, j0, Block.of("light_blue_terracotta", 0x716C89));
        v.set(X(0), top, j0 + 1, Interior.LIGHT);
        rooms.add(new int[]{Math.min(X(0), X(1)), j0, Math.max(X(0), X(1)), j0 + 2, L, 4});
    }

    /**
     * 원룸 (안쪽 uw×(jb-ja+1)): 뒤 구석 욕실 (bath 가 이미 그림), 바깥 벽을 따라 부엌, 창가 침대, 책상, 옷장, 밥상.
     * backDoor 면 욕실 벽 옆 뒷벽에 윗골목으로 나가는 문.
     */
    private void oneRoom(int L, int ja, int jb, int uw, boolean backDoor) {
        int top = L + 2;
        Frame f = Frame.of(v);
        if (backDoor) {
            Interior.door(f, X(3), L, ja - 1, DOORS[r.nextInt(DOORS.length)], "north");
            Interior.floor(f, X(3), ja, X(3), ja, L, Interior.TILE);
        }
        kitchen(L, ja, uw - 1);
        Furniture.bed(f, r, X(uw - 1), L, jb - 1, "south");
        Furniture.desk(f, X(uw - 3), L, jb, "north");
        v.set(X(0), L, ja + 5, Rooms.WARDROBE);
        v.set(X(0), L + 1, ja + 5, Rooms.WARDROBE);
        if (uw >= 7) {
            v.set(X(3), L, ja + 5, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430)); // 밥상
        }
        v.set(X(uw / 2), top, (ja + jb) / 2 + 1, Interior.LIGHT);
    }

    /** 부엌 (바깥 벽 x 줄, j0 부터 넷): 냉장고·싱크·가스레인지·조리대, 위 수납장 */
    private void kitchen(int L, int j0, int x) {
        String face = left ? "west" : "east";
        v.set(X(x), L, j0, Furniture.FRIDGE);
        v.set(X(x), L + 1, j0, Furniture.FRIDGE);
        v.set(X(x), L, j0 + 1, CAULDRON);
        v.set(X(x), L, j0 + 2, Block.of("smoker[facing=" + face + ",lit=false]", 0x555451));
        v.set(X(x), L, j0 + 3, Furniture.COUNTER);
        for (int j = j0 + 1; j <= j0 + 3; j++) {
            v.set(X(x), L + 2, j, Furniture.COUNTER);
        }
    }

    /**
     * 투룸 (안쪽 uw ≥ 10 × 10 이상): 뒤쪽에 욕실·작은방·안방 (각 3×4 이상), 앞쪽에 현관(2×2)·거실(소파·TV)·주방(바깥 벽).
     */
    private void twoRoom(int L, boolean backDoor) {
        int top = L + 2;
        Frame f = Frame.of(v);
        int mid = 2 + (uw - 2) / 2;
        rooms.add(new int[]{Math.min(X(3), X(mid - 1)), 1, Math.max(X(3), X(mid - 1)), 4, L, 3});
        rooms.add(new int[]{Math.min(X(mid + 1), X(uw - 1)), 1, Math.max(X(mid + 1), X(uw - 1)), 4, L, 3});
        rooms.add(new int[]{Math.min(X(0), X(uw - 2)), 6, Math.max(X(0), X(uw - 2)), jf - 1, L, 1});
        // 뒤쪽 방 둘 (j 1..4), 앞벽 j = 5
        v.fill(Math.min(X(2), X(uw - 1)), L, 5, Math.max(X(2), X(uw - 1)), top, 5, Interior.INNER_WALL);
        v.fill(X(mid), L, 1, X(mid), top, 4, Interior.INNER_WALL);
        Interior.door(f, X(4), L, 5, "pale_oak", "south");
        Interior.door(f, X(mid + 2), L, 5, "pale_oak", "south");
        if (backDoor) {
            // 작은방 구석에 윗골목으로 나가는 뒷문
            Interior.door(f, X(3), L, 0, DOORS[r.nextInt(DOORS.length)], "north");
            Furniture.bed(f, r, X(mid - 1), L, 1, "south");
        } else {
            Furniture.bed(f, r, X(3), L, 1, "south");
            v.set(X(mid - 1), L, 1, Rooms.WARDROBE);
            v.set(X(mid - 1), L + 1, 1, Rooms.WARDROBE);
        }
        v.set(X((3 + mid - 1) / 2), top, 3, Interior.LIGHT);
        Furniture.bed(f, r, X(uw - 1), L, 1, "south");
        Furniture.desk(f, X(mid + 1), L, 1, "south");
        v.set(X((mid + 1 + uw - 1) / 2), top, 3, Interior.LIGHT);
        // 앞쪽: 현관, 주방 (바깥 벽), 거실
        Interior.floor(f, Math.min(X(0), X(1)), cz1 + 1, Math.max(X(0), X(1)), cz1 + 2, L, Interior.TILE);
        kitchen(L, 6, uw - 1);
        int ty = jf - 1;
        if (uw >= 11) {
            Furniture.table(f, Math.min(X(uw - 4), X(uw - 3)), L, 8, 2, 1, "oak");
        }
        String sofa = Furniture.SOFA[r.nextInt(Furniture.SOFA.length)];
        for (int x = 2; x <= 4; x++) {
            v.set(X(x), L, ty, Block.of(sofa + "_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", Furniture.rgb(sofa)));
        }
        v.set(X(3), L, ty - 3, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        v.set(X(3), L + 1, ty - 3, Furniture.TV);
        v.set(X(3), L, ty - 2, Block.of("gray_carpet", 0x3E4447));
        Furniture.plant(f, r, X(5), L, ty);
        v.set(X(3), top, ty - 1, Interior.LIGHT);
        v.set(X(uw - 2), top, 8, Interior.LIGHT);
    }

    /** 창 (i..i+n-1, 높이 L+1..L+2, 벽 j). 앞벽 위층이면 바깥에 창턱 */
    private void window(int i, int L, int j, int n) {
        for (int a = i; a < i + n; a++) {
            if (a <= 0 || a >= w - 1) {
                continue;
            }
            v.set(a, L + 1, j, GLASS_PANE);
            v.set(a, L + 2, j, GLASS_PANE);
            if (j == jf && L > 0) {
                v.set(a, L, j + 1, SILL);
            }
        }
    }

    // ------------------------------------------------------------------ 1층 가게

    private void shopFloor() {
        Frame f = Frame.of(v);
        hall(0, false);
        String kind = shop[1];
        Interior.floor(f, ua, 1, ub, jf - 1, 0, switch (kind) {
            case "미용실" -> Block.of("white_concrete", 0xCFD5D6);
            case "슈퍼", "철물" -> Block.of("light_gray_concrete", 0x7D7D73);
            default -> POLISHED_ANDESITE;
        });
        // 유리 가게 앞: 어두운 틀, 나무 문
        for (int i = ua; i <= ub; i++) {
            v.set(i, 0, jf, POLISHED_DEEPSLATE);
            v.set(i, 1, jf, GLASS_PANE);
            v.set(i, 2, jf, GLASS_PANE);
        }
        int door = X(1);
        Interior.door(f, door, 0, jf, "spruce", "south");
        // 간판판 (2층 바닥 높이 띠) + 글씨, 차양
        Object[] board = ShopHouse.BOARDS[r.nextInt(ShopHouse.BOARDS.length)];
        v.fill(ua, 3, jf, ub, 3, jf, (Block) board[0]);
        v.set((ua + ub) / 2, 3, jf + 1, Blocks.wallSign((String) board[1], "south", (String) board[2], true, "", shop[0]));
        String awning = r.nextBoolean() ? "spruce" : "dark_oak";
        for (int i = ua; i <= ub; i++) {
            if (i != door) {
                v.set(i, 2, jf + 1, Block.of(awning + "_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", 0x725430));
            }
        }
        // 실내: 바깥 벽 쪽 계산대, 업종별 진열
        int back = 1, front = jf - 1;
        int cnt = X(uw - 1);
        v.set(cnt, 0, front - 1, Furniture.COUNTER);
        v.set(cnt, 0, front - 2, Furniture.COUNTER);
        switch (kind) {
            case "슈퍼", "철물", "방앗간" -> {
                for (int x = 0; x < uw - 1; x += 2) {
                    for (int j = back; j <= front - 3; j++) {
                        v.set(X(x), 0, j, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
                        v.set(X(x), 1, j, kind.equals("철물") ? (j % 2 == 0 ? BARREL : ANVIL)
                                : kind.equals("방앗간") ? (j % 2 == 0 ? BARREL : HAY) : (j % 2 == 0 ? BARREL : Furniture.BOOKSHELF));
                    }
                }
                if (kind.equals("슈퍼")) {
                    v.fill(cnt, 0, back, cnt, 1, back + 1, Furniture.FRIDGE);
                }
            }
            case "세탁" -> {
                // 옷걸이 줄 (쇠사슬에 걸린 옷)
                for (int x = 0; x < uw - 1; x++) {
                    v.set(X(x), 2, back + 1, Block.of("iron_chain[axis=x,waterlogged=false]", 0x505050));
                    v.set(X(x), 1, back + 1, Blocks.wool(r.nextBoolean() ? "white" : r.nextBoolean() ? "gray" : "light_blue"));
                }
                v.set(cnt, 0, back + 3, Block.of("smoker[facing=south,lit=false]", 0x555451));
            }
            case "미용실" -> {
                for (int j = back + 1; j <= front - 2; j += 2) {
                    Furniture.chair(f, X(1), 0, j, left ? "west" : "east", "dark_oak");
                    v.set(X(0), 1, j, Block.of("light_blue_stained_glass", 0x6699D8));
                }
            }
            case "분식" -> {
                for (int j = back + 2; j <= front - 2; j += 3) {
                    Furniture.table(f, X(1), 0, j, 1, 1, "oak");
                }
                for (int x = 0; x < uw; x++) {
                    v.set(X(x), 0, back, x == 1 ? SMOKER : Furniture.COUNTER);
                }
            }
            default -> {
                Furniture.desk(f, X(uw / 2), 0, back + 2, "south");
                v.set(X(0), 0, back, Furniture.BOOKSHELF);
                v.set(X(0), 1, back, Furniture.BOOKSHELF);
            }
        }
        for (int j = back + 1; j <= front; j += 3) {
            if (v.get(X(uw / 2), 2, j) == null) {
                v.set(X(uw / 2), 2, j, Interior.LIGHT);
            }
        }
        if (backFloor == 0) {
            Interior.door(f, X(uw - 1), 0, 0, "spruce", "north");
            v.set(X(uw - 1), 0, back, AIR);
            v.set(X(uw - 1), 1, back, AIR);
        }
    }

    // ------------------------------------------------------------------ 옥상

    private void roof() {
        int top = 4 * floors;
        for (int j = 1; j < jf; j++) {
            for (int i = 1; i < w - 1; i++) {
                if (!inCore(i, j)) {
                    v.set(i, top - 1, j, GREEN_ROOF);
                }
            }
        }
        // 난간 (벽 재료 + 위 판)
        for (int i = 0; i < w; i++) {
            for (int j = 0; j <= jf; j++) {
                boolean edge = i == 0 || i == w - 1 || j == 0 || j == jf;
                if (edge && !inCore(i, j)) {
                    v.set(i, top, j, wall);
                    v.set(i, top + 1, j, CAP);
                }
            }
        }
        // 옥탑: 넓은 집은 사람이 사는 옥탑방 (안쪽 6×8, 욕실·부엌·침대), 좁은 집은 창고 (세탁기·선반)
        boolean home = uw >= 10 && jf - 1 >= 10;
        int ow = home ? 6 : 3, od = home ? 8 : 3;
        int oa = Math.min(X(-1), X(ow)), ob = Math.max(X(-1), X(ow));
        int oj = od + 1;
        Block hut = r.nextInt(3) == 0 ? wall : Block.of("white_concrete", 0xCFD5D6);
        for (int j = 0; j <= oj; j++) {
            for (int i = oa; i <= ob; i++) {
                if (inCore(i, j)) {
                    continue;
                }
                boolean edge = i == oa || i == ob || j == 0 || j == oj;
                for (int y = top; y <= top + 2; y++) {
                    v.set(i, y, j, edge ? hut : AIR);
                }
            }
        }
        v.fill(oa, top + 3, 0, ob, top + 3, oj + 1, COPPER_ROOF);
        int door = X(1);
        Interior.door(Frame.of(v), door, top, oj, "spruce", "south");
        v.set(X(3), top + 1, oj, GLASS_PANE);
        v.set(X(ow), top + 1, 2, GLASS_PANE);
        v.set(door, top + 2, oj + 1, LANTERN_HANGING);
        if (home) {
            Interior.floor(Frame.of(v), Math.min(X(0), X(ow - 1)), 1, Math.max(X(0), X(ow - 1)), od, top, Rooms.MARU_LIGHT);
            units.add(new int[]{Math.min(X(0), X(ow - 1)), 1, Math.max(X(0), X(ow - 1)), od, top, 1});
            bath(top, 1);
            oneRoom(top, 1, od, ow, false);
            v.set(X(ow), top + 1, od - 2, GLASS_PANE);
        } else {
            v.set(X(1), top, 1, Block.of("smoker[facing=south,lit=false]", 0x555451)); // 세탁기 자리
            v.set(X(2), top, 1, BARREL);
            v.set(X(1), top + 2, 2, Interior.LIGHT);
        }
        // 윗골목이 옥상 높이면 뒤 난간을 터서 골목에서 옥상으로 바로 드나듦
        if (backFloor == floors) {
            int gx = home ? ow + 2 : 5;
            for (int x = gx; x <= gx + 1 && x <= uw - 2; x++) {
                v.set(X(x), top, 0, AIR);
                v.set(X(x), top + 1, 0, AIR);
                v.set(X(x), top - 1, 0, GREEN_ROOF);
            }
        }
        // 파란 물탱크 (앞 바깥 모서리), 안테나, 화분·텃밭 상자
        int ti = Math.min(X(uw - 1), X(uw - 2));
        v.fill(ti, top, jf - 2, ti + 1, top + 1, jf - 1, TANK);
        v.fill(lane, top + 4, 1, lane, top + 5, 1, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
        for (int x = 1; x <= Math.min(3, uw - 3); x++) {
            if (r.nextInt(3) > 0 && v.get(X(x), top, jf - 1) == null) {
                v.set(X(x), top, jf - 1, x == 2 ? Block.of("composter[level=7]", 0x86643B) : Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
            }
        }
    }

    // ------------------------------------------------------------------ 앞 (대문·문패·가스관·실외기)

    private void front() {
        // 공동현관: 계단 앞 복도로 들어가는 나무 대문, 위 작은 처마, 문패
        int di = left ? cx0 + 2 : cx1 - 2;
        Interior.door(Frame.of(v), di, 0, jf, DOORS[r.nextInt(DOORS.length)], "south");
        v.fill(di - 1, 3, jf + 1, di + 1, 3, jf + 1, SILL);
        int si = left ? di - 1 : di + 1;
        if (v.get(si, 1, jf + 1) == null) {
            v.set(si, 1, jf + 1, Blocks.wallSign("dark_oak", "south", "white", false, street, KoreanNames.address(r)));
        }
        // 노란 가스관 (바깥 앞 모서리를 따라 옥상까지)
        int gi = left ? w - 1 : 0;
        for (int y = 0; y <= 4 * floors - 2; y++) {
            if (v.get(gi, y, jf + 1) == null) {
                v.set(gi, y, jf + 1, GAS);
            }
        }
        // 실외기 (층마다 가끔)
        for (int k = 1; k < floors; k++) {
            int ai = X(Math.min(uw - 2, 3));
            if (r.nextInt(10) < 6 && v.get(ai, 4 * k, jf + 1) == null) {
                v.set(ai, 4 * k, jf + 1, AC);
            }
        }
        // 1층 창 방범창
        if (shop == null) {
            for (int i = 1; i < w - 1; i++) {
                for (int y = 1; y <= 2; y++) {
                    Block b = v.get(i, y, jf);
                    if (b != null && b.id().equals("minecraft:glass_pane") && v.get(i, y, jf + 1) == null) {
                        v.set(i, y, jf + 1, IRON_BARS);
                    }
                }
            }
        }
        // 대문 옆 화분
        int pi = left ? di + 1 : di - 1;
        if (r.nextBoolean() && v.get(pi, 0, jf + 1) == null) {
            v.set(pi, 0, jf + 1, Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
        }
    }
}
