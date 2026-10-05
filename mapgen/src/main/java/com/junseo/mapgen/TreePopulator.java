package com.junseo.mapgen;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;
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
            double chance = mountain ? 0.45 : c.district == null ? 0.05 : 0;
            if (random.nextDouble() >= chance) {
                continue;
            }
            // 한국 산: 높은 곳은 소나무(가문비로 대신), 낮은 곳은 참나무에 소나무·자작나무 조금
            int roll = random.nextInt(8);
            TreeType type = mountain && (c.mountainHeight > 60 ? roll < 5 : roll < 2) ? TreeType.REDWOOD
                    : roll == 7 ? TreeType.BIRCH : TreeType.TREE;
            Location at = new Location(null, x, c.groundY + 1, z);
            if (region.isInRegion(at)) {
                region.generateTree(at, random, type);
            }
        }
    }
}
