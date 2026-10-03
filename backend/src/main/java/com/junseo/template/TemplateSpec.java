package com.junseo.template;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * What an admin uploads alongside the pictures ({@code meta} part of PUT /api/admin/templates/{id}).
 * requires: extra features beyond the ones the server can see in the slots (quad, glow) and pictures (overlay).
 */
public record TemplateSpec(
        String name, int width, int height, String backgroundColor, JsonNode slots, List<String> requires, Integer sortOrder) {}
