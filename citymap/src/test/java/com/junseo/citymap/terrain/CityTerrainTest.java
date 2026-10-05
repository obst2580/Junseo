package com.junseo.citymap.terrain;

import com.junseo.citymap.layout.Layout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 실제 설계도(map/layout.json)로 지형이 의도대로 나오는지 확인합니다. */
class CityTerrainTest {
    static CityTerrain terrain;

    static CityTerrain load() throws IOException {
        Path file = Path.of(System.getProperty("layoutFile", "../map/layout.json"));
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return CityTerrain.load(reader);
        }
    }

    @BeforeAll
    static void setUp() throws IOException {
        terrain = load();
    }

    static Layout.Hub hub(String id) {
        return terrain.layout().hubs().stream().filter(h -> h.id().equals(id)).findFirst().orElseThrow();
    }

    static Column at(double x, double z) {
        return terrain.column((int) Math.floor(x), (int) Math.floor(z));
    }

    /** 한강 북서쪽(도시 밖, 다리 없는 곳) 한가운데 */
    static double[] riverMiddle() {
        Layout.Water han = terrain.layout().water().stream().filter(w -> w.id().equals("hangang")).findFirst().orElseThrow();
        double[] a = han.line().get(1), b = han.line().get(2);
        return new double[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
    }

    /** 남서쪽 바다 (월드 경계 모서리 안쪽) */
    static double[] seaPoint() {
        return new double[]{terrain.layout().borderMin()[0] + 100, terrain.layout().borderMax()[1] - 100};
    }

    @Test
    void spawnIsMarkedLandAtGroundLevel() {
        Layout.Hub spawn = hub("spawn");
        Column c = at(spawn.x(), spawn.z());
        assertFalse(c.isWater());
        assertEquals(Surface.PAD, c.surface);
        assertEquals(7, c.hubPillar);
        assertEquals(0, c.groundY);
        assertEquals("airport", c.district);
        Column next = at(spawn.x() + 2, spawn.z());
        assertEquals(Surface.PAD, next.surface);
        assertEquals(0, next.hubPillar);
    }

    @Test
    void hanRiverIsDeepWater() {
        double[] mid = riverMiddle();
        Column c = at(mid[0], mid[1]);
        assertTrue(c.isWater(), "한강이어야 해요");
        assertEquals(-1, c.waterTop);
        assertTrue(c.groundY <= -8, "가운데는 깊어야 해요: " + c.groundY);
        assertEquals(Column.Biome.RIVER, c.biome);
    }

    @Test
    void yeouidoIsAnIslandDistrict() {
        double[] center = terrain.districtCenter("yeouido");
        Column c = at(center[0], center[1]);
        assertFalse(c.isWater());
        assertEquals("yeouido", c.district);
        assertEquals(0, c.groundY);
    }

    @Test
    void mapoBridgeHasDeckOverWater() {
        Layout.Road b1 = terrain.layout().bridges().stream().filter(b -> b.id().equals("B1")).findFirst().orElseThrow();
        double[] a = b1.line().get(0), b = b1.line().get(1);
        Column c = at((a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
        assertTrue(c.isWater(), "다리 밑은 물이어야 해요");
        assertTrue(c.deck, "다리 상판이 있어야 해요");
        assertTrue(c.isRoad(), "상판은 도로여야 해요: " + c.surface);
    }

    @Test
    void seaAndOutsideBorderAreOcean() {
        double[] s = seaPoint();
        Column sea = at(s[0], s[1]);
        assertTrue(sea.isWater());
        assertEquals(Column.Biome.OCEAN, sea.biome);
        double outsideZ = terrain.layout().borderMax()[1] + 600;
        Column outside = at(0, outsideZ);
        assertTrue(outside.isWater());
        assertEquals(Column.Biome.OCEAN, outside.biome);
        assertFalse(terrain.insideBorder(0, outsideZ));
    }

    @Test
    void prisonIslandIsLandInTheSea() {
        Layout.Island island = terrain.layout().islands().stream().filter(i -> i.id().equals("prison")).findFirst().orElseThrow();
        Column center = at(island.cx() + 10, island.cz() + 10);
        assertFalse(center.isWater(), "섬 가운데는 땅이어야 해요");
        assertEquals(1, terrain.standY((int) island.cx() + 10, (int) island.cz() + 10));
        Column offshore = at(island.cx(), island.cz() + island.radius() + 40);
        assertTrue(offshore.isWater(), "섬 밖은 바다여야 해요");
    }

    @Test
    void airportIsAnIslandReachedByBridges() {
        Layout.Hub spawn = hub("spawn");
        assertFalse(at(spawn.x() + 10, spawn.z() + 10).isWater(), "공항은 땅이어야 해요");
        // 공항과 청라 사이 해협 (다리에서 떨어진 곳)
        Column strait = at(-1500, 1000);
        assertTrue(strait.isWater(), "공항과 청라 사이는 바다여야 해요");
        assertFalse(strait.deck);
        for (String id : new String[]{"HB3", "HB4"}) {
            Layout.Road bridge = terrain.layout().bridges().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
            double[] a = bridge.line().get(0), b = bridge.line().get(1);
            Column mid = at((a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
            assertTrue(mid.isWater() && mid.deck, id + " 다리 가운데는 물 위의 상판이어야 해요");
        }
    }

    @Test
    void namsanRisesNearTheTower() {
        Layout.Hub tower = hub("tower");
        Column c = at(tower.x() + 10, tower.z() + 10);
        assertTrue(c.mountainHeight > 120, "남산은 높아야 해요: " + c.mountainHeight);
        assertTrue(c.mountainHeight <= 300);
        assertTrue(c.groundY == c.mountainHeight);
    }

    @Test
    void roadsStayFlatAtGroundLevel() {
        // 중구를 감싸는 고속도로 위 (H2 의 꼭짓점 사이)
        Layout.Road h2 = terrain.layout().roads().stream().filter(r -> r.id().equals("H2")).findFirst().orElseThrow();
        double[] a = h2.line().get(4), b = h2.line().get(5);
        Column c = at((a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
        assertTrue(c.isRoad(), "도로여야 해요: " + c.surface);
        assertEquals(0, c.groundY);
        assertEquals(0, c.mountainHeight);
    }

    @Test
    void namsanTunnelGoesThroughTheMountain() {
        Layout.Road t1 = terrain.layout().roads().stream().filter(r -> r.id().equals("T1")).findFirst().orElseThrow();
        double[] a = t1.line().get(1), b = t1.line().get(2);
        Column c = at(a[0] + (b[0] - a[0]) * 0.4, a[1] + (b[1] - a[1]) * 0.4);
        assertTrue(c.tunnel, "남산 아래는 덮인 터널이어야 해요");
        assertTrue(c.mountainHeight >= 10);
        assertEquals(0, c.groundY);
        assertTrue(c.isRoad());
    }

    @Test
    void quarryIsAFlatRockFloor() {
        Layout.Hub quarry = hub("quarry");
        Column c = at(quarry.x() + 5, quarry.z() + 5);
        assertEquals(Surface.ROCK, c.surface);
        double flat = terrain.layout().mountains().stream().flatMap(m -> m.flats().stream()).findFirst().orElseThrow().height();
        assertTrue(Math.abs(c.mountainHeight - flat) <= 3, "채석장 높이: " + c.mountainHeight);
    }

    @Test
    void districtBorderIsMarked() {
        double[] center = terrain.districtCenter("market");
        assertNotNull(center);
        // 대형시장 경계 상자 가운데에서 동쪽으로 걸어가며 경계선을 찾음
        boolean found = false;
        for (int dx = 0; dx < 600 && !found; dx++) {
            Column c = at(center[0] + dx, center[1]);
            found = c.surface == Surface.BORDER_1;
        }
        assertTrue(found, "1차 오픈 구역 경계선이 있어야 해요");
    }

    @Test
    void sameColumnIsAlwaysTheSame() {
        for (int i = 0; i < 200; i++) {
            int x = -2450 + i * 24, z = -1350 + i * 13;
            Column a = terrain.column(x, z), b = terrain.column(x, z);
            assertEquals(a.groundY, b.groundY);
            assertEquals(a.surface, b.surface);
            assertEquals(a.waterTop, b.waterTop);
        }
    }

    @Test
    void biomeMatchesColumnForWaterAndMountains() {
        double[] mid = riverMiddle(), sea = seaPoint(), yeouido = terrain.districtCenter("yeouido");
        assertEquals(Column.Biome.RIVER, terrain.biomeAt((int) mid[0], (int) mid[1]));
        assertEquals(Column.Biome.OCEAN, terrain.biomeAt((int) sea[0], (int) sea[1]));
        Layout.Hub tower = hub("tower");
        assertEquals(Column.Biome.FOREST, terrain.biomeAt((int) tower.x(), (int) tower.z()));
        assertEquals(Column.Biome.PLAINS, terrain.biomeAt((int) yeouido[0], (int) yeouido[1]));
    }
}
