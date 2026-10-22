package tech.cameia.entrevista.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.ErrorCode;

/**
 * Toda excepción del paquete de excepciones del dominio es de negocio: lleva código estable y
 * pasa por el manejador del servicio. Una excepción suelta terminaría en el error genérico.
 */
class UntypedExceptionClassificationTest {

    @Test
    @DisplayName("Toda clase de domain.exception, salvo el catálogo de códigos, hereda de BusinessException")
    void domainExceptions_shouldExtendBusinessException() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("tech.cameia.entrevista.domain.exception");

        classes().that().resideInAPackage("tech.cameia.entrevista.domain.exception..")
                .and().areNotAssignableFrom(ErrorCode.class)
                .and().areNotAssignableFrom(BusinessException.class)
                .and().areTopLevelClasses()
                .and().doNotHaveSimpleName("package-info")
                .should().beAssignableTo(BusinessException.class)
                .check(classes);
    }
}
