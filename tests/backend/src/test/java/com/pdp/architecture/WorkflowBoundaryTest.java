package com.pdp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.pdp.workflow.port.WorkflowAdministrationPort;
import com.pdp.workflow.port.WorkflowDefinitionPort;
import com.pdp.workflow.port.WorkflowRuntimePort;
import com.pdp.workflow.port.WorkflowTaskPort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class WorkflowBoundaryTest {

    @Test
    void domainApplicationAndPortsMustNotDependOnFlowable() {
        var classes = new ClassFileImporter().importPackages("com.pdp.workflow");
        noClasses()
                .that().resideInAnyPackage(
                        "com.pdp.workflow.domain..",
                        "com.pdp.workflow.application..",
                        "com.pdp.workflow.port..")
                .should().dependOnClassesThat().resideInAnyPackage("org.flowable..")
                .check(classes);
    }

    @Test
    void publicPortsExposeOnlyPlatformContracts() {
        for (Class<?> port : java.util.List.of(
                WorkflowDefinitionPort.class,
                WorkflowRuntimePort.class,
                WorkflowTaskPort.class,
                WorkflowAdministrationPort.class)) {
            assertThat(Arrays.stream(port.getMethods())
                    .flatMap(method -> java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(method.getReturnType()),
                            Arrays.stream(method.getParameterTypes())))
                    .map(Class::getName))
                    .noneMatch(name -> name.startsWith("org.flowable."));
        }
    }

    @Test
    void businessModulesMustNotQueryFlowableTablesDirectly() throws IOException {
        Path root = repositoryRoot();
        try (var sources = Files.walk(root.resolve("modules"))) {
            var violations = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/modules/workflow/"))
                    .filter(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8).contains("ACT_");
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .map(root::relativize)
                    .toList();
            assertThat(violations)
                    .as("业务模块不得直接引用 Flowable ACT_* 引擎表")
                    .isEmpty();
        }
    }

    @Test
    void apiMustDisableEmbeddedFlowableRestAndIdm() throws IOException {
        String configuration = new ClassPathResource("application-workflow.yml")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(configuration)
                .contains("org.flowable.spring.boot.RestApiAutoConfiguration")
                .contains("org.flowable.spring.boot.idm.IdmEngineAutoConfiguration")
                .contains("rest-api-enabled: false")
                .contains("enabled: false");
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null && !Files.isDirectory(candidate.resolve("modules"))) {
            candidate = candidate.getParent();
        }
        assertThat(candidate).as("应能定位包含 modules 的仓库根目录").isNotNull();
        return candidate;
    }
}
