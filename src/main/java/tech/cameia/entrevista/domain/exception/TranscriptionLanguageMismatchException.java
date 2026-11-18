package tech.cameia.entrevista.domain.exception;

import tech.cameia.entrevista.domain.model.InterviewLanguage;

/**
 * El audio está, con confianza suficiente, en un idioma distinto al de la entrevista (CA-5.12.10): no se registra
 * la respuesta, se descarta el audio y no se gasta un intento. El mensaje nombra el idioma de la entrevista.
 */
public class TranscriptionLanguageMismatchException extends BusinessException {

    /** Mensaje de una entrevista en inglés, literal de CA-5.12.10. */
    public static final String MESSAGE_ENGLISH = "Tu entrevista es en inglés. Responde en inglés.";

    /** Mensaje de una entrevista en español, literal de CA-5.12.10. */
    public static final String MESSAGE_SPANISH = "Tu entrevista es en español. Responde en español.";

    /**
     * Crea la excepción con el mensaje del idioma de la entrevista.
     *
     * @param interviewLanguage idioma de la entrevista
     */
    public TranscriptionLanguageMismatchException(InterviewLanguage interviewLanguage) {
        super(ErrorCode.TRANSCRIPTION_LANGUAGE_MISMATCH,
                interviewLanguage == InterviewLanguage.ENGLISH ? MESSAGE_ENGLISH : MESSAGE_SPANISH);
    }
}
