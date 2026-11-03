package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;

class InterviewTurnRepositoryAdapterTest extends AbstractPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private InterviewTurnRepository turns;

    @Autowired
    private InterviewSessionRepository sessions;

    private UUID insertedTurn() {
        return insertTurn(sessions.save(newSession("uid-a")).id());
    }

    @Test
    @DisplayName("Un turno insertado por SQL se lee con su estado y su versión")
    void findById_shouldMapRow_whenExists() {
        UUID id = insertedTurn();

        InterviewTurn turn = turns.findById(id).orElseThrow();

        assertThat(turn.id()).isEqualTo(id);
        assertThat(turn.status()).isEqualTo(TurnStatus.PENDING);
        assertThat(turn.version()).isZero();
        assertThat(turn.questionId()).isNotNull();
    }

    @Test
    @DisplayName("findById devuelve vacío cuando el turno no existe")
    void findById_shouldReturnEmpty_whenUnknown() {
        assertThat(turns.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Guardar una transición actualiza el estado, la fecha y sube la versión")
    void save_shouldPersistTransition() {
        UUID id = insertedTurn();
        InterviewTurn turn = turns.findById(id).orElseThrow();
        turn.startProcessing(CLOCK);

        InterviewTurn saved = turns.save(turn);

        assertThat(saved.status()).isEqualTo(TurnStatus.PROCESSING);
        assertThat(saved.version()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT estado FROM microentrevista.turno WHERE id = ?", String.class, id))
                .isEqualTo("PROCESSING");
        assertThat(turns.findById(id).orElseThrow().updatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("Guardar con una versión vencida lanza el conflicto")
    void save_shouldThrowConflict_whenStaleVersion() {
        UUID id = insertedTurn();
        InterviewTurn first = turns.findById(id).orElseThrow();
        InterviewTurn second = turns.findById(id).orElseThrow();
        first.startProcessing(CLOCK);
        turns.save(first);
        second.startProcessing(CLOCK);

        assertThatThrownBy(() -> turns.save(second)).isInstanceOf(SessionConflictException.class);

        assertThat(jdbc.queryForObject("SELECT version FROM microentrevista.turno WHERE id = ?", Long.class, id))
                .isEqualTo(1L);
    }
}
