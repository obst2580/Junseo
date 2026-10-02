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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
            postJson("/api/moments/" + latest + "/reactions", friends[i], Map.of("emoji", emojis[i])).andExpect(status().isOk());
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

        // Counts are taps, not people: one friend tapping 😂 four more times moves it to the top.
        postJson("/api/moments/" + latest + "/reactions", friends[2], Map.of("emoji", "😂", "count", 4)).andExpect(status().isOk());
        widget(viewer, null)
                .andExpect(jsonPath("$.reactions[*].emoji", contains("😂", "❤️", "👍")))
                .andExpect(jsonPath("$.reactions[*].count", contains(5, 2, 1)))
                .andExpect(jsonPath("$.reactionCount").value(9));
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

        postJson("/api/moments/" + m + "/reactions", other, Map.of("emoji", "❤️")).andExpect(status().isOk());
        String v3 = version(viewer);
        assertThat(v3).isNotIn(v1, v2);

        postJson("/api/moments/" + m + "/reactions", other, Map.of("emoji", "😂")).andExpect(status().isOk());
        String v4 = version(viewer);
        assertThat(v4).isNotIn(v1, v2, v3);

        // Another tap on the same emoji changes the count, so the widget must redraw.
        postJson("/api/moments/" + m + "/reactions", other, Map.of("emoji", "😂")).andExpect(status().isOk());
        String v5 = version(viewer);
        assertThat(v5).isNotIn(v1, v2, v3, v4);

        deleteAs(owner, "/api/comments/" + comment).andExpect(status().isNoContent());
        assertThat(version(viewer)).isNotIn(v2, v3, v4, v5);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsHowLongANewPhotoTookToReachEachPhone(CapturedOutput output) throws Exception {
        var owner = signup("민지");
        var viewer = signup("준서");
        var other = signup("지우");
        befriend(owner, viewer);
        befriend(owner, other);
        long m = upload(owner);

        String v1 = body(mvc.perform(get("/api/widget/latest")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer))
                        .header(WidgetController.SOURCE_HEADER, "notification"))
                .andExpect(status().isOk()).andReturn());
        assertThat(output.getOut()).containsPattern(
                "Widget got new photo: user=" + viewer.id() + " moment=" + m + " source=notification after=\\d+ms");

        // Same photo with a new comment: a refresh, not a new photo.
        postJson("/api/moments/" + m + "/comments", other, Map.of("text", "와")).andExpect(status().isCreated());
        mvc.perform(get("/api/widget/latest")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer))
                        .header(HttpHeaders.IF_NONE_MATCH, "\"" + JsonPath.read(v1, "$.version") + "\"")
                        .header(WidgetController.SOURCE_HEADER, "widget"))
                .andExpect(status().isOk());
        assertThat(output.getOut()).doesNotContain("source=widget");

        // A header that is not a plain word never reaches the log as is (newlines are already refused by the firewall).
        mvc.perform(get("/api/widget/latest")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .header(WidgetController.SOURCE_HEADER, "Fake source=admin"))
                .andExpect(status().isOk());
        assertThat(output.getOut()).contains("user=" + other.id() + " moment=" + m + " source=unknown").doesNotContain("source=admin");
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
