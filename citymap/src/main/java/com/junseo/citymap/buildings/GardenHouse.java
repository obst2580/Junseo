package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 평창동 단독주택 한 필지: 높은 화강석 담과 기와 지붕 대문, (길가 필지는) 담에 붙은 차고, 잔디 정원(소나무·철쭉·석등·디딤돌),
 * 뒤쪽에 2층 큰 집.
 * <ul>
 *   <li>옛 저택: 붉은 벽돌이나 미색 미장, 화강석 밑단, 짙은 기와 모임지붕 (1980년대 평창동)</li>
 *   <li>새 저택: 흰 벽과 큰 유리, 평지붕 옥상 데크와 유리 난간 (계단으로 옥상까지)</li>
 *   <li>1층: 현관, 거실(소파·TV·탁자), 식당(4인 식탁), 부엌(뒤 벽), 손님방, 욕실. 뒤 구석 꺾인 계단 (1칸 줄)</li>
 *   <li>2층: 복도, 안방(큰 침대·옷장·안방 욕실), 침실 둘(뒤), 침실 하나(앞), 공용 욕실</li>
 * </ul>
 * 정면(남쪽, j = d-1)이 아래 길 쪽, 맨 앞 한 줄은 담 밖 보도. 너비 22~28, 깊이 23~28 (차고가 있으면 26 이상).
 * 집 바닥 18×15 (실거주 크기: 1층 거실 10×6, 손님방 4×5, 2층 안방 6×5 + 안방 욕실, 침실 셋, 공용 욕실).
 */
final class GardenHouse {
    static final Block GRANITE = Block.of("polished_diorite", 0xC0C0C1);
    static final Block GRANITE_ROUGH = Block.of("diorite", 0xBCBCBC);
    static final Block GRANITE_CAP = Block.of("polished_diorite_slab[type=bottom,waterlogged=false]", 0xC0C0C1);
    static final Block TILE = Block.of("deepslate_tiles", 0x363637);
    static final Block TILE_SLAB = Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637);
    static final Block PAVER = Block.of("smooth_stone", 0x9E9E9E);
    private static final Block DARK_PANE = Block.of("gray_stained_glass_pane", 0x4C4C4C);
    private static final Block WHITE_PANE = Block.of("white_stained_glass_pane", 0xF0F0F0);

    final Voxels v;
    private final Random r;
    final int w, d, hx0, hx1, hj0, hj1, cx0, cx1, cz1, lane, top;
    private final boolean modern, garage;
    private final Block wall, base;
    final int[] levels;
    /** 방 {i0, j0, i1, j1, 서는 높이, 종류 (ROOM_*)} (검사용) */
    final java.util.List<int[]> rooms = new java.util.ArrayList<>();
    static final int LIVING = 1, MASTER = 2, BED = 3, BATH = 4, GUEST = 5, KITCHEN = 6;

    private GardenHouse(int w, int d, boolean garage, Random r) {
        this.w = Math.max(22, w);
        this.d = Math.max(garage ? 26 : 23, d);
        this.r = r;
        this.garage = garage;
        modern = r.nextInt(5) < 2;
        hx0 = 2;
        hx1 = hx0 + 17;
        hj0 = 1;
        hj1 = hj0 + 14;
        levels = modern ? Floors.levels(Floors.HOME, Floors.HOME, 2) : new int[]{0, 4};
        cx0 = hx0;
        cx1 = cx0 + 4;
        cz1 = hj0 + Interior.stairDepth(levels) + 1;
        lane = cx0 + 3;
        top = 8;
        if (modern) {
            wall = WHITE_CONCRETE;
            base = Block.of("polished_deepslate", 0x484849);
        } else {
            wall = r.nextBoolean() ? BRICKS : Block.of("white_terracotta", 0xD1B2A1);
            base = GRANITE;
        }
        v = new Voxels(this.w, this.d, -1, top + 8);
    }

    /** 필지 하나. garage 면 앞 담에 차고 (차 한 대, 앞 길로 바로 나감) */
    static Voxels build(int w, int d, boolean garage, Random r) {
        return create(w, d, garage, r).v;
    }

    static GardenHouse create(int w, int d, boolean garage, Random r) {
        GardenHouse g = new GardenHouse(w, d, garage, r);
        g.garden();
        g.house();
        g.firstFloor();
        g.secondFloor();
        if (g.modern) {
            g.roofDeck();
        } else {
            g.hipRoof();
        }
        g.walls();
        g.v.connect();
        return g;
    }

    // ------------------------------------------------------------------ 정원

    private void garden() {
        int fw = d - 2; // 앞 담 줄 (그 앞 한 줄은 길가 보도)
        v.fill(0, -1, 0, w - 1, -1, fw - 1, GRASS);
        v.fill(0, -1, fw, w - 1, -1, d - 1, Block.of("light_gray_concrete", 0x7D7D73));
        // 대문에서 현관까지 디딤돌 길
        int gx = gateI();
        int door = (hx0 + hx1) / 2;
        for (int j = hj1 + 1; j < fw; j++) {
            int i = j < hj1 + 3 ? door : gx + (door - gx) * (fw - 1 - j) / Math.max(1, fw - 1 - (hj1 + 3));
            v.set(i, -1, j, PAVER);
            v.set(i + 1, -1, j, PAVER);
        }
        // 집 둘레 자갈 띠
        for (int j = hj0 - 1; j <= hj1 + 1; j++) {
            for (int i = hx0 - 1; i <= hx1 + 1; i++) {
                if (i >= 0 && j >= 0 && (i < hx0 || i > hx1 || j < hj0 || j > hj1)) {
                    v.set(i, -1, j, Block.of("gravel", 0x837F7E));
                }
            }
        }
        // 소나무 (길에서 먼 구석), 철쭉 덤불, 석등
        int gj0 = hj1 + 2, gj1 = fw - 2;
        int pineI = garage ? 2 : gx < w / 2 ? w - 4 : 3;
        if (gj1 - gj0 >= 2) {
            Kit.pine(v, pineI, garage ? gj0 + 1 : (gj0 + gj1) / 2, -1, r);
        }
        for (int k = 0; k < 5; k++) {
            int i = 1 + r.nextInt(w - 2), j = gj0 + r.nextInt(Math.max(1, gj1 - gj0 + 1));
            if (v.get(i, 0, j) == null && GRASS.equals(v.get(i, -1, j)) && Math.abs(i - door) > 2 && Math.abs(i - gx) > 2) {
                v.set(i, 0, j, Block.of(r.nextBoolean() ? "flowering_azalea" : "azalea", 0x63753A));
            }
        }
        int li = door + 3;
        if (v.get(li, 0, hj1 + 3) == null) {
            v.set(li, 0, hj1 + 3, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
            v.set(li, 1, hj1 + 3, LANTERN);
        }
    }

    /** 대문 자리 (2칸, 왼쪽 칸 i) */
    private int gateI() {
        return garage ? 4 : w / 2 - 1;
    }

    // ------------------------------------------------------------------ 담·대문·차고

    private void walls() {
        int gi = gateI(), fw = d - 2;
        // 앞 담 (3칸) + 위 판, 옆 담 (2칸), 뒤 담 (2칸)
        for (int i = 0; i < w; i++) {
            v.fill(i, 0, fw, i, 2, fw, (i * 7 + d) % 5 == 0 ? GRANITE_ROUGH : GRANITE);
            v.set(i, 3, fw, GRANITE_CAP);
        }
        for (int j = 0; j < fw; j++) {
            for (int i : new int[]{0, w - 1}) {
                v.fill(i, 0, j, i, 1, j, GRANITE);
                v.set(i, 2, j, GRANITE_CAP);
            }
        }
        v.fill(1, 0, 0, w - 2, 1, 0, GRANITE);
        v.fill(1, 2, 0, w - 2, 2, 0, GRANITE_CAP);
        // 대문: 두 짝 나무 문, 양쪽 돌기둥, 위 기와 지붕
        Kit.doubleDoor(Frame.of(v), gi, 0, fw, "dark_oak", "south");
        v.set(gi, 2, fw, GRANITE);
        v.set(gi + 1, 2, fw, GRANITE);
        v.fill(gi - 1, 0, fw, gi - 1, 3, fw, GRANITE_ROUGH);
        v.fill(gi + 2, 0, fw, gi + 2, 3, fw, GRANITE_ROUGH);
        v.fill(gi - 1, 4, fw - 1, gi + 2, 4, fw + 1, TILE_SLAB);
        v.set(gi - 2, 4, fw, Block.of("deepslate_tile_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0x363637));
        v.set(gi + 3, 4, fw, Block.of("deepslate_tile_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", 0x363637));
        // 문패 (대문 옆 담, 길 쪽)
        v.set(gi + 3, 1, fw + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", KoreanNames.address(r)));
        v.fill(gi, -1, fw - 1, gi + 1, -1, fw - 1, PAVER);
        if (garage) {
            garageBox();
        }
    }

    /** 차고: 앞 담에 붙은 한 칸 (안쪽 5×6, 3칸 높이), 문은 열어 둔 셔터 자리 (차가 앞 보도로 바로 나감) */
    private void garageBox() {
        int fw = d - 2;
        int a0 = w - 8, a1 = w - 2, b0 = fw - 7, b1 = fw;
        v.walls(a0, 0, b0, a1, 2, b1, GRANITE);
        v.fill(a0 + 1, 0, b0 + 1, a1 - 1, 2, b1 - 1, AIR);
        v.fill(a0 + 1, -1, b0 + 1, a1 - 1, -1, d - 1, GRAY_CONCRETE);
        v.fill(a0, 3, b0, a1, 3, b1, GRANITE_CAP.with("type=top,waterlogged=false"));
        v.fill(a0 + 1, 0, b1, a1 - 1, 2, b1, AIR);
        // 셔터 상자 (문 위)
        v.fill(a0 + 1, 3, b1, a1 - 1, 3, b1, Block.of("light_gray_concrete", 0x7D7D73));
        v.set((a0 + a1) / 2, 2, b0 + 1, LANTERN_HANGING);
        v.carSpot((a0 + a1) / 2.0 + 0.5, 0, (b0 + 1 + b1) / 2.0, 0, 1);
        // 차고 옆 정원 쪽 문
        Interior.door(Frame.of(v), a0, 0, b0 + 2, "spruce", "west");
    }

    // ------------------------------------------------------------------ 집

    private boolean inCore(int i, int j) {
        return i >= cx0 && i <= cx1 && j >= hj0 && j <= cz1;
    }

    private void house() {
        int roofLevel = modern ? 8 : 4;
        int wallTop = modern ? 7 : 7;
        for (int k = 0; k < levels.length; k++) {
            v.fill(hx0, levels[k] - 1, hj0, hx1, levels[k] - 1, hj1, k == 0 ? POLISHED_ANDESITE : SMOOTH_STONE);
        }
        v.fill(hx0, 7, hj0, hx1, 7, hj1, SMOOTH_STONE);
        v.walls(hx0, 0, hj0, hx1, wallTop - 1, hj1, wall);
        v.walls(hx0, 0, hj0, hx1, 0, hj1, base);
        if (!modern) {
            v.walls(hx0, 3, hj0, hx1, 3, hj1, Block.of("smooth_stone", 0x9E9E9E));
        }
        Interior.stairCore(Frame.facing(v, lane, cz1 - 1, "north"), levels, wall, "polished_andesite", 0x848685, 1);
        if (modern) {
            v.fill(cx0, roofLevel + 3, hj0, cx1, roofLevel + 3, cz1, SMOOTH_STONE);
        }
        // 앞 큰 창 (1층 거실 통창, 2층 침실 창)
        for (int i = hx0 + 1; i < hx1; i++) {
            if (i >= cx0 && i <= cx1) {
                continue;
            }
            int bay = (i - hx0) % 4;
            for (int y = 1; y <= 2; y++) {
                if (bay != 0) {
                    v.set(i, y, hj1, modern ? GLASS_PANE : GLASS_PANE);
                }
            }
            if (bay == 1 || bay == 2) {
                v.set(i, 5, hj1, modern ? DARK_PANE : GLASS_PANE);
                v.set(i, 6, hj1, modern ? DARK_PANE : GLASS_PANE);
            }
        }
        // 옆·뒤 창
        for (int j = hj0 + 2; j < hj1 - 1; j += 4) {
            if (!inCore(hx1, j)) {
                v.set(hx1, 1, j, GLASS_PANE);
                v.set(hx1, 2, j, GLASS_PANE);
                v.set(hx1, 5, j, GLASS_PANE);
                v.set(hx1, 6, j, GLASS_PANE);
            }
        }
        for (int i = cx1 + 3; i < hx1 - 1; i += 4) {
            v.set(i, 5, hj0, GLASS_PANE);
        }
        // 현관: 가운데 나무 문, 문 위 차양, 문 앞 디딤 판
        int door = (hx0 + hx1) / 2;
        Interior.door(Frame.of(v), door, 0, hj1, modern ? "dark_oak" : "spruce", "south");
        v.set(door - 1, 1, hj1, wall);
        v.set(door - 1, 2, hj1, wall);
        v.set(door + 1, 1, hj1, wall);
        v.set(door + 1, 2, hj1, wall);
        v.fill(door - 1, 3, hj1 + 1, door + 1, 3, hj1 + 1, modern ? Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E) : TILE_SLAB.with("type=top,waterlogged=false"));
        // 2층 앞 작은 발코니 (옛 저택)
        if (!modern) {
            int bi = Math.min(hx1 - 3, door + 3);
            v.fill(bi, 3, hj1 + 1, bi + 2, 3, hj1 + 1, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
            v.fill(bi, 4, hj1 + 1, bi + 2, 4, hj1 + 1, Block.of("iron_bars", 0x888888));
        }
        // 현관 등
        v.set(door + 1, 2, hj1 + 1, LANTERN_HANGING);
    }

    /** 바닥 마감 (계단실은 그대로) */
    private void finish(int L, Block b) {
        for (int j = hj0 + 1; j < hj1; j++) {
            for (int i = hx0 + 1; i < hx1; i++) {
                if (!inCore(i, j)) {
                    v.set(i, L - 1, j, b);
                }
            }
        }
    }

    /** 방 하나: 둘레 벽은 이미 있음, 바닥·등, 검사용 기록 */
    private void room(int i0, int j0, int i1, int j1, int L, int kind, Block floor) {
        Interior.floor(Frame.of(v), i0, j0, i1, j1, L, floor);
        rooms.add(new int[]{i0, j0, i1, j1, L, kind});
        v.set((i0 + i1) / 2, L + 2, (j0 + j1) / 2, Interior.LIGHT);
    }

    /**
     * 욕실 (안쪽 i0..i1 × j0..j1, 너비 2): 문은 앞쪽 (j1 + 1) 의 i0 줄. 깊이 4 이상이면 뒤에 욕조, 아니면 샤워 칸.
     * i0 줄은 비워서 변기·세면대에 다가갈 수 있게.
     */
    private void bathroom(int i0, int j0, int i1, int j1, int L) {
        room(i0, j0, i1, j1, L, BATH, Interior.TILE);
        Block toilet = Block.of("quartz_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE);
        if (j1 - j0 >= 3) {
            v.fill(i0, L, j0, i1, L, j0, Block.of("white_terracotta", 0xD1B2A1)); // 욕조
            v.set(i1, L, j0 + 1, toilet);
            v.set(i1, L, j0 + 2, CAULDRON);
        } else {
            v.set(i1, L, j0, toilet);
            v.set(i1, L, j0 + 1, CAULDRON);
            v.set(i0, L + 2, j0, Block.of("lightning_rod[facing=down,powered=false,waterlogged=false]", 0xC57A55)); // 샤워기
            v.set(i0, L - 1, j0, Block.of("light_blue_terracotta", 0x716C89));
        }
    }

    private void firstFloor() {
        Frame f = Frame.of(v);
        int L = 0, top = 2;
        finish(L, Rooms.MARU);
        // 뒤 줄 (j hj0+1 .. cz1-1): 손님방, 욕실, 부엌 (부엌은 앞쪽 식당과 트임)
        int back1 = cz1 - 1;
        v.fill(cx1, L, cz1, cx1 + 8, top, cz1, Interior.INNER_WALL);
        v.fill(cx1 + 5, L, hj0 + 1, cx1 + 5, top, cz1, Interior.INNER_WALL);
        v.fill(cx1 + 8, L, hj0 + 1, cx1 + 8, top, cz1, Interior.INNER_WALL);
        // 손님방 (4×5)
        room(cx1 + 1, hj0 + 1, cx1 + 4, back1, L, GUEST, Rooms.MARU_LIGHT);
        Interior.door(f, cx1 + 2, L, cz1, "pale_oak", "south");
        Furniture.bed(f, r, cx1 + 4, L, hj0 + 1, "south");
        v.set(cx1 + 1, L, hj0 + 1, Rooms.WARDROBE);
        v.set(cx1 + 1, L + 1, hj0 + 1, Rooms.WARDROBE);
        // 욕실 (2×5)
        bathroom(cx1 + 6, hj0 + 1, cx1 + 7, back1, L);
        Interior.door(f, cx1 + 6, L, cz1, "pale_oak", "south");
        // 부엌 (뒤 벽을 따라) + 식당
        int ka = cx1 + 9, kb = hx1 - 1;
        room(ka, hj0 + 1, kb, cz1 + 1, L, KITCHEN, Block.of("polished_andesite", 0x848685));
        Furniture.kitchen(f, ka, kb, L, hj0 + 1, "north");
        Furniture.table(f, ka, L, cz1 + 4, 3, 1, "dark_oak");
        // 거실 (앞, 10×6): TV 벽, 마주 보는 소파, 탁자, 깔개
        int la = hx0 + 1, lb = cx1 + 6, lj0 = cz1 + 2, lj1 = hj1 - 1;
        room(la, lj0, lb, lj1, L, LIVING, Rooms.MARU);
        int door = (hx0 + hx1) / 2;
        Interior.floor(f, door - 1, hj1 - 2, door + 1, hj1 - 1, L, Interior.TILE);
        String sofa = Furniture.SOFA[r.nextInt(Furniture.SOFA.length)];
        for (int i = la + 1; i <= la + 4; i++) {
            v.set(i, L, lj0 + 1, Block.of(sofa + "_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", Furniture.rgb(sofa)));
        }
        v.set(la, L, lj0 + 2, Block.of(sofa + "_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", Furniture.rgb(sofa)));
        for (int i = la + 2; i <= la + 3; i++) {
            v.set(i, L, lj1, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
            v.set(i, L + 1, lj1, Furniture.TV);
            v.set(i, L, lj0 + 3, Block.of("light_gray_carpet", 0x8E8E86));
        }
        Furniture.plant(f, r, la, L, lj1);
        Furniture.plant(f, r, lb, L, lj0 + 1);
        // 식당 쪽 앞 (거실 옆): 화분, 장식장
        v.set(hx1 - 1, L, hj1 - 1, Furniture.BOOKSHELF);
        v.set(hx1 - 1, L + 1, hj1 - 1, Furniture.BOOKSHELF);
        v.set(lane, top, cz1 + 1, Interior.LIGHT);
    }

    private void secondFloor() {
        Frame f = Frame.of(v);
        int L = 4, top = 6;
        finish(L, Rooms.MARU_LIGHT);
        int cj = cz1 + 1; // 복도 줄
        int fj = cj + 1;  // 앞 방들 뒤 벽
        // 뒤 줄: 침실 · 공용 욕실 · 침실 (j hj0+1 .. cz1-1)
        int back1 = cz1 - 1;
        v.fill(cx1, L, cz1, hx1, top, cz1, Interior.INNER_WALL);
        v.fill(cx1 + 5, L, hj0 + 1, cx1 + 5, top, cz1, Interior.INNER_WALL);
        v.fill(cx1 + 8, L, hj0 + 1, cx1 + 8, top, cz1, Interior.INNER_WALL);
        room(cx1 + 1, hj0 + 1, cx1 + 4, back1, L, BED, Rooms.MARU_LIGHT);
        Interior.door(f, cx1 + 2, L, cz1, "pale_oak", "south");
        Furniture.bed(f, r, cx1 + 4, L, hj0 + 1, "south");
        Furniture.desk(f, cx1 + 1, L, hj0 + 1, "south");
        bathroom(cx1 + 6, hj0 + 1, cx1 + 7, back1, L);
        Interior.door(f, cx1 + 6, L, cz1, "pale_oak", "south");
        room(cx1 + 9, hj0 + 1, hx1 - 1, back1, L, BED, Rooms.MARU_LIGHT);
        Interior.door(f, cx1 + 10, L, cz1, "pale_oak", "south");
        Furniture.bed(f, r, hx1 - 1, L, hj0 + 1, "south");
        for (int i = cx1 + 11; i <= hx1 - 3; i++) {
            v.set(i, L, hj0 + 1, Furniture.BOOKSHELF);
        }
        // 앞 줄: 침실 (왼쪽) · 안방 (오른쪽, 안방 욕실)
        int mid = hx0 + 7;
        v.fill(hx0, L, fj, hx1, top, fj, Interior.INNER_WALL);
        v.fill(mid, L, fj, mid, top, hj1, Interior.INNER_WALL);
        room(hx0 + 1, fj + 1, mid - 1, hj1 - 1, L, BED, Rooms.MARU_LIGHT);
        Interior.door(f, hx0 + 2, L, fj, "pale_oak", "south");
        Furniture.bed(f, r, hx0 + 1, L, hj1 - 2, "east");
        v.set(mid - 1, L, fj + 1, Rooms.WARDROBE);
        v.set(mid - 1, L + 1, fj + 1, Rooms.WARDROBE);
        // 안방 욕실 (2×3) 오른쪽 뒤 구석
        int ba = hx1 - 2, bb = hx1 - 1;
        v.fill(ba - 1, L, fj + 1, ba - 1, top, fj + 4, Interior.INNER_WALL);
        v.fill(ba - 1, L, fj + 4, bb, top, fj + 4, Interior.INNER_WALL);
        bathroom(ba, fj + 1, bb, fj + 3, L);
        Interior.door(f, ba, L, fj + 4, "pale_oak", "south");
        room(mid + 1, fj + 1, ba - 2, hj1 - 1, L, MASTER, Rooms.MARU);
        Interior.door(f, mid + 2, L, fj, "pale_oak", "south");
        Furniture.bed(f, r, mid + 3, L, fj + 2, "south");
        Furniture.bed(f, r, mid + 4, L, fj + 2, "south");
        v.set(mid + 1, L, hj1 - 1, Rooms.WARDROBE);
        v.set(mid + 1, L + 1, hj1 - 1, Rooms.WARDROBE);
        v.set(mid + 2, L, hj1 - 1, Rooms.WARDROBE);
        v.set(mid + 2, L + 1, hj1 - 1, Rooms.WARDROBE);
        Furniture.plant(f, r, hx1 - 1, L, hj1 - 1);
        v.set(lane, top, cj, Interior.LIGHT);
    }

    /** 옛 저택: 짙은 기와 모임지붕 (처마 한 칸 밖으로, 세 칸에 두 칸꼴로 올라감) */
    private void hipRoof() {
        int a0 = hx0 - 1, a1 = hx1 + 1, b0 = hj0 - 1, b1 = hj1 + 1;
        for (int t = 0; a0 + t <= a1 - t && b0 + t <= b1 - t; t++) {
            int ia = a0 + t, ib = a1 - t, ja = b0 + t, jb = b1 - t;
            int halves = Math.round((t + 1) * 4 / 3f); // 지붕 윗면 높이 (반 칸 단위, y 7 바닥부터)
            boolean slab = halves % 2 == 1;
            int yy = 7 + (slab ? (halves - 1) / 2 : halves / 2 - 1);
            for (int i = ia; i <= ib; i++) {
                for (int j = ja; j <= jb; j++) {
                    if (i != ia && i != ib && j != ja && j != jb) {
                        continue;
                    }
                    if (yy - 1 >= 7) {
                        v.fill(i, 7, j, i, yy - 1, j, TILE);
                    }
                    String face = j == ja ? "south" : j == jb ? "north" : i == ia ? "east" : "west";
                    boolean corner = (i == ia || i == ib) && (j == ja || j == jb);
                    v.set(i, yy, j, slab ? TILE_SLAB : corner || ia == ib || ja == jb ? TILE
                            : Block.of("deepslate_tile_stairs[facing=" + face + ",half=bottom,shape=straight,waterlogged=false]", 0x363637));
                }
            }
        }
    }

    /** 새 저택: 평지붕 옥상 데크, 유리 난간, 화분 */
    private void roofDeck() {
        int y = 8;
        v.fill(hx0, y - 1, hj0, hx1, y - 1, hj1, SMOOTH_STONE);
        for (int i = hx0; i <= hx1; i++) {
            for (int j = hj0; j <= hj1; j++) {
                if (inCore(i, j)) {
                    continue;
                }
                boolean edge = i == hx0 || i == hx1 || j == hj0 || j == hj1;
                if (edge) {
                    v.set(i, y, j, WHITE_PANE);
                } else if (i > hx1 - 7 && j > hj1 - 6) {
                    v.set(i, y - 1, j, Block.of("spruce_planks", 0x725430)); // 나무 데크 (탁자 자리)
                }
            }
        }
        Furniture.table(Frame.of(v), hx1 - 4, y, hj1 - 3, 2, 1, "spruce");
        for (int k = 0; k < 3; k++) {
            Furniture.plant(Frame.of(v), r, hx1 - 1, y, hj0 + 2 + k * 2);
        }
        v.set(cx1 + 2, y, hj1 - 1, LANTERN);
    }
}
