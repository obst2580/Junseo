package com.junseo.media;

import com.junseo.common.JunseoProperties;
import com.junseo.common.security.Secrets;
import com.junseo.media.MediaStorage.Variant;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Signed relative media paths: {@code /media/{id}/{variant}.jpg?exp=..&sig=..} where
 * {@code sig = base64url(HMAC-SHA256("{id}/{variant}:{exp}"))}.
 */
@Component
public class MediaUrlSigner {

    static final long DAY_SECONDS = 86_400;
    static final long TTL_SECONDS = 7 * DAY_SECONDS;

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public MediaUrlSigner(JunseoProperties props, Clock clock) {
        this(Secrets.hmacKey("junseo.media.signing-secret", props.media().signingSecret()), clock);
    }

    MediaUrlSigner(SecretKey key, Clock clock) {
        this.key = key;
        this.clock = clock;
    }

    public String url(long momentId, Variant variant) {
        long exp = expiry(clock.instant());
        return "/media/" + momentId + "/" + variant.id() + ".jpg?exp=" + exp + "&sig=" + signature(momentId, variant.id(), exp);
    }

    /** Now + 7 days rounded up to a UTC day boundary, so every URL issued on one day is identical (cacheable). */
    static long expiry(Instant now) {
        return Math.ceilDiv(now.getEpochSecond() + TTL_SECONDS, DAY_SECONDS) * DAY_SECONDS;
    }

    public String signature(long momentId, String variant, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal((momentId + "/" + variant + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean verify(long momentId, String variant, long exp, String sig) {
        if (sig == null || exp <= clock.instant().getEpochSecond()) {
            return false;
        }
        byte[] expected = signature(momentId, variant, exp).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII));
    }
}
