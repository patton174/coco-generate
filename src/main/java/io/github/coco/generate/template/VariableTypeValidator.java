package io.github.coco.generate.template;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates and normalizes declared template variable values recursively. */
public final class VariableTypeValidator {

    private VariableTypeValidator() {
    }

    public static Object validate(TemplateVariable variable, Object value) {
        if (value == null) {
            if (variable.required()) {
                throw new TemplatePackException(
                        "required template variable is missing: " + variable.name());
            }
            return null;
        }
        return validate(variable.name(), variable.type(), value, variable.enumValues(), 0);
    }

    public static Object validate(
            String name,
            TemplateVariableType type,
            Object value,
            List<String> enumValues,
            int depth) {
        if (depth > TemplateTypeParser.MAX_DEPTH) {
            throw new TemplatePackException(
                    "template variable " + name + " exceeds " + TemplateTypeParser.MAX_DEPTH
                            + " nested containers");
        }
        if (type instanceof TemplateVariableType.Scalar scalarType) {
            return switch (scalarType.type()) {
                case STRING -> requireString(name, value);
                case BOOLEAN -> requireBoolean(name, value);
                case INTEGER -> requireInteger(name, value);
                case ENUM -> requireEnum(name, value, enumValues);
                case ANY -> normalizeAny(value, "template variable " + name);
            };
        }
        if (type instanceof TemplateVariableType.ListOf listType) {
            if (!(value instanceof List<?> list)) {
                throw mismatch(name, "a list", value);
            }
            List<Object> normalized = new ArrayList<>(list.size());
            for (int index = 0; index < list.size(); index++) {
                normalized.add(validate(
                        name + "[" + index + "]",
                        listType.element(),
                        list.get(index),
                        List.of(),
                        depth + 1));
            }
            return List.copyOf(normalized);
        }
        if (type instanceof TemplateVariableType.MapOf mapType) {
            if (!(value instanceof Map<?, ?> map)) {
                throw mismatch(name, "a map", value);
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new TemplatePackException(
                            "template variable " + name + " map keys must be strings");
                }
                normalized.put(key, validate(
                        name + "." + key,
                        mapType.value(),
                        entry.getValue(),
                        List.of(),
                        depth + 1));
            }
            return Collections.unmodifiableMap(normalized);
        }
        if (type instanceof TemplateVariableType.ObjectType) {
            if (!(value instanceof Map<?, ?>)) {
                throw mismatch(name, "an object map", value);
            }
            return normalizeAny(value, "template variable " + name);
        }
        throw new TemplatePackException("unsupported template variable type for " + name);
    }

    public static Object normalizeAny(Object value, String location) {
        if (value == null) {
            throw new TemplatePackException(location + " must not be null");
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new TemplatePackException(location + " contains a non-string map key");
                }
                normalized.put(key, normalizeAny(entry.getValue(), location + "." + key));
            }
            return Collections.unmodifiableMap(normalized);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (int index = 0; index < list.size(); index++) {
                normalized.add(normalizeAny(list.get(index), location + "[" + index + "]"));
            }
            return List.copyOf(normalized);
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Character
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof BigInteger || value instanceof java.math.BigDecimal) {
            return value;
        }
        throw new TemplatePackException(
                location + " contains unsupported value type "
                        + value.getClass().getSimpleName());
    }

    private static String requireString(String name, Object value) {
        if (!(value instanceof String text)) {
            throw mismatch(name, "a string", value);
        }
        return text;
    }

    private static Boolean requireBoolean(String name, Object value) {
        if (!(value instanceof Boolean booleanValue)) {
            throw mismatch(name, "a boolean", value);
        }
        return booleanValue;
    }

    private static Long requireInteger(String name, Object value) {
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger bigInteger) {
            try {
                return bigInteger.longValueExact();
            }
            catch (ArithmeticException ex) {
                throw new TemplatePackException(
                        "template variable " + name + " exceeds the Java long range", ex);
            }
        }
        throw mismatch(name, "an integer", value);
    }

    private static String requireEnum(String name, Object value, List<String> enumValues) {
        if (!(value instanceof String text) || !enumValues.contains(text)) {
            throw new TemplatePackException(
                    "template variable " + name + " must be one of " + enumValues
                            + ", but was " + value);
        }
        return text;
    }

    private static TemplatePackException mismatch(String name, String expected, Object value) {
        return new TemplatePackException(
                "template variable " + name + " must be " + expected
                        + ", but was " + typeName(value));
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
