package tech.cameia.entrevista.domain.model;

/**
 * Lo que un caso de uso le envía al modelo de lenguaje: el texto, el tope de tokens de la
 * respuesta, con el que quien llama limita el costo de cada llamada, y, si el caso de uso lo pide, el esquema
 * JSON al que debe ajustarse la respuesta.
 *
 * <p>Un valor inválido es un error de programación de quien construye el prompt, no un dato de
 * la persona: por eso falla con {@link IllegalArgumentException} y no con una excepción de
 * negocio.</p>
 *
 * @param text texto que se envía al modelo; no puede ser nulo ni estar en blanco
 * @param maxOutputTokens máximo de tokens de la respuesta; al menos 1
 * @param jsonSchema esquema JSON de la respuesta, o {@code null} si el modelo responde sin esquema
 */
public record LanguageModelPrompt(String text, int maxOutputTokens, String jsonSchema) {

    /**
     * Valida el texto y el tope al construir el prompt.
     *
     * @throws IllegalArgumentException si el texto es nulo o está en blanco, si el tope es menor que 1 o si el
     *         esquema, cuando se da, está en blanco
     */
    public LanguageModelPrompt {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El texto del prompt no puede estar vacío.");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("El tope de tokens de salida debe ser al menos 1.");
        }
        if (jsonSchema != null && jsonSchema.isBlank()) {
            throw new IllegalArgumentException("El esquema de la respuesta no puede estar en blanco.");
        }
    }

    /**
     * Crea un prompt sin esquema de respuesta.
     *
     * @param text texto que se envía al modelo
     * @param maxOutputTokens máximo de tokens de la respuesta
     */
    public LanguageModelPrompt(String text, int maxOutputTokens) {
        this(text, maxOutputTokens, null);
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
