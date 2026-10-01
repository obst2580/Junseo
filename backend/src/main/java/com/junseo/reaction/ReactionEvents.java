package com.junseo.reaction;

public final class ReactionEvents {

    private ReactionEvents() {}

    public record ReactionSet(long momentId, long userId, String emoji) {}

    public record ReactionRemoved(long momentId, long userId) {}
}
