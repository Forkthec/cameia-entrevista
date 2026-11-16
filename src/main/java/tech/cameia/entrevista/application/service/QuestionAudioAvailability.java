package tech.cameia.entrevista.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.policy.QuestionAudioPolicy;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.SpeechUsageRepository;

/**
 * Agrega al estado de la sesión si se puede escuchar la pregunta y cuántos usos de audio le quedan a cada pregunta
 * mostrada (CA-5.15.1, CA-5.15.9). Solo lee: no cuenta como actividad.
 */
@Component
public class QuestionAudioAvailability {

    private final SpeechSettings settings;
    private final QuestionAudioPolicy policy;
    private final SpeechUsageRepository usage;
    private final InterviewQuestionRepository questions;

    @Autowired
    QuestionAudioAvailability(SpeechSettings settings, QuestionAudioPolicy policy, SpeechUsageRepository usage,
            InterviewQuestionRepository questions) {
        this.settings = settings;
        this.policy = policy;
        this.usage = usage;
        this.questions = questions;
    }

    /**
     * Disponibilidad que nunca ofrece el audio, para quien construye el estado sin la voz.
     *
     * @return una disponibilidad apagada
     */
    static QuestionAudioAvailability disabled() {
        return new QuestionAudioAvailability(new SpeechSettings(false, 1, 1), new QuestionAudioPolicy(1), null, null);
    }

    /**
     * Agrega la voz al estado.
     *
     * @param state estado calculado
     * @return el estado con {@code audioEnabled} y los usos restantes; sin audio si la voz está apagada o la sesión no
     *         está en curso
     */
    SessionState apply(SessionState state) {
        if (!settings.enabled() || state.status() != SessionStatus.IN_PROGRESS) {
            return state.withAudio(false, Map.of());
        }
        int shownUpTo = state.pending() != null ? state.pending().number() : state.currentQuestion();
        List<InterviewQuestion> planned = questions.findBySession(state.sessionId());
        Map<UUID, Integer> plays = usage.playsBySession(state.sessionId());
        Map<Integer, Integer> remaining = new HashMap<>();
        for (InterviewQuestion question : planned) {
            if (question.position() <= shownUpTo) {
                remaining.put(question.position(), policy.remaining(plays.getOrDefault(question.id(), 0)));
            }
        }
        return state.withAudio(true, remaining);
    }
}
