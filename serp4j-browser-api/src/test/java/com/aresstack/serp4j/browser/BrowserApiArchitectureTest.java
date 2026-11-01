package com.aresstack.serp4j.browser;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.io.Serializable;

import static com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT;
import static com.tngtech.archunit.core.domain.JavaModifier.FINAL;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Module rules of ADR-010 and architecture.md §3, enforced on the bytecode. */
@AnalyzeClasses(packages = "com.aresstack.serp4j.browser", importOptions = ImportOption.DoNotIncludeTests.class)
class BrowserApiArchitectureTest {

    @ArchTest
    static final ArchRule depends_only_on_the_jdk = classes()
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.aresstack.serp4j.browser")
            .because("serp4j-browser-api has no dependencies and does not know serp4j-core (ADR-010)");

    @ArchTest
    static final ArchRule no_threads_in_the_port = noClasses()
            .should().dependOnClassesThat().areAssignableTo(Thread.class)
            .because("threading belongs to the implementations (WP3 B-6)");

    @ArchTest
    static final ArchRule value_types_are_final = classes()
            .that().areTopLevelClasses().and().areNotInterfaces().and().areNotEnums()
            .and().doNotHaveModifier(ABSTRACT)
            .and().areNotAssignableTo(Throwable.class)
            .should().haveModifier(FINAL)
            .because("immutable value classes are final (ADR-004)");

    @ArchTest
    static final ArchRule no_serializable_value_types = noClasses()
            .that().areNotAssignableTo(Throwable.class).and().areNotEnums()
            .should().implement(Serializable.class)
            .because("value types are not Java-serialisable (ADR-004)");
}
