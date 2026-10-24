package io.github.coco.generate.template;

/** Parses the small variable type grammar used by template manifests. */
public final class TemplateTypeParser {

    public static final int MAX_DEPTH = 5;

    private TemplateTypeParser() {
    }

    public static TemplateVariableType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new TemplatePackException("variable type must not be blank");
        }
        Parser parser = new Parser(value.trim());
        TemplateVariableType type = parser.parseType(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.invalid("unexpected trailing content");
        }
        return type;
    }

    private static final class Parser {

        private final String value;

        private int index;

        private Parser(String value) {
            this.value = value;
        }

        private TemplateVariableType parseType(int depth) {
            if (depth > MAX_DEPTH) {
                throw invalid("nesting exceeds " + MAX_DEPTH + " levels");
            }
            skipWhitespace();
            String token = readIdentifier();
            skipWhitespace();
            if ("list".equals(token)) {
                expect('<');
                TemplateVariableType element = parseType(depth + 1);
                expect('>');
                return new TemplateVariableType.ListOf(element);
            }
            if ("map".equals(token)) {
                expect('<');
                TemplateVariableType mapValue = parseType(depth + 1);
                expect('>');
                return new TemplateVariableType.MapOf(mapValue);
            }
            return switch (token) {
                case "string" -> new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.STRING);
                case "boolean" -> new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.BOOLEAN);
                case "integer" -> new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.INTEGER);
                case "enum" -> {
                    if (depth > 0) {
                        throw invalid("enum is only supported as a top-level variable type");
                    }
                    yield new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.ENUM);
                }
                case "any" -> new TemplateVariableType.Scalar(TemplateVariableType.ScalarType.ANY);
                case "object" -> new TemplateVariableType.ObjectType();
                default -> throw invalid("unsupported type '" + token + "'");
            };
        }

        private String readIdentifier() {
            int start = index;
            while (index < value.length() && Character.isLetter(value.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw invalid("expected a type name");
            }
            return value.substring(start, index);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= value.length() || value.charAt(index) != expected) {
                throw invalid("expected '" + expected + "'");
            }
            index++;
        }

        private void skipWhitespace() {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
        }

        private boolean atEnd() {
            return index >= value.length();
        }

        private TemplatePackException invalid(String message) {
            return new TemplatePackException("invalid variable type '" + value + "': " + message);
        }
    }
}
