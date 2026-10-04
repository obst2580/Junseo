package com.junseo.city.logic;

/** 목적지 방향 화살표 (미션 안내용). */
public final class Compass {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private Compass() {
    }

    /**
     * 플레이어가 yaw 방향을 보고 있을 때 (dx, dz) 만큼 떨어진 목적지가 어느 쪽인지 화살표로 알려줍니다.
     * ↑ = 정면, → = 오른쪽.
     */
    public static String arrow(float playerYaw, double dx, double dz) {
        if (dx == 0 && dz == 0) {
            return "●";
        }
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = CarPhysics.wrapDegrees((float) (targetYaw - playerYaw));
        int index = Math.floorMod((int) Math.round(relative / 45.0), 8);
        return ARROWS[index];
    }
}
