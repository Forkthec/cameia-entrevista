package tech.cameia.entrevista.application.service;

import tech.cameia.entrevista.domain.model.VoiceSample;

/**
 * Transcripción aceptada como respuesta: el texto, el idioma detectado y sus datos de voz (nulos si no se pudieron
 * calcular).
 *
 * @param text texto transcrito y ya validado
 * @param detectedLanguage idioma que detectó el proveedor, o {@code null}
 * @param voice datos de voz de la respuesta, o {@code null}
 */
public record AcceptedSpeech(String text, String detectedLanguage, VoiceSample voice) {

    /** No imprime el texto: una respuesta nunca llega a un registro. */
    @Override
    public String toString() {
        return "AcceptedSpeech[length=" + text.length() + ", voice=" + voice + "]";
    }
}
