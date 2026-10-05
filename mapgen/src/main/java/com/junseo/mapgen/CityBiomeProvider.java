package com.junseo.mapgen;

import com.junseo.citymap.terrain.CityTerrain;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

/** 생물군계: 도시는 평원, 산은 숲, 한강은 강, 바다는 바다. 잔디·물 색과 소리가 여기에 따라 바뀝니다. */
final class CityBiomeProvider extends BiomeProvider {
    private final CityTerrain terrain;

    CityBiomeProvider(CityTerrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return switch (terrain.biomeAt(x, z)) {
            case FOREST -> Biome.FOREST;
            case RIVER -> Biome.RIVER;
            case OCEAN -> Biome.OCEAN;
            case BEACH -> Biome.BEACH;
            case PLAINS -> Biome.PLAINS;
        };
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return List.of(Biome.PLAINS, Biome.FOREST, Biome.RIVER, Biome.OCEAN, Biome.BEACH);
    }
}
