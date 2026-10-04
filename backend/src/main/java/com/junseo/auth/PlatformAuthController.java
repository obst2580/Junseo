package com.junseo.auth;

import com.junseo.common.JunseoProperties;
import com.junseo.common.security.Sessions;
import com.junseo.user.Me;
import com.junseo.user.User;
import com.junseo.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix = "junseo.auth", name = "mode", havingValue = "platform")
public class PlatformAuthController {
    public record StartRequest(@NotBlank @Size(max = 128) String codeChallenge, @NotBlank @Size(max = 512) String returnUri) {}
    /** reauth: logging in again to confirm (account deletion). Never creates an account for an unknown identity. */
    public record ExchangeRequest(@NotBlank @Size(max = 128) String code, @NotBlank @Size(max = 128) String codeVerifier, Boolean reauth) {}
    public record Session(String accessToken, Instant expiresAt, Me user, boolean needsOnboarding) {}
    private final PlatformLoginFlow flow;
    private final PlatformIdentityService identities;
    private final UserService profiles;
    private final Sessions sessions;
    private final JunseoProperties props;

    public PlatformAuthController(PlatformLoginFlow flow, PlatformIdentityService identities, UserService profiles,
            Sessions sessions, JunseoProperties props) {
        this.flow = flow; this.identities = identities; this.profiles = profiles;
        this.sessions = sessions; this.props = props;
    }

    @GetMapping("/api/auth/config")
    Map<String, String> config() {
        return Map.of("mode", "platform", "loginUrl", props.auth().loginUrl(), "audience", props.auth().audience());
    }

    @PostMapping("/api/auth/start")
    ResponseEntity<PlatformLoginFlow.Start> start(@Valid @RequestBody StartRequest request, HttpServletRequest http) {
        // Behind App Service, server.forward-headers-strategy turns X-Forwarded-For into the remote address
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(flow.start(request.codeChallenge(), request.returnUri(), http.getRemoteAddr()));
    }

    @GetMapping("/auth/launch")
    ResponseEntity<Void> launch(@RequestParam String state) {
        PlatformLoginFlow.Launch result = flow.launch(state);
        return ResponseEntity.status(302).location(URI.create(result.loginUrl()))
                .header(HttpHeaders.SET_COOKIE, cookie(result.cookie(), Duration.ofMinutes(10)).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("Referrer-Policy", "no-referrer").build();
    }

    @GetMapping("/auth/callback")
    ResponseEntity<Void> callback(@RequestParam String state, @RequestParam String token,
            @CookieValue(name = "junseo_login", required = false) String cookie) {
        PlatformLoginFlow.Callback result = flow.callback(state, cookie, token);
        String separator = result.returnUrl().contains("?") ? "&" : "?";
        String target = result.returnUrl() + separator + "code=" + result.code() + "&state=" + result.state();
        return ResponseEntity.status(302).location(URI.create(target))
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("Referrer-Policy", "no-referrer").build();
    }

    /**
     * The LiliPlanet token proves who this is, once. What the app keeps is a Junseo session token: it outlives the
     * central token (about 12 hours, no refresh) and is renewed while the app is used, so the app, the widget and the
     * notification extension stay signed in. A reauth login gets a short, non-renewable session (account deletion).
     */
    @PostMapping("/api/auth/exchange")
    ResponseEntity<Session> exchange(@Valid @RequestBody ExchangeRequest request) {
        Jwt central = flow.exchange(request.code(), request.codeVerifier());
        boolean reauth = Boolean.TRUE.equals(request.reauth());
        User user = reauth ? identities.existing(central) : identities.provision(central);
        var token = sessions.open(user, identities.authenticatedAt(central), reauth ? Sessions.Kind.REAUTH : Sessions.Kind.APP, central);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new Session(token.value(), token.expiresAt(), profiles.toMe(user), !user.isOnboarded()));
    }

    private ResponseCookie cookie(String value, Duration duration) {
        return ResponseCookie.from("junseo_login", value).httpOnly(true).secure(props.auth().publicBaseUrl().startsWith("https://"))
                .sameSite("Lax").path("/auth").maxAge(duration).build();
    }
}
