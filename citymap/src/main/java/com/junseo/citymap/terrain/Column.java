package com.junseo.citymap.terrain;

/**
 * 지도 한 칸(1×1 블록 세로줄)의 계산 결과.
 * 순수 데이터라서 서버 없이 테스트하고 미리보기 그림을 그릴 수 있습니다.
 */
public final class Column {
    public static final int NO_WATER = Integer.MIN_VALUE;

    public enum Biome { PLAINS, FOREST, RIVER, OCEAN, BEACH }

    /** 맨 위 단단한 블록의 Y. 물이면 바닥(강바닥·해저)의 Y */
    public int groundY;
    /** 물 수면 Y. 물이 없으면 {@link #NO_WATER} */
    public int waterTop = NO_WATER;
    public Surface surface = Surface.GRASS;
    /** 물 기둥의 바닥 재료 (강바닥·해저) */
    public Surface bed = Surface.RIVER_BED;
    public Biome biome = Biome.PLAINS;

    /** 다리 상판: 물 위 Y = 땅 높이에 길을 깝니다 */
    public boolean deck;
    /** 다리 가장자리 난간 */
    public boolean railing;
    /** 다리 기둥: 강바닥부터 상판 아래까지 채움 */
    public boolean pillar;

    /** 산 속 터널: 땅 높이에 길, 그 위 7칸은 비우고 천장을 둠 */
    public boolean tunnel;
    public boolean tunnelWall;
    public boolean tunnelLight;

    /** 거점 표시 기둥 높이 (0이면 없음) */
    public int hubPillar;

    /** 속한 구역 id (없으면 null) */
    public String district;
    /** 산 높이 (땅 위로 잰 값, 산이 아니면 0) */
    public int mountainHeight;

    public boolean isWater() {
        return waterTop != NO_WATER;
    }

    public boolean isRoad() {
        return switch (surface) {
            case ASPHALT, LINE_WHITE, LINE_YELLOW, SIDEWALK -> true;
            default -> false;
        };
    }
}
