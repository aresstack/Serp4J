package com.aresstack.serp4j.browser.playwright;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/** Playwright types must not leak into the public API of the adapter. */
class PlaywrightAdapterArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.aresstack.serp4j.browser.playwright");

    @Test
    void public_methods_do_not_expose_playwright_types() {
        noMethods().that().arePublic().and().areDeclaredInClassesThat().arePublic()
                .should().haveRawReturnType(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "a Playwright type", (com.tngtech.archunit.core.domain.JavaClass c) ->
                                c.getPackageName().startsWith("com.microsoft.playwright")))
                .check(CLASSES);
        noMethods().that().arePublic().and().areDeclaredInClassesThat().arePublic()
                .should().haveRawParameterTypes(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "a Playwright type", (java.util.List<com.tngtech.archunit.core.domain.JavaClass> types) ->
                                types.stream().anyMatch(c -> c.getPackageName().startsWith("com.microsoft.playwright"))))
                .check(CLASSES);
    }

    @Test
    void the_adapter_does_not_know_the_core_or_engines() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("com.aresstack.serp4j.core..",
                        "com.aresstack.serp4j.engine..")
                .check(CLASSES);
    }
}
