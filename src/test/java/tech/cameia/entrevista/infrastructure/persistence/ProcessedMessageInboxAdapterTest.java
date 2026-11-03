package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.IllegalTransactionStateException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.port.ProcessedMessageInbox;

class ProcessedMessageInboxAdapterTest extends AbstractPersistenceTest {

    private static final String EVENT_TYPE = "perfil.profesional-actualizado";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProcessedMessageInbox inbox;

    @BeforeEach
    void cleanInbox() {
        jdbc.execute("TRUNCATE microentrevista.evento_procesado");
    }

    private boolean register(UUID messageId) {
        return transaction.execute(status -> inbox.registerIfAbsent(messageId, EVENT_TYPE, CLOCK.instant()));
    }

    private int rows(UUID messageId) {
        return jdbc.queryForObject("SELECT count(*) FROM microentrevista.evento_procesado WHERE message_id = ?",
                Integer.class, messageId);
    }

    @Test
    @DisplayName("El primer mensaje se anota y el segundo con el mismo id no")
    void registerIfAbsent_shouldReturnTrueThenFalse() {
        UUID id = UUID.randomUUID();

        assertThat(register(id)).isTrue();
        assertThat(register(id)).isFalse();
        assertThat(rows(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos mensajes con ids distintos se anotan los dos")
    void registerIfAbsent_shouldRegisterBoth_whenDifferentIds() {
        assertThat(register(UUID.randomUUID())).isTrue();
        assertThat(register(UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("La fila guarda el tipo y la fecha de proceso")
    void registerIfAbsent_shouldStoreTypeAndInstant() {
        UUID id = UUID.randomUUID();
        register(id);

        String type = jdbc.queryForObject("SELECT tipo FROM microentrevista.evento_procesado WHERE message_id = ?",
                String.class, id);
        Instant processedAt = jdbc.queryForObject(
                "SELECT procesado_en FROM microentrevista.evento_procesado WHERE message_id = ?",
                java.sql.Timestamp.class, id).toInstant();

        assertThat(type).isEqualTo(EVENT_TYPE);
        assertThat(processedAt).isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("Con dos hilos y el mismo id solo uno gana")
    void registerIfAbsent_shouldHaveSingleWinner_whenConcurrent() throws Exception {
        UUID id = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int winners = 0;
        try {
            Future<Boolean> first = pool.submit(() -> {
                start.await();
                return register(id);
            });
            Future<Boolean> second = pool.submit(() -> {
                start.await();
                return register(id);
            });
            start.countDown();
            winners += first.get(10, TimeUnit.SECONDS) ? 1 : 0;
            winners += second.get(10, TimeUnit.SECONDS) ? 1 : 0;
        } finally {
            pool.shutdownNow();
        }

        assertThat(winners).isEqualTo(1);
        assertThat(rows(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Anotar fuera de una transacción falla")
    void registerIfAbsent_shouldFail_whenOutsideTransaction() {
        assertThatThrownBy(() -> inbox.registerIfAbsent(UUID.randomUUID(), EVENT_TYPE, CLOCK.instant()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
