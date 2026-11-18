package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Los registros del cierre y de la consolidación fallida no llevan la evaluación ni el texto de las respuestas. */
@AnswerFlowSupport.AnswerFlowTest
class ClosureLogContentTest extends AnswerFlowSupport {

    @AfterEach
    void removeCloseBlocker() {
        jdbc.execute("DROP TRIGGER IF EXISTS trg_bloquear_cierre ON microentrevista.sesion");
        jdbc.execute("DROP FUNCTION IF EXISTS microentrevista.fn_bloquear_cierre()");
    }

    @Test
    @DisplayName("Ningún registro del cierre, fallido o no, contiene la evaluación ni la respuesta")
    void closure_shouldNeverLogFeedbackOrAnswerText() {
        String marker = "RESPUESTA-CIERRE-8842";
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String id = startedSession(UID_A, PROFILE_A);
            for (int number = 1; number <= 4; number++) {
                model.enqueue(AnswerFlowSupport::evaluation);
                answer(UID_A, id, turnId(id, number), marker + number, "clave-" + number);
            }
            jdbc.execute("""
                    CREATE FUNCTION microentrevista.fn_bloquear_cierre() RETURNS trigger AS $$
                    BEGIN
                        IF NEW.estado = 'FINALIZADA' THEN
                            RAISE EXCEPTION 'cierre bloqueado por la prueba';
                        END IF;
                        RETURN NEW;
                    END $$ LANGUAGE plpgsql""");
            jdbc.execute("CREATE TRIGGER trg_bloquear_cierre BEFORE UPDATE ON microentrevista.sesion FOR EACH ROW"
                    + " EXECUTE FUNCTION microentrevista.fn_bloquear_cierre()");
            model.enqueue(AnswerFlowSupport::evaluation);
            answer(UID_A, id, turnId(id, 5), marker + 5, "clave-5");
            consolidate(UID_A, id);
            removeCloseBlocker();
            consolidate(UID_A, id);
        } finally {
            root.detachAppender(appender);
        }

        List<String> forbidden = new ArrayList<>(List.of(marker, "Buena estructura", "Responde lo pedido"));
        forbidden.addAll(List.of(QUESTIONS));
        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list.stream().map(ILoggingEvent::getFormattedMessage))
                .anyMatch(message -> message.contains("SESSION_CONSOLIDATION_FAILED"));
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
