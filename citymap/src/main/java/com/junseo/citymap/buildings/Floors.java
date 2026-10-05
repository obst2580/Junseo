package com.junseo.citymap.buildings;

/**
 * 층고 기준 (바닥에서 다음 층 바닥까지 칸 수, 1칸 = 1m). 바닥 1칸 + 빈 칸.
 * 실제 건물에 맞춰서, 사람이 서면 머리 위가 너무 휑하지도 답답하지도 않게 합니다.
 * <ul>
 *   <li>집(빌라·아파트·원룸): 4 (빈 칸 3, 아파트 층고 약 2.8m)</li>
 *   <li>사무실·위층 가게·병실·교실: 4</li>
 *   <li>큰 건물 1층(로비·은행 영업장·식당가): 5 (상가 1층 약 4.5m)</li>
 *   <li>로비·대합실·홀처럼 실제로도 높게 트는 곳만 8 이상 (두 층 이상을 틈)</li>
 * </ul>
 * 동네 근린상가 1층은 4 (빈 칸 3)도 실제와 맞습니다.
 */
final class Floors {
    static final int HOME = 4;
    static final int OFFICE = 4;
    static final int GROUND = 5;
    static final int HALL = 8;

    /**
     * 서는 높이(바닥 블록 바로 위 칸 y)들. 1층은 ground 칸, 그 위는 typical 칸씩, 마지막은 옥상.
     * 예: levels(5, 4, 3) = {0, 5, 9, 13} (3개 층 + 옥상)
     */
    static int[] levels(int ground, int typical, int floors) {
        int[] out = new int[floors + 1];
        for (int k = 1; k <= floors; k++) {
            out[k] = out[k - 1] + (k == 1 ? ground : typical);
        }
        return out;
    }

    private Floors() {
    }
}
