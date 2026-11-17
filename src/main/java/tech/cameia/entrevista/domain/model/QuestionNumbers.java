package tech.cameia.entrevista.domain.model;

/** Rango del número de pregunta de una entrevista: siempre de 1 a 5. */
final class QuestionNumbers {

    private static final int FIRST = 1;

    private static final int LAST = 5;

    private QuestionNumbers() {
    }

    /**
     * Comprueba que el número esté entre 1 y 5.
     *
     * @param number número de la pregunta
     * @throws IllegalArgumentException si está fuera de rango
     */
    static void require(int number) {
        if (number < FIRST || number > LAST) {
            throw new IllegalArgumentException("El número de pregunta debe estar entre " + FIRST + " y " + LAST);
        }
    }
}
