package io.github.coco.generate.template;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.lang.model.SourceVersion;

/** Merges manifest defaults, project vars, resource vars, and CLI vars. */
public final class VariableResolver {

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Set<String> RESERVED_NAMES = Set.of(
            "_coco", "crud", "resource", "vars", "templateGroup", "targetPackage",
            "basePackage", "basePackagePath", "resourceName", "resourceVariable",
            "resourcePackage", "tableName", "apiPath");

    public Map<String, Object> resolve(
            List<TemplateVariable> declarations,
            Map<String, Object> rootVars,
            Map<String, Object> resourceVars,
            Map<String, String> cliVars) {
        Map<String, TemplateVariable> declared = new LinkedHashMap<>();
        for (TemplateVariable declaration : declarations) {
            if (declared.putIfAbsent(declaration.name(), declaration) != null) {
                throw new TemplatePackException(
                        "duplicate template variable declaration: " + declaration.name());
            }
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        for (TemplateVariable declaration : declarations) {
            if (declaration.defaultValue() != null) {
                merged.put(declaration.name(), declaration.defaultValue());
            }
        }
        if (rootVars != null) {
            merged.putAll(rootVars);
        }
        if (resourceVars != null) {
            merged.putAll(resourceVars);
        }

        if (cliVars != null) {
            for (Map.Entry<String, String> entry : cliVars.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    throw new TemplatePackException("CLI template variables must have a name and value");
                }
                validateName(entry.getKey());
                TemplateVariable declaration = declared.get(entry.getKey());
                if (declaration == null) {
                    if (!declarations.isEmpty()) {
                        throw new TemplatePackException(
                                "unknown template variable from CLI: " + entry.getKey());
                    }
                    merged.put(entry.getKey(), entry.getValue());
                }
                else {
                    merged.put(entry.getKey(), parseCliValue(declaration, entry.getValue()));
                }
            }
        }

        if (!declarations.isEmpty()) {
            for (String name : merged.keySet()) {
                if (!declared.containsKey(name)) {
                    throw new TemplatePackException("unknown template variable: " + name);
                }
            }
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        if (declarations.isEmpty()) {
            for (Map.Entry<String, Object> entry : merged.entrySet()) {
                resolved.put(entry.getKey(), VariableTypeValidator.normalizeAny(
                        entry.getValue(), "template variable " + entry.getKey()));
            }
        }
        else {
            for (TemplateVariable declaration : declarations) {
                Object value = merged.get(declaration.name());
                if (value == null) {
                    if (declaration.required()) {
                        throw new TemplatePackException(
                                "required template variable is missing: " + declaration.name());
                    }
                    continue;
                }
                resolved.put(declaration.name(), VariableTypeValidator.validate(declaration, value));
            }
        }
        return Collections.unmodifiableMap(resolved);
    }

    private static void validateName(String name) {
        if (!VARIABLE_NAME.matcher(name).matches() || SourceVersion.isKeyword(name)) {
            throw new TemplatePackException("invalid CLI template variable name: " + name);
        }
        if (RESERVED_NAMES.contains(name)) {
            throw new TemplatePackException("CLI template variable uses a reserved name: " + name);
        }
    }

    private static Object parseCliValue(TemplateVariable declaration, String value) {        if (!(declaration.type() instanceof TemplateVariableType.Scalar scalarType)) {
            throw new TemplatePackException(
                    "CLI --var only supports scalar variables; use YAML vars for '"
                            + declaration.name() + "'");
        }
        return switch (scalarType.type()) {
            case STRING -> value;
            case BOOLEAN -> parseBoolean(declaration.name(), value);
            case INTEGER -> parseLong(declaration.name(), value);
            case ENUM -> parseEnum(declaration, value);
            case ANY -> throw new TemplatePackException(
                    "CLI --var does not support 'any' variables; use YAML vars for '"
                            + declaration.name() + "'");
        };
    }

    private static Boolean parseBoolean(String name, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new TemplatePackException(
                "CLI template variable " + name + " must be true or false: " + value);
    }

    private static Long parseLong(String name, String value) {
        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException ex) {
            throw new TemplatePackException(
                    "CLI template variable " + name + " must be an integer: " + value, ex);
        }
    }

    private static String parseEnum(TemplateVariable declaration, String value) {
        if (!declaration.enumValues().contains(value)) {
            throw new TemplatePackException(
                    "CLI template variable " + declaration.name() + " must be one of "
                            + declaration.enumValues() + ", but was " + value);
        }
        return value;
    }
}
