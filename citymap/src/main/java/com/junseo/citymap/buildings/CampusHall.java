package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 서울대학교 캠퍼스 건물 (가운데 복도 양옆에 방이 늘어선 판상형, 관악 캠퍼스의 회색 콘크리트·벽돌 건물).
 * <ul>
 *   <li>긴 가운데 복도(3칸), 북쪽·남쪽 방 줄. 북쪽 줄 서쪽 끝에 꺾인 계단실·엘리베이터·남녀 화장실,
 *       긴 건물은 동쪽 끝에도 계단실</li>
 *   <li>1층 남쪽(정면) 가운데 현관 로비와 양여닫이 문, 차양, 「서울대학교 ○○ ○동」 표지판. 복도 양 끝 비상문</li>
 *   <li>쓰임({@link Kind})마다 방: 강의동은 교실과 교수 연구실, 행정관은 사무실·민원실·총장실, 도서관은 열람실과 자료실,
 *       학생회관은 학생식당·서점·동아리방, 기숙사는 2인실, 연구소는 실험실</li>
 *   <li>바깥({@link Skin}): 노출 콘크리트에 세로 핀(행정관·연구소), 붉은 벽돌에 창(강의동·기숙사·학생회관),
 *       유리와 흰 띠(도서관). 옥상 난간, 계단실·승강기 기계실 옥탑, 실외기</li>
 * </ul>
 * 정면은 남쪽(j = d-1). 층고는 {@link Floors} 기준 (1층 5칸, 위층 4칸, 기숙사는 모두 4칸).
 */
final class CampusHall {
    enum Kind { LECTURE, ADMIN, LIBRARY, UNION, DORM, LAB }

    enum Skin { CONCRETE_FINS, BRICK, GLASS }

    static final class Spec {
        int w, d, floors;
        int north = 9, south = 9, front = 4;
        int groundH = Floors.GROUND, typicalH = Floors.OFFICE;
        Kind kind = Kind.LECTURE;
        Skin skin = Skin.BRICK;
        /** 건물 이름 (예: 「중앙도서관」)과 동 번호 (예: 「62동」) */
        String name = "", number = "";

        Spec(int w, int floors, Kind kind, Skin skin) {
            this.w = w;
            this.floors = floors;
            this.kind = kind;
            this.skin = skin;
        }

        /** 이 설정으로 필요한 상자 깊이 */
        int depth() {
            return north + south + 8 + front;
        }
    }

    /** 노출 콘크리트 (밝은 회색) */
    private static final Block CONCRETE = SMOOTH_STONE;
    private static final Block CONCRETE_LIGHT = Block.of("polished_diorite", 0xC0C0C1);
    private static final Block BRICK = BRICKS;
    private static final Block BASE = POLISHED_ANDESITE;
    private static final Block DARK_GLASS = Block.of("gray_stained_glass", 0x4C4C4C);
    private static final Block CLEAR_GLASS = Block.of("light_blue_stained_glass", 0x6699D8);
    private static final Block WALL = Interior.INNER_WALL;
    private static final String[] PROFS = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신"};

    final Spec s;
    final Voxels v;
    final Random r;
    final int[] levels;
    final int x0, x1, z0, z1, c0, mid, roof;
    final boolean eastStair;
    private final int stairDepth;

    private CampusHall(Spec s, int d, Random r) {
        this.s = s;
        this.r = r;
        levels = Floors.levels(s.groundH, s.typicalH, s.floors);
        roof = levels[s.floors];
        v = new Voxels(s.w, d, -1, roof + 6);
        x0 = 1;
        x1 = s.w - 2;
        z0 = 1;
        c0 = z0 + s.north + 2;
        z1 = c0 + s.south + 4;
        mid = s.w / 2;
        eastStair = s.w >= 56;
        stairDepth = Interior.stairDepth(levels);
    }

    static Voxels build(Spec s, int w, int d, Random r) {
        s.w = w;
        CampusHall h = new CampusHall(s, d, r);
        h.shell();
        h.core();
        for (int k = 0; k < s.floors; k++) {
            h.floor(k);
        }
        h.facade();
        h.entrance();
        h.roofTop();
        h.v.connect();
        return h.v;
    }

    // ------------------------------------------------------------------ 몸체·코어

    private void shell() {
        // 앞·옆 포장
        v.fill(0, -1, 0, s.w - 1, -1, v.d - 1, LIGHT_GRAY_CONCRETE);
        for (int k = 0; k <= s.floors; k++) {
            int y = levels[k] - 1;
            v.fill(x0, y, z0, x1, y, z1, k == 0 ? BASE : k == s.floors ? SMOOTH_STONE : SMOOTH_STONE);
        }
        for (int k = 0; k < s.floors; k++) {
            int level = levels[k], top = levels[k + 1] - 2;
            v.walls(x0, level, z0, x1, top, z1, wallBlock());
            // 복도 벽
            v.fill(x0 + 1, level, c0 - 1, x1 - 1, top, c0 - 1, WALL);
            v.fill(x0 + 1, level, c0 + 3, x1 - 1, top, c0 + 3, WALL);
            // 복도 바닥 (밝은 테라조) 과 등
            v.fill(x0 + 1, level - 1, c0, x1 - 1, level - 1, c0 + 2, k == 0 ? BASE : POLISHED_DIORITE);
            for (int i = x0 + 4; i <= x1 - 3; i += 6) {
                v.set(i, top, c0 + 1, Interior.LIGHT);
            }
        }
    }

    private Block wallBlock() {
        return switch (s.skin) {
            case CONCRETE_FINS -> CONCRETE;
            case BRICK -> BRICK;
            case GLASS -> CONCRETE_LIGHT;
        };
    }

    /** 계단실(서쪽, 긴 건물은 동쪽에도), 엘리베이터, 화장실 */
    private void core() {
        int[] liftLevels = java.util.Arrays.copyOf(levels, s.floors);
        Block wall = Interior.CORE_WALL;
        stair(Frame.facing(v, x0 + 5, c0 - 2, "north"), x0, x0 + 6);
        if (eastStair) {
            stair(Frame.facing(v, x1 - 1, c0 - 2, "north"), x1 - 6, x1);
        }
        boolean lift = s.kind != Kind.DORM || s.floors > 4;
        if (lift) {
            Interior.elevator(new Frame(v, x0 + 7, c0 - 3, 0), liftLevels, wall);
            v.fill(x0 + 6, levels[0] - 1, z0 + 1, x0 + 9, roof + 3, c0 - 5, wall);
            v.fill(x0 + 6, roof, c0 - 4, x0 + 9, roof + 3, c0 - 1, wall);
        }
        int ra = lift ? x0 + 10 : x0 + 7;
        String[] labels = s.kind == Kind.DORM ? new String[]{"화장실·샤워실", "세면실"} : new String[]{"남자 화장실", "여자 화장실"};
        for (int k = 0; k < s.floors; k++) {
            for (int n = 0; n < 2; n++) {
                int a0 = ra + n * 8;
                Interior.restroom(Frame.of(v), a0, z0 + 1, a0 + 6, c0 - 2, levels[k], levels[k + 1] - levels[k], labels[n], a0 + 3);
            }
        }
    }

    private void stair(Frame f, int wi0, int wi1) {
        Interior.stairCore(f, levels, Interior.CORE_WALL, "polished_andesite", 0x848685);
        int back = c0 - 2 - stairDepth; // 계단실 뒷벽 j
        if (back > z0) {
            v.fill(wi0, levels[0] - 1, z0 + 1, wi1, levels[s.floors] + 2, back - 1, Interior.CORE_WALL);
        }
        // 옥탑 지붕
        v.fill(wi0, levels[s.floors] + 3, Math.max(z0, back), wi1, levels[s.floors] + 3, c0 - 1, SMOOTH_STONE);
    }

    /** 서쪽 계단·승강기·화장실이 차지하는 북쪽 줄 끝 i */
    int serviceEnd() {
        return (s.kind != Kind.DORM || s.floors > 4) ? x0 + 25 : x0 + 22;
    }

    int northRoomsEnd() {
        return eastStair ? x1 - 7 : x1 - 1;
    }

    // ------------------------------------------------------------------ 층

    private void floor(int k) {
        int level = levels[k], h = levels[k + 1] - level;
        int n0 = serviceEnd() + 1, n1 = northRoomsEnd();
        // 북쪽 방 줄: +b 가 북쪽 (a 는 서쪽으로)
        Frame fn = Frame.facing(v, n1, c0 - 2, "north");
        Kit.Room north = northRoom(k, level, h);
        Kit.strip(fn, 0, n1 - n0, s.north, level, h, northWidth(k), WALL, false, north);
        // 남쪽 방 줄: 1층은 가운데 현관 로비를 비움
        Kit.Room south = southRoom(k, level, h);
        int sw = southWidth(k);
        if (k == 0) {
            Kit.strip(Frame.facing(v, x0 + 1, c0 + 4, "south"), 0, mid - 6 - (x0 + 1), s.south, level, h, sw, WALL, false, south);
            Kit.strip(Frame.facing(v, mid + 6, c0 + 4, "south"), 0, x1 - 1 - (mid + 6), s.south, level, h, sw, WALL, false, south);
            lobby(level, h);
        } else {
            Kit.strip(Frame.facing(v, x0 + 1, c0 + 4, "south"), 0, x1 - 1 - (x0 + 1), s.south, level, h, sw, WALL, false, south);
        }
        // 복도 끝 창 (위층), 1층은 비상문
        for (int i : new int[]{x0, x1}) {
            if (k == 0) {
                Frame fd = Frame.facing(v, i, c0 + 1, i == x0 ? "west" : "east");
                Interior.door(fd, 0, level, 0, "oak", "south");
            } else {
                v.fill(i, level + 1, c0, i, level + 2, c0 + 2, CLEAR_GLASS);
            }
        }
    }

    private int northWidth(int k) {
        return switch (s.kind) {
            case LECTURE -> k == 0 ? 9 : 5;
            case ADMIN -> k == s.floors - 1 ? 12 : 9;
            case LIBRARY -> 200;
            case UNION -> k == 0 ? 200 : 6;
            case DORM -> 4;
            case LAB -> 5;
        };
    }

    private int southWidth(int k) {
        return switch (s.kind) {
            case LECTURE -> k == 0 ? 15 : 11;
            case ADMIN -> k == s.floors - 1 ? 14 : 13;
            case LIBRARY -> 200;
            case UNION -> k == 0 ? 200 : k == 1 ? 12 : 6;
            case DORM -> k == 0 ? 9 : 4;
            case LAB -> 11;
        };
    }

    private Kit.Room northRoom(int k, int level, int h) {
        return (f, w, d, n) -> {
            switch (s.kind) {
                case LECTURE -> {
                    if (k == 0) {
                        Kit.roomDoor(f, w - 2, level, "oak", n == 0 ? "학과 사무실" : "조교실");
                        office(f, w, d, level);
                    } else {
                        professor(f, w, d, level, k, n);
                    }
                }
                case ADMIN -> {
                    if (k == s.floors - 1 && n == 0) {
                        Kit.roomDoor(f, w - 2, level, "dark_oak", "대회의실");
                        Rooms.meeting(f, 0, 0, w - 1, d - 1, level, h);
                    } else {
                        Kit.roomDoor(f, w - 2, level, "oak", adminOffice(k, n + 3));
                        office(f, w, d, level);
                    }
                }
                case LIBRARY -> {
                    Kit.roomDoor(f, 2, level, "oak", k == 0 ? "보존서고" : k + 1 + "층 자료실");
                    stacks(f, w, d, level);
                }
                case UNION -> {
                    if (k == 0) {
                        Kit.roomDoor(f, 2, level, "oak", "조리실");
                        kitchen(f, w, d, level);
                    } else {
                        club(f, w, d, level, k, n + 10);
                    }
                }
                case DORM -> dormRoom(f, w, d, level, k, n);
                case LAB -> professor(f, w, d, level, k, n);
            }
            Interior.lights(f, 0, 0, w - 1, d - 1, level, h, 4, Interior.LIGHT);
        };
    }

    private Kit.Room southRoom(int k, int level, int h) {
        return (f, w, d, n) -> {
            switch (s.kind) {
                case LECTURE -> {
                    if (w >= 6) {
                        Rooms.classroom(f, w, d, level, h);
                        label(f, w - 1, level, (k + 1) + String.format("%02d", n + 1 + (f.oi > mid ? 5 : 0)) + "호 강의실");
                    }
                }
                case ADMIN -> {
                    if (k == s.floors - 1 && n == 1) {
                        Kit.roomDoor(f, 1, level, "dark_oak", "총장실");
                        president(f, w, d, level);
                    } else if (k == 0) {
                        Kit.roomDoor(f, w / 2, level, "oak", n == 0 ? "학사과 민원실" : "장학복지과");
                        counterRoom(f, w, d, level);
                    } else {
                        Kit.roomDoor(f, w / 2, level, "oak", adminOffice(k, n));
                        Rooms.office(f, r, 0, 0, w - 1, d - 1, level, h, (a, b) -> a >= 0 && b >= 0 && a < w && b < d);
                    }
                }
                case LIBRARY -> {
                    if (k == 0) {
                        Kit.roomDoor(f, w / 2, level, "oak", n == 0 ? "신문·잡지실" : "북카페");
                        magazines(f, w, d, level, n);
                    } else {
                        Kit.doubleDoor(f, 2, level, -1, "oak", "south");
                        label(f, 4, level, "제" + ((k - 1) * 2 + 1) + "열람실");
                        reading(f, w, d, level);
                    }
                }
                case UNION -> {
                    if (k == 0) {
                        if (f.oi < mid) {
                            Kit.doubleDoor(f, w - 3, level, -1, "oak", "south");
                            label(f, w - 4, level, "학생식당");
                            cafeteria(f, w, d, level, h);
                        } else {
                            Kit.roomDoor(f, 2, level, "oak", "편의점·카페");
                            store(f, w, d, level);
                        }
                    } else if (k == 1) {
                        String[] names = {"서점", "문구점", "은행", "우체국", "여행사"};
                        String name = names[n % names.length];
                        Kit.roomDoor(f, w / 2, level, "oak", name);
                        if (name.equals("서점") || name.equals("문구점")) {
                            bookstore(f, w, d, level);
                        } else {
                            counterRoom(f, w, d, level);
                        }
                    } else {
                        club(f, w, d, level, k, n);
                    }
                }
                case DORM -> {
                    if (k == 0) {
                        String[] names = {"관리사무실", "휴게실", "세탁실", "편의점", "택배 보관실"};
                        String name = names[n % names.length];
                        Kit.roomDoor(f, w / 2, level, "oak", name);
                        switch (name) {
                            case "휴게실" -> lounge(f, w, d, level);
                            case "세탁실" -> laundry(f, w, d, level);
                            case "편의점" -> store(f, w, d, level);
                            default -> office(f, w, d, level);
                        }
                    } else {
                        dormRoom(f, w, d, level, k, 20 + n);
                    }
                }
                case LAB -> {
                    Kit.roomDoor(f, w - 2, level, "oak", (k == 0 ? "공동기기실 " : "실험실 ") + (k + 1) + String.format("%02d", n + 1));
                    lab(f, w, d, level, k == 0);
                }
            }
            Interior.lights(f, 0, 0, w - 1, d - 1, level, h, 4, Interior.LIGHT);
        };
    }

    private String adminOffice(int k, int n) {
        String[][] names = {{"학사과", "입학본부", "교무처", "기획처", "연구처", "재무과"},
                {"총무과", "시설과", "인사과", "대외협력", "감사실", "법무팀"}};
        return names[k % 2][n % names[0].length];
    }

    private static void label(Frame f, int a, int level, String text) {
        if (f.empty(a, level + 1, -2)) {
            f.set(a, level + 1, -2, Blocks.wallSign("birch", "north", "black", false, "", text));
        }
    }

    // ------------------------------------------------------------------ 방 꾸미기

    /** 교수 연구실: 책상과 모니터, 책장 벽, 작은 손님 탁자 */
    private void professor(Frame f, int w, int d, int level, int k, int n) {
        String name = PROFS[Math.floorMod(n * 7 + k * 3, PROFS.length)] + "○○ 교수";
        Kit.roomDoor(f, w - 1, level, "oak", (k + 1) + String.format("%02d", n + 31) + "호");
        f.set(w - 1, level + 1, -2, null);
        label(f, w - 1, level, name);
        Furniture.desk(f, 1, level, d - 2, "north");
        for (int b = 0; b < d - 1; b++) {
            f.set(0, level, b, Furniture.BOOKSHELF);
            f.set(0, level + 1, b, Furniture.BOOKSHELF);
        }
        if (w >= 4 && d >= 5) {
            f.set(w - 2, level, 2, Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
            Furniture.chair(f, w - 2, level, 3, "south", "spruce");
        }
    }

    private void office(Frame f, int w, int d, int level) {
        for (int a = 1; a < w - 1; a += 3) {
            Furniture.desk(f, a, level, d - 2, "north");
            if (d >= 6) {
                Furniture.desk(f, a, level, 2, "south");
            }
        }
        f.set(w - 1, level, d - 1, Furniture.BOOKSHELF);
        f.set(w - 1, level + 1, d - 1, Furniture.BOOKSHELF);
        Furniture.plant(f, r, 0, level, d - 1);
    }

    /** 민원 창구: 문 앞 대기 의자, 창구 카운터, 뒤 책상 */
    private void counterRoom(Frame f, int w, int d, int level) {
        int cb = d / 2;
        for (int a = 0; a < w; a++) {
            f.set(a, level, cb, a == w - 1 ? AIR : Furniture.COUNTER);
        }
        for (int a = 1; a < w - 2; a += 2) {
            Furniture.desk(f, a, level, d - 1, "north");
        }
        Kit.seats(f, 1, w - 3, level, 1, "south", "polished_andesite");
    }

    /** 총장실: 큰 책상, 응접 소파, 책장 */
    private void president(Frame f, int w, int d, int level) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, Block.of("dark_oak_planks", 0x432B14));
        f.fill(w / 2 - 1, level, d - 3, w / 2 + 1, level, d - 3, Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
        Furniture.chair(f, w / 2, level, d - 2, "south", "dark_oak");
        for (int a = 0; a < w; a++) {
            f.set(a, level, d - 1, Furniture.BOOKSHELF);
        }
        Furniture.sofa(f, r, 1, level, 1, 3, "south");
        Furniture.sofa(f, r, 1, level, 4, 3, "north");
        f.fill(1, level, 2, 3, level, 3, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        Furniture.plant(f, r, w - 1, level, 0);
    }

    /** 자료실 서가: 두 칸 높이 책장 줄, 사이 통로 */
    private void stacks(Frame f, int w, int d, int level) {
        for (int b = 1; b < d - 1; b += 3) {
            for (int a = 1; a < w - 1; a++) {
                if (a % 12 == 6) {
                    continue;
                }
                f.set(a, level, b, Furniture.BOOKSHELF);
                f.set(a, level + 1, b, Furniture.BOOKSHELF);
            }
        }
        for (int a = 0; a < w; a++) {
            if (f.empty(a, level, d - 1)) {
                f.set(a, level, d - 1, Furniture.DESK_TOP);
            }
        }
    }

    /** 열람실: 칸막이 긴 책상 줄과 의자, 책상마다 스탠드 */
    private void reading(Frame f, int w, int d, int level) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, Block.of("light_gray_carpet", 0x8E8E86));
        for (int b = 2; b + 2 < d; b += 4) {
            for (int a = 1; a < w - 1; a++) {
                if (a % 10 == 0) {
                    continue;
                }
                f.set(a, level, b, Furniture.DESK_TOP);
                f.set(a, level, b + 1, Furniture.DESK_TOP);
                f.set(a, level + 1, b, a % 2 == 0 ? Block.of("white_stained_glass_pane", 0xF0F0F0) : AIR);
                if (a % 2 == 1) {
                    Furniture.chair(f, a, level, b - 1, "north", "oak");
                    Furniture.chair(f, a, level, b + 2, "south", "oak");
                }
            }
        }
    }

    private void magazines(Frame f, int w, int d, int level, int n) {
        for (int a = 0; a < w; a += 1) {
            f.set(a, level, d - 1, Furniture.BOOKSHELF);
        }
        for (int a = 1; a + 2 < w; a += 5) {
            Furniture.sofa(f, r, a, level, d / 2, 3, "north");
            f.set(a + 1, level, d / 2 - 1, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        }
        if (n == 1) {
            for (int a = 0; a < Math.min(w, 6); a++) {
                f.set(a, level, 1, Furniture.COUNTER);
            }
        }
    }

    /** 학생식당: 배식대, 식판 반납대, 긴 식탁 줄 */
    private void cafeteria(Frame f, int w, int d, int level, int h) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, Block.of("white_terracotta", 0xD1B2A1));
        // 배식대: 북쪽 벽(복도 쪽) 서쪽 끝을 따라
        for (int a = 0; a < Math.min(w - 4, 14); a++) {
            f.set(a, level, 0, a % 4 == 1 ? CAULDRON : Furniture.COUNTER);
            if (a % 4 == 3) {
                f.set(a, level + 1, 0, Block.of("iron_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
            }
        }
        f.set(4, level + h - 2, 1, Blocks.hangingSign("birch", 0, "black", false, "학생식당", "오늘의 메뉴", "백반 5,000원"));
        // 식탁 줄 (긴 탁자, 양옆 의자)
        for (int b = 3; b + 2 < d; b += 3) {
            for (int a = 2; a < w - 2; a++) {
                if (a % 9 == 0) {
                    continue;
                }
                f.set(a, level, b, Block.of("birch_slab[type=top,waterlogged=false]", 0xC0AF79));
                Furniture.chair(f, a, level, b - 1, "north", "birch");
                if (b + 1 < d - 1) {
                    Furniture.chair(f, a, level, b + 1, "south", "birch");
                }
            }
        }
        // 반납대
        f.set(w - 1, level, 0, Furniture.COUNTER);
        f.set(w - 2, level, 0, Furniture.COUNTER);
    }

    private void kitchen(Frame f, int w, int d, int level) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, Block.of("light_gray_terracotta", 0x876A61));
        for (int a = 0; a < w; a++) {
            Block b = switch (a % 5) {
                case 0 -> Block.of("smoker[facing=south,lit=false]", 0x555451);
                case 1 -> CAULDRON;
                case 2 -> IRON_BLOCK;
                default -> Furniture.COUNTER;
            };
            f.set(a, level, d - 1, b);
        }
        for (int a = 2; a < w - 2; a += 6) {
            f.fill(a, level, d / 2, a + 3, level, d / 2, Furniture.COUNTER);
        }
    }

    private void store(Frame f, int w, int d, int level) {
        for (int b = 2; b < d - 1; b += 2) {
            for (int a = 1; a < w - 2; a++) {
                f.set(a, level, b, Block.of("white_terracotta", 0xD1B2A1));
                f.set(a, level + 1, b, (a + b) % 3 == 0 ? Block.of("barrel[facing=up,open=false]", 0x86643B) : Block.of("white_stained_glass", 0xF0F0F0));
            }
        }
        f.set(w - 1, level, 1, Furniture.COUNTER);
        f.set(w - 1, level, 2, Furniture.COUNTER);
        f.set(w - 1, level, d - 1, Furniture.FRIDGE);
        f.set(w - 1, level + 1, d - 1, Furniture.FRIDGE);
    }

    private void bookstore(Frame f, int w, int d, int level) {
        for (int a = 0; a < w; a++) {
            f.set(a, level, d - 1, Furniture.BOOKSHELF);
            f.set(a, level + 1, d - 1, Furniture.BOOKSHELF);
        }
        for (int a = 2; a < w - 2; a += 4) {
            f.fill(a, level, 2, a + 1, level, d - 3, Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430));
        }
        f.set(0, level, 0, Furniture.COUNTER);
    }

    /** 동아리방: 가운데 탁자와 의자, 벽 선반, 가끔 악기 */
    private void club(Frame f, int w, int d, int level, int k, int n) {
        String[] clubs = {"밴드 동아리", "등산부", "사진 동아리", "토론 동아리", "봉사 동아리", "바둑부", "합창단", "영화 동아리", "학회실", "총학생회"};
        String name = clubs[Math.floorMod(n * 3 + k, clubs.length)];
        Kit.roomDoor(f, w / 2, level, "oak", name);
        Furniture.table(f, 1, level, d / 2, Math.max(1, w - 2), 1, "spruce");
        for (int b = 0; b < d; b += 1) {
            if (f.empty(0, level, b) && b >= d - 2) {
                f.set(0, level, b, Furniture.BOOKSHELF);
            }
        }
        if (name.equals("밴드 동아리") || name.equals("합창단")) {
            f.set(w - 1, level, d - 1, Block.of("note_block", 0x58342D));
            f.set(w - 2, level, d - 1, Block.of("note_block", 0x58342D));
        }
    }

    /** 기숙사 2인실: 침대 둘, 창가 책상 둘, 옷장 둘 */
    private void dormRoom(Frame f, int w, int d, int level, int k, int n) {
        Interior.floor(f, 0, 0, w - 1, d - 1, level, Rooms.MARU_LIGHT);
        Kit.roomDoor(f, w / 2, level, "oak", (k + 1) + String.format("%02d", n + 1) + "호");
        Furniture.bed(f, r, 0, level, d - 1, "north");
        Furniture.bed(f, r, w - 1, level, d - 1, "north");
        if (w >= 4) {
            f.set(1, level, d - 1, Furniture.DESK_TOP);
            f.set(w - 2, level, d - 1, Furniture.DESK_TOP);
            f.set(1, level + 1, d - 1, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            Furniture.chair(f, 1, level, d - 2, "south", "oak");
            Furniture.chair(f, w - 2, level, d - 2, "south", "oak");
        }
        f.set(0, level, 0, Rooms.WARDROBE);
        f.set(0, level + 1, 0, Rooms.WARDROBE);
        f.set(w - 1, level, 0, Rooms.WARDROBE);
        f.set(w - 1, level + 1, 0, Rooms.WARDROBE);
    }

    private void lounge(Frame f, int w, int d, int level) {
        Furniture.sofa(f, r, 1, level, d - 1, Math.min(4, w - 2), "north");
        f.fill(1, level, d - 3, 3, level, d - 3, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        f.set(w - 1, level + 1, d / 2, Furniture.TV);
        Furniture.table(f, w - 4, level, 2, 2, 1, "birch");
    }

    private void laundry(Frame f, int w, int d, int level) {
        for (int a = 0; a < w; a++) {
            f.set(a, level, d - 1, IRON_BLOCK);
            f.set(a, level + 1, d - 1, a % 2 == 0 ? IRON_BLOCK : Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        }
        f.fill(1, level, d / 2, w - 2, level, d / 2, Furniture.WHITE_TOP);
    }

    /** 실험실: 가운데 실험대 줄, 벽 따라 흄후드와 개수대, 장비 */
    private void lab(Frame f, int w, int d, int level, boolean instruments) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, Block.of("white_concrete", 0xCFD5D6));
        for (int a = 0; a < w; a++) {
            f.set(a, level, d - 1, a % 4 == 0 ? CAULDRON : Furniture.COUNTER);
            if (a % 4 == 2) {
                f.set(a, level + 1, d - 1, Block.of("glass", 0xC8DCE4)); // 흄후드 유리
            }
        }
        for (int b = 2; b < d - 2; b += 3) {
            f.fill(1, level, b, Math.max(1, w - 3), level, b, Furniture.WHITE_TOP);
            for (int a = 1; a < w - 3; a += 3) {
                if (instruments) {
                    f.set(a, level + 1, b, IRON_BLOCK);
                }
                Furniture.chair(f, a + 1, level, b + 1, "south", "dark_oak");
            }
        }
        f.set(0, level, 0, IRON_BLOCK);
        f.set(0, level + 1, 0, Kit.MONITOR);
    }

    // ------------------------------------------------------------------ 로비·바깥

    private void lobby(int level, int h) {
        int top = level + h - 2;
        // 로비: 남쪽 줄 가운데 (mid-4..mid+4), 복도 벽을 틈
        v.fill(mid - 4, level, c0 + 3, mid + 4, top, c0 + 3, AIR);
        v.fill(mid - 4, level - 1, c0 + 3, mid + 4, level - 1, z1 - 1, POLISHED_DIORITE);
        v.set(mid - 4, top, z1 - 2, Interior.LIGHT);
        v.set(mid + 4, top, z1 - 2, Interior.LIGHT);
        v.set(mid, top, c0 + 5, Interior.LIGHT);
        // 층 안내판
        String[] dir = switch (s.kind) {
            case LECTURE -> new String[]{s.name, "1층 학과 사무실", "2~" + s.floors + "층 강의실", "교수 연구실"};
            case ADMIN -> new String[]{s.name, "1층 민원실", s.floors + "층 총장실", "대회의실"};
            case LIBRARY -> new String[]{s.name, "1층 대출·반납", "2층~ 열람실", "자료실"};
            case UNION -> new String[]{s.name, "1층 학생식당", "2층 서점·은행", "3층~ 동아리방"};
            case DORM -> new String[]{s.name, "1층 관리사무실", "휴게실·세탁실", "편의점"};
            case LAB -> new String[]{s.name, "1층 공동기기실", "2층~ 실험실", "연구실"};
        };
        v.set(mid - 3, level + 1, c0 + 2, null);
        if (v.get(mid + 4, level + 1, c0 + 4) == null) {
            v.set(mid + 4, level + 1, c0 + 4, Blocks.wallSign("dark_oak", "west", "white", false, dir));
        }
        if (s.kind == Kind.LIBRARY) {
            // 대출·반납 데스크와 출입 게이트
            for (int i = mid - 3; i <= mid + 3; i++) {
                v.set(i, level, c0 + 6, i == mid ? AIR : Furniture.COUNTER);
            }
            v.set(mid - 2, level + 1, c0 + 6, Kit.MONITOR);
            v.set(mid + 2, level + 1, c0 + 6, Kit.MONITOR);
            v.set(mid, top, c0 + 6, Blocks.hangingSign("birch", 0, "black", false, "대출·반납"));
        } else {
            Furniture.plant(Frame.of(v), r, mid - 4, level, z1 - 1);
            Furniture.plant(Frame.of(v), r, mid + 4, level, z1 - 1);
        }
    }

    private void facade() {
        Block wall = wallBlock();
        Block glass = s.skin == Skin.CONCRETE_FINS ? DARK_GLASS : CLEAR_GLASS;
        Block band = s.skin == Skin.BRICK ? CONCRETE_LIGHT : s.skin == Skin.GLASS ? WHITE_CONCRETE : CONCRETE;
        for (int k = 0; k < s.floors; k++) {
            int level = levels[k], top = levels[k + 1] - 2, h = levels[k + 1] - level;
            for (int j = z0; j <= z1; j++) {
                for (int i = x0; i <= x1; i++) {
                    boolean ns = j == z0 || j == z1, ew = i == x0 || i == x1;
                    if (!ns && !ew) {
                        continue;
                    }
                    if (ns && ew) {
                        v.fill(i, level, j, i, top, j, s.skin == Skin.BRICK ? BRICK : band);
                        v.set(i, top + 1, j, band);
                        continue;
                    }
                    int along = ns ? i : j;
                    boolean core = ns && j == z0 && i <= serviceEnd() && false;
                    for (int y = level; y <= top; y++) {
                        Block b = wall;
                        switch (s.skin) {
                            case CONCRETE_FINS -> {
                                boolean win = y >= level + 1 && y <= top && along % 3 != 0;
                                if (ew) {
                                    win = y >= level + 1 && y <= level + 2 && along % 4 == 2;
                                }
                                b = win ? glass : CONCRETE;
                            }
                            case BRICK -> {
                                boolean win = y >= level + 1 && y <= Math.min(top, level + 2 + (h > 4 ? 1 : 0))
                                        && (ns ? window(j == z1, i) : Math.floorMod(along, 6) == 2);
                                b = win ? glass : (y == level && k == 0 ? BASE : BRICK);
                            }
                            case GLASS -> {
                                boolean post = along % 4 == 0;
                                b = post ? WHITE_CONCRETE : y == level ? band : glass;
                                if (ew && along % 4 != 2) {
                                    b = CONCRETE_LIGHT;
                                }
                            }
                        }
                        if (!core) {
                            v.set(i, y, j, b);
                        }
                    }
                    v.set(i, top + 1, j, band);
                }
            }
        }
        // 노출 콘크리트 세로 핀 (앞·뒤 면, 바닥부터 난간까지)
        if (s.skin == Skin.CONCRETE_FINS) {
            for (int i = x0; i <= x1; i += 3) {
                v.fill(i, 0, z1 + 1, i, roof + 1, z1 + 1, CONCRETE);
                v.fill(i, 0, z0 - 1, i, roof + 1, z0 - 1, CONCRETE);
            }
        }
        // 벽돌 건물: 창턱
        if (s.skin == Skin.BRICK) {
            for (int k = 1; k < s.floors; k++) {
                for (int i = x0 + 1; i < x1; i++) {
                    if (window(true, i) && v.get(i, levels[k], z1 + 1) == null) {
                        v.set(i, levels[k], z1 + 1, Kit.TOP_SLAB);
                    }
                }
            }
        }
    }

    /** 벽돌 면의 창 자리: 위층 방 칸에 맞춤 (좁은 방은 방마다 두 칸, 넓은 방은 4칸마다 두 칸) */
    private boolean window(boolean south, int i) {
        int pitch = (south ? southWidth(1) : northWidth(1)) + 1;
        int off;
        if (south) {
            off = Math.floorMod(i - (x0 + 1), pitch);
        } else {
            if (i > northRoomsEnd() || i <= serviceEnd()) {
                return Math.floorMod(i, 4) == 1 || Math.floorMod(i, 4) == 2;
            }
            off = Math.floorMod(northRoomsEnd() - i, pitch);
        }
        if (pitch <= 7) {
            return off == 1 || off == 2;
        }
        return off != pitch - 1 && (Math.floorMod(off, 4) == 1 || Math.floorMod(off, 4) == 2);
    }

    /** 정면 현관: 양여닫이 유리문, 차양과 기둥, 학교 표지판, 계단 대신 경사로 없는 평지 출입 */
    private void entrance() {
        int canopyY = levels[1] - 1;
        Frame f = Frame.of(v);
        // 문 (가운데 두 짝 + 양옆 두 짝), 위는 유리
        for (int i = mid - 3; i <= mid + 3; i++) {
            v.fill(i, 0, z1, i, levels[1] - 2, z1, Block.of("light_blue_stained_glass_pane", 0x6699D8));
        }
        Kit.doubleDoor(f, mid - 1, 0, z1, "birch", "south");
        v.set(mid + 1, 0, z1, null);
        Kit.doubleDoor(f, mid - 3, 0, z1, "birch", "south");
        Kit.doubleDoor(f, mid + 1, 0, z1, "birch", "south");
        v.set(mid, 0, z1, Kit.door("birch", "south", false, true));
        // 차양 (앞으로 3칸)
        int depth = Math.min(3, v.d - 1 - z1);
        if (depth >= 2) {
            v.fill(mid - 5, canopyY, z1 + 1, mid + 5, canopyY, z1 + depth, s.skin == Skin.BRICK ? CONCRETE_LIGHT : CONCRETE);
            v.fill(mid - 5, 0, z1 + depth, mid - 5, canopyY - 1, z1 + depth, CONCRETE);
            v.fill(mid + 5, 0, z1 + depth, mid + 5, canopyY - 1, z1 + depth, CONCRETE);
            v.set(mid, canopyY, z1 + 2, Interior.LIGHT);
            v.fill(mid - 4, -1, z1 + 1, mid + 4, -1, z1 + depth, POLISHED_ANDESITE);
        }
        // 학교 표지판 (현관 옆 벽): 동 번호는 관악 캠퍼스처럼
        int si = mid + 7;
        if (v.get(si, 1, z1 + 1) == null) {
            v.set(si, 1, z1 + 1, Blocks.wallSign("dark_oak", "south", "white", false, "서울대학교", s.name, s.number));
        }
        // 동 번호 판 (모서리 높이, 파란 판에 흰 글씨)
        int ni = x1 - 1, ny = Math.min(roof - 2, levels[Math.min(2, s.floors)] + 1);
        v.set(ni, ny, z1 + 1, Blocks.wallSign("warped", "south", "white", true, "", s.number));
    }

    private void roofTop() {
        for (int i = x0; i <= x1; i++) {
            v.set(i, roof, z0, wallBlock());
            v.set(i, roof, z1, wallBlock());
            v.set(i, roof + 1, z0, Kit.SLAB);
            v.set(i, roof + 1, z1, Kit.SLAB);
        }
        for (int j = z0; j <= z1; j++) {
            v.set(x0, roof, j, wallBlock());
            v.set(x1, roof, j, wallBlock());
            v.set(x0, roof + 1, j, Kit.SLAB);
            v.set(x1, roof + 1, j, Kit.SLAB);
        }
        // 실외기 줄
        for (int i = serviceEnd() + 4; i <= northRoomsEnd() - 4; i += 5) {
            v.fill(i, roof, c0 + 5, i + 1, roof, c0 + 5, Block.of("light_gray_concrete", 0x7D7D73));
            v.set(i, roof + 1, c0 + 5, Block.of("iron_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
        }
    }
}
