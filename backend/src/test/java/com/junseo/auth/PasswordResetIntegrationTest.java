package com.junseo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PasswordResetIntegrationTest extends IntegrationTest {

    private ResultActions request(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("email", email))));
    }

    private ResultActions confirm(String email, String code, String password) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "code", code, "password", password))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("email", email, "password", password))));
    }

    @Test
    void codeByMailSetsANewPasswordAndEndsOtherLogins() throws Exception {
        var a = signup("준서");
        request(" " + a.email().toUpperCase() + " ").andExpect(status().isNoContent());
        assertThat(mail.to(a.email())).hasSize(1);
        String code = mail.lastCode(a.email());

        String wrong = code.equals("000000") ? "111111" : "000000";
        confirm(a.email(), wrong, "new-password-1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
        String res = body(confirm(a.email(), code, "new-password-1").andExpect(status().isOk()).andReturn());
        String fresh = JsonPath.read(res, "$.accessToken");

        // 이전 로그인(다른 기기)은 끝, 새 로그인은 된다
        getAs(a, "/api/me").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh)).andExpect(status().isOk());
        login(a.email(), "password123!").andExpect(status().isUnauthorized());
        login(a.email(), "new-password-1").andExpect(status().isOk());
        // 한 번 쓴 코드는 끝
        confirm(a.email(), code, "new-password-2").andExpect(status().isBadRequest());
    }

    @Test
    void unknownEmailLooksTheSameAndResendIsThrottled() throws Exception {
        var a = signup("준서");
        request("nobody@test.junseo.app").andExpect(status().isNoContent());
        assertThat(mail.to("nobody@test.junseo.app")).isEmpty();
        request(a.email()).andExpect(status().isNoContent());
        request(a.email()).andExpect(status().isNoContent());
        assertThat(mail.to(a.email())).hasSize(1);
    }

    @Test
    void fiveWrongTriesOrTimeOutKillsTheCode() throws Exception {
        var a = signup("준서");
        request(a.email()).andExpect(status().isNoContent());
        String code = mail.lastCode(a.email());
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            confirm(a.email(), wrong, "new-password-1").andExpect(status().isBadRequest());
        }
        confirm(a.email(), code, "new-password-1").andExpect(status().isBadRequest());

        // 시간이 지난 코드
        jdbc.update("delete from password_resets");
        request(a.email()).andExpect(status().isNoContent());
        String second = mail.lastCode(a.email());
        jdbc.update("update password_resets set expires_at = now() - interval '1 minute'");
        confirm(a.email(), second, "new-password-1").andExpect(status().isBadRequest());
        login(a.email(), "password123!").andExpect(status().isOk());
        confirm(a.email(), "123456", "short").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void legalPagesArePublic() throws Exception {
        byte[] terms = mvc.perform(get("/legal/terms.html")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(terms, java.nio.charset.StandardCharsets.UTF_8)).contains("무관용");
        mvc.perform(get("/legal/privacy.html").header(HttpHeaders.AUTHORIZATION, "Bearer stale")).andExpect(status().isOk());
    }
}
