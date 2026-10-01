package com.junseo.group;

import java.time.Instant;

/** unreadCount: how many members (other than the sender) have not read this message yet. */
public record GroupMessageView(long id, long groupId, long senderId, String text, Instant createdAt, int unreadCount) {}
