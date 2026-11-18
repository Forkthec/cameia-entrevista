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

import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/** Los registros de responder y reintentar no llevan la respuesta, la pregunta ni la evaluación. */
@AnswerFlowSupport.AnswerFlowTest
class AnswerLogContentTest extends AnswerFlowSupport {

    @Test
    @DisplayName("Ningún registro contiene la respuesta, la pregunta ni el texto de la evaluación")
    void answer_shouldNeverLogAnswerQuestionOrEvaluationText() {
        String marker = "RESPUESTA-UNICA-5521";
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String id = startedSession(UID_A, PROFILE_A);
            String turn1 = turnId(id, 1);
            model.enqueue(() -> {
                throw new LlmUnavailableException(new RuntimeException("caída"));
            });
            answer(UID_A, id, turn1, marker + " " + ANSWER, KEY);
            model.enqueue(AnswerFlowSupport::evaluation);
            reprocess(UID_A, id, turn1);
            answer(UID_A, id, turn1, marker, "otra-clave");
            answer(UID_A, id, turnId(id, 2), "   ", "k-vacia");
            state(UID_A, id);
        } finally {
            root.detachAppender(appender);
        }

        List<String> forbidden = new ArrayList<>(List.of(marker, ANSWER, "Buena estructura", "Responde lo pedido"));
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
