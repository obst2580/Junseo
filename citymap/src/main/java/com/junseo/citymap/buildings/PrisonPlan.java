package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 교도소 섬: 섬 한가운데 높은 담(6m, 위에 철조망)으로 둘러싼 교도소, 담 네 모서리 망루, 북쪽(구인천 부두 쪽) 정문과
 * 차량용 이중 문(sallyport), 담 밖 북쪽 해안에 호송선이 대는 선착장.
 * <ul>
 *   <li>북쪽 줄: 본관(민원실·접견실·사무실, 바깥에서 들어옴), 정문, 의료동</li>
 *   <li>가운데: 서·동 수용동(3층) 사이 운동장 — 거점(수감자가 오는 곳)이 운동장 한가운데</li>
 *   <li>남쪽 줄: 노역장(작업장), 식당, 텃밭</li>
 * </ul>
 * 좌표는 섬 가운데(거점) 기준으로 정해서, 섬이 조금 옮겨져도 따라갑니다.
 * 안쪽은 철문·철창으로 막혀 있어 걸어서 나갈 수 없습니다 (정문·본관 안쪽 문은 철문).
 */
final class PrisonPlan {
    /** 담 바깥 사각형 (섬 가운데 기준) */
    static final int WX0 = -37, WX1 = 37, WZ0 = -36, WZ1 = 36;

    record Rect(int x0, int z0, int x1, int z1) {
        boolean has(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    static List<Placement> plan(CityTerrain t) {
        Layout.Hub hub = Plans.hub(t, "prison");
        if (hub == null) {
            return List.of();
        }
        int cx = (int) Math.floor(hub.x()), cz = (int) Math.floor(hub.z());
        Random rnd = Plans.random(t, "prison");
        Site s = new Site(cx, cz);
        List<Placement> out = new ArrayList<>();
        long gseed = rnd.nextLong();
        out.add(Placement.rect("교도소", "prison", s.gx0, s.gz0, s.gx1, s.gz1, "south", (w, d) -> s.wallRing(),
                (w, d) -> grounds(s, new Random(gseed))));
        int shore = cz - 40;
        while (shore > cz - 90 && !t.column(cx, shore - 1).isWater()) {
            shore--;
        }
        int sh = shore;
        Ground dg = Ground.rect(t, cx - 26, sh - 2, cx + 26, s.gate.z0() - 1, "south");
        out.add(Placement.rect("교도소 선착장", "plaza", cx - 26, sh - 2, cx + 26, s.gate.z0() - 1, "south",
                (w, d) -> dock(w, d, dg, cx - (cx - 26), sh - (sh - 2), new Random(gseed + 1))));
        long[] seeds = new long[8];
        for (int k = 0; k < seeds.length; k++) {
            seeds[k] = rnd.nextLong();
        }
        out.add(rect("교도소 본관", s.admin, "north", (w, d) -> Prison.admin(w, d, new Random(seeds[0]), "준서교도소")));
        out.add(rect("교도소 의료동", s.infirmary, "south", (w, d) -> Prison.infirmary(w, d, new Random(seeds[1]))));
        out.add(rect("수용동 가동", s.blockA, "east", (w, d) -> Prison.cellBlock(w, d, new Random(seeds[2]), "수용동 가동")));
        out.add(rect("수용동 나동", s.blockB, "west", (w, d) -> Prison.cellBlock(w, d, new Random(seeds[3]), "수용동 나동")));
        out.add(rect("교도소 노역장", s.workshop, "north", (w, d) -> Prison.workshop(w, d, new Random(seeds[4]))));
        out.add(rect("교도소 식당", s.mess, "north", (w, d) -> Prison.messHall(w, d, new Random(seeds[5]))));
        return out;
    }

    static Placement rect(String name, Rect r, String front, java.util.function.BiFunction<Integer, Integer, Voxels> build) {
        return Placement.rect(name, "prison", r.x0, r.z0, r.x1, r.z1, front, build);
    }

    /** 섬 가운데 (cx, cz) 기준으로 정한 자리들 (월드 좌표) */
    static final class Site {
        final int cx, cz;
        final Rect wall, admin, gate, infirmary, blockA, blockB, yard, workshop, mess, garden;
        final Rect[] towers;
        final String[] towerFront = {"south", "south", "north", "north"};
        /** 담·마당 상자 (담 밖 순찰로 포함) */
        final int gx0, gz0, gx1, gz1;

        Site(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            wall = r(WX0, WZ0, WX1, WZ1);
            admin = r(-29, -37, -11, -22);       // 앞마당 한 줄이 담 밖
            gate = r(-7, -42, 7, -28);
            infirmary = r(11, -36, 29, -21);
            blockA = r(-36, -17, -21, 15);
            blockB = r(21, -17, 36, 15);
            yard = r(-20, -17, 20, 15);
            workshop = r(-29, 19, -2, 35);
            mess = r(2, 19, 21, 35);
            garden = r(24, 19, 34, 27);
            towers = new Rect[]{r(-38, -38, -30, -28), r(30, -38, 38, -28), r(-38, 28, -30, 38), r(30, 28, 38, 38)};
            gx0 = cx + WX0 - 4;
            gz0 = gate.z0 - 2;
            gx1 = cx + WX1 + 4;
            gz1 = cz + WZ1 + 4;
        }

        Rect r(int dx0, int dz0, int dx1, int dz1) {
            return new Rect(cx + dx0, cz + dz0, cx + dx1, cz + dz1);
        }

        /** 건물 몸체가 있는 칸 (담·마당을 그리지 않음) */
        boolean building(int x, int z) {
            for (Rect b : new Rect[]{admin, infirmary, blockA, blockB, workshop, mess}) {
                if (b.has(x, z)) {
                    return true;
                }
            }
            return false;
        }

        /** 망루 계단실 (밑동) 자리 */
        boolean towerBase(int x, int z) {
            for (int k = 0; k < 4; k++) {
                Rect t = towers[k];
                boolean south = towerFront[k].equals("south");
                int bx0 = t.x0 + 2, bx1 = t.x0 + 6;
                int bz0 = south ? t.z0 + 2 : t.z1 - 8, bz1 = south ? t.z0 + 8 : t.z1 - 2;
                if (x >= bx0 && x <= bx1 && z >= bz0 && z <= bz1) {
                    return true;
                }
            }
            return false;
        }

        boolean inTowerRect(int x, int z) {
            for (Rect t : towers) {
                if (t.has(x, z)) {
                    return true;
                }
            }
            return false;
        }

        /** 미니맵용: 담 둘레 띠 (가운데가 빈 다각형) */
        List<double[]> wallRing() {
            double ox0 = wall.x0 - gx0, oz0 = wall.z0 - gz0, ox1 = wall.x1 + 1 - gx0, oz1 = wall.z1 + 1 - gz0;
            List<double[]> p = new ArrayList<>();
            p.add(new double[]{ox0, oz0});
            p.add(new double[]{ox1, oz0});
            p.add(new double[]{ox1, oz1});
            p.add(new double[]{ox0, oz1});
            p.add(new double[]{ox0, oz0 + 1});
            p.add(new double[]{ox0 + 1, oz0 + 1});
            p.add(new double[]{ox0 + 1, oz1 - 1});
            p.add(new double[]{ox1 - 1, oz1 - 1});
            p.add(new double[]{ox1 - 1, oz0 + 1});
            p.add(new double[]{ox0, oz0 + 1});
            return p;
        }
    }

    // ------------------------------------------------------------------ 담·망루·정문·마당

    static final Block WALL = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block CAP = Block.of("smooth_stone", 0x9E9E9E);
    static final Block PAVE = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block YARD = Block.of("coarse_dirt", 0x77563B);
    static final Block STRIP = Block.of("gravel", 0x837F7E);

    static Voxels grounds(Site s, Random r) {
        int w = s.gx1 - s.gx0 + 1, d = s.gz1 - s.gz0 + 1;
        Voxels v = new Voxels(w, d, -1, 13);
        Rect wl = s.wall;
        for (int z = s.gz0; z <= s.gz1; z++) {
            for (int x = s.gx0; x <= s.gx1; x++) {
                int i = x - s.gx0, j = z - s.gz0;
                boolean onWall = wl.has(x, z) && (x == wl.x0 || x == wl.x1 || z == wl.z0 || z == wl.z1);
                boolean inside = wl.has(x, z) && !onWall;
                if (s.building(x, z) || s.gate.has(x, z)) {
                    continue;
                }
                if (s.towerBase(x, z)) {
                    continue;
                }
                if (onWall) {
                    v.fill(i, -1, j, i, 4, j, WALL);
                    v.set(i, 5, j, CAP);
                    if (!s.inTowerRect(x, z)) {
                        v.set(i, 6, j, IRON_BARS); // 철조망
                        // 안쪽으로 기운 철조망 한 줄 더
                        int ii = x == wl.x0 ? i + 1 : x == wl.x1 ? i - 1 : i;
                        int jj = z == wl.z0 ? j + 1 : z == wl.z1 ? j - 1 : j;
                        v.set(i, 7, j, IRON_BARS);
                        if (!s.building(ii + s.gx0, jj + s.gz0)) {
                            v.set(ii, 7, jj, IRON_BARS);
                        }
                    }
                    continue;
                }
                if (inside) {
                    int dw = Math.min(Math.min(x - wl.x0, wl.x1 - x), Math.min(z - wl.z0, wl.z1 - z));
                    Block ground = s.yard.has(x, z) ? YARD : dw <= 2 ? STRIP : PAVE;
                    if (s.garden.has(x, z)) {
                        ground = (z - s.garden.z0) % 4 == 2 ? WATER : Block.of("farmland[moisture=7]", 0x5A3E26);
                    }
                    v.set(i, -1, j, ground);
                    if (s.garden.has(x, z) && ground != WATER) {
                        String crop = (x - s.garden.x0) < 5 ? "potatoes" : (x - s.garden.x0) < 8 ? "carrots" : "wheat";
                        v.set(i, 0, j, Block.of(crop + "[age=" + (5 + r.nextInt(3)) + "]", crop.equals("wheat") ? 0xB5A445 : 0x5C8A2E));
                    }
                } else {
                    // 담 밖 순찰로 (자갈, 3칸)
                    int ox = Math.max(wl.x0 - x, x - wl.x1), oz = Math.max(wl.z0 - z, z - wl.z1);
                    if (Math.max(ox, oz) <= 3 && !s.gate.has(x, z)) {
                        v.set(i, -1, j, STRIP);
                    }
                }
            }
        }
        // 운동장: 철봉, 의자, 농구 골대, 조명탑 (거점 둘레 5칸은 비움)
        yard(v, s, r);
        // 망루
        for (int k = 0; k < 4; k++) {
            Rect t = s.towers[k];
            stamp(v, Prison.guardTower(), t.x0 - s.gx0, t.z0 - s.gz0, s.towerFront[k]);
        }
        gate(v, s);
        v.connect();
        return v;
    }

    private static void yard(Voxels v, Site s, Random r) {
        Rect y = s.yard;
        // 철봉 (동쪽 끝)
        for (int n = 0; n < 3; n++) {
            int x = y.x1 - 3, z = y.z0 + 4 + n * 3;
            post(v, s, x, z, 3);
            post(v, s, x, z + 2, 3);
            v.set(x - s.gx0, 2, z + 1 - s.gz0, Block.of("iron_chain[axis=z]", 0x4A4A4A));
        }
        // 농구 골대 (서쪽 끝)
        int bx = y.x0 + 2, bz = s.cz;
        post(v, s, bx, bz, 4);
        v.fill(bx + 1 - s.gx0, 3, bz - 1 - s.gz0, bx + 1 - s.gx0, 4, bz + 1 - s.gz0, WHITE_CONCRETE);
        v.set(bx + 2 - s.gx0, 3, bz - s.gz0, Block.of("iron_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1));
        // 족구장 (북쪽): 흰 선, 가운데 낮은 네트
        int kx0 = s.cx - 7, kx1 = s.cx + 7, kz0 = y.z0 + 2, kz1 = y.z0 + 8;
        Block line = Block.of("white_concrete_powder", 0xE2E4E4);
        for (int z = kz0; z <= kz1; z++) {
            for (int x = kx0; x <= kx1; x++) {
                if (x == kx0 || x == kx1 || z == kz0 || z == kz1 || x == s.cx) {
                    v.set(x - s.gx0, -1, z - s.gz0, line);
                }
            }
        }
        post(v, s, s.cx, kz0 - 1, 2);
        post(v, s, s.cx, kz1 + 1, 2);
        for (int z = kz0; z <= kz1; z++) {
            v.set(s.cx - s.gx0, 1, z - s.gz0, Block.of("iron_chain[axis=z]", 0x4A4A4A));
        }
        // 벽 쪽 의자
        for (int x = y.x0 + 6; x <= y.x1 - 6; x += 5) {
            v.set(x - s.gx0, 0, y.z1 - 1 - s.gz0, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
            v.set(x + 1 - s.gx0, 0, y.z1 - 1 - s.gz0, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        }
        // 조명탑 (네 모서리)
        for (int[] c : new int[][]{{y.x0 + 1, y.z0 + 1}, {y.x1 - 1, y.z0 + 1}, {y.x0 + 1, y.z1 - 1}, {y.x1 - 1, y.z1 - 1}}) {
            post(v, s, c[0], c[1], 8);
            v.set(c[0] - s.gx0, 8, c[1] - s.gz0, Block.of("sea_lantern", 0xACC7BE));
        }
    }

    private static void post(Voxels v, Site s, int x, int z, int h) {
        v.fill(x - s.gx0, 0, z - s.gz0, x - s.gx0, h - 1, z - s.gz0, Block.of("polished_blackstone_wall", 0x353038));
    }

    /**
     * 정문: 서쪽 차량용 이중 문(sallyport, 바깥문·안문 모두 쇠창살 문으로 닫힘)과 동쪽 사람 출입 보안실
     * (바깥에서 나무문 → 검색대 → 철문 → 대기실 → 철문 → 안). 담보다 높은 2층 정문 건물, 옥상 난간.
     */
    private static void gate(Voxels v, Site s) {
        Rect g = s.gate;
        int i0 = g.x0 - s.gx0, j0 = g.z0 - s.gz0, i1 = g.x1 - s.gx0, j1 = g.z1 - s.gz0;
        Block body = Prison.WALL;
        int H = 7;
        v.fill(i0, -1, j0, i1, -1, j1, PAVE);
        v.walls(i0, 0, j0, i1, H, j1, body);
        v.walls(i0, 0, j0, i1, 0, j1, Prison.PLINTH);
        v.fill(i0, H + 1, j0, i1, H + 1, j1, Prison.SLAB);
        v.walls(i0, H + 1, j0, i1, H + 2, j1, Prison.TRIM);
        // 차량 통로 (서쪽 7칸): 양 끝 쇠창살 문, 위는 2층 감시실 바닥
        int ri0 = i0 + 1, ri1 = i0 + 7;
        v.fill(ri0, -1, j0, ri1, -1, j1, Block.of("gray_concrete", 0x36393D));
        v.fill(ri0, 0, j0, ri1, 4, j1, AIR);
        v.fill(ri0, 0, j0, ri1, 4, j0, IRON_BARS);
        v.fill(ri0, 0, j1, ri1, 4, j1, IRON_BARS);
        v.fill(ri1 + 1, 0, j0 + 1, ri1 + 1, 4, j1 - 1, body);
        v.fill(ri0, 5, j0, ri1, 5, j1, Prison.SLAB);
        v.fill(ri0, 1, j0 + 3, ri0, 1, j0 + 3, Interior.LIGHT);
        // 사람 출입 (동쪽): 보안실 두 칸
        int pi0 = ri1 + 2, pi1 = i1 - 1, pm = (pi0 + pi1) / 2;
        v.fill(pi0, 0, j0 + 1, pi1, H - 1, j1 - 1, AIR);
        v.fill(pi0, H, j0 + 1, pi1, H, j1 - 1, Prison.SLAB);
        int mid = (j0 + j1) / 2;
        v.fill(pi0, 0, mid, pi1, H - 1, mid, body);
        Frame f = Frame.of(v);
        Interior.door(f, pm, 0, j0, "dark_oak", "north");
        v.set(pm, 0, mid, Blocks.door("iron", "north", false));
        v.set(pm, 1, mid, Blocks.door("iron", "north", true));
        v.set(pm, 0, j1, Blocks.door("iron", "north", false));
        v.set(pm, 1, j1, Blocks.door("iron", "north", true));
        v.fill(pi0, 0, j0 + 3, pm - 1, 0, j0 + 3, Furniture.COUNTER); // 검색대
        v.set(pi0, 1, mid - 1, Furniture.TV);
        v.set(pm, 3, j0 + 3, Interior.LIGHT);
        v.set(pm, 3, mid + 3, Interior.LIGHT);
        for (int j = j0 + 2; j < j1 - 1; j += 3) {
            v.set(i1, 2, j, IRON_BARS);
        }
        // 이름 (정문 바깥, 차량 문 위)
        v.set((ri0 + ri1) / 2, 6, j0 - 1, Blocks.wallSign("dark_oak", "north", "white", false, "", "준서교도소"));
        v.set(pm, 2, j0 - 1, Blocks.wallSign("birch", "north", "black", false, "", "민원·면회", "본관으로"));
        for (int i = ri0 - 1; i <= ri1 + 1; i++) {
            v.set(i, 6, j0, Prison.TRIM);
        }
    }

    /**
     * 건물 상자 src 를 dst 의 (i0, j0) 직사각형에 front 쪽을 보게 찍습니다 ({@link Placement#rect} 와 같은 돌림).
     */
    static void stamp(Voxels dst, Voxels src, int i0, int j0, String front) {
        stamp(dst, src, i0, 0, j0, front);
    }

    /** {@link #stamp(Voxels, Voxels, int, int, String)} 와 같고, 높이를 dy 만큼 올려 찍음 */
    static void stamp(Voxels dst, Voxels src, int i0, int dy, int j0, String front) {
        int q = Frame.quarter(front);
        int sx = q % 2 == 0 ? src.w : src.d, sz = q % 2 == 0 ? src.d : src.w;
        int i1 = i0 + sx - 1, j1 = j0 + sz - 1;
        for (int y = src.y0; y < src.y0 + src.h; y++) {
            for (int j = 0; j < src.d; j++) {
                for (int i = 0; i < src.w; i++) {
                    Block b = src.get(i, y, j);
                    if (b == null) {
                        continue;
                    }
                    int di, dj;
                    switch (q) {
                        case 0 -> {
                            di = i0 + i;
                            dj = j0 + j;
                        }
                        case 1 -> {
                            di = i1 - j;
                            dj = j0 + i;
                        }
                        case 2 -> {
                            di = i1 - i;
                            dj = j1 - j;
                        }
                        default -> {
                            di = i0 + j;
                            dj = j1 - i;
                        }
                    }
                    dst.set(di, y + dy, dj, b.rotate(q));
                }
            }
        }
    }

    // ------------------------------------------------------------------ 선착장

    /**
     * 선착장: 북쪽 해안을 파서 만든 작은 배 대는 곳(물, 비어 있음). 둘레 콘크리트 안벽, 물로 내려가는 계단,
     * 계류 기둥, 경비 초소, 대기 차양, 정문까지 이어지는 차로와 보도.
     * (hi, sj) 는 상자 안에서 섬 가운데 x 와 해안선(처음 땅) z.
     */
    static Voxels dock(int w, int d, Ground g, int hi, int sj, Random r) {
        Voxels v = new Voxels(w, d, -6, 5);
        Block quay = Block.of("light_gray_concrete", 0x7D7D73);
        Block edge = Block.of("yellow_concrete", 0xF0AF15);
        int b0 = hi - 6, b1 = hi + 6, bj1 = sj + 7;
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (g.column(i, j).isWater()) {
                    continue;
                }
                boolean basin = i >= b0 && i <= b1 && j <= bj1;
                boolean apron = i >= b0 - 6 && i <= b1 + 6 && j <= bj1 + 5;
                boolean road = Math.abs(i - (hi - 3)) <= 3 && j > bj1;
                boolean walk = i >= hi + 2 && i <= hi + 6 && j > bj1;
                if (basin) {
                    v.set(i, -1, j, AIR);
                    v.fill(i, -4, j, i, -2, j, WATER);
                    v.set(i, -5, j, Block.of("gravel", 0x837F7E));
                } else if (apron) {
                    boolean rim = (i == b0 - 1 || i == b1 + 1) && j <= bj1 + 1 || j == bj1 + 1 && i >= b0 - 1 && i <= b1 + 1;
                    v.fill(i, -5, j, i, -2, j, quay);
                    v.set(i, -1, j, rim ? edge : quay);
                } else if (road) {
                    v.set(i, -1, j, Math.abs(i - (hi - 3)) == 3 ? WHITE_CONCRETE : Block.of("gray_concrete", 0x36393D));
                } else if (walk) {
                    v.set(i, -1, j, SMOOTH_STONE);
                }
            }
        }
        // 물로 내려가는 계단 (안쪽 끝)
        for (int i = hi - 1; i <= hi + 1; i++) {
            v.set(i, -2, bj1, Block.of("stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=true]", 0x7A7979));
            v.set(i, -3, bj1 - 1, Block.of("stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=true]", 0x7A7979));
            v.set(i, -3, bj1, quay);
            v.set(i, -4, bj1 - 1, quay);
            v.set(i, -4, bj1, quay);
        }
        // 계류 기둥
        for (int j = Math.max(0, sj); j <= bj1; j += 3) {
            bollard(v, g, b0 - 2, j);
            bollard(v, g, b1 + 2, j);
        }
        // 경비 초소 (동쪽)
        int ki = b1 + 4, kj = bj1 + 1;
        v.walls(ki, 0, kj, ki + 2, 2, kj + 2, WHITE_CONCRETE);
        v.set(ki + 1, 1, kj, Block.of("glass_pane", 0xC8DCE4));
        v.set(ki + 2, 1, kj + 1, Block.of("glass_pane", 0xC8DCE4));
        v.set(ki, 0, kj + 1, Blocks.door("spruce", "west", false));
        v.set(ki, 1, kj + 1, Blocks.door("spruce", "west", true));
        v.fill(ki - 1, 3, kj - 1, ki + 3, 3, kj + 3, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.set(ki + 1, 2, kj - 1, Blocks.wallSign("birch", "north", "black", false, "", "교도소", "선착장"));
        v.set(ki + 1, 2, kj + 1, Interior.LIGHT);
        // 대기 차양 (서쪽): 기둥 넷, 지붕, 의자
        int si = b0 - 5, sjj = bj1 + 1;
        for (int[] c : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
            v.fill(si + c[0], 0, sjj + c[1], si + c[0], 2, sjj + c[1], Block.of("polished_blackstone_wall", 0x353038));
        }
        v.fill(si, 3, sjj, si + 3, 3, sjj + 3, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        v.fill(si + 1, 0, sjj + 2, si + 2, 0, sjj + 2, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        // 길가 보행등
        for (int j = bj1 + 6; j < d - 1; j += 6) {
            v.fill(hi + 1, 0, j, hi + 1, 3, j, StreetPlan.POST);
            v.set(hi + 1, 4, j, LANTERN);
        }
        v.connect();
        return v;
    }

    private static void bollard(Voxels v, Ground g, int i, int j) {
        if (i >= 0 && i < v.w && j >= 0 && j < v.d && !g.column(i, j).isWater()) {
            v.set(i, 0, j, Block.of("polished_blackstone_wall", 0x353038));
        }
    }

    private PrisonPlan() {
    }
}
