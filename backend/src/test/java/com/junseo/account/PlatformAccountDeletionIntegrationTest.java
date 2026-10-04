package com.junseo.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.auth.PlatformLoginFlow;
import com.junseo.common.security.AdminAuth;
import com.junseo.support.IntegrationTest;
import com.junseo.support.PlatformTokens;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** LiliPlanet accounts have no password: deleting needs a session from a login made just now. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "junseo.auth.mode=platform")
class PlatformAccountDeletionIntegrationTest extends IntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("junseo.auth.jwks-url", PlatformTokens::jwksUrl);
    }

    @Autowired PlatformLoginFlow flow;

    /** The app's login: central token through the browser flow, exchanged for a Junseo session. */
    private ResultActions exchange(String central, boolean reauth) throws Exception {
        String verifier = "r".repeat(64);
        var start = flow.start(PlatformLoginFlow.challenge(verifier), "junseo://auth", UUID.randomUUID().toString());
        var launch = flow.launch(start.state());
        var callback = flow.callback(start.state(), launch.cookie(), central);
        return mvc.perform(post("/api/auth/exchange").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(reauth
                        ? Map.of("code", callback.code(), "codeVerifier", verifier, "reauth", true)
                        : Map.of("code", callback.code(), "codeVerifier", verifier))));
    }

    private String login(String central) throws Exception {
        return JsonPath.read(body(exchange(central, false).andExpect(status().isOk()).andReturn()), "$.accessToken");
    }

    private String reauth(String central) throws Exception {
        return JsonPath.read(body(exchange(central, true).andExpect(status().isOk()).andReturn()), "$.accessToken");
    }

    private long idOf(String session) throws Exception {
        return longAt(body(as(session, get("/api/me")).andExpect(status().isOk()).andReturn()), "$.id");
    }

    private ResultActions as(String session, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + session));
    }

    private ResultActions deleteWith(String session) throws Exception {
        return as(session, post("/api/me/delete"));
    }

    private static String centralSignedIn(String subject, int secondsAgo) {
        return PlatformTokens.token(subject, Instant.now().minusSeconds(secondsAgo), null);
    }

    @Test
    void freshLoginDeletesTheAccountAndEndsEverySession() throws Exception {
        String session = login(centralSignedIn("person-1", 30 * 60));
        long id = idOf(session);
        as(session, get("/api/me")).andExpect(jsonPath("$.loginMethod").value("platform"));

        String fresh = reauth(PlatformTokens.token("person-1"));
        // A password means nothing for this account; no body is needed
        deleteWith(fresh).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Long.class, id)).isZero();
        as(fresh, get("/api/me")).andExpect(status().isUnauthorized());
        as(session, get("/api/me")).andExpect(status().isUnauthorized());
        // Not banned: logging in again starts a new, empty account
        assertThat(idOf(login(PlatformTokens.token("person-1")))).isNotEqualTo(id);
    }

    @Test
    void oldLoginIsAskedToLogInAgain() throws Exception {
        String session = login(centralSignedIn("person-2", 10 * 60));
        long id = idOf(session);
        deleteWith(session).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Long.class, id)).isOne();
        // 403, not 401: the session itself is fine and the app must not sign out
        as(session, get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void aRenewedSessionNeverCountsAsAFreshLogin() throws Exception {
        String session = login(centralSignedIn("person-3", 10 * 60));
        String renewed = JsonPath.read(body(as(session, post("/api/auth/renew")).andExpect(status().isOk()).andReturn()), "$.accessToken");
        deleteWith(renewed).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
    }

    @Test
    void centralAuthTimeWinsOverIssuedAt() throws Exception {
        // Re-issued just now from a sign-in an hour ago (e.g. a silent refresh at LiliPlanet): not a fresh login
        String reissued = login(PlatformTokens.token("person-4", Instant.now().minusSeconds(1), Instant.now().minusSeconds(3600)));
        deleteWith(reissued).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        String signedIn = reauth(PlatformTokens.token("person-4", Instant.now().minusSeconds(1), Instant.now().minusSeconds(30)));
        deleteWith(signedIn).andExpect(status().isNoContent());
    }

    @Test
    void reauthReturnsTheExistingAccountButNeverCreatesOne() throws Exception {
        long id = idOf(login(PlatformTokens.token("person-6")));
        exchange(PlatformTokens.token("person-6"), true).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(id));
        long before = jdbc.queryForObject("select count(*) from users", Long.class);
        exchange(PlatformTokens.token("someone-else"), true)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REAUTH_NO_ACCOUNT"));
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(before);
        // A reauth session is short and cannot be renewed into a long one
        String confirm = reauth(PlatformTokens.token("person-6"));
        as(confirm, post("/api/auth/renew")).andExpect(status().isUnauthorized());
    }

    @Test
    void operatorRemovalKeepsThePersonOutUntilLifted() throws Exception {
        long id = idOf(login(PlatformTokens.token("person-7")));
        mvc.perform(delete("/api/admin/users/" + id).param("reason", "괴롭힘 신고").header(AdminAuth.HEADER, "test-admin-token"))
                .andExpect(status().isNoContent());

        exchange(PlatformTokens.token("person-7"), false).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));
        String bans = body(mvc.perform(get("/api/admin/bans").header(AdminAuth.HEADER, "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].externalSubject").value("person-7"))
                .andExpect(jsonPath("$.items[0].reason").value("괴롭힘 신고"))
                .andReturn());
        long ban = longAt(bans, "$.items[0].id");
        // Someone else is not affected
        login(PlatformTokens.token("person-8"));

        mvc.perform(delete("/api/admin/bans/" + ban).header(AdminAuth.HEADER, "test-admin-token")).andExpect(status().isNoContent());
        assertThat(idOf(login(PlatformTokens.token("person-7")))).isNotEqualTo(id);
    }
}
