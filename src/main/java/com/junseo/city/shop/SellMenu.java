package com.junseo.city.shop;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.menu.Menu;
import com.junseo.city.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 광물 거래소: 광물을 클릭하면 인벤토리에 있는 그 광물을 전부 팝니다. */
public final class SellMenu extends Menu {
    private final JunseoCity plugin;
    private final Map<Integer, Material> slots = new HashMap<>();

    public SellMenu(JunseoCity plugin, Player viewer) {
        super(3, Text.mm("<dark_gray>광물 거래소"));
        this.plugin = plugin;
        double bonus = bonus(viewer);
        int slot = 9;
        for (Map.Entry<String, Long> e : plugin.settings().sellPrices().entrySet()) {
            Material material = Material.matchMaterial(e.getKey());
            if (material == null || !material.isItem() || slot >= 27) {
                continue;
            }
            long each = Math.round(e.getValue() * bonus);
            ItemStack icon = new ItemStack(material);
            icon.editMeta(meta -> {
                List<Component> lore = new ArrayList<>();
                lore.add(Text.plain("<gold>1개당 " + plugin.settings().money(each)));
                if (bonus > 1) {
                    lore.add(Text.plain("<green>광부 보너스 x" + bonus + " 적용!"));
                }
                lore.add(Text.plain("<gray>가진 개수: <white>" + count(viewer, material)));
                lore.add(Text.plain("<yellow>클릭</yellow><gray>: 전부 팔기"));
                meta.lore(lore);
            });
            inventory.setItem(slot, icon);
            slots.put(slot, material);
            slot++;
        }
    }

    private Job jobOf(Player player) {
        CharacterData data = plugin.characters().get(player);
        return data == null ? Job.CITIZEN : data.job();
    }

    private double bonus(Player player) {
        return jobOf(player) == Job.MINER ? plugin.settings().minerSellBonus : 1.0;
    }

    private static int count(Player player, Material material) {
        int total = 0;
        ItemStack plain = new ItemStack(material);
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(plain)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        Material material = slots.get(slot);
        if (material == null) {
            return;
        }
        int amount = count(player, material);
        if (amount <= 0) {
            Text.send(player, "<red>팔 물건이 없어요.");
            return;
        }
        Long base = plugin.settings().sellPrices().get(material.getKey().getKey());
        if (base == null) {
            return;
        }
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            return;
        }
        long each = Math.round(base * bonus(player));
        player.getInventory().removeItem(new ItemStack(material, amount));
        long total = each * amount;
        data.addCash(total);
        Text.send(player, "<green>" + amount + "개를 팔아서 " + plugin.settings().money(total) + "을 벌었어요!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        new SellMenu(plugin, player).open(player);
    }
}
