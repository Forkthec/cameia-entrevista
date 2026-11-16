package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.QuestionAudioLimitReachedException;
import tech.cameia.entrevista.domain.exception.QuestionNotAvailableException;
import tech.cameia.entrevista.domain.exception.SpeechBusyException;
import tech.cameia.entrevista.domain.exception.SpeechDisabledException;
import tech.cameia.entrevista.domain.exception.SpeechMonthlyLimitReachedException;
import tech.cameia.entrevista.domain.exception.SpeechTimeoutException;
import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SpeechAudio;
import tech.cameia.entrevista.domain.model.SpeechDirection;
import tech.cameia.entrevista.domain.policy.QuestionAudioPolicy;
import tech.cameia.entrevista.domain.policy.SpeechDirectionPolicy;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.SpeechDirectorPort;
import tech.cameia.entrevista.domain.port.SpeechSynthesisPort;
import tech.cameia.entrevista.domain.port.SpeechUsageRepository;

/**
 * Caso de uso: escuchar una pregunta ya mostrada (HU-5.15).
 *
 * <p>Orden de las comprobaciones, todas antes de llamar a un servicio externo: sesión propia (404
 * {@code SESSION_NOT_FOUND}), número de pregunta (404 {@code QUESTION_NOT_FOUND}), estado de la sesión (409), voz
 * encendida (503 {@code SPEECH_DISABLED}), pregunta ya mostrada (409 {@code QUESTION_NOT_AVAILABLE}), texto (422
 * {@code SPEECH_TEXT_*}), tope mensual (503 {@code SPEECH_MONTHLY_LIMIT_REACHED}), síntesis simultáneas (503
 * {@code SPEECH_BUSY}) y uso de audio (409 {@code QUESTION_AUDIO_LIMIT_REACHED}).</p>
 *
 * <p>Después, sin transacción abierta: un modelo de lenguaje coloca las etiquetas de voz según el tono y el momento
 * de la entrevista (si falla o propone algo que cambia la pregunta, se lee tal cual) y el servicio de voz genera el
 * audio. Si la voz falla, se devuelven el uso de audio y los caracteres reservados (CA-5.15.8, CA-5.15.23). Si la
 * entrevista dejó de estar en curso mientras se generaba, no se entrega el audio (CA-5.15.25); el uso queda gastado,
 * porque la voz ya procesó el texto, igual que si la persona cierra la pestaña (CA-5.15.24).</p>
 *
 * <p>Escuchar no cambia la sesión ni el turno, no toma su candado y no mueve la última actividad (CA-5.15.14,
 * CA-5.15.18). No se guardan el audio ni el texto enviado: solo los contadores (CA-5.15.15).</p>
 */
@Service
public class ListenQuestionService {

    private static final Logger log = LoggerFactory.getLogger(ListenQuestionService.class);

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewTurnRepository turns;
    private final SpeechUsageRepository usage;
    private final SpeechDirectorPort director;
    private final SpeechSynthesisPort synthesis;
    private final QuestionAudioPolicy policy;
    private final SpeechDirectionPolicy directionPolicy;
    private final SpeechSettings settings;
    private final Semaphore slots;
    private final Clock clock;

    @Autowired
    @SuppressWarnings("java:S107")
    ListenQuestionService(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewTurnRepository turns, SpeechUsageRepository usage, SpeechDirectorPort director,
            SpeechSynthesisPort synthesis, QuestionAudioPolicy policy, SpeechDirectionPolicy directionPolicy,
            SpeechSettings settings, Clock clock) {
        this.sessions = sessions;
        this.questions = questions;
        this.turns = turns;
        this.usage = usage;
        this.director = director;
        this.synthesis = synthesis;
        this.policy = policy;
        this.directionPolicy = directionPolicy;
        this.settings = settings;
        this.slots = new Semaphore(settings.maxConcurrent());
        this.clock = clock;
    }

    /**
     * Genera el audio de una pregunta ya mostrada.
     *
     * @param command sesión pedida y dueño
     * @param rawNumber segmento {@code {number}} de la ruta
     * @return el audio de la pregunta, que no se guarda
     */
    public SpeechAudio listen(SessionCommand command, String rawNumber) {
        InterviewSession session = GetSessionStateService.findOwned(sessions, command);
        int number = policy.parseNumber(rawNumber);
        session.requireAcceptsAnswers();
        if (!settings.enabled()) {
            throw new SpeechDisabledException();
        }
        InterviewQuestion question = shownQuestion(session, number);
        String text = policy.requireSpeakable(question.originalText());
        int characters = text.codePointCount(0, text.length());

        YearMonth month = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        if (!usage.reserveCharacters(month, characters, settings.monthlyCharacterCap())) {
            throw new SpeechMonthlyLimitReachedException();
        }
        if (!slots.tryAcquire()) {
            usage.releaseCharacters(month, characters);
            throw new SpeechBusyException();
        }
        try {
            if (!usage.reservePlay(question.id(), policy.maxPlays())) {
                usage.releaseCharacters(month, characters);
                throw new QuestionAudioLimitReachedException();
            }
            SpeechAudio audio = generate(session, question, text, month, characters);
            // CA-5.15.25: si la entrevista cambió mientras se generaba, no se entrega el audio.
            sessions.findByIdAndOwner(session.id(), session.owner()).orElseThrow().requireAcceptsAnswers();
            return audio;
        } finally {
            slots.release();
        }
    }

    // La pregunta pendiente o una anterior; nunca una futura (CA-5.15.5).
    private InterviewQuestion shownQuestion(InterviewSession session, int number) {
        Optional<InterviewTurn> open = turns.findOpenBySession(session.id());
        Optional<InterviewQuestion> wanted = Optional.empty();
        int shownUpTo = InterviewQuestion.TOTAL;
        for (InterviewQuestion candidate : questions.findBySession(session.id())) {
            if (open.isPresent() && candidate.id().equals(open.get().questionId())) {
                shownUpTo = candidate.position();
            }
            if (candidate.position() == number) {
                wanted = Optional.of(candidate);
            }
        }
        policy.requireShown(number, shownUpTo);
        return wanted.orElseThrow(QuestionNotAvailableException::new);
    }

    private SpeechAudio generate(InterviewSession session, InterviewQuestion question, String text, YearMonth month,
            int characters) {
        Instant started = clock.instant();
        SessionConfiguration configuration = session.configuration();
        SpeechDirection direction;
        SpeechAudio audio;
        try {
            direction = direct(question, text, configuration);
            audio = synthesis.synthesize(direction, configuration.language());
        } catch (SpeechUnavailableException | SpeechTimeoutException failure) {
            giveBack(question, month, characters, failure.getErrorCode());
            log.warn("Voz de la pregunta no disponible [sessionId={}, pregunta={}, code={}, causa={}]", session.id(),
                    question.position(), failure.getErrorCode(), reasonOf(failure));
            throw failure;
        } catch (RuntimeException unexpected) {
            giveBack(question, month, characters, ErrorCode.SPEECH_UNAVAILABLE);
            log.error("Fallo imprevisto al generar la voz de la pregunta [sessionId={}, pregunta={}]", session.id(),
                    question.position(), unexpected);
            throw new SpeechUnavailableException("fallo imprevisto", unexpected);
        }
        log.info("Voz de la pregunta generada [sessionId={}, pregunta={}, bytes={}, modelo={}, dirigida={}, estilo={},"
                + " ms={}]", session.id(), question.position(), audio.content().length, audio.model(),
                direction.directed(), direction.style(), Duration.between(started, clock.instant()).toMillis());
        return audio;
    }

    private SpeechDirection direct(InterviewQuestion question, String text, SessionConfiguration configuration) {
        SpeechDirectionPolicy.Rules rules = directionPolicy.rulesFor(configuration.tone());
        SpeechDirectorPort.Request request = new SpeechDirectorPort.Request(text, question.position(),
                configuration.tone(), configuration.language(), rules);
        return director.direct(request)
                .flatMap(proposal -> directionPolicy.accept(text, configuration.tone(), proposal.text(),
                        proposal.style()))
                .orElseGet(() -> directionPolicy.plain(text, configuration.tone()));
    }

    // CA-5.15.23: el uso y los caracteres vuelven; si la base falla al devolverlos, se registra y la persona ve el
    // error de la voz.
    private void giveBack(InterviewQuestion question, YearMonth month, int characters, ErrorCode code) {
        try {
            usage.releasePlay(question.id());
            usage.releaseCharacters(month, characters);
        } catch (RuntimeException notReturned) {
            log.error("No se pudo devolver el uso de audio [code={}, preguntaId={}]", code, question.id(),
                    notReturned);
        }
    }

    private static String reasonOf(BusinessException failure) {
        if (failure instanceof SpeechUnavailableException unavailable) {
            return unavailable.getReason();
        }
        return ((SpeechTimeoutException) failure).getReason();
    }
}
