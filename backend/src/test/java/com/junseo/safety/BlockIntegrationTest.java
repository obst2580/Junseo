package com.junseo.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockIntegrationTest extends IntegrationTest {

    @Test
    void blockingUnfriendsAndPreventsAddingBackUntilUnblocked() throws Exception {
        var a = signup("준서");
        var b = signup("민지");
        befriend(a, b);
        long bPhoto = upload(b);

        postJson("/api/blocks", a, Map.of("userId", b.id())).andExpect(status().isNoContent());
        getAs(a, "/api/blocks").andExpect(jsonPath("$.items[*].id", contains((int) b.id())));
        getAs(a, "/api/friends").andExpect(jsonPath("$.friends", empty()));
        getAs(b, "/api/friends").andExpect(jsonPath("$.friends", empty()));
        // 친구가 끊겨서 서로의 사진이 안 보인다
        getAs(a, "/api/moments/" + bPhoto).andExpect(status().isNotFound());

        // 차단한 사람: 이유를 알려 준다. 차단당한 사람: 없는 코드처럼 (차단 사실을 숨긴다)
        postJson("/api/friends", a, Map.of("inviteCode", b.inviteCode()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCKED_USER"));
        postJson("/api/friends", b, Map.of("inviteCode", a.inviteCode()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_FOUND"));

        deleteAs(a, "/api/blocks/" + b.id()).andExpect(status().isNoContent());
        deleteAs(a, "/api/blocks/" + b.id()).andExpect(status().isNotFound());
        befriend(a, b);
    }

    @Test
    void blockedPeoplesCommentsAndChatsDisappearForTheBlocker() throws Exception {
        var a = signup("준서");
        var b = signup("민지");
        var c = signup("지우");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);
        long cPhoto = upload(c);
        postJson("/api/moments/" + cPhoto + "/comments", b, Map.of("text", "민지 댓글")).andExpect(status().isCreated());
        postJson("/api/moments/" + cPhoto + "/comments", a, Map.of("text", "준서 댓글")).andExpect(status().isCreated());
        postJson("/api/conversations/" + a.id() + "/messages", b, Map.of("text", "야")).andExpect(status().isCreated());
        long group = longAt(body(postJson("/api/groups", a, Map.of("memberIds", List.of(b.id(), c.id()))).andReturn()), "$.id");
        String aDevice = registerDevice(a, "app");
        String cDevice = registerDevice(c, "app");

        postJson("/api/blocks", a, Map.of("userId", b.id())).andExpect(status().isNoContent());

        // 댓글: 준서에게 민지 댓글이, 민지에게 준서 댓글이 안 보인다. 지우에게는 둘 다
        assertThat(JsonPath.<List<String>>read(body(getAs(a, "/api/moments/" + cPhoto).andReturn()), "$.comments[*].text")).containsExactly("준서 댓글");
        assertThat(JsonPath.<List<String>>read(body(getAs(b, "/api/moments/" + cPhoto).andReturn()), "$.comments[*].text")).containsExactly("민지 댓글");
        assertThat(JsonPath.<List<String>>read(body(getAs(c, "/api/moments/" + cPhoto).andReturn()), "$.comments[*].text"))
                .containsExactly("민지 댓글", "준서 댓글");
        getAs(a, "/api/moments?limit=10").andExpect(jsonPath("$.items[0].commentCount").value(1));
        // 1:1 대화 목록에서 빠진다
        getAs(a, "/api/conversations").andExpect(jsonPath("$.items", empty()));

        // 단챗: 민지 메시지는 준서에게 안 보이고, 안 읽음에도 안 세고, 알림도 안 간다
        push.clear();
        postJson("/api/groups/" + group + "/messages", b, Map.of("text", "민지가 보냄")).andExpect(status().isCreated());
        postJson("/api/groups/" + group + "/messages", c, Map.of("text", "지우가 보냄")).andExpect(status().isCreated());
        assertThat(JsonPath.<List<String>>read(body(getAs(a, "/api/groups/" + group + "/messages").andReturn()), "$.items[*].text"))
                .containsExactly("지우가 보냄");
        getAs(a, "/api/conversations")
                .andExpect(jsonPath("$.groups[0].unreadCount").value(1))
                .andExpect(jsonPath("$.groups[0].lastMessage.text").value("지우가 보냄"));
        await().atMost(Duration.ofSeconds(5)).until(() -> push.to(cDevice).size() == 1);
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5)).until(() -> push.to(aDevice).size() == 1);
        assertThat(push.to(aDevice).getFirst().payload()).contains("지우가 보냄");
    }

    @Test
    void blockingNeedsSomeoneElseWhoExists() throws Exception {
        var a = signup("준서");
        postJson("/api/blocks", a, Map.of("userId", a.id())).andExpect(status().isBadRequest());
        postJson("/api/blocks", a, Map.of("userId", 999_999_999)).andExpect(status().isNotFound());
        postJson("/api/blocks", a, Map.of()).andExpect(status().isBadRequest());
    }
}
