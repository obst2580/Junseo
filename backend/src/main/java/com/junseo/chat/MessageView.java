package com.junseo.chat;

import java.time.Instant;

public record MessageView(
        long id, long senderId, long receiverId, String text, Instant createdAt, Instant readAt, MomentRef moment) {

    /** The photo a reply refers to; null when it is gone or no longer visible to the viewer. */
    public record MomentRef(long id, String thumbUrl) {}
}
