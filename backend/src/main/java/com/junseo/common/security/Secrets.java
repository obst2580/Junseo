package com.junseo.common.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Secrets {

    private static final Logger log = LoggerFactory.getLogger(Secrets.class);
    private static final int MIN_BYTES = 32;

    private Secrets() {}

    /** HMAC key from a configured secret; refuses short secrets and warns about the bundled dev defaults. */
    public static SecretKey hmacKey(String property, String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES) {
            throw new IllegalStateException(property + " must be at least " + MIN_BYTES + " bytes");
        }
        if (secret.startsWith("dev-only-")) {
            log.warn("{} is using the bundled development default. Set a real secret outside local development.", property);
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
