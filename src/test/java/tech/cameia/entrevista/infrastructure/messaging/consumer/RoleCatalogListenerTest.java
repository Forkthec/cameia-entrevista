package tech.cameia.entrevista.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import jakarta.validation.Validation;
import tech.cameia.entrevista.application.command.ReplicateRoleCatalogCommand;
import tech.cameia.entrevista.application.service.ReplicateRoleCatalogService;
import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;
import tech.cameia.entrevista.domain.model.ReplicationOutcome;
import tech.cameia.entrevista.domain.model.RoleStatus;
import tech.cameia.entrevista.infrastructure.messaging.payload.RoleCatalogPayloadV1;

@ExtendWith(OutputCaptureExtension.class)
class RoleCatalogListenerTest {

    private static final String MESSAGE_ID = "3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d10";
    private static final String TYPE = "perfil.catalogo-roles-actualizado";
    private static final Map<String, Object> V1 = Map.of("x-event-version", 1);

    private RoleCatalogEventReader reader;
    private ReplicateRoleCatalogService service;
    private RoleCatalogListener listener;
    private ReplicateRoleCatalogCommand command;

    @BeforeEach
    void setUp() {
        reader = mock(RoleCatalogEventReader.class);
        service = mock(ReplicateRoleCatalogService.class);
        listener = new RoleCatalogListener(reader, service);
        command = new ReplicateRoleCatalogCommand(UUID.fromString(MESSAGE_ID), List.of(
                new ProfessionalRoleReplica(UUID.randomUUID(), "Rol", "Role", RoleStatus.ACTIVE)),
                Instant.parse("2026-10-24T12:00:00.123Z"));
        MDC.clear();
    }

    private RoleCatalogPayloadV1 payload() {
        return new RoleCatalogPayloadV1("2026-10-24T12:00:00.123Z", List.of());
    }

    private void receive(String correlationId) {
        listener.onRoleCatalogUpdated(payload(), MESSAGE_ID, TYPE, V1, correlationId);
    }

    @Test
    @DisplayName("Un mensaje válido se lee y se aplica una vez")
    void onRoleCatalogUpdated_shouldApply_whenValid() {
        var received = payload();
        when(reader.read(received, MESSAGE_ID, TYPE, 1)).thenReturn(command);
        when(service.apply(command)).thenReturn(ReplicationOutcome.APPLIED);

        listener.onRoleCatalogUpdated(received, MESSAGE_ID, TYPE, V1, "perfil-catalogo-3b6f0c2e");

        verify(service).apply(command);
    }

    @Test
    @DisplayName("Sin el encabezado de versión el lector recibe null y decide el rechazo")
    void onRoleCatalogUpdated_shouldPassNullVersion_whenHeaderMissing() {
        var received = payload();
        when(reader.read(received, MESSAGE_ID, TYPE, null))
                .thenThrow(new ContractViolation(ContractViolation.EVENT_VERSION_UNSUPPORTED, null));

        assertThatThrownBy(() -> listener.onRoleCatalogUpdated(received, MESSAGE_ID, TYPE, Map.of(), "req-1"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("EVENT_VERSION_UNSUPPORTED");
    }

    @Test
    @DisplayName("Un mensaje repetido o más viejo termina sin error")
    void onRoleCatalogUpdated_shouldAcknowledge_whenDuplicateOrStale() {
        when(reader.read(any(), any(), any(), any())).thenReturn(command);
        when(service.apply(command)).thenReturn(ReplicationOutcome.DUPLICATE_IGNORED, ReplicationOutcome.STALE_IGNORED);

        receive("req-1");
        receive("req-2");

        verify(service, org.mockito.Mockito.times(2)).apply(command);
    }

    @Test
    @DisplayName("Un incumplimiento del contrato se rechaza sin reencolar y se registra una vez con su campo")
    void onRoleCatalogUpdated_shouldReject_whenContractViolation(CapturedOutput output) {
        when(reader.read(any(), any(), any(), any())).thenThrow(
                new ContractViolation(ContractViolation.CATALOG_ROLE_DUPLICATED, "roles[1].codigo"));

        assertThatThrownBy(() -> receive("req-1")).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("CATALOG_ROLE_DUPLICATED");

        assertThat(output.getAll()).contains("razón=CATALOG_ROLE_DUPLICATED", "campo=roles[1].codigo",
                "messageId=" + MESSAGE_ID, "cola=entrevista.catalogo-roles-actualizado");
        assertThat(output.getAll().split("rechazado hacia la cola de fallidos", -1)).hasSize(2);
    }

    @Test
    @DisplayName("Un rechazo sin campo registra un guion en su lugar")
    void onRoleCatalogUpdated_shouldLogDash_whenNoField(CapturedOutput output) {
        when(reader.read(any(), any(), any(), any()))
                .thenThrow(new ContractViolation(ContractViolation.EVENT_TYPE_INVALID, null));

        assertThatThrownBy(() -> receive("req-1")).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(output.getAll()).contains("razón=EVENT_TYPE_INVALID", "campo=-");
    }

    @Test
    @DisplayName("Un fallo técnico del caso de uso sale tal cual, sin registro de rechazo")
    void onRoleCatalogUpdated_shouldPropagate_whenServiceFailsTechnically(CapturedOutput output) {
        var failure = new DataAccessResourceFailureException("base caída");
        when(reader.read(any(), any(), any(), any())).thenReturn(command);
        when(service.apply(command)).thenThrow(failure);

        assertThatThrownBy(() -> receive("req-1")).isSameAs(failure);

        assertThat(output.getAll()).doesNotContain("rechazado");
    }

    @Test
    @DisplayName("El requestId del MDC es el correlation_id durante el mensaje y se quita al terminar, también si falla")
    void onRoleCatalogUpdated_shouldSetAndClearRequestId() {
        List<String> seen = new ArrayList<>();
        when(reader.read(any(), any(), any(), any())).thenReturn(command);
        when(service.apply(command)).thenAnswer(call -> {
            seen.add(MDC.get("requestId"));
            return ReplicationOutcome.APPLIED;
        });

        receive("perfil-catalogo-3b6f0c2e");

        assertThat(seen).containsExactly("perfil-catalogo-3b6f0c2e");
        assertThat(MDC.get("requestId")).isNull();

        org.mockito.Mockito.doAnswer(call -> {
            seen.add(MDC.get("requestId"));
            throw new DataAccessResourceFailureException("base caída");
        }).when(service).apply(command);
        assertThatThrownBy(() -> receive("req-456")).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(seen).containsExactly("perfil-catalogo-3b6f0c2e", "req-456");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("Con un correlation_id inválido el requestId es el message_id saneado")
    void onRoleCatalogUpdated_shouldUseMessageIdAsRequestId_whenCorrelationInvalid() {
        List<String> seen = new ArrayList<>();
        when(reader.read(any(), any(), any(), any())).thenReturn(command);
        when(service.apply(command)).thenAnswer(call -> {
            seen.add(MDC.get("requestId"));
            return ReplicationOutcome.APPLIED;
        });

        receive("a b");
        receive(null);
        receive("x".repeat(65));

        assertThat(seen).containsExactly(MESSAGE_ID, MESSAGE_ID, MESSAGE_ID);
    }

    @Test
    @DisplayName("logs_shouldNotContainRoleNames: el registro de un rechazo real no trae nombres de rol")
    void logs_shouldNotContainRoleNames(CapturedOutput output) {
        var realReader = new RoleCatalogEventReader(Validation.buildDefaultValidatorFactory().getValidator());
        var secret = new RoleCatalogPayloadV1("2026-10-24T12:00:00.123Z", List.of(
                new RoleCatalogPayloadV1.RoleItem("a0000001-0000-0000-0000-000000000001", "Rol Secreto Es",
                        "Rol Secreto En", "DELETED")));
        var realListener = new RoleCatalogListener(realReader, service);

        assertThatThrownBy(() -> realListener.onRoleCatalogUpdated(secret, MESSAGE_ID, TYPE, V1, "req-1"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(output.getAll()).contains("razón=PAYLOAD_CONTRACT_VIOLATION", "campo=roles[0].estado")
                .doesNotContain("Rol Secreto", "DELETED");
    }

    @Test
    @DisplayName("Un rechazo por repetido tampoco registra los nombres")
    void logs_shouldNotContainRoleNames_whenDuplicated(CapturedOutput output) {
        var realReader = new RoleCatalogEventReader(Validation.buildDefaultValidatorFactory().getValidator());
        var item = new RoleCatalogPayloadV1.RoleItem("a0000001-0000-0000-0000-000000000001", "Nombre Reservado",
                "Reserved Name", "ACTIVE");
        var realListener = new RoleCatalogListener(realReader, service);

        assertThatThrownBy(() -> realListener.onRoleCatalogUpdated(
                new RoleCatalogPayloadV1("2026-10-24T12:00:00.123Z", List.of(item, item)), MESSAGE_ID, TYPE, V1,
                "req-1")).isInstanceOf(AmqpRejectAndDontRequeueException.class).hasMessage("CATALOG_ROLE_DUPLICATED");

        assertThat(output.getAll()).contains("razón=CATALOG_ROLE_DUPLICATED", "campo=roles[1].codigo")
                .doesNotContain("Nombre Reservado", "Reserved Name");
    }
}
