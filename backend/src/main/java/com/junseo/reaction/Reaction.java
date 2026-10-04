package com.junseo.reaction;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "reactions")
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long momentId;
    private Long userId;
    private String emoji;
    private int taps;
    private Instant createdAt;
    private Instant updatedAt;

    protected Reaction() {}

    public Reaction(long momentId, long userId, String emoji, Instant at) {
        this(momentId, userId, emoji, 1, at);
    }

    public Reaction(long momentId, long userId, String emoji, int taps, Instant at) {
        this.momentId = momentId;
        this.userId = userId;
        this.emoji = emoji;
        this.taps = taps;
        this.createdAt = at;
        this.updatedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getMomentId() {
        return momentId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmoji() {
        return emoji;
    }

    public int getTaps() {
        return taps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
