package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;

import java.util.ArrayDeque;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 광장·앞마당 같은 바깥 땅을 꾸밀 때 쓰는 땅 지도 (월드 직사각형, 정면 남쪽 기준이라 i = x - x0, j = z - z0).
 * 칸마다 포장해도 되는 땅인지(도로·물·다리·산이 아니고 구역 안), 도로·물까지 거리, 거점 바닥까지 거리를 미리 잽니다.
 * 나무·의자·가로등처럼 땅 위에 서는 것은 도로에서 3칸, 거점 바닥에서 4칸 넘게 떨어진 곳에만 둡니다.
 * 같이 쓰는 바깥 시설(나무, 의자, 보행등, 국기 게양대, 화단)도 여기 있습니다.
 */
final class Site {
    final int x0, z0, w, d;
    private final boolean[] land;
    private final short[] road;
    private final short[] pad;

    Site(CityTerrain t, Polygon area, int x0, int z0, int x1, int z1) {
        this.x0 = x0;
        this.z0 = z0;
        this.w = x1 - x0 + 1;
        this.d = z1 - z0 + 1;
        land = new boolean[w * d];
        road = new short[w * d];
        pad = new short[w * d];
        ArrayDeque<Integer> rq = new ArrayDeque<>(), pq = new ArrayDeque<>();
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                Column c = t.column(x0 + i, z0 + j);
                boolean blocked = c.isRoad() || c.isWater() || c.deck || c.tunnel;
                road[k] = blocked ? 0 : Short.MAX_VALUE;
                pad[k] = c.surface == Surface.PAD || c.hubPillar > 0 ? 0 : Short.MAX_VALUE;
                if (blocked) {
                    rq.add(k);
                }
                if (pad[k] == 0) {
                    pq.add(k);
                }
                land[k] = !blocked && c.mountainHeight == 0 && (area == null || area.contains(x0 + i + 0.5, z0 + j + 0.5));
            }
        }
        spread(road, rq);
        spread(pad, pq);
    }

    private void spread(short[] dist, ArrayDeque<Integer> q) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int k = q.poll();
            int i = k % w, j = k / w;
            if (dist[k] >= 12) {
                continue;
            }
            for (int[] dd : dirs) {
                int ni = i + dd[0], nj = j + dd[1];
                if (ni < 0 || nj < 0 || ni >= w || nj >= d) {
                    continue;
                }
                int nk = nj * w + ni;
                if (dist[nk] > dist[k] + 1) {
                    dist[nk] = (short) (dist[k] + 1);
                    q.add(nk);
                }
            }
        }
    }

    boolean in(int i, int j) {
        return i >= 0 && j >= 0 && i < w && j < d;
    }

    /** 포장해도 되는 땅 */
    boolean land(int i, int j) {
        return in(i, j) && land[j * w + i];
    }

    /** 도로·물까지 거리 (12 넘으면 큰 값) */
    int roadDistance(int i, int j) {
        return in(i, j) ? road[j * w + i] : 0;
    }

    /** 땅 위에 무엇을 세워도 되는 칸 (도로에서 3칸, 거점 바닥에서 4칸 넘게) */
    boolean solid(int i, int j) {
        return land(i, j) && road[j * w + i] > 3 && pad[j * w + i] > 4;
    }

    /** 반지름 r 둘레가 모두 세워도 되는 칸 */
    boolean solid(int i, int j, int r) {
        for (int b = -r; b <= r; b++) {
            for (int a = -r; a <= r; a++) {
                if (!solid(i + a, j + b)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 바깥 시설

    static final Block BENCH = Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430);
    static final Block CURB = Block.of("smooth_stone", 0x9E9E9E);

    /** 가로수 (나무 구덩이, 줄기, 둥근 잎): 은행나무는 노란 잎이 아니라 초록 여름 잎 */
    static void tree(Voxels v, int i, int j, boolean birch) {
        v.set(i, -1, j, COARSE_DIRT);
        Block log = birch ? Block.of("birch_log[axis=y]", 0xD8D3C5) : OAK_LOG;
        Block leaves = birch ? Block.of("birch_leaves[persistent=true]", 0x6B8F3F) : OAK_LEAVES;
        v.fill(i, 0, j, i, 3, j, log);
        v.ellipsoid(i + 0.5, 5.2, j + 0.5, 2.3, 2.0, 2.3, leaves);
        v.set(i, 4, j, log);
    }

    /** 돌 화단에 심은 나무 (3×3 화단 테두리) */
    static void planter(Voxels v, int i, int j) {
        for (int b = -1; b <= 1; b++) {
            for (int a = -1; a <= 1; a++) {
                if (a != 0 || b != 0) {
                    v.set(i + a, 0, j + b, Block.of("stone_brick_slab[type=bottom,waterlogged=false]", 0x7A7979));
                    v.set(i + a, -1, j + b, GRASS);
                }
            }
        }
        tree(v, i, j, false);
    }

    /** 나무 의자 (길이 len, i 방향으로 늘어섬 아니면 j 방향) */
    static void bench(Voxels v, int i, int j, int len, boolean alongI) {
        for (int k = 0; k < len; k++) {
            v.set(alongI ? i + k : i, 0, alongI ? j : j + k, BENCH);
        }
    }

    /** 보행등: 회색 기둥 위 등 */
    static void lamp(Voxels v, int i, int j) {
        v.fill(i, 0, j, i, 3, j, StreetPlan.POST);
        v.set(i, 4, j, Block.of("lantern[hanging=false,waterlogged=false]", 0x6A5B49));
    }

    /** 국기 게양대: 흰 가는 기둥과 꼭대기 장식 (깃발은 없음) */
    static void flagpole(Voxels v, int i, int j, int h) {
        v.set(i, 0, j, Block.of("polished_andesite", 0x848685));
        v.fill(i, 1, j, i, h, j, Block.of("end_rod[facing=up]", 0xE8E2D8));
        v.set(i, h + 1, j, Block.of("polished_blackstone_button[face=floor,facing=north,powered=false]", 0x353038));
    }

    /** 바닥 마감 (y 칸을 바꿈), keep 상자 {i0, j0, i1, j1} 안은 건드리지 않음 (계단실 등) */
    static void floor(Voxels v, int i0, int j0, int i1, int j1, int y, Block b, int[]... keep) {
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (!kept(keep, i, j)) {
                    v.set(i, y, j, b);
                }
            }
        }
    }

    /** 천장 등 (y 칸, spacing 간격), 빈 칸에만, keep 상자 안은 피함 */
    static void lights(Voxels v, int i0, int j0, int i1, int j1, int y, int spacing, int[]... keep) {
        int off = Math.max(1, spacing / 2);
        for (int j = j0 + off; j <= j1 - 1; j += spacing) {
            for (int i = i0 + off; i <= i1 - 1; i += spacing) {
                Block b = v.get(i, y, j);
                if ((b == null || b.isAir()) && !kept(keep, i, j)) {
                    v.set(i, y, j, Interior.LIGHT);
                }
            }
        }
    }

    private static boolean kept(int[][] keep, int i, int j) {
        for (int[] k : keep) {
            if (i >= k[0] && i <= k[2] && j >= k[1] && j <= k[3]) {
                return true;
            }
        }
        return false;
    }

    /** 주차 칸 (흰 선만, 차는 없음): (i, j) 부터 i 방향으로 n 칸, 깊이 5 (j 방향) */
    static void stalls(Voxels v, int i, int j, int n, int depth) {
        for (int k = 0; k <= n; k++) {
            v.fill(i + 3 * k, -1, j, i + 3 * k, -1, j + depth - 1, WHITE_CONCRETE);
        }
    }
}
