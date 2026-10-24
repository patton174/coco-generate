package io.github.coco.generate.catalog;

import io.github.coco.generate.template.ClasspathTemplateResourceRoot;
import io.github.coco.generate.template.TemplatePackException;
import io.github.coco.generate.template.TemplatePackLoader;
import io.github.coco.generate.template.TemplatePackManifest;
import io.github.coco.generate.template.TemplateResourceRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loads template-route metadata from a v2 template root. */
public final class TemplateCatalog {

    static final String RESOURCE_ROOT = "META-INF/coco-generate/templates/";

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
        return from(new ClasspathTemplateResourceRoot(
                classLoader, TemplatePackLoader.BUILTIN_ROOT, "classpath:" + TemplatePackLoader.BUILTIN_ROOT));
    }

    /** Loads the catalog from one template root using the shared v2 parser. */
    public static TemplateCatalog from(TemplateResourceRoot root) {
        Objects.requireNonNull(root, "root must not be null");
        TemplatePackLoader loader = new TemplatePackLoader();
        List<String> groups = loader.listGroups(root);
        List<TemplateManifest> manifests = new ArrayList<>(groups.size());
        for (String group : groups) {
            TemplatePackManifest manifest = loader.readManifest(root, group);
            manifests.add(TemplateManifest.from(manifest));
        }
        if (manifests.isEmpty()) {
            throw new TemplatePackException(
                    "template catalog must not be empty: " + root.describe());
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
}
