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
    public interface AccessToken { String get() throws IOException; }

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
            var request = Map.of("message", Map.of("token", message.token(), "data", data, "android", android));
            var response = transport.post(endpoint,
                    Map.of("Authorization", "Bearer " + accessToken.get(), "Content-Type", "application/json"),
                    json.writeValueAsBytes(request));
            if (response.status() >= 200 && response.status() < 300) return PushOutcome.SENT;
            if (response.status() == 404) {
                for (var detail : json.readTree(response.body()).path("error").path("details")) {
                    if (detail.path("@type").asString().equals("type.googleapis.com/google.firebase.fcm.v1.FcmError")
                            && detail.path("errorCode").asString().equals("UNREGISTERED")) return PushOutcome.INVALID_TOKEN;
                }
            }
            log.warn("FCM delivery failed: status={}", response.status());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            log.warn("FCM delivery failed: {}", e.getClass().getSimpleName());
        }
        return PushOutcome.FAILED;
    }
}
