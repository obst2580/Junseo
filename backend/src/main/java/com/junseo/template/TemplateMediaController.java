package com.junseo.template;

import com.junseo.common.ApiException;
import com.junseo.media.MediaStorage;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Template pictures are the same for everyone, so they are public and cached for good (the URL carries ?v=). */
@RestController
public class TemplateMediaController {

    private static final Set<String> FILES = Set.of("background.jpg", "background.png", "overlay.png");

    private final MediaStorage storage;

    public TemplateMediaController(MediaStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/media/templates/{id}/{file:.+}")
    ResponseEntity<Resource> get(@PathVariable String id, @PathVariable String file) {
        if (!TemplateService.ID.matcher(id).matches() || !FILES.contains(file)) {
            throw ApiException.notFound();
        }
        Resource image = storage.get(TemplateService.key(id, file)).orElseThrow(ApiException::notFound);
        return ResponseEntity.ok()
                .contentType(file.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(image);
    }
}
