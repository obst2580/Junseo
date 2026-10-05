package com.junseo.city.map;

import java.awt.Color;

/** 지도 칸 종류별 색. 어두운 바탕에 밝은 도로 (GTA 레이더 느낌) */
public final class RadarPalette {
    static final Color[] COLORS = new Color[RadarRaster.KINDS];

    static {
        COLORS[RadarRaster.OUTSIDE] = new Color(28, 52, 82);
        COLORS[RadarRaster.WATER] = new Color(48, 98, 150);
        COLORS[RadarRaster.LAND] = new Color(58, 72, 56);
        COLORS[RadarRaster.CITY] = new Color(78, 82, 86);
        COLORS[RadarRaster.SAND] = new Color(160, 148, 104);
        COLORS[RadarRaster.MOUNTAIN_LOW] = new Color(62, 96, 58);
        COLORS[RadarRaster.MOUNTAIN_HIGH] = new Color(104, 118, 92);
        COLORS[RadarRaster.ROAD_EDGE] = new Color(150, 150, 150);
        COLORS[RadarRaster.ROAD] = new Color(214, 214, 214);
        COLORS[RadarRaster.BUILDING] = new Color(120, 126, 136);
    }

    private RadarPalette() {
    }

    public static Color color(byte kind) {
        return COLORS[kind];
    }
}
