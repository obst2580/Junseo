package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

/**
 * 건물 상자 칸 (i, j) 아래의 실제 땅을 알려 줍니다. 광장처럼 땅 모양을 따라 물건을 놓아야 하는 건물이 씁니다
 * ({@link Placement#rect} 와 같은 방식으로 돌림).
 */
final class Ground {
    private final CityTerrain terrain;
    private final int x0, z0, x1, z1, q;

    private Ground(CityTerrain terrain, int x0, int z0, int x1, int z1, int q) {
        this.terrain = terrain;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.q = q;
    }

    static Ground rect(CityTerrain terrain, int x0, int z0, int x1, int z1, String front) {
        int q = switch (front) {
            case "south" -> 0;
            case "west" -> 1;
            case "north" -> 2;
            default -> 3;
        };
        return new Ground(terrain, x0, z0, x1, z1, q);
    }

    /** 건물 칸 → 월드 칸 {x, z} */
    int[] world(int i, int j) {
        return switch (q) {
            case 0 -> new int[]{x0 + i, z0 + j};
            case 1 -> new int[]{x1 - j, z0 + i};
            case 2 -> new int[]{x1 - i, z1 - j};
            default -> new int[]{x0 + j, z1 - i};
        };
    }

    /** 칸 (i, j) 아래 지형 */
    Column column(int i, int j) {
        int[] w = world(i, j);
        return terrain.column(w[0], w[1]);
    }

    /**
     * 칸 (i, j) 의 땅 윗면 블록이 건물 y 로 몇인지. 평지는 -1, 산 위는 산 높이 - 1.
     * (건물 y = 0 이 월드 groundY + 1 이고, 산은 groundY + 산 높이까지 솟아 있음)
     */
    int surfaceY(int i, int j) {
        return column(i, j).mountainHeight - 1;
    }

    /** 칸 (i, j) 둘레 r 칸 안에 도로·물이 없는지 */
    boolean clear(int i, int j, int r) {
        int[] w = world(i, j);
        return clearAt(terrain, w[0], w[1], r);
    }

    static boolean clearAt(CityTerrain terrain, int x, int z, int r) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dz * dz > r * r + r) {
                    continue;
                }
                Column c = terrain.column(x + dx, z + dz);
                if (c.isRoad() || c.isWater() || c.deck || c.tunnel) {
                    return false;
                }
            }
        }
        return true;
    }
}
