package io.github.coco.generate.template;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Parsed v2 template-pack manifest. */
public record TemplatePackManifest(
        int manifestVersion,
        String id,
        String displayName,
        String description,
        String status,
        String outputKind,
        String ownership,
        String packVersion,
        List<TemplateVariable> variables,
        List<TemplateDefinition> templates) {

    public static final int CURRENT_VERSION = 2;

    public static final String EXECUTABLE = "executable";

    public static final String METADATA_ONLY = "metadata-only";

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,63}");

    private static final Set<String> STATUSES = Set.of(EXECUTABLE, METADATA_ONLY);

    public TemplatePackManifest {
        if (manifestVersion != CURRENT_VERSION) {
            throw new TemplatePackException("unsupported template manifest version: " + manifestVersion);
        }
        id = requireId(id);
        displayName = requireText(displayName, "displayName");
        description = requireText(description, "description");
        status = requireText(status, "status");
        outputKind = requireText(outputKind, "outputKind");
        ownership = requireText(ownership, "ownership");
        packVersion = requireText(packVersion, "packVersion");
        if (!STATUSES.contains(status)) {
            throw new TemplatePackException("unsupported template status: " + status);
        }
        if (!"readable-source".equals(outputKind)) {
            throw new TemplatePackException("unsupported template outputKind: " + outputKind);
        }
        if (!"business-project".equals(ownership)) {
            throw new TemplatePackException("unsupported template ownership: " + ownership);
        }
        variables = variables == null ? List.of() : List.copyOf(variables);
        templates = templates == null ? List.of() : List.copyOf(templates);
        if (variables.size() > 50) {
            throw new TemplatePackException("template pack declares more than 50 variables");
        }
        if (templates.size() > 100) {
            throw new TemplatePackException("template pack declares more than 100 templates");
        }
        if (EXECUTABLE.equals(status) && templates.isEmpty()) {
            throw new TemplatePackException("executable template pack must declare at least one template");
        }
        ensureUniqueVariableNames(variables);
        ensureUniqueOutputs(templates);
    }

    public static String requireId(String value) {
        String id = requireText(value, "id");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new TemplatePackException("invalid template group id: " + id);
        }
        return id;
    }

    private static void ensureUniqueVariableNames(List<TemplateVariable> variables) {
        Set<String> names = new LinkedHashSet<>();
        for (TemplateVariable variable : variables) {
            if (!names.add(variable.name())) {
                throw new TemplatePackException("duplicate template variable: " + variable.name());
            }
        }
    }

    private static void ensureUniqueOutputs(List<TemplateDefinition> templates) {
        Set<String> outputs = new LinkedHashSet<>();
        for (TemplateDefinition template : templates) {
            if (!outputs.add(template.output())) {
                throw new TemplatePackException("duplicate template output path: " + template.output());
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new TemplatePackException(field + " must not be blank");
        }
        return value.trim();
    }
}
