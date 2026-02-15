package com.finstream.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.finstream", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_application =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..application..");

    @ArchTest
    static final ArchRule domain_should_not_use_spring =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule application_should_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..");

    // Whitelist rationale: web adapters legitimately need Spring Web, validation, stereotype
    // annotations, Jakarta Servlet (for filters), SLF4J logging, and Spring Core (for @Order).
    // Review this list when adding new infrastructure classes to the web package.
    @ArchTest
    static final ArchRule web_adapters_should_only_depend_on_application_and_domain =
            classes()
                    .that().resideInAPackage("..adapters.web..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "..application..",
                            "..domain.model..",
                            "..domain.model.ids..",
                            "..domain.ports.inbound..",
                            "java..",
                            "jakarta..",
                            "org.slf4j..",
                            "org.springframework.core..",
                            "org.springframework.http..",
                            "org.springframework.stereotype..",
                            "org.springframework.validation..",
                            "org.springframework.web.."
                    );
}
