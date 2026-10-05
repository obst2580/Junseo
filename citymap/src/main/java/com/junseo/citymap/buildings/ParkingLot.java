package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 노상 주차장: 아스팔트 바닥, 흰 주차선, 둘레 연석. 차 모형은 두지 않고 (차는 따로 실제로 타는 것),
 * 주차 칸마다 차를 꺼내 줄 자리({@link Voxels#carSpot})를 표시합니다.
 * <ul>
 *   <li>깊은 땅(정면이 길에 닿음): 통로가 정면 쪽 길로 바로 이어지게 세로로 냄. 칸 5 + 통로 6 + 칸 5 를 16칸마다 되풀이.</li>
 *   <li>얕고 긴 땅(아파트 동 사이 등): 통로를 가로로 길게 내고 양 끝이 단지 길로 이어짐.</li>
 * </ul>
 * 주차 칸은 너비 3 (선 사이 2칸), 깊이 5. 차 앞은 통로 쪽(빼서 바로 나가게)을 봅니다.
 */
final class ParkingLot {
    static final int STALL = 3, DEPTH = 5, AISLE = 6;

    static Voxels build(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 2);
        v.fill(0, -1, 0, w - 1, -1, d - 1, GRAY_CONCRETE);
        v.walls(0, -1, 0, w - 1, -1, d - 1, SMOOTH_STONE);
        if (d >= w || d >= 2 * DEPTH + AISLE + 2) {
            deep(v, w, d);
        } else {
            shallow(v, w, d);
        }
        return v;
    }

    /** 통로가 b 방향 (정면 길로 바로 나감) */
    private static void deep(Voxels v, int w, int d) {
        int period = 2 * DEPTH + AISLE;
        for (int i = 1; i + DEPTH < w - 1; i += period) {
            boolean second = i + period - 1 < w - 1;
            for (int j = 1; j <= d - 2; j += STALL) {
                boolean stall = j + STALL <= d - 2;
                v.fill(i, -1, j, i + DEPTH - 1, -1, j, WHITE_CONCRETE);
                if (stall) {
                    v.carSpot(i + DEPTH / 2.0, 0, j + 2.0, 1, 0);
                }
                if (second) {
                    v.fill(i + DEPTH + AISLE, -1, j, i + period - 1, -1, j, WHITE_CONCRETE);
                    if (stall) {
                        v.carSpot(i + DEPTH + AISLE + DEPTH / 2.0, 0, j + 2.0, -1, 0);
                    }
                }
            }
            // 칸 끝 선과 통로 가운데 노란 점선
            for (int j = 2; j < d - 2 && i + DEPTH + AISLE / 2 < w - 1; j += 4) {
                v.fill(i + DEPTH + AISLE / 2, -1, j, i + DEPTH + AISLE / 2, -1, j + 1, YELLOW_CONCRETE);
            }
        }
    }

    /** 통로가 a 방향 (양 끝으로 나감): 뒤쪽 한 줄, 넓으면 앞쪽도 한 줄 */
    private static void shallow(Voxels v, int w, int d) {
        boolean front = d >= 2 * DEPTH + AISLE - 1;
        for (int i = 3; i <= w - 4; i += STALL) {
            boolean stall = i + STALL <= w - 4;
            v.fill(i, -1, 1, i, -1, DEPTH, WHITE_CONCRETE);
            if (stall) {
                v.carSpot(i + 2.0, 0, 1 + DEPTH / 2.0, 0, 1);
            }
            if (front) {
                v.fill(i, -1, d - 1 - DEPTH, i, -1, d - 2, WHITE_CONCRETE);
                if (stall) {
                    v.carSpot(i + 2.0, 0, d - 1 - DEPTH / 2.0, 0, -1);
                }
            }
        }
        int mid = front ? (DEPTH + 1 + d - 1 - DEPTH) / 2 : (DEPTH + d) / 2;
        for (int i = 3; i < w - 3; i += 4) {
            v.fill(i, -1, mid, i + 1, -1, mid, YELLOW_CONCRETE);
        }
    }

    private ParkingLot() {
    }
}
