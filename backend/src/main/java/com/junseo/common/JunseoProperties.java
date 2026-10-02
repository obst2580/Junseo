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
        @DefaultValue Admin admin,
        @DefaultValue Mail mail) {

    public record Jwt(String secret, @DefaultValue("30d") Duration ttl) {}

    public record Media(String signingSecret) {}

    public record Storage(@DefaultValue("./data/media") String dir) {}

    public record Cors(@DefaultValue List<String> extraOrigins) {}

    public record Apns(boolean enabled, String keyId, String teamId, String bundleId, String keyPath) {}

    /**
     * token: X-Admin-Token for /api/admin/** (templates, reports). Blank switches those endpoints off.
     * email: where new reports are mailed (blank: only logged).
     */
    public record Admin(String token, String email) {}

    /** from: sender of the app's mails (password reset codes, report alerts). SMTP itself is spring.mail.*. */
    public record Mail(@DefaultValue("잡다 <no-reply@junseo.app>") String from) {}
}
