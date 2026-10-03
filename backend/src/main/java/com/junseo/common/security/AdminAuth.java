package com.junseo.common.security;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.JunseoProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * Operator endpoints (/api/admin/**) use the {@code X-Admin-Token} header instead of a user login.
 * Switched off (404) unless JUNSEO_ADMIN_TOKEN is set.
 */
@Component
public class AdminAuth {

    public static final String HEADER = "X-Admin-Token";

    private final byte[] token;

    public AdminAuth(JunseoProperties props) {
        String t = props.admin().token();
        this.token = t == null || t.isBlank() ? null : t.getBytes(StandardCharsets.UTF_8);
    }

    public void check(String given) {
        if (token == null) {
            throw ApiException.notFound();
        }
        if (given == null || !MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.FORBIDDEN, "관리자 토큰이 올바르지 않아요.");
        }
    }
}
