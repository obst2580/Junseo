package com.junseo.city.job;

import com.junseo.city.JunseoCity;
import com.junseo.city.economy.Economy;
import com.junseo.city.logic.Compass;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.place.Place;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 택배기사 미션: 택배 상자를 들고 배달지까지 제한 시간 안에 가기. */
public final class DeliveryService {

    private static final class Mission {
        final Location target;
        final String targetName;
        final long startMillis;
        final long deadlineMillis;
        final long reward;
        final BossBar bar;

        Mission(Location target, String targetName, long startMillis, long deadlineMillis, long reward, BossBar bar) {
            this.target = target;
            this.targetName = targetName;
            this.startMillis = startMillis;
            this.deadlineMillis = deadlineMillis;
            this.reward = reward;
            this.bar = bar;
        }
    }

    private final JunseoCity plugin;
    private final Map<UUID, Mission> missions = new HashMap<>();

    public DeliveryService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    public boolean hasMission(Player player) {
        return missions.containsKey(player.getUniqueId());
    }

    public void start(Player player) {
        PlayerData data = plugin.data().get(player);
        if (data.job() != Job.DELIVERY) {
            Text.send(player, "<red>택배기사만 할 수 있어요. <white>/직업</white> 으로 바꿔 보세요.");
            return;
        }
        if (data.isJailed()) {
            Text.send(player, "<red>감옥에서는 일할 수 없어요.");
            return;
        }
        if (hasMission(player)) {
            Text.send(player, "<yellow>이미 배달 중이에요! 보스바의 화살표를 따라가세요. (취소: /택배 취소)");
            return;
        }
        Location from = player.getLocation();
        Location target = null;
        String name = null;
        List<Place> candidates = new ArrayList<>();
        for (Place place : plugin.places().of(PlaceType.DELIVERY_POINT)) {
            if (place.distanceSquared(from) >= 30 * 30) {
                candidates.add(place);
            }
        }
        if (!candidates.isEmpty()) {
            Place place = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            target = place.toLocation();
            name = place.label();
        }
        if (target == null) {
            target = randomTarget(from);
            name = "좌표 " + target.getBlockX() + ", " + target.getBlockZ();
        }
        double distance = from.distance(target);
        var s = plugin.settings();
        long reward = Math.round(s.deliveryBaseReward + distance * s.deliveryRewardPerBlock);
        long seconds = 40 + Math.round(distance / 3.0);
        long now = System.currentTimeMillis();
        BossBar bar = BossBar.bossBar(Text.mm("택배"), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        missions.put(player.getUniqueId(), new Mission(target, name, now, now + seconds * 1000, reward, bar));
        player.showBossBar(bar);
        Economy.give(player, CustomItems.deliveryPackage());
        Text.send(player, "<gold>택배 배달 시작! <white>" + Text.esc(name) + "</white> 까지 "
                + Math.round(distance) + "m, 제한시간 " + seconds + "초. 보상 <green>" + s.money(reward));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
        update(player, missions.get(player.getUniqueId()));
    }

    private Location randomTarget(Location from) {
        World world = from.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        var s = plugin.settings();
        double angle = r.nextDouble(Math.PI * 2);
        double dist = r.nextDouble(s.deliveryRandomMin, s.deliveryRandomMax);
        int x = (int) Math.round(from.getX() + Math.cos(angle) * dist);
        int z = (int) Math.round(from.getZ() + Math.sin(angle) * dist);
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    /** 10틱마다: 보스바 갱신, 도착/시간초과 확인. */
    public void tick() {
        for (UUID uuid : new ArrayList<>(missions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            Mission mission = missions.get(uuid);
            if (player == null || mission == null) {
                missions.remove(uuid);
                continue;
            }
            update(player, mission);
        }
    }

    private void update(Player player, Mission mission) {
        long now = System.currentTimeMillis();
        if (now > mission.deadlineMillis) {
            cancel(player, "<red>시간 초과! 배달에 실패했어요.");
            return;
        }
        Location loc = player.getLocation();
        if (loc.getWorld() != mission.target.getWorld()) {
            mission.bar.name(Text.mm("<yellow>택배: 다른 월드에 있어요"));
            return;
        }
        double dx = mission.target.getX() - loc.getX();
        double dz = mission.target.getZ() - loc.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= 3.5 && Math.abs(mission.target.getY() - loc.getY()) <= 4) {
            complete(player, mission);
            return;
        }
        long left = (mission.deadlineMillis - now) / 1000;
        String arrow = Compass.arrow(loc.getYaw(), dx, dz);
        mission.bar.name(Text.mm("<gold>택배 <white>" + Text.esc(mission.targetName) + "</white>  <yellow>"
                + Math.round(horizontal) + "m " + arrow + "</yellow>  <gray>남은 시간 " + left / 60 + ":" + String.format("%02d", left % 60)));
        float progress = (float) (mission.deadlineMillis - now) / (mission.deadlineMillis - mission.startMillis);
        mission.bar.progress(Math.max(0f, Math.min(1f, progress)));
        mission.bar.color(progress < 0.25f ? BossBar.Color.RED : BossBar.Color.YELLOW);
        if (horizontal < 96) {
            for (int i = 0; i < 12; i++) {
                player.spawnParticle(Particle.HAPPY_VILLAGER, mission.target.clone().add(0, i * 0.5, 0), 2, 0.2, 0.1, 0.2, 0);
            }
        }
    }

    private void complete(Player player, Mission mission) {
        if (!takePackage(player)) {
            cancel(player, "<red>택배 상자가 없어요! 상자를 잃어버려서 배달 실패.");
            return;
        }
        missions.remove(player.getUniqueId());
        player.hideBossBar(mission.bar);
        long reward = mission.reward;
        long half = (mission.deadlineMillis - mission.startMillis) / 2;
        boolean fast = System.currentTimeMillis() - mission.startMillis <= half;
        if (fast) {
            reward += reward * plugin.settings().deliveryFastBonusPercent / 100;
        }
        plugin.data().get(player).addCash(reward);
        player.showTitle(Title.title(Text.mm("<green>배달 완료!"),
                Text.mm("<gold>+" + plugin.settings().money(reward) + (fast ? " <yellow>(빠른 배달 보너스!)" : "")),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.sendMessage(Text.mm("<yellow>[다음 배달 시작하기]").clickEvent(ClickEvent.runCommand("/delivery start")));
    }

    private static boolean takePackage(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (CustomItems.is(stack, CustomItems.PACKAGE)) {
                stack.setAmount(stack.getAmount() - 1);
                inv.setItem(i, stack.getAmount() <= 0 ? null : stack);
                return true;
            }
        }
        return false;
    }

    /** reason 이 null 이면 조용히 취소합니다. */
    public void cancel(Player player, String reason) {
        Mission mission = missions.remove(player.getUniqueId());
        if (mission == null) {
            return;
        }
        player.hideBossBar(mission.bar);
        takePackage(player);
        if (reason != null) {
            Text.send(player, reason);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    public void cancelAll() {
        for (UUID uuid : new ArrayList<>(missions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                cancel(player, null);
            }
        }
        missions.clear();
    }

    /** 사이드바에 보여줄 한 줄. 배달 중이 아니면 null. */
    public String hudLine(Player player) {
        Mission mission = missions.get(player.getUniqueId());
        if (mission == null || player.getWorld() != mission.target.getWorld()) {
            return null;
        }
        Location loc = player.getLocation();
        double dx = mission.target.getX() - loc.getX();
        double dz = mission.target.getZ() - loc.getZ();
        return "<gray>배달 <gold>" + Math.round(Math.sqrt(dx * dx + dz * dz)) + "m " + Compass.arrow(loc.getYaw(), dx, dz);
    }
}
