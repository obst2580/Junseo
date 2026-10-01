package com.junseo.push;

import com.junseo.common.JunseoProperties;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class PushConfig {

    @Bean
    @ConditionalOnProperty(prefix = "junseo.apns", name = "enabled", havingValue = "true")
    PushSender apnsPushSender(JunseoProperties props, ObjectMapper json, Clock clock) {
        JunseoProperties.Apns apns = props.apns();
        require("key-id", apns.keyId());
        require("team-id", apns.teamId());
        require("bundle-id", apns.bundleId());
        require("key-path", apns.keyPath());
        ApnsTokenProvider tokens = new ApnsTokenProvider(
                ApnsTokenProvider.loadP8(Path.of(apns.keyPath())), apns.keyId(), apns.teamId(), clock);
        return new ApnsPushSender(ApnsTransport.http2(), tokens, apns.bundleId(), json);
    }

    @Bean
    @ConditionalOnProperty(prefix = "junseo.apns", name = "enabled", havingValue = "false", matchIfMissing = true)
    PushSender loggingPushSender() {
        return new LoggingPushSender();
    }

    private static void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("junseo.apns." + name + " is required when junseo.apns.enabled=true");
        }
    }
}
