package com.junseo.city.logic;

/** 수배 별 계산 규칙 (서버 API 없이 테스트할 수 있도록 분리). */
public final class WantedRules {
    public static final int MAX_STARS = 5;

    private WantedRules() {
    }

    public static int add(int current, int stars) {
        return clamp(current + stars);
    }

    public static int clamp(int stars) {
        return Math.max(0, Math.min(MAX_STARS, stars));
    }

    /** 마지막 범죄(또는 마지막 감소) 이후 decayMillis 가 지났으면 별을 하나 줄여야 합니다. */
    public static boolean shouldDecay(int stars, long millisSinceLastChange, long decayMillis) {
        return stars > 0 && millisSinceLastChange >= decayMillis;
    }

    public static int jailSeconds(int stars, int secondsPerStar) {
        return Math.max(1, stars) * secondsPerStar;
    }

    /** 별 개수에 맞는 AI 경찰 수. 설정 목록이 짧으면 마지막 값을 씁니다. */
    public static int policeCount(int stars, int[] perStar) {
        if (perStar.length == 0 || stars <= 0) {
            return 0;
        }
        return Math.max(0, perStar[Math.min(stars, perStar.length - 1)]);
    }

    /** ★★☆☆☆ 형태의 문자열. */
    public static String stars(int stars) {
        int n = clamp(stars);
        return "★".repeat(n) + "☆".repeat(MAX_STARS - n);
    }
}
