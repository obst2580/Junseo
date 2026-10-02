package com.junseo.realtime;

import com.junseo.common.security.Sessions;
import java.util.Map;
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
    private final Sessions sessions;
    private final RealtimeHub hub;
    private final ObjectMapper json;

    public RealtimeHandler(JwtDecoder jwtDecoder, Sessions sessions, RealtimeHub hub, ObjectMapper json) {
        this.jwtDecoder = jwtDecoder;
        this.sessions = sessions;
        this.hub = hub;
        this.json = json;
    }

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
                id = Long.parseLong(jwt.getSubject());
            } catch (JwtException | NumberFormatException e) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid token"));
                return;
            }
            // 탈퇴했거나 비밀번호를 바꾼 뒤의 예전 로그인
            if (!sessions.isValid(id, jwt)) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid token"));
                return;
            }
            session.getAttributes().put(USER, id);
            WebSocketSession safe = hub.register(id, session);
            session.getAttributes().put(SAFE, safe);
            safe.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type", "ready"))));
            return;
        }
        if ("ping".equals(type)) {
            ((WebSocketSession) session.getAttributes().get(SAFE)).sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get(USER);
        Object safe = session.getAttributes().get(SAFE);
        if (userId instanceof Long id && safe instanceof WebSocketSession s) {
            hub.unregister(id, s);
        }
    }
}
