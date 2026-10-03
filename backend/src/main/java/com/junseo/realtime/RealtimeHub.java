package com.junseo.realtime;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.ObjectMapper;

/**
 * Open sockets per signed-in user (one per device or tab). Only this server instance's sockets: with more
 * than one instance the signals would need a shared channel (e.g. Postgres LISTEN/NOTIFY or Redis).
 */
@Component
public class RealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_LIMIT_BYTES = 64 * 1024;

    private final Map<Long, Set<WebSocketSession>> byUser = new ConcurrentHashMap<>();
    private final ObjectMapper json;

    public RealtimeHub(ObjectMapper json) {
        this.json = json;
    }

    /** Returns the thread-safe wrapper to use for this session from now on. */
    WebSocketSession register(long userId, WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
        byUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(safe);
        return safe;
    }

    void unregister(long userId, WebSocketSession session) {
        byUser.computeIfPresent(userId, (id, set) -> {
            set.removeIf(s -> s == session || s.getId().equals(session.getId()));
            return set.isEmpty() ? null : set;
        });
    }

    /** Best effort: a dead socket is dropped, and the app re-fetches on reconnect anyway. */
    public void send(Collection<Long> userIds, Map<String, Object> signal) {
        TextMessage message = new TextMessage(json.writeValueAsString(signal));
        for (long userId : userIds) {
            Set<WebSocketSession> sessions = byUser.get(userId);
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession session : sessions) {
                try {
                    session.sendMessage(message);
                } catch (IOException | RuntimeException e) {
                    log.debug("Dropping socket {} of user {}", session.getId(), userId, e);
                    unregister(userId, session);
                }
            }
        }
    }

    int openSockets(long userId) {
        Set<WebSocketSession> sessions = byUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    public void closeToken(String jti) {
        byUser.values().forEach(sessions -> sessions.forEach(session -> {
            if (jti != null && jti.equals(session.getAttributes().get("jti"))) {
                try { session.close(CloseStatus.POLICY_VIOLATION.withReason("session revoked")); }
                catch (IOException ignored) { }
            }
        }));
    }
}
