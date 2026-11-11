package tech.cameia.entrevista.application.command;

import java.util.UUID;

import tech.cameia.entrevista.domain.model.FirebaseUid;

/**
 * Pedido sobre una sesión concreta de una persona: iniciarla, consultar su estado o cancelarla.
 *
 * @param sessionId identificador de la sesión, ya convertido a UUID; {@code null} si el texto de la ruta no era un
 *        UUID, caso que se trata igual que una sesión inexistente
 * @param owner dueño que viene de la identidad del Gateway, nunca de la ruta ni del cuerpo
 */
public record SessionCommand(UUID sessionId, FirebaseUid owner) {

    /**
     * Crea el pedido a partir del texto de la ruta.
     *
     * @param rawSessionId texto del segmento {@code {sessionId}} de la ruta
     * @param owner dueño de la identidad
     * @return el pedido; con {@code sessionId} nulo si el texto no es un UUID canónico
     */
    public static SessionCommand of(String rawSessionId, FirebaseUid owner) {
        return new SessionCommand(parse(rawSessionId), owner);
    }

    private static UUID parse(String raw) {
        // UUID.fromString acepta formas laxas ("1-1-1-1-1"); solo se admite la forma canónica de 36 caracteres.
        if (raw == null || raw.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
