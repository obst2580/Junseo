package com.junseo.template;

import java.util.List;
import tools.jackson.databind.JsonNode;

/** A template as the app draws it. Image URLs are relative to the API and change with every upload ({@code ?v=}). */
public record TemplateView(
        String id,
        String name,
        int width,
        int height,
        String backgroundColor,
        String backgroundUrl,
        String overlayUrl,
        JsonNode slots,
        List<String> requires,
        int version) {

    public record Page(List<TemplateView> items) {}
}
