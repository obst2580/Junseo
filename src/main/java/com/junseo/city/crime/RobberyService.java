package com.junseo.city.crime;

import com.junseo.city.JunseoCity;
import com.junseo.city.Settings;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Job;
import com.junseo.city.phone.DispatchService;
import com.junseo.city.place.Place;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Text;
import com.junseo.city.weapon.GunType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 편의점/은행 강도. 인생모드처럼 <b>접속 중인 경찰이 일정 수 이상</b>일 때만 시작할 수 있고,
 * 시작하면 경찰에게 자동 신고가 갑니다. 금고 근처에서 버티면 돈을 받습니다.
 * (3단계에서 보상이 검은돈으로 바뀌고 도구·미니게임이 추가될 예정)
 */
public final class RobberyService implements Listener {

    private static final class Robbery {
        final Place place;
        final boolean bank;
        final BossBar bar;
        int elapsed;

        Robbery(Place place, boolean bank, BossBar bar) {
            this.place = place;
            this.bank = bank;
            this.bar = bar;
        }
    }

    private final JunseoCity plugin;
    private final Map<UUID, Robbery> active = new ConcurrentHashMap<>();
    private final Map<Integer, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> robbing = new ConcurrentHashMap<>();

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
        CharacterData data = plugin.characters().get(player);
        if (data == null || data.isJailed()) {
            return;
        }
        boolean bank = place.type() == PlaceType.BANK_VAULT;
        Settings.RobberySettings rs = bank ? plugin.settings().bankRobbery : plugin.settings().storeRobbery;
        if (active.containsKey(player.getUniqueId())) {
            Text.send(player, "<yellow>이미 강도 중이에요!");
            return;
        }
        GunType gun = CustomItems.gunType(player.getInventory().getItemInMainHand());
        if (gun == null || gun == GunType.TASER) {
            Text.send(player, "<gray>[" + Text.esc(place.label()) + "] <dark_gray>총을 들고 우클릭하면... 강도?!");
            return;
        }
        int police = plugin.dispatch().onDuty(Job.POLICE);
        if (police < rs.minPolice()) {
            Text.send(player, "<yellow>지금은 털 수 없어요. <gray>(근무 중인 경찰 " + police + "명 / 필요 " + rs.minPolice() + "명)");
            return;
        }
        if (robbing.putIfAbsent(place.id(), player.getUniqueId()) != null) {
            Text.send(player, "<yellow>누군가 이미 털고 있어요!");
            return;
        }
        long now = System.currentTimeMillis();
        long until = cooldownUntil.getOrDefault(place.id(), 0L);
        if (now < until) {
            robbing.remove(place.id(), player.getUniqueId());
            Text.send(player, "<yellow>금고가 비어 있어요. " + ((until - now) / 1000 + 59) / 60 + "분 뒤에 다시 차요.");
            return;
        }
        BossBar bar = BossBar.bossBar(Text.mm("강도"), 0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        active.put(player.getUniqueId(), new Robbery(place, bank, bar));
        player.showBossBar(bar);
        Location loc = place.toLocation();
        plugin.dispatch().automatic(DispatchService.Line.POLICE, "robbery:" + place.id(),
                (bank ? "은행 강도 발생! " : "편의점 강도 발생! ") + place.label(), loc != null ? loc : player.getLocation());
        Text.send(player, "<red>강도 시작! " + rs.durationSeconds() + "초 동안 금고 근처(" + (int) rs.radius()
                + "칸)에서 버티세요! 경찰에 신고가 들어갔어요.");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 0.6f);
    }

    /** 플레이어 스레드에서 1초마다. */
    public void tickSecond(Player player) {
        Robbery robbery = active.get(player.getUniqueId());
        if (robbery == null) {
            return;
        }
        Settings.RobberySettings rs = robbery.bank ? plugin.settings().bankRobbery : plugin.settings().storeRobbery;
        Location safe = robbery.place.toLocation();
        if (safe == null || player.isDead() || player.getWorld() != safe.getWorld()
                || player.getLocation().distanceSquared(safe) > rs.radius() * rs.radius()) {
            cancel(player, "<red>금고에서 너무 멀어져서 강도 실패!");
            return;
        }
        robbery.elapsed++;
        robbery.bar.progress(Math.min(1f, (float) robbery.elapsed / rs.durationSeconds()));
        robbery.bar.name(Text.mm("<red>강도 중 <white>" + (rs.durationSeconds() - robbery.elapsed) + "초</white> 남음"));
        if (robbery.elapsed % 2 == 0) {
            player.getWorld().playSound(safe, Sound.BLOCK_BELL_USE, 2f, 1.2f);
        }
        if (robbery.elapsed >= rs.durationSeconds()) {
            finish(player, robbery, rs);
        }
    }

    private void finish(Player player, Robbery robbery, Settings.RobberySettings rs) {
        active.remove(player.getUniqueId());
        robbing.remove(robbery.place.id(), player.getUniqueId());
        player.hideBossBar(robbery.bar);
        long reward = ThreadLocalRandom.current().nextLong(rs.minReward(), rs.maxReward() + 1);
        CharacterData data = plugin.characters().get(player);
        if (data != null) {
            data.addCash(reward);
        }
        cooldownUntil.put(robbery.place.id(), System.currentTimeMillis() + rs.cooldownMinutes() * 60_000L);
        player.showTitle(Title.title(Text.mm("<green>강도 성공!"), Text.mm("<gold>+" + plugin.settings().money(reward)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        Text.send(player, "<yellow>이제 경찰을 따돌리세요!");
    }

    /** reason 이 null 이면 조용히. */
    public void cancel(Player player, String reason) {
        Robbery robbery = active.remove(player.getUniqueId());
        if (robbery == null) {
            return;
        }
        robbing.remove(robbery.place.id(), player.getUniqueId());
        player.hideBossBar(robbery.bar);
        if (reason != null) {
            Text.send(player, reason);
        }
    }

    public void forget(UUID uuid) {
        Robbery robbery = active.remove(uuid);
        if (robbery != null) {
            robbing.remove(robbery.place.id(), uuid);
        }
    }
}
