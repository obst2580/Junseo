package com.junseo.city.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Folia 호환 스케줄러 모음.
 * Folia 에는 "메인 스레드"가 없어서, 일을 그 일이 다루는 대상(엔티티·위치)의 지역 스레드에서 실행해야 합니다.
 * Paper 에서는 전부 메인 스레드에서 실행되므로 같은 코드가 양쪽에서 동작합니다.
 */
public final class Sched {
    private static Plugin plugin;

    private Sched() {
    }

    public static void init(Plugin owner) {
        plugin = owner;
    }

    /** 엔티티(플레이어)를 다루는 일. 엔티티가 사라졌으면 실행하지 않습니다. */
    public static void entity(Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, t -> task.run(), null);
    }

    public static void entityLater(Entity entity, long delayTicks, Runnable task) {
        entity.getScheduler().runDelayed(plugin, t -> task.run(), null, Math.max(1, delayTicks));
    }

    /** 엔티티에 붙어 반복하는 일. 엔티티가 사라지면 자동으로 멈춥니다. */
    public static ScheduledTask entityRepeat(Entity entity, long delayTicks, long periodTicks, Consumer<ScheduledTask> task) {
        return entity.getScheduler().runAtFixedRate(plugin, task, null, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    /** 특정 위치(청크)를 다루는 일. */
    public static void region(Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
    }

    public static ScheduledTask regionRepeat(Location location, long delayTicks, long periodTicks, Consumer<ScheduledTask> task) {
        return Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    /** 특정 위치·엔티티와 상관없는 서버 전체 일 (월급 등). */
    public static ScheduledTask globalRepeat(long delayTicks, long periodTicks, Consumer<ScheduledTask> task) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public static void global(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }
}
