package com.junseo.common.security;

import com.junseo.user.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** A valid signature is not enough: the account may be gone, or the password changed after this login. */
@Component
public class Sessions {

    /** Claim holding the user's token version at login (missing in tokens from before it existed = 0). */
    public static final String VERSION_CLAIM = "ver";

    private final UserRepository users;

    public Sessions(UserRepository users) {
        this.users = users;
    }

    public boolean isValid(long userId, Jwt token) {
        Object claim = token.getClaims().get(VERSION_CLAIM);
        long version = claim instanceof Number n ? n.longValue() : 0;
        return users.findById(userId).map(u -> u.getTokenVersion() == version).orElse(false);
    }
}
