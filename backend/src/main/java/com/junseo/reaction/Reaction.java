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
    private Instant createdAt;
    private Instant updatedAt;

    protected Reaction() {}

    public Reaction(long momentId, long userId, String emoji, Instant at) {
        this.momentId = momentId;
        this.userId = userId;
        this.emoji = emoji;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
