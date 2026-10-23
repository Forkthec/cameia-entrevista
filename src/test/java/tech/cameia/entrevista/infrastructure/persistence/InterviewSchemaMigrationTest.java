package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Prueba de integración del esquema inicial de Entrevista contra PostgreSQL 16 real.
 *
 * <p>El arranque del contexto demuestra que Flyway aplica la migración sobre una base vacía y
 * que Hibernate valida su mapeo. Las pruebas comprueban lo que solo vive en la base: cada
 * {@code CHECK}, cada índice único parcial, las claves foráneas y los disparadores. Son la red
 * de seguridad para cuando el error esté en el código y no en el dominio.</p>
 *
 * <p>Todo el SQL califica las tablas con el esquema {@code microentrevista}: la conexión de
 * {@link JdbcTemplate} usa el {@code search_path} por defecto. Se omite si no hay Docker.</p>
 */
@SpringBootTest
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class InterviewSchemaMigrationTest {

	private static final String SCHEMA = "microentrevista";

	/** Tabla de control de Flyway: vive en el esquema pero no es parte del modelo de Entrevista. */
	private static final String FLYWAY_TABLE = "flyway_schema_history";

	/** Columnas {@code jsonb}: el valor se envía como texto y se convierte en el {@code INSERT}. */
	private static final Set<String> JSONB_COLUMNS =
			Set.of("contexto_perfil_snapshot", "feedback", "feedback_star", "cuerpo_respuesta");

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanTables() {
		jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia CASCADE");
	}

	// ------------------------------------------------------------------ estructura

	@Test
	@DisplayName("Las migraciones crean las 8 tablas del motor y las 2 de la réplica de Perfil")
	void migration_shouldCreateAllTables_whenApplied() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name <> ?",
				String.class, SCHEMA, FLYWAY_TABLE);

		assertThat(tables).containsExactlyInAnyOrder("tipo_pregunta", "sesion", "pregunta", "turno",
				"intento_respuesta", "feedback_turno", "ejecucion_ia", "clave_idempotencia",
				"contexto_profesional_replica", "evento_procesado");
	}

	@Test
	@DisplayName("El catálogo de tipos de pregunta trae 4 filas y solo la conductual aplica STAR")
	void migration_shouldSeedQuestionTypes_whenApplied() {
		Map<String, Boolean> types = jdbc.query(
				"SELECT codigo, aplica_star FROM microentrevista.tipo_pregunta",
				rs -> {
					Map<String, Boolean> found = new LinkedHashMap<>();
					while (rs.next()) {
						found.put(rs.getString(1), rs.getBoolean(2));
					}
					return found;
				});

		assertThat(types).hasSize(4)
				.containsEntry("CONDUCTUAL", true)
				.containsEntry("APERTURA", false)
				.containsEntry("TECNICA", false)
				.containsEntry("SITUACIONAL", false);
	}

	@Test
	@DisplayName("Toda restricción e índice lleva un nombre explícito con su prefijo")
	void migration_shouldNameEveryConstraint_whenApplied() {
		List<String> badConstraints = jdbc.queryForList("""
				SELECT c.conname FROM pg_constraint c
				 WHERE c.connamespace = 'microentrevista'::regnamespace
				   AND c.conrelid <> 'microentrevista.flyway_schema_history'::regclass
				   AND c.conname !~ '^(pk|fk|uq|ck)_'
				""", String.class);
		List<String> badIndexes = jdbc.queryForList("""
				SELECT indexname FROM pg_indexes
				 WHERE schemaname = 'microentrevista' AND tablename <> 'flyway_schema_history'
				   AND indexname !~ '^(pk|uq|ix)_'
				""", String.class);

		assertThat(badConstraints).isEmpty();
		assertThat(badIndexes).isEmpty();
	}

	@Test
	@DisplayName("Toda clave foránea es la primera columna de algún índice")
	void migration_shouldIndexEveryForeignKey_whenApplied() {
		List<String> unindexed = jdbc.queryForList("""
				SELECT c.conname FROM pg_constraint c
				 WHERE c.contype = 'f' AND c.connamespace = 'microentrevista'::regnamespace
				   AND c.conname LIKE 'fk\\_%'
				   AND NOT EXISTS (SELECT 1 FROM pg_index i
				                    WHERE i.indrelid = c.conrelid AND i.indpred IS NULL
				                      AND i.indkey[0] = c.conkey[1])
				""", String.class);

		assertThat(unindexed).isEmpty();
	}

	@Test
	@DisplayName("Existe el índice parcial de sesiones en curso por última actividad")
	void session_shouldHavePartialIndexOnLastActivity() {
		List<String> definitions = jdbc.queryForList("""
				SELECT indexdef FROM pg_indexes
				 WHERE schemaname = 'microentrevista' AND tablename = 'sesion'
				   AND indexname = 'ix_sesion_en_curso_ultima_actividad'
				""", String.class);

		assertThat(definitions).hasSize(1);
		assertThat(definitions.get(0)).contains("(fecha_ultima_actividad)").contains("WHERE").contains("EN_CURSO");
	}

	@Test
	@DisplayName("La sesión no tiene clave foránea hacia los datos de Perfil")
	void session_shouldHaveNoForeignKeyToOtherServices() {
		Integer foreignKeys = jdbc.queryForObject("""
				SELECT count(*) FROM pg_constraint
				 WHERE conrelid = 'microentrevista.sesion'::regclass AND contype = 'f'
				""", Integer.class);

		assertThat(foreignKeys).isZero();
	}

	@Test
	@DisplayName("El esquema no guarda columnas binarias: el audio no se persiste")
	void schema_shouldHaveNoBinaryColumns() {
		Integer binaryColumns = jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.columns
				 WHERE table_schema = 'microentrevista' AND data_type = 'bytea'
				""", Integer.class);

		assertThat(binaryColumns).isZero();
	}

	@Test
	@DisplayName("Cada tabla y cada columna de valores cerrados tiene su comentario")
	void schema_shouldCommentEveryTableAndClosedValueColumn() {
		for (String table : List.of("tipo_pregunta", "sesion", "pregunta", "turno", "intento_respuesta",
				"feedback_turno", "ejecucion_ia", "clave_idempotencia")) {
			assertThat(tableComment(table)).as("comentario de la tabla " + table).isNotBlank();
		}
		for (String[] column : new String[][] { { "sesion", "modalidad" }, { "sesion", "tono" },
				{ "sesion", "forma_respuesta" }, { "sesion", "idioma" }, { "pregunta", "tipo" },
				{ "intento_respuesta", "tipo_interaccion" }, { "intento_respuesta", "origen_respuesta" },
				{ "ejecucion_ia", "tipo_operacion" }, { "ejecucion_ia", "estado" } }) {
			assertThat(columnComment(column[0], column[1])).as(column[0] + "." + column[1]).startsWith("Valores: ");
		}
		assertThat(columnComment("sesion", "version")).isNotBlank();
		assertThat(columnComment("sesion", "contexto_perfil_snapshot")).isNotBlank();
		assertThat(columnComment("sesion", "estado")).isEqualTo(
				"CONFIGURADA al crearla, EN_CURSO al iniciarla, EVALUANDO al terminar las preguntas, "
						+ "FINALIZADA con la evaluacion lista, ABANDONADA si se deja. Una sesion configurada "
						+ "se elimina con DELETE: no tiene estado propio.");
		assertThat(columnComment("turno", "estado")).isEqualTo(
				"PENDING al crearse, PROCESSING al recibir la respuesta, COMPLETED al evaluarse, "
						+ "RETRYING si el modelo falla.");
	}

	// ------------------------------------------------------------------ valores cerrados y nulos

	static Stream<Arguments> closedValues() {
		return Stream.of(
				Arguments.of("sesion", "estado", "PAUSADA", "ck_sesion_estado"),
				Arguments.of("sesion", "estado", "en_curso", "ck_sesion_estado"),
				Arguments.of("sesion", "estado", "", "ck_sesion_estado"),
				Arguments.of("turno", "estado", "FAILED", "ck_turno_estado"),
				Arguments.of("sesion", "modalidad", "PRESENCIAL", "ck_sesion_modalidad"),
				Arguments.of("sesion", "tono", "AMABLE", "ck_sesion_tono"),
				Arguments.of("sesion", "forma_respuesta", "VIDEO", "ck_sesion_forma_respuesta"),
				Arguments.of("sesion", "idioma", "es", "ck_sesion_idioma"),
				Arguments.of("intento_respuesta", "tipo_interaccion", "FOLLOW_UP",
						"ck_intento_respuesta_tipo_interaccion"),
				Arguments.of("intento_respuesta", "origen_respuesta", "VOZ", "ck_intento_respuesta_origen_respuesta"),
				Arguments.of("ejecucion_ia", "tipo_operacion", "RESUMIR", "ck_ejecucion_ia_tipo_operacion"),
				Arguments.of("ejecucion_ia", "estado", "CANCELADA", "ck_ejecucion_ia_estado"));
	}

	@ParameterizedTest(name = "{0}.{1} = ''{2}''")
	@MethodSource("closedValues")
	@DisplayName("Una columna de valores cerrados rechaza un valor fuera de su lista")
	void shouldRejectUnknownValue_whenCheckedColumn(String table, String column, String value, String constraint) {
		Map<String, Object> row = validRowFor(table);
		row.put(column, value);

		assertConstraint(() -> insert(table, row), constraint);
	}

	@Test
	@DisplayName("La modalidad ENTRENO es válida")
	void session_shouldAcceptTrainingModality_whenChecked() {
		Map<String, Object> row = sessionRow("uid-a", "CONFIGURADA", null);
		row.put("modalidad", "ENTRENO");

		assertThatCode(() -> insert("sesion", row)).doesNotThrowAnyException();
	}

	static Stream<Arguments> notNullColumns() {
		return Stream.of(
				Arguments.of("tipo_pregunta", "aplica_star"),
				Arguments.of("sesion", "firebase_uid"),
				Arguments.of("pregunta", "texto_original"),
				Arguments.of("turno", "pregunta_id"),
				Arguments.of("intento_respuesta", "respuesta_texto"),
				Arguments.of("feedback_turno", "feedback"),
				Arguments.of("ejecucion_ia", "modelo"),
				Arguments.of("clave_idempotencia", "clave"));
	}

	@ParameterizedTest(name = "{0}.{1}")
	@MethodSource("notNullColumns")
	@DisplayName("Una columna obligatoria rechaza el valor nulo")
	void notNull_shouldReject_whenColumnMissing(String table, String column) {
		Map<String, Object> row = validRowFor(table);
		row.put(column, null);

		assertThatThrownBy(() -> insert(table, row))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("null value in column \"" + column + "\"");
	}

	// ------------------------------------------------------------------ sesión

	@Test
	@DisplayName("Un usuario no puede tener dos sesiones configuradas")
	void session_shouldRejectSecondConfigured_whenSameUser() {
		insertSession("uid-a", "CONFIGURADA", null);

		assertConstraint(() -> insertSession("uid-a", "CONFIGURADA", null), "uq_sesion_configurada_por_usuario");
	}

	@Test
	@DisplayName("Un usuario no puede tener dos sesiones en curso")
	void session_shouldRejectSecondInProgress_whenSameUser() {
		insertSession("uid-a", "EN_CURSO", null);

		assertConstraint(() -> insertSession("uid-a", "EN_CURSO", null), "uq_sesion_en_curso_por_usuario");
	}

	@Test
	@DisplayName("Un usuario puede tener una configurada y una en curso")
	void session_shouldAllowConfiguredAndInProgress_whenSameUser() {
		assertThatCode(() -> {
			insertSession("uid-a", "CONFIGURADA", null);
			insertSession("uid-a", "EN_CURSO", null);
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Un usuario puede tener muchas sesiones finalizadas")
	void session_shouldAllowManyFinished_whenSameUser() {
		assertThatCode(() -> {
			insertSession("uid-a", "FINALIZADA", Instant.now());
			insertSession("uid-a", "FINALIZADA", Instant.now());
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Dos usuarios pueden tener cada uno una sesión configurada")
	void session_shouldAllowSameStateForDifferentUsers() {
		assertThatCode(() -> {
			insertSession("uid-a", "CONFIGURADA", null);
			insertSession("uid-b", "CONFIGURADA", null);
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Una sesión abandonada exige fecha de fin")
	void session_shouldRejectFinishedWithoutEndDate() {
		assertConstraint(() -> insertSession("uid-a", "ABANDONADA", null), "ck_sesion_fecha_fin");
	}

	@Test
	@DisplayName("Una sesión en curso no puede tener fecha de fin")
	void session_shouldRejectInProgressWithEndDate() {
		assertConstraint(() -> insertSession("uid-a", "EN_CURSO", Instant.now()), "ck_sesion_fecha_fin");
	}

	@Test
	@DisplayName("La oferta laboral acepta 3000 caracteres")
	void session_shouldAcceptOffer3000() {
		Map<String, Object> row = sessionRow("uid-a", "CONFIGURADA", null);
		row.put("oferta_laboral", "a".repeat(3000));

		assertThatCode(() -> insert("sesion", row)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("La oferta laboral rechaza 3001 caracteres")
	void session_shouldRejectOffer3001() {
		Map<String, Object> row = sessionRow("uid-a", "CONFIGURADA", null);
		row.put("oferta_laboral", "a".repeat(3001));

		assertRejected(() -> insert("sesion", row));
	}

	@Test
	@DisplayName("La oferta laboral rechaza un texto de solo espacios")
	void session_shouldRejectBlankOffer() {
		Map<String, Object> row = sessionRow("uid-a", "CONFIGURADA", null);
		row.put("oferta_laboral", "   ");

		assertConstraint(() -> insert("sesion", row), "ck_sesion_oferta_laboral");
	}

	@ParameterizedTest(name = "reintentos = {0}")
	@ValueSource(ints = { -1, 3 })
	@DisplayName("Los reintentos de planificación rechazan valores fuera de 0 a 2")
	void session_shouldRejectPlanningRetries_whenOutOfRange(int retries) {
		Map<String, Object> row = sessionRow("uid-a", "CONFIGURADA", null);
		row.put("reintentos_planificacion", retries);

		assertConstraint(() -> insert("sesion", row), "ck_sesion_reintentos_planificacion");
	}

	@ParameterizedTest(name = "reintentos = {0}")
	@ValueSource(ints = { 0, 2 })
	@DisplayName("Los reintentos de planificación aceptan los límites 0 y 2")
	void session_shouldAcceptPlanningRetries_whenAtLimits(int retries) {
		Map<String, Object> row = sessionRow("uid-a", "CONFIGURADA", null);
		row.put("reintentos_planificacion", retries);

		assertThatCode(() -> insert("sesion", row)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Borrar una sesión borra sus preguntas, turnos, intentos, evaluaciones y ejecuciones")
	void session_shouldCascadeDelete_whenConfigured() {
		UUID session = insertSession("uid-a", "EN_CURSO", null);
		UUID question = insertQuestion(session, 1);
		UUID turn = insertTurn(question);
		insertAnswer(turn, true, "TEXTO", null);
		insertFeedback(turn, evaluationExecution(session, turn));

		jdbc.update("DELETE FROM microentrevista.sesion WHERE id = ?", session);

		for (String table : List.of("pregunta", "turno", "intento_respuesta", "feedback_turno", "ejecucion_ia")) {
			assertThat(count(table)).as(table).isZero();
		}
	}

	// ------------------------------------------------------------------ pregunta

	@Test
	@DisplayName("El texto de la pregunta acepta 1000 puntos de código, también emoji")
	void question_shouldAccept1000CodePoints() {
		UUID session = insertSession("uid-a", "CONFIGURADA", null);

		for (String text : List.of("😀".repeat(1000), "é".repeat(1000))) {
			Map<String, Object> row = questionRow(session, nextOrder(session), planExecution(session));
			row.put("texto_original", text);
			assertThatCode(() -> insert("pregunta", row)).doesNotThrowAnyException();
		}
	}

	@Test
	@DisplayName("El texto de la pregunta rechaza 1001 puntos de código")
	void question_shouldReject1001CodePoints() {
		UUID session = insertSession("uid-a", "CONFIGURADA", null);
		Map<String, Object> row = questionRow(session, 1, planExecution(session));
		row.put("texto_original", "é".repeat(1001));

		assertRejected(() -> insert("pregunta", row));
	}

	@ParameterizedTest(name = "orden = {0}")
	@ValueSource(ints = { 0, 6 })
	@DisplayName("El orden de la pregunta rechaza valores fuera de 1 a 5")
	void question_shouldRejectOrder_whenOutOfRange(int order) {
		UUID session = insertSession("uid-a", "CONFIGURADA", null);

		assertConstraint(() -> insert("pregunta", questionRow(session, order, planExecution(session))),
				"ck_pregunta_orden");
	}

	@Test
	@DisplayName("Dos preguntas de la misma sesión no pueden tener el mismo orden")
	void question_shouldRejectDuplicateOrder_whenSameSession() {
		UUID session = insertSession("uid-a", "CONFIGURADA", null);
		insertQuestion(session, 1);

		assertConstraint(() -> insertQuestion(session, 1), "uq_pregunta_sesion_orden");
	}

	@Test
	@DisplayName("No se cambia el texto de una pregunta con la sesión iniciada")
	void question_shouldRejectTextChange_whenSessionStarted() {
		UUID question = insertQuestion(insertSession("uid-a", "EN_CURSO", null), 1);

		assertTrigger(() -> jdbc.update("UPDATE microentrevista.pregunta SET texto_original = 'otra' WHERE id = ?",
				question), "pregunta inmutable");
	}

	@Test
	@DisplayName("No se cambia el tipo de una pregunta con la sesión iniciada")
	void question_shouldRejectTypeChange_whenSessionStarted() {
		UUID question = insertQuestion(insertSession("uid-a", "EN_CURSO", null), 1);

		assertTrigger(() -> jdbc.update("UPDATE microentrevista.pregunta SET tipo = 'TECNICA' WHERE id = ?",
				question), "pregunta inmutable");
	}

	@Test
	@DisplayName("No se cambia el orden de una pregunta con la sesión iniciada")
	void question_shouldRejectOrderChange_whenSessionStarted() {
		UUID question = insertQuestion(insertSession("uid-a", "EN_CURSO", null), 1);

		assertTrigger(() -> jdbc.update("UPDATE microentrevista.pregunta SET orden = 2 WHERE id = ?", question),
				"pregunta inmutable");
	}

	@Test
	@DisplayName("No se cambia una pregunta de una sesión finalizada")
	void question_shouldRejectChange_whenSessionFinished() {
		UUID question = insertQuestion(insertSession("uid-a", "FINALIZADA", Instant.now()), 1);

		assertTrigger(() -> jdbc.update("UPDATE microentrevista.pregunta SET texto_original = 'otra' WHERE id = ?",
				question), "pregunta inmutable");
	}

	@Test
	@DisplayName("Se puede cambiar una pregunta mientras la sesión está configurada")
	void question_shouldAllowChange_whenSessionConfigured() {
		UUID question = insertQuestion(insertSession("uid-a", "CONFIGURADA", null), 1);

		int updated = jdbc.update("UPDATE microentrevista.pregunta SET texto_original = 'otra' WHERE id = ?", question);

		assertThat(updated).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT texto_original FROM microentrevista.pregunta WHERE id = ?",
				String.class, question)).isEqualTo("otra");
	}

	@Test
	@DisplayName("No se borra una pregunta de una sesión iniciada")
	void question_shouldRejectDelete_whenSessionStarted() {
		UUID question = insertQuestion(insertSession("uid-a", "EN_CURSO", null), 1);

		assertTrigger(() -> jdbc.update("DELETE FROM microentrevista.pregunta WHERE id = ?", question),
				"pregunta inmutable");
	}

	@Test
	@DisplayName("Borrar la sesión completa sigue permitido aunque tenga preguntas fijas")
	void question_shouldAllowCascadeDelete_whenSessionDeleted() {
		UUID session = insertSession("uid-a", "FINALIZADA", Instant.now());
		insertQuestion(session, 1);
		insertQuestion(session, 2);

		jdbc.update("DELETE FROM microentrevista.sesion WHERE id = ?", session);

		assertThat(count("pregunta")).isZero();
	}

	@Test
	@DisplayName("Una pregunta exige la ejecución de la IA que la produjo: nula o inexistente se rechaza")
	void question_shouldRejectMissingExecution() {
		UUID session = insertSession("uid-a", "CONFIGURADA", null);
		Map<String, Object> withoutExecution = questionRow(session, 1, null);
		Map<String, Object> unknownExecution = questionRow(session, 2, UUID.randomUUID());

		assertThatThrownBy(() -> insert("pregunta", withoutExecution))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("null value in column \"ejecucion_ia_id\"");
		assertConstraint(() -> insert("pregunta", unknownExecution), "fk_pregunta_ejecucion_ia");
	}

	// ------------------------------------------------------------------ intento de respuesta

	@Test
	@DisplayName("La respuesta acepta 4000 caracteres")
	void answer_shouldAccept4000() {
		Map<String, Object> row = validRowFor("intento_respuesta");
		row.put("respuesta_texto", "a".repeat(4000));

		assertThatCode(() -> insert("intento_respuesta", row)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("La respuesta rechaza 4001 caracteres")
	void answer_shouldReject4001() {
		Map<String, Object> row = validRowFor("intento_respuesta");
		row.put("respuesta_texto", "a".repeat(4001));

		assertRejected(() -> insert("intento_respuesta", row));
	}

	@Test
	@DisplayName("La respuesta rechaza un texto de solo espacios")
	void answer_shouldRejectBlank() {
		Map<String, Object> row = validRowFor("intento_respuesta");
		row.put("respuesta_texto", "   ");

		assertConstraint(() -> insert("intento_respuesta", row), "ck_intento_respuesta_respuesta_texto");
	}

	@Test
	@DisplayName("Una respuesta por audio guarda el idioma detectado")
	void answer_shouldAcceptAudioWithDetectedLanguage() {
		UUID turn = newTurn();

		assertThatCode(() -> insertAnswer(turn, false, "AUDIO", "en")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Una respuesta por texto no lleva idioma detectado")
	void answer_shouldRejectDetectedLanguage_whenTextOrigin() {
		UUID turn = newTurn();

		assertConstraint(() -> insertAnswer(turn, false, "TEXTO", "en"), "ck_intento_respuesta_idioma_origen");
	}

	@ParameterizedTest(name = "idioma detectado = ''{0}''")
	@ValueSource(strings = { "e", "abcdefghijk", "          " })
	@DisplayName("El idioma detectado rechaza menos de 2 o más de 10 caracteres y el texto en blanco")
	void answer_shouldRejectDetectedLanguage_whenTooShortOrLong(String language) {
		UUID turn = newTurn();

		assertRejected(() -> insertAnswer(turn, false, "AUDIO", language));
	}

	@Test
	@DisplayName("Un turno no puede tener dos intentos definitivos")
	void answer_shouldRejectSecondDefinitive_whenSameTurn() {
		UUID turn = newTurn();
		insertAnswer(turn, true, "TEXTO", null);

		assertConstraint(() -> insertAnswer(turn, true, "TEXTO", null), "uq_intento_definitivo_por_turno");
	}

	@Test
	@DisplayName("Un turno puede tener un intento definitivo y otros que no lo son")
	void answer_shouldAllowNonDefinitive_whenSameTurn() {
		UUID turn = newTurn();

		assertThatCode(() -> {
			insertAnswer(turn, true, "TEXTO", null);
			insertAnswer(turn, false, "TEXTO", null);
		}).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Un intento de respuesta nunca se actualiza")
	void answer_shouldRejectUpdate_always() {
		UUID turn = newTurn();
		insertAnswer(turn, true, "TEXTO", null);

		assertTrigger(() -> jdbc.update("UPDATE microentrevista.intento_respuesta SET respuesta_texto = 'x'"),
				"intento de respuesta de solo insercion");
	}

	// ------------------------------------------------------------------ retroalimentación

	@Test
	@DisplayName("Un turno tiene a lo sumo una retroalimentación")
	void feedback_shouldRejectSecond_whenSameTurn() {
		UUID session = insertSession("uid-a", "EN_CURSO", null);
		UUID turn = insertTurn(insertQuestion(session, 1));
		insertFeedback(turn, evaluationExecution(session, turn));

		assertConstraint(() -> insertFeedback(turn, evaluationExecution(session, turn)), "uq_feedback_turno_turno");
	}

	@Test
	@DisplayName("La retroalimentación debe ser un objeto JSON")
	void feedback_shouldRejectArray_whenNotObject() {
		Map<String, Object> row = validRowFor("feedback_turno");
		row.put("feedback", "[]");

		assertConstraint(() -> insert("feedback_turno", row), "ck_feedback_turno_feedback");
	}

	@Test
	@DisplayName("La retroalimentación STAR puede ser nula")
	void feedback_shouldAcceptNullStar() {
		Map<String, Object> row = validRowFor("feedback_turno");
		row.put("feedback_star", null);

		assertThatCode(() -> insert("feedback_turno", row)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("La retroalimentación exige la ejecución de la IA que la produjo")
	void feedback_shouldRejectMissingExecution() {
		Map<String, Object> withoutExecution = validRowFor("feedback_turno");
		withoutExecution.put("ejecucion_ia_id", null);
		Map<String, Object> unknownExecution = validRowFor("feedback_turno");
		unknownExecution.put("ejecucion_ia_id", UUID.randomUUID());

		assertThatThrownBy(() -> insert("feedback_turno", withoutExecution))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("null value in column \"ejecucion_ia_id\"");
		assertConstraint(() -> insert("feedback_turno", unknownExecution), "fk_feedback_turno_ejecucion_ia");
	}

	// ------------------------------------------------------------------ ejecución de la IA

	@Test
	@DisplayName("Una transcripción en curso, con turno, sin fecha de fin y sin versión de prompt, es válida")
	void execution_shouldAcceptTranscribeOperation() {
		UUID session = insertSession("uid-a", "EN_CURSO", null);
		UUID turn = insertTurn(insertQuestion(session, 1));

		assertThatCode(() -> insert("ejecucion_ia", executionRow(session, turn, "TRANSCRIBIR", "EN_CURSO")))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Planificar no lleva turno")
	void execution_shouldRejectPlanWithTurn() {
		UUID session = insertSession("uid-a", "EN_CURSO", null);
		UUID turn = insertTurn(insertQuestion(session, 1));

		assertConstraint(() -> insert("ejecucion_ia", executionRow(session, turn, "PLANIFICAR", "EXITOSA")),
				"ck_ejecucion_ia_turno");
	}

	@Test
	@DisplayName("Evaluar exige turno")
	void execution_shouldRejectEvaluateWithoutTurn() {
		UUID session = insertSession("uid-a", "EN_CURSO", null);

		assertConstraint(() -> insert("ejecucion_ia", executionRow(session, null, "EVALUAR", "EXITOSA")),
				"ck_ejecucion_ia_turno");
	}

	@ParameterizedTest(name = "numero_intento = {0}")
	@ValueSource(ints = { 0, 4 })
	@DisplayName("El número de intento rechaza valores fuera de 1 a 3")
	void execution_shouldRejectAttempt_whenOutOfRange(int attempt) {
		Map<String, Object> row = validRowFor("ejecucion_ia");
		row.put("numero_intento", attempt);

		assertConstraint(() -> insert("ejecucion_ia", row), "ck_ejecucion_ia_numero_intento");
	}

	@Test
	@DisplayName("Una ejecución exitosa exige fecha de fin")
	void execution_shouldRejectFinishedWithoutEndDate() {
		Map<String, Object> row = validRowFor("ejecucion_ia");
		row.put("fecha_fin", null);

		assertConstraint(() -> insert("ejecucion_ia", row), "ck_ejecucion_ia_fecha_fin");
	}

	@Test
	@DisplayName("La clave de idempotencia de la ejecución no se repite")
	void execution_shouldRejectDuplicateIdempotencyKey() {
		UUID session = insertSession("uid-a", "EN_CURSO", null);
		Map<String, Object> first = executionRow(session, null, "PLANIFICAR", "EXITOSA");
		Map<String, Object> second = executionRow(session, null, "PLANIFICAR", "EXITOSA");
		second.put("idempotency_key", first.get("idempotency_key"));
		insert("ejecucion_ia", first);

		assertConstraint(() -> insert("ejecucion_ia", second), "uq_ejecucion_ia_idempotency_key");
	}

	@Test
	@DisplayName("El modelo no puede ser un texto de solo espacios")
	void execution_shouldRequireModel() {
		Map<String, Object> row = validRowFor("ejecucion_ia");
		row.put("modelo", "   ");

		assertConstraint(() -> insert("ejecucion_ia", row), "ck_ejecucion_ia_modelo");
	}

	@Test
	@DisplayName("Una ejecución fallida lleva código de error y una exitosa no")
	void execution_shouldRequireErrorCode_whenFailed() {
		UUID session = insertSession("uid-a", "EN_CURSO", null);
		Map<String, Object> failedWithoutCode = executionRow(session, null, "PLANIFICAR", "FALLIDA");
		failedWithoutCode.put("error_codigo", null);
		Map<String, Object> succeededWithCode = executionRow(session, null, "PLANIFICAR", "EXITOSA");
		succeededWithCode.put("error_codigo", "LLM_UNAVAILABLE");

		assertConstraint(() -> insert("ejecucion_ia", failedWithoutCode), "ck_ejecucion_ia_error_codigo");
		assertConstraint(() -> insert("ejecucion_ia", succeededWithCode), "ck_ejecucion_ia_error_codigo");
	}

	@ParameterizedTest(name = "versión de prompt = ''{0}''")
	@ValueSource(strings = { "", "   " })
	@DisplayName("La versión de prompt no puede estar vacía ni en blanco")
	void execution_shouldRejectBlankPromptVersion(String version) {
		UUID session = insertSession("uid-a", "EN_CURSO", null);
		Map<String, Object> row = executionRow(session, null, "PLANIFICAR", "EXITOSA");
		row.put("version_prompt", version);

		assertConstraint(() -> insert("ejecucion_ia", row), "ck_ejecucion_ia_version_prompt");
	}

	@ParameterizedTest(name = "código de error = ''{0}''")
	@ValueSource(strings = { "", "   " })
	@DisplayName("El código de error de una ejecución fallida no puede estar vacío ni en blanco")
	void execution_shouldRejectBlankErrorCode(String errorCode) {
		UUID session = insertSession("uid-a", "EN_CURSO", null);
		Map<String, Object> row = executionRow(session, null, "PLANIFICAR", "FALLIDA");
		row.put("error_codigo", errorCode);

		assertConstraint(() -> insert("ejecucion_ia", row), "ck_ejecucion_ia_error_codigo");
	}

	@Test
	@DisplayName("La transcripción no lleva versión de prompt y las demás operaciones sí")
	void execution_shouldRejectPromptVersion_whenTranscribe() {
		UUID session = insertSession("uid-a", "EN_CURSO", null);
		UUID turn = insertTurn(insertQuestion(session, 1));
		Map<String, Object> transcribeWithPrompt = executionRow(session, turn, "TRANSCRIBIR", "EXITOSA");
		transcribeWithPrompt.put("version_prompt", "v1");
		Map<String, Object> planWithoutPrompt = executionRow(session, null, "PLANIFICAR", "EXITOSA");
		planWithoutPrompt.put("version_prompt", null);

		assertConstraint(() -> insert("ejecucion_ia", transcribeWithPrompt), "ck_ejecucion_ia_version_prompt");
		assertConstraint(() -> insert("ejecucion_ia", planWithoutPrompt), "ck_ejecucion_ia_version_prompt");
	}

	@Test
	@DisplayName("Los tokens no pueden ser negativos ni el proveedor estar en blanco; nulo, cero y un valor sí")
	void execution_shouldRejectNegativeTokens() {
		Map<String, Object> negativeInput = validRowFor("ejecucion_ia");
		negativeInput.put("tokens_entrada", -1L);
		Map<String, Object> negativeOutput = validRowFor("ejecucion_ia");
		negativeOutput.put("tokens_salida", -1L);
		Map<String, Object> blankProvider = validRowFor("ejecucion_ia");
		blankProvider.put("proveedor", "  ");
		Map<String, Object> nullValues = validRowFor("ejecucion_ia");
		nullValues.put("proveedor", null);
		nullValues.put("tokens_entrada", null);
		nullValues.put("tokens_salida", null);
		Map<String, Object> zeroValues = validRowFor("ejecucion_ia");
		zeroValues.put("proveedor", "google-genai");
		zeroValues.put("tokens_entrada", 0L);
		zeroValues.put("tokens_salida", 0L);

		assertConstraint(() -> insert("ejecucion_ia", negativeInput), "ck_ejecucion_ia_tokens_entrada");
		assertConstraint(() -> insert("ejecucion_ia", negativeOutput), "ck_ejecucion_ia_tokens_salida");
		assertConstraint(() -> insert("ejecucion_ia", blankProvider), "ck_ejecucion_ia_proveedor");
		assertThatCode(() -> insert("ejecucion_ia", nullValues)).doesNotThrowAnyException();
		assertThatCode(() -> insert("ejecucion_ia", zeroValues)).doesNotThrowAnyException();
	}

	// ------------------------------------------------------------------ clave de idempotencia

	@Test
	@DisplayName("La misma clave para el mismo usuario y operación se rechaza")
	void idempotency_shouldRejectSameUserOperationKey() {
		insert("clave_idempotencia", keyRow("uid-a", "CREAR_SESION", "k-1"));

		assertConstraint(() -> insert("clave_idempotencia", keyRow("uid-a", "CREAR_SESION", "k-1")),
				"uq_clave_idempotencia_usuario_operacion_clave");
	}

	@Test
	@DisplayName("Otro usuario puede usar la misma clave")
	void idempotency_shouldAllowOtherUser() {
		insert("clave_idempotencia", keyRow("uid-a", "CREAR_SESION", "k-1"));

		assertThatCode(() -> insert("clave_idempotencia", keyRow("uid-b", "CREAR_SESION", "k-1")))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("La clave con espacios y la huella que no es SHA-256 hexadecimal se rechazan")
	void idempotency_shouldRejectInvalidKeyOrFingerprint() {
		Map<String, Object> spacedKey = keyRow("uid-a", "CREAR_SESION", "a b");
		Map<String, Object> longKey = keyRow("uid-a", "CREAR_SESION", "a".repeat(65));
		Map<String, Object> badFingerprint = keyRow("uid-a", "CREAR_SESION", "k-1");
		badFingerprint.put("huella_peticion", "XYZ");

		assertConstraint(() -> insert("clave_idempotencia", spacedKey), "ck_clave_idempotencia_clave");
		assertRejected(() -> insert("clave_idempotencia", longKey));
		assertConstraint(() -> insert("clave_idempotencia", badFingerprint),
				"ck_clave_idempotencia_huella_peticion");
	}

	// ------------------------------------------------------------------ ayudantes de aserción

	private void assertConstraint(ThrowingCallable operation, String constraint) {
		assertThatThrownBy(operation)
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining(constraint);
	}

	private void assertRejected(ThrowingCallable operation) {
		assertThatThrownBy(operation).isInstanceOf(DataIntegrityViolationException.class);
	}

	private void assertTrigger(ThrowingCallable operation, String message) {
		assertThatThrownBy(operation).isInstanceOf(DataAccessException.class).hasMessageContaining(message);
	}

	// ------------------------------------------------------------------ ayudantes de datos

	private static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

	private void insert(String table, Map<String, Object> row) {
		String columns = String.join(", ", row.keySet());
		String placeholders = row.keySet().stream()
				.map(column -> JSONB_COLUMNS.contains(column) ? "CAST(? AS jsonb)" : "?")
				.collect(Collectors.joining(", "));
		jdbc.update("INSERT INTO microentrevista." + table + " (" + columns + ") VALUES (" + placeholders + ")",
				row.values().toArray());
	}

	private Map<String, Object> sessionRow(String uid, String status, Instant endDate) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.randomUUID());
		row.put("firebase_uid", uid);
		row.put("perfil_id", UUID.randomUUID());
		row.put("rol_objetivo_id", UUID.randomUUID());
		row.put("oferta_laboral", null);
		row.put("contexto_perfil_snapshot", "{}");
		row.put("modalidad", "SIMULACION");
		row.put("tono", "CALIDO");
		row.put("forma_respuesta", "TEXTO");
		row.put("idioma", "es-CO");
		row.put("estado", status);
		row.put("fecha_creacion", now());
		row.put("fecha_actualizacion", now());
		row.put("fecha_fin", endDate == null ? null : OffsetDateTime.ofInstant(endDate, ZoneOffset.UTC));
		return row;
	}

	private UUID insertSession(String uid, String status, Instant endDate) {
		Map<String, Object> row = sessionRow(uid, status, endDate);
		insert("sesion", row);
		return (UUID) row.get("id");
	}

	private Map<String, Object> executionRow(UUID sessionId, UUID turnId, String operation, String status) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.randomUUID());
		row.put("sesion_id", sessionId);
		row.put("turno_id", turnId);
		row.put("tipo_operacion", operation);
		row.put("numero_intento", 1);
		row.put("idempotency_key", UUID.randomUUID());
		row.put("estado", status);
		row.put("modelo", "modelo-de-prueba");
		row.put("version_prompt", "TRANSCRIBIR".equals(operation) ? null : "v1");
		row.put("error_codigo", "FALLIDA".equals(status) ? "LLM_UNAVAILABLE" : null);
		row.put("fecha_inicio", now());
		row.put("fecha_fin", "EN_CURSO".equals(status) ? null : now());
		return row;
	}

	/** Inserta la ejecución de planificación (sin turno) que produce las preguntas de una sesión. */
	private UUID planExecution(UUID sessionId) {
		Map<String, Object> row = executionRow(sessionId, null, "PLANIFICAR", "EXITOSA");
		insert("ejecucion_ia", row);
		return (UUID) row.get("id");
	}

	private UUID evaluationExecution(UUID sessionId, UUID turnId) {
		Map<String, Object> row = executionRow(sessionId, turnId, "EVALUAR", "EXITOSA");
		insert("ejecucion_ia", row);
		return (UUID) row.get("id");
	}

	private Map<String, Object> questionRow(UUID sessionId, int order, UUID executionId) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.randomUUID());
		row.put("sesion_id", sessionId);
		row.put("orden", order);
		row.put("tipo", "CONDUCTUAL");
		row.put("texto_original", "Cuéntame de un reto que resolviste en equipo.");
		row.put("ejecucion_ia_id", executionId);
		row.put("fecha_creacion", now());
		return row;
	}

	private UUID insertQuestion(UUID sessionId, int order) {
		Map<String, Object> row = questionRow(sessionId, order, planExecution(sessionId));
		insert("pregunta", row);
		return (UUID) row.get("id");
	}

	private int nextOrder(UUID sessionId) {
		return jdbc.queryForObject("SELECT count(*) + 1 FROM microentrevista.pregunta WHERE sesion_id = ?",
				Integer.class, sessionId);
	}

	private Map<String, Object> turnRow(UUID questionId) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.randomUUID());
		row.put("pregunta_id", questionId);
		row.put("fecha_creacion", now());
		row.put("fecha_actualizacion", now());
		return row;
	}

	private UUID insertTurn(UUID questionId) {
		Map<String, Object> row = turnRow(questionId);
		insert("turno", row);
		return (UUID) row.get("id");
	}

	/** Crea una sesión en curso con una pregunta y su turno. */
	private UUID newTurn() {
		return insertTurn(insertQuestion(insertSession("uid-" + UUID.randomUUID(), "EN_CURSO", null), 1));
	}

	private Map<String, Object> answerRow(UUID turnId, boolean definitive, String origin, String language) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.randomUUID());
		row.put("turno_id", turnId);
		row.put("tipo_interaccion", "INICIAL");
		row.put("es_definitiva", definitive);
		row.put("origen_respuesta", origin);
		row.put("respuesta_texto", "Respuesta de prueba.");
		row.put("pregunta_mostrada", "Pregunta de prueba.");
		row.put("idioma_detectado", language);
		row.put("fecha_creacion", now());
		return row;
	}

	private void insertAnswer(UUID turnId, boolean definitive, String origin, String language) {
		insert("intento_respuesta", answerRow(turnId, definitive, origin, language));
	}

	private Map<String, Object> feedbackRow(UUID turnId, UUID executionId) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.randomUUID());
		row.put("turno_id", turnId);
		row.put("feedback", "{}");
		row.put("feedback_star", null);
		row.put("ejecucion_ia_id", executionId);
		row.put("fecha_creacion", now());
		return row;
	}

	private void insertFeedback(UUID turnId, UUID executionId) {
		insert("feedback_turno", feedbackRow(turnId, executionId));
	}

	private Map<String, Object> keyRow(String uid, String operation, String key) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.randomUUID());
		row.put("firebase_uid", uid);
		row.put("operacion", operation);
		row.put("clave", key);
		row.put("huella_peticion", "a".repeat(64));
		row.put("estado_respuesta", 201);
		row.put("cuerpo_respuesta", "{}");
		row.put("fecha_creacion", now());
		return row;
	}

	/** Fila válida de la tabla, con sus filas padre ya insertadas. */
	private Map<String, Object> validRowFor(String table) {
		return switch (table) {
			case "tipo_pregunta" -> new LinkedHashMap<>(Map.of("codigo", "APERTURA", "aplica_star", false));
			case "sesion" -> sessionRow("uid-" + UUID.randomUUID(), "CONFIGURADA", null);
			case "pregunta" -> {
				UUID session = insertSession("uid-" + UUID.randomUUID(), "CONFIGURADA", null);
				yield questionRow(session, 1, planExecution(session));
			}
			case "turno" -> {
				UUID session = insertSession("uid-" + UUID.randomUUID(), "CONFIGURADA", null);
				yield turnRow(insertQuestion(session, 1));
			}
			case "intento_respuesta" -> answerRow(newTurn(), false, "TEXTO", null);
			case "feedback_turno" -> {
				UUID session = insertSession("uid-" + UUID.randomUUID(), "EN_CURSO", null);
				UUID turn = insertTurn(insertQuestion(session, 1));
				yield feedbackRow(turn, evaluationExecution(session, turn));
			}
			case "ejecucion_ia" ->
				executionRow(insertSession("uid-" + UUID.randomUUID(), "EN_CURSO", null), null, "PLANIFICAR",
						"EXITOSA");
			case "clave_idempotencia" -> keyRow("uid-a", "CREAR_SESION", "k-1");
			default -> throw new IllegalArgumentException("Tabla sin fila de prueba: " + table);
		};
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM microentrevista." + table, Integer.class);
	}

	private String tableComment(String table) {
		return jdbc.queryForObject("SELECT obj_description(CAST(? AS regclass), 'pg_class')", String.class,
				SCHEMA + "." + table);
	}

	private String columnComment(String table, String column) {
		return jdbc.queryForObject("""
				SELECT col_description(a.attrelid, a.attnum) FROM pg_attribute a
				 WHERE a.attrelid = CAST(? AS regclass) AND a.attname = ?
				""", String.class, SCHEMA + "." + table, column);
	}

}
