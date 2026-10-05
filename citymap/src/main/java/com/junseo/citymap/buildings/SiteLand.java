package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.util.ArrayDeque;

/**
 * 송파·마포 랜드마크용 땅 도구: 거점 둘레의 땅 덩어리(도로로 둘러싸인 블록)를 찾고,
 * 건물 상자 칸마다 땅인지(도로·인도·물·산이 아닌 그 구역 땅) 알려 줍니다.
 * 블록 가장자리가 비스듬해도 칸 단위로 맞춰 짓도록 땅 지도와 「도로까지 거리」를 줍니다.
 */
final class SiteLand {
    /** 건물 상자 크기 (정면 기준 너비 w, 깊이 d) */
    final int w, d;
    /** [i][j] 땅이면 true */
    final boolean[][] land;
    /** [i][j] 가장 가까운 땅 아닌 칸까지 맨해튼 거리 (땅이 아니면 0, 상자 밖은 땅 아님으로 봄) */
    final int[][] dist;
    final Ground ground;

    private SiteLand(Ground ground, int w, int d, boolean[][] land) {
        this.ground = ground;
        this.w = w;
        this.d = d;
        this.land = land;
        this.dist = new int[w][d];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                boolean edge = i == 0 || j == 0 || i == w - 1 || j == d - 1;
                if (!land[i][j]) {
                    dist[i][j] = 0;
                    q.add(new int[]{i, j});
                } else if (edge) {
                    dist[i][j] = 1;
                    q.add(new int[]{i, j});
                } else {
                    dist[i][j] = Integer.MAX_VALUE;
                }
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] dd : dirs) {
                int ni = p[0] + dd[0], nj = p[1] + dd[1];
                if (ni < 0 || nj < 0 || ni >= w || nj >= d) {
                    continue;
                }
                if (dist[ni][nj] > dist[p[0]][p[1]] + 1) {
                    dist[ni][nj] = dist[p[0]][p[1]] + 1;
                    q.add(new int[]{ni, nj});
                }
            }
        }
    }

    /** 건물 상자(정면 front, 크기 w×d)가 놓인 월드 직사각형의 땅 지도 */
    static SiteLand of(CityTerrain t, String district, int x0, int z0, int x1, int z1, String front, int w, int d) {
        Ground g = Ground.rect(t, x0, z0, x1, z1, front);
        boolean[][] land = new boolean[w][d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                land[i][j] = isLand(g.column(i, j), district);
            }
        }
        return new SiteLand(g, w, d, land);
    }

    static boolean isLand(Column c, String district) {
        return !c.isRoad() && !c.isWater() && !c.deck && !c.tunnel && c.mountainHeight == 0 && district.equals(c.district);
    }

    boolean land(int i, int j) {
        return i >= 0 && j >= 0 && i < w && j < d && land[i][j];
    }

    /** 땅 안쪽으로 r 칸 이상 들어온 칸인지 (r = 1 이면 땅) */
    boolean inner(int i, int j, int r) {
        return i >= 0 && j >= 0 && i < w && j < d && dist[i][j] >= r;
    }

    /**
     * 거점 (hx, hz) 이 있는 땅 덩어리의 바깥 상자 {minX, minZ, maxX, maxZ}.
     * radius 칸 넘게는 찾지 않습니다 (길을 없애서 블록이 합쳐져도 너무 커지지 않게).
     */
    static int[] blockAround(CityTerrain t, String district, int hx, int hz, int radius) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        q.add(new int[]{hx, hz});
        seen.add(key(hx, hz));
        int[] box = {hx, hz, hx, hz};
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            box[0] = Math.min(box[0], p[0]);
            box[1] = Math.min(box[1], p[1]);
            box[2] = Math.max(box[2], p[0]);
            box[3] = Math.max(box[3], p[1]);
            for (int[] dd : dirs) {
                int nx = p[0] + dd[0], nz = p[1] + dd[1];
                if (Math.abs(nx - hx) > radius || Math.abs(nz - hz) > radius || !seen.add(key(nx, nz))) {
                    continue;
                }
                if (isLand(t.column(nx, nz), district)) {
                    q.add(new int[]{nx, nz});
                }
            }
        }
        return box;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** 월드 칸 (x, z) → 건물 칸 {i, j} (상자 밖이면 null) */
    int[] local(int x, int z) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                int[] p = ground.world(i, j);
                if (p[0] == x && p[1] == z) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    /** 땅이 아닌 칸(도로·인도·물) 위의 낮은 블록(y ≤ 5)을 지움: 길을 막지 않게 */
    void clip(Voxels v) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                if (!land[i][j]) {
                    for (int y = v.y0; y <= CityBuildings.ROAD_CLEARANCE - 1; y++) {
                        v.set(i, y, j, null);
                    }
                }
            }
        }
    }

    /** 거점 (hx, hz) 이 직사각형의 어느 변에 가장 가까운지: 그 쪽이 정면 */
    static String sideNearest(int[] box, int hx, int hz) {
        int west = hx - box[0], east = box[2] - hx, north = hz - box[1], south = box[3] - hz;
        int best = Math.min(Math.min(west, east), Math.min(north, south));
        if (best == north) {
            return "north";
        }
        if (best == south) {
            return "south";
        }
        return best == west ? "west" : "east";
    }
}
