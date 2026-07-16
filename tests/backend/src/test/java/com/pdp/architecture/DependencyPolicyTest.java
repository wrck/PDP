package com.pdp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class DependencyPolicyTest {

    @Test
    void domainAndApplicationCodeMustNotDependOnJpaOrHibernate() {
        var classes = new ClassFileImporter().importPackages("com.pdp");
        noClasses()
                .that().resideInAnyPackage("..domain..", "..application..", "..port..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.hibernate..", "jakarta.persistence..", "org.springframework.data.jpa..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
