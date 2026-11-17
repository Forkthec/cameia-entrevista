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
import org.springframework.context.annotation.Import;

import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/** Los registros de generar y leer el reporte no llevan preguntas, respuestas ni la salida del modelo. */
@AnswerFlowSupport.AnswerFlowTest
@Import(ReportFlowSupport.Transcribers.class)
class ReportLogContentTest extends ReportFlowSupport {

    @Test
    @DisplayName("Ningún registro contiene el texto de las preguntas ni de las respuestas ni la salida de la IA")
    void report_shouldNeverLogQuestionsAnswersOrModelOutput() {
        String id = finishedMixedSession(UID_A, PROFILE_A);
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            model.enqueue(() -> {
                throw new LlmUnavailableException(new RuntimeException("caída " + ANSWER));
            });
            model.enqueue(() -> response("no es JSON " + SPOKEN));
            model.enqueue(() -> response(REPORT_OK));
            report(UID_A, id);
            report(UID_A, id);
        } finally {
            root.detachAppender(appender);
        }

        List<String> forbidden = new ArrayList<>(List.of(ANSWER, SPOKEN, "precision_claridad", "carisma_verbal"));
        forbidden.addAll(List.of(QUESTIONS));
        List<String> texts = appender.list.stream().map(event -> event.getFormattedMessage() + " "
                + stack(event.getThrowableProxy())).toList();
        assertThat(texts).isNotEmpty();
        for (String text : texts) {
            for (String secret : forbidden) {
                assertThat(text).doesNotContain(secret);
            }
        }
        assertThat(texts).anyMatch(text -> text.contains("Reporte de Evaluación emitido")
                && text.contains("sessionId=" + id));
    }

    private static String stack(IThrowableProxy proxy) {
        StringBuilder text = new StringBuilder();
        for (IThrowableProxy current = proxy; current != null; current = current.getCause()) {
            text.append(current.getMessage()).append(' ');
        }
        return text.toString();
    }
}
