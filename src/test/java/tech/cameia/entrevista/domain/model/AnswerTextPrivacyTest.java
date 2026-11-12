package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.application.command.AnswerCommand;
import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.infrastructure.persistence.entity.TurnFeedbackEntity;
import tech.cameia.entrevista.presentation.dto.AnswerRequest;

/** El texto de la persona nunca aparece en la representación en texto de sus contenedores. */
class AnswerTextPrivacyTest {

    private static final String SECRET = "TEXTO-PRIVADO-DE-LA-PERSONA";

    @Test
    @DisplayName("El comando, la petición, la pregunta respondida y el intento no imprimen el texto")
    void toString_shouldNotLeakAnswerText() {
        AnswerCommand command = new AnswerCommand(new SessionCommand(UUID.randomUUID(), new FirebaseUid("uid-a")),
                UUID.randomUUID().toString(), SECRET, "key-1");
        AnswerRequest request = new AnswerRequest(UUID.randomUUID().toString(), SECRET);
        AnsweredQuestion answered = new AnsweredQuestion(1, SECRET, SECRET);
        AnswerAttempt attempt = AnswerAttempt.written(UUID.randomUUID(), SECRET, SECRET,
                Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneOffset.UTC));

        assertThat(command.toString()).isNotEmpty().doesNotContain(SECRET);
        assertThat(request.toString()).contains("turnId=").doesNotContain(SECRET);
        assertThat(answered.toString()).isNotEmpty().doesNotContain(SECRET);
        assertThat(attempt.toString()).contains("origin=TEXTO").doesNotContain(SECRET);
    }

    @Test
    @DisplayName("Un intento con texto en blanco no se crea")
    void answerAttempt_shouldRejectBlankText() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneOffset.UTC);

        assertThatThrownBy(() -> AnswerAttempt.written(UUID.randomUUID(), "   ", "Pregunta", clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("La entidad de la retroalimentación devuelve cada columna que recibió")
    void feedbackEntity_shouldExposeEveryColumn() {
        UUID id = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        UUID execution = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-12T15:00:00Z");

        TurnFeedbackEntity entity = new TurnFeedbackEntity(id, turn, execution, "{}", now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTurnId()).isEqualTo(turn);
        assertThat(entity.getExecutionId()).isEqualTo(execution);
        assertThat(entity.getFeedback()).isEqualTo("{}");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
    }
}
