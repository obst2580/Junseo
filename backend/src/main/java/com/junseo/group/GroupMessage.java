package com.junseo.group;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "group_messages")
public class GroupMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;
    private Long senderId;
    private String text;
    private Instant createdAt;

    protected GroupMessage() {}

    public GroupMessage(long groupId, long senderId, String text, Instant createdAt) {
        this.groupId = groupId;
        this.senderId = senderId;
        this.text = text;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
