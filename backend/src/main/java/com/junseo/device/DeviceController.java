package com.junseo.device;

import com.junseo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
            @Pattern(regexp = "[0-9a-fA-F]{16,200}", message = "토큰은 16진수 문자열이어야 해요.")
            String token,
            @NotBlank @Pattern(regexp = "app|widget", message = "kind는 app 또는 widget이어야 해요.")
            String kind,
            @NotBlank
            @Pattern(regexp = "development|production", message = "environment는 development 또는 production이어야 해요.")
            String environment) {}

    private final DeviceTokenRepository devices;
    private final Clock clock;

    public DeviceController(DeviceTokenRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void register(@CurrentUser long me, @Valid @RequestBody RegisterDeviceRequest request) {
        devices.upsert(normalize(request.token()), me, request.kind(), request.environment(), clock.instant());
    }

    /** Idempotent: unknown tokens and tokens owned by someone else are left alone. */
    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregister(@CurrentUser long me, @PathVariable String token) {
        devices.deleteOwned(normalize(token), me);
    }

    private static String normalize(String token) {
        return token.trim().toLowerCase(Locale.ROOT);
    }
}
