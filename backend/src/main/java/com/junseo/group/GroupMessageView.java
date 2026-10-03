package com.junseo.group;

import java.time.Instant;

/** unreadCount: how many members (other than the sender) have not read this message yet. */
/** clientId: the sender's own id for the send (see V4); only the sender sees it, so the app can match its pending copy. */
public record GroupMessageView(
        long id, long groupId, long senderId, String text, Instant createdAt, int unreadCount, String clientId) {}
