package com.junseo.friend;

public final class FriendEvents {

    private FriendEvents() {}

    public record Unfriended(long userId, long formerFriendId) {}
}
