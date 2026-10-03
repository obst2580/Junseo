package com.junseo.realtime;

import java.util.Map;
import com.junseo.auth.PlatformIdentityService;
import com.junseo.auth.TokenRevocations;
import com.junseo.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * /ws: the app's live channel. The first frame must be {@code {"type":"auth","token":"<access token>"}} (a
 * header cannot be set on a browser WebSocket, and a query string would end up in access logs). After that
 * the server only sends small "something changed" signals and the app fetches the details over REST.
 * {@code {"type":"ping"}} is answered with a pong so idle connections stay open through proxies.
 */
@Component
public class RealtimeHandler extends TextWebSocketHandler {

    private static final String USER = "userId";
    private static final String SAFE = "safeSession";

    private final JwtDecoder jwtDecoder;
    private final RealtimeHub hub;
    private final ObjectMapper json;
    private final PlatformIdentityService identities;
    private final TokenRevocations revocations;
    private final Clock clock;
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "websocket-expiry"); thread.setDaemon(true); return thread;
    });

    public RealtimeHandler(JwtDecoder jwtDecoder, RealtimeHub hub, ObjectMapper json, PlatformIdentityService identities,
            TokenRevocations revocations, Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.hub = hub;
        this.json = json;
        this.identities = identities;
        this.revocations = revocations;
        this.clock = clock;
    }

    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put("expiryTask", expiry.schedule(() -> close(session, "auth timeout"), 15, TimeUnit.SECONDS));
    }

    @PreDestroy public void shutdown() { expiry.shutdownNow(); }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode frame;
        try {
            frame = json.readTree(message.getPayload());
        } catch (RuntimeException e) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        String type = frame.path("type").asString("");
        Object userId = session.getAttributes().get(USER);
        if (userId == null) {
            if (!"auth".equals(type)) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("auth first"));
                return;
            }
            long id;
            Jwt jwt;
            try {
                jwt = jwtDecoder.decode(frame.path("token").asString(""));
                id = identities.require(jwt);
            } catch (JwtException | ApiException e) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid token"));
                return;
            }
            session.getAttributes().put(USER, id);
            if (jwt.getId() != null) session.getAttributes().put("jti", jwt.getId());
            cancelExpiry(session);
            if (jwt.getExpiresAt() != null) session.getAttributes().put("expiryTask", expiry.schedule(
                    () -> close(session, "token expired"), Math.max(0, Duration.between(clock.instant(), jwt.getExpiresAt()).toMillis()), TimeUnit.MILLISECONDS));
            WebSocketSession safe = hub.register(id, session);
            session.getAttributes().put(SAFE, safe);
            safe.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type", "ready"))));
            return;
        }
        if ("ping".equals(type)) {
            String jti = (String) session.getAttributes().get("jti");
            if (jti != null && revocations.revoked(jti)) { close(session, "session revoked"); return; }
            ((WebSocketSession) session.getAttributes().get(SAFE)).sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelExpiry(session);
        Object userId = session.getAttributes().get(USER);
        Object safe = session.getAttributes().get(SAFE);
        if (userId instanceof Long id && safe instanceof WebSocketSession s) {
            hub.unregister(id, s);
        }
    }

    private static void cancelExpiry(WebSocketSession session) {
        Object task = session.getAttributes().remove("expiryTask");
        if (task instanceof ScheduledFuture<?> future) future.cancel(false);
    }
    private static void close(WebSocketSession session, String reason) {
        try { session.close(CloseStatus.POLICY_VIOLATION.withReason(reason)); } catch (java.io.IOException ignored) { }
    }
}
