package com.junseo.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import com.junseo.support.TestImages;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class MediaIntegrationTest extends IntegrationTest {

    private static final Pattern URL = Pattern.compile("/media/(\\d+)/(full|thumb)\\.jpg\\?exp=(\\d+)&sig=(.+)");

    @Autowired
    MediaUrlSigner signer;

    @Test
    void validSignatureServesJpegWithoutAuth() throws Exception {
        var a = signup("민지");
        String thumbUrl = JsonPath.read(body(uploadRaw(a, TestImages.jpeg(800, 600)).andReturn()), "$.thumbUrl");

        byte[] bytes = mvc.perform(get(thumbUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=86400"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(TestImages.read(bytes).getWidth()).isEqualTo(540);

        // A stale bearer token from the widget must not get in the way.
        mvc.perform(get(thumbUrl).header(HttpHeaders.AUTHORIZATION, "Bearer garbage")).andExpect(status().isOk());
    }

    @Test
    void tamperedOrExpiredUrlsAreForbidden() throws Exception {
        var a = signup("민지");
        String thumbUrl = JsonPath.read(body(uploadRaw(a, TestImages.jpeg(64, 48)).andReturn()), "$.thumbUrl");
        Matcher url = URL.matcher(thumbUrl);
        assertThat(url.matches()).isTrue();
        long id = Long.parseLong(url.group(1));
        long exp = Long.parseLong(url.group(3));
        String sig = url.group(4);
        String flipped = (sig.charAt(0) == 'A' ? "B" : "A") + sig.substring(1);
        long expired = Instant.now().getEpochSecond() - 60;

        for (String bad : new String[] {
                "/media/" + id + "/thumb.jpg?exp=" + exp + "&sig=" + flipped,
                "/media/" + id + "/thumb.jpg?exp=" + (exp + 86_400) + "&sig=" + sig,
                "/media/" + id + "/full.jpg?exp=" + exp + "&sig=" + sig,
                "/media/" + (id + 1) + "/thumb.jpg?exp=" + exp + "&sig=" + sig,
                "/media/" + id + "/thumb.jpg?exp=" + exp,
                "/media/" + id + "/thumb.jpg",
                "/media/" + id + "/thumb.jpg?exp=" + expired + "&sig=" + signer.signature(id, "thumb", expired),
                "/media/" + id + "/other.jpg?exp=" + exp + "&sig=" + signer.signature(id, "other", exp)}) {
            mvc.perform(get(bad))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }
}
