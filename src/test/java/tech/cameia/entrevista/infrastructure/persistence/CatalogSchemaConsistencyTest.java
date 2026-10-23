package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.CatalogOption;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.policy.PersonalizationCatalog;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Vigila que el catálogo de personalización y la base de datos digan lo mismo: cada restricción
 * {@code CHECK} de la tabla de sesiones admite exactamente los valores que el catálogo ofrece, y
 * ninguno excede el largo de su columna. Así un valor nuevo en un enumerado sin su migración rompe
 * esta prueba y no un {@code INSERT} en producción. Se omite si no hay Docker.
 */
@SpringBootTest
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class CatalogSchemaConsistencyTest {

    private static final Pattern LITERAL = Pattern.compile("'([^']+)'");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    private final PersonalizationCatalog catalog = new PersonalizationCatalog();

    /** Códigos que el catálogo ofrece para la columna dada, tal como los guarda la base. */
    private Set<String> catalogCodes(String column) {
        return switch (column) {
            case "modalidad" -> codes(catalog.modes(), Modality::code);
            case "tono" -> codes(catalog.tones(), Tone::code);
            case "forma_respuesta" -> codes(catalog.answerFormats(), AnswerMode::code);
            case "idioma" -> codes(catalog.languages(), InterviewLanguage::code);
            default -> throw new IllegalArgumentException(column);
        };
    }

    private static <E extends Enum<E>> Set<String> codes(List<CatalogOption<E>> options,
            Function<E, String> code) {
        return options.stream().map(option -> code.apply(option.value())).collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "{0} ↔ {1}")
    @CsvSource({
            "ck_sesion_modalidad,modalidad",
            "ck_sesion_tono,tono",
            "ck_sesion_forma_respuesta,forma_respuesta",
            "ck_sesion_idioma,idioma"})
    @DisplayName("Los valores de la restricción CHECK son los del catálogo")
    void catalog_shouldMatchDatabaseCheck_forEveryColumn(String constraint, String column) {
        String definition = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c
                  JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE n.nspname = 'microentrevista' AND c.conname = ?
                """, String.class, constraint);

        Set<String> literals = LITERAL.matcher(definition).results()
                .map(match -> match.group(1)).collect(Collectors.toSet());

        assertThat(literals).isEqualTo(catalogCodes(column));
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource({
            "ck_sesion_modalidad,modalidad",
            "ck_sesion_tono,tono",
            "ck_sesion_forma_respuesta,forma_respuesta",
            "ck_sesion_idioma,idioma"})
    @DisplayName("Cada código del catálogo cabe en su columna")
    void catalog_shouldFitColumnLength_forEveryCode(String constraint, String column) {
        Integer maxLength = jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                 WHERE table_schema = 'microentrevista' AND table_name = 'sesion' AND column_name = ?
                """, Integer.class, column);

        assertThat(catalogCodes(column)).isNotEmpty()
                .allSatisfy(code -> assertThat(code.length()).isLessThanOrEqualTo(maxLength));
    }
}
