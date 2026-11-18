package tech.cameia.entrevista.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Promedio de cada métrica de contenido de la sesión. Las tres son nulas juntas («sin resultado») o las tres tienen
 * valor.
 *
 * @param precision Precisión y Claridad, de 1,00 a 5,00
 * @param charisma Carisma Verbal, de 1,00 a 5,00
 * @param language Lenguaje Inclusivo y Adecuado, de 1,00 a 5,00
 */
public record ContentScores(BigDecimal precision, BigDecimal charisma, BigDecimal language) {

    /** Sin notas de contenido: las tres métricas quedan «sin resultado». */
    public static final ContentScores NONE = new ContentScores(null, null, null);

    private static final int SCALE = 2;

    /** Rechaza una mezcla de métricas con valor y sin él. */
    public ContentScores {
        boolean allNull = precision == null && charisma == null && language == null;
        boolean allPresent = precision != null && charisma != null && language != null;
        if (!allNull && !allPresent) {
            throw new IllegalArgumentException("Las tres métricas de contenido van juntas o ninguna");
        }
    }

    /**
     * Promedia cada métrica a 2 decimales, mitad hacia arriba.
     *
     * @param grades notas de las respuestas no conductuales
     * @return los promedios, o {@link #NONE} si no hay notas
     */
    public static ContentScores average(List<ContentGrade> grades) {
        if (grades.isEmpty()) {
            return NONE;
        }
        return new ContentScores(average(grades, ContentGrade::precision), average(grades, ContentGrade::charisma),
                average(grades, ContentGrade::language));
    }

    private static BigDecimal average(List<ContentGrade> grades, ToIntFunction<ContentGrade> metric) {
        long sum = grades.stream().mapToInt(metric).asLongStream().sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(grades.size()), SCALE, RoundingMode.HALF_UP);
    }
}
