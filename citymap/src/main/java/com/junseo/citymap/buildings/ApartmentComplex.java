package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 도로로 둘러싸인 땅 덩어리 하나를 아파트 단지로 채웁니다.
 * <ul>
 *   <li>큰길에 가장 많이 닿은 변이 정문 쪽. 그 변을 따라 20칸 띠에 문주 진입로, 지하주차장 입구, 단지 상가, 관리동
 *       (옛 단지는 경비실과 이름 돌담, 지상 주차장)</li>
 *   <li>나머지 땅에 남향 동을 북쪽부터 채워 넣음 (동 사이 띄움). 구역마다 동 모양이 다름:
 *       송파(잠실 대단지 25~33층 판상형·타워형 섞음), 강남(대치·도곡 타워형 25~35층, 아래 3층 어두운 돌),
 *       마포(공덕 20~25층 판상형·타워형, 베이지), 여의도(1970년대 시범아파트 12~13층 복도식, 바랜 크림색, 큰 동 번호),
 *       광진·용산(한강변 15~25층, 흰 벽에 하늘색), 그 밖(15~20층 계단식, 지상 주차장)</li>
 *   <li>남은 땅: 커뮤니티센터, 놀이터, (옛 단지) 지상 주차장, 그리고 남김없이 단지 공원 (산책로·나무·정자·운동기구)</li>
 * </ul>
 * 모든 조각은 지을 수 있는 땅(m.rectFree)에만 놓고 둘레 1칸까지 m.claim 합니다. 조각마다 둘레 1칸을 잔디로 덮어
 * 조각 사이가 끊겨 보이지 않게 합니다 (원래 지을 수 있던 땅만).
 */
final class ApartmentComplex {
    /** 구역별 단지 모양 */
    record Look(boolean modern, int minF, int maxF, int towerPct, int maxLines, boolean piloti, int baseFloors,
                boolean corridor, AptUnit.Skin[] skins, Block gateStone, Block gateCap) {
    }

    private static final Block GRANITE = POLISHED_GRANITE;
    private static final Block DEEP = Block.of("polished_deepslate", 0x484849);
    private static final Block WHITE_PANE = Block.of("white_stained_glass_pane", 0xF0F0F0);
    private static final Block BLUE_PANE = Block.of("light_blue_stained_glass_pane", 0x6699D8);
    private static final Block GRAY_PANE_B = Block.of("gray_stained_glass_pane", 0x4C4C4C);
    private static final Block LIGHT_GRAY_PANE = Block.of("light_gray_stained_glass_pane", 0x999999);

    static Look look(String district) {
        return switch (district == null ? "" : district) {
            case "songpa" -> new Look(true, 25, 33, 40, 3, true, 2, false, new AptUnit.Skin[]{
                    skin(WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, Block.of("brown_terracotta", 0x4D3323), GLASS_PANE, WHITE_PANE, GRANITE, LIGHT_GRAY_CONCRETE, Block.of("brown_terracotta", 0x4D3323), SMOOTH_STONE),
                    skin(SMOOTH_QUARTZ, WHITE_TERRACOTTA, GRAY_CONCRETE, GLASS_PANE, WHITE_PANE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE, GRAY_CONCRETE, SMOOTH_STONE),
                    skin(WHITE_CONCRETE, SMOOTH_STONE, Block.of("light_gray_terracotta", 0x876A61), BLUE_PANE, WHITE_PANE, GRANITE, Block.of("light_gray_terracotta", 0x876A61), Block.of("brown_terracotta", 0x4D3323), SMOOTH_STONE)},
                    GRANITE, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            case "gangnam" -> new Look(true, 25, 35, 100, 2, true, 3, false, new AptUnit.Skin[]{
                    skin(SMOOTH_QUARTZ, LIGHT_GRAY_CONCRETE, DEEP, LIGHT_GRAY_PANE, GLASS_PANE, DEEP, LIGHT_GRAY_CONCRETE, DEEP, SMOOTH_STONE),
                    skin(WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, Block.of("polished_blackstone", 0x353038), LIGHT_GRAY_PANE, GLASS_PANE,
                            Block.of("polished_blackstone", 0x353038), LIGHT_GRAY_CONCRETE, Block.of("polished_blackstone", 0x353038), SMOOTH_STONE),
                    skin(Block.of("calcite", 0xDFE0DC), SMOOTH_STONE, GRAY_CONCRETE, GRAY_PANE_B, WHITE_PANE, DEEP, GRAY_CONCRETE, GRAY_CONCRETE, SMOOTH_STONE)},
                    DEEP, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
            case "mapo" -> new Look(true, 20, 25, 50, 2, true, 2, false, new AptUnit.Skin[]{
                    skin(SANDSTONE, WHITE_TERRACOTTA, TERRACOTTA, GLASS_PANE, WHITE_PANE, GRANITE, WHITE_TERRACOTTA, Block.of("brown_terracotta", 0x4D3323), SMOOTH_STONE),
                    skin(WHITE_TERRACOTTA, SMOOTH_STONE, Block.of("brown_terracotta", 0x4D3323), GLASS_PANE, WHITE_PANE, GRANITE, SMOOTH_STONE, Block.of("brown_terracotta", 0x4D3323), SMOOTH_STONE),
                    skin(WHITE_CONCRETE, WHITE_TERRACOTTA, Block.of("orange_terracotta", 0xA15325), GLASS_PANE, WHITE_PANE, GRANITE, WHITE_TERRACOTTA, TERRACOTTA, SMOOTH_STONE)},
                    GRANITE, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            case "gwangjin", "yongsan" -> new Look(true, 15, 25, 30, 3, true, 1, false, new AptUnit.Skin[]{
                    skin(WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, Block.of("light_blue_terracotta", 0x716C89), BLUE_PANE, WHITE_PANE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE, Block.of("blue_terracotta", 0x4A3B5B), SMOOTH_STONE),
                    skin(SMOOTH_QUARTZ, WHITE_CONCRETE, Block.of("cyan_terracotta", 0x565B5B), GLASS_PANE, WHITE_PANE, POLISHED_ANDESITE, WHITE_CONCRETE, Block.of("cyan_terracotta", 0x565B5B), SMOOTH_STONE)},
                    POLISHED_ANDESITE, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            case "yeouido" -> new Look(false, 12, 13, 0, 0, false, 0, true, new AptUnit.Skin[]{
                    skin(SANDSTONE, WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, GLASS_PANE, GLASS_PANE, STONE_BRICKS, WHITE_CONCRETE, GRAY_CONCRETE, Block.of("green_terracotta", 0x4C532A)),
                    skin(Block.of("white_terracotta", 0xD1B2A1), WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, GLASS_PANE, GLASS_PANE, STONE_BRICKS, WHITE_CONCRETE, Block.of("red_terracotta", 0x8F3D2E), Block.of("green_terracotta", 0x4C532A))},
                    STONE_BRICKS, Block.of("stone_brick_slab[type=bottom,waterlogged=false]", 0x7A7979));
            default -> new Look(false, 15, 20, 0, 3, false, 0, false, new AptUnit.Skin[]{
                    skin(WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, Block.of("light_blue_terracotta", 0x716C89), GLASS_PANE, WHITE_PANE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE, Block.of("blue_terracotta", 0x4A3B5B), Block.of("green_terracotta", 0x4C532A)),
                    skin(WHITE_TERRACOTTA, SMOOTH_STONE, Block.of("orange_terracotta", 0xA15325), GLASS_PANE, WHITE_PANE, POLISHED_ANDESITE, SMOOTH_STONE, Block.of("brown_terracotta", 0x4D3323), Block.of("green_terracotta", 0x4C532A))},
                    POLISHED_ANDESITE, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        };
    }

    private static AptUnit.Skin skin(Block wall, Block band, Block accent, Block glass, Block rail, Block base, Block frame, Block number, Block roof) {
        return new AptUnit.Skin(wall, band, accent, glass, rail, base, frame, number, roof);
    }

    /**
     * 도로로 둘러싸인 땅 덩어리 하나를 아파트 단지로. 쓴 땅은 m.claim 으로 표시.
     *
     * @param block {minX, minZ, maxX, maxZ, 칸 수} (BuildMask.blocks / componentNear)
     */
    static List<Placement> plan(BuildMask m, int[] block, String district, String complexName, Random r) {
        ApartmentComplex c = new ApartmentComplex(m, block, district, complexName, r);
        c.run();
        return c.out;
    }

    // ------------------------------------------------------------------ 상태

    private final BuildMask m;
    private final int bx0, bz0, bx1, bz1, bw, bd;
    private final Look look;
    private final AptUnit.Skin skin;
    private final String name, label;
    private final Random r;
    private final boolean old;
    private final List<Placement> out = new ArrayList<>();
    /** 처음에 지을 수 있던 땅 (둘레 1칸 포함 상자) */
    private final boolean[] orig;
    /** 이 단지가 차지한 칸 (둘레 띄움 포함) */
    private final boolean[] mine;
    private int[] freeSum, mineSum;
    private int dong;
    /** 정문 쪽 변: "south", "north", "east", "west" */
    private String gate;

    private ApartmentComplex(BuildMask m, int[] block, String district, String complexName, Random r) {
        this.m = m;
        this.bx0 = block[0];
        this.bz0 = block[1];
        this.bx1 = block[2];
        this.bz1 = block[3];
        this.bw = bx1 - bx0 + 1;
        this.bd = bz1 - bz0 + 1;
        this.look = look(district);
        this.r = r;
        this.skin = look.skins()[r.nextInt(look.skins().length)];
        this.name = complexName == null || complexName.isEmpty() ? "준서" : complexName;
        this.label = name + "아파트";
        this.old = !look.modern();
        this.dong = look.corridor() ? 1 + r.nextInt(8) : 101;
        this.orig = new boolean[(bw + 2) * (bd + 2)];
        this.mine = new boolean[bw * bd];
        for (int z = bz0 - 1; z <= bz1 + 1; z++) {
            for (int x = bx0 - 1; x <= bx1 + 1; x++) {
                orig[(z - bz0 + 1) * (bw + 2) + (x - bx0 + 1)] = m.free(x, z);
            }
        }
    }

    private boolean origFree(int x, int z) {
        int i = x - bx0 + 1, j = z - bz0 + 1;
        return i >= 0 && j >= 0 && i < bw + 2 && j < bd + 2 && orig[j * (bw + 2) + i];
    }

    private void sums() {
        freeSum = new int[(bw + 1) * (bd + 1)];
        mineSum = new int[(bw + 1) * (bd + 1)];
        for (int j = 0; j < bd; j++) {
            for (int i = 0; i < bw; i++) {
                int k = (j + 1) * (bw + 1) + i + 1;
                freeSum[k] = freeSum[k - 1] + freeSum[k - bw - 1] - freeSum[k - bw - 2] + (m.free(bx0 + i, bz0 + j) ? 1 : 0);
                mineSum[k] = mineSum[k - 1] + mineSum[k - bw - 1] - mineSum[k - bw - 2] + (mine[j * bw + i] ? 1 : 0);
            }
        }
    }

    /** 상자 안 (월드 좌표, 덩어리 상자로 잘라서) 합 */
    private static int sum(int[] s, int bw, int bx0, int bz0, int bd, int x0, int z0, int x1, int z1) {
        int i0 = Math.max(0, x0 - bx0), j0 = Math.max(0, z0 - bz0), i1 = Math.min(bw - 1, x1 - bx0), j1 = Math.min(bd - 1, z1 - bz0);
        if (i1 < i0 || j1 < j0) {
            return 0;
        }
        int W = bw + 1;
        return s[(j1 + 1) * W + i1 + 1] - s[j0 * W + i1 + 1] - s[(j1 + 1) * W + i0] + s[j0 * W + i0];
    }

    /** 이 상자가 모두 지을 수 있는 땅이고, 둘레 gx·gz 칸 안에 이 단지가 이미 쓴 칸이 없는지 */
    private boolean fits(int x0, int z0, int x1, int z1, int gx, int gz) {
        if (x0 < bx0 || z0 < bz0 || x1 > bx1 || z1 > bz1) {
            return false;
        }
        int area = (x1 - x0 + 1) * (z1 - z0 + 1);
        return sum(freeSum, bw, bx0, bz0, bd, x0, z0, x1, z1) == area
                && sum(mineSum, bw, bx0, bz0, bd, x0 - gx, z0 - gz, x1 + gx, z1 + gz) == 0;
    }

    // ------------------------------------------------------------------ 정문 쪽 변과 단지 좌표 (u: 변을 따라, t: 안쪽으로)

    private String chooseGate() {
        int[] score = new int[4];
        String[] sides = {"south", "north", "east", "west"};
        for (int k = 0; k < 4; k++) {
            int n = 0;
            int len = k < 2 ? bw : bd;
            for (int u = 0; u < len; u++) {
                // 변 바로 안쪽 첫 지을 수 있는 칸이 길 가까이 있는지
                for (int t = 0; t < 4; t++) {
                    int[] p = point(sides[k], u, t);
                    if (origFree(p[0], p[1])) {
                        if (m.roadDistance(p[0], p[1]) <= 4) {
                            n++;
                        }
                        break;
                    }
                }
            }
            score[k] = n * 10 + (k == 0 ? 3 : k == 1 ? 2 : k == 2 ? 1 : 0);
        }
        int best = 0;
        for (int k = 1; k < 4; k++) {
            if (score[k] > score[best]) {
                best = k;
            }
        }
        return sides[best];
    }

    private int length() {
        return gate.equals("south") || gate.equals("north") ? bw : bd;
    }

    private int depth() {
        return gate.equals("south") || gate.equals("north") ? bd : bw;
    }

    private int[] point(String side, int u, int t) {
        return switch (side) {
            case "south" -> new int[]{bx0 + u, bz1 - t};
            case "north" -> new int[]{bx0 + u, bz0 + t};
            case "east" -> new int[]{bx1 - t, bz0 + u};
            default -> new int[]{bx0 + t, bz0 + u};
        };
    }

    /** 단지 좌표 상자 → 월드 상자 {x0, z0, x1, z1} */
    private int[] world(int u0, int t0, int u1, int t1) {
        int[] a = point(gate, u0, t0), b = point(gate, u1, t1);
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    /** 단지 좌표 방향 ("gate", "in", "+u", "-u") → 월드 방향 */
    private String dir(String local) {
        boolean ns = gate.equals("south") || gate.equals("north");
        return switch (local) {
            case "gate" -> gate;
            case "in" -> Furniture.opposite(gate);
            case "+u" -> ns ? "east" : "south";
            default -> ns ? "west" : "north";
        };
    }

    // ------------------------------------------------------------------ 놓기

    /**
     * 월드 상자 [x0..x1]×[z0..z1] 에 놓습니다 (지을 수 있는 땅이어야 함). 둘레 1칸은 원래 지을 수 있던 땅이면 ring 으로 덮습니다.
     * front 쪽으로는 frontRows 칸 (1 = 둘레 한 칸)을 덮는데, 2 이상이면 맨 앞 줄 바닥(y -1)을 그대로 이어 깝니다
     * (진입로를 큰길 인도와 잇기). 블록은 땅 높이 바닥만 두므로 길·다른 건물을 막지 않습니다.
     */
    private boolean place(String pname, String kind, int x0, int z0, int x1, int z1, String front, Block ring, int frontRows,
                          BiFunction<Integer, Integer, Voxels> build) {
        if (!m.rectFree(x0, z0, x1, z1)) {
            return false;
        }
        int ext = Math.max(1, frontRows);
        int ox0 = x0 - 1, oz0 = z0 - 1, ox1 = x1 + 1, oz1 = z1 + 1;
        switch (front) {
            case "south" -> oz1 += ext - 1;
            case "north" -> oz0 -= ext - 1;
            case "east" -> ox1 += ext - 1;
            default -> ox0 -= ext - 1;
        }
        int q = Frame.quarter(front);
        int ww = ox1 - ox0 + 1, wd = oz1 - oz0 + 1;
        boolean[] ringOk = new boolean[ww * wd];
        for (int z = oz0; z <= oz1; z++) {
            for (int x = ox0; x <= ox1; x++) {
                ringOk[(z - oz0) * ww + (x - ox0)] = origFree(x, z);
            }
        }
        final int fx0 = ox0, fz0 = oz0, fx1 = ox1, fz1 = oz1;
        out.add(Placement.rect(pname, kind, ox0, oz0, ox1, oz1, front,
                (w, d) -> List.of(new double[]{1, 1}, new double[]{w - 1, 1}, new double[]{w - 1, d - ext}, new double[]{1, d - ext}),
                (w, d) -> {
                    int iw = w - 2, id = d - 1 - ext;
                    Voxels inner = build.apply(iw, id);
                    Voxels v = new Voxels(w, d, inner.y0, inner.y0 + inner.h - 1);
                    // 만든 상자가 더 커도 자기 자리 안만 옮김
                    for (int y = inner.y0; y < inner.y0 + inner.h; y++) {
                        for (int j = 0; j < Math.min(id, inner.d); j++) {
                            for (int i = 0; i < Math.min(iw, inner.w); i++) {
                                Block b = inner.get(i, y, j);
                                if (b != null) {
                                    v.set(i + 1, y, j + 1, b);
                                }
                            }
                        }
                    }
                    for (double[] c : inner.carSpots()) {
                        if (c[0] < iw && c[2] < id) {
                            v.carSpot(c[0] + 1, (int) c[1], c[2] + 1, (int) c[3], (int) c[4]);
                        }
                    }
                    for (int j = 0; j < d; j++) {
                        for (int i = 0; i < w; i++) {
                            if (v.get(i, -1, j) != null) {
                                continue;
                            }
                            if (ext > 1 && j > id && i >= 1 && i <= w - 2) {
                                Block b = inner.get(i - 1, -1, id - 1);
                                if (b != null) {
                                    v.set(i, -1, j, b);
                                }
                                continue;
                            }
                            int[] wxz = toWorld(q, fx0, fz0, fx1, fz1, i, j);
                            if (ringOk[(wxz[1] - fz0) * ww + (wxz[0] - fx0)]) {
                                v.set(i, -1, j, ring);
                            }
                        }
                    }
                    return v;
                }));
        m.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                if (x >= bx0 && z >= bz0 && x <= bx1 && z <= bz1) {
                    mine[(z - bz0) * bw + (x - bx0)] = true;
                }
            }
        }
        sums();
        return true;
    }

    /** Placement.rect 의 건물 좌표 (a, b) → 월드 칸 */
    private static int[] toWorld(int q, int x0, int z0, int x1, int z1, int a, int b) {
        return switch (q) {
            case 0 -> new int[]{x0 + a, z0 + b};
            case 1 -> new int[]{x1 - b, z0 + a};
            case 2 -> new int[]{x1 - a, z1 - b};
            default -> new int[]{x0 + b, z1 - a};
        };
    }

    private boolean place(String pname, String kind, int[] rect, String front, BiFunction<Integer, Integer, Voxels> build) {
        return place(pname, kind, rect[0], rect[1], rect[2], rect[3], front, GRASS, 1, build);
    }

    // ------------------------------------------------------------------ 계획

    private void run() {
        sums();
        gate = chooseGate();
        int band = old ? 18 : 20;
        if (depth() >= band + 30 && length() >= 40) {
            frontBand(band);
        }
        int placed = buildings(gate, band);
        if (placed == 0) {
            buildings(gate, 0);
        }
        leftovers();
    }

    /** 정문 띠: 진입로(문주), 지하주차장 입구, 상가, 관리동, 방문 주차장 */
    private void frontBand(int band) {
        int len = length();
        // 진입로: 가운데에서 가까운 빈자리
        int roadW = ComplexFacilities.GATE_W;
        int[] road = null;
        for (int off = 0; off <= len / 2 && road == null; off += 3) {
            for (int sign : new int[]{1, -1}) {
                int u0 = len / 2 - roadW / 2 + sign * off;
                if (u0 < 0 || u0 + roadW - 1 >= len) {
                    continue;
                }
                int[] w = world(u0, 0, u0 + roadW - 1, band - 1);
                if (fits(w[0], w[1], w[2], w[3], 0, 0)) {
                    road = new int[]{u0, u0 + roadW - 1};
                    int ext = streetGap(w, gate);
                    boolean grand = !old;
                    String label = this.label;
                    Block stone = look.gateStone(), cap = look.gateCap();
                    long seed = r.nextLong();
                    place(label + " 정문", "plaza", w[0], w[1], w[2], w[3], dir("gate"), GRASS, ext,
                            (bw2, bd2) -> ComplexFacilities.gateRoad(bw2, bd2, name, grand, stone, cap, new Random(seed)));
                    break;
                }
            }
        }
        if (road == null) {
            return;
        }
        int rightRoom = len - 1 - road[1], leftRoom = road[0];
        boolean rampRight = rightRoom >= leftRoom;
        // 지하주차장 입구 (새 단지): 진입로 옆, 정면이 진입로를 봄
        if (!old) {
            int rd = ComplexFacilities.RAMP_D, rw = ComplexFacilities.RAMP_W;
            for (int side = 0; side < 2; side++) {
                boolean right = side == 0 == rampRight;
                int u0 = right ? road[1] + 2 : road[0] - 1 - rd;
                if (u0 < 0 || u0 + rd - 1 >= len) {
                    continue;
                }
                boolean done = false;
                for (int t0 = 0; t0 + rw <= band && !done; t0++) {
                    int[] w = world(u0, t0, u0 + rd - 1, t0 + rw - 1);
                    if (fits(w[0], w[1], w[2], w[3], 0, 0)) {
                        long seed = r.nextLong();
                        done = place(label + " 지하주차장 입구", "parking", w, dir(right ? "-u" : "+u"),
                                (a, b) -> ComplexFacilities.ramp(a, b, new Random(seed)));
                    }
                }
                if (done) {
                    if (right) {
                        road[1] = u0 + rd - 1;
                    } else {
                        road[0] = u0;
                    }
                    break;
                }
            }
        }
        // 상가 (진입로 반대쪽), 관리동, 방문 주차장: 띠를 따라 빈자리에
        String[] order = {"shops", "office", "parking", "parking"};
        for (String what : order) {
            int sw = switch (what) {
                case "shops" -> 24 + 8 * r.nextInt(3);
                case "office" -> 22;
                default -> 24 + 6 * r.nextInt(3);
            };
            int sd = switch (what) {
                case "shops" -> 16;
                case "office" -> 14;
                default -> 15;
            };
            int minW = switch (what) {
                case "shops" -> 20;
                case "office" -> 18;
                default -> 18;
            };
            int[] best = null;
            double bestScore = Double.MAX_VALUE;
            for (int width = sw; width >= minW && best == null; width -= 4) {
                for (int u0 = 0; u0 + width <= len; u0++) {
                    for (int t0 = 0; t0 + sd <= band; t0 += 2) {
                        int[] w = world(u0, t0, u0 + width - 1, t0 + sd - 1);
                        if (!fits(w[0], w[1], w[2], w[3], 1, 1)) {
                            continue;
                        }
                        double score = Math.abs(u0 + width / 2.0 - (road[0] + road[1]) / 2.0) + t0 * 3;
                        if (score < bestScore) {
                            bestScore = score;
                            best = w;
                        }
                    }
                }
            }
            if (best == null) {
                continue;
            }
            long seed = r.nextLong();
            switch (what) {
                case "shops" -> place(label + " 상가", "shop", best, dir("gate"), (a, b) -> ComplexFacilities.shops(a, b, new Random(seed)));
                case "office" -> place(label + " 관리동", "apartment", best, dir("gate"),
                        (a, b) -> ComplexFacilities.management(a, b, label, new Random(seed)));
                default -> place(label + " 주차장", "parking", best, "south", (a, b) -> ParkingLot.build(a, b, new Random(seed)));
            }
        }
    }

    /** 진입로 정면(큰길 쪽) 너머 큰길까지 남은 칸 수 (1..3) */
    private int streetGap(int[] w, String side) {
        int cx = (w[0] + w[2]) / 2, cz = (w[1] + w[3]) / 2;
        int n = 0;
        for (int k = 1; k <= 3; k++) {
            int x = switch (side) {
                case "east" -> w[2] + k;
                case "west" -> w[0] - k;
                default -> cx;
            };
            int z = switch (side) {
                case "south" -> w[3] + k;
                case "north" -> w[1] - k;
                default -> cz;
            };
            if (m.roadDistance(x, z) == 0) {
                break;
            }
            n = k;
        }
        return Math.max(1, n);
    }

    /** 동 건물: 북쪽부터 남향으로 채움. 놓은 수 */
    private int buildings(String gateSide, int band) {
        // 정문 띠를 뺀 동 자리 (월드 상자)
        int x0 = bx0, z0 = bz0, x1 = bx1, z1 = bz1;
        switch (gateSide) {
            case "south" -> z1 -= band;
            case "north" -> z0 += band;
            case "east" -> x1 -= band;
            default -> x0 += band;
        }
        int gx = 6, gz = old ? 17 : 8;
        int placed = 0;
        int fails = 0;
        while (fails < 3 && placed < 40) {
            List<int[]> types = types();
            boolean any = false;
            for (int[] t : types) {
                int[] spot = firstFit(t[0], t[1], x0, z0, x1, z1, gx, gz);
                if (spot == null) {
                    continue;
                }
                if (placeBuilding(t, spot)) {
                    placed++;
                    any = true;
                    break;
                }
            }
            fails = any ? 0 : fails + 1;
        }
        return placed;
    }

    /**
     * 이번에 놓아 볼 동 종류들 (먼저 고른 것부터): {상자 너비, 상자 깊이, 종류(0 타워 1 계단식 2 복도식), 라인·세대 수}
     */
    private List<int[]> types() {
        List<int[]> t = new ArrayList<>();
        if (look.corridor()) {
            for (int units = 7; units >= 4; units--) {
                t.add(new int[]{SlabApartment.corridorWidth(units), SlabApartment.corridorDepth(), 2, units});
            }
            return t;
        }
        boolean towerFirst = r.nextInt(100) < look.towerPct();
        int[] tower = {TowerApartment.BOX_W, TowerApartment.BOX_D, 0, 0};
        List<int[]> slabs = new ArrayList<>();
        for (int lines = look.maxLines(); lines >= 1; lines--) {
            slabs.add(new int[]{SlabApartment.stairWidth(lines), SlabApartment.stairDepth(look.piloti()), 1, lines});
        }
        if (look.towerPct() >= 100) {
            t.add(tower);
            return t;
        }
        if (towerFirst) {
            t.add(tower);
            t.addAll(slabs);
        } else {
            // 판상형은 긴 것부터 (라인 수를 조금 섞음)
            if (slabs.size() > 1 && r.nextBoolean()) {
                slabs.add(slabs.remove(0));
            }
            t.addAll(slabs);
            if (look.towerPct() > 0) {
                t.add(tower);
            }
        }
        return t;
    }

    /** 북쪽부터, 서쪽부터 처음 들어가는 자리 {x0, z0} */
    private int[] firstFit(int sw, int sd, int x0, int z0, int x1, int z1, int gx, int gz) {
        for (int z = z0; z + sd - 1 <= z1; z++) {
            for (int x = x0; x + sw - 1 <= x1; x++) {
                if (fits(x, z, x + sw - 1, z + sd - 1, gx, gz)) {
                    return new int[]{x, z};
                }
            }
        }
        return null;
    }

    private boolean placeBuilding(int[] t, int[] spot) {
        int x0 = spot[0], z0 = spot[1], x1 = x0 + t[0] - 1, z1 = z0 + t[1] - 1;
        int floors = look.minF() + r.nextInt(look.maxF() - look.minF() + 1);
        int no = dong++;
        String pname = label + " " + no + "동";
        long seed = r.nextLong();
        AptUnit.Skin s = skin;
        String complexLabel = label;
        return switch (t[2]) {
            case 0 -> {
                int base = look.baseFloors();
                yield place(pname, "apartment", x0, z0, x1, z1, "south", GRASS, 1,
                        (w, d) -> TowerApartment.build(w, d, floors, s, base, no, complexLabel, new Random(seed)));
            }
            case 1 -> {
                int lines = t[3];
                boolean piloti = look.piloti();
                yield place(pname, "apartment", x0, z0, x1, z1, "south", GRASS, 1,
                        (w, d) -> SlabApartment.stair(w, d, lines, floors, s, piloti, no, complexLabel, new Random(seed)));
            }
            default -> {
                int units = t[3];
                yield place(pname, "apartment", x0, z0, x1, z1, "south", GRASS, 1,
                        (w, d) -> SlabApartment.corridor(w, d, units, floors, s, true, no, complexLabel, new Random(seed)));
            }
        };
    }

    /** 남은 땅: 커뮤니티센터·놀이터·(옛 단지) 주차장, 그리고 공원으로 남김없이 */
    private void leftovers() {
        boolean community = old;
        int playgrounds = 0, lots = 0;
        for (int iter = 0; iter < 120; iter++) {
            int[] rect = largestFree();
            if (rect == null) {
                break;
            }
            int w = rect[2] - rect[0] + 1, d = rect[3] - rect[1] + 1;
            if (Math.min(w, d) < 3 || w * d < 15) {
                break;
            }
            // 너무 큰 빈터는 나눠서
            int cw = Math.min(w, 44), cd = Math.min(d, 40);
            int x0 = rect[0] + (w - cw) / 2, z0 = rect[1] + (d - cd) / 2;
            long seed = r.nextLong();
            if (!community && cw >= 26 && cd >= 20) {
                int fw = Math.min(cw - 2, 34), fd = Math.min(cd - 2, 20);
                int fx = x0 + (cw - fw) / 2, fz = z0 + (cd - fd) / 2;
                Block stone = look.gateStone();
                if (place(label + " 커뮤니티센터", "apartment", new int[]{fx, fz, fx + fw - 1, fz + fd - 1}, "south",
                        (a, b) -> ComplexFacilities.community(a, b, label, stone, new Random(seed)))) {
                    community = true;
                    continue;
                }
            }
            if (playgrounds < 2 && cw >= 14 && cd >= 14) {
                int fw = Math.min(cw, 20), fd = Math.min(cd, 18);
                int fx = x0 + (cw - fw) / 2, fz = z0 + (cd - fd) / 2;
                if (place(label + " 놀이터", "park", new int[]{fx, fz, fx + fw - 1, fz + fd - 1}, "south",
                        (a, b) -> ComplexFacilities.playground(a, b, new Random(seed)))) {
                    playgrounds++;
                    continue;
                }
            }
            if (old && lots < 4 && cw >= 24 && cd >= 15) {
                int fd = 15;
                int fz = z0 + (cd - fd) / 2;
                if (place(label + " 주차장", "parking", new int[]{x0, fz, x0 + cw - 1, fz + fd - 1}, "south",
                        (a, b) -> ParkingLot.build(a, b, new Random(seed)))) {
                    lots++;
                    continue;
                }
            }
            boolean oldPark = old;
            if (!place(label + " 단지 공원", "park", new int[]{x0, z0, x0 + cw - 1, z0 + cd - 1}, "south",
                    (a, b) -> ComplexFacilities.park(a, b, oldPark, new Random(seed)))) {
                break;
            }
        }
    }

    /** 지금 지을 수 있는 칸으로만 된 가장 큰 직사각형 {x0, z0, x1, z1} (없으면 null) */
    private int[] largestFree() {
        int[] heights = new int[bw];
        int bestArea = 0;
        int[] best = null;
        int[] stack = new int[bw + 1];
        for (int j = 0; j < bd; j++) {
            for (int i = 0; i < bw; i++) {
                heights[i] = m.free(bx0 + i, bz0 + j) ? heights[i] + 1 : 0;
            }
            int sp = 0;
            for (int i = 0; i <= bw; i++) {
                int h = i == bw ? 0 : heights[i];
                while (sp > 0 && heights[stack[sp - 1]] >= h) {
                    int top = stack[--sp];
                    int height = heights[top];
                    int left = sp == 0 ? 0 : stack[sp - 1] + 1;
                    int width = i - left;
                    // 너무 가늘면 빼고 넓이로 고름
                    int area = Math.min(width, 44) * Math.min(height, 40);
                    if (height >= 3 && width >= 3 && area > bestArea) {
                        bestArea = area;
                        best = new int[]{bx0 + left, bz0 + j - height + 1, bx0 + i - 1, bz0 + j};
                    }
                }
                if (i < bw) {
                    stack[sp++] = i;
                }
            }
        }
        return best;
    }
}
