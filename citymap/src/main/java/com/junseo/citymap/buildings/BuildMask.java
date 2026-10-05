package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 구역 안에서 건물을 지을 수 있는 땅 지도.
 * 평평한 잔디 땅이면서 도로·물에서 setback 칸 이상 떨어져 있고, 거점 표시 바닥 근처가 아닌 곳만 씁니다.
 * 이미 건물이 들어간 자리는 {@link #claim} 으로 표시합니다.
 */
final class BuildMask {
    /** 이 거리(칸)까지 도로에서 떨어진 거리를 잽니다 */
    private static final int FAR = 250;

    final int x0, z0, w, h;
    /** 지을 수 있으면 true */
    private final boolean[] ok;
    /** 가장 가까운 도로·물 칸까지 거리 (맨해튼, FAR 까지) */
    private final short[] road;

    private BuildMask(int x0, int z0, int w, int h) {
        this.x0 = x0;
        this.z0 = z0;
        this.w = w;
        this.h = h;
        this.ok = new boolean[w * h];
        this.road = new short[w * h];
    }

    /** area 안의 땅을 훑어서 만듭니다 */
    static BuildMask of(CityTerrain terrain, Polygon area, int setback) {
        double[] b = area.bounds();
        int x0 = (int) Math.floor(b[0]) - 2, z0 = (int) Math.floor(b[1]) - 2;
        int w = (int) Math.ceil(b[2]) - x0 + 3, h = (int) Math.ceil(b[3]) - z0 + 3;
        BuildMask m = new BuildMask(x0, z0, w, h);
        boolean[] land = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int x = x0 + i, z = z0 + j, k = j * w + i;
                Column c = terrain.column(x, z);
                boolean blocked = c.isRoad() || c.isWater() || c.deck || c.tunnel;
                m.road[k] = blocked ? 0 : (short) FAR;
                if (blocked) {
                    queue.add(k);
                }
                land[k] = !blocked && c.mountainHeight == 0 && c.hubPillar == 0
                        && (c.surface == Surface.GRASS || c.surface == Surface.PAD)
                        && area.contains(x + 0.5, z + 0.5);
            }
        }
        // 도로에서 떨어진 거리 (너비 우선 탐색)
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int k = queue.poll();
            int i = k % w, j = k / w;
            for (int[] dd : dirs) {
                int ni = i + dd[0], nj = j + dd[1];
                if (ni < 0 || nj < 0 || ni >= w || nj >= h) {
                    continue;
                }
                int nk = nj * w + ni;
                if (m.road[nk] > m.road[k] + 1) {
                    m.road[nk] = (short) (m.road[k] + 1);
                    queue.add(nk);
                }
            }
        }
        for (int k = 0; k < w * h; k++) {
            m.ok[k] = land[k] && m.road[k] > setback;
        }
        // 거점 바닥(PAD) 둘레는 비워 둠
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                Column c = terrain.column(x0 + i, z0 + j);
                if (c.surface == Surface.PAD) {
                    m.claim(x0 + i - 4, z0 + j - 4, x0 + i + 4, z0 + j + 4);
                }
            }
        }
        return m;
    }

    boolean free(int x, int z) {
        int i = x - x0, j = z - z0;
        return i >= 0 && j >= 0 && i < w && j < h && ok[j * w + i];
    }

    /** 도로까지 거리 (지도 밖이면 0) */
    int roadDistance(int x, int z) {
        int i = x - x0, j = z - z0;
        return i >= 0 && j >= 0 && i < w && j < h ? road[j * w + i] : 0;
    }

    /** 직사각형이 모두 지을 수 있는 땅인지 */
    boolean rectFree(int xa, int za, int xb, int zb) {
        for (int z = za; z <= zb; z++) {
            for (int x = xa; x <= xb; x++) {
                if (!free(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 직사각형 안에서 지을 수 있는 칸 수 */
    int count(int xa, int za, int xb, int zb) {
        int n = 0;
        for (int z = za; z <= zb; z++) {
            for (int x = xa; x <= xb; x++) {
                if (free(x, z)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 건물이 들어간 자리로 표시 */
    void claim(int xa, int za, int xb, int zb) {
        for (int z = za; z <= zb; z++) {
            for (int x = xa; x <= xb; x++) {
                int i = x - x0, j = z - z0;
                if (i >= 0 && j >= 0 && i < w && j < h) {
                    ok[j * w + i] = false;
                }
            }
        }
    }

    /** 지을 수 있는 땅 덩어리(도로로 둘러싸인 블록)마다 {minX, minZ, maxX, maxZ, 칸 수} */
    List<int[]> blocks() {
        boolean[] seen = new boolean[w * h];
        List<int[]> out = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < w * h; start++) {
            if (!ok[start] || seen[start]) {
                continue;
            }
            int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 0};
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int k = queue.poll();
                int i = k % w, j = k / w;
                box[0] = Math.min(box[0], x0 + i);
                box[1] = Math.min(box[1], z0 + j);
                box[2] = Math.max(box[2], x0 + i);
                box[3] = Math.max(box[3], z0 + j);
                box[4]++;
                int[] nbs = {i > 0 ? k - 1 : -1, i + 1 < w ? k + 1 : -1, j > 0 ? k - w : -1, j + 1 < h ? k + w : -1};
                for (int nk : nbs) {
                    if (nk >= 0 && ok[nk] && !seen[nk]) {
                        seen[nk] = true;
                        queue.add(nk);
                    }
                }
            }
            out.add(box);
        }
        return out;
    }

    /**
     * (x, z) 에서 가장 가까운 지을 수 있는 칸이 속한 땅 덩어리 {minX, minZ, maxX, maxZ, 칸 수}.
     * radius 안에 지을 수 있는 칸이 없으면 null.
     */
    int[] componentNear(int x, int z, int radius) {
        int sx = Integer.MIN_VALUE, sz = 0;
        for (int r = 0; r <= radius && sx == Integer.MIN_VALUE; r++) {
            for (int dz = -r; dz <= r && sx == Integer.MIN_VALUE; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && free(x + dx, z + dz)) {
                        sx = x + dx;
                        sz = z + dz;
                        break;
                    }
                }
            }
        }
        if (sx == Integer.MIN_VALUE) {
            return null;
        }
        boolean[] seen = new boolean[w * h];
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 0};
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = (sz - z0) * w + (sx - x0);
        seen[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int k = queue.poll();
            int i = k % w, j = k / w;
            box[0] = Math.min(box[0], x0 + i);
            box[1] = Math.min(box[1], z0 + j);
            box[2] = Math.max(box[2], x0 + i);
            box[3] = Math.max(box[3], z0 + j);
            box[4]++;
            int[] nbs = {i > 0 ? k - 1 : -1, i + 1 < w ? k + 1 : -1, j > 0 ? k - w : -1, j + 1 < h ? k + w : -1};
            for (int nk : nbs) {
                if (nk >= 0 && ok[nk] && !seen[nk]) {
                    seen[nk] = true;
                    queue.add(nk);
                }
            }
        }
        return box;
    }

    /** 직사각형의 정면: 네 변 바로 바깥의 도로 거리를 비교해서 가장 가까운 쪽 */
    String frontOf(int xa, int za, int xb, int zb) {
        int south = edge(xa, zb + 1, xb, zb + 1), north = edge(xa, za - 1, xb, za - 1);
        int east = edge(xb + 1, za, xb + 1, zb), west = edge(xa - 1, za, xa - 1, zb);
        int best = Math.min(Math.min(south, north), Math.min(east, west));
        // 같으면 긴 변을 정면으로
        boolean wide = xb - xa >= zb - za;
        if (wide && south == best) {
            return "south";
        }
        if (wide && north == best) {
            return "north";
        }
        if (east == best) {
            return "east";
        }
        if (west == best) {
            return "west";
        }
        return south == best ? "south" : "north";
    }

    /** 변을 따라 도로 거리의 최솟값 */
    private int edge(int xa, int za, int xb, int zb) {
        int best = Integer.MAX_VALUE;
        for (int z = za; z <= zb; z++) {
            for (int x = xa; x <= xb; x++) {
                best = Math.min(best, roadDistance(x, z));
            }
        }
        return best;
    }
}
