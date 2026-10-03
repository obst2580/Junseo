package com.junseo.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.junseo.auth.PlatformIdentityService;
import com.junseo.auth.PlatformLoginFlow;
import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.security.AdminAuth;
import com.junseo.support.IntegrationTest;
import com.junseo.support.PlatformTokens;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** LiliPlanet accounts have no password: deleting needs a token from a login made just now. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "junseo.auth.mode=platform")
class PlatformAccountDeletionIntegrationTest extends IntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("junseo.auth.jwks-url", PlatformTokens::jwksUrl);
    }

    @Autowired PlatformIdentityService identities;
    @Autowired PlatformLoginFlow flow;
    @Autowired JwtDecoder decoder;

    /** The app's re-login before deleting: same flow as a login, exchanged with reauth=true. */
    private ResultActions reauth(String raw) throws Exception {
        String verifier = "r".repeat(64);
        var start = flow.start(PlatformLoginFlow.challenge(verifier), "junseo://auth", java.util.UUID.randomUUID().toString());
        var launch = flow.launch(start.state());
        var callback = flow.callback(start.state(), launch.cookie(), raw);
        return mvc.perform(post("/api/auth/exchange").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("code", callback.code(), "codeVerifier", verifier, "reauth", true))));
    }

    @Test
    void reauthReturnsTheExistingAccountButNeverCreatesOne() throws Exception {
        long id = provision(PlatformTokens.token("person-6"));
        reauth(PlatformTokens.token("person-6")).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(id));
        long before = jdbc.queryForObject("select count(*) from users", Long.class);
        reauth(PlatformTokens.token("someone-else"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REAUTH_NO_ACCOUNT"));
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(before);
    }

    private long provision(String raw) {
        return identities.provision(decoder.decode(raw)).getId();
    }

    private ResultActions deleteWith(String raw, Object body) throws Exception {
        return mvc.perform(post("/api/me/delete").header("Authorization", "Bearer " + raw)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    @Test
    void freshLoginDeletesTheAccountAndEndsThatSession() throws Exception {
        String session = PlatformTokens.token("person-1", Instant.now().minusSeconds(30 * 60), null);
        long id = provision(PlatformTokens.token("person-1"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.loginMethod").value("platform"));

        String fresh = PlatformTokens.token("person-1");
        // A password means nothing for this account; an empty body is fine too
        mvc.perform(post("/api/me/delete").header("Authorization", "Bearer " + fresh)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Long.class, id)).isZero();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + fresh)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + session)).andExpect(status().isUnauthorized());
        // Not banned: logging in again starts a new, empty account
        assertThat(provision(PlatformTokens.token("person-1"))).isNotEqualTo(id);
    }

    @Test
    void oldLoginIsAskedToLogInAgain() throws Exception {
        String old = PlatformTokens.token("person-2", Instant.now().minusSeconds(10 * 60), null);
        long id = provision(old);
        deleteWith(old, Map.of("password", "anything"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Long.class, id)).isOne();
        // The session itself is fine (403, not 401: the app must not sign out)
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + old)).andExpect(status().isOk());
    }

    @Test
    void authTimeWinsOverIssuedAt() throws Exception {
        // Re-issued just now from a sign-in made an hour ago (e.g. a silent refresh): not a fresh login
        String reissued = PlatformTokens.token("person-3", Instant.now().minusSeconds(1), Instant.now().minusSeconds(3600));
        provision(reissued);
        deleteWith(reissued, Map.of()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        String signedIn = PlatformTokens.token("person-3", Instant.now().minusSeconds(1), Instant.now().minusSeconds(30));
        deleteWith(signedIn, Map.of()).andExpect(status().isNoContent());
    }

    @Test
    void operatorRemovalKeepsThePersonOutUntilLifted() throws Exception {
        long id = provision(PlatformTokens.token("person-4"));
        mvc.perform(delete("/api/admin/users/" + id).param("reason", "괴롭힘 신고").header(AdminAuth.HEADER, "test-admin-token"))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> provision(PlatformTokens.token("person-4")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_BANNED));
        String bans = body(mvc.perform(get("/api/admin/bans").header(AdminAuth.HEADER, "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].externalSubject").value("person-4"))
                .andExpect(jsonPath("$.items[0].reason").value("괴롭힘 신고"))
                .andReturn());
        long ban = longAt(bans, "$.items[0].id");
        // Someone else is not affected
        provision(PlatformTokens.token("person-5"));

        mvc.perform(delete("/api/admin/bans/" + ban).header(AdminAuth.HEADER, "test-admin-token")).andExpect(status().isNoContent());
        assertThat(provision(PlatformTokens.token("person-4"))).isNotEqualTo(id);
    }
}
