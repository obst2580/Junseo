package com.junseo.city.npc;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Job;
import com.junseo.city.place.PlaceType;
import com.junseo.city.shop.SellMenu;
import com.junseo.city.shop.ShopMenu;
import com.junseo.city.util.Keys;
import com.junseo.city.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** 상점·은행·직업소개소 NPC 세우기와 클릭 처리. 세운 위치는 폰 지도 앱에 나옵니다. */
public final class NpcService implements Listener {
    private final JunseoCity plugin;

    public NpcService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    /** 관리자가 서 있는 위치에 NPC 를 세웁니다 (그 위치의 지역 스레드에서 호출). */
    public Villager spawn(NpcType type, Location location) {
        Villager npc = location.getWorld().spawn(location, Villager.class, villager -> {
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);
            villager.setProfession(type.profession());
            villager.setVillagerLevel(5);
            villager.customName(Text.plain("<yellow><bold>" + type.displayName()));
            villager.setCustomNameVisible(true);
            villager.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, type.id());
        });
        plugin.places().add(PlaceType.NPC, location, type.id());
        return npc;
    }

    /** 시티 NPC 이면 종류, 아니면 null. */
    public NpcType typeOf(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.NPC, PersistentDataType.STRING);
        return NpcType.byId(id);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        NpcType type = typeOf(event.getRightClicked());
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || plugin.characters().get(event.getPlayer()) == null) {
            return;
        }
        open(event.getPlayer(), type);
    }

    public void open(Player player, NpcType type) {
        CharacterData data = plugin.characters().get(player);
        switch (type) {
            case CONVENIENCE -> ShopMenu.convenience(plugin).open(player);
            case GUN_STORE -> ShopMenu.gunStore(plugin).open(player);
            case CAR_DEALER -> ShopMenu.carDealer(plugin).open(player);
            case MINERAL -> new SellMenu(plugin, player).open(player);
            case JOB_CENTER -> plugin.phone().job(player, null);
            case BANKER -> plugin.phone().bank(player, null);
            case DELIVERY_DEPOT -> {
                if (data.job() != Job.DELIVERY) {
                    Text.send(player, "<yellow>택배기사만 일할 수 있어요. 폰의 <white>직업</white> 앱에서 바꿀 수 있어요.");
                } else {
                    plugin.delivery().start(player);
                }
            }
        }
    }

    /** 관리자가 크리에이티브로 NPC 를 없애면 지도에서도 지웁니다. */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (typeOf(event.getEntity()) != null) {
            event.getDrops().clear();
            plugin.places().removeNearest(event.getEntity().getLocation(), 3, PlaceType.NPC);
        }
    }

    /** 번개 맞아서 마녀가 되는 등 NPC 가 변하지 않게. */
    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        if (typeOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }
}
