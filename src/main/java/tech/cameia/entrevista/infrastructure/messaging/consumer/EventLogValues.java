package tech.cameia.entrevista.infrastructure.messaging.consumer;

import java.util.regex.Pattern;

/**
 * Valores de un mensaje que se pueden escribir en el registro sin riesgo.
 *
 * <p>El identificador del mensaje lo pone el productor y no es de confianza: si no tiene la forma
 * esperada nunca se copia al registro, para que nadie inyecte saltos de línea ni un texto largo.</p>
 */
final class EventLogValues {

    private static final int MAX_MESSAGE_ID_LENGTH = 64;
    private static final Pattern SAFE_MESSAGE_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private EventLogValues() {
    }

    /**
     * Devuelve el identificador del mensaje en una forma segura para el registro.
     *
     * @param messageId identificador recibido, o {@code null} si el mensaje no lo traía
     * @return {@code -} si falta; el mismo valor si mide a lo sumo 64 caracteres y solo tiene letras,
     *         dígitos, punto, guion o guion bajo; {@code invalid} en cualquier otro caso
     */
    static String safeMessageId(String messageId) {
        if (messageId == null) {
            return "-";
        }
        boolean safe = messageId.length() <= MAX_MESSAGE_ID_LENGTH && SAFE_MESSAGE_ID.matcher(messageId).matches();
        return safe ? messageId : "invalid";
    }
}
