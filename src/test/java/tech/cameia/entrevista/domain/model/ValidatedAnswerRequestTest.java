package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidatedAnswerRequestTest {

    private static final UUID TURN = UUID.fromString("9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12");

    @Test
    @DisplayName("La huella es un SHA-256 en hexadecimal")
    void fingerprint_shouldBeSha256Hex() {
        assertThat(new ValidatedAnswerRequest(TURN, "hola", "k").fingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("La misma entrada da la misma huella aunque cambie la clave; otro texto o turno, otra")
    void fingerprint_shouldDependOnTurnAndText() {
        String base = new ValidatedAnswerRequest(TURN, "hola", "k1").fingerprint();

        assertThat(new ValidatedAnswerRequest(TURN, "hola", "k2").fingerprint()).isEqualTo(base);
        assertThat(new ValidatedAnswerRequest(TURN, "hola.", "k1").fingerprint()).isNotEqualTo(base);
        assertThat(new ValidatedAnswerRequest(UUID.randomUUID(), "hola", "k1").fingerprint()).isNotEqualTo(base);
    }

    @Test
    @DisplayName("toString no muestra el texto de la respuesta")
    void toString_shouldNotShowAnswer() {
        assertThat(new ValidatedAnswerRequest(TURN, "secreto", "k").toString()).doesNotContain("secreto");
    }
}
