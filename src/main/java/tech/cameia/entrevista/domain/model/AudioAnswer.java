package tech.cameia.entrevista.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Grabación de una respuesta por voz tal como llegó en la petición. Vive solo en memoria mientras dura la
 * petición: nunca se escribe en disco ni en la base (CA-5.12.4).
 *
 * @param content bytes del audio
 * @param mediaType tipo de contenido declarado, sin parámetros y en minúsculas (por ejemplo {@code audio/webm})
 */
public record AudioAnswer(byte[] content, String mediaType) {

    /** Exige los dos datos. */
    public AudioAnswer {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mediaType, "mediaType");
    }

    /** @return tamaño del audio en bytes */
    public int size() {
        return content.length;
    }

    /** No imprime el contenido: el audio nunca llega a un registro. */
    @Override
    public String toString() {
        return "AudioAnswer[size=" + content.length + ", mediaType=" + mediaType + "]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AudioAnswer that && Arrays.equals(content, that.content)
                && mediaType.equals(that.mediaType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(content) + mediaType.hashCode();
    }
}
