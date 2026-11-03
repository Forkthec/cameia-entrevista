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
            ErrorCode.DATABASE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR, ErrorCode.REQUEST_MULTIPART_INVALID_FORMAT);

    /** Causas por campo que viajan dentro de un {@code VALIDATION_FAILED}, en el elemento de su campo. */
    private static final Set<ErrorCode> FIELD_CODES = EnumSet.of(ErrorCode.PROFILE_ID_REQUIRED,
            ErrorCode.PROFILE_ID_INVALID_FORMAT, ErrorCode.TARGET_ROLE_ID_REQUIRED,
            ErrorCode.TARGET_ROLE_ID_INVALID_FORMAT, ErrorCode.TARGET_ROLE_ID_INVALID_VALUE,
            ErrorCode.JOB_OFFER_TOO_LONG, ErrorCode.JOB_OFFER_INVALID_CHARACTERS,
            ErrorCode.MODE_REQUIRED, ErrorCode.MODE_INVALID_VALUE,
            ErrorCode.MODE_NOT_ALLOWED, ErrorCode.TONE_REQUIRED, ErrorCode.TONE_INVALID_VALUE,
            ErrorCode.ANSWER_FORMAT_REQUIRED, ErrorCode.ANSWER_FORMAT_INVALID_VALUE,
            ErrorCode.SESSION_LANGUAGE_REQUIRED, ErrorCode.SESSION_LANGUAGE_INVALID_VALUE,
            ErrorCode.IDEMPOTENCY_KEY_INVALID_FORMAT);

    private static final Set<ErrorCode> MODEL_CLIENT = EnumSet.of(ErrorCode.LLM_UNAVAILABLE,
            ErrorCode.LLM_LIMIT_REACHED);

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void errorCode_shouldHaveAnEmitter_whenDeclared(ErrorCode code) {
        boolean covered = FRAMEWORK.contains(code) || FIELD_CODES.contains(code) || MODEL_CLIENT.contains(code)
                || ProblemDetailTestSupport.businessCodes().contains(code);

        assertThat(covered).as("código sin quien lo responda: %s", code).isTrue();
    }
}
