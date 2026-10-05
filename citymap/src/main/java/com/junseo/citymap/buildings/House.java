package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 구역마다 다른 주택 (kind "house"): 동네마다 실제 서울 주택가처럼 생긴 낮은 집들.
 * <ul>
 *   <li>university (관악 녹두거리): 원룸 다가구 4~5층. 흰·회색 타일, 작은 창, 창마다 실외기, 1층 필로티 주차,
 *       우편함이 있는 공동현관과 계단실, 층마다 복도를 따라 원룸 여러 칸</li>
 *   <li>gwangjin (구의·자양): 갈색·베이지 벽돌 타일 다세대 빌라, 필로티 주차, 층마다 두 세대</li>
 *   <li>gangnam (논현): 돌 마감 고급 빌라, 큰 창과 유리 난간 발코니, 필로티 주차, 층마다 한 세대</li>
 *   <li>mapo (망원동): 붉은 벽돌 다세대, 반지하(길에서 내려가는 좁은 계단, 땅 높이 창과 방범창), 2층으로 오르는 바깥 계단</li>
 *   <li>hongdae (연남동): 2층 붉은·흰 벽돌 단독주택. 절반은 1층 카페로 고친 집 (낮은 벽돌 담 안 앞마당, 바깥 자리),
 *       평지붕 옥상 테라스</li>
 *   <li>yongsan·namsan (해방촌·후암동): 낡은 붉은 벽돌 다가구, 옥상에 초록 지붕 옥탑방, 파란 물탱크, 장독, 빨랫줄</li>
 *   <li>bukhansan (평창동): 큰 단독주택. 화강석·회벽, 검은 기와 모임지붕 또는 평지붕 테라스, 잔디 마당과 소나무,
 *       높은 돌담과 대문, 차고</li>
 *   <li>그 밖 (songpa·yeouido·junggu 등): 보통 다세대 빌라, 필로티 주차</li>
 * </ul>
 * 크게 네 갈래: 필로티 빌라(원룸·다세대·고급 빌라는 겉 재료와 창만 다름), 벽돌 다가구(반지하·옥탑방),
 * 연남동 2층 벽돌집(카페), 평창동 단독. 실내 배치는 함께 씁니다 (복도형·가로 복도형·ㄱ자형, 아래 "층 계획").
 * 살 만한 크기: 원룸 6×8 이상(욕실 2×3 포함), 투룸 넓이 90칸 이상에 현관 2×2·거실 4×5·주방 2×4·침실 둘 3×4·욕실 2×3.
 * 한 층에 투룸이 안 들어가는 좁은 땅은 두 층을 한 집으로 씁니다. 땅 크기는 w 12..24 × d 14..24, 1..6층.
 * 정면은 남쪽(j = d-1). 모든 층은 정면 입구에서 계단실(꺾인 계단)로 걸어서 오르고, 평지붕·옥탑이 있으면 옥상까지 오릅니다.
 * 필로티·차고의 주차 칸마다 차 꺼내는 자리({@link Voxels#carSpot})를 표시합니다 (차 앞이 정면, 앞으로 바로 나감).
 * 계단실은 늘 왼쪽 뒤 구석에 그리고, 절반은 마지막에 좌우를 뒤집습니다.
 */
final class House {
    enum Type { ONEROOM, VILLA, LUX, MANGWON, YEONNAM, HILLSIDE, PYEONGCHANG }

    static Type type(String district) {
        return switch (district == null ? "" : district) {
            case "university" -> Type.ONEROOM;
            case "gangnam" -> Type.LUX;
            case "mapo" -> Type.MANGWON;
            case "hongdae" -> Type.YEONNAM;
            case "yongsan", "namsan" -> Type.HILLSIDE;
            case "bukhansan" -> Type.PYEONGCHANG;
            default -> Type.VILLA;
        };
    }

    /** 변형 번호 0..3 (build 와 name 의 첫 난수라서 같은 씨앗이면 이름과 생김새가 맞음) */
    private static int variant(Random r) {
        return r.nextInt(4);
    }

    /** 연남동 집 중 카페로 고친 변형 */
    private static boolean cafeVariant(Type t, int variant) {
        return t == Type.YEONNAM && variant < 2;
    }

    /** 이 구역 주택 이름 (같은 씨앗의 Random 이면 build 결과와 맞음: 예) 연남동 카페 주택) */
    static String name(String district, Random r) {
        Type t = type(district);
        int k = variant(r);
        return switch (t) {
            case ONEROOM -> k == 1 ? "원룸 다가구" : k == 3 ? "다가구 원룸" : "원룸";
            case VILLA -> "gwangjin".equals(district) ? (k % 2 == 0 ? "다세대 빌라" : "필로티 빌라") : (k % 2 == 0 ? "다세대 주택" : "빌라");
            case LUX -> k % 2 == 0 ? "고급 빌라" : "논현동 빌라";
            case MANGWON -> k % 2 == 0 ? "다세대 주택" : "반지하 다세대";
            case YEONNAM -> cafeVariant(t, k) ? "연남동 카페 주택" : "단독주택";
            case HILLSIDE -> k % 2 == 0 ? "다가구 주택" : "옥탑방 주택";
            case PYEONGCHANG -> k % 2 == 0 ? "평창동 단독주택" : "단독주택";
        };
    }

    /**
     * 구역마다 다른 주택.
     *
     * @param w      너비 12..24 (땅 폭)
     * @param d      깊이 14..24 (정면 = 남쪽, 길이나 3칸 뒷골목)
     * @param floors 땅 위 층수 1..6 (필로티 1층도 한 층). 반지하는 따로 (망원동)
     */
    static Voxels build(String district, int w, int d, int floors, Random r) {
        House h = new House(district, w, d, floors, r);
        h.make();
        return h.v;
    }

    // ------------------------------------------------------------------ 재료

    static final Block CALCITE = Block.of("calcite", 0xDFE0DC);
    static final Block WHITE_BRICK = Block.of("quartz_bricks", 0xEAE5DD);
    static final Block TERRA = Block.of("terracotta", 0x985E43);
    static final Block PACKED_MUD = Block.of("packed_mud", 0x8E6B50);
    static final Block CUT_SANDSTONE = Block.of("cut_sandstone", 0xD9CE9F);
    static final Block FRAME_DARK = Block.of("polished_deepslate", 0x484849);
    static final Block ROOF_GREEN = Block.of("green_concrete", 0x495B24);
    static final Block TANK_BLUE = Block.of("light_blue_concrete", 0x2389C7);
    static final Block AC = Block.of("smooth_quartz", 0xECE6DF);
    static final Block AC_RACK = Block.of("iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1);
    static final Block GAS = Block.of("yellow_stained_glass_pane", 0xE5E533);
    static final Block SILL = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);
    static final Block CAP = Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E);
    static final Block DECK = Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430);
    static final Block BRICK_CAP = Block.of("brick_slab[type=bottom,waterlogged=false]", 0x966153);
    static final Block TILE_ROOF = Block.of("deepslate_tiles", 0x363637);
    static final Block LAWN = Block.of("grass_block[snowy=false]", 0x6FA552);
    static final Block PINE_LOG = Block.of("spruce_log[axis=y]", 0x3A2A1A);
    static final Block PINE_LEAVES = Block.of("spruce_leaves[distance=7,persistent=true,waterlogged=false]", 0x3D5E3A);
    static final Block SHRUB = Block.of("azalea_leaves[distance=7,persistent=true,waterlogged=false]", 0x5A7A35);
    static final Block PAVE = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block ASPHALT = Block.of("gray_concrete", 0x36393D);
    static final Block LINE = Block.of("white_concrete", 0xCFD5D6);
    static final Block LIGHT = Interior.LIGHT;
    static final Block RAIL = Block.of("iron_bars", 0x888888);
    static final Block GLASS_RAIL = Block.of("glass_pane", 0xC8DCE4);

    /** {벽, 띠·창틀, 1층·바탕, 창 유리} */
    private static Block[] skin(Type t, String district, int k) {
        Block pane = GLASS_PANE;
        return switch (t) {
            case ONEROOM -> new Block[][]{
                    {CALCITE, SMOOTH_STONE, POLISHED_ANDESITE, pane},
                    {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, pane},
                    {POLISHED_DIORITE, WHITE_CONCRETE, POLISHED_ANDESITE, pane},
                    {SMOOTH_QUARTZ, GRAY_CONCRETE, POLISHED_DEEPSLATE, pane}}[k];
            case VILLA -> "gwangjin".equals(district) ? new Block[][]{
                    {MUD_BRICKS, SANDSTONE, POLISHED_GRANITE, pane},
                    {TERRA, WHITE_TERRACOTTA, POLISHED_GRANITE, pane},
                    {SANDSTONE, MUD_BRICKS, POLISHED_GRANITE, pane},
                    {PACKED_MUD, SANDSTONE, STONE_BRICKS, pane}}[k] : new Block[][]{
                    {BRICKS, SMOOTH_STONE, POLISHED_GRANITE, pane},
                    {WHITE_TERRACOTTA, LIGHT_GRAY_TERRACOTTA, POLISHED_GRANITE, pane},
                    {SANDSTONE, BRICKS, POLISHED_ANDESITE, pane},
                    {BRICKS, WHITE_CONCRETE, POLISHED_ANDESITE, pane}}[k];
            case LUX -> new Block[][]{
                    {SANDSTONE, FRAME_DARK, CUT_SANDSTONE, GRAY_PANE},
                    {CALCITE, FRAME_DARK, POLISHED_DIORITE, GRAY_PANE},
                    {POLISHED_ANDESITE, BLACK_CONCRETE, POLISHED_DEEPSLATE, GRAY_PANE},
                    {POLISHED_DIORITE, BROWN_CONCRETE, POLISHED_GRANITE, GRAY_PANE}}[k];
            case MANGWON -> new Block[][]{
                    {BRICKS, SMOOTH_STONE, BRICKS, pane},
                    {BRICKS, WHITE_CONCRETE, POLISHED_GRANITE, pane},
                    {BRICKS, SMOOTH_STONE, STONE_BRICKS, pane},
                    {MUD_BRICKS, SMOOTH_STONE, BRICKS, pane}}[k];
            case YEONNAM -> k != 3
                    ? new Block[]{BRICKS, SMOOTH_STONE, BRICKS, pane}
                    : new Block[]{WHITE_BRICK, LIGHT_GRAY_CONCRETE, WHITE_BRICK, pane};
            case HILLSIDE -> new Block[][]{
                    {BRICKS, SMOOTH_STONE, COBBLESTONE, pane},
                    {BRICKS, WHITE_CONCRETE, STONE_BRICKS, pane},
                    {WHITE_TERRACOTTA, SMOOTH_STONE, COBBLESTONE, pane},
                    {BRICKS, SMOOTH_STONE, MOSSY_STONE_BRICKS, pane}}[k];
            case PYEONGCHANG -> new Block[][]{
                    {SANDSTONE, CUT_SANDSTONE, STONE_BRICKS, pane},
                    {BRICKS, SMOOTH_STONE, POLISHED_GRANITE, pane},
                    {CALCITE, FRAME_DARK, POLISHED_ANDESITE, GRAY_PANE},
                    {POLISHED_ANDESITE, FRAME_DARK, STONE_BRICKS, GRAY_PANE}}[k];
        };
    }

    // ------------------------------------------------------------------ 집·방 기록 (검사용)

    /** 방 하나의 이름과 안쪽 크기 (벽 안쪽, 칸) */
    record Space(String name, int w, int d) {
        /** a×b 가 (어느 방향으로든) 들어가는지 */
        boolean fits(int a, int b) {
            return Math.min(w, d) >= Math.min(a, b) && Math.max(w, d) >= Math.max(a, b);
        }
    }

    /** 한 집(세대): 종류("원룸", "투룸", "단독", "카페"), 방들, 안쪽 넓이(칸, 계단실·공용 복도 제외), 쓰는 층 수 */
    static final class Dwelling {
        final String kind;
        final List<Space> rooms = new ArrayList<>();
        int area;
        int levels = 1;

        Dwelling(String kind) {
            this.kind = kind;
        }

        Dwelling room(String name, int w, int d) {
            rooms.add(new Space(name, w, d));
            return this;
        }

        List<Space> named(String prefix) {
            return rooms.stream().filter(s -> s.name().startsWith(prefix)).toList();
        }

        @Override
        public String toString() {
            return kind + "(" + area + "칸, " + levels + "층) " + rooms;
        }
    }

    // ------------------------------------------------------------------ 상태

    private Voxels v;
    private final Random r;
    private final String district;
    private final Type type;
    private final int variant, w, d, floors;
    private final Block wall, trim, plinth, glass;
    private final boolean cafe;
    /** 바깥벽 i0..i1, 뒤 j0, 정면 벽 jf */
    int i0, i1, j0, jf;
    /** 서는 높이: lv[0..floors-1] 층, lv[floors] 옥상 */
    int[] lv;
    boolean piloti, basement, elevator, hipRoof, rooftopRoom;
    /** 계단실 (벽 포함) i cx0..cx1, j j0..cz1. 문은 (stairI, cz1) */
    int cx0, cx1, cz1, stairI;
    /** 반지하 내려가는 계단 자리 i (없으면 -1) */
    private int pitI = -1;
    /** 평창동 차고: 0 = 없음 / 1 = 마당 차고 / 3 = 돌담 안 주차 자리 */
    private int garage;
    /** 층마다 쓰임 (계획 결과) */
    final String[] floorPlan;
    final List<Dwelling> dwellings = new ArrayList<>();

    private House(String district, int w, int d, int floors, Random r) {
        this.district = district == null ? "" : district;
        this.type = type(district);
        this.r = r;
        this.variant = variant(r);
        this.w = Math.max(12, w);
        this.d = Math.max(14, d);
        this.floors = Math.max(1, Math.min(floors, 12));
        Block[] s = skin(type, this.district, variant);
        wall = s[0];
        trim = s[1];
        plinth = s[2];
        glass = s[3];
        cafe = cafeVariant(type, variant);
        floorPlan = new String[this.floors];
        v = new Voxels(w, d, -4, 4 * this.floors + 14);
    }

    /** 검사용: 집을 짓고 기록(세대·방)까지 돌려줌 */
    static House make(String district, int w, int d, int floors, Random r) {
        House h = new House(district, w, d, floors, r);
        h.make();
        return h;
    }

    Voxels voxels() {
        return v;
    }

    // ------------------------------------------------------------------ 순서

    private void make() {
        setup();
        site();
        shell();
        core();
        floors();
        if (basement) {
            basement();
        }
        if (piloti) {
            piloti();
        }
        front();
        roof();
        details();
        if (r.nextBoolean()) {
            mirror();
        }
        v.connect();
    }

    int a0() {
        return i0 + 1;
    }

    int a1() {
        return i1 - 1;
    }

    int b0() {
        return j0 + 1;
    }

    int b1() {
        return jf - 1;
    }

    /** 바깥벽 크기, 마당 깊이, 층 높이, 계단실 자리, 필로티·반지하·옥탑 같은 선택 */
    private void setup() {
        int side = switch (type) {
            case PYEONGCHANG -> w >= 20 ? 2 : w >= 18 ? 1 : 0;
            case YEONNAM -> w >= 15 ? 1 : 0;
            default -> w >= 22 ? 1 : 0;
        };
        i0 = side;
        i1 = w - 1 - side;
        j0 = d >= 18 ? 1 : 0;
        int fy = switch (type) {
            case MANGWON -> 2;
            case YEONNAM -> Math.max(2, Math.min(5, d - j0 - 13));
            case PYEONGCHANG -> Math.max(2, Math.min(8, d - j0 - 14));
            case HILLSIDE -> d >= 18 ? 2 : 1;
            default -> d >= 18 ? 2 : 1;
        };
        // 몸채 안쪽 깊이 11칸 이상 (계단실 6 + 앞 5)
        fy = Math.max(1, Math.min(fy, d - j0 - 13));
        jf = d - 1 - fy;
        cx0 = i0;
        cx1 = i0 + 4;
        stairI = cx0 + 3;
        hipRoof = type == Type.PYEONGCHANG && variant < 2 && floors >= 2;
        piloti = (type == Type.ONEROOM || type == Type.VILLA || type == Type.LUX) && floors >= 2;
        elevator = floors >= 6;
        basement = type == Type.MANGWON && fy >= 2 && a1() - cx1 >= 6 && !elevator;
        rooftopRoom = type == Type.HILLSIDE || (type == Type.MANGWON && variant == 3) || (type == Type.ONEROOM && variant == 1);
        int g = basement ? 2 : 0;
        lv = new int[floors + 1];
        for (int k = 0; k <= floors; k++) {
            lv[k] = g + 4 * k;
        }
        cz1 = j0 + Interior.stairDepth(coreLevels()) + 1;
    }

    /** 계단실이 서는 높이들 (모임지붕이면 옥상 없음) */
    int[] coreLevels() {
        return hipRoof ? java.util.Arrays.copyOf(lv, floors) : lv;
    }

    int roofY() {
        return lv[floors] - 1;
    }

    // ------------------------------------------------------------------ 몸체

    /** 바닥판, 바깥벽, 층 띠, 1층 바탕돌 */
    private void shell() {
        if (basement) {
            // 반지하: 땅을 파고 바닥·벽, 계단실·복도 밑은 기초로 채움
            v.fill(i0, -4, j0, i1, -4, jf, STONE);
            v.fill(i0, -3, j0, i1, 0, jf, wall);
            v.fill(cx1 + 1, -2, b0(), a1(), 0, b1(), AIR);
            v.fill(cx1 + 1, -3, b0(), a1(), -3, b1(), SMOOTH_STONE);
        } else if (lv[0] > 0) {
            v.fill(i0, 0, j0, i1, lv[0] - 1, jf, wall);
        }
        for (int k = 0; k <= floors; k++) {
            int y = lv[k] - 1;
            Block slab = k == 0 && piloti ? ASPHALT : k == 0 && lv[0] == 0 ? POLISHED_ANDESITE : SMOOTH_STONE;
            v.fill(i0, y, j0, i1, y, jf, slab);
        }
        v.walls(i0, lv[0], j0, i1, roofY(), jf, wall);
        for (int k = 0; k < floors; k++) {
            v.fill(a0(), lv[k], b0(), a1(), lv[k] + 2, b1(), AIR);
        }
        for (int k = 1; k < floors; k++) {
            v.walls(i0, lv[k] - 1, j0, i1, lv[k] - 1, jf, trim);
        }
        // 1층 바탕 마감 (필로티 아닌 집)
        if (!piloti && plinth != wall) {
            int top = type == Type.PYEONGCHANG || type == Type.LUX ? lv[0] + 2 : lv[0];
            v.walls(i0, basement ? -1 : 0, j0, i1, top, jf, plinth);
        }
        v.walls(i0, roofY(), j0, i1, roofY(), jf, trim);
    }

    private Frame coreFrame() {
        return Frame.facing(v, stairI, cz1 - 1, "north");
    }

    /** 왼쪽 뒤 계단실 (1칸 줄 꺾인 계단), 계단참 창, 옥상 옥탑, 6층 이상은 엘리베이터(복도 앞 끝) */
    private void core() {
        String stair = switch (type) {
            case PYEONGCHANG, YEONNAM -> "oak";
            case LUX -> "polished_andesite";
            default -> "stone_brick";
        };
        int rgb = switch (stair) {
            case "oak" -> 0xA2834F;
            case "polished_andesite" -> 0x848685;
            default -> 0x7A7979;
        };
        int[] cl = coreLevels();
        Interior.stairCore(coreFrame(), cl, wall, stair, rgb, 1);
        int n = cl.length;
        if (!hipRoof) {
            // 옥탑 계단실 지붕
            v.fill(cx0, cl[n - 1] + 3, j0, cx1, cl[n - 1] + 3, cz1, SMOOTH_STONE);
            v.fill(cx0, cl[n - 1] + 4, j0, cx1, cl[n - 1] + 4, cz1, CAP);
        }
        for (int k = 0; k + 1 < n; k++) {
            v.set(cx0 + 2, cl[k] + 3, j0, glass);
        }
        if (elevator) {
            // 엘리베이터: 복도 앞 끝 (승강로 a0+1..a0+2 × b1-1..b1), 문은 뒤(계단실 쪽)를 봄
            Interior.elevator(Frame.facing(v, a0() + 2, b1(), "north"), java.util.Arrays.copyOf(lv, floors), wall);
        }
    }

    // ------------------------------------------------------------------ 층 계획
    //
    // 계단실은 왼쪽 뒤 구석 (i0..cx1, j0..cz1). 나머지 바닥을 쓰는 방법:
    //  - 복도형 (hall): 계단실 앞 3칸 폭 공용 복도가 정면 벽까지, 오른쪽 R = (cx1+1..a1) × (b0..b1) 를 앞뒤로 나눠 세대.
    //    1층이면 복도 앞 끝이 공동현관, 6층 이상은 복도 앞 끝에 엘리베이터.
    //  - ㄱ자형 (L): 한 층 한 세대. 계단실 문이 곧 현관(E: 계단실 앞 3×2), 옆 S = (cx1+1..a1) × (b0..cz1-1) 에 침실·욕실,
    //    앞 FL = (a0..a0+2) × (cz1+4..b1) 에 방 하나, 나머지 LK = (a0+4..a1) × (cz1+1..b1) 이 거실·주방.
    //  - 가로 복도형 (cross, 원룸 건물): 계단실 앞에서 가로로 지나는 2칸 복도, 뒤 S 와 앞 F 에 원룸.

    static final Block IN = Interior.INNER_WALL;

    int ws() {
        return a1() - cx1;
    }

    int din() {
        return b1() - b0() + 1;
    }

    /** ㄱ자형 앞 방(FL) 깊이 */
    int dfl() {
        return b1() - cz1 - 3;
    }

    private void floors() {
        if (type == Type.PYEONGCHANG || (type == Type.YEONNAM && !cafe && floors <= 2)) {
            Dwelling h = new Dwelling("단독");
            h.levels = floors;
            for (int k = 0; k < floors; k++) {
                lFloor(k, k == 0 ? "1층" : "2층", h);
            }
            dwellings.add(h);
            return;
        }
        int k = 0;
        if (piloti) {
            floorPlan[0] = "필로티";
            k = 1;
        } else {
            String kind = cafe ? "카페" : type == Type.ONEROOM || !tworoomFitsR() ? "원룸" : "투룸";
            hallFloor(0, kind);
            k = 1;
        }
        boolean lTwo = lTwoFits();
        while (k < floors) {
            if (type == Type.ONEROOM) {
                if (!elevator && crossCount() > hallCount("원룸")) {
                    crossFloor(k);
                } else {
                    hallFloor(k, "원룸");
                }
                k++;
            } else if (elevator) {
                hallFloor(k, tworoomFitsR() ? "투룸" : "원룸");
                k++;
            } else if (lTwo) {
                lFloor(k, "투룸", null);
                k++;
            } else if (k + 1 < floors) {
                // 한 층에 투룸이 안 들어가면 두 층을 한 집으로 (아래층 거실·주방, 위층 침실)
                Dwelling dup = new Dwelling("투룸");
                dup.levels = 2;
                lFloor(k, "아래층", dup);
                lFloor(k + 1, "위층", dup);
                dwellings.add(dup);
                k += 2;
            } else {
                lFloor(k, "원룸", null);
                k++;
            }
        }
    }

    // ------------------------------------------------------------------ 복도형

    /** R 영역(복도 오른쪽)에 투룸이 들어가는지: 앞뒤 길이 W_f, 폭 D_f = ws */
    boolean tworoomFitsR() {
        return tworoomMin(ws()) > 0 && din() >= tworoomMin(ws());
    }

    /** 폭(문 벽에서 창까지) depth 인 투룸이 차지할 최소 길이 (0 = 안 됨) */
    static int tworoomMin(int depth) {
        return depth >= 13 ? 9 : depth >= 10 ? 12 : 0;
    }

    /** 폭 depth 인 원룸 최소 길이 */
    static int oneroomMin(int depth) {
        return depth >= 8 ? 6 : depth >= 6 ? 8 : 0;
    }

    int hallCount(String kind) {
        List<int[]> parts = hallParts(kind);
        return parts == null ? 0 : parts.size();
    }

    /** 복도형 세대 나누기 (j 범위들). 첫 세대는 계단실 앞(cz1+1)까지 닿아야 문을 냄 */
    List<int[]> hallParts(String kind) {
        int min = kind.equals("투룸") ? tworoomMin(ws()) : kind.equals("카페") ? din() : oneroomMin(ws());
        if (min <= 0) {
            return null;
        }
        int hallEnd = elevator ? b1() - 3 : b1();
        // 첫 세대(계단실 옆)는 복도(cz1+1..)에 문을 내려고 cz1+2 까지 닿아야 함
        int first = Math.max(min, cz1 + 2 - b0() + 1);
        if (first > din()) {
            return null;
        }
        int n = 1 + Math.max(0, (din() - first) / (min + 1));
        while (n > 1 && b1() - (din() - first - (n - 1)) / (n - 1) + 2 > hallEnd) {
            n--;
        }
        List<int[]> out = new ArrayList<>();
        int len = n > 1 ? (din() - first - (n - 1)) / (n - 1) : 0;
        first = din() - (n - 1) * (len + 1);
        int s = b0();
        for (int u = 0; u < n; u++) {
            int e = s + (u == 0 ? first : len) - 1;
            out.add(new int[]{s, e});
            s = e + 2;
        }
        return out;
    }

    /** 복도 칸에서 세대 문 자리 (j): 문이 세대 한쪽 끝(현관)에 오게 */
    private int hallDoor(int[] part, int hallEnd) {
        int j = part[0] + 1 >= cz1 + 2 ? part[0] + 1 : part[1] - 1;
        return Math.max(cz1 + 1, Math.min(j, hallEnd));
    }

    private void hallFloor(int k, String kind) {
        int L = lv[k];
        boolean ground = k == 0;
        floorPlan[k] = "복도형 " + kind;
        wallI(cx1, cz1 + 1, b1(), L);
        int hallEnd = b1();
        if (elevator) {
            hallEnd = b1() - 3;
        }
        v.fill(a0(), L - 1, cz1 + 1, cx1 - 1, L - 1, hallEnd, Interior.LANDING);
        for (int j = cz1 + 2; j <= hallEnd; j += 3) {
            v.set(a0() + 1, L + 2, j, LIGHT);
        }
        List<int[]> parts = hallParts(kind);
        if (parts == null) {
            parts = List.of(new int[]{b0(), b1()});
            kind = "창고";
        }
        for (int u = 0; u < parts.size(); u++) {
            int[] pt = parts.get(u);
            if (u > 0) {
                wallJ(pt[0] - 1, cx1 + 1, a1(), L);
            }
            int dj = hallDoor(pt, hallEnd);
            P p = room(cx1 + 1, pt[0], a1(), pt[1], "west", dj, L);
            Dwelling dw;
            switch (kind) {
                case "투룸" -> {
                    p.door("pale_oak");
                    dw = twoRoom(p);
                }
                case "카페" -> {
                    dw = cafeRoom(p);
                }
                case "창고" -> {
                    p.door("spruce");
                    storage(p);
                    dw = null;
                }
                default -> {
                    p.door("pale_oak");
                    dw = oneRoom(p);
                }
            }
            if (dw != null) {
                dw.area = (a1() - cx1) * (pt[1] - pt[0] + 1);
                dwellings.add(dw);
            }
            windowsRoom(cx1 + 1, pt[0], a1(), pt[1], L, kind.equals("카페"));
        }
        // 복도 앞 창 (1층은 공동현관 자리라서 비움)
        if (!ground && !elevator) {
            v.set(a0() + 1, L + 1, jf, glass);
            v.set(a0() + 1, L + 2, jf, glass);
        }
    }

    // ------------------------------------------------------------------ 가로 복도형 (원룸)

    int crossCount() {
        int[] c = crossSplit();
        return c[0] + c[1];
    }

    /** {뒤 S 원룸 수, 앞 F 원룸 수, F 깊이} */
    private int[] crossSplit() {
        int nS = ws() >= 8 ? (ws() + 1) / 9 : 0;
        int df = b1() - cz1 - 4;
        int min = oneroomMin(df);
        int wIn = a1() - a0() + 1;
        int nF = min > 0 ? (wIn + 1) / (min + 1) : 0;
        return new int[]{nS, nF, df};
    }

    private void crossFloor(int k) {
        int L = lv[k];
        floorPlan[k] = "가로 복도형 원룸";
        int[] c = crossSplit();
        // 복도: 계단실 문 앞 한 줄 + 가로 2줄
        wallJ(cz1 + 1, cx1, a1(), L);
        wallI(cx1, cz1 + 1, cz1 + 1, L);
        if (c[1] > 0) {
            wallJ(cz1 + 4, a0(), a1(), L);
        }
        int corrEnd = c[1] > 0 ? cz1 + 3 : b1();
        v.fill(a0(), L - 1, cz1 + 1, cx1 - 1, L - 1, cz1 + 1, Interior.LANDING);
        v.fill(a0(), L - 1, cz1 + 2, a1(), L - 1, corrEnd, Interior.LANDING);
        for (int i = a0() + 1; i <= a1(); i += 4) {
            v.set(i, L + 2, cz1 + 2, LIGHT);
        }
        // 뒤 S: 계단실 옆, 깊이 6
        for (int[] u : splitRow(cx1 + 1, a1(), c[0], b0(), cz1, L)) {
            P p = room(u[0], b0(), u[1], cz1, "south", u[0] + 1, L);
            p.door("pale_oak");
            Dwelling dw = oneRoom(p);
            dw.area = (u[1] - u[0] + 1) * (cz1 - b0() + 1);
            dwellings.add(dw);
            windowsRoom(u[0], b0(), u[1], cz1, L, false);
        }
        for (int[] u : splitRow(a0(), a1(), c[1], cz1 + 5, b1(), L)) {
            P p = room(u[0], cz1 + 5, u[1], b1(), "north", u[1] - 1, L);
            p.door("pale_oak");
            Dwelling dw = oneRoom(p);
            dw.area = (u[1] - u[0] + 1) * (b1() - cz1 - 4);
            dwellings.add(dw);
            windowsRoom(u[0], cz1 + 5, u[1], b1(), L, false);
        }
        if (c[0] == 0 && ws() >= 1) {
            P p = room(cx1 + 1, b0(), a1(), cz1, "south", cx1 + 1 + Math.min(1, ws() - 1), L);
            p.door("spruce");
            storage(p);
        }
    }

    /** a..b 를 n 칸({시작, 끝})으로 나누고 사이에 벽 (j 범위 ja..jb). n = 0 이면 빈 목록 */
    private List<int[]> splitRow(int a, int b, int n, int ja, int jb, int L) {
        List<int[]> out = new ArrayList<>();
        if (n <= 0) {
            return out;
        }
        int len = (b - a + 1 - (n - 1)) / n;
        int s = a;
        for (int u = 0; u < n; u++) {
            int e = u == n - 1 ? b : s + len - 1;
            out.add(new int[]{s, e});
            if (u < n - 1) {
                wallI(e + 1, ja, jb, L);
            }
            s = e + 2;
        }
        return out;
    }

    // ------------------------------------------------------------------ ㄱ자형 (한 층 한 집, 두 층 한 집, 단독)

    /** 한 층에 투룸(침실 둘·욕실·거실·주방·현관)이 들어가는지 */
    boolean lTwoFits() {
        int ws = ws(), dl = b1() - cz1;
        if (ws < 6 || dl < 5) {
            return false;
        }
        if (ws >= 11) {
            return true;
        }
        return ws >= 7 ? dfl() >= 2 : dfl() >= 4;
    }

    /** 계단실 옆(S) 방 이름들 */
    private String[] sProgram(String mode) {
        int ws = ws();
        return switch (mode) {
            case "투룸" -> ws >= 11 ? new String[]{"침실", "욕실", "침실"} : ws >= 7 ? new String[]{"침실", "침실"} : new String[]{"침실", "욕실"};
            case "위층" -> ws >= 7 ? new String[]{"침실", "침실"} : new String[]{"침실", "욕실"};
            case "아래층" -> new String[]{"침실", "욕실"};
            case "원룸" -> new String[]{"욕실", "드레스룸"};
            case "1층" -> floors == 1 ? (ws >= 11 ? new String[]{"침실", "욕실", "침실"} : new String[]{"침실", "욕실"})
                    : ws >= 7 ? new String[]{"주방", "욕실"} : new String[]{"주방"};
            default -> houseFrontRooms()
                    ? (ws >= 13 ? new String[]{"안방", "욕실", "침실"} : ws >= 8 ? new String[]{"안방", "욕실"} : new String[]{"침실", "욕실"})
                    : ws >= 9 ? new String[]{"안방", "침실"} : ws >= 7 ? new String[]{"침실", "침실"} : new String[]{"침실", "욕실"};
        };
    }

    /** 단독 위층: 거실 자리를 복도 + 앞 침실들로 나눌 수 있는지 */
    boolean houseFrontRooms() {
        return b1() - cz1 - 2 >= 3 && ws() >= 4;
    }

    private static int minWidth(String name) {
        return switch (name) {
            case "안방" -> 5;
            case "주방" -> 4;
            case "침실" -> 3;
            default -> 2;
        };
    }

    private static int growWeight(String name) {
        return switch (name) {
            case "안방", "주방" -> 3;
            case "침실" -> 2;
            default -> 1;
        };
    }

    /** a..b 를 이름들 최소 폭대로 나누고 남는 폭은 무게대로 (욕실·드레스룸은 3칸까지). 사이 벽 1칸 */
    static List<int[]> partition(int a, int b, String[] names) {
        int n = names.length, extra = b - a + 1 - (n - 1);
        int[] wd = new int[n];
        for (int k = 0; k < n; k++) {
            wd[k] = minWidth(names[k]);
            extra -= wd[k];
        }
        while (extra > 0) {
            boolean grew = false;
            for (int k = 0; k < n && extra > 0; k++) {
                boolean capped = growWeight(names[k]) == 1 && wd[k] >= 3;
                if (!capped) {
                    int g = Math.min(extra, growWeight(names[k]));
                    wd[k] += g;
                    extra -= g;
                    grew = true;
                }
            }
            if (!grew) {
                wd[0] += extra;
                extra = 0;
            }
        }
        List<int[]> out = new ArrayList<>();
        int s = a;
        for (int k = 0; k < n; k++) {
            out.add(new int[]{s, s + wd[k] - 1});
            s += wd[k] + 1;
        }
        return out;
    }

    /** 앞 방(FL) 쓰임 (null = 없음) */
    private String flProgram(String mode, String[] s) {
        if (dfl() < 2) {
            return null;
        }
        boolean bath = java.util.Arrays.asList(s).contains("욕실");
        long beds = java.util.Arrays.stream(s).filter(x -> x.equals("침실") || x.equals("안방")).count();
        return switch (mode) {
            case "투룸" -> beds < 2 ? (dfl() >= 4 ? "침실" : "다용도실") : !bath ? "욕실" : "다용도실";
            case "아래층" -> !bath ? "욕실" : "다용도실";
            case "위층" -> !bath ? "욕실" : dfl() >= 4 ? "서재" : "다용도실";
            case "원룸" -> "다용도실";
            case "1층" -> floors == 1 && beds < 2 && dfl() >= 4 ? "침실" : !bath ? "욕실" : dfl() >= 4 ? "손님방" : "다용도실";
            default -> !bath ? "욕실" : dfl() >= 4 ? "침실" : "욕실";
        };
    }

    /**
     * ㄱ자형 한 층. mode: "투룸"(한 층 한 집), "아래층"·"위층"(두 층 한 집), "원룸", "1층"·"2층"(단독주택).
     * into 가 있으면 그 집에 방을 보탬.
     */
    private void lFloor(int k, String mode, Dwelling into) {
        int L = lv[k];
        boolean house = mode.equals("1층") || mode.equals("2층");
        floorPlan[k] = "ㄱ자형 " + mode;
        Dwelling dw = into != null ? into : new Dwelling(mode.equals("원룸") ? "원룸" : "투룸");
        int lx0 = a0() + 4, dl = b1() - cz1;
        // 현관 (계단실 문 앞 3×2)
        v.fill(a0(), L - 1, cz1 + 1, a0() + 2, L - 1, cz1 + 2, house ? Interior.LANDING : Interior.TILE);
        if (!house) {
            v.set(stairI, L, cz1, Blocks.door("pale_oak", "south", false));
            v.set(stairI, L + 1, cz1, Blocks.door("pale_oak", "south", true));
            v.set(stairI, L + 2, cz1, wall);
            v.set(a0(), L, cz1 + 1, Rooms.WARDROBE);
            dw.room("현관", 3, 2);
        }
        // 계단실 옆 방들
        String[] sn = sProgram(mode);
        wallJ(cz1, cx1 + 1, a1(), L);
        List<int[]> sr = partition(cx1 + 1, a1(), sn);
        for (int q = 0; q < sn.length; q++) {
            int ia = sr.get(q)[0], ib = sr.get(q)[1];
            if (q > 0) {
                wallI(ia - 1, b0(), cz1 - 1, L);
            }
            int door = ib - ia >= 2 ? ia + 1 : ia;
            P p = room(ia, b0(), ib, cz1 - 1, "south", door, L);
            p.door(sn[q].equals("드레스룸") ? "spruce" : "pale_oak");
            furnish(p, sn[q]);
            dw.room(sn[q], ib - ia + 1, cz1 - b0());
            windowsRoom(ia, b0(), ib, cz1 - 1, L, false);
        }
        // 앞 방
        String fl = flProgram(mode, sn);
        if (fl != null) {
            wallJ(cz1 + 3, a0(), a0() + 3, L);
            wallI(a0() + 3, cz1 + 3, b1(), L);
            P p = mode.equals("2층") ? room(a0(), cz1 + 4, a0() + 2, b1(), "north", a0() + 1, L)
                    : room(a0(), cz1 + 4, a0() + 2, b1(), "east", cz1 + 4, L);
            p.door("pale_oak");
            furnish(p, fl);
            dw.room(fl, 3, dfl());
            windowsRoom(a0(), cz1 + 4, a0() + 2, b1(), L, false);
        }
        // 거실·주방 (LK)
        int lxa = fl != null ? lx0 : a0();
        switch (mode) {
            case "투룸", "아래층" -> {
                kitchenColumn(L);
                livingRoom(L, lxa, fl != null, false);
                dw.room("거실", a1() - lx0 - 1, dl).room("주방", 2, Math.min(4, dl - 1));
            }
            case "1층" -> {
                boolean kitchenHere = !java.util.Arrays.asList(sn).contains("주방");
                if (kitchenHere) {
                    kitchenColumn(L);
                    dw.room("주방", 2, Math.min(4, dl - 1));
                }
                livingRoom(L, lxa, fl != null, true);
                dw.room("거실", a1() - lx0 + (kitchenHere ? -1 : 1), dl).room("현관", 2, 2);
            }
            case "2층" -> {
                if (houseFrontRooms()) {
                    // 뒤 한 줄 복도 + 앞 침실들
                    int fd = b1() - cz1 - 2;
                    wallJ(cz1 + 2, a0() + 3, a1(), L);
                    lkFloor(L, lxa, Rooms.MARU_LIGHT);
                    int nb = Math.max(1, Math.min(3, (ws() + 1) / (fd >= 4 ? 4 : 5)));
                    for (int[] u : splitRow(lx0, a1(), nb, cz1 + 3, b1(), L)) {
                        P p = room(u[0], cz1 + 3, u[1], b1(), "north", u[0] + 1, L);
                        p.door("pale_oak");
                        furnish(p, "침실");
                        dw.room("침실", u[1] - u[0] + 1, fd);
                        windowsRoom(u[0], cz1 + 3, u[1], b1(), L, false);
                    }
                    v.set(lx0 + 1, L + 2, cz1 + 1, LIGHT);
                } else {
                    familyRoom(L, lxa);
                    dw.room("가족실", a1() - lx0 + 1, dl);
                }
            }
            case "위층" -> {
                familyRoom(L, lxa);
                dw.room("가족실", a1() - lx0 + 1, dl);
            }
            default -> {
                kitchenColumn(L);
                studioRoom(L, lxa);
                dw.room("방", a1() - lx0 - 1, dl);
            }
        }
        if (!mode.equals("2층") || !houseFrontRooms()) {
            windowsRoom(lx0, cz1 + 1, a1(), b1(), L, false);
        }
        dw.area += (a1() - a0() + 1) * din() - 4 * (cz1 - b0() + 1);
        if (into == null) {
            dwellings.add(dw);
        }
    }

    // ------------------------------------------------------------------ 그리기 도구

    /** i 고정, j 를 따라 안벽 (서는 높이 L 위 3칸) */
    void wallI(int i, int ja, int jb, int L) {
        v.fill(i, L, ja, i, L + 2, jb, IN);
    }

    /** j 고정, i 를 따라 안벽 */
    void wallJ(int j, int ia, int ib, int L) {
        v.fill(ia, L, j, ib, L + 2, j, IN);
    }

    /** 방 칸(안쪽 ia..ib × ja..jb, 문은 side 벽의 at)을 그리는 붓 */
    P room(int ia, int ja, int ib, int jb, String side, int at, int L) {
        return switch (side) {
            case "north" -> new P(Frame.facing(v, ia, ja, "south"), ib - ia + 1, jb - ja + 1, at - ia, L);
            case "south" -> new P(Frame.facing(v, ib, jb, "north"), ib - ia + 1, jb - ja + 1, ib - at, L);
            case "west" -> new P(Frame.facing(v, ia, jb, "east"), jb - ja + 1, ib - ia + 1, jb - at, L);
            default -> new P(Frame.facing(v, ib, ja, "west"), jb - ja + 1, ib - ia + 1, at - ja, L);
        };
    }

    /**
     * 방 안 붓: 문 벽이 b = -1, 창 쪽 끝이 b = D-1. 문(e)이 늘 오른쪽 반(e ≥ W/2)에 오게 좌우를 뒤집어 그립니다.
     * 높이 y 는 서는 높이 기준 (0 = 바닥 위 첫 칸).
     */
    final class P {
        final Frame f;
        final int W, D, e, L;
        final boolean flip;

        P(Frame f, int W, int D, int at, int L) {
            this.f = f;
            this.W = W;
            this.D = D;
            this.L = L;
            this.flip = 2 * at < W - 1;
            this.e = flip ? W - 1 - at : at;
        }

        int ma(int a) {
            return flip ? W - 1 - a : a;
        }

        boolean in(int a, int b) {
            return a >= 0 && b >= 0 && a < W && b < D;
        }

        void set(int a, int y, int b, Block x) {
            f.set(ma(a), L + y, b, flip ? flipX(x) : x);
        }

        Block get(int a, int y, int b) {
            return f.get(ma(a), L + y, b);
        }

        boolean empty(int a, int y, int b) {
            Block x = get(a, y, b);
            return x == null || x.isAir();
        }

        /** 방 안이고 바닥 위 두 칸이 비었는지 */
        boolean free(int a, int b) {
            return in(a, b) && empty(a, 0, b) && empty(a, 1, b);
        }

        void put(int a, int y, int b, Block x) {
            if (in(a, b) && empty(a, y, b)) {
                set(a, y, b, x);
            }
        }

        void fill(int a0, int y0, int b0, int a1, int y1, int b1, Block x) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++) {
                    for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) {
                        set(a, y, b, x);
                    }
                }
            }
        }

        void floor(Block x) {
            fill(0, -1, 0, W - 1, -1, D - 1, x);
        }

        /** 문 벽(b = -1)의 e 에 문 */
        void door(String wood) {
            set(e, 0, -1, Blocks.door(wood, "south", false));
            set(e, 1, -1, Blocks.door(wood, "south", true));
        }

        void light(int a, int b) {
            set(a, 2, b, LIGHT);
        }
    }

    /** 좌우(동서)로 뒤집은 블록: facing·연결·경첩·계단 모양·회전 */
    static Block flipX(Block b) {
        String data = b.data();
        int k = data.indexOf('[');
        if (k < 0) {
            return b;
        }
        StringBuilder out = new StringBuilder(data.length()).append(data, 0, k + 1);
        String[] parts = data.substring(k + 1, data.length() - 1).split(",");
        for (int q = 0; q < parts.length; q++) {
            String kv = parts[q];
            int eq = kv.indexOf('=');
            String key = kv.substring(0, eq), val = kv.substring(eq + 1);
            if (key.equals("east")) {
                key = "west";
            } else if (key.equals("west")) {
                key = "east";
            } else if (key.equals("facing")) {
                val = val.equals("east") ? "west" : val.equals("west") ? "east" : val;
            } else if (key.equals("hinge")) {
                val = val.equals("left") ? "right" : "left";
            } else if (key.equals("shape")) {
                val = val.replace("left", "@").replace("right", "left").replace("@", "right");
            } else if (key.equals("rotation")) {
                val = Integer.toString((16 - Integer.parseInt(val)) % 16);
            }
            if (q > 0) {
                out.append(',');
            }
            out.append(key).append('=').append(val);
        }
        return new Block(out.append(']').toString(), b.rgb(), b.text());
    }

    // ------------------------------------------------------------------ 방 꾸미기 (꼭 필요한 살림만)

    static final Block BATH_FLOOR = Block.of("light_gray_terracotta", 0x876A61);
    static final Block TOILET_W = Block.of("quartz_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE);
    static final Block SHOWER = Block.of("lightning_rod[facing=south,powered=false,waterlogged=false]", 0xC57A55);
    static final Block WASHER = Block.of("white_concrete", 0xCFD5D6);
    static final Block SHELF = Furniture.BOOKSHELF;

    private void furnish(P p, String name) {
        switch (name) {
            case "욕실" -> bath(p);
            case "침실", "손님방" -> bedroom(p, false);
            case "안방" -> bedroom(p, true);
            case "주방" -> kitchenDining(p);
            case "서재" -> study(p);
            default -> storage(p);
        }
    }

    /** 의자 (등받이가 back 쪽) */
    private static Block chair(String back) {
        return Block.of("oak_stairs[facing=" + back + ",half=bottom,shape=straight,waterlogged=false]", 0xA2834F);
    }

    /** 침대 한 칸 (머리 (a, b), 발치는 da, db 쪽) */
    private void bed(P p, int a, int b, int da, int db) {
        p.set(a, 0, b, Block.of("white_wool", 0xE9ECEC));
        p.set(a, 1, b, Block.of("white_carpet", 0xE9ECEC));
        p.set(a + da, 0, b + db, Blocks.wool(Furniture.SHEETS[r.nextInt(Furniture.SHEETS.length)]));
    }

    /** 욕실: 안쪽 구석 변기, 세면대, 샤워 (문 줄 e 는 비움) */
    private void bath(P p) {
        p.floor(BATH_FLOOR);
        int W = p.W, D = p.D;
        p.set(0, 0, D - 1, TOILET_W);
        if (W == 2 || D == 2) {
            // 좁은 욕실: 한 줄에 변기·세면대·샤워
            if (D >= 3) {
                p.set(0, 0, D - 2, CAULDRON);
                p.set(0, 0, 0, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
                p.set(0, 2, 0, SHOWER);
            } else {
                p.set(1, 0, D - 1, CAULDRON);
                p.set(Math.max(0, W - 1), 2, 0, SHOWER);
            }
        } else {
            p.set(W - 1, 0, D - 1, CAULDRON);
            p.set(0, 0, 0, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
            p.set(0, 0, 1, Block.of("glass_pane", 0xC8DCE4));
            p.set(0, 1, 1, Block.of("glass_pane", 0xC8DCE4));
            p.set(0, 2, 0, SHOWER);
        }
        p.light(W / 2, D / 2);
    }

    /** 침실: 창가 침대(안방은 2인), 옷장, 넓으면 책상 */
    private void bedroom(P p, boolean master) {
        p.floor(r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        int W = p.W, D = p.D;
        if (master && W >= 4 && D >= 4) {
            int c = (W - 2) / 2;
            bed(p, c, D - 1, 0, -1);
            bed(p, c + 1, D - 1, 0, -1);
            if (c - 1 >= 0) {
                p.set(c - 1, 0, D - 1, Furniture.DESK_TOP);
                p.set(c - 1, 1, D - 1, LANTERN);
            }
        } else if (W >= 3) {
            bed(p, 0, D - 1, 1, 0);
        } else {
            bed(p, 0, D - 1, 0, -1);
        }
        // 옷장 (문 반대쪽 벽, 두 칸 높이)
        if (D >= 4) {
            p.set(0, 0, 0, Rooms.WARDROBE);
            p.set(0, 1, 0, Rooms.WARDROBE);
        }
        if (W >= 4 && D >= 4 && p.free(W - 1, D - 1) && W - 1 != p.e) {
            p.set(W - 1, 0, D - 1, Furniture.DESK_TOP);
            p.put(W - 1, 0, D - 2, chair("south"));
        }
        p.light(W / 2, D / 2);
    }

    /** 단독주택 주방·식당: 안쪽 벽을 따라 조리대, 가운데 식탁 */
    private void kitchenDining(P p) {
        p.floor(Block.of("white_terracotta", 0xD1B2A1));
        int W = p.W, D = p.D;
        kitchenRun(p, 0, W - 1, D - 1, "north");
        if (D >= 5 && W >= 4) {
            int c = (W - 2) / 2;
            p.fill(c, 0, 2, c + 1, 0, 2, Block.of("oak_slab[type=top,waterlogged=false]", 0xA2834F));
            p.put(c, 0, 1, chair("north"));
            p.put(c + 1, 0, 1, chair("north"));
            p.put(c, 0, 3, chair("south"));
            p.put(c + 1, 0, 3, chair("south"));
        }
        p.light(W / 2, D / 2);
    }

    /** 조리대 줄 (a0..a1, 줄 b): 개수대·가스레인지·냉장고, 위 수납장. face 는 사람이 서는 쪽 */
    private void kitchenRun(P p, int a0, int a1, int b, String face) {
        for (int a = a0; a <= a1; a++) {
            Block x = a == a1 ? Furniture.FRIDGE : a == a0 + 1 ? CAULDRON
                    : a == a0 + 2 ? Block.of("smoker[facing=" + face + ",lit=false]", 0x555451) : Furniture.COUNTER;
            p.set(a, 0, b, x);
            p.set(a, a == a1 ? 1 : 2, b, a == a1 ? Furniture.FRIDGE : Furniture.COUNTER);
        }
    }

    private void study(P p) {
        p.floor(Rooms.MARU);
        p.set(0, 0, p.D - 1, Furniture.DESK_TOP);
        p.put(0, 0, p.D - 2, chair("south"));
        for (int b = 0; b < Math.min(3, p.D - 2); b++) {
            p.set(0, 0, b, SHELF);
            p.set(0, 1, b, SHELF);
        }
        p.light(p.W / 2, p.D / 2);
    }

    /** 다용도실·창고·드레스룸: 세탁기, 수납 */
    private void storage(P p) {
        p.set(0, 0, p.D - 1, WASHER);
        if (p.D >= 2) {
            p.set(0, 0, p.D - 2, BARREL);
            p.set(0, 1, p.D - 2, BARREL);
        }
        p.light(p.W / 2, p.D / 2);
    }

    /**
     * 원룸 (안쪽 W×D, 6×8 이상): 현관 옆 욕실(2×3), 오른쪽 벽 부엌(조리대·개수대·가스레인지·냉장고),
     * 창가 침대, 책상, 옷장.
     */
    private Dwelling oneRoom(P p) {
        int W = p.W, D = p.D;
        p.floor(r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        p.fill(Math.max(3, p.e - 1), -1, 0, Math.min(W - 1, p.e + 1), -1, 1, Interior.TILE);
        // 욕실 a 0..1, b 0..2
        p.fill(2, 0, 0, 2, 2, 3, IN);
        p.fill(0, 0, 3, 1, 2, 3, IN);
        p.set(2, 0, 1, Blocks.door("pale_oak", "east", false));
        p.set(2, 1, 1, Blocks.door("pale_oak", "east", true));
        bathIn(p, 0, 0, 1, 2);
        // 부엌: 오른쪽 벽 b 2.. (개수대·가스레인지·조리대·냉장고)
        int kb = Math.min(5, D - 2);
        Block[] run = kb - 2 + 1 >= 4
                ? new Block[]{CAULDRON, Block.of("smoker[facing=west,lit=false]", 0x555451), Furniture.COUNTER, Furniture.FRIDGE}
                : new Block[]{CAULDRON, Block.of("smoker[facing=west,lit=false]", 0x555451), Furniture.FRIDGE};
        for (int q = 0; q < run.length; q++) {
            p.set(W - 1, 0, 2 + q, run[q]);
            p.set(W - 1, run[q] == Furniture.FRIDGE ? 1 : 2, 2 + q, run[q] == Furniture.FRIDGE ? Furniture.FRIDGE : Furniture.COUNTER);
        }
        // 창가 침대, 옷장, 책상
        bed(p, 0, D - 1, 1, 0);
        if (D >= 7) {
            p.set(0, 0, 4, Rooms.WARDROBE);
            p.set(0, 1, 4, Rooms.WARDROBE);
        } else {
            p.set(3, 0, 0, Rooms.WARDROBE);
            p.set(3, 1, 0, Rooms.WARDROBE);
        }
        int da = Math.min(W - 2, Math.max(3, W / 2));
        p.set(da, 0, D - 1, Furniture.DESK_TOP);
        p.put(da, 0, D - 2, chair("north"));
        p.light(W / 2, D - 3);
        p.light(Math.max(3, W - 2), 2);
        Dwelling dw = new Dwelling("원룸");
        dw.room("원룸", W, D).room("욕실", 2, 3);
        return dw;
    }

    /** 방 안의 한 칸 영역(a0..a1, b0..b1)을 욕실로 */
    private void bathIn(P p, int a0, int b0, int a1, int b1) {
        for (int b = b0; b <= b1; b++) {
            for (int a = a0; a <= a1; a++) {
                p.set(a, -1, b, BATH_FLOOR);
            }
        }
        p.set(a0, 0, b1, TOILET_W);
        p.set(a1, 0, b1, CAULDRON);
        p.set(a0, 0, b0, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
        p.set(a0, 2, b0, SHOWER);
        p.light(a1, b0 + 1);
    }

    /**
     * 투룸 (안쪽 W×D): 보통 (W ≥ 12, D ≥ 10) — 왼쪽 앞 욕실·뒤 침실, 오른쪽 침실, 가운데 거실·주방, 오른쪽 앞 현관.
     * 깊은 꼴 (W ≥ 9, D ≥ 13) — 왼쪽 줄에 욕실·침실·침실, 오른쪽이 현관·주방·거실.
     */
    private Dwelling twoRoom(P p) {
        int W = p.W, D = p.D;
        Dwelling dw = new Dwelling("투룸");
        p.floor(r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        // 공통: 왼쪽 욕실 a 0..2 × b 0..2, 벽 a=3 (끝까지)
        p.fill(3, 0, 0, 3, 2, D - 1, IN);
        p.fill(0, 0, 3, 2, 2, 3, IN);
        doorEast(p, 3, 1);
        bathIn(p, 0, 0, 2, 2);
        dw.room("욕실", 3, 3);
        p.fill(Math.max(4, p.e - 1), -1, 0, Math.min(W - 1, p.e + 1), -1, 1, Interior.TILE);
        dw.room("현관", Math.min(W - 1, p.e + 1) - Math.max(4, p.e - 1) + 1, 2);
        if (W >= 12 && D >= 10) {
            // 왼쪽 뒤 침실
            doorEast(p, 3, 5);
            bedroomIn(p, 0, 4, 2, D - 1);
            dw.room("침실", 3, D - 4);
            // 오른쪽 침실 (벽 a = W-4)
            p.fill(W - 4, 0, 3, W - 4, 2, D - 1, IN);
            p.fill(W - 3, 0, 3, W - 1, 2, 3, IN);
            p.set(W - 4, 0, 5, Blocks.door("pale_oak", "west", false));
            p.set(W - 4, 1, 5, Blocks.door("pale_oak", "west", true));
            bedroomIn(p, W - 3, 4, W - 1, D - 1);
            dw.room("침실", 3, D - 4);
            // 주방 (안쪽 벽 b = 0, a 4..7)
            int ka = 4, kb = Math.min(W - 5, 7);
            if (p.e <= kb + 1 && p.e >= ka) {
                kb = p.e - 2;
            }
            kitchenRun(p, ka, Math.max(ka + 3, kb), 0, "south");
            dw.room("주방", 4, 2);
            // 거실 (a 4..W-5, b 5..D-1)
            livingIn(p, 4, 5, W - 5, D - 1);
            dw.room("거실", W - 8, D - 5);
        } else {
            // 깊은 꼴: 왼쪽 침실 둘
            p.fill(0, 0, 8, 2, 2, 8, IN);
            doorEast(p, 3, 5);
            doorEast(p, 3, 10);
            bedroomIn(p, 0, 4, 2, 7);
            bedroomIn(p, 0, 9, 2, D - 1);
            dw.room("침실", 3, 4).room("침실", 3, D - 9);
            // 주방: 오른쪽 벽 b 3..6
            for (int b = 3; b <= 6; b++) {
                Block x = b == 3 ? Furniture.COUNTER : b == 4 ? CAULDRON : b == 5 ? Block.of("smoker[facing=west,lit=false]", 0x555451) : Furniture.FRIDGE;
                p.set(W - 1, 0, b, x);
                p.set(W - 1, b == 6 ? 1 : 2, b, b == 6 ? Furniture.FRIDGE : Furniture.COUNTER);
            }
            dw.room("주방", 2, 4);
            livingIn(p, 4, 8, W - 1, D - 1);
            dw.room("거실", W - 4, D - 8);
        }
        return dw;
    }

    /** 벽 a 의 b 칸에 동쪽(기준)으로 여는 문 */
    private void doorEast(P p, int a, int b) {
        p.set(a, 0, b, Blocks.door("pale_oak", "east", false));
        p.set(a, 1, b, Blocks.door("pale_oak", "east", true));
    }

    /** 방 안 영역에 침실 살림: 침대, 옷장 */
    private void bedroomIn(P p, int a0, int b0, int a1, int b1) {
        for (int b = b0; b <= b1; b++) {
            for (int a = a0; a <= a1; a++) {
                p.set(a, -1, b, Rooms.MARU_LIGHT);
            }
        }
        bed(p, a0, b1, 1, 0);
        p.set(a0, 0, b0, Rooms.WARDROBE);
        p.set(a0, 1, b0, Rooms.WARDROBE);
        p.light((a0 + a1) / 2, (b0 + b1) / 2);
    }

    /** 방 안 영역에 거실 살림: 벽걸이 TV(왼쪽 벽), 마주 보는 소파, 화분 */
    private void livingIn(P p, int a0, int b0, int a1, int b1) {
        int tb = Math.max(b0 + 1, b1 - 1);
        p.set(a0, 0, tb, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        p.set(a0, 1, tb, Furniture.TV);
        int sa = Math.min(a1, a0 + 4);
        for (int b = tb - 1; b <= Math.min(b1, tb + 1); b++) {
            p.put(sa, 0, b, Block.of("spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0x725430));
        }
        p.put(a0 + 1, 0, b1, Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
        p.light((a0 + a1) / 2, (b0 + b1) / 2);
        p.light((a0 + a1) / 2, b0);
    }

    // ------------------------------------------------------------------ ㄱ자형 거실·주방 (건물 좌표, 계단실 왼쪽 기준)

    /** 거실 동쪽 벽을 따라 부엌 (cz1+2..cz1+5): 조리대·개수대·가스레인지·냉장고, 위 수납장 */
    private void kitchenColumn(int L) {
        int i = a1();
        Block[] run = {Furniture.COUNTER, CAULDRON, Block.of("smoker[facing=west,lit=false]", 0x555451), Furniture.FRIDGE};
        for (int q = 0; q < run.length && cz1 + 2 + q <= b1(); q++) {
            int j = cz1 + 2 + q;
            v.set(i, L, j, run[q]);
            v.set(i, run[q] == Furniture.FRIDGE ? L + 1 : L + 2, j, run[q] == Furniture.FRIDGE ? Furniture.FRIDGE : Furniture.COUNTER);
            v.set(i - 1, L - 1, j, Block.of("white_terracotta", 0xD1B2A1));
        }
    }

    /** 거실 바닥 (LK 전체, 현관 빼고) */
    private void lkFloor(int L, int lxa, Block finish) {
        v.fill(a0() + 3, L - 1, cz1 + 1, a1(), L - 1, b1(), finish);
        if (lxa < a0() + 3) {
            v.fill(lxa, L - 1, cz1 + 3, a0() + 2, L - 1, b1(), finish);
        }
    }

    private void lkLights(int L, int lxa) {
        for (int j = cz1 + 2; j <= b1(); j += 4) {
            for (int i = lxa + 1; i <= a1(); i += 4) {
                if (v.get(i, L + 2, j) == null || v.get(i, L + 2, j).isAir()) {
                    v.set(i, L + 2, j, LIGHT);
                }
            }
        }
    }

    /** 거실: TV 와 마주 보는 소파 (단독주택이면 정면 현관문과 신발장도) */
    private void livingRoom(int L, int lxa, boolean fl, boolean house) {
        lkFloor(L, lxa, r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        int x0 = fl ? a0() + 4 : lxa + 1, dl = b1() - cz1;
        int x1 = a1() - 2;
        Block sofa = Block.of("spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0x725430);
        if (dl >= 7) {
            int tj = b1() - 1;
            v.set(x0, L, tj, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
            v.set(x0, L + 1, tj, Furniture.TV);
            int si = Math.min(x1, x0 + 4);
            for (int j = tj - 1; j <= b1(); j++) {
                setIfAir(si, L, j, sofa);
            }
        } else {
            int ti = x0 + 2;
            v.set(ti, L, b1(), Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
            v.set(ti, L + 1, b1(), Furniture.TV);
            for (int i = ti - 1; i <= ti + 1; i++) {
                setIfAir(i, L, b1() - 3, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430));
            }
        }
        setIfAir(x1, L, b1(), Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
        if (house) {
            // 정면 현관문 (오른쪽 앞), 현관 타일
            int fi = a1() - 1;
            v.fill(fi - 1, L - 1, b1() - 1, fi, L - 1, b1(), Interior.TILE);
            v.set(fi, L, jf, Blocks.door("dark_oak", "north", false));
            v.set(fi, L + 1, jf, Blocks.door("dark_oak", "north", true));
            v.set(fi, L, b1(), AIR);
            v.set(fi, L + 1, b1(), AIR);
            v.set(fi - 1, L, b1(), AIR);
        }
        lkLights(L, lxa);
    }

    /** 가족실 (두 층 집 위층·단독 위층): 책장, 소파, 책상 */
    private void familyRoom(int L, int lxa) {
        lkFloor(L, lxa, Rooms.MARU_LIGHT);
        for (int j = b1() - 2; j <= b1(); j++) {
            v.set(a1(), L, j, SHELF);
            v.set(a1(), L + 1, j, SHELF);
        }
        int x0 = a0() + 5;
        for (int i = x0; i <= Math.min(x0 + 2, a1() - 2); i++) {
            setIfAir(i, L, b1() - 2, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430));
        }
        setIfAir(x0 + 1, L, b1(), Furniture.DESK_TOP);
        lkLights(L, lxa);
    }

    /** 원룸(ㄱ자 한 층): 침대, 책상, 옷장 (부엌은 kitchenColumn) */
    private void studioRoom(int L, int lxa) {
        lkFloor(L, lxa, r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        int x0 = a0() + 5;
        v.set(x0, L, b1(), Block.of("white_wool", 0xE9ECEC));
        v.set(x0, L + 1, b1(), Block.of("white_carpet", 0xE9ECEC));
        v.set(x0 + 1, L, b1(), Blocks.wool(Furniture.SHEETS[r.nextInt(Furniture.SHEETS.length)]));
        setIfAir(a1() - 2, L, b1(), Furniture.DESK_TOP);
        setIfAir(a1() - 2, L, b1() - 1, chair("south"));
        setIfAir(x0, L, b1() - 2, Rooms.WARDROBE);
        setIfAir(x0, L + 1, b1() - 2, Rooms.WARDROBE);
        lkLights(L, lxa);
    }

    private void setIfAir(int i, int y, int j, Block b) {
        Block x = v.get(i, y, j);
        if (v.inside(i, y, j) && (x == null || x.isAir())) {
            v.set(i, y, j, b);
        }
    }

    // ------------------------------------------------------------------ 창

    /** 방(안쪽 ia..ib × ja..jb)의 바깥벽 쪽에 창. shop 이면 정면을 통유리로 */
    private void windowsRoom(int ia, int ja, int ib, int jb, int L, boolean shop) {
        if (jb + 1 == jf) {
            windowRun(ia, ib, jf, true, L, shop, 1);
        }
        if (ja - 1 == j0) {
            windowRun(ia, ib, j0, true, L, false, -1);
        }
        if (ia - 1 == i0) {
            windowRun(ja, jb, i0, false, L, false, -1);
        }
        if (ib + 1 == i1) {
            windowRun(ja, jb, i1, false, L, false, 1);
        }
    }

    private void windowRun(int from, int to, int fixed, boolean alongI, int L, boolean shop, int out) {
        int n = to - from + 1;
        if (n < 2) {
            return;
        }
        boolean big = type == Type.LUX || (type == Type.PYEONGCHANG && variant >= 2);
        int ww = shop ? n : big ? Math.min(4, n) : type == Type.ONEROOM ? 2 : Math.min(n >= 7 ? 3 : 2, n);
        int wh = shop || big ? 3 : 2;
        int y0 = shop || big ? L : L + 1;
        int k = shop ? 1 : Math.max(1, (n - 1) / (ww + 2));
        int total = k * ww + (k - 1) * 2;
        int start = from + (n - total) / 2;
        for (int q = 0; q < k; q++) {
            for (int c = start + q * (ww + 2); c < start + q * (ww + 2) + ww; c++) {
                int i = alongI ? c : fixed, j = alongI ? fixed : c;
                for (int y = y0; y < y0 + wh; y++) {
                    Block x = v.get(i, y, j);
                    if (x != null && !x.id().endsWith("_door") && !x.id().endsWith("sign")) {
                        v.set(i, y, j, glass);
                    }
                }
                // 창턱 (바깥)
                int si = alongI ? i : i + out, sj = alongI ? j + out : j;
                if (!shop && v.inside(si, y0 - 1, sj) && v.get(si, y0 - 1, sj) == null && y0 - 1 >= 2) {
                    v.set(si, y0 - 1, sj, SILL);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 카페 (연남동)

    private static final String[] CAFE_NAMES = {"골목 커피", "연남 로스터리", "오후의 빵", "모퉁이 카페", "책방 카페", "주택 카페"};

    /** 1층 카페 (복도형 R 칸): 통유리 정면과 문, 안쪽 바 카운터, 화장실, 2인 탁자들 */
    private Dwelling cafeRoom(P p) {
        int L = p.L, ia = cx1 + 1, ib = a1(), ja = b0(), jb = b1();
        v.fill(ia, L - 1, ja, ib, L - 1, jb, Block.of("oak_planks", 0xA2834F));
        // 화장실 (오른쪽 뒤 2×3), 문은 앞쪽
        wallI(ib - 2, ja, ja + 3, L);
        wallJ(ja + 3, ib - 2, ib, L);
        v.set(ib - 1, L, ja + 3, Blocks.door("pale_oak", "south", false));
        v.set(ib - 1, L + 1, ja + 3, Blocks.door("pale_oak", "south", true));
        v.fill(ib - 1, L - 1, ja, ib, L - 1, ja + 2, BATH_FLOOR);
        v.set(ib, L, ja, TOILET_W.rotate(1));
        v.set(ib - 1, L, ja, CAULDRON);
        v.set(ib - 1, L + 2, ja + 1, LIGHT);
        // 바 카운터 (뒤 벽 앞), 뒤 선반에 커피 머신
        for (int i = ia + 1; i <= ib - 3; i++) {
            v.set(i, L, ja + 2, Furniture.COUNTER);
            v.set(i, L, ja, i == ia + 2 ? Block.of("smoker[facing=south,lit=false]", 0x555451) : i == ia + 3 ? CAULDRON : SHELF);
            v.set(i, L + 1, ja, i % 2 == 0 ? SHELF : FLOWER_POT_FERN);
        }
        // 2인 탁자
        for (int j = ja + 5; j <= jb - 1; j += 3) {
            for (int i = ia + 1; i <= ib - 1; i += 3) {
                v.set(i, L, j, Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
                setIfAir(i, L, j - 1, chair("north"));
                setIfAir(i, L, j + 1, chair("south"));
            }
        }
        for (int j = ja + 2; j <= jb; j += 3) {
            for (int i = ia + 1; i <= ib; i += 3) {
                setIfAir(i, L + 2, j, LANTERN_HANGING);
            }
        }
        // 정면 문 (복도 쪽 끝)과 간판
        v.set(ia + 1, L, jf, Blocks.door("dark_oak", "north", false));
        v.set(ia + 1, L + 1, jf, Blocks.door("dark_oak", "north", true));
        v.set(ia + 1, L, jb, AIR);
        v.set(ia + 1, L + 1, jb, AIR);
        String name = CAFE_NAMES[r.nextInt(CAFE_NAMES.length)];
        v.set(ia + 3, L + 3, jf + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", name));
        Dwelling dw = new Dwelling("카페");
        dw.room("카페", ib - ia + 1, jb - ja - 3).room("화장실", 2, 3);
        return dw;
    }

    // ------------------------------------------------------------------ 땅·마당

    /** 땅 전체 포장, 마당 (단독주택은 잔디) */
    private void site() {
        v.fill(0, -1, 0, w - 1, -1, d - 1, PAVE);
        if (type == Type.PYEONGCHANG || (type == Type.YEONNAM && !cafe)) {
            v.fill(0, -1, jf + 1, w - 1, -1, d - 1, LAWN);
        }
        if (piloti) {
            v.fill(0, -1, jf + 1, w - 1, -1, d - 1, ASPHALT);
        }
    }

    /** 1층 필로티: 기둥만 남긴 주차장, 주차선과 차 자리, 공동현관(유리문·우편함), 천장 등 */
    private void piloti() {
        int top = lv[1] - 2;
        boolean open = !elevator;
        // 벽 걷어내기: 정면, 오른쪽 옆, 공동현관 앞
        v.fill(cx1 + 1, 0, jf, i1, top, jf, AIR);
        v.fill(i1, 0, j0 + 1, i1, top, jf, AIR);
        if (open) {
            v.fill(i0, 0, cz1 + 4, cx1, top, jf, AIR);
        }
        // 공동현관: 계단실 앞 (엘리베이터면 복도 끝까지)
        int lobbyEnd = open ? cz1 + 2 : b1();
        wallI(cx1, cz1 + 1, lobbyEnd, 0);
        v.fill(a0(), -1, cz1 + 1, cx1 - 1, -1, lobbyEnd, Interior.LANDING);
        if (open) {
            v.fill(i0, 0, cz1 + 3, cx1, top, cz1 + 3, wall);
            v.set(a0() + 1, 1, cz1 + 3, glass);
            v.set(a0() + 1, 0, cz1 + 3, Blocks.door("birch", "north", false));
            v.set(a0() + 1, 1, cz1 + 3, Blocks.door("birch", "north", true));
            v.set(a0() + 2, 1, cz1 + 3, glass);
            v.set(a0(), 1, cz1 + 4, Blocks.wallSign("dark_oak", "south", "white", false, KoreanNames.villa(r), KoreanNames.address(r)));
        } else {
            v.set(cx1, 0, cz1 + 2, Blocks.door("birch", "west", false));
            v.set(cx1, 1, cz1 + 2, Blocks.door("birch", "west", true));
            v.set(cx1 + 1, 2, cz1 + 3, Blocks.wallSign("dark_oak", "east", "white", false, KoreanNames.villa(r), KoreanNames.address(r)));
        }
        // 우편함 (철판), 등
        v.set(a0(), 1, cz1 + 1, Block.of("iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        v.set(a0(), 1, cz1 + 2, Block.of("iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        v.set(a0() + 1, top, cz1 + 1, LIGHT);
        // 주차 칸: 계단실·현관 오른쪽 띠, 앞 5줄. 차 앞은 정면
        int n = (i1 - cx1) / 3, sj = d - 5;
        for (int k = 0; k <= n; k++) {
            int li = cx1 + 3 * k;
            v.fill(li, -1, Math.max(sj, cz1 + 4), li, -1, d - 1, LINE);
            if (k < n) {
                v.carSpot(li + 2.0, 0, d - 2.5, 0, 1);
            }
            // 두 칸마다 기둥 (맨 끝 칸은 옆 기둥이 3칸 길을 막지 않을 때만)
            boolean column = k % 2 == 0 && !(k == n && n % 2 == 1);
            if (column && k > 0 && li <= i1) {
                v.fill(li, 0, jf, li, top, jf, trim);
            }
        }
        // 옆 기둥 (뒤쪽, 주차 줄 뒤)
        for (int j = j0; j < Math.min(sj, jf); j += 5) {
            v.fill(i1, 0, j, i1, top, j, trim);
        }
        v.fill(i1, 0, sj - 1, i1, top, sj - 1, trim);
        if (open) {
            v.fill(i0, 0, jf, i0, top, jf, trim);
            v.fill(cx1, 0, jf, cx1, top, jf, trim);
        }
        // 천장 등
        for (int i = cx1 + 2; i < i1; i += 4) {
            for (int j = j0 + 2; j < jf; j += 4) {
                if (v.get(i, top, j) == null || v.get(i, top, j).isAir()) {
                    v.set(i, top, j, LANTERN_HANGING);
                }
            }
        }
    }

    /** 반지하: 오른쪽 R 칸 한 세대, 정면 땅 높이 창(방범창), 길에서 내려가는 좁은 계단 */
    private void basement() {
        int L = -2;
        pitI = cx1 + 2;
        P p = room(cx1 + 1, b0(), a1(), b1(), "south", pitI, L);
        p.door("pale_oak");
        int wsz = ws(), dsz = din();
        boolean two = (wsz >= 12 && dsz >= 10) || (wsz >= 9 && dsz >= 13);
        Dwelling dw = two ? twoRoom(p) : oneRoom(p);
        dw.area = wsz * dsz;
        dwellings.add(dw);
        // 땅 높이 창 (y = 0), 정면은 방범창
        for (int i = cx1 + 2; i <= a1() - 1; i += 3) {
            if (Math.abs(i - pitI) > 1) {
                v.set(i, 0, jf, glass);
                v.set(i + 1, 0, jf, glass);
                v.set(i, 0, jf + 1, RAIL);
                v.set(i + 1, 0, jf + 1, RAIL);
            }
            v.set(i, 0, j0, glass);
        }
        for (int j = b0() + 1; j <= b1() - 1; j += 3) {
            v.set(i1, 0, j, glass);
        }
        // 내려가는 계단 (정면 앞 한 줄, 동쪽으로 오름)
        int j = jf + 1;
        v.fill(pitI, -2, j, pitI + 2, 0, j, AIR);
        v.set(pitI, -3, j, STONE_BRICKS);
        v.set(pitI + 1, -2, j, Blocks.stairs("stone_brick", "east", 0x7A7979));
        v.set(pitI + 1, -3, j, STONE_BRICKS);
        v.set(pitI + 2, -1, j, Blocks.stairs("stone_brick", "east", 0x7A7979));
        v.fill(pitI + 2, -3, j, pitI + 2, -2, j, STONE_BRICKS);
        v.fill(pitI - 1, -3, j, pitI - 1, -1, j, STONE_BRICKS);
        v.fill(pitI, -3, j + 1, pitI + 2, -1, j + 1, STONE_BRICKS);
        v.set(pitI - 1, 0, j, RAIL);
        v.fill(pitI, 0, j + 1, pitI + 1, 0, j + 1, RAIL);
    }

    /** 정면 출입구·마당 (집 종류마다) */
    private void front() {
        if (!piloti && type != Type.PYEONGCHANG && !(type == Type.YEONNAM && !cafe && floors <= 2)) {
            // 공동현관 (복도 앞 끝), 문패, 차양
            int L = lv[0];
            if (!elevator) {
                v.set(a0() + 1, L, jf, Blocks.door("birch", "north", false));
                v.set(a0() + 1, L + 1, jf, Blocks.door("birch", "north", true));
            } else {
                v.set(i0, L, cz1 + 2, Blocks.door("birch", "east", false));
                v.set(i0, L + 1, cz1 + 2, Blocks.door("birch", "east", true));
            }
            v.fill(a0(), L + 3, jf + 1, a0() + 2, L + 3, jf + 1, SILL);
            v.set(a0() + 2, L + 1, jf + 1, Blocks.wallSign("dark_oak", "south", "white", false, KoreanNames.villa(r), KoreanNames.address(r)));
            if (L > 0) {
                // 반지하 집: 현관 앞 계단 두 단 (동쪽에서 오름)
                v.set(a0() + 1, 1, jf + 1, Blocks.stairs("stone_brick", "west", 0x7A7979));
                v.set(a0() + 1, 0, jf + 1, wall);
                v.set(a0() + 2, 0, jf + 1, Blocks.stairs("stone_brick", "west", 0x7A7979));
                v.set(a0() + 2, L + 1, jf + 1, AIR);
                v.set(a0(), L + 1, jf + 1, Blocks.wallSign("dark_oak", "south", "white", false, KoreanNames.villa(r), KoreanNames.address(r)));
            }
        }
        if (type == Type.YEONNAM) {
            yeonnamYard();
        } else if (type == Type.PYEONGCHANG) {
            pyeongchangYard();
        }
    }

    /** 연남동: 낮은 벽돌 담(문 자리 트임), 마당 나무, 카페면 바깥 자리 */
    private void yeonnamYard() {
        int jw = d - 1;
        if (jw - jf < 2) {
            return;
        }
        int g0 = cafe || floors > 2 ? a0() : a1() - 2, g1 = cafe || floors > 2 ? cx1 + 3 : a1();
        for (int i = 0; i < w; i++) {
            if (i < g0 || i > g1) {
                v.set(i, 0, jw, wall == WHITE_BRICK ? WHITE_BRICK : BRICKS);
                v.set(i, 1, jw, BRICK_CAP);
            }
        }
        v.fill(g0, -1, jf + 1, g1, -1, jw, Block.of("stone_bricks", 0x7A7979));
        // 마당 나무 한 그루 (문 반대쪽)
        int ti = g0 > w / 2 ? 2 : w - 3, tj = (jf + 1 + jw) / 2;
        v.fill(ti, 0, tj, ti, 2, tj, Block.of("oak_log[axis=y]", 0x6D5532));
        v.ellipsoid(ti + 0.5, 4, tj + 0.5, 2.2, 1.6, Math.min(2.2, (jw - jf) / 2.0), OAK_LEAVES);
        if (cafe) {
            for (int i = cx1 + 5; i + 1 < ti - 1 && i <= a1(); i += 3) {
                v.set(i, 0, jf + 2, Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
                setIfAir(i + 1, 0, jf + 2, chair("east"));
            }
        }
    }

    /** 평창동: 높은 돌담과 대문, 잔디 마당, 소나무, 차고(또는 마당 주차 자리) */
    private void pyeongchangYard() {
        int jw = d - 1;
        if (jw - jf < 2) {
            return;
        }
        Block stone = variant % 2 == 0 ? STONE_BRICKS : POLISHED_GRANITE;
        int gate = a1() - 1;
        for (int i = 0; i < w; i++) {
            v.fill(i, 0, jw, i, 2, jw, stone);
            v.set(i, 3, jw, CAP);
        }
        v.fill(0, 0, jf + 1, 0, 1, jw, stone);
        v.fill(w - 1, 0, jf + 1, w - 1, 1, jw, stone);
        // 대문 (사람 문) + 작은 기와 지붕
        v.set(gate, 0, jw, Blocks.door("dark_oak", "north", false));
        v.set(gate, 1, jw, Blocks.door("dark_oak", "north", true));
        v.set(gate, 2, jw, AIR);
        v.fill(gate - 1, 3, jw, gate + 1, 3, jw, Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637));
        v.set(gate - 1, 2, jw + 0, stone);
        v.set(gate + 1, 2, jw, stone);
        v.set(gate + 1, 1, jw - 1 >= jf + 1 ? jw - 1 : jw, LANTERN);
        // 디딤돌 길
        for (int j = jf + 1; j < jw; j++) {
            v.set(gate, -1, j, (j & 1) == 0 ? SMOOTH_STONE : POLISHED_ANDESITE);
        }
        int fy = jw - jf;
        if (fy >= 6 && w >= 12) {
            // 차고: 왼쪽 앞, 돌담에 바로 트인 입구 (안쪽 3×5 + 벽)
            garage = fy >= 7 ? 1 : 3;
            int gi0 = 1, gi1 = 3, gj0 = jw - 5;
            if (garage == 1) {
                v.fill(gi0 - 1, 0, gj0 - 1, gi1 + 1, 2, jw, stone);
                v.fill(gi0, 0, gj0, gi1, 2, jw, AIR);
                v.fill(gi0 - 1, 3, gj0 - 1, gi1 + 1, 3, jw, SMOOTH_STONE);
                v.set(gi0 + 1, 2, gj0, LIGHT);
            } else {
                v.fill(gi0, 0, jw, gi1, 3, jw, AIR);
                v.set(gi0 - 1, 3, jw, stone);
                v.set(gi1 + 1, 3, jw, stone);
            }
            v.fill(gi0, -1, gj0, gi1, -1, jw, POLISHED_ANDESITE);
            v.carSpot(gi0 + 1.5, 0, jw - 2.0, 0, 1);
        }
        // 소나무 (마당 가운데쯤), 회양목
        if (fy >= 4) {
            int ti = garage > 0 ? 6 : 3, tj = jf + 1 + fy / 2;
            if (ti < gate - 2) {
                pine(ti, tj, 5 + r.nextInt(2));
            }
            for (int i = ti + 3; i < gate - 1; i += 2) {
                setIfAir(i, 0, jw - 1, SHRUB);
            }
        }
    }

    /** 소나무: 살짝 굽은 줄기, 납작한 잎 덩어리 두세 층 */
    private void pine(int i, int j, int h) {
        for (int y = 0; y < h; y++) {
            v.set(i + (y >= h - 2 ? 1 : 0), y, j, PINE_LOG);
        }
        v.ellipsoid(i + 1.5, h, j + 0.5, 2.6, 0.9, 2.2, PINE_LEAVES);
        v.ellipsoid(i + 0.5, h - 2, j + 0.5, 1.8, 0.7, 1.6, PINE_LEAVES);
        v.ellipsoid(i + 2, h + 1.2, j + 0.5, 1.5, 0.6, 1.3, PINE_LEAVES);
    }

    // ------------------------------------------------------------------ 옥상

    private boolean inCore(int i, int j) {
        return i >= cx0 && i <= cx1 && j >= j0 && j <= cz1;
    }

    private void roof() {
        int y = roofY(), R = lv[floors];
        if (hipRoof) {
            hipRoof(y + 1);
            return;
        }
        boolean terrace = type == Type.YEONNAM || type == Type.PYEONGCHANG || type == Type.LUX;
        Block finish = terrace ? SMOOTH_STONE : type == Type.ONEROOM && variant % 2 == 0 ? LIGHT_GRAY_CONCRETE : ROOF_GREEN;
        for (int j = j0 + 1; j < jf; j++) {
            for (int i = i0 + 1; i < i1; i++) {
                if (!inCore(i, j)) {
                    v.set(i, y, j, finish);
                }
            }
        }
        // 난간: 벽 한 단 + 갓돌 (테라스면 쇠 난간)
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= jf; j++) {
                boolean edge = i == i0 || i == i1 || j == j0 || j == jf;
                if (edge && !inCore(i, j)) {
                    v.set(i, R, j, wall);
                    v.set(i, R + 1, j, terrace ? RAIL : CAP);
                }
            }
        }
        if (terrace) {
            // 나무 데크, 화분, 탁자
            v.fill(cx1 + 2, y, cz1 + 2, a1() - 1, y, b1() - 1, DECK);
            v.fill(cx1 + 2, y + 1, cz1 + 2, a1() - 1, y + 1, b1() - 1, AIR);
            for (int i = cx1 + 2; i <= a1() - 1; i += 3) {
                setIfAir(i, R, b1(), SHRUB);
            }
            int ti = (cx1 + a1()) / 2 + 1, tj = (cz1 + b1()) / 2 + 1;
            v.set(ti, R, tj, Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
            setIfAir(ti - 1, R, tj, chair("west"));
            setIfAir(ti + 1, R, tj, chair("east"));
        }
        if (rooftopRoom) {
            rooftopRoom(R);
        }
        // 물탱크: 낡은 다가구는 파란 탱크, 빌라는 스테인리스
        if (!terrace) {
            boolean blue = type == Type.HILLSIDE || type == Type.MANGWON;
            double ti = a1() - 1.0, tj = rooftopRoom ? b1() - 1.0 : b0() + 1.0;
            v.cylinder(ti, tj, 1.3, R, R + 1, blue ? TANK_BLUE : IRON_BLOCK);
            v.set((int) ti, R + 2, (int) tj, blue ? TANK_BLUE : IRON_BLOCK);
        }
        // 실외기 몇 대
        for (int n = 0; n < 2; n++) {
            int ai = cx1 + 2 + r.nextInt(Math.max(1, a1() - cx1 - 3)), aj = cz1 + 2 + r.nextInt(Math.max(1, b1() - cz1 - 2));
            setIfAir(ai, R, aj, AC);
        }
    }

    /** 옥탑방 (계단실 옆, 초록 지붕). 6×8 이 들어가면 원룸 한 집, 아니면 창고 */
    private void rooftopRoom(int R) {
        boolean home = ws() >= 7 && din() >= 9;
        int ib = home ? cx1 + 6 : Math.min(a1(), cx1 + 4), jb = home ? j0 + 8 : Math.min(b1(), j0 + 4);
        v.walls(cx1, R, j0, ib + 1, R + 2, jb + 1, Block.of("white_concrete", 0xCFD5D6));
        v.fill(cx1 + 1, R, j0 + 1, ib, R + 2, jb, AIR);
        v.fill(cx1, R + 3, j0, ib + 1, R + 3, jb + 1, ROOF_GREEN);
        v.fill(cx1 + 1, R - 1, j0 + 1, ib, R - 1, jb, SMOOTH_STONE);
        // 계단실 벽(옥상 위)은 그대로 둠
        for (int j = j0; j <= cz1; j++) {
            v.fill(cx1, R, j, cx1, R + 2, j, wall);
        }
        P p = room(cx1 + 1, j0 + 1, ib, jb, "south", cx1 + 2, R);
        p.door("spruce");
        if (home) {
            Dwelling dw = oneRoom(p);
            dw.area = (ib - cx1) * (jb - j0);
            dwellings.add(dw);
            v.set(ib + 1, R + 1, j0 + 3, glass);
            v.set(ib + 1, R + 1, j0 + 4, glass);
            v.set(cx1 + 5, R + 1, jb + 1, glass);
        } else {
            storage(p);
        }
        // 옥상 살림: 장독, 빨랫줄
        for (int i = ib + 2; i <= Math.min(a1(), ib + 4); i++) {
            setIfAir(i, R, b1() - 1, Block.of("brown_terracotta", 0x4D3323));
        }
        if (b1() - jb >= 4) {
            int cj = jb + 3;
            setIfAir(cx1 + 1, R, cj, Block.of("spruce_fence", 0x725430));
            setIfAir(cx1 + 1, R + 1, cj, Block.of("spruce_fence", 0x725430));
            setIfAir(a1() - 3, R, cj, Block.of("spruce_fence", 0x725430));
            setIfAir(a1() - 3, R + 1, cj, Block.of("spruce_fence", 0x725430));
        }
    }

    /** 모임지붕 (검은 기와): 처마 1칸, 한 단씩 안으로 */
    private void hipRoof(int y0) {
        int ia = i0 - 1, ib = i1 + 1, ja = j0 - 1, jb = jf + 1;
        for (int t = 0; ; t++) {
            int a = ia + t, b = ib - t, c = ja + t, e = jb - t, y = y0 + t;
            if (a > b || c > e) {
                break;
            }
            if (b - a <= 1 || e - c <= 1) {
                v.fill(a, y, c, b, y, e, Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637));
                break;
            }
            for (int i = a; i <= b; i++) {
                v.set(i, y, c, roofStair("south", i == a ? "outer_right" : i == b ? "outer_left" : "straight"));
                v.set(i, y, e, roofStair("north", i == a ? "outer_left" : i == b ? "outer_right" : "straight"));
            }
            for (int j = c + 1; j < e; j++) {
                v.set(a, y, j, roofStair("east", "straight"));
                v.set(b, y, j, roofStair("west", "straight"));
            }
            if (t > 0) {
                v.fill(a + 1, y - 1, c + 1, b - 1, y - 1, e - 1, TILE_ROOF);
            }
        }
    }

    private static Block roofStair(String facing, String shape) {
        return Block.of("deepslate_tile_stairs[facing=" + facing + ",half=bottom,shape=" + shape + ",waterlogged=false]", 0x363637);
    }

    // ------------------------------------------------------------------ 생활 디테일

    private void details() {
        // 노란 가스관: 정면 오른쪽 모서리를 따라 (필로티는 차 길 위로만)
        if (type != Type.PYEONGCHANG && type != Type.LUX) {
            for (int y = piloti ? 2 : 1; y < roofY(); y++) {
                setIfAir(i1, y, jf + 1, GAS);
            }
        }
        // 실외기: 원룸은 위층마다 창 옆, 다른 집은 몇 대
        for (int k = piloti ? 1 : 0; k < floors; k++) {
            int L = lv[k];
            if (L < 2) {
                continue;
            }
            int step = type == Type.ONEROOM ? 4 : 9;
            for (int i = cx1 + 2; i < i1 - 1; i += step) {
                if (v.get(i, L, jf + 1) == null && v.get(i, L + 1, jf) != null && !v.get(i, L + 1, jf).id().contains("pane")) {
                    v.set(i, L, jf + 1, AC);
                    v.set(i, L - 1, jf + 1, AC_RACK);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 좌우 뒤집기

    private void mirror() {
        Voxels m = new Voxels(v.w, v.d, v.y0, v.y0 + v.h - 1);
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b != null) {
                        m.set(v.w - 1 - i, y, j, flipX(b));
                    }
                }
            }
        }
        for (double[] s : v.carSpots()) {
            m.carSpot(v.w - s[0], (int) s[1], s[2], (int) -s[3], (int) s[4]);
        }
        v = m;
    }
}
