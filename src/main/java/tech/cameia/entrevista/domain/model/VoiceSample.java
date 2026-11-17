package tech.cameia.entrevista.domain.model;

import java.util.List;
import java.util.Optional;

import tech.cameia.entrevista.domain.policy.FillerWords;

/**
 * Los cuatro datos de voz de una respuesta, calculados con los tiempos de sus palabras.
 *
 * @param words cantidad de palabras con tiempos; al menos 1
 * @param spanMs fin de la última palabra menos inicio de la primera; mayor que 0
 * @param pauseMs suma de los silencios de {@link #MIN_PAUSE_MS} o más entre palabras seguidas; entre 0 y
 *        {@code spanMs}
 * @param fillers muletillas; entre 0 y {@code words}
 */
public record VoiceSample(int words, int spanMs, int pauseMs, int fillers) {

    /** Silencio mínimo, en milisegundos, para contarlo como pausa. */
    public static final long MIN_PAUSE_MS = 500;

    /** Valida los rangos de los cuatro datos. */
    public VoiceSample {
        if (words < 1 || spanMs < 1 || pauseMs < 0 || pauseMs > spanMs || fillers < 0 || fillers > words) {
            throw new IllegalArgumentException("Datos de voz fuera de rango");
        }
    }

    /**
     * Calcula los datos de una respuesta. Vacío si no hay palabras o el tramo de habla es 0: la respuesta se guarda
     * igual y no cuenta en las métricas de voz.
     *
     * @param words palabras con tiempos, en el orden en que se dijeron
     * @param language idioma de la entrevista, que fija la lista de muletillas
     * @return los datos de voz, o vacío si no se pueden calcular
     */
    public static Optional<VoiceSample> from(List<TimedWord> words, InterviewLanguage language) {
        if (words == null || words.isEmpty()) {
            return Optional.empty();
        }
        long span = words.get(words.size() - 1).endMs() - words.get(0).startMs();
        if (span <= 0 || span > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        long pauses = 0;
        int fillers = FillerWords.isFiller(words.get(0).text(), language) ? 1 : 0;
        for (int i = 1; i < words.size(); i++) {
            long gap = words.get(i).startMs() - words.get(i - 1).endMs();
            if (gap >= MIN_PAUSE_MS) {
                pauses += gap;
            }
            if (FillerWords.isFiller(words.get(i).text(), language)) {
                fillers++;
            }
        }
        // Con palabras solapadas la suma de silencios no puede pasar del tramo total.
        return Optional.of(new VoiceSample(words.size(), (int) span, (int) Math.min(pauses, span), fillers));
    }
}
