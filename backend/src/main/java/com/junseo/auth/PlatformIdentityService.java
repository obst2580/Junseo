package com.junseo.auth;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.JunseoProperties;
import com.junseo.common.security.Sessions;
import com.junseo.safety.Bans;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformIdentityService {
    private final UserRepository users;
    private final UserService profiles;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final JunseoProperties props;
    private final Sessions sessions;
    private final Bans bans;

    public PlatformIdentityService(UserRepository users, UserService profiles, JdbcTemplate jdbc, Clock clock, JunseoProperties props, Sessions sessions,
            Bans bans) {
        this.users = users;
        this.profiles = profiles;
        this.jdbc = jdbc;
        this.clock = clock;
        this.props = props;
        this.sessions = sessions;
        this.bans = bans;
    }

    @Transactional
    public User provision(Jwt token) {
        String issuer = token.getClaimAsString("iss");
        String subject = token.getSubject();
        // Serialize concurrent first logins, without matching email or the two databases' numeric IDs.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", issuer + ":" + subject);
        // Removed by an operator: logging in again must not hand out a fresh account
        if (bans.isBanned(issuer, subject)) throw new ApiException(ErrorCode.ACCOUNT_BANNED);
        return users.findByExternalIssuerAndExternalSubject(issuer, subject).orElseGet(() -> {
            String email = token.getClaimAsString("email");
            if (email == null || email.isBlank() || email.length() > 254) {
                throw new ApiException(ErrorCode.UNAUTHORIZED);
            }
            return users.saveAndFlush(User.platform(issuer, subject, email, profiles.newInviteCode(), clock.instant()));
        });
    }

    /** The account this central identity already has (re-confirming who you are must not provision one). */
    @Transactional(readOnly = true)
    public User existing(Jwt token) {
        return users.findByExternalIssuerAndExternalSubject(token.getClaimAsString("iss"), token.getSubject())
                .orElseThrow(() -> new ApiException(ErrorCode.REAUTH_NO_ACCOUNT));
    }

    /**
     * The Junseo user behind an API token. In both login modes this is Junseo's own session token (sub = user id):
     * the session must still be open, the account still there and the password unchanged since.
     */
    @Transactional(readOnly = true)
    public long require(Jwt token) {
        try {
            long id = Long.parseLong(token.getSubject());
            if (!sessions.isValid(id, token)) throw new ApiException(ErrorCode.UNAUTHORIZED);
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** When the person signed in at LiliPlanet: auth_time if the central token has it, otherwise when it was issued. */
    public Instant authenticatedAt(Jwt central) {
        Instant at = central.hasClaim("auth_time") ? central.getClaimAsInstant("auth_time") : central.getIssuedAt();
        Instant now = clock.instant();
        return at == null || at.isAfter(now) ? now : at;
    }
}
