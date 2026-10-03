package com.junseo.auth;

import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

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

    public void revoke(Jwt token) {
        jdbc.update("insert into revoked_tokens(jti, expires_at) values (?, ?) on conflict (jti) do nothing",
                token.getId(), java.sql.Timestamp.from(token.getExpiresAt()));
        jdbc.update("delete from revoked_tokens where expires_at <= ?", java.sql.Timestamp.from(clock.instant()));
    }
}
