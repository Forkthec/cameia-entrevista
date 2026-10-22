package tech.cameia.entrevista.presentation.advice;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.IdentityRequiredException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TurnTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.FirebaseUid;

/**
 * Controlador que solo existe en las pruebas: provoca cada error del servicio para comprobar
 * su respuesta. No se empaqueta con la aplicación.
 */
@RestController
@RequestMapping("/test/errors")
public class ErrorProbeController {

    /** Cuerpo de prueba con dos campos obligatorios. */
    public record ProbeRequest(@NotBlank String name, @NotBlank String city) {
    }

    @GetMapping("/identity")
    Map<String, String> identity(@RequestHeader(value = "X-User-Id", required = false) String uid) {
        return Map.of("uid", FirebaseUid.required(uid).value());
    }

    @PostMapping(value = "/identity-body", consumes = "application/json")
    Map<String, String> identityWithBody(@RequestHeader(value = "X-User-Id", required = false) String uid,
            @RequestBody Map<String, String> ignored) {
        return Map.of("uid", FirebaseUid.required(uid).value());
    }

    @PostMapping(value = "/body", consumes = "application/json")
    Map<String, String> body(@Valid @RequestBody ProbeRequest request) {
        return Map.of("name", request.name());
    }

    @GetMapping("/business/{code}")
    String business(@PathVariable ErrorCode code) {
        throw switch (code) {
            case SESSION_TRANSITION_NOT_ALLOWED -> new SessionTransitionNotAllowedException();
            case TURN_TRANSITION_NOT_ALLOWED -> new TurnTransitionNotAllowedException();
            case SESSION_CONFLICT -> new SessionConflictException();
            case SESSION_ALREADY_EXISTS -> new SessionAlreadyExistsException();
            case IDEMPOTENCY_KEY_ALREADY_EXISTS -> new IdempotencyKeyAlreadyExistsException();
            case IDENTITY_REQUIRED -> new IdentityRequiredException();
            default -> new IllegalArgumentException("sin excepción de prueba para " + code);
        };
    }

    @GetMapping("/undefined-business")
    String undefinedBusiness() {
        throw new BusinessException(ErrorCode.LLM_UNAVAILABLE, "sin definición") {
        };
    }

    @GetMapping("/database/{kind}")
    String database(@PathVariable String kind) {
        SQLException cause = new SQLException("conexión rechazada por el servidor db-interno", "08001");
        throw switch (kind) {
            case "transaction" -> new CannotCreateTransactionException("no se pudo abrir", cause);
            case "resource" -> new DataAccessResourceFailureException("recurso caído", cause);
            default -> new CannotGetJdbcConnectionException("sin conexión", cause);
        };
    }

    @GetMapping("/integrity")
    String integrity() {
        throw new DataIntegrityViolationException("x");
    }

    @GetMapping("/boom")
    String boom() {
        throw new IllegalStateException("detalle interno");
    }

    @GetMapping("/ok")
    String ok() {
        return "ok";
    }

    @GetMapping("/typed/{id}")
    String typed(@PathVariable UUID id) {
        return id.toString();
    }

    @GetMapping("/header")
    String header(@RequestHeader("X-Trace") String trace) {
        return "ok";
    }

    /** Restricción de prueba que declara su código de error como carga dinámica de la violación. */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = AlwaysRejectsWithCode.class)
    public @interface RejectedWithCode {
        String message() default "Valor rechazado con código propio.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** Validador que siempre rechaza y declara REQUEST_BODY_INVALID_FORMAT como código. */
    public static class AlwaysRejectsWithCode implements ConstraintValidator<RejectedWithCode, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            context.unwrap(HibernateConstraintValidatorContext.class)
                    .withDynamicPayload(ErrorCode.REQUEST_BODY_INVALID_FORMAT);
            return false;
        }
    }

    /** Cuerpo de prueba cuyo único campo siempre se rechaza con un código declarado. */
    public record CodedRequest(@RejectedWithCode String value) {
    }

    @PostMapping(value = "/coded", consumes = "application/json")
    String coded(@Valid @RequestBody CodedRequest request) {
        return request.value();
    }

    @GetMapping("/required-identity")
    String requiredIdentity(@RequestHeader("X-User-Id") String uid) {
        return "ok";
    }

    @GetMapping("/required-param")
    String requiredParam(@RequestParam("page") String page) {
        return "ok";
    }

    @GetMapping("/multipart")
    String multipart() {
        throw new MultipartException("Current request is not a multipart request");
    }

    @GetMapping("/multipart-too-large")
    String multipartTooLarge() {
        throw new MaxUploadSizeExceededException(1);
    }
}
