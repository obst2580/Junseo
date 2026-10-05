package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 한강변 카지노 리조트 (워커힐처럼): 준서카지노 호텔.
 * <ul>
 *   <li>2층 카지노 포디움(창 없는 베이지 돌 벽과 금빛 세로 띠) 뒤쪽 위로 16층 호텔 타워(짙은 유리, 베이지 틀).
 *       모두 18층. 정문 앞 큰 포르트코셰르(차 대는 곳 지붕)와 이름 표지판, 차로(차는 없음)</li>
 *   <li>1층(높이 8): 가운데 현관 홀과 샹들리에, 동쪽 호텔 로비(프런트 데스크, 소파, 벨 데스크), 서쪽과 뒤쪽은 카지노:
 *       바카라·블랙잭 테이블(초록 양털 판, 나무 테두리, 의자), 룰렛 테이블, 슬롯머신 줄과 의자, 캐셔(환전) 창구 철창,
 *       입장 확인 데스크, 바, 무늬 카펫 바닥, 샹들리에</li>
 *   <li>2층: VIP 룸(바카라 테이블, 소파, 바), 뷔페 식당, 라운지. 3층에서 나가는 포디움 옥상 수영장과 선베드</li>
 *   <li>3~17층 호텔 객실(욕실 있는 2인실), 18층 스카이 라운지 바. 가운데 코어: 계단, 엘리베이터 3대, 층마다 화장실</li>
 * </ul>
 * 정면은 남쪽(j = d-1, 놓을 때 큰길 쪽). 앞 {@link #FRONT} 줄은 차로와 포르트코셰르 자리입니다.
 */
final class Casino {
    static final String NAME = "준서카지노 호텔";
    static final int FLOORS = 18, PODIUM = 2, FRONT = 13;
    /** 타워 뒤·앞 벽 (j), 포디움 앞 벽은 d-1-FRONT */
    static final int TJ0 = 2, TJ1 = 33;
    static final int MIN_W = 66, MIN_D = 58;

    private static final Block STONE = SANDSTONE;
    private static final Block GOLD = Block.of("yellow_terracotta", 0xBA8523);
    private static final Block WALL = Interior.INNER_WALL;
    private static final Block CARPET = Block.of("red_terracotta", 0x8F3D2E);
    private static final Block CARPET_DOT = Block.of("orange_terracotta", 0xA15325);
    private static final Block MARBLE = Block.of("smooth_quartz", 0xECE6DF);
    private static final Block MARBLE_LINE = Block.of("polished_granite", 0x9A6A59);
    private static final Block RIM = Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14);
    private static final Block FELT = Block.of("green_wool", 0x546D1B);

    private final int w, d, pf, ti0, ti1, mid;
    private final Random r;
    private Tower t;

    private Casino(int w, int d, Random r) {
        this.w = w;
        this.d = d;
        this.r = r;
        pf = d - 1 - FRONT;
        ti0 = 10;
        ti1 = w - 11;
        mid = w / 2;
    }

    static Voxels build(int w, int d, Random r) {
        Casino c = new Casino(w, d, r);
        Tower.Spec s = new Tower.Spec(w, d, FLOORS);
        s.lobbyH = Floors.HALL;
        s.typicalH = Floors.OFFICE;
        s.shape = (i, j, k) -> k < PODIUM
                ? i >= 2 && i <= w - 3 && j >= TJ0 && j <= c.pf
                : i >= c.ti0 && i <= c.ti1 && j >= TJ0 && j <= TJ1;
        s.use = k -> Tower.Use.CUSTOM;
        s.custom = (tw, k, level, height) -> {
            c.t = tw;
            c.floor(k, level, height);
        };
        s.glass = Block.of("black_stained_glass", 0x191919);
        s.mullion = STONE;
        s.spandrel = STONE;
        s.podium = MARBLE;
        s.mullionEvery = 3;
        s.elevators = 3;
        s.restrooms = 2;
        s.extraTop = 6;
        Tower tw = Tower.build(s, new Random(r.nextLong()));
        c.t = tw;
        c.podiumFacade();
        c.front();
        c.pool();
        c.crown();
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                tw.v.setIfEmpty(i, -1, j, LIGHT_GRAY_CONCRETE);
            }
        }
        tw.v.connect();
        return tw.v;
    }

    private void floor(int k, int level, int h) {
        if (k == 0) {
            gamingFloor(level, h);
        } else if (k == 1) {
            secondFloor(level, h);
        } else if (k == FLOORS - 1) {
            skyLounge(level, h);
        } else {
            hotelFloor(k, level, h);
        }
    }

    /** 바닥 마감 (코어는 건드리지 않음) */
    private void finish(int i0, int j0, int i1, int j1, int level, Block a, Block b) {
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (i >= t.ci0 && i <= t.ci1 && j >= t.cj0 && j <= t.cj1) {
                    continue;
                }
                boolean dot = b != null && Math.floorMod(i + j, 4) == 0 && Math.floorMod(i - j, 4) == 0;
                t.v.set(i, level - 1, j, dot ? b : a);
            }
        }
    }

    // ------------------------------------------------------------------ 1층: 카지노와 호텔 로비

    private void gamingFloor(int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        int top = level + h - 2;
        int lobby0 = mid + 9; // 호텔 로비 서쪽 벽 i
        int foyer0 = mid - 8, foyerJ = pf - 10;
        // 바닥: 카지노 무늬 카펫, 홀·로비 대리석
        finish(3, TJ0 + 1, w - 4, pf - 1, level, CARPET, CARPET_DOT);
        finish(foyer0, foyerJ, lobby0 - 1, pf - 1, level, MARBLE, null);
        finish(lobby0, t.corridorJ + 2, w - 4, pf - 1, level, MARBLE, null);
        for (int j = foyerJ; j < pf; j += 4) {
            for (int i = foyer0; i < w - 3; i++) {
                if (v.get(i, level - 1, j) != null && v.get(i, level - 1, j).equals(MARBLE)) {
                    v.set(i, level - 1, j, MARBLE_LINE);
                }
            }
        }
        // 호텔 로비 벽 (카지노와 사이, 넓은 통로 남김)
        v.fill(lobby0 - 1, level, t.corridorJ + 2, lobby0 - 1, top, foyerJ - 1, STONE);
        v.fill(lobby0 - 1, level, foyerJ - 1, lobby0 - 1, level + 3, foyerJ + 1, AIR);
        v.fill(lobby0 - 1, level + 4, t.corridorJ + 2, lobby0 - 1, top, pf - 1, STONE);
        // 현관 홀과 카지노 사이: 입장 확인 데스크와 낮은 칸막이
        for (int j = foyerJ; j < pf; j++) {
            if (Math.abs(j - (foyerJ + 4)) > 2) {
                v.set(foyer0 - 1, level, j, GOLD);
                v.set(foyer0 - 1, level + 1, j, Block.of("glass_pane", 0xC8DCE4));
            }
        }
        for (int j = foyerJ + 2; j <= foyerJ + 6; j++) {
            if (Math.abs(j - (foyerJ + 4)) == 2) {
                v.set(foyer0 + 1, level, j, Furniture.COUNTER);
            }
        }
        Furniture.chair(f, foyer0 + 2, level, foyerJ + 2, "east", "dark_oak");
        v.set(foyer0 - 1, top, foyerJ + 4, Blocks.hangingSign("dark_oak", 4, "yellow", true, "CASINO", "카지노", "입장 확인"));
        // 호텔 프런트 데스크 (로비 동쪽 벽, 서쪽을 봄)
        int fd = w - 7;
        for (int j = t.corridorJ + 5; j <= t.corridorJ + 15; j++) {
            v.set(fd, level, j, Furniture.COUNTER);
            if (j % 3 == 0) {
                v.set(fd, level + 1, j, Kit.MONITOR);
                Furniture.chair(Frame.of(v), fd + 1, level, j, "east", "dark_oak");
            }
        }
        v.fill(w - 4, level, t.corridorJ + 4, w - 4, level + 3, t.corridorJ + 16, Block.of("dark_oak_planks", 0x432B14));
        v.set(w - 5, level + 3, t.corridorJ + 10, Blocks.wallSign("dark_oak", "west", "yellow", true, "", NAME, "FRONT DESK"));
        v.set(fd, top, t.corridorJ + 10, Blocks.hangingSign("dark_oak", 4, "yellow", true, "프런트", "체크인·체크아웃"));
        // 로비 소파와 탁자, 벨 데스크
        for (int j = t.corridorJ + 6; j < pf - 4; j += 6) {
            for (int i = lobby0 + 2; i < fd - 4; i += 7) {
                Furniture.sofa(f, r, i, level, j, 3, "south");
                v.fill(i, level, j + 1, i + 2, level, j + 1, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
                Furniture.sofa(f, r, i, level, j + 2, 3, "north");
                Furniture.plant(f, r, i + 4, level, j + 1);
            }
        }
        v.set(lobby0 + 1, level, pf - 3, Furniture.COUNTER);
        v.set(lobby0 + 2, level, pf - 3, Furniture.COUNTER);
        // 샹들리에 (현관 홀, 로비)
        chandelier(mid, level, h, foyerJ + 5);
        chandelier((lobby0 + w - 4) / 2, level, h, t.corridorJ + 12);

        // ---- 카지노 ----
        int cw0 = 3, cw1 = foyer0 - 2; // 서쪽 테이블 구역 i
        int tj0 = t.corridorJ + 3;
        // 슬롯머신: 서쪽 벽 쪽 세로 줄, 코어 뒤 가로 줄
        for (int j = TJ0 + 2; j <= pf - 3; j++) {
            if (j % 6 == 0) {
                continue;
            }
            slot(f, cw0 + 3, level, j, "west");
            slot(f, cw0 + 4, level, j, "east");
        }
        for (int i = cw0 + 8; i <= w - 6; i++) {
            if (i % 7 == 0 || (i > t.ci0 - 4 && i < t.ci0 + 8)) {
                continue;
            }
            slot(f, i, level, TJ0 + 4, "north");
            slot(f, i, level, TJ0 + 5, "south");
        }
        // 코어 서쪽 (복도 사이) 슬롯 줄
        for (int j = t.cj0; j <= t.cj1; j++) {
            if (j == t.cj0 + 4) {
                continue;
            }
            slot(f, cw0 + 9, level, j, "west");
            slot(f, cw0 + 10, level, j, "east");
        }
        // 캐셔 (환전) 창구: 남서쪽 구석, 철창 창구
        cage(f, cw0, level, h, pf - 9, pf - 1);
        // 바: 홀 쪽 카지노 앞, 바 의자
        int bj = pf - 3, bi0 = cw0 + 10, bi1 = Math.min(cw1 - 2, bi0 + 10);
        for (int i = bi0; i <= bi1; i++) {
            v.set(i, level, bj, Block.of("dark_oak_planks", 0x432B14));
            v.set(i, level, bj + 2, i % 2 == 0 ? Block.of("glass", 0xC8DCE4) : Block.of("dark_oak_planks", 0x432B14));
            v.set(i, level + 1, bj + 2, i % 3 == 0 ? LANTERN : Block.of("glass", 0xC8DCE4));
            v.set(i, level, bj - 1, Block.of("mangrove_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x763631));
        }
        v.set((bi0 + bi1) / 2, top, bj, Blocks.hangingSign("dark_oak", 0, "yellow", true, "BAR", "바"));
        // 테이블: 빈 카펫 자리마다 바카라·블랙잭·룰렛
        tables(level, 3, TJ0 + 7, w - 4, pf - 2, new String[]{"바카라", "블랙잭", "바카라", "룰렛"});
        // 카지노 샹들리에
        for (int j = tj0 + 4; j < pf - 4; j += 10) {
            for (int i = cw0 + 8; i < cw1; i += 12) {
                chandelier(i, level, h, j);
            }
        }
        for (int i = 8; i < w - 8; i += 10) {
            chandelier(i, level, h, TJ0 + 7);
        }
        Interior.lights(f, 3, TJ0 + 1, w - 4, pf - 1, level, h, 6, Block.of("ochre_froglight[axis=y]", 0xF5E9B6));
    }

    /**
     * 빈 카펫 자리에 게임 테이블을 격자로 놓습니다 (테이블 5×4 와 둘레 1칸이 비어 있고, 코어 둘레 복도가 아닌 곳).
     * games 를 돌아가며 씀 ("룰렛" 은 룰렛 테이블)
     */
    private void tables(int level, int i0, int j0, int i1, int j1, String[] games) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        int n = 0;
        for (int j = j0; j + 4 <= j1; j += 7) {
            for (int i = i0; i + 6 <= i1; ) {
                if (tableFits(level, i, j)) {
                    String g = games[n++ % games.length];
                    if (g.equals("룰렛")) {
                        roulette(f, i, level, j);
                    } else {
                        cardTable(f, i, level, j, g);
                    }
                    i += 8;
                } else {
                    i += 2;
                }
            }
        }
    }

    private boolean tableFits(int level, int i, int j) {
        Voxels v = t.v;
        for (int b = j - 1; b <= j + 4; b++) {
            for (int a = i - 1; a <= i + 6; a++) {
                if (a >= t.ci0 - 3 && a <= t.ci1 + 3 && b >= t.cj0 - 3 && b <= t.cj1 + 3) {
                    return false;
                }
                if (!t.inside(a, b, 0) || t.edge(a, b, 0)) {
                    return false;
                }
                for (int y = level; y <= level + 2; y++) {
                    if (v.get(a, y, b) != null) {
                        return false;
                    }
                }
                Block floor = v.get(a, level - 1, b);
                if (floor == null || !(floor.equals(CARPET) || floor.equals(CARPET_DOT) || floor.id().contains("brown_terracotta"))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 샹들리에: 천장 아래 빛 블록과 매달린 등 */
    private void chandelier(int i, int level, int h, int j) {
        Voxels v = t.v;
        int y = level + h - 2;
        if (v.get(i, y, j) != null) {
            return;
        }
        v.set(i, y, j, Block.of("shroomlight", 0xF09246));
        v.set(i, y - 1, j, Kit.CHAIN);
        v.set(i, y - 2, j, Block.of("gold_block", 0xF6D03D));
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (v.get(i + o[0], y - 1, j + o[1]) == null) {
                v.set(i + o[0], y - 1, j + o[1], LANTERN_HANGING);
            }
        }
    }

    /** 카드 테이블 (5×3): 가운데 초록 양털, 둘레 나무 테두리, 손님 쪽(+b) 의자 다섯, 딜러는 반대쪽 */
    private void cardTable(Frame f, int a, int level, int b, String game) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++) {
                boolean felt = x >= 1 && x <= 3 && y == 1;
                f.set(a + x, level, b + y, felt ? FELT : RIM);
            }
            Furniture.chair(f, a + x, level, b + 3, "south", "mangrove");
        }
        f.set(a + 2, level + 1, b + 1, Block.of("green_carpet", 0x546D1B));
        f.set(a + 2, level + 3, b + 1, Blocks.hangingSign("dark_oak", 0, "yellow", true, game));
    }

    /** 룰렛 테이블 (6×3): 한쪽 끝 나무 회전판, 초록 배팅판, 손님 의자 */
    private void roulette(Frame f, int a, int level, int b) {
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 3; y++) {
                Block blk;
                if (x <= 1) {
                    blk = x == 0 && y == 1 ? Block.of("stripped_dark_oak_wood[axis=y]", 0x4A3A2A) : Block.of("dark_oak_planks", 0x432B14);
                } else {
                    blk = y == 1 && x <= 4 ? FELT : RIM;
                }
                f.set(a + x, level, b + y, blk);
            }
            if (x >= 2) {
                Furniture.chair(f, a + x, level, b + 3, "south", "mangrove");
            }
        }
        f.set(a, level + 1, b + 1, Block.of("gold_block", 0xF6D03D));
        f.set(a + 3, level + 3, b + 1, Blocks.hangingSign("dark_oak", 0, "yellow", true, "룰렛"));
    }

    /** 슬롯머신 한 대: 검은 몸체, 화면, 위 불빛, 앞(face 쪽)에 의자 */
    private void slot(Frame f, int a, int level, int b, String face) {
        if (!f.empty(a, level, b)) {
            return;
        }
        f.set(a, level, b, Block.of("black_concrete", 0x080A0F));
        f.set(a, level + 1, b, Block.of("light_blue_stained_glass", 0x6699D8));
        f.set(a, level + 2, b, (a + b) % 2 == 0 ? Block.of("yellow_stained_glass", 0xE5E533) : Block.of("red_stained_glass", 0x993333));
        int[] s = Furniture.step(face);
        if (f.empty(a + s[0], level, b + s[1])) {
            Furniture.chair(f, a + s[0], level, b + s[1], Furniture.opposite(face), "mangrove");
        }
    }

    /** 캐셔 창구: 철창 창구 카운터, 안쪽 책상과 금고, 뒤 출입문 */
    private void cage(Frame f, int i0, int level, int h, int j0, int j1) {
        Voxels v = t.v;
        int i1 = i0 + 6, top = level + h - 2;
        v.fill(i0, level, j0, i1, top, j0, WALL);
        v.fill(i1, level, j0, i1, top, j1, WALL);
        for (int j = j0 + 1; j < j1; j++) {
            v.set(i1, level, j, Furniture.COUNTER);
            v.fill(i1, level + 1, j, i1, level + 2, j, IRON_BARS);
        }
        v.fill(i0, level, j0 + 1, i0 + 1, level + 1, j0 + 2, IRON_BLOCK); // 금고
        for (int j = j0 + 2; j < j1; j += 2) {
            Furniture.chair(f, i1 - 1, level, j, "west", "dark_oak");
        }
        v.set(i0 + 3, level, j0, Kit.door("dark_oak", "north", false, false));
        v.set(i0 + 3, level + 1, j0, Kit.door("dark_oak", "north", true, false));
        v.set(i1 + 1, top, (j0 + j1) / 2, Blocks.hangingSign("dark_oak", 12, "yellow", true, "CASHIER", "캐셔", "칩 환전"));
        v.set((i0 + i1) / 2, top, (j0 + j1) / 2, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 2층

    private void secondFloor(int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        finish(3, TJ0 + 1, w - 4, pf - 1, level, Block.of("brown_terracotta", 0x4D3323), null);
        // VIP 룸: 앞쪽 서쪽 절반, 복도(j pf-12..pf-10) 남쪽
        int cj = pf - 12;
        v.fill(3, level, cj - 1, mid + 6, level + h - 2, cj - 1, WALL);
        Frame vip = Frame.facing(v, 3, cj + 3, "south");
        Kit.strip(vip, 0, mid + 3 - 3, pf - 1 - (cj + 3) + 1, level, h, 9, WALL, false, (rf, rw, rd, n) -> {
            Kit.doubleDoor(rf, rw / 2 - 1, level, -1, "dark_oak", "south");
            if (rf.empty(rw / 2 + 1, level + 1, -2)) {
                rf.set(rw / 2 + 1, level + 1, -2, Blocks.wallSign("dark_oak", "north", "yellow", true, "", "VIP " + (n + 1)));
            }
            Kit.finish(rf, 0, 0, rw - 1, rd - 1, level, Block.of("red_wool", 0xA12722));
            cardTable(rf, 1, level, 1, "VIP 바카라");
            Furniture.sofa(rf, r, rw - 3, level, rd - 1, 3, "north");
            rf.set(0, level, rd - 1, Block.of("dark_oak_planks", 0x432B14));
            rf.set(0, level + 1, rd - 1, LANTERN);
            Interior.lights(rf, 0, 0, rw - 1, rd - 1, level, h, 4, Block.of("ochre_froglight[axis=y]", 0xF5E9B6));
        });
        // 뷔페 식당: 앞쪽 동쪽
        int b0 = mid + 8;
        v.fill(b0 - 1, level, t.corridorJ + 3, b0 - 1, level + h - 2, pf - 1, WALL);
        Interior.door(Frame.facing(v, b0 - 1, t.corridorJ + 6, "west"), 0, level, 0, "dark_oak", "south");
        v.set(b0, level + 2, t.corridorJ + 8, Blocks.hangingSign("dark_oak", 12, "yellow", true, "뷔페", "BUFFET"));
        for (int i = b0 + 1; i < w - 5; i++) {
            v.set(i, level, t.corridorJ + 4, i % 5 == 0 ? CAULDRON : Furniture.COUNTER);
            if (i % 5 == 2) {
                v.set(i, level + 1, t.corridorJ + 4, Block.of("glass", 0xC8DCE4));
            }
        }
        for (int j = t.corridorJ + 7; j < pf - 1; j += 4) {
            for (int i = b0 + 1; i < w - 5; i += 4) {
                Furniture.table(f, i, level, j, 2, 1, "dark_oak");
            }
        }
        // 가운데: 포커 룸 테이블
        tables(level, 3, t.corridorJ + 3, mid + 6, pf - 13, new String[]{"포커", "포커", "블랙잭"});
        // 라운지: 코어 뒤 (뒤쪽 줄)
        for (int i = 6; i < w - 6; i += 8) {
            Furniture.sofa(f, r, i, level, TJ0 + 2, 3, "south");
            v.fill(i, level, TJ0 + 4, i + 2, level, TJ0 + 4, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
            Furniture.plant(f, r, i + 4, level, TJ0 + 2);
        }
        Interior.lights(f, 3, TJ0 + 1, w - 4, pf - 1, level, h, 5, Block.of("ochre_froglight[axis=y]", 0xF5E9B6));
    }

    // ------------------------------------------------------------------ 호텔 층

    private void hotelFloor(int k, int level, int h) {
        Voxels v = t.v;
        int top = level + h - 2;
        int i0 = ti0 + 1, i1 = ti1 - 1;
        int cn = t.cj0 - 2, cs = t.corridorJ;
        finish(i0, TJ0 + 1, i1, TJ1 - 1, level, Block.of("brown_concrete_powder", 0x7D5435), null);
        // 복도 (북·남) 를 타워 끝까지, 카펫 바닥
        v.fill(i0, level, cn, i1, top, cn + 1, AIR);
        v.fill(i0, level, cs, i1, top, cs + 1, AIR);
        for (int i = i0; i <= i1; i++) {
            v.set(i, level - 1, cn, Block.of("red_wool", 0xA12722));
            v.set(i, level - 1, cn + 1, Block.of("red_wool", 0xA12722));
            v.set(i, level - 1, cs, Block.of("red_wool", 0xA12722));
            v.set(i, level - 1, cs + 1, Block.of("red_wool", 0xA12722));
        }
        for (int i = i0 + 2; i <= i1 - 2; i += 5) {
            v.set(i, top, cn, Interior.LIGHT);
            v.set(i, top, cs + 1, Interior.LIGHT);
        }
        // 북쪽 객실 (+b 북쪽), 남쪽 객실
        int nDepth = cn - 1 - (TJ0 + 1);
        Kit.strip(Frame.facing(v, i1, cn - 2, "north"), 0, i1 - i0, nDepth, level, h, 6, WALL, false, (rf, rw, rd, n) -> {
            Rooms.hotel(rf, r, rw, rd, level, h);
            roomNo(rf, level, k, n);
        });
        int sDepth = TJ1 - 1 - (cs + 3) + 1;
        boolean poolDoor = k == PODIUM;
        int poolRoom = (mid - i0) / 7;
        Kit.strip(Frame.facing(v, i0, cs + 3, "south"), 0, i1 - i0, sDepth, level, h, 6, WALL, false, (rf, rw, rd, n) -> {
            if (poolDoor && n == poolRoom) {
                Interior.door(rf, rw / 2, level, -1, "dark_oak", "south");
                Interior.door(rf, rw / 2, level, rd, "dark_oak", "south");
                rf.set(rw / 2 + 1, level + 1, -2, Blocks.wallSign("dark_oak", "north", "yellow", true, "", "수영장", "POOL"));
                Furniture.plant(rf, r, 0, level, rd - 1);
                return;
            }
            Rooms.hotel(rf, r, rw, rd, level, h);
            roomNo(rf, level, k, 20 + n);
        });
        // 양 끝 (복도 사이): 서쪽 객실 관리실, 동쪽 비상 휴게
        int wEnd = t.ci0 - 3, eEnd = t.ci1 + 3;
        v.fill(i0, level, cn + 2, wEnd, top, cn + 2, WALL);
        v.fill(wEnd + 1, level, cn + 2, wEnd + 1, top, cs - 1, WALL);
        Interior.door(Frame.facing(v, wEnd + 1, cs - 2, "east"), 0, level, 0, "pale_oak", "south");
        v.fill(i0, level, cs - 1, wEnd, top, cs - 1, WALL);
        v.fill(i0 + 1, level, cn + 3, i0 + 1, level + 1, cs - 2, Block.of("white_wool", 0xE9ECEC));
        v.set(wEnd + 2, level + 1, cs - 3, Blocks.wallSign("birch", "east", "black", false, "", "객실 관리실"));
        v.fill(eEnd, level, cn + 2, i1, top, cn + 2, WALL);
        v.fill(eEnd - 1, level, cn + 2, eEnd - 1, top, cs - 1, WALL);
        Interior.door(Frame.facing(v, eEnd - 1, cs - 2, "west"), 0, level, 0, "pale_oak", "south");
        v.fill(eEnd, level, cs - 1, i1, top, cs - 1, WALL);
        v.set(i1, level, cn + 4, IRON_BLOCK);
        v.set(i1, level + 1, cn + 4, Block.of("light_blue_stained_glass", 0x6699D8));
        v.set(eEnd - 2, level + 1, cs - 3, Blocks.wallSign("birch", "west", "black", false, "", "자판기·제빙기"));
        v.set((eEnd + i1) / 2, top, (cn + cs) / 2, Interior.LIGHT);
        v.set((i0 + wEnd) / 2, top, (cn + cs) / 2, Interior.LIGHT);
    }

    /** 객실 번호 (문 옆 복도 벽) */
    private static void roomNo(Frame f, int level, int k, int n) {
        if (f.empty(2, level + 1, -2)) {
            f.set(2, level + 1, -2, Blocks.wallSign("dark_oak", "north", "yellow", false, "", (k + 1) + String.format("%02d", n + 1) + "호"));
        }
    }

    /** 맨 위층 스카이 라운지: 창가 소파와 탁자, 가운데 바 */
    private void skyLounge(int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        int i0 = ti0 + 1, i1 = ti1 - 1;
        finish(i0, TJ0 + 1, i1, TJ1 - 1, level, Block.of("dark_oak_planks", 0x432B14), null);
        for (int i = i0 + 1; i < i1 - 2; i += 5) {
            Furniture.sofa(f, r, i, level, TJ1 - 2, 3, "north");
            v.fill(i, level, TJ1 - 4, i + 2, level, TJ1 - 4, Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
            Furniture.sofa(f, r, i, level, TJ0 + 2, 3, "south");
            v.fill(i, level, TJ0 + 4, i + 2, level, TJ0 + 4, Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
        }
        for (int i = t.ci0; i <= t.ci1; i++) {
            v.set(i, level, t.corridorJ + 3, Block.of("dark_oak_planks", 0x432B14));
            if (i % 2 == 0) {
                Furniture.chair(f, i, level, t.corridorJ + 4, "south", "mangrove");
            }
        }
        v.set(mid, level + 2, t.corridorJ + 3, Blocks.hangingSign("dark_oak", 0, "yellow", true, "SKY LOUNGE", "스카이 라운지"));
        Interior.lights(f, i0, TJ0 + 1, i1, TJ1 - 1, level, h, 5, Block.of("ochre_froglight[axis=y]", 0xF5E9B6));
    }

    // ------------------------------------------------------------------ 바깥

    /** 포디움: 창 없는 베이지 돌 벽, 금빛 세로 띠, 호텔 로비 쪽만 큰 유리 */
    private void podiumFacade() {
        Voxels v = t.v;
        int lobby0 = mid + 9;
        for (int k = 0; k < PODIUM; k++) {
            int level = t.levels[k], top = t.levels[k + 1] - 2;
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    if (!t.edge(i, j, k)) {
                        continue;
                    }
                    boolean front = j == pf;
                    boolean lobbyGlass = front && i >= lobby0 && i <= w - 4 && k == 0;
                    for (int y = level; y <= top; y++) {
                        Block b;
                        if (lobbyGlass) {
                            b = i % 4 == 0 ? STONE : Block.of("light_blue_stained_glass", 0x6699D8);
                        } else {
                            b = (i + j) % 6 == 0 ? GOLD : STONE;
                        }
                        v.set(i, y, j, b);
                    }
                    v.set(i, top + 1, j, Block.of("smooth_stone", 0x9E9E9E));
                }
            }
        }
        // 포디움 옥상 난간 (타워 밖)
        int y = t.levels[PODIUM];
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (t.edge(i, j, PODIUM - 1) && !t.inside(i, j, PODIUM)) {
                    v.set(i, y, j, STONE);
                    v.set(i, y + 1, j, Block.of("glass_pane", 0xC8DCE4));
                }
            }
        }
    }

    /** 정문: 회전문 대신 열린 유리문, 포르트코셰르 지붕과 기둥, 이름 표지판, 차로 (차는 없음), 보행 광장 */
    private void front() {
        Voxels v = t.v;
        int cy = 6;
        // 차로
        v.fill(0, -1, pf + 1, w - 1, -1, d - 1, GRAY_CONCRETE);
        v.fill(2, -1, pf + 1, w - 3, -1, pf + 2, POLISHED_ANDESITE);
        // 정문 (유리 벽 사이 열린 문 5칸)
        for (int i = mid - 6; i <= mid + 6; i++) {
            v.fill(i, 0, pf, i, 5, pf, Math.abs(i - mid) <= 2 ? AIR : Block.of("light_blue_stained_glass_pane", 0x6699D8));
            v.set(i, 6, pf, GOLD);
        }
        v.fill(mid - 2, 3, pf, mid + 2, 5, pf, Block.of("light_blue_stained_glass_pane", 0x6699D8));
        // 포르트코셰르: 넓은 지붕, 네 기둥, 두꺼운 테두리, 밑 조명
        int c0 = mid - 13, c1 = mid + 13, cj1 = pf + 10;
        v.fill(c0, cy, pf + 1, c1, cy, cj1, STONE);
        v.fill(c0, cy + 1, cj1, c1, cy + 1, cj1, STONE);
        v.fill(c0, cy + 1, pf + 1, c0, cy + 1, cj1, STONE);
        v.fill(c1, cy + 1, pf + 1, c1, cy + 1, cj1, STONE);
        v.fill(c0, cy + 2, cj1, c1, cy + 2, cj1, GOLD);
        for (int i : new int[]{c0 + 1, c1 - 1}) {
            for (int j : new int[]{pf + 2, cj1 - 1}) {
                v.fill(i, 0, j, i, cy - 1, j, Block.of("quartz_pillar[axis=y]", 0xEBE6E0));
            }
        }
        for (int i = c0 + 3; i <= c1 - 3; i += 4) {
            for (int j = pf + 3; j <= cj1 - 2; j += 3) {
                v.set(i, cy, j, Block.of("ochre_froglight[axis=y]", 0xF5E9B6));
            }
        }
        // 이름 표지판 (지붕 앞 금빛 띠)
        for (int i = mid - 4; i <= mid + 4; i += 4) {
            v.set(i, cy + 2, cj1 + 1, Blocks.wallSign("dark_oak", "south", "yellow", true, "", i == mid ? NAME : i < mid ? "CASINO" : "HOTEL"));
        }
        v.set(mid, cy + 1, cj1 + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", "카지노 · 호텔"));
        // 지붕 밑 차 대는 줄 (노란 선) 과 보도
        v.fill(c0 + 2, -1, pf + 4, c1 - 2, -1, pf + 4, YELLOW_CONCRETE);
        v.fill(c0 + 2, -1, cj1 - 3, c1 - 2, -1, cj1 - 3, WHITE_CONCRETE);
        // 카지노 벽 위 표지판
        v.fill(mid - 20, 4, pf + 1, mid - 16, 5, pf + 1, Block.of("black_concrete", 0x080A0F));
        v.set(mid - 18, 5, pf + 2, Blocks.wallSign("dark_oak", "south", "yellow", true, "", "CASINO", "카지노"));
        v.set(mid - 18, 4, pf + 2, Blocks.wallSign("dark_oak", "south", "white", true, "", "외국인 전용", "24시간"));
        // 보행 광장 (서쪽 끝): 포장, 화분, 벤치 (가운데는 비움)
        int p0 = w - 16, p1 = w - 3;
        for (int j = pf + 1; j < d; j++) {
            for (int i = p0; i <= p1; i++) {
                v.set(i, -1, j, (i + j) % 4 == 0 ? POLISHED_GRANITE : Block.of("smooth_sandstone", 0xDFD6AA));
            }
        }
        for (int i = p0; i <= p1; i += 4) {
            v.set(i, 0, pf + 1, Block.of("potted_azalea_bush", 0x63753A));
        }
        Kit.bench(v, p0 + 1, 0, d - 2, 3, true, "north");
        Kit.bench(v, p1 - 3, 0, d - 2, 3, true, "north");
        // 차로 가운데선
        for (int i = 2; i < p0 - 1; i += 3) {
            v.set(i, -1, d - 4, WHITE_CONCRETE);
        }
    }

    /** 포디움 옥상 수영장: 타워 앞 데크, 한 칸 높인 수영장(테두리 돌), 선베드, 파라솔, 난간 */
    private void pool() {
        Voxels v = t.v;
        int y = t.levels[PODIUM];
        int j0 = TJ1 + 2, j1 = pf - 2;
        for (int j = j0; j <= j1; j++) {
            for (int i = 4; i <= w - 5; i++) {
                v.set(i, y - 1, j, (i + j) % 2 == 0 ? Block.of("stripped_spruce_wood[axis=y]", 0x735A3A) : Block.of("spruce_planks", 0x725430));
            }
        }
        int p0 = 12, p1 = w - 13, q0 = j0 + 4, q1 = j1 - 3;
        if (q1 - q0 < 4) {
            return;
        }
        for (int j = q0; j <= q1; j++) {
            for (int i = p0; i <= p1; i++) {
                boolean rim = i == p0 || i == p1 || j == q0 || j == q1;
                v.set(i, y - 1, j, rim ? MARBLE : Block.of("light_blue_terracotta", 0x716C89));
                v.set(i, y, j, rim ? MARBLE : Blocks.WATER);
            }
        }
        for (int i = p0 + 3; i < p1; i += 6) {
            v.set(i, y - 1, (q0 + q1) / 2, SEA_LANTERN);
        }
        // 선베드와 파라솔 (수영장 앞뒤)
        for (int i = p0; i <= p1 - 2; i += 5) {
            for (int j : new int[]{q0 - 2, q1 + 2}) {
                v.set(i, y, j, Block.of("white_wool", 0xE9ECEC));
                v.set(i + 1, y, j, Block.of("white_carpet", 0xE9ECEC));
            }
            int pj = q1 + 2;
            v.fill(i + 3, y, pj, i + 3, y + 2, pj, Block.of("oak_fence", 0xA2834F));
            v.fill(i + 2, y + 3, pj - 1, i + 4, y + 3, pj + 1, Block.of("white_wool", 0xE9ECEC));
        }
    }

    /** 타워 꼭대기 테두리 (금빛 띠) */
    private void crown() {
        Voxels v = t.v;
        int y = t.roofLevel;
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (t.edge(i, j, FLOORS - 1)) {
                    v.set(i, y + 1, j, GOLD);
                    v.set(i, y + 2, j, STONE);
                }
            }
        }
    }
}
