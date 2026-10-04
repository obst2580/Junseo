package com.junseo.city.logic;

import java.util.Locale;

/** 금액 표시와 입력 해석. */
public final class Money {
    private Money() {
    }

    public static String format(long amount, String currency) {
        return String.format(Locale.ROOT, "%,d", amount) + currency;
    }

    /**
     * "1000", "1,000", "1만", "2.5만", "3천" 같은 입력을 숫자로 바꿉니다.
     * 잘못된 입력이거나 0 이하면 -1.
     */
    public static long parse(String input) {
        if (input == null) {
            return -1;
        }
        String s = input.trim().replace(",", "").replace("원", "");
        long multiplier = 1;
        if (s.endsWith("만")) {
            multiplier = 10_000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("천")) {
            multiplier = 1_000;
            s = s.substring(0, s.length() - 1);
        }
        try {
            double value = Double.parseDouble(s) * multiplier;
            if (Double.isNaN(value) || value < 1 || value > 1_000_000_000_000L) {
                return -1;
            }
            return (long) Math.floor(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
