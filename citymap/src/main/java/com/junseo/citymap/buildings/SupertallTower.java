package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 준서월드타워: 롯데월드타워 같은 초고층 (실제 555m 를 꼭대기 y 약 307 로 줄임).
 * <ul>
 *   <li>몸통: 모서리가 둥근 네모 평면이 위로 갈수록 가늘어짐 (밑 43칸 → 맨 위층 31칸), 은백색 유리 커튼월(흰 층 띠, 회색 멀리언),
 *       네 면 가운데를 위아래로 가르는 홈(이음매). 69층, 층고 4 (1층 로비 5)</li>
 *   <li>층: 1층 로비, 2~4층 백화점(포디움과 이어짐), 5층 하늘정원 로비, 6~21층 오피스, 23~38층 레지던스, 40층 호텔 로비,
 *       41~56층 호텔 객실, 58~62층 프리미어 오피스, 64~69층 전망대, 13·22·39·57·63층 피난안전구역(기계실).
 *       가운데 코어: 꺾인 계단(옥상까지 걸어서), 엘리베이터 2대, 화장실</li>
 *   <li>꼭대기: 이음매를 따라 넷으로 갈라지며 좁아지는 격자 왕관(랜턴), 아래쪽은 유리, 안은 옥상 전망 테라스</li>
 *   <li>포디움: 타워 뒤와 양옆을 두르는 4층 쇼핑몰 「준서월드몰」(가게, 가운데 아트리움과 층 사이 계단, 4층 푸드코트,
 *       계단실·엘리베이터·화장실), 옥상 정원. 정면(거점 쪽)은 광장</li>
 * </ul>
 * 정면(남쪽, j 큰 쪽)이 광장과 거점 쪽입니다.
 */
final class SupertallTower {
    static final int FLOORS = 69;
    /** 포디움 층 (타워 0~3층과 같은 높이) */
    static final int[] PODIUM = Floors.levels(Floors.GROUND, Floors.OFFICE, 4);
    private static final int CROWN = 31;
    private static final Block GLASS_SKIN = Block.of("glass", 0xC8DCE4);
    private static final Block MULLION = Block.of("polished_diorite", 0xC0C0C1);
    private static final Block BAND = Block.of("white_stained_glass", 0xF0F0F0);
    private static final Block STONE = Block.of("smooth_sandstone", 0xDFD6AA);
    private static final Block FIN = Block.of("white_concrete", 0xCFD5D6);
    private static final Block LATTICE = Block.of("smooth_quartz", 0xECE6DF);
    private static final Block CROWN_GLASS = Block.of("white_stained_glass_pane", 0xF0F0F0);
    private static final String[][] SHOPS = {{"%s패션", "의류"}, {"%s슈즈", "신발"}, {"%s화장품", "화장품"}, {"%s안경", "안경"},
            {"%s서점", "서점"}, {"카페 %s", "카페"}, {"%s키즈", "아동"}, {"%s스포츠", "스포츠"}, {"%s리빙", "생활"},
            {"%s베이커리", "빵집"}, {"%s주얼리", "보석"}, {"%s전자", "전자"}, {"%s가방", "가방"}, {"%s시계", "시계"}};

    private final SiteLand land;
    private final Random r;
    private final int hubI, hubJ;
    private Voxels v;
    private Tower tower;
    /** 타워 가운데 칸, 밑 너비(홀수), 맨 위층 너비(홀수) */
    private int tc, tj, w0, w1;
    /** 포디움 바깥 상자 */
    private int pi0, pi1, pj0, pj1;

    private SupertallTower(SiteLand land, int hubI, int hubJ, Random r) {
        this.land = land;
        this.r = r;
        this.hubI = hubI;
        this.hubJ = hubJ;
    }

    static Voxels build(SiteLand land, int hubI, int hubJ, Random r) {
        SupertallTower s = new SupertallTower(land, hubI, hubJ, r);
        s.fit();
        s.tower();
        s.plaza();
        s.podium();
        s.links();
        s.crown();
        s.v.connect();
        land.clip(s.v);
        return s.v;
    }

    /** 땅 모양: 포디움 바깥 상자 (타워 포함) */
    static List<double[]> footprint(SiteLand land, int hubI, int hubJ) {
        SupertallTower s = new SupertallTower(land, hubI, hubJ, new Random(0));
        s.fit();
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{s.pi0, s.pj0});
        pts.add(new double[]{s.pi1 + 1, s.pj0});
        pts.add(new double[]{s.pi1 + 1, s.pj1 + 1});
        pts.add(new double[]{s.pi0, s.pj1 + 1});
        return pts;
    }

    // ------------------------------------------------------------------ 크기

    private void fit() {
        int midJ = land.d / 2, midI = land.w / 2;
        pi0 = 0;
        while (pi0 < land.w && !land.inner(pi0, midJ, 4)) {
            pi0++;
        }
        pi1 = land.w - 1;
        while (pi1 > 0 && !land.inner(pi1, midJ, 4)) {
            pi1--;
        }
        pj0 = 0;
        while (pj0 < land.d && !land.inner(midI, pj0, 4)) {
            pj0++;
        }
        // 정면 광장: 거점 앞으로 18칸은 비움
        pj1 = Math.min(hubJ - 18, land.d - 20);
        int width = pi1 - pi0 + 1, depth = pj1 - pj0 + 1;
        // 타워 밑 너비: 실제 비율(밑 약 69m)을 줄인 43칸, 땅이 좁으면 포디움 날개(양옆 14칸, 뒤 14칸)를 남기고 줄임
        w0 = Math.min(43, Math.min(width - 28, depth - 14));
        w0 -= 1 - Math.floorMod(w0, 2); // 홀수
        w1 = Math.max(27, (int) Math.round(w0 * 0.72));
        w1 -= 1 - Math.floorMod(w1, 2);
        tc = (pi0 + pi1) / 2;
        tj = pj1 - w0 / 2;
    }

    /** 층 k 의 바닥판 너비 (홀수) */
    private int width(int k) {
        double t = k / (double) (FLOORS - 1);
        int wk = (int) Math.round(w0 - (w0 - w1) * Math.pow(t, 1.8));
        return wk - (1 - Math.floorMod(wk, 2));
    }

    /** 칸 (i, j) 가 층 k 바닥판 안인지: 둥근 모서리 네모, 5층부터 네 면 가운데 이음매 홈 */
    private boolean inPlan(int i, int j, int k) {
        int half = width(k) / 2;
        int dx = i - tc, dz = j - tj;
        if (Math.abs(dx) > half || Math.abs(dz) > half) {
            return false;
        }
        double rc = 5.5 - 2.5 * k / (double) FLOORS;
        double qx = Math.abs(dx) + 0.5 - (half + 0.5 - rc), qz = Math.abs(dz) + 0.5 - (half + 0.5 - rc);
        if (qx > 0 && qz > 0 && qx * qx + qz * qz > rc * rc) {
            return false;
        }
        if (k >= PODIUM.length && ((dx == 0 && Math.abs(dz) == half) || (dz == 0 && Math.abs(dx) == half))) {
            return false; // 이음매 홈
        }
        return true;
    }

    // ------------------------------------------------------------------ 타워

    private void tower() {
        Tower.Spec s = new Tower.Spec(land.w, land.d, FLOORS);
        s.lobbyH = Floors.GROUND;
        s.typicalH = Floors.OFFICE;
        s.shape = this::inPlan;
        s.glass = GLASS_SKIN;
        s.mullion = MULLION;
        s.spandrel = BAND;
        s.podium = POLISHED_DIORITE;
        s.mullionEvery = 2;
        s.elevators = 2;
        s.restrooms = 1;
        s.extraTop = CROWN + 1;
        s.helipad = false;
        s.name = "준서월드타워";
        s.directory = new String[]{"2~4F 백화점", "6~21F 오피스", "23~38F 레지던스", "40~56F 준서호텔"};
        s.use = this::use;
        s.custom = this::customFloor;
        tower = Tower.build(s, new Random(r.nextLong()));
        v = tower.v;
        // 둥근 모서리·이음매 밖으로 삐져나간 방 벽과 가구를 걷어냄 (바닥판 밖은 비움)
        for (int k = 0; k < FLOORS; k++) {
            int y0 = tower.levels[k], y1 = tower.levels[k + 1] - 2;
            int half = width(0) / 2 + 2;
            for (int i = tc - half; i <= tc + half; i++) {
                for (int j = tj - half; j <= tj + half; j++) {
                    if (!inPlan(i, j, k)) {
                        for (int y = y0; y <= y1; y++) {
                            v.set(i, y, j, null);
                        }
                        // 바닥 마감(방 바닥)이 바닥판 밖으로 나간 것: 아래층 천장도 아니면 비움
                        if (k == 0 || !inPlan(i, j, k - 1)) {
                            v.set(i, y0 - 1, j, null);
                        } else if (!BAND.equals(v.get(i, y0 - 1, j))) {
                            v.set(i, y0 - 1, j, SMOOTH_STONE); // 아래층 지붕(물러난 테라스)은 바닥 마감 대신 돌판
                        }
                    }
                }
            }
        }
        for (int k = 0; k < FLOORS; k++) {
            Tower.Use u = use(k);
            if (u == Tower.Use.HOME || u == Tower.Use.HOTEL) {
                lounges(k);
            }
        }
        // 정문 위 이름 (차양 위)
        int jf = tj + width(0) / 2;
        v.set(tc, 3, jf + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "준서월드타워", "", ""));
        v.fill(tc - 4, 4, jf + 1, tc + 4, 4, jf + 3, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
    }

    private Tower.Use use(int k) {
        if (k == 0) {
            return Tower.Use.LOBBY;
        }
        if (k == 12 || k == 21 || k == 38 || k == 56 || k == 62) {
            return Tower.Use.MECH;
        }
        if (k >= 22 && k <= 37) {
            return Tower.Use.HOME;
        }
        if (k >= 40 && k <= 55) {
            return Tower.Use.HOTEL;
        }
        return Tower.Use.CUSTOM;
    }

    /** 집·호텔 층: 코어 양옆 복도 끝 넓은 곳에 소파·탁자·화분 (엘리베이터 홀 라운지) */
    private void lounges(int k) {
        Tower t = tower;
        int level = t.levels[k];
        int[] b = t.bounds(k);
        Frame f = Frame.of(v);
        int jm = (t.cj0 + t.corridorJ) / 2;
        for (int side = 0; side < 2; side++) {
            int i0 = side == 0 ? b[0] + 3 : t.ci1 + 4;
            int i1 = side == 0 ? t.ci0 - 4 : b[2] - 3;
            if (i1 - i0 < 4) {
                continue;
            }
            boolean clear = true;
            for (int i = i0; i <= i0 + 3 && clear; i++) {
                for (int j = jm - 2; j <= jm + 2 && clear; j++) {
                    clear = t.inside(i, j, k) && !t.edge(i, j, k) && v.get(i, level, j) == null;
                }
            }
            if (!clear) {
                continue;
            }
            Furniture.sofa(f, r, i0, level, jm - 2, 3, "south");
            Furniture.sofa(f, r, i0, level, jm + 2, 3, "north");
            v.fill(i0, level, jm, i0 + 2, level, jm, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
            Furniture.plant(f, r, i0 + 3, level, jm - 2);
            Furniture.plant(f, r, i0 + 3, level, jm + 2);
            v.set(i0 + 1, level + t.height(k) - 2, jm, Interior.LIGHT);
        }
    }

    /** 층 이름 (안내 표지판용, 1층부터) */
    private String floorName(int k) {
        if (k <= 3) {
            return "준서백화점 " + (k + 1) + "F";
        }
        if (k == 4) {
            return "하늘정원 로비";
        }
        if (k == 39) {
            return "준서호텔 로비";
        }
        if (k >= 57 && k <= 61) {
            return "프리미어 오피스";
        }
        if (k >= 63) {
            return "준서스카이 " + (k + 1) + "F";
        }
        return "오피스 " + (k + 1) + "F";
    }

    private void customFloor(Tower t, int k, int level, int h) {
        Frame f = Frame.of(t.v);
        int[] b = t.bounds(k);
        // 엘리베이터 앞 층 안내
        int si = t.ci0 - 1, sj = t.corridorJ;
        if (t.free(si, sj + 1, k) || t.v.get(si, level + 1, sj) == null) {
            t.v.set(si, level + 1, sj, Blocks.wallSign("dark_oak", "south", "white", true, "", floorName(k), "", ""));
        }
        womensRestroom(t, k, level, h);
        if (k <= 3) {
            departmentStore(t, k, level, h);
        } else if (k == 4) {
            skyLobby(t, k, level, h);
        } else if (k == 39) {
            hotelLobby(t, k, level, h);
        } else if (k >= 63) {
            observatory(t, k, level, h);
        } else {
            Rooms.office(f, r, b[0], b[1], b[2], b[3], level, h, (i, j) -> t.free(i, j, k) && !nearRestroom(t, i, j));
            int a0 = b[0] + 3, b0 = b[1] + 3;
            boolean ok = true;
            for (int j = b0; j <= b0 + 5 && ok; j++) {
                for (int i = a0; i <= a0 + 7 && ok; i++) {
                    ok = t.free(i, j, k) && t.v.get(i, level, j) == null && !nearRestroom(t, i, j);
                }
            }
            if (ok) {
                Rooms.meeting(f, a0, b0, a0 + 7, b0 + 5, level, h);
            }
        }
    }

    /** 코어 북쪽(뒤) 복도 건너 여자 화장실: 안쪽 7×5 */
    private void womensRestroom(Tower t, int k, int level, int h) {
        int a0 = t.ci0 + 1, a1 = a0 + 6, b1 = t.cj0 - 4, b0 = b1 - 4;
        Interior.restroom(Frame.of(t.v), a0, b0, a1, b1, level, h, "여자 화장실", a0 + 3);
    }

    private boolean nearRestroom(Tower t, int i, int j) {
        return i >= t.ci0 - 1 && i <= t.ci0 + 9 && j >= t.cj0 - 10 && j <= t.cj0;
    }

    /** 백화점 층 (포디움과 이어짐): 매장 진열대 섬과 옷걸이, 계산대 */
    private void departmentStore(Tower t, int k, int level, int h) {
        int[] b = t.bounds(k);
        Block table = Block.of("smooth_quartz_slab[type=top,waterlogged=false]", 0xECE6DF);
        Block rack = Block.of("iron_bars", 0x888888);
        String[] cloth = {"white", "light_gray", "gray", "black", "brown", "pink", "light_blue", "blue"};
        for (int j = b[1] + 3; j <= b[3] - 3; j += 5) {
            for (int i = b[0] + 3; i <= b[2] - 3; i += 6) {
                if (!t.free(i, j, k) || !t.free(i + 2, j + 1, k) || nearRestroom(t, i, j) || nearRestroom(t, i + 2, j + 1)) {
                    continue;
                }
                if ((i / 6 + j / 5 + k) % 2 == 0) {
                    t.v.fill(i, level, j, i + 2, level, j + 1, table);
                    t.v.set(i + 1, level + 1, j, Block.of(cloth[r.nextInt(cloth.length)] + "_carpet", 0xE9ECEC));
                } else {
                    t.v.fill(i, level, j, i + 2, level, j, rack);
                    t.v.set(i, level + 1, j, Furniture.wool(cloth[r.nextInt(cloth.length)]));
                    t.v.set(i + 2, level + 1, j, Furniture.wool(cloth[r.nextInt(cloth.length)]));
                }
            }
        }
        Interior.lights(Frame.of(t.v), b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, level, h, 5, Interior.LIGHT);
    }

    /** 하늘정원 로비: 포디움 옥상 정원과 이어지는 카페와 의자 */
    private void skyLobby(Tower t, int k, int level, int h) {
        int[] b = t.bounds(k);
        Frame f = Frame.of(t.v);
        int ci = (b[0] + b[2]) / 2, cj = b[3] - 5;
        for (int i = ci - 4; i <= ci + 4; i++) {
            if (t.free(i, cj, k)) {
                t.v.set(i, level, cj, Furniture.COUNTER);
            }
        }
        for (int j = b[1] + 3; j <= b[3] - 3; j += 4) {
            for (int i = b[0] + 3; i <= b[2] - 3; i += 8) {
                if (t.free(i, j, k) && t.free(i + 2, j, k) && !nearRestroom(t, i, j) && Math.abs(j - cj) > 1) {
                    Furniture.table(f, i, level, j, 2, 1, "birch");
                    Furniture.plant(f, r, i + 3, level, j);
                }
            }
        }
        Interior.lights(f, b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, level, h, 5, Interior.LIGHT);
    }

    /** 호텔 로비: 프런트 데스크, 라운지 소파, 바 */
    private void hotelLobby(Tower t, int k, int level, int h) {
        int[] b = t.bounds(k);
        Frame f = Frame.of(t.v);
        int ci = (b[0] + b[2]) / 2;
        int fj = t.corridorJ + 4;
        for (int i = ci - 5; i <= ci + 5; i++) {
            if (t.free(i, fj, k)) {
                t.v.set(i, level, fj, Block.of("polished_blackstone", 0x353038));
            }
        }
        t.v.set(ci, level + 1, fj + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "준서호텔", "프런트", ""));
        for (int j = fj + 3; j <= b[3] - 3; j += 4) {
            for (int i = b[0] + 3; i <= b[2] - 5; i += 7) {
                if (t.free(i, j, k) && t.free(i + 3, j, k)) {
                    Furniture.sofa(f, r, i, level, j, 3, "south");
                    t.v.set(i + 1, level, j + 1, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
                }
            }
        }
        for (int i = b[0] + 3; i <= b[0] + 9; i++) {
            if (t.free(i, b[1] + 3, k)) {
                t.v.set(i, level, b[1] + 3, Block.of("dark_oak_planks", 0x432B14));
                if (t.free(i, b[1] + 4, k)) {
                    Furniture.chair(f, i, level, b[1] + 4, "south", "dark_oak");
                }
            }
        }
        Interior.lights(f, b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, level, h, 5, Block.of("ochre_froglight[axis=y]", 0xF5E9B6));
    }

    /** 전망대 층: 창가 의자, 카페, 안내판 */
    private void observatory(Tower t, int k, int level, int h) {
        int[] b = t.bounds(k);
        Frame f = Frame.of(t.v);
        for (int j = b[1] + 2; j <= b[3] - 2; j++) {
            for (int i = b[0] + 2; i <= b[2] - 2; i++) {
                boolean window = !t.inside(i - 2, j, k) || !t.inside(i + 2, j, k) || !t.inside(i, j - 2, k) || !t.inside(i, j + 2, k);
                if (window && t.free(i, j, k) && (i + j) % 4 == 0 && !nearRestroom(t, i, j)) {
                    String back = !t.inside(i - 2, j, k) ? "east" : !t.inside(i + 2, j, k) ? "west" : !t.inside(i, j - 2, k) ? "south" : "north";
                    Furniture.chair(f, i, level, j, back, "birch");
                }
            }
        }
        int ci = (b[0] + b[2]) / 2;
        if (k == FLOORS - 1) {
            for (int i = ci - 3; i <= ci + 3; i++) {
                if (t.free(i, t.corridorJ + 4, k)) {
                    t.v.set(i, level, t.corridorJ + 4, Furniture.COUNTER);
                }
            }
        }
        Interior.lights(f, b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, level, h, 6, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 광장

    private void plaza() {
        for (int i = 0; i < land.w; i++) {
            for (int j = 0; j < land.d; j++) {
                if (!land.land(i, j) || inPlan(i, j, 0)) {
                    continue;
                }
                boolean path = Math.abs(i - tc) <= 4 && j > pj1;
                long h = (Math.floorDiv(i, 4) * 73856093L) ^ (Math.floorDiv(j, 4) * 19349663L);
                v.set(i, -1, j, path ? POLISHED_DIORITE : Math.floorMod(h, 7) == 0 ? SMOOTH_STONE : POLISHED_ANDESITE);
            }
        }
        // 광장 가장자리(길 쪽)를 따라 가로수와 가로등, 가운데는 트인 광장 (거점 둘레는 비움)
        for (int i = pi0 + 2; i <= pi1 - 2; i += 7) {
            for (int j : new int[]{land.d - 6}) {
                if ((Math.abs(i - hubI) < 6 && Math.abs(j - hubJ) < 6) || !land.inner(i, j, 3) || Math.abs(i - tc) < 6) {
                    continue;
                }
                MarketPlan.tree(v, i, j);
            }
        }
        for (int j = pj1 + 8; j < land.d - 8; j += 7) {
            for (int i : new int[]{pi0 + 3, pi1 - 3}) {
                if (land.inner(i, j, 3) && !(Math.abs(i - hubI) < 6 && Math.abs(j - hubJ) < 6)) {
                    MarketPlan.tree(v, i, j);
                }
            }
        }
        for (int i = pi0 + 8; i <= pi1 - 8; i += 12) {
            int j = pj1 + 6;
            if (land.inner(i, j, 2) && Math.abs(i - tc) > 5 && !(Math.abs(i - hubI) < 4 && Math.abs(j - hubJ) < 4)) {
                MarketPlan.lampPost(v, i, j);
                v.set(i + 2, 0, j, SPRUCE_SLAB);
                v.set(i - 2, 0, j, SPRUCE_SLAB);
            }
        }
        // 이름 돌판 (낮은 돌벽에 표지판)
        int mj = pj1 + 4, mi = tc + 9;
        if (land.inner(mi + 4, mj, 3)) {
            v.fill(mi, 0, mj, mi + 4, 1, mj, POLISHED_GRANITE);
            v.set(mi + 2, 1, mj + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "준서월드타워", "준서월드몰", ""));
        }
    }

    // ------------------------------------------------------------------ 포디움 (쇼핑몰)

    /** 포디움 칸인지 (타워 밑 바닥판 바깥, 포디움 상자 안) */
    private boolean pod(int i, int j) {
        return i >= pi0 && i <= pi1 && j >= pj0 && j <= pj1 && !inPlan(i, j, 0);
    }

    private boolean podEdge(int i, int j) {
        return pod(i, j) && (!pod(i + 1, j) && !inPlan(i + 1, j, 0) || !pod(i - 1, j) && !inPlan(i - 1, j, 0)
                || !pod(i, j + 1) && !inPlan(i, j + 1, 0) || !pod(i, j - 1) && !inPlan(i, j - 1, 0));
    }

    /** 아트리움 (뒤 날개 가운데, 2층부터 뚫림) */
    private boolean atrium(int i, int j) {
        int back = tj - width(0) / 2 - 1; // 타워 뒷면 바로 뒤
        return Math.abs(i - tc) <= 9 && j >= pj0 + 8 && j <= back - 3;
    }

    private void podium() {
        int top = PODIUM[PODIUM.length - 1];
        // 바닥판과 지붕
        for (int i = pi0; i <= pi1; i++) {
            for (int j = pj0; j <= pj1; j++) {
                if (!pod(i, j)) {
                    continue;
                }
                for (int k = 0; k < PODIUM.length; k++) {
                    int y = PODIUM[k] - 1;
                    boolean hole = k > 0 && k < PODIUM.length - 1 && atrium(i, j);
                    v.set(i, y, j, hole ? null : k == 0 ? POLISHED_DIORITE : k == PODIUM.length - 1 ? SMOOTH_STONE : SMOOTH_STONE);
                }
                if (atrium(i, j)) {
                    v.set(i, top - 1, j, (i + j) % 3 == 0 ? FIN : GLASS); // 천창
                }
            }
        }
        // 바깥벽: 돌 띠와 유리, 세로 핀
        for (int i = pi0; i <= pi1; i++) {
            for (int j = pj0; j <= pj1; j++) {
                if (!podEdge(i, j)) {
                    continue;
                }
                boolean fin = (i + j) % 4 == 0;
                for (int k = 0; k + 1 < PODIUM.length; k++) {
                    int y0 = PODIUM[k], y1 = PODIUM[k + 1] - 1;
                    for (int y = y0; y < y1; y++) {
                        v.set(i, y, j, fin ? FIN : k == 0 ? GLASS : y == y0 ? STONE : GLASS);
                    }
                    v.set(i, y1, j, STONE);
                }
                // 옥상 난간
                v.set(i, top, j, STONE);
                v.set(i, top + 1, j, GLASS_PANE);
            }
        }
        cores();
        shops();
        atriumStairs();
        foodCourt();
        benches();
        entrances();
        roofGarden();
    }

    /** 뒤 두 귀퉁이 계단실(옥상까지)과 엘리베이터, 층마다 남녀 화장실 */
    private void cores() {
        int[] lv = PODIUM;
        for (int side = 0; side < 2; side++) {
            // 계단실: 바깥 a -1..5, b -1..깊이. 출입구는 b = -1 쪽(남쪽)
            int depth = Interior.stairDepth(lv);
            int ci = side == 0 ? pi0 + 2 : pi1 - 6;
            int cj = pj0 + 2;
            Frame cf = new Frame(v, ci + 4, cj + depth - 1, 2); // +b 가 북쪽: 출입구가 남쪽(콘코스)을 봄
            Interior.stairCore(cf, lv, Interior.CORE_WALL, "polished_andesite", 0x848685);
            // 엘리베이터: 계단실 옆
            int ei = side == 0 ? ci + 7 : ci - 4;
            Interior.elevator(new Frame(v, ei, cj, 0), lv, Interior.CORE_WALL);
            // 화장실: 엘리베이터 옆 (문은 남쪽)
            int ri = side == 0 ? ei + 4 : ei - 15;
            for (int k = 0; k + 1 < lv.length; k++) {
                int h = lv[k + 1] - lv[k];
                Interior.restroom(Frame.of(v), ri, cj, ri + 4, cj + 4, lv[k], h, "남자 화장실", ri + 2);
                Interior.restroom(Frame.of(v), ri + 7, cj, ri + 11, cj + 4, lv[k], h, "여자 화장실", ri + 9);
            }
        }
    }

    /** 바깥벽을 따라 가게 (깊이 7): 유리 가게 앞, 매단 간판, 계산대와 진열대 */
    private void shops() {
        int depth = 7;
        int[] lv = PODIUM;
        for (int k = 0; k + 1 < lv.length; k++) {
            if (k == lv.length - 2) {
                continue; // 4층은 푸드코트
            }
            int level = lv[k], h = lv[k + 1] - lv[k];
            // 양옆 날개: 바깥벽(i 끝)을 따라 j 방향으로 가게 줄
            for (int side = 0; side < 2; side++) {
                int wallI = side == 0 ? pi0 : pi1;
                int dir = side == 0 ? 1 : -1;
                for (int j0 = pj0 + 16; j0 + 8 <= pj1 - 1; j0 += 9) {
                    shop(wallI, j0, dir, 0, depth, level, h, side == 0 ? "east" : "west");
                }
            }
            // 뒤 날개: 뒷벽(j 끝)을 따라 i 방향
            for (int i0 = pi0 + 28; i0 + 8 <= pi1 - 28; i0 += 9) {
                if (Math.abs(i0 + 4 - tc) < 6) {
                    continue; // 뒤 출입구
                }
                shop(i0, pj0, 0, 1, depth, level, h, "south");
            }
        }
    }

    /**
     * 가게 하나: 바깥벽 칸 (wi, wj) 에서 (di, dj) 쪽으로 깊이 depth, 너비 8 (벽 따라 +j 또는 +i).
     * 앞(콘코스 쪽) 벽은 유리, 가운데 문 2칸, 문 위 매단 간판.
     */
    private void shop(int wi, int wj, int di, int dj, int depth, int level, int h, String front) {
        boolean alongJ = di != 0;
        Block glass = Block.of("glass_pane", 0xC8DCE4);
        String[] s = SHOPS[r.nextInt(SHOPS.length)];
        String name = s[0].formatted(KoreanNames.prefix(r));
        int top = level + h - 2;
        for (int along = 0; along <= 8; along++) {
            for (int dd = 1; dd <= depth; dd++) {
                int i = alongJ ? wi + di * dd : wi + along;
                int j = alongJ ? wj + along : wj + dj * dd;
                if (!pod(i, j)) {
                    return;
                }
            }
        }
        for (int along = 0; along <= 8; along++) {
            for (int dd = 1; dd <= depth; dd++) {
                int i = alongJ ? wi + di * dd : wi + along;
                int j = alongJ ? wj + along : wj + dj * dd;
                boolean side = along == 0 || along == 8;
                boolean frontRow = dd == depth;
                if (side) {
                    v.fill(i, level, j, i, top, j, Interior.INNER_WALL);
                } else if (frontRow) {
                    boolean door = along == 4 || along == 5;
                    for (int y = level; y <= top; y++) {
                        v.set(i, y, j, door && y < level + 2 ? AIR : y == top ? Interior.INNER_WALL : glass);
                    }
                }
                if (!side && !frontRow) {
                    v.set(i, level - 1, j, Block.of("birch_planks", 0xC0AF79));
                }
            }
        }
        // 안: 뒤 벽 진열장, 가운데 계산대, 등
        for (int along = 1; along <= 7; along++) {
            int i = alongJ ? wi + di : wi + along, j = alongJ ? wj + along : wj + dj;
            v.set(i, level, j, along % 2 == 0 ? Furniture.BOOKSHELF : Furniture.COUNTER);
        }
        int ci = alongJ ? wi + di * 3 : wi + 2, cj = alongJ ? wj + 2 : wj + dj * 3;
        v.set(ci, level, cj, Furniture.COUNTER);
        int li = alongJ ? wi + di * 4 : wi + 4, lj = alongJ ? wj + 4 : wj + dj * 4;
        v.set(li, top, lj, Interior.LIGHT);
        // 매단 간판 (문 앞 콘코스 쪽, 천장 아래)
        int si = alongJ ? wi + di * (depth + 1) : wi + 4, sj = alongJ ? wj + 4 : wj + dj * (depth + 1);
        int rot = switch (front) {
            case "east" -> 12;
            case "west" -> 4;
            case "north" -> 8;
            default -> 0;
        };
        if (pod(si, sj) && v.get(si, top, sj) == null) {
            v.set(si, top, sj, Blocks.hangingSign("spruce", rot, "white", true, name, s[1]));
        }
    }

    /**
     * 층 사이 계단 (에스컬레이터 자리): 양옆 날개 복도에 층마다 번갈아 2칸 폭 계단, 위층 바닥에 계단 구멍과 유리 난간.
     * 뒤 날개 가운데 아트리움은 2층부터 지붕 천창까지 뚫려 있고 둘레에 유리 난간.
     */
    private void atriumStairs() {
        int[] lv = PODIUM;
        for (int k = 0; k + 2 < lv.length; k++) {
            int rise = lv[k + 1] - lv[k];
            boolean west = k % 2 == 0;
            int[] lanes = west ? new int[]{pi0 + 12, pi0 + 13} : new int[]{pi1 - 13, pi1 - 12};
            int jb = pj1 - 10;
            for (int lane : lanes) {
                for (int s = 0; s < rise; s++) {
                    int j = jb - s;
                    v.set(lane, lv[k] + s, j, Blocks.stairs("polished_andesite", "north", 0x848685));
                    if (s > 0) {
                        v.fill(lane, lv[k], j, lane, lv[k] + s - 1, j, Block.of("light_gray_concrete", 0x7D7D73));
                    }
                    v.fill(lane, lv[k] + s + 1, j, lane, lv[k + 1] - 1, j, AIR); // 위층 바닥 구멍까지
                }
            }
            // 위층 계단 구멍 둘레 유리 난간 (도착 쪽은 열림)
            int y = lv[k + 1];
            for (int j = jb - rise + 1; j <= jb + 1; j++) {
                v.set(lanes[0] - 1, y, j, GLASS_PANE);
                v.set(lanes[1] + 1, y, j, GLASS_PANE);
            }
            v.set(lanes[0], y, jb + 1, GLASS_PANE);
            v.set(lanes[1], y, jb + 1, GLASS_PANE);
        }
        // 아트리움 둘레 난간 (2층부터)
        for (int k = 1; k + 1 < lv.length; k++) {
            for (int i = pi0; i <= pi1; i++) {
                for (int j = pj0; j <= pj1; j++) {
                    if (pod(i, j) && !atrium(i, j) && (atrium(i + 1, j) || atrium(i - 1, j) || atrium(i, j + 1) || atrium(i, j - 1))
                            && v.get(i, lv[k], j) == null) {
                        v.set(i, lv[k], j, GLASS_PANE);
                    }
                }
            }
        }
        // 1층 아트리움 가운데 화분과 긴 의자
        Frame f = Frame.of(v);
        for (int i = tc - 6; i <= tc + 6; i += 4) {
            Furniture.plant(f, r, i, 0, pj0 + 16);
            Furniture.sofa(f, r, i - 1, 0, pj0 + 14, 2, "south");
        }
    }

    /** 4층 푸드코트: 양옆 날개 바깥벽 따라 음식 가게(계산대와 매단 간판), 가운데 식탁 */
    private void foodCourt() {
        int k = PODIUM.length - 2, level = PODIUM[k];
        String[][] food = {{"한식", "비빔밥"}, {"분식", "떡볶이"}, {"중식", "짜장면"}, {"일식", "돈가스"}, {"국수", "칼국수"},
                {"카페", "커피"}, {"치킨", "닭강정"}, {"양식", "파스타"}};
        int n = 0;
        Frame f = Frame.of(v);
        for (int side = 0; side < 2; side++) {
            int wallI = side == 0 ? pi0 : pi1, dir = side == 0 ? 1 : -1;
            for (int j0 = pj0 + 16; j0 + 7 <= pj1 - 1; j0 += 8) {
                String[] name = food[n++ % food.length];
                for (int dj = 0; dj <= 6; dj++) {
                    int i = wallI + dir * 4, j = j0 + dj;
                    if (!pod(i, j)) {
                        continue;
                    }
                    v.set(i, level, j, Furniture.COUNTER);
                    v.set(wallI + dir, level, j, dj % 3 == 0 ? SMOKER : Furniture.COUNTER);
                }
                v.fill(wallI + dir, level, j0, wallI + dir * 4, level + 2, j0, Interior.INNER_WALL);
                int si = wallI + dir * 5, sj = j0 + 3;
                if (pod(si, sj)) {
                    v.set(si, level + 2, sj, Blocks.hangingSign("spruce", side == 0 ? 12 : 4, "white", true, name[0], name[1]));
                }
            }
        }
        // 식탁: 뒤 날개 (아트리움 둘레 빼고)
        for (int i = pi0 + 20; i <= pi1 - 22; i += 5) {
            for (int j = pj0 + 9; j <= tj - width(0) / 2 - 4; j += 4) {
                if (pod(i, j) && pod(i + 2, j) && !atrium(i, j) && !atrium(i + 1, j) && !atrium(i - 1, j) && !atrium(i + 2, j) && !atrium(i, j + 1)
                        && !atrium(i, j - 1) && v.get(i, level, j) == null && v.get(i + 1, level, j) == null) {
                    Furniture.table(f, i, level, j, 2, 1, "birch");
                }
            }
        }
        // 천장 등 (포디움 전체, 층마다)
        for (int kk = 0; kk + 1 < PODIUM.length; kk++) {
            int y = PODIUM[kk + 1] - 2;
            for (int i = pi0 + 2; i <= pi1 - 2; i += 5) {
                for (int j = pj0 + 2; j <= pj1 - 2; j += 5) {
                    if (pod(i, j) && !atrium(i, j) && v.get(i, y, j) == null) {
                        v.set(i, y, j, Interior.LIGHT);
                    }
                }
            }
        }
    }

    /** 1~3층 양옆 날개 복도 가운데: 긴 의자와 화분 (10칸마다) */
    private void benches() {
        Frame f = Frame.of(v);
        int half = width(0) / 2;
        for (int k = 0; k + 2 < PODIUM.length; k++) {
            int level = PODIUM[k];
            for (int side = 0; side < 2; side++) {
                int i = side == 0 ? (pi0 + 8 + tc - half) / 2 + 1 : (pi1 - 8 + tc + half) / 2;
                for (int j = pj0 + 18; j + 3 <= pj1 - 4; j += 10) {
                    boolean free = true;
                    for (int dj = -1; dj <= 3 && free; dj++) {
                        free = pod(i, j + dj) && v.get(i, level, j + dj) == null && v.get(i, level - 1, j + dj) != null;
                    }
                    if (!free) {
                        continue;
                    }
                    for (int dj = 0; dj < 2; dj++) {
                        v.set(i, level, j + dj, Block.of("spruce_stairs", 0x725430).with("facing=" + (side == 0 ? "west" : "east")
                                + ",half=bottom,shape=straight,waterlogged=false"));
                    }
                    Furniture.plant(f, r, i, level, j + 2);
                }
            }
        }
    }

    /** 1층 출입구: 정면(광장 쪽) 양 날개 가운데, 양옆 길 쪽, 뒤 */
    private void entrances() {
        int y1 = 3;
        int[][] doors = {
                {(pi0 + tc - width(0) / 2) / 2, pj1, 0, 1, 0},
                {(pi1 + tc + width(0) / 2) / 2, pj1, 0, 1, 0},
                {pi0, (pj0 + pj1) / 2, 1, 0, 1},
                {pi1, (pj0 + pj1) / 2, 1, 0, 1},
                {tc, pj0, 0, 1, 0}};
        for (int[] d : doors) {
            for (int s = -2; s <= 2; s++) {
                int i = d[0] + (d[4] == 1 ? 0 : s), j = d[1] + (d[4] == 1 ? s : 0);
                v.fill(i, 0, j, i, y1 - 1, j, AIR);
            }
            // 문 위 이름
            String facing = d[4] == 1 ? (d[0] == pi0 ? "west" : "east") : (d[1] == pj1 ? "south" : "north");
            int oi = facing.equals("west") ? -1 : facing.equals("east") ? 1 : 0, oj = facing.equals("north") ? -1 : facing.equals("south") ? 1 : 0;
            v.set(d[0] + oi, y1, d[1] + oj, Blocks.wallSign("dark_oak", facing, "white", true, "", "준서월드몰", "", ""));
            for (int s = -3; s <= 3; s++) {
                int i = d[0] + (d[4] == 1 ? 0 : s) + oi, j = d[1] + (d[4] == 1 ? s : 0) + oj;
                if (land.inner(i, j, 2)) {
                    v.set(i, 4, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
                }
            }
        }
    }

    /** 포디움 옥상 정원: 잔디, 나무 화단, 의자, 산책길 */
    private void roofGarden() {
        int y = PODIUM[PODIUM.length - 1];
        for (int i = pi0 + 1; i <= pi1 - 1; i++) {
            for (int j = pj0 + 1; j <= pj1 - 1; j++) {
                if (!pod(i, j) || podEdge(i, j) || atrium(i, j)) {
                    continue;
                }
                boolean lawn = Math.floorMod(i - pi0, 12) >= 3 && Math.floorMod(j - pj0, 12) >= 3 && v.get(i, y, j) == null;
                if (lawn) {
                    v.set(i, y - 1, j, GRASS);
                }
            }
        }
        for (int i = pi0 + 8; i <= pi1 - 8; i += 12) {
            for (int j = pj0 + 8; j <= pj1 - 6; j += 12) {
                if (pod(i, j) && !atrium(i, j) && pod(i + 2, j + 2) && pod(i - 2, j - 2) && v.get(i, y, j) == null) {
                    v.set(i, y - 1, j, COARSE_DIRT);
                    v.fill(i, y, j, i, y + 2, j, OAK_LOG);
                    v.ellipsoid(i + 0.5, y + 4.2, j + 0.5, 2.3, 1.8, 2.3, AZALEA_LEAVES);
                    v.set(i, y + 3, j, OAK_LOG);
                    v.set(i + 2, y, j, SPRUCE_SLAB);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 타워 ↔ 포디움

    /** 타워 0~4층 옆·뒷면에 포디움으로 가는 출입구 (폭 3) */
    private void links() {
        for (int k = 0; k < PODIUM.length; k++) {
            int level = tower.levels[k];
            int half = width(k) / 2;
            int[][] spots = {{tc - half, tj - 6}, {tc - half, tj + 6}, {tc + half, tj - 6}, {tc + half, tj + 6}, {tc - 6, tj - half}, {tc + 6, tj - half}};
            for (int[] p : spots) {
                for (int s = -1; s <= 1; s++) {
                    boolean side = p[0] != tc - 6 && p[0] != tc + 6;
                    int i = p[0] + (side ? 0 : s), j = p[1] + (side ? s : 0);
                    v.fill(i, level, j, i, level + 2, j, AIR);
                    // 포디움 쪽 바깥 칸도 비움 (5층은 옥상 정원으로)
                    int oi = side ? (p[0] < tc ? -1 : 1) : 0, oj = side ? 0 : -1;
                    v.fill(i + oi, level, j + oj, i + oi, level + 2, j + oj, AIR);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 꼭대기 왕관

    /**
     * 격자 왕관 (랜턴): 맨 위층 너비에서 위로 좁아지는 둥근 네모 껍데기. 네 면 가운데 이음매가 위로 갈수록 벌어져
     * 네 꽃잎으로 갈라지고, 대각선 격자와 가로 고리, 아래 1/4 은 유리로 막아 옥상 전망 테라스를 감쌉니다.
     */
    private void crown() {
        int base = tower.roofLevel;
        int topY = base + CROWN;
        for (int y = base; y <= topY; y++) {
            double t = (y - base) / (double) CROWN;
            double wc = w1 - (w1 - 9) * Math.pow(t, 1.15);
            double half = wc / 2.0;
            double rc = Math.max(2, 4.5 - 2.5 * t);
            double gap = 0.5 + 5.5 * Math.pow(t, 1.4);
            for (int i = (int) Math.floor(tc - half - 1); i <= (int) Math.ceil(tc + half + 1); i++) {
                for (int j = (int) Math.floor(tj - half - 1); j <= (int) Math.ceil(tj + half + 1); j++) {
                    double px = i + 0.5 - (tc + 0.5), pz = j + 0.5 - (tj + 0.5);
                    double sd = FootballStadium.sdf(px, pz, half, half, rc);
                    if (sd > 0 || sd <= -1.0) {
                        continue;
                    }
                    // 이음매 틈
                    if ((Math.abs(px) < gap && Math.abs(pz) > half - 2) || (Math.abs(pz) < gap && Math.abs(px) > half - 2)) {
                        continue;
                    }
                    // 세로 살(면을 따라 3칸마다, 모서리는 꽉), 가로 고리(4칸마다), 아래쪽은 유리, 위는 트임
                    boolean onX = Math.abs(pz) > Math.abs(px);
                    double along = onX ? px : pz;
                    boolean corner = Math.abs(Math.abs(px) - Math.abs(pz)) < 1.6;
                    boolean fin = Math.floorMod((int) Math.round(along), 3) == 0;
                    boolean ring = (y - base) % 4 == 0;
                    if (corner || fin || ring) {
                        v.set(i, y, j, LATTICE);
                    } else if (t < 0.45) {
                        v.set(i, y, j, CROWN_GLASS);
                    }
                    if (corner && ring && t > 0.1) {
                        v.set(i, y, j, SEA_LANTERN); // 밤에 빛나는 왕관
                    }
                }
            }
        }
        // 옥상 테라스 조명
        for (int i = tc - w1 / 2 + 3; i <= tc + w1 / 2 - 3; i += 6) {
            for (int j = tj - w1 / 2 + 3; j <= tj + w1 / 2 - 3; j += 6) {
                if (v.get(i, base, j) == null && inPlan(i, j, FLOORS - 1)) {
                    v.set(i, base, j, Block.of("lantern[hanging=false,waterlogged=false]", 0x6A5B49));
                }
            }
        }
    }
}
