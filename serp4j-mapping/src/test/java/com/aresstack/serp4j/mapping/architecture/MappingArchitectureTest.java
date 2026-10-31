package com.aresstack.serp4j.mapping.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Module rules of ADR-019 §1 and architecture.md §5.6 rule 1, enforced on the bytecode. */
@AnalyzeClasses(packages = "com.aresstack.serp4j.mapping", importOptions = ImportOption.DoNotIncludeTests.class)
class MappingArchitectureTest {

    @ArchTest
    static final ArchRule depends_only_on_core_and_the_jdk = classes()
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "com.aresstack.serp4j.core.model..", "com.aresstack.serp4j.mapping..")
            .because("serp4j-mapping depends only on serp4j-core (its model) and on no third-party library (ADR-019 §1)");

    @ArchTest
    static final ArchRule no_json_library = noClasses()
            .should().dependOnClassesThat(resideInAnyPackage("com.google.gson..", "com.fasterxml..", "javax.json..",
                    "jakarta.json..", "org.json.."))
            .because("the mapping engine works on StructuredValue only; Gson lives in serp4j-json (ADR-019 §1)");

    @ArchTest
    static final ArchRule no_core_internals = noClasses()
            .should().dependOnClassesThat(resideInAnyPackage("com.aresstack.serp4j.core.internal.."))
            .because("core.internal is not API for other modules");

    @ArchTest
    static final ArchRule no_io = noClasses()
            .should().dependOnClassesThat(resideInAnyPackage("java.net..", "java.nio.file..", "java.io.File"))
            .because("reading resources is the caller's job (serp4j-json); the engine performs no I/O");

    @ArchTest
    static final ArchRule public_api_does_not_use_internal = noClasses()
            .that().resideOutsideOfPackage("..mapping.internal..")
            .and().resideOutsideOfPackage("com.aresstack.serp4j.mapping")
            .should().dependOnClassesThat(resideInAnyPackage("..mapping.internal.."))
            .because("only the engine and the definition reader use the internal helpers");
}
