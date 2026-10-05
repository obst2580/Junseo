package com.junseo.citymap.buildings;

import java.util.ArrayDeque;
import java.util.BitSet;

/**
 * 사람이 걸어서 갈 수 있는 곳 찾기 (검사용). 점프 없이, 계단·반 블록만 밟고 오를 수 있다고 봅니다
 * (마인크래프트에서 0.5칸은 저절로 올라감). 내려갈 때는 한 칸까지 뛰어내려도 됩니다.
 * 철문은 손으로 못 여니 막힌 것으로 봅니다.
 * <p>
 * 건물 상자 둘레 한 칸 밖은 땅(y = -1 이 땅, y = 0 부터 빈 공간)으로 봐서, 밖에서 걸어 들어오는 길을 찾습니다.
 */
final class WalkCheck {
    private final Voxels v;
    private final int ox = 1, oz = 1, w, d, ylo, yhi;
    private final BitSet seen;

    WalkCheck(Voxels v) {
        this.v = v;
        this.w = v.w + 2;
        this.d = v.d + 2;
        this.ylo = Math.max(v.y0, -1) + 1;
        this.yhi = v.y0 + v.h;
        this.seen = new BitSet(w * d * (yhi - ylo + 1));
    }

    private Block at(int x, int y, int z) {
        int i = x - ox, j = z - oz;
        Block b = v.get(i, y, j);
        if (b == null) {
            return y < 0 ? GROUND : null;
        }
        return b.isAir() ? null : b;
    }

    private static final Block GROUND = Block.of("stone", 0);

    /** 몸이 지나갈 수 있는 칸 */
    static boolean passable(Block b) {
        if (b == null) {
            return true;
        }
        String id = b.id().substring(b.id().indexOf(':') + 1);
        if (id.equals("iron_door")) {
            return false;
        }
        return id.endsWith("_door") || id.endsWith("sign") || id.endsWith("_carpet") || id.endsWith("_button")
                || id.equals("tripwire_hook") || id.endsWith("torch") || id.endsWith("pressure_plate") || id.endsWith("rail")
                || id.equals("ladder") || id.equals("snow") || id.equals("light")
                || (id.endsWith("_trapdoor") && "true".equals(b.property("open")));
    }

    /** 밟고 설 수 있는 칸 (그 위에 서면 발은 한 칸 위) */
    static boolean support(Block b) {
        if (b == null || passable(b)) {
            return false;
        }
        String id = b.id();
        return !(id.endsWith("_pane") || id.endsWith("iron_bars") || id.endsWith("_fence") || id.endsWith("_wall")
                || id.endsWith("lantern") || id.contains("iron_chain") || id.endsWith("_rod") || id.contains("potted"));
    }

    /** 반 칸 높이라 걸어서 올라서는 칸 (계단, 아래 반 블록) */
    static boolean step(Block b) {
        if (b == null) {
            return false;
        }
        String id = b.id();
        return (id.endsWith("_stairs") && !"top".equals(b.property("half")))
                || (id.endsWith("_slab") && "bottom".equals(b.property("type")));
    }

    private boolean stand(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= w || z >= d || y < ylo || y + 1 > yhi) {
            return false;
        }
        return passable(at(x, y, z)) && passable(at(x, y + 1, z)) && support(at(x, y - 1, z));
    }

    private int key(int x, int y, int z) {
        return ((y - ylo) * d + z) * w + x;
    }

    /** 앞(남쪽) 가운데 바깥 땅에서 출발해 갈 수 있는 곳을 모두 찾습니다 */
    WalkCheck run() {
        return run(w / 2, 0, d - 1);
    }

    WalkCheck run(int sx, int sy, int sz) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        if (!stand(sx, sy, sz)) {
            throw new IllegalArgumentException("출발점에 설 수 없어요: " + sx + "," + sy + "," + sz);
        }
        seen.set(key(sx, sy, sz));
        q.add(new int[]{sx, sy, sz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1], z = p[2];
            for (int[] dd : dirs) {
                int nx = x + dd[0], nz = z + dd[1];
                // 같은 높이
                visit(q, nx, y, nz);
                // 계단·반 블록으로 한 칸 오름 (머리 위 한 칸 더 비어야 함)
                if (step(at(nx, y, nz)) && passable(at(x, y + 2, z))) {
                    visit(q, nx, y + 1, nz);
                }
                // 내려가기 (한 칸)
                visit(q, nx, y - 1, nz);
            }
        }
        return this;
    }

    private void visit(ArrayDeque<int[]> q, int x, int y, int z) {
        if (stand(x, y, z)) {
            int k = key(x, y, z);
            if (!seen.get(k)) {
                seen.set(k);
                q.add(new int[]{x, y, z});
            }
        }
    }

    /** 건물 좌표 (i, y, j) 에 서서 갈 수 있는지 */
    boolean reached(int i, int y, int j) {
        int x = i + ox, z = j + oz;
        return x >= 0 && z >= 0 && x < w && z < d && y >= ylo && y + 1 <= yhi && seen.get(key(x, y, z));
    }

    /** 이 높이(서는 높이)에 건물 안쪽으로 갈 수 있는 칸이 몇 개인지 */
    int reachedAt(int y) {
        int n = 0;
        for (int j = 0; j < v.d; j++) {
            for (int i = 0; i < v.w; i++) {
                if (reached(i, y, j)) {
                    n++;
                }
            }
        }
        return n;
    }
}
