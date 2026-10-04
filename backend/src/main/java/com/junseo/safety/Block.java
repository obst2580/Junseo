package com.junseo.safety;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "blocks")
@IdClass(Block.Key.class)
public class Block {

    public record Key(Long blockerId, Long blockedId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    private Long blockerId;

    @Id
    private Long blockedId;

    private Instant createdAt;

    protected Block() {}

    public Block(long blockerId, long blockedId, Instant createdAt) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createdAt = createdAt;
    }

    public Long getBlockedId() {
        return blockedId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
