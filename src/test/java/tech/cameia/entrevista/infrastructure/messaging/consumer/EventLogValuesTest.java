package tech.cameia.entrevista.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventLogValuesTest {

    @Test
    @DisplayName("Un identificador ausente se registra como guion")
    void safeMessageId_shouldReturnDash_whenNull() {
        assertThat(EventLogValues.safeMessageId(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("Un identificador de 64 caracteres válidos queda igual")
    void safeMessageId_shouldKeepValue_whenSafeAnd64() {
        String id = "a".repeat(64);

        assertThat(EventLogValues.safeMessageId(id)).isEqualTo(id);
        assertThat(EventLogValues.safeMessageId("3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d10"))
                .isEqualTo("3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d10");
    }

    @Test
    @DisplayName("Un identificador de 65 caracteres se registra como invalid")
    void safeMessageId_shouldReturnInvalid_when65() {
        assertThat(EventLogValues.safeMessageId("a".repeat(65))).isEqualTo("invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\nb", "a b", "a\rb", "", "á", "a;b", "[x]"})
    @DisplayName("Un identificador con saltos de línea, espacios o símbolos se registra como invalid")
    void safeMessageId_shouldReturnInvalid_whenUnsafeCharacters(String id) {
        assertThat(EventLogValues.safeMessageId(id)).isEqualTo("invalid");
    }
}
