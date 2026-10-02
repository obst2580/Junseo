package com.junseo.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.junseo.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** The app retries a send when the response is lost; the same clientId must never make a second copy. */
class SendRetryIntegrationTest extends IntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void retryingAOneToOneSendReturnsTheFirstCopyAndPushesOnce() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        befriend(a, b);
        String bApp = registerDevice(b, "app");
        String path = "/api/conversations/" + b.id() + "/messages";

        long first = idOf(postJson(path, a, Map.of("text", "도착했어?", "clientId", "c-0000001"))
                .andExpect(status().isCreated()));
        long again = idOf(postJson(path, a, Map.of("text", "도착했어?", "clientId", "c-0000001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("도착했어?")));

        assertThat(again).isEqualTo(first);
        // Only the sender sees its clientId (to match its pending copy).
        getAs(a, path).andExpect(jsonPath("$.items[0].clientId").value("c-0000001"));
        getAs(b, "/api/conversations/" + a.id() + "/messages")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].clientId").value(nullValue()));
        await().during(Duration.ofMillis(300)).atMost(WAIT).until(() -> push.to(bApp).size() == 1);

        // A different send with the same text is a new message.
        long other = idOf(postJson(path, a, Map.of("text", "도착했어?", "clientId", "c-0000002"))
                .andExpect(status().isCreated()));
        assertThat(other).isNotEqualTo(first);
    }

    @Test
    void clientIdsBelongToTheirSender() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        befriend(a, b);

        long fromA = idOf(postJson("/api/conversations/" + b.id() + "/messages", a, Map.of("text", "안녕", "clientId", "same-id-1")));
        long fromB = idOf(postJson("/api/conversations/" + a.id() + "/messages", b, Map.of("text", "안녕", "clientId", "same-id-1")));
        assertThat(fromB).isNotEqualTo(fromA);
    }

    @Test
    void sendsRacingWithTheSameClientIdLeaveOneMessage() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        befriend(a, b);
        String path = "/api/conversations/" + b.id() + "/messages";

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Long>> sends = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            sends.add(() -> {
                start.await();
                return idOf(postJson(path, a, Map.of("text", "동시에", "clientId", "race-0001")).andExpect(status().isCreated()));
            });
        }
        List<Long> ids = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(sends.size())) {
            List<Future<Long>> futures = sends.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Long> f : futures) {
                ids.add(f.get());
            }
        }

        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(jdbc.queryForObject("select count(*) from messages where sender_id = ?", Long.class, a.id())).isEqualTo(1);
    }

    @Test
    void retryingAGroupSendReturnsTheFirstCopy() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        TestUser c = signup("지우");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);
        long group = longAt(body(postJson("/api/groups", a, Map.of("memberIds", List.of(b.id(), c.id())))
                .andExpect(status().isCreated()).andReturn()), "$.id");
        String path = "/api/groups/" + group + "/messages";

        long first = idOf(postJson(path, a, Map.of("text", "토요일?", "clientId", "g-0000001")).andExpect(status().isCreated()));
        postAs(b, "/api/groups/" + group + "/read").andExpect(status().isNoContent());
        long again = idOf(postJson(path, a, Map.of("text", "토요일?", "clientId", "g-0000001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unreadCount").value(1)));

        assertThat(again).isEqualTo(first);
        getAs(a, path).andExpect(jsonPath("$.items[0].clientId").value("g-0000001"));
        getAs(c, path)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].clientId").value(nullValue()));
    }

    @Test
    void aMalformedClientIdIsRejected() throws Exception {
        TestUser a = signup("준서");
        TestUser b = signup("민지");
        befriend(a, b);
        postJson("/api/conversations/" + b.id() + "/messages", a, Map.of("text", "안녕", "clientId", "x"))
                .andExpect(status().isBadRequest());
        postJson("/api/conversations/" + b.id() + "/messages", a, Map.of("text", "안녕", "clientId", "has space 123"))
                .andExpect(status().isBadRequest());
        // Without a clientId sending works as before.
        postJson("/api/conversations/" + b.id() + "/messages", a, Map.of("text", "안녕")).andExpect(status().isCreated());
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(body(result.andReturn()), "$.id")).longValue();
    }
}
