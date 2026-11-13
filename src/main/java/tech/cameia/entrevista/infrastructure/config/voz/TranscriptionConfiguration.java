package tech.cameia.entrevista.infrastructure.config.voz;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.policy.AudioAnswerPolicy;
import tech.cameia.entrevista.domain.policy.TranscriptionPolicy;
import tech.cameia.entrevista.domain.policy.TranscriptionRetryPolicy;
import tech.cameia.entrevista.domain.port.SpeechTranscriptionPort;
import tech.cameia.entrevista.infrastructure.client.assemblyai.AssemblyAiTranscriptionAdapter;

/**
 * Ensambla la respuesta por voz: las reglas de dominio de la transcripción y del audio, y el adaptador de
 * AssemblyAI.
 */
@Configuration
@EnableConfigurationProperties(TranscriptionProperties.class)
class TranscriptionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionConfiguration.class);

    /**
     * @param properties configuración validada
     * @return la regla que valida la transcripción con el umbral de idioma configurado
     */
    @Bean
    TranscriptionPolicy transcriptionPolicy(TranscriptionProperties properties) {
        return new TranscriptionPolicy(properties.languageConfidenceThreshold());
    }

    /**
     * Regla del reintento automático de la transcripción (HU-5.13): un intento dura como máximo
     * {@code TRANSCRIPTION_TIMEOUT}, el reintento espera {@code TRANSCRIPTION_RETRY_PAUSE} y todos los intentos caben en
     * {@code TRANSCRIPTION_TOTAL_BUDGET}, por debajo de los 120 s del Gateway.
     *
     * @param properties configuración validada
     * @param pause pausa antes del reintento
     * @param budget tiempo total de la transcripción de un envío
     * @return la regla
     */
    @Bean
    TranscriptionRetryPolicy transcriptionRetryPolicy(TranscriptionProperties properties,
            @Value("${cameia.transcription.retry-pause}") Duration pause,
            @Value("${cameia.transcription.total-budget}") Duration budget) {
        return new TranscriptionRetryPolicy(properties.timeout(), pause, budget);
    }

    /** @return la regla de los límites del audio */
    @Bean
    AudioAnswerPolicy audioAnswerPolicy() {
        return new AudioAnswerPolicy();
    }

    /**
     * @param properties configuración validada
     * @param json lector de JSON del servicio
     * @return el adaptador de AssemblyAI
     */
    @Bean
    SpeechTranscriptionPort speechTranscriptionPort(TranscriptionProperties properties, JsonMapper json) {
        if (!properties.hasApiKey()) {
            log.warn("ASSEMBLYAI_API_KEY no está definida: las respuestas por voz responderán TRANSCRIPTION_UNAVAILABLE");
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        return new AssemblyAiTranscriptionAdapter(properties.apiKey(), properties.baseUrl(),
                properties.speechModels(), properties.timeout(), properties.pollInterval(), http, json);
    }
}
