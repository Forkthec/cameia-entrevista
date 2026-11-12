package tech.cameia.entrevista.domain.policy;

import java.util.Locale;
import java.util.Set;

import tech.cameia.entrevista.domain.exception.AudioFormatNotAllowedException;

/**
 * Tipos de contenido de audio que se admiten en una respuesta por voz.
 *
 * <p>Son los que produce {@code MediaRecorder} en los navegadores soportados ({@code audio/webm} en Chrome y
 * Edge, {@code audio/mp4} en Safari, {@code audio/ogg} en Firefox) y los formatos comunes que acepta el servicio
 * de transcripción, útiles para probar con archivos ({@code audio/mpeg}, {@code audio/wav}). Los parámetros del
 * tipo ({@code ;codecs=opus}) se ignoran.</p>
 */
public final class AudioAnswerPolicy {

    /** Tipos admitidos, sin parámetros y en minúsculas. */
    public static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("audio/webm", "audio/ogg", "audio/mp4",
            "audio/x-m4a", "audio/m4a", "audio/aac", "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
            "audio/wave", "video/webm");

    /**
     * Normaliza el tipo de contenido y comprueba que se admite.
     *
     * @param declared tipo de contenido declarado en la parte del multipart; puede traer parámetros
     * @return el tipo sin parámetros y en minúsculas
     * @throws AudioFormatNotAllowedException si falta o no se admite
     */
    public String requireAllowed(String declared) {
        if (declared == null || declared.isBlank()) {
            throw new AudioFormatNotAllowedException();
        }
        int parameters = declared.indexOf(';');
        String bare = (parameters >= 0 ? declared.substring(0, parameters) : declared).strip()
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_MEDIA_TYPES.contains(bare)) {
            throw new AudioFormatNotAllowedException();
        }
        return bare;
    }
}
