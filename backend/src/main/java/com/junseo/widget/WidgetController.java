package com.junseo.widget;

import com.junseo.common.security.CurrentUser;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WidgetController {

    private final WidgetService widgetService;

    public WidgetController(WidgetService widgetService) {
        this.widgetService = widgetService;
    }

    @GetMapping("/api/widget/latest")
    ResponseEntity<WidgetLatest> latest(
            @CurrentUser long me, @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Optional<WidgetLatest> latest = widgetService.latest(me);
        if (latest.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        String version = latest.get().version();
        String etag = "\"" + version + "\"";
        if (matches(ifNoneMatch, version)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .body(latest.get());
    }

    /** Weak comparison per RFC 9110; also tolerates the bare version string without quotes. */
    static boolean matches(String ifNoneMatch, String version) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .map(tag -> tag.startsWith("W/") ? tag.substring(2) : tag)
                .map(tag -> tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")
                        ? tag.substring(1, tag.length() - 1)
                        : tag)
                .anyMatch(tag -> tag.equals("*") || tag.equals(version));
    }
}
