package com.junseo.common.security;

import com.junseo.common.ErrorBody;
import com.junseo.common.ErrorCode;
import com.junseo.common.JunseoProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    static final String EXPO_WEB_ORIGIN = "http://localhost:8081";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, e) ->
                write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorBody.of(ErrorCode.UNAUTHORIZED), json);
        AccessDeniedHandler forbidden = (request, response, e) ->
                write(response, HttpServletResponse.SC_FORBIDDEN, ErrorBody.of(ErrorCode.FORBIDDEN), json);

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(c -> {})
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/start", "/api/auth/exchange",
                                "/api/auth/password-reset", "/api/auth/password-reset/confirm").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/login", "/login.html", "/auth/**", "/api/auth/config", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        // 이용약관 · 개인정보처리방침 (앱 · 앱스토어에서 연결하는 공개 페이지)
                        .requestMatchers(HttpMethod.GET, "/legal/**").permitAll()
                        // 관리자 API 는 로그인 대신 X-Admin-Token 을 컨트롤러가 확인한다 (TemplateAdminController)
                        .requestMatchers("/api/admin/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 실시간 채널: 첫 메시지로 토큰을 받아 RealtimeHandler 가 확인한다
                        .requestMatchers(HttpMethod.GET, "/ws").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> {}))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden));
        return http.build();
    }

    /** A stale token sent to login, /media or the admin API must not turn those endpoints into a 401. */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return path.equals("/api/auth/signup") || path.equals("/api/auth/login") || path.equals("/api/auth/start") || path.equals("/api/auth/exchange")
                    || path.startsWith("/api/auth/password-reset") || path.startsWith("/auth/")
                    || path.startsWith("/api/admin/") || path.startsWith("/media/") || path.startsWith("/legal/") || path.equals("/ws")
                    ? null
                    : delegate.resolve(request);
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "junseo.auth", name = "mode", havingValue = "local", matchIfMissing = true)
    SecretKey jwtSecretKey(JunseoProperties props) {
        return Secrets.hmacKey("junseo.jwt.secret", props.jwt().secret());
    }

    @Bean
    @ConditionalOnProperty(prefix = "junseo.auth", name = "mode", havingValue = "local", matchIfMissing = true)
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Only for local development from Expo web; native apps do not send an Origin. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(JunseoProperties props) {
        List<String> origins = new ArrayList<>();
        if ("local".equals(props.auth().mode())) origins.add(EXPO_WEB_ORIGIN);
        props.cors().extraOrigins().stream().map(String::trim).filter(o -> !o.isEmpty()).forEach(origins::add);
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("ETag"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void write(HttpServletResponse response, int status, ErrorBody body, ObjectMapper json)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.getWriter().write(json.writeValueAsString(body));
    }
}
