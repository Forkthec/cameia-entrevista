package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;

/**
 * Convierte el audio de una respuesta en texto, sin saber qué proveedor lo hace.
 *
 * <p>El audio viaja dentro de la propia llamada y no se sube a ningún almacenamiento. Una llamada hace un solo
 * intento.</p>
 */
public interface TranscriptionPort {

    /**
     * Transcribe el audio.
     *
     * @param clip audio validado
     * @param language idioma de la entrevista, como pista para el proveedor
     * @return el texto transcrito; en blanco si no se detectó voz
     * @throws TranscriptionUnavailableException si el proveedor no responde a tiempo, falla o responde algo
     *                                           inutilizable
     */
    Transcription transcribe(AudioClip clip, InterviewLanguage language);
}
