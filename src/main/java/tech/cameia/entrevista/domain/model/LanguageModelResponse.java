package tech.cameia.entrevista.domain.model;

/**
 * Lo que el modelo de lenguaje devolvió: el texto y lo que costó producirlo.
 *
 * <p>Los tokens permiten que el caso de uso registre el consumo de cada llamada y que la
 * política de cuota lo descuente; este objeto no los publica ni los guarda. Un valor inválido
 * es un error de programación del adaptador, así que falla con
 * {@link IllegalArgumentException}.</p>
 *
 * @param text texto de la respuesta; no puede ser nulo ni estar en blanco
 * @param inputTokens tokens del prompt que informó el proveedor; {@code 0} si no los informó
 * @param outputTokens tokens de la respuesta que informó el proveedor; {@code 0} si no los informó
 * @param model identificador del modelo que respondió; no puede ser nulo ni estar en blanco
 */
public record LanguageModelResponse(String text, int inputTokens, int outputTokens, String model) {

    /**
     * Valida el texto, los tokens y el modelo al construir la respuesta.
     *
     * @throws IllegalArgumentException si el texto o el modelo son nulos o están en blanco, o si
     *                                  un número de tokens es negativo
     */
    public LanguageModelResponse {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El texto de la respuesta no puede estar vacío.");
        }
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("Los tokens de la respuesta no pueden ser negativos.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("El modelo de la respuesta no puede estar vacío.");
        }
    }

    /**
     * Representación sin el texto, que puede llevar datos de la persona y no debe llegar al log.
     *
     * @return el modelo, los tokens y la longitud del texto
     */
    @Override
    public String toString() {
        return "LanguageModelResponse[text=<" + text.length() + " caracteres>, inputTokens=" + inputTokens
                + ", outputTokens=" + outputTokens + ", model=" + model + "]";
    }
}
