package com.junseo.common.security;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/** Signs Junseo's own session tokens (HS256) in both login modes; {@link Sessions} decides what goes in them. */
@Component
public class JwtService {

    public record IssuedToken(String value, Instant expiresAt) {}

    private final JwtEncoder encoder;

    public JwtService(SecretKey jwtSecretKey) {
        this.encoder = NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * sub: Junseo user id. sid: the session it belongs to (checked on every request, so it can be ended).
     * ver: the user's token version (a password change ends every earlier login). auth_time: when the person actually
     * signed in, kept across renewals, so a renewed token never counts as a fresh login (account deletion).
     */
    public IssuedToken issue(long userId, int version, UUID session, Instant authenticatedAt, Instant now, Duration ttl) {
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(Sessions.SESSION_CLAIM, session.toString())
                .claim(Sessions.VERSION_CLAIM, version)
                .claim(Sessions.AUTH_TIME_CLAIM, authenticatedAt.getEpochSecond())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
