package com.junseo.city.npc;

import org.bukkit.entity.Villager;

import java.util.Locale;
import java.util.function.Supplier;

/** 관리자가 세우는 NPC 종류. 우클릭하면 각자 메뉴가 열립니다. */
public enum NpcType {
    CONVENIENCE("convenience", "편의점", () -> Villager.Profession.FARMER),
    GUN_STORE("gunstore", "총포상", () -> Villager.Profession.WEAPONSMITH),
    CAR_DEALER("cardealer", "자동차 대리점", () -> Villager.Profession.TOOLSMITH),
    MINERAL("mineral", "광물 거래소", () -> Villager.Profession.MASON),
    JOB_CENTER("jobcenter", "직업 소개소", () -> Villager.Profession.LIBRARIAN),
    DELIVERY_DEPOT("depot", "택배 물류센터", () -> Villager.Profession.SHEPHERD),
    BANKER("banker", "은행원", () -> Villager.Profession.CARTOGRAPHER),
    CITY_HALL("cityhall", "시청 민원실", () -> Villager.Profession.CLERIC);

    private final String id;
    private final String displayName;
    private final Supplier<Villager.Profession> profession;

    NpcType(String id, String displayName, Supplier<Villager.Profession> profession) {
        this.id = id;
        this.displayName = displayName;
        this.profession = profession;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Villager.Profession profession() {
        return profession.get();
    }

    public static NpcType byId(String id) {
        if (id == null) {
            return null;
        }
        for (NpcType type : values()) {
            if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return null;
    }
}
