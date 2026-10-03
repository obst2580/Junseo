package com.junseo.push;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * ES256 provider token for APNs token-based auth. Apple rejects tokens older than an hour and
 * throttles refreshes more often than every 20 minutes, so one token is reused for 50 minutes.
 */
public class ApnsTokenProvider {

    static final Duration REFRESH_AFTER = Duration.ofMinutes(50);

    private final ECPrivateKey key;
    private final String keyId;
    private final String teamId;
    private final Clock clock;
    private String token;
    private Instant issuedAt;

    public ApnsTokenProvider(ECPrivateKey key, String keyId, String teamId, Clock clock) {
        this.key = key;
        this.keyId = keyId;
        this.teamId = teamId;
        this.clock = clock;
    }

    public synchronized String current() {
        Instant now = clock.instant();
        if (token == null || !now.isBefore(issuedAt.plus(REFRESH_AFTER))) {
            token = sign(now);
            issuedAt = now;
        }
        return token;
    }

    /** Called when APNs reports the token as expired or invalid. */
    public synchronized void invalidate() {
        token = null;
    }

    private String sign(Instant now) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(),
                    new JWTClaimsSet.Builder().issuer(teamId).issueTime(Date.from(now)).build());
            jwt.sign(new ECDSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign APNs provider token", e);
        }
    }

    /** Reads the PKCS#8 .p8 key downloaded from the Apple developer portal. */
    public static ECPrivateKey loadP8(Path path) {
        try {
            String pem = Files.readString(path)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("Could not read APNs key " + path, e);
        }
    }
}
