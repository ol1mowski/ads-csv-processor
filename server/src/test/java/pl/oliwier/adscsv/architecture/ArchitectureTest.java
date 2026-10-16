package pl.oliwier.adscsv.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.oliwier.adscsv.repository.ProcessingJobRepository;
import pl.oliwier.adscsv.service.state.JobStateService;

class ArchitectureTest {

  private static final String ROOT = "pl.oliwier.adscsv";
  private static final String CONTROLLER = ROOT + ".controller..";
  private static final String SERVICE = ROOT + ".service..";
  private static final String REPOSITORY = ROOT + ".repository..";
  private static final String MODEL = ROOT + ".model..";
  private static final String DTO = ROOT + ".dto..";
  private static final String CONFIG = ROOT + ".config..";
  private static final String EXCEPTION = ROOT + ".exception..";
  private static final String SHARED = ROOT + ".shared..";
  private static final String SECURITY = ROOT + ".security..";
  private static final String SERVLET = "jakarta.servlet..";

  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  @Test
  @DisplayName("Controllers never reach repositories directly")
  void shouldRejectRepositoryAccessWhenClassIsController() {
    noClasses()
        .that()
        .resideInAPackage(CONTROLLER)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(REPOSITORY)
        .check(PRODUCTION);
  }

  @Test
  @DisplayName("Services never depend on controllers")
  void shouldRejectControllerAccessWhenClassIsService() {
    noClasses()
        .that()
        .resideInAPackage(SERVICE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(CONTROLLER)
        .check(PRODUCTION);
  }

  @Test
  @DisplayName("Services never depend on the security web plumbing")
  void shouldRejectSecurityAccessWhenClassIsService() {
    noClasses()
        .that()
        .resideInAPackage(SERVICE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(SECURITY)
        .check(PRODUCTION);
  }

  @Test
  @DisplayName(
      "Security web plumbing never reaches repositories, the persistence model or controllers")
  void shouldRejectPersistenceAccessWhenClassIsSecurity() {
    noClasses()
        .that()
        .resideInAPackage(SECURITY)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(REPOSITORY, MODEL, CONTROLLER)
        .check(PRODUCTION);
  }

  @Test
  @DisplayName("Services never depend on the Servlet API")
  void shouldRejectServletAccessWhenClassIsService() {
    noClasses()
        .that()
        .resideInAPackage(SERVICE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(SERVLET)
        .because("requests, responses and cookies belong to the web layer")
        .check(PRODUCTION);
  }

  @Test
  @DisplayName("Repositories depend only on the model and shared building blocks")
  void shouldRejectUpperLayerAccessWhenClassIsRepository() {
    noClasses()
        .that()
        .resideInAPackage(REPOSITORY)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(CONTROLLER, SERVICE, DTO, CONFIG, EXCEPTION, SECURITY)
        .check(PRODUCTION);
  }

  @Test
  @DisplayName("The model depends on no upper layer")
  void shouldRejectUpperLayerAccessWhenClassIsModel() {
    noClasses()
        .that()
        .resideInAPackage(MODEL)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(CONTROLLER, SERVICE, REPOSITORY, DTO, CONFIG, EXCEPTION, SECURITY)
        .check(PRODUCTION);
  }

  @Test
  @DisplayName("Shared building blocks are a leaf that knows nothing about the application")
  void shouldRejectApplicationAccessWhenClassIsShared() {
    noClasses()
        .that()
        .resideInAPackage(SHARED)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            CONTROLLER, SERVICE, REPOSITORY, MODEL, DTO, CONFIG, EXCEPTION, SECURITY)
        .check(PRODUCTION);
  }

  @Test
  @DisplayName("Dependencies are injected through constructors only")
  void shouldNotUseFieldInjection() {
    GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(PRODUCTION);
  }

  @Test
  @DisplayName("Only the job state service may depend on the job repository")
  void shouldRejectRepositoryAccessWhenCallerIsNotJobStateService() {
    noClasses()
        .that()
        .doNotBelongToAnyOf(JobStateService.class)
        .and()
        .resideOutsideOfPackage(REPOSITORY)
        .should()
        .dependOnClassesThat()
        .belongToAnyOf(ProcessingJobRepository.class)
        .because("every change after the INSERT is a guarded UPDATE kept in one place")
        .check(PRODUCTION);
  }
}
