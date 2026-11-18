package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.exception.SpeechTimeoutException;
import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.SpeechAudio;

/**
 * Puerto del servicio que convierte el texto de una pregunta en voz (hoy Google Cloud Text-to-Speech, voz Chirp 3 HD).
 *
 * <p>No guarda el audio ni el texto. Todo el trabajo cabe en el tiempo máximo configurado.</p>
 */
public interface SpeechSynthesisPort {

    /**
     * Genera el audio de la pregunta.
     *
     * @param text texto guardado de la pregunta, sin alterar
     * @param language idioma de la entrevista, que fija la voz; el tono no la cambia (CA-5.15.3)
     * @return el audio, ya validado (no vacío y con la cabecera de su formato)
     * @throws SpeechTimeoutException si no termina dentro del tiempo máximo
     * @throws SpeechUnavailableException si el servicio falla, no hay credencial o el audio no es válido
     */
    SpeechAudio synthesize(String text, InterviewLanguage language);
}
