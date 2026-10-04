package com.junseo.city.weapon;

import java.util.Locale;

/** 총 종류와 성능. 데미지 1 = 하트 반 칸. */
public enum GunType {
    //        id         이름        데미지 산탄 연사틱 탄창 장전틱 사거리 퍼짐    겉모양                       경찰전용
    PISTOL("pistol", "권총", 5.0, 1, 6, 12, 30, 60, 0.015, "minecraft:iron_hoe", false),
    SHOTGUN("shotgun", "산탄총", 3.0, 8, 18, 6, 50, 24, 0.09, "minecraft:crossbow", false),
    RIFLE("rifle", "소총", 6.0, 1, 4, 30, 45, 90, 0.02, "minecraft:netherite_hoe", false),
    TASER("taser", "테이저건", 0.0, 1, 30, 1, 40, 16, 0.0, "minecraft:blaze_rod", true);

    private final String id;
    private final String displayName;
    private final double damage;
    private final int pellets;
    private final int fireDelayTicks;
    private final int magazine;
    private final int reloadTicks;
    private final double range;
    private final double spread;
    private final String model;
    private final boolean policeOnly;

    GunType(String id, String displayName, double damage, int pellets, int fireDelayTicks, int magazine,
            int reloadTicks, double range, double spread, String model, boolean policeOnly) {
        this.id = id;
        this.displayName = displayName;
        this.damage = damage;
        this.pellets = pellets;
        this.fireDelayTicks = fireDelayTicks;
        this.magazine = magazine;
        this.reloadTicks = reloadTicks;
        this.range = range;
        this.spread = spread;
        this.model = model;
        this.policeOnly = policeOnly;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public double damage() {
        return damage;
    }

    public int pellets() {
        return pellets;
    }

    public int fireDelayTicks() {
        return fireDelayTicks;
    }

    public int magazine() {
        return magazine;
    }

    public int reloadTicks() {
        return reloadTicks;
    }

    public double range() {
        return range;
    }

    public double spread() {
        return spread;
    }

    public String model() {
        return model;
    }

    public boolean policeOnly() {
        return policeOnly;
    }

    /** 테이저건은 총알 없이 다시 장전됩니다. */
    public boolean usesAmmo() {
        return this != TASER;
    }

    public static GunType byId(String id) {
        if (id == null) {
            return null;
        }
        for (GunType type : values()) {
            if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return null;
    }
}
