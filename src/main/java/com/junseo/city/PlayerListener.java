package com.junseo.city;

import com.junseo.city.logic.CharacterData;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Keys;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 접속/퇴장, 죽음(WASTED), 부활, 붕대, 시티 아이템 조합 막기. */
public final class PlayerListener implements Listener {
    private final JunseoCity plugin;
    /** 죽은 사람: 잃은 병원비. */
    private final Map<UUID, Long> wasted = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bandageCooldown = new ConcurrentHashMap<>();

    public PlayerListener(JunseoCity plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    /** 플러그인을 다시 불러올 때도 접속자마다 호출합니다. */
    public void handleJoin(Player player) {
        plugin.characters().ensureLoaded(player).whenComplete((found, error) -> Sched.entity(player, () -> {
            if (error != null) {
                plugin.getLogger().warning("캐릭터 확인 실패: " + player.getName() + " " + error);
                player.kick(Text.mm("<red>서버 데이터를 불러오지 못했어요. 잠시 뒤 다시 접속해 주세요."));
                return;
            }
            if (found.isPresent()) {
                plugin.onCharacterReady(player, found.get());
                Text.send(player, "<gray>다시 오신 걸 환영해요, <white>" + Text.esc(found.get().name()) + "</white> 님! <yellow>G키</yellow><gray>: 스마트폰");
            } else {
                plugin.creation().start(player);
            }
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        plugin.creation().stop(uuid);
        plugin.forgetTicker(uuid);
        plugin.delivery().cancel(player, null);
        plugin.robbery().forget(uuid);
        plugin.cars().onQuit(player);
        plugin.guns().onQuit(player);
        plugin.jail().forget(uuid);
        plugin.gps().forget(uuid);
        plugin.hud().forget(uuid);
        plugin.ui().forget(uuid);
        plugin.dispatch().forget(uuid);
        wasted.remove(uuid);
        bandageCooldown.remove(uuid);
        plugin.characters().unload(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        CharacterData data = plugin.characters().get(victim);
        if (data != null && !data.isJailed()) {
            wasted.put(victim.getUniqueId(), data.loseCashPercent(plugin.settings().hospitalFeePercent));
        }
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
        CharacterData data = plugin.characters().get(player);
        if (data != null && data.isJailed()) {
            event.setRespawnLocation(plugin.jail().jailLocation(player));
            return;
        }
        Location hospital = plugin.places().location(PlaceType.HOSPITAL);
        if (hospital != null) {
            event.setRespawnLocation(hospital);
        }
        Long lost = wasted.remove(player.getUniqueId());
        Sched.entityLater(player, 1, () -> player.showTitle(Title.title(Text.mm("<dark_red><bold>WASTED"),
                Text.mm(lost != null && lost > 0 ? "<gray>병원비 " + plugin.settings().money(lost) + "을 냈어요" : "<gray>병원에서 깨어났어요"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800)))));
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

    /** 총·수갑·폰·신분증 같은 시티 아이템을 재료로 조합하지 못하게. */
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
