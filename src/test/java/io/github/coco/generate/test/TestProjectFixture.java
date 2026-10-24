package io.github.coco.generate.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Builds a project with an external v2 template pack for end-to-end tests. */
public final class TestProjectFixture {

    private TestProjectFixture() {
    }

    public static final String CONFIG = """
            template:
              location: ./.coco/templates
              group: company-crud
              encoding: UTF-8
            vars:
              author: yaml-team
            base-package: com.example.catalog
            resources:
              - name: Product
                table: catalog_product
                vars:
                  imports:
                    - java.util.List
                    - java.util.Optional
                  annotations:
                    team: platform
                id:
                  name: id
                  column: id
                  type: Long
                  strategy: AUTO
                fields:
                  - name: sku
                    column: sku
                    type: String
                    required: true
              - name: AuditLog
                table: audit_log
                template: audit-crud
                vars:
                  author: resource-team
                id:
                  name: id
                  column: id
                  type: Long
                  strategy: AUTO
                fields:
                  - name: action
                    column: action
                    type: String
                    required: true
            """;

    private static final String COMPANY_MANIFEST = """
            manifestVersion=2
            id=company-crud
            displayName=Company CRUD
            description=Company-standard CRUD source layout.
            status=executable
            outputKind=readable-source
            ownership=business-project
            packVersion=1.0.0
            variable.count=3
            variable.0.name=author
            variable.0.type=string
            variable.0.required=true
            variable.1.name=imports
            variable.1.type=list<string>
            variable.2.name=annotations
            variable.2.type=map<string>
            template.count=2
            template.0.source=templates/domain.java.ftl
            template.0.output=${crud.basePackagePath}/domain/${crud.resourcePackage}/${crud.resourceName}.java
            template.0.kind=java
            template.1.source=templates/controller.java.ftl
            template.1.output=${crud.basePackagePath}/interfaces/rest/${crud.resourcePackage}/${crud.resourceName}Controller.java
            template.1.kind=java
            """;

    private static final String AUDIT_MANIFEST = """
            manifestVersion=2
            id=audit-crud
            displayName=Audit CRUD
            description=Audit source layout.
            status=executable
            outputKind=readable-source
            ownership=business-project
            packVersion=2.0.0
            variable.count=1
            variable.0.name=author
            variable.0.type=string
            variable.0.required=true
            template.count=1
            template.0.source=templates/audit.java.ftl
            template.0.output=${crud.basePackagePath}/audit/${crud.resourceName}Audit.java
            template.0.kind=java
            """;

    private static final String COMPANY_DOMAIN = """
            package ${crud.basePackage}.domain.${crud.resourcePackage};

            /**
             * ${crud.resourceName} domain model.
             * @author ${vars.author}
             * group ${_coco.templateGroup} pack ${_coco.packVersion} resource ${resource.name}
             * imports:
            <#list vars.imports as imported>
             * ${imported}
            </#list>
             * team: ${vars.annotations.team}
             */
            public record ${crud.resourceName}(
            <#list crud.allFields as field>
                    ${field.javaType} ${field.name}<#if field_has_next>,</#if>
            </#list>
            ) {
            }
            """;

    private static final String COMPANY_CONTROLLER = """
            package ${crud.basePackage}.interfaces.rest.${crud.resourcePackage};

            public class ${crud.resourceName}Controller {
                // @author ${vars.author}
            }
            """;

    private static final String AUDIT_TEMPLATE = """
            package ${crud.basePackage}.audit;

            public class ${crud.resourceName}Audit {
                // @author ${vars.author}
                // group ${_coco.templateGroup} pack ${_coco.packVersion}
            }
            """;

    private static final String SIMPLE_CONFIG = """
            template:
              location: ./.coco/templates
              group: simple
            vars:
              author: root-team
            base-package: com.example.simple
            resources:
              - name: Widget
                table: simple_widget
                id:
                  name: id
                  column: id
                  type: Long
                  strategy: AUTO
                fields:
                  - name: label
                    column: label
                    type: String
                    required: true
            """;

    private static String simpleManifest(String id, String packVersion) {
        return """
                manifestVersion=2
                id=%s
                displayName=%s
                description=%s source layout.
                status=executable
                outputKind=readable-source
                ownership=business-project
                packVersion=%s
                variable.count=1
                variable.0.name=author
                variable.0.type=string
                variable.0.required=true
                template.count=1
                template.0.source=templates/widget.java.ftl
                template.0.output=${crud.basePackagePath}/${_coco.templateGroup}/${crud.resourceName}.java
                template.0.kind=java
                """.formatted(id, id, id, packVersion);
    }

    private static final String SIMPLE_TEMPLATE = """
            package ${crud.basePackage}.${_coco.templateGroup};

            public class ${crud.resourceName} {
                // ${vars.author} ${_coco.packVersion}
            }
            """;

    public static Path createExternalProject(Path project) throws IOException {
        Files.createDirectories(project);
        Files.writeString(project.resolve("coco-generate.yml"), CONFIG);
        Path root = project.resolve(".coco/templates");
        Files.createDirectories(root);
        Files.writeString(root.resolve("index.txt"), "company-crud\naudit-crud\n");
        writePack(root, "company-crud", COMPANY_MANIFEST, Map.of(
                "templates/domain.java.ftl", COMPANY_DOMAIN,
                "templates/controller.java.ftl", COMPANY_CONTROLLER));
        writePack(root, "audit-crud", AUDIT_MANIFEST, Map.of(
                "templates/audit.java.ftl", AUDIT_TEMPLATE));
        return project;
    }

    public static Path createSimpleProject(Path project) throws IOException {
        Files.createDirectories(project);
        Files.writeString(project.resolve("coco-generate.yml"), SIMPLE_CONFIG);
        Path root = project.resolve(".coco/templates");
        Files.createDirectories(root);
        Files.writeString(root.resolve("index.txt"), "simple\nalternate\n");
        writePack(root, "simple", simpleManifest("simple", "1.0.0"), Map.of(
                "templates/widget.java.ftl", SIMPLE_TEMPLATE));
        writePack(root, "alternate", simpleManifest("alternate", "3.0.0"), Map.of(
                "templates/widget.java.ftl", SIMPLE_TEMPLATE));
        return project;
    }

    public static void writePack(
            Path root,
            String group,
            String manifest,
            Map<String, String> templates) throws IOException {
        Path groupDirectory = root.resolve(group);
        Files.createDirectories(groupDirectory.resolve("templates"));
        Files.writeString(groupDirectory.resolve("manifest.properties"), manifest);
        for (Map.Entry<String, String> entry : templates.entrySet()) {
            Path target = groupDirectory.resolve(entry.getKey()).normalize();
            if (!target.startsWith(groupDirectory)) {
                throw new IllegalArgumentException("test template escapes group directory: " + entry.getKey());
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
    }

    public static Path templateRoot(Path project) {
        return project.resolve(".coco/templates");
    }
}
