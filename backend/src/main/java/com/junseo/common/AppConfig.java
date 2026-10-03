package com.junseo.common;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration
@EnableAsync
public class AppConfig {

    public static final String PUSH_EXECUTOR = "pushExecutor";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Timestamps go out as {@code 2026-09-30T12:34:56Z}: Swift's default ISO-8601 decoding rejects
     * fractional seconds, and the widget and notification extension decode these directly.
     */
    @Bean
    JacksonModule instantSecondsModule() {
        return new SimpleModule("instant-seconds").addSerializer(Instant.class, new ValueSerializer<>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializationContext ctx) {
                gen.writeString(DateTimeFormatter.ISO_INSTANT.format(value.truncatedTo(ChronoUnit.SECONDS)));
            }
        });
    }

    /** Push fan-out runs here so APNs latency or failures never reach the request thread. */
    @Bean(PUSH_EXECUTOR)
    TaskExecutor pushExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("push-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(64);
        return executor;
    }
}
