package io.github.coco.generate.config;

import java.util.List;

/** Parsed immutable CRUD YAML data. */
public record CrudYamlSpec(String basePackage, List<Resource> resources) {

    public CrudYamlSpec {
        resources = List.copyOf(resources);
    }

    /** One CRUD resource. */
    public record Resource(String name, String table, String apiPath, Id id, List<Field> fields) {

        public Resource {
            fields = List.copyOf(fields);
        }
    }

    /** Primary-key declaration. */
    public record Id(String name, String column, String type, String strategy) {
    }

    /** Regular field declaration. */
    public record Field(String name, String column, String type, boolean required) {
    }
}
