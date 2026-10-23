package tech.cameia.entrevista.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RedactedExceptionTest {

    @Test
    @DisplayName("La copia conserva la clase, la traza y la cadena de causas, y descarta los mensajes")
    void of_shouldKeepClassAndCauses_whenChainHasMessages() {
        var root = new IllegalStateException("valor secreto: Persona Real");
        var original = new RuntimeException("otro secreto", root);

        var copy = RedactedException.of(original);

        assertThat(copy.getMessage()).isEqualTo(RuntimeException.class.getName());
        assertThat(copy.getStackTrace()).isEqualTo(original.getStackTrace());
        assertThat(copy.getCause().getMessage()).isEqualTo(IllegalStateException.class.getName());
        assertThat(copy.getCause().getCause()).isNull();
    }

    @Test
    @DisplayName("Una cadena de causas con ciclo se corta en la primera repetición")
    void of_shouldStopAtFirstRepetition_whenCauseChainIsCircular() {
        var first = new RuntimeException("a");
        var second = new IllegalStateException("b", first);
        first.initCause(second);

        var copy = RedactedException.of(first);

        assertThat(copy.getCause().getMessage()).isEqualTo(IllegalStateException.class.getName());
        assertThat(copy.getCause().getCause()).isNull();
    }
}
