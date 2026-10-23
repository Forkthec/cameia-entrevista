package tech.cameia.entrevista.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(OutputCaptureExtension.class)
class ProfileEventsErrorHandlerTest {

    private final ProfileEventsErrorHandler handler = new ProfileEventsErrorHandler();

    private static Message amqpMessage(String messageId) {
        var properties = new MessageProperties();
        properties.setConsumerQueue("entrevista.perfil-profesional-actualizado");
        properties.setMessageId(messageId);
        return new Message("{\"secreto\":\"dato\"}".getBytes(), properties);
    }

    private static org.springframework.messaging.Message<?> springMessage() {
        return MessageBuilder.withPayload("x").build();
    }

    private ListenerExecutionFailedException failure(Throwable cause) {
        return new ListenerExecutionFailedException("falla", cause, amqpMessage("m-1"));
    }

    private static MethodParameter parameter() throws NoSuchMethodException {
        Method method = ProfileEventsErrorHandlerTest.class.getDeclaredMethod("target", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void target(String ignored) {
    }

    static Stream<Throwable> conversionFailures() throws NoSuchMethodException {
        return Stream.of(
                new org.springframework.amqp.support.converter.MessageConversionException("cuerpo roto"),
                new org.springframework.messaging.converter.MessageConversionException("tipo equivocado"),
                new MethodArgumentResolutionException(springMessage(), parameter(), "no se pudo enlazar"),
                new IllegalStateException("envoltura",
                        new org.springframework.amqp.support.converter.MessageConversionException("anidada")),
                new RuntimeException("otra envoltura", new RuntimeException("más profunda",
                        new org.springframework.messaging.converter.MessageConversionException("al fondo"))));
    }

    @ParameterizedTest
    @MethodSource("conversionFailures")
    @DisplayName("Un cuerpo que no se puede convertir se rechaza sin reintentar como formato inválido")
    void handleError_shouldReject_whenConversionFails(Throwable cause, CapturedOutput output) {
        assertThatThrownBy(() -> handler.handleError(amqpMessage("m-1"), null, springMessage(), failure(cause)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PAYLOAD_INVALID_FORMAT");

        assertThat(output.getAll()).contains("razón=PAYLOAD_INVALID_FORMAT", "messageId=m-1")
                .doesNotContain("secreto", "dato", "cuerpo roto");
    }

    @Test
    @DisplayName("Un rechazo ya hecho por el consumidor se relanza sin tocarlo y sin otro registro")
    void handleError_shouldRethrowRejection_withoutLogging(CapturedOutput output) {
        var rejection = new AmqpRejectAndDontRequeueException("PAYLOAD_CONTRACT_VIOLATION");

        assertThatThrownBy(() -> handler.handleError(amqpMessage("m-1"), null, springMessage(), failure(rejection)))
                .isSameAs(rejection);

        assertThat(output.getAll()).doesNotContain("Falló el procesamiento", "rechazado");
    }

    @Test
    @DisplayName("Un fallo técnico se relanza para reintentar, con un WARN sin traza")
    void handleError_shouldRethrowTechnicalFailure_withWarnWithoutTrace(CapturedOutput output) {
        var technical = failure(new DataAccessResourceFailureException("password=secreto"));

        assertThatThrownBy(() -> handler.handleError(amqpMessage("m-1"), null, springMessage(), technical))
                .isSameAs(technical);

        assertThat(output.getAll()).contains("lo reintenta el contenedor", "messageId=m-1",
                "causa=DataAccessResourceFailureException").doesNotContain("secreto", "\tat ");
    }

    @Test
    @DisplayName("Un identificador de mensaje hostil no llega al registro")
    void handleError_shouldSanitizeMessageId(CapturedOutput output) {
        var technical = new ListenerExecutionFailedException("falla", new IllegalStateException(),
                amqpMessage("a\nERROR forjado"));

        assertThatThrownBy(() -> handler.handleError(amqpMessage("a\nERROR forjado"), null, springMessage(), technical))
                .isSameAs(technical);

        assertThat(output.getAll()).contains("messageId=invalid").doesNotContain("forjado");
    }

    @Test
    @DisplayName("Una cadena de causas con ciclo no cuelga el manejador")
    void handleError_shouldNotLoop_whenCauseChainCycles() {
        var first = new RuntimeException("a");
        var second = new RuntimeException("b", first);
        first.initCause(second);

        assertThatThrownBy(() -> handler.handleError(amqpMessage("m-1"), null, springMessage(), failure(first)))
                .isInstanceOf(ListenerExecutionFailedException.class);
    }

    @Test
    @DisplayName("La causa raíz se nombra por su clase simple")
    void rootCauseName_shouldReturnSimpleNameOfRoot() {
        assertThat(ProfileEventsErrorHandler.rootCauseName(new RuntimeException("x", new IllegalStateException())))
                .isEqualTo("IllegalStateException");
    }
}
