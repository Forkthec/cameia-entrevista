package tech.cameia.entrevista.domain.model;

import java.util.UUID;

/**
 * Respuesta escrita ya validada.
 *
 * @param turnId turno que se responde
 * @param answerText respuesta normalizada a NFC, sin recortar
 * @param idempotencyKey clave anti-duplicados de la cabecera
 */
public record ValidatedAnswerRequest(UUID turnId, String answerText, String idempotencyKey) {

    /** Algoritmo de la huella. */
    private static final String HASH_ALGORITHM = "SHA-256";

    /** Separador entre el turno y el texto en la huella: el salto de línea no aparece en un UUID. */
    private static final char SEPARATOR = (char) 10;

    /**
     * Calcula la huella del turno y la respuesta, para reconocer un reenvío idéntico.
     *
     * @return SHA-256 en hexadecimal (64 caracteres)
     */
    public String fingerprint() {
        return ValidatedSessionRequest.sha256Hex(HASH_ALGORITHM, turnId.toString() + SEPARATOR + answerText);
    }

    /** @return descripción sin el texto de la respuesta, que no debe llegar a un registro */
    @Override
    public String toString() {
        return "ValidatedAnswerRequest[turnId=" + turnId + "]";
    }
}
