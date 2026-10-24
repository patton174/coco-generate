package io.github.coco.generate.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed immutable CRUD YAML data. */
public record CrudYamlSpec(
        String basePackage,
        TemplateSpec template,
        Map<String, Object> vars,
        List<Resource> resources) {

    public CrudYamlSpec {
        template = template == null ? TemplateSpec.defaults() : template;
        vars = immutableVars(vars);
        resources = List.copyOf(resources);
    }

    /** Root template-pack selection. */
    public record TemplateSpec(String location, String group, String encoding) {

        public TemplateSpec {
            location = requireText(location, "template.location");
            group = requireText(group, "template.group");
            encoding = requireText(encoding, "template.encoding");
        }

        public static TemplateSpec defaults() {
            return new TemplateSpec("builtin", "crud", "UTF-8");
        }
    }

    /** One CRUD resource. */
    public record Resource(
            String name,
            String table,
            String apiPath,
            String template,
            Map<String, Object> vars,
            Id id,
            List<Field> fields) {

        public Resource {
            template = template == null || template.isBlank() ? null : template.trim();
            vars = immutableVars(vars);
            fields = List.copyOf(fields);
        }
    }

    /** Primary-key declaration. */
    public record Id(String name, String column, String type, String strategy) {
    }

    /** Regular field declaration. */
    public record Field(String name, String column, String type, boolean required) {
    }

    private static Map<String, Object> immutableVars(Map<String, Object> vars) {
        if (vars == null || vars.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(vars));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
