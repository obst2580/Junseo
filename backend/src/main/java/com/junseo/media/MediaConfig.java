package com.junseo.media;

import com.junseo.common.JunseoProperties;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaConfig {

    @Bean
    MediaStorage mediaStorage(JunseoProperties props) {
        return new LocalMediaStorage(Path.of(props.storage().dir()));
    }
}
