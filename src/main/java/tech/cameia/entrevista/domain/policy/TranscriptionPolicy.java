package tech.cameia.entrevista.domain.policy;

import java.text.Normalizer;
import java.util.Locale;

import tech.cameia.entrevista.domain.exception.TranscriptionEmptyException;
import tech.cameia.entrevista.domain.exception.TranscriptionLanguageMismatchException;
import tech.cameia.entrevista.domain.exception.TranscriptionTooLongException;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;

/**
 * Decide si una transcripción sirve como respuesta (HU-5.12). Las reglas se aplican en este orden, y la primera
 * que falla decide el rechazo:
 *
 * <ol>
 *   <li>Vacía o solo con espacios: {@link TranscriptionEmptyException} (CA-5.12.7).</li>
 *   <li>Idioma distinto al de la entrevista detectado con confianza igual o mayor al umbral:
 *       {@link TranscriptionLanguageMismatchException} (CA-5.12.10).</li>
 *   <li>Más de {@value AnswerAttempt#MAX_TEXT_LENGTH} caracteres: {@link TranscriptionTooLongException}
 *       (CA-5.12.9); exactamente {@value AnswerAttempt#MAX_TEXT_LENGTH} se acepta (CA-5.12.11).</li>
 * </ol>
 *
 * <p>Los caracteres se cuentan como puntos de código Unicode tras normalizar a NFC y recortar los espacios de los
 * extremos, la misma regla que usa el Cliente Web para la respuesta escrita.</p>
 *
 * <p>El idioma va antes que el largo: a una persona que habló en otro idioma le sirve más saber eso que saber que
 * habló de más. Una respuesta que mezcla términos técnicos del otro idioma no cuenta como idioma distinto: el
 * proveedor detecta el idioma dominante del audio, y la confianza sobre el umbral filtra las mezclas.</p>
 */
public final class TranscriptionPolicy {

    private final double languageConfidenceThreshold;

    /**
     * Crea la regla con el umbral de confianza configurado.
     *
     * @param languageConfidenceThreshold confianza mínima, de 0.0 a 1.0, para dar por distinto el idioma del audio
     * @throws IllegalArgumentException si el umbral está fuera de rango
     */
    public TranscriptionPolicy(double languageConfidenceThreshold) {
        if (languageConfidenceThreshold < 0.0 || languageConfidenceThreshold > 1.0) {
            throw new IllegalArgumentException("El umbral de confianza del idioma debe estar entre 0.0 y 1.0");
        }
        this.languageConfidenceThreshold = languageConfidenceThreshold;
    }

    /**
     * Valida la transcripción y devuelve el texto que se registra como respuesta.
     *
     * @param transcription lo que devolvió el servicio de transcripción
     * @param interviewLanguage idioma de la entrevista
     * @return el texto normalizado a NFC y sin espacios en los extremos, de 1 a 4000 caracteres
     * @throws TranscriptionEmptyException si no hay texto
     * @throws TranscriptionLanguageMismatchException si el audio está en otro idioma
     * @throws TranscriptionTooLongException si supera los 4000 caracteres
     */
    public String accept(Transcription transcription, InterviewLanguage interviewLanguage) {
        String text = Normalizer.normalize(transcription.text(), Normalizer.Form.NFC).strip();
        if (text.isEmpty()) {
            throw new TranscriptionEmptyException();
        }
        if (isOtherLanguage(transcription, interviewLanguage)) {
            throw new TranscriptionLanguageMismatchException(interviewLanguage);
        }
        if (text.codePointCount(0, text.length()) > AnswerAttempt.MAX_TEXT_LENGTH) {
            throw new TranscriptionTooLongException();
        }
        return text;
    }

    /** @return el umbral de confianza configurado */
    public double languageConfidenceThreshold() {
        return languageConfidenceThreshold;
    }

    // Sin idioma o sin confianza informados no se puede afirmar que sea otro idioma: se acepta.
    private boolean isOtherLanguage(Transcription transcription, InterviewLanguage interviewLanguage) {
        if (transcription.detectedLanguage() == null || transcription.languageConfidence() == null) {
            return false;
        }
        String detected = baseLanguage(transcription.detectedLanguage());
        return !detected.equals(baseLanguage(interviewLanguage.code()))
                && transcription.languageConfidence() >= languageConfidenceThreshold;
    }

    /**
     * Idioma base de un código: {@code es-CO} y {@code es} son {@code es}; {@code en_us} es {@code en}.
     *
     * @param code código de idioma BCP-47 o ISO 639-1, con o sin región
     * @return las letras del idioma en minúsculas, sin la región
     */
    public static String baseLanguage(String code) {
        String lower = code.strip().toLowerCase(Locale.ROOT);
        int separator = lower.indexOf('-') >= 0 ? lower.indexOf('-') : lower.indexOf('_');
        return separator > 0 ? lower.substring(0, separator) : lower;
    }
}
