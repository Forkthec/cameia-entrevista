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
import tech.cameia.entrevista.application.command.ReplicateProfileCommand;
import tech.cameia.entrevista.application.service.ReplicateProfileService;
import tech.cameia.entrevista.domain.model.ReplicationOutcome;
import tech.cameia.entrevista.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;
import tech.cameia.entrevista.support.ReplicaFixtures;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ProfileUpdatedListenerTest {

    private static final String MESSAGE_ID = "3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d10";
    private static final String TYPE = "perfil.profesional-actualizado";

    private ProfileUpdatedEventReader reader;
    private ReplicateProfileService service;
    private ProfileUpdatedListener listener;
    private ReplicateProfileCommand command;

    @BeforeEach
    void setUp() {
        reader = mock(ProfileUpdatedEventReader.class);
        service = mock(ReplicateProfileService.class);
        listener = new ProfileUpdatedListener(reader, service);
        command = new ReplicateProfileCommand(UUID.fromString(MESSAGE_ID),
                ReplicaFixtures.replica(Instant.parse("2026-10-15T12:00:00.123Z")));
        MDC.clear();
    }

    private ProfileUpdatedPayloadV1 payload() {
        return new ProfileUpdatedPayloadV1("uid", null, null, null);
    }

    private void receive(String correlationId) {
        listener.onProfileUpdated(payload(), MESSAGE_ID, TYPE, Map.of("x-event-version", 1), correlationId);
    }

    @Test
    @DisplayName("Sin el encabezado de versión el lector recibe null y decide el rechazo")
    void onProfileUpdated_shouldPassNullVersion_whenHeaderMissing() {
        var received = payload();
        when(reader.read(received, MESSAGE_ID, TYPE, null))
                .thenThrow(new ContractViolation(ContractViolation.EVENT_VERSION_UNSUPPORTED, null));

        assertThatThrownBy(() -> listener.onProfileUpdated(received, MESSAGE_ID, TYPE, Map.of(), "req-123"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("EVENT_VERSION_UNSUPPORTED");
    }

    @Test
    @DisplayName("Un mensaje válido se lee y se aplica una vez")
    void onProfileUpdated_shouldApply_whenValid() {
        var received = payload();
        when(reader.read(received, MESSAGE_ID, TYPE, 1)).thenReturn(command);
        when(service.apply(command)).thenReturn(ReplicationOutcome.APPLIED);

        listener.onProfileUpdated(received, MESSAGE_ID, TYPE, Map.of("x-event-version", 1), "req-123");

        verify(service).apply(command);
    }

    @Test
    @DisplayName("Un mensaje repetido o más viejo termina sin error")
    void onProfileUpdated_shouldAcknowledge_whenDuplicateOrStale() {
        when(reader.read(any(), any(), any(), any())).thenReturn(command);
        when(service.apply(command)).thenReturn(ReplicationOutcome.DUPLICATE_IGNORED, ReplicationOutcome.STALE_IGNORED);

        receive("req-1");
        receive("req-2");

        verify(service, org.mockito.Mockito.times(2)).apply(command);
    }

    @Test
    @DisplayName("Un incumplimiento del contrato se rechaza sin reencolar y se registra una vez con su campo")
    void onProfileUpdated_shouldReject_whenContractViolation(CapturedOutput output) {
        when(reader.read(any(), any(), any(), any())).thenThrow(
                new ContractViolation(ContractViolation.PAYLOAD_CONTRACT_VIOLATION, "contextoProfesional.nombre"));

        assertThatThrownBy(() -> receive("req-1")).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PAYLOAD_CONTRACT_VIOLATION");

        assertThat(output.getAll()).contains("razón=PAYLOAD_CONTRACT_VIOLATION", "campo=contextoProfesional.nombre",
                "messageId=" + MESSAGE_ID, "cola=entrevista.perfil-profesional-actualizado");
        assertThat(output.getAll().split("rechazado hacia la cola de fallidos", -1)).hasSize(2);
    }

    @Test
    @DisplayName("Un cambio de dueño se rechaza sin reencolar y se registra con el perfil")
    void onProfileUpdated_shouldReject_whenOwnerMismatch(CapturedOutput output) {
        when(reader.read(any(), any(), any(), any())).thenReturn(command);
        when(service.apply(command)).thenReturn(ReplicationOutcome.OWNER_MISMATCH);

        assertThatThrownBy(() -> receive("req-1")).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PROFILE_OWNER_MISMATCH");

        assertThat(output.getAll()).contains("razón=PROFILE_OWNER_MISMATCH", "campo=-",
                "perfilId=" + ReplicaFixtures.PROFILE_ID);
    }

    @Test
    @DisplayName("Un fallo técnico del caso de uso sale tal cual, sin registro de rechazo")
    void onProfileUpdated_shouldPropagate_whenServiceFailsTechnically(CapturedOutput output) {
        var failure = new DataAccessResourceFailureException("base caída");
        when(reader.read(any(), any(), any(), any())).thenReturn(command);
        when(service.apply(command)).thenThrow(failure);

        assertThatThrownBy(() -> receive("req-1")).isSameAs(failure);

        assertThat(output.getAll()).doesNotContain("rechazado");
    }

    @Test
    @DisplayName("El requestId del MDC es el correlation_id durante el mensaje y se quita al terminar, también si falla")
    void onProfileUpdated_shouldSetAndClearRequestId() {
        List<String> seen = new ArrayList<>();
        when(reader.read(any(), any(), any(), any())).thenReturn(command);
        when(service.apply(command)).thenAnswer(call -> {
            seen.add(MDC.get("requestId"));
            return ReplicationOutcome.APPLIED;
        });

        receive("req-123");

        assertThat(seen).containsExactly("req-123");
        assertThat(MDC.get("requestId")).isNull();

        org.mockito.Mockito.doAnswer(call -> {
            seen.add(MDC.get("requestId"));
            throw new DataAccessResourceFailureException("base caída");
        }).when(service).apply(command);
        assertThatThrownBy(() -> receive("req-456")).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(seen).containsExactly("req-123", "req-456");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("Con un correlation_id inválido el requestId es el message_id saneado")
    void onProfileUpdated_shouldUseMessageIdAsRequestId_whenCorrelationInvalid() {
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
    @DisplayName("El registro de un rechazo real no contiene nombre, resumen ni roles del perfil")
    void logs_shouldNotContainPersonalData(CapturedOutput output) {
        var realReader = new ProfileUpdatedEventReader(Validation.buildDefaultValidatorFactory().getValidator(),
                JsonMapper.builder().build());
        var secretContext = new ProfileUpdatedPayloadV1.ProfessionalContext("7c9e6679-7425-40de-944b-e07fc1f90ae7",
                "Nombre Secreto", "COMPLETED", null, "", List.of(), List.of(), List.of());
        var secret = new ProfileUpdatedPayloadV1("uid-secreto", secretContext,
                List.of(new ProfileUpdatedPayloadV1.AspiredRole("a0000001-0000-0000-0000-000000000002", "Rol Secreto")),
                "2026-10-15T12:00:00.123Z");
        var realListener = new ProfileUpdatedListener(realReader, service);

        assertThatThrownBy(() -> realListener.onProfileUpdated(secret, MESSAGE_ID, TYPE, Map.of("x-event-version", 1), "req-1"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(output.getAll()).contains("razón=PAYLOAD_CONTRACT_VIOLATION", "campo=contextoProfesional.")
                .doesNotContain("Nombre Secreto", "Rol Secreto", "uid-secreto");
    }
}
