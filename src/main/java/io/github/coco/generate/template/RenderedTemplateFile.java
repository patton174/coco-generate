package io.github.coco.generate.template;

import java.util.Objects;

/** One rendered template output. */
public record RenderedTemplateFile(
        String relativePath,
        String content,
        String templateGroup,
        String kind) {

    public RenderedTemplateFile {
        relativePath = requireText(relativePath, "relativePath");
        content = Objects.requireNonNull(content, "content must not be null");
        templateGroup = requireText(templateGroup, "templateGroup");
        kind = requireText(kind, "kind");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new TemplatePackException(field + " must not be blank");
        }
        return value.trim();
    }
}
