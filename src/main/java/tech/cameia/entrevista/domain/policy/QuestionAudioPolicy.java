package tech.cameia.entrevista.domain.policy;

import java.util.regex.Pattern;

import tech.cameia.entrevista.domain.exception.QuestionNotAvailableException;
import tech.cameia.entrevista.domain.exception.QuestionNotFoundException;
import tech.cameia.entrevista.domain.exception.SpeechTextEmptyException;
import tech.cameia.entrevista.domain.exception.SpeechTextTooLongException;
import tech.cameia.entrevista.domain.model.InterviewQuestion;

/**
 * Reglas para escuchar una pregunta (HU-5.15): qué número de pregunta es válido, qué pregunta ya se mostró y qué texto
 * puede enviarse a la voz.
 */
public final class QuestionAudioPolicy {

    /** Máximo de puntos de código que se envían a la voz; la base ya limita la pregunta a 500 (CA-5.15.13). */
    public static final int MAX_TEXT_LENGTH = InterviewQuestion.MAX_TEXT_LENGTH;

    // Solo un dígito ASCII de 1 a 5, sin signos, ceros a la izquierda, espacios ni otras cifras (CA-5.15.12).
    private static final Pattern NUMBER = Pattern.compile("[1-5]");

    private final int maxPlays;

    /**
     * Crea la regla.
     *
     * @param maxPlays usos de audio por pregunta; al menos 1
     * @throws IllegalArgumentException si el máximo es menor que 1
     */
    public QuestionAudioPolicy(int maxPlays) {
        if (maxPlays < 1) {
            throw new IllegalArgumentException("El máximo de usos de audio por pregunta debe ser al menos 1");
        }
        this.maxPlays = maxPlays;
    }

    /** @return usos de audio por pregunta */
    public int maxPlays() {
        return maxPlays;
    }

    /**
     * Lee el número de pregunta de la ruta.
     *
     * @param raw segmento {@code {number}} de la ruta, ya decodificado
     * @return el número, de 1 a 5
     * @throws QuestionNotFoundException si no es exactamente un dígito de 1 a 5
     */
    public int parseNumber(String raw) {
        if (raw == null || !NUMBER.matcher(raw).matches()) {
            throw new QuestionNotFoundException();
        }
        return raw.charAt(0) - '0';
    }

    /**
     * Comprueba que la pregunta ya se mostró: la pendiente o una anterior.
     *
     * @param number pregunta pedida
     * @param shownUpTo última pregunta mostrada (la pendiente, o 5 si ya se respondieron todas)
     * @throws QuestionNotAvailableException si la pregunta es posterior
     */
    public void requireShown(int number, int shownUpTo) {
        if (number > shownUpTo) {
            throw new QuestionNotAvailableException();
        }
    }

    /**
     * Comprueba que el texto se puede enviar a la voz, sin alterarlo.
     *
     * @param text texto guardado de la pregunta
     * @return el mismo texto
     * @throws SpeechTextEmptyException si está vacío o solo tiene espacios
     * @throws SpeechTextTooLongException si supera {@value #MAX_TEXT_LENGTH} puntos de código
     */
    public String requireSpeakable(String text) {
        if (text == null || text.isBlank()) {
            throw new SpeechTextEmptyException();
        }
        if (text.codePointCount(0, text.length()) > MAX_TEXT_LENGTH) {
            throw new SpeechTextTooLongException();
        }
        return text;
    }

    /**
     * Usos de audio que le quedan a una pregunta.
     *
     * @param used usos gastados
     * @return de 0 al máximo
     */
    public int remaining(int used) {
        return Math.max(0, Math.min(maxPlays, maxPlays - used));
    }
}
