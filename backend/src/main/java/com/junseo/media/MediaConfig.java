package com.junseo.media;

import com.junseo.common.JunseoProperties;
import java.nio.file.Path;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaConfig {

    @Bean
    @ConditionalOnProperty(prefix = "junseo.storage", name = "provider", havingValue = "blob")
    BlobContainerClient mediaContainer(JunseoProperties props) {
        return new BlobContainerClientBuilder().endpoint(props.storage().endpoint())
                .containerName(props.storage().container()).credential(new DefaultAzureCredentialBuilder().build()).buildClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "junseo.storage", name = "provider", havingValue = "blob")
    HealthIndicator blobHealthIndicator(BlobContainerClient mediaContainer) {
        return () -> {
            try { return mediaContainer.exists() ? Health.up().build() : Health.down().build(); }
            catch (RuntimeException e) { return Health.down().build(); }
        };
    }

    @Bean
    MediaStorage mediaStorage(JunseoProperties props, ObjectProvider<BlobContainerClient> container) {
        if ("blob".equals(props.storage().provider())) {
            return new AzureBlobMediaStorage(container.getObject());
        }
        if (!"local".equals(props.storage().provider())) throw new IllegalStateException("Unknown media storage provider");
        return new LocalMediaStorage(Path.of(props.storage().dir()));
    }
}
