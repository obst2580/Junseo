package com.junseo.chat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long senderId;
    private Long receiverId;
    /** Set when the message is a reply to a photo; nulled if that photo is deleted. */
    private Long momentId;
    private String text;
    private Instant createdAt;
    private Instant readAt;
    /** The app's own id for this send, so a retry finds the first copy (see V4). */
    private String clientId;

    protected Message() {}

    public Message(long senderId, long receiverId, Long momentId, String text, Instant createdAt) {
        this(senderId, receiverId, momentId, text, createdAt, null);
    }

    public Message(long senderId, long receiverId, Long momentId, String text, Instant createdAt, String clientId) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.momentId = momentId;
        this.text = text;
        this.createdAt = createdAt;
        this.clientId = clientId;
    }

    public Long getId() {
        return id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public Long getMomentId() {
        return momentId;
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

    public Instant getReadAt() {
        return readAt;
    }

    public void markRead(Instant at) {
        this.readAt = at;
    }
}
