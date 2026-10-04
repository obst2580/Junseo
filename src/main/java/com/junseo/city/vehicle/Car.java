package com.junseo.city.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * 소환된 자동차 한 대.
 * 보이지 않는 작은 갑옷 거치대(base)가 실제로 움직이고, 그 위에 블록 디스플레이(차체)와 운전자가 탑니다.
 * 클릭은 차 크기만 한 상호작용 엔티티(hitbox)로 받습니다.
 */
public final class Car {
    final UUID id = UUID.randomUUID();
    final CarType type;
    final UUID owner;
    final UUID keyId;
    final ArmorStand base;
    final Interaction hitbox;
    final List<BlockDisplay> parts;

    double speed;
    float yaw;
    Location lastLocation;
    boolean siren;
    boolean prevSprint;
    int enteredTick;
    int crashCooldown;

    Car(CarType type, UUID owner, UUID keyId, ArmorStand base, Interaction hitbox, List<BlockDisplay> parts, float yaw) {
        this.type = type;
        this.owner = owner;
        this.keyId = keyId;
        this.base = base;
        this.hitbox = hitbox;
        this.parts = parts;
        this.yaw = yaw;
    }

    public CarType type() {
        return type;
    }

    public UUID owner() {
        return owner;
    }

    public Player driver() {
        for (Entity passenger : base.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    public Location location() {
        return base.getLocation();
    }
}
