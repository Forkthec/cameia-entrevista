package tech.cameia.entrevista.infrastructure.messaging.payload;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * El texto mide entre {@link #min()} y {@link #max()} puntos de código Unicode y no contiene el
 * carácter nulo.
 *
 * <p>Se usa en lugar de {@code @Size} porque este cuenta unidades UTF-16 (un emoji vale 2) y
 * PostgreSQL cuenta puntos de código en {@code char_length}. Con {@code min = 1} se rechaza además
 * el texto formado solo por espacios. El valor {@code null} es válido: su presencia la exige
 * {@code @NotNull} aparte.</p>
 */
@Documented
@Constraint(validatedBy = TextLengthValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface TextLength {

    /** @return largo mínimo en puntos de código; con 1 o más se rechaza el texto en blanco */
    int min() default 0;

    /** @return largo máximo en puntos de código */
    int max() default Integer.MAX_VALUE;

    /** @return mensaje de la violación */
    String message() default "el texto no cumple el largo permitido";

    /** @return grupos de validación */
    Class<?>[] groups() default {};

    /** @return carga útil de la restricción */
    Class<? extends Payload>[] payload() default {};
}
