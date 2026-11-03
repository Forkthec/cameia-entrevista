package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.application.service.OngoingSession;

/**
 * Entrevista que la persona tiene en curso.
 *
 * @param sessionId identificador de la sesión en curso
 * @param answersCount respuestas definitivas que lleva, de 0 a 5
 */
@Schema(description = "Entrevista en curso de la persona; no cambia al crear la sesión")
public record OngoingSessionResponse(
        @Schema(format = "uuid", example = "0e2d4c1a-8b3f-4a6e-9d21-5c7b8a9f0e1d") UUID sessionId,
        @Schema(description = "Respuestas definitivas entregadas", minimum = "0", maximum = "5", example = "2")
        int answersCount) {

    /**
     * Traduce la entrevista en curso al contrato.
     *
     * @param ongoing entrevista en curso del caso de uso
     * @return la entrevista en curso para el cliente
     */
    static OngoingSessionResponse from(OngoingSession ongoing) {
        return new OngoingSessionResponse(ongoing.sessionId(), ongoing.answersCount());
    }
}
