package tech.cameia.entrevista.infrastructure.client.googletts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;

/**
 * Credenciales ante Google Cloud Text-to-Speech: llave de API y credenciales por defecto (ADC), sin una cuenta real.
 */
class SpeechCredentialsTest {

    private static final URI ENDPOINT = URI.create("https://texttospeech.googleapis.com/v1/text:synthesize");

    @Test
    void apiKey_shouldSendKeyHeader() {
        assertThat(SpeechCredentials.apiKey("llave").headers(ENDPOINT)).containsExactly(
                Map.entry("x-goog-api-key", "llave"));
    }

    @Test
    @DisplayName("ADC: envía el token de la cuenta de servicio y carga las credenciales una sola vez")
    void applicationDefault_shouldSendBearerTokenAndLoadOnce() {
        AtomicInteger loads = new AtomicInteger();
        SpeechCredentials credentials = new SpeechCredentials.ApplicationDefault(() -> {
            loads.incrementAndGet();
            return GoogleCredentials.create(new AccessToken("token", java.util.Date.from(
                    Instant.now().plusSeconds(3600))));
        });

        assertThat(credentials.headers(ENDPOINT)).containsEntry("Authorization", "Bearer token");
        assertThat(credentials.headers(ENDPOINT)).containsEntry("Authorization", "Bearer token");
        assertThat(loads).hasValue(1);
    }

    @Test
    @DisplayName("CA-5.15.21: sin credenciales en el entorno responde SPEECH_UNAVAILABLE sin el detalle")
    void applicationDefault_shouldFail_whenNoCredentials() {
        SpeechCredentials credentials = new SpeechCredentials.ApplicationDefault(() -> {
            throw new IOException("The Application Default Credentials are not available");
        });

        assertThatThrownBy(() -> credentials.headers(ENDPOINT))
                .isInstanceOfSatisfying(SpeechUnavailableException.class, failure -> assertThat(failure.getReason())
                        .isEqualTo("sin credencial de Google: IOException"));
    }

    @Test
    @DisplayName("CA-5.15.21: una credencial que no entrega token responde SPEECH_UNAVAILABLE")
    void applicationDefault_shouldFail_whenNoToken() {
        GoogleCredentials empty = new GoogleCredentials() {
            @Override
            public Map<String, List<String>> getRequestMetadata(URI uri) {
                return Map.of("Authorization", List.of());
            }
        };
        GoogleCredentials nothing = new GoogleCredentials() {
            @Override
            public Map<String, List<String>> getRequestMetadata(URI uri) {
                return null;
            }
        };

        for (GoogleCredentials source : List.of(empty, nothing)) {
            SpeechCredentials credentials = new SpeechCredentials.ApplicationDefault(() -> source);
            assertThatThrownBy(() -> credentials.headers(ENDPOINT))
                    .isInstanceOfSatisfying(SpeechUnavailableException.class,
                            failure -> assertThat(failure.getReason()).isEqualTo("credencial de Google sin token"));
        }
    }

    @Test
    @DisplayName("CA-5.15.21: un fallo al renovar el token (permiso o red) responde SPEECH_UNAVAILABLE")
    void applicationDefault_shouldFail_whenRefreshFails() {
        GoogleCredentials failing = new GoogleCredentials() {
            @Override
            public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
                throw new IOException("403 PERMISSION_DENIED");
            }
        };
        SpeechCredentials credentials = new SpeechCredentials.ApplicationDefault(() -> failing);

        assertThatThrownBy(() -> credentials.headers(ENDPOINT)).isInstanceOf(SpeechUnavailableException.class);
    }

    @Test
    void applicationDefault_shouldBuild() {
        assertThat(SpeechCredentials.applicationDefault()).isInstanceOf(SpeechCredentials.ApplicationDefault.class);
    }
}
