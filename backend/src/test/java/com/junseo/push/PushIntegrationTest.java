package com.junseo.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.junseo.push.PushSender.PushMessage;
import com.junseo.push.PushSender.PushType;
import com.junseo.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PushIntegrationTest extends IntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private TestUser owner;
    private TestUser b;
    private TestUser c;
    private Devices ownerDevices;
    private Devices bDevices;
    private Devices cDevices;

    private record Devices(String app, String widget) {}

    @BeforeEach
    void friendsWithDevices() throws Exception {
        owner = signup("민지");
        b = signup("지우");
        c = signup("서연");
        befriend(owner, b);
        befriend(owner, c);
        ownerDevices = new Devices(registerDevice(owner, "app"), registerDevice(owner, "widget"));
        bDevices = new Devices(registerDevice(b, "app"), registerDevice(b, "widget"));
        cDevices = new Devices(registerDevice(c, "app"), registerDevice(c, "widget"));
    }

    @Test
    void newMomentAlertsAndRefreshesWidgetsOfEveryRecipient() throws Exception {
        long m = upload(owner);

        for (Devices d : List.of(bDevices, cDevices)) {
            PushMessage widget = awaitOne(d.widget());
            assertThat(widget.type()).isEqualTo(PushType.WIDGETS);
            assertThat(widget.payload()).isEqualTo("{\"aps\":{\"content-changed\":true}}");

            PushMessage alert = awaitOne(d.app());
            assertThat(alert.type()).isEqualTo(PushType.ALERT);
            assertThat(alert.environment()).isEqualTo("development");
            Map<?, ?> payload = payload(alert);
            Map<?, ?> aps = (Map<?, ?>) payload.get("aps");
            assertThat(aps.get("alert")).isEqualTo(Map.of("title", "민지", "body", "새 사진을 보냈어요"));
            assertThat(aps.get("sound")).isEqualTo("default");
            assertThat(aps.get("mutable-content")).isEqualTo(1);
            assertThat(aps.get("thread-id")).isEqualTo("moment-" + m);
            assertThat(payload.get("type")).isEqualTo("moment");
            assertThat(((Number) payload.get("momentId")).longValue()).isEqualTo(m);
            assertThat((String) payload.get("thumbUrl")).startsWith("/media/" + m + "/thumb.jpg?exp=");
        }
        assertThat(push.to(ownerDevices.app())).isEmpty();
        assertThat(push.to(ownerDevices.widget())).isEmpty();
    }

    @Test
    void reactionAlertsOwnerAndRefreshesWidgetViewersExceptActor() throws Exception {
        long m = upload(owner);
        settle();

        postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "👍")).andExpect(status().isOk());

        awaitOne(cDevices.widget());
        Map<?, ?> payload = payload(awaitOne(ownerDevices.app()));
        assertThat(((Map<?, ?>) payload.get("aps")).get("alert")).isEqualTo(Map.of("body", "지우님이 👍 반응을 남겼어요"));
        assertThat(payload.get("type")).isEqualTo("reaction");
        assertThat(((Number) payload.get("momentId")).longValue()).isEqualTo(m);
        assertThat(push.to(bDevices.widget())).isEmpty();
        assertThat(push.to(bDevices.app())).isEmpty();
        assertThat(push.to(cDevices.app())).isEmpty();
        // The owner's widget shows photos received from friends, never their own.
        assertThat(push.to(ownerDevices.widget())).isEmpty();

        // Tapping again within a minute adds a tap but notifies no one.
        postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "👍")).andExpect(status().isOk());
        postJson("/api/conversations/" + owner.id() + "/messages", b, Map.of("text", "marker")).andExpect(status().isCreated());
        await().atMost(WAIT).until(() -> push.to(ownerDevices.app()).size() == 2);
        assertThat(payload(push.to(ownerDevices.app()).get(1)).get("type")).isEqualTo("message");
        assertThat(push.to(cDevices.widget())).hasSize(1);
    }

    @Test
    void aRunOfTapsNotifiesOnceWithTheCount() throws Exception {
        long m = upload(owner);
        settle();

        postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "❤️", "count", 3)).andExpect(status().isOk());
        Map<?, ?> payload = payload(awaitOne(ownerDevices.app()));
        assertThat(((Map<?, ?>) payload.get("aps")).get("alert")).isEqualTo(Map.of("body", "지우님이 ❤️ 3개를 보냈어요"));
        awaitOne(cDevices.widget());

        // More taps in the same minute are counted, without another alert or widget push.
        postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "😂", "count", 2)).andExpect(status().isOk());
        postJson("/api/conversations/" + owner.id() + "/messages", b, Map.of("text", "marker")).andExpect(status().isCreated());
        await().atMost(WAIT).until(() -> push.to(ownerDevices.app()).size() == 2);
        assertThat(payload(push.to(ownerDevices.app()).get(1)).get("type")).isEqualTo("message");
        assertThat(push.to(cDevices.widget())).hasSize(1);
    }

    @Test
    void commentByOwnerDoesNotAlertOwner() throws Exception {
        long m = upload(owner);
        settle();

        postJson("/api/moments/" + m + "/comments", owner, Map.of("text", "고마워")).andExpect(status().isCreated());

        awaitOne(bDevices.widget());
        awaitOne(cDevices.widget());
        assertThat(push.to(ownerDevices.app())).isEmpty();
        assertThat(push.to(ownerDevices.widget())).isEmpty();
        assertThat(push.to(bDevices.app())).isEmpty();
    }

    @Test
    void commentByFriendAlertsOwnerWithTruncatedText() throws Exception {
        long m = upload(owner);
        settle();

        postJson("/api/moments/" + m + "/comments", b, Map.of("text", "대박")).andExpect(status().isCreated());
        Map<?, ?> payload = payload(awaitOne(ownerDevices.app()));
        assertThat(((Map<?, ?>) payload.get("aps")).get("alert")).isEqualTo(Map.of("body", "지우: 대박"));
        assertThat(((Map<?, ?>) payload.get("aps")).get("thread-id")).isEqualTo("moment-" + m);
        assertThat(payload.get("type")).isEqualTo("comment");
        assertThat(((Number) payload.get("momentId")).longValue()).isEqualTo(m);
        awaitOne(cDevices.widget());
        assertThat(push.to(bDevices.widget())).isEmpty();
        assertThat(push.to(ownerDevices.widget())).isEmpty();

        postJson("/api/moments/" + m + "/comments", b, Map.of("text", "가".repeat(100))).andExpect(status().isCreated());
        await().atMost(WAIT).until(() -> push.to(ownerDevices.app()).size() == 2);
        String body = (String) ((Map<?, ?>) ((Map<?, ?>) payload(push.to(ownerDevices.app()).get(1)).get("aps")).get("alert")).get("body");
        assertThat(body).isEqualTo("지우: " + "가".repeat(79) + "…");
    }

    @Test
    void activityOnAnOlderPhotoSkipsWidgetsShowingANewerOne() throws Exception {
        long older = upload(owner);
        settle();
        befriend(b, c);
        upload(c); // b's widget now shows c's newer photo; c's widget still shows the owner's.
        await().atMost(WAIT).until(() -> !push.to(bDevices.widget()).isEmpty());
        push.clear();

        postJson("/api/moments/" + older + "/comments", owner, Map.of("text", "다들 뭐해")).andExpect(status().isCreated());

        awaitOne(cDevices.widget());
        assertThat(push.to(bDevices.widget())).isEmpty();
    }

    @Test
    void messageAlertsOnlyTheReceiver() throws Exception {
        postJson("/api/conversations/" + owner.id() + "/messages", b, Map.of("text", "ㅋㅋㅋ")).andExpect(status().isCreated());

        Map<?, ?> payload = payload(awaitOne(ownerDevices.app()));
        Map<?, ?> aps = (Map<?, ?>) payload.get("aps");
        assertThat(aps.get("alert")).isEqualTo(Map.of("body", "지우: ㅋㅋㅋ"));
        assertThat(aps.get("mutable-content")).isEqualTo(1);
        assertThat(payload.get("type")).isEqualTo("message");
        assertThat(((Number) payload.get("peerId")).longValue()).isEqualTo(b.id());
        assertThat(payload.containsKey("momentId")).isFalse();
        for (String token : List.of(ownerDevices.widget(), bDevices.app(), bDevices.widget(), cDevices.app(), cDevices.widget())) {
            assertThat(push.to(token)).isEmpty();
        }
    }

    @Test
    void replyAlertsThePhotoOwnerAsAMessage() throws Exception {
        long m = upload(owner);
        settle();
        postJson("/api/moments/" + m + "/replies", c, Map.of("text", "어디야")).andExpect(status().isCreated());
        Map<?, ?> payload = payload(awaitOne(ownerDevices.app()));
        assertThat(payload.get("type")).isEqualTo("message");
        assertThat(((Number) payload.get("peerId")).longValue()).isEqualTo(c.id());
        assertThat(((Map<?, ?>) payload.get("aps")).get("alert")).isEqualTo(Map.of("body", "서연: 어디야"));
    }

    @Test
    void deadTokensAreDeleted() throws Exception {
        push.markDead(bDevices.widget());
        upload(owner);
        await().atMost(WAIT).until(() -> tokenCount(bDevices.widget()) == 0);
        assertThat(tokenCount(bDevices.app())).isEqualTo(1);
        assertThat(tokenCount(cDevices.widget())).isEqualTo(1);
    }

    @Test
    void tokenRegisteredByAnotherAccountMovesAndLogoutOnlyRemovesOwnTokens() throws Exception {
        String shared = bDevices.app();
        putJson("/api/devices", c, Map.of("token", shared.toUpperCase(), "kind", "app", "environment", "production"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select user_id from device_tokens where token = ?", Long.class, shared)).isEqualTo(c.id());

        deleteAs(b, "/api/devices/" + shared).andExpect(status().isNoContent());
        assertThat(tokenCount(shared)).isEqualTo(1);
        deleteAs(c, "/api/devices/" + shared).andExpect(status().isNoContent());
        assertThat(tokenCount(shared)).isZero();

        putJson("/api/devices", c, Map.of("token", "not-hex", "kind", "app", "environment", "production"))
                .andExpect(status().isBadRequest());
        putJson("/api/devices", c, Map.of("token", "abcdef0123456789", "kind", "watch", "environment", "production"))
                .andExpect(status().isBadRequest());
    }

    /** Waits until the moment-created fan-out has landed, then forgets it. */
    private void settle() {
        await().atMost(WAIT).until(() -> !push.to(bDevices.widget()).isEmpty() && !push.to(cDevices.widget()).isEmpty());
        push.clear();
    }

    private PushMessage awaitOne(String token) {
        await().atMost(WAIT).until(() -> !push.to(token).isEmpty());
        assertThat(push.to(token)).hasSize(1);
        return push.to(token).getFirst();
    }

    private Map<?, ?> payload(PushMessage message) {
        return json.readValue(message.payload(), Map.class);
    }

    private int tokenCount(String token) {
        return jdbc.queryForObject("select count(*) from device_tokens where token = ?", Integer.class, token);
    }
}
