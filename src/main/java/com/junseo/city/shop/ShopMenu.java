package com.junseo.city.shop;

import com.junseo.city.JunseoCity;
import com.junseo.city.economy.Economy;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.menu.Menu;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Text;
import com.junseo.city.weapon.GunType;
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
import java.util.function.Function;
import java.util.function.Supplier;

/** 물건을 사는 상점 메뉴 (편의점, 총포상, 자동차 대리점). */
public final class ShopMenu extends Menu {

    /** @param bulk Shift+클릭 때 한 번에 사는 묶음 수 (1이면 묶음 구매 없음) */
    private record Entry(String name, Function<Player, ItemStack> product, long price, int bulk) {
    }

    private final JunseoCity plugin;
    private final Map<Integer, Entry> entries = new HashMap<>();

    private ShopMenu(JunseoCity plugin, String title) {
        super(3, Text.mm("<dark_gray>" + title));
        this.plugin = plugin;
    }

    public static ShopMenu convenience(JunseoCity plugin) {
        ShopMenu menu = new ShopMenu(plugin, "편의점");
        menu.add(10, "빵", () -> new ItemStack(Material.BREAD), "bread", 15, 10);
        menu.add(11, "스테이크", () -> new ItemStack(Material.COOKED_BEEF), "steak", 40, 10);
        menu.add(12, "황금 사과", () -> new ItemStack(Material.GOLDEN_APPLE), "golden_apple", 500, 5);
        menu.add(13, "붕대", CustomItems::bandage, "bandage", 120, 5);
        menu.add(14, "횃불 16개", () -> new ItemStack(Material.TORCH, 16), "torch", 30, 4);
        return menu;
    }

    public static ShopMenu gunStore(JunseoCity plugin) {
        ShopMenu menu = new ShopMenu(plugin, "총포상");
        menu.add(10, "권총", () -> CustomItems.gun(GunType.PISTOL), "pistol", 2000, 1);
        menu.add(11, "산탄총", () -> CustomItems.gun(GunType.SHOTGUN), "shotgun", 5000, 1);
        menu.add(12, "소총", () -> CustomItems.gun(GunType.RIFLE), "rifle", 9000, 1);
        menu.add(14, "총알 30발", () -> CustomItems.ammo(30), "ammo", 300, 5);
        menu.add(16, "방탄조끼", ShopMenu::armor, "armor", 1500, 1);
        return menu;
    }

    private static ItemStack armor() {
        ItemStack stack = new ItemStack(Material.IRON_CHESTPLATE);
        stack.editMeta(meta -> meta.itemName(Text.plain("<gray>방탄조끼")));
        return stack;
    }

    private void add(int slot, String name, Supplier<ItemStack> supplier, String priceId, long defaultPrice, int bulk) {
        put(slot, name, supplier.get(), List.of(), p -> supplier.get(), priceId, defaultPrice, bulk);
    }

    private void put(int slot, String name, ItemStack display, List<Component> info, Function<Player, ItemStack> product,
                     String priceId, long defaultPrice, int bulk) {
        long price = plugin.settings().price(priceId, defaultPrice);
        entries.put(slot, new Entry(name, product, price, bulk));
        display.editMeta(meta -> {
            List<Component> lore = new ArrayList<>(info);
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
                lore.add(Component.empty());
            }
            lore.add(Text.plain("<gold>가격:" + plugin.settings().money(price)));
            lore.add(Text.plain("<yellow>클릭</yellow><gray>: 1개 구매"));
            if (bulk > 1) {
                lore.add(Text.plain("<yellow>Shift+클릭</yellow><gray>: " + bulk + "개 구매"));
            }
            meta.lore(lore);
        });
        inventory.setItem(slot, display);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        Entry entry = entries.get(slot);
        if (entry == null) {
            return;
        }
        int times = click.isShiftClick() ? entry.bulk() : 1;
        long total = entry.price() * times;
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            return;
        }
        CharacterData.PayResult result = data.pay(total);
        if (result == CharacterData.PayResult.INSUFFICIENT) {
            Text.send(player, "<red>돈이 부족해요! <gray>(" + plugin.settings().money(total) + " 필요)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        for (int i = 0; i < times; i++) {
            Economy.give(player, entry.product().apply(player));
        }
        String how = result == CharacterData.PayResult.CASH ? "현금" : "카드";
        Text.send(player, "<green>" + entry.name() + (times > 1 ? " x" + times : "") + " 구매 완료! <gray>("
                + how + " 결제 " + plugin.settings().money(total) + ")");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }
}
