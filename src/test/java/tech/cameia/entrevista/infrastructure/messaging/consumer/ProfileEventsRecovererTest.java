package tech.cameia.entrevista.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(OutputCaptureExtension.class)
class ProfileEventsRecovererTest {

    private final ProfileEventsRecoverer recoverer = new ProfileEventsRecoverer();

    private static Message message() {
        var properties = new MessageProperties();
        properties.setConsumerQueue("entrevista.perfil-profesional-actualizado");
        properties.setMessageId("m-1");
        return new Message("{\"nombre\":\"Persona Real\"}".getBytes(), properties);
    }

    @Test
    @DisplayName("Un fallo técnico agotado registra un ERROR con traza sin mensajes y rechaza sin reencolar")
    void recover_shouldLogErrorAndReject_whenTechnicalFailure(CapturedOutput output) {
        var cause = new DataAccessResourceFailureException("secreto: password=abc");

        assertThatThrownBy(() -> recoverer.recover(message(), cause))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(output.getAll()).contains("ERROR", "agotado tras los reintentos",
                "cola=entrevista.perfil-profesional-actualizado", "messageId=m-1",
                "causa=DataAccessResourceFailureException", "DataAccessResourceFailureException")
                .doesNotContain("secreto", "password", "Persona Real");
        assertThat(output.getAll().split("agotado tras los reintentos", -1)).hasSize(2);
    }

    @Test
    @DisplayName("Un rechazo por contrato no registra otro ERROR: ya se anotó una vez")
    void recover_shouldNotLogError_whenContractRejection(CapturedOutput output) {
        var cause = new AmqpRejectAndDontRequeueException("PAYLOAD_CONTRACT_VIOLATION");

        assertThatThrownBy(() -> recoverer.recover(message(), cause))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(output.getAll()).doesNotContain("ERROR");
    }
}
