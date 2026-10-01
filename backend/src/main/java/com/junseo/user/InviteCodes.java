package com.junseo.user;

import java.security.SecureRandom;
import java.util.Locale;

public final class InviteCodes {

    /** Uppercase letters and digits without the look-alikes 0, O, 1, I and L. */
    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {}

    public static String random() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** What users type or paste: any case, with spaces anywhere. */
    public static String normalize(String input) {
        return input == null ? "" : input.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
