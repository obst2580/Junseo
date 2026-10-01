package com.junseo.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.junseo.push.PushSender.PushMessage;
import com.junseo.push.PushSender.PushOutcome;
import com.junseo.push.PushSender.PushType;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** APNs request building and response handling against a fake HTTP layer; nothing touches the network. */
class ApnsPushSenderTest {

    private static final String BUNDLE = "app.junseo";
    private static final String TOKEN = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";

    private record Request(URI uri, Map<String, String> headers, String body) {}

    private final List<Request> requests = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T12:00:00Z"));
    private ApnsTransport.Response nextResponse = new ApnsTransport.Response(200, "");
    private IOException nextFailure;
    private ECPublicKey publicKey;
    private ApnsPushSender sender;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keys = generator.generateKeyPair();
        publicKey = (ECPublicKey) keys.getPublic();
        Path p8 = tmp.resolve("AuthKey_TEST.p8");
        Files.writeString(p8, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");

        ApnsTokenProvider tokens = new ApnsTokenProvider(ApnsTokenProvider.loadP8(p8), "KEY123", "TEAM456", clock);
        ApnsTransport fake = (uri, headers, body) -> {
            requests.add(new Request(uri, headers, new String(body, StandardCharsets.UTF_8)));
            if (nextFailure != null) {
                throw nextFailure;
            }
            return nextResponse;
        };
        sender = new ApnsPushSender(fake, tokens, BUNDLE, JsonMapper.builder().build());
    }

    @Test
    void alertGoesToSandboxForDevelopmentTokensWithAlertHeaders() throws Exception {
        String payload = "{\"aps\":{\"alert\":{\"body\":\"hi\"}}}";
        assertThat(sender.send(message("development", PushType.ALERT, payload))).isEqualTo(PushOutcome.SENT);

        Request request = requests.getFirst();
        assertThat(request.uri()).hasToString("https://api.sandbox.push.apple.com/3/device/" + TOKEN);
        assertThat(request.headers())
                .containsEntry("apns-push-type", "alert")
                .containsEntry("apns-topic", BUNDLE)
                .containsEntry("apns-priority", "10");
        assertThat(request.body()).isEqualTo(payload);

        SignedJWT jwt = SignedJWT.parse(request.headers().get("authorization").substring("bearer ".length()));
        assertThat(jwt.verify(new ECDSAVerifier(publicKey))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEY123");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("TEAM456");
        assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(clock.instant());
    }

    @Test
    void widgetPushUsesWidgetTopicAndProductionHost() {
        sender.send(message("production", PushType.WIDGETS, PushNotifier.WIDGET_PAYLOAD));

        Request request = requests.getFirst();
        assertThat(request.uri()).hasToString("https://api.push.apple.com/3/device/" + TOKEN);
        assertThat(request.headers())
                .containsEntry("apns-push-type", "widgets")
                .containsEntry("apns-topic", BUNDLE + ".push-type.widgets")
                .containsEntry("apns-priority", "5");
        assertThat(request.body()).isEqualTo("{\"aps\":{\"content-changed\":true}}");
    }

    @Test
    void deadTokenResponsesAreReportedAsInvalid() {
        assertThat(outcomeFor(410, "{\"reason\":\"Unregistered\",\"timestamp\":1790000000000}")).isEqualTo(PushOutcome.INVALID_TOKEN);
        assertThat(outcomeFor(410, "")).isEqualTo(PushOutcome.INVALID_TOKEN);
        assertThat(outcomeFor(400, "{\"reason\":\"BadDeviceToken\"}")).isEqualTo(PushOutcome.INVALID_TOKEN);
        assertThat(outcomeFor(400, "{\"reason\":\"Unregistered\"}")).isEqualTo(PushOutcome.INVALID_TOKEN);
    }

    @Test
    void otherFailuresKeepTheToken() {
        assertThat(outcomeFor(400, "{\"reason\":\"PayloadTooLarge\"}")).isEqualTo(PushOutcome.FAILED);
        assertThat(outcomeFor(429, "{\"reason\":\"TooManyRequests\"}")).isEqualTo(PushOutcome.FAILED);
        assertThat(outcomeFor(500, "not json")).isEqualTo(PushOutcome.FAILED);
        nextFailure = new IOException("connection reset");
        assertThat(sender.send(message("development", PushType.ALERT, "{}"))).isEqualTo(PushOutcome.FAILED);
    }

    @Test
    void providerTokenIsCachedThenRefreshed() {
        sender.send(message("development", PushType.ALERT, "{}"));
        clock.advance(Duration.ofMinutes(49));
        sender.send(message("development", PushType.ALERT, "{}"));
        assertThat(bearer(1)).isEqualTo(bearer(0));

        clock.advance(Duration.ofMinutes(2));
        sender.send(message("development", PushType.ALERT, "{}"));
        assertThat(bearer(2)).isNotEqualTo(bearer(1));

        nextResponse = new ApnsTransport.Response(403, "{\"reason\":\"ExpiredProviderToken\"}");
        sender.send(message("development", PushType.ALERT, "{}"));
        nextResponse = new ApnsTransport.Response(200, "");
        clock.advance(Duration.ofSeconds(1));
        sender.send(message("development", PushType.ALERT, "{}"));
        assertThat(bearer(4)).isNotEqualTo(bearer(3));
    }

    private PushOutcome outcomeFor(int status, String body) {
        nextResponse = new ApnsTransport.Response(status, body);
        return sender.send(message("development", PushType.ALERT, "{}"));
    }

    private String bearer(int index) {
        return requests.get(index).headers().get("authorization");
    }

    private static PushMessage message(String environment, PushType type, String payload) {
        return new PushMessage(1L, TOKEN, environment, type, payload);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
