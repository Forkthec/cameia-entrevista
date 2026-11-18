package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AcceptedSpeechTest {

    @Test
    @DisplayName("Al imprimirla no muestra el texto de la respuesta, solo su largo")
    void toString_shouldNotPrintTheText() {
        AcceptedSpeech speech = new AcceptedSpeech("Eh, soy ingeniera", "es", null);

        assertThat(speech.toString()).doesNotContain("ingeniera").contains("length=17");
    }
}
