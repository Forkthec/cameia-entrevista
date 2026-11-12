package tech.cameia.entrevista.domain.model;

/**
 * Una pregunta ya respondida, para el historial de la sesión.
 *
 * @param number posición de la pregunta, de 1 a 5
 * @param question texto original de la pregunta
 * @param answer respuesta definitiva de la persona
 */
public record AnsweredQuestion(int number, String question, String answer) {

    /** @return descripción sin los textos, que no deben llegar a un registro */
    @Override
    public String toString() {
        return "AnsweredQuestion[number=" + number + "]";
    }
}
