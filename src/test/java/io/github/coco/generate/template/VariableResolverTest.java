package io.github.coco.generate.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class VariableResolverTest {

    private final VariableResolver resolver = new VariableResolver();

    @Test
    void appliesManifestRootResourceAndCliPrecedence() {
        List<TemplateVariable> declarations = List.of(
                new TemplateVariable(
                        "author", TemplateTypeParser.parse("string"), true, "default-team",
                        List.of(), ""),
                new TemplateVariable(
                        "apiVersion", TemplateTypeParser.parse("enum"), false, "v1",
                        List.of("v1", "v2"), ""));

        Map<String, Object> defaults = resolver.resolve(declarations, null, null, null);
        assertEquals("default-team", defaults.get("author"));
        assertEquals("v1", defaults.get("apiVersion"));

        Map<String, Object> root = resolver.resolve(
                declarations, Map.of("author", "root-team"), null, null);
        assertEquals("root-team", root.get("author"));

        Map<String, Object> resource = resolver.resolve(
                declarations, Map.of("author", "root-team"),
                Map.of("author", "resource-team"), null);
        assertEquals("resource-team", resource.get("author"));

        Map<String, Object> cli = resolver.resolve(
                declarations, Map.of("author", "root-team"),
                Map.of("author", "resource-team"), Map.of("author", "cli-team"));
        assertEquals("cli-team", cli.get("author"));
    }

    @Test
    void parsesCliScalarsAccordingToDeclaredType() {
        List<TemplateVariable> declarations = List.of(
                variable("enabled", "boolean"),
                variable("count", "integer"),
                new TemplateVariable(
                        "mode", TemplateTypeParser.parse("enum"), false, null,
                        List.of("fast", "safe"), ""));

        Map<String, Object> resolved = resolver.resolve(
                declarations, null, null,
                Map.of("enabled", "true", "count", "42", "mode", "safe"));

        assertEquals(true, resolved.get("enabled"));
        assertEquals(42L, resolved.get("count"));
        assertEquals("safe", resolved.get("mode"));
    }

    @Test
    void rejectsUnknownRequiredAndMistypedValues() {
        List<TemplateVariable> declarations = List.of(
                new TemplateVariable(
                        "author", TemplateTypeParser.parse("string"), true, null,
                        List.of(), ""));

        assertThrows(
                TemplatePackException.class,
                () -> resolver.resolve(declarations, null, null, null));
        assertThrows(
                TemplatePackException.class,
                () -> resolver.resolve(declarations, Map.of("unknown", "x"), null, null));
        assertThrows(
                TemplatePackException.class,
                () -> resolver.resolve(declarations, Map.of("author", 42), null, null));
        assertThrows(
                TemplatePackException.class,
                () -> resolver.resolve(declarations, null, null, Map.of("author", "cli", "unknown", "x")));
    }

    @Test
    void rejectsComplexAndAnyCliValues() {
        assertThrows(
                TemplatePackException.class,
                () -> resolver.resolve(
                        List.of(variable("imports", "list<string>")), null, null,
                        Map.of("imports", "java.util.List")));
        assertThrows(
                TemplatePackException.class,
                () -> resolver.resolve(
                        List.of(variable("metadata", "any")), null, null,
                        Map.of("metadata", "text")));
    }

    @Test
    void validatesCliVariableNames() {
        assertThrows(
                TemplatePackException.class,
                () -> resolver.resolve(List.of(), null, null, Map.of("bad-name", "x")));
        assertThrows(
                TemplatePackException.class,
                () -> resolver.resolve(List.of(), null, null, Map.of("crud", "x")));
    }

    @Test
    void allowsFreeVariablesWhenNoTypesAreDeclared() {
        Map<String, Object> resolved = resolver.resolve(
                List.of(), Map.of("author", "root-team"), Map.of("team", "platform"),
                Map.of("author", "cli-team"));

        assertEquals("cli-team", resolved.get("author"));
        assertEquals("platform", resolved.get("team"));
    }

    private static TemplateVariable variable(String name, String type) {
        return new TemplateVariable(
                name, TemplateTypeParser.parse(type), false, null, List.of(), "");
    }
}
