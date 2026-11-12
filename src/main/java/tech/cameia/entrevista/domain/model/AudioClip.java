package tech.cameia.entrevista.domain.model;

import java.util.Objects;

/**
 * Audio de una respuesta, tal como llegó en la petición. Vive solo en memoria mientras dura la petición: no se
 * guarda en la base ni en disco y se descarta al terminar, con éxito o sin él (CA-5.13.4).
 *
 * <p>{@link #toString()} no imprime el contenido: el audio nunca llega al registro. La igualdad es por identidad,
 * porque comparar grabaciones byte a byte no tiene sentido de negocio.</p>
 *
 * @param content bytes del audio; no se copian para no duplicar la grabación en memoria
 * @param contentType tipo de contenido declarado por el cliente; {@value #UNKNOWN_TYPE} si no lo dijo
 */
@SuppressWarnings("java:S6218")
public record AudioClip(byte[] content, String contentType) {

    /** Tipo que se usa cuando el cliente no declaró ninguno. */
    public static final String UNKNOWN_TYPE = "application/octet-stream";

    /**
     * Exige el contenido y completa el tipo si falta.
     */
    public AudioClip {
        Objects.requireNonNull(content, "content");
        contentType = contentType == null || contentType.isBlank() ? UNKNOWN_TYPE : contentType;
    }

    /** @return {@code true} si no trae ningún byte */
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "AudioClip[contentType=" + contentType + ", bytes=" + content.length + "]";
    }
}
