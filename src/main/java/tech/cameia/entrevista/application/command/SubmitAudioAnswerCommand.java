package tech.cameia.entrevista.application.command;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import tech.cameia.entrevista.domain.model.FirebaseUid;

/**
 * Pedido de registrar la respuesta por voz a un turno (HU-5.8). No lleva el nombre del archivo ni el tipo declarado:
 * el controlador los descarta (CA-5.8.16) y el formato se reconoce por el contenido.
 *
 * @param sessionId sesión de la ruta, o {@code null} si no era un UUID canónico
 * @param owner identidad del Gateway
 * @param rawTurnId turno tal como llegó en el formulario; puede faltar o estar mal escrito
 * @param idempotencyKey clave anti-duplicados del encabezado, o {@code null}
 * @param audio bytes del audio, o {@code null} si la petición no trae la parte {@code audio}
 */
public record SubmitAudioAnswerCommand(UUID sessionId, FirebaseUid owner, String rawTurnId, String idempotencyKey,
        byte[] audio) {

    /** @return descripción sin el contenido del audio */
    @Override
    public String toString() {
        return "SubmitAudioAnswerCommand[sessionId=" + sessionId + ", bytes=" + (audio == null ? null : audio.length)
                + "]";
    }

    /**
     * Dos pedidos son iguales si piden lo mismo; el audio se compara por contenido.
     *
     * @param other otro objeto
     * @return si es el mismo pedido
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof SubmitAudioAnswerCommand that && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(owner, that.owner) && Objects.equals(rawTurnId, that.rawTurnId)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Arrays.equals(audio, that.audio);
    }

    /** @return código hash coherente con {@link #equals(Object)} */
    @Override
    public int hashCode() {
        return Objects.hash(sessionId, owner, rawTurnId, idempotencyKey) * 31
                + Arrays.hashCode(audio);
    }
}
