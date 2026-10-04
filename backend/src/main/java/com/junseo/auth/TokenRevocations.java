package com.junseo.auth;

import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** LiliPlanet tokens that may not start another login (the session made from them was logged out). */
@Service
public class TokenRevocations {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TokenRevocations(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean revoked(String jti) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from revoked_tokens where jti = ? and expires_at > ?)",
                Boolean.class, jti, java.sql.Timestamp.from(clock.instant())));
    }

    public void revoke(String jti, java.time.Instant expiresAt) {
        jdbc.update("insert into revoked_tokens(jti, expires_at) values (?, ?) on conflict (jti) do nothing",
                jti, java.sql.Timestamp.from(expiresAt));
        jdbc.update("delete from revoked_tokens where expires_at <= ?", java.sql.Timestamp.from(clock.instant()));
    }
}
