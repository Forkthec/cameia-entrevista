package tech.cameia.entrevista.application.command;

import java.util.UUID;

import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.FirebaseUid;

/**
 * Pedido de responder por voz la pregunta pendiente de una sesión.
 *
 * @param sessionId identificador de la sesión; {@code null} si el texto de la ruta no era un UUID
 * @param owner dueño que viene de la identidad del Gateway
 * @param rawTurnId texto del campo {@code turnId} tal como llegó; puede ser nulo o no ser un UUID
 * @param audio audio de la respuesta; vive solo en memoria durante la petición
 */
public record SubmitAudioAnswerCommand(UUID sessionId, FirebaseUid owner, String rawTurnId, AudioClip audio) {

    /**
     * Crea el pedido a partir del texto de la ruta.
     *
     * @param rawSessionId texto del segmento {@code {sessionId}} de la ruta
     * @param owner dueño de la identidad
     * @param rawTurnId texto del campo {@code turnId}
     * @param audio audio recibido
     * @return el pedido
     */
    public static SubmitAudioAnswerCommand of(String rawSessionId, FirebaseUid owner, String rawTurnId,
            AudioClip audio) {
        return new SubmitAudioAnswerCommand(SessionCommand.of(rawSessionId, owner).sessionId(), owner, rawTurnId,
                audio);
    }

    /** @return el pedido sobre la sesión, para buscarla con la regla común de dueño */
    public SessionCommand session() {
        return new SessionCommand(sessionId, owner);
    }
}
