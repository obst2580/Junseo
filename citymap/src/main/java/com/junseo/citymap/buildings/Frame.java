package com.junseo.citymap.buildings;

/**
 * 건물 상자 안의 돌린 좌표계. 방·가구·계단실을 한 방향(기준: +b 가 남쪽)으로만 그려 두고,
 * 원점 (oi, oj) 과 방향 q 로 돌려서 아무 쪽으로나 놓습니다.
 * <p>
 * 기준 좌표 (a, b): a 는 기준에서 동쪽, b 는 기준에서 남쪽. q 는 위에서 볼 때 시계 방향으로 90도씩 돌린 횟수
 * (0: +b 가 남쪽, 1: +b 가 서쪽, 2: +b 가 북쪽, 3: +b 가 동쪽). 블록의 방향 속성도 같이 돌립니다.
 */
final class Frame {
    final Voxels v;
    final int oi, oj, q;

    Frame(Voxels v, int oi, int oj, int q) {
        this.v = v;
        this.oi = oi;
        this.oj = oj;
        this.q = Math.floorMod(q, 4);
    }

    /** 돌리지 않은 좌표계 */
    static Frame of(Voxels v) {
        return new Frame(v, 0, 0, 0);
    }

    /** +b 가 dir 쪽을 보게 돌린 좌표계 ("south", "west", "north", "east") */
    static Frame facing(Voxels v, int oi, int oj, String dir) {
        return new Frame(v, oi, oj, quarter(dir));
    }

    static int quarter(String dir) {
        return switch (dir) {
            case "south" -> 0;
            case "west" -> 1;
            case "north" -> 2;
            case "east" -> 3;
            default -> throw new IllegalArgumentException(dir);
        };
    }

    int i(int a, int b) {
        return switch (q) {
            case 0 -> oi + a;
            case 1 -> oi - b;
            case 2 -> oi - a;
            default -> oi + b;
        };
    }

    int j(int a, int b) {
        return switch (q) {
            case 0 -> oj + b;
            case 1 -> oj + a;
            case 2 -> oj - b;
            default -> oj - a;
        };
    }

    void set(int a, int y, int b, Block block) {
        v.set(i(a, b), y, j(a, b), block == null ? null : block.rotate(q));
    }

    Block get(int a, int y, int b) {
        return v.get(i(a, b), y, j(a, b));
    }

    boolean empty(int a, int y, int b) {
        Block x = get(a, y, b);
        return x == null || x.isAir();
    }

    void setIfEmpty(int a, int y, int b, Block block) {
        if (v.inside(i(a, b), y, j(a, b)) && get(a, y, b) == null) {
            set(a, y, b, block);
        }
    }

    void fill(int a0, int ya, int b0, int a1, int yb, int b1, Block block) {
        for (int y = Math.min(ya, yb); y <= Math.max(ya, yb); y++) {
            for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++) {
                for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) {
                    set(a, y, b, block);
                }
            }
        }
    }

    void walls(int a0, int ya, int b0, int a1, int yb, int b1, Block block) {
        fill(a0, ya, b0, a1, yb, b0, block);
        fill(a0, ya, b1, a1, yb, b1, block);
        fill(a0, ya, b0, a0, yb, b1, block);
        fill(a1, ya, b0, a1, yb, b1, block);
    }

    /** 이 좌표계 안에 다른 원점·방향을 하나 더 얹은 좌표계 (dir 은 이 좌표계 기준 방향) */
    Frame sub(int a, int b, String dir) {
        return new Frame(v, i(a, b), j(a, b), q + quarter(dir));
    }

    /** 기준 방향 이름(이 좌표계 기준) → 상자 기준 방향 이름 */
    String dir(String local) {
        String[] dirs = {"south", "west", "north", "east"};
        return dirs[(quarter(local) + q) % 4];
    }
}
