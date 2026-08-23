package io.github.coco.generate.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TemplateCatalogTest {

    @Test
    void loadsAllBuiltInTemplateRoutesInStableOrder() {
        TemplateCatalog catalog = TemplateCatalog.builtIn();

        assertEquals(
                List.of(
                        "crud",
                        "admin-module",
                        "master-data",
                        "purchase",
                        "sales",
                        "inventory",
                        "finance"),
                catalog.templates().stream().map(TemplateManifest::id).toList());
        assertEquals("executable", catalog.templates().get(0).status());
        assertTrue(catalog.templates().stream().skip(1)
                .allMatch(template -> "metadata-only".equals(template.status())));
        assertTrue(catalog.templates().stream()
                .allMatch(template -> "readable-source".equals(template.outputKind())));
        assertTrue(catalog.templates().stream()
                .allMatch(template -> "business-project".equals(template.ownership())));
    }
}
