package com.junseo.safety;

import com.junseo.user.User;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * People an operator removed. Deleting the account alone is not enough: the next LiliPlanet login would provision
 * a fresh account for the same person (and a local signup could reuse the email).
 */
@Service
public class Bans {

    public record BanView(long id, String externalIssuer, String externalSubject, String email, String displayName, String reason, Instant createdAt) {}

    private final JdbcTemplate jdbc;

    public Bans(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isBanned(String issuer, String subject) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from banned_identities where external_issuer = ? and external_subject = ?)", Boolean.class, issuer, subject));
    }

    public boolean isEmailBanned(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from banned_identities where external_subject is null and email = ?)", Boolean.class, normalize(email)));
    }

    /** Called in the operator's deletion transaction, before the user row goes. */
    public void ban(User user, String reason, Instant now) {
        if (user.isPlatform()) {
            jdbc.update("""
                    insert into banned_identities (external_issuer, external_subject, email, display_name, reason, created_at)
                    values (?, ?, ?, ?, ?, ?) on conflict do nothing""",
                    user.getExternalIssuer(), user.getExternalSubject(), user.getEmail(), user.getDisplayName(), reason, Timestamp.from(now));
        } else {
            jdbc.update("""
                    insert into banned_identities (email, display_name, reason, created_at)
                    values (?, ?, ?, ?) on conflict do nothing""",
                    normalize(user.getEmail()), user.getDisplayName(), reason, Timestamp.from(now));
        }
    }

    public List<BanView> list() {
        return jdbc.query("""
                select id, external_issuer, external_subject, email, display_name, reason, created_at
                from banned_identities order by created_at desc limit 500""",
                (rs, i) -> new BanView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getTimestamp(7).toInstant()));
    }

    /** Lets the person sign up or log in again (as a new, empty account). */
    public boolean lift(long id) {
        return jdbc.update("delete from banned_identities where id = ?", id) > 0;
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
