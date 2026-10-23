package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.application.command.ReplicateProfileCommand;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.ReplicationOutcome;
import tech.cameia.entrevista.support.InMemoryProcessedMessageInbox;
import tech.cameia.entrevista.support.InMemoryProfileReplicaRepository;
import tech.cameia.entrevista.support.ReplicaFixtures;

class ReplicateProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final Instant T100 = Instant.parse("2026-10-15T12:00:00.100Z");
    private static final Instant T200 = Instant.parse("2026-10-15T12:00:00.200Z");

    private InMemoryProfileReplicaRepository replicas;
    private InMemoryProcessedMessageInbox inbox;
    private ReplicateProfileService service;

    @BeforeEach
    void setUp() {
        replicas = new InMemoryProfileReplicaRepository();
        inbox = new InMemoryProcessedMessageInbox();
        service = new ReplicateProfileService(replicas, inbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ReplicateProfileCommand command(UUID messageId, Instant updatedAt) {
        return new ReplicateProfileCommand(messageId, ReplicaFixtures.replica(updatedAt));
    }

    @Test
    @DisplayName("El primer mensaje guarda la copia y anota el Inbox")
    void apply_shouldInsert_whenFirstMessage() {
        UUID id = UUID.randomUUID();

        assertThat(service.apply(command(id, T100))).isEqualTo(ReplicationOutcome.APPLIED);

        assertThat(replicas.findById(ReplicaFixtures.PROFILE_ID)).isPresent();
        assertThat(inbox.contains(id)).isTrue();
    }

    @Test
    @DisplayName("Un mensaje ya procesado se ignora sin tocar la copia")
    void apply_shouldIgnore_whenMessageAlreadyProcessed() {
        UUID id = UUID.randomUUID();
        service.apply(command(id, T200));

        ReplicationOutcome second = service.apply(new ReplicateProfileCommand(id,
                ReplicaFixtures.replica(ReplicaFixtures.OWNER, "Otro nombre", T200.plusMillis(5))));

        assertThat(second).isEqualTo(ReplicationOutcome.DUPLICATE_IGNORED);
        assertThat(replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow().name()).isEqualTo("Analista de datos");
    }

    @Test
    @DisplayName("Un mensaje anterior a la copia se anota en el Inbox y no la cambia")
    void apply_shouldIgnore_whenOlderThanCopy() {
        service.apply(command(UUID.randomUUID(), T200));
        UUID older = UUID.randomUUID();

        ReplicationOutcome outcome = service.apply(new ReplicateProfileCommand(older,
                ReplicaFixtures.replica(ReplicaFixtures.OWNER, "Viejo", T100)));

        assertThat(outcome).isEqualTo(ReplicationOutcome.STALE_IGNORED);
        assertThat(inbox.contains(older)).isTrue();
        assertThat(replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow().name()).isEqualTo("Analista de datos");
    }

    @Test
    @DisplayName("Un mensaje que cambia el dueño se rechaza sin escribir nada")
    void apply_shouldRejectOwnerChange_withoutWriting() {
        service.apply(command(UUID.randomUUID(), T100));
        UUID intruder = UUID.randomUUID();

        ReplicationOutcome outcome = service.apply(new ReplicateProfileCommand(intruder,
                ReplicaFixtures.replica(new FirebaseUid("uid-b"), "Robado", T200)));

        assertThat(outcome).isEqualTo(ReplicationOutcome.OWNER_MISMATCH);
        assertThat(inbox.contains(intruder)).isFalse();
        assertThat(replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow().owner())
                .isEqualTo(ReplicaFixtures.OWNER);
    }

    @Test
    @DisplayName("Dos mensajes distintos con el mismo contenido y fecha creciente se aplican los dos")
    void apply_shouldApplyBoth_whenSameContentNewerInstant() {
        assertThat(service.apply(command(UUID.randomUUID(), T100))).isEqualTo(ReplicationOutcome.APPLIED);
        assertThat(service.apply(command(UUID.randomUUID(), T200))).isEqualTo(ReplicationOutcome.APPLIED);
    }

    @Test
    @DisplayName("Un mensaje con el mismo instante que la copia se aplica")
    void apply_shouldApply_whenSameInstant() {
        service.apply(command(UUID.randomUUID(), T100));

        assertThat(service.apply(command(UUID.randomUUID(), T100))).isEqualTo(ReplicationOutcome.APPLIED);
    }
}
