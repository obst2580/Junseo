package com.junseo.city.crime;

import com.junseo.city.JunseoCity;
import com.junseo.city.Settings;
import com.junseo.city.logic.Crime;
import com.junseo.city.place.Place;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Text;
import com.junseo.city.weapon.GunType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 편의점/은행 강도: 금고 블록을 총을 든 채 우클릭하고 정해진 시간 동안 버티면 돈을 받습니다. */
public final class RobberyService implements Listener {

    private static final class Robbery {
        final UUID robber;
        final Place place;
        final boolean bank;
        final BossBar bar;
        int elapsed;

        Robbery(UUID robber, Place place, boolean bank, BossBar bar) {
            this.robber = robber;
            this.place = place;
            this.bank = bank;
            this.bar = bar;
        }
    }

    private final JunseoCity plugin;
    private final Map<UUID, Robbery> active = new HashMap<>();
    private final Map<Integer, Long> cooldownUntil = new HashMap<>();

    public RobberyService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Place place = plugin.places().atBlock(block, PlaceType.STORE_SAFE, PlaceType.BANK_VAULT);
        if (place == null) {
            return;
        }
        event.setCancelled(true);
        tryStart(event.getPlayer(), place);
    }

    private void tryStart(Player player, Place place) {
        boolean bank = place.type() == PlaceType.BANK_VAULT;
        Settings.RobberySettings rs = bank ? plugin.settings().bankRobbery : plugin.settings().storeRobbery;
        if (plugin.data().get(player).isJailed()) {
            return;
        }
        if (active.containsKey(player.getUniqueId())) {
            Text.send(player, "<yellow>이미 강도 중이에요!");
            return;
        }
        GunType gun = CustomItems.gunType(player.getInventory().getItemInMainHand());
        if (gun == null || gun == GunType.TASER) {
            Text.send(player, "<gray>[" + Text.esc(place.label()) + "] <dark_gray>총을 들고 우클릭하면... 강도?!");
            return;
        }
        for (Robbery r : active.values()) {
            if (r.place.id() == place.id()) {
                Text.send(player, "<yellow>누군가 이미 털고 있어요!");
                return;
            }
        }
        long now = System.currentTimeMillis();
        long until = cooldownUntil.getOrDefault(place.id(), 0L);
        if (now < until) {
            Text.send(player, "<yellow>금고가 비어 있어요. " + ((until - now) / 1000 + 59) / 60 + "분 뒤에 다시 차요.");
            return;
        }
        BossBar bar = BossBar.bossBar(Text.mm("강도"), 0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        active.put(player.getUniqueId(), new Robbery(player.getUniqueId(), place, bank, bar));
        player.showBossBar(bar);
        plugin.wanted().commit(player, bank ? Crime.BANK_ROBBERY : Crime.STORE_ROBBERY);
        plugin.wanted().alertPolice(player, "<red><bold>[강도 발생]</bold> " + place.label() + " - " + player.getName());
        Text.send(player, "<red>강도 시작! " + rs.durationSeconds() + "초 동안 금고 근처(" + (int) rs.radius()
                + "칸)에서 버티세요!");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 0.6f);
    }

    /** 1초마다 진행. */
    public void tickSecond() {
        for (Robbery robbery : new ArrayList<>(active.values())) {
            Player player = Bukkit.getPlayer(robbery.robber);
            Settings.RobberySettings rs = robbery.bank ? plugin.settings().bankRobbery : plugin.settings().storeRobbery;
            Location safe = robbery.place.toLocation();
            if (player == null || safe == null) {
                active.remove(robbery.robber);
                continue;
            }
            if (player.isDead() || player.getWorld() != safe.getWorld()
                    || player.getLocation().distanceSquared(safe) > rs.radius() * rs.radius()) {
                cancel(player, "<red>금고에서 너무 멀어져서 강도 실패!");
                continue;
            }
            robbery.elapsed++;
            robbery.bar.progress(Math.min(1f, (float) robbery.elapsed / rs.durationSeconds()));
            robbery.bar.name(Text.mm("<red>강도 중 <white>" + (rs.durationSeconds() - robbery.elapsed) + "초</white> 남음"));
            if (robbery.elapsed % 2 == 0) {
                safe.getWorld().playSound(safe, Sound.BLOCK_BELL_USE, 2f, 1.2f);
            }
            if (robbery.elapsed >= rs.durationSeconds()) {
                finish(player, robbery, rs);
            }
        }
    }

    private void finish(Player player, Robbery robbery, Settings.RobberySettings rs) {
        active.remove(robbery.robber);
        player.hideBossBar(robbery.bar);
        long reward = ThreadLocalRandom.current().nextLong(rs.minReward(), rs.maxReward() + 1);
        plugin.data().get(player).addCash(reward);
        cooldownUntil.put(robbery.place.id(), System.currentTimeMillis() + rs.cooldownMinutes() * 60_000L);
        player.showTitle(Title.title(Text.mm("<green>강도 성공!"), Text.mm("<gold>+" + plugin.settings().money(reward)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        Text.send(player, "<yellow>이제 경찰을 따돌리세요! 죽으면 현금 일부를 병원비로 잃으니 얼른 은행에 넣어 두세요.");
    }

    /** reason 이 null 이면 조용히. */
    public void cancel(Player player, String reason) {
        Robbery robbery = active.remove(player.getUniqueId());
        if (robbery == null) {
            return;
        }
        player.hideBossBar(robbery.bar);
        if (reason != null) {
            Text.send(player, reason);
        }
    }

    public void cancelAll() {
        for (UUID uuid : new ArrayList<>(active.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                cancel(player, null);
            }
        }
        active.clear();
    }
}
