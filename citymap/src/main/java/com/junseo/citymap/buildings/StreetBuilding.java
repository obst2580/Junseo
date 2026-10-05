package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 이태원·홍대 거리의 3~5층 건물. 층마다 가게가 하나씩 들고, 지하에는 클럽·공연장이 들 수 있습니다.
 * <ul>
 *   <li>뒤 구석 계단실(1칸 줄 꺾인 계단)이 지하부터 옥상까지 이어집니다. 1층 가게 옆 계단 입구 문으로 들어가서
 *       내려가면 지하 클럽, 올라가면 위층 가게와 옥상입니다.</li>
 *   <li>1층(4칸)은 유리 가게 앞과 간판판·표지판 글씨, 위층(4칸)은 큰 창과 층 간판, 모서리 돌출 간판</li>
 *   <li>지하 층은 6칸(빈 칸 5): 클럽(DJ 부스, 바, 소파, 어두운 조명에 빛나는 블록 몇 개), 라이브 공연장(무대, 앰프, 바)</li>
 *   <li>쓰임: 식당(케밥·타코·할랄·버거·커리·고기·포차·브런치), 펍·바, 카페, PC방, 편의점, 코인노래방, 옷가게,
 *       양복점, 사무실</li>
 *   <li>옥상 바(선택): 나무 데크, 유리 난간, 바 카운터, 파라솔 탁자, 줄 조명</li>
 * </ul>
 * 정면은 남쪽(j = d-1). 맨 앞 한 줄은 간판·차양 자리입니다.
 */
final class StreetBuilding {
    enum Use { RESTAURANT, BAR, CAFE, CLUB, LIVE, PC, STORE, NORAE, CLOTHES, TAILOR, OFFICE }

    /**
     * 가게 하나. name·sub 는 간판 두 줄 (영어 섞어도 됨, 한 줄 한글 7자·영문 15자 안), food 는 식당 종류
     * ("kebab", "taco", "halal", "burger", "curry", "bbq", "pocha", "brunch") 또는 바 종류("pub", "wine", "lounge").
     */
    record Shop(Use use, String name, String sub, String food) {
        Shop(Use use, String name, String sub) {
            this(use, name, sub, "");
        }
    }

    static final class Spec {
        Shop ground;
        /** 2층부터 위로 */
        Shop[] upper = {};
        /** 지하 1층, 2층 (같은 클럽이 두 층을 쓰면 B1 은 바·라운지, B2 는 춤추는 곳) */
        Shop[] basement = {};
        /** 옥상 바 이름 (없으면 null) */
        String rooftop;
        /** 바깥 재료 번호 (SKINS) */
        int skin;
        boolean coreLeft = true;
        /** 옆벽 창 (옆 건물과 붙어 있으면 false) */
        boolean sideWindows;
    }

    /** {벽, 띠, 1층 기둥} */
    static final Block[][] SKINS = {
            {BRICKS, SMOOTH_STONE, POLISHED_DEEPSLATE},                                   // 0 붉은 벽돌
            {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE},                     // 1 흰 벽
            {LIGHT_GRAY_CONCRETE, GRAY_CONCRETE, POLISHED_ANDESITE},                      // 2 노출 콘크리트
            {BLACK_CONCRETE, GRAY_CONCRETE, POLISHED_BLACKSTONE},                        // 3 검은 벽 (클럽)
            {WHITE_TERRACOTTA, SMOOTH_STONE, POLISHED_GRANITE},                           // 4 베이지 타일
            {DEEPSLATE_BRICKS, POLISHED_DEEPSLATE, POLISHED_DEEPSLATE},                   // 5 짙은 벽돌
            {Block.of("smooth_sandstone", 0xDFD6AA), SMOOTH_STONE, POLISHED_GRANITE},     // 6 모래색 돌
            {Block.of("terracotta", 0x985E43), SMOOTH_STONE, POLISHED_DEEPSLATE},         // 7 갈색 타일
            {Block.of("mud_bricks", 0x89684F), SMOOTH_STONE, POLISHED_DEEPSLATE},         // 8 흙벽돌
    };
    /** 간판판 {판 블록, 표지판 나무, 글자색} */
    private static final Object[][] BOARDS = {
            {BLACK_CONCRETE, "dark_oak", "white"}, {BLACK_CONCRETE, "dark_oak", "yellow"}, {WHITE_CONCRETE, "birch", "black"},
            {Block.of("dark_oak_planks", 0x432B14), "dark_oak", "white"}, {Block.of("spruce_planks", 0x725430), "spruce", "white"},
            {GREEN_CONCRETE, "warped", "white"}, {RED_CONCRETE, "mangrove", "white"}, {BLUE_CONCRETE, "dark_oak", "white"},
            {Block.of("gray_concrete", 0x36393D), "dark_oak", "white"}, {WHITE_CONCRETE, "birch", "red"},
    };
    static final int BASEMENT_H = 6, FLOOR_H = 4;
    private static final Block DARK = BLACK_CONCRETE;
    private static final Block CHAIN = Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050);
    private static final Block SOUL_LANTERN = Block.of("soul_lantern[hanging=true,waterlogged=false]", 0x4A6A7A);

    private final Voxels v;
    private final Random r;
    private final Spec s;
    private final int w, d, jf, floors, depth, cx0, cx1, entryI;
    final int[] levels;
    private final int nb;
    private final Block wall, trim, base;

    private StreetBuilding(int w, int d, Random r, Spec s) {
        this.w = w;
        this.d = d;
        this.r = r;
        this.s = s;
        this.nb = s.basement.length;
        this.floors = 1 + s.upper.length;
        levels = new int[nb + floors + 1];
        for (int k = 0; k < nb; k++) {
            levels[k] = -BASEMENT_H * (nb - k);
        }
        for (int k = 0; k <= floors; k++) {
            levels[nb + k] = FLOOR_H * k;
        }
        jf = d - 2;
        depth = Interior.stairDepth(levels);
        cx0 = s.coreLeft ? 0 : w - 5;
        cx1 = cx0 + 4;
        entryI = s.coreLeft ? 3 : w - 2;
        Block[] skin = SKINS[Math.floorMod(s.skin, SKINS.length)];
        wall = skin[0];
        trim = skin[1];
        base = skin[2];
        v = new Voxels(w, d, levels[0] - 1, FLOOR_H * floors + 8);
    }

    static Voxels build(int w, int d, Random r, Spec s) {
        return create(w, d, r, s).v;
    }

    static StreetBuilding create(int w, int d, Random r, Spec s) {
        StreetBuilding b = new StreetBuilding(w, d, r, s);
        b.shell();
        b.core();
        for (int k = 0; k < b.nb; k++) {
            b.basement(k);
        }
        b.ground();
        for (int k = 1; k < b.floors; k++) {
            b.upper(k);
        }
        b.roof();
        b.verticalSign();
        b.v.connect();
        return b;
    }

    Voxels voxels() {
        return v;
    }

    /** 계단실에서 나오는 곳 {i, j} (검사·미리보기용) */
    int[] landing() {
        return new int[]{entryI, depth + 2};
    }

    private int roofLevel() {
        return FLOOR_H * floors;
    }

    /** 계단실(벽 포함) 안인지 */
    private boolean inCore(int i, int j) {
        return i >= cx0 && i <= cx1 && j <= depth + 1;
    }

    // ------------------------------------------------------------------ 뼈대

    private void shell() {
        // 앞 줄과 바닥 포장
        v.fill(0, -1, 0, w - 1, -1, d - 1, LIGHT_GRAY_CONCRETE);
        int top = roofLevel();
        for (int k = 0; k <= floors; k++) {
            v.fill(0, FLOOR_H * k - 1, 0, w - 1, FLOOR_H * k - 1, jf, k == 0 ? POLISHED_ANDESITE : SMOOTH_STONE);
        }
        v.walls(0, 0, 0, w - 1, top - 1, jf, wall);
        for (int k = 1; k < floors; k++) {
            v.walls(0, FLOOR_H * k - 1, 0, w - 1, FLOOR_H * k - 1, jf, trim);
        }
        // 지하: 둘레 벽(검은 콘크리트)과 층 바닥, 속은 비움
        if (nb > 0) {
            int bottom = levels[0] - 1;
            v.fill(0, bottom, 0, w - 1, -2, jf, AIR);
            v.walls(0, bottom, 0, w - 1, -2, jf, Block.of("gray_concrete", 0x36393D));
            v.fill(0, bottom, 0, w - 1, bottom, jf, Block.of("gray_concrete", 0x36393D));
            for (int k = 1; k < nb; k++) {
                v.fill(0, levels[k] - 1, 0, w - 1, levels[k] - 1, jf, Block.of("gray_concrete", 0x36393D));
            }
        }
    }

    private void core() {
        Frame f = Frame.facing(v, s.coreLeft ? 3 : w - 2, depth, "north");
        Interior.stairCore(f, levels, wall, "stone_brick", 0x7A7979, 1);
        int top = roofLevel();
        // 옥탑 지붕
        v.fill(cx0, top + 3, 0, cx1, top + 3, depth + 1, SMOOTH_STONE);
        // 계단실 안쪽 벽은 회색 (바깥 벽은 건물 재료)
        for (int y = levels[0]; y <= top + 2; y++) {
            for (int j = 1; j <= depth + 1; j++) {
                int inner = s.coreLeft ? cx1 : cx0;
                Block b = v.get(inner, y, j);
                if (b != null && b.equals(wall) && y < top) {
                    v.set(inner, y, j, Interior.CORE_WALL);
                }
            }
        }
        // 지하 계단실 벽
        for (int y = levels[0] - 1; y < 0; y++) {
            for (int j = 0; j <= depth + 1; j++) {
                for (int i = cx0; i <= cx1; i++) {
                    Block b = v.get(i, y, j);
                    if (b != null && b.equals(wall)) {
                        v.set(i, y, j, Interior.CORE_WALL);
                    }
                }
            }
        }
        // 계단참 벽등
        for (int lv : levels) {
            v.set(s.coreLeft ? 1 : w - 4, lv + 2, depth, Interior.LIGHT);
        }
    }

    // ------------------------------------------------------------------ 지하

    private void basement(int k) {
        int L = levels[k], h = levels[k + 1] - L;
        Shop shop = s.basement[k];
        int a0 = 1, a1 = w - 2, b0 = 1, b1 = jf - 1;
        boolean lower = k == 0 && nb == 2 && s.basement[1].name().equals(shop.name());
        boolean upperOfTwo = k == 1 && nb == 2 && s.basement[0].name().equals(shop.name());
        Block floor = shop.use() == Use.LIVE ? Block.of("dark_oak_planks", 0x432B14) : POLISHED_BLACKSTONE;
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                if (!inCore(i, j)) {
                    v.set(i, L - 1, j, floor);
                }
            }
        }
        // 안쪽 벽 마감 (어두운 벽)
        for (int y = L; y < L + h - 1; y++) {
            for (int j = 0; j <= jf; j++) {
                for (int i : new int[]{0, w - 1}) {
                    if (!inCore(i, j)) {
                        v.set(i, y, j, DARK);
                    }
                }
            }
            for (int i = 0; i < w; i++) {
                if (!inCore(i, 0)) {
                    v.set(i, y, 0, DARK);
                }
                v.set(i, y, jf, DARK);
            }
        }
        switch (shop.use()) {
            case LIVE -> live(a0, b0, a1, b1, L, h);
            case CLUB -> {
                if (upperOfTwo) {
                    clubLounge(a0, b0, a1, b1, L, h);
                } else {
                    club(a0, b0, a1, b1, L, h, lower);
                }
            }
            default -> furnish(shop, a0, b0, a1, b1, L, h);
        }
        // 계단에서 나오는 곳 표지판
        v.set(entryI + (s.coreLeft ? 1 : -1), L + 2, depth + 2, Blocks.wallSign("dark_oak", "south", "white", true,
                "B" + (nb - k), shop.name(), shop.sub()));
    }

    /** 클럽 (춤추는 층): 가운데 댄스 플로어, 안쪽 끝 DJ 부스(한 칸 높은 단), 옆벽 바, 앞쪽 소파, 어두운 조명 */
    private void club(int a0, int b0, int a1, int b1, int L, int h, boolean lower) {
        Frame f = Frame.of(v);
        int top = L + h - 2;
        // DJ 부스: 앞쪽(길 쪽) 벽에 붙은 단, 사람들은 계단 쪽에서 들어와 부스를 봄
        int dj = b1 - 2, mid = (a0 + a1) / 2;
        v.fill(a0 + 2, L - 1, dj, a1 - 2, L - 1, b1, POLISHED_BLACKSTONE);
        v.fill(mid - 2, L, dj, mid + 2, L, b1, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
        v.fill(mid - 2, L, dj - 1, mid + 2, L, dj - 1, Block.of("polished_blackstone_bricks", 0x302B31));
        v.fill(mid - 1, L + 1, dj - 1, mid + 1, L + 1, dj - 1, Block.of("iron_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
        // 스피커 (양옆 검은 상자)
        for (int i : new int[]{mid - 4, mid + 4}) {
            if (i > a0 && i < a1) {
                v.fill(i, L, b1, i, L + 2, b1, Block.of("black_concrete", 0x080A0F));
                v.set(i, L + 1, b1 - 1, Block.of("coal_block", 0x101010));
            }
        }
        // 부스 뒤 빛나는 벽 (보라빛 몇 칸)
        for (int i = mid - 2; i <= mid + 2; i += 2) {
            v.set(i, L + 2, b1 + 1, Block.of("crying_obsidian", 0x200A3A));
        }
        // 댄스 플로어
        for (int j = b0 + 3; j <= dj - 2; j++) {
            for (int i = a0 + 2; i <= a1 - 2; i++) {
                if (!inCore(i, j) && !inCore(i, j - 1)) {
                    v.set(i, L - 1, j, ((i + j) & 1) == 0 ? POLISHED_BLACKSTONE : Block.of("polished_deepslate", 0x484849));
                }
            }
        }
        // 바 (계단 반대쪽 옆벽)
        int bi = s.coreLeft ? a1 - 1 : a0 + 1, back = s.coreLeft ? a1 : a0, out = s.coreLeft ? -1 : 1;
        for (int j = b0 + 1; j <= Math.min(dj - 3, b0 + 7); j++) {
            v.set(bi, L, j, Block.of("polished_blackstone", 0x353038));
            v.set(back, L + 1, j, j % 2 == 0 ? Block.of("glass", 0xC8DCE4) : Block.of("dark_oak_planks", 0x432B14));
            Furniture.chair(f, bi + out, L, j, s.coreLeft ? "east" : "west", "dark_oak");
        }
        v.set(back, L + 2, b0 + 2, Block.of("sea_lantern", 0xACC7BE));
        // 소파 (계단 쪽 옆벽, 낮은 탁자)
        int si = s.coreLeft ? a0 : a1;
        for (int j = depth + 3; j + 2 <= dj - 2; j += 4) {
            Furniture.sofa(f.sub(si, j, s.coreLeft ? "west" : "east"), r, 0, L, 0, 3, "north");
            v.set(si - out, L, j + 1, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        }
        // 어두운 조명: 천장 영혼 등불 몇 개, 부스 위 엔드 막대
        for (int i = a0 + 3; i <= a1 - 3; i += 5) {
            v.set(i, top, (b0 + dj) / 2, SOUL_LANTERN);
        }
        v.set(mid, top, dj, Block.of("end_rod[facing=down]", 0xE8E2D8));
        if (lower) {
            v.set(mid, L + 1, b0 + 4, Blocks.wallSign("dark_oak", "south", "white", true, "", "MAIN STAGE"));
        }
    }

    /** 클럽 위층 (B1, 아래가 무대일 때): 입구 계산대·물품 보관, 라운지 바, 소파 */
    private void clubLounge(int a0, int b0, int a1, int b1, int L, int h) {
        Frame f = Frame.of(v);
        int top = L + h - 2;
        // 입구 계산대 (계단에서 나오는 곳)
        int ci = s.coreLeft ? cx1 + 2 : cx0 - 2;
        v.fill(ci, L, depth + 3, ci, L, depth + 5, Block.of("polished_blackstone", 0x353038));
        v.set(ci, L + 1, depth + 4, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        // 물품 보관대
        for (int j = b0; j <= b0 + 3; j++) {
            int i = s.coreLeft ? a1 : a0;
            v.set(i, L, j, Block.of("iron_block", 0xDCDCDC));
            v.set(i, L + 1, j, Block.of("iron_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        }
        // 긴 바 (앞쪽 벽)
        for (int i = a0 + 1; i <= a1 - 1; i++) {
            v.set(i, L, b1 - 1, Block.of("polished_blackstone", 0x353038));
            v.set(i, L + 1, b1, i % 2 == 0 ? Block.of("glass", 0xC8DCE4) : Block.of("dark_oak_planks", 0x432B14));
            if (i % 2 == 1) {
                Furniture.chair(f, i, L, b1 - 2, "south", "dark_oak");
            }
        }
        v.set((a0 + a1) / 2, L + 2, b1, Block.of("sea_lantern", 0xACC7BE));
        // 소파 자리
        for (int j = depth + 3; j <= b1 - 5; j += 4) {
            int i = s.coreLeft ? a1 : a0;
            Furniture.sofa(f.sub(i, j, s.coreLeft ? "east" : "west"), r, 0, L, 0, 3, "north");
        }
        for (int i = a0 + 3; i <= a1 - 3; i += 5) {
            v.set(i, top, (b0 + b1) / 2, SOUL_LANTERN);
        }
    }

    /** 라이브 공연장: 앞쪽 벽에 무대(한 칸 높음), 앰프·드럼, 무대 조명, 뒤에 바와 음향 콘솔 */
    private void live(int a0, int b0, int a1, int b1, int L, int h) {
        Frame f = Frame.of(v);
        int top = L + h - 2, mid = (a0 + a1) / 2;
        int sj = b1 - 3;
        v.fill(a0, L, sj, a1, L, b1, Block.of("spruce_planks", 0x725430));
        v.fill(a0, L - 1, sj, a1, L - 1, b1, Block.of("spruce_planks", 0x725430));
        v.fill(mid - 1, L, sj - 1, mid + 1, L, sj - 1, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430));
        // 앰프와 드럼
        v.fill(a0 + 1, L + 1, b1, a0 + 1, L + 2, b1, Block.of("black_concrete", 0x080A0F));
        v.fill(a1 - 1, L + 1, b1, a1 - 1, L + 2, b1, Block.of("black_concrete", 0x080A0F));
        v.set(mid, L + 1, b1, Block.of("note_block", 0x58402A));
        v.set(mid - 1, L + 1, b1, Block.of("cauldron", 0x4A4A4A));
        v.set(mid + 1, L + 1, b1 - 1, CAULDRON);
        // 마이크 스탠드
        v.set(mid, L + 1, sj + 1, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0x5A5A5A));
        v.set(mid - 3, L + 1, sj + 1, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0x5A5A5A));
        // 무대 조명 (천장에서 아래로)
        for (int i = a0 + 1; i <= a1 - 1; i += 3) {
            v.set(i, top, sj, Block.of("end_rod[facing=down]", 0xE8E2D8));
        }
        v.fill(a0, top, sj - 1, a1, top, sj - 1, Block.of("iron_bars", 0x888888));
        // 음향 콘솔 (객석 뒤 가운데)
        int cj = depth + 4;
        v.fill(mid - 1, L, cj, mid + 1, L, cj, Block.of("polished_blackstone", 0x353038));
        v.fill(mid - 1, L + 1, cj, mid + 1, L + 1, cj, Block.of("iron_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
        // 바 (계단 반대쪽 뒤 구석)
        int bi = s.coreLeft ? a1 : a0;
        for (int j = b0; j <= b0 + 4; j++) {
            v.set(bi + (s.coreLeft ? -1 : 1), L, j, Block.of("dark_oak_planks", 0x432B14));
            v.set(bi, L + 1, j, j % 2 == 0 ? Block.of("glass", 0xC8DCE4) : Block.of("dark_oak_planks", 0x432B14));
        }
        v.set(bi, L + 2, b0 + 2, Block.of("shroomlight", 0xF09246));
        for (int i = a0 + 3; i <= a1 - 3; i += 5) {
            v.set(i, top, (b0 + sj) / 2, Block.of("lantern[hanging=true,waterlogged=false]", 0x6A5B49));
        }
    }

    // ------------------------------------------------------------------ 1층

    private void ground() {
        Shop shop = s.ground;
        int L = 0;
        // 계단 입구 통로 (계단실 앞에서 길까지)
        int ca = cx0 + 1, cb = cx1 - 1;
        int inner = s.coreLeft ? cx1 : cx0;
        v.fill(inner, 0, depth + 1, inner, 2, jf, wall);
        v.fill(ca, -1, depth + 2, cb, -1, jf - 1, POLISHED_ANDESITE);
        v.fill(ca, 0, jf, cb, 2, jf, POLISHED_DEEPSLATE);
        int di = (ca + cb) / 2;
        v.set(di, 0, jf, door("dark_oak", "south", false));
        v.set(di, 1, jf, door("dark_oak", "south", true));
        v.set(ca, 2, jf, GLASS_PANE);
        v.set(cb, 2, jf, GLASS_PANE);
        v.set(di, 2, (depth + jf) / 2 + 1, Interior.LIGHT);
        // 계단 입구 위 층 안내 (지하 가게가 있으면 그 간판)
        if (nb > 0) {
            Shop b1 = s.basement[nb - 1];
            v.set(s.coreLeft ? cx0 : cx1, 1, jf + 1, Blocks.wallHangingSign("dark_oak", s.coreLeft ? "west" : "east", "white", true,
                    "B1", b1.name(), b1.sub()));
            v.set(di, 2, jf + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "↓ B1", b1.name()));
        } else {
            v.set(di, 2, jf + 1, Blocks.wallSign("birch", "south", "black", false, "", "계단", "2F~"));
        }
        // 가게 앞
        int sa = s.coreLeft ? cx1 + 1 : 1, sb = s.coreLeft ? w - 2 : cx0 - 1;
        for (int i = sa; i <= sb; i++) {
            for (int y = 0; y <= 2; y++) {
                v.set(i, y, jf, y == 0 && (i - sa) % 4 != 1 ? base : GLASS_PANE);
            }
        }
        for (int i = sa; i <= sb; i += 4) {
            v.fill(i, 0, jf, i, 2, jf, base);
        }
        v.fill(s.coreLeft ? w - 1 : 0, 0, jf, s.coreLeft ? w - 1 : 0, 2, jf, base);
        int doorI = (sa + sb) / 2;
        v.set(doorI, 0, jf, AIR);
        v.set(doorI, 1, jf, AIR);
        v.set(doorI + 1, 0, jf, AIR);
        v.set(doorI + 1, 1, jf, AIR);
        // 간판판과 글씨
        Object[] board = board(shop);
        v.fill(sa - 1, 3, jf, sb + 1, 3, jf, (Block) board[0]);
        v.set(doorI, 3, jf + 1, Blocks.wallSign((String) board[1], "south", (String) board[2], true, "", shop.name(), shop.sub()));
        v.set(doorI + 1, 3, jf + 1, Blocks.wallSign((String) board[1], "south", (String) board[2], true, "", shop.name(), shop.sub()));
        // 차양 (식당·카페)
        if (shop.use() == Use.RESTAURANT || shop.use() == Use.CAFE || shop.use() == Use.BAR) {
            String wood = new String[]{"spruce", "dark_oak", "mangrove", "warped", "acacia"}[r.nextInt(5)];
            for (int i = sa; i <= sb; i++) {
                if (i != doorI && i != doorI + 1) {
                    v.set(i, 2, jf + 1, Block.of(wood + "_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", 0x725430));
                }
            }
        }
        // 가게 안
        for (int j = 1; j <= jf - 1; j++) {
            for (int i = sa; i <= sb; i++) {
                v.set(i, -1, j, floorFor(shop));
            }
        }
        furnish(shop, sa, 1, sb, jf - 1, L, FLOOR_H);
        if (shop.use() == Use.RESTAURANT || shop.use() == Use.CAFE || shop.use() == Use.BAR) {
            standSign(shop, doorI + 2 <= sb ? doorI + 2 : doorI - 1);
        }
    }

    private Object[] board(Shop shop) {
        if (shop.use() == Use.CLUB || shop.use() == Use.LIVE || shop.use() == Use.PC) {
            return BOARDS[r.nextInt(2)];
        }
        if (shop.use() == Use.STORE) {
            return new Object[]{WHITE_CONCRETE, "birch", "blue"};
        }
        return BOARDS[r.nextInt(BOARDS.length)];
    }

    private Block floorFor(Shop shop) {
        return switch (shop.use()) {
            case BAR -> Block.of("dark_oak_planks", 0x432B14);
            case CAFE -> Block.of("oak_planks", 0xA2834F);
            case STORE -> Block.of("white_concrete", 0xCFD5D6);
            case PC, NORAE -> Block.of("gray_concrete", 0x36393D);
            case CLOTHES, TAILOR -> Block.of("birch_planks", 0xC0AF79);
            case CLUB, LIVE -> POLISHED_BLACKSTONE;
            case RESTAURANT -> switch (shop.food()) {
                case "taco" -> Block.of("orange_terracotta", 0xA15325);
                case "halal", "curry" -> Block.of("red_terracotta", 0x8F3D2E);
                case "bbq", "pocha" -> Block.of("smooth_stone", 0x9E9E9E);
                default -> Block.of("white_terracotta", 0xD1B2A1);
            };
            default -> Block.of("light_gray_concrete", 0x7D7D73);
        };
    }

    // ------------------------------------------------------------------ 위층

    private void upper(int k) {
        int L = FLOOR_H * k;
        Shop shop = s.upper[k - 1];
        // 앞 큰 창 (4칸 칸살에 3칸 창)
        for (int i = 1; i < w - 1; i++) {
            boolean pier = (i - 1) % 4 == 3;
            if (!pier) {
                v.set(i, L + 1, jf, GLASS_PANE);
                v.set(i, L + 2, jf, GLASS_PANE);
            }
        }
        // 뒤·옆 창
        for (int i = 2; i < w - 2; i += 3) {
            if (!inCore(i, 0)) {
                v.set(i, L + 1, 0, GLASS_PANE);
                v.set(i, L + 2, 0, GLASS_PANE);
            }
        }
        if (s.sideWindows) {
            for (int j = depth + 4; j < jf - 1; j += 4) {
                for (int i : new int[]{0, w - 1}) {
                    if (!inCore(i, j)) {
                        v.set(i, L + 1, j, GLASS_PANE);
                        v.set(i, L + 2, j, GLASS_PANE);
                    }
                }
            }
        }
        // 계단실 앞쪽 벽 창 (계단참)
        v.set(s.coreLeft ? 0 : w - 1, L + 2, depth - 1, GLASS_PANE);
        // 층 간판: 창 아래 띠 + 표지판
        Object[] board = board(shop);
        v.fill(1, L, jf, w - 2, L, jf, (Block) board[0]);
        v.set(w / 2, L, jf + 1, Blocks.wallSign((String) board[1], "south", (String) board[2], true, (k + 1) + "F " + shop.name(), shop.sub()));
        // 바닥 마감
        for (int j = 1; j <= jf - 1; j++) {
            for (int i = 1; i <= w - 2; i++) {
                if (!inCore(i, j)) {
                    v.set(i, L - 1, j, floorFor(shop));
                }
            }
        }
        furnish(shop, 1, 1, w - 2, jf - 1, L, FLOOR_H);
        // 반대쪽 모서리 돌출 간판 (2·3층)
        if (k <= 2) {
            int i = s.coreLeft ? w - 1 : 0;
            v.set(i, L + 1, jf + 1, Blocks.wallHangingSign((String) board[1], s.coreLeft ? "west" : "east", (String) board[2], true,
                    (k + 1) + "F", shop.name(), shop.sub()));
        }
    }

    /**
     * 계단 입구 위 세로 간판 (층마다 가게 이름): 벽에서 한 칸 튀어나온 간판 기둥, 양옆에 층마다 표지판.
     * 지하 가게는 맨 아래에 「B1」.
     */
    private void verticalSign() {
        int top = roofLevel() - 1;
        if (floors < 2) {
            return;
        }
        int ci = s.coreLeft ? 1 : w - 2;
        Object[] board = BOARDS[Math.floorMod(s.skin * 3 + floors, BOARDS.length)];
        v.fill(ci, 4, d - 1, ci, top, d - 1, (Block) board[0]);
        v.set(ci, top + 1, d - 1, SMOOTH_STONE_SLAB);
        for (int k = 1; k < floors; k++) {
            Shop shop = s.upper[k - 1];
            for (int side : new int[]{-1, 1}) {
                v.set(ci + side, 4 * k + 1, d - 1, Blocks.wallSign((String) board[1], side < 0 ? "west" : "east", (String) board[2], true,
                        (k + 1) + "F", shop.name(), shop.sub()));
            }
        }
        if (nb > 0) {
            Shop b1 = s.basement[nb - 1];
            for (int side : new int[]{-1, 1}) {
                v.set(ci + side, 4, d - 1,
                        Blocks.wallSign((String) board[1], side < 0 ? "west" : "east", (String) board[2], true, "B1", b1.name(), b1.sub()));
            }
        }
    }

    /** 1층 가게 앞 입간판 (식당·바·카페) */
    private void standSign(Shop shop, int i) {
        if (v.get(i, 0, d - 1) != null) {
            return;
        }
        String wood = shop.use() == Use.CAFE ? "birch" : "dark_oak";
        v.set(i, 0, d - 1, Block.of(wood + "_sign[rotation=0,waterlogged=false]", 0x725430)
                .withText(wood.equals("birch") ? "black" : "white", false, shop.sub(), shop.name(), shop.use() == Use.BAR ? "OPEN" : "MENU"));
    }

    // ------------------------------------------------------------------ 가게 안

    /** 칸 (i, j) 가 가게 안 빈 바닥인지 (계단실·통로·계단 앞 빼고) */
    private boolean free(int i, int j, int a0, int b0, int a1, int b1) {
        if (i < a0 || i > a1 || j < b0 || j > b1 || inCore(i, j)) {
            return false;
        }
        // 계단 출입구 앞 두 칸은 비움
        return !(Math.abs(i - entryI) <= 1 && j >= depth + 2 && j <= depth + 3);
    }

    private void furnish(Shop shop, int a0, int b0, int a1, int b1, int L, int h) {
        switch (shop.use()) {
            case RESTAURANT -> restaurant(shop, a0, b0, a1, b1, L, h);
            case BAR -> bar(shop, a0, b0, a1, b1, L, h);
            case CAFE -> cafe(a0, b0, a1, b1, L, h);
            case PC -> pc(a0, b0, a1, b1, L, h);
            case STORE -> store(a0, b0, a1, b1, L, h);
            case NORAE -> norae(a0, b0, a1, b1, L, h);
            case CLOTHES -> clothes(a0, b0, a1, b1, L, h, false);
            case TAILOR -> clothes(a0, b0, a1, b1, L, h, true);
            case CLUB -> club(a0, b0, a1, b1, L, h, false);
            case LIVE -> live(a0, b0, a1, b1, L, h);
            default -> Rooms.office(Frame.of(v), r, a0 - 1, b0 - 1, a1 + 1, b1 + 1, L, h, (i, j) -> free(i, j, a0, b0, a1, b1));
        }
        lights(shop, a0, b0, a1, b1, L, h);
    }

    private void lights(Shop shop, int a0, int b0, int a1, int b1, int L, int h) {
        if (shop.use() == Use.CLUB || shop.use() == Use.LIVE) {
            return;
        }
        Block light = switch (shop.use()) {
            case BAR, RESTAURANT -> Block.of("lantern[hanging=true,waterlogged=false]", 0x6A5B49);
            default -> Interior.LIGHT;
        };
        int top = L + h - 2;
        for (int j = b0 + 2; j <= b1; j += 4) {
            for (int i = a0 + 1; i <= a1; i += 4) {
                if (free(i, j, a0, b0, a1, b1) && v.get(i, top, j) == null) {
                    v.set(i, top, j, light);
                }
            }
        }
    }

    /** 식당: 뒤쪽 주방(조리대·화덕·냉장고)과 배식 카운터, 앞쪽 식탁. 음식마다 소품 */
    private void restaurant(Shop shop, int a0, int b0, int a1, int b1, int L, int h) {
        Frame f = Frame.of(v);
        // 주방 줄 (뒷벽)
        int kj = b0;
        for (int i = a0; i <= a1; i++) {
            if (!free(i, kj, a0, b0, a1, b1)) {
                continue;
            }
            int m = (i - a0) % 4;
            v.set(i, L, kj, m == 0 ? Block.of("smoker[facing=south,lit=true]", 0x555451) : m == 1 ? CAULDRON
                    : m == 2 ? Furniture.COUNTER : Furniture.FRIDGE);
            if (m == 3) {
                v.set(i, L + 1, kj, Furniture.FRIDGE);
            }
            v.set(i, L + 2, kj, Furniture.COUNTER);
        }
        // 배식 카운터 (주방 앞, 한 칸 통로)
        int cj = b0 + 2;
        for (int i = a0; i <= a1 - 2; i++) {
            if (free(i, cj, a0, b0, a1, b1)) {
                v.set(i, L, cj, shop.food().equals("burger") ? Block.of("red_concrete", 0x8E2121) : Furniture.COUNTER);
            }
        }
        // 식탁 (2×1 판, 마주 보는 의자)
        String wood = switch (shop.food()) {
            case "taco", "curry" -> "acacia";
            case "halal" -> "dark_oak";
            case "pocha", "bbq" -> "spruce";
            case "brunch" -> "birch";
            default -> "oak";
        };
        for (int j = cj + 3; j + 1 <= b1 - 1; j += 3) {
            for (int i = a0 + 1; i + 1 <= a1 - 1; i += 4) {
                if (free(i, j, a0, b0, a1, b1) && free(i + 1, j, a0, b0, a1, b1) && free(i, j - 1, a0, b0, a1, b1)
                        && free(i + 1, j + 1, a0, b0, a1, b1)) {
                    Furniture.table(f, i, L, j, 2, 1, wood);
                    if (shop.food().equals("bbq") || shop.food().equals("pocha")) {
                        // 불판과 환기 통
                        v.set(i, L, j, Block.of("iron_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
                        v.fill(i, L + 2, j, i, L + h - 2, j, CHAIN);
                    }
                }
            }
        }
        switch (shop.food()) {
            case "kebab" -> {
                // 창가 케밥 꼬치 (세운 고기 덩이와 굽는 판)
                int i = a1 - 1, j = b1;
                if (free(i, j, a0, b0, a1, b1)) {
                    v.set(i, L, j, Furniture.COUNTER);
                    v.set(i, L + 1, j, Block.of("brown_terracotta", 0x4D3323));
                    v.set(i, L + 2, j, CHAIN);
                    v.set(i - 1, L, j, Block.of("blast_furnace[facing=south,lit=true]", 0x505050));
                }
            }
            case "taco" -> {
                for (int i = a0; i <= a1; i += 3) {
                    Furniture.plant(f, r, i, L, b1);
                }
                v.set(a0, L, b1, Block.of("potted_cactus", 0x5B7F3A));
            }
            case "halal" -> {
                // 무늬 깔개와 매단 등
                for (int j = cj + 2; j <= b1; j++) {
                    for (int i = a0; i <= a1; i++) {
                        if (free(i, j, a0, b0, a1, b1) && f.empty(i, L, j)) {
                            v.set(i, L, j, Block.of((i + j) % 3 == 0 ? "red_carpet" : "brown_carpet", 0x8E3B2E));
                        }
                    }
                }
            }
            case "burger" -> {
                // 창가 바 자리
                for (int i = a0; i <= a1; i++) {
                    if (free(i, b1, a0, b0, a1, b1) && f.empty(i, L, b1)) {
                        v.set(i, L, b1, Furniture.DESK_TOP);
                    }
                }
            }
            default -> {
            }
        }
        // 메뉴판 (주방 위 벽, 표지판)
        int mi = (a0 + a1) / 2;
        if (v.get(mi, L + 2, cj) == null) {
            v.set(mi, L + 2, cj, Blocks.hangingSign("dark_oak", 0, "white", true, "MENU", shop.name()));
        }
    }

    /** 펍·바: 옆벽 긴 바(술병 진열 뒷벽, 맥주 꼭지), 바 의자, 높은 탁자, 다트판, 구석 소파 */
    private void bar(Shop shop, int a0, int b0, int a1, int b1, int L, int h) {
        Frame f = Frame.of(v);
        boolean right = s.coreLeft;
        int back = right ? a1 : a0, ci = right ? a1 - 2 : a0 + 2, out = right ? -1 : 1;
        Block counter = shop.food().equals("wine") ? Block.of("stripped_dark_oak_wood[axis=y]", 0x4B3A28) : Block.of("dark_oak_planks", 0x432B14);
        for (int j = b0 + 1; j <= b1 - 2; j++) {
            if (!free(ci, j, a0, b0, a1, b1)) {
                continue;
            }
            v.set(ci, L, j, counter);
            if (j % 3 == 0) {
                v.set(ci, L + 1, j, Block.of("lever[face=floor,facing=north,powered=false]", 0x6B6B6B)); // 맥주 꼭지
            }
            Furniture.chair(f, ci + out, L, j, right ? "west" : "east", "spruce");
            // 술병 진열 (뒷벽): 나무 선반과 불 켜진 유리
            v.set(back, L + 1, j, j % 2 == 0 ? Block.of("glass", 0xC8DCE4) : Block.of("dark_oak_planks", 0x432B14));
            v.set(back, L, j, BARREL);
        }
        if (free(back, b0 + 2, a0, b0, a1, b1)) {
            v.set(back, L + 2, b0 + 2, Block.of("shroomlight", 0xF09246));
        }
        // 높은 탁자 (울타리 + 나무 판)
        int ta = right ? a0 + 2 : a1 - 2;
        for (int j = b0 + 3; j <= b1 - 2; j += 3) {
            if (free(ta, j, a0, b0, a1, b1) && free(ta, j + 1, a0, b0, a1, b1)) {
                v.set(ta, L, j, Block.of("dark_oak_fence", 0x432B14));
                v.set(ta, L + 1, j, Block.of("dark_oak_pressure_plate[powered=false]", 0x432B14));
                Furniture.chair(f, ta, L, j + 1, "south", "dark_oak");
            }
        }
        // 다트판 (펍)
        if (shop.food().equals("pub")) {
            int di = right ? a0 : a1;
            if (free(di, b0 + 4, a0, b0, a1, b1)) {
                v.set(di + (right ? -1 : 1), L + 1, b0 + 4, Block.of("target[power=0]", 0xE5D8C4));
            }
        }
        // 창가 소파
        int sj = b1;
        Furniture.sofa(f, r, ta - 1, L, sj, 3, "north");
    }

    /** 카페: 계산대와 에스프레소 기계, 진열장, 탁자와 소파, 화분 */
    private void cafe(int a0, int b0, int a1, int b1, int L, int h) {
        Frame f = Frame.of(v);
        int cj = b0 + 2;
        for (int i = a0; i <= a1 - 2; i++) {
            if (free(i, cj, a0, b0, a1, b1)) {
                v.set(i, L, cj, i == a0 + 1 ? Block.of("glass", 0xC8DCE4) : Furniture.COUNTER);
            }
            if (free(i, b0, a0, b0, a1, b1)) {
                v.set(i, L, b0, Furniture.COUNTER);
                if ((i - a0) % 3 == 1) {
                    v.set(i, L + 1, b0, Block.of("iron_block", 0xDCDCDC)); // 커피 기계
                }
            }
        }
        int mi = (a0 + a1) / 2;
        if (v.get(mi, L + 2, cj) == null) {
            v.set(mi, L + 2, cj, Blocks.hangingSign("birch", 0, "black", false, "COFFEE", "아메리카노", "라떼"));
        }
        for (int j = cj + 3; j <= b1 - 1; j += 3) {
            for (int i = a0 + 1; i <= a1 - 1; i += 3) {
                if (free(i, j, a0, b0, a1, b1) && free(i, j - 1, a0, b0, a1, b1) && free(i, j + 1, a0, b0, a1, b1)) {
                    v.set(i, L, j, Block.of("birch_slab[type=top,waterlogged=false]", 0xC0AF79));
                    Furniture.chair(f, i, L, j - 1, "north", "birch");
                    Furniture.chair(f, i, L, j + 1, "south", "birch");
                }
            }
        }
        for (int i = a0; i <= a1; i += 4) {
            if (free(i, b1, a0, b0, a1, b1)) {
                Furniture.plant(f, r, i, L, b1);
            }
        }
    }

    /** PC방: 입구 계산대(간식·음료 냉장고), 모니터 책상 줄 */
    private void pc(int a0, int b0, int a1, int b1, int L, int h) {
        Frame f = Frame.of(v);
        // 계산대 (계단 앞 옆)
        int ci = s.coreLeft ? cx1 + 2 : cx0 - 2;
        for (int j = depth + 3; j <= depth + 5; j++) {
            if (free(ci, j, a0, b0, a1, b1)) {
                v.set(ci, L, j, Furniture.COUNTER);
            }
        }
        if (free(ci, depth + 4, a0, b0, a1, b1)) {
            v.set(ci, L + 1, depth + 4, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        }
        for (int j = b0; j <= b0 + 2; j++) {
            int i = s.coreLeft ? a1 : a0;
            if (free(i, j, a0, b0, a1, b1)) {
                v.set(i, L, j, Furniture.FRIDGE);
                v.set(i, L + 1, j, Block.of("light_blue_stained_glass", 0x6699D8));
            }
        }
        // 책상 줄: 마주 보는 두 줄씩, 사이 통로
        for (int j = b0 + 4; j + 1 <= b1 - 1; j += 4) {
            for (int i = a0 + 1; i <= a1 - 1; i++) {
                if (free(i, j, a0, b0, a1, b1) && free(i, j + 1, a0, b0, a1, b1) && free(i, j - 1, a0, b0, a1, b1)
                        && free(i, j + 2, a0, b0, a1, b1)) {
                    Furniture.desk(f, i, L, j, "north");
                    Furniture.desk(f, i, L, j + 1, "south");
                }
            }
        }
    }

    /** 편의점: 진열대 줄, 뒷벽 음료 냉장고, 입구 계산대(담배 진열장), 현금인출기 */
    private void store(int a0, int b0, int a1, int b1, int L, int h) {
        Frame f = Frame.of(v);
        for (int i = a0; i <= a1; i++) {
            if (free(i, b0, a0, b0, a1, b1)) {
                v.set(i, L, b0, Furniture.FRIDGE);
                v.set(i, L + 1, b0, Block.of("light_blue_stained_glass", 0x6699D8));
                v.set(i, L + 2, b0, Block.of("white_concrete", 0xCFD5D6));
            }
        }
        for (int i = a0 + 1; i <= a1 - 2; i += 3) {
            for (int j = b0 + 3; j <= b1 - 4; j++) {
                if (free(i, j, a0, b0, a1, b1)) {
                    v.set(i, L, j, Block.of("smooth_quartz", 0xECE6DF));
                    v.set(i, L + 1, j, (j & 1) == 0 ? BARREL : Furniture.BOOKSHELF);
                }
            }
        }
        // 계산대 (입구 옆)
        int ci = a1 - 1;
        for (int j = b1 - 2; j <= b1; j++) {
            if (free(ci, j, a0, b0, a1, b1)) {
                v.set(ci, L, j, Furniture.COUNTER);
            }
        }
        if (free(a1, b1 - 1, a0, b0, a1, b1)) {
            v.set(a1, L, b1 - 1, Furniture.BOOKSHELF);
            v.set(a1, L + 1, b1 - 1, Furniture.BOOKSHELF);
        }
        v.set(ci, L + 1, b1 - 1, Block.of("iron_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        if (free(a0, b1, a0, b0, a1, b1)) {
            v.set(a0, L, b1, Block.of("iron_block", 0xDCDCDC));
            v.set(a0, L + 1, b1, Block.of("stone_button[face=wall,facing=east,powered=false]", 0x7E7E7E).with("face=floor,facing=south,powered=false"));
        }
        // 가게 앞 파라솔 탁자 (편의점 앞 플라스틱 탁자)
        int ti = a0 + 1;
        if (v.get(ti, 0, d - 1) == null && L == 0) {
            v.set(ti, 0, d - 1, Block.of("dark_oak_fence", 0x432B14));
            v.set(ti, 1, d - 1, Block.of("green_carpet", 0x546D1B));
        }
    }

    /** 코인노래방: 작은 방 줄 (소파와 화면), 가운데 복도 */
    private void norae(int a0, int b0, int a1, int b1, int L, int h) {
        Frame f = Frame.of(v);
        int top = L + h - 2;
        for (int i = a0; i + 3 <= a1; i += 4) {
            if (!free(i, b0, a0, b0, a1, b1) || !free(i + 3, b0 + 4, a0, b0, a1, b1)) {
                continue;
            }
            v.fill(i + 3, L, b0, i + 3, top, b0 + 4, Interior.INNER_WALL);
            v.fill(i, L, b0 + 4, i + 3, top, b0 + 4, Interior.INNER_WALL);
            Interior.door(f, i + 1, L, b0 + 4, "dark_oak", "south");
            v.set(i + 2, L + 1, b0 + 4, Block.of("glass_pane", 0xC8DCE4));
            Furniture.sofa(f.sub(i, b0, "west"), r, 0, L, 0, 3, "north");
            v.set(i + 3, L + 1, b0 + 2, Furniture.TV);
            v.set(i + 1, top, b0 + 2, Block.of("shroomlight", 0xF09246));
        }
        // 앞쪽 방 줄 (창가)
        for (int i = a0; i + 3 <= a1; i += 4) {
            if (!free(i, b1 - 4, a0, b0, a1, b1) || !free(i + 3, b1, a0, b0, a1, b1)) {
                continue;
            }
            v.fill(i + 3, L, b1 - 4, i + 3, top, b1, Interior.INNER_WALL);
            v.fill(i, L, b1 - 4, i + 3, top, b1 - 4, Interior.INNER_WALL);
            Interior.door(f, i + 1, L, b1 - 4, "dark_oak", "north");
            Furniture.sofa(f, r, i, L, b1, 3, "north");
            v.set(i + 1, L + 1, b1 - 3, Furniture.TV);
            v.set(i + 1, top, b1 - 2, Block.of("shroomlight", 0xF09246));
        }
    }

    /** 옷가게 (양복점이면 원단 선반과 재단대) */
    private void clothes(int a0, int b0, int a1, int b1, int L, int h, boolean tailor) {
        Frame f = Frame.of(v);
        String[] colors = tailor ? new String[]{"gray", "black", "blue", "light_gray", "brown", "white"}
                : new String[]{"white", "black", "light_blue", "pink", "gray", "yellow", "red", "green"};
        // 벽 옷걸이 (사슬 아래 양털 옷)
        for (int j = b0; j <= b1 - 2; j++) {
            for (int i : new int[]{a0, a1}) {
                if (free(i, j, a0, b0, a1, b1)) {
                    if (tailor) {
                        v.set(i, L, j, Furniture.BOOKSHELF);
                        v.set(i, L + 1, j, Blocks.wool(colors[r.nextInt(colors.length)]));
                    } else {
                        v.set(i, L + 2, j, CHAIN);
                        v.set(i, L + 1, j, Blocks.wool(colors[r.nextInt(colors.length)]));
                    }
                }
            }
        }
        // 가운데 진열대 또는 재단대
        int mi = (a0 + a1) / 2;
        for (int j = b0 + 3; j <= b1 - 3; j++) {
            if (free(mi, j, a0, b0, a1, b1)) {
                v.set(mi, L, j, tailor ? Block.of("oak_planks", 0xA2834F) : Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
            }
        }
        // 거울과 계산대, 탈의실
        if (free(a0 + 1, b0, a0, b0, a1, b1)) {
            v.fill(a0 + 1, L, b0, a0 + 1, L + 1, b0, Block.of("light_blue_stained_glass", 0x6699D8));
        }
        int ci = a1 - 1;
        if (free(ci, b1 - 1, a0, b0, a1, b1)) {
            v.set(ci, L, b1 - 1, Furniture.COUNTER);
        }
    }

    // ------------------------------------------------------------------ 옥상

    private void roof() {
        int y = roofLevel();
        boolean bar = s.rooftop != null;
        for (int j = 0; j <= jf; j++) {
            for (int i = 0; i < w; i++) {
                if (inCore(i, j)) {
                    continue;
                }
                boolean edge = i == 0 || i == w - 1 || j == 0 || j == jf;
                v.set(i, y - 1, j, bar && !edge ? Block.of("spruce_planks", 0x725430) : SMOOTH_STONE);
                if (edge) {
                    if (bar && (j == jf || i == 0 || i == w - 1)) {
                        v.set(i, y, j, trim);
                        v.set(i, y + 1, j, Block.of("glass_pane", 0xC8DCE4));
                    } else {
                        v.set(i, y, j, wall);
                        v.set(i, y + 1, j, SMOOTH_STONE_SLAB);
                    }
                }
            }
        }
        if (!bar) {
            // 실외기와 물탱크
            for (int n = 0; n < 3; n++) {
                int i = 2 + r.nextInt(Math.max(1, w - 4)), j = depth + 3 + r.nextInt(Math.max(1, jf - depth - 4));
                if (!inCore(i, j) && v.get(i, y, j) == null) {
                    v.set(i, y, j, SMOOTH_STONE);
                }
            }
            return;
        }
        Frame f = Frame.of(v);
        // 바 카운터 (계단 옥탑 옆)
        int ci = s.coreLeft ? cx1 + 2 : cx0 - 2;
        for (int j = 1; j <= Math.min(jf - 3, depth); j++) {
            v.set(ci, y, j, Block.of("dark_oak_planks", 0x432B14));
            v.set(s.coreLeft ? ci + 2 : ci - 2, y, j, BARREL);
        }
        v.set(s.coreLeft ? ci + 2 : ci - 2, y + 1, 2, Blocks.wallSign("dark_oak", s.coreLeft ? "west" : "east", "white", true, "", s.rooftop, "ROOFTOP"));
        // 파라솔 탁자
        for (int j = depth + 4; j <= jf - 3; j += 4) {
            for (int i = 3; i <= w - 4; i += 5) {
                if (inCore(i, j) || Math.abs(i - entryI) <= 1) {
                    continue;
                }
                v.set(i, y, j, Block.of("spruce_fence", 0x725430));
                v.set(i, y + 1, j, Block.of("spruce_fence", 0x725430));
                v.set(i, y + 2, j, Block.of("white_wool", 0xE9ECEC));
                v.set(i, y, j - 1, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430));
                v.set(i, y, j + 1, Block.of("spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", 0x725430));
            }
        }
        // 줄 조명 (난간 기둥 사이 등)
        for (int i = 1; i < w - 1; i += 3) {
            v.set(i, y + 2, jf, Block.of("spruce_fence", 0x725430));
            v.set(i, y + 3, jf, Block.of("lantern[hanging=false,waterlogged=false]", 0x6A5B49));
        }
        Furniture.plant(f, r, s.coreLeft ? w - 2 : 1, y, jf - 1);
    }
}
