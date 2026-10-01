package com.junseo.chat;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Reactions, comments, replies and 1:1 messages. */
class InteractionIntegrationTest extends IntegrationTest {

    @Test
    void tapsAddUpPerEmojiAndDeleteClearsMine() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        befriend(a, b);
        long m = upload(a);

        postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "❤️"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(m))
                .andExpect(jsonPath("$.myReactions[0].emoji").value("❤️"))
                .andExpect(jsonPath("$.myReactions[0].count").value(1))
                .andExpect(jsonPath("$.reactions[0].count").value(1));
        // Tapping again adds up; a quick run of taps can arrive as one request.
        postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "❤️", "count", 4))
                .andExpect(jsonPath("$.myReactions[0].count").value(5))
                .andExpect(jsonPath("$.reactions[0].count").value(5));
        postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "😂"))
                .andExpect(jsonPath("$.myReactions[*].emoji", contains("❤️", "😂")))
                .andExpect(jsonPath("$.myReactions[*].count", contains(5, 1)))
                .andExpect(jsonPath("$.reactions[*].count", contains(5, 1)));
        getAs(a, "/api/moments/" + m)
                .andExpect(jsonPath("$.myReactions", hasSize(0)))
                .andExpect(jsonPath("$.reactions[0].count").value(5));

        // At most 20 per request and 99 per emoji.
        for (int bad : new int[] {0, 21, -1}) {
            postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "👍", "count", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        for (int i = 0; i < 6; i++) {
            postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "👍", "count", 20)).andExpect(status().isOk());
        }
        getAs(b, "/api/moments/" + m).andExpect(jsonPath("$.myReactions[0].emoji").value("👍"))
                .andExpect(jsonPath("$.myReactions[0].count").value(99));

        deleteAs(b, "/api/moments/" + m + "/reactions").andExpect(status().isNoContent());
        getAs(b, "/api/moments/" + m)
                .andExpect(jsonPath("$.myReactions", hasSize(0)))
                .andExpect(jsonPath("$.reactions", hasSize(0)));
        deleteAs(b, "/api/moments/" + m + "/reactions").andExpect(status().isNoContent());
    }

    @Test
    void reactionsAreOrderedByCountThenRecency() throws Exception {
        var owner = signup("민지");
        long m = 0;
        var reactors = new IntegrationTest.TestUser[4];
        for (int i = 0; i < reactors.length; i++) {
            reactors[i] = signup("친구" + i);
            befriend(owner, reactors[i]);
        }
        m = upload(owner);
        String[] emojis = {"😂", "👍", "👍", "❤️"};
        for (int i = 0; i < reactors.length; i++) {
            postJson("/api/moments/" + m + "/reactions", reactors[i], Map.of("emoji", emojis[i])).andExpect(status().isOk());
        }
        getAs(owner, "/api/moments/" + m)
                .andExpect(jsonPath("$.reactions[*].emoji", contains("👍", "❤️", "😂")))
                .andExpect(jsonPath("$.reactions[*].count", contains(2, 1, 1)));
    }

    @Test
    void cannotReactToOwnMomentAndEmojiIsValidated() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        befriend(a, b);
        long m = upload(a);

        postJson("/api/moments/" + m + "/reactions", a, Map.of("emoji", "👍"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ALLOWED_ON_OWN_MOMENT"));
        // Only the five reactions the app offers are accepted.
        for (String bad : new String[] {"", "👍 👍", " ", "x".repeat(17), "🔥", "😍", "👨‍👩‍👧", "👍🏽"}) {
            postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        for (String ok : new String[] {"😂", "😢", "👍", "🖕", "❤️"}) {
            postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", ok))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myReactions[*].emoji", hasItem(ok)));
        }
        // A bare heart without the variation selector is stored as ❤️.
        postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "❤"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myReactions[?(@.emoji == '❤️')].count", contains(2)));
    }

    @Test
    void commentPermissions() throws Exception {
        var owner = signup("민지");
        var author = signup("지우");
        var other = signup("서연");
        befriend(owner, author);
        befriend(owner, other);
        long m = upload(owner);

        String created = body(postJson("/api/moments/" + m + "/comments", author, Map.of("text", " 대박 "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.momentId").value(m))
                .andExpect(jsonPath("$.author.id").value(author.id()))
                .andExpect(jsonPath("$.author.displayName").value("지우"))
                .andExpect(jsonPath("$.text").value("대박"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(".+T.+Z")))
                .andReturn());
        long commentId = longAt(created, "$.id");

        deleteAs(other, "/api/comments/" + commentId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        deleteAs(owner, "/api/comments/" + commentId).andExpect(status().isNoContent());
        deleteAs(owner, "/api/comments/" + commentId).andExpect(status().isNotFound());

        long own = longAt(body(postJson("/api/moments/" + m + "/comments", author, Map.of("text", "다시"))
                .andExpect(status().isCreated()).andReturn()), "$.id");
        deleteAs(author, "/api/comments/" + own).andExpect(status().isNoContent());

        postJson("/api/moments/" + m + "/comments", owner, Map.of("text", "고마워")).andExpect(status().isCreated());
        postJson("/api/moments/" + m + "/comments", author, Map.of("text", "가".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        postJson("/api/moments/" + m + "/comments", author, Map.of("text", "   "))
                .andExpect(status().isBadRequest());
        getAs(other, "/api/moments/" + m).andExpect(jsonPath("$.recentComments[*].text", contains("고마워")));
    }

    @Test
    void unfriendedUsersReactionsAndCommentsDisappear() throws Exception {
        var owner = signup("민지");
        var viewer = signup("지우");
        var ex = signup("서연");
        befriend(owner, viewer);
        befriend(owner, ex);
        long m = upload(owner);
        postJson("/api/moments/" + m + "/reactions", ex, Map.of("emoji", "👍")).andExpect(status().isOk());
        postJson("/api/moments/" + m + "/comments", ex, Map.of("text", "나 서연")).andExpect(status().isCreated());
        postJson("/api/moments/" + m + "/comments", viewer, Map.of("text", "나 지우")).andExpect(status().isCreated());

        deleteAs(owner, "/api/friends/" + ex.id()).andExpect(status().isNoContent());

        getAs(viewer, "/api/moments/" + m)
                .andExpect(jsonPath("$.reactions", hasSize(0)))
                .andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.comments[*].text", contains("나 지우")));
    }

    @Test
    void replyCreatesMessageToOwnerReferencingThePhoto() throws Exception {
        var owner = signup("민지");
        var b = signup("지우");
        befriend(owner, b);
        long m = upload(owner);

        postJson("/api/moments/" + m + "/replies", b, Map.of("text", "어디야?"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.senderId").value(b.id()))
                .andExpect(jsonPath("$.receiverId").value(owner.id()))
                .andExpect(jsonPath("$.text").value("어디야?"))
                .andExpect(jsonPath("$.readAt").value(nullValue()))
                .andExpect(jsonPath("$.moment.id").value(m))
                .andExpect(jsonPath("$.moment.thumbUrl").value(matchesPattern("/media/" + m + "/thumb\\.jpg\\?exp=\\d+&sig=.+")));
        getAs(owner, "/api/conversations/" + b.id() + "/messages")
                .andExpect(jsonPath("$.items[0].moment.id").value(m));

        postJson("/api/moments/" + m + "/replies", owner, Map.of("text", "내 사진"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ALLOWED_ON_OWN_MOMENT"));
        postJson("/api/moments/" + m + "/replies", b, Map.of("text", "가".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void messagingRequiresFriendship() throws Exception {
        var a = signup("민지");
        var stranger = signup("모르는사람");
        postJson("/api/conversations/" + stranger.id() + "/messages", a, Map.of("text", "안녕"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_FRIENDS"));
        postJson("/api/conversations/999999999/messages", a, Map.of("text", "안녕"))
                .andExpect(status().isNotFound());
        postJson("/api/conversations/" + a.id() + "/messages", a, Map.of("text", "나에게"))
                .andExpect(status().isForbidden());
    }

    @Test
    void conversationsShowLastMessageAndUnreadCount() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        var c = signup("서연");
        befriend(a, b);
        befriend(a, c);
        long m = upload(a);

        postJson("/api/moments/" + m + "/replies", b, Map.of("text", "사진 좋다")).andExpect(status().isCreated());
        postJson("/api/conversations/" + a.id() + "/messages", b, Map.of("text", "ㅋㅋㅋ")).andExpect(status().isCreated());
        postJson("/api/conversations/" + a.id() + "/messages", c, Map.of("text", "서연이야")).andExpect(status().isCreated());
        postJson("/api/conversations/" + a.id() + "/messages", b, Map.of("text", "자니?")).andExpect(status().isCreated());
        postJson("/api/conversations/" + b.id() + "/messages", a, Map.of("text", "아니")).andExpect(status().isCreated());

        getAs(a, "/api/conversations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].peer.id", contains((int) b.id(), (int) c.id())))
                .andExpect(jsonPath("$.items[0].peer.displayName").value("지우"))
                .andExpect(jsonPath("$.items[0].lastMessage.text").value("아니"))
                .andExpect(jsonPath("$.items[0].lastMessage.senderId").value(a.id()))
                .andExpect(jsonPath("$.items[0].unreadCount").value(3))
                .andExpect(jsonPath("$.items[1].unreadCount").value(1));
        getAs(b, "/api/conversations").andExpect(jsonPath("$.items[0].unreadCount").value(1));

        postAs(a, "/api/conversations/" + b.id() + "/read").andExpect(status().isNoContent());
        getAs(a, "/api/conversations")
                .andExpect(jsonPath("$.items[0].unreadCount").value(0))
                .andExpect(jsonPath("$.items[1].unreadCount").value(1));
        getAs(b, "/api/conversations").andExpect(jsonPath("$.items[0].unreadCount").value(1));

        String page1 = body(getAs(a, "/api/conversations/" + b.id() + "/messages?limit=2")
                .andExpect(jsonPath("$.items[*].text", contains("아니", "자니?")))
                .andExpect(jsonPath("$.items[1].readAt").value(notNullValue()))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn());
        getAs(a, "/api/conversations/" + b.id() + "/messages?limit=2&cursor=" + JsonPath.read(page1, "$.nextCursor"))
                .andExpect(jsonPath("$.items[*].text", contains("ㅋㅋㅋ", "사진 좋다")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void historyStaysAfterUnfriendButPhotoReferenceIsHidden() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        befriend(a, b);
        long m = upload(a);
        postJson("/api/moments/" + m + "/replies", b, Map.of("text", "예쁘다")).andExpect(status().isCreated());
        deleteAs(a, "/api/friends/" + b.id()).andExpect(status().isNoContent());

        getAs(b, "/api/conversations/" + a.id() + "/messages")
                .andExpect(jsonPath("$.items[0].text").value("예쁘다"))
                .andExpect(jsonPath("$.items[0].moment").value(nullValue()));
        getAs(a, "/api/conversations/" + b.id() + "/messages").andExpect(jsonPath("$.items[0].moment.id").value(m));
        getAs(b, "/api/conversations").andExpect(jsonPath("$.items[0].peer.id").value(a.id()));
        postJson("/api/conversations/" + a.id() + "/messages", b, Map.of("text", "왜"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_FRIENDS"));
    }
}
