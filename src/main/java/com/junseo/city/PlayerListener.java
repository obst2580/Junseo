package com.junseo.city;

import com.junseo.city.logic.Crime;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.logic.WantedRules;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Keys;
import com.junseo.city.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 접속/퇴장, 죽음(WASTED/BUSTED), 부활, 붕대, 시티 아이템 조합 막기. */
public final class PlayerListener implements Listener {
    private final JunseoCity plugin;
    /** 경찰에게 죽어서 부활하면 감옥에 갈 사람: 수배 별 개수. */
    private final Map<UUID, Integer> busted = new HashMap<>();
    /** 경찰 플레이어가 잡았으면 그 경찰 (체포 보상용). */
    private final Map<UUID, UUID> bustedBy = new HashMap<>();
    /** 그냥 죽은 사람: 잃은 병원비. */
    private final Map<UUID, Long> wasted = new HashMap<>();
    private final Map<UUID, Long> bandageCooldown = new HashMap<>();

    public PlayerListener(JunseoCity plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    /** 플러그인을 다시 불러올 때도 접속자마다 호출합니다. */
    public void handleJoin(Player player) {
        boolean firstJoin = !plugin.data().exists(player.getUniqueId());
        PlayerData data = plugin.data().get(player);
        data.setName(player.getName());
        plugin.hud().show(player);
        plugin.wanted().updateGlow(player);
        plugin.jail().onJoin(player);
        if (firstJoin) {
            Location spawn = plugin.places().location(PlaceType.SPAWN);
            if (spawn != null) {
                player.teleport(spawn);
            }
            player.showTitle(Title.title(Text.mm("<gold><bold>" + Text.esc(plugin.settings().serverName)),
                    Text.mm("<white>에 오신 걸 환영해요!"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(1000))));
            Text.send(player, "<green>환영 선물로 현금 " + plugin.settings().money(data.cash()) + "과 은행 "
                    + plugin.settings().money(data.bank()) + "을 받았어요! <white>/도움말</white> 을 입력해 보세요.");
            player.getInventory().addItem(new ItemStack(Material.BREAD, 5));
        } else {
            Text.send(player, "<gray>다시 오신 걸 환영해요! <white>/도움말");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.delivery().cancel(player, null);
        plugin.robbery().cancel(player, null);
        plugin.police().dismiss(player);
        plugin.cars().onQuit(player);
        plugin.guns().onQuit(player);
        plugin.jail().onQuit(player);
        plugin.hud().remove(player);
        plugin.wanted().forget(player);
        Integer pendingJail = busted.remove(player.getUniqueId());
        if (pendingJail != null) {
            // 체포된 채로 나가도 다음 접속 때 감옥에서 시작해요.
            plugin.data().get(player).setJailSeconds(
                    WantedRules.jailSeconds(pendingJail, plugin.settings().jailSecondsPerStar));
        }
        bustedBy.remove(player.getUniqueId());
        wasted.remove(player.getUniqueId());
        bandageCooldown.remove(player.getUniqueId());
        plugin.data().unload(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        PlayerData data = plugin.data().get(victim);
        int stars = data.wanted();
        Entity cause = event.getDamageSource().getCausingEntity();
        Player killer = victim.getKiller();
        boolean byCop = plugin.police().isCop(cause)
                || (killer != null && plugin.data().get(killer).job() == Job.POLICE);

        if (killer != null && !killer.equals(victim) && stars == 0) {
            plugin.wanted().commit(killer, data.job() == Job.POLICE ? Crime.COP_KILL : Crime.MURDER);
        }
        if (stars > 0 && byCop && !data.isJailed()) {
            busted.put(victim.getUniqueId(), stars);
            if (killer != null && plugin.data().get(killer).job() == Job.POLICE) {
                bustedBy.put(victim.getUniqueId(), killer.getUniqueId());
            }
        } else if (!data.isJailed()) {
            wasted.put(victim.getUniqueId(), data.loseCashPercent(plugin.settings().hospitalFeePercent));
        }
        plugin.wanted().clear(victim);
        plugin.delivery().cancel(victim, "<red>쓰러져서 배달에 실패했어요.");
        plugin.robbery().cancel(victim, "<red>쓰러져서 강도에 실패했어요.");

        if (plugin.settings().keepInventory) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Integer stars = busted.remove(uuid);
        UUID copId = bustedBy.remove(uuid);
        if (stars != null) {
            event.setRespawnLocation(plugin.jail().jailLocation(player));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    plugin.data().get(player).setWanted(stars, System.currentTimeMillis());
                    plugin.jail().arrest(player, copId == null ? null : plugin.getServer().getPlayer(copId));
                }
            });
            return;
        }
        if (plugin.data().get(player).isJailed()) {
            event.setRespawnLocation(plugin.jail().jailLocation(player));
            return;
        }
        Location hospital = plugin.places().location(PlaceType.HOSPITAL);
        if (hospital != null) {
            event.setRespawnLocation(hospital);
        }
        Long lost = wasted.remove(uuid);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.showTitle(Title.title(Text.mm("<dark_red><bold>WASTED"),
                    Text.mm(lost != null && lost > 0 ? "<gray>병원비 " + plugin.settings().money(lost) + "을 냈어요" : "<gray>병원에서 깨어났어요"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))));
        });
    }

    /** 붕대: 우클릭하면 체력 회복. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBandage(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!CustomItems.is(hand, CustomItems.BANDAGE)) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - bandageCooldown.getOrDefault(player.getUniqueId(), 0L) < 3000) {
            return;
        }
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20 : max.getValue();
        if (player.getHealth() >= maxHealth) {
            Text.send(player, "<gray>이미 건강해요!");
            return;
        }
        bandageCooldown.put(player.getUniqueId(), now);
        player.setHealth(Math.min(maxHealth, player.getHealth() + 6));
        hand.setAmount(hand.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.2f);
        player.sendActionBar(Text.mm("<red>붕대를 감았어요 +3칸"));
    }

    /** 총·수갑·총알 같은 시티 아이템을 재료로 조합하지 못하게. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(Keys.ITEM)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
