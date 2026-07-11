package io.github.coco.generate.catalog;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Metadata describing one template route.
 *
 * @param manifestVersion manifest contract version
 * @param id stable template route identifier
 * @param displayName human-readable route name
 * @param description honest capability description
 * @param status implementation status
 * @param outputKind intended output kind
 * @param ownership owner of generated output
 */
public record TemplateManifest(
        int manifestVersion,
        String id,
        String displayName,
        String description,
        String status,
        String outputKind,
        String ownership) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,63}");

    public TemplateManifest {
        if (manifestVersion != 1) {
            throw new IllegalArgumentException("Unsupported template manifest version: " + manifestVersion);
        }
        id = requireId(id);
        displayName = requireText(displayName, "displayName");
        description = requireText(description, "description");
        status = requireText(status, "status");
        outputKind = requireText(outputKind, "outputKind");
        ownership = requireText(ownership, "ownership");
    }

    static String requireId(String value) {
        String id = requireText(value, "id");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid template route id: " + id);
        }
        return id;
    }

    private static String requireText(String value, String field) {
        String text = Objects.requireNonNull(value, field + " must not be null").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return text;
    }
}
