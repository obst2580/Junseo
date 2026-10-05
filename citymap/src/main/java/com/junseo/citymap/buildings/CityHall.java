package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 서울시청: 옛 청사(1926년 화강석 4층, 가운데 시계탑, 지금은 서울도서관)와 그 뒤 새 청사(13층, 물결처럼 앞으로
 * 기울어 나온 유리 정면), 그리고 길 건너 서울광장(타원 잔디).
 * <ul>
 *   <li>옛 청사: 가운데 앞으로 나온 현관부(아치 세 개)와 그 위 시계탑, 세로로 긴 창, 화강석 띠.
 *       안은 서울도서관: 1층 안내·로비, 양 날개 자료실(책장 줄과 열람 탁자), 2·3층 벽을 가득 채운 책장과 열람실,
 *       4층 옛 시장실. 가운데 계단과 층마다 화장실.</li>
 *   <li>새 청사: 위로 갈수록 앞으로 나오는 물결 유리 정면. 1층(높이 8) 시민 로비의 수직 정원(초록 벽),
 *       민원실(번호 붙은 민원 창구, 번호표, 대기 의자), 9층 시의회 회의장(반원 의석, 의장석), 13층 하늘광장,
 *       나머지 사무실. 코어 계단·엘리베이터·화장실.</li>
 * </ul>
 */
final class CityHall {
    // ------------------------------------------------------------------ 옛 청사 (서울도서관)

    static final int OLD_W = 41, OLD_D = 24;
    static final int[] OLD_LEVELS = {0, 5, 10, 14, 18};

    private static final Block GRANITE = Block.of("smooth_stone", 0x9E9E9E);
    private static final Block TRIM = POLISHED_ANDESITE;
    private static final Block BASE = Block.of("stone_bricks", 0x7A7979);

    static Voxels oldHall(int w, int d, Random r) {
        int[] lv = OLD_LEVELS;
        int roof = lv[lv.length - 1];
        Voxels v = new Voxels(w, d, -1, roof + 16);
        Frame f = Frame.of(v);
        int mid = w / 2;
        int front = d - 3;              // 몸체 앞벽
        int p0 = mid - 6, p1 = mid + 6; // 가운데 현관부 (앞으로 2칸)
        // 바닥판과 겉벽
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("light_gray_concrete", 0x7D7D73));
        for (int k = 0; k < lv.length; k++) {
            v.fill(1, lv[k] - 1, 1, w - 2, lv[k] - 1, front, k == 0 ? POLISHED_ANDESITE : SMOOTH_STONE);
            v.fill(p0, lv[k] - 1, front, p1, lv[k] - 1, d - 1, k == 0 ? POLISHED_ANDESITE : SMOOTH_STONE);
        }
        v.walls(1, 0, 1, w - 2, roof - 1, front, GRANITE);
        v.walls(p0, 0, front, p1, roof - 1, d - 1, GRANITE);
        v.fill(p0 + 1, 0, front, p1 - 1, roof - 2, front, AIR);    // 현관부와 몸체 사이 트임
        // 1층 기단 (돌벽돌)과 층 띠
        v.walls(1, 0, 1, w - 2, 1, front, BASE);
        v.walls(p0, 0, front, p1, 1, d - 1, BASE);
        for (int k = 1; k < lv.length; k++) {
            v.walls(1, lv[k] - 1, 1, w - 2, lv[k] - 1, front, TRIM);
            v.walls(p0, lv[k] - 1, front, p1, lv[k] - 1, d - 1, TRIM);
        }
        // 세로 창: 3칸마다 (기둥 사이), 층마다 2칸 높이
        for (int k = 0; k < lv.length - 1; k++) {
            int y0 = lv[k] + 1, y1 = lv[k] + Math.min(3, lv[k + 1] - lv[k] - 2);
            for (int i = 3; i < w - 3; i += 3) {
                if (i >= p0 && i <= p1) {
                    continue;
                }
                v.fill(i, y0, front, i, y1, front, GLASS_PANE);
                v.fill(i, y0, 1, i, y1, 1, GLASS_PANE);
            }
            for (int j = 4; j < front - 2; j += 3) {
                v.fill(1, y0, j, 1, y1, j, GLASS_PANE);
                v.fill(w - 2, y0, j, w - 2, y1, j, GLASS_PANE);
            }
            for (int i = p0 + 2; i <= p1 - 2; i += 2) {
                if (k > 0) {
                    v.fill(i, y0, d - 1, i, y1, d - 1, GLASS_PANE);
                }
            }
            // 기둥 (창 사이 벽에서 반 칸 나온 느낌: 띠 색)
            for (int i = 2; i < w - 2; i += 3) {
                if (i < p0 || i > p1) {
                    v.fill(i - 1, lv[k], front, i - 1, lv[k + 1] - 2, front, GRANITE);
                }
            }
        }
        // 현관: 아치 셋 (2칸 너비, 3칸 높이, 위 둥근 머리)
        for (int a : new int[]{mid - 4, mid - 1, mid + 2}) {
            v.fill(a, 0, d - 1, a + 1, 2, d - 1, AIR);
            v.set(a, 3, d - 1, Block.of("stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]", 0x7A7979));
            v.set(a + 1, 3, d - 1, Block.of("stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]", 0x7A7979));
        }
        v.fill(p0 + 1, 0, d - 2, p1 - 1, 3, front + 1, AIR);
        v.fill(p0 + 1, -1, d - 2, p1 - 1, -1, front + 1, POLISHED_ANDESITE);
        // 현관 안쪽 벽과 문 (1층만, 위층은 현관부와 몸체가 이어짐)
        v.fill(p0 + 1, 0, front, p1 - 1, 3, front, GRANITE);
        v.fill(mid - 1, 0, front, mid + 1, 2, front, AIR);
        // 지붕 난간
        v.walls(1, roof, 1, w - 2, roof, front, GRANITE);
        v.walls(1, roof + 1, 1, w - 2, roof + 1, front, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.walls(p0, roof, front, p1, roof, d - 1, GRANITE);
        // 시계탑: 현관부 위 네모 탑, 남쪽 면 시계, 위 난간과 작은 지붕
        clockTower(v, mid, d - 1, roof);
        oldInside(v, f, r, w, d, mid, front, p0, p1);
        v.connect();
        return v;
    }

    /** 시계탑 (현관부 앞 끝 jf 에서 안쪽으로) */
    private static void clockTower(Voxels v, int mid, int jf, int roof) {
        int t0 = mid - 3, t1 = mid + 3, j0 = jf - 5, j1 = jf - 1;
        int top = roof + 11;
        v.walls(t0, roof, j0, t1, top, j1, GRANITE);
        v.fill(t0 + 1, roof, j0 + 1, t1 - 1, top, j1 - 1, Block.of("stone_bricks", 0x7A7979));
        // 모서리 기둥과 띠
        for (int i : new int[]{t0, t1}) {
            for (int j : new int[]{j0, j1}) {
                v.fill(i, roof, j, i, top, j, TRIM);
            }
        }
        v.walls(t0, roof + 4, j0, t1, roof + 4, j1, TRIM);
        v.walls(t0 - 1, top + 1, j0 - 1, t1 + 1, top + 1, j1 + 1, TRIM);
        v.walls(t0 - 1, top + 2, j0 - 1, t1 + 1, top + 2, j1 + 1, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
        v.fill(t0, top + 1, j0, t1, top + 1, j1, TRIM);
        v.fill(mid - 1, top + 2, j0 + 2, mid + 1, top + 3, j1 - 2, GRANITE);
        v.set(mid, top + 4, (j0 + j1) / 2, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
        // 시계 (남쪽 면): 흰 둥근 판, 검은 테와 바늘, 아래 세로 창
        int cy = roof + 8;
        for (int y = cy - 2; y <= cy + 2; y++) {
            for (int i = mid - 2; i <= mid + 2; i++) {
                double dd = Math.hypot(i - mid, y - cy);
                if (dd <= 2.4) {
                    v.set(i, y, j1, dd > 1.6 ? BLACK_CONCRETE : WHITE_CONCRETE);
                }
            }
        }
        v.set(mid, cy, j1, BLACK_CONCRETE);
        v.set(mid, cy + 1, j1, BLACK_CONCRETE);
        v.set(mid + 1, cy, j1, BLACK_CONCRETE);
        for (int y = roof + 1; y <= roof + 3; y++) {
            v.set(mid - 1, y, j1, GLASS_PANE);
            v.set(mid + 1, y, j1, GLASS_PANE);
        }
        // 다른 세 면도 시계
        for (int[] face : new int[][]{{t0, 0}, {t1, 0}}) {
            int fi = face[0], fj = (j0 + j1) / 2;
            for (int y = cy - 1; y <= cy + 1; y++) {
                for (int j = fj - 1; j <= fj + 1; j++) {
                    v.set(fi, y, j, (y == cy && j == fj) ? BLACK_CONCRETE : WHITE_CONCRETE);
                }
            }
        }
    }

    /** 서울도서관 실내 */
    private static void oldInside(Voxels v, Frame f, Random r, int w, int d, int mid, int front, int p0, int p1) {
        int[] lv = OLD_LEVELS;
        Block in = Interior.INNER_WALL;
        // 가운데 계단 (출입구가 남쪽 로비를 봄)
        int sj = front - 5;          // 계단 출입구 벽 줄
        Frame st = Frame.facing(v, mid + 2, sj - 1, "north");
        int sd = Interior.stairDepth(lv);
        Interior.stairCore(st, lv, Interior.CORE_WALL, "polished_andesite", 0x848685);
        st.fill(-1, lv[lv.length - 1] + 3, -1, 5, lv[lv.length - 1] + 3, sd, SMOOTH_STONE);
        int sj0 = sj - 1 - sd;       // 계단실 북쪽 벽 줄
        for (int k = 0; k < lv.length - 1; k++) {
            int L = lv[k], h = lv[k + 1] - lv[k], top = L + h - 2;
            Site.floor(v, 2, 2, w - 3, front - 1, L - 1, k == 0 ? Block.of("polished_diorite", 0xC0C0C1) : Block.of("oak_planks", 0xA2834F),
                    new int[]{mid - 3, sj0, mid + 3, sj});
            // 계단 양옆 화장실 (남·여)
            Interior.restroom(f, mid - 9, sj0 + 1, mid - 5, sj - 1, L, h, "남자 화장실", mid - 7);
            Interior.restroom(f, mid + 5, sj0 + 1, mid + 9, sj - 1, L, h, "여자 화장실", mid + 7);
            // 화장실 문 앞 복도가 막히지 않게 (화장실 남쪽 벽 = sj)
            // 날개 자료실 경계 (안쪽 벽, 가운데 문)
            for (int side : new int[]{-1, 1}) {
                int wi = mid + side * 11;
                v.fill(wi, L, 2, wi, top, front - 1, in);
                Interior.door(f, wi, L, front - 3, "oak", side < 0 ? "west" : "east");
                Interior.door(f, wi, L, 5, "oak", side < 0 ? "west" : "east");
                int a0 = side < 0 ? 2 : wi + 1, a1 = side < 0 ? wi - 1 : w - 3;
                String label = k == 0 ? (side < 0 ? "일반자료실" : "어린이자료실") : k == 3 ? (side < 0 ? "옛 시장실" : "서울기록관") : (k + 1) + "층 자료실";
                v.set(side < 0 ? wi + 1 : wi - 1, L + 1, front - 4, Blocks.wallSign("birch", side < 0 ? "east" : "west", "black", false, "", label));
                if (k == 3 && side < 0) {
                    mayorRoom(v, f, r, a0, a1, L, front, h);
                } else {
                    readingRoom(v, f, r, a0, a1, L, front, h, k > 0);
                }
            }
            // 가운데 뒤쪽 (계단 북쪽): 사무실·열람석
            for (int i = mid - 9; i <= mid + 9; i += 3) {
                if (v.get(i, L, 3) == null) {
                    v.set(i, L, 3, Furniture.BOOKSHELF);
                    v.set(i, L + 1, 3, Furniture.BOOKSHELF);
                }
            }
            Interior.lights(f, mid - 10, 2, mid + 10, sj0 - 2, L, h, 4, Interior.LIGHT);
            // 로비 등
            Interior.lights(f, mid - 10, sj + 1, mid + 10, front - 1, L, h, 4, Interior.LIGHT);
        }
        // 1층 로비 안내 데스크와 이름판
        for (int i = mid - 3; i <= mid - 1; i++) {
            v.set(i - 4, 0, front - 2, Furniture.COUNTER);
        }
        Furniture.chair(f, mid - 6, 0, front - 3, "north", "dark_oak");
        v.set(mid + 3, 1, sj + 1, Blocks.wallSign("dark_oak", "south", "white", false, "서울도서관", "1층 자료실", "2·3층 열람실", "4층 옛 시장실"));
        // 현관 안 이름판 (현관부 안쪽 벽, 바깥에서 보임)
        v.set(mid, 3, front + 1, Blocks.wallSign("dark_oak", "south", "yellow", false, "", "서울도서관", "옛 서울시청"));
    }

    /** 자료실: 책장 줄 + 열람 탁자 (위층은 바깥 벽을 따라 책벽) */
    private static void readingRoom(Voxels v, Frame f, Random r, int a0, int a1, int L, int front, int h, boolean wallOfBooks) {
        int top = L + h - 2;
        if (wallOfBooks) {
            for (int i = a0; i <= a1; i++) {
                for (int y = L; y <= top; y++) {
                    if (v.get(i, y, 2) == null) {
                        v.set(i, y, 2, Furniture.BOOKSHELF);
                    }
                }
            }
        }
        // 책장 줄 (남북으로)
        for (int i = a0 + 1; i <= a1 - 1; i += 3) {
            for (int j = 4; j <= front / 2; j++) {
                v.set(i, L, j, Furniture.BOOKSHELF);
                v.set(i, L + 1, j, Furniture.BOOKSHELF);
            }
        }
        // 열람 탁자 (앞쪽)
        for (int i = a0 + 1; i + 2 <= a1 - 1; i += 4) {
            for (int j = front / 2 + 3; j + 1 <= front - 5; j += 4) {
                Furniture.table(f, i, L, j, 3, 1, "oak");
                v.set(i + 1, L + 1, j, Block.of("lantern[hanging=false,waterlogged=false]", 0x6A5B49));
            }
        }
        Interior.lights(f, a0, 2, a1, front - 1, L, h, 4, Interior.LIGHT);
    }

    /** 4층 옛 시장실: 큰 책상, 회의 탁자, 소파, 책장, 붉은 깔개 길 */
    private static void mayorRoom(Voxels v, Frame f, Random r, int a0, int a1, int L, int front, int h) {
        Interior.floor(f, a0, 2, a1, front - 1, L, Block.of("dark_oak_planks", 0x432B14));
        int ci = (a0 + a1) / 2;
        for (int i = ci - 2; i <= ci + 2; i++) {
            v.set(i, L, 5, Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
        }
        Furniture.chair(f, ci, L, 4, "north", "dark_oak");
        for (int j = 7; j <= front - 2; j++) {
            v.set(ci, L, j, Block.of("red_carpet", 0xA12722));
        }
        Furniture.table(f, ci - 4, L, 10, 3, 1, "dark_oak");
        Furniture.sofa(f, r, a0 + 1, L, front - 3, 3, "north");
        for (int i = a0 + 1; i <= a1 - 1; i++) {
            if (v.get(i, L, 2) == null) {
                v.set(i, L, 2, Furniture.BOOKSHELF);
                v.set(i, L + 1, 2, Furniture.BOOKSHELF);
            }
        }
        v.set(a1 - 1, L, 4, Block.of("polished_andesite", 0x848685));
        v.fill(a1 - 1, L + 1, 4, a1 - 1, L + 2, 4, Block.of("end_rod[facing=up]", 0xE8E2D8));   // 깃대
        Interior.lights(f, a0, 2, a1, front - 1, L, h, 4, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 새 청사

    static final int NEW_W = 71, NEW_D = 58, NEW_FLOORS = 13;
    /** 새 청사 물결: 맨 위층 정면이 1층 정면보다 앞으로 나온 칸 수 */
    static final int WAVE = 10;
    /** 정면이 평면에서 휜 정도 (가운데가 양 끝보다 앞으로) */
    static final int CURVE = 5;
    /** 지붕이 뒤로 갈수록 낮아지는 물결: 맨 위층 뒷벽이 1층 뒷벽보다 앞으로 들어온 칸 수 */
    static final int BACK = 16;

    /**
     * 새 청사 층 k 의 바닥판. 위로 갈수록 정면이 앞으로 휘어 나오고(물결 머리), 뒤쪽은 위로 갈수록 깎여서
     * 옆에서 보면 뒤에서 앞으로 솟아올라 앞으로 쏟아지는 파도 모양입니다.
     */
    static boolean newInside(int w, int d, int i, int j, int k) {
        double t = k / (double) (NEW_FLOORS - 1);
        double c = (i - (w - 1) / 2.0) / ((w - 1) / 2.0);
        int front = d - 1 - WAVE + (int) Math.round(WAVE * t * t) - (int) Math.round(CURVE * c * c);
        double u = Math.max(0, (t - 0.35) / 0.65);
        int back = (int) Math.round(BACK * u * u);
        return i >= 0 && i < w && j >= back && j <= front;
    }

    static int[] newLevels() {
        return Floors.levels(Floors.HALL, Floors.OFFICE, NEW_FLOORS);
    }

    static Voxels newHall(int w, int d, Random r) {
        Tower.Spec s = new Tower.Spec(w, d, NEW_FLOORS);
        s.lobbyH = Floors.HALL;
        s.shape = (i, j, k) -> newInside(w, d, i, j, k);
        s.glass = Block.of("light_blue_stained_glass", 0x6699D8);
        s.mullion = WHITE_CONCRETE;
        s.spandrel = Block.of("light_gray_concrete", 0x7D7D73);
        s.podium = POLISHED_ANDESITE;
        s.mullionEvery = 2;
        s.elevators = 2;
        s.restrooms = 2;
        s.name = "서울시청";
        s.use = k -> switch (k) {
            case 0, 8 -> Tower.Use.CUSTOM;
            case NEW_FLOORS - 1 -> Tower.Use.OBSERVATORY;
            default -> Tower.Use.OFFICE;
        };
        s.custom = (t, k, level, h) -> {
            if (k == 0) {
                civicLobby(t, level, h);
            } else {
                council(t, k, level, h);
            }
        };
        Tower t = Tower.build(s, r);
        Voxels v = t.v;
        // 정문 (1층 정면 가운데)
        int mid = w / 2;
        int jf = frontAt(t, mid, 0);
        for (int i = mid - 3; i <= mid + 3; i++) {
            int j = frontAt(t, i, 0);
            v.fill(i, 0, j, i, 2, j, AIR);
        }
        // 하늘광장 안내
        int top = t.levels[NEW_FLOORS - 1];
        v.set(t.ci0 + 6, top + 1, t.corridorJ, Blocks.wallSign("dark_oak", "south", "white", true, "", "하늘광장", "13층"));
        v.connect();
        return v;
    }

    /** 층 k 에서 i 줄의 정면 j */
    private static int frontAt(Tower t, int i, int k) {
        for (int j = t.s.d - 1; j >= 0; j--) {
            if (t.inside(i, j, k)) {
                return j;
            }
        }
        return 0;
    }

    /** 1층 시민 로비: 수직 정원(초록 벽), 안내, 민원실(번호 창구, 번호표, 대기 의자) */
    private static void civicLobby(Tower t, int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        Random r = new Random(t.r.nextLong());
        int w = t.s.w, top = level + h - 2, mid = w / 2;
        int cor = t.corridorJ;
        // 바닥
        for (int j = 0; j < t.s.d; j++) {
            for (int i = 0; i < w; i++) {
                if (t.free(i, j, 0) || (t.inside(i, j, 0) && !t.edge(i, j, 0) && j >= cor)) {
                    v.set(i, level - 1, j, Math.floorMod(i + j, 5) == 0 ? POLISHED_DIORITE : Block.of("smooth_quartz", 0xECE6DF));
                }
            }
        }
        // 수직 정원: 시민 로비 뒷벽 전체를 덮는 초록 벽 (높이 전체)
        int[] bb = t.bounds(0);
        int gj = bb[1] + 1;
        for (int i = bb[0] + 1; i <= bb[2] - 1; i++) {
            for (int y = level; y <= top; y++) {
                if (t.inside(i, gj, 0) && !t.edge(i, gj, 0)) {
                    int g = Math.floorMod(i * 7 + y * 3, 5);
                    v.set(i, y, gj, g == 0 ? Block.of("flowering_azalea_leaves[persistent=true]", 0x63753A)
                            : g <= 2 ? Block.of("moss_block", 0x596E2D) : OAK_LEAVES);
                }
            }
        }
        v.set(mid, level + 1, gj + 1, Blocks.wallSign("birch", "south", "green", false, "", "수직 정원"));
        // 로비 뒤쪽: 시민 쉼터 (소파 둘레 탁자), 전시판, 화분
        for (int j = gj + 6; j <= t.cj0 - 6; j += 11) {
            for (int i = bb[0] + 6; i <= bb[2] - 8; i += 14) {
                if (t.free(i, j, 0) && t.free(i + 3, j + 3, 0)) {
                    Furniture.sofa(f, r, i, level, j, 3, "south");
                    v.set(i + 1, level, j + 2, Block.of("oak_slab[type=top,waterlogged=false]", 0xA2834F));
                    Furniture.sofa(f, r, i, level, j + 4, 3, "north");
                    Furniture.plant(f, r, i + 4, level, j + 2);
                }
            }
        }
        for (int i = bb[0] + 9; i <= bb[2] - 9; i += 9) {
            int j = t.cj0 - 3;
            if (t.free(i, j, 0) && t.free(i + 3, j, 0)) {
                v.fill(i, level, j, i + 3, level + 2, j, WHITE_CONCRETE);   // 전시판
            }
        }
        // 안내 데스크 (정문 안)
        int jf = frontAt(t, mid, 0);
        for (int i = mid - 3; i <= mid + 3; i++) {
            v.set(i, level, jf - 7, Furniture.COUNTER);
        }
        Furniture.chair(f, mid, level, jf - 8, "north", "dark_oak");
        v.set(mid, level + 3, jf - 7, Blocks.hangingSign("dark_oak", 0, "white", true, "", "안내", "Information"));
        v.fill(mid, level + 4, jf - 7, mid, top, jf - 7, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
        // 민원실 (오른쪽): 창구 줄은 동서로, 직원은 북쪽
        int a0 = t.ci1 + 4, a1 = w - 4;
        int cj = cor + 3;   // 창구 줄
        for (int i = a0; i <= a1; i++) {
            if (!t.inside(i, cj, 0) || t.edge(i, cj, 0)) {
                continue;
            }
            v.set(i, level, cj, Furniture.COUNTER);
            if ((i - a0) % 3 == 2) {
                v.set(i, level + 1, cj, Block.of("white_stained_glass_pane", 0xF0F0F0));
            } else {
                v.set(i, level + 1, cj, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            }
            v.fill(i, level + 3, cj, i, level + 3, cj, WHITE_CONCRETE);
            if ((i - a0) % 3 == 0) {
                int n = (i - a0) / 3 + 1;
                v.set(i, level + 3, cj + 1, Blocks.wallSign("birch", "south", "black", true, "", "민원 " + n + "번"));
                Furniture.chair(f, i, level, cj + 1, "south", "birch");
                Furniture.chair(f, i, level, cj - 1, "north", "dark_oak");
            }
        }
        v.set((a0 + a1) / 2, level + 4, cj + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "민원실"));
        // 대기 의자 (창구를 봄)와 번호표
        for (int j = cj + 4; j <= cj + 10; j += 2) {
            for (int i = a0 + 1; i <= a1 - 1; i++) {
                if (t.free(i, j, 0) && (i - a0) % 6 != 0 && j < frontAt(t, i, 0) - 2) {
                    Furniture.chair(f, i, level, j, "south", "dark_oak");
                }
            }
        }
        v.fill(a0 - 2, level, cj + 3, a0 - 2, level + 1, cj + 3, Block.of("light_gray_concrete", 0x7D7D73));
        v.set(a0 - 2, level + 2, cj + 3, Block.of("stone_button[face=floor,facing=south,powered=false]", 0x7E7E7E));
        v.set(a0 - 2, level + 1, cj + 4, Blocks.wallSign("birch", "south", "black", false, "", "번호표"));
        // 층별 안내
        v.set(t.ci0 + 6, level + 1, cor, Blocks.wallSign("dark_oak", "south", "white", true, "서울시청", "1층 민원실", "9층 회의장", "13층 하늘광장"));
        // 등
        for (int j = 2; j < t.s.d - 1; j += 5) {
            for (int i = 2; i < w - 1; i += 6) {
                if (t.inside(i, j, 0) && !t.edge(i, j, 0) && v.get(i, top, j) == null && !(i >= t.ci0 && i <= t.ci1 && j >= t.cj0 && j <= t.cj1)) {
                    v.set(i, top, j, Interior.LIGHT);
                }
            }
        }
        Furniture.plant(f, r, mid - 5, level, jf - 2);
        Furniture.plant(f, r, mid + 5, level, jf - 2);
    }

    /** 9층 시의회 회의장: 의장석과 발언대, 반원으로 놓인 의원석, 방청석 */
    private static void council(Tower t, int k, int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        int[] b = t.bounds(k);
        int mid = (b[0] + b[2]) / 2;
        int jf = frontAt(t, mid, k);
        int cj = t.corridorJ + 3;        // 의장석 줄 (코어 앞)
        for (int j = cj - 1; j < jf; j++) {
            for (int i = b[0] + 1; i < b[2]; i++) {
                if (t.inside(i, j, k) && !t.edge(i, j, k)) {
                    v.set(i, level - 1, j, Block.of("blue_wool", 0x35399D));
                }
            }
        }
        // 의장석 (단 위)과 발언대
        for (int i = mid - 4; i <= mid + 4; i++) {
            v.set(i, level, cj, Block.of("dark_oak_planks", 0x432B14));
        }
        for (int i = mid - 3; i <= mid + 3; i++) {
            v.set(i, level + 1, cj, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        }
        v.set(mid, level, cj + 2, Block.of("dark_oak_planks", 0x432B14));
        v.set(mid, level + 1, cj + 2, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        // 의원석: 의장석을 바라보는 반원 줄
        for (int row = 0; row < 4; row++) {
            double rad = 6 + row * 2.5;
            for (int deg = 200; deg <= 340; deg += 9) {
                double a = Math.toRadians(deg);
                int i = (int) Math.round(mid + rad * Math.cos(a)), j = (int) Math.round(cj - rad * Math.sin(a));
                if (t.free(i, j, k) && t.free(i, j + 1, k) && v.get(i, level, j) == null) {
                    v.set(i, level, j, Furniture.DESK_TOP);
                    Furniture.chair(f, i, level, j + 1, "south", "dark_oak");
                }
            }
        }
        v.set(t.ci0 + 6, level + 1, t.corridorJ, Blocks.wallSign("dark_oak", "south", "white", true, "", "시의회", "본회의장"));
        Interior.lights(f, b[0] + 1, cj, b[2] - 1, jf - 1, level, h, 4, Interior.LIGHT);
        // 뒤쪽은 의회 사무실
        Rooms.office(f, new Random(k), b[0], b[1], b[2], t.cj0 - 3, level, h, (i, j) -> t.free(i, j, k) && j < t.cj0 - 2);
    }

    // ------------------------------------------------------------------ 서울광장

    /** 서울광장 (i = x - x0, j = z - z0): 타원 잔디, 화강석 포장, 꽃밭, 보행등, 의자, 표석 */
    static Voxels plaza(Site s, int ci, int cj, int hi, int hj) {
        Voxels v = new Voxels(s.w, s.d, -1, 6);
        double rx = Math.min(28, ci - 4), rz = Math.min(20, cj - 4);
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (!s.land(i, j)) {
                    continue;
                }
                double e = Math.hypot((i - ci) / rx, (j - cj) / rz);
                Block b;
                if (e < 1) {
                    b = GRASS;
                } else if (e < 1 + 1.3 / rz) {
                    b = SMOOTH_STONE;          // 잔디 테두리 돌
                } else {
                    b = Math.floorMod(i + 2 * j, 7) == 0 ? Block.of("light_gray_concrete", 0x7D7D73) : POLISHED_ANDESITE;
                }
                v.set(i, -1, j, b);
            }
        }
        // 잔디 둘레 보행등과 꽃밭, 의자
        for (int k = 0; k < 28; k++) {
            double a = 2 * Math.PI * k / 28;
            int li = (int) Math.round(ci + (rx + 3) * Math.cos(a)), lj = (int) Math.round(cj + (rz + 3) * Math.sin(a));
            if (!s.solid(li, lj)) {
                continue;
            }
            if (k % 4 == 0) {
                Site.lamp(v, li, lj);
            } else if (k % 4 == 2) {
                v.set(li, 0, lj, Site.BENCH);
            }
            int fi = (int) Math.round(ci + (rx - 1.5) * Math.cos(a)), fj = (int) Math.round(cj + (rz - 1.5) * Math.sin(a));
            String[] flowers = {"poppy", "dandelion", "oxeye_daisy", "cornflower", "azure_bluet"};
            v.set(fi, 0, fj, Block.of(flowers[k % flowers.length], 0xC0503A));
        }
        // 가장자리 나무 (도로 쪽 둘레, 광장 안쪽은 비움)
        for (int j = 3; j < s.d - 3; j += 7) {
            for (int i = 3; i < s.w - 3; i += 7) {
                double e = Math.hypot((i - ci) / rx, (j - cj) / rz);
                if (e > 1.5 && s.solid(i, j, 2) && s.roadDistance(i, j) < 9 && Math.hypot(i - hi, j - hj) > 9) {
                    Site.tree(v, i, j, false);
                }
            }
        }
        // 표석
        int mi = ci - 2, mj = (int) (cj + rz + 5);
        if (s.solid(mi, mj, 1) && s.solid(mi + 4, mj, 1)) {
            v.fill(mi, 0, mj, mi + 4, 1, mj, POLISHED_GRANITE);
            v.set(mi + 2, 1, mj + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", "서울광장", "SEOUL PLAZA"));
            v.set(mi + 2, 1, mj - 1, Blocks.wallSign("dark_oak", "north", "white", false, "", "서울광장", "SEOUL PLAZA"));
        }
        v.connect();
        return v;
    }

    /** 옛 청사·새 청사 앞뒤 마당 (i = x - x0, j = z - z0): 건물 자리는 비우고 보도 포장, 화단 */
    static Voxels yard(Site s, int[][] keep) {
        Voxels v = new Voxels(s.w, s.d, -1, 8);
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (!s.land(i, j) || inside(keep, i, j)) {
                    continue;
                }
                v.set(i, -1, j, Math.floorMod(i + j, 6) == 0 ? Block.of("light_gray_concrete", 0x7D7D73) : POLISHED_ANDESITE);
            }
        }
        for (int j = 2; j < s.d - 2; j += 8) {
            for (int i = 2; i < s.w - 2; i += 8) {
                if (s.solid(i, j, 2) && !near(keep, i, j, 4)) {
                    Site.planter(v, i, j);
                }
            }
        }
        v.connect();
        return v;
    }

    private static boolean inside(int[][] boxes, int i, int j) {
        for (int[] b : boxes) {
            if (i >= b[0] && i <= b[2] && j >= b[1] && j <= b[3]) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(int[][] boxes, int i, int j, int r) {
        for (int[] b : boxes) {
            if (i >= b[0] - r && i <= b[2] + r && j >= b[1] - r && j <= b[3] + r) {
                return true;
            }
        }
        return false;
    }

    private CityHall() {
    }
}
