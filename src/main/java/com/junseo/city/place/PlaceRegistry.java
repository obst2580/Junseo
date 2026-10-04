package com.junseo.city.place;

import com.junseo.city.JunseoCity;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

/** places.yml 에 병원·감옥·ATM 같은 장소를 저장합니다. */
public final class PlaceRegistry {
    private final JunseoCity plugin;
    private final File file;
    private final List<Place> places = new ArrayList<>();
    private int nextId = 1;

    public PlaceRegistry(JunseoCity plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "places.yml");
        load();
    }

    private void load() {
        places.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("places");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            PlaceType type = s == null ? null : PlaceType.byId(s.getString("type"));
            if (type == null) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            places.add(new Place(id, type, s.getString("world", "world"), s.getDouble("x"), s.getDouble("y"),
                    s.getDouble("z"), (float) s.getDouble("yaw"), (float) s.getDouble("pitch"), s.getString("name", "")));
            nextId = Math.max(nextId, id + 1);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Place p : places) {
            String base = "places." + p.id() + ".";
            yml.set(base + "type", p.type().id());
            yml.set(base + "name", p.name());
            yml.set(base + "world", p.world());
            yml.set(base + "x", p.x());
            yml.set(base + "y", p.y());
            yml.set(base + "z", p.z());
            yml.set(base + "yaw", p.yaw());
            yml.set(base + "pitch", p.pitch());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "places.yml 저장 실패", e);
        }
    }

    public Place add(PlaceType type, Location location, String name) {
        if (type.single()) {
            places.removeIf(p -> p.type() == type);
        }
        Place place = Place.of(nextId++, type, location, name);
        places.add(place);
        save();
        return place;
    }

    public Place removeNearest(Location location, double maxDistance) {
        Place nearest = nearest(location, maxDistance, null);
        if (nearest != null) {
            places.remove(nearest);
            save();
        }
        return nearest;
    }

    public List<Place> all() {
        return Collections.unmodifiableList(places);
    }

    public List<Place> of(PlaceType type) {
        return places.stream().filter(p -> p.type() == type).toList();
    }

    /** 그 종류의 첫 장소 위치. 지정 안 됐거나 월드가 없으면 null. */
    public Location location(PlaceType type) {
        for (Place p : places) {
            if (p.type() == type) {
                Location loc = p.toLocation();
                if (loc != null) {
                    return loc;
                }
            }
        }
        return null;
    }

    public Place atBlock(Block block, PlaceType... types) {
        for (Place p : places) {
            for (PlaceType t : types) {
                if (p.type() == t && p.isBlock(block)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** type 이 null 이면 모든 종류에서 찾습니다. */
    public Place nearest(Location location, double maxDistance, PlaceType type) {
        double max = maxDistance * maxDistance;
        return places.stream()
                .filter(p -> type == null || p.type() == type)
                .filter(p -> p.distanceSquared(location) <= max)
                .min(Comparator.comparingDouble(p -> p.distanceSquared(location)))
                .orElse(null);
    }
}
