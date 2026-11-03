package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidatedSessionRequestTest {

    private static final UUID PROFILE = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID ROLE = UUID.fromString("a0000001-0000-0000-0000-000000000001");

    private static ValidatedSessionRequest request(String offer, Tone tone, String key) {
        return new ValidatedSessionRequest(PROFILE, ROLE, offer, Modality.SIMULATION, tone, AnswerMode.TEXT,
                InterviewLanguage.SPANISH_COLOMBIA, key);
    }

    @Test
    @DisplayName("La huella es un SHA-256 de 64 caracteres hexadecimales en minúsculas")
    void fingerprint_shouldBeSha256Hex() {
        assertThat(request("Buscamos", Tone.WARM, null).fingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("La huella no depende de la clave: la misma configuración da la misma huella con otra clave")
    void fingerprint_shouldBeEqual_whenOnlyKeyDiffers() {
        assertThat(request("Buscamos", Tone.WARM, "k1").fingerprint())
                .isEqualTo(request("Buscamos", Tone.WARM, "k2").fingerprint());
    }

    @Test
    @DisplayName("Una oferta nula y una oferta con el texto «-» dan huellas distintas")
    void fingerprint_shouldDiffer_whenOfferIsNullOrDash() {
        assertThat(request(null, Tone.WARM, null).fingerprint())
                .isNotEqualTo(request("-", Tone.WARM, null).fingerprint());
    }

    @Test
    @DisplayName("Una oferta nula y una oferta con otro texto dan huellas distintas")
    void fingerprint_shouldDiffer_whenOfferDiffers() {
        assertThat(request(null, Tone.WARM, null).fingerprint())
                .isNotEqualTo(request("otra", Tone.WARM, null).fingerprint());
    }

    @Test
    @DisplayName("Un algoritmo que la JVM no trae falla con un error de instalación")
    void sha256Hex_shouldFail_whenAlgorithmMissing() {
        assertThatThrownBy(() -> ValidatedSessionRequest.sha256Hex("NO-EXISTE", "x"))
                .isInstanceOf(IllegalStateException.class).hasMessage("NO-EXISTE no disponible");
    }

    @Test
    @DisplayName("Cambiar el tono cambia la huella")
    void fingerprint_shouldDiffer_whenToneDiffers() {
        assertThat(request("Buscamos", Tone.WARM, null).fingerprint())
                .isNotEqualTo(request("Buscamos", Tone.STRICT, null).fingerprint());
    }

    @Test
    @DisplayName("Dos peticiones iguales ya normalizadas dan la misma huella")
    void fingerprint_shouldBeEqual_whenSameValues() {
        assertThat(request("Buscamos", Tone.WARM, null).fingerprint())
                .isEqualTo(request("Buscamos", Tone.WARM, null).fingerprint());
    }
}
