package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/** Ninguna respuesta de ningún endpoint, en ningún estado de la sesión, lleva la retroalimentación. */
@AnswerFlowSupport.AnswerFlowTest
class NoFeedbackExposureTest extends AnswerFlowSupport {

    private static final List<String> FORBIDDEN = List.of("\"accion\"", "\"clasificacion\"", "\"motivo\"",
            "\"feedback\"", "\"score\"", "\"star\"", "Buena estructura", "Responde lo pedido");

    private ResponseEntity<String> withHints(HttpMethod method, String path, String uid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", uid);
        headers.set("X-Include-Feedback", "true");
        return client.exchange(path + "?feedback=true", method, new HttpEntity<>("{\"feedback\":true}", headers),
                String.class);
    }

    @Test
    @DisplayName("Los endpoints nunca exponen la retroalimentación, en ningún estado ni con pistas en la petición")
    void endpoints_shouldNeverExposeFeedback() {
        List<ResponseEntity<String>> responses = new ArrayList<>();
        String configured = configure(UID_B, PROFILE_B);
        responses.add(state(UID_B, configured));
        responses.add(call(HttpMethod.DELETE, BASE + "/" + configured, UID_B, null, null));
        String id = startedSession(UID_A, PROFILE_A);
        model.enqueue(() -> {
            throw new LlmUnavailableException(new RuntimeException("caída"));
        });
        responses.add(answer(UID_A, id, turnId(id, 1), ANSWER, "clave-1"));
        model.enqueue(AnswerFlowSupport::evaluation);
        responses.add(reprocess(UID_A, id, turnId(id, 1)));
        responses.add(state(UID_A, id));
        for (int number = 2; number <= 5; number++) {
            model.enqueue(AnswerFlowSupport::evaluation);
            responses.add(answer(UID_A, id, turnId(id, number), ANSWER, "clave-" + number));
        }
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isEqualTo(5);
        for (String status : new String[] {"FINALIZADA", "EVALUANDO", "ABANDONADA"}) {
            forceStatus(id, status);
            responses.add(state(UID_A, id));
            responses.add(withHints(HttpMethod.GET, BASE + "/" + id + "/state", UID_A));
        }
        forceStatus(id, "EVALUANDO");
        responses.add(consolidate(UID_A, id));
        responses.add(withHints(HttpMethod.POST, BASE + "/" + id + "/consolidation", UID_A));
        responses.add(withHints(HttpMethod.POST, BASE + "/" + id + "/consolidation", UID_B));

        assertThat(responses).hasSize(18);
        for (ResponseEntity<String> response : responses) {
            String body = String.valueOf(response.getBody());
            for (String forbidden : FORBIDDEN) {
                assertThat(body).as(response.getStatusCode() + " " + body).doesNotContain(forbidden);
            }
        }
    }
}
