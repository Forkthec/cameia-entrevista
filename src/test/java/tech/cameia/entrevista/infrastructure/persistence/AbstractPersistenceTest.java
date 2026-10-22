package tech.cameia.entrevista.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Base de las pruebas de los adaptadores de persistencia: PostgreSQL 16 real, base vacía en cada
 * prueba y ayudantes para armar sesiones y filas hijas. Sin transacción alrededor de la prueba, para
 * ver los choques de concurrencia tal como ocurren. Cada clase concreta declara su propio contenedor,
 * porque el contexto de Spring se guarda en caché y no puede apuntar a un contenedor ya detenido.
 * Se omite si no hay Docker.
 */
@SpringBootTest
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractPersistenceTest {

    protected static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneOffset.UTC);

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected TransactionTemplate transaction;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia CASCADE");
        transaction = new TransactionTemplate(transactionManager);
    }

    protected static SessionConfiguration configuration(String jobOffer, InterviewLanguage language,
            AnswerMode answerMode, String profileSnapshotJson) {
        return new SessionConfiguration(UUID.randomUUID(), UUID.randomUUID(), jobOffer, profileSnapshotJson,
                Modality.SIMULATION, Tone.WARM, answerMode, language);
    }

    protected static InterviewSession newSession(String uid) {
        return InterviewSession.configure(UUID.randomUUID(), new FirebaseUid(uid),
                configuration(null, InterviewLanguage.ENGLISH, AnswerMode.TEXT, "{}"), CLOCK);
    }

    /** Inserta por SQL la ejecución de planificación, una pregunta y su turno pendiente; devuelve el turno. */
    protected UUID insertTurn(UUID sessionId) {
        UUID executionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO microentrevista.ejecucion_ia (id, sesion_id, turno_id, tipo_operacion, numero_intento,
                    idempotency_key, estado, fecha_inicio, fecha_fin, modelo, version_prompt)
                VALUES (?, ?, NULL, 'PLANIFICAR', 1, ?, 'EXITOSA', now(), now(), 'modelo-de-prueba', 'v1')
                """, executionId, sessionId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO microentrevista.pregunta (id, sesion_id, orden, tipo, texto_original, fecha_creacion,
                    ejecucion_ia_id)
                VALUES (?, ?, 1, 'APERTURA', 'Cuéntame sobre ti', now(), ?)
                """, questionId, sessionId, executionId);
        jdbc.update("""
                INSERT INTO microentrevista.turno (id, pregunta_id, estado, fecha_creacion, fecha_actualizacion,
                    version)
                VALUES (?, ?, 'PENDING', now(), now(), 0)
                """, turnId, questionId);
        return turnId;
    }
}
