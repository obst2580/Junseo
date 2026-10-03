package com.junseo.auth;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.JunseoProperties;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserService;
import java.time.Clock;
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

    public PlatformIdentityService(UserRepository users, UserService profiles, JdbcTemplate jdbc, Clock clock, JunseoProperties props) {
        this.users = users;
        this.profiles = profiles;
        this.jdbc = jdbc;
        this.clock = clock;
        this.props = props;
    }

    @Transactional
    public User provision(Jwt token) {
        String issuer = token.getClaimAsString("iss");
        String subject = token.getSubject();
        // Serialize concurrent first logins, without matching email or the two databases' numeric IDs.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", issuer + ":" + subject);
        return users.findByExternalIssuerAndExternalSubject(issuer, subject).orElseGet(() -> {
            String email = token.getClaimAsString("email");
            if (email == null || email.isBlank() || email.length() > 254) {
                throw new ApiException(ErrorCode.UNAUTHORIZED);
            }
            return users.saveAndFlush(User.platform(issuer, subject, email, profiles.newInviteCode(), clock.instant()));
        });
    }

    @Transactional(readOnly = true)
    public long require(Jwt token) {
        if ("platform".equals(props.auth().mode())) {
            return users.findByExternalIssuerAndExternalSubject(token.getClaimAsString("iss"), token.getSubject())
                    .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED)).getId();
        }
        try {
            long id = Long.parseLong(token.getSubject());
            if (!users.existsById(id)) throw new ApiException(ErrorCode.UNAUTHORIZED);
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }
}
