package tech.cameia.entrevista.infrastructure.config.ia;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Los dos modelos de chat del respaldo en Groq, o ninguno si falta la llave.
 *
 * @param planning modelo con el tiempo de espera de la planificación; nulo si no hay llave
 * @param evaluation modelo con el tiempo de espera de la evaluación; nulo si no hay llave
 */
public record GroqChatModels(ChatModel planning, ChatModel evaluation) {

    /**
     * Respaldo sin llave: ninguno de los dos modelos existe.
     *
     * @return el conjunto vacío
     */
    public static GroqChatModels absent() {
        return new GroqChatModels(null, null);
    }

    /**
     * Indica si el respaldo puede usarse.
     *
     * @return {@code true} si los dos modelos existen
     */
    public boolean available() {
        return planning != null && evaluation != null;
    }
}
