package com.junseo.city.map;

import com.junseo.citymap.buildings.CityBuildings;
import com.junseo.citymap.buildings.Placement;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

/**
 * 미니맵·지도에 쓰는 도시 그림. 설계도로 미리 계산해 둔 칸 종류 배열입니다.
 * 한 번 만들면 바뀌지 않아서 여러 스레드에서 같이 읽어도 됩니다.
 */
public final class RadarRaster {
    // 칸 종류
    public static final byte OUTSIDE = 0;
    public static final byte WATER = 1;
    public static final byte LAND = 2;
    public static final byte CITY = 3;
    public static final byte SAND = 4;
    public static final byte MOUNTAIN_LOW = 5;
    public static final byte MOUNTAIN_HIGH = 6;
    public static final byte ROAD_EDGE = 7;
    public static final byte ROAD = 8;
    public static final byte BUILDING = 9;
    public static final int KINDS = 10;

    /** 여러 칸을 하나로 줄일 때 무엇을 남길지 (클수록 우선): 도로 > 물 > 산 > 건물 > 땅 */
    private static final int[] PRIORITY = {0, 5, 1, 2, 2, 3, 4, 6, 7, 3};

    private final int x0;
    private final int z0;
    private final int width;
    private final int height;
    /** 한 칸이 몇 블록인지 */
    private final int scale;
    private final byte[] data;

    private RadarRaster(int x0, int z0, int width, int height, int scale, byte[] data) {
        this.x0 = x0;
        this.z0 = z0;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.data = data;
    }

    /** 설계도 전체를 scale 블록 간격으로 훑어서 만듭니다 (건물 없이) */
    public static RadarRaster build(CityTerrain terrain, int scale) {
        return build(terrain, CityBuildings.none(terrain), scale);
    }

    /** 설계도 전체를 scale 블록 간격으로 훑어서 만듭니다 (scale 2 면 약 350만 칸, 몇 초 걸림). 건물 자리도 표시 */
    public static RadarRaster build(CityTerrain terrain, CityBuildings buildings, int scale) {
        double[] min = terrain.layout().borderMin(), max = terrain.layout().borderMax();
        int x0 = (int) Math.floor(min[0]), z0 = (int) Math.floor(min[1]);
        int w = (int) Math.ceil((max[0] - min[0]) / scale), h = (int) Math.ceil((max[1] - min[1]) / scale);
        byte[] data = new byte[w * h];
        int half = scale / 2;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int x = x0 + i * scale + half, z = z0 + j * scale + half;
                data[j * w + i] = classify(terrain.column(x, z), buildings.at(x + 0.5, z + 0.5));
            }
        }
        return new RadarRaster(x0, z0, w, h, scale, data);
    }

    /** 건물이 있으면 건물(광장·주차장은 포장된 회색), 없으면 땅 종류 */
    static byte classify(Column c, Placement building) {
        byte kind = classify(c);
        if (building != null && (kind == LAND || kind == CITY || kind == SAND)) {
            return switch (building.kind) {
                case "plaza", "parking" -> kind;
                default -> BUILDING;
            };
        }
        return kind;
    }

    static byte classify(Column c) {
        if (c.deck || c.tunnel || c.isRoad()) {
            return c.surface == com.junseo.citymap.terrain.Surface.SIDEWALK ? ROAD_EDGE : ROAD;
        }
        if (c.isWater()) {
            return WATER;
        }
        return switch (c.surface) {
            case PAD -> ROAD_EDGE;
            case SAND -> SAND;
            default -> c.mountainHeight > 100 ? MOUNTAIN_HIGH
                    : c.mountainHeight > 0 ? MOUNTAIN_LOW
                    : c.district != null ? CITY : LAND;
        };
    }

    /** factor×factor 칸을 하나로 줄인 지도 (도로·물이 사라지지 않게 우선순위가 높은 것을 남김) */
    public RadarRaster pooled(int factor) {
        int w = (width + factor - 1) / factor, h = (height + factor - 1) / factor;
        byte[] out = new byte[w * h];
        int[] count = new int[KINDS];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                java.util.Arrays.fill(count, 0);
                for (int dz = 0; dz < factor; dz++) {
                    for (int dx = 0; dx < factor; dx++) {
                        int x = i * factor + dx, z = j * factor + dz;
                        if (x < width && z < height) {
                            count[data[z * width + x]]++;
                        }
                    }
                }
                // 도로·물은 조금만 있어도 남기고, 나머지는 가장 많은 것
                byte best;
                if (count[ROAD] > 0) {
                    best = ROAD;
                } else if (count[WATER] * 3 >= factor * factor) {
                    best = WATER;
                } else {
                    best = LAND;
                    int most = -1;
                    for (byte k = 0; k < KINDS; k++) {
                        if (k == ROAD || k == WATER) {
                            continue;
                        }
                        if (count[k] > most || (count[k] == most && PRIORITY[k] > PRIORITY[best])) {
                            most = count[k];
                            best = k;
                        }
                    }
                }
                out[j * w + i] = best;
            }
        }
        return new RadarRaster(x0, z0, w, h, scale * factor, out);
    }

    /**
     * 건물 사이 한 칸짜리 틈(골목)을 메운 지도. 미니맵은 줄마다 색이 바뀔 때마다 글자가 늘어나므로,
     * 작은 건물이 빽빽한 동네를 블록 덩어리로 보여 줘서 보내는 양을 줄입니다 (GTA 레이더도 비슷하게 뭉쳐 보임).
     */
    public RadarRaster closeBuildingGaps() {
        byte[] out = data.clone();
        for (int pass = 0; pass < 2; pass++) {
            byte[] src = out.clone();
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    int k = j * width + i;
                    if (src[k] != CITY && src[k] != LAND) {
                        continue;
                    }
                    boolean horizontal = i > 0 && i + 1 < width && src[k - 1] == BUILDING && src[k + 1] == BUILDING;
                    boolean vertical = j > 0 && j + 1 < height && src[k - width] == BUILDING && src[k + width] == BUILDING;
                    if (pass == 0 ? horizontal : vertical) {
                        out[k] = BUILDING;
                    }
                }
            }
        }
        return new RadarRaster(x0, z0, width, height, scale, out);
    }

    /** 월드 좌표의 칸 종류. 설계도 바깥은 OUTSIDE (바다) */
    public byte at(double x, double z) {
        int i = (int) Math.floor((x - x0) / scale), j = (int) Math.floor((z - z0) / scale);
        if (i < 0 || j < 0 || i >= width || j >= height) {
            return OUTSIDE;
        }
        return data[j * width + i];
    }

    public int scale() {
        return scale;
    }

    /** 설계도 가운데 좌표 */
    public double centerX() {
        return x0 + width * scale / 2.0;
    }

    public double centerZ() {
        return z0 + height * scale / 2.0;
    }

    public int widthBlocks() {
        return width * scale;
    }
}
