package com.junseo.chat;

import java.time.Instant;

/** clientId: the sender's own id for the send (see V4); only the sender sees it, so the app can match its pending copy. */
public record MessageView(
        long id,
        long senderId,
        long receiverId,
        String text,
        Instant createdAt,
        Instant readAt,
        MomentRef moment,
        String clientId) {

    /** The photo a reply refers to; null when it is gone or no longer visible to the viewer. */
    public record MomentRef(long id, String thumbUrl) {}
}
