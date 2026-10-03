package com.junseo.auth;

import com.junseo.auth.AuthController.AuthResponse;
import com.junseo.auth.AuthController.LoginRequest;
import com.junseo.auth.AuthController.SignupRequest;
import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.security.JwtService;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "junseo.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class AuthService {

    /** BCrypt only looks at the first 72 bytes; refuse rather than silently ignore the rest. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(
            UserRepository users,
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock) {
        this.users = users;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "비밀번호가 너무 길어요. 조금 더 짧게 입력해 주세요.");
        }
        if (users.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                userService.newInviteCode(),
                clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        return respond(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        return respond(user);
    }

    private AuthResponse respond(User user) {
        JwtService.IssuedToken token = jwtService.issue(user.getId());
        return new AuthResponse(token.value(), token.expiresAt(), userService.toMe(user));
    }
}
