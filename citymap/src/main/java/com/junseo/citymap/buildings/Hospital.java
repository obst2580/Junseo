package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 대학병원 (의료국 거점이자 되살아나는 곳): 준서대학교병원.
 * <ul>
 *   <li>4층 진료동(포디움, 약 93×53m) 위 뒤쪽에 10층 병동 타워(약 69×30m)가 올라간 모두 14층 건물. 흰 띠와 푸른 유리,
 *       진료동은 밝은 돌 마감에 띠창. 병동 타워 옥상의 기계실 위에 높인 헬기장(노란 원과 H, 안전 난간, 오르는 철 계단)</li>
 *   <li>1층: 가운데 정문 로비(안내 데스크, 원무과 접수·수납 창구, 대기 의자 줄, 약제부 「약 받는 곳」, 카페, 기둥),
 *       정문 앞 큰 차양. 한쪽은 응급실(구급차 진입로와 빨간 띠 차양, 「응급실」 표지판, 구급차 대는 칸(비워 둠), 분류소,
 *       보호자 대기 의자, 커튼 친 침상 줄, 가운데 간호 데스크, 소생실, 격리실), 다른 쪽은 영상의학과(X선·CT·MRI·초음파)</li>
 *   <li>2층 외래 진료실(과별 진료실, 처치실·채혈실, 복도 대기 의자), 3층 수술실·회복실·소독실,
 *       4층 중환자실·진단검사의학과, 5~14층 병동(4인실, 간호사실, 휴게실), 5층에서 나가는 진료동 옥상 정원</li>
 *   <li>가운데 코어: 꺾인 계단(옥상까지 걸어서), 엘리베이터 4대, 층마다 남녀 화장실</li>
 * </ul>
 * 건물 기준 정면은 남쪽(j = d-1). 앞 {@link #FRONT} 줄은 차양과 차로 자리입니다. 층고는 {@link Floors} 기준
 * (1층 5칸, 위층 4칸).
 */
final class Hospital {
    static final String NAME = "준서대학교병원";
    static final int FLOORS = 14, PODIUM = 4, FRONT = 10;
    /** 진료동 줄 (j): 뒷벽 2, 뒤 방 3..8, 북쪽 복도 10..11, 코어 12..19, 큰 복도 20..22, 방 줄 24..31·33..40, 복도 42..44, 앞 방 46..53 */
    static final int BACK = 2, CN = 10, CA = 20, R1 = 24, R2 = 33, CC = 42, R3 = 46, FACE = 54;
    /** 병동 타워 뒤·앞 벽 (j) */
    static final int TJ1 = 31;
    static final int MIN_W = 80;

    static final Block CLADDING = POLISHED_DIORITE;
    static final Block WHITE = WHITE_CONCRETE;
    static final Block FLOOR = Block.of("white_concrete", 0xCFD5D6);
    static final Block VINYL = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block LOBBY_FLOOR = POLISHED_DIORITE;
    static final Block WALL = Interior.INNER_WALL;
    static final Block GLASS_WALL = Kit.WHITE_PANE;
    private static final String[] DEPTS_A = {"내과", "외과", "소아청소년과", "정형외과"};
    private static final String[] DEPTS_B = {"신경과", "산부인과", "피부과", "안과", "이비인후과", "비뇨의학과"};

    private final int w, d;
    private final Random r;
    private final int ti0, ti1, mid;
    private Tower t;

    private Hospital(int w, int d, Random r) {
        this.w = w;
        this.d = d;
        this.r = r;
        ti0 = 14;
        ti1 = w - 15;
        mid = w / 2;
    }

    static int depth() {
        return FACE + 1 + FRONT;
    }

    static Voxels build(int w, int d, Random r) {
        Hospital h = new Hospital(w, d, r);
        Tower.Spec s = new Tower.Spec(w, d, FLOORS);
        s.lobbyH = Floors.GROUND;
        s.typicalH = Floors.OFFICE;
        s.shape = (i, j, k) -> k < PODIUM
                ? i >= 2 && i <= w - 3 && j >= BACK && j <= FACE
                : i >= h.ti0 && i <= h.ti1 && j >= BACK && j <= TJ1;
        s.use = k -> Tower.Use.CUSTOM;
        s.custom = (tw, k, level, height) -> {
            h.t = tw;
            h.floor(k, level, height);
        };
        s.glass = Block.of("light_blue_stained_glass", 0x6699D8);
        s.mullion = WHITE;
        s.spandrel = WHITE;
        s.podium = LOBBY_FLOOR;
        s.mullionEvery = 4;
        s.elevators = 4;
        s.restrooms = 2;
        s.extraTop = 14;
        Tower tw = Tower.build(s, new Random(r.nextLong()));
        h.t = tw;
        h.exterior();
        h.front();
        h.roofGarden();
        h.helipad();
        // 상자 가장자리 바닥 (건물 둘레 보도)
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                tw.v.setIfEmpty(i, -1, j, LIGHT_GRAY_CONCRETE);
            }
        }
        tw.v.connect();
        return tw.v;
    }

    // ------------------------------------------------------------------ 층

    private void floor(int k, int level, int h) {
        if (k >= PODIUM) {
            ward(k, level, h);
            return;
        }
        Voxels v = t.v;
        int top = level + h - 2;
        // 바닥 마감 (진료동 전체)
        finish(3, BACK + 1, w - 4, FACE - 1, level, k == 0 ? LOBBY_FLOOR : VINYL);
        // 북쪽 복도·큰 복도 등
        for (int i = 5; i <= w - 5; i += 6) {
            v.set(i, top, CN + 1, Interior.LIGHT);
            v.set(i, top, CA + 1, Interior.LIGHT);
            v.set(i, top, CC + 1, Interior.LIGHT);
        }
        backRooms(k, level, h);
        midRooms(k, level, h);
        switch (k) {
            case 0 -> {
                lobby(level, h);
                emergency(level, h);
                grid(level, h, t.ci1 + 3, w - 4, k);
            }
            default -> grid(level, h, 3, w - 4, k);
        }
    }

    /** 뒤 방 줄 (j 3..8, 북쪽 복도 j 10..11 로 문) */
    private void backRooms(int k, int level, int h) {
        Frame f = Frame.facing(t.v, w - 4, CN - 2, "north");
        String[][] names = {
                {"원무과 사무실", "의무기록실", "직원 휴게실", "보안실", "물품 창고", "전기실"},
                {"채혈실", "주사실", "처치실", "의국", "물품 창고", "의료 상담실"},
                {"남자 탈의실", "여자 탈의실", "의국", "중앙 공급실", "물품 창고", "마취과"},
                {"보호자 대기실", "면회 상담실", "의국", "당직실", "물품 창고", "검체 보관실"}};
        Kit.strip(f, 0, w - 7, 6, level, h, 13, WALL, false, (rf, rw, rd, n) -> {
            String name = names[k][n % names[k].length];
            Kit.roomDoor(rf, rw / 2, level, "pale_oak", name);
            if (name.contains("창고") || name.contains("전기") || name.contains("공급") || name.contains("보관")) {
                shelves(rf, rw, rd, level);
            } else if (name.contains("탈의") || name.contains("당직")) {
                lockers(rf, rw, rd, level);
            } else if (name.contains("대기") || name.contains("휴게")) {
                lounge(rf, rw, rd, level);
            } else {
                offices(rf, rw, rd, level);
            }
            Interior.lights(rf, 0, 0, rw - 1, rd - 1, level, h, 5, Interior.LIGHT);
        });
    }

    /** 코어 양옆 방 (j 13..18, 큰 복도로 문) */
    private void midRooms(int k, int level, int h) {
        int west1 = t.ci0 - 4, east0 = t.ci1 + 4;
        String[] names = {"의국", "회의실", "교수 연구실", "간호부"};
        if (k > 0) {
            // 응급실이 1층 서쪽을 다 씀
            Frame fw = Frame.facing(t.v, west1, CA - 2, "north");
            Kit.strip(fw, 0, west1 - 3, 6, level, h, 10, WALL, true, (rf, rw, rd, n) -> {
                Kit.roomDoor(rf, rw - 2, level, "pale_oak", names[(k + n) % names.length]);
                offices(rf, rw, rd, level);
                Interior.lights(rf, 0, 0, rw - 1, rd - 1, level, h, 5, Interior.LIGHT);
            });
        }
        Frame fe = Frame.facing(t.v, w - 4, CA - 2, "north");
        Kit.strip(fe, 0, w - 4 - east0, 6, level, h, 10, WALL, true, (rf, rw, rd, n) -> {
            String name = k == 0 ? (n == 0 ? "판독실" : "영상의학과 사무실") : names[(k + n + 1) % names.length];
            Kit.roomDoor(rf, 1, level, "pale_oak", name);
            offices(rf, rw, rd, level);
            Interior.lights(rf, 0, 0, rw - 1, rd - 1, level, h, 5, Interior.LIGHT);
        });
    }

    /**
     * 기본 칸 나누기 (i0..i1): 방 줄 1(j 24..31, 큰 복도로 문)·방 줄 2(j 33..40, 앞 복도로 문)·앞 방 줄(j 46..53),
     * 사이 세로 복도 3칸.
     */
    private void grid(int level, int h, int i0, int i1, int k) {
        Voxels v = t.v;
        // 세로 복도 시작 i: 서쪽 끝, 가운데, 동쪽 끝 (i0..i1 안에 드는 것만)
        int[] cross = {3, mid - 1, w - 6};
        java.util.List<int[]> segs = new java.util.ArrayList<>();
        int a = i0;
        for (int c : cross) {
            if (c < i0 - 2 || c > i1) {
                continue;
            }
            if (c - 2 >= a + 3) {
                segs.add(new int[]{a, c - 2});
            }
            a = Math.max(a, c + 4);
        }
        if (i1 - a >= 3) {
            segs.add(new int[]{a, i1});
        }
        int si = 0;
        for (int[] s : segs) {
            final int segNo = si++;
            // 방 줄 1: +b 가 남쪽
            Frame f1 = Frame.facing(v, s[0], R1, "south");
            row(f1, s[1] - s[0], R2 - R1 - 1, level, h, k, 1, segNo, true);
            // 방 줄 2: +b 가 북쪽 (앞 복도 쪽 문)
            Frame f2 = Frame.facing(v, s[1], CC - 2, "north");
            row(f2, s[1] - s[0], CC - 2 - R2 + 1, level, h, k, 2, segNo, true);
        }
        // 앞 방 줄 (창가)
        Frame f3 = Frame.facing(v, i0, R3, "south");
        row(f3, i1 - i0, FACE - R3, level, h, k, 3, 0, false);
        // 복도 대기 의자 (외래층)
        if (k == 1) {
            for (int[] s : segs) {
                for (int i = s[0] + 1; i <= s[1] - 1; i++) {
                    if (i % 6 == 0 || i % 6 == 3) {
                        continue;
                    }
                    if (v.get(i, level, CC) == null) {
                        v.set(i, level, CC, Block.of("polished_andesite_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x848685));
                    }
                }
            }
        }
    }

    /** 방 줄 하나 (len = 안쪽 a 범위 끝, depth 깊이). which: 1, 2, 3 */
    private void row(Frame f, int len, int depth, int level, int h, int k, int which, int seg, boolean back) {
        int roomW = switch (k) {
            case 0 -> which == 3 ? 10 : 8;
            case 1 -> which == 3 ? 9 : 4;
            case 2 -> which == 3 ? 15 : 9;
            default -> which == 3 ? 10 : 40;
        };
        Kit.strip(f, 0, len, depth, level, h, roomW, WALL, back, (rf, rw, rd, n) -> {
            switch (k) {
                case 0 -> radiology(rf, rw, rd, level, h, which, n);
                case 1 -> {
                    if (which == 3) {
                        String dept = DEPTS_B[(n / 2) % DEPTS_B.length];
                        if (n % 2 == 0) {
                            Kit.roomDoor(rf, rw / 2, level, "pale_oak", dept);
                            waiting(rf, rw, rd, level, dept);
                        } else {
                            Kit.roomDoor(rf, rw - 2, level, "pale_oak", dept + " 진료실");
                            consult(rf, rw, rd, level);
                        }
                    } else {
                        String dept = DEPTS_A[(which - 1) * 2 + seg % 2];
                        Kit.roomDoor(rf, rw - 1, level, "pale_oak", dept + " " + (n + 1) + "진료");
                        consult(rf, rw, rd, level);
                    }
                }
                case 2 -> {
                    if (which == 3) {
                        Kit.roomDoor(rf, rw / 2, level, "pale_oak", n % 2 == 0 ? "회복실" : "수술 보호자 대기");
                        if (n % 2 == 0) {
                            beds(rf, rw, rd, level, true);
                        } else {
                            waiting(rf, rw, rd, level, null);
                        }
                    } else {
                        int no = (which - 1) * 8 + seg * 4 + n + 1;
                        Kit.doubleDoor(rf, rw / 2 - 1, level, -1, "pale_oak", "south");
                        label(rf, rw / 2 + 1, level, which == 2 && n % 4 == 3 ? "소독실" : "수술실 " + no);
                        if (which == 2 && n % 4 == 3) {
                            shelves(rf, rw, rd, level);
                        } else {
                            operating(rf, rw, rd, level, h);
                        }
                    }
                }
                default -> {
                    if (which == 3) {
                        Kit.roomDoor(rf, rw / 2, level, "pale_oak", n % 3 == 2 ? "채혈 대기실" : "진단검사의학과");
                        if (n % 3 == 2) {
                            waiting(rf, rw, rd, level, null);
                        } else {
                            lab(rf, rw, rd, level);
                        }
                    } else {
                        Kit.doubleDoor(rf, 2, level, -1, "pale_oak", "south");
                        label(rf, 4, level, which == 1 ? "내과계 중환자실" : "외과계 중환자실");
                        icu(rf, rw, rd, level);
                    }
                }
            }
            if (k != 2 || which == 3) {
                Interior.lights(rf, 0, 0, rw - 1, rd - 1, level, h, 4, Interior.LIGHT);
            }
        });
    }

    /** 바닥 마감 (코어 계단·승강로 자리는 건드리지 않음) */
    private void finish(int i0, int j0, int i1, int j1, int level, Block b) {
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (i < t.ci0 || i > t.ci1 || j < t.cj0 || j > t.cj1) {
                    t.v.set(i, level - 1, j, b);
                }
            }
        }
    }

    private static void label(Frame f, int a, int level, String text) {
        if (f.empty(a, level + 1, -2)) {
            f.set(a, level + 1, -2, Blocks.wallSign("birch", "north", "black", false, "", text));
        }
    }

    // ------------------------------------------------------------------ 1층

    /** 정문 로비 (코어 앞 i ci0-2..ci1+2, j 20..53) */
    private void lobby(int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        int i0 = t.ci0 - 2, i1 = t.ci1 + 2, top = level + h - 2;
        // 응급실·영상의학과와 나누는 벽 (복도 쪽 문)
        v.fill(i0 - 1, level, CA - 1 + 1, i0 - 1, top, FACE - 1, WALL);
        v.fill(i1 + 1, level, CA + 3, i1 + 1, top, FACE - 1, WALL);
        Interior.door(Frame.facing(v, i0 - 1, CA + 1, "west"), 0, level, 0, "pale_oak", "south");
        // 바닥: 밝은 돌, 가운데 길은 짙은 띠
        v.fill(i0, level - 1, CA, i1, level - 1, FACE - 1, LOBBY_FLOOR);
        for (int j = CA + 4; j < FACE; j += 8) {
            v.fill(i0, level - 1, j, i1, level - 1, j, POLISHED_ANDESITE);
        }
        // 기둥
        for (int j = CA + 8; j < FACE - 3; j += 8) {
            for (int i = i0 + 5; i <= i1 - 5; i += 9) {
                if (Math.abs(i - mid) > 3) {
                    v.fill(i, level, j, i, top, j, WHITE);
                }
            }
        }
        // 원무과 접수·수납 창구 (서쪽 벽을 따라, 동쪽을 봄)
        int ca = i0 + 3;
        for (int j = CA + 6; j <= FACE - 9; j++) {
            v.set(ca, level, j, Furniture.COUNTER);
            v.set(ca, level + 1, j, j % 3 == 0 ? GLASS_WALL : AIR);
            if (j % 3 == 1) {
                Furniture.desk(Frame.facing(v, ca - 1, j, "west"), 0, level, 0, "south");
            }
        }
        v.set(ca, top, CA + 8, Blocks.hangingSign("birch", 12, "black", false, "원무과", "접수", "수납"));
        v.set(ca, top, FACE - 12, Blocks.hangingSign("birch", 12, "black", false, "원무과", "입퇴원 수속"));
        // 약제부 (동쪽 벽, 서쪽을 봄)
        int pa = i1 - 3;
        for (int j = CA + 6; j <= FACE - 9; j++) {
            v.set(pa, level, j, Furniture.COUNTER);
            v.set(i1, level, j, j % 2 == 0 ? Furniture.COUNTER : CAULDRON);
            v.set(i1, level + 1, j, Block.of("white_terracotta", 0xD1B2A1));
            v.set(i1, level + 2, j, Block.of("iron_trapdoor[facing=west,half=top,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        }
        for (int j = CA + 7; j <= FACE - 10; j += 4) {
            Furniture.desk(Frame.facing(v, pa + 1, j, "east"), 0, level, 0, "south");
        }
        v.set(pa, top, CA + 8, Blocks.hangingSign("birch", 4, "black", false, "약제부", "약 받는 곳"));
        // 대기 의자: 서쪽 절반은 원무과를, 동쪽 절반은 약제부를 봄
        for (int j = CA + 7; j <= FACE - 11; j += 3) {
            for (int i = ca + 4; i <= pa - 4; i++) {
                if (Math.abs(i - mid) <= 2 || v.get(i, level, j) != null) {
                    continue;
                }
                String back = i < mid ? "east" : "west";
                v.set(i, level, j, Block.of("polished_andesite_stairs[facing=" + back + ",half=bottom,shape=straight,waterlogged=false]", 0x848685));
            }
        }
        // 안내 데스크 (정문 안쪽)
        for (int i = mid - 3; i <= mid + 3; i++) {
            v.set(i, level, FACE - 6, Furniture.COUNTER);
        }
        Furniture.chair(f, mid - 1, level, FACE - 7, "north", "dark_oak");
        Furniture.chair(f, mid + 1, level, FACE - 7, "north", "dark_oak");
        v.set(mid, top, FACE - 6, Blocks.hangingSign("birch", 0, "black", false, "안내", NAME));
        // 카페 (정문 동쪽 구석)
        int c0 = mid + 8;
        for (int i = c0; i <= c0 + 6; i++) {
            v.set(i, level, FACE - 5, Furniture.COUNTER);
        }
        v.set(c0 + 6, level + 1, FACE - 5, Block.of("smoker[facing=south,lit=false]", 0x555451));
        v.set(c0 + 3, top, FACE - 5, Blocks.hangingSign("spruce", 0, "white", true, "카페"));
        for (int i = c0; i <= c0 + 6; i += 3) {
            Furniture.table(f, i, level, FACE - 2, 1, 1, "spruce");
        }
        // 화분과 등
        Furniture.plant(f, r, mid - 4, level, FACE - 1);
        Furniture.plant(f, r, mid + 4, level, FACE - 1);
        Interior.lights(f, i0, CA, i1, FACE - 1, level, h, 5, Interior.LIGHT);
        // 정문 안쪽 층 안내판
        v.set(mid, level + 1, CA - 1 + 0, null);
    }

    /** 응급실 (서쪽 i 3..ci0-3, j 13..53). 구급차 입구는 앞벽 */
    private void emergency(int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        int i0 = 3, i1 = t.ci0 - 3, top = level + h - 2;
        int j0 = CN + 3;
        // 둘레 벽: 북쪽(j0-1), 동쪽(i1+1 = 로비·코어 둘레 복도와 사이)
        v.fill(i0, level, j0 - 1, i1 + 1, top, j0 - 1, WALL);
        v.fill(i1 + 1, level, j0 - 1, i1 + 1, top, FACE - 1, WALL);
        // 복도와 이어지는 문 (직원·환자 이송)
        Interior.door(Frame.facing(v, i1 + 1, CA + 1, "east"), 0, level, 0, "pale_oak", "south");
        Interior.door(f, i0 + 6, level, j0 - 1, "pale_oak", "north");
        v.fill(i0, level - 1, j0, i1, level - 1, FACE - 1, Block.of("white_concrete", 0xCFD5D6));
        // 소생실 (뒤 서쪽, 유리 벽)
        int rj = j0 + 8;
        v.fill(i0, level, rj, i0 + 10, top, rj, GLASS_WALL);
        v.fill(i0 + 11, level, j0, i0 + 11, top, rj, WALL);
        Kit.doubleDoor(f, i0 + 4, level, rj, "pale_oak", "south");
        v.set(i0 + 7, level + 2, rj + 1, Blocks.wallSign("birch", "south", "red", false, "", "소생실"));
        for (int a = i0 + 1; a <= i0 + 8; a += 5) {
            v.fill(a, level, j0 + 3, a + 1, level, j0 + 3, Block.of("white_wool", 0xE9ECEC));
            v.set(a, level + 1, j0 + 1, Kit.MONITOR);
            v.set(a, level, j0 + 1, IRON_BLOCK);
            v.set(a + 1, level, j0 + 1, Block.of("light_gray_concrete", 0x7D7D73));
            v.set(a, top, j0 + 3, Interior.LIGHT);
        }
        // 격리실 두 칸 (뒤 동쪽)
        for (int n = 0; n < 2; n++) {
            int a0 = i0 + 12 + n * 5;
            if (a0 + 4 > i1) {
                break;
            }
            v.fill(a0 + 4, level, j0, a0 + 4, top, rj, WALL);
            v.fill(a0, level, rj, a0 + 4, top, rj, WALL);
            Interior.door(f, a0 + 2, level, rj, "pale_oak", "south");
            v.set(a0 + 1, level, j0 + 1, Block.of("white_wool", 0xE9ECEC));
            v.set(a0 + 1, level, j0 + 2, Block.of("white_wool", 0xE9ECEC));
            v.set(a0 + 3, level, j0, CAULDRON);
            v.set(a0 + 2, top, j0 + 2, Interior.LIGHT);
            v.set(a0 + 3, level + 2, rj + 1, Blocks.wallSign("birch", "south", "black", false, "", "격리실 " + (n + 1)));
        }
        // 침상 줄: 서쪽 벽과 동쪽 벽을 따라, 사이 커튼
        for (int j = rj + 3; j <= FACE - 10; j += 3) {
            for (int side = 0; side < 2; side++) {
                int a = side == 0 ? i0 : i1 - 1;
                v.fill(a, level, j, a + 1, level, j, Block.of("white_wool", 0xE9ECEC));
                v.set(side == 0 ? a : a + 1, level + 1, j, Block.of("white_carpet", 0xE9ECEC));
                v.set(side == 0 ? a - 0 : a + 1, level + 1, j + 1, Kit.MONITOR);
                v.fill(a, level, j + 1, a + 1, level + 1, j + 1, Kit.CURTAIN);
                v.set(side == 0 ? a : a + 1, level + 1, j + 1, Kit.MONITOR);
            }
        }
        // 가운데 간호 데스크
        int nc = (i0 + i1) / 2, nj = (rj + FACE - 10) / 2;
        for (int i = nc - 3; i <= nc + 3; i++) {
            v.set(i, level, nj - 2, Furniture.COUNTER);
            v.set(i, level, nj + 2, i == nc ? AIR : Furniture.COUNTER);
        }
        for (int j = nj - 2; j <= nj + 2; j++) {
            v.set(nc - 3, level, j, Furniture.COUNTER);
            v.set(nc + 3, level, j, Furniture.COUNTER);
        }
        for (int i = nc - 2; i <= nc + 2; i += 2) {
            v.set(i, level + 1, nj - 2, Kit.MONITOR);
            Furniture.chair(f, i, level, nj - 1, "south", "dark_oak");
        }
        v.set(nc, top, nj, Blocks.hangingSign("birch", 0, "black", false, "응급실", "간호사 데스크"));
        // 앞: 분류소 창구와 보호자 대기 의자
        int fj = FACE - 6;
        for (int i = i0 + 9; i <= i0 + 13; i++) {
            v.set(i, level, fj, Furniture.COUNTER);
        }
        Furniture.chair(f, i0 + 11, level, fj - 1, "north", "dark_oak");
        v.set(i0 + 11, top, fj, Blocks.hangingSign("birch", 0, "black", false, "응급실", "환자 분류소"));
        for (int j = FACE - 4; j <= FACE - 2; j += 2) {
            for (int i = i0 + 15; i <= i1; i++) {
                v.set(i, level, j, Block.of("polished_andesite_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", 0x848685));
            }
        }
        Interior.lights(f, i0, j0, i1, FACE - 1, level, h, 4, Interior.LIGHT);
    }

    /** 영상의학과 방 (1층 동쪽 칸 나누기) */
    private void radiology(Frame f, int w, int d, int level, int h, int which, int n) {
        String[] names = which == 1 ? new String[]{"일반촬영실 1", "일반촬영실 2"} : which == 2 ? new String[]{"CT실", "MRI실"}
                : new String[]{"영상의학과 접수", "초음파실"};
        String name = names[n % 2];
        Kit.roomDoor(f, w - 2, level, "pale_oak", name);
        Kit.finish(f, 0, 0, w - 1, d - 1, level, Block.of("light_gray_concrete", 0x7D7D73));
        int cb = d / 2;
        if (name.startsWith("CT")) {
            // 둥근 갠트리 (가운데 구멍) 와 눕는 침대
            f.fill(1, level, cb - 1, 1, level + 2, cb + 1, WHITE);
            f.set(1, level + 1, cb, AIR);
            for (int a = 1; a <= Math.min(w - 2, 5); a++) {
                f.set(a, level, cb, Kit.QUARTZ_TOP);
            }
            f.set(1, level + 1, cb, Kit.QUARTZ_TOP);
        } else if (name.startsWith("MRI")) {
            f.fill(0, level, cb - 2, 2, level + 2, cb + 2, WHITE);
            f.set(1, level + 1, cb, AIR);
            f.set(2, level + 1, cb, AIR);
            for (int a = 1; a <= Math.min(w - 2, 6); a++) {
                f.set(a, level, cb, Kit.QUARTZ_TOP);
            }
        } else if (name.contains("촬영")) {
            f.fill(1, level, cb, 4, level, cb, Kit.QUARTZ_TOP);
            f.fill(0, level, cb - 2, 0, level + 2, cb - 2, IRON_BLOCK);
            f.set(1, level + 2, cb, IRON_BLOCK);
        } else if (name.contains("초음파")) {
            f.fill(1, level, 1, 2, level, 1, Block.of("white_wool", 0xE9ECEC));
            f.set(3, level, 1, IRON_BLOCK);
            f.set(3, level + 1, 1, Kit.MONITOR);
            Furniture.chair(f, 4, level, 2, "east", "dark_oak");
        } else {
            for (int a = 1; a < w - 1; a++) {
                f.set(a, level, 1, Furniture.COUNTER);
            }
            Kit.seats(f, 1, w - 2, level, d - 2, "north", "polished_andesite");
            Kit.seats(f, 1, w - 2, level, d - 4, "north", "polished_andesite");
        }
        // 조정실 책상 (문 쪽 구석)
        if (!name.contains("접수") && d >= 6 && w >= 6) {
            Furniture.desk(f, w - 1, level, d - 2, "west");
        }
    }

    // ------------------------------------------------------------------ 방 꾸미기

    /** 진료실: 의사 책상과 모니터, 환자 의자, 진찰대와 커튼, 세면대 */
    private void consult(Frame f, int w, int d, int level) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, Block.of("white_concrete", 0xCFD5D6));
        Furniture.desk(f, 1, level, 1, "south");
        if (w >= 3) {
            Furniture.chair(f, 2, level, 1, "east", "polished_andesite");
        }
        f.set(0, level, d - 2, Block.of("white_wool", 0xE9ECEC));
        f.set(0, level, d - 3, Block.of("white_wool", 0xE9ECEC));
        if (w >= 3) {
            f.fill(1, level, d - 4, 1, level + 1, d - 4, Kit.WHITE_PANE);
        }
        f.set(w - 1, level, d - 1, CAULDRON);
    }

    /** 대기실: 의자 줄과 벽걸이 TV, 접수 데스크 */
    private void waiting(Frame f, int w, int d, int level, String dept) {
        for (int b = 2; b < d - 1; b += 2) {
            Kit.seats(f, 1, w - 2, level, b, "south", "polished_andesite");
        }
        f.set(w / 2, level + 1, d, Kit.MONITOR);
        if (dept != null) {
            f.set(0, level, 0, Furniture.COUNTER);
            f.set(1, level, 0, Furniture.COUNTER);
        }
        Furniture.plant(f, r, w - 1, level, 0);
    }

    /** 침상 줄 (회복실·병실): 벽을 따라 침대, 사이 커튼, 침대 머리 쪽 모니터 */
    private void beds(Frame f, int w, int d, int level, boolean monitors) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, VINYL);
        for (int a = 0; a + 1 < w; a += 3) {
            f.set(a, level, d - 1, Block.of("white_wool", 0xE9ECEC));
            f.set(a, level, d - 2, Block.of("white_wool", 0xE9ECEC));
            f.set(a, level + 1, d - 1, Block.of("white_carpet", 0xE9ECEC));
            if (monitors) {
                f.set(a + 1, level + 1, d - 1, Kit.MONITOR);
                f.set(a + 1, level, d - 1, IRON_BLOCK);
            }
            if (a + 2 < w) {
                f.fill(a + 2, level, d - 3, a + 2, level + 1, d - 1, Kit.CURTAIN);
            }
        }
        f.set(w - 1, level, 0, Furniture.COUNTER);
    }

    /** 수술실: 가운데 수술대, 위 무영등, 마취기와 모니터, 기구대, 벽 수납장 */
    private void operating(Frame f, int w, int d, int level, int h) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, Block.of("light_blue_terracotta", 0x716C89));
        int ca = w / 2, cb = d / 2;
        Kit.finish(f, ca - 2, cb - 2, ca + 2, cb + 2, level, Block.of("white_concrete", 0xCFD5D6));
        for (int b = cb - 1; b <= cb + 1; b++) {
            f.set(ca, level, b, Kit.QUARTZ_TOP);
        }
        f.set(ca, level + h - 2, cb, Block.of("pearlescent_froglight[axis=y]", 0xEDDBDC));
        f.set(ca - 1, level + h - 2, cb - 1, Block.of("end_rod[facing=down]", 0xE8E2D8));
        f.set(ca + 1, level + h - 2, cb + 1, Block.of("end_rod[facing=down]", 0xE8E2D8));
        // 마취기 (머리 쪽)
        f.set(ca, level, cb - 3, IRON_BLOCK);
        f.set(ca, level + 1, cb - 3, Kit.MONITOR);
        f.set(ca + 1, level, cb - 3, Block.of("light_gray_concrete", 0x7D7D73));
        // 기구대
        f.set(ca + 2, level, cb + 1, Block.of("iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
        f.set(ca - 2, level, cb, Block.of("iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
        // 벽 수납장
        for (int a = 0; a < w; a++) {
            if (a != w / 2 - 1 && a != w / 2) {
                f.set(a, level, d - 1, Block.of("white_terracotta", 0xD1B2A1));
                f.set(a, level + 1, d - 1, Kit.WHITE_PANE);
            }
        }
        f.set(0, level + 1, cb, Kit.MONITOR);
        Interior.lights(f, 0, 0, w - 1, d - 1, level, h, 4, Interior.LIGHT);
    }

    /** 중환자실: 유리 칸막이 침상, 침상마다 모니터, 앞에 간호 데스크 */
    private void icu(Frame f, int w, int d, int level) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, VINYL);
        for (int a = 0; a + 2 < w; a += 4) {
            f.set(a + 1, level, d - 1, Block.of("white_wool", 0xE9ECEC));
            f.set(a + 1, level, d - 2, Block.of("white_wool", 0xE9ECEC));
            f.set(a + 1, level + 1, d - 1, Block.of("white_carpet", 0xE9ECEC));
            f.set(a + 2, level, d - 1, IRON_BLOCK);
            f.set(a + 2, level + 1, d - 1, Kit.MONITOR);
            f.fill(a + 3, level, d - 3, a + 3, level + 1, d - 1, Kit.WHITE_PANE);
        }
        // 간호 데스크 (문 쪽)
        for (int a = 5; a < Math.min(w - 2, 17); a++) {
            f.set(a, level, 2, Furniture.COUNTER);
            if (a % 3 == 0) {
                f.set(a, level + 1, 2, Kit.MONITOR);
                Furniture.chair(f, a, level, 1, "north", "dark_oak");
            }
        }
    }

    /** 검사실: 벽 따라 실험대와 분석 장비, 개수대 */
    private void lab(Frame f, int w, int d, int level) {
        Kit.finish(f, 0, 0, w - 1, d - 1, level, Block.of("white_concrete", 0xCFD5D6));
        for (int a = 0; a < w; a++) {
            f.set(a, level, d - 1, Furniture.COUNTER);
            if (a % 3 == 1) {
                f.set(a, level + 1, d - 1, a % 2 == 0 ? IRON_BLOCK : WHITE);
            }
        }
        for (int a = 1; a < w - 1; a++) {
            f.set(a, level, d / 2, Furniture.WHITE_TOP);
        }
        f.set(0, level, d / 2 - 2, CAULDRON);
        Furniture.chair(f, 2, level, d / 2 - 1, "north", "dark_oak");
        Furniture.chair(f, w - 3, level, d / 2 + 1, "south", "dark_oak");
    }

    /** 사무실: 책상 두 줄과 책장 */
    private void offices(Frame f, int w, int d, int level) {
        for (int a = 1; a < w - 1; a += 2) {
            if (d >= 5) {
                Furniture.desk(f, a, level, d - 2, "north");
            }
        }
        f.set(0, level, 1, Furniture.BOOKSHELF);
        f.set(0, level + 1, 1, Furniture.BOOKSHELF);
        Furniture.plant(f, r, w - 1, level, 0);
    }

    /** 창고: 벽 따라 선반 */
    private void shelves(Frame f, int w, int d, int level) {
        for (int a = 0; a < w; a++) {
            f.set(a, level, d - 1, Block.of("iron_block", 0xDCDCDC));
            f.set(a, level + 1, d - 1, Block.of("barrel[facing=up,open=false]", 0x86643B));
        }
    }

    /** 탈의실·당직실: 사물함 줄 (나무 판에 쇠 다락문) */
    private void lockers(Frame f, int w, int d, int level) {
        for (int a = 0; a < w; a++) {
            f.set(a, level, d - 1, Block.of("stripped_birch_wood[axis=y]", 0xC4B07B));
            f.set(a, level + 1, d - 1, Block.of("stripped_birch_wood[axis=y]", 0xC4B07B));
        }
        for (int a = 1; a < w - 1; a += 3) {
            f.set(a, level, 2, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        }
    }

    /** 휴게실: 소파, 탁자, 정수기 */
    private void lounge(Frame f, int w, int d, int level) {
        Furniture.sofa(f, r, 1, level, d - 1, Math.min(4, w - 2), "north");
        f.set(2, level, d - 3, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        f.set(w - 1, level, 0, IRON_BLOCK);
        f.set(w - 1, level + 1, 0, Block.of("light_blue_stained_glass", 0x6699D8));
    }

    // ------------------------------------------------------------------ 병동

    private void ward(int k, int level, int h) {
        Voxels v = t.v;
        int top = level + h - 2;
        int i0 = ti0 + 1, i1 = ti1 - 1;
        int cnJ = t.cj0 - 2, csJ = t.corridorJ;
        finish(i0, BACK + 1, i1, TJ1 - 1, level, VINYL);
        // 북쪽 병실 (j 3..cnJ-2), 남쪽 병실 (csJ+3..TJ1-1)
        int nDepth = cnJ - 1 - (BACK + 1);
        Frame fn = Frame.facing(v, i1, cnJ - 2, "north");
        Kit.strip(fn, 0, i1 - i0, nDepth, level, h, 7, WALL, false, (rf, rw, rd, n) -> {
            Rooms.ward(rf, r, rw, rd, level, h);
            roomNo(rf, rw / 2 + 1, level, k, n);
        });
        int sDepth = TJ1 - 1 - (csJ + 3) + 1;
        Frame fs = Frame.facing(v, i0, csJ + 3, "south");
        boolean garden = k == PODIUM;
        int gardenRoom = (mid - i0) / 8;
        Kit.strip(fs, 0, i1 - i0, sDepth, level, h, 7, WALL, false, (rf, rw, rd, n) -> {
            if (garden && n == gardenRoom) {
                // 5층: 옥상 정원으로 나가는 휴게 공간
                Interior.door(rf, rw / 2, level, -1, "pale_oak", "south");
                Furniture.sofa(rf, r, 0, level, 2, Math.min(3, rw), "east");
                Furniture.plant(rf, r, rw - 1, level, 0);
                label(rf, rw / 2 + 1, level, "옥상정원");
                return;
            }
            Rooms.ward(rf, r, rw, rd, level, h);
            roomNo(rf, rw / 2 + 1, level, k, 20 + n);
        });
        // 복도 (북·남) 를 타워 끝까지
        v.fill(i0, level, cnJ, i1, top, cnJ + 1, AIR);
        v.fill(i0, level, csJ, i1, top, csJ + 1, AIR);
        for (int i = i0 + 2; i <= i1 - 2; i += 5) {
            v.set(i, top, cnJ + 1, Interior.LIGHT);
            v.set(i, top, csJ, Interior.LIGHT);
        }
        // 서쪽 끝: 간호사실 (남쪽 복도를 보는 데스크)
        int ej0 = cnJ + 2, ej1 = csJ - 1;
        int w1 = t.ci0 - 3;
        v.fill(i0, level, ej0, w1, top, ej0, WALL);
        v.fill(w1 + 1, level, ej0, w1 + 1, top, ej1, WALL);
        for (int i = i0; i <= w1; i++) {
            v.set(i, level, ej1, i == i0 + 1 ? AIR : Furniture.COUNTER);
            if (i % 3 == 0) {
                v.set(i, level + 1, ej1, Kit.MONITOR);
                Furniture.chair(Frame.of(v), i, level, ej1 - 1, "north", "dark_oak");
            }
        }
        v.fill(i0, level, ej0 + 1, i0, level + 1, ej0 + 3, Block.of("white_terracotta", 0xD1B2A1));
        v.set(w1 - 1, level, ej0 + 1, Block.of("iron_block", 0xDCDCDC));
        v.set((i0 + w1) / 2, top, ej1, Blocks.hangingSign("birch", 0, "black", false, (k + 1) + "층 병동", "간호사실"));
        v.set((i0 + w1) / 2, top, (ej0 + ej1) / 2, Interior.LIGHT);
        // 동쪽 끝: 휴게실 (창가)
        int e0 = t.ci1 + 3;
        v.fill(e0, level, ej0, i1, top, ej0, WALL);
        v.fill(e0 - 1, level, ej0, e0 - 1, top, ej1, WALL);
        Interior.door(Frame.facing(v, e0 - 1, ej1 - 1, "west"), 0, level, 0, "pale_oak", "south");
        Furniture.sofa(Frame.facing(v, i1, ej0 + 2, "east"), r, 0, level, 0, 3, "south");
        v.set(i1 - 3, level, ej0 + 3, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        v.set(e0, level + 1, ej0 + 3, Kit.MONITOR);
        v.set(e0 + 1, level, ej1, IRON_BLOCK);
        v.set(e0 + 1, level + 1, ej1, Block.of("light_blue_stained_glass", 0x6699D8));
        v.set((e0 + i1) / 2, top, (ej0 + ej1) / 2, Interior.LIGHT);
        v.set(e0, level + 1, ej1 + 0, null);
    }

    private static void roomNo(Frame f, int a, int level, int k, int n) {
        if (f.empty(a, level + 1, -2)) {
            f.set(a, level + 1, -2, Blocks.wallSign("birch", "north", "black", false, "", (k + 1) + String.format("%02d", n + 1) + "호", "4인실"));
        }
    }

    // ------------------------------------------------------------------ 바깥

    /** 진료동 띠창과 돌 마감, 병동 창 아래 흰 띠, 진료동 옥상 난간 */
    private void exterior() {
        Voxels v = t.v;
        for (int k = 0; k < FLOORS; k++) {
            int level = t.levels[k], top = t.levels[k + 1] - 2;
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    if (!t.edge(i, j, k)) {
                        continue;
                    }
                    boolean ns = !t.inside(i, j - 1, k) || !t.inside(i, j + 1, k);
                    boolean ew = !t.inside(i - 1, j, k) || !t.inside(i + 1, j, k);
                    boolean corner = ns && ew;
                    if (k < PODIUM) {
                        boolean post = corner || (ns ? i % 6 == 2 : j % 6 == 2);
                        for (int y = level; y <= top; y++) {
                            boolean glass = !post && y >= level + 1 && y <= level + 2;
                            v.set(i, y, j, glass ? Block.of("light_blue_stained_glass", 0x6699D8) : CLADDING);
                        }
                        v.set(i, top + 1, j, WHITE);
                    } else {
                        Block b = v.get(i, level, j);
                        if (b != null && b.id().contains("glass")) {
                            v.set(i, level, j, WHITE);
                        }
                    }
                }
            }
        }
        // 진료동 옥상 난간 (병동 타워 밖)
        int y = t.levels[PODIUM];
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (t.edge(i, j, PODIUM - 1) && !t.inside(i, j, PODIUM)) {
                    v.set(i, y, j, CLADDING);
                    v.set(i, y + 1, j, Block.of("glass_pane", 0xC8DCE4));
                }
            }
        }
    }

    /** 정문과 응급실: 출입구, 차양, 표지판, 구급차, 앞 차로 */
    private void front() {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        int canopyY = t.levels[1] - 1;
        // 앞 차로와 보도
        v.fill(0, -1, FACE + 1, w - 1, -1, d - 1, GRAY_CONCRETE);
        v.fill(2, -1, FACE + 1, w - 3, -1, FACE + 2, LIGHT_GRAY_CONCRETE);
        // 정문: 5칸 열린 자동문 + 양옆 유리
        for (int i = mid - 6; i <= mid + 6; i++) {
            v.fill(i, 0, FACE, i, 3, FACE, Math.abs(i - mid) <= 2 ? AIR : Block.of("light_blue_stained_glass_pane", 0x6699D8));
        }
        v.fill(mid - 7, 0, FACE, mid - 7, 3, FACE, WHITE);
        v.fill(mid + 7, 0, FACE, mid + 7, 3, FACE, WHITE);
        // 정문 큰 차양 (차 대는 곳 위)
        int c0 = mid - 11, c1 = mid + 11, cj1 = d - 2;
        v.fill(c0, canopyY, FACE + 1, c1, canopyY, cj1, WHITE);
        v.fill(c0, canopyY + 1, cj1, c1, canopyY + 1, cj1, WHITE);
        for (int i = c0 + 2; i <= c1 - 2; i += 4) {
            v.set(i, canopyY, (FACE + cj1) / 2, Interior.LIGHT);
        }
        for (int i : new int[]{c0, c1}) {
            v.fill(i, 0, cj1, i, canopyY - 1, cj1, WHITE);
        }
        v.set(mid, canopyY + 1, cj1 + 1, Blocks.wallSign("birch", "south", "blue", true, "", NAME));
        v.fill(mid - 3, -1, FACE + 1, mid + 3, -1, FACE + 3, POLISHED_ANDESITE);
        // 응급실 입구 (서쪽, 구급차 진입로)
        int e0 = 4, e1 = 22, em = 10;
        for (int i = em - 4; i <= em + 4; i++) {
            v.fill(i, 0, FACE, i, 3, FACE, Math.abs(i - em) <= 1 ? AIR : Block.of("light_blue_stained_glass_pane", 0x6699D8));
        }
        v.fill(e0, canopyY, FACE + 1, e1, canopyY, cj1, WHITE);
        v.fill(e0, canopyY + 1, FACE + 1, e1, canopyY + 1, cj1, RED_CONCRETE);
        v.fill(e0 + 1, canopyY + 1, FACE + 1, e1 - 1, canopyY + 1, cj1 - 1, WHITE);
        for (int i : new int[]{e0, e1}) {
            v.fill(i, 0, cj1, i, canopyY - 1, cj1, WHITE);
        }
        for (int i = e0 + 2; i <= e1 - 2; i += 4) {
            v.set(i, canopyY, (FACE + cj1) / 2, Interior.LIGHT);
        }
        // 「응급실」 표지판: 차양 앞 빨간 판에 흰 글씨 (밤에 빛남)
        v.fill(em - 3, canopyY + 2, cj1, em + 3, canopyY + 3, cj1, RED_CONCRETE);
        for (int i = em - 2; i <= em + 2; i += 2) {
            v.set(i, canopyY + 3, cj1 + 1, Blocks.wallSign("mangrove", "south", "white", true, "", "응급실", "EMERGENCY"));
        }
        v.set(em, canopyY + 2, cj1 + 1, Blocks.wallSign("mangrove", "south", "white", true, "", "24시간 진료"));
        v.set(em + 3, 2, FACE + 1, Blocks.wallSign("mangrove", "south", "white", true, "", "응급실", "구급차 전용"));
        // 구급차 자리 (비워 둠): 노면에 빨간 줄과 흰 칸
        v.fill(e0 + 1, -1, FACE + 2, e1 - 1, -1, FACE + 2, RED_CONCRETE);
        for (int i = e0 + 2; i <= e1 - 2; i += 4) {
            v.fill(i, -1, FACE + 3, i, -1, d - 3, WHITE_CONCRETE);
        }
        // 진료동 앞 화단 (차양 사이)
        for (int i = e1 + 3; i <= c0 - 3; i += 1) {
            v.set(i, -1, FACE + 1, GRASS);
            v.set(i, 0, FACE + 1, Kit.BOX_HEDGE);
        }
        for (int i = c1 + 3; i <= w - 4; i += 1) {
            v.set(i, -1, FACE + 1, GRASS);
            v.set(i, 0, FACE + 1, Kit.BOX_HEDGE);
        }
        f.set(mid - 9, 0, FACE + 1, Block.of("potted_azalea_bush", 0x63753A));
    }

    /** 5층 진료동 옥상 정원: 잔디밭, 화단, 벤치, 산책길, 퍼걸러 */
    private void roofGarden() {
        Voxels v = t.v;
        int y = t.levels[PODIUM];
        Random g = new Random(r.nextLong());
        int j0 = TJ1 + 2, j1 = FACE - 2;
        for (int j = j0; j <= j1; j++) {
            for (int i = 4; i <= w - 5; i++) {
                boolean path = j == (j0 + j1) / 2 || Math.abs(i - mid) <= 1 || i == 5 || i == w - 6 || j == j0 || j == j1;
                v.set(i, y - 1, j, path ? Block.of("polished_andesite", 0x848685) : GRASS);
            }
        }
        for (int i = 9; i <= w - 10; i += 10) {
            if (Math.abs(i - mid) < 4) {
                continue;
            }
            int jj = (j0 + j1) / 2 - 4;
            Kit.flowerBed(v, i - 2, jj - 1, i + 2, jj + 1, y - 1, g);
            Kit.bench(v, i - 1, y, (j0 + j1) / 2 - 1, 3, true, "south");
            Kit.bench(v, i - 1, y, (j0 + j1) / 2 + 1, 3, true, "north");
            v.ellipsoid(i + 0.5, y + 0.6, (j0 + j1) / 2 + 5.5, 1.6, 1.2, 1.6, Block.of("flowering_azalea_leaves[persistent=true]", 0x63753A));
        }
        // 퍼걸러 (가운데 길 위)
        for (int j = j0 + 1; j <= j1 - 1; j += 3) {
            v.fill(mid - 2, y, j, mid - 2, y + 2, j, Block.of("dark_oak_fence", 0x432B14));
            v.fill(mid + 2, y, j, mid + 2, y + 2, j, Block.of("dark_oak_fence", 0x432B14));
            v.fill(mid - 2, y + 3, j, mid + 2, y + 3, j, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        }
        for (int i = 8; i <= w - 9; i += 12) {
            Kit.parkLamp(v, i, y, j0 + 1);
        }
    }

    /** 높인 헬기장: 코어 위 철골 갑판, 노란 원과 흰 H, 둘레 안전망, 옥상에서 오르는 철 계단 */
    private void helipad() {
        Voxels v = t.v;
        int roof = t.roofLevel;
        int deck = roof + 6;
        int cx = (t.ci0 + t.ci1) / 2, cz = (BACK + TJ1) / 2;
        int half = Math.min(11, (TJ1 - BACK) / 2 - 1);
        int x0 = cx - half, x1 = cx + half, z0 = cz - half, z1 = cz + half;
        Block steel = Block.of("gray_concrete", 0x36393D);
        // 기둥
        for (int i = x0 + 1; i <= x1 - 1; i += 7) {
            for (int j = z0 + 1; j <= z1 - 1; j += 7) {
                v.fill(i, roof, j, i, deck - 2, j, Block.of("polished_deepslate", 0x484849));
            }
        }
        v.fill(x0, deck - 1, z0, x1, deck - 1, z1, steel);
        for (int j = z0; j <= z1; j++) {
            for (int i = x0; i <= x1; i++) {
                double dd = Math.hypot(i - cx, j - cz);
                if (dd <= half - 1 && dd > half - 2.2) {
                    v.set(i, deck - 1, j, YELLOW_CONCRETE);
                }
                boolean rim = i == x0 || i == x1 || j == z0 || j == z1;
                if (rim) {
                    v.set(i, deck, j, Block.of("iron_bars", 0x888888));
                    if ((i + j) % 4 == 0) {
                        v.set(i, deck - 1, j, Block.of("verdant_froglight[axis=y]", 0xE5F4E0));
                    }
                }
            }
        }
        for (int t2 = -3; t2 <= 3; t2++) {
            v.set(cx - 2, deck - 1, cz + t2, WHITE);
            v.set(cx + 2, deck - 1, cz + t2, WHITE);
        }
        v.fill(cx - 1, deck - 1, cz, cx + 1, deck - 1, cz, WHITE);
        // 옥상에서 갑판까지 철 계단 (갑판 동쪽 가장자리를 따라, 남쪽에서 북쪽으로 오름)
        int si = x1 + 1, sj = z1 - 1;
        Block step = Blocks.stairs("polished_deepslate", "north", 0x484849);
        for (int s = 0; s < 6; s++) {
            int j = sj - s;
            v.set(si, roof + s, j, step);
            v.fill(si, roof + s + 1, j, si, roof + s + 3, j, AIR);
            v.set(si + 1, roof + s + 1, j, Block.of("iron_bars", 0x888888));
            if (s > 0) {
                v.set(si, roof + s - 1, j, Block.of("polished_deepslate", 0x484849));
            }
        }
        v.fill(si, deck - 1, sj - 7, si, deck - 1, sj - 6, steel);
        v.set(x1, deck, sj - 6, AIR);
        v.set(x1, deck, sj - 7, AIR);
        v.fill(si - 1, deck - 1, sj - 7, si - 1, deck - 1, sj - 6, steel);
    }

    // ------------------------------------------------------------------ 앞 광장 (따로 놓는 것)

    /**
     * 병원 앞 광장 (정면 +j 가 큰길 쪽, j = 0 이 병원 건물 앞 차로와 맞닿음): 큰길에서 응급실 차양으로 들어오는 차로와
     * 반대쪽으로 나가는 차로, 거점 자리(비워 둔 보행 광장), 방문객 주차장(주차선만), 느티나무와 벤치,
     * 병원 이름 돌 표지, 응급실 안내 표지.
     *
     * @param hubI 거점 칸 (광장 기준)
     * @param erI  응급실 차양 가운데 i (광장 기준), mainI 는 정문 가운데 i
     */
    static Voxels forecourt(int w, int d, Random r, Ground ground, int hubI, int hubJ, int erI, int mainI) {
        Voxels v = new Voxels(w, d, -1, 9);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                v.set(i, -1, j, (i + j) % 7 == 0 ? POLISHED_ANDESITE : LIGHT_GRAY_CONCRETE);
            }
        }
        // 들어오는 차로 (응급실 쪽)와 나가는 차로 (정문 반대쪽 끝)
        int exitI = w - 9;
        for (int gi : new int[]{erI, exitI}) {
            for (int j = 0; j < d; j++) {
                for (int i = gi - 3; i <= gi + 3; i++) {
                    v.set(i, -1, j, GRAY_CONCRETE);
                }
                v.set(gi, -1, j, j % 4 < 2 ? WHITE_CONCRETE : GRAY_CONCRETE);
            }
        }
        // 방문객 주차장: 정문과 나가는 차로 사이, 주차선만 (차는 없음)
        int p0 = mainI + 14, p1 = exitI - 5;
        for (int j = 2; j + 5 <= d - 4; j += 13) {
            for (int i = p0; i <= p1; i++) {
                for (int jj = j; jj <= Math.min(d - 4, j + 11); jj++) {
                    v.set(i, -1, jj, GRAY_CONCRETE);
                }
            }
            for (int i = p0; i <= p1; i += 3) {
                v.fill(i, -1, j, i, -1, j + 4, WHITE_CONCRETE);
                if (j + 11 <= d - 4) {
                    v.fill(i, -1, j + 7, i, -1, j + 11, WHITE_CONCRETE);
                }
            }
        }
        // 보행 광장: 큰길 쪽 한 줄 느티나무와 벤치 (거점 둘레는 비움)
        Random g = new Random(r.nextLong());
        int tj = d - 6;
        for (int i = 5; i < p0 - 3; i += 10) {
            if (Math.abs(i - hubI) <= 7 || Math.abs(i - erI) <= 7 || Math.abs(i - mainI) <= 4 || !ground.clear(i, tj, 4)) {
                continue;
            }
            v.fill(i - 1, -1, tj - 1, i + 1, -1, tj + 1, GRASS);
            Kit.zelkova(v, i, tj, -1, g);
            Kit.bench(v, i - 1, 0, tj - 3, 3, true, "north");
        }
        // 정문 쪽 보행로 (큰길 → 정문 차양)
        for (int j = 0; j < d; j++) {
            for (int i = mainI - 2; i <= mainI + 2; i++) {
                v.set(i, -1, j, POLISHED_GRANITE);
            }
        }
        // 병원 이름 돌 표지 (큰길 쪽, 정문 길 옆)
        int si = mainI + 6, sj = d - 4;
        if (ground.clear(si, sj, 3)) {
            v.fill(si - 3, 0, sj, si + 3, 1, sj, POLISHED_GRANITE);
            v.fill(si - 3, 2, sj, si + 3, 2, sj, Block.of("polished_granite_slab[type=bottom,waterlogged=false]", 0x9A6A59));
            v.set(si, 1, sj + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", NAME));
            v.set(si, 1, sj - 1, Blocks.wallSign("dark_oak", "north", "white", true, "", NAME));
        }
        // 응급실 안내 표지 (들어오는 차로 옆)
        int ei = erI + 5;
        if (ground.clear(ei, d - 3, 1) && Math.abs(ei - hubI) > 3) {
            v.fill(ei, 0, d - 3, ei, 2, d - 3, Block.of("andesite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x888888));
            v.fill(ei, 3, d - 3, ei, 4, d - 3, RED_CONCRETE);
            v.set(ei, 4, d - 2, Blocks.wallSign("mangrove", "south", "white", true, "", "응급실", "들어오는 길"));
        }
        v.connect();
        return v;
    }

    private Hospital() {
        this(0, 0, null);
    }
}
