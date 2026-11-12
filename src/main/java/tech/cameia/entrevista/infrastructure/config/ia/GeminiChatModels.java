package tech.cameia.entrevista.infrastructure.config.ia;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Los dos modelos de chat de Gemini: cada uno con el tiempo de espera de su operación.
 *
 * @param planning modelo con el tiempo de espera de la planificación ({@code cameia.llm.timeout})
 * @param evaluation modelo con el tiempo de espera de la evaluación ({@code cameia.llm.evaluation-timeout})
 */
public record GeminiChatModels(ChatModel planning, ChatModel evaluation) {
}
