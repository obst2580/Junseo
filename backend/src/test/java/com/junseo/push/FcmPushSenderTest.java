package com.junseo.push;

import static org.assertj.core.api.Assertions.assertThat;
import com.junseo.push.PushSender.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FcmPushSenderTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String> bodies = new ArrayList<>();
    private ApnsTransport.Response response = new ApnsTransport.Response(200, "{}");
    private final FcmPushSender sender = new FcmPushSender((uri, headers, body) -> {
        assertThat(uri).isEqualTo(URI.create("https://fcm.googleapis.com/v1/projects/junseo-test/messages:send"));
        assertThat(headers).containsEntry("Authorization", "Bearer provider-token");
        bodies.add(new String(body, StandardCharsets.UTF_8));
        return response;
    }, () -> "provider-token", "junseo-test", json);

    @Test void dataAlertPreservesCaseSensitiveRecipientAndExpoNavigationData() {
        assertThat(sender.send(message(PushType.ALERT,
                "{\"aps\":{\"alert\":{\"body\":\"안녕\"},\"thread-id\":\"message-42\"},\"type\":\"message\",\"peerId\":42}")))
                .isEqualTo(PushOutcome.SENT);
        var request = json.readTree(bodies.getFirst()).path("message");
        assertThat(request.path("token").asString()).isEqualTo("FCM:AbCd_123456789");
        assertThat(request.path("notification").isMissingNode()).isTrue();
        assertThat(request.path("data").path("message").asString()).isEqualTo("안녕");
        assertThat(json.readTree(request.path("data").path("body").asString()).path("peerId").asInt()).isEqualTo(42);
        assertThat(request.path("android").path("priority").asString()).isEqualTo("HIGH");
    }

    @Test void widgetRefreshIsSilentAndCollapsible() {
        sender.send(message(PushType.WIDGETS, "{}"));
        var request = json.readTree(bodies.getFirst()).path("message");
        assertThat(request.path("data").size()).isEqualTo(1);
        assertThat(request.path("data").path("type").asString()).isEqualTo("widget-refresh");
        assertThat(request.path("android").path("priority").asString()).isEqualTo("NORMAL");
        assertThat(request.path("android").path("collapse_key").asString()).isEqualTo("junseo-widget");
    }

    @Test void onlyExplicitUnregisteredErrorsRemoveDeviceTokens() {
        response = new ApnsTransport.Response(404, "{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\"UNREGISTERED\"}]}}");
        assertThat(sender.send(message(PushType.WIDGETS, "{}"))).isEqualTo(PushOutcome.INVALID_TOKEN);
        for (int status : List.of(400, 401, 403, 404, 429, 500)) {
            response = new ApnsTransport.Response(status, "{}");
            assertThat(sender.send(message(PushType.WIDGETS, "{}"))).isEqualTo(PushOutcome.FAILED);
        }
    }

    @Test void tokensFromAnotherProjectOrNotATokenAreRemovedButPayloadErrorsAreNot() {
        response = new ApnsTransport.Response(403, "{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\"SENDER_ID_MISMATCH\"}]}}");
        assertThat(sender.send(message(PushType.WIDGETS, "{}"))).isEqualTo(PushOutcome.INVALID_TOKEN);
        response = new ApnsTransport.Response(400, "{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\"INVALID_ARGUMENT\"},"
                + "{\"@type\":\"type.googleapis.com/google.rpc.BadRequest\",\"fieldViolations\":[{\"field\":\"message.token\",\"description\":\"Invalid registration token\"}]}]}}");
        assertThat(sender.send(message(PushType.WIDGETS, "{}"))).isEqualTo(PushOutcome.INVALID_TOKEN);
        // INVALID_ARGUMENT about the payload, or a proxy's HTML page: the token may be fine
        response = new ApnsTransport.Response(400, "{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\"INVALID_ARGUMENT\"},"
                + "{\"@type\":\"type.googleapis.com/google.rpc.BadRequest\",\"fieldViolations\":[{\"field\":\"message.data\"}]}]}}");
        assertThat(sender.send(message(PushType.WIDGETS, "{}"))).isEqualTo(PushOutcome.FAILED);
        response = new ApnsTransport.Response(403, "<html>Forbidden</html>");
        assertThat(sender.send(message(PushType.WIDGETS, "{}"))).isEqualTo(PushOutcome.FAILED);
    }

    @Test void unauthorizedRefreshesTheOAuthTokenAndRetriesOnce() {
        List<String> used = new ArrayList<>();
        String[] current = {"stale"};
        var retrying = new FcmPushSender((uri, headers, body) -> {
            used.add(headers.get("Authorization"));
            return headers.get("Authorization").equals("Bearer stale") ? new ApnsTransport.Response(401, "{}") : new ApnsTransport.Response(200, "{}");
        }, new FcmPushSender.AccessToken() {
            @Override public String get() { return current[0]; }
            @Override public void invalidate() { current[0] = "fresh"; }
        }, "junseo-test", json);
        assertThat(retrying.send(message(PushType.WIDGETS, "{}"))).isEqualTo(PushOutcome.SENT);
        assertThat(used).containsExactly("Bearer stale", "Bearer fresh");
    }

    @Test void credentialNetworkFailureDoesNotFailTheDomainEvent() {
        var unavailable = new FcmPushSender((uri, headers, body) -> { throw new IOException(); },
                () -> "token", "junseo-test", json);
        assertThat(unavailable.send(message(PushType.WIDGETS, "{}"))).isEqualTo(PushOutcome.FAILED);
    }

    @Test void platformRouterUsesRecordedPlatformRatherThanTokenShape() {
        List<String> delivered = new ArrayList<>();
        var router = new PlatformPushSender(m -> { delivered.add("ios"); return PushOutcome.SENT; },
                m -> { delivered.add("android"); return PushOutcome.SENT; });
        router.send(message(PushType.WIDGETS, "{}"));
        router.send(new PushMessage(1, "FCM-looking-token", "production", PushType.ALERT, "{}"));
        assertThat(delivered).containsExactly("android", "ios");
    }

    private PushMessage message(PushType type, String payload) {
        return new PushMessage(1, "FCM:AbCd_123456789", "production", type, payload, "android");
    }
}
