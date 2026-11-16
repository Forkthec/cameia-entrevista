package tech.cameia.entrevista.infrastructure.config.voz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.application.service.SpeechSettings;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.policy.QuestionAudioPolicy;
import tech.cameia.entrevista.domain.policy.SpeechDirectionPolicy;
import tech.cameia.entrevista.domain.port.SpeechDirectorPort;
import tech.cameia.entrevista.domain.port.SpeechSynthesisPort;
import tech.cameia.entrevista.infrastructure.client.gemini.GeminiSpeechDirectorAdapter;
import tech.cameia.entrevista.infrastructure.client.gemini.GeminiSpeechSynthesisAdapter;

/**
 * Ensambla la voz de las preguntas (HU-5.15): las reglas, el director de la voz y el adaptador de Gemini TTS.
 *
 * <p>Con la voz encendida y sin llave de Gemini, el servicio arranca igual y avisa una sola vez; cada petición de audio
 * responde {@code SPEECH_UNAVAILABLE} (CA-5.15.21), como el respaldo sin llave del ADR 0014.</p>
 */
@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
class SpeechConfiguration {

    private static final String PROMPT = "prompts/" + GeminiSpeechDirectorAdapter.PROMPT_VERSION + ".txt";
    private static final Logger log = LoggerFactory.getLogger(SpeechConfiguration.class);

    /**
     * @param properties configuración validada
     * @return los límites que usan los casos de uso
     */
    @Bean
    SpeechSettings speechSettings(SpeechProperties properties) {
        return new SpeechSettings(properties.enabled(), properties.maxConcurrent(),
                properties.monthlyCharacterCap());
    }

    /**
     * @param properties configuración validada
     * @return la regla de las preguntas que se pueden escuchar
     */
    @Bean
    QuestionAudioPolicy questionAudioPolicy(SpeechProperties properties) {
        return new QuestionAudioPolicy(properties.maxPlays());
    }

    /** @return las etiquetas y estilos de cada tono */
    @Bean
    SpeechDirectionPolicy speechDirectionPolicy() {
        return new SpeechDirectionPolicy();
    }

    /**
     * @param properties configuración validada
     * @param apiKey llave de Gemini
     * @param json lector de JSON del servicio
     * @return el adaptador de Gemini TTS
     */
    @Bean
    SpeechSynthesisPort speechSynthesisPort(SpeechProperties properties,
            @Value("${cameia.llm.api-key:}") String apiKey, JsonMapper json) {
        if (properties.enabled() && (apiKey == null || apiKey.isBlank())) {
            log.warn("GOOGLE_GENAI_API_KEY no está definida: escuchar la pregunta responderá SPEECH_UNAVAILABLE");
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        return new GeminiSpeechSynthesisAdapter(apiKey, properties.baseUrl(), properties.model(),
                properties.fallbackModel(),
                Map.of(InterviewLanguage.SPANISH_COLOMBIA, properties.voiceEs(),
                        InterviewLanguage.ENGLISH, properties.voiceEn()),
                properties.timeout(), http, json);
    }

    /**
     * @param properties configuración validada
     * @param apiKey llave de Gemini
     * @param json lector de JSON del servicio
     * @return el adaptador que coloca las etiquetas de voz
     */
    @Bean
    SpeechDirectorPort speechDirectorPort(SpeechProperties properties,
            @Value("${cameia.llm.api-key:}") String apiKey, JsonMapper json) {
        SpeechProperties.Director director = properties.director();
        Duration timeout = director.timeout();
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        return new GeminiSpeechDirectorAdapter(apiKey, properties.baseUrl(), director.model(),
                director.thinkingLevel(), timeout, readPrompt(), http, json);
    }

    private static String readPrompt() {
        try (InputStream in = new ClassPathResource(PROMPT).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException missing) {
            throw new UncheckedIOException("No se pudo leer " + PROMPT, missing);
        }
    }
}
