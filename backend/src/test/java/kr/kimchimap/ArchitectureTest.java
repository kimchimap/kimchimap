package kr.kimchimap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "kr.kimchimap")
class ArchitectureTest {
  @ArchTest
  static final ArchRule controllersDoNotAccessPersistence =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..repository..", "..entity..");
}
