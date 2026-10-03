package com.junseo.common.security;

import com.junseo.common.JunseoProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "junseo.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class JwtService {

    public record IssuedToken(String value, Instant expiresAt) {}

    private final JwtEncoder encoder;
    private final JunseoProperties props;
    private final Clock clock;

    public JwtService(SecretKey jwtSecretKey, JunseoProperties props, Clock clock) {
        this.encoder = NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
        this.props = props;
        this.clock = clock;
    }

    /** version: the user's token version ({@link Sessions}); a password change makes older tokens invalid. */
    public IssuedToken issue(long userId, int version) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(props.jwt().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(Sessions.VERSION_CLAIM, version)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
