package tech.cameia.entrevista.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica la regla de dependencias entre capas: {@code domain} no importa nada de
 * {@code presentation}, {@code application} ni {@code infrastructure} ni conoce la tecnología que lo
 * rodea; {@code application} no depende de {@code infrastructure} ni de {@code presentation}.
 *
 * <p>Corre sobre las clases compiladas del servicio, así que solo detecta violaciones en código ya
 * existente; no reemplaza la revisión de diseño al introducir una clase nueva.</p>
 */
class LayeredArchitectureTest {

    private static final String BASE_PACKAGE = "tech.cameia.entrevista";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Test
    @DisplayName("El dominio no depende de las capas superiores")
    void domain_shouldNotDependOnOuterLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".presentation..",
                        BASE_PACKAGE + ".application..",
                        BASE_PACKAGE + ".infrastructure..");

        rule.check(classes);
    }

    @Test
    @DisplayName("El dominio no conoce la tecnología que lo rodea")
    void domain_shouldNotKnowTechnology() {
        // El dominio define puertos; quien habla con JPA, Hibernate, Spring o un proveedor de IA es la
        // infraestructura. Si esta regla se rompe, las reglas de negocio dejan de probarse sin levantar
        // medio sistema.
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.springframework..",
                        "com.google..",
                        "org.hibernate..");

        rule.check(classes);
    }

    @Test
    @DisplayName("La aplicación no depende de infraestructura ni de presentación")
    void application_shouldNotDependOnInfrastructureOrPresentation() {
        // allowEmptyShould: la capa application aún no tiene clases; en cuanto las tenga, la regla las vigila.
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".infrastructure..",
                        BASE_PACKAGE + ".presentation..")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    @DisplayName("Ninguna clase del servicio escribe archivos: el audio y los datos de la persona no tocan el disco")
    void services_shouldNotWriteFiles() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + "..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Files")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileOutputStream")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileWriter")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.RandomAccessFile")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.nio.channels.FileChannel")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.nio.channels.AsynchronousFileChannel")
                .orShould().callMethodWhere(target(nameMatching("createTempFile"))
                        .and(target(owner(assignableTo(java.io.File.class)))))
                .orShould().callMethodWhere(target(nameMatching("transferTo"))
                        .and(target(owner(assignableTo(org.springframework.web.multipart.MultipartFile.class)))))
                .because("el audio de la respuesta vive solo en memoria y se descarta al terminar la petición");

        rule.check(classes);
    }
}
