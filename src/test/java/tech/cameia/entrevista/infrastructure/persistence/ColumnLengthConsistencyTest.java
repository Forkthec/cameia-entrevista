package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import tech.cameia.entrevista.support.WithLanguageModelProperties;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Vigila que el largo de cada columna de texto libre de la base coincida con el límite que valida
 * la entrada. Así un campo sin validar nunca termina en un error de base de datos.
 *
 * <p>Los límites se comparan contra literales porque el dominio todavía no declara constantes
 * para ellos; cada historia que agregue el campo reemplaza el literal por su constante. Se omite
 * si no hay Docker.</p>
 */
@SpringBootTest
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class ColumnLengthConsistencyTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	private JdbcTemplate jdbc;

	static Stream<Arguments> columnLengths() {
		return Stream.of(
				Arguments.of("sesion", "firebase_uid", 128),
				Arguments.of("sesion", "oferta_laboral", 3000),
				Arguments.of("pregunta", "texto_original", 1000),
				Arguments.of("intento_respuesta", "respuesta_texto", 4000),
				Arguments.of("intento_respuesta", "pregunta_mostrada", 1000),
				Arguments.of("intento_respuesta", "idioma_detectado", 10),
				Arguments.of("clave_idempotencia", "clave", 64));
	}

	@ParameterizedTest(name = "{0}.{1} = {2}")
	@MethodSource("columnLengths")
	@DisplayName("El largo de la columna coincide con el límite validado")
	void columnLength_shouldMatchDomainLimit_whenSchemaApplied(String table, String column, int expected) {
		Integer actual = jdbc.queryForObject("""
				SELECT character_maximum_length FROM information_schema.columns
				 WHERE table_schema = 'microentrevista' AND table_name = ? AND column_name = ?
				""", Integer.class, table, column);

		assertThat(actual).isEqualTo(expected);
	}

}
