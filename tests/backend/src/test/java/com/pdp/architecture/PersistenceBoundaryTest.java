package com.pdp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class PersistenceBoundaryTest {

    @Test
    void publicPersistenceMustNotDependOnMysqlOrMybatisImplementations() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.pdp.persistence", "com.pdp.mysql");

        noClasses()
                .that().resideInAPackage("com.pdp.persistence..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.pdp.mysql..",
                        "com.baomidou..",
                        "org.apache.ibatis..",
                        "com.mysql..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void domainApplicationAndPortsMustNotDependOnDatabaseAdapters() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.pdp");

        noClasses()
                .that().resideInAnyPackage("..domain..", "..application..", "..port..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.pdp.mysql..",
                        "com.baomidou..",
                        "org.apache.ibatis..",
                        "com.mysql..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
