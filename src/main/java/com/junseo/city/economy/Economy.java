package com.junseo.city.economy;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.npc.NpcType;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

/** 돈 관련 공용 기능 (은행 메뉴, ATM, 아이템 지급). */
public final class Economy implements Listener {
    private static final long[] AMOUNTS = {100, 1_000, 10_000};

    private final JunseoCity plugin;

    public Economy(JunseoCity plugin) {
        this.plugin = plugin;
    }

    /** ATM 으로 지정한 블록을 우클릭하면 은행 메뉴. */
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
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.8f);
        showBankMenu(event.getPlayer());
    }

    /** ATM/은행원을 눌렀을 때 채팅에 뜨는 클릭 메뉴. */
    public void showBankMenu(Player player) {
        PlayerData data = plugin.data().get(player);
        player.sendMessage(Text.mm("<gray>────────── <aqua><bold>시티 은행</bold></aqua> ──────────"));
        player.sendMessage(Text.mm("  현금 <green>" + plugin.settings().money(data.cash())
                + "</green>   은행 <aqua>" + plugin.settings().money(data.bank()) + "</aqua>"));
        player.sendMessage(buttonRow("입금", "<green>", "deposit"));
        player.sendMessage(buttonRow("출금", "<yellow>", "withdraw"));
        player.sendMessage(Text.mm("<gray>  직접 입력: /은행 입금 5000  ·  송금: /송금 이름 금액"));
    }

    private Component buttonRow(String label, String color, String action) {
        Component row = Text.mm("  " + color + label + "</" + color.substring(1) + " ");
        for (long amount : AMOUNTS) {
            row = row.append(button("[" + plugin.settings().money(amount) + "]", "/bank " + action + " " + amount)).append(Component.space());
        }
        return row.append(button("[전부]", "/bank " + action + " all"));
    }

    private Component button(String text, String command) {
        return Text.mm("<white>" + text)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Text.mm("<gray>클릭: " + command)));
    }

    /** 은행을 쓸 수 있는 곳에 있는지 (ATM, 은행원 근처 또는 설정상 어디서나). */
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

    /** 인벤토리에 넣고, 자리가 없으면 발밑에 떨어뜨립니다. */
    public static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
