package com.junseo.media;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.moment.MomentRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public on purpose: the widget and notification extension fetch these without an auth header. */
@RestController
public class MediaController {

    private final MediaUrlSigner signer;
    private final MediaStorage storage;
    private final MomentRepository moments;

    public MediaController(MediaUrlSigner signer, MediaStorage storage, MomentRepository moments) {
        this.signer = signer;
        this.storage = storage;
        this.moments = moments;
    }

    @GetMapping("/media/{momentId}/{variant}.jpg")
    ResponseEntity<Resource> get(
            @PathVariable String momentId,
            @PathVariable String variant,
            @RequestParam(required = false) String exp,
            @RequestParam(required = false) String sig) {
        long id;
        long expiresAt;
        try {
            id = Long.parseLong(momentId);
            expiresAt = Long.parseLong(exp);
        } catch (NumberFormatException e) {
            throw forbidden();
        }
        MediaStorage.Variant v = MediaStorage.Variant.parse(variant).orElseThrow(MediaController::forbidden);
        if (!signer.verify(id, v.id(), expiresAt, sig)) {
            throw forbidden();
        }
        if (!moments.existsById(id)) throw ApiException.notFound();
        Resource image = storage.get(MediaStorage.key(id, v)).orElseThrow(ApiException::notFound);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(image);
    }

    private static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN, "사진 링크가 만료되었거나 올바르지 않아요.");
    }
}
