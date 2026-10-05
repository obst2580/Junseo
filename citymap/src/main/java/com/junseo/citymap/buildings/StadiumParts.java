package com.junseo.citymap.buildings;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 경기장 둘레 시설 (월드컵경기장·야구장이 같이 씀): 매표소, 콘코스 매점, 남녀 화장실.
 * 모두 {@link Frame} 기준 좌표, 서는 높이 0 (땅 높이).
 */
final class StadiumParts {
    static final String[][] FOOD = {{"치킨", "맥주"}, {"떡볶이", "순대"}, {"김밥", "어묵"}, {"핫도그", "음료"}, {"커피", "빵"},
            {"피자", "콜라"}, {"닭강정", "감자튀김"}};

    /** 매표소: 바깥 5×3 (a 0..4, b 0..2), 창구 세 칸이 +b 쪽, 뒤(b = 0) 문, 위 차양과 「매표소」 */
    static void ticketBooth(Frame f) {
        f.walls(0, 0, 0, 4, 2, 2, WHITE_CONCRETE);
        f.fill(1, 0, 1, 3, 2, 1, AIR);
        for (int a = 1; a <= 3; a++) {
            f.set(a, 1, 2, GLASS_PANE);
            f.set(a, 0, 1, Furniture.COUNTER);
        }
        Interior.door(f, 2, 0, 0, "pale_oak", "north");
        f.fill(-1, 3, -1, 5, 3, 3, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        f.fill(-1, 3, 3, 5, 3, 3, RED_CONCRETE);
        f.set(2, 2, 3, Blocks.wallSign("dark_oak", "south", "white", true, "", "매표소", "", ""));
        f.set(2, 2, 1, Interior.LIGHT);
    }

    /**
     * 콘코스 매점: 바깥 11×5 (a -1..9, b 0..4), 뒤(b = 0)는 바깥벽 쪽, 손님은 +b 쪽 계산대 앞에 섬.
     * 안에 조리대·튀김기·냉장고, 계산대 위로 매단 간판 둘, 옆(a = -1) 문.
     */
    static void foodStand(Frame f, String[] name) {
        f.walls(-1, 0, 0, 9, 3, 4, WHITE_CONCRETE);
        f.fill(0, 0, 1, 8, 3, 3, AIR);
        f.fill(0, 0, 4, 8, 2, 4, AIR);
        f.fill(0, 0, 4, 8, 0, 4, Furniture.COUNTER);
        f.fill(0, 0, 1, 8, 0, 1, Furniture.COUNTER);
        f.set(2, 1, 1, SMOKER);
        f.set(5, 1, 1, Furniture.FRIDGE);
        f.set(6, 1, 1, Furniture.FRIDGE);
        f.set(4, 2, 2, Interior.LIGHT);
        f.set(2, 3, 5, Blocks.hangingSign("spruce", 0, "white", true, name[0], name[1]));
        f.set(6, 3, 5, Blocks.hangingSign("spruce", 0, "white", true, name[0], name[1]));
        Interior.door(f, -1, 0, 2, "pale_oak", "west");
    }

    /**
     * 콘코스 가판 (작은 매점): 바깥 8×4 (a 0..7, b 0..3), 뒤(b = 0)는 벽 쪽, 앞(b = 3) 계산대, 위 차양(b 4 까지)과
     * 차양 앞 표지판 둘, 옆(a = 0) 문.
     */
    static void kiosk(Frame f, String[] name) {
        f.fill(0, 0, 0, 7, 2, 0, WHITE_CONCRETE);
        f.fill(0, 0, 0, 0, 2, 3, WHITE_CONCRETE);
        f.fill(7, 0, 0, 7, 2, 3, WHITE_CONCRETE);
        f.fill(1, 0, 1, 6, 2, 2, AIR);
        f.fill(1, 0, 3, 6, 0, 3, Furniture.COUNTER);
        f.fill(1, 1, 3, 6, 2, 3, AIR);
        f.set(2, 0, 1, SMOKER);
        f.set(5, 0, 1, Furniture.FRIDGE);
        f.set(5, 1, 1, Furniture.FRIDGE);
        f.set(3, 2, 1, Interior.LIGHT);
        f.fill(0, 3, 0, 7, 3, 4, Block.of("red_concrete", 0x8E2121));
        f.set(2, 3, 5, Blocks.wallSign("spruce", "south", "white", true, "", name[0], name[1], ""));
        f.set(5, 3, 5, Blocks.wallSign("spruce", "south", "white", true, "", name[0], name[1], ""));
        Interior.door(f, 0, 0, 2, "pale_oak", "west");
    }

    /** 남·여 화장실 나란히: 바깥 a -5..5, b 0..5, 문은 +b 쪽 벽 */
    static void restrooms(Frame f) {
        for (int n = 0; n < 2; n++) {
            int a0 = n * 5 - 4;
            Interior.restroom(f, a0, 1, a0 + 3, 4, 0, 4, n == 0 ? "남자 화장실" : "여자 화장실", a0 + 2);
        }
    }

    private StadiumParts() {
    }
}
