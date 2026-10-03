package com.junseo.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import com.junseo.support.TestImages;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class TemplateIntegrationTest extends IntegrationTest {

    private static final String TOKEN = "test-admin-token";

    private static Map<String, Object> spec(int w, int h, List<?> slots) {
        return Map.of("name", "지하철 광고", "width", w, "height", h, "backgroundColor", "#151515", "slots", slots);
    }

    private static final List<?> QUAD_WITH_GLOW = List.of(Map.of(
            "quad", List.of(List.of(20, 30), List.of(180, 20), List.of(180, 120), List.of(20, 110)),
            "aspect", 2,
            "grow", 5,
            "glow", Map.of("grow", 90, "blur", 0.045, "wash", 0.35)));

    private ResultActions putTemplate(String id, String token, Object meta, byte[] background, byte[] overlay) throws Exception {
        var req = multipart(HttpMethod.PUT, "/api/admin/templates/" + id)
                .file(new MockMultipartFile("background", "bg", "application/octet-stream", background))
                .param("meta", toJson(meta));
        if (overlay != null) {
            req.file(new MockMultipartFile("overlay", "ov", "image/png", overlay));
        }
        if (token != null) {
            req.header(TemplateAdminController.TOKEN_HEADER, token);
        }
        return mvc.perform(req);
    }

    @Test
    void uploadedTemplateIsListedForEveryUserWithPublicCachedPictures() throws Exception {
        var a = signup("민지");
        getAs(a, "/api/templates").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));

        putTemplate("subway", TOKEN, spec(200, 150, QUAD_WITH_GLOW), TestImages.jpeg(200, 150), TestImages.pngWithAlpha(200, 150))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        String list = body(getAs(a, "/api/templates")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("subway"))
                .andExpect(jsonPath("$.items[0].name").value("지하철 광고"))
                .andExpect(jsonPath("$.items[0].width").value(200))
                .andExpect(jsonPath("$.items[0].slots[0].aspect").value(2))
                .andExpect(jsonPath("$.items[0].slots[0].glow.wash").value(0.35))
                // 칸 · 그림을 보고 서버가 채운다 → 이걸 못 그리는 예전 앱은 이 템플릿을 건너뛴다
                .andExpect(jsonPath("$.items[0].requires").value(org.hamcrest.Matchers.contains("glow", "overlay", "quad")))
                .andReturn());
        String bg = JsonPath.read(list, "$.items[0].backgroundUrl");
        String ov = JsonPath.read(list, "$.items[0].overlayUrl");
        assertThat(bg).isEqualTo("/media/templates/subway/background.jpg?v=1");

        // 그림은 로그인 없이 (위젯처럼 헤더가 없어도), 한 번 받으면 계속 캐시
        mvc.perform(get(bg))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable"));
        mvc.perform(get(ov)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get(bg).header(HttpHeaders.AUTHORIZATION, "Bearer garbage")).andExpect(status().isOk());
    }

    @Test
    void reuploadBumpsTheVersionSoPhonesFetchNewPictures() throws Exception {
        var a = signup("민지");
        putTemplate("museum", TOKEN, spec(120, 150, QUAD_WITH_GLOW), TestImages.jpeg(120, 150), TestImages.pngWithAlpha(120, 150))
                .andExpect(status().isOk());
        // 두 번째는 PNG 바탕, 앞장 없음, 네모 칸
        putTemplate("museum", TOKEN, spec(120, 150, List.of(Map.of("x", 10, "y", 10, "size", 100, "radius", 16))),
                        TestImages.pngWithAlpha(120, 150), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        getAs(a, "/api/templates")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].backgroundUrl").value("/media/templates/museum/background.png?v=2"))
                .andExpect(jsonPath("$.items[0].overlayUrl").doesNotExist())
                .andExpect(jsonPath("$.items[0].requires.length()").value(0));
    }

    @Test
    void hiddenTemplatesLeaveTheListButKeepTheirPictures() throws Exception {
        var a = signup("민지");
        putTemplate("bus", TOKEN, spec(200, 150, QUAD_WITH_GLOW), TestImages.jpeg(200, 150), null).andExpect(status().isOk());
        putTemplate("card", TOKEN, Map.of("name", "선수 카드", "width", 200, "height", 150, "backgroundColor", "#14182e",
                        "slots", QUAD_WITH_GLOW, "sortOrder", 5), TestImages.jpeg(200, 150), null)
                .andExpect(status().isOk());
        // sortOrder 가 크면 앞에
        getAs(a, "/api/templates").andExpect(jsonPath("$.items[0].id").value("card")).andExpect(jsonPath("$.items[1].id").value("bus"));

        mvc.perform(delete("/api/admin/templates/bus").header(TemplateAdminController.TOKEN_HEADER, TOKEN)).andExpect(status().isNoContent());
        getAs(a, "/api/templates").andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value("card"));
        mvc.perform(get("/media/templates/bus/background.jpg?v=1")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/templates/nope").header(TemplateAdminController.TOKEN_HEADER, TOKEN)).andExpect(status().isNotFound());
    }

    @Test
    void adminApiNeedsTheAdminTokenNotAUserLogin() throws Exception {
        var a = signup("민지");
        byte[] bg = TestImages.jpeg(200, 150);
        putTemplate("subway", null, spec(200, 150, QUAD_WITH_GLOW), bg, null).andExpect(status().isForbidden());
        putTemplate("subway", "wrong", spec(200, 150, QUAD_WITH_GLOW), bg, null).andExpect(status().isForbidden());
        // 사용자 로그인 토큰으로는 안 된다
        mvc.perform(multipart(HttpMethod.PUT, "/api/admin/templates/subway")
                        .file(new MockMultipartFile("background", "bg", "image/jpeg", bg))
                        .param("meta", toJson(spec(200, 150, QUAD_WITH_GLOW)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(a)))
                .andExpect(status().isForbidden());
        getAs(a, "/api/templates").andExpect(jsonPath("$.items.length()").value(0));
        // 목록은 로그인한 사용자만
        mvc.perform(get("/api/templates")).andExpect(status().isUnauthorized());
    }

    @Test
    void badUploadsAreRejectedWithAReason() throws Exception {
        byte[] bg = TestImages.jpeg(200, 150);
        // 그림 크기가 템플릿 크기와 다름
        putTemplate("subway", TOKEN, spec(200, 160, QUAD_WITH_GLOW), bg, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("200×150")));
        // 앞장이 PNG 가 아님
        putTemplate("subway", TOKEN, spec(200, 150, QUAD_WITH_GLOW), bg, TestImages.jpeg(200, 150)).andExpect(status().isBadRequest());
        // 칸이 없음 / quad 꼭짓점이 3개 / 잘못된 id / 잘못된 JSON
        putTemplate("subway", TOKEN, spec(200, 150, List.of()), bg, null).andExpect(status().isBadRequest());
        putTemplate("subway", TOKEN, spec(200, 150, List.of(Map.of("quad", List.of(List.of(0, 0), List.of(1, 0), List.of(1, 1)), "aspect", 1))), bg, null)
                .andExpect(status().isBadRequest());
        putTemplate("Subway!", TOKEN, spec(200, 150, QUAD_WITH_GLOW), bg, null).andExpect(status().isBadRequest());
        mvc.perform(multipart(HttpMethod.PUT, "/api/admin/templates/subway")
                        .file(new MockMultipartFile("background", "bg", "image/jpeg", bg))
                        .param("meta", "{not json")
                        .header(TemplateAdminController.TOKEN_HEADER, TOKEN))
                .andExpect(status().isBadRequest());
        // 그림이 아닌 파일
        putTemplate("subway", TOKEN, spec(200, 150, QUAD_WITH_GLOW), "hello world!".getBytes(), null).andExpect(status().isBadRequest());
        var a = signup("민지");
        getAs(a, "/api/templates").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void onlyKnownTemplatePicturesAreServed() throws Exception {
        putTemplate("subway", TOKEN, spec(200, 150, QUAD_WITH_GLOW), TestImages.jpeg(200, 150), null).andExpect(status().isOk());
        mvc.perform(get("/media/templates/subway/secret.txt")).andExpect(status().isNotFound());
        mvc.perform(get("/media/templates/subway/overlay.png")).andExpect(status().isNotFound());
        mvc.perform(get("/media/templates/..%2F1/background.jpg")).andExpect(status().is4xxClientError());
    }
}
