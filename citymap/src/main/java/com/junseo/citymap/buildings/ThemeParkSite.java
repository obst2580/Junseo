package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 테마파크 건설 예정 부지: 놀이기구는 나중에 들어오고, 지금은 땅만 고르고 가림막을 친 공사 현장입니다.
 * <ul>
 *   <li>블록 둘레(인도에서 한 칸 안)에 4m 흰 철판 가림막(EGI 휀스): 아래 회색 띠, 위 초록 띠, 안쪽 철 기둥,
 *       바깥에 「준서랜드 / 테마파크 / 건설 예정 부지」 표지판</li>
 *   <li>거점 쪽 가운데는 가림막이 안으로 들어간 진입 마당: 그 안쪽에 출입문(문틀과 「안전제일」, 옆으로 밀어 연 문짝),
 *       경비실, 세륜기, 건축허가 표지판</li>
 *   <li>안: 평평하게 고른 흙·자갈 땅, 현장 사무실(2층으로 쌓은 컨테이너, 바깥 철계단과 복도, 사무실·회의실·소장실·휴게실),
 *       이동식 화장실, 자재 야적장(철근 다발, 거푸집 합판, H형강, 벽돌 팔레트), 굴착기, 조명탑</li>
 * </ul>
 * 정면(남쪽, j 큰 쪽)이 거점 쪽입니다.
 */
final class ThemeParkSite {
    private static final Block PANEL = WHITE_CONCRETE;
    private static final Block PANEL_BASE = LIGHT_GRAY_CONCRETE;
    private static final Block PANEL_BAND = GREEN_CONCRETE;
    private static final Block STEEL_POST = Block.of("andesite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x888888);
    private static final Block RAIL = IRON_BARS;
    private static final Block CONTAINER = WHITE_CONCRETE;
    private static final Block CONTAINER_RIB = Block.of("smooth_quartz", 0xECE6DF);
    private static final Block FRAME = GRAY_CONCRETE;
    private static final Block STEP = Blocks.stairs("polished_andesite", "west", 0x848685);
    /** 진입 마당 반폭 */
    private static final int NOTCH = 13;
    /** 출입문 반폭 (가운데 ± 이만큼 열림) */
    private static final int GATE = 5;

    private final Voxels v;
    private final SiteLand land;
    private final Random r;
    private final int hi, hj, gateJ;

    private ThemeParkSite(SiteLand land, int hi, int hj, Random r) {
        this.land = land;
        this.r = r;
        this.hi = hi;
        this.hj = hj;
        this.gateJ = hj - 5;
        v = new Voxels(land.w, land.d, -1, 14);
    }

    static Voxels build(SiteLand land, int hubI, int hubJ, Random r) {
        ThemeParkSite s = new ThemeParkSite(land, hubI, hubJ, r);
        s.ground();
        s.hoarding();
        s.gate();
        s.office(s.hi + NOTCH + 3, s.gateJ - 16);
        s.toilets(s.hi + NOTCH + 3, s.gateJ - 21);
        s.materials();
        s.excavator(s.land.w / 2 - 12, s.land.d / 2 - 6);
        s.floodlights();
        s.v.connect();
        land.clip(s.v);
        return s.v;
    }

    /** 진입 마당(가림막 바깥) 칸인지 */
    private boolean notch(int i, int j) {
        return Math.abs(i - hi) <= NOTCH && j > gateJ;
    }

    /** 현장 안(가림막 포함) */
    private boolean site(int i, int j) {
        return land.inner(i, j, 2) && !notch(i, j);
    }

    /** 가림막 칸: 현장 칸인데 옆 칸 하나라도 현장이 아님 */
    private boolean panel(int i, int j) {
        return site(i, j) && (!site(i + 1, j) || !site(i - 1, j) || !site(i, j + 1) || !site(i, j - 1));
    }

    private boolean inside(int i, int j) {
        return site(i, j) && !panel(i, j);
    }

    // ------------------------------------------------------------------ 땅

    private void ground() {
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (!land.land(i, j)) {
                    continue;
                }
                Block b;
                if (inside(i, j)) {
                    // 고른 흙땅: 4칸 단위로 흙·거친 흙·자갈이 섞임, 출입문에서 가운데로 자갈 길
                    long h = (Math.floorDiv(i, 4) * 73856093L) ^ (Math.floorDiv(j, 4) * 19349663L);
                    int k = (int) Math.floorMod(h, 7);
                    b = k < 3 ? COARSE_DIRT : k < 5 ? DIRT : k < 6 ? Block.of("gravel", 0x837E7C) : PACKED_MUD;
                    if (Math.abs(i - hi) <= 4 && j <= gateJ && j >= land.d / 2 - 10) {
                        b = Block.of("gravel", 0x837E7C);
                    }
                } else if (panel(i, j)) {
                    b = PANEL_BASE;
                } else {
                    // 가림막 바깥 보도와 진입 마당 콘크리트
                    b = notch(i, j) && Math.abs(i - hi) <= GATE + 1 ? SMOOTH_STONE : LIGHT_GRAY_CONCRETE;
                }
                v.set(i, -1, j, b);
            }
        }
    }

    // ------------------------------------------------------------------ 가림막

    private void hoarding() {
        int signEvery = 18;
        int count = 0;
        for (int j = 0; j < land.d; j++) {
            for (int i = 0; i < land.w; i++) {
                if (!panel(i, j)) {
                    continue;
                }
                boolean gateGap = j == gateJ && Math.abs(i - hi) <= GATE;
                if (gateGap) {
                    continue;
                }
                v.set(i, 0, j, PANEL_BASE);
                v.set(i, 1, j, PANEL);
                v.set(i, 2, j, PANEL);
                v.set(i, 3, j, PANEL_BAND);
                // 안쪽 철 기둥 (4칸마다)
                if (Math.floorMod(i + j, 4) == 0) {
                    for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int ni = i + dd[0], nj = j + dd[1];
                        if (inside(ni, nj)) {
                            v.fill(ni, 0, nj, ni, 3, nj, STEEL_POST);
                            break;
                        }
                    }
                }
                // 바깥 표지판
                for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ni = i + dd[0], nj = j + dd[1];
                    if (land.land(ni, nj) && !site(ni, nj) && !notch(ni, nj) && Math.floorMod(i + j, signEvery) == 9) {
                        v.set(ni, 2, nj, Blocks.wallSign("dark_oak", facing(dd), "white", true, "준서랜드", "테마파크", "건설 예정 부지", ""));
                        count++;
                    }
                }
            }
        }
    }

    private static String facing(int[] dd) {
        if (dd[0] == 1) {
            return "east";
        }
        if (dd[0] == -1) {
            return "west";
        }
        return dd[1] == 1 ? "south" : "north";
    }

    // ------------------------------------------------------------------ 출입문·경비실·세륜기

    private void gate() {
        // 문틀: 양쪽 기둥과 위 가로대 (트럭이 지나가게 6칸 높이)
        int p0 = hi - GATE - 1, p1 = hi + GATE + 1;
        v.fill(p0, 0, gateJ, p0, 6, gateJ, FRAME);
        v.fill(p1, 0, gateJ, p1, 6, gateJ, FRAME);
        v.fill(p0, 6, gateJ, p1, 6, gateJ, YELLOW_CONCRETE);
        for (int i = p0 + 1; i < p1; i += 2) {
            v.set(i, 6, gateJ, BLACK_CONCRETE); // 노랑·검정 안전 띠
        }
        v.set(hi, 5, gateJ + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "안전제일", "", ""));
        v.set(hi, 5, gateJ - 1, Blocks.wallSign("dark_oak", "north", "white", true, "", "안전제일", "", ""));
        // 바닥 레일과 옆으로 밀어 연 문짝 (가림막 안쪽에 겹쳐 둠)
        for (int i = hi - GATE; i <= hi + GATE; i++) {
            v.set(i, -1, gateJ, SMOOTH_STONE);
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 1; k <= GATE + 1; k++) {
                int i = hi + side * (GATE + 1 + k);
                if (inside(i, gateJ - 1)) {
                    v.set(i, 0, gateJ - 1, RAIL);
                    v.set(i, 1, gateJ - 1, PANEL);
                    v.set(i, 2, gateJ - 1, PANEL);
                    v.set(i, 3, gateJ - 1, RAIL);
                }
            }
        }
        // 진입 마당 쪽 가림막에 출입 안내와 건축허가 표지판
        int wi = hi - GATE - 3;
        v.set(wi, 2, gateJ + 1, Blocks.wallSign("birch", "south", "black", false, "관계자 외", "출입금지", "", ""));
        int bi = hi + GATE + 3;
        v.fill(bi, 1, gateJ + 1, bi + 4, 3, gateJ + 1, null);
        v.fill(bi, 1, gateJ, bi + 4, 3, gateJ, WHITE_CONCRETE);
        v.set(bi + 1, 2, gateJ + 1, Blocks.wallSign("birch", "south", "black", false, "건축허가표지", "공사명:", "준서랜드", "신축공사"));
        v.set(bi + 2, 2, gateJ + 1, Blocks.wallSign("birch", "south", "black", false, "건축주:", "준서랜드(주)", "시공자:", "준서건설"));
        v.set(bi + 3, 2, gateJ + 1, Blocks.wallSign("birch", "south", "black", false, "공사기간:", "2026.10 ~", "2029.12", ""));
        // 진입 마당 둘레 가로등
        for (int side = -1; side <= 1; side += 2) {
            int li = hi + side * (NOTCH - 1);
            int lj = gateJ + 3;
            if (land.land(li, lj)) {
                MarketPlan.lampPost(v, li, lj);
            }
        }
        guardBooth(hi - GATE - 6, gateJ - 6);
        wheelWash();
    }

    /** 경비실 (초소): 바깥 4×4, 안 2×2, 사방 창, 출입문 쪽(동쪽) 문 */
    private void guardBooth(int i0, int j0) {
        Block wall = WHITE_CONCRETE;
        v.fill(i0, -1, j0, i0 + 3, -1, j0 + 3, SMOOTH_STONE);
        v.walls(i0, 0, j0, i0 + 3, 2, j0 + 3, wall);
        for (int k = 1; k <= 2; k++) {
            v.set(i0 + k, 1, j0, GLASS_PANE);
            v.set(i0 + k, 1, j0 + 3, GLASS_PANE);
            v.set(i0, 1, j0 + k, GLASS_PANE);
        }
        v.set(i0 + 3, 1, j0 + 1, GLASS_PANE);
        Interior.door(Frame.of(v), i0 + 3, 0, j0 + 2, "pale_oak", "east");
        v.fill(i0 - 1, 3, j0 - 1, i0 + 4, 3, j0 + 4, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.set(i0 + 1, 0, j0 + 2, Furniture.DESK_TOP);
        v.set(i0 + 1, 0, j0 + 1, Furniture.DESK_TOP);
        Furniture.chair(Frame.of(v), i0 + 2, 0, j0 + 1, "east", "dark_oak");
        v.set(i0 + 1, 2, j0 + 1, Interior.LIGHT);
        v.set(i0 + 4, 2, j0 + 1, Blocks.wallSign("birch", "east", "black", false, "", "경비실", "", ""));
    }

    /** 세륜기: 출입문 안쪽 길에 바퀴 씻는 철망 바닥과 양옆 물 뿌리는 틀 */
    private void wheelWash() {
        int j1 = gateJ - 3, j0 = gateJ - 12;
        for (int j = j0; j <= j1; j++) {
            for (int i = hi - 3; i <= hi + 3; i++) {
                v.set(i, -1, j, (i + j) % 2 == 0 ? IRON_BLOCK : Block.of("iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
            }
            for (int side = -1; side <= 1; side += 2) {
                int i = hi + side * 4;
                v.set(i, 0, j, SMOOTH_STONE);
                if ((j - j0) % 3 == 0) {
                    v.fill(i, 1, j, i, 2, j, STEEL_POST);
                }
                v.set(i, 3, j, RAIL);
            }
        }
        // 물 탱크와 제어함
        v.fill(hi - 6, 0, j0, hi - 5, 1, j0 + 1, Block.of("light_blue_terracotta", 0x716C89));
        v.set(hi - 6, 0, j0 + 3, Block.of("blast_furnace[facing=east,lit=false]", 0x505050));
    }

    // ------------------------------------------------------------------ 현장 사무실 (컨테이너 2층)

    /**
     * 컨테이너 사무실: 바깥 17×7 상자 2층 (층고 4). 앞(남쪽, j 큰 쪽)에 1층 처마가 되는 2층 복도와 철계단.
     * 1층: 공무 사무실 + 회의실, 2층: 소장실 + 휴게실.
     */
    private void office(int i0, int j0) {
        int len = 17, dep = 7;
        if (!inside(i0, j0) || !inside(i0 + len + 1, j0 + dep + 4)) {
            return;
        }
        Frame f = new Frame(v, i0, j0, 0);
        int[] levels = {0, 4};
        for (int k = 0; k < 2; k++) {
            int y = levels[k];
            // 바닥·벽·천장
            f.fill(0, y - 1, 0, len - 1, y - 1, dep - 1, k == 0 ? FRAME : CONTAINER);
            for (int a = 0; a < len; a++) {
                for (int b = 0; b < dep; b++) {
                    boolean edgeA = a == 0 || a == len - 1, edgeB = b == 0 || b == dep - 1;
                    if (!edgeA && !edgeB) {
                        f.fill(a, y, b, a, y + 2, b, AIR);
                        continue;
                    }
                    boolean corner = edgeA && edgeB;
                    for (int yy = y; yy <= y + 2; yy++) {
                        f.set(a, yy, b, corner ? FRAME : (edgeA ? b : a) % 2 == 0 ? CONTAINER : CONTAINER_RIB);
                    }
                }
            }
            f.fill(0, y + 3, 0, len - 1, y + 3, dep - 1, k == 1 ? FRAME : CONTAINER);
            // 앞뒤 창 (알루미늄 창, 한 줄)
            for (int a = 2; a < len - 2; a += 3) {
                f.set(a, y + 1, dep - 1, GLASS_PANE);
                f.set(a + 1, y + 1, dep - 1, GLASS_PANE);
                f.set(a, y + 1, 0, GLASS_PANE);
            }
            // 칸막이와 문
            int split = k == 0 ? 11 : 6;
            f.fill(split, y, 1, split, y + 2, dep - 2, CONTAINER);
            Interior.door(f, 1, y, dep - 1, "pale_oak", "south");
            Interior.door(f, len - 2, y, dep - 1, "pale_oak", "south");
            f.set(split, y, 3, AIR);
            f.set(split, y + 1, 3, AIR);
            Interior.lights(f, 1, 1, len - 2, dep - 2, y, 4, 4, Interior.LIGHT);
            // 실외기
            f.set(len, y, 2, Block.of("iron_block", 0xDCDCDC));
        }
        // 1층 공무 사무실: 책상 두 줄과 도면 벽, 회의실: 긴 탁자
        for (int a = 3; a <= 9; a += 2) {
            Furniture.desk(f, a, 0, 2, "south");
        }
        f.set(1, 1, 1, Block.of("white_concrete", 0xCFD5D6));
        f.fill(1, 1, 1, 1, 2, 4, Block.of("light_blue_terracotta", 0x716C89)); // 벽에 붙인 공정표·도면
        Furniture.table(f, 13, 0, 3, 2, 1, "spruce");
        f.set(15, 1, 1, Furniture.TV);
        // 2층 소장실: 큰 책상과 소파, 휴게실: 식탁과 정수기
        Furniture.desk(f, 3, 4, 2, "south");
        Furniture.sofa(f, r, 1, 4, 4, 3, "north");
        Furniture.plant(f, r, 5, 4, 5);
        Furniture.table(f, 9, 4, 3, 3, 1, "oak");
        f.set(15, 4, 1, CAULDRON); // 정수기
        f.set(14, 4, 1, Furniture.FRIDGE);
        f.set(14, 5, 1, Furniture.FRIDGE);
        // 2층 복도 (1층 처마) 와 난간, 기둥
        Block deck = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);
        for (int a = 0; a < len; a++) {
            f.set(a, 3, dep, deck);
            f.set(a, 3, dep + 1, deck);
            f.set(a, 4, dep + 2, RAIL);
            if (a % 4 == 0 || a == len - 1) {
                f.fill(a, 0, dep + 1, a, 2, dep + 1, STEEL_POST);
            }
        }
        f.set(-1, 4, dep, RAIL);
        f.set(-1, 4, dep + 1, RAIL);
        f.set(len, 4, dep, RAIL);
        f.set(len, 4, dep + 1, RAIL);
        // 철계단: 복도 바깥쪽 두 칸, 동쪽 끝에서 서쪽으로 올라감
        for (int s = 0; s < 4; s++) {
            int a = len - 1 - s;
            f.set(a, s, dep + 2, STEP);
            f.set(a, s, dep + 3, STEP);
            f.set(a, 4, dep + 2, AIR);
            if (s > 0) {
                f.fill(a, 0, dep + 3, a, s - 1, dep + 3, null);
            }
            f.set(a, s + 1, dep + 4, RAIL);
            f.set(a, s + 2, dep + 4, RAIL);
        }
        f.set(len - 5, 4, dep + 2, AIR);
        f.set(len - 5, 4, dep + 3, RAIL);
        f.set(len - 6, 4, dep + 2, RAIL);
        // 간판
        f.set(dep / 2 + 4, 6, dep, Blocks.wallSign("dark_oak", "south", "white", true, "준서건설", "현장사무소", "", ""));
        f.set(7, 1, dep, Blocks.wallSign("birch", "south", "black", false, "", "공무 사무실", "", ""));
        f.set(12, 1, dep, Blocks.wallSign("birch", "south", "black", false, "", "회의실", "", ""));
        f.set(3, 5, dep, Blocks.wallSign("birch", "south", "black", false, "", "소장실", "", ""));
        f.set(10, 5, dep, Blocks.wallSign("birch", "south", "black", false, "", "휴게실", "", ""));
    }

    /** 이동식 화장실 2칸 (남·여): 바깥 3×3, 안 1칸, 앞(남쪽) 문 */
    private void toilets(int i0, int j0) {
        String[] labels = {"남자 화장실", "여자 화장실"};
        for (int k = 0; k < 2; k++) {
            int a = i0 + k * 4;
            if (!inside(a, j0) || !inside(a + 2, j0 + 3)) {
                continue;
            }
            Block shell = k == 0 ? Block.of("light_blue_terracotta", 0x716C89) : Block.of("cyan_terracotta", 0x565B5B);
            v.walls(a, 0, j0, a + 2, 2, j0 + 2, shell);
            v.fill(a, 3, j0, a + 2, 3, j0 + 2, WHITE_CONCRETE);
            v.set(a + 1, -1, j0 + 1, SMOOTH_STONE);
            v.set(a + 1, 0, j0 + 1, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
            Interior.door(Frame.of(v), a + 1, 0, j0 + 2, "birch", "south");
            v.set(a + 1, 2, j0 + 1, Interior.LIGHT);
            v.set(a, 1, j0 + 3, Blocks.wallSign("birch", "south", "black", false, "", labels[k], "", ""));
        }
    }

    // ------------------------------------------------------------------ 자재·장비

    private void materials() {
        int i0 = hi - NOTCH - 26, j0 = gateJ - 22;
        // 철근 다발 (침목 위에 쇠사슬 막대를 눕혀 쌓음)
        Block rebar = Block.of("iron_chain[axis=x,waterlogged=false]", 0x505050);
        for (int row = 0; row < 3; row++) {
            int j = j0 + row * 3;
            if (!inside(i0, j) || !inside(i0 + 12, j + 1)) {
                continue;
            }
            for (int i = i0; i <= i0 + 12; i++) {
                if ((i - i0) % 6 == 0) {
                    v.set(i, 0, j, SPRUCE_SLAB);
                    v.set(i, 0, j + 1, SPRUCE_SLAB);
                    continue;
                }
                v.set(i, 0, j, rebar);
                v.set(i, 0, j + 1, rebar);
                if (row < 2) {
                    v.set(i, 1, j, rebar);
                }
            }
        }
        // 거푸집 합판 더미와 각목
        int pi = i0 + 16;
        for (int k = 0; k < 2; k++) {
            int j = j0 + k * 4;
            if (inside(pi, j) && inside(pi + 4, j + 2)) {
                v.fill(pi, 0, j, pi + 4, 1, j + 2, Block.of("birch_planks", 0xC0AF79));
                v.fill(pi, 2, j, pi + 4, 2, j + 2, Block.of("birch_slab[type=bottom,waterlogged=false]", 0xC0AF79));
            }
        }
        if (inside(pi, j0 + 8) && inside(pi + 6, j0 + 9)) {
            v.fill(pi, 0, j0 + 8, pi + 6, 0, j0 + 9, Block.of("stripped_spruce_log[axis=x]", 0x6E5432));
        }
        // H형강 (회색 쇠 막대 눕혀 쌓음)
        int hj0 = j0 + 12;
        if (inside(i0, hj0) && inside(i0 + 14, hj0 + 3)) {
            for (int k = 0; k < 3; k++) {
                v.fill(i0, 0, hj0 + k, i0 + 14, 0, hj0 + k, Block.of("polished_basalt[axis=x]", 0x585A5B));
            }
            v.fill(i0 + 1, 1, hj0, i0 + 13, 1, hj0 + 1, Block.of("polished_basalt[axis=x]", 0x585A5B));
            v.set(i0 + 7, 0, hj0 + 3, Blocks.wallSign("birch", "south", "black", false, "", "H형강", "", ""));
        }
        // 벽돌·시멘트 팔레트
        for (int k = 0; k < 4; k++) {
            int a = i0 + 18 + k * 3, b = hj0 + 1;
            if (inside(a, b) && inside(a + 1, b + 1)) {
                v.fill(a, 0, b, a + 1, 0, b + 1, OAK_SLAB);
                v.fill(a, 1, b, a + 1, k % 2 == 0 ? 2 : 1, b + 1, k % 2 == 0 ? BRICKS : Block.of("white_wool", 0xE9ECEC));
            }
        }
        // 흙 더미
        int mi = land.w / 2 + 14, mj = land.d / 2 - 14;
        for (int y = 0; y <= 3; y++) {
            double rr = 5.5 - y * 1.4;
            v.cylinder(mi + 0.5, mj + 0.5, rr, y, y, y == 3 ? COARSE_DIRT : DIRT);
        }
    }

    /** 굴착기 (노란 몸체, 무한궤도, 붐·암·버킷을 땅에 내려놓음): 바깥 10×9 안 */
    private void excavator(int i0, int j0) {
        if (!inside(i0, j0) || !inside(i0 + 13, j0 + 6)) {
            return;
        }
        Block body = YELLOW_CONCRETE, track = BLACK_CONCRETE, steel = GRAY_CONCRETE;
        // 궤도 (앞뒤로 긴 두 줄)
        v.fill(i0, 0, j0, i0 + 7, 0, j0 + 1, track);
        v.fill(i0, 0, j0 + 4, i0 + 7, 0, j0 + 5, track);
        v.fill(i0 + 1, 0, j0 + 2, i0 + 6, 0, j0 + 3, steel);
        // 상부 몸체와 평형추, 운전석
        v.fill(i0 + 1, 1, j0, i0 + 6, 2, j0 + 5, body);
        v.fill(i0, 1, j0, i0, 2, j0 + 5, steel);
        v.fill(i0 + 4, 3, j0, i0 + 6, 4, j0 + 1, body);
        v.fill(i0 + 5, 3, j0, i0 + 6, 4, j0, BLACK_GLASS);
        v.fill(i0 + 6, 3, j0 + 1, i0 + 6, 4, j0 + 1, BLACK_GLASS);
        v.fill(i0 + 4, 5, j0, i0 + 6, 5, j0 + 1, body);
        v.set(i0 + 2, 3, j0 + 4, steel); // 배기구
        // 붐(위로)과 암(아래로), 버킷
        v.rod(i0 + 7, 3, j0 + 3, i0 + 10.5, 7.5, j0 + 3, 0.6, body);
        v.rod(i0 + 10.5, 7.5, j0 + 3, i0 + 12.5, 1, j0 + 3, 0.55, body);
        v.fill(i0 + 12, 0, j0 + 2, i0 + 13, 0, j0 + 4, steel);
        v.set(i0 + 13, 1, j0 + 3, steel);
    }

    /** 공사장 조명탑: 귀퉁이 안쪽에 철 기둥과 투광등 */
    private void floodlights() {
        int[][] spots = {{6, 6}, {land.w - 7, 6}, {6, land.d / 2}, {land.w - 7, land.d / 2}};
        for (int[] s : spots) {
            if (!inside(s[0], s[1])) {
                continue;
            }
            v.fill(s[0], 0, s[1], s[0], 9, s[1], STEEL_POST);
            v.set(s[0], 10, s[1], FRAME);
            v.set(s[0] - 1, 10, s[1], Block.of("pearlescent_froglight[axis=y]", 0xEDDBDC));
            v.set(s[0] + 1, 10, s[1], Block.of("pearlescent_froglight[axis=y]", 0xEDDBDC));
        }
    }
}
