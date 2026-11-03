package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.application.command.ReplicateRoleCatalogCommand;
import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;
import tech.cameia.entrevista.domain.model.ReplicationOutcome;
import tech.cameia.entrevista.domain.model.RoleStatus;
import tech.cameia.entrevista.domain.port.ProcessedMessageInbox;
import tech.cameia.entrevista.domain.port.RoleCatalogReplicaRepository;

class ReplicateRoleCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T13:00:00Z");
    private static final Instant SNAPSHOT = Instant.parse("2026-10-24T12:00:00.123Z");
    private static final String TYPE = "perfil.catalogo-roles-actualizado";

    private RoleCatalogReplicaRepository roles;
    private ProcessedMessageInbox inbox;
    private ReplicateRoleCatalogService service;
    private ReplicateRoleCatalogCommand command;

    @BeforeEach
    void setUp() {
        roles = mock(RoleCatalogReplicaRepository.class);
        inbox = mock(ProcessedMessageInbox.class);
        service = new ReplicateRoleCatalogService(roles, inbox, Clock.fixed(NOW, ZoneOffset.UTC));
        command = new ReplicateRoleCatalogCommand(UUID.randomUUID(), List.of(
                new ProfessionalRoleReplica(UUID.randomUUID(), "Desarrollador", "Developer", RoleStatus.ACTIVE)),
                SNAPSHOT);
    }

    @Test
    @DisplayName("Un mensaje nuevo anota el Inbox, guarda los roles y devuelve APPLIED")
    void apply_shouldApply_whenNewMessageChangesRows() {
        when(inbox.registerIfAbsent(command.messageId(), TYPE, NOW)).thenReturn(true);
        when(roles.upsertSnapshot(command.roles(), SNAPSHOT, NOW)).thenReturn(1);

        assertThat(service.apply(command)).isEqualTo(ReplicationOutcome.APPLIED);
    }

    @Test
    @DisplayName("Un mensaje ya procesado no llama al repositorio")
    void apply_shouldNotTouchRoles_whenDuplicate() {
        when(inbox.registerIfAbsent(command.messageId(), TYPE, NOW)).thenReturn(false);

        assertThat(service.apply(command)).isEqualTo(ReplicationOutcome.DUPLICATE_IGNORED);

        verify(roles, never()).upsertSnapshot(any(), any(), any());
    }

    @Test
    @DisplayName("Una instantánea anterior (0 filas cambiadas) devuelve STALE_IGNORED")
    void apply_shouldReportStale_whenNoRowChanged() {
        when(inbox.registerIfAbsent(command.messageId(), TYPE, NOW)).thenReturn(true);
        when(roles.upsertSnapshot(command.roles(), SNAPSHOT, NOW)).thenReturn(0);

        assertThat(service.apply(command)).isEqualTo(ReplicationOutcome.STALE_IGNORED);
    }

    @Test
    @DisplayName("Un fallo del repositorio sale tal cual para que el consumidor reintente")
    void apply_shouldPropagate_whenRepositoryFails() {
        when(inbox.registerIfAbsent(command.messageId(), TYPE, NOW)).thenReturn(true);
        when(roles.upsertSnapshot(any(), any(), any())).thenThrow(new IllegalStateException("caida"));

        assertThatThrownBy(() -> service.apply(command)).isInstanceOf(IllegalStateException.class);
    }
}
