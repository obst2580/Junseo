package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/** 노상 주차장: 아스팔트 바닥, 흰 주차선, 둘레 연석. 차 모형은 두지 않음 (차는 따로 실제로 타는 것) */
final class ParkingLot {
    static Voxels build(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 2);
        v.fill(0, -1, 0, w - 1, -1, d - 1, GRAY_CONCRETE);
        v.walls(0, -1, 0, w - 1, -1, d - 1, SMOOTH_STONE);
        // 주차 칸: 깊이 5 줄과 통로 6 을 번갈아
        for (int j = 1; j + 5 < d - 1; j += 11) {
            for (int i = 1; i < w - 1; i += 3) {
                v.fill(i, -1, j, i, -1, j + 4, WHITE_CONCRETE);
            }
            if (j + 11 < d - 1) {
                for (int i = 1; i < w - 1; i += 3) {
                    v.fill(i, -1, j + 6, i, -1, j + 10, WHITE_CONCRETE);
                }
            }
        }
        return v;
    }

    private ParkingLot() {
    }
}
