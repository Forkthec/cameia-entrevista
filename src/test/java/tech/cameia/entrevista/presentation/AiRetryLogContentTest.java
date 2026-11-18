package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/** Los registros del ciclo de reintentos no llevan el prompt, la salida del modelo, la llave ni el texto de la persona. */
@AnswerFlowSupport.AnswerFlowTest
class AiRetryLogContentTest extends AnswerFlowSupport {

    @Test
    @DisplayName("Ningún registro del ciclo contiene prompt, salida del modelo, llave ni texto de la persona")
    void cycle_shouldNeverLogPromptOutputKeyOrPersonText() {
        String marker = "RESPUESTA-UNICA-7731";
        String modelOutput = "SALIDA-DEL-MODELO-9917";
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String id = startedSession(UID_A, PROFILE_A);
            String turn1 = turnId(id, 1);
            model.fallbackAvailable(true);
            model.enqueue(() -> {
                throw new LlmUnavailableException(new RuntimeException("caída"));
            });
            model.enqueue(() -> response(modelOutput));
            model.enqueue(() -> {
                throw new LlmLimitReachedException(new RuntimeException("429"));
            });
            answer(UID_A, id, turn1, marker + " " + ANSWER, KEY);
            model.enqueueCycle(() -> {
                throw new LlmUnavailableException(new RuntimeException("caída"));
            });
            reprocess(UID_A, id, turn1);
        } finally {
            root.detachAppender(appender);
        }

        List<String> forbidden = new ArrayList<>(List.of(marker, ANSWER, modelOutput, "clave-de-prueba",
                "Buena estructura", "Responde lo pedido"));
        forbidden.addAll(List.of(QUESTIONS));
        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            String text = event.getFormattedMessage() + " " + stack(event.getThrowableProxy());
            for (String secret : forbidden) {
                assertThat(text).as(event.getLoggerName()).doesNotContain(secret);
            }
        }
    }

    private static String stack(IThrowableProxy proxy) {
        StringBuilder text = new StringBuilder();
        for (IThrowableProxy current = proxy; current != null; current = current.getCause()) {
            text.append(current.getMessage()).append(' ');
        }
        return text.toString();
    }
}
