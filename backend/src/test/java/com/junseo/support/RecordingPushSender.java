package com.junseo.support;

import com.junseo.push.PushSender;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures pushes instead of calling APNs; tokens can be marked dead to simulate a 410. */
public class RecordingPushSender implements PushSender {

    private final List<PushMessage> sent = new CopyOnWriteArrayList<>();
    private final Set<String> deadTokens = ConcurrentHashMap.newKeySet();

    @Override
    public PushOutcome send(PushMessage message) {
        sent.add(message);
        return deadTokens.contains(message.token()) ? PushOutcome.INVALID_TOKEN : PushOutcome.SENT;
    }

    public List<PushMessage> to(String token) {
        return sent.stream().filter(m -> m.token().equals(token)).toList();
    }

    public void markDead(String token) {
        deadTokens.add(token);
    }

    public void clear() {
        sent.clear();
        deadTokens.clear();
    }
}
