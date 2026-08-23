package io.github.coco.generate.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable request passed to a built-in template route. */
public record GenerationRequest(String templateGroup, String targetPackage, Map<String, Object> attributes) {

    public GenerationRequest {
        if (templateGroup == null || templateGroup.isBlank()) {
            throw new IllegalArgumentException("templateGroup must not be blank");
        }
        templateGroup = templateGroup.trim();
        targetPackage = targetPackage == null ? "" : targetPackage.trim();
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
    }

    public static Builder builder(String templateGroup) {
        return new Builder(templateGroup);
    }

    /** Builder retained to keep the CRUD model independent from CLI parsing. */
    public static final class Builder {
        private final String templateGroup;
        private String targetPackage;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder(String templateGroup) {
            this.templateGroup = templateGroup;
        }

        public Builder targetPackage(String value) {
            this.targetPackage = value;
            return this;
        }

        public Builder attribute(String name, Object value) {
            if (name != null && !name.isBlank() && value != null) {
                this.attributes.put(name.trim(), value);
            }
            return this;
        }

        public GenerationRequest build() {
            return new GenerationRequest(templateGroup, targetPackage, attributes);
        }
    }
}
