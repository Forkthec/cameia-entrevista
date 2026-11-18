package tech.cameia.entrevista.domain.model;

/**
 * Una pregunta tal como la propone el modelo, antes de validarla.
 *
 * @param position posición que el modelo declara, de 1 a 5
 * @param type tipo como lo escribió el modelo
 * @param text texto de la pregunta
 */
public record PlannedQuestion(int position, String type, String text) {

    /**
     * Representación sin el texto, que puede reflejar datos de la persona y no debe llegar al log.
     *
     * @return la posición y el tipo
     */
    @Override
    public String toString() {
        return "PlannedQuestion[position=" + position + ", type=" + type + "]";
    }
}
