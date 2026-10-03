package com.junseo.template;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.security.AdminAuth;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Adding templates after launch (scripts/templates/upload_template.py calls this). Operator only ({@link AdminAuth}).
 */
@RestController
public class TemplateAdminController {

    static final String TOKEN_HEADER = AdminAuth.HEADER;

    private final TemplateService templates;
    private final ObjectMapper json;
    private final AdminAuth admin;

    public TemplateAdminController(TemplateService templates, ObjectMapper json, AdminAuth admin) {
        this.templates = templates;
        this.json = json;
        this.admin = admin;
    }

    /** multipart: meta (JSON {@link TemplateSpec}), background (JPG/PNG), overlay (PNG, optional). Replaces the whole template. */
    @PutMapping("/api/admin/templates/{id}")
    TemplateView put(
            @RequestHeader(value = TOKEN_HEADER, required = false) String given,
            @PathVariable String id,
            @RequestParam("meta") String meta,
            @RequestParam("background") MultipartFile background,
            @RequestParam(value = "overlay", required = false) MultipartFile overlay)
            throws IOException {
        admin.check(given);
        TemplateSpec spec;
        try {
            spec = json.readValue(meta, TemplateSpec.class);
        } catch (JacksonException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "meta 가 올바른 JSON 이 아니에요.");
        }
        return templates.put(id, spec, background.getBytes(), overlay == null ? null : overlay.getBytes());
    }

    @DeleteMapping("/api/admin/templates/{id}")
    ResponseEntity<Void> hide(@RequestHeader(value = TOKEN_HEADER, required = false) String given, @PathVariable String id) {
        admin.check(given);
        templates.hide(id);
        return ResponseEntity.noContent().build();
    }
}
