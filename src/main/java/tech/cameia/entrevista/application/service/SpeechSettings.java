package tech.cameia.entrevista.application.service;

/**
 * Configuración de la voz de las preguntas (HU-5.15) que usan los casos de uso.
 *
 * @param enabled interruptor de la función; apagada, el estado no ofrece el audio y pedirlo responde
 *        {@code SPEECH_DISABLED}
 * @param maxConcurrent máximo de síntesis simultáneas en la instancia; al menos 1
 * @param monthlyCharacterCap tope de caracteres enviados a la voz por mes calendario (UTC); al menos 1
 */
public record SpeechSettings(boolean enabled, int maxConcurrent, long monthlyCharacterCap) {

    /**
     * Valida los límites.
     *
     * @throws IllegalArgumentException si un límite es menor que 1
     */
    public SpeechSettings {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("El máximo de síntesis simultáneas debe ser al menos 1");
        }
        if (monthlyCharacterCap < 1) {
            throw new IllegalArgumentException("El tope mensual de caracteres debe ser al menos 1");
        }
    }
}
