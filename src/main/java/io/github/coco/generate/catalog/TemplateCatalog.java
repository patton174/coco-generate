package io.github.coco.generate.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Loads the built-in template manifest catalog from classpath resources. */
public final class TemplateCatalog {

    static final String RESOURCE_ROOT = "META-INF/coco-generate/templates/";

    private static final String INDEX_RESOURCE = RESOURCE_ROOT + "index.txt";

    private final List<TemplateManifest> templates;

    private TemplateCatalog(List<TemplateManifest> templates) {
        this.templates = List.copyOf(templates);
    }

    /**
     * Loads the built-in catalog using the application class loader.
     *
     * @return validated built-in catalog
     */
    public static TemplateCatalog builtIn() {
        return load(TemplateCatalog.class.getClassLoader());
    }

    static TemplateCatalog load(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        List<String> routeIds = readIndex(classLoader);
        List<TemplateManifest> manifests = new ArrayList<>(routeIds.size());
        Set<String> seen = new HashSet<>();
        for (String routeId : routeIds) {
            String checkedId = TemplateManifest.requireId(routeId);
            if (!seen.add(checkedId)) {
                throw new IllegalStateException("Duplicate template route in catalog index: " + checkedId);
            }
            TemplateManifest manifest = readManifest(classLoader, checkedId);
            if (!checkedId.equals(manifest.id())) {
                throw new IllegalStateException(
                        "Template manifest id does not match its catalog route: " + checkedId);
            }
            manifests.add(manifest);
        }
        if (manifests.isEmpty()) {
            throw new IllegalStateException("Built-in template catalog must not be empty");
        }
        return new TemplateCatalog(manifests);
    }

    /**
     * Returns manifests in their deterministic catalog order.
     *
     * @return immutable manifest list
     */
    public List<TemplateManifest> templates() {
        return templates;
    }

    private static List<String> readIndex(ClassLoader classLoader) {
        try (InputStream stream = requiredResource(classLoader, INDEX_RESOURCE);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        }
        catch (IOException ex) {
            throw new IllegalStateException("Unable to read built-in template catalog index", ex);
        }
    }

    private static TemplateManifest readManifest(ClassLoader classLoader, String routeId) {
        String resource = RESOURCE_ROOT + routeId + "/manifest.properties";
        Properties properties = new Properties();
        try (InputStream stream = requiredResource(classLoader, resource);
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Unable to read template manifest: " + resource, ex);
        }
        try {
            return new TemplateManifest(
                    Integer.parseInt(required(properties, "manifestVersion", resource)),
                    required(properties, "id", resource),
                    required(properties, "displayName", resource),
                    required(properties, "description", resource),
                    required(properties, "status", resource),
                    required(properties, "outputKind", resource),
                    required(properties, "ownership", resource));
        }
        catch (NumberFormatException ex) {
            throw new IllegalStateException("Template manifest has an invalid version: " + resource, ex);
        }
    }

    private static InputStream requiredResource(ClassLoader classLoader, String resource) {
        InputStream stream = classLoader.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing built-in template resource: " + resource);
        }
        return stream;
    }

    private static String required(Properties properties, String key, String resource) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Template manifest is missing " + key + ": " + resource);
        }
        return value.trim();
    }
}
