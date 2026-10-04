package com.junseo.common;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("junseo")
public record JunseoProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue Media media,
        @DefaultValue Storage storage,
        @DefaultValue Cors cors,
        @DefaultValue Apns apns,
        @DefaultValue Fcm fcm,
        @DefaultValue Admin admin,
        @DefaultValue Auth auth,
        @DefaultValue Mail mail) {

    public record Jwt(String secret, @DefaultValue("30d") Duration ttl) {}

    public record Media(String signingSecret) {}

    public record Storage(@DefaultValue("./data/media") String dir,
            @DefaultValue("local") String provider, String endpoint,
            @DefaultValue("media") String container) {}

    public record Cors(@DefaultValue List<String> extraOrigins) {}

    public record Apns(boolean enabled, String keyId, String teamId, String bundleId, String keyPath) {}

    public record Fcm(boolean enabled, String projectId, String serviceAccountJson) {}

    /** token: X-Admin-Token for templates/reports. Blank disables admin endpoints. */
    public record Admin(String token, String email) {}

    public record Auth(
            @DefaultValue("local") String mode,
            @DefaultValue("auth.liliplanet.net") String issuer,
            @DefaultValue("https://auth.liliplanet.net/.well-known/jwks.json") String jwksUrl,
            @DefaultValue("junseo-api") String audience,
            @DefaultValue("https://login.liliplanet.net") String loginUrl,
            @DefaultValue("http://localhost:8080") String publicBaseUrl,
            @DefaultValue({"junseo://auth", "http://localhost:8081/auth-callback", "http://localhost:8080/login"}) List<String> returnUris) {}

    /** Sender address. SMTP itself is spring.mail.*. */
    public record Mail(@DefaultValue("잡다 <no-reply@junseo.app>") String from) {}
}
