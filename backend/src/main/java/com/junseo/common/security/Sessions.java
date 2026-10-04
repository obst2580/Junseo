package com.junseo.common.security;

import com.junseo.auth.TokenRevocations;
import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.JunseoProperties;
import com.junseo.common.security.JwtService.IssuedToken;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Junseo's own login sessions, in both login modes. LiliPlanet (or the local password) only proves who someone is;
 * the app, the widget and the notification extension then use a Junseo token tied to a session row.
 * A token is valid while its session is not revoked, the account still exists and the password was not changed since.
 * The app renews its token while it is used ({@link #renew}), so people stay signed in without logging in again;
 * logout, account deletion, a ban or a password change end it at once.
 */
@Component
public class Sessions {

    /** Claim holding the user's token version at login (missing = 0). */
    public static final String VERSION_CLAIM = "ver";
    public static final String SESSION_CLAIM = "sid";
    /** When the person actually signed in (epoch seconds). Renewal keeps it. */
    public static final String AUTH_TIME_CLAIM = "auth_time";

    /** app: a normal sign-in. reauth: the short login right before deleting the account (not renewable). */
    public enum Kind { APP, REAUTH }

    static final Duration REAUTH_TTL = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final JwtService jwt;
    private final TokenRevocations revocations;
    private final JunseoProperties props;
    private final Clock clock;

    public Sessions(JdbcTemplate jdbc, UserRepository users, JwtService jwt, TokenRevocations revocations, JunseoProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.jwt = jwt;
        this.revocations = revocations;
        this.props = props;
        this.clock = clock;
    }

    /** A new session after a local sign-in (password); authenticatedAt is when the person signed in. */
    @Transactional
    public IssuedToken open(User user, Instant authenticatedAt, Kind kind) {
        return open(user, authenticatedAt, kind, null);
    }

    /** central: the LiliPlanet token this sign-in came from (refused for new logins once this session is logged out). */
    @Transactional
    public IssuedToken open(User user, Instant authenticatedAt, Kind kind, Jwt central) {
        Instant now = clock.instant();
        // Sessions nobody can use any more (revoked, or idle past a token's lifetime) are just history
        jdbc.update("delete from sessions where user_id = ? and (revoked_at is not null or renewed_at < ?)",
                user.getId(), Timestamp.from(now.minus(props.jwt().ttl())));
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into sessions (id, user_id, kind, authenticated_at, created_at, renewed_at, central_jti, central_expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)""",
                id, user.getId(), kind.name().toLowerCase(), Timestamp.from(authenticatedAt), Timestamp.from(now), Timestamp.from(now),
                central == null ? null : central.getId(),
                central == null || central.getExpiresAt() == null ? null : Timestamp.from(central.getExpiresAt()));
        return jwt.issue(user.getId(), user.getTokenVersion(), id, authenticatedAt, now, kind == Kind.APP ? props.jwt().ttl() : REAUTH_TTL);
    }

    /** A fresh token for the same session (sliding expiry). The old token keeps working until it expires on its own. */
    @Transactional
    public IssuedToken renew(long userId, Jwt token) {
        UUID id = sessionId(token);
        List<Instant> authenticatedAt = id == null ? List.of() : jdbc.query("""
                select s.authenticated_at from sessions s
                where s.id = ? and s.user_id = ? and s.kind = 'app' and s.revoked_at is null""",
                (rs, i) -> rs.getTimestamp(1).toInstant(), id, userId);
        User user = users.findById(userId).orElse(null);
        if (authenticatedAt.isEmpty() || user == null || user.getTokenVersion() != version(token)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        Instant now = clock.instant();
        jdbc.update("update sessions set renewed_at = ? where id = ?", Timestamp.from(now), id);
        return jwt.issue(userId, user.getTokenVersion(), id, authenticatedAt.getFirst(), now, props.jwt().ttl());
    }

    /**
     * Logout: every token of this session stops working (other devices stay signed in), and the LiliPlanet token it
     * came from can no longer start a login (it would otherwise stay usable for its ~12 hours).
     */
    @Transactional
    public void revoke(Jwt token) {
        UUID id = sessionId(token);
        if (id == null) return;
        jdbc.query("""
                update sessions set revoked_at = ? where id = ? and revoked_at is null
                returning central_jti, central_expires_at""",
                rs -> {
                    String jti = rs.getString(1);
                    Timestamp expires = rs.getTimestamp(2);
                    if (jti != null && expires != null) revocations.revoke(jti, expires.toInstant());
                },
                Timestamp.from(clock.instant()), id);
    }

    /** Every device of this person (password changed). */
    public void revokeAll(long userId) {
        jdbc.update("update sessions set revoked_at = ? where user_id = ? and revoked_at is null", Timestamp.from(clock.instant()), userId);
    }

    /** Signature and expiry are already checked by the decoder: here the session, the account and the password version. */
    public boolean isValid(long userId, Jwt token) {
        UUID id = sessionId(token);
        if (id == null) return false;
        List<Integer> versions = jdbc.query("""
                select u.token_version from sessions s join users u on u.id = s.user_id
                where s.id = ? and s.user_id = ? and s.revoked_at is null""",
                (rs, i) -> rs.getInt(1), id, userId);
        return !versions.isEmpty() && versions.getFirst() == version(token);
    }

    /** The realtime channel re-checks on each ping: has this socket's session been ended? */
    public boolean isActive(UUID session) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from sessions where id = ? and revoked_at is null)", Boolean.class, session));
    }

    public static UUID sessionId(Jwt token) {
        String raw = token.getClaimAsString(SESSION_CLAIM);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long version(Jwt token) {
        Object claim = token.getClaims().get(VERSION_CLAIM);
        return claim instanceof Number n ? n.longValue() : 0;
    }
}
