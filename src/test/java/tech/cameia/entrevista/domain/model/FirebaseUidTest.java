package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.exception.IdentityRequiredException;

class FirebaseUidTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t", "uid\u0007", "uid\n", "uid\u0000", "uid\u007f",
        "uid‮", "uid​", "uid ", "uid "})
    @DisplayName("Rechaza una identidad ausente, vacía, en blanco, con caracteres de control, de formato o separadores")
    void required_shouldThrow_whenNullBlankOrControl(String raw) {
        assertThatThrownBy(() -> FirebaseUid.required(raw))
                .isInstanceOf(IdentityRequiredException.class)
                .hasMessage("Tu sesión expiró. Inicia sesión de nuevo.");
    }

    @Test
    @DisplayName("Rechaza una identidad de 129 caracteres")
    void required_shouldThrow_when129Chars() {
        assertThatThrownBy(() -> FirebaseUid.required("u".repeat(129))).isInstanceOf(IdentityRequiredException.class);
    }

    @Test
    @DisplayName("Rechaza una identidad de 129 puntos de código aunque cada uno ocupe dos caracteres")
    void required_shouldThrow_when129CodePoints() {
        assertThatThrownBy(() -> FirebaseUid.required("😀".repeat(129))).isInstanceOf(IdentityRequiredException.class);
    }

    @Test
    @DisplayName("Acepta una identidad de 128 caracteres con el mismo valor")
    void required_shouldAccept_when128Chars() {
        String raw = "u".repeat(128);

        assertThat(FirebaseUid.required(raw).value()).isEqualTo(raw);
    }

    @Test
    @DisplayName("Acepta una identidad de 128 puntos de código")
    void required_shouldAccept_when128CodePoints() {
        String raw = "😀".repeat(128);

        assertThat(FirebaseUid.required(raw).value()).isEqualTo(raw);
    }

    @Test
    @DisplayName("Acepta una identidad normal y no recorta los espacios de los extremos")
    void required_shouldKeepValueUntouched_whenValid() {
        assertThat(FirebaseUid.required("uid-a").value()).isEqualTo("uid-a");
        assertThat(FirebaseUid.required(" uid-a ").value()).isEqualTo(" uid-a ");
    }

    @Test
    @DisplayName("El largo máximo es 128")
    void maxLength_shouldBe128() {
        assertThat(FirebaseUid.MAX_LENGTH).isEqualTo(128);
    }
}
