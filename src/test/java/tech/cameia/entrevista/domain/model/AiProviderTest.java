package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Códigos de proveedor que se guardan en la base. */
class AiProviderTest {

    @Test
    @DisplayName("El principal se guarda como gemini y el respaldo como groq")
    void code_shouldMatchStoredValues() {
        assertThat(AiProvider.PRIMARY.code()).isEqualTo("gemini");
        assertThat(AiProvider.FALLBACK.code()).isEqualTo("groq");
    }
}
