package com.junseo.common;

import java.net.URI;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionGuard implements InitializingBean {
    private final JunseoProperties props;
    private final Environment env;
    public ProductionGuard(JunseoProperties props, Environment env) { this.props = props; this.env = env; }

    @Override public void afterPropertiesSet() {
        if (env.matchesProfiles("dev", "test") || !"platform".equals(props.auth().mode())) fail("production authentication/profile");
        String jwtSecret = props.jwt().secret();
        if (jwtSecret == null || jwtSecret.startsWith("dev-only-") || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) fail("session signing secret");
        String mediaSecret = props.media().signingSecret();
        if (mediaSecret == null || mediaSecret.startsWith("dev-only-") || mediaSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) fail("media signing secret");
        if (!"blob".equals(props.storage().provider())) fail("persistent Blob storage");
        for (String value : new String[]{props.auth().publicBaseUrl(), props.auth().jwksUrl(), props.auth().loginUrl(), props.storage().endpoint()}) {
            if (value == null || !"https".equals(URI.create(value).getScheme()) || URI.create(value).getHost() == null
                    || URI.create(value).getHost().equals("localhost") || URI.create(value).getUserInfo() != null) fail("HTTPS endpoint");
        }
        if (!"auth.liliplanet.net".equals(props.auth().issuer()) || !"junseo-api".equals(props.auth().audience())) fail("platform identity contract");
        if (!env.getProperty("spring.datasource.url", "").contains("sslmode=verify-full")) fail("database TLS verification");
    }
    private static void fail(String setting) { throw new IllegalStateException("Invalid Junseo " + setting); }
}
