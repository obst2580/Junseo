package com.junseo.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.junseo.support.IntegrationTest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class RealtimeIntegrationTest extends IntegrationTest {

    private final List<WebSocketSession> open = new java.util.ArrayList<>();

    @AfterEach
    void closeSockets() throws Exception {
        for (WebSocketSession s : open) {
            if (s.isOpen()) {
                s.close();
            }
        }
    }

    /** Frames received by one socket, plus how it was closed. */
    private static final class Inbox extends TextWebSocketHandler {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<CloseStatus> closed = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.add(status);
        }
    }

    private Inbox connect(TestUser user) throws Exception {
        Inbox inbox = new Inbox();
        WebSocketSession session = new StandardWebSocketClient()
                .execute(inbox, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws"))
                .get(5, TimeUnit.SECONDS);
        open.add(session);
        session.sendMessage(new TextMessage(toJson(Map.of("type", "auth", "token", user.token()))));
        assertThat(frame(inbox).get("type")).isEqualTo("ready");
        return inbox;
    }

    private Map<?, ?> frame(Inbox inbox) throws Exception {
        String raw = inbox.frames.poll(5, TimeUnit.SECONDS);
        assertThat(raw).as("a frame within 5s").isNotNull();
        return json.readValue(raw, Map.class);
    }

    @Test
    void messagesAndReadsSignalTheOtherSide() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        befriend(a, b);
        Inbox aSocket = connect(a);
        Inbox bSocket = connect(b);

        postJson("/api/conversations/" + b.id() + "/messages", a, Map.of("text", "안녕")).andExpect(status().isCreated());
        Map<?, ?> toB = frame(bSocket);
        assertThat(toB.get("type")).isEqualTo("message");
        assertThat(((Number) toB.get("peerId")).longValue()).isEqualTo(a.id());

        postAs(b, "/api/conversations/" + a.id() + "/read").andExpect(status().isNoContent());
        Map<?, ?> toA = frame(aSocket);
        assertThat(toA.get("type")).isEqualTo("read");
        assertThat(((Number) toA.get("peerId")).longValue()).isEqualTo(b.id());

        // Reading again with nothing new is not a change.
        postAs(b, "/api/conversations/" + a.id() + "/read").andExpect(status().isNoContent());
        assertThat(aSocket.frames.poll(400, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void groupMessagesAndReadsReachTheOtherMembers() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        TestUser c = signup("지우");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);
        long group = longAt(body(postJson("/api/groups", a, Map.of("memberIds", List.of(b.id(), c.id())))
                .andExpect(status().isCreated()).andReturn()), "$.id");
        Inbox aSocket = connect(a);
        Inbox bSocket = connect(b);
        Inbox cSocket = connect(c);

        postJson("/api/groups/" + group + "/messages", a, Map.of("text", "모여")).andExpect(status().isCreated());
        for (Inbox member : List.of(bSocket, cSocket)) {
            Map<?, ?> f = frame(member);
            assertThat(f.get("type")).isEqualTo("group-message");
            assertThat(((Number) f.get("groupId")).longValue()).isEqualTo(group);
        }
        assertThat(aSocket.frames.poll(300, TimeUnit.MILLISECONDS)).as("not to the sender").isNull();

        postAs(b, "/api/groups/" + group + "/read").andExpect(status().isNoContent());
        assertThat(frame(aSocket).get("type")).isEqualTo("group-read");
        assertThat(frame(cSocket).get("type")).isEqualTo("group-read");
        assertThat(bSocket.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void aBadTokenClosesTheSocketAndPingIsAnswered() throws Exception {
        TestUser a = signup("준서");
        Inbox stranger = new Inbox();
        WebSocketSession s = new StandardWebSocketClient()
                .execute(stranger, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws"))
                .get(5, TimeUnit.SECONDS);
        s.sendMessage(new TextMessage("{\"type\":\"auth\",\"token\":\"nope\"}"));
        CloseStatus closed = stranger.closed.poll(5, TimeUnit.SECONDS);
        assertThat(closed).isNotNull();
        assertThat(closed.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());

        Inbox inbox = connect(a);
        open.getLast().sendMessage(new TextMessage("{\"type\":\"ping\"}"));
        assertThat(frame(inbox).get("type")).isEqualTo("pong");
    }
}
