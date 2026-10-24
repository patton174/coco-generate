package io.github.coco.generate.template;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.lang.model.SourceVersion;

/** One declared template variable and its optional default. */
public record TemplateVariable(
        String name,
        TemplateVariableType type,
        boolean required,
        Object defaultValue,
        List<String> enumValues,
        String description) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Set<String> RESERVED_NAMES = Set.of(
            "_coco", "crud", "resource", "vars", "templateGroup", "targetPackage",
            "basePackage", "basePackagePath", "resourceName", "resourceVariable",
            "resourcePackage", "tableName", "apiPath");

    public TemplateVariable {
        name = requireText(name, "variable name");
        if (!NAME.matcher(name).matches() || SourceVersion.isKeyword(name)) {
            throw new TemplatePackException("invalid template variable name: " + name);
        }
        if (RESERVED_NAMES.contains(name)) {
            throw new TemplatePackException("template variable uses a reserved name: " + name);
        }
        type = Objects.requireNonNull(type, "variable type must not be null");
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        description = description == null ? "" : description.trim();
        if (new LinkedHashSet<>(enumValues).size() != enumValues.size()) {
            throw new TemplatePackException("template variable declares duplicate enum values: " + name);
        }
        if (type instanceof TemplateVariableType.Scalar scalar
                && scalar.type() == TemplateVariableType.ScalarType.ENUM) {
            if (enumValues.isEmpty()) {
                throw new TemplatePackException("enum variable must declare values: " + name);
            }
        }
        else if (!enumValues.isEmpty()) {
            throw new TemplatePackException("only enum variables may declare values: " + name);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new TemplatePackException(field + " must not be blank");
        }
        return value.trim();
    }
}
