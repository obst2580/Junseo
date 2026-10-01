package com.junseo.group;

import java.util.List;

public final class GroupEvents {

    private GroupEvents() {}

    /** memberIds includes the sender; groupName is null when the group shows its members' names. */
    public record GroupMessageSent(
            long messageId, long groupId, String groupName, long senderId, List<Long> memberIds, String text) {}
}
