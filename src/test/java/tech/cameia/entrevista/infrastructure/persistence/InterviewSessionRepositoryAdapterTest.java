package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.exception.SessionAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.SessionSnapshot;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;

class InterviewSessionRepositoryAdapterTest extends AbstractPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");

    @Autowired
    private InterviewSessionRepository sessions;

    private String stateOf(UUID id) {
        return jdbc.queryForObject("SELECT estado FROM microentrevista.sesion WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("Guardar una sesión nueva y leerla devuelve los mismos valores, con versión 0")
    void save_shouldRoundTrip_whenNew() {
        InterviewSession session = newSession("uid-a");

        InterviewSession saved = sessions.save(session);
        InterviewSession found = sessions.findByIdAndOwner(session.id(), OWNER).orElseThrow();

        assertThat(saved.version()).isZero();
        assertThat(found.version()).isZero();
        assertThat(found.id()).isEqualTo(session.id());
        assertThat(found.owner()).isEqualTo(new FirebaseUid("uid-a"));
        assertThat(found.status()).isEqualTo(SessionStatus.CONFIGURED);
        assertThat(found.planningRetries()).isZero();
        assertThat(found.configuration().jobOffer()).isNull();
        assertThat(found.configuration().language()).isEqualTo(InterviewLanguage.ENGLISH);
        assertThat(found.configuration().answerMode()).isEqualTo(AnswerMode.TEXT);
        assertThat(found.configuration().modality()).isEqualTo(session.configuration().modality());
        assertThat(found.configuration().tone()).isEqualTo(session.configuration().tone());
        assertThat(found.configuration().profileId()).isEqualTo(session.configuration().profileId());
        assertThat(found.configuration().targetRoleId()).isEqualTo(session.configuration().targetRoleId());
        assertThat(found.createdAt()).isEqualTo(session.createdAt());
        assertThat(found.updatedAt()).isEqualTo(session.updatedAt());
        assertThat(found.lastActivityAt()).isNull();
        assertThat(found.finishedAt()).isNull();
    }

    @Test
    @DisplayName("Una oferta de 3000 caracteres con tildes y el contexto JSON con Unicode se guardan íntegros")
    void save_shouldKeepJobOfferAndSnapshot_whenUnicode() {
        String offer = "é".repeat(3000);
        InterviewSession session = InterviewSession.configure(UUID.randomUUID(), new FirebaseUid("uid-a"),
                configuration(offer, InterviewLanguage.SPANISH_COLOMBIA, AnswerMode.AUDIO, "{\"nombre\":\"José 😀\"}"),
                CLOCK);

        sessions.save(session);
        InterviewSession found = sessions.findByIdAndOwner(session.id(), OWNER).orElseThrow();

        assertThat(found.configuration().jobOffer()).isEqualTo(offer);
        assertThat(found.configuration().profileSnapshotJson()).isEqualToIgnoringWhitespace("{\"nombre\":\"José 😀\"}");
        assertThat(found.configuration().language()).isEqualTo(InterviewLanguage.SPANISH_COLOMBIA);
        assertThat(found.configuration().answerMode()).isEqualTo(AnswerMode.AUDIO);
    }

    @Test
    @DisplayName("findByIdAndOwner devuelve vacío cuando la sesión no existe")
    void findByIdAndOwner_shouldReturnEmpty_whenUnknown() {
        assertThat(sessions.findByIdAndOwner(UUID.randomUUID(), OWNER)).isEmpty();
    }

    @Test
    @DisplayName("Abandonar una sesión y empezar otra en la misma transacción confirma las dos")
    void save_shouldAllowStartAfterAbandon_inSameTransaction() {
        InterviewSession first = sessions.save(newSession("uid-a"));
        first.start(CLOCK);
        UUID inProgressId = sessions.save(first).id();
        UUID configuredId = sessions.save(newSession("uid-a")).id();

        transaction.executeWithoutResult(status -> {
            InterviewSession a = sessions.findByIdAndOwner(inProgressId, OWNER).orElseThrow();
            a.abandon(CLOCK);
            sessions.save(a);
            InterviewSession b = sessions.findByIdAndOwner(configuredId, OWNER).orElseThrow();
            b.start(CLOCK);
            sessions.save(b);
        });

        assertThat(stateOf(inProgressId)).isEqualTo("ABANDONADA");
        assertThat(stateOf(configuredId)).isEqualTo("EN_CURSO");
        assertThat(jdbc.queryForObject("SELECT fecha_fin IS NOT NULL FROM microentrevista.sesion WHERE id = ?",
                Boolean.class, inProgressId)).isTrue();
    }

    @Test
    @DisplayName("Empezar la segunda antes de abandonar la primera falla y el rollback deja todo como estaba")
    void save_shouldRejectStartBeforeAbandon_inSameTransaction() {
        InterviewSession first = sessions.save(newSession("uid-a"));
        first.start(CLOCK);
        UUID inProgressId = sessions.save(first).id();
        UUID configuredId = sessions.save(newSession("uid-a")).id();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            InterviewSession b = sessions.findByIdAndOwner(configuredId, OWNER).orElseThrow();
            b.start(CLOCK);
            sessions.save(b);
            InterviewSession a = sessions.findByIdAndOwner(inProgressId, OWNER).orElseThrow();
            a.abandon(CLOCK);
            sessions.save(a);
        })).isInstanceOf(SessionAlreadyExistsException.class);

        assertThat(stateOf(inProgressId)).isEqualTo("EN_CURSO");
        assertThat(stateOf(configuredId)).isEqualTo("CONFIGURADA");
    }

    @Test
    @DisplayName("Guardar con una versión vencida lanza el conflicto y la base conserva la primera escritura")
    void save_shouldThrowConflict_whenStaleVersion() {
        UUID id = sessions.save(newSession("uid-a")).id();
        InterviewSession first = sessions.findByIdAndOwner(id, OWNER).orElseThrow();
        InterviewSession second = sessions.findByIdAndOwner(id, OWNER).orElseThrow();
        first.start(CLOCK);
        sessions.save(first);
        second.start(CLOCK);

        assertThatThrownBy(() -> sessions.save(second)).isInstanceOf(SessionConflictException.class);

        assertThat(jdbc.queryForObject("SELECT version FROM microentrevista.sesion WHERE id = ?", Long.class, id))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Guardar una sesión con una versión que ya no existe en la base lanza el conflicto")
    void save_shouldThrowConflict_whenRowWasDeleted() {
        InterviewSession saved = sessions.save(newSession("uid-a"));
        sessions.deleteConfigured(saved.id(), OWNER);

        assertThatThrownBy(() -> sessions.save(saved)).isInstanceOf(SessionConflictException.class);
    }

    @Test
    @DisplayName("Una segunda sesión configurada del mismo usuario lanza SessionAlreadyExists y queda una sola fila")
    void save_shouldThrowSessionAlreadyExists_whenSecondConfigured() {
        sessions.save(newSession("uid-a"));

        assertThatThrownBy(() -> sessions.save(newSession("uid-a"))).isInstanceOf(SessionAlreadyExistsException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM microentrevista.sesion", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos sesiones en curso del mismo usuario lanzan SessionAlreadyExists")
    void save_shouldThrowSessionAlreadyExists_whenSecondInProgress() {
        InterviewSession first = sessions.save(newSession("uid-a"));
        first.start(CLOCK);
        sessions.save(first);
        // La primera ya está en curso: se puede crear otra configurada, pero no ponerla en curso.
        InterviewSession second = sessions.save(newSession("uid-a"));
        second.start(CLOCK);

        assertThatThrownBy(() -> sessions.save(second)).isInstanceOf(SessionAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Usuarios distintos pueden tener cada uno su sesión configurada")
    void save_shouldAllowConfigured_whenDifferentUsers() {
        sessions.save(newSession("uid-a"));
        sessions.save(newSession("uid-b"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM microentrevista.sesion", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("deleteConfigured borra la sesión configurada")
    void deleteConfigured_shouldDelete_whenConfigured() {
        UUID id = sessions.save(newSession("uid-a")).id();

        sessions.deleteConfigured(id, OWNER);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM microentrevista.sesion", Integer.class)).isZero();
    }

    @Test
    @DisplayName("deleteConfigured lanza la excepción de transición si la sesión está en curso, y la fila sigue")
    void deleteConfigured_shouldThrow_whenInProgress() {
        UUID id = sessions.save(newSession("uid-a")).id();
        jdbc.update("UPDATE microentrevista.sesion SET estado = 'EN_CURSO' WHERE id = ?", id);

        assertThatThrownBy(() -> sessions.deleteConfigured(id, OWNER)).isInstanceOf(SessionTransitionNotAllowedException.class);

        assertThat(stateOf(id)).isEqualTo("EN_CURSO");
    }

    @Test
    @DisplayName("deleteConfigured lanza la excepción de transición si la sesión no existe")
    void deleteConfigured_shouldThrow_whenUnknown() {
        assertThatThrownBy(() -> sessions.deleteConfigured(UUID.randomUUID(), OWNER))
                .isInstanceOf(SessionTransitionNotAllowedException.class);
    }

    @Test
    @DisplayName("Si la sesión pasó a EN_CURSO después de leerla, deleteConfigured no la borra")
    void deleteConfigured_shouldThrow_whenStateChangedConcurrently() {
        UUID id = sessions.save(newSession("uid-a")).id();
        InterviewSession read = sessions.findByIdAndOwner(id, OWNER).orElseThrow();
        jdbc.update("UPDATE microentrevista.sesion SET estado = 'EN_CURSO' WHERE id = ?", id);

        assertThat(read.status()).isEqualTo(SessionStatus.CONFIGURED);
        assertThatThrownBy(() -> sessions.deleteConfigured(read.id(), OWNER))
                .isInstanceOf(SessionTransitionNotAllowedException.class);
        assertThat(stateOf(id)).isEqualTo("EN_CURSO");
    }

    @Test
    @DisplayName("findByIdAndOwner devuelve vacío cuando la sesión es de otra persona")
    void findByIdAndOwner_shouldReturnEmpty_whenOtherOwner() {
        UUID id = sessions.save(newSession("uid-a")).id();

        assertThat(sessions.findByIdAndOwner(id, new FirebaseUid("uid-b"))).isEmpty();
    }

    @Test
    @DisplayName("deleteConfigured no borra la sesión de otra persona y lanza la excepción de transición")
    void deleteConfigured_shouldThrow_whenOtherOwner() {
        UUID id = sessions.save(newSession("uid-a")).id();

        assertThatThrownBy(() -> sessions.deleteConfigured(id, new FirebaseUid("uid-b")))
                .isInstanceOf(SessionTransitionNotAllowedException.class);

        assertThat(stateOf(id)).isEqualTo("CONFIGURADA");
    }

    @Test
    @DisplayName("Borrar la sesión configurada borra también sus preguntas y turnos")
    void deleteConfigured_shouldCascade_whenHasChildren() {
        UUID id = sessions.save(newSession("uid-a")).id();
        insertTurn(id);

        sessions.deleteConfigured(id, OWNER);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM microentrevista.turno", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM microentrevista.pregunta", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Una restricción distinta de las de duplicado se propaga sin traducir")
    void save_shouldPropagate_whenOtherConstraintViolated() {
        // En curso con fecha de cierre: la base lo rechaza con ck_sesion_fecha_fin, que no es un duplicado.
        InterviewSession inconsistent = InterviewSession.reconstitute(new SessionSnapshot(UUID.randomUUID(),
                new FirebaseUid("uid-a"), configuration(null, InterviewLanguage.ENGLISH, AnswerMode.TEXT, "{}"),
                SessionStatus.IN_PROGRESS, 0, CLOCK.instant(), CLOCK.instant(), CLOCK.instant(), CLOCK.instant(),
                null));

        assertThatThrownBy(() -> sessions.save(inconsistent)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM microentrevista.sesion", Integer.class)).isZero();
    }

    private InterviewSession inProgress(String uid) {
        InterviewSession session = newSession(uid);
        session.start(CLOCK);
        return sessions.save(session);
    }

    private void insertAttempt(UUID turnId, boolean definitive) {
        jdbc.update("""
                INSERT INTO microentrevista.intento_respuesta (id, turno_id, tipo_interaccion, es_definitiva,
                    origen_respuesta, respuesta_texto, pregunta_mostrada, fecha_creacion)
                VALUES (?, ?, 'INICIAL', ?, 'TEXTO', 'Mi respuesta', 'Cuéntame sobre ti', now())
                """, UUID.randomUUID(), turnId, definitive);
    }

    @Test
    @DisplayName("findConfiguredByOwner devuelve solo la sesión configurada del dueño")
    void findConfiguredByOwner_shouldReturnOnlyOwnersConfigured() {
        UUID mine = sessions.save(newSession("uid-a")).id();
        sessions.save(newSession("uid-b"));

        assertThat(sessions.findConfiguredByOwner(OWNER)).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(mine);
            assertThat(found.status()).isEqualTo(SessionStatus.CONFIGURED);
        });
        assertThat(sessions.findConfiguredByOwner(new FirebaseUid("uid-c"))).isEmpty();
    }

    @Test
    @DisplayName("findConfiguredByOwner ignora la sesión en curso del mismo dueño")
    void findConfiguredByOwner_shouldIgnoreInProgress() {
        inProgress("uid-a");

        assertThat(sessions.findConfiguredByOwner(OWNER)).isEmpty();
    }

    @Test
    @DisplayName("findInProgressByOwner devuelve solo la sesión en curso del dueño, aunque tenga otra configurada")
    void findInProgressByOwner_shouldReturnOnlyOwnersInProgress() {
        UUID running = inProgress("uid-a").id();
        sessions.save(newSession("uid-a"));
        inProgress("uid-b");

        assertThat(sessions.findInProgressByOwner(OWNER)).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(running);
            assertThat(found.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        });
        assertThat(sessions.findInProgressByOwner(new FirebaseUid("uid-c"))).isEmpty();
    }

    @Test
    @DisplayName("findInProgressByOwner devuelve vacío si el dueño solo tiene una sesión configurada")
    void findInProgressByOwner_shouldBeEmpty_whenOnlyConfigured() {
        sessions.save(newSession("uid-a"));

        assertThat(sessions.findInProgressByOwner(OWNER)).isEmpty();
    }

    @Test
    @DisplayName("countDefinitiveAnswers cuenta solo los intentos definitivos de esa sesión")
    void countDefinitiveAnswers_shouldCountOnlyDefinitive() {
        UUID id = inProgress("uid-a").id();
        UUID other = inProgress("uid-b").id();
        UUID first = insertTurn(id, 1);
        UUID second = insertTurn(id, 2);
        UUID third = insertTurn(id, 3);
        insertAttempt(first, true);
        insertAttempt(second, true);
        insertAttempt(third, false);
        insertAttempt(insertTurn(other, 1), true);

        assertThat(sessions.countDefinitiveAnswers(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("countDefinitiveAnswers es cero sin respuestas y sin preguntas")
    void countDefinitiveAnswers_shouldBeZero_whenNoAnswers() {
        UUID id = inProgress("uid-a").id();

        assertThat(sessions.countDefinitiveAnswers(id)).isZero();

        insertTurn(id, 1);

        assertThat(sessions.countDefinitiveAnswers(id)).isZero();
        assertThat(sessions.countDefinitiveAnswers(UUID.randomUUID())).isZero();
    }
}
