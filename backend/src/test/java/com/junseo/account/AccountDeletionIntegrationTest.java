package com.junseo.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.common.security.AdminAuth;
import com.junseo.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AccountDeletionIntegrationTest extends IntegrationTest {

    @Test
    void wrongPasswordKeepsTheAccount() throws Exception {
        var a = signup("준서");
        postJson("/api/me/delete", a, Map.of("password", "not-my-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        getAs(a, "/api/me").andExpect(status().isOk());
    }

    @Test
    void deletingRemovesEverythingTheUserMade() throws Exception {
        var a = signup("준서");
        var b = signup("민지");
        var c = signup("지우");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);
        long aPhoto = upload(a);
        long bPhoto = upload(b);
        String aThumb = JsonPath.read(body(getAs(b, "/api/moments/" + aPhoto).andReturn()), "$.thumbUrl");
        mvc.perform(get(aThumb)).andExpect(status().isOk());
        postJson("/api/moments/" + bPhoto + "/comments", a, Map.of("text", "멋지다")).andExpect(status().isCreated());
        postJson("/api/conversations/" + b.id() + "/messages", a, Map.of("text", "안녕")).andExpect(status().isCreated());
        // 둘이 남는 단챗은 남고, 준서만 있던 단챗은 사라진다
        long trio = longAt(body(postJson("/api/groups", a, Map.of("memberIds", List.of(b.id(), c.id()))).andReturn()), "$.id");
        postJson("/api/groups/" + trio + "/messages", a, Map.of("text", "모여라")).andExpect(status().isCreated());

        postJson("/api/me/delete", a, Map.of("password", "password123!")).andExpect(status().isNoContent());

        // 로그인 · 남은 토큰 모두 끝
        getAs(a, "/api/me").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", a.email(), "password", "password123!"))))
                .andExpect(status().isUnauthorized());
        // 친구 · 사진 · 사진 파일 · 댓글 · 대화 · 단챗 메시지
        getAs(b, "/api/friends").andExpect(jsonPath("$.friends[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem((int) a.id()))));
        getAs(b, "/api/moments/" + aPhoto).andExpect(status().isNotFound());
        mvc.perform(get(aThumb)).andExpect(status().isNotFound());
        getAs(b, "/api/moments/" + bPhoto).andExpect(jsonPath("$.comments.length()").value(0));
        getAs(b, "/api/conversations").andExpect(jsonPath("$.items.length()").value(0));
        getAs(b, "/api/groups/" + trio + "/messages").andExpect(jsonPath("$.items.length()").value(0));
        getAs(b, "/api/groups/" + trio).andExpect(jsonPath("$.members.length()").value(2));
        // 같은 이메일로 다시 가입할 수 있다
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", a.email(), "password", "password123!", "displayName", "준서"))))
                .andExpect(status().isCreated());
    }

    @Test
    void operatorCanRemoveAnAccount() throws Exception {
        var a = signup("준서");
        mvc.perform(delete("/api/admin/users/" + a.id())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/" + a.id()).header(AdminAuth.HEADER, "test-admin-token")).andExpect(status().isNoContent());
        getAs(a, "/api/me").andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Long.class, a.id())).isZero();
    }
}
