package com.junseo.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.FileSystemUtils;
import tools.jackson.databind.ObjectMapper;

/** Full application against the real PostgreSQL test database; every test starts from empty tables. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class IntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RecordingPushSender push;

    @Autowired
    protected RecordingMailer mail;

    @Autowired
    protected ObjectMapper json;

    @LocalServerPort
    protected int port;

    public record TestUser(long id, String token, String inviteCode, String name, String email) {}

    @BeforeEach
    void resetState() {
        // No RESTART IDENTITY: ids stay unique across tests, so a late async push from a previous
        // test can never be mistaken for one of this test's.
        jdbc.execute("truncate table banned_identities, templates, reports, blocks, password_resets, device_tokens, group_messages, chat_group_members, chat_groups, messages, comments, reactions, moment_recipients, moments, friendships, users");
        push.clear();
        mail.clear();
        FileSystemUtils.deleteRecursively(Path.of("build/test-media").toFile());
    }

    protected TestUser signup(String name) throws Exception {
        String email = "user" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.junseo.app";
        String body = body(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "password", "password123!", "displayName", name))))
                .andExpect(status().isCreated())
                .andReturn());
        return new TestUser(
                longAt(body, "$.user.id"), JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.user.inviteCode"), name, email);
    }

    protected void befriend(TestUser a, TestUser b) throws Exception {
        postJson("/api/friends", a, Map.of("inviteCode", b.inviteCode())).andExpect(status().isCreated());
    }

    protected long upload(TestUser user) throws Exception {
        return upload(user, TestImages.jpeg(64, 48));
    }

    protected long upload(TestUser user, byte[] image) throws Exception {
        String body = body(uploadRaw(user, image).andExpect(status().isCreated()).andReturn());
        return longAt(body, "$.id");
    }

    protected ResultActions uploadRaw(TestUser user, byte[] image) throws Exception {
        return mvc.perform(multipart("/api/moments")
                .file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", image))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    /** Upload to only the given friends (multipart field recipientIds, repeated). */
    protected ResultActions uploadTo(TestUser user, String... recipientIds) throws Exception {
        return mvc.perform(multipart("/api/moments")
                .file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", TestImages.jpeg(64, 48)))
                .param("recipientIds", recipientIds)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    protected String registerDevice(TestUser user, String kind) throws Exception {
        byte[] raw = new byte[32];
        ThreadLocalRandom.current().nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        putJson("/api/devices", user, Map.of("token", token, "kind", kind, "environment", "development"))
                .andExpect(status().isNoContent());
        return token;
    }

    protected ResultActions getAs(TestUser user, String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    protected ResultActions postJson(String path, TestUser user, Object body) throws Exception {
        return mvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    protected ResultActions postAs(TestUser user, String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    protected ResultActions putJson(String path, TestUser user, Object body) throws Exception {
        return mvc.perform(put(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    protected ResultActions patchJson(String path, TestUser user, Object body) throws Exception {
        return mvc.perform(patch(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    protected ResultActions deleteAs(TestUser user, String path) throws Exception {
        return mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    protected String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    protected static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    protected static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static long longAt(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }
}
