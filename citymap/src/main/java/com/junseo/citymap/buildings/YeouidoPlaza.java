package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 여의도 메인 광장: 서버의 모임 장소. 여의도공원 문화의 마당·한강공원 광장처럼 넓은 포장 광장과 그 북쪽 공원.
 * <ul>
 *   <li>광장(남쪽): 화강석 포장과 줄눈, 가운데 남북 축(거점 → 야외무대), 동서 가장자리 두 줄 가로수와 그 아래 의자,
 *       서쪽 원형 분수(물 바닥 조명, 가운데 3단 석조 분수대), 도로 쪽 국기 게양대 줄(깃발은 없음), 보행등</li>
 *   <li>야외무대(광장 북쪽 끝, 남쪽을 봄): 돌 무대와 양쪽 계단, 뒷벽, 앞으로 내민 흰 지붕과 조명 트러스, 스피커,
 *       무대 뒤 대기실</li>
 *   <li>공원(북쪽): 잔디, 붉은 보도블록 산책로, 나무숲(참나무·자작나무·소나무), 연못과 정자, 공중화장실</li>
 * </ul>
 * 상자는 월드에 정면 남쪽으로 놓으므로 i = x - x0, j = z - z0 입니다.
 */
final class YeouidoPlaza {
    private static final Block PAVE = POLISHED_ANDESITE;
    private static final Block JOINT = Block.of("light_gray_concrete", 0x7D7D73);
    private static final Block BORDER = SMOOTH_STONE;
    private static final Block PATH = BRICKS;

    private final Voxels v;
    private final Site s;
    private final Random r;
    /** 거점 (상자 좌표) */
    private final int hi, hj;
    /** 광장 북쪽 끝 줄 (그 북쪽은 공원) */
    private final int squareTop;

    private YeouidoPlaza(Site s, int hi, int hj, Random r) {
        this.s = s;
        this.r = r;
        this.hi = hi;
        this.hj = hj;
        v = new Voxels(s.w, s.d, -2, 16);
        squareTop = Math.max(10, hj - 66);
    }

    static Voxels build(Site s, int hubI, int hubJ, Random r) {
        YeouidoPlaza p = new YeouidoPlaza(s, hubI, hubJ, r);
        p.ground();
        p.square();
        p.stage();
        p.fountain(hubI - 53, hubJ - 22);
        p.park();
        p.v.connect();
        return p.v;
    }

    // ------------------------------------------------------------------ 바닥

    private void ground() {
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (!s.land(i, j)) {
                    continue;
                }
                if (j >= squareTop) {
                    boolean axis = Math.abs(i - hi) <= 4;
                    boolean joint = Math.floorMod(i - hi, 8) == 0 || Math.floorMod(j - hj, 8) == 0;
                    v.set(i, -1, j, axis ? (Math.floorMod(j, 4) == 0 ? JOINT : SMOOTH_STONE) : joint ? JOINT : PAVE);
                    if (j == squareTop) {
                        v.set(i, -1, j, BORDER);
                    }
                } else {
                    v.set(i, -1, j, GRASS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 광장

    private void square() {
        // 동서 가장자리 두 줄 가로수와 의자 (광장 안쪽 끝을 찾아서)
        for (int j = squareTop + 4; j < s.d; j += 7) {
            int west = -1, east = -1;
            for (int i = 0; i < s.w; i++) {
                if (s.solid(i, j)) {
                    if (west < 0) {
                        west = i;
                    }
                    east = i;
                }
            }
            if (west < 0) {
                continue;
            }
            for (int[] row : new int[][]{{west + 2, 1}, {west + 7, 1}, {east - 2, -1}, {east - 7, -1}}) {
                int i = row[0];
                if (s.solid(i, j, 2) && Math.abs(i - hi) > 12) {
                    Site.tree(v, i, j, (j / 7) % 3 == 0);
                    if (s.solid(i + 2 * row[1], j + 3, 1)) {
                        Site.bench(v, i + 2 * row[1], j + 2, 3, false);
                    }
                }
            }
        }
        // 도로 쪽 국기 게양대 줄 (거점 양옆)
        int fj = hj + 3;
        for (int k = 1; k <= 4; k++) {
            for (int sign : new int[]{-1, 1}) {
                int i = hi + sign * (5 + 6 * k);
                if (s.solid(i, fj)) {
                    Site.flagpole(v, i, fj, 11);
                }
            }
        }
        // 남북 축 양옆 보행등
        for (int j = squareTop + 14; j < hj - 4; j += 10) {
            for (int sign : new int[]{-1, 1}) {
                if (s.solid(hi + sign * 6, j)) {
                    Site.lamp(v, hi + sign * 6, j);
                }
            }
        }
        // 광장 동쪽: 나무 그늘 쉼터 (격자로 심은 나무와 의자)
        for (int j = hj - 40; j <= hj - 12; j += 8) {
            for (int i = hi + 22; i <= hi + 46; i += 8) {
                if (s.solid(i, j, 2)) {
                    Site.planter(v, i, j);
                    if (s.solid(i, j + 3)) {
                        Site.bench(v, i - 1, j + 3, 3, true);
                    }
                }
            }
        }
        // 광장 이름 표석 (거점 옆, 도로 쪽)
        int mi = hi + 9, mj = hj - 1;
        if (s.solid(mi, mj, 1) && s.solid(mi + 2, mj, 1)) {
            v.fill(mi, 0, mj, mi + 2, 1, mj, POLISHED_GRANITE);
            v.set(mi + 1, 1, mj + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", "여의도 광장", "YEOUIDO PLAZA"));
        }
    }

    /** 원형 분수: 물 바닥 조명, 돌 테두리, 가운데 3단 분수대 */
    private void fountain(int ci, int cj) {
        double R = 8;
        if (!s.solid(ci, cj, (int) R + 1)) {
            return;
        }
        for (int j = cj - 10; j <= cj + 10; j++) {
            for (int i = ci - 10; i <= ci + 10; i++) {
                double dd = Math.hypot(i - ci, j - cj);
                if (dd <= R - 0.5) {
                    v.set(i, -2, j, (Math.floorMod(i + j, 5) == 0 && dd > 2) ? SEA_LANTERN : Block.of("stone", 0x7E7E7E));
                    v.set(i, -1, j, WATER);
                } else if (dd <= R + 0.5) {
                    v.set(i, -1, j, POLISHED_ANDESITE);
                    v.set(i, 0, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
                } else if (dd <= R + 2.5) {
                    v.set(i, -1, j, SMOOTH_STONE);
                }
            }
        }
        // 가운데 분수대: 넓은 받침 → 기둥 → 위 물그릇 (물은 둘레 돌에 갇힘)
        v.fill(ci - 1, -1, cj - 1, ci + 1, 0, cj + 1, STONE_BRICKS);
        v.fill(ci, 1, cj, ci, 2, cj, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
        v.fill(ci - 1, 3, cj - 1, ci + 1, 3, cj + 1, STONE_BRICKS);
        v.set(ci, 3, cj, WATER);
        v.set(ci, 4, cj, Block.of("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x7A7979));
        // 둘레 의자
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45 + 22.5);
            int bi = (int) Math.round(ci + (R + 4) * Math.cos(a)), bj = (int) Math.round(cj + (R + 4) * Math.sin(a));
            if (s.solid(bi, bj)) {
                v.set(bi, 0, bj, Site.BENCH);
            }
        }
    }

    // ------------------------------------------------------------------ 야외무대

    private void stage() {
        int w = 31, dep = 11;
        int a0 = hi - w / 2, a1 = a0 + w - 1;
        int b1 = squareTop + 1 + dep;   // 무대 앞 끝
        int b0 = b1 - dep + 1;          // 뒷벽 줄
        if (!s.solid(a0 - 3, b0 - 6, 1) || !s.solid(a1 + 3, b1 + 2, 1) || !s.solid(a0 - 3, b1 + 2, 1) || !s.solid(a1 + 3, b0 - 6, 1)) {
            return;
        }
        Block deck = Block.of("stone_bricks", 0x7A7979);
        // 무대 (1칸 높이)와 나무 마루
        v.fill(a0, 0, b0, a1, 0, b1, deck);
        v.fill(a0 + 1, 0, b0 + 1, a1 - 1, 0, b1 - 1, Block.of("spruce_planks", 0x725430));
        // 양쪽 앞 계단
        for (int a : new int[]{a0 - 1, a1 + 1}) {
            for (int b = b1 - 3; b <= b1; b++) {
                v.set(a, 0, b, Blocks.stairs("stone_brick", a < hi ? "east" : "west", 0x7A7979));
            }
        }
        // 뒷벽 (흰 판)과 대기실 문
        v.fill(a0, 1, b0, a1, 9, b0, Block.of("white_concrete", 0xCFD5D6));
        v.fill(a0, 1, b0 - 1, a1, 9, b0 - 1, Block.of("light_gray_concrete", 0x7D7D73));
        // 앞으로 내민 지붕 (뒤가 높고 앞으로 낮아지는 판)
        for (int b = b0; b <= b1 + 1; b++) {
            int y = 10 - (b - b0) / 4;
            for (int a = a0 - 1; a <= a1 + 1; a++) {
                v.set(a, y, b, Block.of("white_concrete", 0xCFD5D6));
            }
        }
        // 지붕 기둥 (앞 양끝)
        for (int a : new int[]{a0, a1}) {
            int y = 10 - (b1 + 1 - b0) / 4;
            v.fill(a, 1, b1, a, y - 1, b1, Block.of("light_gray_concrete", 0x7D7D73));
        }
        // 조명 트러스와 등
        int ty = 10 - (b1 - 1 - b0) / 4 - 1;
        for (int a = a0 + 1; a <= a1 - 1; a++) {
            v.set(a, ty, b1 - 1, IRON_BARS);
            if ((a - a0) % 4 == 2) {
                v.set(a, ty - 1, b1 - 1, LANTERN_HANGING);
            }
        }
        for (int a = a0 + 2; a <= a1 - 2; a += 6) {
            v.set(a, 9, b0 + 3, Block.of("ochre_froglight[axis=y]", 0xF5E9B6));
        }
        // 스피커 (무대 앞 양옆)
        for (int a : new int[]{a0 - 3, a1 + 2}) {
            v.fill(a, 0, b1 - 1, a + 1, 3, b1, Block.of("black_concrete", 0x080A0F));
        }
        // 무대 뒤 대기실 (뒷벽 뒤, 문 둘)
        int rb0 = b0 - 6;
        v.fill(a0 + 4, -1, rb0, a1 - 4, -1, b0 - 1, POLISHED_ANDESITE);
        v.walls(a0 + 4, 0, rb0, a1 - 4, 3, b0 - 1, Block.of("light_gray_concrete", 0x7D7D73));
        v.fill(a0 + 4, 4, rb0, a1 - 4, 4, b0 - 1, SMOOTH_STONE);
        v.fill(a0 + 5, 0, rb0 + 1, a1 - 5, 3, b0 - 2, AIR);
        Frame f = Frame.of(v);
        Interior.door(f, a0 + 7, 0, rb0, "dark_oak", "north");
        Interior.door(f, a1 - 7, 0, rb0, "dark_oak", "north");
        v.set(a0 + 8, 1, rb0 - 1, Blocks.wallSign("birch", "north", "black", false, "", "무대 대기실"));
        for (int a = a0 + 6; a <= a1 - 6; a += 4) {
            v.set(a, 0, b0 - 2, Furniture.WHITE_TOP);
            Furniture.chair(f, a, 0, b0 - 3, "north", "birch");
            v.set(a, 3, rb0 + 2, Interior.LIGHT);
        }
        // 무대 위로 오르는 뒷계단 (대기실 → 무대): 뒷벽에 문
        v.set(hi, 1, b0, AIR);
        v.set(hi, 2, b0, AIR);
        v.set(hi, 1, b0 - 1, AIR);
        v.set(hi, 2, b0 - 1, AIR);
        v.set(hi, 0, b0 - 1, Blocks.stairs("spruce", "south", 0x725430));
    }

    // ------------------------------------------------------------------ 공원

    private void park() {
        // 산책로: 광장 북쪽 끝에서 이어지는 고리 길 (타원)
        double cx = hi, cz = squareTop / 2.0, rx = Math.max(20, s.w / 2.0 - 18), rz = Math.max(8, squareTop / 2.0 - 6);
        for (int j = 0; j < squareTop; j++) {
            for (int i = 0; i < s.w; i++) {
                if (!s.land(i, j)) {
                    continue;
                }
                double e = Math.hypot((i - cx) / rx, (j - cz) / rz);
                boolean loop = Math.abs(e - 1) * Math.min(rx, rz) < 1.6;
                boolean spine = Math.abs(i - hi) <= 2 && j > cz;
                if (loop || spine) {
                    v.set(i, -1, j, PATH);
                }
            }
        }
        // 연못과 정자: 공원 땅 안에서 자리를 찾음 (고리 길 안쪽 가운데부터)
        double prx = Math.min(10, rx * 0.25), prz = Math.min(6, rz * 0.45);
        int need = (int) Math.ceil(Math.max(prx, prz)) + 2;
        int pi = -1, pj = -1;
        for (int rr = 0; rr < 60 && pi < 0; rr += 2) {
            for (int dj = -rr; dj <= rr && pi < 0; dj += 2) {
                for (int di = -rr; di <= rr; di += 2) {
                    int ti = (int) cx - 12 + di, tj = (int) cz + dj;
                    if (Math.max(Math.abs(di), Math.abs(dj)) == rr && tj + need < squareTop - 2
                            && s.solid(ti, tj, need) && s.solid(ti + (int) prx + 4, tj, 4)) {
                        pi = ti;
                        pj = tj;
                        break;
                    }
                }
            }
        }
        boolean pond = pi >= 0;
        if (pond) {
            for (int j = pj - 10; j <= pj + 10; j++) {
                for (int i = pi - 14; i <= pi + 14; i++) {
                    double e = Math.hypot((i - pi) / prx, (j - pj) / prz);
                    if (e < 1) {
                        v.set(i, -2, j, Block.of("mud", 0x3C3A3D));
                        v.set(i, -1, j, WATER);
                    } else if (e < 1 + 1.2 / prz) {
                        v.set(i, -1, j, Block.of("cobblestone", 0x7F7F7F));
                    }
                }
            }
            pavilion(pi + (int) prx + 4, pj);
        }
        // 공중화장실 (공원 동쪽 길가)
        restroom((int) (cx + rx * 0.6), (int) (cz - rz * 0.2));
        // 나무숲: 길·연못·정자·화장실을 피해서
        for (int j = 2; j < squareTop - 2; j += 4) {
            for (int i = 2; i < s.w - 2; i += 4) {
                int ti = i + r.nextInt(3) - 1, tj = j + r.nextInt(3) - 1;
                if (!s.solid(ti, tj, 2) || r.nextInt(10) < 2 || !clear(ti, tj, 3)) {
                    continue;
                }
                if (nearPath(ti, tj, 2) || (pond && Math.abs(ti - pi - 3) < prx + 9 && Math.abs(tj - pj) < prz + 4)) {
                    continue;
                }
                int kind = r.nextInt(10);
                if (kind < 2) {
                    pine(ti, tj);
                } else {
                    Site.tree(v, ti, tj, kind < 5);
                    v.set(ti, -1, tj, GRASS);
                }
            }
        }
        // 길가 의자와 보행등
        for (int k = 0; k < 24; k++) {
            double a = 2 * Math.PI * k / 24;
            int bi = (int) Math.round(cx + (rx + 2.5) * Math.cos(a)), bj = (int) Math.round(cz + (rz + 2.5) * Math.sin(a));
            if (s.solid(bi, bj) && v.get(bi, 0, bj) == null) {
                if (k % 3 == 0) {
                    Site.lamp(v, bi, bj);
                } else if (k % 3 == 1) {
                    v.set(bi, 0, bj, Site.BENCH);
                }
            }
        }
    }

    /** 둘레 rr 칸 땅 위(0~6)가 비어 있는지 */
    private boolean clear(int i, int j, int rr) {
        for (int b = -rr; b <= rr; b++) {
            for (int a = -rr; a <= rr; a++) {
                for (int y = 0; y <= 6; y++) {
                    if (v.get(i + a, y, j + b) != null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean nearPath(int i, int j, int rr) {
        for (int b = -rr; b <= rr; b++) {
            for (int a = -rr; a <= rr; a++) {
                Block x = v.get(i + a, -1, j + b);
                if (x != null && (x == PATH || x.id().contains("cobblestone") || x == WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 소나무: 가는 붉은 줄기, 위에 넓고 납작한 잎 */
    private void pine(int i, int j) {
        v.set(i, -1, j, GRASS);
        v.fill(i, 0, j, i, 4, j, Block.of("spruce_log[axis=y]", 0x3A2A18));
        v.ellipsoid(i + 0.5, 6.0, j + 0.5, 2.8, 1.2, 2.8, Block.of("spruce_leaves[persistent=true]", 0x3E5C3A));
        v.set(i, 5, j, Block.of("spruce_log[axis=y]", 0x3A2A18));
    }

    /** 정자: 돌 기단, 나무 기둥 넷, 마루, 처마가 들린 기와 지붕 */
    private void pavilion(int ci, int cj) {
        if (!s.solid(ci, cj, 4)) {
            return;
        }
        Block post = Block.of("stripped_spruce_log[axis=y]", 0x735A3A);
        v.fill(ci - 3, -1, cj - 3, ci + 3, -1, cj + 3, Block.of("stone_bricks", 0x7A7979));
        v.fill(ci - 2, 0, cj - 2, ci + 2, 0, cj + 2, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        for (int a : new int[]{-2, 2}) {
            for (int b : new int[]{-2, 2}) {
                v.fill(ci + a, 0, cj + b, ci + a, 3, cj + b, post);
            }
        }
        // 지붕: 처마 (한 칸 밖) → 위로 좁아짐, 기와는 짙은 회색
        Block tile = Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637);
        Block tileFull = Block.of("deepslate_tiles", 0x363637);
        v.fill(ci - 2, 4, cj - 2, ci + 2, 4, cj + 2, Block.of("spruce_planks", 0x725430));
        for (int b = -4; b <= 4; b++) {
            for (int a = -4; a <= 4; a++) {
                int ring = Math.max(Math.abs(a), Math.abs(b));
                if (ring == 4) {
                    v.set(ci + a, (Math.abs(a) == 4 && Math.abs(b) == 4) ? 5 : 4, cj + b, tile);
                } else if (ring == 3) {
                    v.set(ci + a, 5, cj + b, tile);
                } else if (ring == 2) {
                    v.set(ci + a, 5, cj + b, tileFull);
                } else if (ring == 1) {
                    v.set(ci + a, 6, cj + b, tile);
                } else {
                    v.set(ci + a, 6, cj + b, tileFull);
                    v.set(ci + a, 7, cj + b, tile);
                }
            }
        }
        // 처마 끝 (네 귀퉁이) 들림
        for (int a : new int[]{-4, 4}) {
            for (int b : new int[]{-4, 4}) {
                v.set(ci + a, 5, cj + b, Block.of("deepslate_tile_slab[type=top,waterlogged=false]", 0x363637));
            }
        }
        v.set(ci, 3, cj, LANTERN_HANGING);
    }

    /** 공원 공중화장실: 벽돌 벽, 평지붕, 남녀 입구 */
    private void restroom(int ci, int cj) {
        int a0 = ci - 5, a1 = ci + 5, b0 = cj - 3, b1 = cj + 3;
        if (!s.solid(ci, cj, 7)) {
            return;
        }
        v.fill(a0, -1, b0, a1, -1, b1, POLISHED_ANDESITE);
        v.walls(a0, 0, b0, a1, 3, b1, BRICKS);
        v.fill(a0 - 1, 4, b0 - 1, a1 + 1, 4, b1 + 1, SMOOTH_STONE);
        v.fill(a0 + 1, 0, b0 + 1, a1 - 1, 3, b1 - 1, AIR);
        v.fill(ci, 0, b0 + 1, ci, 3, b1 - 1, BRICKS);
        Frame f = Frame.of(v);
        Interior.restroom(f, a0 + 1, b0 + 1, ci - 1, b1 - 1, 0, 4, "남자 화장실", ci - 3);
        Interior.restroom(f, ci + 1, b0 + 1, a1 - 1, b1 - 1, 0, 4, "여자 화장실", ci + 3);
    }
}
