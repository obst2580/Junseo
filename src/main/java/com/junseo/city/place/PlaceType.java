package com.junseo.city.place;

import java.util.Locale;

/** 관리자가 지정하는 시티의 장소 종류. */
public enum PlaceType {
    SPAWN("spawn", "시티 스폰", true, false),
    HOSPITAL("hospital", "병원", true, false),
    JAIL("jail", "감옥", true, false),
    JAIL_EXIT("jail_exit", "출소 장소", true, false),
    ATM("atm", "ATM", false, true),
    DELIVERY_POINT("delivery_point", "택배 배달지", false, false),
    STORE_SAFE("store_safe", "편의점 금고", false, true),
    BANK_VAULT("bank_vault", "은행 금고", false, true),
    /** NPC 를 세우면 자동으로 기록 (폰 지도 앱에서 찾기용). 이름 = NPC 종류 id. */
    NPC("npc", "NPC", false, false);

    private final String id;
    private final String displayName;
    private final boolean single;
    private final boolean block;

    PlaceType(String id, String displayName, boolean single, boolean block) {
        this.id = id;
        this.displayName = displayName;
        this.single = single;
        this.block = block;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** 하나만 있을 수 있는 장소 (다시 지정하면 바뀜). */
    public boolean single() {
        return single;
    }

    /** 서 있는 위치가 아니라 바라보는 블록을 지정하는 장소. */
    public boolean block() {
        return block;
    }

    public static PlaceType byId(String id) {
        if (id == null) {
            return null;
        }
        for (PlaceType type : values()) {
            if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return null;
    }
}
