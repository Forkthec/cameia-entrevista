package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.exception.SpeechTimeoutException;
import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.SpeechAudio;
import tech.cameia.entrevista.domain.model.SpeechDirection;

/**
 * Puerto del servicio que convierte el texto de una pregunta en voz (hoy Gemini TTS).
 *
 * <p>No guarda el audio ni el texto. Todo el trabajo, incluido un posible cambio al modelo de respaldo, cabe en el
 * tiempo máximo configurado.</p>
 */
public interface SpeechSynthesisPort {

    /**
     * Genera el audio de la pregunta.
     *
     * @param direction texto con etiquetas y estilo
     * @param language idioma de la entrevista, que fija la voz
     * @return el audio, ya validado (no vacío y con la cabecera de su formato)
     * @throws SpeechTimeoutException si no termina dentro del tiempo máximo
     * @throws SpeechUnavailableException si el servicio falla, no hay credencial o el audio no es válido
     */
    SpeechAudio synthesize(SpeechDirection direction, InterviewLanguage language);
}
