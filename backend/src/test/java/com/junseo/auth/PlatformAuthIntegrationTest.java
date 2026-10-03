package com.junseo.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.junseo.common.ApiException;
import com.junseo.support.IntegrationTest;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "junseo.auth.mode=platform")
class PlatformAuthIntegrationTest extends IntegrationTest {
    private static final RSAKey KEY;
    private static final HttpServer JWKS;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            JWKS.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("junseo.auth.jwks-url", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }
    @Autowired PlatformLoginFlow flow;
    @Autowired PlatformIdentityService identities;
    @Autowired JwtDecoder decoder;
    @Autowired UserRepository users;

    private static final class SocketInbox extends TextWebSocketHandler {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<CloseStatus> closed = new LinkedBlockingQueue<>();
        @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) { frames.add(message.getPayload()); }
        @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { closed.add(status); }
    }
    private WebSocketSession connect(String raw, SocketInbox inbox) throws Exception {
        WebSocketSession session = new StandardWebSocketClient().execute(inbox, new WebSocketHttpHeaders(),
                URI.create("ws://localhost:" + port + "/ws")).get(5, TimeUnit.SECONDS);
        session.sendMessage(new TextMessage(toJson(Map.of("type", "auth", "token", raw))));
        assertThat(inbox.frames.poll(5, TimeUnit.SECONDS)).contains("ready");
        return session;
    }
    private String token(String subject, String issuer, String audience, Instant expiry, boolean jti) throws Exception {
        var claims = new JWTClaimsSet.Builder().subject(subject).issuer(issuer).audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(1))).expirationTime(Date.from(expiry))
                .claim("email", "same@example.com");
        if (jti) claims.jwtID(UUID.randomUUID().toString());
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }
    private String token(String subject) throws Exception {
        return token(subject, "auth.liliplanet.net", "junseo-api", Instant.now().plusSeconds(600), true);
    }
    private PlatformLoginFlow.Callback callback(String raw, String verifier) {
        var start = flow.start(PlatformLoginFlow.challenge(verifier), "junseo://auth");
        var launch = flow.launch(start.state());
        return flow.callback(start.state(), launch.cookie(), raw);
    }

    @Test void completeLoginMapsIdentityOnboardsAndRevokesSession() throws Exception {
        String raw = token("central-123"), verifier = "v".repeat(64);
        var grant = callback(raw, verifier);
        String result = body(mvc.perform(post("/api/auth/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("code", grant.code(), "codeVerifier", verifier))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.needsOnboarding").value(true))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn());
        long id = longAt(result, "$.user.id");
        assertThat(identities.require(decoder.decode(raw))).isEqualTo(id);
        mvc.perform(patch("/api/me").header("Authorization", "Bearer " + raw).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("displayName", "친구")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.needsOnboarding").value(false));
        SocketInbox inbox = new SocketInbox();
        WebSocketSession socket = connect(raw, inbox);
        try {
            mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + raw)).andExpect(status().isNoContent());
            assertThat(inbox.closed.poll(5, TimeUnit.SECONDS)).isNotNull().extracting(CloseStatus::getCode).isEqualTo(1008);
        } finally { if (socket.isOpen()) socket.close(); }
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + raw)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> callback(raw, verifier)).isInstanceOf(ApiException.class);
    }

    @Test void websocketClosesAtCentralTokenExpiryWithoutWaitingForPing() throws Exception {
        String raw = token("central-expiring", "auth.liliplanet.net", "junseo-api", Instant.now().plusSeconds(5), true);
        identities.provision(decoder.decode(raw));
        SocketInbox inbox = new SocketInbox();
        WebSocketSession socket = connect(raw, inbox);
        try {
            assertThat(inbox.closed.poll(7, TimeUnit.SECONDS)).isNotNull().extracting(CloseStatus::getCode).isEqualTo(1008);
        } finally { if (socket.isOpen()) socket.close(); }
    }

    @Test void codeRequiresVerifierAndCannotBeReplayed() throws Exception {
        String verifier = "z".repeat(64), raw = token("central-456");
        var grant = callback(raw, verifier);
        assertThat(grant.code()).doesNotContain(raw);
        assertThatThrownBy(() -> flow.exchange(grant.code(), "wrong".repeat(12))).isInstanceOf(ApiException.class);
        assertThat(flow.exchange(grant.code(), verifier).getTokenValue()).isEqualTo(raw);
        assertThatThrownBy(() -> flow.exchange(grant.code(), verifier)).isInstanceOf(ApiException.class);
    }

    @Test void callbackIsBoundToBrowserAndLaunchIsSingleUse() throws Exception {
        String raw = token("central-789");
        var start = flow.start(PlatformLoginFlow.challenge("a".repeat(64)), "junseo://auth");
        var launch = flow.launch(start.state());
        assertThat(launch.loginUrl()).startsWith("https://login.liliplanet.net?client=junseo-api&redirect=");
        assertThatThrownBy(() -> flow.launch(start.state())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> flow.callback(start.state(), "different-cookie", raw)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> flow.callback(start.state(), null, raw)).isInstanceOf(ApiException.class);
        var grant = flow.callback(start.state(), launch.cookie(), raw);
        assertThat(grant.returnUrl()).isEqualTo("junseo://auth");
        assertThatThrownBy(() -> flow.callback(start.state(), launch.cookie(), raw)).isInstanceOf(ApiException.class);
    }

    @Test void launchSetsHttpOnlyCookieAndDoesNotExposeTokenInAppRedirect() throws Exception {
        String verifier = "b".repeat(64), raw = token("central-browser");
        var start = flow.start(PlatformLoginFlow.challenge(verifier), "junseo://auth");
        var launch = mvc.perform(get("/auth/launch").param("state", start.state()))
                .andExpect(status().isFound()).andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn();
        String setCookie = launch.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("HttpOnly", "SameSite=Lax");
        String cookie = setCookie.split(";")[0].substring("junseo_login=".length());
        var callback = mvc.perform(get("/auth/callback").param("state", start.state()).param("token", raw)
                .cookie(new Cookie("junseo_login", cookie))).andExpect(status().isFound()).andReturn();
        assertThat(callback.getResponse().getHeader("Location")).startsWith("junseo://auth?code=").doesNotContain(raw, "token=");
    }

    @Test void rejectsWrongIssuerAudienceExpiryMissingIdAndSignature() throws Exception {
        Instant future = Instant.now().plusSeconds(600);
        for (String raw : List.of(token("x", "evil.example", "junseo-api", future, true),
                token("x", "auth.liliplanet.net", "another-api", future, true),
                token("x", "auth.liliplanet.net", "junseo-api", Instant.now().minusSeconds(120), true),
                token("x", "auth.liliplanet.net", "junseo-api", future, false))) {
            assertThatThrownBy(() -> decoder.decode(raw)).isInstanceOf(JwtException.class);
        }
        String raw = token("signed");
        String[] parts = raw.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        String forged = parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test void neverLinksLocalUserByMatchingEmailOrNumericSubject() throws Exception {
        User local = users.saveAndFlush(new User("same@example.com", "unused", "local", "ABCDEFGH", Instant.now()));
        Jwt jwt = decoder.decode(token(Long.toString(local.getId())));
        assertThatThrownBy(() -> identities.require(jwt)).isInstanceOf(ApiException.class);
        User central = identities.provision(jwt);
        assertThat(central.getId()).isNotEqualTo(local.getId());
        assertThat(identities.provision(jwt).getId()).isEqualTo(central.getId());
        assertThat(users.findByEmail("same@example.com").orElseThrow().getId()).isEqualTo(local.getId());
    }

    @Test void rejectsExternalReturnUriAndLocalPasswordEndpointsAreUnavailable() throws Exception {
        assertThatThrownBy(() -> flow.start(PlatformLoginFlow.challenge("c".repeat(64)), "https://evil.example/auth")).isInstanceOf(ApiException.class);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }
}
