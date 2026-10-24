package io.github.coco.generate.template;

import java.util.Objects;

/** Loaded template pack: manifest, safe resource root, and source description. */
public record TemplatePack(
        TemplatePackManifest manifest,
        TemplateResourceRoot resources,
        String location) {

    public TemplatePack {
        manifest = Objects.requireNonNull(manifest, "manifest must not be null");
        resources = Objects.requireNonNull(resources, "resources must not be null");
        if (location == null || location.isBlank()) {
            throw new TemplatePackException("template pack location must not be blank");
        }
        location = location.trim();
    }

    public String group() {
        return manifest.id();
    }
}
