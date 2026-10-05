package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 경찰서 (용산경찰서·강남경찰서): 6층 관공서 건물과 앞마당·뒷마당.
 * <ul>
 *   <li>앞마당: 도로 쪽 보도와 이어진 포장 마당, 가운데 정문 길(거점 자리), 국기 게양대 세 개(깃대만),
 *       양옆 주차장(순찰차·민원인 칸, 주차선만), 화단과 볼라드. 건물 양옆은 뒷마당으로 가는 차로</li>
 *   <li>1층(5칸): 로비와 안내 데스크, 민원실·교통민원실(창구와 대기 의자), 무기고, 당직실, 상황실, 후문</li>
 *   <li>2~5층(4칸): 형사과·수사과·여성청소년과 사무실, 유치장(쇠창살 방), 조사실, 회의실, 전산실, 서장실,
 *       브리핑룸, 남녀 탈의실</li>
 *   <li>6층(8칸, 실제로 높게 트는 곳): 강당, 무도장, 체력단련실, 구내식당. 정면 윗부분은 창 없는 돌 띠에 큰 글씨</li>
 *   <li>양 끝 계단실(옥상까지 걸어서), 가운데 엘리베이터 두 대와 남녀 화장실, 옥상 무전 안테나</li>
 * </ul>
 * 정면은 남쪽(j = d-1, 도로 쪽). CLASSIC 은 베이지 돌과 네모 창(용산), MODERN 은 유리 커튼월과
 * 흰 돌 틀(강남)입니다.
 */
final class PoliceStation {
    enum Style { CLASSIC, MODERN }

    /** 앞마당 깊이 (도로 쪽) */
    static final int FORECOURT = 21;
    /** 건물 깊이 */
    static final int DEPTH = 25;
    /** 건물 옆 차로 폭 */
    static final int SIDE = 6;
    /** 서는 높이: 1층 5, 2~5층 4, 6층(강당·무도장) 8, 옥상 */
    static final int[] LEVELS = {0, 5, 9, 13, 17, 21, 29};
    static final int FLOORS = LEVELS.length - 1;
    /** 대지 깊이 (앞마당 + 건물 + 뒷마당) */
    static final int SITE_DEPTH = 60;

    private static final Block IN = Interior.INNER_WALL;
    private static final Block CARPET = Rooms.CARPET_TILE;
    private static final Block OFFICE_FLOOR = Block.of("light_gray_concrete", 0x7D7D73);
    private static final Block CORRIDOR = Block.of("polished_diorite", 0xC0C0C1);
    private static final Block CHAIN = Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050);
    private static final Block IRON_DOOR_LO = Block.of("iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]", 0xC2C1C1);
    private static final Block IRON_DOOR_HI = Block.of("iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]", 0xC2C1C1);
    private static final Block LOCKER = Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1);

    private final Voxels v;
    private final Random r;
    private final Style style;
    private final String name;
    private final int w, d, bi0, bi1, bj0, bj1, cx, hubI, hubJ;
    /** 복도 북쪽 벽, 복도, 복도 남쪽 벽 (j) */
    private final int jCN, jC0, jC1, jCS, jB0, jB1, jF0, jF1;
    /** 뒤쪽 방 구역 (i): 서쪽 계단실 옆 ~ 남자 화장실 앞, 여자 화장실 옆 ~ 동쪽 계단실 앞 */
    private final int wb0, wb1, eb0, eb1;
    private final Block wall, base, trim, glass, frame, band, letter;

    private PoliceStation(int w, int d, Random r, Style style, String name, int hubI, int hubFront) {
        this.w = w;
        this.d = d;
        this.r = r;
        this.style = style;
        this.name = name;
        this.hubI = hubI;
        this.hubJ = d - 1 - hubFront;
        v = new Voxels(w, d, -1, LEVELS[FLOORS] + 18);
        bi0 = SIDE;
        bi1 = w - 1 - SIDE;
        bj1 = d - 1 - FORECOURT;
        bj0 = bj1 - DEPTH + 1;
        cx = (bi0 + bi1) / 2;
        jB0 = bj0 + 1;
        jB1 = bj0 + 9;
        jCN = bj0 + 10;
        jC0 = bj0 + 11;
        jC1 = bj0 + 13;
        jCS = bj0 + 14;
        jF0 = bj0 + 15;
        jF1 = bj1 - 1;
        wb0 = bi0 + 7;
        wb1 = cx - 13;
        eb0 = cx + 13;
        eb1 = bi1 - 7;
        if (style == Style.CLASSIC) {
            wall = WHITE_TERRACOTTA;
            base = POLISHED_ANDESITE;
            trim = SMOOTH_QUARTZ;
            glass = Block.of("light_blue_stained_glass_pane", 0x6699D8);
            frame = GRAY_CONCRETE;
            band = WHITE_TERRACOTTA;
            letter = BLUE_CONCRETE;
        } else {
            wall = CALCITE;
            base = POLISHED_DEEPSLATE;
            trim = SMOOTH_QUARTZ;
            glass = Block.of("gray_stained_glass", 0x4C4C4C);
            frame = Block.of("light_gray_concrete", 0x7D7D73);
            band = POLISHED_DEEPSLATE;
            letter = WHITE_CONCRETE;
        }
    }

    /**
     * @param hubI     거점의 i. 거점 둘레(5×5 와 여유)는 비워 둡니다
     * @param hubFront 거점이 대지 앞 끝(도로 쪽)에서 몇 칸 안쪽인지
     */
    static Voxels build(int w, int d, Random r, Style style, String name, int hubI, int hubFront) {
        PoliceStation p = new PoliceStation(w, d, r, style, name, hubI, hubFront);
        p.slabs();
        for (int k = 0; k < FLOORS; k++) {
            p.floor(k);
        }
        p.cores();
        p.facade();
        p.roof();
        p.forecourt();
        p.rearYard();
        p.v.connect();
        return p.v;
    }

    /**
     * 거점 앞에 경찰서 대지를 잡아 놓습니다: 거점에서 가장 가까운 도로 쪽이 정면, 대지 앞 끝은 그 인도 바로 안쪽.
     * 대지는 도로를 따라 width 칸, 안쪽으로 {@link #SITE_DEPTH} 칸이고, 거점이 가운데 오도록 하되
     * 도로·물·산·구역 밖에 걸리면 옆으로 밀어 봅니다. 못 놓으면 null.
     */
    static Placement place(com.junseo.citymap.terrain.CityTerrain t, com.junseo.citymap.geo.Polygon area, double hx, double hz,
                           int width, String name, Style style, Random rnd) {
        int x = (int) Math.floor(hx), z = (int) Math.floor(hz);
        int[][] dirs = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        String[] fronts = {"south", "west", "north", "east"};
        int best = -1, dist = Integer.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            for (int s = 1; s <= 40; s++) {
                if (blocked(t, x + dirs[k][0] * s, z + dirs[k][1] * s)) {
                    if (s < dist) {
                        dist = s;
                        best = k;
                    }
                    break;
                }
            }
        }
        if (best < 0 || dist < 8) {
            return null;
        }
        int hubFront = dist - 1;
        int dx = dirs[best][0], dz = dirs[best][1];
        // 앞 끝 칸과 안쪽 끝 칸 (정면 방향 축)
        int along = dx != 0 ? x : z;
        int front = along + (dx + dz) * hubFront, back = front - (dx + dz) * (SITE_DEPTH - 1);
        int lo = Math.min(front, back), hi = Math.max(front, back);
        int lateral = dx != 0 ? z : x;
        for (int shift = 0; shift <= width / 2 - 8; shift++) {
            for (int sign : new int[]{1, -1}) {
                int c0 = lateral - width / 2 + shift * sign, c1 = c0 + width - 1;
                int x0 = dx != 0 ? lo : c0, x1 = dx != 0 ? hi : c1, z0 = dx != 0 ? c0 : lo, z1 = dx != 0 ? c1 : hi;
                if (!landOk(t, area, x0, z0, x1, z1)) {
                    continue;
                }
                String f = fronts[best];
                // 정면 방향으로 돌렸을 때 거점의 건물 좌표 i
                int hubI = switch (f) {
                    case "south" -> x - x0;
                    case "north" -> x1 - x;
                    case "west" -> z - z0;
                    default -> z1 - z;
                };
                long seed = rnd.nextLong();
                return Placement.rect(name, "police", x0, z0, x1, z1, f,
                        (w, d) -> build(w, d, new Random(seed), style, name, hubI, hubFront));
            }
        }
        return null;
    }

    private static boolean blocked(com.junseo.citymap.terrain.CityTerrain t, int x, int z) {
        com.junseo.citymap.terrain.Column c = t.column(x, z);
        return c.isRoad() || c.isWater() || c.deck || c.tunnel;
    }

    /** 직사각형이 모두 구역 안의 평평한 땅인지 (도로·물·산이 아님) */
    static boolean landOk(com.junseo.citymap.terrain.CityTerrain t, com.junseo.citymap.geo.Polygon area, int x0, int z0, int x1, int z1) {
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                if (blocked(t, x, z) || t.column(x, z).mountainHeight > 0 || !area.contains(x + 0.5, z + 0.5)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 층마다 서는 높이 (검사용) */
    static int[] levels() {
        return LEVELS.clone();
    }

    private int height(int k) {
        return LEVELS[k + 1] - LEVELS[k];
    }

    // ------------------------------------------------------------------ 뼈대

    private void slabs() {
        // 대지 바닥 (앞마당·차로·뒷마당은 아래에서 다시 깜)
        v.fill(0, -1, 0, w - 1, -1, d - 1, LIGHT_GRAY_CONCRETE);
        for (int k = 0; k <= FLOORS; k++) {
            int y = LEVELS[k] - 1;
            v.fill(bi0, y, bj0, bi1, y, bj1, k == 0 ? POLISHED_ANDESITE : k == FLOORS ? SMOOTH_STONE : SMOOTH_STONE);
        }
    }

    /** 계단실 둘, 엘리베이터 둘, 남녀 화장실 (층마다) */
    private void cores() {
        Block cw = Interior.CORE_WALL;
        // 계단실: +b 가 북쪽(뒤), 출입구가 복도(남쪽)를 봄. 서쪽은 a 가 서쪽으로, 바깥 벽에 붙임
        Interior.stairCore(Frame.facing(v, bi0 + 5, jB1, "north"), LEVELS, cw, "polished_andesite", 0x848685);
        Interior.stairCore(Frame.facing(v, bi1 - 1, jB1, "north"), LEVELS, cw, "polished_andesite", 0x848685);
        int depth = Interior.stairDepth(LEVELS);
        int top = LEVELS[FLOORS] + 3;
        // 옥탑 지붕
        v.fill(bi0, top, jB1 - depth, bi0 + 6, top, jCN, SMOOTH_STONE);
        v.fill(bi1 - 6, top, jB1 - depth, bi1, top, jCN, SMOOTH_STONE);
        // 계단실 뒤 남는 줄은 벽으로
        v.fill(bi0 + 1, 0, jB0, bi0 + 5, LEVELS[FLOORS] - 1, jB1 - depth, cw);
        v.fill(bi1 - 5, 0, jB0, bi1 - 1, LEVELS[FLOORS] - 1, jB1 - depth, cw);
        // 엘리베이터 두 대 (문은 복도 쪽)
        int[] lift = java.util.Arrays.copyOf(LEVELS, FLOORS);
        for (int e = 0; e < 2; e++) {
            int ce = cx - 2 + 3 * e;
            Interior.elevator(Frame.facing(v, ce, jB1 - 1, "south"), lift, cw);
            // 승강로는 옥상 기계실까지
            v.fill(ce - 1, LEVELS[FLOORS - 1] + 4, jB1 - 2, ce + 2, LEVELS[FLOORS] + 3, jCN, cw);
            v.fill(ce, LEVELS[FLOORS - 1] + 3, jB1 - 1, ce + 1, LEVELS[FLOORS] + 2, jB1, AIR);
        }
        // 엘리베이터 기계실 지붕
        v.fill(cx - 3, LEVELS[FLOORS] + 3, jB1 - 2, cx + 3, LEVELS[FLOORS] + 3, jCN, SMOOTH_STONE);
        // 화장실: 엘리베이터 양옆, 문은 복도 쪽
        Frame f = Frame.of(v);
        for (int k = 0; k < FLOORS; k++) {
            int L = LEVELS[k], h = Math.min(height(k), Floors.OFFICE + 1);
            Interior.restroom(f, cx - 11, jB0 + 2, cx - 5, jB1, L, h, "남자 화장실", cx - 8);
            Interior.restroom(f, cx + 5, jB0 + 2, cx + 11, jB1, L, h, "여자 화장실", cx + 8);
            if (height(k) > h) {
                // 높은 층(6층)은 화장실 위에 천장
                v.fill(cx - 12, L + h - 1, jB0 + 1, cx - 4, L + h - 1, jCN, IN);
                v.fill(cx + 4, L + h - 1, jB0 + 1, cx + 12, L + h - 1, jCN, IN);
                v.fill(cx - 3, L + h - 1, jB0 + 1, cx + 3, L + h - 1, jB0 + 1, IN);
            }
        }
    }

    // ------------------------------------------------------------------ 층

    private void floor(int k) {
        int L = LEVELS[k], h = height(k), top = L + h - 2;
        Frame f = Frame.of(v);
        // 복도 바닥과 벽
        Interior.floor(f, bi0 + 1, jC0, bi1 - 1, jC1, L, CORRIDOR);
        v.fill(bi0 + 1, L, jCN, bi1 - 1, top, jCN, IN);
        if (k == 0) {
            v.fill(bi0 + 1, L, jCS, cx - 10, top, jCS, IN);
            v.fill(cx + 10, L, jCS, bi1 - 1, top, jCS, IN);
        } else {
            v.fill(bi0 + 1, L, jCS, bi1 - 1, top, jCS, IN);
        }
        // 복도 등
        for (int i = bi0 + 3; i <= bi1 - 3; i += 5) {
            v.set(i, top, (jC0 + jC1) / 2, Interior.LIGHT);
        }
        switch (k) {
            case 0 -> ground();
            case 1 -> {
                if (style == Style.MODERN) {
                    // 2층 높이로 트인 로비 (아트리움): 앞쪽 가운데 바닥을 비우고 복도에 유리 난간
                    office(bi0 + 1, cx - 10, L, h, "형사과", new String[]{"강력1팀", "강력2팀"});
                    office(cx + 10, bi1 - 1, L, h, "여성청소년과", new String[]{"여청수사팀", "학교폭력"});
                    v.fill(cx - 9, L - 1, jF0, cx + 9, L - 1, jF1, AIR);
                    v.fill(cx - 9, L, jCS, cx + 9, top, jCS, AIR);
                    v.fill(cx - 9, L, jCS, cx + 9, L, jCS, Block.of("glass_pane", 0xC8DCE4));
                    for (int i = cx - 6; i <= cx + 6; i += 6) {
                        v.set(i, top, (jF0 + jF1) / 2, CHAIN);
                        v.set(i, top - 1, (jF0 + jF1) / 2, Interior.LIGHT);
                    }
                } else {
                    office(bi0 + 1, cx - 1, L, h, "형사과", new String[]{"강력1팀", "강력2팀", "형사지원팀"});
                    office(cx + 1, bi1 - 1, L, h, "여성청소년과", new String[]{"여청수사팀", "학교폭력", "실종수사"});
                }
                cells(wb0, wb1, L, h);
                interrogation(eb0, eb1, L, h, 1);
            }
            case 2 -> {
                office(bi0 + 1, cx - 1, L, h, "수사과", new String[]{"경제1팀", "경제2팀", "지능팀"});
                office(cx + 1, bi1 - 1, L, h, "사이버수사팀", new String[]{"사이버1팀", "사이버2팀", "디지털포렌식"});
                interrogation(wb0, wb1, L, h, 4);
                meeting(eb0, eb1, L, h, "회의실");
            }
            case 3 -> {
                office(bi0 + 1, cx - 1, L, h, "경무과", new String[]{"경무계", "인사", "경리"});
                office(cx + 1, bi1 - 1, L, h, "생활안전과", new String[]{"생활안전계", "112상황", "정보과"});
                servers(wb0, wb1, L, h);
                archive(eb0, eb1, L, h);
            }
            case 4 -> {
                chief(L, h);
                office(bi0 + 1, cx - 10, L, h, "경비교통과", new String[]{"경비계", "교통계"});
                briefing(cx + 10, bi1 - 1, L, h);
                lockers(wb0, wb1, L, h, "남자 탈의실");
                lockers(eb0, eb1, L, h, "여자 탈의실");
            }
            default -> {
                auditorium(bi0 + 1, cx - 1, L, h);
                dojo(cx + 1, bi1 - 1, L, h);
                gym(wb0, wb1, L, h);
                canteen(eb0, eb1, L, h);
            }
        }
    }

    /** 방 이름표 (복도 쪽 벽 위, 문 옆) */
    private void label(int i, int y, int j, String facing, String text) {
        if (v.get(i, y, j) == null) {
            v.set(i, y, j, Blocks.wallSign("birch", facing, "black", false, "", text));
        }
    }

    private void door(int i, int L, int j, String wood) {
        Interior.door(Frame.of(v), i, L, j, wood, "south");
    }

    /** 뒤쪽 방: 복도 북쪽 벽에 문과 이름표 */
    private void backDoor(int i, int L, String text) {
        door(i, L, jCN, "spruce");
        label(i + 1, L + 1, jC0, "south", text);
    }

    /** 앞쪽 방: 복도 남쪽 벽에 문과 이름표 */
    private void frontDoor(int i, int L, String text) {
        door(i, L, jCS, "spruce");
        label(i + 1, L + 1, jC1, "north", text);
    }

    // ------------------------------------------------------------------ 1층

    private void ground() {
        int L = 0, h = height(0), top = L + h - 2;
        Frame f = Frame.of(v);
        // 로비 (가운데, 앞쪽과 복도를 틈)
        Interior.floor(f, cx - 9, jC0, cx + 9, jF1, L, POLISHED_ANDESITE);
        for (int i = cx - 9; i <= cx + 9; i += 3) {
            v.set(i, L - 1, (jC0 + jF1) / 2, SMOOTH_STONE);
        }
        v.fill(cx - 10, L, jF0, cx - 10, top, jF1, IN);
        v.fill(cx + 10, L, jF0, cx + 10, top, jF1, IN);
        // 안내 데스크 (정문을 봄)
        int dj = jF0 + 2;
        v.fill(cx - 3, L, dj, cx + 3, L, dj, Furniture.COUNTER);
        v.set(cx - 3, L, dj - 1, Furniture.COUNTER);
        v.set(cx + 3, L, dj - 1, Furniture.COUNTER);
        Furniture.chair(f, cx - 1, L, dj - 1, "north", "dark_oak");
        Furniture.chair(f, cx + 1, L, dj - 1, "north", "dark_oak");
        v.set(cx - 2, L + 1, dj, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        v.set(cx, L + 3, dj - 1, Blocks.hangingSign("dark_oak", 0, "white", true, "", "안내", "Information"));
        // 대기 소파와 화분
        Random rr = new Random(r.nextLong());
        for (int i : new int[]{cx - 8, cx + 6}) {
            Furniture.sofa(f, rr, i, L, jF1 - 2, 3, "north");
            Furniture.plant(f, rr, i == cx - 8 ? cx - 9 : cx + 9, L, jF1 - 1);
        }
        // 층별 안내 (엘리베이터 옆 벽)
        v.set(cx - 4, L + 1, jC0, Blocks.wallSign("dark_oak", "south", "white", true, name, "1층 민원실", "2층 형사과", "3층 수사과"));
        v.set(cx + 4, L + 1, jC0, Blocks.wallSign("dark_oak", "south", "white", true, "4층 경무과", "5층 서장실", "6층 강당", "B 없음"));
        Interior.lights(f, cx - 9, jC0, cx + 9, jF1, L, h, 4, Interior.LIGHT);

        // 민원실 (서쪽 앞) · 교통민원실 (동쪽 앞)
        counters(bi0 + 1, cx - 11, L, h, "민원실", new String[]{"종합민원", "고소·고발", "분실물", "범죄경력"});
        counters(cx + 11, bi1 - 1, L, h, "교통민원실", new String[]{"운전면허", "교통사고", "과태료", "주차단속"});
        // 로비와 민원실 사이 넓은 문
        v.fill(cx - 10, L, jF0 + 4, cx - 10, L + 2, jF0 + 6, AIR);
        v.fill(cx + 10, L, jF0 + 4, cx + 10, L + 2, jF0 + 6, AIR);
        label(cx - 9, L + 3, jF0 + 5, "east", "민원실");
        label(cx + 9, L + 3, jF0 + 5, "west", "교통민원실");

        // 뒤쪽 서: 후문 통로, 무기고, 당직실
        Interior.floor(f, wb0, jB0, wb0 + 2, jB1, L, CORRIDOR);
        v.fill(wb0, L, jCN, wb0 + 2, L + 2, jCN, AIR);
        v.fill(wb0 + 3, L, jB0, wb0 + 3, top, jB1, IN);
        v.set(wb0 + 1, top, (jB0 + jB1) / 2, Interior.LIGHT);
        armory(wb0 + 4, wb0 + 10, L, h);
        v.fill(wb0 + 11, L, jB0, wb0 + 11, top, jB1, IN);
        duty(wb0 + 12, wb1, L, h);
        // 뒤쪽 동: 상황실
        situation(eb0, eb1, L, h);
    }

    /** 민원 창구 방 (앞쪽, i0..i1): 창구 줄 뒤 직원 책상, 앞쪽 대기 의자와 기재대, 번호 안내 */
    private void counters(int i0, int i1, int L, int h, String title, String[] windows) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jF0, i1, jF1, L, Block.of("smooth_stone", 0x9E9E9E));
        int cj = jF0 + 2;
        // 직원 쪽 (뒤 2줄): 책상과 모니터
        for (int i = i0 + 1; i <= i1 - 1; i++) {
            v.set(i, L, cj, Furniture.COUNTER);
            if ((i - i0) % 3 == 0) {
                v.set(i, L + 1, cj, Block.of("glass_pane", 0xC8DCE4));
            }
        }
        for (int n = 0, i = i0 + 2; i + 1 <= i1 - 1 && n < 8; i += 3, n++) {
            Furniture.desk(f, i, L, cj, "north");
            // 창구 번호판 (천장에 매단 표지판)
            v.set(i, L + 3, cj, Blocks.hangingSign("dark_oak", 0, "white", true, (n + 1) + "번 창구", windows[n % windows.length]));
            // 민원인 의자 (창구 앞)
            Furniture.chair(f, i, L, cj + 1, "south", "birch");
        }
        // 대기 의자 줄 (창구를 봄)
        for (int j = cj + 3; j <= jF1 - 1; j += 2) {
            for (int i = i0 + 2; i <= i1 - 2; i++) {
                if ((i - i0) % 6 != 5) {
                    Furniture.chair(f, i, L, j, "south", "birch");
                }
            }
        }
        // 기재대 (서류 쓰는 탁자)와 번호표 발권기
        int ti = title.equals("민원실") ? i0 + 1 : i1 - 1;
        v.set(ti, L, jF1, Furniture.WHITE_TOP);
        v.set(ti, L, jF1 - 1, Furniture.WHITE_TOP);
        v.set(ti == i0 + 1 ? i1 - 1 : i0 + 1, L, jF1 - 1, Block.of("iron_block", 0xDCDCDC));
        v.set(ti == i0 + 1 ? i1 - 1 : i0 + 1, L + 1, jF1 - 1, Block.of("stone_button[face=wall,facing=south,powered=false]", 0x7E7E7E));
        // 직원 출입문 (복도)
        door(i0 + 1, L, jCS, "spruce");
        label(i0 + 2, L + 1, jC1, "north", title);
        Interior.lights(f, i0, jF0, i1, jF1, L, h, 4, Interior.LIGHT);
    }

    private void armory(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        int top = L + h - 2;
        Interior.floor(f, i0, jB0, i1, jB1, L, Block.of("gray_concrete", 0x36393D));
        v.set(i0 + 2, L, jCN, IRON_DOOR_LO);
        v.set(i0 + 2, L + 1, jCN, IRON_DOOR_HI);
        v.set(i0 + 1, L + 1, jC0, Block.of("stone_button[face=wall,facing=south,powered=false]", 0x7E7E7E));
        label(i0 + 3, L + 1, jC0, "south", "무기고");
        // 총기 보관대: 받침 위에 세운 총(피뢰침), 벽에 철제 보관함
        for (int j = jB0 + 1; j <= jB1 - 2; j += 3) {
            for (int i = i0 + 1; i <= i1 - 1; i++) {
                v.set(i, L, j, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
                v.set(i, L + 1, j, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0x5A5A5A));
            }
        }
        for (int i = i0; i <= i1; i++) {
            v.set(i, L, jB0, IRON_BLOCK);
            v.set(i, L + 1, jB0, LOCKER.with("facing=south,half=bottom,open=true,powered=false,waterlogged=false"));
        }
        v.set((i0 + i1) / 2, top, jB1 - 1, Interior.LIGHT);
    }

    private void duty(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jB0, i1, jB1, L, CARPET);
        backDoor(i0 + 1, L, "당직실");
        Furniture.desk(f, i0 + 2, L, jB0 + 1, "south");
        Furniture.desk(f, i0 + 3, L, jB0 + 1, "south");
        v.set(i1, L + 1, jB0 + 3, Furniture.TV);
        Furniture.bed(f, r, i1, L, jB1 - 2, "south");
        Furniture.sofa(f, r, i0 + 1, L, jB1 - 1, 3, "north");
        Interior.lights(f, i0, jB0, i1, jB1, L, h, 4, Interior.LIGHT);
    }

    /** 상황실: 뒷벽 큰 화면들을 보는 책상 줄 */
    private void situation(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jB0, i1, jB1, L, CARPET);
        backDoor(i0 + 2, L, "상황실");
        // 화면 벽
        for (int i = i0; i <= i1; i++) {
            for (int y = L + 1; y <= L + 3; y++) {
                boolean screen = i % 4 != 0 && y < L + 3;
                v.set(i, y, jB0, screen ? Block.of("black_stained_glass", 0x191919) : Block.of("black_concrete", 0x080A0F));
            }
        }
        v.fill(i0 + 2, L + 1, jB0, i1 - 2, L + 1, jB0, Block.of("cyan_stained_glass", 0x4C7F99));
        // 책상 줄 (화면을 봄)
        for (int j = jB0 + 3; j <= jB1 - 2; j += 3) {
            for (int i = i0 + 2; i <= i1 - 2; i++) {
                if ((i - i0) % 5 != 1) {
                    Furniture.desk(f, i, L, j, "south");
                }
            }
        }
        Interior.lights(f, i0, jB0, i1, jB1, L, h, 5, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 위층 방

    /** 앞쪽 사무실 (i0..i1, 창가): 팀마다 책상 묶음, 팀장 책상, 복도 쪽 문 두 개 */
    private void office(int i0, int i1, int L, int h, String title, String[] teams) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jF0, i1, jF1, L, CARPET);
        if (i0 > bi0 + 1) {
            v.fill(i0 - 1, L, jF0, i0 - 1, L + h - 2, jF1, IN);
        }
        if (i1 < bi1 - 1) {
            v.fill(i1 + 1, L, jF0, i1 + 1, L + h - 2, jF1, IN);
        }
        frontDoor(i0 + 2, L, title);
        door(i1 - 2, L, jCS, "spruce");
        // 팀 책상 묶음 (마주 보는 두 줄), 사이 통로
        int n = 0;
        for (int i = i0 + 2; i + 3 <= i1 - 2; i += 7) {
            for (int a = i; a <= i + 3; a++) {
                Furniture.desk(f, a, L, jF0 + 3, "north");
                Furniture.desk(f, a, L, jF0 + 4, "south");
            }
            // 팀장 책상 (창가, 묶음을 봄)
            Furniture.desk(f, i + 1, L, jF1 - 1, "north");
            v.set(i + 2, L, jF1 - 1, Furniture.DESK_TOP);
            if (n < teams.length) {
                v.set(i + 1, L + h - 2, jF0 + 3, Blocks.hangingSign("birch", 0, "black", false, "", teams[n]));
            }
            n++;
        }
        // 서류 캐비닛 (복도 쪽 벽)
        for (int i = i0 + 4; i <= i1 - 4; i += 7) {
            v.set(i, L, jF0, Furniture.BOOKSHELF);
            v.set(i + 1, L, jF0, Block.of("iron_block", 0xDCDCDC));
        }
        Interior.lights(f, i0, jF0, i1, jF1, L, h, 4, Interior.LIGHT);
    }

    /** 유치장: 뒷벽을 따라 쇠창살 방, 앞에 감시 통로와 감시대 */
    private void cells(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        int top = L + h - 2;
        int barsJ = jB0 + 4;
        Interior.floor(f, i0, jB0, i1, jB1, L, Block.of("light_gray_concrete", 0x7D7D73));
        door(i0 + 2, L, jCN, "iron");
        v.set(i0 + 1, L + 1, jC0, Block.of("stone_button[face=wall,facing=south,powered=false]", 0x7E7E7E));
        label(i0 + 3, L + 1, jC0, "south", "유치장");
        // 방: 안쪽 3칸 폭, 칸막이 벽
        int no = 1;
        for (int i = i0; i + 4 <= i1; i += 4) {
            v.fill(i, L, jB0, i, top, barsJ, Block.of("gray_concrete", 0x36393D));
            v.fill(i + 4, L, jB0, i + 4, top, barsJ, Block.of("gray_concrete", 0x36393D));
            v.fill(i + 1, L, barsJ, i + 3, top, barsJ, IRON_BARS);
            // 쇠창살 문 (밖에서 버튼)
            v.set(i + 2, L, barsJ, IRON_DOOR_LO);
            v.set(i + 2, L + 1, barsJ, IRON_DOOR_HI);
            v.set(i + 1, L + 1, barsJ + 1, Block.of("stone_button[face=floor,facing=south,powered=false]", 0x7E7E7E).with("face=wall,facing=south,powered=false"));
            // 마루 바닥, 변기 칸막이, 담요
            v.fill(i + 1, L - 1, jB0, i + 3, L - 1, barsJ - 1, Block.of("oak_planks", 0xA2834F));
            v.set(i + 3, L, jB0, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
            v.set(i + 3, L, jB0 + 1, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
            v.set(i + 1, L, jB0, Block.of("gray_carpet", 0x3E4447));
            v.set(i + 1, L + 2, jB0 + 1, Interior.LIGHT);
            // 방 번호
            v.set(i + 3, L + 2, barsJ + 1, Blocks.wallSign("birch", "south", "black", false, "", no++ + "호실"));
        }
        // 감시대 (창살을 봄): 책상과 화면
        int gi = (i0 + i1) / 2;
        v.fill(gi - 2, L, jB1, gi + 2, L, jB1, Furniture.COUNTER);
        v.set(gi - 1, L + 1, jB1, Furniture.TV);
        v.set(gi + 1, L + 1, jB1, Furniture.TV);
        Furniture.chair(f, gi, L, jB1 - 1, "south", "dark_oak");
        for (int i = i0 + 2; i <= i1 - 2; i += 4) {
            v.set(i, top, jB1 - 2, Interior.LIGHT);
        }
    }

    /** 조사실 여러 칸 (뒤쪽): 탁자 하나와 마주 보는 의자, 한쪽 벽 거울 유리, 천장 카메라 */
    private void interrogation(int i0, int i1, int L, int h, int firstNo) {
        Frame f = Frame.of(v);
        int top = L + h - 2;
        int no = firstNo;
        for (int i = i0; i + 5 <= i1 + 1; i += 6) {
            int a = i, b = Math.min(i1, i + 4);
            Interior.floor(f, a, jB0, b, jB1, L, Block.of("gray_concrete", 0x36393D));
            if (b < i1) {
                v.fill(b + 1, L, jB0, b + 1, top, jB1, IN);
            }
            int mid = (a + b) / 2;
            door(mid, L, jCN, "spruce");
            label(mid + 1, L + 1, jC0, "south", "조사실 " + no++);
            // 탁자와 의자
            v.fill(mid - 1, L, jB0 + 3, mid + 1, L, jB0 + 3, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
            Furniture.chair(f, mid, L, jB0 + 2, "north", "spruce");
            Furniture.chair(f, mid, L, jB0 + 4, "south", "spruce");
            // 진술 녹화 모니터, 거울 유리 (옆방 쪽)
            v.set(mid + 1, L + 1, jB0 + 3, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            if (b < i1) {
                v.fill(b + 1, L + 1, jB0 + 2, b + 1, L + 1, jB0 + 4, Block.of("black_stained_glass", 0x191919));
            }
            v.set(a, top, jB0, Block.of("tripwire_hook[attached=false,facing=south,powered=false]", 0x8F8F8F));
            v.set(mid, top, jB0 + 3, Interior.LIGHT);
        }
    }

    private void meeting(int i0, int i1, int L, int h, String title) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jB0, i1, jB1, L, CARPET);
        backDoor(i0 + 2, L, title);
        Furniture.table(f, i0 + 3, L, (jB0 + jB1) / 2, i1 - i0 - 5, 2, "dark_oak");
        v.set((i0 + i1) / 2, L + 1, jB0, Furniture.TV);
        v.set((i0 + i1) / 2 + 1, L + 1, jB0, Furniture.TV);
        Interior.lights(f, i0, jB0, i1, jB1, L, h, 4, Interior.LIGHT);
    }

    private void servers(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jB0, i1, jB1, L, Block.of("white_concrete", 0xCFD5D6));
        backDoor(i0 + 2, L, "전산실");
        for (int i = i0 + 1; i <= i1 - 1; i += 3) {
            for (int j = jB0 + 1; j <= jB1 - 3; j++) {
                v.set(i, L, j, Block.of("iron_block", 0xDCDCDC));
                v.set(i, L + 1, j, Block.of("black_concrete", 0x080A0F));
            }
        }
        Interior.lights(f, i0, jB0, i1, jB1, L, h, 4, Interior.LIGHT);
    }

    private void archive(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jB0, i1, jB1, L, OFFICE_FLOOR);
        backDoor(i0 + 2, L, "문서고");
        for (int i = i0 + 1; i <= i1 - 1; i += 3) {
            for (int j = jB0; j <= jB1 - 3; j++) {
                v.set(i, L, j, Furniture.BOOKSHELF);
                v.set(i, L + 1, j, Furniture.BOOKSHELF);
            }
        }
        Interior.lights(f, i0, jB0, i1, jB1, L, h, 4, Interior.LIGHT);
    }

    /** 서장실 (앞 가운데): 큰 책상, 뒤에 깃대 두 개, 손님 소파, 책장. 옆에 부속실 */
    private void chief(int L, int h) {
        Frame f = Frame.of(v);
        int i0 = cx - 9, i1 = cx + 9, top = L + h - 2;
        Interior.floor(f, i0, jF0, i1, jF1, L, Block.of("dark_oak_planks", 0x432B14));
        v.fill(i0 - 1, L, jF0, i0 - 1, top, jF1, IN);
        v.fill(i1 + 1, L, jF0, i1 + 1, top, jF1, IN);
        // 부속실 (비서) i0..i0+5
        v.fill(i0 + 6, L, jF0, i0 + 6, top, jF1, IN);
        frontDoor(i0 + 2, L, "서장실");
        Interior.door(f, i0 + 6, L, jF0 + 2, "dark_oak", "east");
        Furniture.desk(f, i0 + 2, L, jF0 + 4, "north");
        Furniture.sofa(f, r, i0 + 1, L, jF1 - 1, 3, "north");
        // 서장 책상 (창을 등지고 문을 봄)
        int di = (i0 + 7 + i1) / 2;
        v.fill(di - 1, L, jF1 - 2, di + 1, L, jF1 - 2, Furniture.DESK_TOP);
        Furniture.chair(f, di, L, jF1 - 1, "south", "dark_oak");
        v.set(di - 1, L + 1, jF1 - 2, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        // 깃대 (책상 양옆, 깃발 없이)
        for (int i : new int[]{di - 3, di + 3}) {
            v.set(i, L, jF1 - 1, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
            v.fill(i, L + 1, jF1 - 1, i, L + 2, jF1 - 1, Block.of("iron_bars", 0x888888));
        }
        // 손님 소파와 탁자
        Furniture.sofa(f.sub(di - 2, jF0 + 1, "south"), r, 0, L, 0, 5, "south");
        v.fill(di - 1, L, jF0 + 2, di + 1, L, jF0 + 2, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        for (int i = i0 + 8; i <= i1 - 1; i += 2) {
            v.set(i, L, jF0, Furniture.BOOKSHELF);
            v.set(i, L + 1, jF0, Furniture.BOOKSHELF);
        }
        Furniture.plant(f, r, i1, L, jF1);
        Interior.lights(f, i0, jF0, i1, jF1, L, h, 4, Interior.LIGHT);
    }

    /** 브리핑룸: 앞 단상과 배경판, 기자석 의자 줄 */
    private void briefing(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jF0, i1, jF1, L, CARPET);
        frontDoor(i0 + 2, L, "브리핑룸");
        // 단상 (동쪽 끝)
        v.fill(i1 - 2, L - 1, jF0, i1, L - 1, jF1, Block.of("dark_oak_planks", 0x432B14));
        v.fill(i1 - 2, L, jF0 + 1, i1 - 2, L, jF1 - 1, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        v.set(i1 - 1, L, (jF0 + jF1) / 2, Block.of("dark_oak_planks", 0x432B14));
        v.fill(i1, L + 1, jF0 + 1, i1, L + 2, jF1 - 1, Block.of("blue_concrete", 0x2C2E8F));
        v.set(i1 - 1, L + 1, (jF0 + jF1) / 2, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0x5A5A5A));
        // 의자 줄 (단상을 봄)
        for (int i = i0 + 2; i <= i1 - 5; i += 2) {
            for (int j = jF0 + 1; j <= jF1 - 1; j++) {
                if (j != (jF0 + jF1) / 2) {
                    Furniture.chair(f, i, L, j, "west", "birch");
                }
            }
        }
        Interior.lights(f, i0, jF0, i1, jF1, L, h, 4, Interior.LIGHT);
    }

    /** 탈의실: 벽을 따라 철제 사물함, 가운데 긴 의자, 안쪽 샤워 칸 */
    private void lockers(int i0, int i1, int L, int h, String title) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jB0, i1, jB1, L, Block.of("light_gray_terracotta", 0x876A61));
        backDoor(i0 + 2, L, title);
        for (int i = i0; i <= i1 - 4; i++) {
            v.set(i, L, jB0, Block.of("iron_block", 0xDCDCDC));
            v.set(i, L + 1, jB0, Block.of("iron_block", 0xDCDCDC));
            v.set(i, L, jB0 + 1, LOCKER.with("facing=south,half=bottom,open=false,powered=false,waterlogged=false"));
        }
        for (int i = i0 + 1; i <= i1 - 5; i++) {
            v.set(i, L, jB0 + 4, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
            v.set(i, L, jB1 - 2, Block.of("iron_block", 0xDCDCDC));
            v.set(i, L + 1, jB1 - 2, Block.of("iron_block", 0xDCDCDC));
        }
        // 샤워 칸 (동쪽 끝)
        v.fill(i1 - 3, L, jB0, i1 - 3, L + h - 2, jB1 - 3, Block.of("white_terracotta", 0xD1B2A1));
        Interior.floor(f, i1 - 2, jB0, i1, jB1 - 3, L, Interior.TILE);
        Interior.door(f, i1 - 3, L, jB1 - 4, "birch", "east");
        for (int j = jB0; j <= jB1 - 5; j += 2) {
            v.set(i1, L + 2, j, Block.of("tripwire_hook[attached=false,facing=west,powered=false]", 0x8F8F8F));
        }
        Interior.lights(f, i0, jB0, i1, jB1, L, h, 4, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 6층 (높은 방)

    private void auditorium(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jF0, i1, jF1, L, Block.of("dark_oak_planks", 0x432B14));
        v.fill(i1 + 1, L, jF0, i1 + 1, L + h - 2, jF1, IN);
        frontDoor(i1 - 3, L, "강당");
        door(i1 - 8, L, jCS, "spruce");
        // 무대 (서쪽 끝, 한 칸 높음) 와 계단
        v.fill(i0, L, jF0, i0 + 4, L, jF1, Block.of("spruce_planks", 0x725430));
        v.fill(i0 + 5, L, jF0 + 2, i0 + 5, L, jF1 - 2, Block.of("spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", 0x725430));
        v.fill(i0, L + 2, jF0 + 1, i0, L + 5, jF1 - 1, Block.of("white_concrete", 0xCFD5D6)); // 스크린
        v.set(i0 + 3, L + 1, (jF0 + jF1) / 2, Block.of("dark_oak_planks", 0x432B14)); // 연단
        v.set(i0 + 3, L + 2, (jF0 + jF1) / 2, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0x5A5A5A));
        // 의자 줄
        for (int i = i0 + 8; i <= i1 - 2; i += 2) {
            for (int j = jF0 + 1; j <= jF1 - 1; j++) {
                if (j != (jF0 + jF1) / 2) {
                    Furniture.chair(f, i, L, j, "east", "dark_oak");
                }
            }
        }
        Interior.lights(f, i0, jF0, i1, jF1, L, h, 4, Block.of("lantern[hanging=true,waterlogged=false]", 0x6A5B49));
        for (int i = i0 + 3; i <= i1 - 2; i += 6) {
            v.set(i, L + h - 2, (jF0 + jF1) / 2, Interior.LIGHT);
        }
    }

    /** 무도장: 유도 매트, 벽 거울 */
    private void dojo(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        frontDoor(i0 + 3, L, "무도장");
        door(i1 - 3, L, jCS, "spruce");
        for (int j = jF0; j <= jF1; j++) {
            for (int i = i0; i <= i1; i++) {
                boolean edge = i == i0 + 1 || i == i1 - 1 || j == jF0 + 1 || j == jF1 - 1;
                boolean in = i > i0 && i < i1 && j > jF0 && j < jF1;
                v.set(i, L - 1, j, Block.of("spruce_planks", 0x725430));
                if (in) {
                    v.set(i, L, j, Block.of(edge ? "yellow_carpet" : "green_carpet", edge ? 0xF8C527 : 0x546D1B));
                }
            }
        }
        v.fill(i1, L + 1, jF0 + 1, i1, L + 3, jF1 - 1, Block.of("light_blue_stained_glass", 0x6699D8));
        Interior.lights(f, i0, jF0, i1, jF1, L, h, 5, Interior.LIGHT);
    }

    /** 체력단련실: 운동 기구, 샌드백 */
    private void gym(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jB0, i1, jB1, L, Block.of("black_concrete", 0x080A0F));
        backDoor(i0 + 2, L, "체력단련실");
        for (int i = i0 + 1; i <= i1 - 2; i += 4) {
            // 러닝머신
            v.set(i, L, jB0, Block.of("gray_carpet", 0x3E4447));
            v.set(i, L, jB0 + 1, Block.of("gray_carpet", 0x3E4447));
            v.set(i, L + 1, jB0, Block.of("iron_bars", 0x888888));
            // 역기 받침
            v.set(i + 1, L, jB0 + 4, Block.of("anvil[facing=east]", 0x444444));
            v.set(i + 2, L, jB0 + 4, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
        }
        // 샌드백 (천장에 사슬)
        for (int i = i0 + 3; i <= i1 - 2; i += 5) {
            v.fill(i, L + 3, jB1 - 2, i, L + h - 2, jB1 - 2, CHAIN);
            v.fill(i, L + 1, jB1 - 2, i, L + 2, jB1 - 2, Block.of("black_wool", 0x141519));
        }
        v.fill(i1, L + 1, jB0, i1, L + 3, jB1 - 1, Block.of("light_blue_stained_glass", 0x6699D8));
        Interior.lights(f, i0, jB0, i1, jB1, L, h, 4, Interior.LIGHT);
    }

    /** 구내식당: 배식대와 긴 식탁 */
    private void canteen(int i0, int i1, int L, int h) {
        Frame f = Frame.of(v);
        Interior.floor(f, i0, jB0, i1, jB1, L, Block.of("white_concrete", 0xCFD5D6));
        backDoor(i0 + 2, L, "구내식당");
        // 배식대 (뒷벽 쪽)와 주방
        for (int i = i0; i <= i1; i++) {
            v.set(i, L, jB0, i % 3 == 0 ? CAULDRON : i % 3 == 1 ? Block.of("smoker[facing=south,lit=false]", 0x555451) : Furniture.COUNTER);
            v.set(i, L, jB0 + 2, Furniture.COUNTER);
        }
        v.set(i1, L, jB0 + 2, AIR);
        v.set(i1, L + 1, jB0 + 2, AIR);
        for (int i = i0 + 1; i <= i1 - 4; i += 4) {
            Furniture.table(f, i, L, jB0 + 6, 3, 1, "birch");
        }
        Interior.lights(f, i0, jB0, i1, jB1, L, h, 4, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 바깥

    private void facade() {
        for (int k = 0; k < FLOORS; k++) {
            int L = LEVELS[k], h = height(k);
            for (int y = L - 1; y <= L + h - 2; y++) {
                for (int i = bi0; i <= bi1; i++) {
                    v.set(i, y, bj0, skin(i - bi0, y, k, false, bi1 - bi0));
                    v.set(i, y, bj1, skin(i - bi0, y, k, true, bi1 - bi0));
                }
                for (int j = bj0 + 1; j < bj1; j++) {
                    v.set(bi0, y, j, skin(j - bj0, y, k, false, bj1 - bj0));
                    v.set(bi1, y, j, skin(j - bj0, y, k, false, bj1 - bj0));
                }
            }
        }
        // 계단실 바깥 벽: 층계참 높이의 작은 창
        for (int k = 0; k < FLOORS; k++) {
            for (int i : new int[]{bi0, bi1}) {
                for (int j = jB1 - 4; j <= jB1 - 2; j++) {
                    v.set(i, LEVELS[k] + 1, j, wallBlock());
                    v.set(i, LEVELS[k] + 2, j, wallBlock());
                }
            }
        }
        entrance();
        lettering();
    }

    private Block wallBlock() {
        return style == Style.CLASSIC ? wall : trim;
    }

    /**
     * 바깥 벽 한 칸. t = 모서리부터 거리, n = 벽 길이 - 1.
     * CLASSIC: 4칸 칸살(흰 기둥 + 3칸 창), 층마다 창턱과 바닥 띠, 1층은 회색 돌.
     * MODERN: 3칸마다 멀리언, 층 높이 통유리, 양 끝 6칸은 흰 돌 틀.
     */
    private Block skin(int t, int y, int k, boolean front, int n) {
        int L = LEVELS[k], h = height(k);
        boolean corner = t == 0 || t == n;
        if (style == Style.CLASSIC) {
            if (k == 0) {
                if (corner || t % 4 == 0 || y == L - 1 || y == L + h - 2) {
                    return y == L + h - 2 ? trim : base;
                }
                return y >= L + 1 ? glass : base;
            }
            if (k == FLOORS - 1) {
                if (front) {
                    return band;
                }
                return !corner && t % 4 == 2 && y >= L + 1 && y <= L + h - 3 ? glass : wall;
            }
            int c = t - (cx - bi0);
            if (front && Math.abs(c) <= 8) {
                // 정문 위 가운데: 회색 돌 틀 안의 세로 유리면
                if (Math.abs(c) >= 7) {
                    return base;
                }
                return y == L - 1 ? frame : c % 3 == 0 ? frame : glass;
            }
            if (corner || t % 4 == 0) {
                return trim;
            }
            if (y == L - 1) {
                return wall;
            }
            return y >= L + 1 ? glass : wall;
        }
        // MODERN
        boolean frameZone = t <= 5 || t >= n - 5;
        if (k == FLOORS - 1) {
            if (front) {
                return band;
            }
            return !frameZone && t % 3 != 0 && y >= L + 1 && y <= L + h - 3 ? glass : (frameZone ? wall : band);
        }
        if (front && k <= 1 && Math.abs(t - (cx - bi0)) <= 10) {
            // 아트리움 로비: 두 층 통유리, 굵은 돌 기둥
            int c = Math.abs(t - (cx - bi0));
            if (c == 10 || (c % 5 == 0 && y >= 0)) {
                return c == 10 ? wall : frame;
            }
            return k == 1 && y == L - 1 ? Block.of("glass", 0xC8DCE4) : Block.of("glass", 0xC8DCE4);
        }
        if (frameZone) {
            if (t == 3 || t == n - 3) {
                return y >= L && y <= L + h - 2 && k > 0 ? Block.of("gray_stained_glass", 0x4C4C4C) : wall;
            }
            return wall;
        }
        if (y == L - 1) {
            return Block.of("gray_concrete", 0x36393D);
        }
        return t % 3 == 0 ? frame : glass;
    }

    /** 정문: 가운데 유리문, 위 차양과 두 기둥, 민원실 문 (서쪽), 후문 */
    private void entrance() {
        int L = 0;
        // 정문 (5칸 열린 자동문 자리, 양옆 유리)
        v.fill(cx - 3, L, bj1, cx + 3, L + 3, bj1, style == Style.CLASSIC ? glass : Block.of("glass_pane", 0xC8DCE4));
        v.fill(cx - 2, L, bj1, cx + 2, L + 2, bj1, AIR);
        // 차양 (앞으로 6칸) 과 기둥
        int cj1 = bj1 + 6;
        Block slab = style == Style.CLASSIC ? SMOOTH_QUARTZ : Block.of("light_gray_concrete", 0x7D7D73);
        v.fill(cx - 7, 5, bj1 + 1, cx + 7, 5, cj1, slab);
        v.fill(cx - 7, 6, bj1 + 1, cx + 7, 6, bj1 + 1, slab);
        for (int i = cx - 5; i <= cx + 5; i += 5) {
            v.set(i, 4, bj1 + 3, Interior.LIGHT);
        }
        Block col = style == Style.CLASSIC ? Block.of("quartz_pillar[axis=y]", 0xEBE6E0) : Block.of("polished_deepslate", 0x484849);
        for (int i : new int[]{cx - 7, cx + 7}) {
            v.fill(i, 0, cj1, i, 4, cj1, col);
        }
        // 정문 위 이름 (표지판)
        v.set(cx, 6, bj1 + 2, AIR);
        v.set(cx, 5, cj1 + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", name));
        // 민원실 문 (서쪽 앞, 유리문)
        int mi = (bi0 + cx - 11) / 2;
        v.fill(mi, 0, bj1, mi + 1, 1, bj1, AIR);
        v.fill(mi - 1, 3, bj1 + 1, mi + 2, 3, bj1 + 2, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.set(mi, 2, bj1 + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "민원실", "Civil Affairs"));
        int ti = (cx + 11 + bi1) / 2;
        v.fill(ti - 1, 0, bj1, ti, 1, bj1, AIR);
        v.fill(ti - 2, 3, bj1 + 1, ti + 1, 3, bj1 + 2, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.set(ti, 2, bj1 + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "교통민원실"));
        // 후문 (뒤쪽, 통로 끝)
        v.fill(wb0, 0, bj0, wb0 + 2, 2, bj0, AIR);
        v.fill(wb0 - 1, 3, bj0 - 2, wb0 + 3, 3, bj0 - 1, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.set(wb0 + 3, 2, bj0 - 1, Blocks.wallSign("dark_oak", "north", "white", false, "", "후문", "관계자 외", "출입금지"));
    }

    /** 정면 꼭대기 돌 띠의 큰 글씨 (「○○경찰서」) */
    private void lettering() {
        int L = LEVELS[FLOORS - 1];
        int yTop = LEVELS[FLOORS] + 3;
        int width = HangulFont.width(12, name);
        int start = cx - width / 2;
        // 띠: 6층 정면 전체와 난간 (위 마감)
        v.fill(bi0, L - 1, bj1, bi1, yTop + 1, bj1, band);
        v.fill(bi0, yTop + 1, bj1, bi1, yTop + 1, bj1, trim);
        v.fill(bi0, L - 2, bj1, bi1, L - 2, bj1, trim);
        HangulFont.draw(v, 12, name, start, yTop, bj1 + 1, 1, 0, letter);
    }

    private void roof() {
        int y = LEVELS[FLOORS];
        // 난간 (정면은 글씨 띠가 대신함)
        for (int i = bi0; i <= bi1; i++) {
            v.fill(i, y, bj0, i, y + 1, bj0, wallBlock());
            v.set(i, y + 2, bj0, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        }
        for (int j = bj0; j <= bj1; j++) {
            for (int i : new int[]{bi0, bi1}) {
                if (v.get(i, y, j) == null) {
                    v.fill(i, y, j, i, y + 1, j, wallBlock());
                    v.set(i, y + 2, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
                }
            }
        }
        // 실외기 줄
        for (int i = bi0 + 10; i <= bi1 - 10; i += 3) {
            if (Math.abs(i - cx) > 5) {
                v.set(i, y, jF0 + 2, SMOOTH_STONE);
                v.set(i, y, jF0 + 3, SMOOTH_STONE);
            }
        }
        // 무전 안테나 (철탑)
        int ai = cx + 18, aj = jB0 + 4;
        v.fill(ai, y, aj, ai, y + 12, aj, IRON_BARS);
        v.set(ai, y + 13, aj, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
        for (int t = 3; t <= 11; t += 4) {
            v.set(ai - 1, y + t, aj, Block.of("end_rod[facing=west]", 0xE8E2D8));
            v.set(ai + 1, y + t, aj, Block.of("end_rod[facing=east]", 0xE8E2D8));
        }
        v.fill(ai - 1, y, aj - 1, ai + 1, y, aj + 1, SMOOTH_STONE);
        v.set(ai, y, aj, IRON_BARS);
    }

    // ------------------------------------------------------------------ 앞마당·뒷마당

    private boolean nearHub(int i, int j, int margin) {
        return Math.abs(i - hubI) <= 2 + margin && Math.abs(j - hubJ) <= 2 + margin;
    }

    private void forecourt() {
        int j0 = bj1 + 1, j1 = d - 1;
        // 포장: 회색 돌 마당, 정문 길은 밝은 돌
        for (int j = j0; j <= j1; j++) {
            for (int i = 0; i < w; i++) {
                boolean path = Math.abs(i - cx) <= 4;
                Block b = path ? (style == Style.CLASSIC ? SMOOTH_STONE : CALCITE)
                        : (i + j) % 7 == 0 ? POLISHED_ANDESITE : LIGHT_GRAY_CONCRETE;
                v.set(i, -1, j, b);
            }
        }
        // 주차장: 정문 길 양옆, 건물 앞에 한 줄 (차는 건물 쪽을 봄), 통로, 도로 쪽에 한 줄
        int rowA = j0 + 2, aisle0 = rowA + 5, rowB = aisle0 + 5;
        for (int side = -1; side <= 1; side += 2) {
            int i0 = side < 0 ? 1 : cx + 12, i1 = side < 0 ? cx - 12 : w - 2;
            for (int j = rowA; j <= rowB + 4; j++) {
                for (int i = i0; i <= i1; i++) {
                    if (!nearHub(i, j, 1)) {
                        v.set(i, -1, j, GRAY_CONCRETE);
                    }
                }
            }
            int n = 0;
            for (int i = i0 + 1; i + 2 <= i1; i += 3) {
                for (int row = 0; row < 2; row++) {
                    int jj = row == 0 ? rowA : rowB;
                    boolean ok = true;
                    for (int a = i; a <= i + 3 && ok; a++) {
                        ok = !nearHub(a, jj, 1) && !nearHub(a, jj + 4, 1);
                    }
                    if (!ok) {
                        continue;
                    }
                    v.fill(i, -1, jj, i, -1, jj + 4, WHITE_CONCRETE);
                    if (i + 3 <= i1) {
                        v.fill(i + 3, -1, jj, i + 3, -1, jj + 4, WHITE_CONCRETE);
                    }
                    // 건물 가까운 줄은 순찰차 칸: 바닥에 노란 글씨 대신 노란 칸 끝 표시
                    if (row == 0 && i + 2 <= i1 && n++ % 3 != 2) {
                        v.fill(i + 1, -1, jj, i + 2, -1, jj, YELLOW_CONCRETE);
                    }
                }
            }
            // 주차장 표지판
            int si = side < 0 ? i0 : i1;
            if (!nearHub(si, rowB + 5, 1)) {
                v.fill(si, 0, rowB + 5, si, 1, rowB + 5, StreetPlan.POST);
                v.set(si, 2, rowB + 5, Blocks.wallSign("dark_oak", "south", "white", false, "", side < 0 ? "순찰차 전용" : "민원인 주차장"));
            }
        }
        // 정문 길 양옆 화단 (나무) 과 국기 게양대
        for (int side = -1; side <= 1; side += 2) {
            int i0 = cx + side * 6, i1 = cx + side * 10;
            int a = Math.min(i0, i1), b = Math.max(i0, i1);
            for (int j = rowB; j <= j1 - 2; j++) {
                for (int i = a; i <= b; i++) {
                    if (nearHub(i, j, 1)) {
                        continue;
                    }
                    boolean edge = i == a || i == b || j == rowB || j == j1 - 2;
                    v.set(i, -1, j, edge ? SMOOTH_STONE : GRASS);
                    if (edge) {
                        v.set(i, 0, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
                    } else if ((i + j) % 3 == 0) {
                        v.set(i, 0, j, Block.of("azalea_leaves[persistent=true,distance=1,waterlogged=false]", 0x63753A));
                    }
                }
            }
            int ti = cx + side * 8, tj = rowB + 3;
            if (!nearHub(ti, tj, 3)) {
                MarketPlan.tree(v, ti, tj);
            }
        }
        // 국기 게양대 세 개 (깃대만): 정문 서쪽, 건물 앞 화단
        int pi0 = cx - 11, pj = j0 + 1;
        v.fill(pi0 - 1, -1, pj - 1, pi0 + 5, -1, pj + 1, POLISHED_ANDESITE);
        v.fill(pi0 - 1, 0, pj - 1, pi0 + 5, 0, pj + 1, Block.of("polished_andesite_slab[type=bottom,waterlogged=false]", 0x848685));
        for (int n = 0; n < 3; n++) {
            int pi = pi0 + n * 2;
            int top = n == 1 ? 13 : 11;
            v.fill(pi, 0, pj, pi, top, pj, Block.of("iron_bars", 0x888888));
            v.set(pi, top + 1, pj, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
        }
        // 표지석 (정문 길 동쪽, 도로 가까이): 돌덩이와 이름판
        int mi = cx + 6, mj = j1 - 2;
        if (!nearHub(mi, mj, 2) && !nearHub(mi + 3, mj, 2)) {
            v.fill(mi, 0, mj, mi + 3, 1, mj, style == Style.CLASSIC ? POLISHED_GRANITE : POLISHED_DEEPSLATE);
            v.fill(mi, -1, mj - 1, mi + 3, -1, mj + 1, POLISHED_ANDESITE);
            v.set(mi + 1, 1, mj + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", name));
            v.set(mi + 2, 1, mj + 1, Blocks.wallSign("dark_oak", "south", "white", false, "서울", "경찰청"));
        }
        // 도로 쪽 볼라드 (차 드나드는 곳은 비움)
        for (int i = 0; i < w; i += 2) {
            boolean drive = i <= 5 || i >= w - 6 || Math.abs(i - cx) <= 4;
            if (!drive && !nearHub(i, j1, 1) && v.get(i, 0, j1) == null) {
                v.set(i, 0, j1, Block.of("polished_andesite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x848685));
            }
        }
        // 보행등
        for (int i = 3; i < w - 3; i += 14) {
            if (!nearHub(i, j1 - 1, 2) && Math.abs(i - cx) > 4 && v.get(i, 0, j1 - 1) == null) {
                MarketPlan.lampPost(v, i, j1 - 1);
            }
        }
    }

    private void rearYard() {
        // 뒷마당·옆 차로: 아스팔트와 주차선 (순찰차·호송버스 칸)
        for (int j = 0; j <= bj1; j++) {
            for (int i = 0; i < w; i++) {
                if (j < bj0 || i < bi0 || i > bi1) {
                    v.set(i, -1, j, GRAY_CONCRETE);
                }
            }
        }
        // 둘레 담 (뒤·옆): 돌 받침 위 쇠창살
        for (int i = 0; i < w; i++) {
            fence(i, 0);
        }
        for (int j = 1; j <= bj1; j++) {
            fence(0, j);
            fence(w - 1, j);
        }
        // 주차 칸 (뒷벽을 따라)
        for (int i = bi0 + 14; i + 3 <= bi1 - 2; i += 3) {
            v.fill(i, -1, 1, i, -1, 5, WHITE_CONCRETE);
        }
        // 호송버스 칸 (서쪽, 큰 칸)
        v.walls(bi0 + 1, -1, 1, bi0 + 5, -1, 12, YELLOW_CONCRETE);
        // 뒷마당 등
        for (int i = bi0 + 10; i <= bi1 - 10; i += 16) {
            MarketPlan.lampPost(v, i, bj0 - 1);
        }
    }

    private void fence(int i, int j) {
        boolean post = (i + j) % 4 == 0;
        v.set(i, 0, j, POLISHED_ANDESITE);
        v.fill(i, 1, j, i, 2, j, post ? POLISHED_ANDESITE : IRON_BARS);
    }
}
