package com.junseo.auth;

import com.junseo.user.Me;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "junseo.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class AuthController {

    public record SignupRequest(
            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "이메일 형식이 올바르지 않아요.")
            @Size(max = 254, message = "이메일이 너무 길어요.")
            String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(min = 8, max = 72, message = "비밀번호는 8~72자로 입력해 주세요.")
            String password,
            @NotBlank(message = "이름을 입력해 주세요.")
            @Size(max = 20, message = "이름은 1~20자로 입력해 주세요.")
            String displayName) {

        public SignupRequest {
            email = normalizeEmail(email);
            displayName = displayName == null ? null : displayName.strip();
        }
    }

    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해 주세요.") String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {

        public LoginRequest {
            email = normalizeEmail(email);
        }
    }

    public record AuthResponse(String accessToken, Instant expiresAt, Me user) {}

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
