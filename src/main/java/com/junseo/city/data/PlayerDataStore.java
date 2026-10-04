package com.junseo.city.data;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** plugins/JunseoCity/players/&lt;uuid&gt;.yml 에 플레이어 데이터를 저장합니다. */
public final class PlayerDataStore {
    private final JunseoCity plugin;
    private final File folder;
    private final Map<UUID, PlayerData> loaded = new HashMap<>();

    public PlayerDataStore(JunseoCity plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("players 폴더를 만들 수 없어요: " + folder);
        }
    }

    public boolean exists(UUID uuid) {
        return loaded.containsKey(uuid) || file(uuid).exists();
    }

    /** 접속 중인 플레이어의 데이터 (없으면 불러오거나 새로 만듭니다). */
    public PlayerData get(Player player) {
        PlayerData data = loaded.get(player.getUniqueId());
        if (data == null) {
            data = load(player.getUniqueId(), player.getName());
            loaded.put(player.getUniqueId(), data);
        }
        return data;
    }

    public Collection<PlayerData> loaded() {
        return Collections.unmodifiableCollection(loaded.values());
    }

    private PlayerData load(UUID uuid, String name) {
        File file = file(uuid);
        PlayerData data = new PlayerData(uuid, name);
        if (!file.exists()) {
            data.setCash(plugin.settings().startingCash);
            data.setBank(plugin.settings().startingBank);
            return data;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        data.setCash(yml.getLong("cash"));
        data.setBank(yml.getLong("bank"));
        Job job = Job.parse(yml.getString("job"));
        data.setJob(job == null ? Job.CITIZEN : job);
        data.setWanted(yml.getInt("wanted"), System.currentTimeMillis());
        data.setJailSeconds(yml.getInt("jail-seconds"));
        data.markClean();
        return data;
    }

    public void save(PlayerData data) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("name", data.name());
        yml.set("cash", data.cash());
        yml.set("bank", data.bank());
        yml.set("job", data.job().key());
        yml.set("wanted", data.wanted());
        yml.set("jail-seconds", data.jailSeconds());
        try {
            yml.save(file(data.uuid()));
            data.markClean();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, data.name() + " 데이터 저장 실패", e);
        }
    }

    /** 바뀐 데이터만 저장합니다. */
    public void saveDirty() {
        for (PlayerData data : loaded.values()) {
            if (data.isDirty()) {
                save(data);
            }
        }
    }

    public void saveAll() {
        loaded.values().forEach(this::save);
    }

    public void unload(UUID uuid) {
        PlayerData data = loaded.remove(uuid);
        if (data != null) {
            save(data);
        }
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }
}
