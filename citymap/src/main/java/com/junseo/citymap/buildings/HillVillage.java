package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 비탈 마을 한 곳: 큰길에서 산 위로 층층이 올라가는 계단식 터 (석축), 줄마다 등고선을 따라 가는 골목,
 * 골목과 골목을 잇는 계단길, 줄마다 늘어선 집.
 * <p>
 * 마을 좌표: 정면(길 쪽, 아래쪽)이 남쪽 (j = d-1), 위쪽(산 쪽)이 북쪽 (j = 0). 아래에서 위로
 * <ol>
 *   <li>길가 보도 (P 줄, 높이 0)</li>
 *   <li>길가 줄 (S 줄): 길을 보는 가게·집 (높이 0)</li>
 *   <li>골목 1 (A 줄, 높이 L1) + 집 줄 1 (H 줄, 높이 L1): 집은 아래 골목을 보고, 뒤는 윗골목 석축에 붙음</li>
 *   <li>골목 2 + 집 줄 2 … (높이는 땅을 따라 4칸 단위로 올라감)</li>
 * </ol>
 * 계단길은 모든 줄을 같은 자리(i)로 가로질러 아래 골목에서 윗골목까지 오릅니다 (계단 블록, 한 칸에 한 칸).
 * 집은 아래 골목 쪽 1층 대문과, 윗골목 높이 층의 뒷문으로 드나듭니다.
 * <p>
 * 땅 고르기: 칸마다 고른 높이(윗면 블록 y)를 정해 두고, 위로 솟은 땅은 깎고(공기), 모자란 곳은 채우고, 옆 칸보다 높아
 * 드러나는 면(깎은 면·채운 면 모두)은 석축 블록으로 쌓습니다 ({@link Earthwork} 와 같은 방식에 깎은 면 마감을 더함).
 * 길·물·터널 칸은 건드리지 않습니다. 골목·계단 가장자리가 두 칸 넘게 떨어지면 난간.
 * <p>
 * 바닥(골목·계단·석축)은 한 Placement ("plaza"), 집·가게는 필지마다 따로 (그 필지 땅 고르기 포함) 놓습니다.
 */
final class HillVillage {
    enum Style { HAEBANGCHON, PYEONGCHANG, TRAILHEAD }

    static final int NONE = Integer.MIN_VALUE;

    /** 필지: 마을 좌표 (i0..i1, j0..j1), 서는 높이, 걸어서 닿아야 하는 층 높이들 (서는 높이 기준), 건물 (건물 y 0 이 서는 높이) */
    record Lot(String name, String kind, int i0, int j0, int i1, int j1, int level, int[] floors, Supplier<Voxels> build) {
    }

    /** 집 줄 하나: 줄 칸 ja(위)..jb(아래), 그 아래 골목 aa..ab (길가 줄은 보도), 높이 */
    record Row(int ja, int jb, int aa, int ab, int level) {
    }

    /** 칸 위 장식: 서는 높이에서 dy 위 */
    private record Deco(int i, int j, int dy, Block block) {
    }

    // 재료
    static final Block PAVE = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block ALLEY = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block ALLEY_PATCH = Block.of("andesite", 0x888888);
    static final Block LANE = Block.of("gray_concrete", 0x36393D);
    static final Block LANDING = Block.of("smooth_stone", 0x9E9E9E);
    static final Block LOT_FLOOR = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block FILL = STONE;
    static final Block TRAIL = Block.of("dirt_path", 0x947A41);
    static final Block TRAIL_STEP = Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430);
    private static final Block[] STONE_WALL = {STONE_BRICKS, STONE_BRICKS, MOSSY_STONE_BRICKS, CRACKED_STONE_BRICKS, STONE_BRICKS, ANDESITE};
    private static final Block[] GRANITE_WALL = {GardenHouse.GRANITE, GardenHouse.GRANITE_ROUGH, GardenHouse.GRANITE, Block.of("smooth_stone", 0x9E9E9E)};

    final CityTerrain t;
    final HillsidePlan.Site site;
    final Style style;
    final String name;
    final Ground g;
    final int w, d;
    private final int[] nat;
    private final boolean[] off;
    final int[] top;
    private final Block[] surf;
    private final int[] owner;
    private final boolean[] walk;
    final List<Lot> lots = new ArrayList<>();
    final List<Row> rows = new ArrayList<>();
    final List<int[]> corridors = new ArrayList<>();
    private final List<Deco> decos = new ArrayList<>();
    private final Random rnd;
    // 모양 값
    private final int P, S, A, H, lotMin, lotMax, maxStep, cw;
    private final Block rail;
    private final Block[] faces;
    private long decoSeed;
    private final List<int[]> trees = new ArrayList<>();
    /** 등산로 (등산로 입구 마을만) */
    HillTrail trail;

    HillVillage(CityTerrain t, HillsidePlan.Site s, Style style, String name) {
        this.t = t;
        this.site = s;
        this.style = style;
        this.name = name;
        this.g = Ground.rect(t, s.x0(), s.z0(), s.x1(), s.z1(), s.front());
        this.w = s.width();
        this.d = s.depth();
        this.rnd = Plans.random(t, "hill-" + name + "-" + s.x0() + "," + s.z0());
        nat = new int[w * d];
        off = new boolean[w * d];
        top = new int[w * d];
        surf = new Block[w * d];
        owner = new int[w * d];
        walk = new boolean[w * d];
        Arrays.fill(top, NONE);
        Arrays.fill(owner, -1);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                Column c = g.column(i, j);
                nat[j * w + i] = c.mountainHeight - 1;
                off[j * w + i] = c.isRoad() || c.isWater() || c.deck || c.tunnel;
            }
        }
        switch (style) {
            case PYEONGCHANG -> {
                P = 2;
                S = 26;
                A = 5;
                H = 23;
                lotMin = 22;
                lotMax = 26;
                maxStep = 12;
                cw = 3;
                rail = Block.of("diorite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0xBCBCBC);
                faces = GRANITE_WALL;
            }
            case TRAILHEAD -> {
                P = 2;
                S = 13;
                A = 10;
                H = 0;
                lotMin = 10;
                lotMax = 13;
                maxStep = 8;
                cw = 5;
                rail = SPRUCE_FENCE;
                faces = STONE_WALL;
            }
            default -> {
                P = 2;
                S = 14;
                A = 3;
                H = 14;
                lotMin = 12;
                lotMax = 16;
                maxStep = 12;
                cw = 3;
                rail = IRON_BARS;
                faces = STONE_WALL;
            }
        }
        layout();
    }

    Random rnd() {
        return rnd;
    }

    /** 길·물·터널 칸 (건드리지 않음) */
    boolean offAt(int i, int j) {
        if (inside(i, j)) {
            return off[k(i, j)];
        }
        Column c = g.column(i, j);
        return c.isRoad() || c.isWater() || c.deck || c.tunnel;
    }

    int k(int i, int j) {
        return j * w + i;
    }

    boolean inside(int i, int j) {
        return i >= 0 && j >= 0 && i < w && j < d;
    }

    /** 원래 땅 윗면 y (상자 밖은 지형에서) */
    int nat(int i, int j) {
        return inside(i, j) ? nat[k(i, j)] : g.surfaceY(i, j);
    }

    /** 고른 뒤 윗면 y */
    int fin(int i, int j) {
        if (!inside(i, j)) {
            return g.surfaceY(i, j);
        }
        int tp = top[k(i, j)];
        return tp == NONE ? nat[k(i, j)] : tp;
    }

    private void level(int i, int j, int topY, Block b, boolean walkway) {
        if (!inside(i, j) || off[k(i, j)]) {
            return;
        }
        top[k(i, j)] = topY;
        surf[k(i, j)] = b;
        walk[k(i, j)] = walkway;
    }

    // ------------------------------------------------------------------ 배치

    private void layout() {
        int i0 = 2, i1 = w - 3;
        // 1. 줄과 높이
        int jb = d - 1 - P;
        rows.add(new Row(jb - S + 1, jb, d - P, d - 1, 0));
        int prev = 0, cur = jb - S;
        while (true) {
            int ab = cur, aa = cur - A + 1;
            int sb = aa - 1, sa = sb - H + 1;
            if (style == Style.TRAILHEAD) {
                sa = sb + 1; // 광장만
            }
            if (Math.min(sa, aa) < 2) {
                break;
            }
            int[] hs = new int[(i1 - i0 + 1) * (ab - Math.min(sa, aa) + 1)];
            int n = 0;
            for (int j = Math.min(sa, aa); j <= ab; j++) {
                for (int i = i0; i <= i1; i++) {
                    if (!off[k(i, j)]) {
                        hs[n++] = nat[k(i, j)] + 1;
                    }
                }
            }
            if (n == 0) {
                break;
            }
            Arrays.sort(hs, 0, n);
            int med = hs[n / 2];
            int L = prev + 4 * Math.round((med - prev) / 4f);
            L = Math.max(prev, Math.min(prev + maxStep, L));
            // 산이 너무 가파르게 올라가면 (뒤 석축이 너무 높아짐) 여기서 멈춤
            int back = 0;
            for (int i = i0; i <= i1; i++) {
                back = Math.max(back, nat[k(i, Math.max(0, Math.min(sa, aa)))] + 1);
            }
            if (rows.size() >= 2 && back - L > 18) {
                break;
            }
            rows.add(new Row(sa, sb, aa, ab, L));
            prev = L;
            cur = Math.min(sa, aa) - 1;
            if (style == Style.TRAILHEAD) {
                break;
            }
        }
        // 2. 계단길 자리 (모든 줄을 같은 i 로 가로지름): 토막마다 필지가 딱 맞게 들어가도록 나눔
        int span = i1 - i0 + 1;
        if (style == Style.TRAILHEAD) {
            int ci = i0 + span / 2 - cw / 2;
            corridors.add(new int[]{ci, ci + cw - 1});
        } else {
            int perSeg = style == Style.PYEONGCHANG ? 2 : 3;
            int avg = (lotMin + lotMax) / 2 + (style == Style.HAEBANGCHON ? 1 : 0);
            int nc = Math.max(1, Math.round((span + cw) / (float) (perSeg * avg + cw)) - 1);
            int seg = (span - nc * cw) / (nc + 1);
            for (int c = 0; c < nc; c++) {
                int ci = i0 + (c + 1) * seg + c * cw;
                corridors.add(new int[]{ci, ci + cw - 1});
            }
        }
        // 3. 보도, 골목, 줄 바닥
        for (int j = d - P; j < d; j++) {
            for (int i = 0; i < w; i++) {
                level(i, j, -1, PAVE, true);
            }
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            if (r > 0) {
                for (int j = row.aa; j <= row.ab; j++) {
                    for (int i = i0; i <= i1; i++) {
                        level(i, j, row.level - 1, alley(i, j), true);
                    }
                }
            }
            for (int j = row.ja; j <= row.jb; j++) {
                for (int i = i0; i <= i1; i++) {
                    level(i, j, row.level - 1, gapBlock(i, j), false);
                }
            }
            // 계단길 (윗골목이 있는 줄만)
            if (r + 1 < rows.size()) {
                Row up = rows.get(r + 1);
                for (int[] c : corridors) {
                    stairs(c[0], c[1], row.ja, row.jb, row.level, up.level);
                }
            }
        }
        // 4. 필지
        if (style == Style.TRAILHEAD) {
            trailhead();
        } else {
            for (int r = 0; r < rows.size(); r++) {
                if (style == Style.TRAILHEAD || rows.get(r).jb < rows.get(r).ja) {
                    continue;
                }
                fillRow(r);
            }
        }
        // 5. 마을 둘레 비탈: 석축 한 단(4칸) 위로 1:1 풀 비탈을 깎아 자연 땅과 잇기
        bank();
        // 나무는 길·집에서 5칸 넘게 떨어진 비탈에만 (잎이 길을 막지 않게)
        for (int[] tc : trees) {
            boolean clear = true;
            for (int j = tc[1] - 5; j <= tc[1] + 5 && clear; j++) {
                for (int i = tc[0] - 5; i <= tc[0] + 5 && clear; i++) {
                    clear = !inside(i, j) || (!walk[k(i, j)] && owner[k(i, j)] < 0);
                }
            }
            if (clear) {
                decos.add(new Deco(tc[0], tc[1], -100, null));
            }
        }
        // 6. 난간·가로등·화분
        decorate();
        decoSeed = rnd.nextLong();
    }

    /**
     * 고른 터 둘레의 자연 땅이 너무 높으면 (깎은 면이 높게 드러나면), 터 가장자리에서 석축 한 단(4칸) 위로
     * 한 칸에 두 칸씩 오르는 계단식 석축 비탈(풀 덮인 단)로 깎아서, 높은 벽 하나 대신 층층 비탈이 되게 합니다. 군데군데 나무.
     */
    private void bank() {
        int[] cap = new int[w * d];
        Arrays.fill(cap, Integer.MAX_VALUE);
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        for (int kk = 0; kk < w * d; kk++) {
            if (top[kk] != NONE) {
                q.add(kk);
            }
        }
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int kk = q.poll();
            int i = kk % w, j = kk / w;
            int base = top[kk] != NONE ? top[kk] + 4 : cap[kk] + 2;
            for (int[] n : nb) {
                int ni = i + n[0], nj = j + n[1];
                if (!inside(ni, nj)) {
                    continue;
                }
                int nk = k(ni, nj);
                if (top[nk] != NONE || off[nk] || base >= cap[nk]) {
                    continue;
                }
                cap[nk] = base;
                q.add(nk);
            }
        }
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int kk = k(i, j);
                if (top[kk] != NONE || off[kk] || cap[kk] >= nat[kk]) {
                    continue;
                }
                // 상자 가장자리 한 칸은 자연 땅으로 남겨 둠 (바깥 땅과 이어지는 면을 석축으로)
                if (i == 0 || j == 0 || i == w - 1) {
                    continue;
                }
                top[kk] = cap[kk];
                surf[kk] = GRASS;
                if (rnd.nextInt(40) == 0) {
                    trees.add(new int[]{i, j});
                }
            }
        }
    }

    /** 골목 바닥: 콘크리트에 가끔 덧씌운 자국 (평창동은 회색 길) */
    private Block alley(int i, int j) {
        if (style == Style.PYEONGCHANG) {
            return LANE;
        }
        if (style == Style.TRAILHEAD) {
            return Math.floorMod(i * 3 + j * 5, 7) == 0 ? STONE_BRICKS : Block.of("polished_andesite", 0x848685);
        }
        long h = (Math.floorDiv(i, 3) * 73856093L) ^ (Math.floorDiv(j, 2) * 19349663L);
        return Math.floorMod(h, 7) == 0 ? ALLEY_PATCH : ALLEY;
    }

    /** 집 사이 빈 칸 바닥 */
    private Block gapBlock(int i, int j) {
        return style == Style.HAEBANGCHON ? ALLEY : GRASS;
    }

    /**
     * 계단길 한 토막: i a..b, 줄 칸 ja(위)..jb(아래), 아래 높이 lo 에서 위 높이 hi 까지.
     * 가운데쯤에 계단을 모으고 양 끝은 평평한 참.
     */
    private void stairs(int a, int b, int ja, int jb, int lo, int hi) {
        int n = jb - ja + 1, rise = hi - lo;
        int start = Math.max(0, (n - rise) / 2);
        String mat = style == Style.PYEONGCHANG ? "polished_diorite" : "stone_brick";
        int rgb = style == Style.PYEONGCHANG ? 0xC0C0C1 : 0x7A7979;
        for (int tt = 0; tt < n; tt++) {
            int j = jb - tt;
            int stand;
            Block blk;
            if (tt < start) {
                stand = lo;
                blk = LANDING;
            } else if (tt < start + rise) {
                stand = lo + (tt - start) + 1;
                blk = Blocks.stairs(mat, "north", rgb);
            } else {
                stand = hi;
                blk = LANDING;
            }
            for (int i = a; i <= b; i++) {
                level(i, j, stand - 1, blk, true);
            }
        }
    }

    private boolean inCorridor(int i) {
        for (int[] c : corridors) {
            if (i >= c[0] && i <= c[1]) {
                return true;
            }
        }
        return false;
    }

    /** 이 직사각형이 길·물에서 한 칸 넘게 떨어져 있고 땅 고르기가 지나치지 않은지 */
    private boolean lotOk(int i0, int j0, int i1, int j1, int level) {
        int cut = 0, fill = 0;
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (off[k(i, j)]) {
                    return false;
                }
            }
        }
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int tp = nat[k(i, j)] + 1;
                cut = Math.max(cut, tp - level);
                fill = Math.max(fill, level - tp);
            }
        }
        return cut <= (style == Style.PYEONGCHANG ? 22 : 15) && fill <= 12;
    }

    /** 집 줄 하나를 필지로 나눔 (계단길 사이 토막마다) */
    private void fillRow(int r) {
        Row row = rows.get(r);
        boolean street = r == 0;
        int upLevel = r + 1 < rows.size() ? rows.get(r + 1).level : NONE;
        int depth = row.jb - row.ja + 1;
        int i = 2;
        while (i <= w - 3) {
            if (inCorridor(i) && r + 1 < rows.size()) {
                i++;
                continue;
            }
            // 다음 계단길 (또는 끝)까지 토막
            int end = i;
            while (end + 1 <= w - 3 && !(inCorridor(end + 1) && r + 1 < rows.size())) {
                end++;
            }
            int len = end - i + 1, n = len / lotMin;
            if (n > 0) {
                int lw = Math.min(lotMax, len / n);
                int spare = len - n * lw;
                int a = i + spare / 2;
                for (int c = 0; c < n; c++) {
                    int b = a + lw - 1;
                    if (lotOk(a, row.ja, b, row.jb, row.level)) {
                        addLot(r, street, a, b, row, upLevel, depth);
                    }
                    a = b + 1 + (c < spare - spare / 2 && style == Style.HAEBANGCHON ? 1 : 0);
                }
            }
            i = end + 1;
        }
    }

    private void addLot(int r, boolean street, int a, int b, Row row, int upLevel, int depth) {
        int lw = b - a + 1;
        long seed = rnd.nextLong();
        Lot lot;
        if (style == Style.PYEONGCHANG) {
            lot = new Lot(name + " 단독주택", "house", a, row.ja, b, row.jb, row.level, new int[]{0, 4},
                    () -> GardenHouse.build(lw, depth, street, new Random(seed)));
        } else {
            int back = upLevel == NONE ? -1 : (upLevel - row.level) / 4;
            int floors = Math.min(3, Math.max(2, back + 1) + (rnd.nextInt(10) < 4 ? 1 : 0));
            boolean shop = street ? rnd.nextInt(10) < 5 : rnd.nextInt(10) < 1;
            String[] sn = shop ? HillHouse.shopName(rnd) : null;
            String street2 = name.length() <= 4 ? name + "길" : "산동네길";
            int fb = back;
            lot = new Lot(shop ? name + " " + sn[0] : name + " 다가구", "house", a, row.ja, b, row.jb, row.level,
                    Floors.levels(Floors.HOME, Floors.HOME, floors), () -> HillHouse.build(lw, depth, floors, fb, sn, street2, new Random(seed)));
        }
        int idx = lots.size();
        lots.add(lot);
        for (int j = lot.j0(); j <= lot.j1(); j++) {
            for (int i = lot.i0(); i <= lot.i1(); i++) {
                level(i, j, row.level - 1, LOT_FLOOR, false);
                owner[k(i, j)] = idx;
            }
        }
    }

    // ------------------------------------------------------------------ 등산로 입구

    /**
     * 등산로 입구: 길가 줄에 공영주차장·식당·등산복 가게, 가운데 계단으로 오르는 입구 광장 (안내도, 의자, 화장실, 물),
     * 광장 뒤 등산로 문과 산으로 오르는 흙길 (마을 뒤 끝까지; 그 위는 {@link HillTrail}).
     */
    private void trailhead() {
        Row street = rows.get(0);
        Row plaza = rows.size() > 1 ? rows.get(1) : null;
        int depth = street.jb - street.ja + 1;
        int[] gate = corridors.get(0);
        // 길가 줄: 왼쪽부터 주차장, 식당 둘, (입구 계단), 식당, 등산복 가게
        String[][] shops = {
                {"할매 파전", "막걸리·도토리묵"}, {"북한산 산채", "산채비빔밥"}, {"등산로 손두부", "두부전골"},
                {"정릉 칼국수", "보리밥"}, {"백운대 장터", "파전·막걸리"},
        };
        int i = 2;
        int pw = Math.min(24, Math.max(18, gate[0] - 2 - 2 * lotMin - 2));
        if (gate[0] - i >= pw + 2 * lotMin) {
            addTrailLot(new Lot("등산로 공영주차장", "garage", i, street.ja, i + pw - 1, street.jb, 0, new int[]{0},
                    () -> parking(pw, depth)));
            i += pw + 1;
        }
        int sIdx = rnd.nextInt(shops.length);
        while (i + lotMin - 1 < gate[0]) {
            int lw = Math.min(lotMax, gate[0] - i);
            if (lw < lotMin) {
                break;
            }
            String[] sh = shops[sIdx++ % shops.length];
            addTrailLot(restaurant(i, i + lw - 1, street, sh, false));
            i += lw;
        }
        i = gate[1] + 1;
        boolean outdoor = false;
        while (i + lotMin - 1 <= w - 3) {
            int lw = Math.min(lotMax, w - 2 - i);
            if (lw < lotMin) {
                break;
            }
            String[] sh = shops[sIdx++ % shops.length];
            addTrailLot(restaurant(i, i + lw - 1, street, outdoor ? sh : new String[]{"산사랑 등산복", "등산화·배낭"}, !outdoor));
            outdoor = true;
            i += lw;
        }
        if (plaza == null) {
            return;
        }
        // 광장: 돌 바닥, 가운데 뒤에 등산로 문, 그 뒤로 산길
        int ci = (gate[0] + gate[1]) / 2;
        int L = plaza.level;
        // 화장실 (광장 오른쪽 끝)
        int ri0 = Math.min(w - 13, ci + 8), rj0 = plaza.aa;
        if (ri0 + 10 <= w - 3 && lotOk(ri0, rj0, ri0 + 10, rj0 + 7, L)) {
            Lot rest = new Lot("등산로 화장실", "park", ri0, rj0, ri0 + 10, rj0 + 7, L, new int[]{0}, () -> GrandPark.restroom(11, 8));
            addTrailLot(rest);
        }
        // 안내도 (왼쪽), 의자, 음수대
        int mi = Math.max(3, ci - 8);
        int mj = plaza.aa + 2;
        decos.add(new Deco(mi, mj, 0, Block.of("spruce_log[axis=y]", 0x3A2A1A)));
        decos.add(new Deco(mi + 3, mj, 0, Block.of("spruce_log[axis=y]", 0x3A2A1A)));
        for (int a = mi; a <= mi + 3; a++) {
            decos.add(new Deco(a, mj, 1, a == mi || a == mi + 3 ? Block.of("spruce_log[axis=y]", 0x3A2A1A) : Block.of("spruce_planks", 0x725430)));
            decos.add(new Deco(a, mj, 2, a == mi || a == mi + 3 ? Block.of("spruce_log[axis=y]", 0x3A2A1A) : Block.of("spruce_planks", 0x725430)));
            decos.add(new Deco(a, mj, 3, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430)));
        }
        decos.add(new Deco(mi + 1, mj + 1, 2, Blocks.wallSign("spruce", "south", "white", false, "북한산", "등산 안내도")));
        decos.add(new Deco(mi + 2, mj + 1, 1, Blocks.wallSign("spruce", "south", "white", false, "백운대 3.2km", "대동문 2.5km", "산불 조심")));
        for (int a = gate[0] - 6; a <= gate[0] - 3; a += 3) {
            decos.add(new Deco(a, plaza.ab - 1, 0, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430)));
            decos.add(new Deco(a + 1, plaza.ab - 1, 0, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430)));
        }
        decos.add(new Deco(gate[1] + 4, plaza.aa + 1, 0, Block.of("water_cauldron[level=3]", 0x3F76E4)));
        // 광장 뒤 등산로: 문 기둥 (돌), 가로 나무, 매단 표지판, 그 뒤로 지그재그 흙길 ({@link HillTrail})
        int pa = plaza.aa;
        int ta = ci - 1, tb = ci;
        trail = new HillTrail(this, ta, pa - 1, L);
        for (java.util.Map.Entry<Long, int[]> e : trail.cells.entrySet()) {
            long key = e.getKey();
            int ti = (int) (key >> 32), tj = (int) key;
            if (inside(ti, tj) && tj < pa) {
                level(ti, tj, e.getValue()[0] - 1, HillTrail.floor(e.getValue()[1]), true);
            }
        }
        // 문은 광장 맨 뒤 줄 (광장 높이)에: 돌기둥 둘, 통나무 들보, 매단 표지판
        for (int y = 0; y <= 3; y++) {
            decos.add(new Deco(ta - 1, pa, y, Block.of("mossy_stone_bricks", 0x737969)));
            decos.add(new Deco(tb + 1, pa, y, Block.of("mossy_stone_bricks", 0x737969)));
        }
        for (int a = ta - 2; a <= tb + 2; a++) {
            decos.add(new Deco(a, pa, 4, Block.of("spruce_log[axis=x]", 0x3A2A1A)));
        }
        decos.add(new Deco(ta, pa, 3, Blocks.hangingSign("spruce", 0, "white", false, "북한산", "등산로 입구")));
    }

    private void addTrailLot(Lot lot) {
        int idx = lots.size();
        lots.add(lot);
        for (int j = lot.j0(); j <= lot.j1(); j++) {
            for (int i = lot.i0(); i <= lot.i1(); i++) {
                level(i, j, lot.level() - 1, LOT_FLOOR, false);
                owner[k(i, j)] = idx;
            }
        }
    }

    private Lot restaurant(int a, int b, Row row, String[] sign, boolean clothes) {
        int lw = b - a + 1, depth = row.jb - row.ja + 1;
        long seed = rnd.nextLong();
        StreetBuilding.Spec spec = new StreetBuilding.Spec();
        if (clothes) {
            spec.ground = new StreetBuilding.Shop(StreetBuilding.Use.CLOTHES, sign[0], sign[1]);
            spec.upper = new StreetBuilding.Shop[]{new StreetBuilding.Shop(StreetBuilding.Use.CLOTHES, sign[0], "2층 매장")};
        } else {
            spec.ground = new StreetBuilding.Shop(StreetBuilding.Use.RESTAURANT, sign[0], sign[1], "pocha");
            spec.upper = new StreetBuilding.Shop[]{new StreetBuilding.Shop(StreetBuilding.Use.RESTAURANT, sign[0], "2층 단체석", "bbq")};
        }
        spec.skin = new int[]{0, 7, 8, 4}[rnd.nextInt(4)];
        spec.coreLeft = rnd.nextBoolean();
        return new Lot(name + " " + sign[0], clothes ? "shop" : "restaurant", a, row.ja, b, row.jb, row.level, new int[]{0, 4},
                () -> StreetBuilding.build(lw, depth, new Random(seed), spec));
    }

    /** 공영주차장: 주차선·차 자리, 길가 쪽 매표 부스와 표지판 */
    private static Voxels parking(int w, int d) {
        Voxels v = ParkingLot.build(w, d, null);
        Voxels out = new Voxels(w, d, -1, 4);
        PrisonPlan.stamp(out, v, 0, 0, "south");
        for (double[] c : v.carSpots()) {
            out.carSpot(c[0], (int) c[1], c[2], (int) c[3], (int) c[4]);
        }
        out.fill(w - 3, 0, d - 3, w - 2, 2, d - 2, Block.of("white_concrete", 0xCFD5D6));
        out.set(w - 3, 1, d - 2, GLASS_PANE);
        out.fill(w - 3, 3, d - 3, w - 2, 3, d - 2, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        out.set(w - 4, 0, d - 1, Block.of("polished_blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x353038));
        out.set(w - 4, 1, d - 1, Block.of("polished_blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x353038));
        out.set(w - 4, 2, d - 1, Blocks.hangingSign("birch", 0, "blue", false, "공영주차장", "P", "북한산 입구"));
        out.connect();
        return out;
    }

    // ------------------------------------------------------------------ 난간·가로등·화분

    private void decorate() {
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int kk = k(i, j);
                if (!walk[kk] || top[kk] == NONE) {
                    continue;
                }
                int G = top[kk];
                boolean drop = false;
                for (int[] n : nb) {
                    int ni = i + n[0], nj = j + n[1];
                    if (!inside(ni, nj)) {
                        continue;
                    }
                    int nk = k(ni, nj);
                    if (off[nk] || owner[nk] >= 0 || walk[nk]) {
                        continue;
                    }
                    if (fin(ni, nj) <= G - 2) {
                        drop = true;
                    }
                }
                boolean trailCell = surf[kk] == TRAIL || (surf[kk] != null && surf[kk].id().equals("minecraft:spruce_stairs"));
                if (drop && surf[kk] != null && !surf[kk].id().endsWith("_stairs") && !trailCell) {
                    decos.add(new Deco(i, j, 0, rail));
                }
            }
        }
        // 보안등: 골목 위쪽 가장자리 (집 앞), 12칸마다
        if (style == Style.HAEBANGCHON || style == Style.PYEONGCHANG) {
            for (int r = 1; r < rows.size(); r++) {
                Row row = rows.get(r);
                for (int i = 4; i <= w - 5; i += 12) {
                    if (inCorridor(i) || inCorridor(i + 1) || inCorridor(i - 1)) {
                        continue;
                    }
                    int j = row.aa;
                    if (top[k(i, j)] == NONE || owner[k(i, j)] >= 0) {
                        continue;
                    }
                    for (int y = 0; y <= 3; y++) {
                        decos.add(new Deco(i, j, y, StreetPlan.POST));
                    }
                    decos.add(new Deco(i, j, 4, StreetPlan.TOP_SLAB));
                    decos.add(new Deco(i, j + 1, 3, Block.of("lantern[hanging=true,waterlogged=false]", 0x6A5B49)));
                }
            }
        }
        // 집 사이 빈 칸: 화분, 고무대야 텃밭, 평상
        if (style == Style.HAEBANGCHON) {
            for (int j = 0; j < d; j++) {
                for (int i = 2; i < w - 2; i++) {
                    int kk = k(i, j);
                    if (walk[kk] || owner[kk] >= 0 || top[kk] == NONE || off[kk] || rnd.nextInt(10) != 0) {
                        continue;
                    }
                    boolean byHouse = false;
                    for (int[] n : nb) {
                        byHouse |= inside(i + n[0], j + n[1]) && owner[k(i + n[0], j + n[1])] >= 0;
                    }
                    if (!byHouse) {
                        continue;
                    }
                    decos.add(new Deco(i, j, 0, rnd.nextBoolean() ? Furniture.PLANTS[rnd.nextInt(Furniture.PLANTS.length)]
                            : Block.of("composter[level=7]", 0x86643B)));
                }
            }
        }
    }

    // ------------------------------------------------------------------ 블록 상자

    /** 석축 블록 (자리마다 조금씩 다르게) */
    private Block face(int i, int y, int j) {
        long h = (i * 73856093L) ^ (y * 19349663L) ^ (j * 83492791L);
        return faces[(int) Math.floorMod(h, (long) faces.length)];
    }

    /**
     * 칸 (i, j) 세로줄을 상자 v 의 (a, b) 에 그립니다: 깎기·채우기·드러난 면 석축·윗면.
     * 반환: 쓴 가장 낮은 y (안 쓰면 NONE)
     */
    private void carve(Voxels v, int a, int b, int i, int j) {
        int kk = k(i, j);
        if (off[kk]) {
            return;
        }
        int T = nat[kk], G = top[kk];
        int F = G == NONE ? T : G;
        int low = F;
        boolean leveledNb = false;
        for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int ni = i + n[0], nj = j + n[1];
            if (inside(ni, nj) && off[k(ni, nj)]) {
                continue;
            }
            int nf = fin(ni, nj);
            boolean nl = inside(ni, nj) && top[k(ni, nj)] != NONE;
            if (G != NONE || nl) {
                low = Math.min(low, nf);
            }
            leveledNb |= nl;
        }
        if (G == NONE) {
            // 자연 땅: 옆을 깎아서 드러난 면만 석축
            if (leveledNb) {
                for (int y = low + 1; y < T; y++) {
                    v.set(a, y, b, face(i, y, j));
                }
            }
            return;
        }
        for (int y = G + 1; y <= T; y++) {
            v.set(a, y, b, AIR);
        }
        for (int y = T + 1; y < G; y++) {
            v.set(a, y, b, FILL);
        }
        for (int y = low + 1; y < G; y++) {
            v.set(a, y, b, face(i, y, j));
        }
        v.set(a, G, b, surf[kk]);
    }

    /** 칸들 (i0..i1, j0..j1) 을 덮는 상자의 높이 범위 {lo, hi} */
    private int[] range(int i0, int j0, int i1, int j1) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int j = j0 - 1; j <= j1 + 1; j++) {
            for (int i = i0 - 1; i <= i1 + 1; i++) {
                int f = fin(i, j), n = nat(i, j);
                lo = Math.min(lo, Math.min(f, n));
                hi = Math.max(hi, Math.max(f, n));
            }
        }
        return new int[]{lo, hi};
    }

    /** 바닥 (골목·계단·석축·장식): 필지 칸은 비움 */
    Voxels groundVoxels() {
        int[] r = range(0, 0, w - 1, d - 1);
        Voxels v = new Voxels(w, d, r[0], r[1] + 14);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (owner[k(i, j)] < 0) {
                    carve(v, i, j, i, j);
                }
            }
        }
        Random tr = new Random(decoSeed);
        for (Deco dc : decos) {
            if (!inside(dc.i, dc.j) || owner[k(dc.i, dc.j)] >= 0 || off[k(dc.i, dc.j)]) {
                continue;
            }
            if (dc.block == null) {
                if (dc.i > 2 && dc.i < w - 3 && dc.j > 2) {
                    Kit.tree(v, dc.i, dc.j, fin(dc.i, dc.j), tr);
                }
                continue;
            }
            v.set(dc.i, fin(dc.i, dc.j) + 1 + dc.dy, dc.j, dc.block);
        }
        v.connect();
        return v;
    }

    /** 필지 하나: 그 칸들의 땅 고르기 + 건물 (서는 높이로 올려서) */
    Voxels lotVoxels(Lot lot) {
        Voxels b = lot.build().get();
        int lw = lot.i1() - lot.i0() + 1, ld = lot.j1() - lot.j0() + 1;
        int[] r = range(lot.i0(), lot.j0(), lot.i1(), lot.j1());
        int lo = Math.min(r[0], lot.level() + b.y0), hi = Math.max(r[1], lot.level() + b.y0 + b.h - 1);
        Voxels v = new Voxels(lw, ld, lo, hi);
        for (int j = 0; j < ld; j++) {
            for (int i = 0; i < lw; i++) {
                carve(v, i, j, lot.i0() + i, lot.j0() + j);
            }
        }
        PrisonPlan.stamp(v, b, 0, lot.level(), 0, "south");
        for (double[] c : b.carSpots()) {
            v.carSpot(c[0], (int) c[1] + lot.level(), c[2], (int) c[3], (int) c[4]);
        }
        return v;
    }

    /** 마을 좌표 직사각형 → 월드 {x0, z0, x1, z1} */
    int[] worldRect(int i0, int j0, int i1, int j1) {
        int[] a = g.world(i0, j0), c = g.world(i1, j1);
        return new int[]{Math.min(a[0], c[0]), Math.min(a[1], c[1]), Math.max(a[0], c[0]), Math.max(a[1], c[1])};
    }

    List<Placement> placements() {
        List<Placement> out = new ArrayList<>();
        String front = site.front();
        String groundName = switch (style) {
            case PYEONGCHANG -> name + " 골목·석축";
            case TRAILHEAD -> name + " 광장";
            default -> name + " 골목·계단길";
        };
        out.add(Placement.rect(groundName, "plaza", site.x0(), site.z0(), site.x1(), site.z1(), front, (ww, dd) -> groundVoxels()));
        for (Lot lot : lots) {
            int[] wr = worldRect(lot.i0(), lot.j0(), lot.i1(), lot.j1());
            out.add(Placement.rect(lot.name(), lot.kind(), wr[0], wr[1], wr[2], wr[3], front, (ww, dd) -> lotVoxels(lot)));
        }
        if (trail != null) {
            out.addAll(trail.placement());
        }
        return out;
    }
}
