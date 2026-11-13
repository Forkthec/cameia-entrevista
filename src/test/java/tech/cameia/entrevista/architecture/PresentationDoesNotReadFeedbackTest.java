package tech.cameia.entrevista.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La capa de presentación no puede leer la retroalimentación: no hay forma de que llegue a una respuesta HTTP. */
class PresentationDoesNotReadFeedbackTest {

    private static final String BASE_PACKAGE = "tech.cameia.entrevista";

    @Test
    @DisplayName("Ninguna clase de presentación depende de la retroalimentación ni de la evaluación")
    void presentation_shouldNotDependOnFeedback() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".presentation..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(BASE_PACKAGE + ".domain.model.TurnFeedback")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName(BASE_PACKAGE + ".domain.port.TurnFeedbackRepository")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName(BASE_PACKAGE + ".domain.model.EvaluationResult")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName(BASE_PACKAGE + ".infrastructure.persistence.entity.TurnFeedbackEntity")
                .check(classes);
    }
}
