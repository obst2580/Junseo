package com.junseo.citymap.buildings;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 땅 고르기 (깎기·채우기). 산 위·섬 위에 짓는 랜드마크가 씁니다.
 * 칸마다 「서는 높이」(바닥 블록 바로 위 칸 y)와 바닥 블록을 정해 두고 {@link #apply} 를 부르면,
 * 그보다 높은 땅은 깎고(AIR) 낮은 땅은 땅 윗면까지 기초로 채운 뒤 바닥을 깝니다 (공중에 뜨는 바닥이 없음).
 * 옆 칸보다 높아서 드러나는 기초는 옹벽 블록으로, 깎아서 드러나는 옆 땅의 면도 옹벽 블록으로 마감하고,
 * 옆 땅보다 2칸 이상 높은 가장자리에는 난간을 둡니다.
 */
final class Earthwork {
    static final int NONE = Integer.MIN_VALUE;

    final Voxels v;
    final Ground g;
    final int w, d;
    private final int[] top;
    private final int[] stand;
    private final Block[] floor;
    private final boolean[] noRail;

    Earthwork(Voxels v, Ground g) {
        this.v = v;
        this.g = g;
        this.w = v.w;
        this.d = v.d;
        top = new int[w * d];
        stand = new int[w * d];
        floor = new Block[w * d];
        noRail = new boolean[w * d];
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                top[j * w + i] = g.surfaceY(i, j);
                stand[j * w + i] = NONE;
            }
        }
    }

    boolean inside(int i, int j) {
        return i >= 0 && j >= 0 && i < w && j < d;
    }

    /** 원래 땅 윗면 블록 y (상자 밖도 지형에서 읽음) */
    int top(int i, int j) {
        return inside(i, j) ? top[j * w + i] : g.surfaceY(i, j);
    }

    /** 정한 서는 높이 (안 정했으면 NONE) */
    int stand(int i, int j) {
        return inside(i, j) ? stand[j * w + i] : NONE;
    }

    /** 고른 뒤 땅 윗면 블록 y */
    int ground(int i, int j) {
        int s = stand(i, j);
        return s == NONE ? top(i, j) : s - 1;
    }

    void set(int i, int j, int standY, Block floorBlock) {
        if (inside(i, j)) {
            stand[j * w + i] = standY;
            floor[j * w + i] = floorBlock;
        }
    }

    /** 이 칸 가장자리에는 난간을 두지 않음 (건물 벽처럼 따로 막는 곳) */
    void noRail(int i, int j) {
        if (inside(i, j)) {
            noRail[j * w + i] = true;
        }
    }

    /**
     * 계단: (i, j) 에서 (di, dj) 쪽으로 서는 높이 upper → lower 로 한 칸씩 내려갑니다 (폭 width, 옆으로 (si, sj) 쪽).
     * 칸 수는 upper - lower. 계단 블록은 올라가는 쪽(-di, -dj)을 봅니다. 다음 칸(끝)부터가 아래 바닥입니다.
     */
    void stairs(int i, int j, int di, int dj, int si, int sj, int width, int upper, int lower, String material, int rgb) {
        String facing = di > 0 ? "west" : di < 0 ? "east" : dj > 0 ? "north" : "south";
        Block step = Blocks.stairs(material, facing, rgb);
        for (int n = 1; n <= upper - lower; n++) {
            for (int k = 0; k < width; k++) {
                set(i + di * (n - 1) + si * k, j + dj * (n - 1) + sj * k, upper - n + 1, step);
            }
        }
    }

    /** 땅을 고릅니다. fill 은 속 채움, face 는 드러나는 옹벽 면 */
    void apply(Block fill, Block face) {
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int s = stand[j * w + i];
                if (s == NONE) {
                    continue;
                }
                int t = top[j * w + i];
                int lowest = Integer.MAX_VALUE;
                for (int[] n : nb) {
                    lowest = Math.min(lowest, ground(i + n[0], j + n[1]));
                }
                boolean wall = lowest < s - 1;
                for (int y = t + 1; y <= s - 2; y++) {
                    v.set(i, y, j, wall ? face : fill);
                }
                for (int y = s; y <= t; y++) {
                    v.set(i, y, j, AIR);
                }
                v.set(i, s - 1, j, floor[j * w + i]);
                // 깎아서 드러난 옆 땅 면
                for (int[] n : nb) {
                    int ni = i + n[0], nj = j + n[1];
                    if (inside(ni, nj) && stand(ni, nj) == NONE) {
                        for (int y = s; y <= top(ni, nj); y++) {
                            if (v.get(ni, y, nj) == null) {
                                v.set(ni, y, nj, face);
                            }
                        }
                    }
                }
            }
        }
    }

    /** 옆 땅보다 2칸 이상 높은 가장자리에 난간 (건물 등으로 이미 찬 칸은 그대로). 건물을 다 그린 뒤 부릅니다 */
    void rails(Block rail) {
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int s = stand[j * w + i];
                if (s == NONE || noRail[j * w + i] || v.solid(i, s, j)) {
                    continue;
                }
                int lowest = Math.min(Math.min(ground(i + 1, j), ground(i - 1, j)), Math.min(ground(i, j + 1), ground(i, j - 1)));
                if (lowest <= s - 3) {
                    v.set(i, s, j, rail);
                }
            }
        }
    }
}
