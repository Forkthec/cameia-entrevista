package tech.cameia.entrevista.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tech.cameia.entrevista.presentation.advice.ProblemDetailTestSupport;

/**
 * Cada código del catálogo tiene quién lo emite: la tabla de códigos de negocio del manejador, un
 * manejador de errores del framework, o un manejador propio de los fallos del modelo de lenguaje.
 */
class ErrorCodeTest {

    private static final Set<ErrorCode> FRAMEWORK = EnumSet.of(ErrorCode.VALIDATION_FAILED,
            ErrorCode.REQUEST_BODY_INVALID_FORMAT, ErrorCode.REQUEST_INVALID_VALUE, ErrorCode.ROUTE_NOT_FOUND,
            ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.MEDIA_TYPE_NOT_ALLOWED, ErrorCode.MEDIA_TYPE_NOT_ACCEPTABLE,
            ErrorCode.DATABASE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR);

    private static final Set<ErrorCode> MODEL_CLIENT = EnumSet.of(ErrorCode.LLM_UNAVAILABLE,
            ErrorCode.LLM_LIMIT_REACHED);

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void errorCode_shouldHaveAnEmitter_whenDeclared(ErrorCode code) {
        boolean covered = FRAMEWORK.contains(code) || MODEL_CLIENT.contains(code)
                || ProblemDetailTestSupport.businessCodes().contains(code);

        assertThat(covered).as("código sin quien lo responda: %s", code).isTrue();
    }
}
