package com.junseo.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.junseo.push.PushSender.PushType;
import com.junseo.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AndroidDeviceIntegrationTest extends IntegrationTest {
    private static final String TOKEN = "FCM:CaseSensitive_AbCd123456789";

    @Test void fcmRegistrationKeepsCaseAndOwnershipMovesWithLogin() throws Exception {
        var first = signup("첫 계정");
        var second = signup("다음 계정");
        register(first);
        register(second);
        assertThat(jdbc.queryForObject("select user_id from device_tokens where token = ?", Long.class, TOKEN)).isEqualTo(second.id());
        deleteAs(first, "/api/devices/" + TOKEN).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from device_tokens where token = ?", Integer.class, TOKEN)).isEqualTo(1);
        deleteAs(second, "/api/devices/" + TOKEN).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from device_tokens where token = ?", Integer.class, TOKEN)).isZero();
    }

    @Test void androidDeviceReceivesBothAlertsAndSilentWidgetChangesThroughOneToken() throws Exception {
        var owner = signup("보낸 친구");
        var recipient = signup("받는 친구");
        befriend(owner, recipient);
        register(recipient);
        long moment = upload(owner);
        await().atMost(Duration.ofSeconds(5)).until(() -> push.to(TOKEN).size() == 2);
        assertThat(push.to(TOKEN)).extracting(PushSender.PushMessage::type).containsExactly(PushType.ALERT, PushType.WIDGETS);
        assertThat(push.to(TOKEN)).allMatch(m -> m.platform().equals("android"));
        push.clear();
        postJson("/api/moments/" + moment + "/comments", owner, Map.of("text", "댓글 갱신")).andExpect(status().isCreated());
        await().atMost(Duration.ofSeconds(5)).until(() -> push.to(TOKEN).size() == 1);
        assertThat(push.to(TOKEN).getFirst().type()).isEqualTo(PushType.WIDGETS);
    }

    @Test void legacyIosRegistrationDefaultsToIosAndRejectsFcmToken() throws Exception {
        var user = signup("기존 앱");
        String token = registerDevice(user, "app");
        assertThat(jdbc.queryForObject("select platform from device_tokens where token = ?", String.class, token)).isEqualTo("ios");
        putJson("/api/devices", user, Map.of("token", TOKEN, "kind", "app", "environment", "development"))
                .andExpect(status().isBadRequest());
    }

    @Test void androidCannotRegisterAnApnsWidgetOrDevelopmentEnvironment() throws Exception {
        var user = signup("안드로이드");
        putJson("/api/devices", user, Map.of("token", TOKEN, "kind", "widget", "environment", "production", "platform", "android"))
                .andExpect(status().isBadRequest());
        putJson("/api/devices", user, Map.of("token", TOKEN, "kind", "app", "environment", "development", "platform", "android"))
                .andExpect(status().isBadRequest());
    }

    @Test void onePersonKeepsOnlyTheirNewestTokens() throws Exception {
        var user = signup("준서");
        for (int i = 0; i < 25; i++) {
            putJson("/api/devices", user, Map.of("token", "junk-token-" + i + "-" + "x".repeat(20), "kind", "app", "environment", "production", "platform", "android"))
                    .andExpect(status().isNoContent());
        }
        var kept = jdbc.queryForList("select token from device_tokens where user_id = ? order by updated_at desc, id desc", String.class, user.id());
        assertThat(kept).hasSize(10).first().asString().startsWith("junk-token-24-");
    }

    private void register(TestUser user) throws Exception {
        putJson("/api/devices", user, Map.of("token", TOKEN, "kind", "app", "environment", "production", "platform", "android"))
                .andExpect(status().isNoContent());
    }
}
