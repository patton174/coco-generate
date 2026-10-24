package io.github.coco.generate.template;

import io.github.coco.generate.core.GenerationException;
import io.github.coco.generate.core.SafePaths;
import java.util.Set;

/** One template source and its rendered output path. */
public record TemplateDefinition(String source, String output, String kind) {

    private static final Set<String> KINDS = Set.of("java", "resource", "test", "config", "other");

    public TemplateDefinition {
        source = normalizePath(source, "template source");
        if (!source.startsWith("templates/")) {
            throw new TemplatePackException("template source must live under templates/: " + source);
        }
        output = normalizePath(output, "template output");
        kind = kind == null || kind.isBlank() ? "other" : kind.trim();
        if (!KINDS.contains(kind)) {
            throw new TemplatePackException("unsupported template kind: " + kind);
        }
    }

    private static String normalizePath(String value, String field) {
        String text = requireText(value, field);
        try {
            return SafePaths.normalize(text);
        }
        catch (GenerationException ex) {
            throw new TemplatePackException("unsafe " + field + ": " + text, ex);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new TemplatePackException(field + " must not be blank");
        }
        return value.trim();
    }
}
