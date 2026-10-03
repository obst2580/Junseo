package com.junseo.realtime;

import com.junseo.chat.ChatEvents.MessageSent;
import com.junseo.chat.ChatEvents.MessagesRead;
import com.junseo.group.GroupEvents.GroupMessageSent;
import com.junseo.group.GroupEvents.GroupRead;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Signals for open chat screens, sent after commit so a fetch triggered by the signal sees the change.
 * The payload only says what changed; the app re-fetches that conversation.
 */
@Component
public class RealtimeNotifier {

    private final RealtimeHub hub;

    public RealtimeNotifier(RealtimeHub hub) {
        this.hub = hub;
    }

    @TransactionalEventListener
    public void on(MessageSent e) {
        hub.send(List.of(e.receiverId()), Map.of("type", "message", "peerId", e.senderId()));
    }

    /** The reader's friend sees 「1」 disappear from their messages. */
    @TransactionalEventListener
    public void on(MessagesRead e) {
        hub.send(List.of(e.peerId()), Map.of("type", "read", "peerId", e.readerId()));
    }

    @TransactionalEventListener
    public void on(GroupMessageSent e) {
        hub.send(others(e.memberIds(), e.senderId()), Map.of("type", "group-message", "groupId", e.groupId()));
    }

    @TransactionalEventListener
    public void on(GroupRead e) {
        hub.send(others(e.memberIds(), e.readerId()), Map.of("type", "group-read", "groupId", e.groupId()));
    }

    private static List<Long> others(List<Long> memberIds, long me) {
        return memberIds.stream().filter(id -> id != me).toList();
    }
}
