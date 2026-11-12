package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;

/**
 * Puerto hacia el servicio de Voz que transcribe el audio de una respuesta.
 *
 * <p>Cada llamada es <b>un solo intento</b>, sin reintentos: si se reintenta y cuándo lo decide el caso de uso con
 * la política del dominio. La implementación no guarda el audio ni lo registra.</p>
 */
public interface SpeechToTextPort {

    /**
     * Transcribe un audio en un único intento.
     *
     * @param audio audio de la respuesta
     * @param language idioma de la entrevista
     * @return la transcripción; su texto puede venir vacío si no se detectó voz
     * @throws TranscriptionUnavailableException si el intento falló, con la causa clasificada
     */
    Transcription transcribe(AudioClip audio, InterviewLanguage language);
}
