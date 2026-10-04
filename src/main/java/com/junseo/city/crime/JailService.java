package com.junseo.city.crime;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 감옥: 정해진 시간 동안 감옥 안에 있어야 합니다. (2단계부터 경찰 플레이어가 수감) */
public final class JailService {
    private static final double ESCAPE_RANGE = 20;

    private final JunseoCity plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public JailService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    /** 플레이어 스레드에서 호출. */
    public void jail(Player player, int seconds, String reason) {
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            return;
        }
        data.setJailSeconds(seconds);
        plugin.delivery().cancel(player, null);
        plugin.robbery().cancel(player, null);
        player.leaveVehicle();
        player.teleportAsync(jailLocation(player));
        Text.send(player, "<red>" + reason + " 감옥에서 " + seconds + "초 동안 반성하세요.");
        showBar(player);
    }

    public void release(Player player) {
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            return;
        }
        data.setJailSeconds(0);
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        Location exit = plugin.places().location(PlaceType.JAIL_EXIT);
        player.teleportAsync(exit != null ? exit : plugin.spawnLocation(player));
        Text.send(player, "<green>출소했어요! 이제 착하게 살아요.");
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1f);
    }

    public void onJoin(Player player) {
        CharacterData data = plugin.characters().get(player);
        if (data != null && data.isJailed()) {
            player.teleportAsync(jailLocation(player));
            showBar(player);
        }
    }

    public void forget(UUID uuid) {
        bars.remove(uuid);
    }

    private void showBar(Player player) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                k -> BossBar.bossBar(Text.mm("감옥"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10));
        player.showBossBar(bar);
        updateBar(player, bar);
    }

    private void updateBar(Player player, BossBar bar) {
        CharacterData data = plugin.characters().get(player);
        if (data != null) {
            bar.name(Text.mm("<red>감옥 <white>" + data.jailSeconds() + "초</white> 남음"));
        }
    }

    /** 플레이어 스레드에서 1초마다: 남은 시간 줄이기, 탈옥 막기. */
    public void tickSecond(Player player) {
        CharacterData data = plugin.characters().get(player);
        if (data == null || !data.isJailed() || player.isDead()) {
            return;
        }
        data.setJailSeconds(data.jailSeconds() - 1);
        if (!data.isJailed()) {
            release(player);
            return;
        }
        Location jail = jailLocation(player);
        if (player.getWorld() != jail.getWorld() || player.getLocation().distanceSquared(jail) > ESCAPE_RANGE * ESCAPE_RANGE) {
            player.leaveVehicle();
            player.teleportAsync(jail);
            Text.send(player, "<red>탈옥은 안 돼요!");
        }
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            showBar(player);
        } else {
            updateBar(player, bar);
        }
    }

    public Location jailLocation(Player player) {
        Location jail = plugin.places().location(PlaceType.JAIL);
        return jail != null ? jail : plugin.spawnLocation(player);
    }
}
