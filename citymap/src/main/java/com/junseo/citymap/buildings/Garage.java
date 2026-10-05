package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 공영 차고: 플레이어가 차를 꺼내 타는 곳 (kind "garage").
 * <ul>
 *   <li>{@link #lot}: 공영주차장. 노상 주차장에 정산소 부스, 입구 차단기 기둥, "P 공영주차장" 간판, 가로등.</li>
 *   <li>{@link #tower}: 기계식 주차타워 (도심 이면도로에 흔한 좁고 높은 철골 타워). 1층 입출고실에서 차가 나오고,
 *       위는 철골·환기 루버 벽. 관리실에 사람이 들어갈 수 있음.</li>
 * </ul>
 * 차 모형은 두지 않고 차 꺼내는 자리({@link Voxels#carSpot})만 표시합니다.
 */
final class Garage {
    static final int TOWER_W = 9, TOWER_D = 12;

    private static final Block STEEL = Block.of("light_gray_concrete", 0x7D7D73);
    private static final Block FRAME = Block.of("gray_concrete", 0x36393D);
    private static final Block LOUVER = Block.of("iron_bars", 0x888888);
    private static final Block BLUE = Block.of("blue_concrete", 0x2C2E8F);

    /** 공영주차장 (정면 = 길) */
    static Voxels lot(int w, int d, String name, Random r) {
        Voxels v = ParkingLot.build(w, d, r);
        // 정산소 부스 (정면 오른쪽 귀퉁이, 2×3) — 주차 칸 자리를 차지하면 그 칸은 뺌
        int bi = w - 4, bj = d - 5;
        v.fill(bi, 0, bj, bi + 2, 2, bj + 3, WHITE_CONCRETE);
        v.fill(bi + 1, 0, bj + 1, bi + 1, 1, bj + 2, AIR);
        v.set(bi + 1, 0, bj + 3, Block.of("iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]", 0xC0C0C0));
        v.set(bi + 1, 1, bj + 3, Block.of("iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]", 0xC0C0C0));
        v.set(bi, 1, bj + 1, GLASS_PANE);
        v.set(bi, 1, bj + 2, GLASS_PANE);
        v.fill(bi - 1, 3, bj - 1, bi + 3, 3, bj + 4, SMOOTH_STONE_SLAB);
        v.set(bi + 1, 2, bj + 1, Interior.LIGHT);
        v.set(bi - 1, 1, bj, wallSign("spruce", "west", "white", true, "공영주차장", name, "10분 500원"));
        v.carSpots().removeIf(s -> s[0] >= bi - 3 && s[2] >= bj - 3);
        // 입구 차단기 기둥과 P 간판 기둥 (정면 왼쪽)
        v.fill(1, 0, d - 2, 1, 4, d - 2, StreetPlan.POST);
        v.set(1, 5, d - 2, BLUE);
        v.set(1, 4, d - 1, wallSign("spruce", "south", "white", true, "P", "공영주차장"));
        // 둘레 가로등
        for (int i = 2; i < w - 6; i += 12) {
            if (v.get(i, 0, 0) == null) {
                MarketPlan.lampPost(v, i, 0);
            }
        }
        return v;
    }

    /**
     * 기계식 주차타워 (TOWER_W × TOWER_D, 정면 = 입출고실).
     *
     * @param levels 차를 쌓는 단 수 (한 단 3칸)
     */
    static Voxels tower(int levels, String name, Random r) {
        int w = TOWER_W, d = TOWER_D;
        int top = 4 + levels * 3;
        Voxels v = new Voxels(w, d, -1, top + 3);
        v.fill(0, -1, 0, w - 1, -1, d - 1, SMOOTH_STONE);
        // 1층: 가운데 입출고실(트인 문 3칸 + 회전판), 왼쪽 관리실
        v.walls(0, 0, 0, w - 1, 3, d - 1, WHITE_CONCRETE);
        v.fill(3, 0, d - 1, 5, 2, d - 1, AIR); // 입출고구
        v.fill(3, -1, 2, 5, -1, d - 1, GRAY_CONCRETE);
        v.fill(3, -1, 4, 5, -1, 7, POLISHED_ANDESITE); // 회전판
        v.fill(2, 0, 1, 2, 3, d - 2, WHITE_CONCRETE);
        v.fill(6, 0, 1, 6, 3, d - 2, WHITE_CONCRETE);
        v.fill(3, 3, 1, 5, 3, d - 1, WHITE_CONCRETE);
        v.set(4, 2, 5, Interior.LIGHT);
        v.carSpot(4.5, 0, d - 3.0, 0, 1);
        v.carSpot(4.5, 0, 4.5, 0, 1);
        // 관리실 (왼쪽 1칸 폭 → 작은 방): 정면 문, 창
        v.set(1, 0, d - 1, Block.of("iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]", 0xC0C0C0));
        v.set(1, 1, d - 1, Block.of("iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]", 0xC0C0C0));
        v.set(1, 2, d - 3, Interior.LIGHT);
        v.set(2, 1, d - 3, GLASS_PANE); // 입출고실을 내다보는 창
        // 위: 철골 기둥과 단마다 테두리, 사이는 환기 루버
        for (int y = 4; y < top; y++) {
            boolean band = (y - 4) % 3 == 2;
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < d; j++) {
                    boolean edge = i == 0 || j == 0 || i == w - 1 || j == d - 1;
                    if (!edge) {
                        continue;
                    }
                    boolean column = (i == 0 || i == w - 1) && (j == 0 || j == d - 1 || j == d / 2)
                            || (j == 0 || j == d - 1) && (i == 0 || i == w - 1 || i == w / 2);
                    v.set(i, y, j, column || band ? FRAME : LOUVER);
                }
            }
        }
        // 기계 승강로 안쪽 (막힌 판, 밖에서는 루버 사이로 어둡게 보임)
        v.walls(2, 4, 2, w - 3, top - 1, d - 3, STEEL);
        // 지붕: 기계실
        v.fill(0, top, 0, w - 1, top, d - 1, FRAME);
        v.fill(2, top + 1, 3, w - 3, top + 2, d - 4, STEEL);
        // 세로 간판 (정면 오른쪽 모서리 기둥), 입출고구 옆 표지판 (벽 한 칸을 안으로 물리고 그 자리에 붙임)
        for (int y = 5; y < Math.min(top - 1, 17); y++) {
            v.set(w - 1, y, d - 1, BLUE);
        }
        v.set(7, 2, d - 2, WHITE_CONCRETE);
        v.set(7, 2, d - 1, wallSign("spruce", "south", "white", true, "P 주차타워", name, "입출고"));
        return v;
    }

    private Garage() {
    }
}
