package tech.cameia.entrevista.infrastructure.client.googletts;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.auth.oauth2.GoogleCredentials;

import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;

/**
 * Credencial con la que el servicio se presenta ante Google Cloud Text-to-Speech.
 *
 * <p>En Cloud Run es la cuenta de servicio del servicio (credenciales por defecto de la aplicación, ADC); en desarrollo
 * puede ser una llave de API restringida a Text-to-Speech. Nunca se registra su valor.</p>
 */
@FunctionalInterface
public interface SpeechCredentials {

    /** Alcance de OAuth que pide Text-to-Speech. */
    String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    /**
     * Encabezados de autenticación para una petición.
     *
     * @param uri dirección de la petición
     * @return los encabezados, sin registrar
     * @throws SpeechUnavailableException si no hay credencial o no se pudo obtener el token (CA-5.15.21)
     */
    Map<String, String> headers(URI uri);

    /**
     * Credencial de llave de API, enviada en {@code x-goog-api-key}.
     *
     * @param apiKey llave; en blanco, cada llamada falla sin salir del servicio
     * @return la credencial
     */
    static SpeechCredentials apiKey(String apiKey) {
        return uri -> {
            if (apiKey == null || apiKey.isBlank()) {
                throw new SpeechUnavailableException("sin credencial de Text-to-Speech", null);
            }
            return Map.of("x-goog-api-key", apiKey);
        };
    }

    /**
     * Credenciales por defecto de la aplicación (ADC): la cuenta de servicio en Cloud Run, o
     * {@code GOOGLE_APPLICATION_CREDENTIALS} y {@code gcloud auth application-default login} en desarrollo. Se cargan
     * en la primera petición, así que el servicio arranca aunque falten.
     *
     * @return la credencial
     */
    static SpeechCredentials applicationDefault() {
        return new ApplicationDefault(() -> GoogleCredentials.getApplicationDefault().createScoped(List.of(SCOPE)));
    }

    /** Fuente de las credenciales de Google; existe para poder probar sin una cuenta real. */
    @FunctionalInterface
    interface GoogleCredentialsSource {

        /**
         * @return las credenciales con el alcance de Text-to-Speech
         * @throws IOException si no hay credenciales en el entorno
         */
        GoogleCredentials load() throws IOException;
    }

    /** ADC cargadas una sola vez y reutilizadas; la biblioteca de Google renueva el token cuando vence. */
    final class ApplicationDefault implements SpeechCredentials {

        private final GoogleCredentialsSource source;
        private volatile GoogleCredentials credentials;

        ApplicationDefault(GoogleCredentialsSource source) {
            this.source = Objects.requireNonNull(source, "source");
        }

        @Override
        public Map<String, String> headers(URI uri) {
            Map<String, List<String>> metadata;
            try {
                metadata = loaded().getRequestMetadata(uri);
            } catch (IOException | RuntimeException failure) {
                throw new SpeechUnavailableException("sin credencial de Google: " + failure.getClass().getSimpleName(),
                        failure);
            }
            Map<String, String> headers = new LinkedHashMap<>();
            if (metadata != null) {
                metadata.forEach((name, values) -> {
                    if (values != null && !values.isEmpty()) {
                        headers.put(name, values.getFirst());
                    }
                });
            }
            if (headers.isEmpty()) {
                throw new SpeechUnavailableException("credencial de Google sin token", null);
            }
            return headers;
        }

        private GoogleCredentials loaded() throws IOException {
            GoogleCredentials current = credentials;
            if (current == null) {
                synchronized (this) {
                    current = credentials;
                    if (current == null) {
                        current = source.load();
                        credentials = current;
                    }
                }
            }
            return current;
        }
    }
}
