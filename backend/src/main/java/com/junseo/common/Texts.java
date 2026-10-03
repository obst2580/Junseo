package com.junseo.common;

public final class Texts {

    private Texts() {}

    /** Truncates by code points so emoji and Hangul are never split. */
    public static String truncate(String text, int maxCodePoints) {
        if (text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxCodePoints - 1);
        return text.substring(0, end) + "…";
    }
}
