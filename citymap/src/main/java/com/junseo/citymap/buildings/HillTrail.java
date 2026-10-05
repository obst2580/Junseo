package com.junseo.citymap.buildings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 등산로: 등산로 입구 광장 뒤 문에서 산비탈을 지그재그로 오르는 2칸 너비 흙길.
 * 비탈을 짧게 곧장 오르는 토막(나무 계단, 한 칸에 한 칸)과 등고선을 따라 가는 긴 토막(땅을 따라 한 칸에 한 칸까지)을
 * 번갈아 두어서 길이 땅에 붙어 갑니다. 땅보다 낮으면 깎고 높으면 채우며, 길 옆 드러난 면은 바윗돌. 끝에는 쉼터 (긴 의자, 이정표).
 * 좌표는 마을 좌표 (j 가 작아질수록 산 위, 상자 밖으로도 이어짐). 마을 상자 안 칸은 마을 바닥이 그리고,
 * 이 Placement 는 상자 밖 칸만 그립니다.
 */
final class HillTrail {
    private static final Block[] ROCK = {STONE, ANDESITE, COBBLESTONE, MOSSY_COBBLESTONE, STONE};

    /** 칸 → {서는 높이, 바닥 (0 흙, 1 북쪽 계단, 2 동쪽 계단, 3 서쪽 계단, 4 쉼터 돌)} */
    final Map<Long, int[]> cells = new HashMap<>();
    int ia = Integer.MAX_VALUE, ib = Integer.MIN_VALUE, ja = Integer.MAX_VALUE, jb = Integer.MIN_VALUE;
    int restI, restJ, restStand;
    private final HillVillage vl;

    /** (i, j) 와 (i+1, j) 에서 서는 높이 stand 로 출발해 산 위로 */
    HillTrail(HillVillage vl, int i, int j, int stand) {
        this.vl = vl;
        int s = stand;
        int dir = vl.rnd().nextBoolean() ? 1 : -1;
        for (int leg = 0; leg < 14 && s < 120; leg++) {
            // 짧게 곧장 오르는 토막 (북쪽, 나무 계단)
            for (int n = 0; n < 5; n++) {
                int nat = Math.max(vl.nat(i, j), vl.nat(i + 1, j)) + 1;
                int ns = Math.max(s, Math.min(s + 1, nat));
                if (cells.containsKey(key(i, j)) || cells.containsKey(key(i + 1, j))) {
                    ns = s; // 앞 토막과 겹친 칸: 높이를 그대로 이어감
                }
                put(i, j, ns, ns > s ? 1 : 0);
                put(i + 1, j, ns, ns > s ? 1 : 0);
                s = ns;
                j--;
            }
            // 등고선 토막 (동·서 번갈아): 땅을 따라 한 칸에 한 칸까지
            for (int n = 0; n < 11; n++) {
                int ni = i + dir;
                int nat = Math.max(vl.nat(ni, j + 1), vl.nat(ni + 1, j + 1)) + 1;
                int ns = Math.max(s - 1, Math.min(s + 1, nat));
                int kind = ns > s ? (dir > 0 ? 2 : 3) : 0;
                put(dir > 0 ? ni + 1 : ni, j + 1, ns, kind);
                put(dir > 0 ? ni + 1 : ni, j, ns, kind);
                if (n == 0) {
                    put(dir > 0 ? ni : ni + 1, j, ns, 0);
                }
                s = ns;
                i = ni;
            }
            dir = -dir;
        }
        // 쉼터 (5×4, 길 끝 위쪽)
        restI = i - 1;
        restJ = j - 3;
        restStand = s;
        for (int b = restJ; b <= j; b++) {
            for (int a = restI; a <= restI + 4; a++) {
                if (!cells.containsKey(key(a, b))) {
                    put(a, b, s, 4);
                }
            }
        }
    }

    static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xffffffffL);
    }

    private void put(int i, int j, int stand, int kind) {
        long k = key(i, j);
        if (cells.containsKey(k)) {
            int[] old = cells.get(k);
            if (old[0] != stand) {
                // 겹친 칸은 낮은 쪽 높이로 (계단 한 칸 차이만 남게)
                old[0] = Math.min(old[0], stand);
                old[1] = 0;
            }
            return;
        }
        cells.put(k, new int[]{stand, kind});
        ia = Math.min(ia, i);
        ib = Math.max(ib, i);
        ja = Math.min(ja, j);
        jb = Math.max(jb, j);
    }

    static Block floor(int kind) {
        return switch (kind) {
            case 1 -> Blocks.stairs("spruce", "north", 0x725430);
            case 2 -> Blocks.stairs("spruce", "east", 0x725430);
            case 3 -> Blocks.stairs("spruce", "west", 0x725430);
            case 4 -> Block.of("stone_bricks", 0x7A7979);
            default -> HillVillage.TRAIL;
        };
    }

    /** 고른 뒤 윗면 y (마을 상자 안은 마을이 정한 높이) */
    private int fin(int i, int j) {
        if (vl.inside(i, j)) {
            return vl.fin(i, j);
        }
        int[] c = cells.get(key(i, j));
        return c != null ? c[0] - 1 : vl.nat(i, j);
    }

    /** 상자 밖으로 나간 칸이 있으면 그 부분을 그리는 Placement */
    List<Placement> placement() {
        int i0 = ia - 2, i1 = ib + 2, j0 = ja - 2, j1 = jb + 1;
        boolean any = false;
        for (Map.Entry<Long, int[]> e : cells.entrySet()) {
            long k = e.getKey();
            int i = (int) (k >> 32), j = (int) k;
            any |= !vl.inside(i, j);
        }
        if (!any) {
            return List.of();
        }
        int[] wr = vl.worldRect(i0, j0, i1, j1);
        return List.of(Placement.rect(vl.name + " 등산로", "park", wr[0], wr[1], wr[2], wr[3], vl.site.front(),
                (w, d) -> voxels(i0, j0, w, d)));
    }

    private Voxels voxels(int i0, int j0, int w, int d) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int b = 0; b < d; b++) {
            for (int a = 0; a < w; a++) {
                int i = i0 + a, j = j0 + b;
                lo = Math.min(lo, Math.min(fin(i, j), vl.nat(i, j)) - 1);
                hi = Math.max(hi, Math.max(fin(i, j), vl.nat(i, j)) + 4);
            }
        }
        Voxels v = new Voxels(w, d, lo, hi);
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int b = 0; b < d; b++) {
            for (int a = 0; a < w; a++) {
                int i = i0 + a, j = j0 + b;
                if (vl.inside(i, j) || vl.offAt(i, j)) {
                    continue; // 마을 상자 안은 마을이 그림, 길·물은 그대로
                }
                int[] c = cells.get(key(i, j));
                int T = vl.nat(i, j);
                int low = Integer.MAX_VALUE;
                boolean nearTrail = false;
                for (int[] n : nb) {
                    low = Math.min(low, fin(i + n[0], j + n[1]));
                    nearTrail |= cells.containsKey(key(i + n[0], j + n[1]));
                }
                if (c == null) {
                    if (nearTrail) {
                        for (int y = low + 1; y < T; y++) {
                            v.set(a, y, b, rock(i, y, j));
                        }
                    }
                    continue;
                }
                int G = c[0] - 1;
                for (int y = G + 1; y <= T; y++) {
                    v.set(a, y, b, AIR);
                }
                for (int y = Math.min(T + 1, low + 1); y < G; y++) {
                    v.set(a, y, b, rock(i, y, j));
                }
                v.set(a, G, b, floor(c[1]));
            }
        }
        // 쉼터: 긴 의자, 이정표 (상자 밖일 때만)
        if (!vl.inside(restI, restJ)) {
            int ra = restI - i0, rb = restJ - j0, y = restStand;
            for (int k = 0; k < 3; k++) {
                v.set(ra + 1 + k, y, rb, Blocks.stairs("spruce", "north", 0x725430));
            }
            v.set(ra + 4, y, rb + 1, Block.of("spruce_log[axis=y]", 0x3A2A1A));
            v.set(ra + 4, y + 1, rb + 1, Block.of("spruce_log[axis=y]", 0x3A2A1A));
            v.set(ra + 4, y + 1, rb + 2, Blocks.wallSign("spruce", "south", "white", false, "백운대 2.8km", "대동문 2.1km", "등산로 입구 0.6km"));
        }
        v.connect();
        return v;
    }

    private static Block rock(int i, int y, int j) {
        long h = (i * 73856093L) ^ (y * 19349663L) ^ (j * 83492791L);
        return ROCK[(int) Math.floorMod(h, (long) ROCK.length)];
    }
}
