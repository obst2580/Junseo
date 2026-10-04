package com.junseo.auth;

import com.junseo.auth.AuthController.AuthResponse;
import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.security.JwtService;
import com.junseo.common.security.Sessions;
import com.junseo.mail.Mailer;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 찾기: 메일로 6자리 코드를 보내고, 코드가 맞으면 새 비밀번호로 바꾼다.
 * 코드는 15분, 5번 틀리면 끝, 다시 보내기는 1분에 한 번. 가입한 메일인지는 알려 주지 않는다 (항상 「보냈어요」).
 * 바꾸면 다른 기기에 남은 로그인은 모두 끝난다.
 */
@Service
@ConditionalOnProperty(prefix = "junseo.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class PasswordResetService {

    static final Duration CODE_TTL = Duration.ofMinutes(15);
    static final Duration RESEND_AFTER = Duration.ofMinutes(1);
    static final int MAX_ATTEMPTS = 5;
    private static final int BCRYPT_MAX_BYTES = 72;

    private final SecureRandom random = new SecureRandom();
    private final UserRepository users;
    private final UserService userService;
    private final PasswordResetRepository resets;
    private final PasswordEncoder passwordEncoder;
    private final Sessions sessions;
    private final Mailer mailer;
    private final Clock clock;

    public PasswordResetService(
            UserRepository users,
            UserService userService,
            PasswordResetRepository resets,
            PasswordEncoder passwordEncoder,
            Sessions sessions,
            Mailer mailer,
            Clock clock) {
        this.users = users;
        this.userService = userService;
        this.resets = resets;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Transactional
    public void request(String email) {
        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        Instant now = clock.instant();
        PasswordReset reset = resets.findById(user.getId()).orElseGet(() -> new PasswordReset(user.getId()));
        if (reset.getSentAt() != null && reset.getSentAt().plus(RESEND_AFTER).isAfter(now)) {
            return;
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        reset.issue(passwordEncoder.encode(code), now, now.plus(CODE_TTL));
        resets.save(reset);
        mailer.send(user.getEmail(), "[잡다] 비밀번호 찾기 코드 " + code,
                user.getDisplayName() + "님, 비밀번호를 새로 정하려면 앱에 이 코드를 입력해 주세요.\n\n"
                        + "    " + code + "\n\n"
                        + "15분 동안 쓸 수 있어요. 직접 요청하지 않았다면 이 메일은 무시해 주세요.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse confirm(String email, String code, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "비밀번호가 너무 길어요. 조금 더 짧게 입력해 주세요.");
        }
        User user = users.findByEmail(email).orElseThrow(() -> new ApiException(ErrorCode.RESET_CODE_INVALID));
        PasswordReset reset = resets.findById(user.getId()).orElseThrow(() -> new ApiException(ErrorCode.RESET_CODE_INVALID));
        Instant now = clock.instant();
        if (reset.getExpiresAt().isBefore(now)) {
            resets.delete(reset);
            throw new ApiException(ErrorCode.RESET_CODE_INVALID);
        }
        if (code == null || !passwordEncoder.matches(code.strip(), reset.getCodeHash())) {
            // 틀린 횟수는 남긴다 (롤백하지 않음): 5번이면 코드를 버린다
            if (reset.failedAttempt() >= MAX_ATTEMPTS) {
                resets.delete(reset);
            }
            throw new ApiException(ErrorCode.RESET_CODE_INVALID);
        }
        resets.delete(reset);
        user.changePassword(passwordEncoder.encode(newPassword));
        // Every earlier login ends (the token version also changed); this device starts a new session
        sessions.revokeAll(user.getId());
        JwtService.IssuedToken token = sessions.open(user, now, Sessions.Kind.APP);
        return new AuthResponse(token.value(), token.expiresAt(), userService.toMe(user));
    }
}
