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

import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;

class IdempotencyKeyRepositoryAdapterTest extends AbstractPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final FirebaseUid UID_A = new FirebaseUid("uid-a");

    @Autowired
    private IdempotencyKeyRepository keys;

    private static IdempotencyKey keyOf(FirebaseUid owner, String key) {
        return new IdempotencyKey(UUID.randomUUID(), owner, "CREAR_SESION", key, "a".repeat(64), 201,
                "{\"id\":\"x\"}", CLOCK.instant());
    }

    @Test
    @DisplayName("Guardar un registro y buscarlo devuelve los mismos valores")
    void save_shouldRoundTrip() {
        IdempotencyKey saved = keys.save(keyOf(UID_A, "k-1"));

        IdempotencyKey found = keys.find(UID_A, "CREAR_SESION", "k-1").orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.owner()).isEqualTo(UID_A);
        assertThat(found.requestFingerprint()).isEqualTo("a".repeat(64));
        assertThat(found.responseStatus()).isEqualTo(201);
        assertThat(found.responseBodyJson()).isEqualToIgnoringWhitespace("{\"id\":\"x\"}");
        assertThat(found.createdAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("Repetir usuario, operación y clave lanza IdempotencyKeyAlreadyExists")
    void save_shouldThrow_whenSameUserOperationAndKey() {
        keys.save(keyOf(UID_A, "k-1"));

        assertThatThrownBy(() -> keys.save(keyOf(UID_A, "k-1"))).isInstanceOf(IdempotencyKeyAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Otro usuario puede usar la misma clave y no ve el registro del primero")
    void find_shouldReturnEmpty_whenOtherUser() {
        keys.save(keyOf(UID_A, "k-1"));
        FirebaseUid other = new FirebaseUid("uid-b");

        assertThat(keys.find(other, "CREAR_SESION", "k-1")).isEmpty();
        assertThat(keys.save(keyOf(other, "k-1")).owner()).isEqualTo(other);
    }

    @Test
    @DisplayName("Una operación que el esquema no admite se propaga sin traducir a clave repetida")
    void save_shouldPropagate_whenOperationNotAllowed() {
        keys.save(keyOf(UID_A, "k-1"));
        IdempotencyKey otherOperation = new IdempotencyKey(UUID.randomUUID(), UID_A, "INICIAR_SESION", "k-1",
                "b".repeat(64), 200, "{}", CLOCK.instant());

        assertThatThrownBy(() -> keys.save(otherOperation)).isNotInstanceOf(IdempotencyKeyAlreadyExistsException.class)
                .hasMessageContaining("ck_clave_idempotencia_operacion");
    }

    @Test
    @DisplayName("Una restricción distinta de la clave repetida se propaga sin traducir")
    void save_shouldPropagate_whenOtherConstraintViolated() {
        IdempotencyKey badFingerprint = new IdempotencyKey(UUID.randomUUID(), UID_A, "CREAR_SESION", "k-2",
                "XYZ", 201, "{}", CLOCK.instant());

        assertThatThrownBy(() -> keys.save(badFingerprint)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
