package com.junseo.widget;

import com.junseo.common.security.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WidgetController {

    /** Which path on the phone asked: "notification" (notification service extension) or "widget" (timeline). */
    static final String SOURCE_HEADER = "X-Widget-Source";

    private static final Logger log = LoggerFactory.getLogger(WidgetController.class);
    private static final Pattern SAFE_SOURCE = Pattern.compile("[a-z-]{1,20}");

    private final WidgetService widgetService;
    private final Clock clock;

    public WidgetController(WidgetService widgetService, Clock clock) {
        this.widgetService = widgetService;
        this.clock = clock;
    }

    @GetMapping("/api/widget/latest")
    ResponseEntity<WidgetLatest> latest(
            @CurrentUser long me,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @RequestHeader(value = SOURCE_HEADER, required = false) String source) {
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
        logNewPhoto(me, latest.get(), ifNoneMatch, source);
        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .body(latest.get());
    }

    /**
     * The widget is the product, so its real update delay is measured from the server: when a phone that had a
     * different photo (or none) receives this one, log how long after the photo was taken and by which path.
     * Comment and reaction refreshes of the same photo are not logged.
     */
    private void logNewPhoto(long me, WidgetLatest latest, String ifNoneMatch, String source) {
        long momentId = latest.moment().id();
        if (ifNoneMatch != null && ifNoneMatch.replace("W/", "").replace("\"", "").trim().startsWith(momentId + "-")) {
            return;
        }
        String path = source != null && SAFE_SOURCE.matcher(source).matches() ? source : "unknown";
        long ms = Duration.between(latest.moment().createdAt(), clock.instant()).toMillis();
        log.info("Widget got new photo: user={} moment={} source={} after={}ms", me, momentId, path, ms);
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
