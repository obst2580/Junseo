package com.junseo.city.logic;

import java.util.Locale;

/** 플레이어가 고를 수 있는 직업. */
public enum Job {
    CITIZEN("시민", "citizen", "기본 직업. 자유롭게 돈을 벌어요."),
    POLICE("경찰", "police", "시민을 지키고 범죄자를 체포해요. (공무직: 채용 필요)"),
    EMS("의료국", "ems", "쓰러진 사람을 구하고 치료해요. (공무직: 채용 필요)"),
    DELIVERY("택배기사", "delivery", "택배를 배달지까지 옮기고 돈을 받아요."),
    MINER("광부", "miner", "광물을 캐서 광물 거래소에 비싸게 팔아요.");

    private final String displayName;
    private final String key;
    private final String description;

    Job(String displayName, String key, String description) {
        this.displayName = displayName;
        this.key = key;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    /** config.yml 등에서 쓰는 영문 키. */
    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    /** 경찰·의료국처럼 아무나 고를 수 없고 채용되어야 하는 직업. */
    public boolean government() {
        return this == POLICE || this == EMS;
    }

    /** 영문 키, enum 이름, 한글 이름 중 아무거나로 직업을 찾습니다. 없으면 null. */
    public static Job parse(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        for (Job job : values()) {
            if (job.key.equals(s) || job.name().toLowerCase(Locale.ROOT).equals(s) || job.displayName.equals(input.trim())) {
                return job;
            }
        }
        return null;
    }
}
