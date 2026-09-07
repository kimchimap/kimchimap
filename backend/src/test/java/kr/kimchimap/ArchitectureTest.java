package kr.kimchimap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "kr.kimchimap", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  @ArchTest
  static final ArchRule controllersDoNotAccessPersistence =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..repository..", "..entity..");

  @ArchTest
  static final ArchRule repositoriesDoNotCallServices =
      noClasses()
          .that()
          .resideInAPackage("..repository..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..service..", "..controller..");

  @ArchTest
  static final ArchRule servicesDoNotCallControllers =
      noClasses()
          .that()
          .resideInAPackage("..service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..controller..");

  @ArchTest
  static final ArchRule featuresHaveNoCycles =
      slices().matching("kr.kimchimap.(*)..").should().beFreeOfCycles();
}
