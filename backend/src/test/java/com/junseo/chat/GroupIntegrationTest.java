package com.junseo.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroupIntegrationTest extends IntegrationTest {

    @Test
    void everyMemberMustBeFriendsWithEveryOther() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        TestUser c = signup("지우");
        TestUser d = signup("하준");
        TestUser stranger = signup("모르는 사람");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);
        befriend(a, d);

        postJson("/api/groups", a, Map.of("memberIds", List.of(b.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("나 말고 2명 이상 골라 주세요."));
        postJson("/api/groups", a, Map.of("memberIds", List.of(b.id(), a.id())))
                .andExpect(status().isBadRequest());
        postJson("/api/groups", a, Map.of("memberIds", List.of(b.id(), stranger.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_FRIENDS"));
        // d is my friend but not b's or c's.
        postJson("/api/groups", a, Map.of("memberIds", List.of(b.id(), c.id(), d.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MUTUAL_FRIENDS"))
                .andExpect(jsonPath("$.message").value("서로 친구인 사람끼리만 단챗을 만들 수 있어요."));

        postJson("/api/groups", a, Map.of("memberIds", List.of(c.id(), b.id(), b.id()), "name", "  우리  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("우리"))
                .andExpect(jsonPath("$.members[*].displayName", contains("민지", "준서", "지우")));
        postJson("/api/groups", a, Map.of("memberIds", List.of(c.id(), b.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").doesNotExist());
        getAs(b, "/api/groups").andExpect(jsonPath("$.items", hasSize(2)));
        getAs(d, "/api/groups").andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void friendLinksTellWhichFriendsKnowEachOther() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        TestUser c = signup("지우");
        TestUser d = signup("하준");
        TestUser e = signup("도윤");
        befriend(a, b);
        befriend(a, c);
        befriend(a, d);
        befriend(b, c);
        befriend(d, e); // e is not my friend, so this pair is not mine to see

        getAs(a, "/api/friends/links")
                .andExpect(jsonPath("$.pairs", hasSize(1)))
                .andExpect(jsonPath("$.pairs[0]", contains((int) Math.min(b.id(), c.id()), (int) Math.max(b.id(), c.id()))));
        getAs(e, "/api/friends/links").andExpect(jsonPath("$.pairs", hasSize(0)));
    }

    @Test
    void unreadCountsFollowWhatEachMemberHasRead() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        TestUser c = signup("지우");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);
        long group = createGroup(a, b, c);

        postJson("/api/groups/" + group + "/messages", a, Map.of("text", "토요일 한강?"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unreadCount").value(2));
        getAs(b, "/api/conversations")
                .andExpect(jsonPath("$.groups[0].group.id").value(group))
                .andExpect(jsonPath("$.groups[0].lastMessage.text").value("토요일 한강?"))
                .andExpect(jsonPath("$.groups[0].unreadCount").value(1));
        getAs(a, "/api/conversations").andExpect(jsonPath("$.groups[0].unreadCount").value(0));

        postAs(b, "/api/groups/" + group + "/read").andExpect(status().isNoContent());
        getAs(b, "/api/conversations").andExpect(jsonPath("$.groups[0].unreadCount").value(0));
        getAs(a, "/api/groups/" + group + "/messages").andExpect(jsonPath("$.items[0].unreadCount").value(1));

        postJson("/api/groups/" + group + "/messages", b, Map.of("text", "좋아!")).andExpect(status().isCreated());
        String page = body(getAs(c, "/api/groups/" + group + "/messages").andExpect(status().isOk()).andReturn());
        assertThat(JsonPath.<List<String>>read(page, "$.items[*].text")).containsExactly("좋아!", "토요일 한강?");
        assertThat(JsonPath.<List<Integer>>read(page, "$.items[*].unreadCount")).containsExactly(2, 1);
        getAs(c, "/api/conversations").andExpect(jsonPath("$.groups[0].unreadCount").value(2));

        postAs(c, "/api/groups/" + group + "/read").andExpect(status().isNoContent());
        page = body(getAs(a, "/api/groups/" + group + "/messages").andReturn());
        assertThat(JsonPath.<List<Integer>>read(page, "$.items[*].unreadCount")).containsExactly(1, 0);
    }

    @Test
    void onlyMembersCanReadOrWrite() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        TestUser c = signup("지우");
        TestUser d = signup("하준");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);
        befriend(a, d);
        long group = createGroup(a, b, c);

        getAs(d, "/api/groups/" + group).andExpect(status().isNotFound());
        getAs(d, "/api/groups/" + group + "/messages").andExpect(status().isNotFound());
        postJson("/api/groups/" + group + "/messages", d, Map.of("text", "나도")).andExpect(status().isNotFound());
        postAs(d, "/api/groups/" + group + "/read").andExpect(status().isNotFound());
        postJson("/api/groups/" + group + "/messages", a, Map.of("text", " ")).andExpect(status().isBadRequest());
    }

    @Test
    void leavingRemovesTheMemberAndTheLastOneOutDeletesTheGroup() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        TestUser c = signup("지우");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);
        long group = createGroup(a, b, c);
        postJson("/api/groups/" + group + "/messages", a, Map.of("text", "안녕")).andExpect(status().isCreated());

        deleteAs(b, "/api/groups/" + group + "/members/me").andExpect(status().isNoContent());
        getAs(b, "/api/groups/" + group).andExpect(status().isNotFound());
        getAs(a, "/api/groups/" + group).andExpect(jsonPath("$.members", hasSize(2)));
        // Read receipts only count people still in the group.
        getAs(a, "/api/groups/" + group + "/messages").andExpect(jsonPath("$.items[0].unreadCount").value(1));

        deleteAs(a, "/api/groups/" + group + "/members/me").andExpect(status().isNoContent());
        deleteAs(c, "/api/groups/" + group + "/members/me").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from chat_groups where id = ?", Long.class, group)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from group_messages where group_id = ?", Long.class, group)).isZero();
    }

    private long createGroup(TestUser creator, TestUser... others) throws Exception {
        List<Long> ids = java.util.Arrays.stream(others).map(TestUser::id).toList();
        return longAt(body(postJson("/api/groups", creator, Map.of("memberIds", ids)).andExpect(status().isCreated()).andReturn()), "$.id");
    }
}
