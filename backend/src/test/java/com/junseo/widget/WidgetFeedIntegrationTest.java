package com.junseo.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** GET /api/widget/feed: what the widget pages through (‹ › on the widget), and per-friend widgets. */
class WidgetFeedIntegrationTest extends IntegrationTest {

    @Test
    void holdsTheFiveNewestFriendPhotosOfTheLastDayNewestFirst() throws Exception {
        var me = signup("준서");
        var minji = signup("민지");
        var jiwoo = signup("지우");
        befriend(me, minji);
        befriend(me, jiwoo);
        upload(me); // my own photos never show on my widget
        List<Long> sent = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sent.add(upload(i % 2 == 0 ? minji : jiwoo));
        }

        String feed = body(feed(me, null, null).andExpect(status().isOk()).andReturn());
        assertThat(ids(feed)).containsExactly(sent.get(5), sent.get(4), sent.get(3), sent.get(2), sent.get(1));
        assertThat(JsonPath.<String>read(feed, "$.items[0].moment.sender.displayName")).isEqualTo("지우");
        assertThat(JsonPath.<List<?>>read(feed, "$.items[0].comments")).isEmpty();

        String version = JsonPath.read(feed, "$.version");
        assertThat(version).startsWith(sent.get(5) + "-");
        feed(me, null, "\"" + version + "\"").andExpect(status().isNotModified());

        // A comment on the 3rd photo changes what the widget shows on that page, so the version changes.
        postJson("/api/moments/" + sent.get(3) + "/comments", me, Map.of("text", "여기 어디야")).andExpect(status().isCreated());
        feed(me, null, "\"" + version + "\"").andExpect(status().isOk());
    }

    @Test
    void withNothingFromTheLastDayItShowsJustTheNewestOne() throws Exception {
        var me = signup("준서");
        var minji = signup("민지");
        befriend(me, minji);
        long old1 = upload(minji);
        long old2 = upload(minji);
        jdbc.update("update moments set created_at = now() - interval '3 days' where id in (?, ?)", old1, old2);

        assertThat(ids(body(feed(me, null, null).andExpect(status().isOk()).andReturn()))).containsExactly(old2);

        long fresh = upload(minji);
        assertThat(ids(body(feed(me, null, null).andReturn()))).containsExactly(fresh);
    }

    @Test
    void aWidgetSetToOneFriendShowsOnlyTheirPhotos() throws Exception {
        var me = signup("준서");
        var minji = signup("민지");
        var jiwoo = signup("지우");
        var stranger = signup("모르는사람");
        befriend(me, minji);
        befriend(me, jiwoo);
        long fromMinji = upload(minji);
        upload(jiwoo);
        long fromMinji2 = upload(minji);
        upload(jiwoo);

        assertThat(ids(body(feed(me, minji.id(), null).andExpect(status().isOk()).andReturn())))
                .containsExactly(fromMinji2, fromMinji);
        feed(me, stranger.id(), null).andExpect(status().isNoContent());
        feed(me, me.id(), null).andExpect(status().isNoContent());

        // Unfriending empties that widget.
        deleteAs(me, "/api/friends/" + minji.id()).andExpect(status().isNoContent());
        feed(me, minji.id(), null).andExpect(status().isNoContent());
    }

    private ResultActions feed(TestUser user, Long from, String ifNoneMatch) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/widget/feed" + (from == null ? "" : "?from=" + from))
                .header(HttpHeaders.AUTHORIZATION, bearer(user));
        if (ifNoneMatch != null) {
            request.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        return mvc.perform(request);
    }

    private static List<Long> ids(String feed) {
        return JsonPath.<List<Number>>read(feed, "$.items[*].moment.id").stream().map(Number::longValue).toList();
    }
}
