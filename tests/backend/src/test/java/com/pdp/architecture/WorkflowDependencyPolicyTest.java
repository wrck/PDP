package com.pdp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class WorkflowDependencyPolicyTest {

    @Test
    void businessModulesMustNotDependOnFlowableTypes() {
        var classes = new ClassFileImporter().importPackages("com.pdp");
        noClasses()
                .that().resideOutsideOfPackage("com.pdp.workflow..")
                .should().dependOnClassesThat().resideInAnyPackage("org.flowable..")
                .check(classes);
    }
}

