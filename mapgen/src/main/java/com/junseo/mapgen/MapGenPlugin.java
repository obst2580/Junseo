package com.junseo.mapgen;

import com.junseo.citymap.terrain.CityTerrain;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 준서 시티 도시 바탕 생성기. 건축 서버(Paper)에서만 씁니다.
 *
 * <p>쓰는 법 두 가지
 * <ul>
 *   <li>/mapgen create : 「city」 월드를 만들고 공항으로 이동 (가장 쉬움)</li>
 *   <li>bukkit.yml 의 worlds.&lt;월드이름&gt;.generator 를 JunseoMapGen 으로 지정 → 기본 월드 자체를 도시로</li>
 * </ul>
 */
public final class MapGenPlugin extends JavaPlugin {
    static final NamespacedKey CITY_WORLD = NamespacedKey.minecraft("city");

    private CityTerrain terrain;

    @Override
    public void onLoad() {
        terrain = loadTerrain();
    }

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("mapgen", "도시 바탕 생성기", List.of("맵생성"), new MapGenCommand(this)));
        // 전에 만들어 둔 city 월드가 있으면 서버가 다 켜진 뒤 다시 불러옵니다
        if (getConfig().getBoolean("city-created", false)) {
            Bukkit.getScheduler().runTask(this, () -> {
                World world = openCityWorld();
                getLogger().info("도시 월드를 불러왔어요: " + world.getName());
            });
        }
        getLogger().info("도시 설계도 v" + terrain.layout().version() + " 불러옴 (구역 "
                + terrain.layout().districts().size() + "곳, 거점 " + terrain.layout().hubs().size() + "곳)");
    }

    /** bukkit.yml 에서 generator: JunseoMapGen 으로 지정했을 때 쓰입니다 */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new CityChunkGenerator(terrain());
    }

    CityTerrain terrain() {
        if (terrain == null) {
            terrain = loadTerrain();
        }
        return terrain;
    }

    /** city 월드를 만들거나 (이미 있으면) 불러옵니다. 메인 스레드에서 불러야 합니다. */
    World openCityWorld() {
        World world = Bukkit.getWorld(CITY_WORLD);
        if (world != null) {
            return world;
        }
        boolean firstTime = !getConfig().getBoolean("city-created", false);
        world = new WorldCreator(CITY_WORLD)
                .generator(new CityChunkGenerator(terrain()))
                .generateStructures(false)
                .createWorld();
        if (world == null) {
            throw new IllegalStateException("도시 월드를 만들지 못했어요");
        }
        if (firstTime) {
            // 건축하기 편하게: 늘 낮, 맑음, 몹 없음
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setTime(6000);
            world.getWorldBorder().setCenter(0, 0);
            world.getWorldBorder().setSize(borderSize());
            getConfig().set("city-created", true);
            saveConfig();
        }
        return world;
    }

    /** 월드 경계(정사각형) 한 변. 설계도의 긴 쪽에 맞춥니다. 설계도 바깥은 바다로 만들어집니다 */
    double borderSize() {
        double[] min = terrain().layout().borderMin(), max = terrain().layout().borderMax();
        return Math.max(max[0] - min[0], max[1] - min[1]);
    }

    /** Chunky 로 미리 생성할 때 쓰는 반지름 "X Z" */
    String chunkyRadius() {
        double[] min = terrain().layout().borderMin(), max = terrain().layout().borderMax();
        return Math.round((max[0] - min[0]) / 2) + " " + Math.round((max[1] - min[1]) / 2);
    }

    /** plugins/JunseoMapGen/layout.json 을 읽습니다. 없으면 플러그인 안의 설계도를 꺼내 둡니다. */
    private CityTerrain loadTerrain() {
        File file = new File(getDataFolder(), "layout.json");
        if (!file.exists()) {
            saveResource("layout.json", false);
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return CityTerrain.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("layout.json 을 읽지 못했어요: " + e.getMessage(), e);
        }
    }
}
