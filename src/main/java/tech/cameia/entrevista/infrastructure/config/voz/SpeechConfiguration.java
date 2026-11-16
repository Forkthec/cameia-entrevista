package tech.cameia.entrevista.infrastructure.config.voz;

import java.net.http.HttpClient;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.application.service.SpeechSettings;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.policy.QuestionAudioPolicy;
import tech.cameia.entrevista.domain.port.SpeechSynthesisPort;
import tech.cameia.entrevista.infrastructure.client.googletts.GoogleTextToSpeechAdapter;
import tech.cameia.entrevista.infrastructure.client.googletts.SpeechCredentials;

/**
 * Ensambla la voz de las preguntas (HU-5.15): las reglas y el adaptador de Google Cloud Text-to-Speech.
 *
 * <p>La credencial es la llave {@code SPEECH_API_KEY} si está definida y, si no, las credenciales por defecto de la
 * aplicación (la cuenta de servicio en Cloud Run). Estas se cargan en la primera petición: si faltan, el servicio
 * arranca igual y cada petición de audio responde {@code SPEECH_UNAVAILABLE} (CA-5.15.21).</p>
 */
@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
class SpeechConfiguration {

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

    /**
     * @param properties configuración validada
     * @param json lector de JSON del servicio
     * @return el adaptador de Google Cloud Text-to-Speech
     */
    @Bean
    SpeechSynthesisPort speechSynthesisPort(SpeechProperties properties, JsonMapper json) {
        SpeechCredentials credentials = properties.usesApiKey() ? SpeechCredentials.apiKey(properties.apiKey())
                : SpeechCredentials.applicationDefault();
        if (properties.enabled()) {
            log.info("Voz de las preguntas con Google Cloud Text-to-Speech [credencial={}, vozEs={}, vozEn={},"
                    + " timeout={}]", properties.usesApiKey() ? "llave de API" : "ADC", properties.voiceEs(),
                    properties.voiceEn(), properties.timeout());
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        return new GoogleTextToSpeechAdapter(credentials, properties.baseUrl(),
                Map.of(InterviewLanguage.SPANISH_COLOMBIA, properties.voiceEs(),
                        InterviewLanguage.ENGLISH, properties.voiceEn()),
                properties.timeout(), http, json);
    }
}
