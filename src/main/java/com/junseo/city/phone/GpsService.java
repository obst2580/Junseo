package com.junseo.city.phone;

import com.junseo.city.logic.Compass;
import com.junseo.city.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 폰 지도·신고 출동의 길 안내 (화면 위 보스바에 거리와 화살표). */
public final class GpsService {
    private static final double ARRIVE = 6;

    private record Route(Location target, String label, BossBar bar) {
    }

    private final Map<UUID, Route> routes = new ConcurrentHashMap<>();

    public void start(Player player, Location target, String label) {
        stop(player);
        BossBar bar = BossBar.bossBar(Text.mm("길 안내"), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        routes.put(player.getUniqueId(), new Route(target.clone(), label, bar));
        player.showBossBar(bar);
        tick(player);
    }

    public boolean active(Player player) {
        return routes.containsKey(player.getUniqueId());
    }

    public void stop(Player player) {
        Route route = routes.remove(player.getUniqueId());
        if (route != null) {
            player.hideBossBar(route.bar());
        }
    }

    public void forget(UUID uuid) {
        routes.remove(uuid);
    }

    /** 플레이어 스레드에서 10틱마다 호출. */
    public void tick(Player player) {
        Route route = routes.get(player.getUniqueId());
        if (route == null) {
            return;
        }
        Location loc = player.getLocation();
        if (loc.getWorld() != route.target().getWorld()) {
            route.bar().name(Text.mm("<yellow>" + Text.esc(route.label()) + " <gray>(다른 지역)"));
            return;
        }
        double dx = route.target().getX() - loc.getX();
        double dz = route.target().getZ() - loc.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= ARRIVE) {
            stop(player);
            player.sendActionBar(Text.mm("<green>도착했어요: " + Text.esc(route.label())));
            player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.6f);
            return;
        }
        route.bar().name(Text.mm("<aqua>" + Text.esc(route.label()) + "  <white>" + Math.round(distance) + "m "
                + Compass.arrow(loc.getYaw(), dx, dz)));
    }
}
