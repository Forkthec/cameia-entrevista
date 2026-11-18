package tech.cameia.entrevista.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.model.FirebaseUid;

/** Conversión del identificador de la ruta: solo se admite un UUID canónico. */
class SessionCommandTest {

    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");

    @Test
    void of_shouldParseCanonicalUuid() {
        UUID id = UUID.randomUUID();
        assertThat(SessionCommand.of(id.toString(), OWNER).sessionId()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1-1-1-1-1", "<script>", "../etc/passwd", "%00",
            "3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10X", "3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a1",
            "3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a1g", "' OR 1=1 --"})
    void of_shouldReturnNullId_whenTextIsNotAUuid(String raw) {
        assertThat(SessionCommand.of(raw, OWNER).sessionId()).isNull();
    }

    @Test
    void of_shouldReturnNullId_whenTextIsNull() {
        assertThat(SessionCommand.of(null, OWNER).sessionId()).isNull();
    }
}
