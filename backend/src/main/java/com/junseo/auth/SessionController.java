package com.junseo.auth;

import com.junseo.common.security.CurrentUser;
import com.junseo.common.security.JwtService.IssuedToken;
import com.junseo.common.security.Sessions;
import com.junseo.realtime.RealtimeHub;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Junseo sessions in both login modes: renew while the app is used, end on logout. */
@RestController
public class SessionController {

    public record Renewed(String accessToken, Instant expiresAt) {}

    private final Sessions sessions;
    private final RealtimeHub hub;

    public SessionController(Sessions sessions, RealtimeHub hub) {
        this.sessions = sessions;
        this.hub = hub;
    }

    /** Same session, new expiry. The app stores it where the widget and extensions read it. */
    @PostMapping("/api/auth/renew")
    ResponseEntity<Renewed> renew(@CurrentUser long me, @AuthenticationPrincipal Jwt token) {
        IssuedToken renewed = sessions.renew(me, token);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(new Renewed(renewed.value(), renewed.expiresAt()));
    }

    /** Ends this device's session (all its tokens and sockets). Other devices and other LiliPlanet products stay signed in. */
    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(@CurrentUser long me, @AuthenticationPrincipal Jwt token) {
        sessions.revoke(token);
        hub.closeSession(Sessions.sessionId(token));
        return ResponseEntity.noContent().build();
    }
}
