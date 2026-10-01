package com.junseo.friend;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** One direction of a friendship; every pair is stored as two rows (a→b and b→a). */
@Entity
@Table(name = "friendships")
@IdClass(Friendship.Key.class)
public class Friendship {

    public record Key(Long userId, Long friendId) implements Serializable {}

    @Id
    private Long userId;

    @Id
    private Long friendId;

    private Instant createdAt;

    protected Friendship() {}

    public Friendship(long userId, long friendId, Instant createdAt) {
        this.userId = userId;
        this.friendId = friendId;
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getFriendId() {
        return friendId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
