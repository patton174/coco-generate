package io.github.coco.generate.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TemplateTypeParserTest {

    @Test
    void parsesScalarTypes() {
        assertEquals(
                new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.STRING),
                TemplateTypeParser.parse("string"));
        assertEquals(
                new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.BOOLEAN),
                TemplateTypeParser.parse(" boolean "));
        assertEquals(
                new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.INTEGER),
                TemplateTypeParser.parse("integer"));
        assertEquals(
                new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.ENUM),
                TemplateTypeParser.parse("enum"));
        assertEquals(
                new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.ANY),
                TemplateTypeParser.parse("any"));
        assertEquals(new TemplateVariableType.ObjectType(), TemplateTypeParser.parse("object"));
    }

    @Test
    void parsesNestedContainers() {
        assertEquals(
                new TemplateVariableType.ListOf(
                        new TemplateVariableType.MapOf(
                                new TemplateVariableType.ListOf(
                                        new TemplateVariableType.Scalar(
                                                TemplateVariableType.ScalarType.STRING)))),
                TemplateTypeParser.parse("list<map<list<string>>>"));
        assertEquals(
                new TemplateVariableType.MapOf(new TemplateVariableType.ObjectType()),
                TemplateTypeParser.parse("map<object>"));
    }

    @Test
    void rejectsEnumBelowTopLevel() {
        assertThrows(TemplatePackException.class, () -> TemplateTypeParser.parse("list<enum>"));
        assertThrows(TemplatePackException.class, () -> TemplateTypeParser.parse("map<enum>"));
    }

    @Test
    void rejectsNestingBeyondFiveLevels() {
        assertThrows(
                TemplatePackException.class,
                () -> TemplateTypeParser.parse("list<list<list<list<list<list<string>>>>>>"));
    }

    @Test
    void rejectsUnknownAndMalformedTypes() {
        assertThrows(TemplatePackException.class, () -> TemplateTypeParser.parse(""));
        assertThrows(TemplatePackException.class, () -> TemplateTypeParser.parse("String"));
        assertThrows(TemplatePackException.class, () -> TemplateTypeParser.parse("list<string"));
        assertThrows(TemplatePackException.class, () -> TemplateTypeParser.parse("list<string> trailing"));
        assertThrows(TemplatePackException.class, () -> TemplateTypeParser.parse("map<>"));
    }
}
