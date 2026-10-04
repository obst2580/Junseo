package com.junseo.push;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/** FCM HTTP v1 data messages reach the native widget worker even when React Native is asleep. */
public class FcmPushSender implements PushSender {
    @FunctionalInterface
    public interface AccessToken {
        String get() throws IOException;

        /** FCM said 401: the cached OAuth token is no good (revoked or rotated key) even if it has not expired yet. */
        default void invalidate() throws IOException {}
    }

    private static final String FCM_ERROR = "type.googleapis.com/google.firebase.fcm.v1.FcmError";
    private static final String BAD_REQUEST = "type.googleapis.com/google.rpc.BadRequest";

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private final ApnsTransport transport;
    private final AccessToken accessToken;
    private final URI endpoint;
    private final ObjectMapper json;

    public FcmPushSender(ApnsTransport transport, AccessToken accessToken, String projectId, ObjectMapper json) {
        if (!projectId.matches("[a-z][a-z0-9-]{4,61}[a-z0-9]")) throw new IllegalArgumentException("Invalid FCM project ID");
        this.transport = transport;
        this.accessToken = accessToken;
        this.endpoint = URI.create("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send");
        this.json = json;
    }

    @Override
    public PushOutcome send(PushMessage message) {
        try {
            var data = new LinkedHashMap<String, String>();
            var android = new LinkedHashMap<String, Object>();
            if (message.type() == PushType.WIDGETS) {
                data.put("type", "widget-refresh");
                android.put("priority", "NORMAL");
                android.put("ttl", "900s");
                android.put("collapse_key", "junseo-widget");
            } else {
                var payload = json.readTree(message.payload());
                var alert = payload.path("aps").path("alert");
                data.put("title", alert.path("title").asString("Junseo"));
                data.put("message", alert.path("body").asString(""));
                data.put("channelId", "junseo");
                data.put("sound", "default");
                data.put("tag", payload.path("aps").path("thread-id").asString("junseo"));
                var appData = new LinkedHashMap<String, Object>();
                payload.properties().forEach(e -> {
                    if (!e.getKey().equals("aps")) appData.put(e.getKey(), e.getValue());
                });
                data.put("body", json.writeValueAsString(appData));
                data.put("type", payload.path("type").asString("moment"));
                android.put("priority", "HIGH");
                android.put("ttl", "86400s");
            }
            var request = json.writeValueAsBytes(Map.of("message", Map.of("token", message.token(), "data", data, "android", android)));
            var response = post(request);
            if (response.status() == 401) {
                // Once: refresh the OAuth token and try again
                accessToken.invalidate();
                response = post(request);
            }
            if (response.status() >= 200 && response.status() < 300) return PushOutcome.SENT;
            if (deadToken(response)) return PushOutcome.INVALID_TOKEN;
            log.warn("FCM delivery failed: status={}", response.status());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            log.warn("FCM delivery failed: {}", e.getClass().getSimpleName());
        }
        return PushOutcome.FAILED;
    }

    private ApnsTransport.Response post(byte[] request) throws IOException, InterruptedException {
        return transport.post(endpoint, Map.of("Authorization", "Bearer " + accessToken.get(), "Content-Type", "application/json"), request);
    }

    /**
     * Tokens that will never work again and should be deleted (like APNs BadDeviceToken):
     * UNREGISTERED (404, app uninstalled), SENDER_ID_MISMATCH (403, token from another Firebase project) and
     * INVALID_ARGUMENT that names message.token (400, not a token at all). Other 400s are about the payload: keep the token.
     */
    private boolean deadToken(ApnsTransport.Response response) {
        if (response.status() != 400 && response.status() != 403 && response.status() != 404) return false;
        tools.jackson.databind.JsonNode details;
        try {
            details = json.readTree(response.body() == null ? "{}" : response.body()).path("error").path("details");
        } catch (RuntimeException notJson) {
            return false; // e.g. an HTML error page from a proxy: not FCM's verdict on the token
        }
        String code = null;
        boolean tokenField = false;
        for (var detail : details) {
            String type = detail.path("@type").asString("");
            if (type.equals(FCM_ERROR)) code = detail.path("errorCode").asString("");
            if (type.equals(BAD_REQUEST)) {
                for (var violation : detail.path("fieldViolations")) {
                    if ("message.token".equals(violation.path("field").asString(""))) tokenField = true;
                }
            }
        }
        return switch (response.status()) {
            case 404 -> "UNREGISTERED".equals(code);
            case 403 -> "SENDER_ID_MISMATCH".equals(code);
            default -> "INVALID_ARGUMENT".equals(code) && tokenField;
        };
    }
}
