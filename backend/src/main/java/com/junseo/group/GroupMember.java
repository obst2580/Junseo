package com.junseo.group;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "chat_group_members")
@IdClass(GroupMember.Key.class)
public class GroupMember {

    public record Key(Long groupId, Long userId) implements Serializable {}

    @Id
    private Long groupId;

    @Id
    private Long userId;

    /** Newest message this member has seen; 0 before reading anything. */
    private long lastReadId;

    private Instant joinedAt;

    protected GroupMember() {}

    public GroupMember(long groupId, long userId, Instant joinedAt) {
        this.groupId = groupId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public long getLastReadId() {
        return lastReadId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
