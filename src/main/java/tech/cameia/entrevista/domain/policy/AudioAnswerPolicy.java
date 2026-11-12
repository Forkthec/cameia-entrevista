package tech.cameia.entrevista.domain.policy;

import java.time.Duration;
import java.util.Optional;

import tech.cameia.entrevista.domain.exception.AudioInvalidFormatException;
import tech.cameia.entrevista.domain.exception.AudioTooLargeException;
import tech.cameia.entrevista.domain.exception.AudioTooLongException;
import tech.cameia.entrevista.domain.exception.AudioTooShortException;
import tech.cameia.entrevista.domain.model.AudioInspection;

/**
 * Límites de una respuesta por voz (HU-5.8), en el orden en que se comprueban: tamaño (413),
 * formato (415) y duración (422).
 */
public final class AudioAnswerPolicy {

    /** Tamaño máximo del audio: 10 MiB exactos se aceptan (CA-5.8.12) y un byte más se rechaza (CA-5.8.13). */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /**
     * Duración máxima. La interfaz corta a los 3:00 por reloj y el contenedor puede marcar unas décimas más: se
     * tolera hasta 181 s, de modo que 3:00 se acepta (CA-5.8.7) y 3:02 se rechaza (CA-5.8.8).
     */
    public static final Duration MAX_DURATION = Duration.ofSeconds(181);

    /**
     * Duración mínima. La interfaz exige 2 s de voz detectada (CA-5.8.9); un audio de menos de 1,5 s no puede
     * traerlos y se rechaza sin pedir la transcripción. El margen absorbe el redondeo de las marcas de tiempo.
     */
    public static final Duration MIN_DURATION = Duration.ofMillis(1500);

    /**
     * Comprueba que el audio no supera el tamaño. Va antes de leer el contenido; que falte o venga vacío lo responde
     * la validación de campos del caso de uso (422 {@code VALIDATION_FAILED} con {@code AUDIO_REQUIRED}).
     *
     * @param size tamaño en bytes
     * @throws AudioTooLargeException si supera {@link #MAX_BYTES}
     */
    public void checkSize(long size) {
        if (size > MAX_BYTES) {
            throw new AudioTooLargeException();
        }
    }

    /**
     * Comprueba el formato reconocido y la duración.
     *
     * @param inspection resultado de leer el contenido; vacío si no es un formato admitido
     * @return el resultado, ya validado
     * @throws AudioInvalidFormatException si el contenido no es un formato admitido
     * @throws AudioTooLongException si dura más de {@link #MAX_DURATION}
     * @throws AudioTooShortException si dura menos de {@link #MIN_DURATION}
     */
    public AudioInspection checkContent(Optional<AudioInspection> inspection) {
        AudioInspection found = inspection.orElseThrow(AudioInvalidFormatException::new);
        if (found.duration().compareTo(MAX_DURATION) > 0) {
            throw new AudioTooLongException();
        }
        if (found.duration().compareTo(MIN_DURATION) < 0) {
            throw new AudioTooShortException();
        }
        return found;
    }
}
