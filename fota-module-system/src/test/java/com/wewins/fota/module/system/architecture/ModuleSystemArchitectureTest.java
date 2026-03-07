package com.wewins.fota.module.system.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.wewins.fota.module.system", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleSystemArchitectureTest {

    @ArchTest
    static final ArchRule adapter_api_should_not_depend_on_domain_entities = noClasses()
            .that().resideInAnyPackage("..adapter.api..")
            .and().resideOutsideOfPackage("..adapter.api.admin..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain.entity..");

    @ArchTest
    static final ArchRule application_should_not_depend_on_mybatis_mapper = noClasses()
            .that().resideInAnyPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infra.persistence.mybatis.mapper..");
}
