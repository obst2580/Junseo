package com.junseo.city.crime;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.Crime;
import com.junseo.city.logic.WantedRules;
import com.junseo.city.util.Keys;
import com.junseo.city.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 경찰. 수배자 주변에 별 개수만큼 경찰(변명자=경찰봉, 약탈자=특공대)이 출동해서 쫓아옵니다.
 * 저장되지 않는 엔티티라서 서버를 껐다 켜면 사라집니다.
 */
public final class PoliceForce implements Listener {
    private static final double LOSE_TRACK_RANGE = 64;
    private static final long SPAWN_INTERVAL_MS = 2000;

    private final JunseoCity plugin;
    private final Map<UUID, List<Mob>> squads = new HashMap<>();
    private final Map<UUID, Long> lastSpawn = new HashMap<>();

    public PoliceForce(JunseoCity plugin) {
        this.plugin = plugin;
    }

    public void update(Player player, int stars, boolean seen) {
        List<Mob> squad = squads.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        Location loc = player.getLocation();
        for (Iterator<Mob> it = squad.iterator(); it.hasNext(); ) {
            Mob cop = it.next();
            if (!cop.isValid()) {
                it.remove();
            } else if (cop.getWorld() != loc.getWorld() || cop.getLocation().distanceSquared(loc) > LOSE_TRACK_RANGE * LOSE_TRACK_RANGE) {
                cop.remove();
                it.remove();
            }
        }
        int desired = WantedRules.policeCount(stars, plugin.settings().policePerStar);
        while (squad.size() > desired) {
            squad.remove(squad.size() - 1).remove();
        }
        long now = System.currentTimeMillis();
        if (squad.size() < desired && seen && now - lastSpawn.getOrDefault(player.getUniqueId(), 0L) >= SPAWN_INTERVAL_MS) {
            boolean swat = stars >= 3 && squad.size() % 2 == 1;
            Mob cop = spawnCop(player, swat);
            if (cop != null) {
                squad.add(cop);
                lastSpawn.put(player.getUniqueId(), now);
            }
        }
        for (Mob cop : squad) {
            if (cop.getTarget() != player) {
                cop.setTarget(player);
            }
        }
    }

    private Mob spawnCop(Player target, boolean swat) {
        Location spawn = findSpawn(target.getLocation());
        if (spawn == null) {
            return null;
        }
        Class<? extends Mob> type = swat ? Pillager.class : Vindicator.class;
        Mob cop = spawn.getWorld().spawn(spawn, type, mob -> {
            mob.customName(Text.plain(swat ? "<dark_blue><bold>특공대" : "<blue><bold>경찰"));
            mob.setCustomNameVisible(true);
            mob.setPersistent(false);
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            if (mob instanceof Raider raider) {
                raider.setCanJoinRaid(false);
                raider.setPatrolLeader(false);
            }
            EntityEquipment eq = mob.getEquipment();
            if (!swat) {
                ItemStack baton = new ItemStack(Material.STICK);
                baton.editMeta(meta -> meta.itemName(Text.plain("경찰봉")));
                eq.setItemInMainHand(baton);
            }
            eq.setItemInMainHandDropChance(0f);
            eq.setHelmetDropChance(0f);
            mob.getPersistentDataContainer().set(Keys.POLICE, PersistentDataType.STRING, target.getUniqueId().toString());
        });
        cop.setTarget(target);
        spawn.getWorld().playSound(spawn, Sound.ITEM_GOAT_HORN_SOUND_1, 2f, 1.4f);
        return cop;
    }

    private static Location findSpawn(Location around) {
        World world = around.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = r.nextDouble(Math.PI * 2);
            double dist = r.nextDouble(16, 26);
            int x = (int) Math.floor(around.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(around.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z);
            if (Math.abs(y - around.getBlockY()) > 12) {
                continue;
            }
            Block ground = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid()) {
                continue;
            }
            Block feet = ground.getRelative(0, 1, 0);
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && !feet.isLiquid()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    public void dismiss(Player player) {
        List<Mob> squad = squads.remove(player.getUniqueId());
        if (squad == null) {
            return;
        }
        for (Mob cop : squad) {
            if (cop.isValid()) {
                cop.getWorld().spawnParticle(Particle.POOF, cop.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
                cop.remove();
            }
        }
    }

    public void removeAll() {
        for (List<Mob> squad : squads.values()) {
            squad.forEach(Entity::remove);
        }
        squads.clear();
    }

    public boolean isCop(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(Keys.POLICE, PersistentDataType.STRING);
    }

    public boolean hasCopNear(Player player, double range) {
        List<Mob> squad = squads.get(player.getUniqueId());
        if (squad == null) {
            return false;
        }
        Location loc = player.getLocation();
        for (Mob cop : squad) {
            if (cop.isValid() && cop.getWorld() == loc.getWorld() && cop.getLocation().distanceSquared(loc) <= range * range) {
                return true;
            }
        }
        return false;
    }

    /** 경찰은 맡은 수배자만 노립니다 (주민이나 다른 사람은 공격 안 함). */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!isCop(event.getEntity()) || event.getTarget() == null) {
            return;
        }
        String assigned = event.getEntity().getPersistentDataContainer().get(Keys.POLICE, PersistentDataType.STRING);
        if (!event.getTarget().getUniqueId().toString().equals(assigned)) {
            event.setCancelled(true);
        }
    }

    /** 경찰끼리 (특공대 화살 등) 다치지 않게. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (isCop(damager) && isCop(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!isCop(dead)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = dead.getKiller();
        if (killer != null) {
            plugin.wanted().commit(killer, Crime.COP_KILL);
        }
    }

    /** 서버가 갑자기 꺼져서 남은 경찰 정리. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isCop(entity)) {
                entity.remove();
            }
        }
    }
}
