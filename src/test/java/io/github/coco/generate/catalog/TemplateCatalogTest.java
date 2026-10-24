package io.github.coco.generate.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.coco.generate.template.FileSystemTemplateResourceRoot;
import io.github.coco.generate.test.TestProjectFixture;

class TemplateCatalogTest {

    @TempDir
    Path temporaryDirectory;

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
        assertEquals(2, catalog.templates().get(0).manifestVersion());
        assertEquals("executable", catalog.templates().get(0).status());
        assertTrue(catalog.templates().stream().skip(1)
                .allMatch(template -> "metadata-only".equals(template.status())));
        assertTrue(catalog.templates().stream()
                .allMatch(template -> "readable-source".equals(template.outputKind())));
        assertTrue(catalog.templates().stream()
                .allMatch(template -> "business-project".equals(template.ownership())));
    }

    @Test
    void loadsExternalV2PacksThroughTheSameCatalog() throws Exception {
        Path project = TestProjectFixture.createSimpleProject(temporaryDirectory.resolve("project"));

        TemplateCatalog catalog = TemplateCatalog.from(new FileSystemTemplateResourceRoot(
                TestProjectFixture.templateRoot(project)));

        assertEquals(List.of("simple", "alternate"),
                catalog.templates().stream().map(TemplateManifest::id).toList());
        assertTrue(catalog.templates().stream()
                .allMatch(template -> template.manifestVersion() == 2));
    }
}
