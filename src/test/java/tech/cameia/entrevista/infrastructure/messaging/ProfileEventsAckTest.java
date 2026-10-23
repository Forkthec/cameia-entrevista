package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * La confirmación del mensaje al broker ocurre después del commit de la base, y un fallo antes del
 * commit no confirma ni pierde el mensaje.
 */
class ProfileEventsAckTest extends ProfileEventsBrokerSupport {

    @BeforeEach
    void clean() {
        resetState();
    }

    @Test
    @DisplayName("Mientras la transacción está abierta el mensaje sigue sin confirmar; se confirma tras el commit")
    void ack_shouldHappenAfterCommit_whenMessageApplied() throws Exception {
        CountDownLatch inTransaction = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        probe().aroundUpsert(real -> () -> {
            inTransaction.countDown();
            await().atMost(Duration.ofSeconds(30)).until(() -> release.getCount() == 0);
            return real.getAsBoolean();
        });

        publish(PROFILE_KEY, VALID_BODY, UUID.randomUUID(), 1);

        assertThat(inTransaction.await(15, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(unacknowledged(PROFILE_QUEUE)).isEqualTo(1));
        assertThat(count("contexto_profesional_replica")).isZero();

        release.countDown();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(unacknowledged(PROFILE_QUEUE)).isZero();
            assertThat(count("contexto_profesional_replica")).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Si la transacción se deshace el mensaje no se pierde: se reentrega y se aplica una vez")
    void ack_shouldNotConfirm_whenTransactionRollsBack() {
        AtomicInteger attempts = new AtomicInteger();
        probe().aroundUpsert(real -> () -> {
            boolean stored = real.getAsBoolean();
            if (attempts.incrementAndGet() == 1) {
                throw new DataAccessResourceFailureException("caida despues de escribir");
            }
            return stored;
        });

        publish(PROFILE_KEY, VALID_BODY, UUID.randomUUID(), 1);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(count("contexto_profesional_replica")).isEqualTo(1);
            assertThat(count("evento_procesado")).isEqualTo(1);
            assertThat(unacknowledged(PROFILE_QUEUE)).isZero();
        });
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(ready(PROFILE_DLQ)).isZero();
    }
}
