package com.junseo.reaction;

import java.util.List;

/** The only reactions the app offers. Checked here so every client and every widget shows the same five. */
public final class ReactionEmojis {

    public static final List<String> ALLOWED = List.of("❤️", "😂", "😢", "👍", "🖕");

    private ReactionEmojis() {}

    /** The stored form of an allowed emoji, or {@code null}. A heart sent without the emoji variation selector counts as ❤️. */
    static String canonical(String emoji) {
        if (emoji == null) {
            return null;
        }
        String e = "❤".equals(emoji) ? "❤️" : emoji;
        return ALLOWED.contains(e) ? e : null;
    }
}
