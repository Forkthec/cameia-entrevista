package tech.cameia.entrevista.infrastructure.config.voz;

import java.net.http.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.policy.AudioAnswerPolicy;
import tech.cameia.entrevista.domain.policy.TranscriptionPolicy;
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

    /** @return la regla de los tipos de audio admitidos */
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
