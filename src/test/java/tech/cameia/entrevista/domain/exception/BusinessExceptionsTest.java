package tech.cameia.entrevista.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BusinessExceptionsTest {

    static Stream<Arguments> exceptions() {
        return Stream.of(
                Arguments.of(new SessionTransitionNotAllowedException(), ErrorCode.SESSION_TRANSITION_NOT_ALLOWED,
                        "Esta entrevista ya no admite esa acción."),
                Arguments.of(new TurnTransitionNotAllowedException(), ErrorCode.TURN_TRANSITION_NOT_ALLOWED,
                        "Esta pregunta ya no admite esa acción."),
                Arguments.of(new SessionConflictException(), ErrorCode.SESSION_CONFLICT,
                        "La entrevista cambió mientras se procesaba tu solicitud. Inténtalo de nuevo."),
                Arguments.of(new SessionAlreadyExistsException(), ErrorCode.SESSION_ALREADY_EXISTS,
                        "Ya tienes una entrevista en preparación o en curso. Inténtalo de nuevo."),
                Arguments.of(new IdempotencyKeyAlreadyExistsException(), ErrorCode.IDEMPOTENCY_KEY_ALREADY_EXISTS,
                        "Esta solicitud ya se registró."),
                Arguments.of(new IdentityRequiredException(), ErrorCode.IDENTITY_REQUIRED,
                        "Tu sesión expiró. Inicia sesión de nuevo."),
                Arguments.of(new RequestPathInvalidException(), ErrorCode.REQUEST_PATH_INVALID_FORMAT,
                        "La petición no es válida."),
                Arguments.of(new DatabaseUnavailableException(new IllegalStateException("x")),
                        ErrorCode.DATABASE_UNAVAILABLE, "Ocurrió un error. Inténtalo de nuevo."));
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    @DisplayName("Cada excepción de negocio trae su código estable y su mensaje")
    void exception_shouldCarryCodeAndMessage(BusinessException error, ErrorCode code, String message) {
        assertThat(error.getErrorCode()).isEqualTo(code);
        assertThat(error.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("DatabaseUnavailableException conserva la causa técnica sin mostrarla en su mensaje")
    void databaseUnavailable_shouldKeepCause() {
        IllegalStateException cause = new IllegalStateException("password=secreto");

        DatabaseUnavailableException error = new DatabaseUnavailableException(cause);

        assertThat(error.getCause()).isSameAs(cause);
        assertThat(error.getMessage()).doesNotContain("secreto");
    }
}
