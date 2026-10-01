package com.junseo.reaction;

public final class ReactionEvents {

    private ReactionEvents() {}

    /**
     * Someone tapped an emoji {@code taps} times. {@code firstInBurst} is false when they already reacted
     * to this photo within the last minute, so a run of taps notifies the owner once.
     */
    public record ReactionSet(long momentId, long userId, String emoji, int taps, boolean firstInBurst) {}

    public record ReactionRemoved(long momentId, long userId) {}
}
