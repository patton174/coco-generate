package io.github.coco.generate.engine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Optional CLI overrides for one planning run. */
public record GenerationOptions(
        Path configFile,
        String templateRoot,
        String templateGroup,
        Map<String, String> variables) {

    public GenerationOptions {
        templateRoot = blankToNull(templateRoot);
        templateGroup = blankToNull(templateGroup);
        Map<String, String> copy = new LinkedHashMap<>();
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("template variable name must not be blank");
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException(
                            "template variable value must not be null: " + entry.getKey());
                }
                copy.put(entry.getKey().trim(), entry.getValue());
            }
        }
        variables = Collections.unmodifiableMap(copy);
    }

    /** Returns options that defer entirely to the project YAML file. */
    public static GenerationOptions none() {
        return new GenerationOptions(null, null, null, Map.of());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
