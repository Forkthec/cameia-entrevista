package tech.cameia.entrevista.application.service;

import tech.cameia.entrevista.domain.model.LanguageModelPrompt;

/**
 * Prompt de planificación ya armado y la versión que se registra en la ejecución de la IA.
 *
 * @param prompt texto y tope de tokens de salida que se envían al modelo
 * @param version versión con la forma {@code planificacion.v6+<TONO>.v<N>}
 * @param fallbackSchema esquema JSON de la salida que solo se envía al proveedor de respaldo, o {@code null}
 */
public record PlanningPrompt(LanguageModelPrompt prompt, String version, String fallbackSchema) {

    /** Prompt sin esquema de respaldo. */
    public PlanningPrompt(LanguageModelPrompt prompt, String version) {
        this(prompt, version, null);
    }
}
