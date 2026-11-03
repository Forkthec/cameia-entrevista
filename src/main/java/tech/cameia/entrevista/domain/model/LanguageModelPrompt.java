package tech.cameia.entrevista.domain.model;

/**
 * Lo que un caso de uso le envía al modelo de lenguaje: el texto y el tope de tokens de la
 * respuesta, con el que quien llama limita el costo de cada llamada.
 *
 * <p>Un valor inválido es un error de programación de quien construye el prompt, no un dato de
 * la persona: por eso falla con {@link IllegalArgumentException} y no con una excepción de
 * negocio.</p>
 *
 * @param text texto que se envía al modelo; no puede ser nulo ni estar en blanco
 * @param maxOutputTokens máximo de tokens de la respuesta; al menos 1
 */
public record LanguageModelPrompt(String text, int maxOutputTokens) {

    /**
     * Valida el texto y el tope al construir el prompt.
     *
     * @throws IllegalArgumentException si el texto es nulo o está en blanco, o si el tope es menor que 1
     */
    public LanguageModelPrompt {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El texto del prompt no puede estar vacío.");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("El tope de tokens de salida debe ser al menos 1.");
        }
    }

    /**
     * Representación sin el texto, que puede llevar datos de la persona y no debe llegar al log.
     *
     * @return el tope de tokens y la longitud del texto
     */
    @Override
    public String toString() {
        return "LanguageModelPrompt[text=<" + text.length() + " caracteres>, maxOutputTokens=" + maxOutputTokens + "]";
    }
}
