package com.junseo.city.crime;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.logic.WantedRules;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 감옥: 체포되면 정해진 시간 동안 감옥 안에 있어야 합니다. */
public final class JailService {
    private static final double ESCAPE_RANGE = 20;

    private final JunseoCity plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public JailService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    public void jail(Player player, int seconds, String reason) {
        PlayerData data = plugin.data().get(player);
        data.setJailSeconds(seconds);
        plugin.wanted().clear(player);
        plugin.delivery().cancel(player, null);
        plugin.robbery().cancel(player, null);
        player.leaveVehicle();
        player.teleport(jailLocation(player));
        Text.send(player, "<red>" + reason + " 감옥에서 " + seconds + "초 동안 반성하세요.");
        showBar(player);
    }

    /** 수배자를 체포합니다. cop 이 있으면 체포 보상을 줍니다. */
    public void arrest(Player suspect, Player cop) {
        int stars = Math.max(1, plugin.data().get(suspect).wanted());
        int seconds = WantedRules.jailSeconds(stars, plugin.settings().jailSecondsPerStar);
        suspect.showTitle(Title.title(Text.mm("<blue><bold>BUSTED"), Text.mm("<gray>체포되었어요!"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))));
        suspect.playSound(suspect.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.8f);
        if (cop != null) {
            long reward = stars * plugin.settings().arrestRewardPerStar;
            plugin.data().get(cop).addCash(reward);
            cop.showTitle(Title.title(Text.mm("<aqua>체포 성공!"), Text.mm("<gold>+" + plugin.settings().money(reward)),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            cop.playSound(cop.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            Bukkit.getServer().sendMessage(Text.mm("<blue>[뉴스] 경찰 " + cop.getName() + " 님이 수배자 "
                    + suspect.getName() + " 님을 체포했어요! " + WantedRules.stars(stars)));
        }
        jail(suspect, seconds, "체포되었어요!");
    }

    public void release(Player player) {
        PlayerData data = plugin.data().get(player);
        data.setJailSeconds(0);
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        Location exit = plugin.places().location(PlaceType.JAIL_EXIT);
        if (exit == null) {
            exit = plugin.spawnLocation(player);
        }
        player.teleport(exit);
        Text.send(player, "<green>출소했어요! 이제 착하게 살아요.");
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1f);
    }

    public void onJoin(Player player) {
        if (plugin.data().get(player).isJailed()) {
            player.teleport(jailLocation(player));
            showBar(player);
        }
    }

    public void onQuit(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void showBar(Player player) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                k -> BossBar.bossBar(Text.mm("감옥"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10));
        player.showBossBar(bar);
        updateBar(player, bar);
    }

    private void updateBar(Player player, BossBar bar) {
        int left = plugin.data().get(player).jailSeconds();
        bar.name(Text.mm("<red>감옥 <white>" + left + "초</white> 남음"));
    }

    /** 1초마다 남은 시간 줄이기, 탈옥 막기. */
    public void tickSecond() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.data().get(player);
            if (!data.isJailed() || player.isDead()) {
                continue;
            }
            data.setJailSeconds(data.jailSeconds() - 1);
            if (!data.isJailed()) {
                release(player);
                continue;
            }
            Location jail = jailLocation(player);
            if (player.getWorld() != jail.getWorld() || player.getLocation().distanceSquared(jail) > ESCAPE_RANGE * ESCAPE_RANGE) {
                player.leaveVehicle();
                player.teleport(jail);
                Text.send(player, "<red>탈옥은 안 돼요!");
            }
            BossBar bar = bars.get(player.getUniqueId());
            if (bar == null) {
                showBar(player);
            } else {
                updateBar(player, bar);
            }
        }
    }

    public Location jailLocation(Player player) {
        Location jail = plugin.places().location(PlaceType.JAIL);
        return jail != null ? jail : plugin.spawnLocation(player);
    }
}
