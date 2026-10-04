package com.junseo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void signupLoginAndMe() throws Exception {
        String signup = body(signupRaw(" MinJi@Example.COM ", "password123!", " 민지 ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").value(matchesPattern("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ")))
                .andExpect(jsonPath("$.user.email").value("minji@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("민지"))
                .andExpect(jsonPath("$.user.inviteCode").value(matchesPattern("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}")))
                .andExpect(jsonPath("$.user.friendCount").value(0))
                .andExpect(jsonPath("$.user.friendLimit").value(20))
                .andReturn());
        long id = longAt(signup, "$.user.id");
        Instant expiresAt = Instant.parse(JsonPath.read(signup, "$.expiresAt"));
        assertThat(Duration.between(Instant.now(), expiresAt)).isBetween(Duration.ofDays(89), Duration.ofDays(90));

        String token = JsonPath.read(signup, "$.accessToken");
        Map<?, ?> claims = json.readValue(Base64.getUrlDecoder().decode(token.split("\\.")[1]), Map.class);
        assertThat(claims.get("sub")).isEqualTo(Long.toString(id));
        Map<?, ?> header = json.readValue(Base64.getUrlDecoder().decode(token.split("\\.")[0]), Map.class);
        assertThat(header.get("alg")).isEqualTo("HS256");

        String login = body(loginRaw("MINJI@example.com", "password123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(id))
                .andReturn());

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(login, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("minji@example.com"))
                .andExpect(jsonPath("$.friendLimit").value(20));
    }

    @Test
    void renameMe() throws Exception {
        var user = signup("민지");
        patchJson("/api/me", user, Map.of("displayName", "  민지짱 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("민지짱"));
        patchJson("/api/me", user, Map.of("displayName", "가".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        signupRaw("dup@example.com", "password123!", "하나").andExpect(status().isCreated());
        signupRaw("DUP@example.com", "password123!", "둘")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void badCredentialsAreUnauthorized() throws Exception {
        signupRaw("who@example.com", "password123!", "누구").andExpect(status().isCreated());
        loginRaw("who@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        loginRaw("nobody@example.com", "password123!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void missingOrBadTokenIsJsonUnauthorized() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/api/moments").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Correctly shaped token signed with a different key.
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZXhwIjo0MTAyNDQ0ODAwfQ."
                + "c2lnbmF0dXJlLXdpdGgtdGhlLXdyb25nLWtleS0xMjM0NTY3ODkw";
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenOfDeletedAccountIsUnauthorized() throws Exception {
        var user = signup("유령");
        jdbc.update("delete from users where id = ?", user.id());
        getAs(user, "/api/me").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void staleTokenDoesNotBreakLogin() throws Exception {
        signupRaw("stale@example.com", "password123!", "스테일").andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "stale@example.com", "password", "password123!"))))
                .andExpect(status().isOk());
    }

    @Test
    void signupValidation() throws Exception {
        signupRaw("short@example.com", "1234567", "짧은").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        signupRaw("not-an-email", "password123!", "이름").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        signupRaw("long@example.com", "password123!", "가".repeat(21)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        signupRaw("blank@example.com", "password123!", "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        signupRaw("toolong@example.com", "a".repeat(73), "이름").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions signupRaw(String email, String password, String name) throws Exception {
        return mvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(toJson(Map.of("email", email, "password", password, "displayName", name))));
    }

    private ResultActions loginRaw(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "password", password))));
    }
}
