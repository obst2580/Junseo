package com.junseo.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    RecordingPushSender recordingPushSender() {
        return new RecordingPushSender();
    }

    @Bean
    @Primary
    RecordingMailer recordingMailer() {
        return new RecordingMailer();
    }

    /** Fresh schema per test JVM so migrations are always exercised from scratch. */
    @Bean
    FlywayMigrationStrategy cleanMigrate() {
        return flyway -> {
            try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
                String url = connection.getMetaData().getURL();
                if (!(url.startsWith("jdbc:postgresql://localhost:") || url.startsWith("jdbc:postgresql://127.0.0.1:"))
                        || !connection.getCatalog().equals("junseo_test")) {
                    throw new IllegalStateException("Refusing to clean a non-local/non-junseo_test database");
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("Could not verify isolated test database", e);
            }
            flyway.clean();
            flyway.migrate();
        };
    }
}
