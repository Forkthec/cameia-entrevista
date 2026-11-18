package tech.cameia.entrevista.application.command;

import tech.cameia.entrevista.domain.model.FirebaseUid;

/**
 * Pedido de responder por voz la pregunta pendiente de una sesión (HU-5.8 y HU-5.12).
 *
 * <p>No lleva el nombre ni el tipo declarado del archivo: el formato se decide por el contenido real del audio
 * (CA-5.8.14 a CA-5.8.16).</p>
 *
 * @param session sesión pedida y dueño, que viene de la identidad del Gateway
 * @param rawTurnId texto de la parte {@code turnId} del multipart, sin validar; {@code null} si no llegó
 * @param audio bytes de la parte {@code audio}; {@code null} si no llegó
 * @param idempotencyKey cabecera {@code Idempotency-Key}, sin validar; {@code null} si no llegó
 */
public record AudioAnswerCommand(SessionCommand session, String rawTurnId, byte[] audio, String idempotencyKey) {

    /**
     * Crea el pedido a partir de lo que llegó en la petición.
     *
     * @param rawSessionId segmento {@code {sessionId}} de la ruta
     * @param owner dueño de la identidad
     * @param rawTurnId parte {@code turnId}
     * @param audio parte {@code audio}
     * @param idempotencyKey cabecera {@code Idempotency-Key}
     * @return el pedido
     */
    public static AudioAnswerCommand of(String rawSessionId, FirebaseUid owner, String rawTurnId, byte[] audio,
            String idempotencyKey) {
        return new AudioAnswerCommand(SessionCommand.of(rawSessionId, owner), rawTurnId, audio, idempotencyKey);
    }

    /** No imprime el audio: nunca llega a un registro. */
    @Override
    public String toString() {
        return "AudioAnswerCommand[session=" + session + ", audioBytes=" + (audio == null ? 0 : audio.length) + "]";
    }
}
