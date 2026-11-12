package tech.cameia.entrevista.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Audio de una respuesta por voz, ya validado, que solo vive en memoria durante la petición (CA-5.8.18).
 *
 * <p>No guarda el nombre del archivo recibido ni el tipo declarado: solo los bytes y el formato reconocido por su
 * contenido. {@link #discard()} sobrescribe los bytes al terminar, con éxito o con error, para que el audio no quede
 * recuperable en la memoria del proceso.</p>
 */
public final class AudioClip {

    private final byte[] content;
    private final AudioInspection inspection;

    /**
     * Crea el audio. No copia el arreglo para no duplicar hasta 10 MiB en memoria: quien lo crea deja de usarlo.
     *
     * @param content bytes del audio
     * @param inspection formato y duración reconocidos
     */
    public AudioClip(byte[] content, AudioInspection inspection) {
        this.content = Objects.requireNonNull(content, "content");
        this.inspection = Objects.requireNonNull(inspection, "inspection");
    }

    /** @return los bytes del audio; no deben guardarse ni registrarse */
    public byte[] content() {
        return content;
    }

    /** @return formato reconocido por el contenido */
    public AudioFormat format() {
        return inspection.format();
    }

    /** @return formato y duración reconocidos */
    public AudioInspection inspection() {
        return inspection;
    }

    /** Sobrescribe los bytes con ceros. Se llama una vez terminada la petición. */
    public void discard() {
        Arrays.fill(content, (byte) 0);
    }

    /** @return descripción sin el contenido del audio */
    @Override
    public String toString() {
        return "AudioClip[bytes=" + content.length + ", format=" + inspection.format() + ", durationMs="
                + inspection.duration().toMillis() + "]";
    }
}
