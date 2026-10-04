package com.junseo.common.security;

import com.junseo.auth.TokenRevocations;
import com.junseo.common.JunseoProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@ConditionalOnProperty(prefix = "junseo.auth", name = "mode", havingValue = "platform")
public class PlatformJwtConfig {
    /**
     * The LiliPlanet token, read once at login (callback and code exchange). API requests use Junseo's own token.
     * A token whose session was logged out cannot start another login.
     */
    @Bean
    JwtDecoder centralJwtDecoder(JunseoProperties props, TokenRevocations revocations) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.auth().jwksUrl())
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.auth().issuer()), claims(props.auth().audience()),
                token -> revocations.revoked(token.getId()) ? invalid() : OAuth2TokenValidatorResult.success()));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> claims(String audience) {
        return token -> token.getAudience().contains(audience)
                && token.getExpiresAt() != null && validId(token.getSubject()) && validId(token.getId())
                ? OAuth2TokenValidatorResult.success() : invalid();
    }

    private static boolean validId(String value) {
        return value != null && !value.isBlank() && value.length() <= 200;
    }

    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid Junseo token", null));
    }
}
