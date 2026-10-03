package com.junseo.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** A stand-in for the central LiliPlanet login: an RSA key served as JWKS, and tokens signed with it. */
public final class PlatformTokens {

    public static final String ISSUER = "auth.liliplanet.net";
    public static final String AUDIENCE = "junseo-api";

    private static final RSAKey KEY;
    private static final HttpServer JWKS;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-platform-key").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            JWKS.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private PlatformTokens() {}

    public static String jwksUrl() {
        return "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks";
    }

    /** issuedAt: when the central login issued the token; authTime: when the person signed in (null = no claim). */
    public static String token(String subject, Instant issuedAt, Instant authTime) {
        var claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(3600)))
                .jwtID(UUID.randomUUID().toString())
                .claim("email", subject + "@example.com");
        if (authTime != null) claims.claim("auth_time", authTime.getEpochSecond());
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims.build());
        try {
            jwt.sign(new RSASSASigner(KEY));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    public static String token(String subject) {
        return token(subject, Instant.now().minusSeconds(1), null);
    }
}
