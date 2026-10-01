package com.junseo.push;

import com.junseo.device.DeviceToken;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class ApnsPushSender implements PushSender {

    static final String PRODUCTION_HOST = "https://api.push.apple.com";
    static final String SANDBOX_HOST = "https://api.sandbox.push.apple.com";

    private static final Logger log = LoggerFactory.getLogger(ApnsPushSender.class);
    private static final Set<String> DEAD_TOKEN_REASONS = Set.of("BadDeviceToken", "Unregistered");
    private static final Set<String> PROVIDER_TOKEN_REASONS = Set.of("ExpiredProviderToken", "InvalidProviderToken");

    private final ApnsTransport transport;
    private final ApnsTokenProvider tokens;
    private final String bundleId;
    private final ObjectMapper json;

    public ApnsPushSender(ApnsTransport transport, ApnsTokenProvider tokens, String bundleId, ObjectMapper json) {
        this.transport = transport;
        this.tokens = tokens;
        this.bundleId = bundleId;
        this.json = json;
    }

    @Override
    public PushOutcome send(PushMessage m) {
        boolean widget = m.type() == PushType.WIDGETS;
        String host = DeviceToken.ENV_PRODUCTION.equals(m.environment()) ? PRODUCTION_HOST : SANDBOX_HOST;
        URI uri = URI.create(host + "/3/device/" + m.token());
        Map<String, String> headers = Map.of(
                "authorization", "bearer " + tokens.current(),
                "apns-push-type", widget ? "widgets" : "alert",
                "apns-topic", widget ? bundleId + ".push-type.widgets" : bundleId,
                "apns-priority", widget ? "5" : "10");
        try {
            ApnsTransport.Response response = transport.post(uri, headers, m.payload().getBytes(StandardCharsets.UTF_8));
            return interpret(response, m);
        } catch (IOException e) {
            log.warn("APNs request failed for user {}: {}", m.userId(), e.toString());
            return PushOutcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PushOutcome.FAILED;
        }
    }

    PushOutcome interpret(ApnsTransport.Response response, PushMessage m) {
        if (response.status() == 200) {
            return PushOutcome.SENT;
        }
        String reason = reason(response.body());
        if (response.status() == 410 || DEAD_TOKEN_REASONS.contains(reason)) {
            log.info("APNs token for user {} is no longer valid ({} {})", m.userId(), response.status(), reason);
            return PushOutcome.INVALID_TOKEN;
        }
        if (response.status() == 403 && PROVIDER_TOKEN_REASONS.contains(reason)) {
            tokens.invalidate();
        }
        log.warn("APNs rejected push for user {}: {} {}", m.userId(), response.status(), reason);
        return PushOutcome.FAILED;
    }

    private String reason(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = json.readTree(body).get("reason");
            return node == null ? "" : node.asString();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
