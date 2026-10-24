package io.github.coco.generate.template;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Loads and validates v2 template-pack manifests. */
public final class TemplatePackLoader {

    public static final String BUILTIN_ROOT = "META-INF/coco-generate/templates";

    private static final Set<String> METADATA_KEYS = Set.of(
            "manifestVersion", "id", "displayName", "description",
            "status", "outputKind", "ownership", "packVersion");

    private static final Set<String> VARIABLE_FIELDS = Set.of(
            "name", "type", "required", "default", "values", "description");

    private static final Set<String> TEMPLATE_FIELDS = Set.of("source", "output", "kind");

    /** Loads one executable pack and verifies every declared template exists. */
    public TemplatePack load(TemplateResourceRoot root, String group) {
        TemplatePackManifest manifest = readManifest(root, group);
        if (!TemplatePackManifest.EXECUTABLE.equals(manifest.status())) {
            throw new TemplatePackException(
                    "template route is not executable: " + group + " (" + manifest.status() + ")");
        }
        for (TemplateDefinition definition : manifest.templates()) {
            String path = manifest.id() + "/" + definition.source();
            if (!root.exists(path)) {
                throw new TemplatePackException("missing template source: " + path);
            }
        }
        return new TemplatePack(manifest, root, root.describe());
    }

    /** Loads one executable pack from the bundled classpath root. */
    public TemplatePack loadBuiltin(String group) {
        ClasspathTemplateResourceRoot root = new ClasspathTemplateResourceRoot(
                TemplatePackLoader.class.getClassLoader(), BUILTIN_ROOT, "builtin");
        return load(root, group);
    }

    /** Reads a manifest without requiring it to be executable. */
    public TemplatePackManifest readManifest(TemplateResourceRoot root, String group) {
        String checkedGroup = TemplatePackManifest.requireId(group);
        String path = checkedGroup + "/manifest.properties";
        byte[] bytes;
        try {
            bytes = root.read(path);
        }
        catch (IOException ex) {
            throw new TemplatePackException("unable to read template manifest: " + path, ex);
        }
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        catch (IOException ex) {
            throw new TemplatePackException("unable to parse template manifest: " + path, ex);
        }
        try {
            return parseManifest(checkedGroup, properties);
        }
        catch (TemplatePackException ex) {
            throw ex;
        }
        catch (RuntimeException ex) {
            throw new TemplatePackException(
                    "invalid template manifest '" + path + "': " + ex.getMessage(), ex);
        }
    }

    /** Reads the deterministic group list from index.txt. */
    public List<String> listGroups(TemplateResourceRoot root) {
        byte[] bytes;
        try {
            bytes = root.read("index.txt");
        }
        catch (IOException ex) {
            throw new TemplatePackException("unable to read template index from " + root.describe(), ex);
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        List<String> groups = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String group = TemplatePackManifest.requireId(trimmed);
            if (!seen.add(group)) {
                throw new TemplatePackException("duplicate template group in index: " + group);
            }
            groups.add(group);
        }
        return List.copyOf(groups);
    }

    private static TemplatePackManifest parseManifest(String group, Properties properties) {
        int variableCount = intProperty(properties, "variable.count", 0);
        int templateCount = requiredInt(properties, "template.count");
        if (variableCount < 0) {
            throw new TemplatePackException("template manifest variable.count must not be negative");
        }
        if (variableCount > 50) {
            throw new TemplatePackException("template manifest declares more than 50 variables");
        }
        if (templateCount < 0) {
            throw new TemplatePackException("template manifest template.count must not be negative");
        }
        if (templateCount > 100) {
            throw new TemplatePackException("template manifest declares more than 100 templates");
        }
        validateKnownKeys(properties, variableCount, templateCount);

        int version = requiredInt(properties, "manifestVersion");
        String id = required(properties, "id");
        if (!id.equals(group)) {
            throw new TemplatePackException(
                    "template manifest id '" + id + "' does not match group directory '" + group + "'");
        }
        String displayName = required(properties, "displayName");
        String description = required(properties, "description");
        String status = required(properties, "status");
        String outputKind = required(properties, "outputKind");
        String ownership = required(properties, "ownership");
        String packVersion = required(properties, "packVersion");

        List<TemplateVariable> variables = new ArrayList<>(variableCount);
        for (int index = 0; index < variableCount; index++) {
            variables.add(parseVariable(properties, index));
        }
        List<TemplateDefinition> templates = new ArrayList<>(templateCount);
        for (int index = 0; index < templateCount; index++) {
            templates.add(parseTemplate(properties, index));
        }
        return new TemplatePackManifest(
                version, id, displayName, description, status, outputKind, ownership,
                packVersion, variables, templates);
    }

    private static TemplateVariable parseVariable(Properties properties, int index) {
        String prefix = "variable." + index + ".";
        String name = required(properties, prefix + "name");
        String typeText = required(properties, prefix + "type");
        TemplateVariableType type = TemplateTypeParser.parse(typeText);
        boolean required = booleanProperty(properties, prefix + "required", false);
        List<String> values = enumValues(properties, prefix + "values");
        String description = properties.getProperty(prefix + "description", "").trim();
        Object defaultValue = defaultValue(properties, prefix + "default", type, values, name);
        return new TemplateVariable(name, type, required, defaultValue, values, description);
    }

    private static TemplateDefinition parseTemplate(Properties properties, int index) {
        String prefix = "template." + index + ".";
        String source = required(properties, prefix + "source");
        String output = required(properties, prefix + "output");
        String kind = properties.getProperty(prefix + "kind");
        return new TemplateDefinition(source, output, kind);
    }

    private static void validateKnownKeys(Properties properties, int variableCount, int templateCount) {
        Set<String> allowed = new LinkedHashSet<>(METADATA_KEYS);
        allowed.add("variable.count");
        allowed.add("template.count");
        for (int index = 0; index < variableCount; index++) {
            for (String field : VARIABLE_FIELDS) {
                allowed.add("variable." + index + "." + field);
            }
        }
        for (int index = 0; index < templateCount; index++) {
            for (String field : TEMPLATE_FIELDS) {
                allowed.add("template." + index + "." + field);
            }
        }
        for (String key : properties.stringPropertyNames()) {
            if (!allowed.contains(key)) {
                throw new TemplatePackException("template manifest contains unknown key: " + key);
            }
        }
    }

    private static Object defaultValue(
            Properties properties,
            String key,
            TemplateVariableType type,
            List<String> enumValues,
            String variableName) {
        String text = properties.getProperty(key);
        if (text == null) {
            return null;
        }
        if (!(type instanceof TemplateVariableType.Scalar scalarType)) {
            throw new TemplatePackException(
                    "default is only supported for scalar variables: " + variableName);
        }
        return switch (scalarType.type()) {
            case STRING -> text;
            case BOOLEAN -> parseBoolean(text, key);
            case INTEGER -> parseLong(text, key);
            case ENUM -> {
                String value = text.trim();
                if (!enumValues.contains(value)) {
                    throw new TemplatePackException(
                            "default for enum variable '" + variableName + "' is not one of its values: " + value);
                }
                yield value;
            }
            case ANY -> throw new TemplatePackException(
                    "default is not supported for type any: " + variableName);
        };
    }

    private static List<String> enumValues(Properties properties, String key) {
        String text = properties.getProperty(key);
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : text.split(",", -1)) {
            String value = part.trim();
            if (value.isEmpty()) {
                throw new TemplatePackException("template manifest contains a blank enum value: " + key);
            }
            if (!seen.add(value)) {
                throw new TemplatePackException("template manifest contains a duplicate enum value: " + key);
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static int requiredInt(Properties properties, String key) {
        String value = required(properties, key);
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ex) {
            throw new TemplatePackException(
                    "template manifest key must be an integer: " + key + "=" + value, ex);
        }
    }

    private static int intProperty(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        if (value.isBlank()) {
            throw new TemplatePackException("template manifest key must not be blank: " + key);
        }
        try {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException ex) {
            throw new TemplatePackException(
                    "template manifest key must be an integer: " + key + "=" + value, ex);
        }
    }

    private static boolean booleanProperty(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return parseBoolean(value, key);
    }

    private static boolean parseBoolean(String value, String key) {
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new TemplatePackException(
                "template manifest key must be true or false: " + key + "=" + value);
    }

    private static long parseLong(String value, String key) {
        try {
            return Long.parseLong(value.trim());
        }
        catch (NumberFormatException ex) {
            throw new TemplatePackException(
                    "template manifest key must be an integer: " + key + "=" + value, ex);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new TemplatePackException("template manifest is missing " + key);
        }
        return value.trim();
    }
}
