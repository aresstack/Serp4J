package com.aresstack.serp4j.core.architecture;

import com.aresstack.serp4j.core.annotation.Experimental;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT;
import static com.tngtech.archunit.core.domain.JavaModifier.FINAL;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Dependency rules of architecture.md §3 (rule 5) and the API conventions of ADR-004, enforced on the bytecode. */
@AnalyzeClasses(packages = "com.aresstack.serp4j.core", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreArchitectureTest {

    @ArchTest
    static final ArchRule core_has_no_third_party_dependencies = classes()
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.aresstack.serp4j.core..")
            .because("serp4j-core has zero dependencies (ADR-001)");

    @ArchTest
    static final ArchRule model_depends_only_on_jdk_and_itself = classes()
            .that().resideInAPackage("..core.model..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..core.model..", "..core.internal..")
            .because("core.model.. depends only on the JDK (architecture.md §3 rule 5)");

    @ArchTest
    static final ArchRule ports_and_errors_depend_only_on_model = classes()
            .that().resideInAnyPackage("..core.port..", "..core.error..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..core.model..", "..core.port..", "..core.error..",
                    "..core.internal..", "..core.annotation..")
            .because("core.port.. and core.error depend only on core.model.. (architecture.md §3 rule 5)");

    @ArchTest
    static final ArchRule no_network_or_file_system_access = noClasses()
            .should().dependOnClassesThat(resideInAnyPackage("java.net..", "java.nio.file..")
                    .or(type(java.io.File.class)))
            .because("the core performs no I/O (architecture.md §3 rule 5)");

    @ArchTest
    static final ArchRule no_own_threads = noClasses()
            .should().dependOnClassesThat(type(Thread.class).or(type(Executors.class))
                    .or(type(ThreadPoolExecutor.class)).or(type(ForkJoinPool.class)))
            .because("executors belong to the application layer (WP2 F10)");

    @ArchTest
    static final ArchRule no_serializable_value_types = noClasses()
            .that().areNotAssignableTo(Throwable.class)
            .and().areNotEnums()
            .should().implement(Serializable.class)
            .because("value types are not Java-serialisable (ADR-004); enums are serialisable by definition");

    @ArchTest
    static final ArchRule model_value_classes_are_final = classes()
            .that().resideInAPackage("..core.model..")
            .and().areTopLevelClasses()
            .and().areNotInterfaces()
            .and().areNotEnums()
            .and().doNotHaveModifier(ABSTRACT)
            .should().haveModifier(FINAL)
            .because("immutable value classes are final (ADR-004)");

    @ArchTest
    static final ArchRule port_classes_are_interfaces_enums_or_final = classes()
            .that().resideInAPackage("..core.port..")
            .and().areTopLevelClasses()
            .and().areNotInterfaces()
            .and().areNotEnums()
            .should().haveModifier(FINAL)
            .because("ports are interfaces, their value types final (ADR-004)");

    @ArchTest
    static final ArchRule internal_is_not_used_outside_core = classes()
            .that().resideInAPackage("..core.internal..")
            .should().onlyBeAccessed().byClassesThat().resideInAPackage("com.aresstack.serp4j.core..")
            .because("..internal.. packages are not public API (architecture.md §2)");

    @ArchTest
    static final ArchRule conversation_api_is_experimental = classes()
            .that().resideInAPackage("..core.port.conversation..")
            .and().areTopLevelClasses()
            .should().beAnnotatedWith(Experimental.class)
            .because("conversational search is experimental until the first adapter exists (ADR-013)");
}
