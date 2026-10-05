package com.junseo.mapgen;

import com.junseo.mapgen.terrain.CityTerrain;
import com.junseo.mapgen.terrain.Column;
import com.junseo.mapgen.terrain.Surface;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/** 산에는 숲, 도시 바깥 빈 땅에는 나무 몇 그루. 도시 구역 안에는 심지 않습니다 (건축할 자리). */
final class TreePopulator extends BlockPopulator {
    private static final int ATTEMPTS = 12;
    private final CityTerrain terrain;

    TreePopulator(CityTerrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        for (int i = 0; i < ATTEMPTS; i++) {
            int x = (chunkX << 4) + random.nextInt(16);
            int z = (chunkZ << 4) + random.nextInt(16);
            Column c = terrain.column(x, z);
            if (c.surface != Surface.GRASS || c.isWater() || c.tunnel || c.hubPillar > 0) {
                continue;
            }
            boolean mountain = c.mountainHeight >= 6;
            double chance = mountain ? 0.55 : c.district == null ? 0.05 : 0;
            if (random.nextDouble() >= chance) {
                continue;
            }
            TreeType type = c.mountainHeight > 150 ? TreeType.REDWOOD
                    : random.nextInt(4) == 0 ? TreeType.BIRCH : TreeType.TREE;
            Location at = new Location(null, x, c.groundY + 1, z);
            if (region.isInRegion(at)) {
                region.generateTree(at, random, type);
            }
        }
    }
}
