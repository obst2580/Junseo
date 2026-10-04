package com.junseo.city.crime;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.Crime;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Text;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** 누가 누구를 때렸는지 보고 범죄로 처리합니다. 수갑 체포도 여기서. */
public final class CrimeListener implements Listener {
    private final JunseoCity plugin;

    public CrimeListener(JunseoCity plugin) {
        this.plugin = plugin;
    }

    /** 공격한 플레이어 (직접 때렸거나 화살을 쏜 사람). 없으면 null. */
    public static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHandcuffs(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player cop)
                || !CustomItems.is(cop.getInventory().getItemInMainHand(), CustomItems.HANDCUFFS)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player suspect)) {
            return;
        }
        if (plugin.data().get(cop).job() != Job.POLICE) {
            Text.send(cop, "<red>수갑은 경찰만 쓸 수 있어요.");
            return;
        }
        PlayerData data = plugin.data().get(suspect);
        if (data.wanted() <= 0) {
            Text.send(cop, "<yellow>" + suspect.getName() + " 님은 수배자가 아니에요.");
            return;
        }
        plugin.jail().arrest(suspect, cop);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim instanceof Player target) {
            if (target.equals(attacker)) {
                return;
            }
            PlayerData data = plugin.data().get(target);
            if (data.wanted() > 0) {
                return; // 수배자를 공격하는 건 범죄가 아니에요.
            }
            plugin.wanted().commit(attacker, data.job() == Job.POLICE ? Crime.COP_ASSAULT : Crime.ASSAULT);
        } else if (plugin.police().isCop(victim)) {
            plugin.wanted().commit(attacker, Crime.COP_ASSAULT);
        }
    }
}
