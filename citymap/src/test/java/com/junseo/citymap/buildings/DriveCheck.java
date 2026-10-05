package com.junseo.citymap.buildings;

import java.util.ArrayDeque;
import java.util.BitSet;

/**
 * 블록 차가 갈 수 있는 곳 찾기 (검사용). 차는 폭 3칸(앞뒤 방향에 수직인 3칸 줄)이고 높이 2칸,
 * 한 번에 반 칸(아래 반 블록)까지만 오르내리며, 반 칸 오르내린 뒤에는 같은 높이에서 2칸 이상 달려야 다음 반 칸을 오릅니다.
 * 방향은 제자리에서만 바꾸고(둘레 3×3 이 모두 같은 높이의 바닥), 옆으로는 못 움직입니다.
 * <p>
 * 높이는 반 칸 단위(h2 = 2 × 바닥 높이)입니다. 표지판·카펫·버튼처럼 얇은 것은 걸리지 않는다고 봅니다.
 * 건물 상자 둘레 pad 칸 밖은 땅(y = -1 이 땅)입니다.
 */
final class DriveCheck {
    private static final int PAD = 4;
    private static final int[] FULL = {0, 2};
    private static final int[] BOTTOM = {0, 1};
    private static final int[] TOP = {1, 2};

    private final Voxels v;
    private final int w, d, hh;
    private final BitSet seen;

    DriveCheck(Voxels v) {
        this.v = v;
        this.w = v.w + 2 * PAD;
        this.d = v.d + 2 * PAD;
        this.hh = 2 * (v.y0 + v.h) + 4;
        this.seen = new BitSet(w * d * hh * 2 * 3);
    }

    /** 칸에서 막힌 부분 {아래, 위} (반 칸 단위 0..2), 비었으면 null */
    private int[] solid(int x, int y, int z) {
        int i = x - PAD, j = z - PAD;
        Block b = v.get(i, y, j);
        if (b == null) {
            return y < 0 ? FULL : null; // 비운 칸: 땅 아래는 흙, 위는 빈 공간
        }
        if (b.isAir()) {
            return null;
        }
        String id = b.id().substring(b.id().indexOf(':') + 1);
        if (id.endsWith("sign") || id.endsWith("_carpet") || id.endsWith("_button") || id.endsWith("pressure_plate")
                || id.endsWith("rail") || id.equals("tripwire_hook") || id.endsWith("torch") || id.equals("light")) {
            return null;
        }
        if (id.endsWith("_slab")) {
            String type = b.property("type");
            return "bottom".equals(type) ? BOTTOM : "top".equals(type) ? TOP : FULL;
        }
        return FULL;
    }

    /** (x, z) 칸에 바닥 높이 h2 로 설 수 있는지: 받침이 있고 위 2칸이 빔 */
    private boolean ok(int x, int z, int h2) {
        if (x < 0 || z < 0 || x >= w || z >= d || h2 < 0 || h2 >= hh) {
            return false;
        }
        if (h2 % 2 == 0) {
            int[] s = solid(x, h2 / 2 - 1, z);
            if (s == null || s[1] != 2) {
                return false;
            }
        } else {
            int[] s = solid(x, (h2 - 1) / 2, z);
            if (s != BOTTOM) {
                return false;
            }
        }
        for (int y = h2 / 2; y <= (h2 + 3) / 2; y++) {
            int[] s = solid(x, y, z);
            if (s != null && 2 * y + s[0] < h2 + 4 && 2 * y + s[1] > h2) {
                return false;
            }
        }
        return true;
    }

    /** 차 한 대(가운데 x, z): axis 0 = 동서로 달림(줄은 남북 3칸), 1 = 남북으로 달림 */
    private boolean strip(int x, int z, int h2, int axis) {
        return axis == 0 ? ok(x, z - 1, h2) && ok(x, z, h2) && ok(x, z + 1, h2)
                : ok(x - 1, z, h2) && ok(x, z, h2) && ok(x + 1, z, h2);
    }

    private boolean square(int x, int z, int h2) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!ok(x + dx, z + dz, h2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private int key(int x, int z, int h2, int axis, int run) {
        return (((z * w + x) * hh + h2) * 2 + axis) * 3 + run;
    }

    /** 건물 앞(남쪽) 바깥 길, 건물 좌표 i 에서 북쪽을 보고 출발 */
    DriveCheck run(int startI) {
        int sx = startI + PAD, sz = v.d + PAD + 2;
        if (!strip(sx, sz, 0, 1)) {
            throw new IllegalArgumentException("출발점에 차를 놓을 수 없어요: " + startI);
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        visit(q, sx, sz, 0, 1, 2);
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], z = p[1], h2 = p[2], axis = p[3], run = p[4];
            for (int s = -1; s <= 1; s += 2) {
                int nx = x + (axis == 0 ? s : 0), nz = z + (axis == 1 ? s : 0);
                for (int dh = -1; dh <= 1; dh++) {
                    if (dh != 0 && run < 2) {
                        continue;
                    }
                    if (strip(nx, nz, h2 + dh, axis)) {
                        visit(q, nx, nz, h2 + dh, axis, dh == 0 ? Math.min(2, run + 1) : 1);
                    }
                }
            }
            if (square(x, z, h2)) {
                visit(q, x, z, h2, 1 - axis, run);
            }
        }
        return this;
    }

    private void visit(ArrayDeque<int[]> q, int x, int z, int h2, int axis, int run) {
        int k = key(x, z, h2, axis, run);
        if (!seen.get(k)) {
            seen.set(k);
            q.add(new int[]{x, z, h2, axis, run});
        }
    }

    /** 건물 좌표 (i, j), 바닥 높이 h2, 방향 axis 로 차가 온 적이 있는지 */
    boolean reached(int i, int j, int h2, int axis) {
        int x = i + PAD, z = j + PAD;
        if (x < 0 || z < 0 || x >= w || z >= d || h2 < 0 || h2 >= hh) {
            return false;
        }
        for (int run = 0; run < 3; run++) {
            if (seen.get(key(x, z, h2, axis, run))) {
                return true;
            }
        }
        return false;
    }

    /** 차 자리 {a, y, b, da, db} 에 (차 앞 방향 축으로) 차가 갈 수 있는지 */
    boolean reachedSpot(double[] s) {
        return reached((int) Math.floor(s[0]), (int) Math.floor(s[2]), 2 * (int) s[1], s[3] != 0 ? 0 : 1);
    }
}
