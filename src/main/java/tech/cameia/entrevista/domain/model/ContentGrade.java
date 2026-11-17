package tech.cameia.entrevista.domain.model;

/**
 * Nota de contenido de una respuesta no conductual, con las tres métricas del reporte.
 *
 * @param number número de la pregunta, de 1 a 5
 * @param precision Precisión y Claridad, de 1 a 5
 * @param charisma Carisma Verbal, de 1 a 5
 * @param language Lenguaje Inclusivo y Adecuado, de 1 a 5
 */
public record ContentGrade(int number, int precision, int charisma, int language) {

    /** Menor nota posible. */
    public static final int MIN = 1;

    /** Mayor nota posible. */
    public static final int MAX = 5;

    /** Valida el número de pregunta y que cada nota esté entre {@value #MIN} y {@value #MAX}. */
    public ContentGrade {
        QuestionNumbers.require(number);
        require(precision);
        require(charisma);
        require(language);
    }

    private static void require(int grade) {
        if (grade < MIN || grade > MAX) {
            throw new IllegalArgumentException("La nota de contenido debe estar entre " + MIN + " y " + MAX);
        }
    }
}
