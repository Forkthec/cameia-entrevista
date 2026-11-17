package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.TranscriptionExecution;
import tech.cameia.entrevista.domain.model.VoiceSample;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.TranscriptionExecutionRepository;

/**
 * Esquema y adaptadores de la respuesta por voz (CM-349) sobre PostgreSQL real: la respuesta transcrita en
 * {@code intento_respuesta} con origen {@code AUDIO} (CA-5.12.1), la ejecución {@code TRANSCRIBIR} del turno
 * (CA-5.12.3) y la operación anti-duplicados {@code RESPONDER_AUDIO} de la migración V8.
 */
class VoiceAnswerPersistenceTest extends AbstractPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private InterviewSessionRepository sessions;

    @Autowired
    private AnswerAttemptRepository attempts;

    @Autowired
    private TranscriptionExecutionRepository executions;

    private UUID sessionId() {
        InterviewSession session = sessions.save(newSession("uid-voz"));
        return session.id();
    }

    @Test
    void save_shouldStoreAudioAnswerWithDetectedLanguage() {
        UUID session = sessionId();
        UUID turn = insertTurn(session);

        attempts.save(new AnswerAttempt(UUID.randomUUID(), turn, AnswerAttempt.AnswerOrigin.AUDIO, "a".repeat(4000),
                "Cuéntame sobre ti", "es", Instant.parse("2026-11-11T15:00:00Z")));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT origen_respuesta, idioma_detectado, es_definitiva, tipo_interaccion, char_length(respuesta_texto)"
                        + " AS largo FROM microentrevista.intento_respuesta WHERE turno_id = ?", turn);
        assertThat(row).containsEntry("origen_respuesta", "AUDIO").containsEntry("idioma_detectado", "es")
                .containsEntry("es_definitiva", true).containsEntry("tipo_interaccion", "INICIAL")
                .containsEntry("largo", 4000);
        assertThat(sessions.countDefinitiveAnswers(session)).isEqualTo(1);
    }

    @Test
    void save_shouldRejectSecondDefinitiveAnswerOfTheSameTurn() {
        UUID turn = insertTurn(sessionId());
        attempts.save(new AnswerAttempt(UUID.randomUUID(), turn, AnswerAttempt.AnswerOrigin.AUDIO, "uno", "P", "es",
                Instant.now()));

        assertThatThrownBy(() -> attempts.save(new AnswerAttempt(UUID.randomUUID(), turn,
                AnswerAttempt.AnswerOrigin.AUDIO, "dos", "P", "es", Instant.now())))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.AnswerAlreadyExistsException.class);
    }

    @Test
    void save_shouldStoreTranscriptionExecutionLinkedToTheTurn() {
        UUID session = sessionId();
        UUID turn = insertTurn(session);
        Instant start = Instant.parse("2026-11-11T15:00:00Z");

        executions.save(TranscriptionExecution.succeeded(session, turn, "universal-3-5-pro,universal-2",
                "assemblyai", start, start.plusSeconds(4)));
        executions.save(TranscriptionExecution.failed(session, turn, "universal-3-5-pro,universal-2", "assemblyai",
                ErrorCode.TRANSCRIPTION_UNAVAILABLE, start, start.plusSeconds(90)));

        assertThat(jdbc.queryForList("""
                SELECT estado, numero_intento, proveedor, version_prompt, error_codigo
                  FROM microentrevista.ejecucion_ia WHERE turno_id = ? AND tipo_operacion = 'TRANSCRIBIR'
                 ORDER BY fecha_fin
                """, turn))
                .extracting(row -> row.get("estado") + "/" + row.get("numero_intento") + "/" + row.get("proveedor")
                        + "/" + row.get("version_prompt") + "/" + row.get("error_codigo"))
                .containsExactly("EXITOSA/1/assemblyai/null/null",
                        "FALLIDA/1/assemblyai/null/TRANSCRIPTION_UNAVAILABLE");
    }

    @Test
    void save_shouldStoreTheFourVoiceNumbers() {
        UUID turn = insertTurn(sessionId());

        attempts.save(AnswerAttempt.spoken(turn, "Respuesta hablada", "Cuéntame sobre ti", "es",
                new VoiceSample(65, 36310, 15734, 6), CLOCK));

        assertThat(jdbc.queryForMap("SELECT voz_palabras, voz_tramo_ms, voz_pausas_ms, voz_muletillas FROM "
                + "microentrevista.intento_respuesta WHERE turno_id = ?", turn))
                .containsEntry("voz_palabras", 65).containsEntry("voz_tramo_ms", 36310)
                .containsEntry("voz_pausas_ms", 15734).containsEntry("voz_muletillas", 6);
        assertThat(attempts.findDefinitive(turn)).get().extracting(AnswerAttempt::voice)
                .isEqualTo(new VoiceSample(65, 36310, 15734, 6));
    }

    @Test
    void save_shouldStoreNullVoiceForWrittenAnswer() {
        UUID turn = insertTurn(sessionId());

        attempts.save(AnswerAttempt.written(turn, "Respuesta escrita", "Cuéntame sobre ti", CLOCK));

        assertThat(jdbc.queryForMap("SELECT voz_palabras, voz_tramo_ms, voz_pausas_ms, voz_muletillas FROM "
                + "microentrevista.intento_respuesta WHERE turno_id = ?", turn))
                .containsEntry("voz_palabras", null).containsEntry("voz_tramo_ms", null)
                .containsEntry("voz_pausas_ms", null).containsEntry("voz_muletillas", null);
        assertThat(attempts.findDefinitive(turn)).get().extracting(AnswerAttempt::voice).isNull();
    }

    @Test
    void save_shouldStoreAudioAnswerWithoutVoiceData() {
        UUID turn = insertTurn(sessionId());

        attempts.save(AnswerAttempt.spoken(turn, "Respuesta hablada", "Cuéntame sobre ti", "es", null, CLOCK));

        assertThat(attempts.findDefinitive(turn)).get().extracting(AnswerAttempt::voice).isNull();
    }

    @Test
    void migrationV8_shouldAcceptAudioAnswerIdempotencyOperation() {
        assertThatCode(() -> jdbc.update("""
                INSERT INTO microentrevista.clave_idempotencia (id, firebase_uid, operacion, clave, huella_peticion,
                    estado_respuesta, cuerpo_respuesta, fecha_creacion)
                VALUES (?, 'uid-voz', 'RESPONDER_AUDIO', 'clave-1', repeat('a', 64), 200, '{}'::jsonb, now())
                """, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void migrationV8_shouldStillRejectUnknownOperations() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO microentrevista.clave_idempotencia (id, firebase_uid, operacion, clave, huella_peticion,
                    estado_respuesta, cuerpo_respuesta, fecha_creacion)
                VALUES (?, 'uid-voz', 'RESPONDER_VIDEO', 'clave-2', repeat('a', 64), 200, '{}'::jsonb, now())
                """, UUID.randomUUID())).hasMessageContaining("ck_clave_idempotencia_operacion");
    }
}
