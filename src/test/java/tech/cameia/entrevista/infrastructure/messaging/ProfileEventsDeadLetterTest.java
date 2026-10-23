package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.support.ReplicaFixtures;

/**
 * Qué mensajes llegan a la cola de fallidos y con cuántos intentos: el que incumple el contrato o
 * cambia el dueño, sin reintentos; el que falla por la base, tras tres intentos.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProfileEventsDeadLetterTest extends ProfileEventsBrokerSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        resetState();
    }

    @Test
    @DisplayName("Una versión del evento no soportada va a la cola de fallidos sin reintentos")
    void deadLetter_shouldRouteToDlq_whenContractViolated(CapturedOutput output) {
        publish(PROFILE_KEY, VALID_BODY, UUID.randomUUID(), 2);

        awaitDeadLettered(PROFILE_QUEUE, PROFILE_DLQ);

        assertThat(output.getAll()).contains("EVENT_VERSION_UNSUPPORTED");
        assertThat(count("contexto_profesional_replica")).isZero();
        assertThat(count("evento_procesado")).isZero();
    }

    @Test
    @DisplayName("Un fallo de la base que no cede va a la cola de fallidos tras tres intentos")
    void deadLetter_shouldRouteToDlq_whenDatabaseFailsThreeTimes(CapturedOutput output) {
        UUID messageId = UUID.randomUUID();
        probe().beforeFind(() -> {
            throw new DataAccessResourceFailureException("caida");
        });

        publish(PROFILE_KEY, VALID_BODY, messageId, 1);

        awaitDeadLettered(PROFILE_QUEUE, PROFILE_DLQ);
        assertThat(probe().findCalls()).isEqualTo(3);
        assertThat(count("contexto_profesional_replica")).isZero();
        assertThat(count("evento_procesado")).isZero();
        assertThat(output.getAll().lines()
                .filter(line -> line.contains("ERROR") && line.contains("cola=" + PROFILE_QUEUE)
                        && line.contains(messageId.toString()))).hasSize(1);
    }

    @Test
    @DisplayName("Un perfil que cambia de dueño va a la cola de fallidos sin reintentos y la copia no cambia")
    void deadLetter_shouldRouteToDlq_whenOwnerChanges(CapturedOutput output) {
        transactionalUpsert();

        publish(PROFILE_KEY, VALID_BODY.replace("6f1d2c3b4a5e4f60718293a4b5c6d7e8", "otro-dueno"), UUID.randomUUID(), 1);

        awaitDeadLettered(PROFILE_QUEUE, PROFILE_DLQ);
        assertThat(output.getAll()).contains("PROFILE_OWNER_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT firebase_uid FROM microentrevista.contexto_profesional_replica",
                String.class)).isEqualTo(ReplicaFixtures.OWNER.value());
    }

    private void transactionalUpsert() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> replicas.upsertIfNotOlder(
                ReplicaFixtures.replica(ReplicaFixtures.OWNER, "Analista de datos",
                        Instant.parse("2026-10-15T11:00:00.000Z")),
                Instant.parse("2026-10-20T15:00:00Z")));
    }
}
