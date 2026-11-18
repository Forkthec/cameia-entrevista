package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/** El estado de la sesión tras responder: historial, estado del turno pendiente y reintentos usados. */
@AnswerFlowSupport.AnswerFlowTest
class SessionStateEndToEndTest extends AnswerFlowSupport {

    @Test
    @DisplayName("El historial lista las preguntas respondidas en orden y la pendiente trae su estado y reintentos")
    void state_shouldListHistoryInOrder_afterAnswers() {
        String id = startedSession(UID_A, PROFILE_A);
        for (int number = 1; number <= 2; number++) {
            model.enqueue(AnswerFlowSupport::evaluation);
            answer(UID_A, id, turnId(id, number), "Respuesta " + number, "k" + number);
        }

        ResponseEntity<String> response = state(UID_A, id);

        assertThat(status(response)).isEqualTo(200);
        assertThat((List<Integer>) JsonPath.read(response.getBody(), "$.history[*].number")).containsExactly(1, 2);
        assertThat((List<String>) JsonPath.read(response.getBody(), "$.history[*].answerText"))
                .containsExactly("Respuesta 1", "Respuesta 2");
        assertThat((List<String>) JsonPath.read(response.getBody(), "$.history[*].text"))
                .containsExactly(QUESTIONS[0], QUESTIONS[1]);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.pendingQuestion.number")).isEqualTo(3);
        assertThat((String) JsonPath.read(response.getBody(), "$.pendingQuestion.status")).isEqualTo("PENDING");
        assertThat((Integer) JsonPath.read(response.getBody(), "$.pendingQuestion.retriesUsed")).isZero();
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.currentQuestion")).isEqualTo(3);
        assertThat(response.getBody()).doesNotContain("feedback", "clasificacion", "motivo", "Buena estructura");
    }

    @Test
    @DisplayName("Tras recargar, el turno que falló sigue RETRYING, con los reintentos usados y su respuesta en el historial")
    void state_shouldShowRetryingAndRetriesUsed_afterReload() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        for (int attempt = 0; attempt < 3; attempt++) {
            model.enqueue(() -> {
                throw new LlmUnavailableException(new RuntimeException("caída"));
            });
        }
        answer(UID_A, id, turn1, ANSWER, KEY);
        reprocess(UID_A, id, turn1);
        reprocess(UID_A, id, turn1);

        ResponseEntity<String> response = state(UID_A, id);

        assertThat((String) JsonPath.read(response.getBody(), "$.pendingQuestion.status")).isEqualTo("RETRYING");
        assertThat((Integer) JsonPath.read(response.getBody(), "$.pendingQuestion.retriesUsed")).isEqualTo(2);
        assertThat((String) JsonPath.read(response.getBody(), "$.pendingQuestion.turnId")).isEqualTo(turn1);
        assertThat((List<String>) JsonPath.read(response.getBody(), "$.history[*].answerText"))
                .containsExactly(ANSWER);
        assertThat((Boolean) JsonPath.read(response.getBody(), "$.interviewFinished")).isFalse();
    }

    @Test
    @DisplayName("Una sesión sin respuestas trae el historial vacío y la pregunta 1 pendiente")
    void state_shouldShowEmptyHistory_beforeAnyAnswer() {
        String id = startedSession(UID_A, PROFILE_A);

        ResponseEntity<String> response = state(UID_A, id);

        assertThat((Integer) JsonPath.read(response.getBody(), "$.history.length()")).isZero();
        assertThat((String) JsonPath.read(response.getBody(), "$.pendingQuestion.status")).isEqualTo("PENDING");
    }
}
