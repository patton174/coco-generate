package io.github.coco.generate.template;

import java.util.Objects;

/** Declared type of a template variable. */
public sealed interface TemplateVariableType {

    record Scalar(ScalarType type) implements TemplateVariableType {
        public Scalar {
            Objects.requireNonNull(type, "scalar type must not be null");
        }
    }

    record ListOf(TemplateVariableType element) implements TemplateVariableType {
        public ListOf {
            Objects.requireNonNull(element, "list element type must not be null");
        }
    }

    record MapOf(TemplateVariableType value) implements TemplateVariableType {
        public MapOf {
            Objects.requireNonNull(value, "map value type must not be null");
        }
    }

    record ObjectType() implements TemplateVariableType {
    }

    enum ScalarType {
        STRING, BOOLEAN, INTEGER, ENUM, ANY
    }
}
