package com.junseo.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import com.junseo.user.InviteCodes;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FriendIntegrationTest extends IntegrationTest {

    @Test
    void addByInviteCodeIgnoresCaseAndSpaces() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        String code = b.inviteCode();
        String typed = "  " + code.substring(0, 4).toLowerCase() + " " + code.substring(4) + " ";

        postJson("/api/friends", a, Map.of("inviteCode", typed))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(b.id()))
                .andExpect(jsonPath("$.displayName").value("지우"));

        getAs(a, "/api/friends")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friends[*].id", contains((int) b.id())))
                .andExpect(jsonPath("$.limit").value(20));
        getAs(b, "/api/friends").andExpect(jsonPath("$.friends[*].displayName", contains("민지")));
        getAs(b, "/api/me").andExpect(jsonPath("$.friendCount").value(1));
    }

    @Test
    void friendsAreSortedByName() throws Exception {
        var me = signup("나");
        for (String name : new String[] {"하준", "민지", "지우"}) {
            befriend(me, signup(name));
        }
        getAs(me, "/api/friends").andExpect(jsonPath("$.friends[*].displayName", contains("민지", "지우", "하준")));
    }

    @Test
    void addingYourselfIsRejected() throws Exception {
        var a = signup("민지");
        postJson("/api/friends", a, Map.of("inviteCode", a.inviteCode()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_ADD_SELF"));
    }

    @Test
    void duplicateIsConflictInBothDirections() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        befriend(a, b);
        postJson("/api/friends", a, Map.of("inviteCode", b.inviteCode()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));
        postJson("/api/friends", b, Map.of("inviteCode", a.inviteCode()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));
    }

    @Test
    void unknownCodeIsNotFound() throws Exception {
        var a = signup("민지");
        postJson("/api/friends", a, Map.of("inviteCode", "ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_FOUND"));
        postJson("/api/friends", a, Map.of("inviteCode", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void twentyFriendLimitAppliesToEitherSide() throws Exception {
        var full = signup("인싸");
        for (int i = 0; i < 20; i++) {
            Long id = jdbc.queryForObject(
                    "insert into users (email, password_hash, display_name, invite_code, created_at) "
                            + "values (?, 'x', ?, ?, now()) returning id",
                    Long.class, "filler" + i + "-" + full.id() + "@test.junseo.app", "친구" + i, InviteCodes.random());
            jdbc.update("insert into friendships (user_id, friend_id, created_at) values (?, ?, now()), (?, ?, now())",
                    full.id(), id, id, full.id());
        }
        var newcomer = signup("새친구");

        postJson("/api/friends", full, Map.of("inviteCode", newcomer.inviteCode()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_LIMIT_REACHED"));
        postJson("/api/friends", newcomer, Map.of("inviteCode", full.inviteCode()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_LIMIT_REACHED"));
        getAs(newcomer, "/api/me").andExpect(jsonPath("$.friendCount").value(0));
    }

    @Test
    void rotatingInviteCodeInvalidatesTheOldOne() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        String rotated = JsonPath.read(body(postAs(b, "/api/me/invite-code")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value(not(b.inviteCode())))
                .andReturn()), "$.inviteCode");
        assertThat(rotated).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}");

        postJson("/api/friends", a, Map.of("inviteCode", b.inviteCode()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_FOUND"));
        postJson("/api/friends", a, Map.of("inviteCode", rotated)).andExpect(status().isCreated());
    }

    @Test
    void unfriendRemovesBothDirections() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        befriend(a, b);

        deleteAs(a, "/api/friends/" + b.id()).andExpect(status().isNoContent());

        getAs(a, "/api/friends").andExpect(jsonPath("$.friends", hasSize(0)));
        getAs(b, "/api/friends").andExpect(jsonPath("$.friends", hasSize(0)));
        deleteAs(b, "/api/friends/" + a.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        postJson("/api/conversations/" + a.id() + "/messages", b, Map.of("text", "안녕"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_FRIENDS"));
    }
}
