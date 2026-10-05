package com.junseo.city.vehicle;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * 소환된 자동차 한 대.
 * 보이지 않는 작은 갑옷 거치대(base)가 실제로 움직이고, 그 위에 차체(블록 디스플레이), 클릭 판정(상호작용 엔티티),
 * 운전자가 탑니다. 운전 계산은 이 차가 있는 지역의 스레드에서만 합니다 (Folia 호환).
 */
public final class Car {
    final CarType type;
    final UUID owner;
    final UUID keyId;
    final ArmorStand base;
    final Interaction hitbox;
    final List<Display> parts;
    /** 3D 모델 차의 바퀴 (앞왼, 앞오, 뒤왼, 뒤오). 블록 모양 차는 빈 목록 */
    final List<ItemDisplay> wheels;
    /** 번호판 글자 */
    final String plate;

    double speed;
    float yaw;
    Location lastLocation;
    boolean siren;
    boolean prevSprint;
    long ticks;
    long enteredTick;
    int crashCooldown;
    /** 바퀴 굴림 각 (라디안), 앞바퀴 꺾임 (도) */
    double spin;
    float steer;
    /** 부품이 타는 자리의 높이 (받침 발밑 기준) */
    float attachY;
    ScheduledTask task;
    /** 다른 스레드(폰 차고 앱 등)에서 읽는 위치. 차 스레드가 매 틱 갱신합니다. */
    volatile Location lastKnown;
    volatile boolean removed;

    Car(CarType type, UUID owner, UUID keyId, ArmorStand base, Interaction hitbox, List<Display> parts, List<ItemDisplay> wheels,
        String plate, float yaw) {
        this.type = type;
        this.owner = owner;
        this.keyId = keyId;
        this.base = base;
        this.hitbox = hitbox;
        this.parts = parts;
        this.wheels = wheels;
        this.plate = plate;
        this.yaw = yaw;
        this.lastKnown = base.getLocation();
    }

    public CarType type() {
        return type;
    }

    public UUID owner() {
        return owner;
    }

    /** 번호판 (예: 12가 3456) */
    public String plate() {
        return plate;
    }

    /** 다른 스레드에서도 안전하게 읽을 수 있는 최근 위치. */
    public Location lastKnownLocation() {
        Location loc = lastKnown;
        return loc == null ? null : loc.clone();
    }

    /** 차 스레드에서만 호출. */
    Player driver() {
        for (Entity passenger : base.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
