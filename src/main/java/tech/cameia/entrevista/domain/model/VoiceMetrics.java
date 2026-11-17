package tech.cameia.entrevista.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Las tres métricas de voz de la sesión. Las tres son nulas juntas («sin resultado») o las tres tienen valor.
 *
 * @param speechRate Ritmo de Habla, en palabras por minuto, entero
 * @param pauseTime Tiempo de Pausas, en porcentaje del tiempo hablado, con 1 decimal
 * @param hesitationRate Tasa de Vacilación, en muletillas por cada 100 palabras, con 1 decimal
 */
public record VoiceMetrics(Integer speechRate, BigDecimal pauseTime, BigDecimal hesitationRate) {

    /** Sin respuestas por voz: las tres métricas quedan «sin resultado». */
    public static final VoiceMetrics NONE = new VoiceMetrics(null, null, null);

    private static final long MILLIS_PER_MINUTE = 60_000L;

    private static final long PERCENT = 100L;

    private static final int DECIMALS = 1;

    /** Rechaza una mezcla de métricas con valor y sin él. */
    public VoiceMetrics {
        boolean allNull = speechRate == null && pauseTime == null && hesitationRate == null;
        boolean allPresent = speechRate != null && pauseTime != null && hesitationRate != null;
        if (!allNull && !allPresent) {
            throw new IllegalArgumentException("Las tres métricas de voz van juntas o ninguna");
        }
    }

    /**
     * Suma los datos de las respuestas con datos de voz y calcula las tres métricas con redondeo mitad hacia arriba.
     * Un ritmo que no cabe en un entero (imposible con tiempos reales) se limita al mayor entero en lugar de fallar.
     *
     * @param samples datos de voz de las respuestas que los tienen
     * @return las métricas, o {@link #NONE} si no hay ninguna muestra
     */
    public static VoiceMetrics of(List<VoiceSample> samples) {
        if (samples.isEmpty()) {
            return NONE;
        }
        long words = samples.stream().mapToLong(VoiceSample::words).sum();
        long span = samples.stream().mapToLong(VoiceSample::spanMs).sum();
        long pauses = samples.stream().mapToLong(VoiceSample::pauseMs).sum();
        long fillers = samples.stream().mapToLong(VoiceSample::fillers).sum();
        BigDecimal rate = BigDecimal.valueOf(words * MILLIS_PER_MINUTE)
                .divide(BigDecimal.valueOf(span), 0, RoundingMode.HALF_UP);
        int speechRate = rate.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE
                : rate.intValueExact();
        return new VoiceMetrics(speechRate,
                BigDecimal.valueOf(pauses * PERCENT).divide(BigDecimal.valueOf(span), DECIMALS, RoundingMode.HALF_UP),
                BigDecimal.valueOf(fillers * PERCENT).divide(BigDecimal.valueOf(words), DECIMALS,
                        RoundingMode.HALF_UP));
    }
}
