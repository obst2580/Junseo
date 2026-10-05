package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 검사용: 실제 설계도(map/layout.json)로 만든 지형과 건물 배치 (한 번만 만듦) */
final class TestCity {
    private static CityTerrain terrain;
    private static CityBuildings buildings;

    static synchronized CityTerrain terrain() {
        if (terrain == null) {
            Path file = Path.of(System.getProperty("layoutFile", "../map/layout.json"));
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                terrain = CityTerrain.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return terrain;
    }

    static synchronized CityBuildings buildings() {
        if (buildings == null) {
            buildings = CityBuildings.plan(terrain());
        }
        return buildings;
    }

    private TestCity() {
    }
}
