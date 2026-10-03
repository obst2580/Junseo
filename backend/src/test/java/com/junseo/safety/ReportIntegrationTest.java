package com.junseo.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.common.security.AdminAuth;
import com.junseo.support.IntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ReportIntegrationTest extends IntegrationTest {

    private static final String TOKEN = "test-admin-token";

    private ResultActions report(TestUser by, String kind, Long targetId, Long userId, String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", kind);
        body.put("reason", reason);
        if (targetId != null) {
            body.put("targetId", targetId);
        }
        if (userId != null) {
            body.put("userId", userId);
        }
        return postJson("/api/reports", by, body);
    }

    private String adminReports(String query) throws Exception {
        return body(mvc.perform(get("/api/admin/reports" + query).header(AdminAuth.HEADER, TOKEN)).andExpect(status().isOk()).andReturn());
    }

    @Test
    void everythingYouCanSeeCanBeReportedAndTheOperatorIsMailed() throws Exception {
        var a = signup("준서");
        var b = signup("민지");
        var c = signup("지우");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);
        long bPhoto = upload(b);
        long comment = longAt(body(postJson("/api/moments/" + bPhoto + "/comments", c, Map.of("text", "나쁜 말")).andReturn()), "$.id");
        long message = longAt(body(postJson("/api/conversations/" + a.id() + "/messages", b, Map.of("text", "나쁜 메시지")).andReturn()), "$.id");
        long group = longAt(body(postJson("/api/groups", a, Map.of("memberIds", List.of(b.id(), c.id()))).andReturn()), "$.id");
        long groupMessage = longAt(body(postJson("/api/groups/" + group + "/messages", c, Map.of("text", "단챗 나쁜 말")).andReturn()), "$.id");

        report(a, "moment", bPhoto, null, "sexual").andExpect(status().isCreated());
        report(a, "comment", comment, null, "abuse").andExpect(status().isCreated());
        report(a, "message", message, null, "abuse").andExpect(status().isCreated());
        report(a, "group-message", groupMessage, null, "spam").andExpect(status().isCreated());
        postJson("/api/reports", a, Map.of("kind", "user", "userId", c.id(), "reason", "other", "detail", "계속 괴롭혀요")).andExpect(status().isCreated());

        String open = adminReports("");
        assertThat(JsonPath.<List<String>>read(open, "$.items[*].kind")).containsExactly("moment", "comment", "message", "group-message", "user");
        assertThat(JsonPath.<String>read(open, "$.items[1].snapshot")).isEqualTo("나쁜 말");
        assertThat(JsonPath.<String>read(open, "$.items[1].target.displayName")).isEqualTo("지우");
        assertThat(JsonPath.<String>read(open, "$.items[0].reporter.displayName")).isEqualTo("준서");
        assertThat(JsonPath.<String>read(open, "$.items[4].detail")).isEqualTo("계속 괴롭혀요");
        // 신고된 사진은 운영자가 바로 볼 수 있다
        String image = JsonPath.read(open, "$.items[0].imageUrl");
        mvc.perform(get(image)).andExpect(status().isOk());
        // 들어올 때마다 운영자 메일
        assertThat(mail.to("ops@test.junseo.app")).hasSize(5);
        assertThat(mail.to("ops@test.junseo.app").get(1).text()).contains("나쁜 말").contains("괴롭힘");
    }

    @Test
    void onlyWhatYouCanSeeAndNotYourOwn() throws Exception {
        var a = signup("준서");
        var b = signup("민지");
        var stranger = signup("모름");
        befriend(a, b);
        long aPhoto = upload(a);
        long strangerPhoto = upload(stranger);
        long message = longAt(body(postJson("/api/conversations/" + b.id() + "/messages", a, Map.of("text", "안녕")).andReturn()), "$.id");

        report(a, "moment", strangerPhoto, null, "spam").andExpect(status().isNotFound());
        report(stranger, "message", message, null, "spam").andExpect(status().isNotFound());
        report(a, "moment", aPhoto, null, "spam").andExpect(status().isBadRequest());
        report(a, "user", null, a.id(), "spam").andExpect(status().isBadRequest());
        report(a, "moment", aPhoto, null, "boring").andExpect(status().isBadRequest());
        report(a, "photo", aPhoto, null, "spam").andExpect(status().isBadRequest());
        report(a, "comment", null, null, "spam").andExpect(status().isBadRequest());
        assertThat(JsonPath.<List<?>>read(adminReports(""), "$.items")).isEmpty();
    }

    @Test
    void operatorRemovesContentAndClosesReports() throws Exception {
        var a = signup("준서");
        var b = signup("민지");
        var c = signup("지우");
        befriend(a, b);
        befriend(b, c);
        befriend(a, c);
        long bPhoto = upload(b);
        long comment = longAt(body(postJson("/api/moments/" + bPhoto + "/comments", c, Map.of("text", "나쁜 말")).andReturn()), "$.id");
        long message = longAt(body(postJson("/api/conversations/" + a.id() + "/messages", b, Map.of("text", "나쁜 메시지")).andReturn()), "$.id");
        long group = longAt(body(postJson("/api/groups", a, Map.of("memberIds", List.of(b.id(), c.id()))).andReturn()), "$.id");
        long groupMessage = longAt(body(postJson("/api/groups/" + group + "/messages", c, Map.of("text", "단챗 나쁜 말")).andReturn()), "$.id");
        long reportId = longAt(body(report(a, "comment", comment, null, "abuse").andReturn()), "$.id");

        // 토큰 없이는 아무것도 못 한다
        mvc.perform(get("/api/admin/reports")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/comments/" + comment)).andExpect(status().isForbidden());

        mvc.perform(delete("/api/admin/comments/" + comment).header(AdminAuth.HEADER, TOKEN)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/messages/" + message).header(AdminAuth.HEADER, TOKEN)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/group-messages/" + groupMessage).header(AdminAuth.HEADER, TOKEN)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/moments/" + bPhoto).header(AdminAuth.HEADER, TOKEN)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/moments/" + bPhoto).header(AdminAuth.HEADER, TOKEN)).andExpect(status().isNotFound());
        getAs(a, "/api/moments/" + bPhoto).andExpect(status().isNotFound());
        getAs(a, "/api/conversations/" + b.id() + "/messages").andExpect(jsonPath("$.items.length()").value(0));
        getAs(a, "/api/groups/" + group + "/messages").andExpect(jsonPath("$.items.length()").value(0));

        // 지워진 뒤에도 신고 당시 글은 남는다
        assertThat(JsonPath.<String>read(adminReports(""), "$.items[0].snapshot")).isEqualTo("나쁜 말");
        mvc.perform(post("/api/admin/reports/" + reportId + "/resolve").header(AdminAuth.HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("resolution", "댓글 삭제"))))
                .andExpect(status().isNoContent());
        assertThat(JsonPath.<List<?>>read(adminReports(""), "$.items")).isEmpty();
        String all = adminReports("?all=true");
        assertThat(JsonPath.<String>read(all, "$.items[0].resolution")).isEqualTo("댓글 삭제");
    }
}
