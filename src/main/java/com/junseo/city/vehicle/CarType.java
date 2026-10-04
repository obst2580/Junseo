package com.junseo.city.vehicle;

import org.bukkit.Material;

import java.util.Locale;

/** 차 종류와 성능. 속도 단위는 블록/틱 (1.0 = 시속 72km). */
public enum CarType {
    COMPACT("compact", "경차", 0.75, 0.018, 5.0, Material.LIGHT_BLUE_CONCRETE),
    SEDAN("sedan", "세단", 1.0, 0.022, 4.2, Material.WHITE_CONCRETE),
    SPORTS("sports", "스포츠카", 1.4, 0.032, 3.8, Material.RED_CONCRETE),
    POLICE("police", "경찰차", 1.2, 0.028, 4.2, Material.BLUE_CONCRETE);

    private final String id;
    private final String displayName;
    private final double maxSpeed;
    private final double acceleration;
    private final double turnDegrees;
    private final Material bodyColor;

    CarType(String id, String displayName, double maxSpeed, double acceleration, double turnDegrees, Material bodyColor) {
        this.id = id;
        this.displayName = displayName;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.turnDegrees = turnDegrees;
        this.bodyColor = bodyColor;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public double acceleration() {
        return acceleration;
    }

    public double turnDegrees() {
        return turnDegrees;
    }

    public Material bodyColor() {
        return bodyColor;
    }

    public static CarType byId(String id) {
        if (id == null) {
            return null;
        }
        for (CarType type : values()) {
            if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return null;
    }
}
