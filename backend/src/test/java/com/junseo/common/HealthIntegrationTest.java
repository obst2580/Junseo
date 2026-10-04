package com.junseo.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.junseo.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class HealthIntegrationTest extends IntegrationTest {

    /** The deploy script and App Service health check use this; mail (no SMTP here) must not take it down. */
    @Test
    void healthIsUpWithoutSmtp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
