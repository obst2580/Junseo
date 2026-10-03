package com.junseo.auth;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.JunseoProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/** Single-instance login transactions. Codes are short lived, single use and bound to S256 PKCE. */
@Service
@ConditionalOnProperty(prefix = "junseo.auth", name = "mode", havingValue = "platform")
public class PlatformLoginFlow {
    public record Start(String launchUrl, String state) {}
    public record Launch(String loginUrl, String cookie) {}
    public record Callback(String returnUrl, String code, String state) {}
    private record Transaction(String challenge, String returnUri, Instant expiresAt, String cookie) {}
    private record Grant(String challenge, Jwt jwt, Instant expiresAt) {}
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final JunseoProperties props;
    private final JwtDecoder decoder;
    private final Clock clock;

    public PlatformLoginFlow(JunseoProperties props, JwtDecoder decoder, Clock clock) {
        this.props = props;
        this.decoder = decoder;
        this.clock = clock;
    }

    public synchronized Start start(String challenge, String returnUri) {
        purge();
        if (challenge == null || !challenge.matches("[A-Za-z0-9_-]{43}") || !props.auth().returnUris().contains(returnUri)) throw invalid();
        if (transactions.size() >= 1000 || grants.size() >= 1000) throw invalid();
        String state = nonce();
        transactions.put(state, new Transaction(challenge, returnUri, clock.instant().plus(Duration.ofMinutes(10)), null));
        return new Start(base() + "/auth/launch?state=" + state, state);
    }

    public synchronized Launch launch(String state) {
        Transaction t = transaction(state);
        // A launch URL is used once; returning to it cannot replace the cookie for an existing flow.
        if (t.cookie() != null) throw invalid();
        String cookie = nonce();
        transactions.put(state, new Transaction(t.challenge(), t.returnUri(), t.expiresAt(), cookie));
        String callback = base() + "/auth/callback?state=" + encode(state);
        return new Launch(props.auth().loginUrl() + "?client=" + encode(props.auth().audience()) + "&redirect=" + encode(callback), cookie);
    }

    public synchronized Callback callback(String state, String cookie, String rawToken) {
        Transaction t = transaction(state);
        if (cookie == null || t.cookie() == null || !equal(cookie, t.cookie()) || rawToken == null || rawToken.length() > 16384) throw invalid();
        Jwt jwt;
        try {
            jwt = decoder.decode(rawToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw invalid();
        }
        transactions.remove(state);
        String code = nonce();
        grants.put(code, new Grant(t.challenge(), jwt, clock.instant().plusSeconds(60)));
        return new Callback(t.returnUri(), code, state);
    }

    public synchronized Jwt exchange(String code, String verifier) {
        purge();
        Grant grant = code == null ? null : grants.get(code);
        if (grant == null || verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}") || !equal(grant.challenge(), challenge(verifier))) throw invalid();
        grants.remove(code);
        // Recheck expiry and local revocation at exchange time.
        try { return decoder.decode(grant.jwt().getTokenValue()); }
        catch (JwtException e) { throw invalid(); }
    }

    public static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private Transaction transaction(String state) {
        purge();
        Transaction t = state == null ? null : transactions.get(state);
        if (t == null) throw invalid();
        return t;
    }

    private void purge() {
        Instant now = clock.instant();
        transactions.values().removeIf(t -> !t.expiresAt().isAfter(now));
        grants.values().removeIf(t -> !t.expiresAt().isAfter(now));
    }

    private String nonce() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String base() { return props.auth().publicBaseUrl().replaceAll("/+$", ""); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static boolean equal(String a, String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
    private static ApiException invalid() { return new ApiException(ErrorCode.UNAUTHORIZED, "로그인 연결이 만료되었거나 올바르지 않아요. 앱에서 다시 시작해 주세요."); }
}
