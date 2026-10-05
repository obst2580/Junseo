package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.Column;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 북한산 채석장 블록 그리기: 화강암 채굴면, 운반로, 바닥의 파쇄·선별 설비와 건물들.
 * 북한산은 화강암이라 갓 깎은 면은 밝은 회색(섬록암·돌·안산암 줄무늬), 가끔 분홍 화강암 맥과 풍화된 응회암.
 */
final class Quarry {
    static final Block GRAVEL = Block.of("gravel", 0x837F7E);
    static final Block COARSE = Block.of("coarse_dirt", 0x77563B);
    static final Block CONCRETE = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block STEEL = Block.of("iron_bars", 0x888888);
    static final Block BELT = Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637);
    static final Block BELT_TOP = Block.of("deepslate_tile_slab[type=top,waterlogged=false]", 0x363637);
    static final Block CLAD = Block.of("cyan_terracotta", 0x565B5B);
    static final Block PANEL = Block.of("white_concrete", 0xCFD5D6);

    /**
     * 채굴면 바위 (자리마다 정해진 블록). 3칸 덩어리마다 한 가지 돌(층리처럼 덩어리 높이가 엇갈림)에
     * 칸마다 조금 섞음: 돌·섬록암(밝은 화강암 느낌)·안산암이 대부분, 가끔 풍화된 응회암과 철분 얼룩(화강암).
     */
    static Block rock(int x, int y, int z) {
        int px = Math.floorDiv(x, 3), pz = Math.floorDiv(z, 3);
        int py = Math.floorDiv(y + (int) Math.floorMod(px * 5 + pz * 3, 3), 3);
        int patch = (int) Math.floorMod(mix(px, py, pz), 100);
        int cell = (int) Math.floorMod(mix(x, y, z) >>> 7, 100);
        if (cell < 6) {
            return Block.of("cobblestone", 0x7F7F7F);   // 금 간 곳
        }
        if (cell < 9) {
            return Block.of("andesite", 0x888888);
        }
        if (patch < 34) {
            return Block.of("stone", 0x7E7E7E);
        }
        if (patch < 62) {
            return Block.of("diorite", 0xBCBCBC);
        }
        if (patch < 84) {
            return Block.of("andesite", 0x888888);
        }
        if (patch < 95) {
            return Block.of("tuff", 0x6C6D66);
        }
        return Block.of("granite", 0x956755);
    }

    private static long mix(int a, int b, int c) {
        long h = (a * 73856093L) ^ (b * 19349663L) ^ (c * 83492791L);
        h ^= h >>> 13;
        h *= 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 29);
    }

    /** 단 바닥·야드 바닥 (깨진 돌·자갈·흙) */
    static Block rubble(int x, int z, boolean yard) {
        int roll = (int) Math.floorMod((x * 341873128712L) ^ (z * 132897987541L), 100);
        if (yard) {
            return roll < 50 ? GRAVEL : roll < 75 ? Block.of("andesite", 0x888888) : roll < 93 ? Block.of("stone", 0x7E7E7E) : COARSE;
        }
        return roll < 50 ? GRAVEL : roll < 75 ? Block.of("andesite", 0x888888) : roll < 93 ? Block.of("cobblestone", 0x7F7F7F) : COARSE;
    }

    // ------------------------------------------------------------------ 채석장 (땅)

    static Voxels pit(QuarryPlan.Site s, Ground g, int x0, int z0, int w, int d, Random r) {
        int n = w * d;
        int[] old = new int[n], top = new int[n];
        boolean[] slab = new boolean[n], carved = new boolean[n], ramp = new boolean[n];
        int hi = s.floor;
        boolean[] want = new boolean[n];
        double[] targets = new double[n];
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int k = j * w + i, x = x0 + i, z = z0 + j;
                Column c = g.column(i, j);
                int mh = c.mountainHeight;
                old[k] = mh - 1;
                top[k] = mh - 1;
                hi = Math.max(hi, mh);
                if (mh <= 0 || c.isRoad() || c.isWater()) {
                    continue;
                }
                double rs = s.rampStand(x, z);
                double target;
                if (!Double.isNaN(rs)) {
                    target = rs;
                    ramp[k] = true;
                } else {
                    int b = s.benchStand(x, z);
                    if (b > mh || (b == mh && !s.inYard(x, z))) {
                        continue;
                    }
                    target = b;
                }
                want[k] = true;
                targets[k] = target;
            }
        }
        // 바닥에서 이어진 곳만 깎음 (산비탈에 따로 떨어진 조각은 두지 않음)
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        int start = (s.fz - z0) * w + (s.fx - x0);
        if (want[start]) {
            carved[start] = true;
            queue.add(start);
        }
        while (!queue.isEmpty()) {
            int k = queue.poll();
            int ci = k % w, cj = k / w;
            int[] nbs = {ci > 0 ? k - 1 : -1, ci + 1 < w ? k + 1 : -1, cj > 0 ? k - w : -1, cj + 1 < d ? k + w : -1};
            for (int nk : nbs) {
                if (nk >= 0 && want[nk] && !carved[nk]) {
                    carved[nk] = true;
                    queue.add(nk);
                }
            }
        }
        for (int k = 0; k < n; k++) {
            if (!carved[k]) {
                ramp[k] = false;
                continue;
            }
            double target = targets[k];
            slab[k] = ramp[k] && target - Math.floor(target) >= 0.5;
            top[k] = slab[k] ? (int) Math.floor(target) : (int) Math.floor(target) - 1;
        }
        Voxels v = new Voxels(w, d, s.floor - 4, hi + 2);
        Block rampSlab = Block.of("andesite_slab[type=bottom,waterlogged=false]", 0x888888);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int k = j * w + i, x = x0 + i, z = z0 + j;
                if (!carved[k]) {
                    continue;
                }
                for (int y = top[k] + 1; y <= old[k]; y++) {
                    v.set(i, y, j, AIR);
                }
                for (int y = old[k] + 1; y < top[k]; y++) {
                    v.set(i, y, j, rock(x, y, z)); // 운반로 쌓은 곳
                }
                boolean yard = s.inYard(x, z);
                if (slab[k]) {
                    v.set(i, top[k] - 1, j, Block.of("andesite", 0x888888));
                    v.set(i, top[k], j, rampSlab);
                } else {
                    v.set(i, top[k], j, ramp[k] ? Block.of("andesite", 0x888888) : rubble(x, z, yard));
                }
            }
        }
        // 드러난 면은 바위로
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                if (old[k] < 0) {
                    continue;
                }
                int low = top[k];
                boolean near = carved[k];
                for (int[] o : nb) {
                    int ni = i + o[0], nj = j + o[1];
                    if (ni >= 0 && nj >= 0 && ni < w && nj < d) {
                        low = Math.min(low, top[nj * w + ni]);
                        near |= carved[nj * w + ni];
                    }
                }
                if (!near) {
                    continue;
                }
                for (int y = low + 1; y < top[k] - (slab[k] ? 1 : 0); y++) {
                    v.set(i, y, j, rock(x0 + i, y, z0 + j));
                }
                // 단 끝(아래로 떨어지는 곳)의 안전 둑
                if (carved[k] && !ramp[k] && !s.inYard(x0 + i, z0 + j) && low <= top[k] - 5) {
                    boolean nearRamp = false;
                    for (int[] o : nb) {
                        int ni = i + o[0], nj = j + o[1];
                        nearRamp |= ni >= 0 && nj >= 0 && ni < w && nj < d && ramp[nj * w + ni];
                    }
                    if (!nearRamp) {
                        v.set(i, top[k] + 1, j, Math.floorMod(i * 7 + j * 13, 5) < 3 ? GRAVEL : Block.of("cobblestone", 0x7F7F7F));
                    }
                }
            }
        }
        Yard y = new Yard(v, s, x0, z0);
        y.build(r);
        v.connect();
        return v;
    }

    /** 바닥(야드) 시설: 좌표는 거점(바닥 가운데) 기준 (dx, dz), 높이는 바닥 서는 높이 기준 */
    static final class Yard {
        final Voxels v;
        final QuarryPlan.Site s;
        final int x0, z0, Y;

        Yard(Voxels v, QuarryPlan.Site s, int x0, int z0) {
            this.v = v;
            this.s = s;
            this.x0 = x0;
            this.z0 = z0;
            this.Y = s.floor;
        }

        int i(int dx) {
            return s.fx + dx - x0;
        }

        int j(int dz) {
            return s.fz + dz - z0;
        }

        void set(int dx, int y, int dz, Block b) {
            v.set(i(dx), Y + y, j(dz), b);
        }

        void fill(int dx0, int ya, int dz0, int dx1, int yb, int dz1, Block b) {
            v.fill(i(dx0), Y + ya, j(dz0), i(dx1), Y + yb, j(dz1), b);
        }

        void build(Random r) {
            entranceRoad();
            crusher();
            screen();
            stockpiles();
            // 채굴면 밑 발파석 더미 (실어 갈 돌)
            muck(-6, -27, 4);
            muck(27, -9, 3);
            weighbridge();
            parking();
            PrisonPlan.stamp(v, containerOffice(r), i(-34), Y, j(-10), "south");
            PrisonPlan.stamp(v, oreExchange(r), i(-37), Y, j(2), "east");
            PrisonPlan.stamp(v, scaleHouse(), i(-19), Y, j(15), "east");
            for (int[] p : new int[][]{{-28, -16}, {22, -20}, {-20, 28}, {26, 10}}) {
                fill(p[0], 0, p[1], p[0], 8, p[1], StreetPlan.POST);
                set(p[0], 9, p[1], Block.of("sea_lantern", 0xACC7BE));
            }
        }

        /** 진입로가 야드 안으로 이어지는 시멘트 길 (계근대까지) */
        private void entranceRoad() {
            fill(-13, -1, 8, -7, -1, 30, Block.of("smooth_stone", 0x9E9E9E));
            // 출입 차단기: 기둥과 흰 막대
            fill(-14, 0, 26, -14, 1, 26, Block.of("polished_blackstone_wall", 0x353038));
            for (int dx = -13; dx <= -8; dx++) {
                set(dx, 1, 26, Block.of("end_rod[facing=east]", 0xE0D8C8));
            }
        }

        /**
         * 1차 파쇄기: 콘크리트 받침 건물 안에 조 크러셔, 위에 덤프 투입구(쇠창살 그리즐리),
         * 북쪽에 덤프가 뒤로 대는 투입대와 투입 경사로 (반 블록씩 오름).
         */
        private void crusher() {
            // 받침 건물 (dx 12..18, dz -4..2), 높이 6
            fill(12, -1, -4, 18, -1, 2, CONCRETE);
            v.walls(i(12), Y, j(-4), i(18), Y + 5, j(2), CONCRETE);
            fill(14, 0, -2, 16, 2, 0, IRON_BLOCK);               // 조 크러셔 몸체
            set(15, 3, -1, Block.of("anvil[facing=east]", 0x444444));
            fill(14, 0, 3, 16, 0, 3, AIR);                        // 배출구 (남쪽)
            fill(14, 1, 2, 16, 1, 2, AIR);
            fill(14, 0, 2, 16, 0, 2, AIR);
            // 투입구: 둘레 낮은 벽과 그리즐리
            fill(12, 6, -4, 18, 7, 2, Block.of("polished_deepslate", 0x484849));
            fill(13, 6, -3, 17, 7, 1, AIR);
            fill(13, 5, -3, 17, 5, 1, IRON_BARS);
            fill(13, 7, -4, 17, 7, -4, AIR);                      // 덤프 쪽은 턱만
            // 투입대 (dz -10..-5) 서는 높이 +6
            for (int dz = -10; dz <= -5; dz++) {
                for (int dx = 11; dx <= 19; dx++) {
                    boolean edge = dx == 11 || dx == 19;
                    fill(dx, 0, dz, dx, 4, dz, edge ? CONCRETE : STONE);
                    set(dx, 5, dz, edge ? CONCRETE : Block.of("polished_andesite", 0x848685));
                    if (edge) {
                        set(dx, 6, dz, IRON_BARS);
                    }
                }
            }
            // 투입 경사로 (dz -22..-11): 남쪽으로 반 칸씩 올라 투입대에 닿음
            for (int dz = -22; dz <= -11; dz++) {
                double stand = (dz + 23) * 0.5;
                int full = (int) Math.floor(stand) - 1;
                boolean half = stand - Math.floor(stand) >= 0.5;
                for (int dx = 11; dx <= 19; dx++) {
                    boolean edge = dx == 11 || dx == 19;
                    if (full >= 0) {
                        fill(dx, 0, dz, dx, full, dz, edge ? CONCRETE : STONE);
                    }
                    if (edge) {
                        set(dx, Math.max(0, full + 1), dz, CONCRETE);
                    } else {
                        if (full >= -1) {
                            set(dx, full, dz, Block.of("polished_andesite", 0x848685));
                        }
                        if (half) {
                            set(dx, full + 1, dz, Block.of("polished_andesite_slab[type=bottom,waterlogged=false]", 0x848685));
                        }
                    }
                }
            }
            set(15, 4, 3, Blocks.wallSign("birch", "south", "black", false, "", "1차 파쇄기"));
        }

        /** 선별탑: 네 다리 위 선별기 집 (청록 외장), 파쇄기에서 오는 경사 컨베이어 */
        private void screen() {
            int a0 = 12, a1 = 18, b0 = 18, b1 = 24;
            for (int[] c : new int[][]{{a0, b0}, {a1, b0}, {a0, b1}, {a1, b1}}) {
                fill(c[0], 0, c[1], c[0], 6, c[1], CONCRETE);
            }
            fill(a0, 7, b0, a1, 7, b1, Block.of("smooth_stone", 0x9E9E9E));
            v.walls(i(a0), Y + 8, j(b0), i(a1), Y + 11, j(b1), CLAD);
            fill(a0, 12, b0, a1, 12, b1, Block.of("light_gray_concrete", 0x7D7D73));
            fill(a0 + 1, 8, b0 + 1, a1 - 1, 9, b1 - 1, Block.of("iron_block", 0xDCDCDC)); // 진동 선별기
            for (int dx = a0 + 1; dx < a1; dx += 2) {
                set(dx, 10, b0, Block.of("gray_stained_glass", 0x4C4C4C));
                set(dx, 10, b1, Block.of("gray_stained_glass", 0x4C4C4C));
            }
            // 아래 배출 슈트
            fill(14, 4, 20, 16, 6, 22, Block.of("polished_deepslate", 0x484849));
            // 컨베이어: 파쇄기 → 선별탑, 선별탑 → 더미 셋
            belt(15, 1, 3, 15, 9, 18);
            belt(13, 5, 21, 3, 8, 30);
            belt(15, 5, 24, 13, 8, 32);
            belt(18, 5, 21, 26, 8, 21);
        }

        /** 기울어진 컨베이어 (검은 벨트, 4칸마다 쇠 다리) */
        private void belt(int ax, int ay, int az, int bx, int by, int bz) {
            int steps = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
            for (int t = 0; t <= steps; t++) {
                double f = (double) t / steps;
                int dx = (int) Math.round(ax + (bx - ax) * f), dz = (int) Math.round(az + (bz - az) * f);
                double yy = ay + (by - ay) * f;
                int yb = (int) Math.floor(yy);
                boolean upper = yy - yb >= 0.5;
                set(dx, yb, dz, upper ? BELT_TOP : BELT);
                if (t % 4 == 0) {
                    for (int y = 0; y < yb; y++) {
                        set(dx, y, dz, STEEL);
                    }
                }
            }
        }

        /** 골재·모래 더미: 원뿔 (속까지 채움) */
        private void stockpiles() {
            pile(2, 31, 5, 5, GRAVEL);
            pile(13, 33, 5, 5, Block.of("sand", 0xDBCFA3));
            pile(27, 21, 4, 5, Block.of("andesite", 0x888888));
        }

        /** 발파석 더미: 크고 작은 돌덩이가 낮게 쌓임 */
        private void muck(int cx, int cz, int rad) {
            for (int dz = -rad - 1; dz <= rad + 1; dz++) {
                for (int dx = -rad - 2; dx <= rad + 2; dx++) {
                    double dd = Math.hypot(dx / 1.4, dz);
                    int hh = (int) Math.round(3 * (1 - dd / (rad + 0.8)));
                    for (int y = 0; y < hh; y++) {
                        set(cx + dx, y, cz + dz, rock(cx + dx, y + 7, cz + dz));
                    }
                }
            }
        }

        private void pile(int cx, int cz, int rad, int height, Block b) {
            for (int dz = -rad; dz <= rad; dz++) {
                for (int dx = -rad; dx <= rad; dx++) {
                    double dd = Math.hypot(dx, dz);
                    int hh = (int) Math.round(height * (1 - dd / (rad + 0.5)));
                    for (int y = 0; y < hh; y++) {
                        set(cx + dx, y, cz + dz, b);
                    }
                }
            }
        }

        /** 계근대: 강판 바닥 (야드 바닥과 같은 높이), 양 끝 콘크리트 */
        private void weighbridge() {
            fill(-12, -1, 12, -8, -1, 23, Block.of("polished_deepslate", 0x484849));
            fill(-12, -1, 12, -12, -1, 23, IRON_BLOCK);
            fill(-8, -1, 12, -8, -1, 23, IRON_BLOCK);
            fill(-12, -1, 11, -8, -1, 11, CONCRETE);
            fill(-12, -1, 24, -8, -1, 24, CONCRETE);
        }

        /** 빈 주차장 (흰 선) */
        private void parking() {
            fill(-31, -1, 16, -22, -1, 23, Block.of("gray_concrete", 0x36393D));
            for (int dx = -31; dx <= -22; dx += 3) {
                fill(dx, -1, 16, dx, -1, 20, WHITE_CONCRETE);
            }
        }
    }

    // ------------------------------------------------------------------ 건물

    /**
     * 컨테이너 현장사무실 (9×11): 컨테이너 셋을 붙인 1층(현장사무실)과 그 위 셋(휴게실·회의실), 바깥 철 계단.
     * 정면(남쪽) 앞 두 줄은 계단과 난간. 층고 4.
     */
    static Voxels containerOffice(Random r) {
        int w = 9, bd = 9;
        Voxels v = new Voxels(w, 11, -1, 8);
        Block wall = PANEL, post = CONCRETE;
        for (int k = 0; k < 2; k++) {
            int L = k * 4;
            v.fill(0, L - 1, 0, w - 1, L - 1, bd - 1, k == 0 ? Block.of("smooth_stone", 0x9E9E9E) : post);
            v.walls(0, L, 0, w - 1, L + 2, bd - 1, wall);
            for (int c = 0; c <= 2; c++) {
                // 컨테이너 사이 이음 (세로 줄)
                v.fill(0, L, c * 3, 0, L + 2, c * 3, post);
                v.fill(w - 1, L, c * 3, w - 1, L + 2, c * 3, post);
            }
            for (int i : new int[]{0, w - 1}) {
                for (int j : new int[]{0, bd - 1}) {
                    v.fill(i, L, j, i, L + 2, j, post);
                }
            }
            // 창
            for (int i = 2; i < w - 2; i += 3) {
                v.set(i, L + 1, 0, Block.of("glass_pane", 0xC8DCE4));
            }
            v.set(0, L + 1, 4, Block.of("glass_pane", 0xC8DCE4));
            v.set(w - 1, L + 1, 4, Block.of("glass_pane", 0xC8DCE4));
            v.set(4, L + 2, 4, Interior.LIGHT);
        }
        v.fill(0, 7, 0, w - 1, 7, bd - 1, post);
        Frame f = Frame.of(v);
        // 1층: 현장사무실
        Interior.door(f, 1, 0, bd - 1, "birch", "north");
        v.set(3, 1, bd - 1, Block.of("glass_pane", 0xC8DCE4));
        Furniture.desk(f, 2, 0, 2, "south");
        Furniture.desk(f, 4, 0, 2, "south");
        Furniture.desk(f, 6, 0, 2, "south");
        v.fill(7, 0, 5, 7, 1, 7, Furniture.BOOKSHELF);
        v.set(5, 1, 1, Furniture.TV); // 발파 일정 게시판
        v.set(2, 2, bd, Blocks.wallSign("birch", "south", "black", false, "", "준서석산", "현장사무실"));
        // 바깥 철 계단 (동쪽으로 오름) 과 2층 참
        for (int t = 0; t < 4; t++) {
            v.set(2 + t, t, bd, Blocks.stairs("stone", "east", 0x7E7E7E));
            v.set(2 + t, t + 1, bd + 1, IRON_BARS);
        }
        v.fill(6, 3, bd, 8, 3, bd, IRON_BLOCK);
        v.fill(6, 4, bd + 1, 8, 4, bd + 1, IRON_BARS);
        v.set(w - 1, 4, bd, IRON_BARS);
        // 2층: 휴게실 (문은 참 쪽)
        Interior.door(f, 7, 4, bd - 1, "birch", "north");
        Furniture.table(f, 2, 4, 4, 3, 1, "spruce");
        Furniture.sofa(f, r, 1, 4, 7, 3, "north");
        v.set(7, 4, 1, Furniture.FRIDGE);
        v.set(7, 5, 1, Furniture.FRIDGE);
        v.set(6, 4, 1, Furniture.COUNTER);
        v.set(5, 4, 1, CAULDRON);
        v.connect();
        return v;
    }

    /**
     * 광물 거래소 (13×11, 정면 남쪽): 조립식 패널 2층. 1층 매입 창구(저울)와 시세 안내, 광물 보관, 대기 의자,
     * 2층 사무실과 화장실. 북서쪽 계단실(외줄 꺾인 계단)로 옥상까지.
     */
    static Voxels oreExchange(Random r) {
        int w = 13, d = 11, bd = d - 1;
        int[] lv = {0, 4, 8};
        Voxels v = new Voxels(w, d, -1, 12);
        Block blue = Block.of("blue_concrete", 0x2C2E8F);
        for (int k = 0; k < 3; k++) {
            v.fill(0, lv[k] - 1, 0, w - 1, lv[k] - 1, bd - 1, k == 0 ? Block.of("polished_andesite", 0x848685) : SMOOTH_STONE);
        }
        for (int k = 0; k < 2; k++) {
            v.walls(0, lv[k], 0, w - 1, lv[k] + 2, bd - 1, PANEL);
            v.walls(0, lv[k] + 3, 0, w - 1, lv[k] + 3, bd - 1, blue);
            for (int i = 8; i < w - 1; i += 2) {
                v.set(i, lv[k] + 1, 0, Block.of("glass_pane", 0xC8DCE4));
            }
            for (int j = 2; j < bd - 1; j += 2) {
                v.set(w - 1, lv[k] + 1, j, Block.of("glass_pane", 0xC8DCE4));
            }
            if (k == 1) {
                for (int i = 2; i < w - 1; i += 2) {
                    v.set(i, lv[k] + 1, bd - 1, Block.of("glass_pane", 0xC8DCE4));
                }
            }
        }
        v.walls(0, 8, 0, w - 1, 8, bd - 1, blue);
        Frame f = Frame.of(v);
        // 계단실: 북서쪽 (i 0..6, j 0..4), 문은 동쪽
        Frame sf = Frame.facing(v, 5, 1, "west");
        Interior.stairCore(sf, lv, Interior.CORE_WALL, "polished_andesite", 0x848685, 1);
        sf.fill(-1, 11, -1, 3, 11, 5, SMOOTH_STONE);
        // 1층: 매입 창구 (북동쪽), 저울, 직원 자리와 보관 통, 대기 의자
        int L = 0;
        v.fill(8, L, 3, 11, L, 3, Furniture.COUNTER);
        v.set(9, L, 3, IRON_BLOCK);
        v.set(9, L + 1, 3, Block.of("heavy_weighted_pressure_plate[power=0]", 0xDCDCDC)); // 저울
        Furniture.chair(f, 10, L, 2, "north", "dark_oak");
        v.fill(8, L, 1, 11, L, 1, BARREL);
        v.set(11, L + 1, 1, Block.of("raw_iron_block", 0xA6876B));
        v.set(10, L + 1, 1, Block.of("raw_copper_block", 0x9A6A4F));
        v.set(9, L + 1, 1, Block.of("coal_block", 0x101010));
        v.set(10, L + 2, 4, Blocks.hangingSign("birch", 0, "black", false, "", "광물 매입", "창구"));
        v.set(3, L + 1, 5, Blocks.wallSign("birch", "south", "black", false, "시세 안내", "철광석", "구리 광석", "석탄"));
        Furniture.sofa(f, r, 2, L, bd - 2, 4, "north");
        Interior.door(f, 7, L, bd - 1, "birch", "north");
        v.fill(8, L + 1, bd - 1, 10, L + 1, bd - 1, Block.of("glass_pane", 0xC8DCE4));
        v.set(7, L + 3, bd, Blocks.wallSign("dark_oak", "south", "white", false, "", "광물 거래소"));
        v.set(9, L + 2, 6, Interior.LIGHT);
        v.set(3, L + 2, 7, Interior.LIGHT);
        // 2층: 사무실 + 화장실 (동쪽)
        L = lv[1];
        Furniture.desk(f, 8, L, 2, "south");
        Furniture.desk(f, 10, L, 2, "south");
        Furniture.desk(f, 3, L, 7, "north");
        Interior.restroom(Frame.facing(v, 11, 5, "west"), 0, 0, 3, 2, L, 4, "화장실", 1);
        v.set(8, L + 2, 4, Interior.LIGHT);
        v.connect();
        return v;
    }

    /** 계근실 (5×6, 정면 남쪽): 계근대를 내다보는 창과 책상 */
    static Voxels scaleHouse() {
        Voxels v = new Voxels(5, 6, -1, 4);
        v.fill(0, -1, 0, 4, -1, 4, SMOOTH_STONE);
        v.walls(0, 0, 0, 4, 2, 4, PANEL);
        v.fill(0, 3, 0, 4, 3, 4, Block.of("blue_concrete", 0x2C2E8F));
        v.fill(1, 1, 4, 3, 1, 4, Block.of("glass_pane", 0xC8DCE4));
        v.set(4, 1, 2, Block.of("glass_pane", 0xC8DCE4));
        v.set(0, 0, 2, Blocks.door("birch", "west", false));
        v.set(0, 1, 2, Blocks.door("birch", "west", true));
        Frame f = Frame.of(v);
        Furniture.desk(f, 2, 0, 3, "north");
        v.set(2, 2, 2, Interior.LIGHT);
        v.set(2, 2, 5, Blocks.wallSign("birch", "south", "black", false, "", "계근실"));
        v.connect();
        return v;
    }

    private Quarry() {
    }
}
