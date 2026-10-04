package com.junseo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.support.IntegrationTest;
import com.junseo.support.PlatformTokens;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Starting a login needs no account: nobody may use up the login slots of everyone else. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "junseo.auth.mode=platform")
class PlatformLoginLimitsIntegrationTest extends IntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("junseo.auth.jwks-url", PlatformTokens::jwksUrl);
    }

    @Autowired PlatformLoginFlow flow;

    private static final String CHALLENGE = PlatformLoginFlow.challenge("v".repeat(64));

    @Test
    void oneAddressCannotHoldMoreThanAFewPendingLogins() throws Exception {
        String attacker = "203.0.113." + System.nanoTime() % 200;
        for (int i = 0; i < PlatformLoginFlow.MAX_PER_CLIENT; i++) flow.start(CHALLENGE, "junseo://auth", attacker);
        assertThatThrownBy(() -> flow.start(CHALLENGE, "junseo://auth", attacker))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
        // Everyone else still logs in
        flow.start(CHALLENGE, "junseo://auth", "198.51.100.7-" + System.nanoTime());
        // Over HTTP the address is the request's remote address
        for (int i = 0; i < PlatformLoginFlow.MAX_PER_CLIENT; i++) startOverHttp("192.0.2.44").andExpect(status().isOk());
        startOverHttp("192.0.2.44").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void aFullTableMakesRoomInsteadOfRefusingEveryone() {
        // Many addresses (e.g. a botnet) fill the table: the oldest pending logins make room, new logins still start
        int before = flow.pending();
        int clients = (PlatformLoginFlow.MAX_PENDING - before) / PlatformLoginFlow.MAX_PER_CLIENT + 5;
        long run = System.nanoTime();
        for (int c = 0; c < clients; c++) {
            for (int i = 0; i < PlatformLoginFlow.MAX_PER_CLIENT; i++) flow.start(CHALLENGE, "junseo://auth", "bot-" + run + "-" + c);
        }
        assertThat(flow.pending()).isEqualTo(PlatformLoginFlow.MAX_PENDING);
        var real = flow.start(CHALLENGE, "junseo://auth", "real-user-" + run);
        assertThat(real.state()).isNotBlank();
        assertThat(flow.pending()).isEqualTo(PlatformLoginFlow.MAX_PENDING);
        // The person who just started can carry on (their transaction is the newest, not evicted)
        assertThat(flow.launch(real.state()).cookie()).isNotBlank();
    }

    private org.springframework.test.web.servlet.ResultActions startOverHttp(String address) throws Exception {
        return mvc.perform(post("/api/auth/start").with(r -> { r.setRemoteAddr(address); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("codeChallenge", CHALLENGE, "returnUri", "junseo://auth"))));
    }
}
