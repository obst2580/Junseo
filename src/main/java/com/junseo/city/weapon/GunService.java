package com.junseo.city.weapon;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Job;
import com.junseo.city.phone.DispatchService;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Keys;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 총 쏘기 (우클릭), 재장전 (F키). 총알은 눈에서 바라보는 방향으로 즉시 날아가는 방식(히트스캔)입니다.
 * 탄창에 남은 총알 수는 메모리에 두었다가 장전할 때와 나갈 때 아이템에 저장합니다.
 * (쏠 때마다 아이템을 바꾸면 손에 든 총이 계속 흔들려서요.)
 */
public final class GunService implements Listener {
    private final JunseoCity plugin;
    private final Map<UUID, Integer> mags = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastShotMs = new ConcurrentHashMap<>();
    /** 재장전 중인 사람 → 재장전 번호 (중간에 무기를 바꾸면 취소). */
    private final Map<UUID, Long> reloading = new ConcurrentHashMap<>();
    private long reloadSeq;

    public GunService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        GunType type = CustomItems.gunType(hand);
        if (type == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getClickedBlock() != null
                && plugin.places().atBlock(event.getClickedBlock(), PlaceType.STORE_SAFE, PlaceType.BANK_VAULT) != null) {
            return; // 금고를 총으로 우클릭 = 강도 시작 (RobberyService 가 처리)
        }
        shoot(player, hand, type);
    }

    /** F 키 = 재장전. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        GunType type = CustomItems.gunType(hand);
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        startReload(player, hand, type);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        if (reloading.remove(event.getPlayer().getUniqueId()) != null) {
            event.getPlayer().sendActionBar(Text.mm("<gray>장전 취소"));
        }
    }

    private void shoot(Player player, ItemStack hand, GunType type) {
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            return;
        }
        if (data.isJailed()) {
            player.sendActionBar(Text.mm("<red>감옥에서는 총을 쏠 수 없어요"));
            return;
        }
        if (type.policeOnly() && data.job() != Job.POLICE) {
            player.sendActionBar(Text.mm("<red>" + type.displayName() + "은(는) 경찰만 쓸 수 있어요"));
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastShotMs.getOrDefault(uuid, 0L) < type.fireDelayTicks() * 50L - 10 || reloading.containsKey(uuid)) {
            return;
        }
        UUID gunId = CustomItems.uuid(hand, Keys.GUN_ID);
        if (gunId == null) {
            return;
        }
        int rounds = rounds(gunId, hand, type);
        if (rounds <= 0) {
            if (!type.usesAmmo() || countAmmo(player) > 0) {
                startReload(player, hand, type);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1.5f);
                player.sendActionBar(Text.mm("<red>총알이 없어요! 총포상에서 살 수 있어요"));
            }
            return;
        }
        mags.put(gunId, --rounds);
        lastShotMs.put(uuid, now);
        fire(player, type);
        if (type != GunType.TASER) {
            plugin.dispatch().automatic(DispatchService.Line.POLICE, "shots", "총소리 신고", player.getLocation());
        }
        showAmmo(player, type, rounds);
        if (rounds == 0 && (!type.usesAmmo() || countAmmo(player) > 0)) {
            startReload(player, hand, type);
        }
    }

    private void fire(Player shooter, GunType type) {
        Location eye = shooter.getEyeLocation();
        World world = shooter.getWorld();
        Vector dir = eye.getDirection();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < type.pellets(); i++) {
            Vector d = dir.clone();
            if (type.spread() > 0) {
                d.add(new Vector(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).multiply(type.spread()));
            }
            d.normalize();
            RayTraceResult hit = world.rayTrace(eye, d, type.range(), FluidCollisionMode.NEVER, true, 0.2,
                    e -> canHit(shooter, e));
            Vector end = hit != null ? hit.getHitPosition() : eye.toVector().add(d.clone().multiply(type.range()));
            trail(world, eye.toVector(), end, type);
            if (hit == null) {
                continue;
            }
            if (hit.getHitEntity() instanceof LivingEntity target) {
                hitEntity(shooter, target, hit.getHitPosition(), type);
            } else if (hit.getHitBlock() != null) {
                Block block = hit.getHitBlock();
                world.spawnParticle(Particle.BLOCK, end.getX(), end.getY(), end.getZ(), 6, 0.05, 0.05, 0.05, 0,
                        block.getBlockData());
            }
        }
        playShotSound(world, eye, type);
    }

    private boolean canHit(Player shooter, Entity entity) {
        return entity instanceof LivingEntity
                && entity != shooter
                && !entity.isDead()
                && !(entity instanceof ArmorStand)
                && !plugin.cars().isCarPart(entity)
                && !shooter.getPassengers().contains(entity);
    }

    private void hitEntity(Player shooter, LivingEntity target, Vector hitPos, GunType type) {
        World world = target.getWorld();
        if (type == GunType.TASER) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 6));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 2));
            world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
            world.playSound(target.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1f, 1.5f);
            if (target instanceof Player victim) {
                victim.sendActionBar(Text.mm("<yellow>테이저에 맞았어요! 3초 동안 못 움직여요"));
            }
            return;
        }
        double damage = type.damage();
        boolean headshot = target.getHeight() > 1.0 && hitPos.getY() >= target.getEyeLocation().getY() - 0.25;
        if (headshot) {
            damage *= 1.5;
            shooter.sendActionBar(Text.mm("<red><bold>헤드샷!"));
        }
        target.setNoDamageTicks(0);
        target.damage(damage, shooter);
        world.spawnParticle(Particle.CRIT, hitPos.getX(), hitPos.getY(), hitPos.getZ(), 8, 0.1, 0.1, 0.1, 0.2);
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.4f, 1.6f);
    }

    private static void trail(World world, Vector from, Vector to, GunType type) {
        Vector step = to.clone().subtract(from);
        double length = step.length();
        if (length < 1) {
            return;
        }
        step.normalize();
        Particle particle = type == GunType.TASER ? Particle.ELECTRIC_SPARK : Particle.CRIT;
        for (double t = 1.0; t < length; t += 1.0) {
            Vector p = from.clone().add(step.clone().multiply(t));
            world.spawnParticle(particle, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private static void playShotSound(World world, Location at, GunType type) {
        switch (type) {
            case PISTOL -> world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.35f, 2.0f);
            case SHOTGUN -> world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 1.3f);
            case RIFLE -> world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.4f, 1.7f);
            case TASER -> world.playSound(at, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.PLAYERS, 1f, 2.0f);
        }
        if (type != GunType.TASER) {
            world.playSound(at, Sound.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 0.6f, 1.8f);
        }
    }

    private void startReload(Player player, ItemStack hand, GunType type) {
        UUID uuid = player.getUniqueId();
        if (reloading.containsKey(uuid)) {
            return;
        }
        UUID gunId = CustomItems.uuid(hand, Keys.GUN_ID);
        if (gunId == null) {
            return;
        }
        int current = rounds(gunId, hand, type);
        if (current >= type.magazine()) {
            player.sendActionBar(Text.mm("<gray>탄창이 가득 찼어요"));
            return;
        }
        if (type.usesAmmo() && countAmmo(player) == 0) {
            player.sendActionBar(Text.mm("<red>총알이 없어요! 총포상에서 살 수 있어요"));
            return;
        }
        player.sendActionBar(Text.mm("<yellow>장전 중..."));
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 1f, 1f);
        long seq;
        synchronized (this) {
            seq = ++reloadSeq;
        }
        reloading.put(uuid, seq);
        Sched.entityLater(player, type.reloadTicks(), () -> {
            if (!reloading.remove(uuid, seq)) {
                return; // 취소됨
            }
            ItemStack now = player.getInventory().getItemInMainHand();
            if (!gunId.equals(CustomItems.uuid(now, Keys.GUN_ID))) {
                return;
            }
            int have = rounds(gunId, now, type);
            int need = type.magazine() - have;
            int got = type.usesAmmo() ? takeAmmo(player, need) : need;
            int total = have + got;
            mags.put(gunId, total);
            now.editPersistentDataContainer(pdc -> pdc.set(Keys.GUN_AMMO, PersistentDataType.INTEGER, total));
            player.getInventory().setItemInMainHand(now);
            player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.2f);
            showAmmo(player, type, total);
        });
    }

    private int rounds(UUID gunId, ItemStack stack, GunType type) {
        return mags.computeIfAbsent(gunId, id -> stack.getPersistentDataContainer()
                .getOrDefault(Keys.GUN_AMMO, PersistentDataType.INTEGER, type.magazine()));
    }

    private void showAmmo(Player player, GunType type, int rounds) {
        String spare = type.usesAmmo() ? "  <dark_gray>|</dark_gray>  <gray>총알 <white>" + countAmmo(player) : "";
        player.sendActionBar(Text.mm("<gold>" + type.displayName() + " <white>" + rounds + "</white><gray>/" + type.magazine() + spare));
    }

    private static int countAmmo(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (CustomItems.is(stack, CustomItems.AMMO)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static int takeAmmo(Player player, int wanted) {
        PlayerInventory inv = player.getInventory();
        int taken = 0;
        for (int i = 0; i < inv.getSize() && taken < wanted; i++) {
            ItemStack stack = inv.getItem(i);
            if (!CustomItems.is(stack, CustomItems.AMMO)) {
                continue;
            }
            int use = Math.min(wanted - taken, stack.getAmount());
            stack.setAmount(stack.getAmount() - use);
            inv.setItem(i, stack.getAmount() <= 0 ? null : stack);
            taken += use;
        }
        return taken;
    }

    /** 메모리에 있는 탄창 수를 아이템에 저장합니다. */
    public void writeBack(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            UUID gunId = CustomItems.uuid(stack, Keys.GUN_ID);
            Integer rounds = gunId == null ? null : mags.get(gunId);
            if (rounds != null) {
                stack.editPersistentDataContainer(pdc -> pdc.set(Keys.GUN_AMMO, PersistentDataType.INTEGER, rounds));
                inv.setItem(i, stack);
            }
        }
    }

    /** 서버 종료 때. Folia 에서는 다른 스레드의 인벤토리를 못 만질 수 있어서 실패해도 넘어갑니다. */
    public void writeBackAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                writeBack(player);
            } catch (RuntimeException ignored) {
                // Folia: 나갈 때(onQuit) 이미 저장됨
            }
        }
    }

    /** 나갈 때 (플레이어 스레드). */
    public void onQuit(Player player) {
        reloading.remove(player.getUniqueId());
        lastShotMs.remove(player.getUniqueId());
        writeBack(player);
    }
}
