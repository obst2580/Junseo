package com.junseo.city.economy;

import com.junseo.city.JunseoCity;
import com.junseo.city.npc.NpcType;
import com.junseo.city.place.PlaceType;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** 돈 관련 공용 기능: ATM, 은행 사용 가능 위치, 아이템 지급. */
public final class Economy implements Listener {
    private final JunseoCity plugin;

    public Economy(JunseoCity plugin) {
        this.plugin = plugin;
    }

    /** ATM 으로 지정한 블록을 우클릭하면 은행 화면 (입금·출금 가능). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAtm(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        if (plugin.places().atBlock(event.getClickedBlock(), PlaceType.ATM) == null) {
            return;
        }
        event.setCancelled(true);
        if (plugin.characters().get(event.getPlayer()) == null) {
            return;
        }
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.8f);
        plugin.phone().bank(event.getPlayer(), null);
    }

    /** 입금·출금을 할 수 있는 곳에 있는지 (ATM, 은행원 근처 또는 설정상 어디서나). */
    public boolean canUseBank(Player player) {
        if (plugin.settings().bankAnywhere || player.hasPermission("junseocity.admin")) {
            return true;
        }
        double range = plugin.settings().atmRange;
        if (plugin.places().nearest(player.getLocation(), range, PlaceType.ATM) != null) {
            return true;
        }
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (plugin.npcs().typeOf(entity) == NpcType.BANKER) {
                return true;
            }
        }
        return false;
    }

    /** 인벤토리에 넣고, 자리가 없으면 발밑에 떨어뜨립니다. (플레이어 스레드에서 호출) */
    public static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
