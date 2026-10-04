package com.junseo.city.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** 상자 모양 메뉴(GUI). 아이템을 꺼낼 수 없고 클릭만 받습니다. */
public abstract class Menu implements InventoryHolder {
    protected final Inventory inventory;

    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** slot 은 메뉴 칸 번호 (0부터). */
    public abstract void onClick(Player player, int slot, ClickType click);
}
