package com.junseo.city.shop;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.menu.Menu;
import com.junseo.city.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** 직업 소개소 메뉴. */
public final class JobMenu extends Menu {
    private final JunseoCity plugin;
    private final Map<Integer, Job> slots = new HashMap<>();

    public JobMenu(JunseoCity plugin) {
        super(3, Text.mm("<dark_gray>직업 소개소"));
        this.plugin = plugin;
        put(10, Job.CITIZEN, Material.PLAYER_HEAD);
        put(12, Job.POLICE, Material.IRON_HELMET);
        put(14, Job.DELIVERY, Material.CHEST);
        put(16, Job.MINER, Material.IRON_PICKAXE);
    }

    private void put(int slot, Job job, Material icon) {
        ItemStack stack = new ItemStack(icon);
        stack.editMeta(meta -> {
            meta.itemName(Text.plain("<gold><bold>" + job.displayName()));
            meta.lore(Text.lore(
                    job.description(),
                    "월급: <green>" + plugin.settings().money(plugin.settings().salary(job)) + "</green> / "
                            + plugin.settings().paycheckMinutes + "분",
                    "",
                    "<yellow>클릭해서 이 직업 하기"));
        });
        inventory.setItem(slot, stack);
        slots.put(slot, job);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        Job job = slots.get(slot);
        if (job == null) {
            return;
        }
        PlayerData data = plugin.data().get(player);
        if (data.job() == job) {
            Text.send(player, "<yellow>이미 하고 있는 직업이에요: " + job.displayName());
            return;
        }
        player.closeInventory();
        plugin.jobs().setJob(player, job);
    }
}
