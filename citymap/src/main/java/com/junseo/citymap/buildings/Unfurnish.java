package com.junseo.citymap.buildings;

import java.util.Set;

/**
 * 집 안 가구를 걷어 냅니다 [운영자 요청: 주택 안 가구는 모두 없앰 — 플레이어가 직접 꾸밈].
 * 방·벽·문·바닥·계단·조명·창은 그대로 두고, 침대(양털·카펫), 부엌(조리대·냉장고·스토브·싱크),
 * 욕실 설비(변기·욕조·샤워), 책상·탁자·의자·소파, 화분 같은 살림만 지웁니다.
 * <p>
 * 바깥에 있는 같은 블록(벽에 붙은 실외기, 간판, 마당 장식)은 남기려고, 천장 아래이면서 네 방향 모두
 * 가까이에 벽이 있는(실내) 칸만 지웁니다. 나무 계단은 위아래로 이어진 계단(층 계단)이면 남기고
 * 따로 놓인 것(의자·소파)만 지웁니다.
 */
final class Unfurnish {
    private static final Set<String> EXACT = Set.of(
            "smoker", "furnace", "blast_furnace", "cauldron", "water_cauldron", "barrel", "crafting_table", "bookshelf",
            "loom", "composter", "flower_pot", "lightning_rod", "iron_chain", "iron_block", "smooth_quartz",
            "smooth_quartz_slab", "stonecutter", "grindstone", "smithing_table", "cartography_table",
            "fletching_table", "note_block", "anvil", "brewing_stand");
    private static final String[] WOOD = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
            "pale_oak", "bamboo", "crimson", "warped"};
    private static final int REACH = 12, CEILING = 6, LANTERN_GAP = 7;
    /** 집: 방·벽·계단은 두고 가구만 걷음 */
    private static final Set<String> HOMES = Set.of("house");
    /** 사람이 안 들어가는 건물: 바깥벽·창·지붕·층 바닥만 남기고 속을 비움 */
    private static final Set<String> HOLLOW = Set.of("shop", "office", "school", "campus", "retail", "cargo");

    /**
     * 렉 줄이기 [운영자 요청: "건물에 뭐가 너무 많아서 렉이 걸려", "표지판들도 다 없애고",
     * "사람 안 들어갈 건물들은 다 비운다" — 직업·상호작용 건물(시장·보석상·의료국 등)은 제외]:
     * <ul>
     *   <li>모든 건물: 표지판 없앰, 지붕 아래 등은 LANTERN_GAP 칸에 하나, 실내 화분·카펫·초 없앰.</li>
     *   <li>집(house): 가구만 걷음. 아파트: 아래 3개 층은 가구만 걷고 그 위는 비움.</li>
     *   <li>동네 상가·업무 빌딩·학교·캠퍼스: 속을 비움 ({@link #hollow}).</li>
     *   <li>63빌딩·준서월드타워: 로비·은행·금고·쇼핑몰·꼭대기 전망층은 두고 가운데 사무 층은 가구만 걷음 (계단은 남김).</li>
     *   <li>나머지(시장·보석상·병원·경찰서·교도소·역·터미널·항만·직업 현장·차고·카지노·클럽 등): 실내 그대로.</li>
     * </ul>
     */
    static void declutter(Voxels v, String kind) {
        v.removeIf(b -> name(b).endsWith("_sign"));
        if (kind.equals("pave") || kind.equals("street") || kind.equals("hubcover")) {
            return;
        }
        int top = v.y0 + v.h - 1;
        if (HOMES.contains(kind)) {
            strip(v);
        } else if (kind.equals("apartment")) {
            strip(v, v.y0, 11);
            hollow(v, 12, top);
        } else if (HOLLOW.contains(kind)) {
            hollow(v, 0, top);
        } else if (kind.equals("bank")) {
            strip(v, 14, top - 16); // 가운데 사무 층은 가구만 (계단은 꼭대기 전망층까지 이어져야 함)
        } else if (kind.equals("skyscraper")) {
            strip(v, 30, top - 56);
        }
        for (int y = top; y >= v.y0; y--) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b == null || b.isAir()) {
                        continue;
                    }
                    String id = name(b);
                    boolean decor = id.startsWith("potted_") || id.equals("flower_pot") || id.endsWith("_carpet") || id.contains("candle");
                    if (decor && covered(v, i, y, j)) {
                        v.set(i, y, j, Blocks.AIR);
                    }
                }
            }
        }
        thinLanterns(v);
    }

    /**
     * 속 비우기: 높이 [yLo, yHi] 의 층마다 바깥에서 들어오는 빈 칸을 칠해 바깥벽(바깥에 닿은 칸)을 찾고,
     * 대부분 막힌 층(바닥·천장 판)은 그대로, 나머지 층의 안쪽 블록(칸막이·계단·가구·문)은 모두 지움.
     * 바닥 판에는 8칸마다 천장 조명을 박아 밤에 빈 상자로 안 보이게 함.
     */
    static void hollow(Voxels v, int yLo, int yHi) {
        int lo = Math.max(v.y0, yLo), hi = Math.min(v.y0 + v.h - 1, yHi);
        if (hi < lo) {
            return;
        }
        int w = v.w, d = v.d;
        boolean[][] out = new boolean[hi - lo + 1][];
        boolean[] floor = new boolean[hi - lo + 1];
        for (int y = lo; y <= hi; y++) {
            boolean[] o = outside(v, y);
            out[y - lo] = o;
            int inner = 0, full = 0;
            for (int k = 0; k < w * d; k++) {
                if (!o[k]) {
                    inner++;
                    if (solid(v.get(k % w, y, k / w))) {
                        full++;
                    }
                }
            }
            floor[y - lo] = inner > 0 && full >= inner * 0.7;
        }
        for (int y = lo; y <= hi; y++) {
            boolean[] o = out[y - lo];
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    if (o[j * w + i] || !solid(v.get(i, y, j))) {
                        continue;
                    }
                    boolean facade = i == 0 || j == 0 || i == w - 1 || j == d - 1
                            || o[j * w + i - 1] || o[j * w + i + 1] || o[(j - 1) * w + i] || o[(j + 1) * w + i];
                    if (facade) {
                        continue;
                    }
                    if (floor[y - lo]) {
                        if (i % 8 == 4 && j % 8 == 4 && y > lo && !floor[y - 1 - lo] && !solid(v.get(i, y - 1, j))) {
                            v.set(i, y, j, Interior.LIGHT);
                        }
                        continue;
                    }
                    v.set(i, y, j, Blocks.AIR);
                }
            }
        }
    }

    /** 층 y 에서 상자 가장자리부터 막히지 않은 칸으로 이어지는 바깥 칸 */
    private static boolean[] outside(Voxels v, int y) {
        int w = v.w, d = v.d;
        boolean[] o = new boolean[w * d];
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        for (int i = 0; i < w; i++) {
            seed(v, y, i, 0, o, q);
            seed(v, y, i, d - 1, o, q);
        }
        for (int j = 0; j < d; j++) {
            seed(v, y, 0, j, o, q);
            seed(v, y, w - 1, j, o, q);
        }
        while (!q.isEmpty()) {
            int k = q.poll();
            int i = k % w, j = k / w;
            seed(v, y, i + 1, j, o, q);
            seed(v, y, i - 1, j, o, q);
            seed(v, y, i, j + 1, o, q);
            seed(v, y, i, j - 1, o, q);
        }
        return o;
    }

    private static void seed(Voxels v, int y, int i, int j, boolean[] o, java.util.ArrayDeque<Integer> q) {
        if (i < 0 || j < 0 || i >= v.w || j >= v.d || o[j * v.w + i] || solid(v.get(i, y, j))) {
            return;
        }
        o[j * v.w + i] = true;
        q.add(j * v.w + i);
    }

    /** 지붕 아래 등(랜턴)은 가로세로 LANTERN_GAP 칸 안에 하나만 (매단 사슬도 같이 지움) */
    private static void thinLanterns(Voxels v) {
        java.util.List<int[]> kept = new java.util.ArrayList<>();
        for (int y = v.y0 + v.h - 1; y >= v.y0; y--) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b == null || !name(b).endsWith("lantern") || !covered(v, i, y, j)) {
                        continue;
                    }
                    boolean near = false;
                    for (int[] k : kept) {
                        if (Math.abs(k[0] - i) < LANTERN_GAP && Math.abs(k[2] - j) < LANTERN_GAP && Math.abs(k[1] - y) <= 2) {
                            near = true;
                            break;
                        }
                    }
                    if (!near) {
                        kept.add(new int[]{i, y, j});
                        continue;
                    }
                    v.set(i, y, j, Blocks.AIR);
                    for (int up = y + 1; up < v.y0 + v.h; up++) {
                        Block c = v.get(i, up, j);
                        if (c == null || !name(c).equals("iron_chain")) {
                            break;
                        }
                        v.set(i, up, j, Blocks.AIR);
                    }
                }
            }
        }
    }

    /** 위 CEILING 칸 안에 막힌 칸이 있음 (지붕·천장 아래) */
    private static boolean covered(Voxels v, int i, int y, int j) {
        for (int k = 1; k <= CEILING; k++) {
            if (solid(v.get(i, y + k, j))) {
                return true;
            }
        }
        return false;
    }

    /** y 범위 [yLo, yHi] 안의 실내 가구를 지움 */
    static void strip(Voxels v, int yLo, int yHi) {
        int lo = Math.max(v.y0, yLo), hi = Math.min(v.y0 + v.h - 1, yHi);
        for (int y = hi; y >= lo; y--) { // 위에서부터 (TV 를 받침보다 먼저)
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b != null && !b.isAir() && furniture(v, b, i, y, j) && indoors(v, i, y, j)) {
                        v.set(i, y, j, Blocks.AIR);
                    }
                }
            }
        }
    }

    static void strip(Voxels v) {
        strip(v, v.y0, v.y0 + v.h - 1);
    }

    private static boolean furniture(Voxels v, Block b, int i, int y, int j) {
        String id = name(b);
        if (EXACT.contains(id) || id.endsWith("_wool") || id.endsWith("_carpet") || id.startsWith("potted_") || id.contains("candle")) {
            return true;
        }
        if (id.endsWith("_trapdoor") && "true".equals(b.property("open"))) {
            return true; // 침대 머리판·책상 가림판·수납장 문
        }
        if (id.equals("quartz_stairs")) {
            return !staircase(v, i, y, j); // 변기 (층 계단이 아니면)
        }
        if (id.equals("black_concrete")) {
            Block below = v.get(i, y - 1, j);
            return below != null && name(below).endsWith("_slab") && "bottom".equals(below.property("type")); // TV (받침 위)
        }
        for (String w : WOOD) {
            if (id.equals(w + "_slab") && "top".equals(b.property("type"))) {
                return true; // 책상·탁자 상판
            }
            if (id.equals(w + "_slab") && "bottom".equals(b.property("type"))) {
                return !staircase(v, i, y, j); // TV 받침·낮은 수납장 (계단참이 아니면)
            }
            if (id.equals(w + "_stairs")) {
                return !staircase(v, i, y, j); // 의자·소파
            }
            if (id.equals(w + "_fence")) {
                return true; // 탁자 다리
            }
        }
        return id.endsWith("_pressure_plate");
    }

    /** 위나 아래 대각선에 계단·반 블록이 이어지면 층 계단 */
    private static boolean staircase(Voxels v, int i, int y, int j) {
        for (int dy = -1; dy <= 1; dy += 2) {
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    Block n = v.get(i + di, y + dy, j + dj);
                    if (n != null && (n.id().endsWith("_stairs") || n.id().endsWith("_slab"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 천장 아래이고 네 방향 모두 REACH 안에 벽(막힌 칸)이 있으면 실내 */
    private static boolean indoors(Voxels v, int i, int y, int j) {
        boolean ceiling = false;
        for (int k = 1; k <= CEILING && !ceiling; k++) {
            ceiling = solid(v.get(i, y + k, j));
        }
        if (!ceiling) {
            return false;
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dd : dirs) {
            boolean wall = false;
            for (int k = 1; k <= REACH && !wall; k++) {
                int ni = i + dd[0] * k, nj = j + dd[1] * k;
                if (ni < 0 || nj < 0 || ni >= v.w || nj >= v.d) {
                    break;
                }
                wall = solid(v.get(ni, y, nj));
            }
            if (!wall) {
                return false;
            }
        }
        return true;
    }

    /** "minecraft:" 를 뗀 블록 이름 */
    static String name(Block b) {
        String id = b.id();
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private static boolean solid(Block b) {
        return b != null && !b.isAir();
    }

    private Unfurnish() {
    }
}
