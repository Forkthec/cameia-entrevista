package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.policy.QuestionAudioPolicy;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.SpeechUsageRepository;

/** La voz en el estado de la sesión (CA-5.15.1, CA-5.15.9). */
class QuestionAudioAvailabilityTest {

    private static final UUID SESSION = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-15T15:00:00Z");

    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final SpeechUsageRepository usage = mock(SpeechUsageRepository.class);
    private final List<InterviewQuestion> plan = new ArrayList<>();

    @BeforeEach
    void prepare() {
        UUID execution = UUID.randomUUID();
        for (int position = 1; position <= 5; position++) {
            plan.add(new InterviewQuestion(UUID.randomUUID(), SESSION, position, QuestionType.TECNICA, "P" + position,
                    execution, NOW));
        }
        when(questions.findBySession(SESSION)).thenReturn(plan);
    }

    private QuestionAudioAvailability availability(boolean enabled) {
        return new QuestionAudioAvailability(new SpeechSettings(enabled, 10, 900_000), new QuestionAudioPolicy(3),
                usage, questions);
    }

    private static SessionState inProgress(int pending) {
        return new SessionState(SESSION, SessionStatus.IN_PROGRESS, AnswerMode.TEXT, pending,
                new SessionState.PendingQuestion(UUID.randomUUID(), pending, "P" + pending, TurnStatus.PENDING, 0));
    }

    @Test
    @DisplayName("En curso: la pendiente y las anteriores traen sus usos restantes; las futuras no aparecen")
    void apply_shouldListShownQuestions() {
        when(usage.playsBySession(SESSION)).thenReturn(Map.of(plan.get(0).id(), 2, plan.get(2).id(), 3));

        SessionState state = availability(true).apply(inProgress(3));

        assertThat(state.audioEnabled()).isTrue();
        assertThat(state.audioPlaysRemaining()).containsExactlyInAnyOrderEntriesOf(Map.of(1, 1, 2, 3, 3, 0));
    }

    @Test
    @DisplayName("Con las cinco respondidas y la sesión en curso, las cinco se pueden escuchar")
    void apply_shouldListAll_whenNoPendingQuestion() {
        when(usage.playsBySession(SESSION)).thenReturn(Map.of());

        SessionState state = availability(true).apply(new SessionState(SESSION, SessionStatus.IN_PROGRESS,
                AnswerMode.TEXT, 5, null));

        assertThat(state.audioPlaysRemaining()).hasSize(5).containsValue(3);
    }

    @Test
    @DisplayName("CA-5.15.9: con la voz apagada no se ofrece audio ni se consulta la base")
    void apply_shouldDisable_whenSpeechIsOff() {
        SessionState state = availability(false).apply(inProgress(2));

        assertThat(state.audioEnabled()).isFalse();
        assertThat(state.audioPlaysRemaining()).isEmpty();
        verifyNoInteractions(usage);
    }

    @Test
    @DisplayName("Fuera de curso no se ofrece audio")
    void apply_shouldDisable_whenNotInProgress() {
        SessionState state = availability(true).apply(new SessionState(SESSION, SessionStatus.FINISHED,
                AnswerMode.TEXT, 5, null));

        assertThat(state.audioEnabled()).isFalse();
        verifyNoInteractions(usage);
    }

    @Test
    void disabled_shouldNeverOfferAudio() {
        assertThat(QuestionAudioAvailability.disabled().apply(inProgress(1)).audioEnabled()).isFalse();
    }

    @Test
    void speechSettings_shouldRejectInvalidLimits() {
        assertThatThrownBy(() -> new SpeechSettings(true, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpeechSettings(true, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
