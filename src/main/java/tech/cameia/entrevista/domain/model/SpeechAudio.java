package tech.cameia.entrevista.domain.model;

import java.util.Objects;

/**
 * Audio de una pregunta generado por el servicio de voz. Vive solo en la memoria de la petición: no se guarda
 * (CA-5.15.15).
 *
 * @param content bytes del audio
 * @param mediaType tipo de contenido (por ejemplo {@code audio/mpeg})
 * @param voice voz que lo generó, para el registro
 */
public record SpeechAudio(byte[] content, String mediaType, String voice) {

    /** Valida que no falten datos. */
    public SpeechAudio {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(voice, "voice");
    }

    /** No imprime el audio. */
    @Override
    public String toString() {
        return "SpeechAudio[bytes=" + content.length + ", mediaType=" + mediaType + ", voice=" + voice + "]";
    }
}
