package com.edreams.flights;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Turns the Clean Architecture constraint into a failing build. Analyses every module on the
 * classpath, so a framework import in domain or application fails here, not in review.
 */
@AnalyzeClasses(packages = "com.edreams.flights", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String BOOTSTRAP = "..bootstrap..";

    private static final String[] FRAMEWORK_PACKAGES = {
            "org.springframework..",
            "jakarta..",
            "tools.jackson..",
            "com.fasterxml..",
            "io.github.resilience4j..",
            "java.sql..",
            "javax.sql..",
            "org.postgresql.."
    };

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
            .because("the domain must stay plain Java")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_is_framework_free = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
            .because("use cases depend on ports, never on technology")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dependencies_point_inwards = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Infrastructure").definedBy(INFRASTRUCTURE)
            .layer("Bootstrap").definedBy(BOOTSTRAP)
            .whereLayer("Bootstrap").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Bootstrap")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Bootstrap")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Bootstrap")
            .allowEmptyShould(true);
}
