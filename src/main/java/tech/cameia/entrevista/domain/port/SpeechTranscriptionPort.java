package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioAnswer;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;

/**
 * Puerto del servicio de transcripción de voz a texto (hoy AssemblyAI).
 *
 * <p>Hace un solo intento por llamada: los reintentos son de HU-5.13. El audio no se guarda en CAMEIA; la copia
 * temporal del proveedor es la que informa CA-5.8.24.</p>
 */
public interface SpeechTranscriptionPort {

    /**
     * Transcribe el audio esperando el idioma de la entrevista y detecta el idioma en que se habló.
     *
     * @param audio grabación de la respuesta
     * @param expected idioma de la entrevista: el que se espera y el que se usa si no se detecta otro
     * @return el texto transcrito (vacío si no hubo voz) y el idioma detectado
     * @throws TranscriptionUnavailableException si el servicio falla, no responde a tiempo o responde algo
     *         inválido
     */
    Transcription transcribe(AudioAnswer audio, InterviewLanguage expected);

    /** @return nombre del proveedor, para el registro de la ejecución */
    String provider();

    /** @return modelo configurado, para el registro de la ejecución */
    String model();
}
