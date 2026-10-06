package com.junseo.city.job;

import com.junseo.city.JunseoCity;
import com.junseo.city.economy.Economy;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Job;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Sched;
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

    /** 플레이어 스레드에서 호출. */
    public void setJob(Player player, Job job, int grade) {
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            return;
        }
        Job old = data.job();
        data.setJob(job, grade);
        if (old == Job.POLICE && job != Job.POLICE) {
            removePoliceGear(player);
        }
        if (old == Job.DELIVERY && job != Job.DELIVERY) {
            plugin.delivery().cancel(player, null);
        }
        if (job == Job.POLICE) {
            givePoliceKit(player);
        }
        if (old != job) {
            // 업무용 차 열쇠: 의료국 구급차, 택배기사 택배 트럭 (그만두면 회수)
            for (Job j : new Job[]{Job.EMS, Job.DELIVERY}) {
                CarType car = j == Job.EMS ? CarType.AMBULANCE : CarType.DELIVERY;
                if (old == j) {
                    takeJobKey(player, car);
                }
                if (job == j) {
                    Economy.give(player, CustomItems.carKey(car, player.getUniqueId(), player.getName()));
                }
            }
        }
        if (old != job) {
            player.showTitle(Title.title(Text.mm("<gold>" + job.displayName()), Text.mm("<gray>새 직업을 시작했어요!"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
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

    private void takeJobKey(Player player, CarType car) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (CustomItems.is(stack, CustomItems.CAR_KEY) && car.id().equals(CustomItems.string(stack, com.junseo.city.util.Keys.CAR_TYPE))) {
                inv.setItem(i, null);
            }
        }
        plugin.cars().despawnOwned(player.getUniqueId(), car);
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

    /** 월급날 (전역 스케줄러): 접속 중인 모든 사람에게 직업별 월급을 은행으로. */
    public void payday() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            CharacterData data = plugin.characters().get(player);
            if (data == null) {
                continue;
            }
            if (data.isJailed()) {
                Sched.entity(player, () -> Text.send(player, "<gray>감옥에 있어서 이번 월급은 없어요."));
                continue;
            }
            long salary = plugin.settings().salary(data.job());
            if (salary <= 0) {
                continue;
            }
            data.addBank(salary);
            plugin.characters().logMoney(data, salary, "bank", "월급", data.job().key());
            Sched.entity(player, () -> {
                Text.send(player, "<gold>월급날! <white>" + data.job().displayName() + "</white> 월급 <green>"
                        + plugin.settings().money(salary) + "</green>이 은행에 들어왔어요.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
            });
        }
    }
}
