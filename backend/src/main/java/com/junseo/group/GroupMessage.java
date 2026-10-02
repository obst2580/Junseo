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
    /** The app's own id for this send, so a retry finds the first copy (see V4). */
    private String clientId;

    protected GroupMessage() {}

    public GroupMessage(long groupId, long senderId, String text, Instant createdAt) {
        this(groupId, senderId, text, createdAt, null);
    }

    public GroupMessage(long groupId, long senderId, String text, Instant createdAt, String clientId) {
        this.groupId = groupId;
        this.senderId = senderId;
        this.text = text;
        this.createdAt = createdAt;
        this.clientId = clientId;
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

    public String getClientId() {
        return clientId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
