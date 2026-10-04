package com.junseo.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.junseo.media.MediaStorage.Variant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class MediaUrlSignerTest {

    private static final byte[] SECRET = "unit-test-secret-0123456789abcdefghijkl".getBytes(StandardCharsets.UTF_8);

    private static MediaUrlSigner at(String instant) {
        return new MediaUrlSigner(new SecretKeySpec(SECRET, "HmacSHA256"), Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void expiryIsSevenDaysRoundedUpToTheNextUtcDay() {
        assertThat(MediaUrlSigner.expiry(Instant.parse("2026-09-30T12:34:56Z")))
                .isEqualTo(Instant.parse("2026-10-08T00:00:00Z").getEpochSecond());
        assertThat(MediaUrlSigner.expiry(Instant.parse("2026-09-30T00:00:01Z")))
                .isEqualTo(Instant.parse("2026-10-08T00:00:00Z").getEpochSecond());
        assertThat(MediaUrlSigner.expiry(Instant.parse("2026-09-30T00:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-07T00:00:00Z").getEpochSecond());
    }

    @Test
    void urlsAreStableWithinADay() {
        assertThat(at("2026-09-30T00:10:00Z").url(301, Variant.THUMB)).isEqualTo(at("2026-09-30T23:59:59Z").url(301, Variant.THUMB));
        assertThat(at("2026-09-30T23:59:59Z").url(301, Variant.THUMB)).isNotEqualTo(at("2026-10-01T00:00:01Z").url(301, Variant.THUMB));
    }

    @Test
    void signatureIsBase64UrlHmacOfPathAndExpiry() throws Exception {
        String url = at("2026-09-30T12:00:00Z").url(301, Variant.FULL);
        long exp = Instant.parse("2026-10-08T00:00:00Z").getEpochSecond();

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(("301/full:" + exp).getBytes(StandardCharsets.UTF_8)));

        assertThat(url).isEqualTo("/media/301/full.jpg?exp=" + exp + "&sig=" + expected);
        assertThat(expected).doesNotContain("=", "+", "/");
    }

    @Test
    void verifyRejectsTamperingAndExpiry() {
        MediaUrlSigner signer = at("2026-09-30T12:00:00Z");
        long exp = MediaUrlSigner.expiry(Instant.parse("2026-09-30T12:00:00Z"));
        String sig = signer.signature(301, "thumb", exp);

        assertThat(signer.verify(301, "thumb", exp, sig)).isTrue();
        assertThat(signer.verify(302, "thumb", exp, sig)).isFalse();
        assertThat(signer.verify(301, "full", exp, sig)).isFalse();
        assertThat(signer.verify(301, "thumb", exp + 1, sig)).isFalse();
        assertThat(signer.verify(301, "thumb", exp, sig.substring(1))).isFalse();
        assertThat(signer.verify(301, "thumb", exp, null)).isFalse();

        long past = Instant.parse("2026-09-30T11:59:59Z").getEpochSecond();
        assertThat(signer.verify(301, "thumb", past, signer.signature(301, "thumb", past))).isFalse();
        assertThat(at("2026-10-08T00:00:00Z").verify(301, "thumb", exp, sig)).isFalse();
    }
}
