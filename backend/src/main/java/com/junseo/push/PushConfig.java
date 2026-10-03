package com.junseo.push;

import com.junseo.common.JunseoProperties;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class PushConfig {

    @Bean
    PushSender pushSender(JunseoProperties props, ObjectMapper json, Clock clock) throws IOException {
        PushSender ios = new LoggingPushSender();
        if (props.apns().enabled()) {
            var apns = props.apns();
            require("apns.key-id", apns.keyId());
            require("apns.team-id", apns.teamId());
            require("apns.bundle-id", apns.bundleId());
            require("apns.key-path", apns.keyPath());
            var tokens = new ApnsTokenProvider(ApnsTokenProvider.loadP8(Path.of(apns.keyPath())),
                    apns.keyId(), apns.teamId(), clock);
            ios = new ApnsPushSender(ApnsTransport.http2(), tokens, apns.bundleId(), json);
        }
        PushSender android = new LoggingPushSender();
        if (props.fcm().enabled()) {
            var fcm = props.fcm();
            require("fcm.project-id", fcm.projectId());
            require("fcm.service-account-json", fcm.serviceAccountJson());
            var credentials = ServiceAccountCredentials.fromStream(new ByteArrayInputStream(
                    fcm.serviceAccountJson().getBytes(StandardCharsets.UTF_8)));
            if (!fcm.projectId().equals(credentials.getProjectId())) {
                throw new IllegalStateException("FCM service account must belong to junseo.fcm.project-id");
            }
            var scoped = credentials.createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
            android = new FcmPushSender(ApnsTransport.http2(), () -> {
                scoped.refreshIfExpired();
                return scoped.getAccessToken().getTokenValue();
            }, fcm.projectId(), json);
        }
        return new PlatformPushSender(ios, android);
    }

    private static void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("junseo." + name + " is required");
        }
    }
}
