package com.junseo.device;

import com.junseo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    public record RegisterDeviceRequest(
            @NotBlank(message = "토큰이 필요해요.")
            @Size(min = 16, max = 2048)
            @Pattern(regexp = "[A-Za-z0-9_:\\-]+", message = "토큰 형식이 올바르지 않아요.")
            String token,
            @NotBlank @Pattern(regexp = "app|widget", message = "kind는 app 또는 widget이어야 해요.")
            String kind,
            @NotBlank
            @Pattern(regexp = "development|production", message = "environment는 development 또는 production이어야 해요.")
            String environment,
            @Pattern(regexp = "ios|android", message = "platform은 ios 또는 android이어야 해요.")
            String platform) {
        public String resolvedPlatform() { return platform == null ? "ios" : platform; }

        @AssertTrue(message = "iOS 토큰은 16진수여야 하며 Android는 app 토큰을 사용해야 해요.")
        public boolean isPlatformTokenValid() {
            if (token == null) return false;
            return "android".equals(resolvedPlatform())
                    ? "app".equals(kind) && "production".equals(environment)
                    : token.matches("[0-9a-fA-F]{16,200}");
        }
    }

    private final DeviceTokenRepository devices;
    private final Clock clock;

    public DeviceController(DeviceTokenRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void register(@CurrentUser long me, @Valid @RequestBody RegisterDeviceRequest request) {
        String token = "ios".equals(request.resolvedPlatform()) ? normalize(request.token()) : request.token();
        devices.upsert(token, me, request.kind(), request.environment(), request.resolvedPlatform(), clock.instant());
    }

    /** Idempotent: unknown tokens and tokens owned by someone else are left alone. */
    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregister(@CurrentUser long me, @PathVariable String token) {
        // Never lowercase FCM tokens. Keep the legacy APNs case-insensitive delete behavior.
        devices.deleteOwned(token, me);
        if (token.matches("[0-9a-fA-F]{16,200}")) devices.deleteOwned(normalize(token), me);
    }

    private static String normalize(String token) {
        return token.trim().toLowerCase(Locale.ROOT);
    }
}
