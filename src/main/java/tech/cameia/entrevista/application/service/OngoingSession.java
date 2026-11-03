package tech.cameia.entrevista.application.service;

import java.util.UUID;

/**
 * Entrevista que la persona tiene en curso, para que pueda retomarla.
 *
 * @param sessionId identificador de la sesión en curso
 * @param answersCount respuestas definitivas que lleva, de 0 a 5
 */
public record OngoingSession(UUID sessionId, int answersCount) {
}
