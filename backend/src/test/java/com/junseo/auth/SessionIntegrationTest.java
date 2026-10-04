package com.junseo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

/** Junseo's own sessions: renewed while the app is used, ended per device by logout. */
class SessionIntegrationTest extends IntegrationTest {

    @Autowired SecretKey jwtSecretKey;

    private ResultActions as(String token, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String loginAgain(TestUser user) throws Exception {
        return JsonPath.read(body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", user.email(), "password", "password123!"))))
                .andExpect(status().isOk()).andReturn()), "$.accessToken");
    }

    @Test
    void renewingGivesALaterExpiryOnTheSameSession() throws Exception {
        var a = signup("준서");
        String first = a.token();
        String renewedBody = body(as(first, post("/api/auth/renew")).andExpect(status().isOk()).andReturn());
        String renewed = JsonPath.read(renewedBody, "$.accessToken");
        assertThat(Instant.parse(JsonPath.read(renewedBody, "$.expiresAt"))).isAfter(Instant.now().plus(89, ChronoUnit.DAYS));
        as(renewed, get("/api/me")).andExpect(status().isOk());
        // The widget may still hold the previous token for a moment: it keeps working until it expires
        as(first, get("/api/me")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from sessions where user_id = ?", Long.class, a.id())).isOne();
    }

    @Test
    void logoutEndsEveryTokenOfThatDeviceButNotOtherDevices() throws Exception {
        var a = signup("준서");
        String phone = a.token();
        String renewedPhone = JsonPath.read(body(as(phone, post("/api/auth/renew")).andReturn()), "$.accessToken");
        String tablet = loginAgain(a);

        as(phone, post("/api/auth/logout")).andExpect(status().isNoContent());

        as(phone, get("/api/me")).andExpect(status().isUnauthorized());
        as(renewedPhone, get("/api/me")).andExpect(status().isUnauthorized());
        as(renewedPhone, post("/api/auth/renew")).andExpect(status().isUnauthorized());
        as(tablet, get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void aTokenWithoutASessionIsRefusedEvenWithAValidSignature() throws Exception {
        var a = signup("준서");
        // What tokens looked like before sessions: signed with the right key, but tied to nothing that can be ended
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(Long.toString(a.id())).issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("ver", 0).build();
        String legacy = NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        as(legacy, get("/api/me")).andExpect(status().isUnauthorized());
    }
}
