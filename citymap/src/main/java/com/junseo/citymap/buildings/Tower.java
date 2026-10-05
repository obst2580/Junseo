package com.junseo.citymap.buildings;

import java.util.Random;
import java.util.function.IntFunction;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 고층 건물 생성기 (사무 빌딩, 호텔, 주상복합, 병원 병동, 63빌딩·초고층 타워의 몸통).
 * <ul>
 *   <li>층마다 바닥판 모양({@link Shape})을 따로 줄 수 있어서 위로 갈수록 좁아지는 탑도 됩니다.</li>
 *   <li>바깥은 유리 커튼월(세로 멀리언, 층 띠), 1층은 높은 로비와 정문.</li>
 *   <li>가운데 코어: 꺾인 계단(옥상까지 걸어서), 엘리베이터, 남녀 화장실. 코어 앞(남쪽)이 복도.</li>
 *   <li>층마다 쓰임({@link Use}): 사무실, 호텔 객실, 집, 전망대, 기계실, 또는 호출한 쪽이 직접 꾸밈.</li>
 *   <li>옥상: 난간, 계단·엘리베이터 기계실 옥탑, 헬기장(선택).</li>
 * </ul>
 * 정면은 남쪽(j = d-1). 층고는 {@link Floors} 기준.
 */
final class Tower {
    enum Use { LOBBY, OFFICE, HOTEL, HOME, OBSERVATORY, MECH, CUSTOM }

    /** 층 k 에서 (i, j) 칸이 건물 안인지 */
    interface Shape {
        boolean inside(int i, int j, int k);
    }

    /** 호출한 쪽이 꾸미는 층 (Use.CUSTOM) */
    interface Custom {
        void floor(Tower t, int k, int level, int height);
    }

    static final class Spec {
        int w, d, floors;
        int lobbyH = Floors.GROUND, typicalH = Floors.OFFICE;
        Shape shape = (i, j, k) -> true;
        Block glass = Block.of("light_blue_stained_glass", 0x6699D8);
        Block mullion = Block.of("light_gray_concrete", 0x7D7D73);
        Block spandrel = Block.of("gray_concrete", 0x36393D);
        Block podium = POLISHED_ANDESITE;
        Block roof = SMOOTH_STONE;
        int mullionEvery = 3;
        IntFunction<Use> use = k -> k == 0 ? Use.LOBBY : Use.OFFICE;
        Custom custom;
        int elevators = 2;
        int restrooms = 2;
        int extraTop = 8;
        boolean helipad;
        /** 로비 안내판·정문 위 글씨 (표지판) */
        String name;
        String[] directory;

        Spec(int w, int d, int floors) {
            this.w = w;
            this.d = d;
            this.floors = floors;
        }
    }

    final Spec s;
    final Voxels v;
    final Random r;
    final int[] levels;
    /** 코어 바깥 상자 (벽 포함) */
    final int ci0, cj0, ci1, cj1;
    /** 코어 안 좌표(a)로 마지막 벽 칸 */
    private final int coreEnd;
    /** 코어 앞 복도 (엘리베이터 승강장) 줄 */
    final int corridorJ;
    final int roofLevel;

    private Tower(Spec s, Random r) {
        this.s = s;
        this.r = r;
        this.levels = Floors.levels(s.lobbyH, s.typicalH, s.floors);
        roofLevel = levels[s.floors];
        v = new Voxels(s.w, s.d, -1, roofLevel + 2 + s.extraTop);
        int depth = Math.max(Interior.stairDepth(levels), 6);
        coreEnd = s.restrooms > 0 ? 7 + 4 * s.elevators + 8 * s.restrooms : s.elevators > 0 ? 4 * s.elevators + 5 : 5;
        int coreW = coreEnd + 2;
        // 코어는 맨 위층 바닥판 안 가운데
        int[] box = bounds(s.floors - 1);
        int cx = (box[0] + box[2]) / 2, cz = (box[1] + box[3]) / 2;
        ci0 = cx - coreW / 2;
        ci1 = ci0 + coreW - 1;
        cj0 = cz - (depth + 2) / 2;
        cj1 = cj0 + depth + 1;
        corridorJ = cj1 + 1;
    }

    static Tower build(Spec s, Random r) {
        Tower t = new Tower(s, r);
        t.slabs();
        t.core();
        for (int k = 0; k < s.floors; k++) {
            t.floor(k);
        }
        t.facade();
        t.roof();
        t.v.connect();
        return t;
    }

    boolean inside(int i, int j, int k) {
        return i >= 0 && j >= 0 && i < s.w && j < s.d && s.shape.inside(i, j, k);
    }

    /** 바깥 벽 칸인지 (안이면서 옆 칸 하나라도 밖) */
    boolean edge(int i, int j, int k) {
        return inside(i, j, k) && (!inside(i - 1, j, k) || !inside(i + 1, j, k) || !inside(i, j - 1, k) || !inside(i, j + 1, k));
    }

    /** 가구를 놓아도 되는 칸: 안쪽, 바깥 벽·코어·코어 둘레 복도(2칸)가 아님 */
    boolean free(int i, int j, int k) {
        if (!inside(i, j, k) || edge(i, j, k)) {
            return false;
        }
        return i < ci0 - 2 || i > ci1 + 2 || j < cj0 - 2 || j > cj1 + 2;
    }

    int[] bounds(int k) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (inside(i, j, k)) {
                    b[0] = Math.min(b[0], i);
                    b[1] = Math.min(b[1], j);
                    b[2] = Math.max(b[2], i);
                    b[3] = Math.max(b[3], j);
                }
            }
        }
        return b;
    }

    int height(int k) {
        return levels[k + 1] - levels[k];
    }

    // ------------------------------------------------------------------ 바닥판·코어

    private void slabs() {
        for (int k = 0; k <= s.floors; k++) {
            int y = levels[k] - 1;
            for (int j = 0; j < s.d; j++) {
                for (int i = 0; i < s.w; i++) {
                    boolean here = k < s.floors && inside(i, j, k);
                    boolean below = k > 0 && inside(i, j, k - 1);
                    if (here || below) {
                        v.set(i, y, j, k == 0 ? s.podium : k == s.floors ? s.roof : SMOOTH_STONE);
                    }
                }
            }
        }
    }

    private void core() {
        Frame cf = new Frame(v, ci0 + 1, cj0 + 1, 0);
        int depth = cj1 - cj0 - 1;
        Block wall = Interior.CORE_WALL;
        int[] stairLevels = levels.clone();
        // 계단 (출입구가 복도 쪽, 즉 남쪽을 보게 뒤집어 놓음)
        int sd = Interior.stairDepth(levels);
        Interior.stairCore(cf.sub(4, depth - 1, "north"), stairLevels, wall, "polished_andesite", 0x848685);
        if (sd < depth) {
            cf.fill(-1, levels[0] - 1, 0, 5, roofLevel + 2, depth - 1 - sd, wall);
        }
        int a = 7;
        int[] liftLevels = java.util.Arrays.copyOf(levels, s.floors); // 옥상에는 서지 않음
        for (int e = 0; e < s.elevators; e++) {
            cf.fill(a - 1, levels[0] - 1, -1, a + 2, roofLevel + 3, depth - 3, wall);
            Interior.elevator(cf.sub(a, depth - 2, "south"), liftLevels, wall);
            a += 4;
        }
        String[] labels = {"남자 화장실", "여자 화장실"};
        for (int n = 0; n < s.restrooms; n++) {
            for (int k = 0; k < s.floors; k++) {
                if (s.use.apply(k) == Use.MECH) {
                    cf.fill(a, levels[k] - 1, -1, a + 7, levels[k + 1] - 2, depth, wall);
                    continue;
                }
                Interior.restroom(cf, a + 1, 0, a + 7, depth - 1, levels[k], height(k), labels[n % 2], a + 4);
                cf.set(a + 4, levels[k] + 2, depth, wall);
            }
            a += 8;
        }
        // 코어 위 기계실 지붕
        cf.fill(-1, roofLevel + 3, -1, coreEnd, roofLevel + 3, depth, s.roof);
        cf.walls(-1, roofLevel, -1, coreEnd, roofLevel + 2, depth, wall);
        // 옥상 출입구 (계단 쪽)
        cf.fill(3, roofLevel, depth, 4, roofLevel + 2, depth, AIR);
    }

    // ------------------------------------------------------------------ 층마다 꾸미기

    private void floor(int k) {
        int level = levels[k], h = height(k);
        Use use = s.use.apply(k);
        Frame f = Frame.of(v);
        // 복도·코어 둘레 천장 등
        for (int i = ci0 - 1; i <= ci1 + 1; i += 5) {
            if (inside(i, corridorJ + 1, k) && v.get(i, level + h - 2, corridorJ + 1) == null) {
                v.set(i, level + h - 2, corridorJ + 1, Interior.LIGHT);
            }
        }
        switch (use) {
            case LOBBY -> lobby(k);
            case OFFICE -> {
                Rooms.office(f, r, 0, 0, s.w - 1, s.d - 1, level, h, (i, j) -> free(i, j, k));
                meetingRoom(k);
            }
            case HOTEL -> bands(k, 5, (fr, w, d) -> Rooms.hotel(fr, r, w, d, level, h));
            case HOME -> bands(k, 10, (fr, w, d) -> Rooms.home(fr, r, w, d, level, h, 1));
            case OBSERVATORY -> observatory(k);
            case MECH -> mech(k);
            case CUSTOM -> {
                if (s.custom != null) {
                    s.custom.floor(this, k, level, h);
                }
            }
        }
    }

    private void lobby(int k) {
        int level = levels[k], h = height(k);
        int[] b = bounds(k);
        Frame f = Frame.of(v);
        // 정면 안내 데스크와 대기 의자
        int mid = (b[0] + b[2]) / 2, front = b[3] - 1;
        for (int i = mid - 3; i <= mid + 3; i++) {
            if (free(i, front - 6, k)) {
                v.set(i, level, front - 6, Furniture.COUNTER);
            }
        }
        if (free(mid, front - 7, k)) {
            Furniture.chair(f, mid, level, front - 7, "north", "dark_oak");
        }
        Random rr = new Random(r.nextLong());
        for (int i = b[0] + 3; i <= b[2] - 3; i += 7) {
            if (Math.abs(i - mid) < 6) {
                continue;
            }
            if (free(i, front - 3, k) && free(i + 2, front - 3, k)) {
                Furniture.sofa(f, rr, i, level, front - 3, 3, "south");
                Furniture.plant(f, rr, i - 1, level, front - 3);
            }
        }
        // 층 안내판 (엘리베이터 옆 벽)
        if (s.name != null) {
            String[] lines = s.directory != null ? s.directory : new String[]{"", s.name, "", ""};
            Block sign = Blocks.wallSign("dark_oak", "south", "white", true, lines);
            if (v.get(ci0 + 6, level + 1, corridorJ) == null) {
                v.set(ci0 + 6, level + 1, corridorJ, sign);
            }
        }
        // 높은 로비는 큰 등
        for (int j = b[1] + 3; j <= b[3] - 3; j += 6) {
            for (int i = b[0] + 3; i <= b[2] - 3; i += 6) {
                if (free(i, j, k)) {
                    v.set(i, level + h - 2, j, Interior.LIGHT);
                }
            }
        }
    }

    private void meetingRoom(int k) {
        int[] b = bounds(k);
        int a0 = b[0] + 2, b0 = b[1] + 2, a1 = a0 + 7, b1 = b0 + 5;
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                if (!free(i, j, k) || (v.get(i, levels[k], j) != null)) {
                    return;
                }
            }
        }
        Rooms.meeting(Frame.of(v), a0, b0, a1, b1, levels[k], height(k));
    }

    interface Unit {
        void build(Frame f, int w, int d);
    }

    /**
     * 복도식 층: 코어 앞뒤 복도를 따라 북쪽·남쪽 바깥 벽까지 방을 줄지어 놓습니다 (네모난 바닥판).
     * 방 너비 unitW (칸막이 벽 포함).
     */
    private void bands(int k, int unitW, Unit unit) {
        int level = levels[k], h = height(k), top = level + h - 2;
        int[] b = bounds(k);
        Block wall = Interior.INNER_WALL;
        int north0 = b[1] + 1, north1 = cj0 - 4, south0 = corridorJ + 3, south1 = b[3] - 1;
        // 코어 둘레 복도 (2칸) 바깥에 벽
        for (int i = b[0] + 1; i <= b[2] - 1; i++) {
            if (north1 - north0 >= 3) {
                v.fill(i, level, north1 + 1, i, top, north1 + 1, wall);
            }
            if (south1 - south0 >= 3) {
                v.fill(i, level, south0 - 1, i, top, south0 - 1, wall);
            }
        }
        for (int i = b[0] + 1; i + unitW - 1 <= b[2] - 1; i += unitW) {
            // 남쪽 줄: +b 가 남쪽
            if (south1 - south0 >= 3) {
                v.fill(i + unitW - 1, level, south0, i + unitW - 1, top, south1, wall);
                unit.build(Frame.facing(v, i, south0, "south"), unitW - 1, south1 - south0 + 1);
            }
            // 북쪽 줄: +b 가 북쪽, a 는 서쪽으로
            if (north1 - north0 >= 3) {
                v.fill(i + unitW - 1, level, north0, i + unitW - 1, top, north1, wall);
                unit.build(Frame.facing(v, i + unitW - 2, north1, "north"), unitW - 1, north1 - north0 + 1);
            }
        }
    }

    private void observatory(int k) {
        int level = levels[k];
        int[] b = bounds(k);
        Frame f = Frame.of(v);
        // 창가 의자, 가운데 카페 계산대
        for (int j = b[1] + 2; j <= b[3] - 2; j += 4) {
            for (int i = b[0] + 2; i <= b[2] - 2; i += 4) {
                if (free(i, j, k) && (!inside(i - 3, j, k) || !inside(i + 3, j, k) || !inside(i, j - 3, k) || !inside(i, j + 3, k))) {
                    Furniture.chair(f, i, level, j, "north", "spruce");
                }
            }
        }
        int mid = (b[0] + b[2]) / 2;
        for (int i = mid - 2; i <= mid + 2; i++) {
            if (free(i, corridorJ + 4, k)) {
                v.set(i, level, corridorJ + 4, Furniture.COUNTER);
            }
        }
        Interior.lights(f, b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, level, height(k), 6, Interior.LIGHT);
    }

    private void mech(int k) {
        int level = levels[k];
        int[] b = bounds(k);
        for (int j = b[1] + 2; j <= b[3] - 2; j += 4) {
            for (int i = b[0] + 2; i <= b[2] - 3; i += 5) {
                if (free(i, j, k) && free(i + 1, j, k)) {
                    v.fill(i, level, j, i + 1, level + 1, j, IRON_BLOCK);
                    v.set(i, level, j + 1, CAULDRON);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 바깥

    private void facade() {
        for (int k = 0; k < s.floors; k++) {
            int level = levels[k], top = levels[k + 1] - 2;
            boolean lobby = s.use.apply(k) == Use.LOBBY;
            for (int j = 0; j < s.d; j++) {
                for (int i = 0; i < s.w; i++) {
                    if (!edge(i, j, k)) {
                        continue;
                    }
                    boolean ns = !inside(i, j - 1, k) || !inside(i, j + 1, k);
                    boolean ew = !inside(i - 1, j, k) || !inside(i + 1, j, k);
                    boolean post = (ns && ew) || (ns && i % s.mullionEvery == 0) || (!ns && j % s.mullionEvery == 0);
                    for (int y = level; y <= top; y++) {
                        v.set(i, y, j, post ? s.mullion : lobby && y == level ? s.podium : s.glass);
                    }
                    // 층 띠
                    v.set(i, top + 1, j, s.spandrel);
                }
            }
            if (lobby) {
                entrance(k);
            }
        }
        // 옥상 난간
        int k = s.floors - 1;
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (edge(i, j, k)) {
                    v.set(i, roofLevel, j, s.spandrel);
                    v.set(i, roofLevel + 1, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
                }
            }
        }
    }

    /** 정면(남쪽) 가운데 출입구: 3칸 높이 열린 문, 위에 차양 */
    private void entrance(int k) {
        int level = levels[k];
        int[] b = bounds(k);
        int mid = (b[0] + b[2]) / 2;
        int jf = -1;
        for (int j = s.d - 1; j >= 0; j--) {
            if (edge(mid, j, k)) {
                jf = j;
                break;
            }
        }
        if (jf < 0) {
            return;
        }
        for (int i = mid - 2; i <= mid + 2; i++) {
            if (edge(i, jf, k)) {
                v.fill(i, level, jf, i, level + 2, jf, AIR);
                v.set(i, level + 3, jf, s.mullion);
            }
        }
        for (int i = mid - 4; i <= mid + 4; i++) {
            for (int j = jf + 1; j <= Math.min(s.d - 1, jf + 3); j++) {
                if (!inside(i, j, k)) {
                    v.set(i, level + 4, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
                }
            }
        }
        if (s.name != null && jf + 1 < s.d) {
            v.set(mid, level + 3, jf + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", s.name));
        }
    }

    private void roof() {
        if (!s.helipad) {
            return;
        }
        int[] b = bounds(s.floors - 1);
        int cx = (b[0] + b[2]) / 2, cz = b[1] + (cj0 - b[1]) / 2;
        int r = Math.min(7, Math.min(cj0 - b[1] - 3, (b[2] - b[0]) / 2 - 2));
        if (r < 4) {
            return;
        }
        Block pad = Block.of("gray_concrete", 0x36393D), mark = Block.of("yellow_concrete", 0xF0AF15);
        for (int j = cz - r; j <= cz + r; j++) {
            for (int i = cx - r; i <= cx + r; i++) {
                double dd = Math.hypot(i - cx, j - cz);
                if (dd <= r && inside(i, j, s.floors - 1)) {
                    v.set(i, roofLevel - 1, j, dd > r - 1 ? mark : pad);
                }
            }
        }
        // H
        for (int t = -2; t <= 2; t++) {
            v.set(cx - 2, roofLevel - 1, cz + t, Block.of("white_concrete", 0xCFD5D6));
            v.set(cx + 2, roofLevel - 1, cz + t, Block.of("white_concrete", 0xCFD5D6));
        }
        v.set(cx - 1, roofLevel - 1, cz, Block.of("white_concrete", 0xCFD5D6));
        v.set(cx, roofLevel - 1, cz, Block.of("white_concrete", 0xCFD5D6));
        v.set(cx + 1, roofLevel - 1, cz, Block.of("white_concrete", 0xCFD5D6));
    }
}
