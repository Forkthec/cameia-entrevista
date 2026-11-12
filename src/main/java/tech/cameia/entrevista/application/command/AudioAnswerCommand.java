package tech.cameia.entrevista.application.command;

import tech.cameia.entrevista.domain.model.FirebaseUid;

/**
 * Pedido de responder por voz la pregunta pendiente de una sesión (HU-5.12).
 *
 * @param session sesión pedida y dueño, que viene de la identidad del Gateway
 * @param rawTurnId texto de la parte {@code turnId} del multipart, sin validar; {@code null} si no llegó
 * @param audio bytes de la parte {@code audio}; {@code null} si no llegó
 * @param mediaType tipo de contenido declarado en la parte {@code audio}; {@code null} si no llegó
 * @param idempotencyKey cabecera {@code Idempotency-Key}, sin validar; {@code null} si no llegó
 */
public record AudioAnswerCommand(SessionCommand session, String rawTurnId, byte[] audio, String mediaType,
        String idempotencyKey) {

    /**
     * Crea el pedido a partir de lo que llegó en la petición.
     *
     * @param rawSessionId segmento {@code {sessionId}} de la ruta
     * @param owner dueño de la identidad
     * @param rawTurnId parte {@code turnId}
     * @param audio parte {@code audio}
     * @param mediaType tipo de contenido de la parte {@code audio}
     * @param idempotencyKey cabecera {@code Idempotency-Key}
     * @return el pedido
     */
    public static AudioAnswerCommand of(String rawSessionId, FirebaseUid owner, String rawTurnId, byte[] audio,
            String mediaType, String idempotencyKey) {
        return new AudioAnswerCommand(SessionCommand.of(rawSessionId, owner), rawTurnId, audio, mediaType,
                idempotencyKey);
    }

    /** No imprime el audio: nunca llega a un registro. */
    @Override
    public String toString() {
        return "AudioAnswerCommand[session=" + session + ", audioBytes=" + (audio == null ? 0 : audio.length)
                + ", mediaType=" + mediaType + "]";
    }
}
