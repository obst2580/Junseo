package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/** 노상 주차장: 아스팔트 바닥, 흰 주차선, 둘레 연석, 세워 둔 차 몇 대 (블록 장식) */
final class ParkingLot {
    private static final String[] CAR_COLORS = {"white", "black", "gray", "light_gray", "red", "blue", "white", "black"};

    static Voxels build(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 2);
        v.fill(0, -1, 0, w - 1, -1, d - 1, GRAY_CONCRETE);
        v.walls(0, -1, 0, w - 1, -1, d - 1, SMOOTH_STONE);
        // 주차 칸: 깊이 5 줄과 통로 6 을 번갈아
        for (int j = 1; j + 5 < d - 1; j += 11) {
            for (int i = 1; i < w - 1; i += 3) {
                v.fill(i, -1, j, i, -1, j + 4, WHITE_CONCRETE);
                if (i + 3 < w - 1 && r.nextInt(10) < 4) {
                    car(v, r, i + 1, j);
                }
            }
            if (j + 11 < d - 1) {
                for (int i = 1; i < w - 1; i += 3) {
                    v.fill(i, -1, j + 6, i, -1, j + 10, WHITE_CONCRETE);
                }
            }
        }
        return v;
    }

    /** 2×4 칸짜리 세워 둔 차 (i, j 가 왼쪽 앞) */
    static void car(Voxels v, Random r, int i, int j) {
        Block body = concrete(CAR_COLORS[r.nextInt(CAR_COLORS.length)]);
        v.fill(i, 0, j, i + 1, 0, j + 3, body);
        v.fill(i, 1, j + 1, i + 1, 1, j + 2, BLACK_GLASS);
    }

    private ParkingLot() {
    }
}
