package com.junseo.city.job;

import com.junseo.city.JunseoCity;
import com.junseo.city.economy.Economy;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Text;
import com.junseo.city.vehicle.CarType;
import com.junseo.city.weapon.GunType;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/** 직업 바꾸기, 경찰 장비, 월급. */
public final class JobService {
    private final JunseoCity plugin;

    public JobService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    public void setJob(Player player, Job job) {
        PlayerData data = plugin.data().get(player);
        Job old = data.job();
        data.setJob(job);
        if (old == Job.POLICE && job != Job.POLICE) {
            removePoliceGear(player);
        }
        if (old == Job.DELIVERY && job != Job.DELIVERY) {
            plugin.delivery().cancel(player, null);
        }
        if (job == Job.POLICE) {
            givePoliceKit(player);
        }
        player.showTitle(Title.title(Text.mm("<gold>" + job.displayName()), Text.mm("<gray>새 직업을 시작했어요!"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        Text.send(player, "<green>직업이 <white>" + job.displayName() + "</white>(으)로 바뀌었어요!");
        switch (job) {
            case POLICE -> Text.send(player, "<aqua>수배자를 <white>수갑</white>으로 때리면 체포! <white>/수배자</white> 로 위치를 볼 수 있어요. "
                    + "경찰차 열쇠로 땅을 우클릭하면 경찰차가 나와요.");
            case DELIVERY -> Text.send(player, "<aqua>택배 물류센터 NPC를 우클릭하거나 <white>/택배 시작</white> 으로 배달을 시작하세요.");
            case MINER -> Text.send(player, "<aqua>광물을 캐서 광물 거래소에 팔면 <white>x" + plugin.settings().minerSellBonus + "</white> 가격으로 팔 수 있어요.");
            case CITIZEN -> Text.send(player, "<aqua>자유롭게 시티를 즐기세요!");
        }
    }

    /** 경찰 장비 중 없는 것만 지급합니다. */
    public void givePoliceKit(Player player) {
        Set<String> have = new HashSet<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (CustomItems.isPoliceGear(stack)) {
                have.add(CustomItems.id(stack));
            }
        }
        give(player, have, CustomItems.handcuffs());
        give(player, have, CustomItems.gun(GunType.TASER));
        give(player, have, CustomItems.gun(GunType.PISTOL));
        give(player, have, CustomItems.carKey(CarType.POLICE, player.getUniqueId(), player.getName()));
        if (!have.contains(CustomItems.AMMO)) {
            Economy.give(player, CustomItems.markPoliceGear(CustomItems.ammo(36)));
        }
    }

    private static void give(Player player, Set<String> have, ItemStack stack) {
        if (!have.contains(CustomItems.id(stack))) {
            Economy.give(player, CustomItems.markPoliceGear(stack));
        }
    }

    public void removePoliceGear(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (CustomItems.isPoliceGear(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        plugin.cars().despawnOwned(player.getUniqueId(), CarType.POLICE);
    }

    /** 월급날: 접속 중인 모든 사람에게 직업별 월급을 은행으로. */
    public void payday() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.data().get(player);
            if (data.isJailed()) {
                Text.send(player, "<gray>감옥에 있어서 이번 월급은 없어요.");
                continue;
            }
            long salary = plugin.settings().salary(data.job());
            if (salary <= 0) {
                continue;
            }
            data.addBank(salary);
            Text.send(player, "<gold>월급날! <white>" + data.job().displayName() + "</white> 월급 <green>"
                    + plugin.settings().money(salary) + "</green>이 은행에 들어왔어요.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
        }
    }
}
