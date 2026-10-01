package com.junseo.moment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import com.junseo.support.TestImages;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class MomentIntegrationTest extends IntegrationTest {

    @Test
    void uploadJpegReturnsMomentAndBothVariants() throws Exception {
        var a = signup("민지");
        String body = body(uploadRaw(a, TestImages.jpeg(2000, 1000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sender.id").value(a.id()))
                .andExpect(jsonPath("$.sender.displayName").value("민지"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ")))
                .andExpect(jsonPath("$.imageUrl").value(matchesPattern("/media/\\d+/full\\.jpg\\?exp=\\d+&sig=[A-Za-z0-9_-]{43}")))
                .andExpect(jsonPath("$.thumbUrl").value(matchesPattern("/media/\\d+/thumb\\.jpg\\?exp=\\d+&sig=[A-Za-z0-9_-]{43}")))
                .andExpect(jsonPath("$.reactions", hasSize(0)))
                .andExpect(jsonPath("$.myReactions", hasSize(0)))
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.recentComments", hasSize(0)))
                .andExpect(jsonPath("$.comments").doesNotExist())
                .andReturn());
        assertThat(body).contains("\"myReactions\":[]");

        BufferedImage full = TestImages.read(media(JsonPath.read(body, "$.imageUrl")));
        BufferedImage thumb = TestImages.read(media(JsonPath.read(body, "$.thumbUrl")));
        assertThat(new int[] {full.getWidth(), full.getHeight()}).containsExactly(1440, 720);
        assertThat(new int[] {thumb.getWidth(), thumb.getHeight()}).containsExactly(540, 270);
    }

    @Test
    void pngWithAlphaIsFlattenedOntoWhiteAndNotUpscaled() throws Exception {
        var a = signup("민지");
        String body = body(uploadRaw(a, TestImages.pngWithAlpha(300, 200)).andExpect(status().isCreated()).andReturn());
        BufferedImage full = TestImages.read(media(JsonPath.read(body, "$.imageUrl")));
        assertThat(new int[] {full.getWidth(), full.getHeight()}).containsExactly(300, 200);
        int transparentArea = full.getRGB(20, 100);
        assertThat(transparentArea & 0xFF).isGreaterThan(240);
        assertThat((transparentArea >> 8) & 0xFF).isGreaterThan(240);
        assertThat((transparentArea >> 16) & 0xFF).isGreaterThan(240);
    }

    @Test
    void metadataIsStripped() throws Exception {
        var a = signup("민지");
        byte[] original = TestImages.withExif(TestImages.jpeg(400, 300), 1);
        assertThat(TestImages.jpegMarkers(original)).contains(0xE1);
        assertThat(TestImages.contains(original, TestImages.EXIF_SECRET)).isTrue();

        String body = body(uploadRaw(a, original).andExpect(status().isCreated()).andReturn());
        for (String url : List.of(JsonPath.<String>read(body, "$.imageUrl"), JsonPath.<String>read(body, "$.thumbUrl"))) {
            byte[] stored = media(url);
            assertThat(TestImages.jpegMarkers(stored)).doesNotContain(0xE1, 0xE2, 0xED, 0xFE);
            assertThat(TestImages.contains(stored, "Exif")).isFalse();
            assertThat(TestImages.contains(stored, TestImages.EXIF_SECRET)).isFalse();
        }
    }

    @Test
    void invalidImagesAreRejected() throws Exception {
        var a = signup("민지");
        byte[] jpegMagicThenGarbage = new byte[2048];
        Arrays.fill(jpegMagicThenGarbage, (byte) 7);
        jpegMagicThenGarbage[0] = (byte) 0xFF;
        jpegMagicThenGarbage[1] = (byte) 0xD8;
        jpegMagicThenGarbage[2] = (byte) 0xFF;
        byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(TestImages.jpeg(10, 10), 0, tooLarge, 0, 3);

        for (byte[] bad : List.of(
                "hello".getBytes(StandardCharsets.UTF_8),
                "GIF89a-not-allowed".getBytes(StandardCharsets.US_ASCII),
                jpegMagicThenGarbage,
                tooLarge,
                new byte[0])) {
            uploadRaw(a, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        }
        mvc.perform(multipart("/api/moments").header(HttpHeaders.AUTHORIZATION, bearer(a)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** Goes through Tomcat's real multipart parsing, which MockMvc bypasses. */
    @Test
    void oversizedUploadOverHttpIsInvalidImageNot500() throws Exception {
        var a = signup("민지");
        String boundary = "junseo-test-boundary";
        ByteArrayOutputStream multipart = new ByteArrayOutputStream();
        multipart.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"big.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        multipart.writeBytes(new byte[11 * 1024 * 1024]);
        multipart.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/moments"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(a))
                        .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"INVALID_IMAGE\"");
    }

    @Test
    void recipientSeesItButNonFriendGetsNotFound() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        var stranger = signup("모르는사람");
        befriend(a, b);
        long m = upload(a);

        getAs(b, "/api/moments/" + m).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(m));
        getAs(b, "/api/moments").andExpect(jsonPath("$.items[*].id", contains((int) m)));
        getAs(a, "/api/moments").andExpect(jsonPath("$.items[*].id", contains((int) m)));

        getAs(stranger, "/api/moments/" + m)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        getAs(stranger, "/api/moments").andExpect(jsonPath("$.items", hasSize(0)));
        getAs(b, "/api/moments/999999999").andExpect(status().isNotFound());
    }

    @Test
    void unfriendHidesTheMomentEverywhere() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        befriend(a, b);
        long m = upload(a);
        deleteAs(b, "/api/friends/" + a.id()).andExpect(status().isNoContent());

        getAs(b, "/api/moments/" + m).andExpect(status().isNotFound());
        getAs(b, "/api/moments").andExpect(jsonPath("$.items", hasSize(0)));
        postJson("/api/moments/" + m + "/reactions", b, Map.of("emoji", "👍")).andExpect(status().isNotFound());
        deleteAs(b, "/api/moments/" + m + "/reactions").andExpect(status().isNotFound());
        postJson("/api/moments/" + m + "/comments", b, Map.of("text", "안녕")).andExpect(status().isNotFound());
        postJson("/api/moments/" + m + "/replies", b, Map.of("text", "안녕")).andExpect(status().isNotFound());
        getAs(b, "/api/widget/latest").andExpect(status().isNoContent());
    }

    @Test
    void friendAddedAfterPostingCannotSeeOldPhotos() throws Exception {
        var a = signup("민지");
        long before = upload(a);
        var late = signup("늦은친구");
        befriend(late, a);
        long after = upload(a);

        getAs(late, "/api/moments/" + before).andExpect(status().isNotFound());
        getAs(late, "/api/moments/" + after).andExpect(status().isOk());
        getAs(late, "/api/moments").andExpect(jsonPath("$.items[*].id", contains((int) after)));
    }

    @Test
    void listIsNewestFirstWithCursorPagination() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        befriend(a, b);
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = upload(a);
        }
        long own = upload(b);

        String page1 = body(getAs(b, "/api/moments?limit=2&userId=" + a.id())
                .andExpect(jsonPath("$.items[*].id", contains((int) ids[4], (int) ids[3])))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn());
        String page2 = body(getAs(b, "/api/moments?limit=2&userId=" + a.id() + "&cursor=" + JsonPath.read(page1, "$.nextCursor"))
                .andExpect(jsonPath("$.items[*].id", contains((int) ids[2], (int) ids[1])))
                .andReturn());
        getAs(b, "/api/moments?limit=2&userId=" + a.id() + "&cursor=" + JsonPath.read(page2, "$.nextCursor"))
                .andExpect(jsonPath("$.items[*].id", contains((int) ids[0])))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        getAs(b, "/api/moments")
                .andExpect(jsonPath("$.items[*].id",
                        contains((int) own, (int) ids[4], (int) ids[3], (int) ids[2], (int) ids[1], (int) ids[0])))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        getAs(b, "/api/moments?userId=" + b.id()).andExpect(jsonPath("$.items[*].id", contains((int) own)));
        // Empty parameters, exactly as written in the contract's example URL.
        getAs(b, "/api/moments?cursor=&limit=&userId=").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(6)));
        getAs(b, "/api/moments?limit=1000").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(6)));
        getAs(b, "/api/moments?cursor=bm9wZQ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void detailIncludesAllCommentsOldestFirst() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        befriend(a, b);
        long m = upload(a);
        for (String text : List.of("하나", "둘", "셋")) {
            postJson("/api/moments/" + m + "/comments", b, Map.of("text", text)).andExpect(status().isCreated());
        }

        getAs(a, "/api/moments/" + m)
                .andExpect(jsonPath("$.comments[*].text", contains("하나", "둘", "셋")))
                .andExpect(jsonPath("$.recentComments[*].text", contains("둘", "셋")))
                .andExpect(jsonPath("$.commentCount").value(3));
        getAs(a, "/api/moments").andExpect(jsonPath("$.items[0].comments").doesNotExist())
                .andExpect(jsonPath("$.items[0].recentComments[*].text", contains("둘", "셋")));
    }

    @Test
    void onlyTheSenderCanDelete() throws Exception {
        var a = signup("민지");
        var b = signup("지우");
        var stranger = signup("남");
        befriend(a, b);
        String body = body(uploadRaw(a, TestImages.jpeg(64, 48)).andExpect(status().isCreated()).andReturn());
        long m = longAt(body, "$.id");
        postJson("/api/moments/" + m + "/replies", b, Map.of("text", "멋지다")).andExpect(status().isCreated());

        deleteAs(b, "/api/moments/" + m).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        deleteAs(stranger, "/api/moments/" + m).andExpect(status().isNotFound());
        deleteAs(a, "/api/moments/" + m).andExpect(status().isNoContent());

        getAs(a, "/api/moments/" + m).andExpect(status().isNotFound());
        mvc.perform(get(JsonPath.<String>read(body, "$.thumbUrl"))).andExpect(status().isNotFound());
        // The reply survives without its photo.
        getAs(a, "/api/conversations/" + b.id() + "/messages")
                .andExpect(jsonPath("$.items[0].text").value("멋지다"))
                .andExpect(jsonPath("$.items[0].moment").value(nullValue()));
    }

    private byte[] media(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }
}
