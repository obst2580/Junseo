package com.junseo.city.logic;

/** 수배 별을 올리는 범죄 종류. */
public enum Crime {
    ASSAULT("폭행", 1),
    MURDER("살인", 2),
    COP_ASSAULT("경찰 폭행", 2),
    COP_KILL("경찰 살해", 3),
    CAR_THEFT("차량 절도", 1),
    POLICE_CAR_THEFT("경찰차 절도", 2),
    STORE_ROBBERY("편의점 강도", 2),
    BANK_ROBBERY("은행 강도", 3);

    private final String displayName;
    private final int stars;

    Crime(String displayName, int stars) {
        this.displayName = displayName;
        this.stars = stars;
    }

    public String displayName() {
        return displayName;
    }

    public int stars() {
        return stars;
    }
}
