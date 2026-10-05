package com.junseo.citymap.terrain;

/** 기둥(1×1 세로줄) 맨 위에 놓을 블록의 종류. 실제 블록은 생성기에서 고릅니다. */
public enum Surface {
    GRASS,
    ROCK,
    SAND,
    /** 한강 둑 */
    EMBANKMENT,
    ASPHALT,
    LINE_WHITE,
    LINE_YELLOW,
    SIDEWALK,
    /** 거점 표시 바닥 */
    PAD,
    RIVER_BED,
    SEA_BED
}
