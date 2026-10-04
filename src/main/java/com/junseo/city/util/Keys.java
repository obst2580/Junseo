package com.junseo.city.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** 아이템/엔티티에 붙이는 숨은 데이터(PersistentDataContainer)의 키 모음. */
public final class Keys {
    /** 아이템 종류 (pistol, ammo, car_key ...). */
    public static NamespacedKey ITEM;
    /** 총 한 자루마다 고유한 id. */
    public static NamespacedKey GUN_ID;
    /** 총에 남은 탄창 수. */
    public static NamespacedKey GUN_AMMO;
    /** 자동차 열쇠 고유 id. */
    public static NamespacedKey KEY_ID;
    public static NamespacedKey CAR_TYPE;
    public static NamespacedKey OWNER;
    /** 자동차를 이루는 엔티티 표시. */
    public static NamespacedKey CAR;
    /** 상점 NPC 종류. */
    public static NamespacedKey NPC;
    /** AI 경찰 표시. */
    public static NamespacedKey POLICE;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ITEM = new NamespacedKey(plugin, "item");
        GUN_ID = new NamespacedKey(plugin, "gun_id");
        GUN_AMMO = new NamespacedKey(plugin, "gun_ammo");
        KEY_ID = new NamespacedKey(plugin, "key_id");
        CAR_TYPE = new NamespacedKey(plugin, "car_type");
        OWNER = new NamespacedKey(plugin, "owner");
        CAR = new NamespacedKey(plugin, "car");
        NPC = new NamespacedKey(plugin, "npc");
        POLICE = new NamespacedKey(plugin, "police");
    }
}
