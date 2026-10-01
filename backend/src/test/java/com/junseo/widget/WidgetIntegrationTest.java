package com.junseo.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class WidgetIntegrationTest extends IntegrationTest {

    @Test
    void noContentWhenThereIsNothingToShow() throws Exception {
        var lonely = signup("혼자");
        getAs(lonely, "/api/widget/latest").andExpect(status().isNoContent()).andExpect(content().string(""));
        upload(lonely);
        getAs(lonely, "/api/widget/latest").andExpect(status().isNoContent());
    }

    @Test
    void showsLatestFriendMomentWithTopReactionsAndLastTwoComments() throws Exception {
        var owner = signup("민지");
        var viewer = signup("준서");
        befriend(owner, viewer);
        var friends = new TestUser[5];
        for (int i = 0; i < friends.length; i++) {
            friends[i] = signup("친구" + i);
            befriend(owner, friends[i]);
        }
        upload(owner);
        long latest = upload(owner);
        String[] emojis = {"❤️", "❤️", "😂", "😢", "👍"};
        for (int i = 0; i < friends.length; i++) {
            putJson("/api/moments/" + latest + "/reaction", friends[i], Map.of("emoji", emojis[i])).andExpect(status().isOk());
        }
        for (String text : new String[] {"첫 댓글", "두 번째", "세 번째"}) {
            postJson("/api/moments/" + latest + "/comments", friends[0], Map.of("text", text)).andExpect(status().isCreated());
        }
        upload(viewer); // my own newer photo never replaces a friend's on my widget

        String body = body(widget(viewer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(startsWith(latest + "-")))
                .andExpect(jsonPath("$.moment.id").value(latest))
                .andExpect(jsonPath("$.moment.sender.id").value(owner.id()))
                .andExpect(jsonPath("$.moment.sender.displayName").value("민지"))
                .andExpect(jsonPath("$.moment.createdAt").value(matchesPattern(".+T.+Z")))
                .andExpect(jsonPath("$.moment.thumbUrl").value(matchesPattern("/media/" + latest + "/thumb\\.jpg\\?exp=\\d+&sig=.+")))
                .andExpect(jsonPath("$.reactions[*].emoji", contains("❤️", "👍", "😢")))
                .andExpect(jsonPath("$.reactions[*].count", contains(2, 1, 1)))
                .andExpect(jsonPath("$.reactionCount").value(5))
                .andExpect(jsonPath("$.comments[*].author", contains("친구0", "친구0")))
                .andExpect(jsonPath("$.comments[*].text", contains("두 번째", "세 번째")))
                .andExpect(jsonPath("$.commentCount").value(3))
                .andReturn());
        String version = JsonPath.read(body, "$.version");
        widget(viewer, null).andExpect(header().string(HttpHeaders.ETAG, "\"" + version + "\""));
    }

    @Test
    void versionChangesWithCommentsAndReactionsAndEtagGives304() throws Exception {
        var owner = signup("민지");
        var viewer = signup("준서");
        var other = signup("지우");
        befriend(owner, viewer);
        befriend(owner, other);
        long m = upload(owner);

        String v1 = version(viewer);
        widget(viewer, "\"" + v1 + "\"")
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + v1 + "\""))
                .andExpect(content().string(""));
        widget(viewer, "W/\"" + v1 + "\"").andExpect(status().isNotModified());
        widget(viewer, "\"something-else\"").andExpect(status().isOk());

        long comment = longAt(body(postJson("/api/moments/" + m + "/comments", other, Map.of("text", "와"))
                .andExpect(status().isCreated()).andReturn()), "$.id");
        String v2 = version(viewer);
        assertThat(v2).isNotEqualTo(v1);
        widget(viewer, "\"" + v1 + "\"").andExpect(status().isOk());
        widget(viewer, "\"" + v2 + "\"").andExpect(status().isNotModified());

        putJson("/api/moments/" + m + "/reaction", other, Map.of("emoji", "❤️")).andExpect(status().isOk());
        String v3 = version(viewer);
        assertThat(v3).isNotIn(v1, v2);

        putJson("/api/moments/" + m + "/reaction", other, Map.of("emoji", "😂")).andExpect(status().isOk());
        String v4 = version(viewer);
        assertThat(v4).isNotIn(v1, v2, v3);

        deleteAs(owner, "/api/comments/" + comment).andExpect(status().isNoContent());
        assertThat(version(viewer)).isNotIn(v2, v3, v4);
    }

    @Test
    void unfriendingClearsTheWidget() throws Exception {
        var owner = signup("민지");
        var viewer = signup("준서");
        befriend(owner, viewer);
        upload(owner);
        widget(viewer, null).andExpect(status().isOk());
        deleteAs(viewer, "/api/friends/" + owner.id()).andExpect(status().isNoContent());
        widget(viewer, null).andExpect(status().isNoContent());
    }

    private String version(TestUser user) throws Exception {
        return JsonPath.read(body(widget(user, null).andExpect(status().isOk()).andReturn()), "$.version");
    }

    private ResultActions widget(TestUser user, String ifNoneMatch) throws Exception {
        var request = get("/api/widget/latest").header(HttpHeaders.AUTHORIZATION, bearer(user));
        if (ifNoneMatch != null) {
            request.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        return mvc.perform(request);
    }
}
