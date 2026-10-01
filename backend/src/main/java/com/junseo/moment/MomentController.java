package com.junseo.moment;

import com.junseo.common.CursorPage;
import com.junseo.common.security.CurrentUser;
import com.junseo.media.ImageProcessor;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/moments")
public class MomentController {

    private final MomentService momentService;
    private final ImageProcessor imageProcessor;

    public MomentController(MomentService momentService, ImageProcessor imageProcessor) {
        this.momentService = momentService;
        this.imageProcessor = imageProcessor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MomentView upload(@CurrentUser long me, @RequestPart("image") MultipartFile image) throws IOException {
        return momentService.create(me, imageProcessor.process(image.getBytes()));
    }

    @GetMapping
    CursorPage<MomentView> list(
            @CurrentUser long me,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) Long userId) {
        return momentService.list(me, userId, cursor, limit);
    }

    @GetMapping("/{id}")
    MomentView detail(@CurrentUser long me, @PathVariable long id) {
        return momentService.detail(me, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser long me, @PathVariable long id) {
        momentService.delete(me, id);
    }
}
