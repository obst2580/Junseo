package com.junseo.template;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemplateController {

    private final TemplateService templates;

    public TemplateController(TemplateService templates) {
        this.templates = templates;
    }

    /** Server templates for the 「오늘 템플릿」 screen (the app adds its built-in ones after these). */
    @GetMapping("/api/templates")
    TemplateView.Page list() {
        return new TemplateView.Page(templates.list());
    }
}
