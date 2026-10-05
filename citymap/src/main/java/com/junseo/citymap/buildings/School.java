package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 한국 학교 하나 (초·중·고): 본관, 체육관(다목적강당), 운동장, 정문과 담장.
 * <ul>
 *   <li>본관: 4층(고등학교 5층) 붉은 벽돌에 흰 띠, 북쪽 긴 복도(편복도)와 남향 교실(칠판·교탁·책상 줄),
 *       양 끝 꺾인 계단(옥상까지), 엘리베이터, 층마다 남녀 화장실. 1층 가운데 중앙현관(차양, 학교 이름),
 *       교무실·행정실·교장실·보건실, 위층 교실과 과학실·음악실·도서실. 층고 4</li>
 *   <li>체육관: 둥근 지붕의 높은 홀, 나무 바닥에 농구 코트 줄, 양 끝 농구 골대, 북쪽 무대와 막</li>
 *   <li>운동장: 마사토(흙) 바닥에 흰 트랙 줄, 양 끝 축구 골대, 북쪽 가운데 조회대(깃대 셋)와 계단식 스탠드,
 *       교직원 주차장, 담장(낮은 벽돌 + 철창)과 나무(벚나무·은행나무), 정문(벽돌 기둥, 열어 둔 철문, 학교 이름 표지판)</li>
 * </ul>
 * 본관과 체육관은 따로 놓고(종류 "school"), 운동장·담장·정문은 학교 터 전체를 덮는 한 조각(종류 "school")인데
 * 두 건물 자리는 비워 둡니다 (낮은 곳에서 겹치지 않음). 터는 지을 수 있는 땅이어야 하고, 다 놓은 뒤 둘레 1칸까지 m.claim.
 * <p>
 * 터 최소 크기는 {@link #MIN_W}(동서) × {@link #MIN_D}(남북). 본관은 북쪽, 운동장은 남쪽 (교실이 운동장을 봄).
 */
final class School {
    /** 학교 터 최소 크기 (동서 × 남북) */
    static final int MIN_W = 92, MIN_D = 68;
    /** 본관 깊이 (북쪽 벽 ~ 앞마당) */
    static final int HALL_D = 16;
    /** 체육관 크기 */
    static final int GYM_W = 26, GYM_D = 20;

    static final Block BRICK = BRICKS;
    static final Block TRIM = WHITE_CONCRETE;
    static final Block FIELD = Block.of("dirt_path", 0x94793F);
    static final Block TRACK_LINE = Block.of("calcite", 0xDFE0DC);
    static final Block FENCE_BAR = Block.of("iron_bars", 0x888888);
    static final Block CHERRY = Block.of("cherry_leaves[distance=1,persistent=true,waterlogged=false]", 0xE6B3C3);

    /**
     * 학교 하나. 들어갈 자리가 없으면 빈 목록.
     *
     * @param level "초등학교" | "중학교" | "고등학교"
     * @param name  학교 이름 (예: "잠실초등학교")
     */
    static List<Placement> plan(BuildMask m, int[] block, String district, String level, String name, Random r) {
        int[] site = largestFree(m, block, 150, 130);
        if (site == null) {
            return List.of();
        }
        int sw = site[2] - site[0] + 1, sd = site[3] - site[1] + 1;
        if (sw < MIN_W || sd < MIN_D) {
            return List.of();
        }
        // 너무 크면 가운데 적당한 크기로
        int w = Math.min(sw, 130), d = Math.min(sd, 104);
        int x0 = site[0] + (sw - w) / 2, z0 = site[1] + (sd - d) / 2, x1 = x0 + w - 1, z1 = z0 + d - 1;
        if (!m.rectFree(x0, z0, x1, z1)) {
            return List.of();
        }
        String gate = gateSide(m, x0, z0, x1, z1);
        int floors = "고등학교".equals(level) ? 5 : 4;
        String full = name == null || name.isEmpty() ? "준서" + level : name;
        Layout lay = new Layout(w, d, gate);
        List<Placement> out = new ArrayList<>();
        long hallSeed = r.nextLong(), gymSeed = r.nextLong(), siteSeed = r.nextLong();
        int hx0 = x0 + lay.hallI0, hz0 = z0 + lay.top;
        out.add(Placement.rect(full + " 본관", "school", hx0, hz0, hx0 + lay.hallW - 1, hz0 + HALL_D - 1, "south",
                (bw, bd) -> mainHall(bw, bd, floors, full, level, new Random(hallSeed))));
        int gx0 = x0 + lay.gymI0, gz0 = z0 + lay.top;
        out.add(Placement.rect(full + " 체육관", "school", gx0, gz0, gx0 + GYM_W - 1, gz0 + GYM_D - 1, "south",
                (bw, bd) -> gym(bw, bd, full, new Random(gymSeed))));
        out.add(Placement.rect(full + " 운동장", "school", x0, z0, x1, z1, "south",
                (bw, bd) -> site(bw, bd, lay, full, level, new Random(siteSeed))));
        m.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
        return out;
    }

    /** 학교 터 안 배치 (터 좌표, 북쪽 위) */
    static final class Layout {
        final int w, d;
        final String gate;
        /** 건물 줄 시작 (북쪽 담장에서 3칸) */
        final int top = 3;
        final int hallI0, hallW, gymI0;
        /** 건물 앞 마당 줄 (길·조회대·스탠드) 과 운동장 */
        final int zone0, field0, field1, fieldI0, fieldI1;
        /** 정문 자리 (담장 위 가운데 칸, 너비 8) */
        final int gateI, gateJ;

        Layout(int w, int d, String gate) {
            this.w = w;
            this.d = d;
            this.gate = gate;
            gymI0 = w - 3 - GYM_W;
            hallI0 = 3;
            hallW = Math.min(130, gymI0 - 3 - hallI0);
            zone0 = top + GYM_D + 1;
            field0 = zone0 + 10;
            field1 = d - 5;
            boolean southGate = gate.equals("south") || gate.equals("north");
            fieldI0 = southGate ? 10 : 5;
            fieldI1 = w - 6;
            switch (gate) {
                case "east" -> {
                    gateI = w - 1;
                    gateJ = zone0 + 3;
                }
                case "west" -> {
                    gateI = 0;
                    gateJ = zone0 + 3;
                }
                default -> {
                    gateI = 6;
                    gateJ = d - 1;
                }
            }
        }
    }

    /** 터의 정문 쪽: 길에 가장 많이 닿은 변 (북쪽은 본관 뒤라 동·서·남 중에서) */
    private static String gateSide(BuildMask m, int x0, int z0, int x1, int z1) {
        int south = 0, east = 0, west = 0;
        for (int x = x0; x <= x1; x++) {
            south += m.roadDistance(x, z1 + 1) <= 3 ? 1 : 0;
        }
        for (int z = z0; z <= z1; z++) {
            east += m.roadDistance(x1 + 1, z) <= 3 ? 1 : 0;
            west += m.roadDistance(x0 - 1, z) <= 3 ? 1 : 0;
        }
        if (south >= east && south >= west && south > 0) {
            return "south";
        }
        return east >= west ? "east" : "west";
    }

    /** 덩어리 안 지을 수 있는 칸으로만 된 가장 큰 직사각형 (한 변은 maxW·maxD 까지만 셈) */
    static int[] largestFree(BuildMask m, int[] block, int maxW, int maxD) {
        int bw = block[2] - block[0] + 1, bd = block[3] - block[1] + 1;
        int[] heights = new int[bw];
        int[] stack = new int[bw + 1];
        long bestArea = 0;
        int[] best = null;
        for (int j = 0; j < bd; j++) {
            for (int i = 0; i < bw; i++) {
                heights[i] = m.free(block[0] + i, block[1] + j) ? heights[i] + 1 : 0;
            }
            int sp = 0;
            for (int i = 0; i <= bw; i++) {
                int h = i == bw ? 0 : heights[i];
                while (sp > 0 && heights[stack[sp - 1]] >= h) {
                    int top = stack[--sp];
                    int height = heights[top];
                    int left = sp == 0 ? 0 : stack[sp - 1] + 1;
                    int width = i - left;
                    long area = (long) Math.min(width, maxW) * Math.min(height, maxD);
                    // 학교 최소 크기를 넘는 것을 먼저
                    if (width >= MIN_W && height >= MIN_D) {
                        area += 1_000_000;
                    }
                    if (area > bestArea) {
                        bestArea = area;
                        best = new int[]{block[0] + left, block[1] + j - height + 1, block[0] + i - 1, block[1] + j};
                    }
                }
                if (i < bw) {
                    stack[sp++] = i;
                }
            }
        }
        return best;
    }

    // ================================================================== 본관

    /** 본관 (w = 길이, d = 16). 층 수 floors */
    static Voxels mainHall(int w, int d, int floors, String name, String level, Random r) {
        w = Math.max(w, 46);
        int[] levels = Floors.levels(Floors.OFFICE, Floors.OFFICE, floors);
        int roof = levels[floors];
        Voxels v = new Voxels(w, Math.max(d, HALL_D), -1, roof + 6);
        int east = w - 1, jn = 0, c0 = 1, c1 = 3, jc = 4, jr0 = 5, jr1 = 12, js = 13;
        Block inner = Interior.INNER_WALL;
        // 바닥판과 바깥 벽
        for (int k = 0; k <= floors; k++) {
            int y = levels[k] - 1;
            v.fill(0, y, jn, east, y, js, k == 0 ? POLISHED_ANDESITE : k == floors ? SMOOTH_STONE : TRIM);
            v.fill(1, y, jn + 1, east - 1, y, js - 1, k == 0 ? POLISHED_ANDESITE : k == floors ? SMOOTH_STONE
                    : Block.of("light_gray_concrete", 0x7D7D73));
        }
        for (int k = 0; k < floors; k++) {
            int L = levels[k], top = L + 2;
            v.walls(0, L, jn, east, top, js, BRICK);
            v.fill(1, L, jc, east - 1, top, jc, inner);
            // 복도 바닥 (밝은 돌), 복도 등
            v.fill(1, L - 1, c0, east - 1, L - 1, c1, POLISHED_DIORITE);
            for (int i = 4; i < east; i += 6) {
                v.set(i, top, 2, Interior.LIGHT);
            }
        }
        // 계단 둘 (양 끝, 출입구는 복도 쪽)
        Interior.stairCore(new Frame(v, 2, jr0, 0), levels, BRICK, "polished_andesite", 0x848685);
        Interior.stairCore(new Frame(v, east - 6, jr0, 0), levels, BRICK, "polished_andesite", 0x848685);
        v.fill(1, roof + 3, jc, 7, roof + 3, jr0 + Interior.stairDepth(levels), SMOOTH_STONE);
        v.fill(east - 7, roof + 3, jc, east - 1, roof + 3, jr0 + Interior.stairDepth(levels), SMOOTH_STONE);
        int sb = jr0 + Interior.stairDepth(levels); // 계단 뒷벽 줄
        // 엘리베이터 (동쪽 계단 옆, 문은 복도 쪽)
        int ei = east - 9;
        Interior.elevator(Frame.facing(v, ei, 6, "north"), java.util.Arrays.copyOf(levels, floors), BRICK);
        v.fill(ei - 2, 0, 8, ei + 1, roof - 1, js - 1, inner);
        v.fill(ei - 2, roof, jc, ei + 1, roof + 3, 7, BRICK);
        // 화장실 (서쪽 계단 옆, 남·여, 사이 벽 두 겹): i 8..19
        for (int k = 0; k < floors; k++) {
            int L = levels[k];
            Frame tf = Frame.facing(v, 18, 11, "north");
            Interior.restroom(tf, 0, 0, 3, 6, L, Floors.OFFICE, "남자 화장실", 1);
            Interior.restroom(tf, 6, 0, 9, 6, L, Floors.OFFICE, "여자 화장실", 7);
        }
        // 교실 줄: i 20 .. ei-3 (벽 19, ei-2)
        int s0 = 20, s1 = ei - 3;
        int mid = (s0 + s1) / 2;
        String[] grades = {"1", "2", "3", "4", "5", "6"};
        int maxGrade = "초등학교".equals(level) ? 6 : 3;
        for (int k = 0; k < floors; k++) {
            int L = levels[k], top = L + 2;
            List<int[]> rooms = new ArrayList<>();
            if (k == 0) {
                // 중앙현관 (가운데 6칸) 양옆으로 나눔
                split(rooms, s0, mid - 5);
                split(rooms, mid + 4, s1);
                // 현관: 벽 없이 복도와 이어짐, 남쪽 문
                v.fill(mid - 3, L, jc, mid + 2, top, js - 1, AIR);
                v.fill(mid - 4, L, jr0, mid - 4, top, js - 1, inner);
                v.fill(mid + 3, L, jr0, mid + 3, top, js - 1, inner);
                v.fill(mid - 3, L - 1, jr0, mid + 2, L - 1, js - 1, POLISHED_DIORITE);
                v.fill(mid - 1, L, js, mid, L + 1, js, AIR);
                Kit.doubleDoor(Frame.of(v), mid - 1, L, js, "birch", "south");
                v.fill(mid - 3, L + 1, js, mid - 2, L + 2, js, GLASS_PANE);
                v.fill(mid + 1, L + 1, js, mid + 2, L + 2, js, GLASS_PANE);
                v.set(mid - 2, top, jr0 + 3, Interior.LIGHT);
                v.set(mid + 1, top, jr0 + 3, Interior.LIGHT);
                v.set(mid + 2, L, jr0 + 1, Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
                v.set(mid - 3, L, js - 1, Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
                // 신발장 (현관 옆벽)
                for (int j = jr0; j <= js - 2; j++) {
                    v.set(mid - 3, L, j, AptUnit.SHOE);
                }
            } else {
                split(rooms, s0, s1);
            }
            for (int n = 0; n < rooms.size(); n++) {
                int a0 = rooms.get(n)[0], a1 = rooms.get(n)[1];
                if (a1 + 1 <= s1 && !(k == 0 && a1 + 1 == mid - 4)) {
                    v.fill(a1 + 1, L, jr0, a1 + 1, top, js - 1, inner);
                }
                String label = roomLabel(k, n, rooms.size(), floors, grades, maxGrade);
                Frame f = new Frame(v, a0, jr0, 0);
                int rw = a1 - a0 + 1, rd = jr1 - jr0 + 1;
                switch (label) {
                    case "교무실" -> staffRoom(v, r, a0, a1, L);
                    case "행정실" -> adminRoom(v, a0, a1, L);
                    case "교장실" -> principal(v, r, a0, a1, L);
                    case "보건실" -> nurse(v, r, a0, a1, L);
                    case "과학실" -> lab(v, a0, a1, L);
                    case "음악실" -> music(v, r, a0, a1, L);
                    case "도서실" -> library(v, r, a0, a1, L);
                    default -> Rooms.classroom(f, rw, rd, L, Floors.OFFICE);
                }
                if (!label.contains("-")) {
                    Interior.door(f, rw - 2, L, -1, "oak", "south");
                }
                v.set(a0 + rw - 1, L + 2, jc - 1, Blocks.wallSign("birch", "north", "black", false, "", label));
                // 남쪽 창 (교실 앞 큰 창)
                for (int i = a0; i <= a1; i++) {
                    v.set(i, L, js, TRIM);
                    v.set(i, L + 1, js, GLASS_PANE);
                    v.set(i, L + 2, js, GLASS_PANE);
                }
            }
            // 북쪽 복도 창, 화장실 작은 창
            for (int i = 2; i < east - 1; i++) {
                if (i % 3 != 0 && (i < 1 || i > 7) && (i < east - 7)) {
                    v.set(i, L + 1, jn, GLASS_PANE);
                    v.set(i, L + 2, jn, GLASS_PANE);
                }
            }
            // 양 끝 계단실 창
            v.set(0, L + 2, jr0 + 2, GLASS_PANE);
            v.set(east, L + 2, jr0 + 2, GLASS_PANE);
        }
        // 층 띠 (흰 줄): 바깥 벽 바닥 높이
        for (int k = 1; k <= floors; k++) {
            int y = levels[k] - 1;
            v.walls(0, y, jn, east, y, js, TRIM);
        }
        // 옥상 난간, 계단 옥탑, 물탱크
        v.walls(0, roof, jn, east, roof, js, TRIM);
        v.fill(mid - 4, roof, jr0 + 1, mid + 3, roof + 2, js - 2, Block.of("light_gray_concrete", 0x7D7D73));
        v.fill(mid - 4, roof + 3, jr0 + 1, mid + 3, roof + 3, js - 2, TRIM);
        // 학교 이름 (옥상 앞 난간 위 판, 정면)
        int bt = roof + 3;
        v.fill(mid - 6, roof + 1, js, mid + 5, bt, js, TRIM);
        v.set(mid, bt - 1, js + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", short9(name), "", ""));
        v.set(mid - 1, bt - 1, js + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", short9(name), "", ""));
        // 중앙현관 차양과 이름 표지판
        v.fill(mid - 4, 4, js + 1, mid + 3, 4, js + 2, Kit.SLAB);
        v.fill(mid - 4, 0, js + 2, mid - 4, 3, js + 2, TRIM);
        v.fill(mid + 3, 0, js + 2, mid + 3, 3, js + 2, TRIM);
        v.set(mid + 2, 2, js + 1, Blocks.wallSign("birch", "south", "black", false, "", short9(name), "중앙현관", ""));
        // 앞 화단과 보도
        v.fill(0, -1, js + 1, east, -1, v.d - 1, Block.of("light_gray_concrete", 0x7D7D73));
        for (int i = 1; i < east; i++) {
            if (Math.abs(i - mid) > 5) {
                v.set(i, -1, v.d - 1, GRASS);
                v.set(i, 0, v.d - 1, Kit.BOX_HEDGE);
            }
        }
        v.connect();
        return v;
    }

    /** [a0, a1] 을 교실 크기(안쪽 9, 벽 1)로 나눔. 마지막 방이 남는 칸을 가짐 */
    private static void split(List<int[]> rooms, int a0, int a1) {
        int a = a0;
        while (a1 - a + 1 >= 6) {
            int rw = a1 - a + 1 - 10 >= 6 ? 9 : a1 - a + 1;
            rooms.add(new int[]{a, a + rw - 1});
            a += rw + 1;
        }
    }

    private static String roomLabel(int k, int n, int count, int floors, String[] grades, int maxGrade) {
        if (k == 0) {
            String[] first = {"교무실", "행정실", "교장실", "보건실"};
            if (n < first.length) {
                return first[n];
            }
            return "1-" + (n - first.length + 1);
        }
        if (k == floors - 1) {
            String[] top = {"과학실", "음악실", "도서실"};
            if (n < top.length) {
                return top[n];
            }
        }
        int grade = Math.min(maxGrade, 1 + (k * maxGrade) / Math.max(1, floors));
        int no = n + 1 - (k == floors - 1 ? 3 : 0);
        return grade + "-" + Math.max(1, no);
    }

    private static String short9(String s) {
        return s.length() > 9 ? s.substring(0, 9) : s;
    }

    /** 교무실: 마주 보는 책상 줄, 복사기 */
    private static void staffRoom(Voxels v, Random r, int a0, int a1, int L) {
        Frame f = Frame.of(v);
        for (int i = a0 + 1; i <= a1 - 1; i++) {
            Furniture.desk(f, i, L, 7, "north");
            Furniture.desk(f, i, L, 8, "south");
            if (i + 1 <= a1 - 1 && (i - a0) % 4 == 3) {
                i++;
            }
        }
        v.set(a1, L, 11, IRON_BLOCK); // 복사기
        v.fill(a0, L + 1, 5, a0, L + 2, 6, Block.of("green_concrete", 0x495B24)); // 일정판
        Interior.lights(f, a0, 5, a1, 12, L, Floors.OFFICE, 4, Interior.LIGHT);
    }

    private static void adminRoom(Voxels v, int a0, int a1, int L) {
        Frame f = Frame.of(v);
        v.fill(a0 + 1, L, 6, a1 - 1, L, 6, Furniture.COUNTER); // 민원 창구
        for (int i = a0 + 1; i <= a1 - 1; i += 2) {
            Furniture.desk(f, i, L, 9, "north");
        }
        v.set(a1, L, 12, Furniture.BOOKSHELF);
        v.set(a1, L + 1, 12, Furniture.BOOKSHELF);
        Interior.lights(f, a0, 5, a1, 12, L, Floors.OFFICE, 4, Interior.LIGHT);
    }

    private static void principal(Voxels v, Random r, int a0, int a1, int L) {
        Frame f = Frame.of(v);
        Furniture.desk(f, (a0 + a1) / 2, L, 11, "north");
        Furniture.table(f, a0 + 2, L, 7, Math.max(1, Math.min(3, a1 - a0 - 3)), 1, "dark_oak");
        for (int i = a0; i <= a1; i += 2) {
            v.set(i, L, 12, Furniture.BOOKSHELF);
        }
        Furniture.plant(f, r, a1, L, 5);
        Interior.lights(f, a0, 5, a1, 12, L, Floors.OFFICE, 4, Interior.LIGHT);
    }

    /** 보건실: 침대 둘과 커튼, 약장, 책상 */
    private static void nurse(Voxels v, Random r, int a0, int a1, int L) {
        Frame f = Frame.of(v);
        Block curtain = Block.of("white_stained_glass_pane", 0xF0F0F0);
        for (int n = 0; n < 2; n++) {
            int i = a1 - n * 3;
            if (i - 1 < a0) {
                break;
            }
            v.set(i, L, 11, Block.of("white_wool", 0xE9ECEC));
            v.set(i, L, 12, Block.of("white_wool", 0xE9ECEC));
            v.set(i, L + 1, 12, Block.of("white_carpet", 0xE9ECEC));
            v.set(i - 1, L, 11, curtain);
            v.set(i - 1, L + 1, 11, curtain);
        }
        Furniture.desk(f, a0 + 1, L, 7, "south");
        v.set(a0, L, 5, Block.of("white_concrete", 0xCFD5D6));
        v.set(a0, L + 1, 5, Block.of("white_stained_glass", 0xF0F0F0)); // 약장
        Interior.lights(f, a0, 5, a1, 12, L, Floors.OFFICE, 4, Interior.LIGHT);
    }

    /** 과학실: 실험대 (가운데 개수대), 칠판 */
    private static void lab(Voxels v, int a0, int a1, int L) {
        Frame f = Frame.of(v);
        v.fill(a0 - 1, L + 1, 6, a0 - 1, L + 2, 11, Block.of("green_concrete", 0x495B24));
        for (int j = 6; j <= 11; j += 3) {
            for (int i = a0 + 2; i + 1 <= a1 - 1; i += 4) {
                v.fill(i, L, j, i + 1, L, j, Block.of("polished_blackstone", 0x353038));
                v.set(i, L + 1, j, CAULDRON);
                Furniture.chair(f, i, L, j + 1, "south", "oak");
                Furniture.chair(f, i + 1, L, j + 1, "south", "oak");
            }
        }
        Interior.lights(f, a0, 5, a1, 12, L, Floors.OFFICE, 4, Interior.LIGHT);
    }

    /** 음악실: 피아노(검은 판), 계단식 자리 대신 의자 줄, 악보대 */
    private static void music(Voxels v, Random r, int a0, int a1, int L) {
        Frame f = Frame.of(v);
        v.fill(a0, L, 6, a0 + 1, L, 6, Block.of("black_concrete", 0x080A0F));
        v.set(a0, L + 1, 6, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
        for (int j = 8; j <= 11; j += 2) {
            Kit.seats(f, a0 + 2, a1 - 1, L, j, "west", "spruce");
        }
        Interior.lights(f, a0, 5, a1, 12, L, Floors.OFFICE, 4, Interior.LIGHT);
    }

    /** 도서실: 책장 줄과 열람 탁자 */
    private static void library(Voxels v, Random r, int a0, int a1, int L) {
        Frame f = Frame.of(v);
        for (int i = a0 + 1; i <= a1 - 1; i += 3) {
            v.fill(i, L, 5, i, L + 1, 8, Furniture.BOOKSHELF);
        }
        for (int i = a0 + 1; i + 1 <= a1 - 1; i += 4) {
            Furniture.table(f, i, L, 11, 2, 1, "birch");
        }
        Interior.lights(f, a0, 5, a1, 12, L, Floors.OFFICE, 4, Interior.LIGHT);
    }

    // ================================================================== 체육관

    /** 체육관 (다목적강당): 높은 홀, 둥근 지붕, 나무 바닥에 코트 줄, 골대, 북쪽 무대. 문은 남쪽 */
    static Voxels gym(int w, int d, String name, Random r) {
        w = Math.max(w, 20);
        d = Math.max(d, 16);
        int wallTop = 9;
        Voxels v = new Voxels(w, d, -1, wallTop + 8);
        int east = w - 1, jn = 0, js = d - 3;
        v.fill(0, -1, jn, east, -1, js, Block.of("birch_planks", 0xC0AF79));
        v.walls(0, 0, jn, east, wallTop, js, BRICK);
        v.walls(0, 0, jn, east, 0, js, Block.of("stone_bricks", 0x7A7979));
        v.walls(0, wallTop, jn, east, wallTop, js, TRIM);
        // 높은 창 (옆벽 위쪽 띠창)
        for (int j = 2; j < js - 1; j++) {
            if (j % 3 != 0) {
                v.fill(0, 6, j, 0, 8, j, GLASS_PANE);
                v.fill(east, 6, j, east, 8, j, GLASS_PANE);
            }
        }
        for (int i = 2; i < east - 1; i++) {
            if (i % 3 != 0) {
                v.fill(i, 6, js, i, 8, js, GLASS_PANE);
            }
        }
        // 둥근 지붕 (동서로 휜 반원통)
        Block roofB = Block.of("cyan_terracotta", 0x565B5B);
        double half = (w - 1) / 2.0;
        for (int i = 0; i <= east; i++) {
            double t = (i - half) / (half + 0.5);
            int h = wallTop + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - t * t)));
            for (int j = jn; j <= js; j++) {
                v.set(i, h, j, roofB);
            }
            // 박공 끝 벽 (남·북) 은 벽돌로 채움
            for (int y = wallTop + 1; y < h; y++) {
                v.set(i, y, jn, BRICK);
                v.set(i, y, js, BRICK);
            }
        }
        // 코트 줄 (흰 선): 바깥 줄, 가운데 줄, 가운데 원
        int ci = w / 2, cj = (js + 4) / 2;
        Block line = Block.of("white_concrete", 0xCFD5D6);
        for (int i = 2; i <= east - 2; i++) {
            v.set(i, -1, 5, line);
            v.set(i, -1, js - 2, line);
        }
        for (int j = 5; j <= js - 2; j++) {
            v.set(2, -1, j, line);
            v.set(east - 2, -1, j, line);
        }
        for (int j = 5; j <= js - 2; j++) {
            v.set(ci, -1, j, line);
        }
        for (int a = 0; a < 16; a++) {
            double ang = a * Math.PI / 8;
            v.set(ci + (int) Math.round(2.5 * Math.cos(ang)), -1, cj + (int) Math.round(2.5 * Math.sin(ang)), line);
        }
        // 농구 골대 (동·서 끝 벽)
        for (int side = 0; side < 2; side++) {
            int i = side == 0 ? 1 : east - 1;
            v.fill(i, 4, cj - 1, i, 5, cj + 1, Block.of("white_stained_glass", 0xF0F0F0));
            v.set(i + (side == 0 ? 1 : -1), 3, cj, Block.of("iron_bars", 0x888888));
        }
        // 북쪽 무대 (높이 1.5, 계단 둘, 막)
        v.fill(2, 0, 1, east - 2, 0, 3, Block.of("spruce_planks", 0x725430));
        v.fill(2, 1, 1, east - 2, 1, 3, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        v.set(1, 0, 3, Block.of("spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0x725430));
        v.set(east - 1, 0, 3, Block.of("spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", 0x725430));
        v.fill(2, 2, 1, 3, 6, 1, Block.of("red_wool", 0xA12722));
        v.fill(east - 3, 2, 1, east - 2, 6, 1, Block.of("red_wool", 0xA12722));
        v.fill(4, 6, 1, east - 4, 6, 1, Block.of("red_wool", 0xA12722));
        // 등 (천장에 매단 큰 등)
        for (int j = 5; j < js; j += 5) {
            for (int i = 4; i < east; i += 6) {
                v.set(i, wallTop - 1, j, Interior.LIGHT);
            }
        }
        // 정문 (남쪽 가운데 양여닫이) 과 차양, 이름 표지판
        v.fill(ci - 2, 0, js, ci + 1, 2, js, BRICK);
        Kit.doubleDoor(Frame.of(v), ci - 1, 0, js, "spruce", "south");
        v.fill(ci - 3, 3, js + 1, ci + 2, 3, js + 2, Kit.SLAB);
        v.set(ci + 1, 2, js + 1, Blocks.wallSign("birch", "south", "black", false, "", short9(name), "체육관", ""));
        v.fill(0, -1, js + 1, east, -1, d - 1, Block.of("light_gray_concrete", 0x7D7D73));
        v.connect();
        return v;
    }

    // ================================================================== 운동장·담장·정문

    /** 학교 터 (건물 두 자리는 비움): 운동장, 트랙, 골대, 조회대, 스탠드, 길, 주차장, 나무, 담장, 정문 */
    static Voxels site(int w, int d, Layout lay, String name, String level, Random r) {
        Voxels v = new Voxels(w, d, -1, 12);
        // 건물 자리 (비워 둠)
        java.util.function.BiPredicate<Integer, Integer> building = (i, j) ->
                (i >= lay.hallI0 && i < lay.hallI0 + lay.hallW && j >= lay.top && j < lay.top + HALL_D)
                        || (i >= lay.gymI0 && i < lay.gymI0 + GYM_W && j >= lay.top && j < lay.top + GYM_D);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (!building.test(i, j)) {
                    v.set(i, -1, j, GRASS);
                }
            }
        }
        // 건물 앞 길 (동서로 길게, 3칸) 과 본관 앞 마당
        int p0 = lay.zone0, p1 = lay.zone0 + 2;
        for (int j = lay.top + HALL_D; j < lay.zone0; j++) {
            for (int i = lay.hallI0; i < lay.hallI0 + lay.hallW; i++) {
                v.set(i, -1, j, Block.of("light_gray_concrete", 0x7D7D73));
            }
        }
        v.fill(2, -1, p0, w - 3, -1, p1, Block.of("light_gray_concrete", 0x7D7D73));
        // 운동장 (마사토), 트랙 줄
        int f0 = lay.field0, f1 = lay.field1, fi0 = lay.fieldI0, fi1 = lay.fieldI1;
        v.fill(fi0, -1, f0, fi1, -1, f1, FIELD);
        double cz = (f0 + f1) / 2.0, R = (f1 - f0) / 2.0 - 1;
        double ax0 = fi0 + 1 + R, ax1 = fi1 - 1 - R;
        for (int j = f0; j <= f1; j++) {
            for (int i = fi0; i <= fi1; i++) {
                double px = i + 0.5, pz = j + 0.5;
                double dx = px < ax0 ? ax0 - px : px > ax1 ? px - ax1 : 0;
                double dist = Math.hypot(dx, pz - cz);
                for (double rr = R; rr >= R - 4; rr -= 2) {
                    if (Math.abs(dist - rr) < 0.5) {
                        v.set(i, -1, j, TRACK_LINE);
                    }
                }
            }
        }
        // 축구 골대 (양 끝, 트랙 안쪽): 기둥과 가로대는 엔드 로드, 뒤 그물은 철창
        int gz0 = (int) Math.round(cz) - 3, gz1 = gz0 + 6;
        for (int side = 0; side < 2; side++) {
            int gi = side == 0 ? (int) Math.floor(ax0 - R + 5) : (int) Math.ceil(ax1 + R - 5) - 1;
            int back = side == 0 ? gi - 1 : gi + 1;
            for (int j = gz0; j <= gz1; j++) {
                boolean post = j == gz0 || j == gz1;
                if (post) {
                    v.fill(gi, 0, j, gi, 1, j, Block.of("end_rod[facing=up]", 0xE8E2D8));
                }
                v.set(gi, 2, j, Block.of("end_rod[facing=south]", 0xE8E2D8));
                v.fill(back, 0, j, back, 1, j, FENCE_BAR);
            }
            v.fill(back, 0, gz0, gi, 0, gz0, FENCE_BAR);
            v.fill(back, 0, gz1, gi, 0, gz1, FENCE_BAR);
            // 골 에어리어 줄
            for (int j = gz0 - 2; j <= gz1 + 2; j++) {
                v.set(side == 0 ? gi + 3 : gi - 3, -1, j, TRACK_LINE);
            }
        }
        // 조회대 (운동장 북쪽 가운데): 단, 양옆 계단, 깃대 셋, 지붕
        int ci = (fi0 + fi1) / 2, ct = f0 - 6;
        v.fill(ci - 4, 0, ct, ci + 4, 0, ct + 3, Block.of("stone_bricks", 0x7A7979));
        v.fill(ci - 4, 1, ct, ci + 4, 1, ct, Block.of("stone_bricks", 0x7A7979));
        v.set(ci - 5, 0, ct + 2, Block.of("stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0x7A7979));
        v.set(ci + 5, 0, ct + 2, Block.of("stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", 0x7A7979));
        for (int dx : new int[]{-4, 4}) {
            v.fill(ci + dx, 1, ct + 3, ci + dx, 4, ct + 3, TRIM);
            v.fill(ci + dx, 1, ct, ci + dx, 4, ct, TRIM);
        }
        v.fill(ci - 5, 5, ct - 1, ci + 5, 5, ct + 4, Kit.SLAB);
        for (int dx : new int[]{-2, 0, 2}) {
            v.fill(ci + dx, 2, ct, ci + dx, 9, ct, Block.of("end_rod[facing=up]", 0xE8E2D8)); // 깃대
        }
        // 교직원 주차장 자리 (동·서 정문이면 길 남쪽, 정문 가까운 끝)
        boolean ns0 = lay.gate.equals("south") || lay.gate.equals("north");
        boolean parkEast = lay.gate.equals("east");
        int pa0 = parkEast ? fi1 - 12 : fi0 + 1, pa1 = pa0 + 12;
        // 스탠드 (조회대 양옆, 운동장을 보는 계단식 자리 3단)
        Block step = Block.of("stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x7A7979);
        for (int i = fi0; i <= fi1; i++) {
            if (Math.abs(i - ci) <= 6 || (!ns0 && i >= pa0 - 1 && i <= pa1 + 1)) {
                continue;
            }
            v.set(i, 0, f0 - 3, Block.of("stone_bricks", 0x7A7979));
            v.set(i, 1, f0 - 3, step);
            v.set(i, 0, f0 - 2, step);
            v.set(i, -1, f0 - 1, Block.of("light_gray_concrete", 0x7D7D73));
            v.set(i, 0, f0 - 4, Block.of("stone_bricks", 0x7A7979));
            v.set(i, 1, f0 - 4, Block.of("stone_bricks", 0x7A7979));
            v.set(i, 2, f0 - 4, step);
        }
        // 정문 쪽 통로와 교직원 주차장
        Block asphalt = GRAY_CONCRETE;
        if (ns0) {
            v.fill(2, -1, p1 + 1, fi0 - 2, -1, d - 2, asphalt);
        } else {
            int gj = lay.gateJ;
            v.fill(lay.gate.equals("east") ? w - 4 : 1, -1, p0, lay.gate.equals("east") ? w - 2 : 3, -1, gj + 4, asphalt);
        }
        // 주차장
        if (!ns0) {
            // 길(p0..p1) 남쪽, 칸 3×5, 차 앞은 길 쪽(북)
            for (int k = 0; k < 4; k++) {
                int a = pa0 + k * 3;
                v.fill(a, -1, p1 + 1, a + 3, -1, p1 + 5, asphalt);
                v.fill(a, -1, p1 + 1, a, -1, p1 + 5, Block.of("white_concrete", 0xCFD5D6));
                v.fill(a + 3, -1, p1 + 1, a + 3, -1, p1 + 5, Block.of("white_concrete", 0xCFD5D6));
                v.carSpot(a + 2.0, 0, p1 + 3.5, 0, -1);
            }
        } else {
            // 서쪽 진입로 (i 2..8) 담장 쪽에 세로 칸 셋 (3×5, 차 앞은 북), 통로 i 5..8
            for (int k = 0; k < 3; k++) {
                int jj = lay.field0 + 2 + k * 6;
                if (jj + 5 > d - 3) {
                    break;
                }
                v.fill(2, -1, jj - 1, 4, -1, jj - 1, Block.of("white_concrete", 0xCFD5D6));
                v.fill(2, -1, jj + 5, 4, -1, jj + 5, Block.of("white_concrete", 0xCFD5D6));
                v.carSpot(3.5, 0, jj + 2.5, 0, -1);
            }
        }
        // 나무: 담장 안쪽을 따라 (벚나무·은행나무), 건물 뒤·운동장 안은 피함
        for (int i = 4; i < w - 4; i += 6) {
            tree(v, r, i, d - 3, building);
        }
        for (int j = lay.field0; j < d - 4; j += 6) {
            if (lay.fieldI0 >= 7 || !lay.gate.equals("west")) {
                tree(v, r, 2, j, building);
            }
            tree(v, r, w - 3, j, building);
        }
        // 담장: 낮은 벽돌 + 철창, 모서리·6칸마다 벽돌 기둥
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                boolean edge = i == 0 || j == 0 || i == w - 1 || j == d - 1;
                if (!edge) {
                    continue;
                }
                boolean pillar = (i + j) % 6 == 0 || (i == 0 || i == w - 1) && (j == 0 || j == d - 1);
                v.set(i, 0, j, BRICK);
                v.set(i, 1, j, pillar ? BRICK : FENCE_BAR);
                v.set(i, 2, j, pillar ? Block.of("brick_slab[type=bottom,waterlogged=false]", 0x966153) : FENCE_BAR);
            }
        }
        // 정문: 8칸 트임, 벽돌 기둥 둘, 열어 둔 철문 (담장 안쪽에 붙여 둠), 학교 이름 표지판
        int gw = 8;
        boolean ns = lay.gate.equals("south") || lay.gate.equals("north");
        int ga0 = ns ? lay.gateI - 2 : lay.gateJ - gw / 2;
        for (int t = 0; t < gw; t++) {
            int i = ns ? ga0 + t : lay.gateI, j = ns ? lay.gateJ : ga0 + t;
            v.fill(i, 0, j, i, 2, j, AIR);
            v.set(i, -1, j, asphalt);
        }
        for (int side = 0; side < 2; side++) {
            int t = side == 0 ? -1 : gw;
            int i = ns ? ga0 + t : lay.gateI, j = ns ? lay.gateJ : ga0 + t;
            v.fill(i, 0, j, i, 2, j, BRICK);
            v.set(i, 3, j, LANTERN);
        }
        // 교명석: 정문 안쪽 옆에 이름을 새긴 돌 (정문 쪽을 봄)
        int in = 2;
        int ni = ns ? ga0 - 4 : lay.gate.equals("east") ? lay.gateI - in : lay.gateI + in;
        int nj = ns ? (lay.gate.equals("south") ? lay.gateJ - in : lay.gateJ + in) : ga0 - 4;
        int ti = ns ? 1 : 0, tj = ns ? 0 : 1;
        v.fill(ni - ti, 0, nj - tj, ni + ti, 1, nj + tj, Block.of("polished_granite", 0x9A6A59));
        v.set(ni, 2, nj, Block.of("polished_granite_slab[type=bottom,waterlogged=false]", 0x9A6A59));
        int si = ni + (lay.gate.equals("east") ? 1 : lay.gate.equals("west") ? -1 : 0);
        int sj = nj + (lay.gate.equals("south") ? 1 : lay.gate.equals("north") ? -1 : 0);
        v.set(si, 1, sj, Blocks.wallSign("dark_oak", lay.gate, "white", false, "", short9(name), "", ""));
        // 보행등
        for (int i = 6; i < w - 6; i += 10) {
            if (v.get(i, 0, p0 - 1) == null && !building.test(i, p0 - 1)) {
                Kit.parkLamp(v, i, 0, p0 - 1);
            }
        }
        v.connect();
        return v;
    }

    private static void tree(Voxels v, Random r, int i, int j, java.util.function.BiPredicate<Integer, Integer> building) {
        for (int dj = -3; dj <= 3; dj++) {
            for (int di = -3; di <= 3; di++) {
                if (building.test(i + di, j + dj)) {
                    return;
                }
            }
        }
        if (v.get(i, 0, j) != null || v.get(i, -1, j) == null || !v.get(i, -1, j).equals(GRASS)) {
            return;
        }
        if (r.nextBoolean()) {
            // 벚나무: 짧은 줄기, 분홍 꽃 잎
            v.set(i, -1, j, COARSE_DIRT);
            v.fill(i, 0, j, i, 2, j, Block.of("cherry_log[axis=y]", 0x3A2328));
            v.ellipsoid(i + 0.5, 4.2, j + 0.5, 2.6, 1.8, 2.6, CHERRY);
            v.fill(i, 0, j, i, 3, j, Block.of("cherry_log[axis=y]", 0x3A2328));
        } else {
            Kit.ginkgo(v, i, j, -1, r);
        }
    }

    private School() {
    }
}
