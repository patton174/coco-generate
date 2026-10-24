package io.github.coco.generate.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class VariableTypeValidatorTest {

    @Test
    void validatesAndNormalizesScalars() {
        assertEquals("text", VariableTypeValidator.validate(variable("name", "string"), "text"));
        assertEquals(true, VariableTypeValidator.validate(variable("enabled", "boolean"), true));
        assertEquals(42L, VariableTypeValidator.validate(variable("count", "integer"), 42));
        assertEquals(42L, VariableTypeValidator.validate(variable("count", "integer"), 42L));
        assertEquals(7L, VariableTypeValidator.validate(
                variable("count", "integer"), BigInteger.valueOf(7)));
    }

    @Test
    void validatesEnums() {
        TemplateVariable mode = new TemplateVariable(
                "mode", TemplateTypeParser.parse("enum"), false, null, List.of("fast", "safe"), "");
        assertEquals("safe", VariableTypeValidator.validate(mode, "safe"));
        assertThrows(TemplatePackException.class, () -> VariableTypeValidator.validate(mode, "other"));
        assertThrows(TemplatePackException.class, () -> VariableTypeValidator.validate(mode, 1));
    }

    @Test
    void validatesListsRecursively() {
        TemplateVariable names = variable("names", "list<string>");
        assertEquals(List.of("a", "b"), VariableTypeValidator.validate(names, List.of("a", "b")));
        assertThrows(
                TemplatePackException.class,
                () -> VariableTypeValidator.validate(names, List.of("a", 1)));
        assertThrows(
                TemplatePackException.class,
                () -> VariableTypeValidator.validate(names, Arrays.asList("a", null)));
        assertThrows(
                TemplatePackException.class,
                () -> VariableTypeValidator.validate(names, "a"));
    }

    @Test
    void validatesMapsRecursively() {
        TemplateVariable counts = variable("counts", "map<integer>");
        Object result = VariableTypeValidator.validate(counts, Map.of("a", 1, "b", 2L));
        assertEquals(Map.of("a", 1L, "b", 2L), result);
        assertThrows(
                TemplatePackException.class,
                () -> VariableTypeValidator.validate(counts, Map.of("a", "one")));
        assertThrows(
                TemplatePackException.class,
                () -> VariableTypeValidator.validate(counts, Map.of(1, 1)));
    }

    @Test
    void acceptsFreeFormObjectsAndAnyValues() {
        Map<String, Object> value = Map.of("team", "platform", "owners", List.of("a", "b"));
        assertEquals(value, VariableTypeValidator.validate(variable("metadata", "object"), value));
        assertEquals(value, VariableTypeValidator.validate(variable("metadata", "any"), value));
        assertThrows(
                TemplatePackException.class,
                () -> VariableTypeValidator.validate(variable("metadata", "object"), "text"));
    }

    @Test
    void rejectsMissingRequiredValues() {
        TemplateVariable required = new TemplateVariable(
                "author", TemplateTypeParser.parse("string"), true, null, List.of(), "");
        assertThrows(
                TemplatePackException.class,
                () -> VariableTypeValidator.validate(required, null));
    }

    @Test
    void rejectsIntegersOutsideLongRange() {
        assertThrows(
                TemplatePackException.class,
                () -> VariableTypeValidator.validate(
                        variable("count", "integer"), BigInteger.ONE.shiftLeft(80)));
    }

    private static TemplateVariable variable(String name, String type) {
        return new TemplateVariable(
                name, TemplateTypeParser.parse(type), false, null, List.of(), "");
    }
}
