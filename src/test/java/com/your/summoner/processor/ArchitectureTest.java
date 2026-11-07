package com.your.summoner.processor;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
  private static final String BASE_PACKAGE = "com.your.summoner.processor";
  @Test void domainRemainsFrameworkIndependent() {
    ArchRule rule = noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "lombok..", "org.mapstruct..", "com.fasterxml..", "tools.jackson..", "jakarta..", "..application..", "..infrastructure..").allowEmptyShould(true);
    rule.check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(BASE_PACKAGE));
  }
  @Test void applicationDoesNotDependOnInfrastructureOrSpringOutsideStereotypes() {
    DescribedPredicate<JavaClass> springOutsideStereotypes = resideInAPackage("org.springframework..")
            .and(not(resideInAPackage("org.springframework.stereotype..")));
    DescribedPredicate<JavaClass> forbiddenDependency = resideInAPackage("..infrastructure..")
            .or(springOutsideStereotypes);
    ArchRule rule = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat(forbiddenDependency).allowEmptyShould(true);
    rule.check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(BASE_PACKAGE));
  }
}
