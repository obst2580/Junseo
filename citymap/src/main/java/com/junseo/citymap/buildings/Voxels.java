package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 건물 하나를 담는 3차원 블록 상자 (건물 기준 좌표).
 * i 는 너비(0..w-1, 동쪽), j 는 깊이(0..d-1, 남쪽), y 는 높이(0 = 땅 바로 위 첫 칸, -1 = 땅 높이).
 * 정면은 남쪽(j = d-1)으로 짓고, 실제 방향은 {@link Placement} 가 돌려서 놓습니다.
 * 칸을 비워 두면(null) 그 자리의 지형을 그대로 둡니다. 땅을 파려면 AIR 를 넣습니다.
 * 상자 밖에 그리면 그냥 무시합니다.
 */
public final class Voxels {
    public final int w;
    public final int d;
    /** 가장 낮은 y */
    public final int y0;
    /** 높이 칸 수 */
    public final int h;
    private final short[] cells;
    private final List<Block> palette = new ArrayList<>();
    private final Map<Block, Short> index = new HashMap<>();

    public Voxels(int w, int d, int y0, int y1) {
        this.w = w;
        this.d = d;
        this.y0 = y0;
        this.h = y1 - y0 + 1;
        this.cells = new short[w * d * h];
    }

    public boolean inside(int i, int y, int j) {
        return i >= 0 && j >= 0 && i < w && j < d && y >= y0 && y < y0 + h;
    }

    private int at(int i, int y, int j) {
        return ((y - y0) * d + j) * w + i;
    }

    public void set(int i, int y, int j, Block b) {
        if (!inside(i, y, j)) {
            return;
        }
        cells[at(i, y, j)] = b == null ? 0 : code(b);
    }

    /** 빈 칸(null)일 때만 넣음 */
    public void setIfEmpty(int i, int y, int j, Block b) {
        if (inside(i, y, j) && cells[at(i, y, j)] == 0) {
            cells[at(i, y, j)] = code(b);
        }
    }

    private short code(Block b) {
        Short c = index.get(b);
        if (c == null) {
            palette.add(b);
            c = (short) palette.size();
            index.put(b, c);
        }
        return c;
    }

    public Block get(int i, int y, int j) {
        if (!inside(i, y, j)) {
            return null;
        }
        int c = cells[at(i, y, j)];
        return c == 0 ? null : palette.get(c - 1);
    }

    /** 칸 번호 (0 = 비움, n = palette(n-1)). 빠른 읽기용 */
    int raw(int i, int y, int j) {
        return inside(i, y, j) ? cells[at(i, y, j)] : 0;
    }

    List<Block> palette() {
        return palette;
    }

    public boolean solid(int i, int y, int j) {
        Block b = get(i, y, j);
        return b != null && !b.isAir();
    }

    // ------------------------------------------------------------------ 그리기 도구

    /** 상자 채우기 (양 끝 포함, 순서 상관없음) */
    public void fill(int i0, int ya, int j0, int i1, int yb, int j1, Block b) {
        for (int y = Math.min(ya, yb); y <= Math.max(ya, yb); y++) {
            for (int j = Math.min(j0, j1); j <= Math.max(j0, j1); j++) {
                for (int i = Math.min(i0, i1); i <= Math.max(i0, i1); i++) {
                    set(i, y, j, b);
                }
            }
        }
    }

    /** 상자 테두리 벽 (안은 그대로) */
    public void walls(int i0, int ya, int j0, int i1, int yb, int j1, Block b) {
        fill(i0, ya, j0, i1, yb, j0, b);
        fill(i0, ya, j1, i1, yb, j1, b);
        fill(i0, ya, j0, i0, yb, j1, b);
        fill(i1, ya, j0, i1, yb, j1, b);
    }

    /** 세로 원기둥 (가운데 ci, cj 는 소수 가능) */
    public void cylinder(double ci, double cj, double r, int ya, int yb, Block b) {
        for (int j = (int) Math.floor(cj - r); j <= (int) Math.ceil(cj + r); j++) {
            for (int i = (int) Math.floor(ci - r); i <= (int) Math.ceil(ci + r); i++) {
                double dx = i + 0.5 - ci, dz = j + 0.5 - cj;
                if (dx * dx + dz * dz <= r * r) {
                    fill(i, ya, j, i, yb, j, b);
                }
            }
        }
    }

    /** 세로 원통 껍데기 (두께 t) */
    public void tube(double ci, double cj, double r, double t, int ya, int yb, Block b) {
        for (int j = (int) Math.floor(cj - r); j <= (int) Math.ceil(cj + r); j++) {
            for (int i = (int) Math.floor(ci - r); i <= (int) Math.ceil(ci + r); i++) {
                double dist = Math.hypot(i + 0.5 - ci, j + 0.5 - cj);
                if (dist <= r && dist > r - t) {
                    fill(i, ya, j, i, yb, j, b);
                }
            }
        }
    }

    /** 타원체 (가운데와 반지름은 소수 가능) */
    public void ellipsoid(double ci, double cy, double cj, double ri, double ry, double rj, Block b) {
        for (int y = (int) Math.floor(cy - ry); y <= (int) Math.ceil(cy + ry); y++) {
            for (int j = (int) Math.floor(cj - rj); j <= (int) Math.ceil(cj + rj); j++) {
                for (int i = (int) Math.floor(ci - ri); i <= (int) Math.ceil(ci + ri); i++) {
                    double a = (i + 0.5 - ci) / ri, c = (y + 0.5 - cy) / ry, e = (j + 0.5 - cj) / rj;
                    if (a * a + c * c + e * e <= 1) {
                        set(i, y, j, b);
                    }
                }
            }
        }
    }

    /** 두 점을 잇는 굵은 막대 (반지름 r) */
    public void rod(double ia, double ya, double ja, double ib, double yb, double jb, double r, Block b) {
        double len = Math.sqrt((ib - ia) * (ib - ia) + (yb - ya) * (yb - ya) + (jb - ja) * (jb - ja));
        int lo = (int) Math.floor(Math.min(ia, ib) - r), hi = (int) Math.ceil(Math.max(ia, ib) + r);
        int ylo = (int) Math.floor(Math.min(ya, yb) - r), yhi = (int) Math.ceil(Math.max(ya, yb) + r);
        int jlo = (int) Math.floor(Math.min(ja, jb) - r), jhi = (int) Math.ceil(Math.max(ja, jb) + r);
        for (int y = ylo; y <= yhi; y++) {
            for (int j = jlo; j <= jhi; j++) {
                for (int i = lo; i <= hi; i++) {
                    double px = i + 0.5 - ia, py = y + 0.5 - ya, pz = j + 0.5 - ja;
                    double t = len == 0 ? 0 : Math.max(0, Math.min(1, (px * (ib - ia) + py * (yb - ya) + pz * (jb - ja)) / (len * len)));
                    double qx = px - t * (ib - ia), qy = py - t * (yb - ya), qz = pz - t * (jb - ja);
                    if (qx * qx + qy * qy + qz * qz <= r * r) {
                        set(i, y, j, b);
                    }
                }
            }
        }
    }

    /**
     * 유리창·철창·울타리의 연결 방향을 이웃 블록에 맞춰 정합니다 (생성 단계에서는 블록이 저절로 이어지지 않음).
     * 건물을 다 그린 뒤 한 번 부릅니다.
     */
    public void connect() {
        String[] dirs = {"north", "east", "south", "west"};
        int[][] off = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int y = y0; y < y0 + h; y++) {
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    Block b = get(i, y, j);
                    if (b == null || !connectable(b.id())) {
                        continue;
                    }
                    StringBuilder props = new StringBuilder();
                    for (int k = 0; k < 4; k++) {
                        Block n = get(i + off[k][0], y, j + off[k][1]);
                        boolean on = n != null && !n.isAir() && (connectable(n.id()) || full(n.id()));
                        if (props.length() > 0) {
                            props.append(',');
                        }
                        props.append(dirs[k]).append('=').append(on);
                    }
                    props.append(",waterlogged=false");
                    set(i, y, j, b.with(props.toString()));
                }
            }
        }
    }

    static boolean connectable(String id) {
        return id.endsWith("_pane") || id.endsWith("iron_bars") || id.endsWith("_fence");
    }

    /** 유리창이 붙을 수 있는 꽉 찬 블록인지 (대략) */
    static boolean full(String id) {
        for (String part : new String[]{"slab", "stairs", "lantern", "door", "ladder", "carpet", "potted", "campfire",
                "pickle", "button", "torch", "rod", "chain", "cobweb", "scaffolding", "cauldron", "anvil", "stonecutter"}) {
            if (id.contains(part)) {
                return false;
            }
        }
        return true;
    }
}
