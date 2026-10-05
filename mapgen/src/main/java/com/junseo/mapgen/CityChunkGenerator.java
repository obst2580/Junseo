package com.junseo.mapgen;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * 도시 설계도대로 땅·한강·바다·산·도로·다리·터널을 까는 월드 생성기.
 * 바닐라 지형·동굴·구조물·몹은 만들지 않습니다.
 */
public final class CityChunkGenerator extends ChunkGenerator {
    private final CityTerrain terrain;

    public CityChunkGenerator(CityTerrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        int minY = data.getMinHeight();
        int maxY = data.getMaxHeight();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (chunkX << 4) + lx, z = (chunkZ << 4) + lz;
                place(data, lx, lz, x, z, terrain.column(x, z), minY, maxY);
            }
        }
    }

    private void place(ChunkData d, int lx, int lz, int x, int z, Column c, int minY, int maxY) {
        int ground = terrain.groundY();
        d.setBlock(lx, minY, lz, Material.BEDROCK);

        if (c.isWater()) {
            int bed = c.groundY;
            fill(d, lx, lz, minY + 1, bed - 2, Material.STONE);
            fill(d, lx, lz, bed - 2, bed + 1, c.bed == Surface.SEA_BED ? Material.SAND : riverBed(x, z));
            fill(d, lx, lz, bed + 1, c.waterTop + 1, c.pillar ? Material.STONE_BRICKS : Material.WATER);
            if (c.deck) {
                d.setBlock(lx, ground, lz, material(c.surface));
                if (c.railing) {
                    d.setBlock(lx, ground + 1, lz, Material.STONE_BRICKS);
                }
            }
            return;
        }

        if (c.tunnel) {
            // 산 속 터널: 산은 그대로 두고 땅 높이에 길을 뚫음
            int top = Math.min(maxY - 1, ground + c.mountainHeight);
            fill(d, lx, lz, minY + 1, top - 3, Material.STONE);
            fill(d, lx, lz, top - 3, top, Material.DIRT);
            d.setBlock(lx, top, lz, c.mountainHeight > 240 ? Material.STONE : Material.GRASS_BLOCK);
            d.setBlock(lx, ground, lz, material(c.surface));
            fill(d, lx, lz, ground + 1, ground + 8, c.tunnelWall ? Material.STONE_BRICKS : Material.AIR);
            d.setBlock(lx, ground + 8, lz, c.tunnelLight ? Material.SEA_LANTERN : Material.STONE);
            return;
        }

        int g = Math.min(maxY - 1, c.groundY);
        switch (c.surface) {
            case GRASS -> {
                fill(d, lx, lz, minY + 1, g - 3, Material.STONE);
                fill(d, lx, lz, g - 3, g, Material.DIRT);
                d.setBlock(lx, g, lz, Material.GRASS_BLOCK);
            }
            case ROCK -> fill(d, lx, lz, minY + 1, g + 1, Material.STONE);
            case SAND -> {
                fill(d, lx, lz, minY + 1, g - 3, Material.STONE);
                fill(d, lx, lz, g - 3, g - 1, Material.SANDSTONE);
                fill(d, lx, lz, g - 1, g + 1, Material.SAND);
            }
            default -> {
                fill(d, lx, lz, minY + 1, g, Material.STONE);
                d.setBlock(lx, g, lz, material(c.surface));
            }
        }
        if (c.hubPillar > 0) {
            fill(d, lx, lz, g + 1, g + 1 + c.hubPillar, Material.RED_CONCRETE);
            d.setBlock(lx, g + 1 + c.hubPillar, lz, Material.SEA_LANTERN);
        }
    }

    /** [y0, y1) 를 채웁니다 */
    private static void fill(ChunkData d, int lx, int lz, int y0, int y1, Material material) {
        if (y1 > y0) {
            d.setRegion(lx, y0, lz, lx + 1, y1, lz + 1, material);
        }
    }

    static Material material(Surface surface) {
        return switch (surface) {
            case GRASS -> Material.GRASS_BLOCK;
            case ROCK -> Material.STONE;
            case SAND -> Material.SAND;
            case EMBANKMENT -> Material.STONE_BRICKS;
            case ASPHALT -> Material.GRAY_CONCRETE;
            case LINE_WHITE -> Material.WHITE_CONCRETE;
            case LINE_YELLOW -> Material.YELLOW_CONCRETE;
            case SIDEWALK -> Material.LIGHT_GRAY_CONCRETE;
            case BORDER_1 -> Material.ORANGE_CONCRETE;
            case BORDER_2 -> Material.LIME_CONCRETE;
            case BORDER_3 -> Material.LIGHT_BLUE_CONCRETE;
            case PAD -> Material.QUARTZ_BLOCK;
            case RIVER_BED -> Material.GRAVEL;
            case SEA_BED -> Material.SAND;
        };
    }

    /** 강바닥은 자갈·모래·점토를 섞음 */
    private static Material riverBed(int x, int z) {
        int h = (x * 73856093) ^ (z * 19349663);
        return switch (Math.floorMod(h, 7)) {
            case 0, 1 -> Material.SAND;
            case 2 -> Material.CLAY;
            default -> Material.GRAVEL;
        };
    }

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        return terrain.standY(x, z);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new CityBiomeProvider(terrain);
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new TreePopulator(terrain));
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return spawn(world, terrain);
    }

    /** 공항 터미널 옆 (설계도의 spawn 거점) */
    static Location spawn(World world, CityTerrain terrain) {
        for (Layout.Hub h : terrain.layout().hubs()) {
            if ("spawn".equals(h.id())) {
                int x = (int) Math.floor(h.x()) + 2, z = (int) Math.floor(h.z());
                return new Location(world, x + 0.5, terrain.standY(x, z), z + 0.5);
            }
        }
        return new Location(world, 0.5, terrain.standY(0, 0), 0.5);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
