package tech.cameia.entrevista.application.service;

import java.util.UUID;

import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.ValidatedSessionRequest;

/**
 * Resultado de crear o reutilizar la sesión configurada.
 *
 * @param httpStatus 201 si la sesión es nueva, 200 si se reutilizó la que ya estaba configurada
 * @param sessionId identificador de la sesión
 * @param status estado de la sesión
 * @param configuration configuración con la que quedó
 * @param ongoing entrevista en curso de la persona, o {@code null} si no tiene
 */
public record CreateSessionResult(
        int httpStatus,
        UUID sessionId,
        SessionStatus status,
        ValidatedSessionRequest configuration,
        OngoingSession ongoing) {
}
