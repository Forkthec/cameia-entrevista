package tech.cameia.entrevista.domain.model;

import java.util.Objects;

/**
 * Resultado del método STAR de una pregunta conductual. Los cuatro componentes están presentes o los cuatro son
 * nulos («sin resultado»); nunca una mezcla.
 *
 * @param questionNumber número de la pregunta, de 1 a 5
 * @param questionText texto de la pregunta
 * @param situation si la respuesta describe el contexto concreto
 * @param task si dice qué le correspondía lograr
 * @param action si explica qué hizo la persona
 * @param result si cuenta cómo terminó
 */
public record StarResult(int questionNumber, String questionText, Boolean situation, Boolean task, Boolean action,
        Boolean result) {

    /** Valida el número, exige el texto y rechaza una mezcla de componentes presentes y nulos. */
    public StarResult {
        QuestionNumbers.require(questionNumber);
        Objects.requireNonNull(questionText, "questionText");
        int present = (situation == null ? 0 : 1) + (task == null ? 0 : 1) + (action == null ? 0 : 1)
                + (result == null ? 0 : 1);
        if (present != 0 && present != 4) {
            throw new IllegalArgumentException("Los cuatro componentes STAR van juntos o ninguno");
        }
    }

    /**
     * Resultado de una pregunta conductual que no se pudo evaluar: los cuatro componentes sin resultado.
     *
     * @param questionNumber número de la pregunta
     * @param questionText texto de la pregunta
     * @return el resultado sin componentes
     */
    public static StarResult unavailable(int questionNumber, String questionText) {
        return new StarResult(questionNumber, questionText, null, null, null, null);
    }

    /**
     * Cantidad de componentes presentes.
     *
     * @return de 0 a 4, o {@code null} si no hay resultado
     */
    public Integer level() {
        if (situation == null) {
            return null;
        }
        return (situation ? 1 : 0) + (task ? 1 : 0) + (action ? 1 : 0) + (result ? 1 : 0);
    }

    @Override
    public String toString() {
        return "StarResult[questionNumber=" + questionNumber + ", level=" + level() + "]";
    }
}
